#!/usr/bin/env python3
"""§10.2 第 5 问选 ② 的执行工具：用高德 driving 几何补末端最后一段，接到自建图上。

为什么要有这个脚本：三星镇场站框内已有 10 个图节点（全在最大 SCC），但
**物流港框 0 个、川姜框 0 个**（2026-09-23 实测）—— 末端 POI 离最近图节点
719.8 m / 392.9 m，按 250 m 吸附判据这两块地根本进不了服务范围。

口径纪律（§1.6 已按本次定案改掉）：高德几何**只当「这条路存在」的几何来源**，
不作为距离/时长口径 —— 生成的边一律 road_class='AMAP_LINK'，距离仍由 haversine
按节点算，产能/时效结论一律走自建图 A*。

可摘除性：产物单独写 back/sql/seed/zjf_amap_terminal_links.sql，节点码统一
AM{站}xx 前缀。要回到纯 OSM 态，删这个文件 + 两条 DELETE（见文件头注释）即可，
不碰任何 OSM 行。

坐标同源：coord_x/coord_y 用**从 t_road_node 反解出来的同一线性映射**算，
不硬编码常量 —— 与装载器 fit_canvas、application.yml、前端副本同源，
否则 isMetricConsistent 会翻 false、整园退回 Dijkstra。

默认只写 SQL 不落库；--apply 才写。只打本机 fsd-mysql。
"""
from __future__ import annotations
import argparse, json, math, pathlib, re, subprocess, sys, time
import urllib.error, urllib.parse, urllib.request

sys.stdout.reconfigure(encoding="utf-8")

CONTAINER = "fsd-mysql"
BASE_URL = "https://restapi.amap.com/v3/direction/driving"
UA = "DispatchFlow-geo-link/1.0"
OFF_GRAPH_M = 300.0     # 路径顶点离图超过这个距离，才算「缺的那一段」
MIN_SEG_M = 15.0        # 相邻顶点小于这个距离就合并，避免零长边
SPEED_KMH = 20          # 末端接入道按园区内部路保守取值；不作任何对外口径

# 末端：名称 -> (中心 lat, 中心 lng, 节点码前缀)
TERMINALS = {
    "物流港": (31.918410, 121.101561, "WL"),
    "川姜":   (31.912450, 121.062280, "CJ"),
}


def mysql_rows(sql: str) -> list[list[str]]:
    p = subprocess.run(["docker", "exec", "-i", CONTAINER, "sh", "-c",
                        'exec mysql --default-character-set=utf8mb4 -N -B -uroot '
                        '-p"$MYSQL_ROOT_PASSWORD" -D fsd_core'],
                       input=sql, capture_output=True, text=True, encoding="utf-8")
    if p.returncode != 0:
        raise SystemExit(f"mysql 失败：{p.stderr.strip()[:300]}")
    return [l.split("\t") for l in p.stdout.splitlines() if l.strip() and not l.startswith("mysql:")]


def hav(a, b):
    R = 6371000.0
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp, dl = p2 - p1, math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))


def load_graph_nodes():
    """最大强连通分量里的 ACTIVE 节点：[((lat,lng), node_code), ...]"""
    rows = mysql_rows(
        "SELECT n.coord_lat, n.coord_lng, n.node_code FROM t_road_node n "
        "JOIN t_road_node_component c ON c.node_code=n.node_code AND c.park_id=n.park_id "
        "WHERE n.deleted=0 AND n.status='ACTIVE' AND c.is_largest=1 AND n.coord_lng IS NOT NULL;")
    return [((float(r[0]), float(r[1])), r[2]) for r in rows if len(r) >= 3]


def fit_affine():
    """反解 px = a*lng+b / py = c*lat+d。映射本是线性的，残差应为 0。"""
    row = mysql_rows(
        "SELECT (COUNT(*)*SUM(coord_lng*coord_x)-SUM(coord_lng)*SUM(coord_x))"
        "        /(COUNT(*)*SUM(coord_lng*coord_lng)-POW(SUM(coord_lng),2)),"
        "       (SUM(coord_x)-((COUNT(*)*SUM(coord_lng*coord_x)-SUM(coord_lng)*SUM(coord_x))"
        "        /(COUNT(*)*SUM(coord_lng*coord_lng)-POW(SUM(coord_lng),2)))*SUM(coord_lng))/COUNT(*),"
        "       (COUNT(*)*SUM(coord_lat*coord_y)-SUM(coord_lat)*SUM(coord_y))"
        "        /(COUNT(*)*SUM(coord_lat*coord_lat)-POW(SUM(coord_lat),2)),"
        "       (SUM(coord_y)-((COUNT(*)*SUM(coord_lat*coord_y)-SUM(coord_lat)*SUM(coord_y))"
        "        /(COUNT(*)*SUM(coord_lat*coord_lat)-POW(SUM(coord_lat),2)))*SUM(coord_lat))/COUNT(*) "
        "FROM t_road_node WHERE deleted=0 AND status='ACTIVE' AND coord_lng IS NOT NULL;")[0]
    return tuple(float(x) for x in row[:4])


def read_key() -> str:
    env = pathlib.Path(__file__).resolve().parents[2] / ".env"
    if not env.exists():
        raise SystemExit(".env 不存在，无法取 key（不猜）")
    for line in env.read_text(encoding="utf-8", errors="replace").splitlines():
        m = re.match(r"\s*FSD_AMAP_WEB_SERVICE_KEY\s*=\s*(\S+)", line)
        if m:
            return m.group(1)
    raise SystemExit(".env 里没有 FSD_AMAP_WEB_SERVICE_KEY")


def fetch(params, key):
    q = urllib.parse.urlencode({"key": key, **params})
    req = urllib.request.Request(f"{BASE_URL}?{q}", headers={"User-Agent": UA})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=20) as r:
                body = json.loads(r.read().decode("utf-8"))
        except urllib.error.URLError as e:
            print(f"  网络失败 {e}，退避重试", file=sys.stderr)
            time.sleep(3 * (attempt + 1))
            continue
        info = str(body.get("infocode") or "")
        if info == "10000":
            return body
        if info == "10021":
            print("  CUQPS 限流，退避", file=sys.stderr)
            time.sleep(2.5 * (attempt + 1))
            continue
        raise SystemExit(f"高德返回 infocode={info} info={body.get('info')}")
    raise SystemExit("重试耗尽")


def route_verts(origin, dest, key):
    """高德 driving 折线，统一成 (lat,lng)。不传 strategy ⇒ 与生产默认策略一致。"""
    body = fetch({"origin": f"{origin[1]:.6f},{origin[0]:.6f}",
                  "destination": f"{dest[1]:.6f},{dest[0]:.6f}",
                  "extensions": "base"}, key)
    paths = (body.get("route") or {}).get("paths") or []
    if not paths:
        return []
    verts = []
    for step in (paths[0].get("steps") or []):
        for pair in (step.get("polyline") or "").split(";"):
            ll = pair.split(",")
            if len(ll) >= 2:
                try:
                    verts.append((float(ll[1]), float(ll[0])))
                except ValueError:
                    pass
    return verts


def build_link(name, center, prefix, nodes, key):
    pos = [p for p, _ in nodes]
    attach_pt, d0 = min(((p, hav(center, p)) for p, _ in nodes), key=lambda t: t[1])
    print(f"\n=== {name}：中心离最近最大SCC节点 {d0:.1f} m（接入点 "
          f"{next(c for p, c in nodes if p == attach_pt)}）===")
    if d0 <= OFF_GRAPH_M:
        print("  已在图上（≤300 m），不需要补几何 —— 跳过")
        return []

    verts = route_verts(attach_pt, center, key)
    if not verts:
        print("  高德没给路径，跳过", file=sys.stderr)
        return []
    dists = [min(hav(v, q) for q in pos) for v in verts]
    k = next((i for i, dd in enumerate(dists) if dd > OFF_GRAPH_M), None)
    if k is None:
        print(f"  整条路径都贴在图上（最大偏离 {max(dists):.0f} m）⇒ OSM 有这条路，不用补")
        return []

    # 挂点用「最后一个仍在图上的顶点」的最近节点，别从中心硬连
    anchor_pt = verts[max(k - 1, 0)]
    anchor_code, _ = min(((c, hav(anchor_pt, p)) for p, c in nodes), key=lambda t: t[1])

    chain = list(verts[k:])
    if hav(chain[-1], center) > MIN_SEG_M:
        chain.append(center)
    ded = [chain[0]]
    for v in chain[1:]:
        if hav(ded[-1], v) >= MIN_SEG_M:
            ded.append(v)
    if hav(ded[-1], center) >= MIN_SEG_M:
        ded.append(center)
    else:
        ded[-1] = center
    length = hav(anchor_pt, ded[0]) + sum(hav(ded[i], ded[i + 1]) for i in range(len(ded) - 1))
    print(f"  缺的一段：自第 {k}/{len(verts)} 个顶点起 {len(ded)} 点 / 约 {length:.0f} m，"
          f"挂到既有节点 {anchor_code}")
    return emit(ded, anchor_code, anchor_pt, prefix, nodes)


def emit(chain, attach_code, attach_pt, prefix, nodes):
    a, b, c, d = fit_affine()
    sqls, codes = [], []
    for i, pt in enumerate(chain, start=1):
        code = f"AM{prefix}{i:02d}"
        codes.append(code)
        lng, lat = pt[1], pt[0]
        px, py = a * lng + b, c * lat + d
        sqls.append(
            "INSERT INTO t_road_node (park_id,node_code,coord_x,coord_y,coord_lng,coord_lat,status,deleted) "
            f"SELECT 1,'{code}',{px:.4f},{py:.4f},{lng:.7f},{lat:.7f},'ACTIVE',0 "
            "FROM DUAL ON DUPLICATE KEY UPDATE coord_x=VALUES(coord_x),coord_y=VALUES(coord_y),"
            "coord_lng=VALUES(coord_lng),coord_lat=VALUES(coord_lat),status='ACTIVE',deleted=0;")
    prev_code, prev_pt = attach_code, attach_pt
    for code, pt in zip(codes, chain):
        sqls.append(_seg(prev_code, code, hav(prev_pt, pt), [prev_pt, pt]))
        prev_code, prev_pt = code, pt
    return sqls


def _seg(frm, to, length, pts):
    # t_road_segment 没有 length 列 —— 里程一律由两端节点的 haversine 现算（见 ParkRoadGraph.edgeCost），
    # 所以这里不能写 length_meters（会 1054 整条失败）。列集合与 osm_to_road_graph.py 保持一致。
    _ = length
    # ⚠ 这里从前写死的是 `"coordinates":[]` —— 空折线会被 `RoadRouteResult.isForbiddenFallback()`
    #   判成"空折线＝禁行"，让 W2-b 门禁因为**错的理由**拒掉这 16 条边，也会让路线体检画不出线。
    #   这条边本来就是相邻两个链点，几何就是这两端 —— 如实写。
    #   形态与 osm_to_road_graph.py 逐字对齐（GeoJSON 对象、双引号**不带**反斜杠转义、7 位小数）：
    #   原来那串 `\"` 只在 MySQL 默认开启反斜杠转义时才会塌成 `"`，同一列里两种写法不该并存。
    gj = json.dumps({"type": "LineString",
                     "coordinates": [[round(p[1], 7), round(p[0], 7)] for p in pts]},
                    ensure_ascii=False, separators=(",", ":"))
    return ("INSERT INTO t_road_segment (park_id,from_node_code,to_node_code,direction,status,"
            "speed_limit_kmh,road_class,polyline_geojson,access_state,deleted) "
            f"SELECT 1,'{frm}','{to}','BIDIRECTIONAL','ACTIVE',{SPEED_KMH},'AMAP_LINK',"
            f"'{gj}','DRIVABLE',0 "
            "FROM DUAL ON DUPLICATE KEY UPDATE direction=VALUES(direction),status=VALUES(status),"
            "speed_limit_kmh=VALUES(speed_limit_kmh),road_class=VALUES(road_class),"
            "polyline_geojson=VALUES(polyline_geojson),access_state=VALUES(access_state),deleted=0;")


HEADER = (
    "-- 由 scripts/geo/amap_terminal_link.py 生成（§10.2 第 5 问定案 ②）\n"
    "-- 高德 driving 几何仅作「这条路存在」的几何来源，不作距离/时长口径；road_class='AMAP_LINK'。\n"
    "-- 许可：这一段不是 ODbL，随公开仓库分发前请确认高德条款；摘除方式见下两行。\n"
    "--   DELETE FROM t_road_segment WHERE road_class='AMAP_LINK';\n"
    "--   DELETE FROM t_road_node WHERE node_code LIKE 'AM%';\n")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--out", default="back/sql/seed/zjf_amap_terminal_links.sql")
    args = ap.parse_args()
    nodes = load_graph_nodes()
    key = read_key()
    all_sql = []
    for name, (lat, lng, prefix) in TERMINALS.items():
        all_sql += build_link(name, (lat, lng), prefix, nodes, key)
        time.sleep(1.2)
    if not all_sql:
        print("\n没有任何需要补的末端。")
        return 0
    text = HEADER + "\n".join(all_sql) + "\n"
    pathlib.Path(args.out).write_text(text, encoding="utf-8")
    print(f"\n写出 {args.out}：{len(all_sql)} 条语句")
    if args.apply:
        p = subprocess.run(["docker", "exec", "-i", CONTAINER, "sh", "-c",
                            'exec mysql --default-character-set=utf8mb4 -uroot '
                            '-p"$MYSQL_ROOT_PASSWORD" -D fsd_core'],
                           input=text, capture_output=True, text=True, encoding="utf-8")
        if p.returncode:
            print(f"落库失败：{p.stderr[:400]}", file=sys.stderr)
            return 1
        print("已落库")
    return 0


if __name__ == "__main__":
    sys.exit(main())
