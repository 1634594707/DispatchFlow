-- Parking slots and charging piles (replaces YAML parking-spots as source of truth over time).
-- Prerequisite: V04__park_and_station.sql (t_park).

USE `fsd_core`;

CREATE TABLE IF NOT EXISTS `t_parking_slot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '所属园区ID',
  `slot_code` VARCHAR(64) NOT NULL COMMENT '车位编码',
  `slot_name` VARCHAR(128) NOT NULL COMMENT '车位名称',
  `slot_type` VARCHAR(32) NOT NULL DEFAULT 'STANDBY' COMMENT '车位类型: STANDBY/CHARGING_ONLY',
  `coord_x` DECIMAL(12,4) NOT NULL COMMENT '园区坐标X',
  `coord_y` DECIMAL(12,4) NOT NULL COMMENT '园区坐标Y',
  `status` VARCHAR(32) NOT NULL DEFAULT 'FREE' COMMENT '状态: FREE/OCCUPIED/RESERVED/CHARGING/FAULT',
  `occupied_vehicle_id` BIGINT DEFAULT NULL COMMENT '当前占用车辆ID',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_park_slot_code` (`park_id`, `slot_code`),
  KEY `idx_park_status` (`park_id`, `status`),
  KEY `idx_occupied_vehicle` (`occupied_vehicle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='车位表';

CREATE TABLE IF NOT EXISTS `t_charging_pile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '所属园区ID',
  `pile_code` VARCHAR(64) NOT NULL COMMENT '充电桩编码',
  `pile_name` VARCHAR(128) NOT NULL COMMENT '充电桩名称',
  `parking_slot_id` BIGINT NOT NULL COMMENT '绑定车位ID',
  `status` VARCHAR(32) NOT NULL DEFAULT 'FREE' COMMENT '状态: FREE/OCCUPIED/RESERVED/CHARGING/FAULT',
  `occupied_vehicle_id` BIGINT DEFAULT NULL COMMENT '当前占用车辆ID',
  `max_power_kw` DECIMAL(8,2) DEFAULT NULL COMMENT '额定功率(kW)',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_park_pile_code` (`park_id`, `pile_code`),
  UNIQUE KEY `uk_parking_slot` (`parking_slot_id`),
  KEY `idx_park_pile_status` (`park_id`, `status`),
  KEY `idx_pile_occupied_vehicle` (`occupied_vehicle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='充电桩表';

-- Default park: aligns with application.yml fsd.park.parking-spots (P1–P6)
INSERT INTO `t_parking_slot` (
  `id`, `park_id`, `slot_code`, `slot_name`, `slot_type`, `coord_x`, `coord_y`, `status`, `sort_order`, `version`, `deleted`
) VALUES
  (1001, 1, 'P1', '车位 P1', 'STANDBY', 80.0000, 700.0000, 'FREE', 1, 0, 0),
  (1002, 1, 'P2', '车位 P2', 'STANDBY', 120.0000, 700.0000, 'FREE', 2, 0, 0),
  (1003, 1, 'P3', '车位 P3', 'STANDBY', 160.0000, 700.0000, 'FREE', 3, 0, 0),
  (1004, 1, 'P4', '车位 P4', 'STANDBY', 200.0000, 700.0000, 'FREE', 4, 0, 0),
  (1005, 1, 'P5', '车位 P5', 'STANDBY', 80.0000, 740.0000, 'FREE', 5, 0, 0),
  (1006, 1, 'P6', '车位 P6', 'STANDBY', 140.0000, 740.0000, 'FREE', 6, 0, 0)
ON DUPLICATE KEY UPDATE
  `slot_name` = VALUES(`slot_name`),
  `coord_x` = VALUES(`coord_x`),
  `coord_y` = VALUES(`coord_y`),
  `status` = VALUES(`status`);

INSERT INTO `t_charging_pile` (
  `id`, `park_id`, `pile_code`, `pile_name`, `parking_slot_id`, `status`, `max_power_kw`, `sort_order`, `version`, `deleted`
) VALUES
  (2001, 1, 'CP1', '充电桩 CP1', 1001, 'FREE', 7.00, 1, 0, 0),
  (2002, 1, 'CP2', '充电桩 CP2', 1002, 'FREE', 7.00, 2, 0, 0),
  (2003, 1, 'CP3', '充电桩 CP3', 1003, 'FREE', 7.00, 3, 0, 0),
  (2004, 1, 'CP4', '充电桩 CP4', 1004, 'FREE', 7.00, 4, 0, 0),
  (2005, 1, 'CP5', '充电桩 CP5', 1005, 'FREE', 7.00, 5, 0, 0),
  (2006, 1, 'CP6', '充电桩 CP6', 1006, 'FREE', 7.00, 6, 0, 0)
ON DUPLICATE KEY UPDATE
  `pile_name` = VALUES(`pile_name`),
  `parking_slot_id` = VALUES(`parking_slot_id`),
  `max_power_kw` = VALUES(`max_power_kw`);

-- Campus-B: minimal charging bays for multi-park demo
INSERT INTO `t_parking_slot` (
  `id`, `park_id`, `slot_code`, `slot_name`, `slot_type`, `coord_x`, `coord_y`, `status`, `sort_order`, `version`, `deleted`
) VALUES
  (1101, 2, 'P1', 'B区车位 P1', 'STANDBY', 80.0000, 700.0000, 'FREE', 1, 0, 0),
  (1102, 2, 'P2', 'B区车位 P2', 'STANDBY', 140.0000, 700.0000, 'FREE', 2, 0, 0)
ON DUPLICATE KEY UPDATE
  `slot_name` = VALUES(`slot_name`),
  `coord_x` = VALUES(`coord_x`),
  `coord_y` = VALUES(`coord_y`);

INSERT INTO `t_charging_pile` (
  `id`, `park_id`, `pile_code`, `pile_name`, `parking_slot_id`, `status`, `max_power_kw`, `sort_order`, `version`, `deleted`
) VALUES
  (2101, 2, 'CP1', 'B区充电桩 CP1', 1101, 'FREE', 7.00, 1, 0, 0),
  (2102, 2, 'CP2', 'B区充电桩 CP2', 1102, 'FREE', 7.00, 2, 0, 0)
ON DUPLICATE KEY UPDATE
  `pile_name` = VALUES(`pile_name`),
  `parking_slot_id` = VALUES(`parking_slot_id`);
