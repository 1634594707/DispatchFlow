-- Phase 12/13: telemetry history, webhook delivery logs, user alert settings, API rate-limit hits

USE `fsd_core`;

CREATE TABLE IF NOT EXISTS `t_fleet_telemetry_point` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `vehicle_id` BIGINT NOT NULL COMMENT '车辆ID',
  `park_id` BIGINT DEFAULT NULL COMMENT '园区ID',
  `coord_x` DECIMAL(12,4) NOT NULL COMMENT 'X坐标',
  `coord_y` DECIMAL(12,4) NOT NULL COMMENT 'Y坐标',
  `soc` INT DEFAULT NULL COMMENT '电量',
  `recorded_at` DATETIME NOT NULL COMMENT '记录时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_vehicle_recorded` (`vehicle_id`, `recorded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='车队遥测历史点';

CREATE TABLE IF NOT EXISTS `t_webhook_delivery_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `subscription_id` BIGINT NOT NULL COMMENT '订阅ID',
  `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型',
  `business_key` VARCHAR(128) DEFAULT NULL COMMENT '业务键',
  `http_status` INT DEFAULT NULL COMMENT 'HTTP状态码',
  `success` TINYINT NOT NULL DEFAULT 0 COMMENT '是否成功',
  `attempt_no` INT NOT NULL DEFAULT 1 COMMENT '尝试次数',
  `payload_summary` VARCHAR(512) DEFAULT NULL COMMENT 'payload摘要',
  `error_message` VARCHAR(512) DEFAULT NULL COMMENT '错误信息',
  `delivered_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投递时间',
  PRIMARY KEY (`id`),
  KEY `idx_sub_delivered` (`subscription_id`, `delivered_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Webhook投递日志';

CREATE TABLE IF NOT EXISTS `t_user_alert_setting` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `rules_json` TEXT NOT NULL COMMENT '告警规则JSON',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户告警规则';

ALTER TABLE `t_external_api_key`
  ADD COLUMN `rate_limit_hits` BIGINT NOT NULL DEFAULT 0 COMMENT '限流触发次数' AFTER `total_calls`;
