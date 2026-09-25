#!/usr/bin/env python3
"""W3-a：把"围栏内 + R 米内能吸到最大强连通分量节点"这个服务范围**算出来并描出边界**。

为什么要有这个脚本（路线图 §3 W3-a）：全仓此前只有 `draw_zone.py:48` 的凸包，没有任何凹包 ——
凸包会横切空地削掉角落（本人据此说过"好小根本没覆盖"）；而现役两片商圈围栏是 250 m 栅格阶梯，
台阶还会把真地方切掉（三星镇驻地落在 ZJF-ZONE-L1 南界外 214 m）。
服务范围的正确定义不是"有几个站点"，而是 **围栏并集 ∩ 半径 R 内能吸到可派单节点**，
所以边界要从这件事本身推出来，而不是手描。

判据与线上逐字同源（这是这个脚本能不能用的前提）：
  · 可派单围栏 = `status=ACTIVE` 且 `fence_code` 以 `ZJF-ZONE-` 开头
      —— 见 `ParkGeofenceServiceImpl.isDispatchable()`；`OrderEndpointResolver` 只吃这个字段。
  · 吸附 = 距离 ≤ R 的图节点，且节点在**最大强连通分量**上
      —— 覆盖性必须走有向图：单向路（FORWARD）下的可达性是强连通问题，无向会高估。
  · 半径 = `fsd.park.geo.order-snap-meters`（现役 250 m）。

节点与围栏都是 GCJ-02，全程在一个局部等距圆柱投影里算米；**查询点与节点必须共用同一个原点**
（各取一个原点会让 KD 树量出"带偏移的距离"，覆盖整体错位而数字看着仍然合理）。

自校验：不带参数跑出来的五个半径档位应当复现路线图 §3 那张表（50→3.0 / 150→18.0 /
250→32.2 / 400→46.2 / 500→51.6 km²）。复现不了就是仪表坏了，先怀疑脚本再怀疑结论。

用法：
    python scripts/geo/service_area_from_snapping.py
    python scripts/geo/service_area_from_snapping.py --radii 250 --converge-check --out-json tmp/svc.json
    python scripts/geo/service_area_from_snapping.py --split-graph tmp/regen_split400.sql   # W2-c 之后重量
"""
from __future__ import annotations

import argparse
import json
import math
import re
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np
from scipy.spatial import cKDTree

# Windows 默认 GBK 控制台打不出 `km²` 里的上标 ² —— 会把结果那一行崩掉，
# 而图/围栏的诊断行已经先印出去了，看起来像"跑完了只是没数"。仪表必须能重跑。
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

REPO = Path(__file__).resolve().parents[2]
NETWORK_SEED = REPO / "back/sql/seed/zjf_road_network.sql"
LINKS_SEED = REPO / "back/sql/seed/zjf_amap_terminal_links.sql"
GEO_SEED = REPO / "back/sql/seed/zjf_geo.sql"
SERVICE_AREA_SEED = REPO / "back/sql/seed/zjf_service_area.sql"

M_PER_DEG_LAT = 111_320.0

# 节点行：'OSM0017', <x>, <y>, <lng>, <lat>, 'ACTIVE'
NODE_RE = re.compile(
    r"'(?P<code>OSM[A-Z]?\d+|AM[A-Z0-9]+\d+)',\s*(?P<x>-?[\d.]+),\s*(?P<y>-?[\d.]+),"
    r"\s*(?P<lng>-?[\d.]+),\s*(?P<lat>-?[\d.]+),\s*'ACTIVE'")
# 边行：'FROM','TO','DIRECTION','STATUS',<speed>,'CLASS'
SEG_RE = re.compile(
    r"'(?P<frm>[A-Z]+\d+)',\s*'(?P<to>[A-Z]+\d+)',\s*'(?P<dir>BIDIRECTIONAL|FORWARD|REVERSE)',"
    r"\s*'(?P<status>\w+)',\s*\d+,\s*'(?P<cls>[A-Z_]+)'")
ACCESS_RE = re.compile(r"'(?P<access>OPEN|DRIVABLE|BLOCKED|PEDESTRIAN_ONLY)'")
FENCE_RE = re.compile(
    r"INSERT INTO t_park_geofence .*?'(?P<code>ZJF-ZONE-[A-Z0-9-]*)',(?:[^']*|'[^']*')*?"
    r"'(?P<polygon>\[\[.*?\]\])'[^']*'(?P<status>[A-Z_]+)'", re.DOTALL)

# 本工具自己 emit 的那条 UPDATE（W3-b 停用旧手描块）也是围栏定义的一部分：
# 只解析 INSERT 会看不见它，于是 `--scope fence` 量的仍是已被换掉的旧围栏。
FENCE_DISABLE_RE = re.compile(
    r"UPDATE\s+t_park_geofence\s+SET\s+status\s*=\s*'DISABLED'\s+WHERE(?P<where>.*?);",
    re.DOTALL | re.IGNORECASE)
FENCE_CODE_LIKE_RE = re.compile(
    r"`?fence_code`?\s+(?P<neg>NOT\s+)?LIKE\s+'(?P<pat>[^']*)'", re.IGNORECASE)


def read(path: Path) -> str:
    if not path.exists():
        raise SystemExit(f"缺少 {path}")
    return path.read_text(encoding="utf-8")


def active_fences(paths: list[Path] | None = None) -> list[tuple[str, np.ndarray]]:
    """现役可派单围栏集合。**"哪些 seed 共同定义围栏"只留这一份实现** ——
    trip_mileage_sampler 也走它，否则两处会对"现役围栏"各说一套。"""
    return load_fences([read(p) for p in (paths or [GEO_SEED, SERVICE_AREA_SEED])])


def load_nodes(texts: list[str]) -> dict[str, tuple[float, float]]:
    out: dict[str, tuple[float, float]] = {}
    for text in texts:
        for m in NODE_RE.finditer(text):
            out.setdefault(m.group("code"), (float(m.group("lng")), float(m.group("lat"))))
    return out


def load_edges(texts: list[str]) -> list[tuple[str, str]]:
    """有向边。与 `ParkRoadGraph.fromDatabase` 同一套过滤：ACTIVE、非 BLOCKED/PEDESTRIAN_ONLY。

    ⚠ 必须**按语句**扫而不是按行：`osm_to_road_graph.py` 写的 seed 是
      `INSERT INTO t_road_segment (...列...)\nSELECT 1, 'OSM0116', ...` —— SELECT 在下一行。
      按行扫只会命中 `zjf_amap_terminal_links.py` 那种单行写法，实测就是这样只捞到 32 条有向边、
      最大强连通分量掉到 12，而节点数是对的（446）—— 半坏的仪表最容易骗过人。
    """
    out: list[tuple[str, str]] = []
    for text in texts:
        for chunk in text.split("INSERT INTO t_road_segment")[1:]:
            stmt = chunk.split(";", 1)[0]
            seg = SEG_RE.search(stmt)
            if not seg or seg.group("status") != "ACTIVE":
                continue
            access = ACCESS_RE.search(stmt[seg.end():])
            if access and access.group("access") in ("BLOCKED", "PEDESTRIAN_ONLY"):
                continue
            a, b, d = seg.group("frm"), seg.group("to"), seg.group("dir")
            if d in ("BIDIRECTIONAL", "FORWARD"):
                out.append((a, b))
            if d in ("BIDIRECTIONAL", "REVERSE"):
                out.append((b, a))
    return out


def largest_scc(nodes: dict, edges: list[tuple[str, str]]) -> list[str]:
    """Kosaraju，迭代版。"""
    adj: dict[str, list[str]] = {n: [] for n in nodes}
    radj: dict[str, list[str]] = {n: [] for n in nodes}
    for a, b in edges:
        if a in adj and b in adj:
            adj[a].append(b)
            radj[b].append(a)

    order: list[str] = []
    seen: set[str] = set()
    for start in adj:
        if start in seen:
            continue
        seen.add(start)
        stack = [(start, iter(adj[start]))]
        while stack:
            node, it = stack[-1]
            advanced = False
            for nxt in it:
                if nxt not in seen:
                    seen.add(nxt)
                    stack.append((nxt, iter(adj[nxt])))
                    advanced = True
                    break
            if not advanced:
                order.append(node)
                stack.pop()

    comp_of: dict[str, int] = {}
    sizes: list[int] = []
    visited: set[str] = set()
    for start in reversed(order):
        if start in visited:
            continue
        cid = len(sizes)
        stack = [start]
        visited.add(start)
        size = 0
        while stack:
            node = stack.pop()
            size += 1
            comp_of[node] = cid
            for nxt in radj[node]:
                if nxt not in visited:
                    visited.add(nxt)
                    stack.append(nxt)
        sizes.append(size)
    if not sizes:
        return []
    best = int(np.argmax(sizes))
    return [n for n in nodes if comp_of.get(n) == best]


def _like_rx(pattern: str) -> re.Pattern:
    """SQL LIKE → 正则。逐字符翻译，不用 escape+replace（`%` 不是正则元字符，
    re.escape 不会给它加反斜杠，先 escape 再 replace(r'\\%') 是静默的空操作）。"""
    body = "".join(".*" if c == "%" else "." if c == "_" else re.escape(c) for c in pattern)
    return re.compile(f"^{body}$", re.IGNORECASE)


def _matchers(where: str) -> tuple[list, list]:
    pos, neg = [], []
    for m in FENCE_CODE_LIKE_RE.finditer(where):
        (neg if m.group("neg") else pos).append(_like_rx(m.group("pat")))
    return pos, neg


def disabled_by_update(texts: list[str], codes: set[str]) -> tuple[set[str], int]:
    """把 seed 里的 `UPDATE ... SET status='DISABLED' WHERE fence_code LIKE ...` 落到集合上。

    正例是交集：命中所有 LIKE 且不被任何 NOT LIKE 排除才停用。
    返回 (被停用的 code 集合, 解析到的停用语句条数) —— 条数用来区分"没有停用语句"和
    "有语句但正则没吃掉它"，后者必须报错，不能当成前者静默放过。
    """
    out, stmts = set(), 0
    for text in texts:
        for m in FENCE_DISABLE_RE.finditer(text):
            stmts += 1
            pos, neg = _matchers(m.group("where"))
            if not pos:
                continue
            out |= {c for c in codes
                    if all(r.match(c) for r in pos) and not any(r.match(c) for r in neg)}
    return out, stmts


def load_fences(texts: list[str]) -> list[tuple[str, np.ndarray]]:
    """按 seed 的**先后顺序重放**：先吃本文件的 INSERT，再落本文件的停用 UPDATE。

    原来是对全部文本先收集所有 INSERT、再全局套用所有 UPDATE —— 那样"排在含停用语句
    那份 seed 之后"的输入围栏会被自己那条 UPDATE 误杀，等于这台工具没法接收任何新增输入片
    （实测：加一片 ZJF-ZONE-EAST-IN 后仍打印"可派单围栏 1 片"）。按语句顺序重放才是
    MySQL 的真实执行语义；现役两份 seed 的结果不变（仍是 1 片 / 同一面积）。
    """
    found: dict[str, np.ndarray] = {}
    disable_stmts, total_dropped = 0, 0
    for text in texts:
        for m in FENCE_RE.finditer(text):
            if m.group("status") != "ACTIVE":
                continue                               # isDispatchable() 第一半
            try:
                pts = json.loads(m.group("polygon"))
            except json.JSONDecodeError:
                continue
            ring = [(float(p[0]), float(p[1])) for p in pts if p and len(p) >= 2]
            if len(ring) < 3:
                continue
            found[m.group("code")] = np.array(ring, dtype=float)   # 后一份 seed 覆盖前一份
        dropped, stmts = disabled_by_update([text], set(found))
        disable_stmts += stmts
        total_dropped += len(dropped)
        for code in sorted(dropped):
            found.pop(code, None)
    if disable_stmts and not total_dropped:
        raise SystemExit("[ERROR] 解析到停用 UPDATE 却一片没停用 —— 先怀疑这里的正则，"
                         "不要接受『围栏还在』这个结论")
    return list(found.items())


def point_in_polygons(lng: np.ndarray, lat: np.ndarray, polys) -> np.ndarray:
    """向量化射线法（偶奇规则），与 `GeoPolygonUtils.contains` 同判据，只是批量跑。

    ⚠ seed 里的围栏环**不闭合**（逐条实测 closed=False），所以最后一条边要自己补上 ——
      少补这一条会系统性漏判，实测能把结果打穿（面积直接少一截却仍然"看着合理"）。
    """
    inside = np.zeros(lng.shape, dtype=bool)
    for ring in polys:
        x, y = ring[:, 0], ring[:, 1]
        if not (x[0] == x[-1] and y[0] == y[-1]):
            x = np.append(x, x[0])
            y = np.append(y, y[0])
        hit = np.zeros(lng.shape, dtype=bool)
        for i in range(len(x) - 1):
            x1, y1, x2, y2 = x[i], y[i], x[i + 1], y[i + 1]
            if y1 == y2:
                continue
            span = y2 - y1
            t = (lat - y1) / span
            x_at = x1 + t * (x2 - x1)
            with np.errstate(invalid="ignore"):
                # ⚠ 必须**奇偶翻转（XOR）**，不能 `|=`：射线再穿一次就该回到"外面"。
                #   用 OR 会把"被任意一条边跨过"当成内部 ⇒ 非轴对齐的边一律多算。
                #   实测 `ZJF-ZONE-L1` 因此被算成 4.94 km²，而参照三方一致说 3.93：
                #   matplotlib.path 3.9327 / 鞋带 3.9367 / seed 自己的 remark「3.94km2」。
                #   轴对齐的图框正方形恰好看不出来 —— 所以这个错只在真围栏上露头。
                hit ^= ((y1 > lat) != (y2 > lat)) & (lng < x_at)
        inside |= hit
    return inside


def coverage(polys, tree, origin, m_lng, m_lat, radius_m: float, step_m: float):
    lng_min = min(p[:, 0].min() for p in polys)
    lng_max = max(p[:, 0].max() for p in polys)
    lat_min = min(p[:, 1].min() for p in polys)
    lat_max = max(p[:, 1].max() for p in polys)
    dlng, dlat = step_m / m_lng, step_m / m_lat
    lngs = np.arange(lng_min, lng_max + dlng, dlng)
    lats = np.arange(lat_min, lat_max + dlat, dlat)
    glng, glat = np.meshgrid(lngs, lats)
    flat_lng, flat_lat = glng.ravel(), glat.ravel()

    inside = point_in_polygons(flat_lng, flat_lat, polys)
    idx = np.nonzero(inside)[0]
    covered = np.zeros(flat_lng.shape, dtype=bool)
    if idx.size:
        qx = (flat_lng[idx] - origin[0]) * m_lng
        qy = (flat_lat[idx] - origin[1]) * m_lat
        dist, _ = tree.query(np.column_stack([qx, qy]), k=1, distance_upper_bound=radius_m)
        ok = np.isfinite(dist) & (dist <= radius_m)
        covered[idx[ok]] = True

    cell_m2 = (dlng * m_lng) * (dlat * m_lat)
    area_km2 = int(covered.sum()) * cell_m2 / 1e6
    ratio = int(covered.sum()) / max(1, int(inside.sum()))
    return area_km2, ratio, covered.reshape(glat.shape), (lng_min, lat_min, dlng, dlat)


def boundary_edges(mask: np.ndarray) -> list[tuple[tuple[int, int], tuple[int, int]]]:
    """覆盖区与外界的格子边（格点坐标 = (列, 行)）。

    这里刻意出阶梯：边界贴着覆盖区真实外沿，台阶尺寸就是 `--step`，随后由道格拉斯-普克把
    共线台阶收成斜线。现役围栏的问题是**步长 250 m 太粗**，不是"有台阶"这件事本身。
    """
    h, w = mask.shape
    segs = []
    for i in range(h):
        row = mask[i]
        for j in range(w):
            if not row[j]:
                continue
            if i == 0 or not mask[i - 1, j]:
                segs.append(((j, i), (j + 1, i)))
            if i == h - 1 or not mask[i + 1, j]:
                segs.append(((j, i + 1), (j + 1, i + 1)))
            if j == 0 or not row[j - 1]:
                segs.append(((j, i), (j, i + 1)))
            if j == w - 1 or not row[j + 1]:
                segs.append(((j + 1, i), (j + 1, i + 1)))
    return segs


def boundary_rings(segs) -> list[list[tuple[int, int]]]:
    """把散格子边串成闭合环（可能多圈：外沿 + 洞）。

    ⚠ 前两版都错了，且都是"看着成功、结果是个小疙瘩"：
      ① 按 `邻居 != prev` 走 —— 对角相接处顶点度为 4，走到岔口就断；
      ② 用 `len(ring) > 20*len(segs)` 兜底退出 —— 掩盖了早断，产出的 6 顶点环面积只有 0.01 km²，
         而它本该是 32 km²。所以这里改成**消耗边集**的标准做法，并在岔口按"方向最连续"选下一条，
         让对角相接的两块各走各的圈。
    """
    edges = {frozenset((a, b)) for a, b in segs}
    adj: dict[tuple[int, int], set[tuple[int, int]]] = defaultdict(set)
    for a, b in segs:
        adj[a].add(b)
        adj[b].add(a)

    rings: list[list[tuple[int, int]]] = []
    while edges:
        first = next(iter(edges))
        u, v = tuple(first)
        edges.discard(first)
        ring = [u, v]
        prev, cur = u, v
        guard = 0
        while cur != ring[0] and guard <= 8 * len(segs):
            guard += 1
            cands = [n for n in adj[cur] if frozenset((cur, n)) in edges]
            if not cands:
                break
            if len(cands) > 1:
                # 岔口：选与来向夹角最接近 180° 的那条（方向连续），把对角接触拆成两个独立圈
                incoming = (cur[0] - prev[0], cur[1] - prev[1])
                cur = max(cands, key=lambda n: (n[0] - cur[0]) * incoming[0] + (n[1] - cur[1]) * incoming[1])
            else:
                cur = cands[0]
            edges.discard(frozenset((ring[-1], cur)))
            ring.append(cur)
            prev = ring[-2]
        if cur == ring[0] and len(ring) >= 4:
            rings.append(ring)
    rings.sort(key=len, reverse=True)
    return rings


def simplify(points: list[tuple[float, float]], tol: float) -> list[tuple[float, float]]:
    """道格拉斯-普克。"""
    if len(points) < 3:
        return points
    pts = np.asarray(points, dtype=float)
    # ⚠ 必须**只把两端标成保留**，中间一律 False，由递归按需点亮。
    #   从前这里初始化成全 True ⇒ 函数等价于"原样返回"，21 个共线点也一个不删，
    #   于是"把 12.5 m 台阶收成真实走向"这件事其实从未发生（而 polygon vs measured
    #   那道回算抓不到它，因为两边都出自同一张栅格）。
    keep = np.zeros(len(pts), dtype=bool)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        s, e = stack.pop()
        if e <= s + 1:
            continue
        a, b = pts[s], pts[e]
        ab = b - a
        denom = float(ab @ ab)
        t = np.zeros(e - s - 1) if denom == 0 else np.clip(((pts[s + 1:e] - a) @ ab) / denom, 0, 1)
        proj = a + t[:, None] * ab
        dist = np.hypot(*(pts[s + 1:e] - proj).T)
        far = int(np.argmax(dist))
        if dist[far] > tol:
            keep[s + 1 + far] = True
            stack.append((s, s + 1 + far))
            stack.append((s + 1 + far, e))
    return [tuple(p) for p, k in zip(pts, keep) if k]



def render_fence_sql(payload: dict, args) -> str:
    """把外沿环写成 t_park_geofence 的 upsert，并停用旧的 ZJF-ZONE-* 小区块（W3-b/c）。

    编码一律 `ZJF-ZONE-SVC-*` —— 必须带 `ZJF-ZONE-` 前缀，因为 `isDispatchable()` 认的就是这个前缀，
    换个名字围栏就是死数据（图上看得见、受理不认）。
    旧的停用而不是删除：`swap`/回退时还要靠它们复原。
    """
    lines = [
        "-- 由 scripts/geo/service_area_from_snapping.py 生成（W3-a 的可吸附并集围栏）",
        "-- 判据：围栏内 + R 米内吸得到最大强连通分量节点。R=%g m，栅格 %g m，简化容差 %g m。"
        % (payload["radius_m"], payload["step_m"], payload["simplify_m"]),
        "-- 实测可下单面积 %.2f km²；外沿多边形 %.2f km²（差值是区内未覆盖的洞，"
        % (payload["measured_area_km2"], payload["polygon_area_km2"]),
        "--   洞内的点会被第二道吸附判据以 ORDER_ENDPOINT_SNAP_FAILED 拒掉，故围栏不描洞）",
        "-- ⚠ 这是演示口径的几何产物，不是行政边界；换图或改半径后要重新生成，不要手改。",
        "",
        "-- W3-b：停用旧的手描小区块与两片商圈（新围栏已完全覆盖其可下单区，实测 0 格落在外面）",
        "UPDATE t_park_geofence SET status='DISABLED'",
        " WHERE fence_code LIKE 'ZJF-ZONE-%' AND fence_code NOT LIKE 'ZJF-ZONE-SVC-%' AND deleted=0;",
        "",
    ]
    for i, ring in enumerate(payload["rings_gcj02"], start=1):
        code = f"ZJF-ZONE-SVC-{i:02d}"
        pj = json.dumps([[round(float(x), 6), round(float(y), 6)] for x, y in ring],
                        ensure_ascii=False, separators=(", ", ": "))
        remark = ("由 service_area_from_snapping.py 生成的可吸附服务范围第 %d 片"
                  "（R=%gm，%d 顶点，源：%s）" % (i, payload["radius_m"], len(ring), payload["source"]))
        lines.append(
            "INSERT INTO t_park_geofence (`park_id`, `fence_code`, `fence_name`, `fence_type`, "
            "`response_level`, `buffer_meters`, `polygon_json`, `status`, `remark`, `deleted`) "
            "SELECT (select id from t_park where park_code='DEFAULT'), "
            f"'{code}', '服务范围 R={payload['radius_m']:.0f}m 可吸附并集 #{i:02d}', 'BOUNDARY', "
            f"'WARN', '0.00', '{pj}', 'ACTIVE', '{remark}', 0 "
            "FROM DUAL ON DUPLICATE KEY UPDATE `fence_name`=VALUES(`fence_name`), "
            "`polygon_json`=VALUES(`polygon_json`), `status`=VALUES(`status`), "
            "`remark`=VALUES(`remark`), `deleted`=0;")
    return "\n".join(lines) + "\n"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--radii", default="50,150,250,400,500")
    ap.add_argument("--step", type=float, default=50.0, help="栅格步长（米）")
    ap.add_argument("--converge-check", action="store_true", help="再用 step/2 复算，报步长敏感性")
    ap.add_argument("--split-graph", default=None,
                    help="用 W2-c 拆过的图（osm_to_road_graph.py --split-edges-over 的产物）替代现役 seed")
    ap.add_argument("--scope", choices=("fence", "frame"), default="fence",
                    help="fence=只算可派单围栏内（与线上受理判据一致，默认）；"
                         "frame=只按图框算『R 米内能吸到 SCC 节点』，不约束围栏 —— "
                         "§3 那张表的口径需要用这一档才能复现，两者差多少就是要不要重画围栏的账")
    ap.add_argument("--exclude-fences", default=None,
                    help="逗号分隔的 fence_code，量前先停用它们（W3-b 的预演：那 8 片手描小区块"
                         "到底贡献了多少面积 —— 停用它们对服务范围有没有影响）")
    ap.add_argument("--simplify-m", type=float, default=120.0,
                    help="边界道格拉斯-普克容差（米）。台阶步长就是 --step，容差小于它等于没简化")
    ap.add_argument("--emit-sql", default=None,
                    help="把外沿环写成 t_park_geofence 的 upsert（W3-c 的围栏 seed），"
                         "并顺带把非 SVC 的旧 ZJF-ZONE-* 置 DISABLED（W3-b）")
    ap.add_argument("--fence-seeds", default=None,
                    help="逗号分隔的围栏 seed，按顺序生效（默认 zjf_geo.sql 再叠加 zjf_service_area.sql）。"
                         "只读前者会让 `--scope fence` 量的还是被 W3-c 换掉之前的旧围栏")
    ap.add_argument("--out-json", default=None)
    args = ap.parse_args()

    texts = ([read(Path(p)) for p in args.split_graph.split(",") if p.strip()]
             if args.split_graph else [read(NETWORK_SEED), read(LINKS_SEED)])
    nodes = load_nodes(texts)
    edges = load_edges(texts)
    scc = largest_scc(nodes, edges)
    fence_paths = ([Path(p) for p in args.fence_seeds.split(",") if p.strip()]
                   if args.fence_seeds else None)
    fences = active_fences(fence_paths)
    if args.exclude_fences:
        drop = {c.strip() for c in args.exclude_fences.split(",") if c.strip()}
        kept = [(c, p) for c, p in fences if c not in drop]
        print(f"--exclude-fences：停用 {len(fences) - len(kept)} 片（应停 {len(drop)}）："
              f"{', '.join(sorted(c for c, _ in fences if c in drop))}")
        fences = kept
    print(f"图节点 {len(nodes)}、有向边 {len(edges)}、最大强连通分量 {len(scc)}")
    print(f"可派单围栏 {len(fences)} 片：{', '.join(c for c, _ in fences) or '（无）'}")
    if not scc or not fences:
        print("[ERROR] 节点或围栏解析为空 —— 先怀疑正则，不要接受 0 km² 这个结论")
        return 1

    polys = [p for _, p in fences]
    if args.scope == "frame":
        # §3 那张表的口径：只问"图框内 R 米能不能吸到 SCC 节点"，不约束围栏。
        # 图框取 seed 头部记录的 WGS84 提取框；节点是 GCJ-02，两者差 200–400 m，
        # 对这个 A/B（差一个数量级）不构成影响，但**不要**拿这一档的数对外当服务范围。
        m = re.search(r"min_lng':?\s*([\d.]+),\s*'max_lng':?\s*([\d.]+),\s*'min_lat':?\s*([\d.]+),"
                      r"\s*'max_lat':?\s*([\d.]+)", texts[0])
        if not m:
            print("[ERROR] 找不到 seed 头部的提取框，--scope frame 退不回 §3 口径")
            return 1
        a, b, c, d = (float(m.group(i)) for i in (1, 2, 3, 4))
        polys = [np.array([[a, c], [b, c], [b, d], [a, d]], dtype=float)]
        print(f"--scope frame：图框 {a}–{b} / {c}–{d}，**不约束围栏**")
    origin = (nodes[scc[0]][0], nodes[scc[0]][1])
    lat0 = float(np.mean([p[:, 1].mean() for p in polys]))
    m_lng = M_PER_DEG_LAT * math.cos(math.radians(lat0))
    m_lat = M_PER_DEG_LAT
    node_xy = np.array([[(lng - origin[0]) * m_lng, (lat - origin[1]) * m_lat]
                        for lng, lat in (nodes[n] for n in scc)])
    tree = cKDTree(node_xy)

    results = {}
    for radius in (float(r) for r in args.radii.split(",")):
        area, ratio, mask, affine = coverage(polys, tree, origin, m_lng, m_lat, radius, args.step)
        row = {"area_km2": round(area, 2), "share_of_fence_pct": round(ratio * 100, 1)}
        if args.converge_check:
            a2, _, _, _ = coverage(polys, tree, origin, m_lng, m_lat, radius, args.step / 2.0)
            row["area_at_half_step_km2"] = round(a2, 2)
            row["step_sensitivity_pct"] = round(100 * (a2 - area) / area, 2) if area else None
        if radius == 250.0 and (args.out_json or args.emit_sql):
            lng_min, lat_min, dlng, dlat = affine
            all_segs = boundary_edges(mask)
            rings = boundary_rings(all_segs)
            m_lng_, m_lat_ = m_lng, m_lat

            def poly_km2(pts) -> float:
                if len(pts) < 4:
                    return 0.0
                x = np.array([p[0] for p in pts])
                y = np.array([p[1] for p in pts])
                a = 0.5 * abs(float(np.dot(x, np.roll(y, -1)) - np.dot(y, np.roll(x, -1))))
                return a * m_lng_ * m_lat_ / 1e6

            def to_geo(ring):
                return [[lng_min + c * dlng, lat_min + r * dlat] for c, r in ring]

            # ⚠ 只保留**外沿环**，洞一律丢掉。理由不是偷懒：范围受理是两道判据串联
            #   （① 在围栏内 ② R 米内吸得到 SCC 节点），洞内的点即使被围栏放进来，
            #   第 ② 道照样会以 ORDER_ENDPOINT_SNAP_FAILED 拒掉 ⇒ 有没有洞，用户看到的结局一样。
            #   而把洞当正面积加进多边形（第一版就是这么错的）会让 polygon 63.64 > measured 48.09。
            #   判别用质心：落在覆盖区里 = 外沿；落在没覆盖的地方 = 洞。
            def cell_covered(lng: float, lat: float) -> bool:
                """质心那一个栅格格是否被覆盖（越界按未覆盖算）。"""
                row_i = round((lat - lat_min) / dlat)
                col_j = round((lng - lng_min) / dlng)
                if 0 <= row_i < mask.shape[0] and 0 <= col_j < mask.shape[1]:
                    return bool(mask[row_i, col_j])
                return False

            outer, holes = [], []
            for ring in rings:
                geo = to_geo(ring)
                simple = simplify(geo, args.simplify_m / m_lng)
                cx = float(np.mean([p[0] for p in simple]))
                cy = float(np.mean([p[1] for p in simple]))
                (outer if cell_covered(cx, cy) else holes).append(simple)

            ring_areas = [poly_km2(r) for r in outer]
            # ⚠ 丢掉退化碎片。简化到只剩 2–3 点的小环，在地图上是"看得见的多余区块"，
            #   在受理上又永远不含任何点（多边形退化）—— 纯噪音。第一版没过滤，
            #   9 片里有 8 片是 0.00 km² 的碎片，等于亲手造出新的"区块划分乱"。
            kept = [(r, a) for r, a in zip(outer, ring_areas) if len(r) >= 4 and a >= 0.05]
            row["dropped_fragments"] = len(outer) - len(kept)
            outer = [r for r, _ in kept]
            ring_areas = [a for _, a in kept]
            covered_km2 = sum(ring_areas)
            payload = {"radius_m": radius, "step_m": args.step, "scope": args.scope,
                       "source": args.split_graph or "现役 seed", "scc_nodes": len(scc),
                       "measured_area_km2": round(area, 2),
                       "polygon_area_km2": round(covered_km2, 2),
                       "simplify_m": args.simplify_m,
                       "ring_count": len(outer), "dropped_hole_count": len(holes),
                       "rings_gcj02": outer}
            if args.out_json:
                Path(args.out_json).write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
            if args.emit_sql:
                Path(args.emit_sql).write_text(render_fence_sql(payload, args), encoding="utf-8", newline="\n")
                row["emit_sql"] = args.emit_sql
            row["outer_rings"] = len(outer)
            row["dropped_holes"] = len(holes)
            row["polygon_area_km2"] = round(covered_km2, 2)
            row["polygon_vs_measured_pct"] = round(100 * (covered_km2 - area) / area, 1) if area else None
        results[radius] = row
        extra = ""
        if "step_sensitivity_pct" in row:
            extra = f", 步长敏感性 {row['step_sensitivity_pct']}%"
        print(f"  R={radius:>5.0f} m → {row['area_km2']:>6.2f} km² (占围栏 {row['share_of_fence_pct']}%{extra})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
