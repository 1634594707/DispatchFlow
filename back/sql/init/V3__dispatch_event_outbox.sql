-- Dispatch event outbox for reliable publish and retry.

USE `fsd_core`;

CREATE TABLE IF NOT EXISTS `t_dispatch_event_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key ID',
  `event_id` VARCHAR(64) NOT NULL COMMENT 'Event unique ID',
  `event_type` VARCHAR(64) NOT NULL COMMENT 'Event type',
  `business_key` VARCHAR(64) NOT NULL COMMENT 'Business key',
  `payload` JSON NOT NULL COMMENT 'Serialized payload',
  `status` VARCHAR(32) NOT NULL COMMENT 'PENDING/PUBLISHED/FAILED',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT 'Retry count',
  `last_error` VARCHAR(255) DEFAULT NULL COMMENT 'Last publish error',
  `next_retry_time` DATETIME DEFAULT NULL COMMENT 'Next retry time',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Created at',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Updated at',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  KEY `idx_status_next_retry_time` (`status`, `next_retry_time`),
  KEY `idx_event_type_created_at` (`event_type`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='Dispatch event outbox';
