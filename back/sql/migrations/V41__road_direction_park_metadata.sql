-- V41: 阶段七架构扩展 — 单行道方向 + 跨园区路网 + 园区元数据标准化
--
-- 7.1 单行道支持：t_road_segment 增加 direction 字段
--     BIDIRECTIONAL = 双向通行（默认，向后兼容）
--     FORWARD       = 仅正向通行（from_node → to_node）
--     REVERSE       = 仅反向通行（to_node → from_node）
--
-- 7.4 跨园区路网：t_road_segment 增加 connecting_park_id 字段
--     非 NULL 表示该路段为跨园区连接段，连接当前 park_id 与 connecting_park_id。
--     ParkRoadGraph.fromDatabase() 可据此在跨园区路段上建立跨园区邻接边。
--
-- 7.3 园区元数据标准化：t_park 增加 anchor_lng/anchor_lat/park_width_meters/park_height_meters/scenario_code
--     将前端硬编码 ZJF_PILOT_GEO 的锚点/尺寸/场景标识迁入数据库，
--     实现多园区可配置。向后兼容：列允许 NULL，前端缺失时回退到硬编码。
--
-- 幂等性：所有 ALTER 均通过 information_schema 判断列是否存在。

USE `fsd_core`;

-- ============================================================
-- 7.1 t_road_segment.direction
-- ============================================================
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_segment'
     AND COLUMN_NAME = 'direction') = 0,
  'ALTER TABLE `t_road_segment` ADD COLUMN `direction` VARCHAR(20) NOT NULL DEFAULT ''BIDIRECTIONAL'' COMMENT ''方向：BIDIRECTIONAL=双向, FORWARD=仅正向, REVERSE=仅反向'' AFTER `to_node_code`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 7.4 t_road_segment.connecting_park_id
-- ============================================================
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_segment'
     AND COLUMN_NAME = 'connecting_park_id') = 0,
  'ALTER TABLE `t_road_segment` ADD COLUMN `connecting_park_id` BIGINT DEFAULT NULL COMMENT ''跨园区连接段的对端园区ID（NULL=园内路段）'' AFTER `direction`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 7.3 t_park 元数据列
-- ============================================================
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_park'
     AND COLUMN_NAME = 'anchor_lng') = 0,
  'ALTER TABLE `t_park` ADD COLUMN `anchor_lng` DECIMAL(10,6) DEFAULT NULL COMMENT ''坐标转换锚点经度（GCJ-02）'' AFTER `center_lat`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_park'
     AND COLUMN_NAME = 'anchor_lat') = 0,
  'ALTER TABLE `t_park` ADD COLUMN `anchor_lat` DECIMAL(10,6) DEFAULT NULL COMMENT ''坐标转换锚点纬度（GCJ-02）'' AFTER `anchor_lng`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_park'
     AND COLUMN_NAME = 'park_width_meters') = 0,
  'ALTER TABLE `t_park` ADD COLUMN `park_width_meters` DECIMAL(10,2) DEFAULT NULL COMMENT ''园区真实宽度（米）'' AFTER `anchor_lat`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_park'
     AND COLUMN_NAME = 'park_height_meters') = 0,
  'ALTER TABLE `t_park` ADD COLUMN `park_height_meters` DECIMAL(10,2) DEFAULT NULL COMMENT ''园区真实高度（米）'' AFTER `park_width_meters`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_park'
     AND COLUMN_NAME = 'scenario_code') = 0,
  'ALTER TABLE `t_park` ADD COLUMN `scenario_code` VARCHAR(64) DEFAULT NULL COMMENT ''场景编码（如 ZJF_DIESHIQIAO_PILOT）'' AFTER `park_height_meters`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 7.3 初始化 ZJF 试点园区元数据（叠石桥试点）
-- ============================================================
UPDATE `t_park` SET
  `anchor_lng` = 121.080354,
  `anchor_lat` = 31.961977,
  `park_width_meters` = 1570.00,
  `park_height_meters` = 470.00,
  `scenario_code` = 'ZJF_DIESHIQIAO_PILOT'
WHERE `park_code` = 'ZJF'
  AND `anchor_lng` IS NULL
  AND `deleted` = 0;
