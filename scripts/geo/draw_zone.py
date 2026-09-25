#!/usr/bin/env python3
"""§范围补齐：从真实路网节点生成可派单围栏 + 门市站点，直接"画"进库（授权：本人"帮我直接画上去"）。

不画矩形（§1.6 路线 C 禁止）：围栏顶点 = 片区 bbox 内**最大强连通分量**的 OSM 路口节点的凸包，
每个顶点都是一条真实街道的交叉口。站点吸附到片区内**离目标最近的可通行节点**（coord_x/y 取该节点），
保证新建点天然可达（不制造"有站点无接入点"的 UNREACHABLE）。

只读查询走 docker exec fsd-mysql；产出的 SQL 交 `--apply` 落库（默认只打印）。幂等 upsert（业务键
park_id+fence_code / park_id+station_code），与 back/sql/seed/zjf_geo.sql 同风格。坐标系 GCJ-02。
"""
from __future__ import annotations
import argparse, math, subprocess, sys

CONTAINER = "fsd-mysql"
DEFAULTS = dict(area="ZJF", status="ACTIVE", service_hours="06:00-22:00",
                capacity_limit=50, avg_service_seconds=180, station_confidence="C",
                service_direction="BIDIRECTIONAL",
                response_level="WARN", buffer_meters="15.00")

# 站点默认泊位/服务参数：取货/送货 180s、接驳 240s；容量按片区给足
TYPE_AVG = {"PICKUP": 180, "DROPOFF": 264, "GENERAL": 240}


def q(sql: str) -> list[list[str]]:
    out = subprocess.run(["docker", "exec", CONTAINER, "sh", "-c",
                          'exec mysql -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -D fsd_core -e ' + _q(sql)],
                         capture_output=True, text=True).stdout
    rows = []
    for line in out.splitlines():
        if line.strip() == "" or line.startswith("mysql:"):
            continue
        rows.append(line.split("\t"))
    return rows


def _q(s: str) -> str:
    return "'" + s + "'"


def nodes_in_bbox(minl, minla, maxl, maxla):
    sql = (f'SELECT n.node_code, n.coord_lng, n.coord_lat, n.coord_x, n.coord_y '
           f'FROM t_road_node n JOIN t_road_node_component c USING(node_code) '
           f'WHERE n.park_id=1 AND c.park_id=1 AND n.status="ACTIVE" AND n.deleted=0 AND c.is_largest=1 '
           f'AND n.coord_lng BETWEEN {minl} AND {maxl} AND n.coord_lat BETWEEN {minla} AND {maxla}')
    return [(r[0], float(r[1]), float(r[2]), float(r[3]), float(r[4])) for r in q(sql)]


def convex_hull(pts):
    """单调链，输入/输出 (lng,lat) 逆时针。"""
    p = sorted(set(pts))
    if len(p) < 3:
        return p
    def cross(o, a, b):
        return (a[0]-o[0])*(b[1]-o[1]) - (a[1]-o[1])*(b[0]-o[0])
    lo = []
    for x, y in p:
        while len(lo) >= 2 and cross(lo[-2], lo[-1], (x, y)) <= 0: lo.pop()
        lo.append((x, y))
    up = []
    for x, y in reversed(p):
        while len(up) >= 2 and cross(up[-2], up[-1], (x, y)) <= 0: up.pop()
        up.append((x, y))
    lo.pop(); up.pop()
    return lo + up


def area_km2(poly):
    if len(poly) < 3: return 0.0
    lat0 = sum(p[1] for p in poly)/len(poly)
    mx = lambda dlng: dlng*111320*math.cos(math.radians(lat0))
    my = lambda dlat: dlat*111320
    a = 0.0
    for i in range(len(poly)):
        x1, y1 = mx(poly[i][0]), my(poly[i][1]); x2, y2 = mx(poly[(i+1) % len(poly)][0]), my(poly[(i+1) % len(poly)][1])
        a += x1*y2 - x2*y1
    return abs(a)/2/1e6


def poly_json(poly):
    body = ",".join(f"[{lng:.6f},{lat:.6f}]" for lng, lat in poly)
    return "[" + body + "]"


def station_sql(code, name, stype, node, sort_order):
    ncode, lng, lat, x, y = node
    return (f"INSERT INTO t_station (park_id, station_code, station_name, station_type, coord_x, coord_y, "
            f"coord_lng, coord_lat, area, status, sort_order, capacity_limit, service_hours, avg_service_seconds, "
            f"anchor_node_code, service_direction, station_confidence, remark, deleted) "
            f"SELECT 1, '{code}', '{name}', '{stype}', {x:.4f}, {y:.4f}, {lng:.6f}, {lat:.6f}, "
            f"'{DEFAULTS['area']}', '{DEFAULTS['status']}', {sort_order}, {DEFAULTS['capacity_limit']}, "
            f"'{DEFAULTS['service_hours']}', {TYPE_AVG[stype]}, '{ncode}', '{DEFAULTS['service_direction']}', "
            f"'{DEFAULTS['station_confidence']}', "
            f"'演示夹具·路网节点吸附（fit_canvas 扩范围）', 0 "
            f"FROM DUAL ON DUPLICATE KEY UPDATE station_name=VALUES(station_name), station_type=VALUES(station_type), "
            f"coord_x=VALUES(coord_x), coord_y=VALUES(coord_y), coord_lng=VALUES(coord_lng), coord_lat=VALUES(coord_lat), "
            f"anchor_node_code=VALUES(anchor_node_code), avg_service_seconds=VALUES(avg_service_seconds), "
            f"status=VALUES(status), deleted=0;")


def fence_sql(code, name, poly):
    # 列清单里不带 `version`：V62 已物理删除该遗留列，带上会 ERROR 1054 整条失败
    # （export-geo-seed.sh:172 有同源的守门，这两条 INSERT 是它漏掉的一处）
    return (f"INSERT INTO t_park_geofence (park_id, fence_code, fence_name, fence_type, response_level, "
            f"buffer_meters, polygon_json, status, remark, deleted) "
            f"SELECT 1, '{code}', '{name}', 'BOUNDARY', '{DEFAULTS['response_level']}', '{DEFAULTS['buffer_meters']}', "
            f"'{poly_json(poly)}', 'ACTIVE', '路网节点凸包·§范围补齐自动描', 0 "
            f"FROM DUAL ON DUPLICATE KEY UPDATE fence_name=VALUES(fence_name), polygon_json=VALUES(polygon_json), "
            f"status=VALUES(status), deleted=0;")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fence-code", required=True)
    ap.add_argument("--fence-name", required=True)
    ap.add_argument("--bbox", required=True, help="minLng,minLat,maxLng,maxLat")
    # 可多次：CODE:名称:类型:目标lng:目标lat
    ap.add_argument("--station", action="append", default=[])
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()
    minl, minla, maxl, maxla = (float(x) for x in args.bbox.split(","))

    ns = nodes_in_bbox(minl, minla, maxl, maxla)
    if len(ns) < 3:
        print(f"[ERROR] 片区内最大SCC节点仅 {len(ns)} 个（<3），无法成面", file=sys.stderr); return 2
    hull = convex_hull([(n[1], n[2]) for n in ns])
    sqls = [fence_sql(args.fence_code, args.fence_name, hull)]
    print(f"# 节点 {len(ns)} 个 → 凸包 {len(hull)} 顶点，面积 {area_km2(hull):.3f} km²", file=sys.stderr)

    for spec in args.station:
        code, name, stype, tlng, tlat = spec.split(":")
        tlng, tlat = float(tlng), float(tlat)
        # 最近可通行节点（按经纬度近似米）
        def d(n):
            dx = (n[1]-tlng)*math.cos(math.radians(tlat))*111320; dy = (n[2]-tlat)*111320
            return dx*dx+dy*dy
        anchor = min(ns, key=d)
        dist = math.sqrt(d(anchor))
        print(f"# {code} 吸附 {anchor[0]}（离目标 {dist:.0f} m）", file=sys.stderr)
        sqls.append(station_sql(code, name, stype, anchor, 40+int(code[-2:])))

    text = "\n".join(sqls) + "\n"
    if args.apply:
        subprocess.run(["docker", "exec", "-i", CONTAINER, "sh", "-c",
                        'exec mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" -D fsd_core'],
                       input=text.encode("utf-8"), check=True)
        print("# 已落库", file=sys.stderr)
    else:
        print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
