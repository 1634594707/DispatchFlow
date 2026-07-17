-- V37: 配送范围细分分区 + 站点服务时间/承载能力/坐标实地校准
-- 基于叠石桥家纺城真实道路网格（OSM bbox · GCJ-02）划分 5 个配送分区
-- 道路网格：南排(31.9608)、中排(31.9618)、北排(31.9633) × 西列(121.0729)、中列(121.0750)、东列(121.0793)

-- ============================================================
-- 1. t_station 新增服务时间字段
-- ============================================================
ALTER TABLE `t_station`
  ADD COLUMN `service_hours` VARCHAR(32) DEFAULT NULL COMMENT '服务时间窗，如 06:00-22:00 / 24h'
  AFTER `capacity_limit`;

ALTER TABLE `t_station`
  ADD COLUMN `avg_service_seconds` INT DEFAULT NULL COMMENT '平均服务时长（秒），用于 ETA 估算'
  AFTER `service_hours`;

-- ============================================================
-- 2. 新增 5 个配送分区 geofence（基于叠石桥真实道路+建筑肌理）
--    边界沿实际道路中心线+建筑退线绘制，非矩形，反映真实配送可达范围
-- ============================================================

-- 分区 1：家纺城核心南排区（门市取货 · 沿南排门市街+西排南段）
INSERT INTO `t_park_geofence` (`park_id`, `fence_code`, `fence_name`, `fence_type`, `polygon_json`, `status`, `remark`, `version`, `deleted`)
SELECT p.id, 'ZJF-ZONE-CORE-SOUTH', '家纺城核心南排区（门市取货）', 'BOUNDARY',
  JSON_ARRAY(
    JSON_ARRAY(121.071812, 31.960126),
    JSON_ARRAY(121.073405, 31.959762),
    JSON_ARRAY(121.075820, 31.959683),
    JSON_ARRAY(121.077914, 31.959821),
    JSON_ARRAY(121.079352, 31.960235),
    JSON_ARRAY(121.079588, 31.961045),
    JSON_ARRAY(121.079152, 31.961698),
    JSON_ARRAY(121.077213, 31.961912),
    JSON_ARRAY(121.074893, 31.961856),
    JSON_ARRAY(121.072610, 31.961624)
  ),
  'ACTIVE', '南排门市集聚区 · 含 ZJF-PICK-01/02 · 沿南排街+西排南段', 0, 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_park_geofence` g WHERE g.fence_code = 'ZJF-ZONE-CORE-SOUTH' AND g.deleted = 0
  );

-- 分区 2：家纺城核心北排区（仓库 · 北排仓库街+西排北段）
INSERT INTO `t_park_geofence` (`park_id`, `fence_code`, `fence_name`, `fence_type`, `polygon_json`, `status`, `remark`, `version`, `deleted`)
SELECT p.id, 'ZJF-ZONE-CORE-NORTH', '家纺城核心北排区（仓库）', 'BOUNDARY',
  JSON_ARRAY(
    JSON_ARRAY(121.072051, 31.962157),
    JSON_ARRAY(121.074521, 31.962051),
    JSON_ARRAY(121.076852, 31.962214),
    JSON_ARRAY(121.078765, 31.962471),
    JSON_ARRAY(121.079352, 31.962968),
    JSON_ARRAY(121.079028, 31.963734),
    JSON_ARRAY(121.077156, 31.964025),
    JSON_ARRAY(121.074621, 31.964078),
    JSON_ARRAY(121.072862, 31.963921),
    JSON_ARRAY(121.071812, 31.963486)
  ),
  'ACTIVE', '北排仓库集聚区 · 含 ZJF-DROP-03 · 沿北排仓库街', 0, 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_park_geofence` g WHERE g.fence_code = 'ZJF-ZONE-CORE-NORTH' AND g.deleted = 0
  );

-- 分区 3：代发仓集散区（中部 · 代发仓主枢纽，沿志远路+复成路）
INSERT INTO `t_park_geofence` (`park_id`, `fence_code`, `fence_name`, `fence_type`, `polygon_json`, `status`, `remark`, `version`, `deleted`)
SELECT p.id, 'ZJF-ZONE-HUB', '代发仓集散区', 'BOUNDARY',
  JSON_ARRAY(
    JSON_ARRAY(121.079428, 31.961823),
    JSON_ARRAY(121.081125, 31.961642),
    JSON_ARRAY(121.082765, 31.961882),
    JSON_ARRAY(121.083872, 31.962356),
    JSON_ARRAY(121.084291, 31.963112),
    JSON_ARRAY(121.084052, 31.963792),
    JSON_ARRAY(121.082831, 31.964061),
    JSON_ARRAY(121.081012, 31.964103),
    JSON_ARRAY(121.079612, 31.963847),
    JSON_ARRAY(121.079152, 31.963246)
  ),
  'ACTIVE', '代发仓主枢纽 · 含 ZJF-DROP-01/IDLE-01/CHG-02 · 沿志远路', 0, 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_park_geofence` g WHERE g.fence_code = 'ZJF-ZONE-HUB' AND g.deleted = 0
  );

-- 分区 4：东排代拿仓区（东区 · 代拿仓+志浩方向，沿叠石桥路东侧）
INSERT INTO `t_park_geofence` (`park_id`, `fence_code`, `fence_name`, `fence_type`, `polygon_json`, `status`, `remark`, `version`, `deleted`)
SELECT p.id, 'ZJF-ZONE-EAST', '东排代拿仓区', 'BOUNDARY',
  JSON_ARRAY(
    JSON_ARRAY(121.084052, 31.960156),
    JSON_ARRAY(121.085621, 31.959885),
    JSON_ARRAY(121.087342, 31.959912),
    JSON_ARRAY(121.088673, 31.960345),
    JSON_ARRAY(121.088891, 31.961228),
    JSON_ARRAY(121.088432, 31.962156),
    JSON_ARRAY(121.087612, 31.963112),
    JSON_ARRAY(121.086125, 31.963834),
    JSON_ARRAY(121.084521, 31.964078),
    JSON_ARRAY(121.084052, 31.963446)
  ),
  'ACTIVE', '东排代拿仓+志浩面料方向 · 含 ZJF-DROP-02/04/CHG-05', 0, 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_park_geofence` g WHERE g.fence_code = 'ZJF-ZONE-EAST' AND g.deleted = 0
  );

-- 分区 5：快递接驳物流区（西北角 · 快递网点集聚，沿纺都大道+西排北段）
INSERT INTO `t_park_geofence` (`park_id`, `fence_code`, `fence_name`, `fence_type`, `polygon_json`, `status`, `remark`, `version`, `deleted`)
SELECT p.id, 'ZJF-ZONE-EXPRESS', '快递接驳物流区', 'BOUNDARY',
  JSON_ARRAY(
    JSON_ARRAY(121.071812, 31.963343),
    JSON_ARRAY(121.072682, 31.963215),
    JSON_ARRAY(121.073612, 31.963312),
    JSON_ARRAY(121.074521, 31.963586),
    JSON_ARRAY(121.075340, 31.963952),
    JSON_ARRAY(121.075621, 31.964101),
    JSON_ARRAY(121.074821, 31.964201),
    JSON_ARRAY(121.073405, 31.964155),
    JSON_ARRAY(121.072152, 31.963921)
  ),
  'ACTIVE', '快递网点接驳区 · 含 ZJF-EXPRESS-01/CHG-03 · 沿纺都大道', 0, 0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_park_geofence` g WHERE g.fence_code = 'ZJF-ZONE-EXPRESS' AND g.deleted = 0
  );

-- ============================================================
-- 3. 更新所有 ZJF- 站点：坐标校准 + 服务时间 + 承载能力
-- ============================================================

-- 取货站点（门市 · 南排核心区）
UPDATE `t_station` SET
  `coord_lng` = 121.074453, `coord_lat` = 31.960396,
  `coord_x` = 225.0, `coord_y` = 694.3,
  `station_name` = '南通家纺城门市',
  `capacity_limit` = 50,
  `service_hours` = '06:00-22:00',
  `avg_service_seconds` = 180,
  `remark` = '取货 · 南通家纺城（叠石桥南排）· 真实门市位置'
WHERE `station_code` = 'ZJF-PICK-01' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.072610, `coord_lat` = 31.960726,
  `coord_x` = 46.2, `coord_y` = 652.2,
  `station_name` = '成品展示中心门市',
  `capacity_limit` = 30,
  `service_hours` = '08:00-20:00',
  `avg_service_seconds` = 240,
  `remark` = '取货 · 成品展示中心 · 西排南路口'
WHERE `station_code` = 'ZJF-PICK-02' AND `deleted` = 0;

-- 送货站点（仓库）
UPDATE `t_station` SET
  `coord_lng` = 121.079762, `coord_lat` = 31.963627,
  `coord_x` = 513.2, `coord_y` = 107.1,
  `station_name` = '找家纺代发仓',
  `capacity_limit` = 200,
  `service_hours` = '06:00-23:00',
  `avg_service_seconds` = 300,
  `remark` = '送货 · 代发仓主枢纽 · 北排中部'
WHERE `station_code` = 'ZJF-DROP-01' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.087005, `coord_lat` = 31.961780,
  `coord_x` = 1153.5, `coord_y` = 428.8,
  `station_name` = '找家纺代拿仓',
  `capacity_limit` = 150,
  `service_hours` = '07:00-22:00',
  `avg_service_seconds` = 360,
  `remark` = '送货 · 代拿仓 · 叠石桥三期东排'
WHERE `station_code` = 'ZJF-DROP-02' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.074367, `coord_lat` = 31.963548,
  `coord_x` = 105.2, `coord_y` = 670.4,
  `station_name` = '西排北仓',
  `capacity_limit` = 80,
  `service_hours` = '08:00-20:00',
  `avg_service_seconds` = 240,
  `remark` = '送货 · 西排北仓 · 北排西段'
WHERE `station_code` = 'ZJF-DROP-03' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.083893, `coord_lat` = 31.962833,
  `coord_x` = 863.2, `coord_y` = 400.0,
  `station_name` = '东排集散仓',
  `capacity_limit` = 120,
  `service_hours` = '07:00-22:00',
  `avg_service_seconds` = 300,
  `remark` = '送货 · 东排集散仓 · 中排东段'
WHERE `station_code` = 'ZJF-DROP-04' AND `deleted` = 0;

-- 快递接驳点
UPDATE `t_station` SET
  `coord_lng` = 121.073200, `coord_lat` = 31.963800,
  `coord_x` = 83.6, `coord_y` = 642.0,
  `station_name` = '快递接驳点',
  `capacity_limit` = 500,
  `service_hours` = '08:00-22:00',
  `avg_service_seconds` = 120,
  `remark` = '仓库⇌快递网点接驳 · 西北角物流区（纺都大道沿线）'
WHERE `station_code` = 'ZJF-EXPRESS-01' AND `deleted` = 0;

-- 车辆待命区
UPDATE `t_station` SET
  `coord_lng` = 121.080055, `coord_lat` = 31.961922,
  `coord_x` = 600.0, `coord_y` = 400.0,
  `station_name` = '车辆待命区',
  `capacity_limit` = 20,
  `service_hours` = '24h',
  `avg_service_seconds` = 0,
  `remark` = '充电/等待派单 · 试点几何中心'
WHERE `station_code` = 'ZJF-IDLE-01' AND `deleted` = 0;

-- 充电站（24小时服务）
UPDATE `t_station` SET
  `coord_lng` = 121.080069, `coord_lat` = 31.961850,
  `capacity_limit` = 4,
  `service_hours` = '24h',
  `avg_service_seconds` = 1800,
  `remark` = 'L1 快充 4 桩 · 待命区旁'
WHERE `station_code` = 'ZJF-CHG-01' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.079780, `coord_lat` = 31.963518,
  `capacity_limit` = 6,
  `service_hours` = '24h',
  `avg_service_seconds` = 1800,
  `remark` = 'L1 快充 6 桩 · 代发仓旁'
WHERE `station_code` = 'ZJF-CHG-02' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.072610, `coord_lat` = 31.963700,
  `capacity_limit` = 2,
  `service_hours` = '24h',
  `avg_service_seconds` = 3600,
  `remark` = 'L1 慢充 2 桩 · 快递接驳旁'
WHERE `station_code` = 'ZJF-CHG-03' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.074442, `coord_lat` = 31.960671,
  `capacity_limit` = 4,
  `service_hours` = '24h',
  `avg_service_seconds` = 1800,
  `remark` = 'L1 快充 4 桩 · 南排取货旁'
WHERE `station_code` = 'ZJF-CHG-04' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.084334, `coord_lat` = 31.962890,
  `capacity_limit` = 4,
  `service_hours` = '24h',
  `avg_service_seconds` = 1800,
  `remark` = 'L1 快充 4 桩 · 东排集散仓旁'
WHERE `station_code` = 'ZJF-CHG-05' AND `deleted` = 0;
