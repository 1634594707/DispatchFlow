#!/usr/bin/env python3
"""Extract GCJ-02 building polygons and road polylines from data/map.osm for L1 pilot routing."""

from __future__ import annotations

import json
import math
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OSM_PATH = ROOT / "data" / "map.osm"
OUT_PATH = ROOT / "data" / "pilot_osm_geo.json"

PILOT_BOUNDS = {
    "min_lng": 121.072051,
    "max_lng": 121.088674,
    "min_lat": 31.959885,
    "max_lat": 31.964101,
}

ROAD_HIGHWAY_TYPES = {
    "residential",
    "service",
    "secondary",
    "tertiary",
    "unclassified",
    "living_street",
    "primary",
}


def wgs84_to_gcj02(lng: float, lat: float) -> tuple[float, float]:
    a = 6378245.0
    ee = 0.00669342162296594323

    def transform_lat(x: float, y: float) -> float:
        ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * math.sqrt(abs(x))
        ret += (20.0 * math.sin(6.0 * x * math.pi) + 20.0 * math.sin(2.0 * x * math.pi)) * 2.0 / 3.0
        ret += (20.0 * math.sin(y * math.pi) + 40.0 * math.sin(y / 3.0 * math.pi)) * 2.0 / 3.0
        ret += (160.0 * math.sin(y / 12.0 * math.pi) + 320 * math.sin(y * math.pi / 30.0)) * 2.0 / 3.0
        return ret

    def transform_lng(x: float, y: float) -> float:
        ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * math.sqrt(abs(x))
        ret += (20.0 * math.sin(6.0 * x * math.pi) + 20.0 * math.sin(2.0 * x * math.pi)) * 2.0 / 3.0
        ret += (20.0 * math.sin(x * math.pi) + 40.0 * math.sin(x / 3.0 * math.pi)) * 2.0 / 3.0
        ret += (150.0 * math.sin(x / 12.0 * math.pi) + 300.0 * math.sin(x / 30.0 * math.pi)) * 2.0 / 3.0
        return ret

    if lng < 72 or lng > 137.7 or lat < 0.29 or lat > 55.83:
        return lng, lat
    dlat = transform_lat(lng - 105.0, lat - 35.0)
    dlng = transform_lng(lng - 105.0, lat - 35.0)
    radlat = lat / 180.0 * math.pi
    magic = 1 - ee * math.sin(radlat) ** 2
    sqrtmagic = math.sqrt(magic)
    dlat = (dlat * 180.0) / ((a * (1 - ee)) / (magic * sqrtmagic) * math.pi)
    dlng = (dlng * 180.0) / (a / sqrtmagic * math.cos(radlat) * math.pi)
    return round(lng + dlng, 6), round(lat + dlat, 6)


def in_pilot(lng: float, lat: float) -> bool:
    return (
        PILOT_BOUNDS["min_lng"] <= lng <= PILOT_BOUNDS["max_lng"]
        and PILOT_BOUNDS["min_lat"] <= lat <= PILOT_BOUNDS["max_lat"]
    )


def poly_intersects_pilot(points: list[tuple[float, float]]) -> bool:
    for lng, lat in points:
        if in_pilot(lng, lat):
            return True
    min_lng = min(p[0] for p in points)
    max_lng = max(p[0] for p in points)
    min_lat = min(p[1] for p in points)
    max_lat = max(p[1] for p in points)
    return not (
        max_lng < PILOT_BOUNDS["min_lng"]
        or min_lng > PILOT_BOUNDS["max_lng"]
        or max_lat < PILOT_BOUNDS["min_lat"]
        or min_lat > PILOT_BOUNDS["max_lat"]
    )


def parse_osm() -> tuple[list[list[list[float]]], list[list[list[float]]]]:
    root = ET.parse(OSM_PATH).getroot()
    nodes: dict[str, tuple[float, float]] = {}
    for node in root.findall("node"):
        wgs_lng = float(node.attrib["lon"])
        wgs_lat = float(node.attrib["lat"])
        gcj_lng, gcj_lat = wgs84_to_gcj02(wgs_lng, wgs_lat)
        nodes[node.attrib["id"]] = (gcj_lng, gcj_lat)

    buildings: list[list[list[float]]] = []
    roads: list[list[list[float]]] = []

    for way in root.findall("way"):
        tags = {tag.attrib["k"]: tag.attrib["v"] for tag in way.findall("tag")}
        refs = [nd.attrib["ref"] for nd in way.findall("nd")]
        coords = [nodes[r] for r in refs if r in nodes]
        if len(coords) < 2:
            continue

        if "building" in tags:
            if len(coords) < 3:
                continue
            if not poly_intersects_pilot(coords):
                continue
            ring = [[round(lng, 6), round(lat, 6)] for lng, lat in coords]
            if ring[0] != ring[-1]:
                ring.append(ring[0])
            buildings.append(ring)
            continue

        highway = tags.get("highway")
        if highway in ROAD_HIGHWAY_TYPES:
            if not poly_intersects_pilot(coords):
                continue
            polyline = [[round(lng, 6), round(lat, 6)] for lng, lat in coords]
            if len(polyline) >= 2:
                roads.append(polyline)

    return buildings, roads


def main() -> None:
    buildings, roads = parse_osm()
    payload = {
        "scenario": "ZJF_DIESHIQIAO_PILOT",
        "source": "data/map.osm",
        "crs": "GCJ-02",
        "pilot_bounds": PILOT_BOUNDS,
        "buildings": buildings,
        "roads": roads,
        "stats": {"building_count": len(buildings), "road_segment_count": len(roads)},
    }
    OUT_PATH.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {OUT_PATH} — {len(buildings)} buildings, {len(roads)} road polylines")


if __name__ == "__main__":
    main()
