-- V45: one-site production cleanup and deterministic base fleet reset.
-- The user-facing service keeps only the ZJF short-haul fleet and real operating stations.

USE `fsd_core`;
SET NAMES utf8mb4;

-- Repair all authoritative ZJF display names instead of attempting a second encoding conversion.
UPDATE `t_station`
SET `station_name` = CASE `station_code`
  WHEN 'ZJF-PICK-01' THEN '南通家纺城门市'
  WHEN 'ZJF-PICK-02' THEN '成品展示中心门市'
  WHEN 'ZJF-DROP-01' THEN '找家纺代发仓'
  WHEN 'ZJF-DROP-02' THEN '找家纺代拿仓'
  WHEN 'ZJF-DROP-03' THEN '西排北仓'
  WHEN 'ZJF-DROP-04' THEN '东排集散仓'
  WHEN 'ZJF-EXPRESS-01' THEN '快递接驳点'
  WHEN 'ZJF-IDLE-01' THEN '找家纺网基地 · 发货调度原点'
  WHEN 'ZJF-CHG-01' THEN '找家纺网基地 · 充电点'
  WHEN 'ZJF-CHG-02' THEN '代发仓充电站'
  WHEN 'ZJF-CHG-03' THEN '快递接驳充电站'
  WHEN 'ZJF-CHG-04' THEN '南排取货充电站'
  WHEN 'ZJF-CHG-05' THEN '东排快充站'
  ELSE `station_name`
END,
`updated_at` = NOW()
WHERE `station_code` LIKE 'ZJF-%' AND `deleted` = 0;

UPDATE `t_park`
SET `remark` = '找家纺网本地履约：实际运营展示范围约 5.5 × 6.2 km；自动派单仅限五个 L1 核心分区',
    `updated_at` = NOW()
WHERE `park_code` = 'DEFAULT' AND `deleted` = 0;

-- Remove accumulated demo/test transactions and operational noise.
DELETE FROM `t_station_service_position_reservation`;
DELETE FROM `t_charging_session`;
DELETE FROM `t_battery_swap_session`;
DELETE FROM `t_fleet_telemetry_point`;
DELETE FROM `t_route_audit`;
DELETE FROM `t_route_health_metric`;
DELETE FROM `t_vehicle_command`;
DELETE FROM `t_vehicle_maintenance`;
DELETE FROM `t_field_ops_ticket`;
DELETE FROM `t_report_history`;
DELETE FROM `t_webhook_delivery_log`;
DELETE FROM `t_dispatch_task_operate_log`;
DELETE FROM `t_dispatch_exception_record`;
DELETE FROM `t_dispatch_event_outbox`;
DELETE FROM `t_dispatch_task`;
DELETE FROM `t_order`;

-- Release all transient locks and capacity reservations.
UPDATE `t_station_service_position`
SET `reserved_vehicle_id` = NULL,
    `reserved_until` = NULL,
    `status` = CASE WHEN `status` IN ('RESERVED', 'OCCUPIED') THEN 'ACTIVE' ELSE `status` END,
    `updated_at` = NOW();

UPDATE `t_parking_slot`
SET `status` = 'FREE', `occupied_vehicle_id` = NULL, `updated_at` = NOW()
WHERE `park_id` = 1 AND `deleted` = 0;

UPDATE `t_charging_pile`
SET `status` = 'FREE',
    `occupied_vehicle_id` = NULL,
    `reservation_state` = 'FREE',
    `estimated_release_at` = NULL,
    `updated_at` = NOW()
WHERE `park_id` = 1 AND `deleted` = 0;

UPDATE `t_dispatch_pause_state`
SET `is_paused` = 0,
    `pause_reason` = NULL,
    `paused_by` = NULL,
    `paused_at` = NULL,
    `resumed_at` = NOW(),
    `updated_at` = NOW();

-- Keep only the real ZJF operating surface; legacy demo entities remain soft-deleted for auditability.
UPDATE `t_vehicle`
SET `online_status` = 'OFFLINE',
    `dispatch_status` = 'UNAVAILABLE',
    `current_task_id` = NULL,
    `current_order_id` = NULL,
    `deleted` = 1,
    `updated_at` = NOW()
WHERE `vehicle_code` NOT LIKE 'ZJF-AV-%' AND `deleted` = 0;

DELETE c FROM `t_vehicle_credential` c
JOIN `t_vehicle` v ON v.`id` = c.`vehicle_id`
WHERE v.`deleted` = 1;

UPDATE `t_vehicle`
SET `vehicle_name` = CONCAT('找家纺短驳车 ', CAST(SUBSTRING_INDEX(`vehicle_code`, '-', -1) AS UNSIGNED)),
    `link_mode` = 'SIM',
    `online_status` = 'ONLINE',
    `dispatch_status` = 'IDLE',
    `current_task_id` = NULL,
    `current_order_id` = NULL,
    `current_longitude` = 668.4370,
    `current_latitude` = 624.4500,
    `battery_level` = 100,
    `last_report_time` = NOW(),
    `delivery_zone` = 'GEO_DELIVERY',
    `current_load` = 0,
    `current_speed_kmh` = 0,
    `current_heading` = 74,
    `manual_override` = 0,
    `emergency_mode` = 0,
    `remark` = '找家纺网基地统一待命；从 RN27 现实出口出车',
    `deleted` = 0,
    `updated_at` = NOW()
WHERE `vehicle_code` LIKE 'ZJF-AV-%';

UPDATE `t_park`
SET `status` = 'INACTIVE', `deleted` = 1, `updated_at` = NOW()
WHERE `park_code` <> 'DEFAULT' AND `deleted` = 0;

UPDATE `t_station`
SET `status` = 'INACTIVE', `deleted` = 1, `updated_at` = NOW()
WHERE (`park_id` <> 1 OR `station_code` NOT LIKE 'ZJF-%') AND `deleted` = 0;

UPDATE `t_parking_slot` SET `deleted` = 1, `updated_at` = NOW()
WHERE `park_id` <> 1 AND `deleted` = 0;

UPDATE `t_charging_pile` SET `deleted` = 1, `updated_at` = NOW()
WHERE `park_id` <> 1 AND `deleted` = 0;
