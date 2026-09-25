"""坐标系守卫。

**这里刻意不实现 GCJ-02 <-> WGS-84 换算。**

换算的唯一真相源是 Java 侧 `com.fsd.common.geo.Wgs84Gcj02Converter`（含单测）。
Python 服务再抄一份会产生两个可能漂移的实现——那正是本项目要防的「坐标/距离混用」问题
（原 `docs/坐标基准-叠石桥家纺城.md` 已随 2026-09-22 文档收敛删除；判据现由本模块的
`STORED_DATUM` 与 Java 侧 `com.fsd.common.geo.Wgs84Gcj02Converter` 承担）。

所以本模块只做一件事：**要求调用方显式声明传入坐标的坐标系**，
非 GCJ-02 一律拒绝，让调用方（Java 客户端）先用已有转换器换算好再来。
"""

from __future__ import annotations

from enum import StrEnum


class Datum(StrEnum):
    GCJ02 = "GCJ-02"
    WGS84 = "WGS-84"
    BD09 = "BD-09"


# 库内所有 geom 列的存储坐标系
STORED_DATUM = Datum.GCJ02

_REJECT_HINT = (
    "{datum} 坐标不能直接用于查询：库内几何以 GCJ-02 存储。"
    "请先在调用方用 com.fsd.common.geo.Wgs84Gcj02Converter 换算为 GCJ-02 再传入。"
    "（刻意不在本服务重复实现换算，避免两份实现漂移。）"
)


class UnsupportedDatum(ValueError):
    """传入坐标系与存储坐标系不一致。"""

    def __init__(self, datum: Datum) -> None:
        super().__init__(_REJECT_HINT.format(datum=datum.value))
        self.datum = datum


def require_stored_datum(datum: Datum) -> Datum:
    """校验调用方声明的坐标系是否可直接用于查询。"""
    if datum is not STORED_DATUM:
        raise UnsupportedDatum(datum)
    return datum


def validate_lng_lat(lng: float, lat: float) -> None:
    """经纬度范围校验。

    只校验合法区间，不校验「是否落在园区内」——落在园区外是合法输入
    （车辆越界正是要检出的情况），由围栏判断负责。
    """
    if not -180.0 <= lng <= 180.0:
        raise ValueError(f"经度越界：{lng}")
    if not -90.0 <= lat <= 90.0:
        raise ValueError(f"纬度越界：{lat}")
