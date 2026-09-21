"""空间查询能力：最近站点召回、节点吸附、距离口径。

点位与站点编码全部来自真实 ZJF 种子数据（V26/V28/V37/V38 迁移），不是编造坐标。
"""

from __future__ import annotations

import math

import pytest

from fsd_geo import spatial

# 家纺城核心南排区围栏质心（V37 ZJF-ZONE-CORE-SOUTH 十顶点的算术平均）
SOUTH_CORE = (121.076176, 31.960776)


def test_nearest_stations_sorted_ascending() -> None:
    hits = spatial.nearest_stations(1, *SOUTH_CORE, limit=5, radius_m=20000)
    assert hits, "南排区附近应有站点"
    dists = [h.distance_m for h in hits]
    assert dists == sorted(dists)
    assert len(dists) <= 5


def test_charging_only_returns_only_charging_stations() -> None:
    hits = spatial.nearest_stations(1, *SOUTH_CORE, limit=10, radius_m=50000, charging_only=True)
    assert hits
    assert all(h.is_charging for h in hits)
    assert all("CHG" in h.station_code for h in hits)


def test_radius_cutoff_excludes_far_stations() -> None:
    """半径从 200m 收到 50m，结果必须单调不增——验证 ST_DWithin 真的在裁剪。"""
    wide = spatial.nearest_stations(1, *SOUTH_CORE, limit=10, radius_m=200.0)
    tight = spatial.nearest_stations(1, *SOUTH_CORE, limit=10, radius_m=50.0)
    assert len(tight) <= len(wide)
    assert all(h.distance_m <= 200.0 for h in wide)


def test_sphere_distance_matches_java_haversine() -> None:
    """`ST_DistanceSphere` 与 Java 球面 Haversine 应几乎相等（同为球面、同 R=6371000）。

    容差 1cm：两者都是球面公式，差异只来自 PostGIS 内部对 geography 的处理精度。
    """
    lng1, lat1 = SOUTH_CORE
    lng2, lat2 = 121.071812, 31.960126
    _, sphere = spatial.distance_pair(lng1, lat1, lng2, lat2)
    java = spatial.haversine_meters(lng1, lat1, lng2, lat2)
    assert abs(sphere - java) < 0.01, f"球面口径不一致：postgis={sphere} java={java}"


def test_spheroid_vs_sphere_delta_is_small_but_nonzero() -> None:
    """椭球面与球面在园区尺度上差千分之几——这正是「换 PostGIS 会不会改数值」的答案。"""
    lng1, lat1 = SOUTH_CORE
    lng2, lat2 = 121.071812, 31.960126
    spheroid, sphere = spatial.distance_pair(lng1, lat1, lng2, lat2)
    assert spheroid != sphere
    rel = abs(spheroid - sphere) / sphere
    assert 0 < rel < 0.005, f"相对差 {rel:.5f} 超出预期区间"


def test_nearest_road_node_snaps_to_expected_street() -> None:
    """南排街（lat≈31.9606）上的点应吸附到南排街节点，而不是金洲大道（lat 31.9600）。"""
    hit = spatial.nearest_road_node(1, 121.0750, 31.96060, radius_m=500)
    assert hit is not None
    code, distance_m = hit
    assert code == "RN07", f"期望绣女路×南排街 RN07，实得 {code}"
    assert distance_m < 20


def test_road_node_beyond_radius_returns_none() -> None:
    """半径收窄到 0.5m 时，落在路网外的点应吸附不到节点。

    注意别用 (121.0750, 31.96060)——那是 RN07 自身的坐标，距离恰为 0，会误判成 bug。
    """
    assert spatial.nearest_road_node(1, 121.0750, 31.96060, radius_m=0.5) is not None  # 正点在节点上
    assert spatial.nearest_road_node(1, 121.0750, 31.96065, radius_m=0.5) is None  # 偏 5.5m


def test_haversine_reference_impl_agrees_with_closed_form() -> None:
    """对照一个独立算法（大圆向量法），防参考实现本身写错。"""
    cases = [(121.076176, 31.960776, 121.071812, 31.960126), (0.0, 0.0, 0.0, 1.0), (121.0, 31.0, 121.5, 31.5)]
    r = spatial.HAVERSINE_EARTH_RADIUS_M
    for lng1, lat1, lng2, lat2 in cases:
        p1, p2 = math.radians(lat1), math.radians(lat2)
        dl = math.radians(lng2 - lng1)
        v = math.acos(
            max(-1.0, min(1.0,
                math.cos(p1) * math.cos(p2) * math.cos(dl) + math.sin(p1) * math.sin(p2)))
        ) * r
        assert abs(spatial.haversine_meters(lng1, lat1, lng2, lat2) - v) < 1e-6


@pytest.mark.parametrize("limit", [1, 3, 5])
def test_limit_is_respected(limit: int) -> None:
    assert len(spatial.nearest_stations(1, *SOUTH_CORE, limit=limit, radius_m=50000)) <= limit
