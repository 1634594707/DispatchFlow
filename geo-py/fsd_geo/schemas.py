"""请求/响应模型。"""

from __future__ import annotations

from pydantic import BaseModel, Field

from .datum import Datum


class LngLat(BaseModel):
    lng: float = Field(..., ge=-180, le=180)
    lat: float = Field(..., ge=-90, le=90)


class DatumRequest(LngLat):
    """所有查询都必须声明坐标系，非 GCJ-02 直接 400。"""

    datum: Datum = Datum.GCJ02


class FenceCheckRequest(DatumRequest):
    park_id: int


class StationNearbyRequest(DatumRequest):
    park_id: int
    limit: int = Field(5, ge=1, le=50)
    radius_m: float = Field(5000.0, gt=0, le=50000)
    charging_only: bool = False


class SegmentRequest(BaseModel):
    a: LngLat
    b: LngLat
    park_id: int
    datum: Datum = Datum.GCJ02


class DistanceRequest(BaseModel):
    a: LngLat
    b: LngLat
    datum: Datum = Datum.GCJ02


class RoadNodeRequest(DatumRequest):
    park_id: int
    radius_m: float = Field(300.0, gt=0, le=5000)


class StationHitOut(BaseModel):
    station_code: str
    station_name: str
    is_charging: bool
    distance_m: float


class DistanceComparison(BaseModel):
    postgis_spheroid_m: float
    postgis_sphere_m: float
    java_haversine_m: float
    sphere_vs_java_delta_m: float
    spheroid_vs_java_delta_m: float
