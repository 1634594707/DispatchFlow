-- Phase 14: vertical industry (routes, hubs, peak mode, automation rules, battery swap)

USE `fsd_core`;

ALTER TABLE `t_station`
  ADD COLUMN `capacity_limit` INT DEFAULT NULL COMMENT '枢纽/缓冲容量上限，NULL=不限' AFTER `sort_order`;

ALTER TABLE `t_order`
  ADD COLUMN `route_id` BIGINT DEFAULT NULL COMMENT '绑定调度线路' AFTER `park_id`;

CREATE TABLE IF NOT EXISTS `t_dispatch_route` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '园区ID',
  `route_code` VARCHAR(64) NOT NULL COMMENT '线路编码',
  `route_name` VARCHAR(128) NOT NULL COMMENT '线路名称',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `service_start_time` TIME DEFAULT NULL COMMENT '运营开始',
  `service_end_time` TIME DEFAULT NULL COMMENT '运营结束',
  `required_vehicle_type` VARCHAR(32) DEFAULT NULL COMMENT '所需车型',
  `max_concurrent_tasks` INT DEFAULT NULL COMMENT '最大并发任务数',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version` INT NOT NULL DEFAULT 0,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_park_route_code` (`park_id`, `route_code`),
  KEY `idx_park_status` (`park_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='调度线路';

CREATE TABLE IF NOT EXISTS `t_dispatch_route_station` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `route_id` BIGINT NOT NULL COMMENT '线路ID',
  `station_id` BIGINT NOT NULL COMMENT '站点ID',
  `sequence_no` INT NOT NULL COMMENT '顺序号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route_station` (`route_id`, `station_id`),
  KEY `idx_route_seq` (`route_id`, `sequence_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='线路站点顺序';

CREATE TABLE IF NOT EXISTS `t_peak_mode_state` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '园区ID',
  `mode` VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/PEAK',
  `template_code` VARCHAR(64) NOT NULL DEFAULT 'DAILY' COMMENT '预案模板',
  `schedule_cron` VARCHAR(64) DEFAULT NULL COMMENT '定时 cron',
  `enabled_at` DATETIME DEFAULT NULL COMMENT '启用时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_park_id` (`park_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='高峰模式状态';

CREATE TABLE IF NOT EXISTS `t_dispatch_automation_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '园区ID',
  `rule_name` VARCHAR(128) NOT NULL COMMENT '规则名称',
  `condition_type` VARCHAR(64) NOT NULL COMMENT '条件类型',
  `condition_value` VARCHAR(128) NOT NULL COMMENT '条件值',
  `action_type` VARCHAR(64) NOT NULL COMMENT '动作类型',
  `action_params_json` TEXT DEFAULT NULL COMMENT '动作参数',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_park_enabled` (`park_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='IF-THEN自动化规则';

CREATE TABLE IF NOT EXISTS `t_dispatch_automation_rule_audit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_id` BIGINT NOT NULL COMMENT '规则ID',
  `action` VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/DELETE/ENABLE/DISABLE',
  `operator` VARCHAR(64) DEFAULT NULL COMMENT '操作人',
  `detail` VARCHAR(512) DEFAULT NULL COMMENT '详情',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_rule_created` (`rule_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='规则变更审计';

CREATE TABLE IF NOT EXISTS `t_battery_swap_cabinet` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '园区ID',
  `cabinet_code` VARCHAR(64) NOT NULL COMMENT '换电柜编码',
  `cabinet_name` VARCHAR(128) NOT NULL COMMENT '换电柜名称',
  `coord_x` DECIMAL(12,4) NOT NULL COMMENT 'X坐标',
  `coord_y` DECIMAL(12,4) NOT NULL COMMENT 'Y坐标',
  `slot_count` INT NOT NULL DEFAULT 4 COMMENT '槽位数',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `remark` VARCHAR(255) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_park_cabinet_code` (`park_id`, `cabinet_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='换电柜';

CREATE TABLE IF NOT EXISTS `t_battery_swap_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `vehicle_id` BIGINT NOT NULL COMMENT '车辆ID',
  `cabinet_id` BIGINT NOT NULL COMMENT '换电柜ID',
  `park_id` BIGINT NOT NULL COMMENT '园区ID',
  `status` VARCHAR(32) NOT NULL COMMENT 'IN_PROGRESS/COMPLETED/CANCELLED',
  `started_at` DATETIME NOT NULL COMMENT '开始时间',
  `finished_at` DATETIME DEFAULT NULL COMMENT '结束时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_vehicle_status` (`vehicle_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='换电会话';

-- Demo hub stations + route for 家纺产业带
INSERT INTO `t_station` (
  `id`, `park_id`, `station_code`, `station_name`, `station_type`, `coord_x`, `coord_y`,
  `area`, `status`, `sort_order`, `capacity_limit`, `version`, `deleted`
) VALUES
  (501, 1, 'HUB-MAIN', '星环枢纽', 'HUB', 520.0000, 400.0000, 'HUB', 'ACTIVE', 50, 6, 0, 0),
  (502, 1, 'BUF-A', 'A区缓冲', 'BUFFER', 320.0000, 400.0000, 'A', 'ACTIVE', 51, 3, 0, 0),
  (503, 1, 'MS-PORT', '母港分流点', 'MOTHERSHIP', 720.0000, 400.0000, 'MS', 'ACTIVE', 52, 4, 0, 0)
ON DUPLICATE KEY UPDATE
  `station_name` = VALUES(`station_name`),
  `station_type` = VALUES(`station_type`),
  `capacity_limit` = VALUES(`capacity_limit`);

INSERT INTO `t_dispatch_route` (
  `id`, `park_id`, `route_code`, `route_name`, `status`, `service_start_time`, `service_end_time`,
  `required_vehicle_type`, `max_concurrent_tasks`, `remark`, `version`, `deleted`
) VALUES (
  1, 1, 'RT-A1-B1', 'A1→枢纽→B1 家纺线', 'ACTIVE', '08:00:00', '22:00:00',
  'OUTDOOR_L4', 8, '家纺产业带示范线路', 0, 0
) ON DUPLICATE KEY UPDATE
  `route_name` = VALUES(`route_name`),
  `max_concurrent_tasks` = VALUES(`max_concurrent_tasks`);

INSERT INTO `t_dispatch_route_station` (`route_id`, `station_id`, `sequence_no`) VALUES
  (1, 101, 1),
  (1, 501, 2),
  (1, 201, 3)
ON DUPLICATE KEY UPDATE `sequence_no` = VALUES(`sequence_no`);

INSERT INTO `t_peak_mode_state` (`park_id`, `mode`, `template_code`) VALUES
  (1, 'NORMAL', 'DAILY')
ON DUPLICATE KEY UPDATE `template_code` = VALUES(`template_code`);

INSERT INTO `t_dispatch_automation_rule` (
  `park_id`, `rule_name`, `condition_type`, `condition_value`, `action_type`, `action_params_json`, `enabled`
) VALUES
  (1, '低电回充', 'SOC_BELOW', '20', 'CREATE_CHARGE_TASK', '{"priority":"P1"}', 1),
  (1, '高峰模式联动', 'PEAK_MODE', 'PEAK', 'BOOST_DISPATCH', '{"weightDistanceFactor":0.85}', 1)
ON DUPLICATE KEY UPDATE `rule_name` = VALUES(`rule_name`);

INSERT INTO `t_battery_swap_cabinet` (
  `id`, `park_id`, `cabinet_code`, `cabinet_name`, `coord_x`, `coord_y`, `slot_count`, `status`, `deleted`
) VALUES (
  1, 1, 'SWAP-01', '1号换电柜', 600.0000, 500.0000, 4, 'ACTIVE', 0
) ON DUPLICATE KEY UPDATE `cabinet_name` = VALUES(`cabinet_name`);
