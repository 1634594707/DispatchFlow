-- Harden dispatch task and vehicle indexes for MVP main flow.

USE `fsd_core`;

SET @dispatch_order_unique_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_dispatch_task'
      AND index_name = 'uk_order_id'
);

SET @sql = IF(
    @dispatch_order_unique_exists = 0,
    'ALTER TABLE `t_dispatch_task` ADD UNIQUE KEY `uk_order_id` (`order_id`)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @dispatch_vehicle_status_index_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_vehicle'
      AND index_name = 'idx_dispatch_status_online_status'
);

SET @sql = IF(
    @dispatch_vehicle_status_index_exists = 0,
    'CREATE INDEX `idx_dispatch_status_online_status` ON `t_vehicle` (`dispatch_status`, `online_status`)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @dispatch_exception_status_index_exists = (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 't_dispatch_exception_record'
      AND index_name = 'idx_exception_status_occur_time'
);

SET @sql = IF(
    @dispatch_exception_status_index_exists = 0,
    'CREATE INDEX `idx_exception_status_occur_time` ON `t_dispatch_exception_record` (`exception_status`, `occur_time`)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
