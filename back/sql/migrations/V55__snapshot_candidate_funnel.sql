-- V55: 决策快照补上候选漏斗各层的通过数（§7.3「派单失败原因分布」）。
--
-- 为什么需要：单看 fail_reason 会把「压根没车」「遥测全部过期」「SOC 不够」
-- 「全链路 SOC 不够」「路网不可达」压成同一个结论，而这五种情况的处置完全不同。
-- 这些数在选车流程里本来就算出来了（DecisionTrace），只是原先没落库。

SET @db := DATABASE();

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_dispatch_decision_snapshot' AND COLUMN_NAME = 'fresh_telemetry_count') = 0,
  'ALTER TABLE `t_dispatch_decision_snapshot`
       ADD COLUMN `fresh_telemetry_count` INT DEFAULT NULL COMMENT ''遥测未过期的候选车数'' AFTER `candidate_total`,
       ADD COLUMN `soc_eligible_count` INT DEFAULT NULL COMMENT ''SOC 与硬条件通过的候选车数'' AFTER `fresh_telemetry_count`,
       ADD COLUMN `soc_chain_eligible_count` INT DEFAULT NULL COMMENT ''全链路 SOC（取-送-回桩）通过的候选车数'' AFTER `soc_eligible_count`,
       ADD COLUMN `reachable_count` INT DEFAULT NULL COMMENT ''路网可达的候选车数'' AFTER `soc_chain_eligible_count`',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
