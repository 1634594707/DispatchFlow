#!/usr/bin/env python3
"""按范围档位量"单趟里程"，再套 §0.1 的产能公式 —— 为 W4 把 §1.1-b 表重算成可复现的脚本。

为什么要这个：路线图 §0.1 那张表（以及 §3 "代价"那一段）是**一次性手算**贴进文档的，
它依赖"中位单趟里程"这个输入，而这个输入没有留下产生它的命令。范围一旦改档（§6.2 第 4 问
(A)/(B) 三个候选：4.33 / 32.34 / 47.89 km²），表就要重算，而没有脚本就只能再手算一遍 ——
手算的数没法复查。

**方法（与 §0.1 记的前提逐字对齐）**
  · 单趟 = 基地 → 取 → 送 → 基地，里程取**自建路网 A* 里程**；
  · 边权与线上一致：`ParkRoadGraph.edgeCost` 的口径 = 两端 haversine × 拥堵倍率（**不是**折线弧长）。
    ⚠ 这条要说清：现役 275 条边是"两个路口之间一条直线边"，所以 A* 里程本身不含那些边内部的弯，
      与"贴路里程"有系统性差 —— 这里刻意跟线上同尺，不跟"真实世界"同尺。
  · 产能 = 3600 / (行驶 + 装卸 + 补能)；行驶 = 里程 ÷ 18.73 km/h；装卸 = 210 + 264 s；
    补能 = 里程 ÷ 126 km × (充电 7,200 s | 换电 30 s)。

**自校验（必须能失败）**：在**标定条件**下跑，中位应当落在 §13.95 实测值的 ±3% 内：
    --tier frame  --graph data/backup/zjf_road_network.pre-W2c-20260924.sql,back/sql/seed/zjf_amap_terminal_links.sql   → 12,998 m
    --tier fence  同上 --fence-seeds back/sql/seed/zjf_geo.sql                                                            → 4,306 m
条件不同时这一行会明说"仅作记录，别当回归"——拿现网数去对手算基线是假阳性。复现不了标定值
才是采样或寻路坏了，先怀疑脚本。

用法：
    python scripts/geo/trip_mileage_sampler.py --tier fence --samples 600   # 现网（W2-c 图 + SVC 围栏）
    python scripts/geo/trip_mileage_sampler.py                              # 图框档，现网图
"""
from __future__ import annotations

import argparse
import heapq
import json
import math
import random
import re
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np
from scipy.spatial import cKDTree

sys.path.insert(0, str(Path(__file__).resolve().parent))
import service_area_from_snapping as SAS  # noqa: E402  同一套 seed 解析与判据，别抄第二份

AVG_SPEED_MPS = 18.73 * 1000.0 / 3600.0     # §0.1/§13.86 实测均速
LOAD_S, UNLOAD_S = 210.0, 264.0             # §0.2 实测装卸
USABLE_SOC_M = 126_000.0                    # 可用 SOC 70% = 126 km（§0.1）
CHARGE_S_FULL, SWAP_S_FULL = 7200.0, 30.0   # 充满 2 h / 换电 30 s（本人提供口径）
BASE_NODE = "OSM0017"                       # 基地 ZJF-IDLE-01 所在节点（§0.2）


def haversine(p: tuple[float, float], q: tuple[float, float]) -> float:
    r = math.radians
    dlat = r(q[1] - p[1])
    dlng = r(q[0] - p[0])
    h = (math.sin(dlat / 2) ** 2
         + math.cos(r(p[1])) * math.cos(r(q[1])) * math.sin(dlng / 2) ** 2)
    return 2 * 6_371_000.0 * math.asin(min(1.0, math.sqrt(h)))


def load_graph(texts: list[str]):
    """-> (code -> (lng,lat), code -> [(邻居, 米)])。

    边权取**两端 haversine**，与 `ParkRoadGraph.edgeCost` 同尺（seed 里没有 congestion 列 ⇒ 倍率 1.0）。
    ⚠ 这意味着 A* 里程不含长边内部的弯 —— 刻意跟线上同尺，而不是跟"真实世界"同尺。
    """
    nodes = SAS.load_nodes(texts)
    adj: dict[str, list[tuple[str, float]]] = defaultdict(list)
    for a, b in SAS.load_edges(texts):
        if a in nodes and b in nodes:
            adj[a].append((b, haversine(nodes[a], nodes[b])))
    return nodes, adj


def dijkstra(adj, src: str, dst: str) -> float | None:
    """单源最短路，命中终点即停。"""
    if src not in adj or dst not in adj:
        return None
    dist = {src: 0.0}
    pq = [(0.0, src)]
    done = set()
    while pq:
        d, u = heapq.heappop(pq)
        if u in done:
            continue
        if u == dst:
            return d
        done.add(u)
        for v, w in adj[u]:
            nd = d + w
            if nd < dist.get(v, float("inf")):
                dist[v] = nd
                heapq.heappush(pq, (nd, v))
    return None


def sample_pairs(region_polys, scc_xy, tree, origin, m_lng, n: int, rng: random.Random, radius_m: float):
    """在 region 内均匀撒点，**只保留 R 米内能吸到 SCC 节点**的那些（§3 的范围定义）。

    ⚠ 没有这道 R 过滤就不是"可吸附上限"，而是"整个图框" —— 两者的面积差一个量级（§13.93）。
    """
    lng_min = min(p[:, 0].min() for p in region_polys)
    lng_max = max(p[:, 0].max() for p in region_polys)
    lat_min = min(p[:, 1].min() for p in region_polys)
    lat_max = max(p[:, 1].max() for p in region_polys)
    picked: list[str] = []
    guard = 0
    batch, batch_n = 4000, 0
    while len(picked) < n and guard < 400_000:
        guard += 1
        lngs = np.empty(batch)
        lats = np.empty(batch)
        for k in range(batch):
            lngs[k] = rng.uniform(lng_min, lng_max)
            lats[k] = rng.uniform(lat_min, lat_max)
        inside = SAS.point_in_polygons(lngs, lats, region_polys)
        sel = np.nonzero(inside)[0]
        if sel.size == 0:
            continue
        qy = (lats[sel] - origin[1]) * SAS.M_PER_DEG_LAT
        qx = (lngs[sel] - origin[0]) * m_lng
        dist, idx = tree.query(np.column_stack([qx, qy]), k=1, distance_upper_bound=radius_m)
        keep = np.isfinite(dist) & (dist <= radius_m)
        picked.extend(scc_xy[i][2] for i in idx[keep])
        batch_n += sel.size
    if len(picked) < n:
        print(f"[WARN] 只凑到 {len(picked)}/{n} 个端点（投点 {batch_n}，接受率偏低）")
    return picked[:n]


# 基线对照表。**每个档位各自一条**，且各条只在它标定所用的那张图/那套围栏下成立**：
# 两条都标定于 W2-c 拆边之前（拆边会把"R 米内吸得到节点"的样本池放大，档位面积随之从
# 32.2 → 48.09 km²，里程自然变长 —— 那不是回归）。
# ⚠ frame 那条用的是 §13.95 的**实测** 12,998 m，不是 §0.1 手算的 12,267 m：后者已被
#   前者取代（差 −5.6%，P90 两边一致到 1%）。拿手算数当闸门会永远红。
REF = {
    "frame": ("§13.95 实测 frame 档（§0.1 表 C 手算 12,267 已被它取代）",
              12998.0, 19809.0,
              ["data/backup/zjf_road_network.pre-W2c-20260924.sql",
               "back/sql/seed/zjf_amap_terminal_links.sql"],
              None),
    "fence": ("§13.95 实测旧围栏档（= 路线图 §6.2 (B) 行）",
              4306.0, None,
              ["data/backup/zjf_road_network.pre-W2c-20260924.sql",
               "back/sql/seed/zjf_amap_terminal_links.sql"],
              "back/sql/seed/zjf_geo.sql"),
}
REF_TOL = 0.03


def _ref_text(args, med: float, p90: float) -> str:
    """报对照值；条件不齐就说『不适用』，不拿一个错档位的数硬比 ——
    之前这一行不分 tier，于是 fence 档也去对表 C 的 12,267，看着像 −61% 的 regression。"""
    name, ref_med, ref_p90, graph, fence_seeds = REF[args.tier]
    want = f"{ref_med:,.0f}" + (f" / {ref_p90:,.0f}" if ref_p90 else "")
    ok_graph = args.graph is not None and {Path(p).name for p in args.graph.split(",")} == \
        {Path(p).name for p in graph}
    ok_fence = args.tier == "frame" or args.fence_seeds == fence_seeds
    if not (ok_graph and ok_fence):
        return f"（{name} 记：{want} —— 本命令的图/围栏与标定条件不同，仅作记录，别当回归）"
    drift = abs(med - ref_med) / ref_med
    return f"（{name} 记：{want} ⇒ 中位偏 {drift * 100:.1f}%，{'在 ±3% 内' if drift <= REF_TOL else '⚠ 超闸门，先怀疑采样/寻路'}）"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tier", choices=("frame", "fence"), default="frame",
                    help="frame=图框内可吸附区（§0.1 表 C 那一档的口径）；fence=现役可派单围栏内")
    ap.add_argument("--graph", default=None,
                    help="换图时指向该 seed，可逗号分隔多份。只传一份会静默丢掉 "
                         "zjf_amap_terminal_links.sql 那 16 条末端连通边")
    ap.add_argument("--samples", type=int, default=300)
    ap.add_argument("--radius", type=float, default=250.0)
    ap.add_argument("--step", type=float, default=25.0, help="构造可吸附区掩码的栅格步长")
    ap.add_argument("--seed", type=int, default=20260924)
    ap.add_argument("--fence-seeds", default=None,
                    help="只按这些 seed 定义围栏（默认 zjf_geo.sql + zjf_service_area.sql）。"
                         "要复现 W3-c 之前的旧基线就传 --fence-seeds back/sql/seed/zjf_geo.sql")
    ap.add_argument("--json-out", default=None)
    args = ap.parse_args()

    texts = ([Path(p).read_text(encoding="utf-8") for p in args.graph.split(",") if p.strip()]
             if args.graph else [SAS.read(SAS.NETWORK_SEED), SAS.read(SAS.LINKS_SEED)])
    nodes, adj = load_graph(texts)
    scc = SAS.largest_scc(nodes, [(a, b) for a, nb in adj.items() for b, _ in nb])
    fences = SAS.active_fences([Path(p) for p in args.fence_seeds.split(",") if p.strip()]
                               if args.fence_seeds else None)
    print(f"围栏 {len(fences)} 片：{', '.join(c for c, _ in fences) or '（无）'}"
          f"   图节点 {len(nodes)} / SCC {len(scc)}")
    if BASE_NODE not in nodes:
        print(f"[ERROR] 基地节点 {BASE_NODE} 不在这张图里，无法复现 §0.1 口径")
        return 1

    origin = nodes[BASE_NODE]
    lat0 = origin[1]
    m_lng = SAS.M_PER_DEG_LAT * math.cos(math.radians(lat0))
    scc_xy = [((lng - origin[0]) * m_lng, (lat - origin[1]) * SAS.M_PER_DEG_LAT, code)
              for code, (lng, lat) in ((n, nodes[n]) for n in scc)]
    tree = cKDTree(np.array([(x, y) for x, y, _ in scc_xy]))

    # region = (围栏内 或 图框) ∩ "R 米内能吸到 SCC 节点"（R 过滤在 sample_pairs 里做）
    if args.tier == "fence":
        polys = [p for _, p in fences]
    else:
        m = re.search(r"min_lng':?\s*([\d.]+),\s*'max_lng':?\s*([\d.]+),\s*'min_lat':?\s*([\d.]+),"
                      r"\s*'max_lat':?\s*([\d.]+)", texts[0])
        if not m:
            print("[ERROR] 找不到 seed 头部的提取框，frame 档无界可撒")
            return 1
        a, b, c, d = (float(m.group(i)) for i in (1, 2, 3, 4))
        polys = [np.array([[a, c], [b, c], [b, d], [a, d]], dtype=float)]
    rng = random.Random(args.seed)
    ends = sample_pairs(polys, scc_xy, tree, origin, m_lng, 2 * args.samples, rng, args.radius)
    if len(ends) < 20:
        print(f"[ERROR] 只采到 {len(ends)} 个端点 —— 采样或吸附坏了，不接受这个结果")
        return 1

    trips: list[float] = []
    unf = 0
    for i in range(0, len(ends) - 1, 2):
        p, q = ends[i], ends[i + 1]
        if p == q:
            continue
        d1 = dijkstra(adj, BASE_NODE, p)
        d2 = dijkstra(adj, p, q)
        d3 = dijkstra(adj, q, BASE_NODE)
        if d1 is None or d2 is None or d3 is None:
            unf += 1
            continue
        trips.append(d1 + d2 + d3)
    if len(trips) < 20:
        print(f"[ERROR] 只有 {len(trips)} 趟成链（不可达 {unf}），样本不足以出分位数")
        return 1

    arr = np.array(sorted(trips))
    med = float(np.median(arr))
    p90 = float(np.quantile(arr, 0.90))

    def cap(meters: float, full_s: float) -> float:
        travel = meters / AVG_SPEED_MPS
        energy = meters / USABLE_SOC_M * full_s
        return 3600.0 / (travel + LOAD_S + UNLOAD_S + energy)

    print(f"tier={args.tier}  graph={args.graph or '现役 seed'}  样本 {len(trips)} 趟（不可达 {unf}）")
    print(f"  中位 {med:8.1f} m   P90 {p90:8.1f} m   {_ref_text(args, med, p90)}")
    for label, m in (("中位", med), ("P90", p90)):
        print(f"  {label}: 充电 {cap(m, CHARGE_S_FULL):.2f} 单/车·h   换电 {cap(m, SWAP_S_FULL):.2f}"
              f"   补能项 {m / USABLE_SOC_M * CHARGE_S_FULL:.0f} s → {m / USABLE_SOC_M * SWAP_S_FULL:.1f} s")
    if args.json_out:
        Path(args.json_out).write_text(json.dumps(
            {"tier": args.tier, "graph": args.graph or "现役 seed", "n": len(trips),
             "median_m": round(med, 1), "p90_m": round(p90, 1),
             "median_charge_per_vh": round(cap(med, CHARGE_S_FULL), 2),
             "median_swap_per_vh": round(cap(med, SWAP_S_FULL), 2),
             "p90_charge_per_vh": round(cap(p90, CHARGE_S_FULL), 2),
             "p90_swap_per_vh": round(cap(p90, SWAP_S_FULL), 2)}, ensure_ascii=False), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
