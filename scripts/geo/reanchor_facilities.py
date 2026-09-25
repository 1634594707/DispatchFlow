#!/usr/bin/env python3
"""把站点/泊位/充电桩/服务位重新吸附到当前 ACTIVE 路网节点上（§M2E、§1.8 的 anchor_node_code 要求）。

为什么必须做：§1.8 写的是"必须吸附到 OSM 路网节点，没有吸附点 = isReachable 恒 false"。
换掉路网（旧的是 V38 手写的 RN01~RN55 网格）之后，所有 anchor_node_code 都指向已停用的
节点，派单会整片 UNREACHABLE。

规则：
  * 只在**最大连通分量**里挑最近节点 —— 挑到孤立小分量等于挑到不可达（实测小分量只有 9 个节点）。
  * 距离超过 --max-snap-meters 的行不改、只报告，交回人工在地图上确认（§1.8 验收要求）。
  * 幂等：同一行同一目标节点，重复执行结果一致。
  * 没有自身坐标的表按外键借坐标（充电桩 -> 泊位，服务位 -> 站点），避免原本叠在同一点的
    14 个对象被吸到不同节点上。

用法：
  python scripts/geo/reanchor_facilities.py --dry-run
  python scripts/geo/reanchor_facilities.py
"""

from __future__ import annotations

import argparse
import math
import subprocess
import sys

CONTAINER = "fsd-mysql"
SCHEMA = "fsd_core"
LAT_M = 111_320.0

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


def mysql(sql: str) -> list[list[str]]:
    args = ["docker", "exec", "-i", CONTAINER, "sh", "-c",
            f'exec mysql --default-character-set=utf8mb4 -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -D {SCHEMA}']
    proc = subprocess.run(args, input=sql, text=True, capture_output=True, encoding="utf-8")
    if proc.returncode != 0:
        raise RuntimeError((proc.stderr or "").strip()[-400:])
    return [line.split("\t") for line in proc.stdout.splitlines() if line.strip()]


def dist_m(a, b):
    dlat = (b[1] - a[1]) * LAT_M
    dlng = (b[0] - a[0]) * LAT_M * math.cos(math.radians(a[1]))
    return math.hypot(dlng, dlat)


def largest_component():
    """(节点坐标表, 最大连通分量节点集合, 其余分量大小)。BIDIRECTIONAL 两个方向都算。"""
    pts, groups = components_of_graph()
    biggest = max(groups.values(), key=len)
    rest = sorted((len(g) for g in groups.values() if g is not biggest), reverse=True)
    return pts, set(biggest), rest


def components_of_graph():
    """{根节点: [成员...]}，同时返回节点坐标表。

    算的是**强连通分量（有向）**，不是无向连通：`ParkRoadGraph` 把 BIDIRECTIONAL 展成两个方向、
    FORWARD 只放行一个方向，所以"无向连通"并不等于"能送"。实测裁断版图 113 条边里有 17 条单向，
    按无向分量挑锚点会高估可达性（§13.9.2）。
    """
    segs = mysql("SELECT from_node_code, to_node_code, direction FROM t_road_segment "
                 "WHERE deleted=0 AND status='ACTIVE';")
    nrows = mysql("SELECT node_code, coord_lng, coord_lat FROM t_road_node "
                  "WHERE deleted=0 AND status='ACTIVE' AND coord_lng IS NOT NULL;")
    pts = {r[0]: (float(r[1]), float(r[2])) for r in nrows}
    fwd: dict[str, list[str]] = {n: [] for n in pts}
    rev: dict[str, list[str]] = {n: [] for n in pts}
    for a, b, d in segs:
        if a in fwd and b in fwd:
            fwd[a].append(b)
            rev[b].append(a)
            if str(d).upper() != "FORWARD":     # BIDIRECTIONAL / REVERSE 之类都按可双向处理
                fwd[b].append(a)
                rev[a].append(b)

    order: list[str] = []                       # Kosaraju 第一遍：原图后序
    visited: set[str] = set()
    for start in sorted(pts):
        if start in visited:
            continue
        visited.add(start)
        stack: list[tuple[str, object]] = [(start, iter(fwd[start]))]
        while stack:
            node, it = stack[-1]
            pushed = False
            for nxt in it:
                if nxt not in visited:
                    visited.add(nxt)
                    stack.append((nxt, iter(fwd[nxt])))
                    pushed = True
                    break
            if not pushed:
                order.append(node)
                stack.pop()

    groups: dict[str, list[str]] = {}           # 第二遍：反图上取分量
    seen: set[str] = set()
    for start in reversed(order):
        if start in seen:
            continue
        seen.add(start)
        comp, stack = [], [start]
        while stack:
            x = stack.pop()
            comp.append(x)
            for y in rev[x]:
                if y not in seen:
                    seen.add(y)
                    stack.append(y)
        comp.sort()
        groups[comp[0]] = comp
    return pts, groups


def record_components(park_id: int, dry_run: bool) -> None:
    """把分量归属写进 t_road_node_component（§1.8「回退到可达子集并记录断点」的落点）。"""
    pts, groups = components_of_graph()
    ordered = sorted(groups.values(), key=len, reverse=True)
    rows = []
    for cid, members in enumerate(ordered, start=1):
        for n in sorted(members):
            rows.append(f"({park_id}, '{n}', {cid}, {len(members)}, {1 if cid == 1 else 0})")
    print(f"分量归属：{len(ordered)} 个分量 / {len(rows)} 个节点，最大分量 "
          f"{len(ordered[0]) if ordered else 0} 个")
    if dry_run or not rows:
        return
    mysql("DELETE FROM t_road_node_component WHERE park_id = %d;" % park_id)
    for chunk_start in range(0, len(rows), 200):
        chunk = rows[chunk_start:chunk_start + 200]
        mysql("INSERT INTO t_road_node_component (park_id, node_code, component_id, component_size, is_largest)\n"
              "VALUES " + ",".join(chunk) +
              "\nON DUPLICATE KEY UPDATE component_id=VALUES(component_id), "
              "component_size=VALUES(component_size), is_largest=VALUES(is_largest), deleted=0;")


# (表, 要写的列, 业务键列, 用哪种坐标)
TARGETS = [
    ("t_station", "anchor_node_code", "station_code", "station"),
    ("t_parking_slot", "entry_node_code", "slot_code", "slot"),
    ("t_parking_slot", "exit_node_code", "slot_code", "slot"),
    ("t_charging_pile", "entry_node_code", "pile_code", "pile"),
    ("t_charging_pile", "exit_node_code", "pile_code", "pile"),
    ("t_station_service_position", "access_node_code", "position_code", "position"),
]


def load_coords() -> dict[tuple[str, str], tuple[float, float]]:
    """station/slot 用自身经纬度；pile 借泊位、service position 借站点。"""
    geo: dict[tuple[str, str], tuple[float, float]] = {}
    station_id: dict[str, str] = {}
    slot_id: dict[str, str] = {}
    for r in mysql("SELECT station_code, coord_lng, coord_lat FROM t_station "
                   "WHERE deleted=0 AND coord_lng IS NOT NULL;"):
        geo[("station", r[0])] = (float(r[1]), float(r[2]))
    for r in mysql("SELECT id, station_code FROM t_station WHERE deleted=0;"):
        station_id[r[0]] = r[1]
    for r in mysql("SELECT slot_code, coord_lng, coord_lat FROM t_parking_slot "
                   "WHERE deleted=0 AND coord_lng IS NOT NULL;"):
        geo[("slot", r[0])] = (float(r[1]), float(r[2]))
    for r in mysql("SELECT id, slot_code FROM t_parking_slot WHERE deleted=0;"):
        slot_id[r[0]] = r[1]
    for r in mysql("SELECT p.pile_code, s.slot_code FROM t_charging_pile p "
                   "LEFT JOIN t_parking_slot s ON s.id = p.parking_slot_id AND s.deleted = 0 "
                   "WHERE p.deleted = 0;"):
        key = r[1]
        if key and ("slot", key) in geo:
            geo[("pile", r[0])] = geo[("slot", key)]
    for r in mysql("SELECT sp.position_code, st.station_code FROM t_station_service_position sp "
                   "LEFT JOIN t_station st ON st.id = sp.station_id AND st.deleted = 0 "
                   "WHERE sp.deleted = 0;"):
        key = r[1]
        if key and ("station", key) in geo:
            geo[("position", r[0])] = geo[("station", key)]
    return geo


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--components-only", action="store_true",
                    help="只刷新 t_road_node_component，不动任何 anchor_node_code。"
                         "路网 seed 新增节点后必须跑一次：这张表是派生物，落后于图就会让"
                         "新节点在 is_largest 过滤里凭空消失（AMWL/AMCJ 16 个节点踩过）。")
    ap.add_argument("--max-snap-meters", type=float, default=250.0)
    ap.add_argument("--park-id", type=int, default=1)
    args = ap.parse_args()

    pts, keep, rest = largest_component()
    print(f"ACTIVE 且带 GPS 的节点={len(pts)}，最大连通分量={len(keep)}，其余分量大小={rest}")
    record_components(args.park_id, args.dry_run)
    if args.components_only:
        print("--components-only：只重算分量归属，未触碰 anchor/entry/exit/access 列")
        return 0
    geo = load_coords()

    changed = skipped = unchanged = 0
    for table, column, key_col, kind in TARGETS:
        current = {r[0]: r[1] for r in mysql(
            f"SELECT {key_col}, IFNULL({column},'') FROM {table} WHERE deleted=0;")}
        for key, here in sorted(geo.items()):
            if key[0] != kind or key[1] not in current:
                continue
            code, d = min(((n, dist_m(here, pts[n])) for n in keep), key=lambda x: x[1])
            if current[key[1]] == code:
                unchanged += 1
                continue
            if d > args.max_snap_meters:
                skipped += 1
                print(f"  SKIP {table}.{column} {key[1]}: 最近节点 {code} 距离 {d:.0f} m > "
                      f"{args.max_snap_meters:.0f} m，需人工在地图上确认")
                continue
            changed += 1
            print(f"  {table}.{column} {key[1]}: {current[key[1]] or '(null)'} -> {code} ({d:.0f} m)")
            if not args.dry_run:
                mysql(f"UPDATE {table} SET {column}='{code}' WHERE {key_col}='{key[1]}' AND deleted=0;")

    print(("--dry-run：" if args.dry_run else "")
          + f"改 {changed} 处 / 已正确 {unchanged} 处 / 超阈值跳过 {skipped} 处")
    return 0


if __name__ == "__main__":
    sys.exit(main())
