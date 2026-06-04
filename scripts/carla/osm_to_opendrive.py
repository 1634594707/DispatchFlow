#!/usr/bin/env python3
"""Convert data/map.osm to CARLA-ready OpenDRIVE with a local origin at the pilot center."""

from __future__ import annotations

import argparse
import json
import math
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

try:
    from pyproj import Transformer
except ImportError as exc:  # pragma: no cover
    raise SystemExit("Missing dependency: pip install pyproj lxml") from exc

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_OSM = ROOT / "data" / "map.osm"
DEFAULT_OUT = ROOT / "data" / "carla"
DEFAULT_ORIGIN_WGS = (121.075690, 31.963782)
UTM_EPSG = "EPSG:32651"  # WGS-84 / UTM zone 51N (Nantong)


@dataclass(frozen=True)
class OriginInfo:
    wgs_lng: float
    wgs_lat: float
    utm_e: float
    utm_n: float
    sumo_x: float
    sumo_y: float
    gcj_lng: float
    gcj_lat: float


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
    return lng + dlng, lat + dlat


def parse_net_offset(net_xml: Path) -> tuple[float, float]:
    root = ET.parse(net_xml).getroot()
    location = root.find("location")
    if location is None or "netOffset" not in location.attrib:
        raise RuntimeError(f"Missing <location netOffset> in {net_xml}")
    ox, oy = location.attrib["netOffset"].split(",")
    return float(ox), float(oy)


def compute_origin(wgs_lng: float, wgs_lat: float, net_offset: tuple[float, float]) -> OriginInfo:
    transformer = Transformer.from_crs("EPSG:4326", UTM_EPSG, always_xy=True)
    utm_e, utm_n = transformer.transform(wgs_lng, wgs_lat)
    sumo_x = utm_e + net_offset[0]
    sumo_y = utm_n + net_offset[1]
    gcj_lng, gcj_lat = wgs84_to_gcj02(wgs_lng, wgs_lat)
    return OriginInfo(
        wgs_lng=wgs_lng,
        wgs_lat=wgs_lat,
        utm_e=utm_e,
        utm_n=utm_n,
        sumo_x=sumo_x,
        sumo_y=sumo_y,
        gcj_lng=gcj_lng,
        gcj_lat=gcj_lat,
    )


def find_netconvert() -> list[str] | None:
    for candidate in ("netconvert", "netconvert.exe"):
        if shutil.which(candidate):
            return [candidate]
    if shutil.which("docker"):
        return [
            "docker",
            "run",
            "--rm",
            "-v",
            f"{ROOT / 'data'}:/data",
            "mgjm/sumo:latest",
            "netconvert",
        ]
    return None


def run_netconvert(osm_path: Path, out_dir: Path, prefix: str) -> tuple[Path, Path]:
    out_dir.mkdir(parents=True, exist_ok=True)
    net_xml = out_dir / f"{prefix}.net.xml"
    raw_xodr = out_dir / f"{prefix}_raw.xodr"
    prefix_cmd = find_netconvert()
    if prefix_cmd is None:
        raise RuntimeError(
            "netconvert not found. Install SUMO or Docker, then re-run this script."
        )

    osm_in_container = f"/data/{osm_path.relative_to(ROOT / 'data').as_posix()}"
    if prefix_cmd[0] == "docker":
        net_out = f"/data/carla/{net_xml.name}"
        xodr_out = f"/data/carla/{raw_xodr.name}"
        osm_arg = osm_in_container
    else:
        net_out = str(net_xml)
        xodr_out = str(raw_xodr)
        osm_arg = str(osm_path)

    cmd = [
        *prefix_cmd,
        "--osm-files",
        osm_arg,
        "--proj.utm",
        "--output-file",
        net_out,
        "--opendrive-output",
        xodr_out,
        "--geometry.remove",
        "--roundabouts.guess",
        "--ramps.guess",
        "--junctions.join",
        "--tls.guess-signals",
        "--tls.discard-simple",
        "--tls.join",
        "--no-turnarounds",
        "--keep-edges.by-vclass",
        "passenger,bus,truck,delivery",
    ]
    print("Running:", " ".join(cmd))
    subprocess.run(cmd, check=True, cwd=ROOT)
    if not net_xml.exists() or not raw_xodr.exists():
        raise RuntimeError("netconvert finished but expected output files are missing")
    return net_xml, raw_xodr


def _shift_attr(element: ET.Element, attr: str, dx: float, dy: float) -> None:
    if attr not in element.attrib:
        return
    value = float(element.attrib[attr])
    if attr == "x":
        element.attrib[attr] = f"{value - dx:.8f}"
    elif attr == "y":
        element.attrib[attr] = f"{value - dy:.8f}"


def recenter_opendrive(raw_xodr: Path, out_xodr: Path, origin: OriginInfo) -> dict[str, float]:
    dx, dy = origin.sumo_x, origin.sumo_y
    text = raw_xodr.read_text(encoding="utf-8")
    tree = ET.ElementTree(ET.fromstring(text))
    root = tree.getroot()

    xs: list[float] = []
    ys: list[float] = []

    for geometry in root.iter("geometry"):
        _shift_attr(geometry, "x", dx, dy)
        xs.append(float(geometry.attrib["x"]))
        ys.append(float(geometry.attrib["y"]))

    for obj in root.iter("object"):
        _shift_attr(obj, "x", dx, dy)
        if "x" in obj.attrib:
            xs.append(float(obj.attrib["x"]))
        if "y" in obj.attrib:
            ys.append(float(obj.attrib["y"]))

    for signal in root.iter("signal"):
        _shift_attr(signal, "x", dx, dy)
        if "x" in signal.attrib:
            xs.append(float(signal.attrib["x"]))
        if "y" in signal.attrib:
            ys.append(float(signal.attrib["y"]))

    for lane in root.iter("lane"):
        if lane.attrib.get("type") == "restricted":
            lane.attrib["type"] = "driving"

    header = root.find("header")
    if header is not None and xs and ys:
        pad = 20.0
        header.attrib["west"] = f"{min(xs) - pad:.2f}"
        header.attrib["east"] = f"{max(xs) + pad:.2f}"
        header.attrib["south"] = f"{min(ys) - pad:.2f}"
        header.attrib["north"] = f"{max(ys) + pad:.2f}"

    geo_ref = (
        f"+proj=tmerc +lat_0={origin.wgs_lat:.8f} +lon_0={origin.wgs_lng:.8f} "
        f"+k=1 +x_0=0 +y_0=0 +datum=WGS84 +units=m +no_defs"
    )
    if header is not None:
        header.attrib["geoReference"] = geo_ref

    tree.write(out_xodr, encoding="utf-8", xml_declaration=True)

    # Pretty-print is optional; ensure XML declaration matches CARLA expectations.
    normalized = out_xodr.read_text(encoding="utf-8")
    if not normalized.startswith("<?xml"):
        out_xodr.write_text('<?xml version="1.0" encoding="utf-8"?>\n' + normalized, encoding="utf-8")

    return {
        "min_x": min(xs),
        "max_x": max(xs),
        "min_y": min(ys),
        "max_y": max(ys),
    }


def write_manifest(out_dir: Path, origin: OriginInfo, bounds: dict[str, float]) -> Path:
    manifest = {
        "scenario": "ZJF_DIESHIQIAO_PILOT",
        "label": "找家纺网 · 叠石桥短驳试点",
        "source_osm": "data/map.osm",
        "opendrive": "data/carla/zjf_pilot.xodr",
        "net_xml": "data/carla/zjf_pilot.net.xml",
        "origin": {
            "wgs84": {"lng": origin.wgs_lng, "lat": origin.wgs_lat},
            "gcj02": {"lng": origin.gcj_lng, "lat": origin.gcj_lat},
            "utm51n": {"easting": origin.utm_e, "northing": origin.utm_n},
            "local": {"x_m": 0.0, "y_m": 0.0},
        },
        "local_bounds_m": bounds,
        "pilot_polygon_wgs84": [
            [121.06740, 31.96170],
            [121.08400, 31.96170],
            [121.08400, 31.96590],
            [121.06740, 31.96590],
        ],
        "dispatchflow": {
            "anchor_gcj02": [origin.gcj_lng, origin.gcj_lat],
            "park_width_meters": 1570,
            "park_height_meters": 470,
        },
        "carla": {
            "package_name": "ZjfDieshiqiaoPilot",
            "map_name": "ZjfDieshiqiaoPilot",
            "standalone_xodr": True,
            "notes": "Use scripts/carla/load_in_carla.py for OpenDRIVE standalone mode, or make import if FBX mesh is added later.",
        },
    }
    path = out_dir / "manifest.json"
    path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return path


def write_carla_import_json(out_dir: Path) -> Path:
    package_dir = out_dir / "import_package" / "ZjfDieshiqiaoPilot"
    package_dir.mkdir(parents=True, exist_ok=True)
    xodr_target = package_dir / "ZjfDieshiqiaoPilot.xodr"
    shutil.copy2(out_dir / "zjf_pilot.xodr", xodr_target)
    import_json = {
        "maps": [
            {
                "name": "ZjfDieshiqiaoPilot",
                "xodr": "./ZjfDieshiqiaoPilot/ZjfDieshiqiaoPilot.xodr",
                "use_carla_materials": True,
            }
        ],
        "props": [],
    }
    json_path = out_dir / "import_package" / "ZjfDieshiqiaoPilot.json"
    json_path.write_text(json.dumps(import_json, indent=2) + "\n", encoding="utf-8")
    readme = out_dir / "import_package" / "README.txt"
    readme.write_text(
        "\n".join(
            [
                "CARLA source-build import (requires .fbx mesh for full Unreal map):",
                "1. Copy this folder to <CARLA_ROOT>/Import/ZjfDieshiqiaoPilot",
                "2. From CARLA root: make import ARGS=\"--package=ZjfDieshiqiaoPilot\"",
                "",
                "For quick testing without FBX, use OpenDRIVE standalone mode:",
                "  python scripts/carla/load_in_carla.py",
                "",
                "Note: xodr-only import via make import still needs matching FBX tiles.",
                "Standalone mode generates road mesh inside CARLA at runtime.",
            ]
        )
        + "\n",
        encoding="utf-8",
    )
    return json_path


def main() -> int:
    parser = argparse.ArgumentParser(description="Convert OSM to CARLA-ready OpenDRIVE")
    parser.add_argument("--osm", type=Path, default=DEFAULT_OSM)
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT)
    parser.add_argument("--prefix", default="zjf_pilot")
    parser.add_argument("--origin-lng", type=float, default=DEFAULT_ORIGIN_WGS[0])
    parser.add_argument("--origin-lat", type=float, default=DEFAULT_ORIGIN_WGS[1])
    parser.add_argument("--skip-netconvert", action="store_true")
    args = parser.parse_args()

    if not args.osm.exists():
        print(f"OSM file not found: {args.osm}", file=sys.stderr)
        return 1

    args.out_dir.mkdir(parents=True, exist_ok=True)
    net_xml = args.out_dir / f"{args.prefix}.net.xml"
    raw_xodr = args.out_dir / f"{args.prefix}_raw.xodr"
    out_xodr = args.out_dir / f"{args.prefix}.xodr"

    if args.skip_netconvert:
        if not net_xml.exists() or not raw_xodr.exists():
            print("Missing net.xml/raw.xodr; run without --skip-netconvert first.", file=sys.stderr)
            return 1
    else:
        net_xml, raw_xodr = run_netconvert(args.osm, args.out_dir, args.prefix)

    net_offset = parse_net_offset(net_xml)
    origin = compute_origin(args.origin_lng, args.origin_lat, net_offset)
    bounds = recenter_opendrive(raw_xodr, out_xodr, origin)
    manifest = write_manifest(args.out_dir, origin, bounds)
    import_json = write_carla_import_json(args.out_dir)

    print("\nConversion complete.")
    print(f"  OpenDRIVE : {out_xodr}")
    print(f"  Manifest  : {manifest}")
    print(f"  Import pkg: {import_json.parent}")
    print(
        f"  Local origin (0,0) = WGS84 ({origin.wgs_lng:.6f}, {origin.wgs_lat:.6f}) "
        f"/ GCJ-02 ({origin.gcj_lng:.6f}, {origin.gcj_lat:.6f})"
    )
    print(
        f"  Local bounds (m): x=[{bounds['min_x']:.1f}, {bounds['max_x']:.1f}], "
        f"y=[{bounds['min_y']:.1f}, {bounds['max_y']:.1f}]"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
