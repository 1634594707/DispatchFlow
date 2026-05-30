-- Idempotent road segment traffic columns (safe to re-run after V13 partial apply)
USE `fsd_core`;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'fsd_core' AND TABLE_NAME = 't_road_segment' AND COLUMN_NAME = 'speed_limit_kmh') = 0,
    'ALTER TABLE `t_road_segment` ADD COLUMN `speed_limit_kmh` INT DEFAULT 15 COMMENT ''限速 km/h'' AFTER `status`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'fsd_core' AND TABLE_NAME = 't_road_segment' AND COLUMN_NAME = 'congestion_level') = 0,
    'ALTER TABLE `t_road_segment` ADD COLUMN `congestion_level` INT NOT NULL DEFAULT 0 COMMENT ''拥堵等级 0-3'' AFTER `speed_limit_kmh`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
