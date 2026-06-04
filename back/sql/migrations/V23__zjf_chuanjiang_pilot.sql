-- V3 M7: 找家纺川姜短驳试点区（L1）围栏 + 种子站点 GCJ-02

UPDATE `t_park`
SET
  `park_name` = '找家纺网 · 川姜短驳试点',
  `center_lng` = 121.062280,
  `center_lat` = 31.912450,
  `map_provider` = 'AMAP',
  `remark` = 'ZJF_CHUANJIANG_PILOT：家纺城短驳演示区（约 960m×640m）'
WHERE `park_code` = 'DEFAULT' AND `deleted` = 0;

UPDATE `t_park_geofence`
SET
  `fence_name` = '找家纺网送货区（川姜试点）',
  `polygon_json` = JSON_ARRAY(
    JSON_ARRAY(121.057780, 31.909950),
    JSON_ARRAY(121.066780, 31.909950),
    JSON_ARRAY(121.066780, 31.914950),
    JSON_ARRAY(121.057780, 31.914950)
  ),
  `remark` = 'V23 L1 试点围栏（GCJ-02）'
WHERE `fence_code` = 'DEFAULT-BOUNDARY' AND `deleted` = 0;

INSERT INTO `t_station` (
  `park_id`, `station_code`, `station_name`, `station_type`,
  `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `area`, `status`, `sort_order`, `remark`, `deleted`
)
SELECT
  p.id, 'ZJF-PICK-01', '门市样点 A', 'PICKUP',
  380.0000, 320.0000, 121.059500, 31.911500, 'ZJF', 'ACTIVE', 21,
  '找家纺试点取货门市', 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_station` s WHERE s.park_id = p.id AND s.station_code = 'ZJF-PICK-01' AND s.deleted = 0
  );

INSERT INTO `t_station` (
  `park_id`, `station_code`, `station_name`, `station_type`,
  `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `area`, `status`, `sort_order`, `remark`, `deleted`
)
SELECT
  p.id, 'ZJF-PICK-02', '门市样点 B', 'PICKUP',
  820.0000, 320.0000, 121.065000, 31.911500, 'ZJF', 'ACTIVE', 22,
  '找家纺试点取货门市', 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_station` s WHERE s.park_id = p.id AND s.station_code = 'ZJF-PICK-02' AND s.deleted = 0
  );

INSERT INTO `t_station` (
  `park_id`, `station_code`, `station_name`, `station_type`,
  `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `area`, `status`, `sort_order`, `remark`, `deleted`
)
SELECT
  p.id, 'ZJF-DROP-01', '找家纺代发仓', 'DROPOFF',
  520.0000, 520.0000, 121.061000, 31.913500, 'ZJF', 'ACTIVE', 31,
  '主送货点', 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_station` s WHERE s.park_id = p.id AND s.station_code = 'ZJF-DROP-01' AND s.deleted = 0
  );

INSERT INTO `t_station` (
  `park_id`, `station_code`, `station_name`, `station_type`,
  `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `area`, `status`, `sort_order`, `remark`, `deleted`
)
SELECT
  p.id, 'ZJF-DROP-02', '找家纺代拿仓', 'DROPOFF',
  720.0000, 540.0000, 121.064500, 31.913800, 'ZJF', 'ACTIVE', 32,
  '辅送货点', 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_station` s WHERE s.park_id = p.id AND s.station_code = 'ZJF-DROP-02' AND s.deleted = 0
  );

INSERT INTO `t_station` (
  `park_id`, `station_code`, `station_name`, `station_type`,
  `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `area`, `status`, `sort_order`, `remark`, `deleted`
)
SELECT
  p.id, 'ZJF-EXPRESS-01', '快递接驳点', 'GENERAL',
  300.0000, 480.0000, 121.058500, 31.912800, 'ZJF', 'ACTIVE', 40,
  '仓库⇌快递网点接驳', 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_station` s WHERE s.park_id = p.id AND s.station_code = 'ZJF-EXPRESS-01' AND s.deleted = 0
  );

INSERT INTO `t_station` (
  `park_id`, `station_code`, `station_name`, `station_type`,
  `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `area`, `status`, `sort_order`, `remark`, `deleted`
)
SELECT
  p.id, 'ZJF-IDLE-01', '车辆待命区', 'GENERAL',
  600.0000, 400.0000, 121.062280, 31.912450, 'ZJF', 'ACTIVE', 50,
  '充电/等待派单', 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_station` s WHERE s.park_id = p.id AND s.station_code = 'ZJF-IDLE-01' AND s.deleted = 0
  );
