-- V3 M9-E2: 找家纺 L1 试点充电站种子（GCJ-02，道路吸附后由 V24 流程维护）

INSERT INTO `t_station` (
  `park_id`, `station_code`, `station_name`, `station_type`,
  `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `area`, `status`, `sort_order`, `remark`, `deleted`
)
SELECT
  p.id, 'ZJF-CHG-01', '待命区充电站', 'GENERAL',
  580.0000, 380.0000, 121.061800, 31.912200, 'ZJF', 'ACTIVE', 61,
  'L1 快充 4 桩 · 演示回充', 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_station` s WHERE s.park_id = p.id AND s.station_code = 'ZJF-CHG-01' AND s.deleted = 0
  );

INSERT INTO `t_station` (
  `park_id`, `station_code`, `station_name`, `station_type`,
  `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `area`, `status`, `sort_order`, `remark`, `deleted`
)
SELECT
  p.id, 'ZJF-CHG-02', '代发仓充电站', 'GENERAL',
  540.0000, 560.0000, 121.061200, 31.913600, 'ZJF', 'ACTIVE', 62,
  'L1 快充 6 桩', 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_station` s WHERE s.park_id = p.id AND s.station_code = 'ZJF-CHG-02' AND s.deleted = 0
  );

INSERT INTO `t_station` (
  `park_id`, `station_code`, `station_name`, `station_type`,
  `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `area`, `status`, `sort_order`, `remark`, `deleted`
)
SELECT
  p.id, 'ZJF-CHG-03', '快递接驳充电站', 'GENERAL',
  320.0000, 500.0000, 121.058800, 31.912900, 'ZJF', 'ACTIVE', 63,
  'L1 慢充 2 桩', 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_station` s WHERE s.park_id = p.id AND s.station_code = 'ZJF-CHG-03' AND s.deleted = 0
  );
