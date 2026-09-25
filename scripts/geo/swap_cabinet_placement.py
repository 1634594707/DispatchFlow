#!/usr/bin/env python3
"""在**现役路网**上重新推导 35 个换电柜点位，并量它们买到多少覆盖率。

为什么要重跑：§0.2 那张清单是在 2026-09-23 那张图上推的（446 节点 / SCC 417 / 可吸附 32.2 km²）。
W2-c 拆边 + W3-c 重画围栏之后，图是 723 节点 / SCC 685、受理判据下的服务范围是 **47.18 km²**
⇒ "最近柜子 ≤1,085 m"这个覆盖率结论的前提已经不在，必须在新图上重推，不能沿用。

方法（与旧版一致，只是把输入换成 seed 自己）：
  · 候选点 = 最大强连通分量的节点（柜子必须落在车真能开到的地方）；
  · greedy farthest-point sampling（k-center 的 2-近似），从基地节点起步；
  · 覆盖率的口径 = **服务范围本身**（围栏内 ∩ R 米内吸得到 SCC 节点）的栅格，
    不是图框 —— 图框里有 18 km² 本来就下不了单，拿它算覆盖是自欺。

⚠ 这是**演示夹具**：坐标由我们自己的路网推导，不是真实部署清单。对外必须称"仿真/演示布点"。

用法：
    python scripts/geo/swap_cabinet_placement.py                  # 现役图 + 现役围栏，N=35
    python scripts/geo/swap_cabinet_placement.py --sweep 12,20,28,35,45
    python scripts/geo/swap_cabinet_placement.py --step 250 --radius 250

与 `service_area_from_snapping.py` 共用同一套 seed 解析（"哪些节点算可达""哪些围栏可派单"
只留一份实现），别再抄第二份 —— 抄两份就会像 §13.99 那样一份改了另一份没改。
"""
from __future__ import annotations

import argparse
import math
import sys
from pathlib import Path

import numpy as np
from scipy.spatial import cKDTree

# Windows 默认 GBK 控制台会把 "km²" 这类字符直接抛 UnicodeEncodeError —— 崩在打印那一行，
# 于是这份量在双击运行的 shell 里**重跑不出来**。仪表必须能重跑，所以开局就把 stdout 定成 UTF-8。
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

REPO = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPO / "scripts" / "geo"))
import service_area_from_snapping as SAS  # noqa: E402

BASE_NODE = "OSM0017"       # 叠石桥基地，与 trip_mileage_sampler 同一个起点
N_CABINETS = 35
PREFIX = "FSD-SWAP"

# 铺点规则对换电柜和充电站是同一套（同一张图、同一个 SCC、同一个 farthest-point sampling），
# 只有**身份字段**不同 —— 所以这里做成参数，而不是把脚本复制第二份。
# 复制两份的后果本项目已经付过一次：`tval()` 与 `ScenarioBench.T_975` 两份 t 表抄错，CI 系统性偏窄。
KINDS = {
    "swap": {"prefix": "FSD-SWAP", "type": "SWAP_CABINET", "label": "换电柜",
             "service_seconds": "30", "capacity": "1"},
    "charging": {"prefix": "FSD-CHG", "type": "CHARGING_STATION", "label": "充电站",
                 "service_seconds": "7200", "capacity": "6"},
}


def greedy_farthest(points: np.ndarray, k: int, seed_idx: int) -> list[int]:
    """farthest-point sampling。points 是 (n,2) 的米制坐标，返回被选中的下标。"""
    picked = [seed_idx]
    dist = np.linalg.norm(points - points[seed_idx], axis=1)
    while len(picked) < min(k, len(points)):
        i = int(np.argmax(dist))
        if dist[i] <= 0:
            break
        picked.append(i)
        dist = np.minimum(dist, np.linalg.norm(points - points[i], axis=1))
    return picked


def node_xy() -> dict[str, tuple[str, str]]:
    """node_code -> (coord_x, coord_y)（示意坐标，米）。

    柜子的 x/y 直接取**它所吸附的那个节点**的 x/y，而不是用仿射反算 ——
    反算要再抄一份画布参数，抄两份就会分叉（§13.99 的同一课）。
    """
    out: dict[str, tuple[str, str]] = {}
    for text in (SAS.read(SAS.NETWORK_SEED), SAS.read(SAS.LINKS_SEED)):
        for m in SAS.NODE_RE.finditer(text):
            out.setdefault(m.group("code"), (m.group("x"), m.group("y")))
    return out


SQL_TEMPLATE = (
    "INSERT INTO t_station (`park_id`, `station_code`, `station_name`, `station_type`, "
    "`coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `area`, `status`, `sort_order`, "
    "`capacity_limit`, `service_hours`, `avg_service_seconds`, `anchor_node_code`, "
    "`service_direction`, `allowed_vehicle_types`, `unreachable_reason`, `unreachable_until`, "
    "`station_confidence`, `remark`, `deleted`) SELECT (select id from t_park where park_code='DEFAULT'), "
    "'{code}', '{label} {i:02d}（{node}）', '{type}', '{x}', '{y}', '{lng}', '{lat}', "
    "'ZJF', 'ACTIVE', '{i}', '{cap}', '00:00-24:00', '{svc}', '{node}', 'BIDIRECTIONAL', NULL, NULL, NULL, "
    "'C', 'W4-a 演示夹具：由 scripts/geo/swap_cabinet_placement.py 在 {scope} 图上推导，非真实部署清单', 0 "
    "FROM DUAL ON DUPLICATE KEY UPDATE `station_name`=VALUES(`station_name`), "
    "`station_type`=VALUES(`station_type`), `coord_x`=VALUES(`coord_x`), `coord_y`=VALUES(`coord_y`), "
    "`coord_lng`=VALUES(`coord_lng`), `coord_lat`=VALUES(`coord_lat`), "
    "`anchor_node_code`=VALUES(`anchor_node_code`), `station_confidence`=VALUES(`station_confidence`), "
    "`remark`=VALUES(`remark`), `deleted`=0;")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--radius", type=float, default=250.0, help="吸附半径（与线上 order-snap-meters 同值）")
    ap.add_argument("--step", type=float, default=250.0, help="服务范围栅格步长（米）。覆盖距离统计不需要 12.5 m 那么细")
    ap.add_argument("--n", type=int, default=N_CABINETS)
    ap.add_argument("--kind", choices=tuple(KINDS), default="swap",
                    help="铺哪一类补能点。同一套铺点规则，只有类型/前缀/服务时长不同")
    ap.add_argument("--sweep", default=None, help="逗号分隔的 N 列表，用来回答『35 这个数字是怎么定的』")
    ap.add_argument("--scope", choices=("fence", "frame"), default="fence",
                    help="fence=只量受理判据下的服务范围（默认）；frame=连图框里下不了单的地方一起量")
    ap.add_argument("--out", default=None, help="把清单写成 CSV")
    ap.add_argument("--emit-sql", default=None,
                    help="把清单写成 t_station 的 upsert（W4-a 的换电柜 seed）")
    args = ap.parse_args()

    texts = [SAS.read(SAS.NETWORK_SEED), SAS.read(SAS.LINKS_SEED)]
    nodes = SAS.load_nodes(texts)
    edges = SAS.load_edges(texts)
    scc = SAS.largest_scc(nodes, edges)
    fences = SAS.active_fences()
    if not scc or not fences:
        print("[ERROR] 节点或围栏解析为空 —— 先怀疑正则，不要接受 0 覆盖率这个结论")
        return 1
    if BASE_NODE not in nodes:
        print(f"[ERROR] 基地节点 {BASE_NODE} 不在图里，seed 与脚本前提不符")
        return 1

    origin = nodes[BASE_NODE]
    lat0 = origin[1]
    m_lng = SAS.M_PER_DEG_LAT * math.cos(math.radians(lat0))
    m_lat = SAS.M_PER_DEG_LAT
    pts_m = np.array([[(nodes[c][0] - origin[0]) * m_lng, (nodes[c][1] - origin[1]) * m_lat]
                      for c in scc])
    codes = list(scc)
    print(f"图节点 {len(nodes)} / 有向边 {len(edges)} / SCC {len(scc)}  —— 柜子的候选集")

    # 服务范围栅格：与 `service_area_from_snapping.py` 同一个 coverage() 实现
    if args.scope == "fence":
        polys = [p for _, p in fences]
    else:
        lngs = np.array([nodes[c][0] for c in nodes])
        lats = np.array([nodes[c][1] for c in nodes])
        polys = [np.array([[lngs.min(), lats.min()], [lngs.max(), lats.min()],
                           [lngs.max(), lats.max()], [lngs.min(), lats.max()],
                           [lngs.min(), lats.min()]])]
    area, ratio, mask, (lng_min, lat_min, dlng, dlat) = SAS.coverage(
        polys, cKDTree(pts_m), origin, m_lng, m_lat, args.radius, args.step)
    rows, cols = np.nonzero(mask)
    if rows.size == 0:
        print("[ERROR] 服务范围栅格为空 —— 覆盖率统计无从谈起，先查 --scope 与 seed")
        return 1
    cell_lng = lng_min + (cols + 0.5) * dlng
    cell_lat = lat_min + (rows + 0.5) * dlat
    cells_m = np.column_stack([(cell_lng - origin[0]) * m_lng, (cell_lat - origin[1]) * m_lat])
    cell_km2 = (dlng * m_lng) * (dlat * m_lat) / 1e6
    print(f"服务范围（scope={args.scope}、R={args.radius:.0f} m、步长 {args.step:.0f} m）"
          f"= {area:.2f} km²，采样格 {cells_m.shape[0]} 个（≈{cells_m.shape[0]*cell_km2:.2f} km²）")

    seed_idx = int(np.argmin(np.linalg.norm(pts_m, axis=1)))   # 离基地最近的 SCC 节点

    def report(k: int, quiet: bool = False) -> tuple[np.ndarray, list[int]]:
        chosen = greedy_farthest(pts_m, k, seed_idx)
        # 每个采样格到最近柜子的距离
        far = np.sort(cKDTree(pts_m[chosen]).query(cells_m, k=1)[0])
        if not quiet:
            print(f"  N={k:2d}  中位 {far[len(far)//2]:5.0f} m   P90 {far[int(len(far)*.9)]:5.0f} m   "
                  f"最远 {far[-1]:6.0f} m   >3 km 的格子 {int((far>3000).sum()):3d}/{len(far)}")
        return far, chosen

    if args.sweep:
        print("\n=== 柜子数量 vs 覆盖率（回答『35 这个数字是怎么来的』）===")
        for k in (int(x) for x in args.sweep.split(",") if x.strip()):
            report(k)

    print(f"\n=== N={args.n} 的清单（演示夹具，吸附到可派单路网节点）===")
    far, chosen = report(args.n)
    for lim in (1500, 2000, 3000):
        ok = 100.0 * (far <= lim).mean()
        print(f"  距最近{kind['label']} ≤{lim:>4} m 的服务范围占比：{ok:.1f}%")

    lines = ["code,node_code,lng,lat"]
    kind = KINDS[args.kind]
    for i, ci in enumerate(chosen, 1):
        code = f"{kind['prefix']}-{i:02d}"
        nc = codes[ci]
        lng, lat = nodes[nc]
        lines.append(f"{code},{nc},{lng:.6f},{lat:.6f}")
        print(f"  {code}  {nc:<10} {lng:.6f}, {lat:.6f}")
    print(f"\n最坏点到最近柜子的绕行距离 {far[-1]:.0f} m —— "
          f"按 18.73 km/h 折 {far[-1]/(18.73*1000/3600):.0f} s（单程，未计绕行系数）")

    if args.out:
        Path(args.out).write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
        print(f"清单已写出 {args.out}（{len(lines)-1} 行数据）")

    if args.emit_sql:
        xy = node_xy()
        missing = [codes[ci] for ci in chosen if codes[ci] not in xy]
        if missing:
            print(f"[ERROR] {len(missing)} 个锚点节点解析不到示意坐标（例：{missing[:3]}）"
                  f" —— NODE_RE 的 x/y 分组或 seed 的行形态变了，先修解析再生成")
            return 1
        stmts = [f"-- 由 scripts/geo/swap_cabinet_placement.py --kind {args.kind} 生成（{kind['label']}布点，演示夹具）",
                 f"-- 图：现役 seed（SCC {len(scc)} 节点）；服务范围 scope={args.scope} "
                 f"R={args.radius:.0f} m = {area:.2f} km²",
                 f"-- 覆盖率：中位 {far[len(far)//2]:.0f} m / P90 {far[int(len(far)*.9)]:.0f} m "
                 f"/ 最远 {far[-1]:.0f} m（直线，非行驶绕行）",
                 "-- ⚠ 坐标是按 farthest-point sampling 推的，不是真实部署清单；对外称「仿真/演示布点」。"]
        for i, ci in enumerate(chosen, 1):
            nc = codes[ci]
            lng, lat = nodes[nc]
            x, y = xy[nc]
            stmts.append(SQL_TEMPLATE.format(code=f"{kind['prefix']}-{i:02d}", i=i, node=nc,
                                             x=x, y=y, lng=f"{lng:.6f}", lat=f"{lat:.6f}",
                                             scope=args.scope, type=kind["type"], label=kind["label"],
                                             svc=kind["service_seconds"], cap=kind["capacity"]))
        Path(args.emit_sql).write_text("\n".join(stmts) + "\n", encoding="utf-8", newline="\n")
        print(f"seed 已写出 {args.emit_sql}（{len(chosen)} 条 upsert）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
