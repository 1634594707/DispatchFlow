-- V28: 叠石桥试点扩展仓库/充电站（道路走廊吸附 · GCJ-02）
-- 坐标经 PilotForbiddenZones + LocalPilotRoadGraph 校验（距道路 ≤50m，不穿建筑块）

INSERT INTO `t_station` (
  `park_id`, `station_code`, `station_name`, `station_type`,
  `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `area`, `status`, `sort_order`, `remark`, `deleted`
)
SELECT
  p.id, 'ZJF-DROP-03', '西排北仓', 'DROPOFF',
  83.6000, 107.1000, 121.073200, 31.963523, 'ZJF', 'ACTIVE', 44,
  '送货 · 西排北路口（JUNCTION_NW 东 48m · 北排道路）', 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_station` s WHERE s.park_id = p.id AND s.station_code = 'ZJF-DROP-03' AND s.deleted = 0
  );

INSERT INTO `t_station` (
  `park_id`, `station_code`, `station_name`, `station_type`,
  `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `area`, `status`, `sort_order`, `remark`, `deleted`
)
SELECT
  p.id, 'ZJF-DROP-04', '东排集散仓', 'DROPOFF',
  863.2000, 400.0000, 121.084000, 31.961977, 'ZJF', 'ACTIVE', 45,
  '送货 · 中排主干道东段（IDLE 与代拿仓之间）', 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_station` s WHERE s.park_id = p.id AND s.station_code = 'ZJF-DROP-04' AND s.deleted = 0
  );

INSERT INTO `t_station` (
  `park_id`, `station_code`, `station_name`, `station_type`,
  `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `area`, `status`, `sort_order`, `remark`, `deleted`
)
SELECT
  p.id, 'ZJF-CHG-04', '南排取货充电站', 'GENERAL',
  225.1000, 642.0000, 121.075160, 31.960700, 'ZJF', 'ACTIVE', 64,
  'L1 快充 4 桩 · 南通家纺城门市旁南排道路', 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_station` s WHERE s.park_id = p.id AND s.station_code = 'ZJF-CHG-04' AND s.deleted = 0
  );

INSERT INTO `t_station` (
  `park_id`, `station_code`, `station_name`, `station_type`,
  `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `area`, `status`, `sort_order`, `remark`, `deleted`
)
SELECT
  p.id, 'ZJF-CHG-05', '东排快充站', 'GENERAL',
  899.3000, 414.6000, 121.084500, 31.961900, 'ZJF', 'ACTIVE', 65,
  'L1 快充 4 桩 · 东排主干道（代拿仓西侧）', 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_station` s WHERE s.park_id = p.id AND s.station_code = 'ZJF-CHG-05' AND s.deleted = 0
  );
