#!/usr/bin/env python3
"""Generate road network seed fragment from application.yml."""
import pathlib
import yaml

ROOT = pathlib.Path(__file__).resolve().parents[2]
YAML_PATH = ROOT / "back/fsd-bootstrap/src/main/resources/application.yml"
OUT_PATH = ROOT / "back/sql/init/_v9_seed_fragment.sql"

doc = yaml.safe_load(YAML_PATH.read_text(encoding="utf-8"))
park = doc["fsd"]["park"]
nodes = park["road-nodes"]
segs = park["road-segments"]

lines = [
    "-- Seed: default park_id=1 (from application.yml fsd.park.road-nodes / road-segments)",
    "INSERT INTO `t_road_node` (`id`, `park_id`, `node_code`, `coord_x`, `coord_y`, `status`, `version`, `deleted`) VALUES",
]
node_vals = []
for i, n in enumerate(nodes):
    nid = 3001 + i
    node_vals.append(
        f"  ({nid}, 1, '{n['code']}', {n['x']}.0000, {n['y']}.0000, 'ACTIVE', 0, 0)"
    )
lines.append(",\n".join(node_vals))
lines.append(
    "ON DUPLICATE KEY UPDATE `coord_x` = VALUES(`coord_x`), `coord_y` = VALUES(`coord_y`), `status` = VALUES(`status`);"
)
lines.append("")
lines.append(
    "INSERT INTO `t_road_segment` (`id`, `park_id`, `from_node_code`, `to_node_code`, `status`, `version`, `deleted`) VALUES"
)
seg_vals = []
for i, s in enumerate(segs):
    sid = 4001 + i
    seg_vals.append(f"  ({sid}, 1, '{s['from']}', '{s['to']}', 'ACTIVE', 0, 0)")
lines.append(",\n".join(seg_vals))
lines.append("ON DUPLICATE KEY UPDATE `status` = VALUES(`status`);")

OUT_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"Wrote {len(nodes)} nodes, {len(segs)} segments -> {OUT_PATH}")
