-- Phase 8/9: dispatch strategy profiles, webhooks, external API keys, road segment traffic fields

USE `fsd_core`;

CREATE TABLE IF NOT EXISTS `t_dispatch_strategy_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `profile_name` VARCHAR(64) NOT NULL COMMENT '策略名称',
  `profile_type` VARCHAR(32) NOT NULL DEFAULT 'PRODUCTION' COMMENT 'PRODUCTION/EXPERIMENT',
  `active_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否当前生效',
  `gray_percent` INT NOT NULL DEFAULT 0 COMMENT '灰度流量百分比 0-100',
  `park_id` BIGINT DEFAULT NULL COMMENT '适用园区，空表示全局',
  `weight_distance` DECIMAL(10,4) NOT NULL DEFAULT 1.0000 COMMENT '距离权重',
  `weight_soc_margin` DECIMAL(10,4) NOT NULL DEFAULT 0.1500 COMMENT '电量惩罚权重',
  `weight_plugged_standby_bonus` DECIMAL(10,4) NOT NULL DEFAULT 80.0000 COMMENT '插枪待命奖励',
  `min_assignable_soc` INT NOT NULL DEFAULT 30 COMMENT '最低可派 SOC',
  `full_soc` INT NOT NULL DEFAULT 100 COMMENT '满电 SOC',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `version` INT NOT NULL DEFAULT 0,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_active_park` (`active_flag`, `park_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='派车策略配置';

CREATE TABLE IF NOT EXISTS `t_dispatch_strategy_change_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `profile_id` BIGINT NOT NULL COMMENT '策略ID',
  `profile_name` VARCHAR(64) NOT NULL COMMENT '策略名称',
  `change_type` VARCHAR(32) NOT NULL COMMENT 'CREATE/UPDATE/ACTIVATE/DEACTIVATE',
  `operator_name` VARCHAR(64) DEFAULT NULL COMMENT '操作人',
  `change_summary` VARCHAR(512) DEFAULT NULL COMMENT '变更摘要',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_profile_created` (`profile_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='策略变更日志';

CREATE TABLE IF NOT EXISTS `t_webhook_subscription` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` VARCHAR(128) NOT NULL COMMENT '订阅名称',
  `callback_url` VARCHAR(512) NOT NULL COMMENT '回调地址',
  `secret_token` VARCHAR(128) DEFAULT NULL COMMENT '签名密钥',
  `event_types` VARCHAR(512) NOT NULL COMMENT '事件类型，逗号分隔',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `failure_count` INT NOT NULL DEFAULT 0 COMMENT '连续失败次数',
  `last_delivery_at` DATETIME DEFAULT NULL COMMENT '最后投递时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Webhook 订阅';

CREATE TABLE IF NOT EXISTS `t_external_api_key` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `key_name` VARCHAR(128) NOT NULL COMMENT '密钥名称',
  `api_key` VARCHAR(64) NOT NULL COMMENT 'API Key',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  `rate_limit_per_minute` INT NOT NULL DEFAULT 120 COMMENT '每分钟限流',
  `total_calls` BIGINT NOT NULL DEFAULT 0 COMMENT '累计调用次数',
  `last_used_at` DATETIME DEFAULT NULL COMMENT '最后使用时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_key` (`api_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='外部系统 API Key';

INSERT INTO `t_dispatch_strategy_profile` (
  `id`, `profile_name`, `profile_type`, `active_flag`, `gray_percent`,
  `weight_distance`, `weight_soc_margin`, `weight_plugged_standby_bonus`,
  `min_assignable_soc`, `full_soc`, `remark`, `deleted`
) VALUES (
  1, '默认生产策略', 'PRODUCTION', 1, 0,
  1.0, 0.15, 80.0, 30, 100, '系统默认派车评分权重', 0
) ON DUPLICATE KEY UPDATE `active_flag` = VALUES(`active_flag`);

INSERT INTO `t_dispatch_strategy_profile` (
  `id`, `profile_name`, `profile_type`, `active_flag`, `gray_percent`,
  `weight_distance`, `weight_soc_margin`, `weight_plugged_standby_bonus`,
  `min_assignable_soc`, `full_soc`, `remark`, `deleted`
) VALUES (
  2, '实验策略-A', 'EXPERIMENT', 0, 20,
  0.8, 0.20, 100.0, 35, 100, '灰度实验：更重视电量与插枪待命', 0
) ON DUPLICATE KEY UPDATE `profile_name` = VALUES(`profile_name`);
