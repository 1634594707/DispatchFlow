"""坐标系守卫：非 GCJ-02 输入必须被拒绝。

这条守卫是本项目最重要的一道防线——库内几何按 GCJ-02 存储，
若允许 WGS-84 坐标直接进来，距离与包含关系会静默偏移约 300~700m，
而**不会报任何错**。
"""

from __future__ import annotations

import pytest

from fsd_geo.datum import Datum, UnsupportedDatum, require_stored_datum, validate_lng_lat


def test_gcj02_passes() -> None:
    assert require_stored_datum(Datum.GCJ02) is Datum.GCJ02


@pytest.mark.parametrize("datum", [Datum.WGS84, Datum.BD09])
def test_other_datums_rejected_with_actionable_hint(datum: Datum) -> None:
    with pytest.raises(UnsupportedDatum) as exc:
        require_stored_datum(datum)
    msg = str(exc.value)
    assert "Wgs84Gcj02Converter" in msg, "报错必须指向已有的换算入口，而不是只说「不支持」"
    assert datum.value in msg


def test_error_carries_the_offending_datum() -> None:
    with pytest.raises(UnsupportedDatum) as exc:
        require_stored_datum(Datum.WGS84)
    assert exc.value.datum is Datum.WGS84


@pytest.mark.parametrize(
    "lng,lat",
    [(181.0, 31.9), (-181.0, 31.9), (121.0, 91.0), (121.0, -91.0), (121.0, float("nan"))],
)
def test_out_of_range_rejected(lng: float, lat: float) -> None:
    with pytest.raises(ValueError):
        validate_lng_lat(lng, lat)


def test_real_zjf_point_passes_range_check() -> None:
    """叠石桥主市场 GCJ-02 坐标（原 docs/坐标基准-叠石桥家纺城.md §2，该文档已删除；
    现由本模块 STORED_DATUM 与本用例共同钉住）。"""
    validate_lng_lat(121.076301, 31.966722)
