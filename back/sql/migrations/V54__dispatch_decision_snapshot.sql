-- V54: 派单决策快照表（路线图 §7.3）。
--
-- 目的：让任一历史任务能被事后回答「当时为什么选这台车、与次优差多少分」。
-- 修复的现状：DispatchVehicleAssignServiceImpl 组装的 explanation 只进 response VO
-- （DispatchTaskServiceImpl -> DispatchTaskAssignResponse），落库即丢，决策不可证明。
--
-- 只建表，不写任何业务数据；候选清单与分项分数以 JSON 存 candidates_json，
-- 便于与 §2.1 的 DecisionPolicy 输出结构一一对应（也是 Jev state 契约的落点）。

CREATE TABLE IF NOT EXISTS `t_dispatch_decision_snapshot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '园区ID',
  `task_id` BIGINT DEFAULT NULL COMMENT '调度任务ID（派单时可能尚未生成）',
  `order_id` BIGINT DEFAULT NULL COMMENT '订单ID',
  `order_no` VARCHAR(64) DEFAULT NULL COMMENT '订单号（便于人工核对）',
  `policy_id` VARCHAR(64) NOT NULL COMMENT '命中的决策策略标识，如 RULE',
  `policy_version` VARCHAR(64) NOT NULL COMMENT '策略版本，用于影子对照与回滚定位',
  `profile_id` BIGINT DEFAULT NULL COMMENT '命中的策略档案ID，NULL 表示走 YAML 默认',
  `profile_type` VARCHAR(16) DEFAULT NULL COMMENT '档案类型 PRODUCTION/EXPERIMENT',
  `gray_bucket` INT DEFAULT NULL COMMENT '稳定分桶号 0-99',
  `experiment_side` TINYINT DEFAULT NULL COMMENT '0=生产侧 1=实验侧',
  `candidate_total` INT NOT NULL DEFAULT 0 COMMENT '进入打分的候选车数',
  `candidate_evaluated` INT NOT NULL DEFAULT 0 COMMENT '完成打分并有分数的候选车数',
  `candidates_json` TEXT DEFAULT NULL COMMENT '候选清单与分项分数（top-N，含车辆/距离/SOC/加分/总分）',
  `winner_vehicle_id` BIGINT DEFAULT NULL COMMENT '最终选中车辆ID',
  `winner_vehicle_code` VARCHAR(64) DEFAULT NULL COMMENT '最终选中车辆编码',
  `winner_score` DECIMAL(14,4) DEFAULT NULL COMMENT '选中车总分',
  `runner_up_score` DECIMAL(14,4) DEFAULT NULL COMMENT '次优车总分',
  `score_gap` DECIMAL(14,4) DEFAULT NULL COMMENT '选中与次优的分差（含并列=0）',
  `tie_count` INT DEFAULT NULL COMMENT '与本单最优同分的候选数',
  `match_algorithm` VARCHAR(32) DEFAULT NULL COMMENT '撮合算法标识 GREEDY/HUNGARIAN',
  `road_graph_version` VARCHAR(64) DEFAULT NULL COMMENT '路网图版本（缓存键/节点路段计数指纹）',
  `fail_reason` VARCHAR(32) DEFAULT NULL COMMENT '派单失败原因，成功为 NULL',
  `duration_micros` BIGINT DEFAULT NULL COMMENT '选车决策耗时（微秒）',
  `confidence` DECIMAL(6,4) DEFAULT NULL COMMENT '决策置信度，学习/Jev 策略用',
  `generated_at` DATETIME NOT NULL COMMENT '决策发生时间（业务时间，非写入时间）',
  `remark` VARCHAR(512) DEFAULT NULL COMMENT '决策解释原文',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_snapshot_task` (`task_id`),
  KEY `idx_snapshot_order` (`order_id`),
  KEY `idx_snapshot_park_time` (`park_id`, `generated_at`),
  KEY `idx_snapshot_policy_time` (`policy_id`, `generated_at`),
  KEY `idx_snapshot_fail_time` (`fail_reason`, `generated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='派单决策快照表';
