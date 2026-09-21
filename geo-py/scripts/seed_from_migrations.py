"""从 Flyway 迁移脚本导入地理数据到 PostGIS。

为什么解析迁移而不是直连 MySQL：
  迁移脚本是这套 schema 的**唯一真相源**，且离线可重放；
  开发机不一定有跑得起来的 MySQL（本次就是这样），而对照实验需要的是
  「与线上一致的坐标数据」，迁移里的种子数据正是它。

解析策略：按列名映射，不按位置——16 处 `t_station` INSERT 的列顺序并不统一，
位置解析会在下一次加列时静默错位。无法解析的语句**跳过并计数上报**，不猜。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fsd_geo import db  # noqa: E402
from fsd_geo.config import DbConfig  # noqa: E402

MIGRATIONS_DIR = Path(__file__).resolve().parents[2] / "back" / "sql" / "migrations"

# 充电类站点编码特征（V26/V28 用 ZJF-CHG-*，V04 用 CHARGE 类型）
CHARGING_CODE_RE = re.compile(r"CHG|CHARGE", re.IGNORECASE)
CHARGING_TYPE_RE = re.compile(r"CHARG", re.IGNORECASE)

_INSERT_RE = re.compile(
    r"INSERT\s+INTO\s+`?(t_station|t_park_geofence|t_road_node)`?\s*\(([^)]*)\)(.*?);",
    re.IGNORECASE | re.DOTALL,
)
_JSON_ARRAY_RE = re.compile(r"JSON_ARRAY\s*\(", re.IGNORECASE)
_NUMBER_RE = re.compile(r"^-?\d+(?:\.\d+)?$")
_STRING_LIT_RE = re.compile(r"^'((?:[^']|'')*)'$")
_PARK_CODE_RE = re.compile(r"park_code\s*=\s*'([^']+)'", re.IGNORECASE)


def _split_tokens(text: str) -> list[str]:
    """按顶层逗号切分一个值元组，尊重单引号字符串与嵌套括号。"""
    tokens: list[str] = []
    buf: list[str] = []
    depth = 0
    in_str = False
    i = 0
    while i < len(text):
        ch = text[i]
        if in_str:
            if ch == "'":
                if i + 1 < len(text) and text[i + 1] == "'":
                    buf.append("'")
                    i += 2
                    continue
                in_str = False
            buf.append(ch)
        elif ch == "'":
            in_str = True
            buf.append(ch)
        elif ch == "(":
            depth += 1
            buf.append(ch)
        elif ch == ")":
            depth -= 1
            buf.append(ch)
        elif ch == "," and depth == 0:
            tokens.append("".join(buf).strip())
            buf = []
        else:
            buf.append(ch)
        i += 1
    if buf:
        tokens.append("".join(buf).strip())
    return tokens


def _literal(token: str) -> str | None:
    """把 SQL 字面量转成字符串；表达式（如 p.id）返回 None。"""
    token = token.strip()
    if _NUMBER_RE.match(token):
        return token
    m = _STRING_LIT_RE.match(token)
    if m:
        return m.group(1).replace("''", "'")
    if token.upper() == "NULL":
        return None
    return None


def _num(token: str) -> float | None:
    raw = _literal(token)
    if raw is None or not _NUMBER_RE.match(raw):
        return None
    return float(raw)


def _strip_comments(sql: str) -> str:
    return re.sub(r"--[^\n]*", "", sql)


def _extract_polygon(tail: str) -> list[list[float]] | None:
    """从 JSON_ARRAY(JSON_ARRAY(lng,lat),...) 里取出环。"""
    m = _JSON_ARRAY_RE.search(tail)
    if not m:
        return None
    start = m.end()
    depth = 1
    i = start
    while i < len(tail) and depth:
        if tail[i] == "(":
            depth += 1
        elif tail[i] == ")":
            depth -= 1
            if depth == 0:
                break
        i += 1
    body = tail[start:i]
    ring: list[list[float]] = []
    for part in re.findall(r"JSON_ARRAY\s*\(([^()]*)\)", body, re.IGNORECASE):
        nums = _split_tokens(part)
        if len(nums) == 2:
            lng, lat = _num(nums[0]), _num(nums[1])
            if lng is not None and lat is not None:
                ring.append([lng, lat])
    return ring if len(ring) >= 3 else None


def _column_map(header: str) -> dict[str, int]:
    names = [_strip_quotes(t) for t in _split_tokens(header)]
    return {n.lower(): idx for idx, n in enumerate(names)}


def _strip_quotes(token: str) -> str:
    return token.strip().strip("`").strip()


def _rows_from_values(tail: str) -> list[list[str]]:
    """解析 VALUES (a,b,...),(c,d,...) 形态。

    先截掉 `ON DUPLICATE KEY UPDATE` 尾巴：里面的 `VALUES(`col`)` 会被括号扫描
    误认成新的数据行，产出只有 1 个字段的假行。
    """
    tail = re.split(r"\bON\s+DUPLICATE\s+KEY\s+UPDATE\b", tail, maxsplit=1, flags=re.IGNORECASE)[0]
    m = re.search(r"VALUES", tail, re.IGNORECASE)
    if not m:
        return []
    body = tail[m.end():]
    rows: list[list[str]] = []
    depth = 0
    buf: list[str] = []
    in_str = False
    i = 0
    while i < len(body):
        ch = body[i]
        if in_str:
            if ch == "'":
                if i + 1 < len(body) and body[i + 1] == "'":
                    buf.append("''")
                    i += 2
                    continue
                in_str = False
            buf.append(ch)
        elif ch == "'":
            in_str = True
            buf.append(ch)
        elif ch == "(":
            depth += 1
            if depth == 1:
                buf = []
                i += 1
                continue
            buf.append(ch)
        elif ch == ")":
            depth -= 1
            if depth == 0:
                rows.append(_split_tokens("".join(buf)))
                buf = []
            else:
                buf.append(ch)
        else:
            if depth >= 1:
                buf.append(ch)
        i += 1
    return rows


def _rows_from_select(tail: str, columns: dict[str, int]) -> list[list[str]]:
    """解析 `SELECT p.id, 'X', ... FROM t_park p WHERE p.park_code = 'DEFAULT'` 形态。

    这类种子用 `p.id` 作为 park_id，需要靠 WHERE 里的 park_code 反查真实 id。
    """
    m = re.search(r"\bSELECT\b(.*?)\bFROM\b", tail, re.IGNORECASE | re.DOTALL)
    if not m:
        return []
    tokens = _split_tokens(m.group(1))
    park = _PARK_CODE_RE.search(tail)
    if "park_id" in columns and tokens[columns["park_id"]].strip().lower() in {"p.id", "park_id"}:
        if not park:
            return []
        tokens[columns["park_id"]] = f"'{park.group(1)}'"
    return [tokens]


def parse_migrations(path: Path = MIGRATIONS_DIR) -> dict[str, list[dict[str, object]]]:
    stations: list[dict[str, object]] = []
    fences: list[dict[str, object]] = []
    nodes: list[dict[str, object]] = []
    skipped = 0

    for sql_file in sorted(path.glob("V*.sql")):
        text = _strip_comments(sql_file.read_text(encoding="utf-8"))
        for m in _INSERT_RE.finditer(text):
            table, header, tail = m.group(1).lower(), m.group(2), m.group(3)
            columns = _column_map(header)
            if tail.strip().upper().startswith("SELECT"):
                rows = _rows_from_select(tail, columns)
            else:
                rows = _rows_from_values(tail)
            if not rows:
                skipped += 1
                continue
            for row in rows:
                if len(row) != len(columns):
                    skipped += 1
                    continue
                vals = {col: row[idx] for col, idx in columns.items()}
                if table == "t_station":
                    rec = _station_record(vals, sql_file.name)
                elif table == "t_park_geofence":
                    rec = _fence_record(vals, tail, sql_file.name)
                else:
                    rec = _node_record(vals, sql_file.name)
                if rec is None:
                    skipped += 1
                    continue
                {"t_station": stations, "t_park_geofence": fences, "t_road_node": nodes}[table].append(rec)

    return {
        "stations": stations,
        "fences": fences,
        "nodes": nodes,
        "_counts": {
            "stations": len(stations),
            "fences": len(fences),
            "nodes": len(nodes),
            "skipped": skipped,
        },
    }


def _station_record(vals: dict[str, str], source: str) -> dict[str, object] | None:
    code = _literal(vals.get("station_code", ""))
    lng = _num(vals.get("coord_lng", ""))
    lat = _num(vals.get("coord_lat", ""))
    if not code or lng is None or lat is None:
        return None
    status = (_literal(vals.get("status", "")) or "ACTIVE").upper()
    return {
        "park_code_or_id": _literal(vals.get("park_id", "")) or "1",
        "station_code": code,
        "station_name": _literal(vals.get("station_name", "")) or code,
        "station_type": (_literal(vals.get("station_type", "")) or "GENERAL").upper(),
        "status": status,
        "lng": lng,
        "lat": lat,
        "is_charging": bool(
            CHARGING_CODE_RE.search(code) or CHARGING_TYPE_RE.search(vals.get("station_type", ""))
        ),
        "source": source,
    }


def _fence_record(vals: dict[str, str], tail: str, source: str) -> dict[str, object] | None:
    code = _literal(vals.get("fence_code", ""))
    ring = _extract_polygon(tail)
    if not code or ring is None:
        return None
    return {
        "park_code_or_id": _literal(vals.get("park_id", "")) or "1",
        "fence_code": code,
        "fence_name": _literal(vals.get("fence_name", "")) or code,
        "fence_type": (_literal(vals.get("fence_type", "")) or "BOUNDARY").upper(),
        "status": (_literal(vals.get("status", "")) or "ACTIVE").upper(),
        "ring": ring,
        "source": source,
    }


def _node_record(vals: dict[str, str], source: str) -> dict[str, object] | None:
    code = _literal(vals.get("node_code", ""))
    lng = _num(vals.get("coord_lng", ""))
    lat = _num(vals.get("coord_lat", ""))
    if not code or lng is None or lat is None:
        return None
    return {
        "park_code_or_id": _literal(vals.get("park_id", "")) or "1",
        "node_code": code,
        "lng": lng,
        "lat": lat,
        "source": source,
    }


PARK_SEED = [(1, "DEFAULT", "默认示范园区"), (2, "CAMPUS-B", "B区仓储园")]


def seed(data: dict[str, list[dict[str, object]]]) -> dict[str, int]:
    with db.get_pool().connection() as conn:
        cur = conn.cursor()

        for park_id, park_code, park_name in PARK_SEED:
            cur.execute(
                "INSERT INTO park (park_id, park_code, park_name) VALUES (%s,%s,%s) "
                "ON CONFLICT (park_id) DO UPDATE SET park_code = EXCLUDED.park_code, "
                "park_name = EXCLUDED.park_name",
                (park_id, park_code, park_name),
            )
        code_to_id = {park_code: park_id for park_id, park_code, _ in PARK_SEED}

        def resolve(rec: dict[str, object]) -> int:
            raw = str(rec["park_code_or_id"])
            if raw.isdigit():
                return int(raw)
            return code_to_id.get(raw, 1)

        for rec in data["fences"]:
            ring = [list(pt) for pt in rec["ring"]]  # type: ignore[union-attr]
            if ring[0] != ring[-1]:
                ring.append(list(ring[0]))
            geojson = json.dumps({"type": "Polygon", "coordinates": [ring]})
            cur.execute(
                """
                INSERT INTO geofence (park_id, fence_code, fence_name, fence_type, status, geom)
                     VALUES (%s,%s,%s,%s,%s, ST_SetSRID(ST_GeomFromGeoJSON(%s), 4326))
                ON CONFLICT (park_id, fence_code) DO UPDATE SET
                    fence_name = EXCLUDED.fence_name, fence_type = EXCLUDED.fence_type,
                    status = EXCLUDED.status, geom = EXCLUDED.geom
                """,
                (
                    resolve(rec), rec["fence_code"], rec["fence_name"],
                    rec["fence_type"], rec["status"], geojson,
                ),
            )

        for rec in data["stations"]:
            cur.execute(
                """
                INSERT INTO station (park_id, station_code, station_name, station_type,
                                     status, coord_lng, coord_lat, is_charging)
                     VALUES (%s,%s,%s,%s,%s,%s,%s,%s)
                ON CONFLICT (park_id, station_code) DO UPDATE SET
                    station_name = EXCLUDED.station_name, station_type = EXCLUDED.station_type,
                    status = EXCLUDED.status, coord_lng = EXCLUDED.coord_lng,
                    coord_lat = EXCLUDED.coord_lat, is_charging = EXCLUDED.is_charging
                """,
                (resolve(rec), rec["station_code"], rec["station_name"], rec["station_type"],
                 rec["status"], rec["lng"], rec["lat"], rec["is_charging"]),
            )

        for rec in data["nodes"]:
            cur.execute(
                "INSERT INTO road_node (park_id, node_code, coord_lng, coord_lat) VALUES (%s,%s,%s,%s) "
                "ON CONFLICT (park_id, node_code) DO UPDATE SET coord_lng = EXCLUDED.coord_lng, "
                "coord_lat = EXCLUDED.coord_lat",
                (resolve(rec), rec["node_code"], rec["lng"], rec["lat"]),
            )

        for kind in ("station", "geofence", "road_node"):
            cur.execute(
                "INSERT INTO sync_batch (source_file, entity_kind, row_count) "
                "SELECT 'migrations', %s, count(*) FROM " + kind + " "
                "ON CONFLICT (source_file, entity_kind) DO UPDATE SET "
                "row_count = EXCLUDED.row_count, finished_at = now()",
                (kind,),
            )
        conn.commit()

    return {
        "stations": int(db.query_one("SELECT count(*) FROM station")[0]),  # type: ignore[index]
        "fences": int(db.query_one("SELECT count(*) FROM geofence")[0]),  # type: ignore[index]
        "nodes": int(db.query_one("SELECT count(*) FROM road_node")[0]),  # type: ignore[index]
    }


def main() -> int:
    ap = argparse.ArgumentParser(description="从 Flyway 迁移导入地理数据到 PostGIS")
    ap.add_argument("--dry-run", action="store_true", help="只解析不写库，打印条数与样例")
    args = ap.parse_args()

    data = parse_migrations()
    print(json.dumps(data["_counts"], ensure_ascii=False), "解析结果")
    if args.dry_run:
        for rec in data["fences"][:2]:
            print("  fence:", rec["fence_code"], "顶点数", len(rec["ring"]))  # type: ignore[arg-type]
        for rec in data["stations"][:3]:
            tag = "charging" if rec["is_charging"] else ""  # type: ignore[union-attr]
            print("  station:", rec["station_code"], rec["lng"], rec["lat"], tag)  # type: ignore[arg-type]
        return 0

    db.init_pool(DbConfig.from_env())
    print(seed(data), "写库结果")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
