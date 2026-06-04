CARLA source-build import (requires .fbx mesh for full Unreal map):
1. Copy this folder to <CARLA_ROOT>/Import/ZjfDieshiqiaoPilot
2. From CARLA root: make import ARGS="--package=ZjfDieshiqiaoPilot"

For quick testing without FBX, use OpenDRIVE standalone mode:
  python scripts/carla/load_in_carla.py

Note: xodr-only import via make import still needs matching FBX tiles.
Standalone mode generates road mesh inside CARLA at runtime.
