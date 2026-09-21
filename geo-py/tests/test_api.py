"""HTTP 层：契约、降级语义、坐标系拒绝。"""

from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from fsd_geo.app import app

SOUTH_CORE = {"park_id": 1, "lng": 121.076176, "lat": 31.960776}


@pytest.fixture()
def client() -> TestClient:
    with TestClient(app) as c:
        yield c


def test_health_reports_postgis_version_and_stored_datum(client: TestClient) -> None:
    body = client.get("/health").json()
    assert body["ok"] is True
    assert body["stored_datum"] == "GCJ-02"
    assert "3.4" in body["postgis"] or "POSTGIS" in body["postgis"].upper()


def test_fence_contains_inside(client: TestClient) -> None:
    body = client.post("/geo/fence/contains", json=SOUTH_CORE).json()
    assert body["inside_any"] is True
    assert any(f["code"] == "ZJF-ZONE-CORE-SOUTH" for f in body["fences"])


def test_fence_contains_outside(client: TestClient) -> None:
    body = client.post("/geo/fence/contains", json={**SOUTH_CORE, "lat": 32.05}).json()
    assert body["inside_any"] is False
    assert body["fences"] == []


@pytest.mark.parametrize("datum", ["WGS-84", "BD-09"])
def test_wrong_datum_is_400_not_silent_wrong_answer(client: TestClient, datum: str) -> None:
    """坐标系错了必须显式失败。静默返回一个偏移 300~700m 的结果是最坏的行为。"""
    resp = client.post("/geo/fence/contains", json={**SOUTH_CORE, "datum": datum})
    assert resp.status_code == 400
    assert "Wgs84Gcj02Converter" in resp.json()["detail"]


def test_station_nearby_shape_and_ordering(client: TestClient) -> None:
    rows = client.post("/geo/station/nearby", json={**SOUTH_CORE, "limit": 3, "radius_m": 20000}).json()
    assert 0 < len(rows) <= 3
    assert [r["distance_m"] for r in rows] == sorted(r["distance_m"] for r in rows)
    assert {"station_code", "station_name", "is_charging", "distance_m"} <= set(rows[0])


def test_station_nearby_charging_only(client: TestClient) -> None:
    rows = client.post(
        "/geo/station/nearby", json={**SOUTH_CORE, "limit": 10, "radius_m": 50000, "charging_only": True}
    ).json()
    assert rows and all(r["is_charging"] for r in rows)


def test_distance_compare_reports_all_three_calibers(client: TestClient) -> None:
    body = client.post(
        "/geo/distance/compare",
        json={"a": {"lng": 121.076176, "lat": 31.960776}, "b": {"lng": 121.071812, "lat": 31.960126}},
    ).json()
    assert body["java_haversine_m"] > 0
    # 球面口径应与 Java 几乎相等；椭球口径应有可见但很小的差
    assert abs(body["sphere_vs_java_delta_m"]) < 0.01
    assert 0 < abs(body["spheroid_vs_java_delta_m"]) < body["java_haversine_m"] * 0.005


def test_road_node_not_found_is_not_an_error(client: TestClient) -> None:
    """半径内无节点是正常业务结果（车在路网外），不能返 5xx。"""
    body = client.post("/geo/road-node/nearest", json={**SOUTH_CORE, "radius_m": 0.5}).json()
    assert body == {"found": False}


def test_out_of_range_coordinates_rejected_by_schema(client: TestClient) -> None:
    assert client.post("/geo/fence/contains", json={"park_id": 1, "lng": 999, "lat": 31.9}).status_code == 422
