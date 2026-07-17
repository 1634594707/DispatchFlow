-- V40: 围栏告警分级 + 每围栏 GPS 缓冲距离（阶段六 6.1 / 6.2）
--
-- 6.1 新增 response_level 字段：
--     INFO  = 仅记录日志，不写异常、不触发自动化规则
--     WARN  = 记录异常并告警，但不触发紧急停车/升级流程
--     BLOCK = 记录异常并触发紧急停车（最高响应级别）
--     默认 WARN，保持与历史行为一致。
--
-- 6.2 新增 buffer_meters 字段：
--     替代 GeofenceBreachServiceImpl 中硬编码的 GPS_BUFFER_METERS=15.0，
--     每个围栏可独立设置 GPS 缓冲距离（米），用于 GEOFENCE_EXIT 时的边界容差判定。
--     默认 15.0，保持与历史行为一致。
--
-- 幂等性：使用 information_schema 判断列是否存在，可安全重复执行。

USE `fsd_core`;

-- ============================================================
-- 1. response_level 列
-- ============================================================
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_park_geofence'
     AND COLUMN_NAME = 'response_level') = 0,
  'ALTER TABLE `t_park_geofence` ADD COLUMN `response_level` VARCHAR(20) NOT NULL DEFAULT ''WARN'' COMMENT ''响应级别：INFO=仅日志, WARN=告警不升级, BLOCK=触发紧急停车'' AFTER `fence_type`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. buffer_meters 列
-- ============================================================
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_park_geofence'
     AND COLUMN_NAME = 'buffer_meters') = 0,
  'ALTER TABLE `t_park_geofence` ADD COLUMN `buffer_meters` DECIMAL(10,2) NOT NULL DEFAULT 15.00 COMMENT ''GPS 缓冲距离（米），用于 GEOFENCE_EXIT 边界容差'' AFTER `response_level`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. 历史围栏响应级别初始化
--    - BOUNDARY 围栏默认 WARN（边界越界需告警但不需紧急停车）
--    - RESTRICTED 围栏默认 BLOCK（禁入区进入属于高风险，触发紧急停车）
-- ============================================================
UPDATE `t_park_geofence`
SET `response_level` = 'BLOCK'
WHERE `fence_type` = 'RESTRICTED'
  AND (`response_level` IS NULL OR `response_level` = '')
  AND `deleted` = 0;

UPDATE `t_park_geofence`
SET `response_level` = 'WARN'
WHERE `fence_type` = 'BOUNDARY'
  AND (`response_level` IS NULL OR `response_level` = '')
  AND `deleted` = 0;
