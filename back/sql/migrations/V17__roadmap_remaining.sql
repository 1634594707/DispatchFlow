-- Phase 12–14 remaining roadmap items

USE `fsd_core`;

ALTER TABLE `t_dispatch_strategy_profile`
  ADD COLUMN `energy_recovery_mode` VARCHAR(32) NOT NULL DEFAULT 'CHARGE'
    COMMENT 'CHARGE/SWAP/AUTO' AFTER `full_soc`;

ALTER TABLE `t_dispatch_task`
  ADD COLUMN `peak_mode_at_finish` VARCHAR(32) DEFAULT NULL
    COMMENT 'NORMAL/PEAK at task completion' AFTER `finish_time`;

ALTER TABLE `t_dispatch_exception_record`
  ADD COLUMN `escalated_at` DATETIME DEFAULT NULL COMMENT 'OPEN timeout escalated to ADMIN' AFTER `resolved_time`;

ALTER TABLE `t_admin_user`
  ADD COLUMN `totp_secret` VARCHAR(128) DEFAULT NULL AFTER `password_hash`,
  ADD COLUMN `totp_enabled` TINYINT NOT NULL DEFAULT 0 AFTER `totp_secret`;

CREATE TABLE IF NOT EXISTS `t_report_schedule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `park_id` BIGINT DEFAULT NULL COMMENT 'NULL=全园区',
  `cron_expression` VARCHAR(64) NOT NULL COMMENT 'Spring cron',
  `recipients` VARCHAR(512) NOT NULL COMMENT '逗号分隔邮箱',
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `last_sent_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时报表邮件';

CREATE TABLE IF NOT EXISTS `t_field_ops_ticket` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `exception_id` BIGINT NOT NULL,
  `assignee_user_id` BIGINT NOT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/IN_PROGRESS/DONE',
  `notes` VARCHAR(512) DEFAULT NULL,
  `created_by` VARCHAR(64) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_assignee_status` (`assignee_user_id`, `status`),
  KEY `idx_exception_id` (`exception_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='现场运维工单';

INSERT INTO `t_report_schedule` (`park_id`, `cron_expression`, `recipients`, `enabled`)
VALUES (NULL, '0 0 8 * * MON-FRI', 'admin@example.com', 0)
ON DUPLICATE KEY UPDATE `cron_expression` = VALUES(`cron_expression`);
