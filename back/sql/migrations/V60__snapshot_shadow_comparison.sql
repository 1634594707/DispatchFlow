-- ============================================================
-- V60: 决策快照补影子对照列（路线图 §2.2 SHADOW 阶段的落点）
--
-- 现状：t_dispatch_decision_snapshot 只记"选了谁、在位策略自己给的分数"，无法回答
-- "挑战者策略会不会选同一台车、按在位标尺差多少分"⇒ 一致率与 regret 只能靠日志捞。
-- 影子结果写在同一行而不是另建表：一致率必须能与在位侧的分桶、档案、路网版本逐单对齐。
--
-- 全部语句写成可重复执行（本地库这几轮被手工执行过等价 DDL，见路线图 M0 的分叉说明）：
-- MySQL 8 没有 ADD COLUMN/INDEX IF NOT EXISTS，所以按 information_schema 先查再 PREPARE。
-- ============================================================

USE `fsd_core`;

-- shadow_policy_id
SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_dispatch_decision_snapshot` ADD COLUMN `shadow_policy_id` VARCHAR(64) DEFAULT NULL COMMENT ''影子策略标识，NULL=本单未跑影子'' AFTER `policy_version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_dispatch_decision_snapshot'
    AND column_name = 'shadow_policy_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- shadow_policy_version
SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_dispatch_decision_snapshot` ADD COLUMN `shadow_policy_version` VARCHAR(64) DEFAULT NULL COMMENT ''影子策略版本'' AFTER `shadow_policy_id`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_dispatch_decision_snapshot'
    AND column_name = 'shadow_policy_version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- shadow_winner_code
SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_dispatch_decision_snapshot` ADD COLUMN `shadow_winner_code` VARCHAR(64) DEFAULT NULL COMMENT ''影子策略的 top-1 车辆编码'' AFTER `shadow_policy_version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_dispatch_decision_snapshot'
    AND column_name = 'shadow_winner_code');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- shadow_agreed
SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_dispatch_decision_snapshot` ADD COLUMN `shadow_agreed` TINYINT DEFAULT NULL COMMENT ''top-1 是否与在位策略一致：1 是 0 否 NULL 未跑'' AFTER `shadow_winner_code`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_dispatch_decision_snapshot'
    AND column_name = 'shadow_agreed');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- shadow_regret
SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_dispatch_decision_snapshot` ADD COLUMN `shadow_regret` DECIMAL(14,4) DEFAULT NULL COMMENT ''影子选择按在位策略标尺的分差（≥0，越小越可晋级）'' AFTER `shadow_agreed`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_dispatch_decision_snapshot'
    AND column_name = 'shadow_regret');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 一致率是按策略聚合着看的（"能不能进 GRAY"的判据），故建 (shadow_policy_id, generated_at)
SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `t_dispatch_decision_snapshot` ADD INDEX `idx_snapshot_shadow_time` (`shadow_policy_id`, `generated_at`)',
    'SELECT 1')
  FROM information_schema.statistics
  WHERE table_schema = 'fsd_core' AND table_name = 't_dispatch_decision_snapshot'
    AND index_name = 'idx_snapshot_shadow_time');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
