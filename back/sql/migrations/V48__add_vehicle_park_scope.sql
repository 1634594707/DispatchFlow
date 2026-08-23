SET @db := DATABASE();

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_vehicle' AND COLUMN_NAME = 'park_id') = 0,
  'ALTER TABLE `t_vehicle` ADD COLUMN `park_id` BIGINT DEFAULT NULL COMMENT ''所属园区ID'' AFTER `id`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_vehicle' AND INDEX_NAME = 'idx_vehicle_park_id') = 0,
  'ALTER TABLE `t_vehicle` ADD KEY `idx_vehicle_park_id` (`park_id`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `t_vehicle` v
LEFT JOIN `t_order` o ON o.`id` = v.`current_order_id` AND o.`deleted` = 0
SET v.`park_id` = COALESCE(
    o.`park_id`,
    (SELECT p.`id` FROM `t_park` p
      WHERE p.`deleted` = 0 AND p.`status` = 'ACTIVE'
      ORDER BY p.`default_flag` DESC, p.`id` ASC LIMIT 1)
)
WHERE v.`park_id` IS NULL AND v.`deleted` = 0;
