-- V53: webhook 订阅增加"最近一次失败时间"，用于熔断器的冷却窗口（半开探测）。
--
-- 修复的缺陷（路线图 §7.2）：failure_count 只增不减，唯一归零点在投递成功分支，
-- 而该分支在熔断打开后永不可达 —— 连续 5 次失败后订阅永久静默，
-- 只有管理端编辑订阅（IntegrationAdminServiceImpl）才会清零。
-- 半开需要知道"上次失败发生在何时"，因此补这一列；DDL-only，不含任何数据内容。

SET @db := DATABASE();

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_webhook_subscription' AND COLUMN_NAME = 'last_failure_at') = 0,
  'ALTER TABLE `t_webhook_subscription` ADD COLUMN `last_failure_at` DATETIME DEFAULT NULL COMMENT ''最近一次投递失败时间，熔断冷却窗口的起点'' AFTER `failure_count`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
