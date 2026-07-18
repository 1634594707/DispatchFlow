-- V44: Expand the display-only ZJF local service envelope to the actual operating range.
-- Automatic dispatch remains restricted to the five ZJF-ZONE-* L1_CORE fences.

USE `fsd_core`;

UPDATE `t_park_geofence`
SET
  `fence_name` = '找家纺网本地履约服务范围',
  `polygon_json` = JSON_ARRAY(
    JSON_ARRAY(121.060000, 31.953000),
    JSON_ARRAY(121.060000, 31.970000),
    JSON_ARRAY(121.072000, 31.972000),
    JSON_ARRAY(121.100000, 31.972000),
    JSON_ARRAY(121.118000, 31.966000),
    JSON_ARRAY(121.118000, 31.930000),
    JSON_ARRAY(121.102000, 31.916000),
    JSON_ARRAY(121.095000, 31.952000),
    JSON_ARRAY(121.075000, 31.953000)
  ),
  `remark` = 'V44 实际运营展示范围：家纺城及周边本地履约/接驳物理可达范围；非自动派单围栏',
  `updated_at` = NOW()
WHERE `fence_code` = 'DEFAULT-BOUNDARY' AND `deleted` = 0;

-- Expand the existing south L1 zone just enough to include the base dispatch origin.
UPDATE `t_park_geofence`
SET
  `polygon_json` = JSON_ARRAY(
    JSON_ARRAY(121.071812, 31.960126),
    JSON_ARRAY(121.073405, 31.959762),
    JSON_ARRAY(121.075820, 31.959683),
    JSON_ARRAY(121.079400, 31.959720),
    JSON_ARRAY(121.081250, 31.959920),
    JSON_ARRAY(121.081250, 31.960760),
    JSON_ARRAY(121.080650, 31.961220),
    JSON_ARRAY(121.079152, 31.961698),
    JSON_ARRAY(121.077213, 31.961912),
    JSON_ARRAY(121.074893, 31.961856),
    JSON_ARRAY(121.072610, 31.961624)
  ),
  `remark` = 'V44 校准：南排门市区向东连接找家纺网基地发货/调度/充电原点',
  `updated_at` = NOW()
WHERE `fence_code` = 'ZJF-ZONE-CORE-SOUTH' AND `deleted` = 0;

-- One physical base for dispatch standby and charging.
UPDATE `t_station`
SET
  `station_name` = '找家纺网基地 · 发货调度原点',
  `coord_x` = 668.4370,
  `coord_y` = 624.4500,
  `coord_lng` = 121.080681,
  `coord_lat` = 31.960337,
  `anchor_node_code` = 'RN27',
  `status` = 'ACTIVE',
  `capacity_limit` = 20,
  `remark` = '找家纺网仓储物流中心：统一发货、调度待命与充电物理原点',
  `updated_at` = NOW()
WHERE `station_code` = 'ZJF-IDLE-01' AND `deleted` = 0;

UPDATE `t_station`
SET
  `station_name` = '找家纺网基地 · 充电点',
  `coord_x` = 668.4370,
  `coord_y` = 624.4500,
  `coord_lng` = 121.080681,
  `coord_lat` = 31.960337,
  `anchor_node_code` = 'RN27',
  `status` = 'ACTIVE',
  `capacity_limit` = 6,
  `remark` = '与 ZJF-IDLE-01 共用找家纺网基地物理原点',
  `updated_at` = NOW()
WHERE `station_code` = 'ZJF-CHG-01' AND `deleted` = 0;

UPDATE `t_station`
SET
  `status` = 'INACTIVE',
  `remark` = 'V44 收敛为找家纺网基地单一充电原点',
  `updated_at` = NOW()
WHERE `station_code` IN ('ZJF-CHG-02', 'ZJF-CHG-03', 'ZJF-CHG-04', 'ZJF-CHG-05')
  AND `deleted` = 0;

UPDATE `t_station_service_position` p
JOIN `t_station` s ON s.`id` = p.`station_id`
SET
  p.`coord_lng` = 121.080681,
  p.`coord_lat` = 31.960337,
  p.`access_node_code` = 'RN27',
  p.`status` = 'ACTIVE',
  p.`updated_at` = NOW()
WHERE s.`station_code` IN ('ZJF-IDLE-01', 'ZJF-CHG-01')
  AND s.`deleted` = 0;

UPDATE `t_station_service_position` p
JOIN `t_station` s ON s.`id` = p.`station_id`
SET p.`status` = 'OUT_OF_SERVICE', p.`updated_at` = NOW()
WHERE s.`station_code` IN ('ZJF-CHG-02', 'ZJF-CHG-03', 'ZJF-CHG-04', 'ZJF-CHG-05')
  AND s.`deleted` = 0;

-- Keep multiple bays/piles for capacity, but place all of them at the same physical base.
UPDATE `t_parking_slot`
SET
  `slot_name` = CONCAT('找家纺网基地车位 ', `slot_code`),
  `coord_x` = 668.4370,
  `coord_y` = 624.4500,
  `coord_lng` = 121.080681,
  `coord_lat` = 31.960337,
  `entry_node_code` = 'RN27',
  `exit_node_code` = 'RN27',
  `blocking_main_road` = 0,
  `remark` = '找家纺网基地统一发货/待命/充电区域',
  `updated_at` = NOW()
WHERE `park_id` = 1 AND `deleted` = 0;

UPDATE `t_charging_pile`
SET
  `pile_name` = CONCAT('找家纺网基地充电桩 ', `pile_code`),
  `entry_node_code` = 'RN27',
  `exit_node_code` = 'RN27',
  `remark` = '找家纺网基地单一充电中心',
  `updated_at` = NOW()
WHERE `park_id` = 1 AND `deleted` = 0;
