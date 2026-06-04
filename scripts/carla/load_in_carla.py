#!/usr/bin/env python3
"""Load the recentered OpenDRIVE map in CARLA standalone mode (no FBX required)."""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MANIFEST = ROOT / "data" / "carla" / "manifest.json"


def main() -> int:
    parser = argparse.ArgumentParser(description="Load ZJF pilot map in CARLA via OpenDRIVE")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=2000)
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--vertex-distance", type=float, default=2.0)
    parser.add_argument("--max-road-length", type=float, default=50.0)
    parser.add_argument("--wall-height", type=float, default=1.0)
    parser.add_argument("--extra-lane-width", type=float, default=0.3)
    args = parser.parse_args()

    if not args.manifest.exists():
        print(f"Manifest not found: {args.manifest}", file=sys.stderr)
        print("Run: python scripts/carla/osm_to_opendrive.py", file=sys.stderr)
        return 1

    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    xodr_path = ROOT / manifest["opendrive"]
    if not xodr_path.exists():
        print(f"OpenDRIVE not found: {xodr_path}", file=sys.stderr)
        return 1

    try:
        import carla  # type: ignore
    except ImportError:
        print(
            "CARLA Python API not found.\n"
            "Install CARLA and add PythonAPI/carla/dist/carla-*-py3*.egg to PYTHONPATH,\n"
            "or run this script with CARLA's bundled Python.",
            file=sys.stderr,
        )
        return 1

    opendrive = xodr_path.read_text(encoding="utf-8")
    params = carla.OpendriveGenerationParameters()
    params.vertex_distance = args.vertex_distance
    params.max_road_length = args.max_road_length
    params.wall_height = args.wall_height
    params.additional_width = args.extra_lane_width
    params.smooth_junctions = True
    params.enable_mesh_visibility = True

    client = carla.Client(args.host, args.port)
    client.set_timeout(args.timeout)
    print(f"Generating world from {xodr_path.name} ...")
    world = client.generate_opendrive_world(opendrive, params)
    map_name = world.get_map().name
    print(f"World ready: {map_name}")

    origin = manifest["origin"]
    print(
        "Local origin (0,0) = "
        f"WGS84 ({origin['wgs84']['lng']}, {origin['wgs84']['lat']}) / "
        f"GCJ-02 ({origin['gcj02']['lng']}, {origin['gcj02']['lat']})"
    )
    print("Press Ctrl+C to exit.")
    try:
        while True:
            time.sleep(1.0)
    except KeyboardInterrupt:
        print("Done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
