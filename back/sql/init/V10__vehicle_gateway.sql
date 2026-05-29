-- Phase 3: real vehicle gateway — link mode, commands, credentials
-- Prerequisite: V1__init_schema.sql (t_vehicle)

USE `fsd_core`;

ALTER TABLE `t_vehicle`
    ADD COLUMN `link_mode` VARCHAR(16) NOT NULL DEFAULT 'SIM' COMMENT '接入模式 SIM/REAL' AFTER `vehicle_type`;

CREATE TABLE IF NOT EXISTS `t_vehicle_command` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `vehicle_id` BIGINT NOT NULL COMMENT '车辆ID',
  `task_id` BIGINT NOT NULL COMMENT '调度任务ID',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `command_type` VARCHAR(32) NOT NULL COMMENT '指令类型',
  `command_status` VARCHAR(32) NOT NULL COMMENT '指令状态',
  `payload_json` TEXT NOT NULL COMMENT '指令载荷JSON',
  `fail_reason` VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
  `issued_at` DATETIME NOT NULL COMMENT '下发时间',
  `delivered_at` DATETIME DEFAULT NULL COMMENT '车端拉取时间',
  `acked_at` DATETIME DEFAULT NULL COMMENT '确认时间',
  `failed_at` DATETIME DEFAULT NULL COMMENT '失败时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_vehicle_status_issued` (`vehicle_id`, `command_status`, `issued_at`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='车端指令表';

CREATE TABLE IF NOT EXISTS `t_vehicle_credential` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `vehicle_id` BIGINT NOT NULL COMMENT '车辆ID',
  `api_key` VARCHAR(128) NOT NULL COMMENT '车端 API Key',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vehicle_id` (`vehicle_id`),
  UNIQUE KEY `uk_api_key` (`api_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='车端鉴权凭证';

-- Demo real vehicle (simulation can stay on PARK-* SIM fleet)
INSERT INTO `t_vehicle` (
  `vehicle_code`, `vehicle_name`, `vehicle_type`, `link_mode`,
  `online_status`, `dispatch_status`,
  `current_latitude`, `current_longitude`, `battery_level`,
  `last_report_time`, `remark`, `version`, `deleted`
)
SELECT 'REAL-001', 'Real AGV 001', 'AGV', 'REAL',
       'ONLINE', 'IDLE', 220.000000, 170.000000, 95,
       NOW(), 'phase3-real-demo', 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `t_vehicle` WHERE `vehicle_code` = 'REAL-001' AND `deleted` = 0);

INSERT INTO `t_vehicle_credential` (`vehicle_id`, `api_key`, `status`)
SELECT v.id, 'real-demo-key-001', 'ACTIVE'
FROM `t_vehicle` v
WHERE v.vehicle_code = 'REAL-001' AND v.deleted = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_vehicle_credential` c WHERE c.vehicle_id = v.id
  );
