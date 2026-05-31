-- V3 M3: park geofence + optional GCJ-02 on stations / parking slots

CREATE TABLE IF NOT EXISTS `t_park_geofence` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '园区ID',
  `fence_code` VARCHAR(64) NOT NULL COMMENT '围栏编码',
  `fence_name` VARCHAR(128) NOT NULL COMMENT '围栏名称',
  `fence_type` VARCHAR(32) NOT NULL DEFAULT 'BOUNDARY' COMMENT 'BOUNDARY=越界告警, RESTRICTED=禁入告警',
  `polygon_json` JSON NOT NULL COMMENT 'GCJ-02 多边形 [[lng,lat],...]',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_park_fence_code` (`park_id`, `fence_code`),
  KEY `idx_park_status` (`park_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='园区地理围栏';

SET @db := DATABASE();

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_station' AND COLUMN_NAME = 'coord_lng') = 0,
  'ALTER TABLE `t_station` ADD COLUMN `coord_lng` DECIMAL(10,6) DEFAULT NULL COMMENT ''GCJ-02 经度'' AFTER `coord_y`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_station' AND COLUMN_NAME = 'coord_lat') = 0,
  'ALTER TABLE `t_station` ADD COLUMN `coord_lat` DECIMAL(10,6) DEFAULT NULL COMMENT ''GCJ-02 纬度'' AFTER `coord_lng`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_parking_slot' AND COLUMN_NAME = 'coord_lng') = 0,
  'ALTER TABLE `t_parking_slot` ADD COLUMN `coord_lng` DECIMAL(10,6) DEFAULT NULL COMMENT ''GCJ-02 经度'' AFTER `coord_y`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_parking_slot' AND COLUMN_NAME = 'coord_lat') = 0,
  'ALTER TABLE `t_parking_slot` ADD COLUMN `coord_lat` DECIMAL(10,6) DEFAULT NULL COMMENT ''GCJ-02 纬度'' AFTER `coord_lng`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO `t_park_geofence` (
  `park_id`, `fence_code`, `fence_name`, `fence_type`, `polygon_json`, `status`, `remark`, `deleted`
)
SELECT
  p.id,
  'DEFAULT-BOUNDARY',
  '叠石桥示范园区边界',
  'BOUNDARY',
  JSON_ARRAY(
    JSON_ARRAY(121.052280, 31.902450),
    JSON_ARRAY(121.072280, 31.902450),
    JSON_ARRAY(121.072280, 31.922450),
    JSON_ARRAY(121.052280, 31.922450)
  ),
  'ACTIVE',
  'V22 默认园区边界（约 2km x 2km）',
  0
FROM `t_park` p
WHERE p.park_code = 'DEFAULT' AND p.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_park_geofence` g
    WHERE g.park_id = p.id AND g.fence_code = 'DEFAULT-BOUNDARY' AND g.deleted = 0
  );
