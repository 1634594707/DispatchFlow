#!/usr/bin/env python3
"""Compare V27 grid anchors vs V29 OSM-snapped coords against pilot_osm_geo.json roads."""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def haversine(p1, p2):
    r = 6371000
    lat1, lon1 = math.radians(p1[1]), math.radians(p1[0])
    lat2, lon2 = math.radians(p2[1]), math.radians(p2[0])
    dlat, dlon = lat2 - lat1, lon2 - lon1
    a = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def project_on_segment(p, a, b):
    ax, ay, bx, by, px, py = a[0], a[1], b[0], b[1], p[0], p[1]
    abx, aby = bx - ax, by - ay
    apx, apy = px - ax, py - ay
    ab2 = abx * abx + aby * aby
    if ab2 == 0:
        return a
    t = max(0, min(1, (apx * abx + apy * aby) / ab2))
    return (ax + t * abx, ay + t * aby)


def nearest_road(pt, roads):
    best_q, best_d = None, 1e9
    for poly in roads:
        for i in range(1, len(poly)):
            a, b = poly[i - 1], poly[i]
            q = project_on_segment(pt, a, b)
            d = haversine(pt, q)
            if d < best_d:
                best_q, best_d = q, d
    return best_q, best_d


def in_poly(pt, poly):
    x, y = pt
    inside = False
    for i in range(len(poly)):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % len(poly)]
        if ((y1 > y) != (y2 > y)) and (x < (x2 - x1) * (y - y1) / (y2 - y1 + 1e-15) + x1):
            inside = not inside
    return inside


def main():
    geo = json.loads((ROOT / "data/pilot_osm_geo.json").read_text(encoding="utf-8"))
    roads, buildings = geo["roads"], geo["buildings"]

    stations = {
        "ZJF-PICK-01": {"v27": (121.075160, 31.960424), "v29": (121.074453, 31.960396), "role": "pickup"},
        "ZJF-PICK-02": {"v27": (121.072682, 31.960646), "v29": (121.072682, 31.960646), "role": "pickup"},
        "ZJF-DROP-01": {"v27": (121.079152, 31.963523), "v29": (121.079762, 31.963627), "role": "dropoff"},
        "ZJF-DROP-02": {"v27": (121.088022, 31.961825), "v29": (121.088022, 31.961825), "role": "dropoff"},
        "ZJF-DROP-03": {"v27": (121.073200, 31.963523), "v29": (121.074367, 31.963548), "role": "dropoff"},
        "ZJF-DROP-04": {"v27": (121.084000, 31.961977), "v29": (121.084000, 31.961977), "role": "dropoff"},
        "ZJF-EXPRESS-01": {"v27": (121.073500, 31.960550), "v29": (121.073500, 31.960550), "role": "express"},
        "ZJF-IDLE-01": {"v27": (121.080354, 31.961977), "v29": (121.080354, 31.961977), "role": "idle"},
        "ZJF-CHG-01": {"v27": (121.079800, 31.961800), "v29": (121.079800, 31.961800), "role": "charging"},
        "ZJF-CHG-02": {"v27": (121.078500, 31.963300), "v29": (121.079780, 31.963518), "role": "charging"},
        "ZJF-CHG-03": {"v27": (121.073200, 31.960700), "v29": (121.073200, 31.960700), "role": "charging"},
        "ZJF-CHG-04": {"v27": (121.075160, 31.960700), "v29": (121.075160, 31.960700), "role": "charging"},
        "ZJF-CHG-05": {"v27": (121.084500, 31.961900), "v29": (121.084500, 31.961900), "role": "charging"},
    }

    print(f"{'code':14} {'role':8} {'v27_m':>7} {'v29_m':>7} {'bld':>3}  recommended_snap (GCJ-02)")
    print("-" * 95)
    for code, s in sorted(stations.items()):
        for label in ("v27", "v29"):
            pt = s[label]
            q, d = nearest_road(pt, roads)
            s[f"{label}_road"] = d
            s[f"{label}_snap"] = q
            s[f"{label}_bld"] = any(in_poly(pt, b) for b in buildings)
        bld = "Y" if s["v29_bld"] else "N"
        snap = s["v29_snap"]
        flag = " ***" if s["v29_road"] > 25 else (" *" if s["v29_road"] > 12 else "")
        print(
            f"{code:14} {s['role']:8} {s['v27_road']:7.1f} {s['v29_road']:7.1f} {bld:>3}  "
            f"{snap[0]:.6f},{snap[1]:.6f} ({s['v29_road']:.1f}m){flag}"
        )


if __name__ == "__main__":
    main()
