USE `fsd_core`;

-- ALG-FC：站点补能需求预测结果表。
-- 由 scripts/ml/energy_demand_forecast.py 训练（梯度提升分位数回归 P50/P90 + 滑窗 P95 到站压力）后导入。
-- 设计原则：预测只读不写核心业务表；派单/补能策略在预测缺失时回退纯阈值逻辑。
CREATE TABLE IF NOT EXISTS `t_energy_forecast` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '园区ID',
  `station_id` BIGINT NOT NULL COMMENT '站点ID',
  `station_code` VARCHAR(64) DEFAULT NULL COMMENT '站点编码（便于人工核对）',
  `forecast_date` DATE NOT NULL COMMENT '预测日期',
  `hour_of_day` INT NOT NULL COMMENT '预测小时（0-23）',
  `demand_p50` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '补能需求 P50（次/小时）',
  `demand_p90` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '补能需求 P90 上界（次/小时）',
  `pressure_p95` DECIMAL(10,4) NOT NULL DEFAULT 0 COMMENT '滑动窗口 P95 到站压力峰值',
  `sample_count` INT NOT NULL DEFAULT 0 COMMENT '训练样本数（可追溯性）',
  `model_version` VARCHAR(64) NOT NULL COMMENT '模型版本/轮次标识',
  `generated_at` DATETIME NOT NULL COMMENT '预测生成时间（超期后视为失效）',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_park_station_slot` (`park_id`, `station_id`, `forecast_date`, `hour_of_day`, `model_version`),
  KEY `idx_park_slot` (`park_id`, `forecast_date`, `hour_of_day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站点补能需求预测结果表';
