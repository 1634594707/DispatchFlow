-- ============================================================
-- V59: 无界表归档的前置索引（路线图 §7.4）
--
-- 实测（本地库 information_schema.statistics）：四张会无界增长的表，时间列全都只出现在
-- **前导列不对**的复合索引里 ——
--   t_fleet_telemetry_point  idx_vehicle_recorded(vehicle_id, recorded_at)
--   t_webhook_delivery_log   idx_sub_delivered(subscription_id, delivered_at)
--   t_dispatch_event_outbox  idx_event_type_created_at(event_type, created_at)
--   t_order_idempotency      idx_park_id_created_at(park_id, created_at)
-- ⇒ 按时间列做保留期清理会走全表扫，四张表里最大的 outbox 已有 2270 行/1.52 MB 在本地涨着。
--
-- 为什么补索引而不是 PARTITION BY：MySQL 要求分区表的**每个唯一键都包含分区列**，
-- 而这四张表带 uk_event_id / uk_idempotency_key 这样的单列唯一键，按 created_at 分区必须先把
-- 唯一键改成 (created_at, event_id) —— 那会直接削弱幂等约束，得不偿失。
-- ============================================================

USE `fsd_core`;

-- t_fleet_telemetry_point.recorded_at
SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_fleet_telemetry_point` ADD INDEX `idx_telemetry_recorded_at` (`recorded_at`)',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = 'fsd_core' AND table_name = 't_fleet_telemetry_point'
    AND index_name = 'idx_telemetry_recorded_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_webhook_delivery_log.delivered_at
SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_webhook_delivery_log` ADD INDEX `idx_webhook_delivered_at` (`delivered_at`)',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = 'fsd_core' AND table_name = 't_webhook_delivery_log'
    AND index_name = 'idx_webhook_delivered_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_dispatch_event_outbox.created_at（清理只针对 status='PUBLISHED' 终态行）
SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_dispatch_event_outbox` ADD INDEX `idx_outbox_status_created_at` (`status`, `created_at`)',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = 'fsd_core' AND table_name = 't_dispatch_event_outbox'
    AND index_name = 'idx_outbox_status_created_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_order_idempotency.created_at
SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_order_idempotency` ADD INDEX `idx_idempotency_created_at` (`created_at`)',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = 'fsd_core' AND table_name = 't_order_idempotency'
    AND index_name = 'idx_idempotency_created_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
