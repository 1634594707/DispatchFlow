-- Multi-park and station tables (replaces YAML station config as source of truth).
-- Prerequisite: run V01__init_schema.sql first (creates fsd_core and t_order).

CREATE DATABASE IF NOT EXISTS `fsd_core`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE `fsd_core`;

CREATE TABLE IF NOT EXISTS `t_park` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_code` VARCHAR(64) NOT NULL COMMENT '园区编码',
  `park_name` VARCHAR(128) NOT NULL COMMENT '园区名称',
  `map_width` INT DEFAULT NULL COMMENT '地图宽度(px)',
  `map_height` INT DEFAULT NULL COMMENT '地图高度(px)',
  `min_zoom` INT DEFAULT NULL COMMENT '最小缩放',
  `max_zoom` INT DEFAULT NULL COMMENT '最大缩放',
  `vehicle_speed_px_per_second` DECIMAL(10,2) DEFAULT NULL COMMENT '仿真车速(px/s)',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
  `default_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否默认园区',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_park_code` (`park_code`),
  KEY `idx_status_default` (`status`, `default_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='园区表';

CREATE TABLE IF NOT EXISTS `t_station` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '所属园区ID',
  `station_code` VARCHAR(64) NOT NULL COMMENT '站点编码',
  `station_name` VARCHAR(128) NOT NULL COMMENT '站点名称',
  `station_type` VARCHAR(32) NOT NULL COMMENT '站点类型: PICKUP/DROPOFF/GENERAL',
  `coord_x` DECIMAL(12,4) NOT NULL COMMENT '园区坐标X',
  `coord_y` DECIMAL(12,4) NOT NULL COMMENT '园区坐标Y',
  `area` VARCHAR(32) DEFAULT NULL COMMENT '区域标识',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_park_station_code` (`park_id`, `station_code`),
  KEY `idx_park_id_type` (`park_id`, `station_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='园区站点表';

ALTER TABLE `t_order`
  ADD COLUMN `park_id` BIGINT DEFAULT NULL COMMENT '所属园区ID' AFTER `biz_type`,
  ADD KEY `idx_park_id` (`park_id`);

-- Default park (matches legacy application.yml layout)
INSERT INTO `t_park` (
  `id`, `park_code`, `park_name`, `map_width`, `map_height`, `min_zoom`, `max_zoom`,
  `vehicle_speed_px_per_second`, `status`, `default_flag`, `version`, `deleted`
) VALUES (
  1, 'DEFAULT', '默认示范园区', 1200, 800, -1, 3, 8.00, 'ACTIVE', 1, 0, 0
) ON DUPLICATE KEY UPDATE
  `park_name` = VALUES(`park_name`),
  `map_width` = VALUES(`map_width`),
  `map_height` = VALUES(`map_height`),
  `default_flag` = VALUES(`default_flag`);

INSERT INTO `t_station` (
  `id`, `park_id`, `station_code`, `station_name`, `station_type`, `coord_x`, `coord_y`, `area`, `status`, `sort_order`, `version`, `deleted`
) VALUES
  (101, 1, 'A1', 'A1 Pickup', 'PICKUP', 220.0000, 170.0000, 'A', 'ACTIVE', 1, 0, 0),
  (102, 1, 'A2', 'A2 Pickup', 'PICKUP', 420.0000, 170.0000, 'A', 'ACTIVE', 2, 0, 0),
  (103, 1, 'A3', 'A3 Pickup', 'PICKUP', 620.0000, 170.0000, 'A', 'ACTIVE', 3, 0, 0),
  (104, 1, 'A4', 'A4 Pickup', 'PICKUP', 820.0000, 170.0000, 'A', 'ACTIVE', 4, 0, 0),
  (201, 1, 'B1', 'B1 Dropoff', 'DROPOFF', 220.0000, 620.0000, 'B', 'ACTIVE', 11, 0, 0),
  (202, 1, 'B2', 'B2 Dropoff', 'DROPOFF', 420.0000, 620.0000, 'B', 'ACTIVE', 12, 0, 0),
  (203, 1, 'B3', 'B3 Dropoff', 'DROPOFF', 620.0000, 620.0000, 'B', 'ACTIVE', 13, 0, 0),
  (204, 1, 'B4', 'B4 Dropoff', 'DROPOFF', 820.0000, 620.0000, 'B', 'ACTIVE', 14, 0, 0)
ON DUPLICATE KEY UPDATE
  `station_name` = VALUES(`station_name`),
  `coord_x` = VALUES(`coord_x`),
  `coord_y` = VALUES(`coord_y`),
  `area` = VALUES(`area`),
  `status` = VALUES(`status`);

-- Second park for multi-park demo
INSERT INTO `t_park` (
  `id`, `park_code`, `park_name`, `map_width`, `map_height`, `min_zoom`, `max_zoom`,
  `vehicle_speed_px_per_second`, `status`, `default_flag`, `version`, `deleted`
) VALUES (
  2, 'CAMPUS-B', 'B区仓储园', 1200, 800, -1, 3, 8.00, 'ACTIVE', 0, 0, 0
) ON DUPLICATE KEY UPDATE `park_name` = VALUES(`park_name`);

INSERT INTO `t_station` (
  `id`, `park_id`, `station_code`, `station_name`, `station_type`, `coord_x`, `coord_y`, `area`, `status`, `sort_order`, `version`, `deleted`
) VALUES
  (301, 2, 'C1', 'C1 取货', 'PICKUP', 150.0000, 200.0000, 'C', 'ACTIVE', 1, 0, 0),
  (302, 2, 'C2', 'C2 取货', 'PICKUP', 350.0000, 200.0000, 'C', 'ACTIVE', 2, 0, 0),
  (401, 2, 'D1', 'D1 送货', 'DROPOFF', 150.0000, 600.0000, 'D', 'ACTIVE', 11, 0, 0),
  (402, 2, 'D2', 'D2 送货', 'DROPOFF', 350.0000, 600.0000, 'D', 'ACTIVE', 12, 0, 0)
ON DUPLICATE KEY UPDATE
  `station_name` = VALUES(`station_name`),
  `coord_x` = VALUES(`coord_x`),
  `coord_y` = VALUES(`coord_y`);
