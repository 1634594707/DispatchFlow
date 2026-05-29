-- Road network nodes and segments (replaces YAML road-nodes / road-segments as source of truth over time).
-- Prerequisite: V4__park_and_station.sql (t_park).

USE `fsd_core`;

CREATE TABLE IF NOT EXISTS `t_road_node` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '所属园区ID',
  `node_code` VARCHAR(64) NOT NULL COMMENT '路网节点编码',
  `coord_x` DECIMAL(12,4) NOT NULL COMMENT '园区坐标X',
  `coord_y` DECIMAL(12,4) NOT NULL COMMENT '园区坐标Y',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_park_node_code` (`park_id`, `node_code`),
  KEY `idx_park_status` (`park_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='路网节点表';

CREATE TABLE IF NOT EXISTS `t_road_segment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '所属园区ID',
  `from_node_code` VARCHAR(64) NOT NULL COMMENT '起点节点编码',
  `to_node_code` VARCHAR(64) NOT NULL COMMENT '终点节点编码',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/DISABLED',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_park_segment` (`park_id`, `from_node_code`, `to_node_code`),
  KEY `idx_park_segment_status` (`park_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='路网边表（无向，服务层双向建图）';

-- Seed: default park_id=1 (from application.yml fsd.park.road-nodes / road-segments)
INSERT INTO `t_road_node` (`id`, `park_id`, `node_code`, `coord_x`, `coord_y`, `status`, `version`, `deleted`) VALUES
  (3001, 1, 'R1', 100.0000, 120.0000, 'ACTIVE', 0, 0),
  (3002, 1, 'R2', 220.0000, 120.0000, 'ACTIVE', 0, 0),
  (3003, 1, 'R3', 420.0000, 120.0000, 'ACTIVE', 0, 0),
  (3004, 1, 'R4', 620.0000, 120.0000, 'ACTIVE', 0, 0),
  (3005, 1, 'R5', 820.0000, 120.0000, 'ACTIVE', 0, 0),
  (3006, 1, 'R6', 1020.0000, 120.0000, 'ACTIVE', 0, 0),
  (3007, 1, 'R7', 100.0000, 250.0000, 'ACTIVE', 0, 0),
  (3008, 1, 'R8', 220.0000, 250.0000, 'ACTIVE', 0, 0),
  (3009, 1, 'R9', 420.0000, 250.0000, 'ACTIVE', 0, 0),
  (3010, 1, 'R10', 620.0000, 250.0000, 'ACTIVE', 0, 0),
  (3011, 1, 'R11', 820.0000, 250.0000, 'ACTIVE', 0, 0),
  (3012, 1, 'R12', 1020.0000, 250.0000, 'ACTIVE', 0, 0),
  (3013, 1, 'R13', 100.0000, 550.0000, 'ACTIVE', 0, 0),
  (3014, 1, 'R14', 220.0000, 550.0000, 'ACTIVE', 0, 0),
  (3015, 1, 'R15', 420.0000, 550.0000, 'ACTIVE', 0, 0),
  (3016, 1, 'R16', 620.0000, 550.0000, 'ACTIVE', 0, 0),
  (3017, 1, 'R17', 820.0000, 550.0000, 'ACTIVE', 0, 0),
  (3018, 1, 'R18', 1020.0000, 550.0000, 'ACTIVE', 0, 0),
  (3019, 1, 'R19', 100.0000, 700.0000, 'ACTIVE', 0, 0),
  (3020, 1, 'R20', 220.0000, 700.0000, 'ACTIVE', 0, 0),
  (3021, 1, 'R21', 420.0000, 700.0000, 'ACTIVE', 0, 0),
  (3022, 1, 'R22', 620.0000, 700.0000, 'ACTIVE', 0, 0),
  (3023, 1, 'R23', 820.0000, 700.0000, 'ACTIVE', 0, 0),
  (3024, 1, 'R24', 1020.0000, 700.0000, 'ACTIVE', 0, 0)
ON DUPLICATE KEY UPDATE `coord_x` = VALUES(`coord_x`), `coord_y` = VALUES(`coord_y`), `status` = VALUES(`status`);

INSERT INTO `t_road_segment` (`id`, `park_id`, `from_node_code`, `to_node_code`, `status`, `version`, `deleted`) VALUES
  (4001, 1, 'R1', 'R2', 'ACTIVE', 0, 0),
  (4002, 1, 'R2', 'R3', 'ACTIVE', 0, 0),
  (4003, 1, 'R3', 'R4', 'ACTIVE', 0, 0),
  (4004, 1, 'R4', 'R5', 'ACTIVE', 0, 0),
  (4005, 1, 'R5', 'R6', 'ACTIVE', 0, 0),
  (4006, 1, 'R7', 'R8', 'ACTIVE', 0, 0),
  (4007, 1, 'R8', 'R9', 'ACTIVE', 0, 0),
  (4008, 1, 'R9', 'R10', 'ACTIVE', 0, 0),
  (4009, 1, 'R10', 'R11', 'ACTIVE', 0, 0),
  (4010, 1, 'R11', 'R12', 'ACTIVE', 0, 0),
  (4011, 1, 'R13', 'R14', 'ACTIVE', 0, 0),
  (4012, 1, 'R14', 'R15', 'ACTIVE', 0, 0),
  (4013, 1, 'R15', 'R16', 'ACTIVE', 0, 0),
  (4014, 1, 'R16', 'R17', 'ACTIVE', 0, 0),
  (4015, 1, 'R17', 'R18', 'ACTIVE', 0, 0),
  (4016, 1, 'R19', 'R20', 'ACTIVE', 0, 0),
  (4017, 1, 'R20', 'R21', 'ACTIVE', 0, 0),
  (4018, 1, 'R21', 'R22', 'ACTIVE', 0, 0),
  (4019, 1, 'R22', 'R23', 'ACTIVE', 0, 0),
  (4020, 1, 'R23', 'R24', 'ACTIVE', 0, 0),
  (4021, 1, 'R1', 'R7', 'ACTIVE', 0, 0),
  (4022, 1, 'R7', 'R13', 'ACTIVE', 0, 0),
  (4023, 1, 'R13', 'R19', 'ACTIVE', 0, 0),
  (4024, 1, 'R2', 'R8', 'ACTIVE', 0, 0),
  (4025, 1, 'R8', 'R14', 'ACTIVE', 0, 0),
  (4026, 1, 'R14', 'R20', 'ACTIVE', 0, 0),
  (4027, 1, 'R3', 'R9', 'ACTIVE', 0, 0),
  (4028, 1, 'R9', 'R15', 'ACTIVE', 0, 0),
  (4029, 1, 'R15', 'R21', 'ACTIVE', 0, 0),
  (4030, 1, 'R4', 'R10', 'ACTIVE', 0, 0),
  (4031, 1, 'R10', 'R16', 'ACTIVE', 0, 0),
  (4032, 1, 'R16', 'R22', 'ACTIVE', 0, 0),
  (4033, 1, 'R5', 'R11', 'ACTIVE', 0, 0),
  (4034, 1, 'R11', 'R17', 'ACTIVE', 0, 0),
  (4035, 1, 'R17', 'R23', 'ACTIVE', 0, 0),
  (4036, 1, 'R6', 'R12', 'ACTIVE', 0, 0),
  (4037, 1, 'R12', 'R18', 'ACTIVE', 0, 0),
  (4038, 1, 'R18', 'R24', 'ACTIVE', 0, 0)
ON DUPLICATE KEY UPDATE `status` = VALUES(`status`);
