-- V52: 幂等保护 webhook 渠道类型与告警聚合计数两列。
--
-- 为什么另开这个迁移：V33/V34 已经把这两列建好并被 Flyway 记录（checksum 已固化）。
-- 早期初始化脚本曾把 V01-V51 全量当裸 SQL 跑过一遍，在那类遗留库里 V33/V34 会因
-- "Duplicate column" 失败并在 history 里留下 success=0 的行；`flyway repair` 清掉失败行后，
-- 需要有一条幂等的迁移把列补齐。V33/V34 本体不可修改（见 CONTRIBUTING 迁移纪律）。
--
-- 新建库与已正常应用的库上，本迁移是 no-op。

SET @db := DATABASE();

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_webhook_subscription') > 0
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_webhook_subscription' AND COLUMN_NAME = 'channel_type') = 0,
  'ALTER TABLE `t_webhook_subscription` ADD COLUMN `channel_type` VARCHAR(32) NOT NULL DEFAULT ''GENERIC'' COMMENT ''渠道类型: GENERIC/WECHAT_BOT/DINGTALK_BOT/FEISHU_BOT'' AFTER `callback_url`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.TABLES
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_dispatch_exception_record') > 0
  AND (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_dispatch_exception_record' AND COLUMN_NAME = 'agg_count') = 0,
  'ALTER TABLE `t_dispatch_exception_record` ADD COLUMN `agg_count` INT NOT NULL DEFAULT 1 COMMENT ''聚合计数: 同一车辆同类异常聚合次数'' AFTER `resolve_action`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
