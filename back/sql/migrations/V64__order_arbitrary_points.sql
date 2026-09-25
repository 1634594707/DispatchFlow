-- V64: 任意点下单（快递式取送货位置）
--
-- 业务事实：取货点不固定，用户在任意位置下单。此前 t_order 只有两个指向 t_station 的
-- NOT NULL 外键，"任意坐标"这条入口在结构上不存在。
--
-- 本迁移做三件事：
--   1. 给 t_order 加 GCJ-02 原始坐标（用户点的那个位置，用于展示与审计）；
--   2. 加受理时算出的**可派单路网节点**编码与吸附距离（派单只认这个节点，不认原始坐标，
--      否则车辆会被要求开进没有路的田里）；
--   3. 把两个 station 外键放开为可空 —— 站点退化为"常用点/收藏"，不再是派单前提。
--
-- 判据与半径（250 m）来自 2026-09-23 实测：现役图 417 个可派单节点，按 250 m 栅格量
-- "到最近可派单节点"只覆盖 32.2 km²（图框 66.27 km² 的一半）。吸附半径的对外口径见路线图 §1.13。

SET @db := DATABASE();

-- ---------- 1. 原始坐标 ----------
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'pickup_lng') = 0,
  'ALTER TABLE `t_order` ADD COLUMN `pickup_lng` DECIMAL(10,6) DEFAULT NULL COMMENT ''取货点 GCJ-02 经度（用户下单原始坐标，非吸附后）'' AFTER `dropoff_point_id`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'pickup_lat') = 0,
  'ALTER TABLE `t_order` ADD COLUMN `pickup_lat` DECIMAL(10,6) DEFAULT NULL COMMENT ''取货点 GCJ-02 纬度'' AFTER `pickup_lng`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'dropoff_lng') = 0,
  'ALTER TABLE `t_order` ADD COLUMN `dropoff_lng` DECIMAL(10,6) DEFAULT NULL COMMENT ''送货点 GCJ-02 经度（用户下单原始坐标，非吸附后）'' AFTER `pickup_lat`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'dropoff_lat') = 0,
  'ALTER TABLE `t_order` ADD COLUMN `dropoff_lat` DECIMAL(10,6) DEFAULT NULL COMMENT ''送货点 GCJ-02 纬度'' AFTER `dropoff_lng`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 2. 吸附结果 ----------
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'pickup_node_code') = 0,
  'ALTER TABLE `t_order` ADD COLUMN `pickup_node_code` VARCHAR(64) DEFAULT NULL COMMENT ''取货点吸附到的可派单路网节点（派单端点，t_road_node.node_code）'' AFTER `dropoff_lat`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'dropoff_node_code') = 0,
  'ALTER TABLE `t_order` ADD COLUMN `dropoff_node_code` VARCHAR(64) DEFAULT NULL COMMENT ''送货点吸附到的可派单路网节点'' AFTER `pickup_node_code`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 吸附距离是"用户点在没路的地方"唯一的可统计证据；没有它就无法判断 250 m 这个半径定得对不对。
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'pickup_snap_meters') = 0,
  'ALTER TABLE `t_order` ADD COLUMN `pickup_snap_meters` DECIMAL(8,2) DEFAULT NULL COMMENT ''取货点吸附距离（米）；受理时量得，用于校准吸附半径'' AFTER `dropoff_node_code`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'dropoff_snap_meters') = 0,
  'ALTER TABLE `t_order` ADD COLUMN `dropoff_snap_meters` DECIMAL(8,2) DEFAULT NULL COMMENT ''送货点吸附距离（米）'' AFTER `pickup_snap_meters`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 3. 站点外键放开 ----------
-- 至少有一个入口（站点 ID 或坐标）由应用层校验：OrderEndpointResolver 在受理时二选一，
-- 两边都缺即 ORDER_ENDPOINT_MISSING。
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_order'
     AND COLUMN_NAME = 'pickup_point_id' AND IS_NULLABLE = 'NO') = 1,
  'ALTER TABLE `t_order` MODIFY COLUMN `pickup_point_id` BIGINT NULL COMMENT ''取货站点ID；与 pickup_lng/lat 二选一，站点退化为常用点''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_order'
     AND COLUMN_NAME = 'dropoff_point_id' AND IS_NULLABLE = 'NO') = 1,
  'ALTER TABLE `t_order` MODIFY COLUMN `dropoff_point_id` BIGINT NULL COMMENT ''送货站点ID；与 dropoff_lng/lat 二选一''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
