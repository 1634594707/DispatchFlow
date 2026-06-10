USE `fsd_core`;

-- V34 already added agg_count on some environments; keep this migration idempotent.
SET @col_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_dispatch_exception_record'
    AND COLUMN_NAME = 'agg_count'
);

SET @sql = IF(
  @col_exists = 0,
  'ALTER TABLE `t_dispatch_exception_record` ADD COLUMN `agg_count` INT NOT NULL DEFAULT 1 COMMENT ''Aggregated duplicate exception count'' AFTER `severity`',
  'SELECT 1'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
