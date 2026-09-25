#!/usr/bin/env python3
"""§1.6 路 B 的**对照**用途：高德 driving 真实路网 vs 自建 OSM 图，产出差异报告（只读，不进任何链路）。

为什么要它：§13.79 量到两个末端场站里 0 个图节点（最近节点 720 m / 393 m），
但"OSM 有没有那条路"和"我们的提取框里有没有那条路"是两件事。高德在同一地区有完整
园区内部路，所以拿高德的路径几何去问一句：

    这条真实存在的驾车路径，离我们的图节点有多远？

于是"缺的是几何还是围栏"第一次变成可量化的数（下面每条路径都给 顶点→最近图节点 的
min/p50/p90 与"距图 >300 m 的路径长度占比"）。

请求口径**逐字对齐生产** `AmapRoadRouteService.fetchFromAmap()`：同一个 base-url、
只带 `key/origin/destination/extensions=base`（不传 strategy ⇒ 与生产拿到的默认策略一致），
所以这里的差值不能拿"我们换了个更优策略"来解释。

密钥：只从仓库根 `.env` 读 `FSD_AMAP_WEB_SERVICE_KEY`，**不打印、不写进报告**（§12.2 卫生规则）。
限流：串行、每次间隔 1.2 s；撞上 `10021 CUQPS_HAS_EXCEEDED_THE_LIMIT` 退避重试（§1.9 踩过）。

用法：
    python scripts/geo/amap_route_diff.py            # 写 reports/amap-route-diff.md
    python scripts/geo/amap_route_diff.py --osm data/map.corridor.osm
"""

from __future__ import annotations

import argparse
import json
import math
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import osm_to_road_graph as G  # noqa: E402

REPO = Path(__file__).resolve().parents[2]
KEY_NAME = "FSD_AMAP_WEB_SERVICE_KEY"
BASE_URL = "https://restapi.amap.com/v3/direction/driving"
BBOX_WGS84 = "31.8994,121.0487,31.9793,121.1288"
# §1.1-b 那张表里的"单趟路程 = 直线 × 1.481 往返"，按 OD 名尾匹配（键必须与 OD 的终点名逐字一致）
ASSUMED = {"三星镇中心": 9834, "深国际物流港": 14988, "川姜/志浩中心": 16587}

BASE = (121.081142, 31.960347)

# §1.1-b 的产能算式（同一套常数：1% = 1,800 m、可用 70 点、均速 17.84 km/h、装卸 474 s、满充 7,200 s 摊一次）
METERS_PER_PERCENT = 1800.0
USABLE_POINTS = 70.0
SPEED_MS = 17.84 / 3.6
SERVICE_S = 474.0
CHARGE_S = 7200.0


def capacity_per_hour(round_trip_m: float):
    """给一个"往返单趟路程"，返回 §1.1-b 口径的（耗 SOC%、满充可做单数、单周期 min、单车产能 单/h）。"""
    soc = round_trip_m / METERS_PER_PERCENT
    if soc <= 0:
        return None
    per_charge = USABLE_POINTS / soc
    cycle = round_trip_m / SPEED_MS + SERVICE_S + CHARGE_S / per_charge
    return round(soc, 2), round(per_charge, 1), round(cycle / 60.0, 1), round(3600.0 / cycle, 2)

OD = [
    ("基地→三星镇中心", BASE, (121.115222, 31.966141)),
    ("基地→深国际物流港", BASE, (121.101561, 31.918410)),
    ("基地→川姜/志浩中心", BASE, (121.062280, 31.912450)),
    ("基地→叠石桥物流园区118栋", BASE, (121.082150, 31.959850)),
    ("基地→科创园2栋", BASE, (121.087151, 31.963147)),
    ("基地→壹加联盟1987创意园", BASE, (121.073478, 31.960299)),
]


def read_key() -> str:
    env = REPO / ".env"
    if not env.exists():
        raise SystemExit(".env 不存在，无法做对照（不猜 key）")
    for line in env.read_text(encoding="utf-8", errors="replace").splitlines():
        if line.strip().startswith("#"):
            continue
        k, sep, v = line.partition("=")
        if sep and k.strip() == KEY_NAME:
            val = v.strip().strip('"').strip("'")
            if val:
                return val
    raise SystemExit(f".env 里没有 {KEY_NAME}，对照跑不了")


def pct(vals, p):
    if not vals:
        return None
    s = sorted(vals)
    idx = min(len(s) - 1, max(0, int(math.ceil(p / 100.0 * len(s))) - 1))
    return s[idx]


def fetch(url, key):
    q = urllib.parse.urlencode({"key": key, **url})
    req = urllib.request.Request(f"{BASE_URL}?{q}", headers={"User-Agent": "DispatchFlow-geo-diff/1.0"})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=15) as r:
                body = json.loads(r.read().decode("utf-8"))
        except urllib.error.URLError as e:
            print(f"  网络失败 {e}，重试", file=sys.stderr)
            time.sleep(3 * (attempt + 1))
            continue
        info = str(body.get("infocode") or body.get("code"))
        if info in ("10000", "1"):
            return body
        if info == "10021":          # CUQPS：限流，退避再来
            time.sleep(2.5 * (attempt + 1))
            continue
        return {"__error__": info, "info": body.get("info")}
    return {"__error__": "retries_exhausted"}


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--osm", type=Path, default=REPO / "data" / "map.corridor.osm")
    ap.add_argument("--out", type=Path, default=REPO / "reports" / "amap-route-diff.md")
    ap.add_argument("--no-fetch", action="store_true", help="只做 OSM 侧，不打高德（离线复算用）")
    args = ap.parse_args()

    min_lat, min_lng, max_lat, max_lng = (float(v) for v in BBOX_WGS84.split(","))
    G.BBOX.update({"min_lng": min_lng, "max_lng": max_lng, "min_lat": min_lat, "max_lat": max_lat})
    gcj, ways = G.load(args.osm)
    nodes, edges = G.build_graph(gcj, ways, 10.0)
    pos = {n: gcj[n] for n in nodes}
    adj = {n: [] for n in nodes}
    for e in edges.values():
        adj[e["from"]].append((e["to"], e["length"]))
        if e["bidirectional"]:
            adj[e["to"]].append((e["from"], e["length"]))

    def nearest(pt):
        best, bd = None, 1e18
        for n, p in pos.items():
            d = G.haversine(pt, p)
            if d < bd:
                best, bd = n, d
        return best, bd

    def dijkstra(src, dst):
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

    key = "" if args.no_fetch else read_key()
    rows = []
    for name, o, d in OD:
        straight = G.haversine(o, d)
        on, _od = nearest(o)
        dn, dd = nearest(d)
        osm_route = dijkstra(on, dn)
        row = {"name": name, "straight_m": round(straight),
               "osm_nearest_dest_node_m": round(dd, 1),
               "osm_route_m": None if osm_route is None else round(osm_route),
               "assumed_round_m": ASSUMED.get(name.split("→")[-1])}
        am = None
        if key:
            body = fetch({"origin": f"{o[0]:.6f},{o[1]:.6f}",
                          "destination": f"{d[0]:.6f},{d[1]:.6f}",
                          "extensions": "base"}, key)
            time.sleep(1.2)
            if "__error__" not in body:
                path = (body.get("route") or {}).get("paths") or [{}]
                p0 = path[0]
                verts = []
                for step in p0.get("steps") or []:
                    for pair in (step.get("polyline") or "").split(";"):
                        ll = pair.split(",")
                        if len(ll) >= 2:
                            try:
                                verts.append((float(ll[0]), float(ll[1])))
                            except ValueError:
                                pass
                # 每个路径顶点到"最近图节点"的距离：这条真实路径到底走不走我们的图
                dists = [min(G.haversine(v, q) for q in pos.values()) for v in verts]
                off = 0.0
                for i in range(len(verts) - 1):
                    if min(dists[i], dists[i + 1]) > 300:
                        off += G.haversine(verts[i], verts[i + 1])
                seg_len = sum(G.haversine(verts[i], verts[i + 1]) for i in range(len(verts) - 1))
                am = {"distance_m": round(float(p0.get("distance") or 0)),
                      "duration_s": round(float(p0.get("duration") or 0)),
                      "vertices": len(verts),
                      "min_to_graph_m": round(min(dists)) if dists else None,
                      "p90_to_graph_m": round(pct(dists, 90)) if dists else None,
                      "offgraph_len_m": round(off), "route_len_m": round(seg_len),
                      "offgraph_share_pct": round(100 * off / seg_len, 1) if seg_len else None}
        row["amap"] = am
        rows.append(row)
        print(f"  {row['name']}: 直线 {row['straight_m']} m | OSM {row['osm_route_m']} m | "
              f"高德 {(am or {}).get('distance_m')} m | 路径离图最近 {(am or {}).get('min_to_graph_m')} m / "
              f"p90 {(am or {}).get('p90_to_graph_m')} m | 偏离 300 m 以上占 "
              f"{(am or {}).get('offgraph_share_pct')}%", file=sys.stderr)

    out = ["# 高德真实路网 vs 自建 OSM 图：差异对照（§1.6 路 B，**只做对照，不进派单链路**）\n",
           f"- 请求口径逐字对齐生产 `AmapRoadRouteService.fetchFromAmap()`："
           f"`{BASE_URL}` + `key/origin/destination/extensions=base`，不传 `strategy`。",
           f"- 自建图：`{args.osm.name}`（WGS84 框 {BBOX_WGS84}）→ {len(nodes)} 节点 / {len(edges)} 边，"
           f"基地/末端都按最近节点吸附。",
           "- key 只从 `.env` 读、不出现在本报告与任何日志里（§12.2）。\n",
           "| OD | 直线 | §1.1-b 假设往返 | OSM 图单程 | OSM 最近末端节点 | 高德 distance | 高德 duration | "
           "路径顶点→图最近 | p90 | 偏离>300 m 的长度占比 |",
           "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |"]
    for r in rows:
        a = r["amap"] or {}
        out.append("| {n} | {s} | {as_} | {o} | {dd} | {ad} | {au} | {mn} | {p9} | {sh} |".format(
            n=r["name"], s=f"{r['straight_m']:,} m",
            as_="—" if r["assumed_round_m"] is None else f"{r['assumed_round_m']:,} m",
            o="—" if r["osm_route_m"] is None else f"{r['osm_route_m']:,} m",
            dd=f"{r['osm_nearest_dest_node_m']:,} m",
            ad="—" if not a else f"{a['distance_m']:,} m",
            au="—" if not a else f"{a['duration_s'] // 60} min",
            mn="—" if not a else f"{a['min_to_graph_m']:,} m",
            p9="—" if not a else f"{a['p90_to_graph_m']:,} m",
            sh="—" if not a else f"{a['offgraph_share_pct']}%"))
    out.append("\n## 产能对照（同一套 §1.1-b 算式，换的只有「单趟往返路程」）\n\n"
               "| 线路 | 路程口径 | 往返 | 耗 SOC | 满充可做 | 单周期 | 单车产能 单/h | 维持 79 单/h 需要 |\n"
               "| --- | --- | --- | --- | --- | --- | --- | --- |")
    for r in rows:
        assumed = r["assumed_round_m"]
        if assumed is None:
            continue
        am = r["amap"] or {}
        variants = [("①§1.1-b 现口径：直线 × 1.481", assumed)]
        if r["osm_route_m"]:
            variants.append(("②候选 OSM 图实测", r["osm_route_m"] * 2))
        if am.get("distance_m"):
            variants.append(("③高德真实路网", am["distance_m"] * 2))
        for i, (label, dist) in enumerate(variants):
            _soc, per, cyc, cap = capacity_per_hour(dist)
            out.append("| {} | {} | {} m | {}% | {} 单 | {} min | **{}** | {:.0f} 台 |".format(
                r["name"] if i == 0 else "", label, f"{dist:,}", _soc, per, cyc, cap,
                math.ceil(79.0 / cap)))
    out.append("\n## 怎么读\n"
               "- **`路径顶点→图最近` 一路都小 ⇒ OSM 有这条路**，末端进不去只是围栏/锚点没画到；"
               "这个值大、且 `偏离>300 m 占比` 高 ⇒ **OSM 真的缺这段几何**，§10.2 第 5 问只能选"
               "\"挪锚点/承认最后一段不在图上\"或\"引外部几何补缺\"。\n"
               "- 高德 `duration` 是官方驾车耗时（含路况），**只用来判量级**：无人车不是汽车，"
               "不能拿它当 ETA 口径（§9 口径纪律）。\n")
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text("\n".join(out) + "\n", encoding="utf-8")
    print(f"写出 {args.out}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
