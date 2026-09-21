"""FastAPI 应用：调度地理能力（PostGIS）。

Java 侧通过 HTTP 调用本服务；本服务不可用时 Java 侧降级回 `GeoPolygonUtils` 手算。
"""

from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException

from . import db, spatial
from .config import DbConfig
from .datum import STORED_DATUM, UnsupportedDatum, require_stored_datum
from .schemas import (
    DistanceComparison,
    DistanceRequest,
    FenceCheckRequest,
    RoadNodeRequest,
    SegmentRequest,
    StationHitOut,
    StationNearbyRequest,
)


@asynccontextmanager
async def _lifespan(_app: FastAPI) -> AsyncIterator[None]:
    db.init_pool(DbConfig.from_env())
    try:
        yield
    finally:
        db.close_pool()


app = FastAPI(title="FSD Geo Service (PostGIS)", version="0.1.0", lifespan=_lifespan)


@app.get("/health")
def health() -> dict[str, object]:
    row = db.query_one("SELECT postgis_version()")
    return {"ok": row is not None, "postgis": row[0] if row else None, "stored_datum": STORED_DATUM.value}


def _guard(request) -> None:
    try:
        require_stored_datum(request.datum)
    except UnsupportedDatum as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc


@app.post("/geo/fence/contains")
def fence_contains(req: FenceCheckRequest) -> dict[str, object]:
    _guard(req)
    fences = spatial.fences_containing(req.park_id, req.lng, req.lat)
    return {
        "inside_any": bool(fences),
        "fences": [{"code": c, "name": n, "type": t} for c, n, t in fences],
    }


@app.post("/geo/fence/segment-intersects")
def fence_segment(req: SegmentRequest) -> dict[str, object]:
    _guard(req)
    codes = spatial.fences_intersecting_segment(req.park_id, req.a.lng, req.a.lat, req.b.lng, req.b.lat)
    return {"crosses_any": bool(codes), "fence_codes": codes}


@app.post("/geo/station/nearby", response_model=list[StationHitOut])
def station_nearby(req: StationNearbyRequest) -> list[StationHitOut]:
    _guard(req)
    hits = spatial.nearest_stations(
        req.park_id,
        req.lng,
        req.lat,
        limit=req.limit,
        radius_m=req.radius_m,
        charging_only=req.charging_only,
    )
    return [StationHitOut(**h.__dict__) for h in hits]


@app.post("/geo/road-node/nearest")
def road_node_nearest(req: RoadNodeRequest) -> dict[str, object]:
    _guard(req)
    hit = spatial.nearest_road_node(req.park_id, req.lng, req.lat, radius_m=req.radius_m)
    if hit is None:
        return {"found": False}
    code, distance_m = hit
    return {"found": True, "node_code": code, "distance_m": distance_m}


@app.post("/geo/distance/compare", response_model=DistanceComparison)
def distance_compare(req: DistanceRequest) -> DistanceComparison:
    """同一坐标对跑三种算法，量化口径差异。

    这个端点是**对照实验用的**，不是给业务调的——它回答的是
    「Java 那边球面 Haversine 换成 PostGIS 会差多少」。
    """
    _guard(req)
    spheroid, sphere = spatial.distance_pair(req.a.lng, req.a.lat, req.b.lng, req.b.lat)
    java = spatial.haversine_meters(req.a.lng, req.a.lat, req.b.lng, req.b.lat)
    return DistanceComparison(
        postgis_spheroid_m=spheroid,
        postgis_sphere_m=sphere,
        java_haversine_m=java,
        sphere_vs_java_delta_m=sphere - java,
        spheroid_vs_java_delta_m=spheroid - java,
    )
