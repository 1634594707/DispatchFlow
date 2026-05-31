-- V3: park geo anchor for 叠石桥家纺产业带 (找家纺类短驳无人配送场景)
-- Idempotent: safe if columns already exist (run-dev manual apply + Flyway).

SET @db := DATABASE();

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_park' AND COLUMN_NAME = 'center_lng') = 0,
  'ALTER TABLE `t_park` ADD COLUMN `center_lng` DECIMAL(10,6) DEFAULT NULL COMMENT ''地图中心经度 GCJ-02'' AFTER `vehicle_speed_px_per_second`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_park' AND COLUMN_NAME = 'center_lat') = 0,
  'ALTER TABLE `t_park` ADD COLUMN `center_lat` DECIMAL(10,6) DEFAULT NULL COMMENT ''地图中心纬度 GCJ-02'' AFTER `center_lng`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_park' AND COLUMN_NAME = 'map_provider') = 0,
  'ALTER TABLE `t_park` ADD COLUMN `map_provider` VARCHAR(32) DEFAULT NULL COMMENT ''地图提供商: AMAP 等'' AFTER `center_lat`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `t_park`
SET
  `park_name` = '叠石桥家纺示范园区',
  `center_lng` = 121.062280,
  `center_lat` = 31.912450,
  `map_provider` = 'AMAP',
  `remark` = '仿找家纺产业带：电商ERP下单 + AI无人快递车短驳（叠石桥国际家纺城片区）'
WHERE `park_code` = 'DEFAULT' AND `deleted` = 0;
