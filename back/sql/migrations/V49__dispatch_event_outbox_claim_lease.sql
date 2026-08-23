-- Add a database-backed claim lease so only one application instance publishes
-- a retryable outbox event at a time. Existing rows remain immediately retryable.

SET @db := DATABASE();

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_dispatch_event_outbox' AND COLUMN_NAME = 'claim_token') = 0,
  'ALTER TABLE `t_dispatch_event_outbox` ADD COLUMN `claim_token` VARCHAR(64) DEFAULT NULL COMMENT ''Current publisher lease token'' AFTER `next_retry_time`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_dispatch_event_outbox' AND COLUMN_NAME = 'claimed_at') = 0,
  'ALTER TABLE `t_dispatch_event_outbox` ADD COLUMN `claimed_at` DATETIME DEFAULT NULL COMMENT ''Lease acquisition time'' AFTER `claim_token`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_dispatch_event_outbox' AND COLUMN_NAME = 'lease_until') = 0,
  'ALTER TABLE `t_dispatch_event_outbox` ADD COLUMN `lease_until` DATETIME DEFAULT NULL COMMENT ''Lease expiry time'' AFTER `claimed_at`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_dispatch_event_outbox' AND INDEX_NAME = 'idx_outbox_processing_lease') = 0,
  'ALTER TABLE `t_dispatch_event_outbox` ADD KEY `idx_outbox_processing_lease` (`status`, `lease_until`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
