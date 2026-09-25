#!/usr/bin/env python3
"""§1.12 第②/④步用量的候选图覆盖探针（只读：不改库、不写 seed、不出 SQL）。

回答两个问题：
  1) §1.12 画的走廊/场站多边形里，OSM 到底有多少个图节点、连不连得上；
  2) 基地 → 各末端的**真实图上路程**是多少（有向 Dijkstra），和 §1.1-b 用的
     "直线 × 1.481 往返"差多少。

为什么要它：§1.1-b 的远端三行一直是"直线 × 绕行系数"估出来的，而 §1.6 路 A 一旦换图，
这个假设就该被实测替掉；同时 §1.8 的硬规矩是"站点必须吸附到图节点，否则恒 UNREACHABLE"，
所以末端能不能落库，取决于方块里有没有节点 —— 这两件事都不换图就能量。

用法：
  python scripts/geo/probe_corridor_coverage.py data/map.corridor.osm \
      --bbox 31.8994,121.0487,31.9793,121.1288 > reports/corridor-coverage.json

坐标口径：--bbox 是 **WGS84**（Overpass 查询框），而 ZONES/POI 全是高德 **GCJ-02**；
图节点在 osm_to_road_graph.load() 里已经换算成 GCJ-02，所以方块/POI 与节点比对是同一坐标系。
"""

from __future__ import annotations

import argparse
import io
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import osm_to_road_graph as G  # noqa: E402

# §1.12 的区块多边形（GCJ-02 角点，逐字抄自路线图表"一、范围结构"与其下坐标块）
ZONES: dict[str, tuple[str, object]] = {
    "L1 近场": ("box", ((121.0705, 31.9575), (121.0880, 31.9695))),
    "走廊 三星镇": ("poly", [(121.080068, 31.963000), (121.114609, 31.968804),
                        (121.115835, 31.963478), (121.081294, 31.957674)]),
    "走廊 物流港": ("poly", [(121.083604, 31.961400), (121.104484, 31.919473),
                        (121.098638, 31.917347), (121.077758, 31.959274)]),
    "走廊 川姜": ("poly", [(121.083699, 31.959490), (121.065298, 31.911603),
                       (121.059262, 31.913297), (121.077663, 31.961184)]),
    "场站 三星镇": ("box", ((121.111671, 31.963107), (121.118773, 31.969175))),
    "场站 物流港": ("box", ((121.098010, 31.915376), (121.105112, 31.921444))),
    "场站 川姜": ("box", ((121.058729, 31.909416), (121.065831, 31.915484))),
}

BASE = (121.081142, 31.960347)          # 江苏叠石桥物流有限公司（§1.12 枢纽点）
TERMINALS = {
    "三星镇中心": ((121.115222, 31.966141), 9834),
    "深国际物流港": ((121.101561, 31.918410), 14988),
    "川姜/志浩中心": ((121.062280, 31.912450), 16587),
    "叠石桥物流园区118栋": ((121.082150, 31.959850), None),
    "科创园2栋": ((121.087151, 31.963147), None),
    "壹加联盟1987创意园": ((121.073478, 31.960299), None),
}


def point_in_poly(pt, poly):
    x, y = pt
    inside = False
    j = len(poly) - 1
    for i in range(len(poly)):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / (yj - yi) + xi):
            inside = not inside
        j = i
    return inside


def in_box(pt, lo, hi):
    return lo[0] <= pt[0] <= hi[0] and lo[1] <= pt[1] <= hi[1]


def directed_dijkstra(adj, src, dst):
    import heapq
    dist = {src: 0.0}
    pq = [(0.0, src)]
    while pq:
        d, x = heapq.heappop(pq)
        if d > dist.get(x, 1e18):
            continue
        if x == dst:
            return d
        for y, w in adj[x]:
            nd = d + w
            if nd < dist.get(y, 1e18):
                dist[y] = nd
                heapq.heappush(pq, (nd, y))
    return None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("osm", type=Path)
    ap.add_argument("--bbox", required=True, help="minlat,minlng,maxlat,maxlng（WGS84）")
    ap.add_argument("--snap-meters", type=float, default=10.0)
    args = ap.parse_args()

    min_lat, min_lng, max_lat, max_lng = (float(v) for v in args.bbox.split(","))
    G.BBOX.update({"min_lng": min_lng, "max_lng": max_lng,
                   "min_lat": min_lat, "max_lat": max_lat})

    gcj, ways = G.load(args.osm)
    nodes, edges = G.build_graph(gcj, ways, args.snap_meters)
    pos = {n: gcj[n] for n in nodes}

    und = {n: set() for n in nodes}
    adj = {n: [] for n in nodes}
    for e in edges.values():
        und[e["from"]].add(e["to"])
        und[e["to"]].add(e["from"])
        adj[e["from"]].append((e["to"], e["length"]))
        if e["bidirectional"]:
            adj[e["to"]].append((e["from"], e["length"]))

    comp: dict[str, int] = {}
    cid = 0
    for s in nodes:
        if s in comp:
            continue
        cid += 1
        stack = [s]
        while stack:
            x = stack.pop()
            if x in comp:
                continue
            comp[x] = cid
            stack.extend(und[x])
    sizes: dict[int, int] = {}
    for c in comp.values():
        sizes[c] = sizes.get(c, 0) + 1
    big = max(sizes, key=sizes.get)

    def nearest(pt):
        best, bd = None, 1e18
        for n, p in pos.items():
            d = G.haversine(pt, p)
            if d < bd:
                best, bd = n, d
        return best, bd

    zones = {}
    for name, (kind, geo) in ZONES.items():
        hits = [n for n, p in pos.items() if (in_box(p, *geo) if kind == "box" else point_in_poly(p, geo))]
        zones[name] = {"nodes": len(hits), "in_largest_component": sum(1 for n in hits if comp[n] == big)}

    base_node, base_off = nearest(BASE)
    terminals = {}
    for name, (pt, assumed_round) in TERMINALS.items():
        tn, td = nearest(pt)
        route = directed_dijkstra(adj, base_node, tn)
        straight = G.haversine(BASE, pt)
        r600 = sum(1 for p in pos.values() if G.haversine(pt, p) <= 600)
        r1000 = sum(1 for p in pos.values() if G.haversine(pt, p) <= 1000)
        terminals[name] = {
            "nearest_node_m": round(td, 1),
            "nodes_within_600m": r600, "nodes_within_1000m": r1000,
            "reachable_from_base": route is not None,
            "route_one_way_m": None if route is None else round(route),
            "straight_m": round(straight),
            "route_over_straight": None if route is None else round(route / straight, 3),
            "assumed_round_trip_m": assumed_round,
            # 往返一律按"取整后的单程 ×2"，与文档/§1.1-b 的算式同序，避免出现 1 m 的两份口径
            "measured_round_trip_m": None if route is None else round(route) * 2,
        }

    out = {
        "source_osm": str(args.osm), "bbox_wgs84": args.bbox, "snap_meters": args.snap_meters,
        "nodes": len(nodes), "edges": len(edges),
        "components": len(sizes), "largest_component_nodes": sizes[big],
        "largest_component": {"id": big, "components": len(sizes), "nodes": len(nodes)},
        "base_node": {"id": base_node, "poi_offset_m": round(base_off, 1)},
        "zones": zones, "terminals": terminals,
    }
    io.open(sys.stdout.fileno(), "w", encoding="utf-8", closefd=False).write(
        json.dumps(out, ensure_ascii=False, indent=1) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
