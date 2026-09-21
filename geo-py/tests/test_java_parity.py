"""PostGIS `ST_Contains` 与 Java 射线法的逐点等价性。

这是整个替换的**唯一硬证据**：如果两者在围栏边界附近判不一致，
把 Java 手算换成 PostGIS 就是行为变更，不能上。

Java 侧原文：`GeoPolygonUtils.contains(List<double[]> polygon, BigDecimal lng, BigDecimal lat)`
（`back/fsd-dispatch/src/main/java/com/fsd/dispatch/geo/GeoPolygonUtils.java:17`）
"""

from __future__ import annotations

import json

import pytest

from fsd_geo import spatial
from fsd_geo.config import DbConfig


def java_ray_cast_contains(ring: list[list[float]], lng: float, lat: float) -> bool:
    """`GeoPolygonUtils.contains` 的逐行等价移植，含那个 `+ 0.0d` 的除零保护。"""
    n = len(ring)
    if n < 3:
        return False
    inside = False
    j = n - 1
    for i in range(n):
        xi, yi = ring[i]
        xj, yj = ring[j]
        if (yi > lat) != (yj > lat) and lng < (xj - xi) * (lat - yi) / (yj - yi + 0.0) + xi:
            inside = not inside
        j = i
    return inside


def _fence_rings() -> list[tuple[str, list[list[float]]]]:
    """收集期就要拿到参数列表，所以这里直连而不走 session fixture 的连接池。"""
    try:
        import psycopg

        with psycopg.connect(DbConfig.from_env().dsn(), connect_timeout=3) as conn:
            rows = conn.execute(
                "SELECT fence_code, ST_AsGeoJSON(geom) FROM geofence "
                "WHERE status = 'ACTIVE' ORDER BY fence_code"
            ).fetchall()
    except Exception:
        return []
    return [(code, json.loads(geojson)["coordinates"][0]) for code, geojson in rows]


FENCES = _fence_rings()


def _bbox(ring: list[list[float]]):
    lngs = [p[0] for p in ring]
    lats = [p[1] for p in ring]
    return min(lngs), min(lats), max(lngs), max(lats)


@pytest.mark.skipif(not FENCES, reason="围栏表为空")
@pytest.mark.parametrize("code,ring", FENCES, ids=[c for c, _ in FENCES])
def test_contains_matches_java_on_dense_grid(code: str, ring: list[list[float]]) -> None:
    """外接矩形内 60x60 网格逐点比对，要求 100% 一致。

    网格而不是随机点：随机点在凸多边形上几乎全落在内部，测不出边界差异。
    """
    min_lng, min_lat, max_lng, max_lat = _bbox(ring)
    # 外扩一点，把「刚好在矩形内但多边形外」的否定样本也覆盖进来
    pad_lng, pad_lat = (max_lng - min_lng) * 0.1, (max_lat - min_lat) * 0.1
    min_lng -= pad_lng
    max_lng += pad_lng
    min_lat -= pad_lat
    max_lat += pad_lat

    steps = 60
    mismatches: list[str] = []
    for ix in range(steps):
        lng = min_lng + (max_lng - min_lng) * ix / (steps - 1)
        for iy in range(steps):
            lat = min_lat + (max_lat - min_lat) * iy / (steps - 1)
            expected = java_ray_cast_contains(ring, lng, lat)
            actual = any(f[0] == code for f in spatial.fences_containing(1, lng, lat))
            if expected != actual:
                mismatches.append(f"({lng:.6f},{lat:.6f}) java={expected} postgis={actual}")

    assert not mismatches, (
        f"{code}: {len(mismatches)}/{steps * steps} 个点判定不一致，前 5 例：{mismatches[:5]}"
    )


def test_exterior_point_is_outside_every_fence() -> None:
    """园区外 5 公里的点不应命中任何围栏。"""
    assert spatial.fences_containing(1, 121.0762, 31.9608 + 0.05) == []


def test_segment_intersection_matches_java_semantics() -> None:
    """穿入围栏的线段要被判为相交，完全在外部的线段不要。"""
    inside = spatial.fences_containing(1, 121.0762, 31.9608)
    assert inside, "前置条件：该点应在围栏内"
    fence_code = inside[0][0]
    far = spatial.fences_intersecting_segment(1, 121.0762, 31.9608, 121.20, 32.05)
    assert fence_code in far
    outside = spatial.fences_intersecting_segment(1, 121.30, 32.10, 121.31, 32.11)
    assert outside == []
