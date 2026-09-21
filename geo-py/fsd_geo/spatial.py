"""空间查询：PostGIS 实现 + 与 Java 手算口径的对照。

三条能力，逐条对应 DispatchFlow 现有的 Java 实现：

| 能力 | PostGIS | 原 Java |
|---|---|---|
| 点在围栏内 | `ST_Contains` | `GeoPolygonUtils.contains()` 射线法（度平面） |
| 最近站点召回 | GiST + `ST_DWithin` + `<->` KNN | 全表扫描逐个 `haversineMeters` 取最小 |
| 球面距离 | `ST_Distance(geography)` | `haversineMeters()` 球面 R=6371000 |
"""

from __future__ import annotations

import math
from dataclasses import dataclass

from . import db

# 与 Java GeoPolygonUtils.haversineMeters 完全一致：球面、R=6371000m
HAVERSINE_EARTH_RADIUS_M = 6_371_000.0

_FENCE_COLUMNS = "fence_code, fence_name, fence_type"


@dataclass(frozen=True)
class StationHit:
    station_code: str
    station_name: str
    is_charging: bool
    distance_m: float


def haversine_meters(lng1: float, lat1: float, lng2: float, lat2: float) -> float:
    """Java 侧口径的 Python 复刻，仅用于对照报告，不参与线上判定。"""
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    d_phi = phi2 - phi1
    d_lam = math.radians(lng2 - lng1)
    h = math.sin(d_phi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(d_lam / 2) ** 2
    return 2 * HAVERSINE_EARTH_RADIUS_M * math.asin(math.sqrt(h))


def fences_containing(park_id: int, lng: float, lat: float) -> list[tuple[str, str, str]]:
    """返回包含该点的 ACTIVE 围栏。

    用 `ST_Contains` 而非 `ST_Covers`：前者把「点恰好落在边界线上」判为**不包含**，
    与 Java 射线法在边界点上的行为一致（`(yi > y) != (yj > y)` 在共线时不翻转）。
    两边同口径，对照才有意义。
    """
    return db.query(
        f"""
        SELECT {_FENCE_COLUMNS}
          FROM geofence
         WHERE park_id = %s
           AND status = 'ACTIVE'
           AND ST_Contains(geom, ST_SetSRID(ST_MakePoint(%s, %s), 4326))
         ORDER BY fence_code
        """,
        (park_id, lng, lat),
    )


def fences_intersecting_segment(
    park_id: int, lng_a: float, lat_a: float, lng_b: float, lat_b: float
) -> list[str]:
    """线段是否穿过围栏，对应 Java `segmentIntersectsPolygon`。"""
    return [
        row[0]
        for row in db.query(
            """
            SELECT fence_code
              FROM geofence
             WHERE park_id = %s
               AND status = 'ACTIVE'
               AND ST_Intersects(
                     geom,
                     ST_SetSRID(ST_MakeLine(ST_MakePoint(%s, %s), ST_MakePoint(%s, %s)), 4326))
             ORDER BY fence_code
            """,
            (park_id, lng_a, lat_a, lng_b, lat_b),
        )
    ]


def nearest_stations(
    park_id: int,
    lng: float,
    lat: float,
    *,
    limit: int = 5,
    radius_m: float = 5000.0,
    charging_only: bool = False,
) -> list[StationHit]:
    """半径内最近 N 个站点。

    **单位陷阱**：`ST_DWithin` 作用在 `geometry` 上时，距离单位是 SRID 的单位——
    SRID 4326 就是**度**。所以半径必须走 `::geography`（单位：米），
    否则 `radius_m=200` 会被当成 200 度（≈55 公里），裁剪彻底失效。
    KNN 排序同样用 geography 的 `<->`，保证「排序距离」和「返回距离」同一口径。

    替代 Java 那边「把园区站点全捞出来、逐个 haversine 取最小」的线性扫描。
    距离用 `ST_DistanceSphere`（球面 R=6371000），与 Java 同口径，差异只来自算法本身。
    """
    pt = "ST_SetSRID(ST_MakePoint(%s, %s), 4326)::geography"
    sql = f"""
        SELECT station_code, station_name, is_charging,
               ST_DistanceSphere(geom, (ST_SetSRID(ST_MakePoint(%s, %s), 4326))) AS distance_m
          FROM station
         WHERE park_id = %s
           AND status = 'ACTIVE'
           AND ST_DWithin(geom::geography, {pt}, %s)
        """
    params: list[object] = [lng, lat, park_id, lng, lat, radius_m]
    if charging_only:
        sql += "\n           AND is_charging\n"
    sql += f"""
         ORDER BY geom::geography <-> {pt}
         LIMIT %s
        """
    params += [lng, lat, limit]
    rows = db.query(sql, params)
    return [StationHit(r[0], r[1], r[2], float(r[3])) for r in rows]


def distance_pair(lng1: float, lat1: float, lng2: float, lat2: float) -> tuple[float, float]:
    """同一对坐标的两种算法结果：(PostGIS 椭球面, PostGIS 球面)。"""
    row = db.query_one(
        """
        SELECT ST_Distance(g1::geography, g2::geography),
               ST_DistanceSphere(g1, g2)
          FROM (SELECT ST_SetSRID(ST_MakePoint(%s, %s), 4326) AS g1,
                       ST_SetSRID(ST_MakePoint(%s, %s), 4326) AS g2) t
        """,
        (lng1, lat1, lng2, lat2),
    )
    assert row is not None
    return float(row[0]), float(row[1])


def nearest_road_node(
    park_id: int, lng: float, lat: float, *, radius_m: float = 300.0
) -> tuple[str, float] | None:
    """最近路网节点吸附，对应 Java 侧「起终点吸附最近路网节点」。

    半径同样必须走 `::geography`，理由见 `nearest_stations` 的单位陷阱说明。
    """
    pt = "ST_SetSRID(ST_MakePoint(%s, %s), 4326)::geography"
    row = db.query_one(
        f"""
        SELECT node_code, ST_DistanceSphere(geom, ST_SetSRID(ST_MakePoint(%s, %s), 4326))
          FROM road_node
         WHERE park_id = %s
           AND ST_DWithin(geom::geography, {pt}, %s)
         ORDER BY geom::geography <-> {pt}
         LIMIT 1
        """,
        (lng, lat, park_id, lng, lat, radius_m, lng, lat),
    )
    return (row[0], float(row[1])) if row else None
