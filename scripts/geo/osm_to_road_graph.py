#!/usr/bin/env python3
"""OSM -> 园区路网图（§M2E / §1.5 / §1.6 路 A）。

路线图 §1.6 写的是"跑既有工具链入库：osm_to_pilot_geo.py -> pilot_osm_geo.json -> 节点/路段入库"。
实测这条链**只到 JSON 为止**：`osm_to_pilot_geo.py` 产出的是折线数组给运行时
（`OsmPilotGeoRepository` / `PilotGridRoads`）画图用，而 `t_road_node` / `t_road_segment`
的行是 V38 里**手写的 5x5 网格交叉口**（备注写着"西排路×金洲大道"），不是 OSM 拓扑。
所以扩范围必须先有一个真正的 OSM->图 装载器，本脚本就是它。

做法：
  * 只取 target bbox 内、且在 ROAD_CLASS_SPEED 表里的 highway way，并按 bbox **裁断**成框内连续段
    （`*_link`/`trunk` 排除：那是城市快速路接口，园区短驳不该走，实测 bbox 外还有 trunk）。
  * 节点 = 度数 != 2 的 OSM 节点（交叉口与端点）。度数为 2 的纯形状点被折叠进边的
    polyline_geojson —— 这正是 §1.5 要的"124/124 路段无 polyline"的修法。
  * 像素坐标沿用 V38 的线性映射 `x=(lng-121.072)*77000, y=(31.9645-lat)*150000`，
    并报告有多少点落在示意画布外（扩范围必然要和 §6.4 的画布/硬编码一起改）。
  * 输出是按业务键幂等的 upsert SQL，可直接灌进库；灌完由 export-geo-seed.sh 并入 seed。

用法：
  python scripts/geo/osm_to_road_graph.py data/map.expanded.osm \
      --out back/sql/seed/zjf_road_network.sql [--park-id 1] [--report-only]
"""

from __future__ import annotations

import argparse
import heapq
import json
import math
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

# 与 V38 一致的示意像素映射（x/y 只用于前端上图与 nearestNode 吸附，距离一律走 GPS）。
# 仅在 --legacy-canvas 下使用；默认走 fit_canvas() 从实际 GCJ 外接框算出的、保证 0 点越界的映射。
PIXEL_LNG_ORIGIN = 121.072
PIXEL_LAT_ORIGIN = 31.9645
PIXEL_PER_DEG_LNG = 77_000.0
PIXEL_PER_DEG_LAT = 150_000.0
LEGACY_CANVAS = (1300, 800)
METERS_PER_DEG_LAT = 111_320.0

# 园区短驳可用的道路等级 -> 限速 km/h（§0.2 实测限速构成里只有 SECONDARY/ARTERIAL/SERVICE_ROAD 三档，
# 这里按 OSM 等级展开；均速会由实测图重新算出来，不再沿用 §1.1 的加权假设）
ROAD_CLASS_SPEED = {
    "primary": 30,
    "secondary": 25,
    "tertiary": 20,
    "unclassified": 15,
    "residential": 15,
    "living_street": 10,
    "service": 10,
}

# 提取框，WGS84（就是当初 Overpass 查询用的那一组数，§1.6）；入库坐标是 GCJ-02，两者不可混比
BBOX = {"min_lng": 121.0680, "max_lng": 121.0905, "min_lat": 31.9550, "max_lat": 31.9715}


def wgs84_to_gcj02(lng: float, lat: float) -> tuple[float, float]:
    """与 scripts/carla/osm_to_pilot_geo.py 同一套偏移公式；库内坐标系是 GCJ-02。"""
    a = 6378245.0
    ee = 0.00669342162296594323

    def t_lat(x: float, y: float) -> float:
        r = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * math.sqrt(abs(x))
        r += (20.0 * math.sin(6.0 * x * math.pi) + 20.0 * math.sin(2.0 * x * math.pi)) * 2.0 / 3.0
        r += (20.0 * math.sin(y * math.pi) + 40.0 * math.sin(y / 3.0 * math.pi)) * 2.0 / 3.0
        r += (160.0 * math.sin(y / 12.0 * math.pi) + 320 * math.sin(y * math.pi / 30.0)) * 2.0 / 3.0
        return r

    def t_lng(x: float, y: float) -> float:
        r = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * math.sqrt(abs(x))
        r += (20.0 * math.sin(x * math.pi) + 40.0 * math.sin(x / 3.0 * math.pi)) * 2.0 / 3.0
        r += (150.0 * math.sin(x / 12.0 * math.pi) + 300 * math.sin(x / 30.0 * math.pi)) * 2.0 / 3.0
        return r

    if not (72 <= lng <= 137.7 and 0.29 <= lat <= 55.83):
        return lng, lat
    dlat, dlng = t_lat(lng - 105.0, lat - 35.0), t_lng(lng - 105.0, lat - 35.0)
    radlat = lat / 180.0 * math.pi
    magic = 1 - ee * math.sin(radlat) ** 2
    sqrtmagic = math.sqrt(magic)
    dlat = (dlat * 180.0) / ((a * (1 - ee)) / (magic * sqrtmagic) * math.pi)
    dlng = (dlng * 180.0) / (a / sqrtmagic * math.cos(radlat) * math.pi)
    return round(lng + dlng, 7), round(lat + dlat, 7)


def haversine(a: tuple[float, float], b: tuple[float, float]) -> float:
    r = 6_371_000.0
    p1, p2 = math.radians(a[1]), math.radians(b[1])
    dp = p2 - p1
    dl = math.radians(b[0] - a[0])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def in_bbox(pt: tuple[float, float]) -> bool:
    """判定用的是 WGS84 原始坐标 —— BBOX 就是当初 Overpass 查询框，两个坐标系不能混。"""
    return (BBOX["min_lng"] <= pt[0] <= BBOX["max_lng"]) and (BBOX["min_lat"] <= pt[1] <= BBOX["max_lat"])


def fit_canvas(points: list[tuple[float, float]], target_width: int = 1600, pad: int = 40) -> dict:
    """从节点 GCJ 外接框算出一张「米制各向同性、且保证 0 点越界」的示意画布映射（§范围补齐）。

    线性模型（与后端 {@code ParkGeoTransformService} 的仿射同型，可直接互推）：
        x = (lng - X0) * SX      y = (Y0 - lat) * SY
        lng = X0 + x/SX          lat = Y0 - y/SY          （X0/Y0 = 画布左上角）

    与旧 V38 常数映射（77000/150000，宽高比 1.653 拉伸）不同，这里 SX/SY 由外接框反解并强制
    米制各向同性（SY = SX / cos(lat_mid)），所以画布高度按真实南北比长出来，扩范围后节点不再溢出。
    """
    if not points:
        raise ValueError("fit_canvas needs at least one point")
    lngs = [p[0] for p in points]
    lats = [p[1] for p in points]
    lng_min, lng_max, lat_min, lat_max = min(lngs), max(lngs), min(lats), max(lats)
    lng_span = max(lng_max - lng_min, 1e-9)
    lat_span = max(lat_max - lat_min, 1e-9)
    lat_mid = (lat_min + lat_max) / 2.0
    m_per_deg_lng = METERS_PER_DEG_LAT * math.cos(math.radians(lat_mid))

    W = target_width
    usable_w = W - 2 * pad
    sx = usable_w / lng_span                     # px / deg-lng
    sy = sx * (METERS_PER_DEG_LAT / m_per_deg_lng)  # 米制各向同性：px/deg-lat
    usable_h = sy * lat_span
    H = int(round(usable_h)) + 2 * pad

    x0 = lng_min - pad / sx
    y0 = lat_max + pad / sy

    # 后端仿射 lng = a*x + b*y + c / lat = d*x + e*y + f 的系数（b=d=0，本映射无旋转）
    affine = {"a": 1.0 / sx, "b": 0.0, "c": x0, "d": 0.0, "e": -1.0 / sy, "f": y0}
    # 供 branch-B（anchor+metres）与 t_park 使用，两套算法互洽
    anchor_lng = x0 + (W / 2.0) / sx
    anchor_lat = y0 - (H / 2.0) / sy
    width_meters = W * (m_per_deg_lng / sx)
    height_meters = H * (METERS_PER_DEG_LAT / sy)
    corners = []
    for cx, cy in ((pad, pad), (W - pad, pad), (pad, H - pad), (W - pad, H - pad)):
        corners.append({"x": cx, "y": cy,
                        "lng": round(x0 + cx / sx, 7), "lat": round(y0 - cy / sy, 7)})
    return {"canvas_w": W, "canvas_h": H, "pad": pad,
            "x0": x0, "y0": y0, "sx": sx, "sy": sy,
            "lng_span": lng_span, "lat_span": lat_span,
            "affine": affine, "reference_points": corners,
            "anchor_lng": round(anchor_lng, 7), "anchor_lat": round(anchor_lat, 7),
            "park_width_meters": round(width_meters, 1), "park_height_meters": round(height_meters, 1)}


def load(osm_path: Path):
    """-> (osm_id -> GCJ02 点, [(way_id, highway, oneway, [osm_id...])])

    way 必须按提取框**裁断**：Overpass 返回的是整条 way，实测"只要有一个节点在框内就整条保留"
    会带进 28% 的框外节点、最远的离框 2.5 km —— 那不是"扩范围"，是把城市路网灌成了园区路网。
    这里把每条 way 拆成框内的连续段（一条路进出提取框各生成一段），框外部分丢弃。
    """
    root = ET.parse(osm_path).getroot()
    raw = {n.get("id"): (float(n.get("lon")), float(n.get("lat"))) for n in root if n.tag == "node"}
    ways = []
    for w in root:
        if w.tag != "way":
            continue
        tags = {t.get("k"): t.get("v") for t in w.findall("tag")}
        highway = tags.get("highway")
        if highway not in ROAD_CLASS_SPEED:
            continue
        oneway = str(tags.get("oneway", "")).lower()
        run: list[str] = []
        for ref in [r.get("ref") for r in w.findall("nd")] + [None]:  # None 是收尾哨兵
            if ref is not None and ref in raw and in_bbox(raw[ref]):
                run.append(ref)
            else:
                if len(run) >= 2:
                    ways.append((w.get("id"), highway, oneway, list(run)))
                run = []
    used = {r for _way in ways for r in _way[3]}
    gcj = {k: wgs84_to_gcj02(*raw[k]) for k in used}
    return gcj, ways


def snap(gcj: dict[str, tuple[float, float]], ids, meters: float) -> dict[str, str]:
    """把相距 <= meters 的节点并成一个（OSM 里两条 way 近似相交却各有节点，是图碎裂的主因）。

    网格索引：cell 边长按 meters 换算成度（纬度 111320 m/deg，经度按 cos(lat) 收窄），
    只在 3x3 邻域内做精确 haversine 判定 + 并查集合并。
    """
    parent = {i: i for i in ids}

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            # 规范 id 取字典序小者，保证多次运行结果一致
            keep, drop = (ra, rb) if ra < rb else (rb, ra)
            parent[drop] = keep

    if meters <= 0:
        return {i: i for i in ids}

    lat_deg = meters / 111_320.0
    lng_deg = meters / (111_320.0 * math.cos(math.radians(31.96)))
    grid: dict[tuple[int, int], list[str]] = defaultdict(list)
    for i in ids:
        lng, lat = gcj[i]
        grid[(int(lng / lng_deg), int(lat / lat_deg))].append(i)

    for cell, members in grid.items():
        near = []
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                near.extend(grid.get((cell[0] + dx, cell[1] + dy), []))
        for a in members:
            for b in near:
                if a < b and haversine(gcj[a], gcj[b]) <= meters:
                    union(a, b)
    return {i: find(i) for i in ids}


def build_graph(gcj, ways, snap_meters: float):
    """把 OSM way 压成"交叉口节点 + 带形状点的边"。

    度数按**去重后的无向邻接点数**算（不是"出现在几条 way 里"）：一条 way 的内部点度数为 2，
    会被折进 polyline；只有交叉口、端点、以及道路等级/oneway 突变点才成为图节点。
    通行方向逐步判定：非单向 way 的两个方向都放行，单向 way 只放行 way 本身的走向。
    """
    raw_ids = {r for _w, _h, _o, refs in ways for r in refs}
    canon = snap(gcj, raw_ids, snap_meters)

    allowed: set[tuple[str, str]] = set()      # 有向可通行步
    attrs: dict[tuple[str, str], list[tuple[str, bool]]] = defaultdict(list)
    undirected: dict[str, set[str]] = defaultdict(set)
    for _wid, hw, oneway, refs in ways:
        seq = []
        for r in refs:
            c = canon[r]
            if not seq or seq[-1] != c:
                seq.append(c)
        if len(seq) < 2:
            continue
        one = oneway in ("yes", "1", "true")
        for a, b in zip(seq, seq[1:]):
            undirected[a].add(b)
            undirected[b].add(a)
            attrs[(a, b)].append((hw, one))
            allowed.add((a, b))
            if not one:
                attrs[(b, a)].append((hw, one))
                allowed.add((b, a))

    junctions = {n for n, nb in undirected.items() if len(nb) != 2}
    # 道路等级或单向属性突变的点也必须断开，否则一条边会横跨两种限速
    for (a, _b), a_list in attrs.items():
        if len(set(a_list)) > 1:
            junctions.add(a)

    edges: dict[tuple[str, str], dict] = {}
    for start in sorted(junctions):
        for first in sorted(n for n in undirected[start] if (start, n) in allowed):
            prev, cur = start, first
            pts = [gcj[start]]
            steps_taken: list[tuple[str, str]] = []
            while True:
                steps_taken.append((prev, cur))
                pts.append(gcj[cur])
                nxts = [n for n in undirected[cur] if n != prev and (cur, n) in allowed]
                if cur in junctions or not nxts:
                    break
                if set(attrs[(cur, nxts[0])]) != set(attrs[(prev, cur)]):
                    break
                prev, cur = cur, nxts[0]
            if start == cur or len(pts) < 2 or not steps_taken:
                continue
            hw = max((attrs[s][0][0] for s in steps_taken), key=lambda h: ROAD_CLASS_SPEED.get(h, 0))
            one_way = any((b, a) not in allowed for a, b in steps_taken)
            length = sum(haversine(pts[i], pts[i + 1]) for i in range(len(pts) - 1))
            # 无向规范化：t_road_segment 一行 BIDIRECTIONAL 就会被 ParkRoadGraph 展开成两个方向，
            # 所以 (a,b) 与 (b,a) 必须合成一行，否则边数与总里程都会翻倍
            key = (start, cur) if one_way else tuple(sorted((start, cur)))
            rec = edges.get(key)
            if rec is not None and (rec["length"] <= length or not one_way):
                continue
            edges[key] = {"from": key[0], "to": key[1], "polyline": pts, "length": length,
                          "highway": hw, "bidirectional": not one_way}
    nodes = sorted({n for e in edges.values() for n in (e["from"], e["to"])})
    return nodes, edges


def _arc_positions(pts: list[tuple[float, float]]) -> list[float]:
    """折线各顶点的累计弧长（米），长度与 pts 一致、首项为 0。"""
    cum = [0.0]
    for i in range(1, len(pts)):
        cum.append(cum[-1] + haversine(pts[i - 1], pts[i]))
    return cum


def _point_at_arc(pts: list[tuple[float, float]], cum: list[float], dist: float) -> tuple[float, float]:
    """折线上从起点量 `dist` 米处的点（所在段内线性内插）。"""
    total = cum[-1]
    if dist <= 0:
        return pts[0]
    if dist >= total:
        return pts[-1]
    for i in range(1, len(pts)):
        if cum[i] >= dist:
            span = cum[i] - cum[i - 1]
            f = 0.0 if span <= 1e-9 else (dist - cum[i - 1]) / span
            return (pts[i - 1][0] + f * (pts[i][0] - pts[i - 1][0]),
                    pts[i - 1][1] + f * (pts[i][1] - pts[i - 1][1]))
    return pts[-1]


def _slice_polyline(pts: list[tuple[float, float]], cum: list[float],
                    s: float, e: float) -> list[tuple[float, float]]:
    """弧长区间 [s,e] 之间的那段折线：两端切点 + 中间**原样保留的 OSM 顶点**。

    ⚠ 这一段是这条改动的要害：若子边只留两端点，拆边就会把 §13.90 刚接进图里的形状点**拆没了**
      （实测过这个错：`edges_with_shape_points` 从 342 掉到 182、总里程少 0.6%）。
      拆长的目的从来不是把线改成弦，而是在线上多打几个可吸附的锚点。
    """
    out = [_point_at_arc(pts, cum, s)]
    out.extend(p for p, d in zip(pts[1:-1], cum[1:-1]) if s < d < e)
    out.append(_point_at_arc(pts, cum, e))
    return out


def split_long_edges(nodes: list[str], edges: dict, gcj: dict, max_meters: float):
    """把超过 `max_meters` 的边沿折线等弧长拆短，切点成为新的图节点。

    为什么要拆：吸附候选就是图节点，边越长 ⇒ 节点越稀。实测 633 条 ACTIVE 边里 **189 条 >400 m**、
    中位最近节点 245 m / P90 672 m ⇒ R=250 m 只吸得到 32.2 km²。拆短同时把可吸附面积顶上去，
    也让 A* 的分辨率变细。

    ⚠ 切点落在**已有折线**上，所以这条改的是"节点稀"，**不是**"线不贴路"：
      一条本来只有两端的 2,740 m 直线边拆完还是一串共线点。想让线真的有形状，
      靠的是 OSM 那条 way 本来有没有打中间顶点（§13.89 量的就是这件事：275 条没有）。

    ⚠ 新节点一律用 `OSMS` 前缀、且**不参与原有节点的编号**：`anchor_node_code` 到处指着
      `OSM0017` 这类代码（站点/泊位/充电桩/§0.2 那 35 个换电柜），编号一移位它们全部失效。
    """
    if max_meters <= 0:
        return nodes, edges, gcj, 0
    new_nodes = list(nodes)
    new_gcj = dict(gcj)
    new_edges: dict[tuple[str, str], dict] = {}
    synthetic = 0
    for (a, b), e in edges.items():
        pts = [tuple(p) for p in e["polyline"]]
        cum = _arc_positions(pts)
        # 一律用折线自身的弧长，不用 e["length"]：两者在 build_graph 里同源，
        # 但拆边之后子边长度必须逐段可加，混用会让总里程对不上账。
        length = cum[-1]
        parts = int(math.ceil(length / max_meters)) if length > 0 else 1
        if parts <= 1 or len(pts) < 2:
            new_edges[(a, b)] = e
            continue
        step = length / parts
        # 先排好 a 与 b 之间的一串切点（第 parts 个就是 b 本身，不再另造节点）
        cuts = []
        for k in range(1, parts):
            key = f"S{a}-{b}-{k}"
            cuts.append((key, _point_at_arc(pts, cum, k * step)))
            new_gcj[key] = cuts[-1][1]
            synthetic += 1
        marks = [(a, 0.0)] + [(key, k * step) for k, (key, _) in enumerate(cuts, start=1)] + [(b, length)]
        for (ka, sa), (kb, sb) in zip(marks, marks[1:]):
            seg_pts = _slice_polyline(pts, cum, sa, sb)
            sub_len = sum(haversine(seg_pts[i], seg_pts[i + 1]) for i in range(len(seg_pts) - 1))
            key = (ka, kb) if not e["bidirectional"] else tuple(sorted((ka, kb)))
            prev = new_edges.get(key)
            if prev is not None and prev["length"] >= sub_len:
                continue
            new_edges[key] = {"from": key[0], "to": key[1], "polyline": seg_pts,
                              "length": sub_len, "highway": e["highway"],
                              "bidirectional": e["bidirectional"]}
        new_nodes.extend(key for key, _ in cuts)
    return new_nodes, new_edges, new_gcj, synthetic


def components(nodes, edges):
    """无向连通分量，返回 (分量数, 各分量大小降序, 最大分量占比%)。"""
    adj = defaultdict(set)
    for e in edges.values():
        adj[e["from"]].add(e["to"])
        adj[e["to"]].add(e["from"])
    seen, sizes = set(), []
    for s in nodes:
        if s in seen:
            continue
        stack, n = [s], 0
        while stack:
            x = stack.pop()
            if x in seen:
                continue
            seen.add(x)
            n += 1
            stack.extend(adj[x])
        sizes.append(n)
    sizes.sort(reverse=True)
    total = len(nodes) or 1
    return len(sizes), sizes[:5], round(100.0 * (sizes[0] if sizes else 0) / total, 1)


def detour_factor(nodes, edges, gcj: dict[str, tuple[float, float]], min_straight_m: float = 200.0) -> dict:
    """实测绕行系数 = 路网最短路 / 直线距离，在最大连通分量的全部可通行点对上取统计。

    §1.1 的 1.3 是**假设值**，M2 闸门要求它转实测。距离权重与运行时一致（GPS 大圆），方向按边的
    bidirectional 展开成有向图。丢弃直线 <200 m 的点对：短距比值方差极大，不代表行车路径。
    """
    adj: dict[str, list[tuple[str, float]]] = defaultdict(list)
    und: dict[str, set[str]] = defaultdict(set)
    for e in edges.values():
        adj[e["from"]].append((e["to"], e["length"]))
        und[e["from"]].add(e["to"])
        und[e["to"]].add(e["from"])
        if e["bidirectional"]:
            adj[e["to"]].append((e["from"], e["length"]))

    seen, largest = set(), []
    for s in nodes:
        if s in seen:
            continue
        stack, comp = [s], []
        while stack:
            x = stack.pop()
            if x in seen:
                continue
            seen.add(x)
            comp.append(x)
            stack.extend(und[x])
        if len(comp) > len(largest):
            largest = comp

    ratios = []
    for src in largest:
        dist = {src: 0.0}
        pq = [(0.0, src)]
        while pq:
            d, x = heapq.heappop(pq)
            if d > dist.get(x, math.inf):
                continue
            for y, w in adj[x]:
                if d + w < dist.get(y, math.inf):
                    dist[y] = d + w
                    heapq.heappush(pq, (d + w, y))
        for dst, d in dist.items():
            straight = haversine(gcj[src], gcj[dst])
            if straight >= min_straight_m:
                ratios.append(d / straight)
    ratios.sort()
    if not ratios:
        return {"detour_pairs": 0}
    return {
        "detour_pairs": len(ratios),
        "detour_factor_mean": round(sum(ratios) / len(ratios), 3),
        "detour_factor_median": round(ratios[len(ratios) // 2], 3),
        "detour_factor_p90": round(ratios[int(len(ratios) * 0.9) - 1], 3),
    }


def emit_sql(nodes, edges, gcj, park_id: int, out: Path | None, extra_stats: dict | None = None,
             fit: dict | None = None) -> dict:
    node_code = {}
    # 原 OSM 节点的编号必须与拆边前逐字一致：anchor_node_code 到处指着 OSM0017 这类代码。
    originals = [n for n in nodes if not str(n).startswith("S")]
    for i, n in enumerate(originals, start=1):
        node_code[n] = f"OSM{i:04d}"
    for i, n in enumerate([n for n in nodes if str(n).startswith("S")], start=1):
        node_code[n] = f"OSMS{i:04d}"
    if fit is not None:
        x0, y0, sx, sy = fit["x0"], fit["y0"], fit["sx"], fit["sy"]
        cw, ch = fit["canvas_w"], fit["canvas_h"]
    else:
        x0, y0 = PIXEL_LNG_ORIGIN, PIXEL_LAT_ORIGIN
        sx, sy = PIXEL_PER_DEG_LNG, PIXEL_PER_DEG_LAT
        cw, ch = LEGACY_CANVAS
    out_of_canvas = 0
    stmts = []
    for n in nodes:
        lng, lat = gcj[n]
        x = (lng - x0) * sx
        y = (y0 - lat) * sy
        if x < 0 or y < 0 or x > cw or y > ch:
            out_of_canvas += 1
        code = node_code[n]
        stmts.append(
            "INSERT INTO t_road_node (park_id, node_code, coord_x, coord_y, coord_lng, coord_lat, status, deleted)\n"
            f"SELECT {park_id}, '{code}', {round(x, 4)}, {round(y, 4)}, {round(lng, 7)}, {round(lat, 7)}, 'ACTIVE', 0\n"
            "FROM DUAL ON DUPLICATE KEY UPDATE coord_x=VALUES(coord_x), coord_y=VALUES(coord_y), "
            "coord_lng=VALUES(coord_lng), coord_lat=VALUES(coord_lat), status=VALUES(status), deleted=0;")
    for (a, b), e in sorted(edges.items()):
        fa, fb = node_code[a], node_code[b]
        poly = [[round(p[0], 7), round(p[1], 7)] for p in e["polyline"]]
        gj = json.dumps({"type": "LineString", "coordinates": poly}, ensure_ascii=False, separators=(",", ":"))
        speed = ROAD_CLASS_SPEED[e["highway"]]
        direction = "BIDIRECTIONAL" if e["bidirectional"] else "FORWARD"
        stmts.append(
            "INSERT INTO t_road_segment (park_id, from_node_code, to_node_code, direction, status, "
            "speed_limit_kmh, road_class, polyline_geojson, access_state, deleted)\n"
            f"SELECT {park_id}, '{fa}', '{fb}', '{direction}', 'ACTIVE', {speed}, '{e['highway'].upper()}', "
            f"'{gj}', 'OPEN', 0 FROM DUAL ON DUPLICATE KEY UPDATE direction=VALUES(direction), "
            "status=VALUES(status), speed_limit_kmh=VALUES(speed_limit_kmh), road_class=VALUES(road_class), "
            "polyline_geojson=VALUES(polyline_geojson), access_state=VALUES(access_state), deleted=0;")
    stats = {
        "nodes": len(nodes),
        "synthetic_split_nodes": sum(1 for n in nodes if str(n).startswith("S")),
        "edges": len(edges),
        "edges_with_shape_points": sum(1 for e in edges.values() if len(e["polyline"]) > 2),
        "edges_over_400m": sum(1 for e in edges.values() if e["length"] > 400),
        "edges_over_800m": sum(1 for e in edges.values() if e["length"] > 800),
        "max_edge_m": round(max((e["length"] for e in edges.values()), default=0.0), 1),
        "nodes_outside_schematic_canvas": out_of_canvas,
        "total_edge_length_m": round(sum(e["length"] for e in edges.values()), 1),
        "speed_weighted_avg_kmh": round(
            sum(ROAD_CLASS_SPEED[e["highway"]] * e["length"] for e in edges.values())
            / max(1.0, sum(e["length"] for e in edges.values())), 2),
        **(extra_stats or {}),
    }
    if fit is not None:
        stats["canvas"] = {"w": fit["canvas_w"], "h": fit["canvas_h"],
                           "anchor_lng": fit["anchor_lng"], "anchor_lat": fit["anchor_lat"],
                           "park_width_meters": fit["park_width_meters"],
                           "park_height_meters": fit["park_height_meters"]}
    header = ("-- 园区路网图（OSM 提取，§M2E）\n"
              "-- 来源：© OpenStreetMap contributors, ODbL 1.0；原始抽取见 data/map.expanded.osm\n"
              f"-- 生成：scripts/geo/osm_to_road_graph.py；bbox（WGS84 提取框）{BBOX}\n"
              + (f"-- 画布映射（fit_canvas，coord_x/y 即按此写）：canvas {fit['canvas_w']}x{fit['canvas_h']} "
                 f"anchor=({fit['anchor_lng']},{fit['anchor_lat']}) "
                 f"meters=({fit['park_width_meters']},{fit['park_height_meters']}) "
                 f"affine lng={fit['affine']['a']}*x+{fit['affine']['c']} / "
                 f"lat={fit['affine']['e']}*y+{fit['affine']['f']}\n"
                 if fit is not None else
                 f"-- 画布映射（legacy V38）：x=(lng-{PIXEL_LNG_ORIGIN})*{PIXEL_PER_DEG_LNG} "
                 f"y=({PIXEL_LAT_ORIGIN}-lat)*{PIXEL_PER_DEG_LAT} canvas={LEGACY_CANVAS[0]}x{LEGACY_CANVAS[1]}\n")
              + f"-- 统计：{json.dumps(stats, ensure_ascii=False)}\n")
    if out is not None:
        out.write_text(header + "\n".join(stmts) + "\n", encoding="utf-8")
    return stats


def render_transform_params(fit: dict) -> str:
    """把 fit_canvas 反解出的映射渲染成后端可直接粘贴的 YAML（application.yml 的 fsd.park.geo 段）。

    用意：消除「装载器 coord_x/y 一套映射、后端仿射另一套映射」的既有多头（§范围补齐的前提）。
    后端 {@code ParkGeoTransformService.resolveAffine} 从这 4 个参考点最小二乘解出的仿射，
    因这 4 点全落在同一线性映射上，会精确还原 fit_canvas 的映射 → 前后端/seed 单一真相。
    """
    lines = ["fsd.park（示意画布与坐标变换，由 scripts/geo/osm_to_road_graph.py --params-out 生成）:",
             f"  width: {fit['canvas_w']}",
             f"  height: {fit['canvas_h']}",
             "  geo:",
             f"    anchor-lng: {fit['anchor_lng']}",
             f"    anchor-lat: {fit['anchor_lat']}",
             f"    park-width-meters: {fit['park_width_meters']}",
             f"    park-height-meters: {fit['park_height_meters']}",
             "    reference-points:"]
    for p in fit["reference_points"]:
        lines.append(f"      - {{x: {p['x']}, y: {p['y']}, lng: {p['lng']}, lat: {p['lat']}}}")
    return "\n".join(lines) + "\n"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("osm", type=Path)
    ap.add_argument("--out", type=Path, default=Path("back/sql/seed/zjf_road_network.sql"))
    ap.add_argument("--park-id", type=int, default=1)
    ap.add_argument("--snap-meters", type=float, default=10.0,
                    help="邻近节点合并阈值；0 = 不合并（会碎成几十个连通分量）")
    ap.add_argument("--canvas-width", type=int, default=1600,
                    help="fit_canvas 目标画布宽度（高度按真实南北比与 pad 自动长出来）")
    ap.add_argument("--pad", type=int, default=40, help="fit_canvas 画布内边距（像素）")
    ap.add_argument("--legacy-canvas", action="store_true",
                    help="沿用 V38 常数映射（77000/150000, 1300x800），扩范围会大面积越界——仅用于复现旧 seed")
    ap.add_argument("--params-out", type=Path, default=None,
                    help="把反解出的画布/仿射参数写成 YAML，供 application.yml 与 t_park 同步")
    ap.add_argument("--bbox", default=None,
                    help="覆盖提取框，格式 minlat,minlng,maxlat,maxlng（**WGS84**，与 Overpass 查询同序同系；"
                         "§1.12 的走廊/场站多边形是高德 GCJ-02，直接抄进来会整体偏 200–400 m）")
    ap.add_argument("--split-edges-over", type=float, default=0.0,
                    help="W2-c：把长于该米数的边沿折线等弧长拆短（0 = 不拆，保持与现役 seed 逐字一致）。"
                         "⚠ 拆过的图不能和没拆的比可达率 —— 节点密度本身就是被改的量。")
    ap.add_argument("--report-only", action="store_true")
    args = ap.parse_args()

    if args.bbox is not None:
        try:
            min_lat, min_lng, max_lat, max_lng = (float(v) for v in args.bbox.split(","))
        except ValueError:
            print("--bbox 需要四个逗号分隔的数：minlat,minlng,maxlat,maxlng", file=sys.stderr)
            return 2
        if not (min_lat < max_lat and min_lng < max_lng):
            print("--bbox 的 min/max 反了或有相等：", args.bbox, file=sys.stderr)
            return 2
        BBOX.update({"min_lng": min_lng, "max_lng": max_lng,
                     "min_lat": min_lat, "max_lat": max_lat})

    gcj, ways = load(args.osm)          # 已在 load 内按提取框裁断
    nodes, edges = build_graph(gcj, ways, args.snap_meters)
    if args.split_edges_over > 0:
        nodes, edges, gcj, synthetic = split_long_edges(nodes, edges, gcj, args.split_edges_over)
        print(f"[W2-c] 边长按 >{args.split_edges_over:.0f} m 拆分：新增 {synthetic} 个 OSMS 节点，"
              f"图节点 {len(nodes)}、边 {len(edges)}")
    comps, top5, share = components(nodes, edges)
    measured = {"components": comps, "largest_components": top5, "largest_share_pct": share,
                **detour_factor(nodes, edges, gcj)}
    fit = None if args.legacy_canvas else fit_canvas([gcj[n] for n in nodes], args.canvas_width, args.pad)
    stats = emit_sql(nodes, edges, gcj, args.park_id,
                     None if args.report_only else args.out, measured, fit=fit)
    print(json.dumps({"way_runs": len(ways), "snap_meters": args.snap_meters,
                      **stats}, ensure_ascii=False, indent=2))
    if fit is not None and args.params_out is not None and not args.report_only:
        args.params_out.write_text(render_transform_params(fit), encoding="utf-8")
        print(f"变换参数写出 {args.params_out}")
    if args.report_only:
        print("(--report-only：未写文件)")
    else:
        print(f"写出 {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
