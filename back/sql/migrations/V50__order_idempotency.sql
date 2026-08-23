-- V50: 移动端订单创建请求幂等契约（路线图 2.2 / 3.3）
-- 同一幂等键只允许成功创建一次订单；重复提交返回原订单结果。
CREATE TABLE IF NOT EXISTS `t_order_idempotency` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `idempotency_key` VARCHAR(128) NOT NULL COMMENT '客户端幂等键',
  `request_hash` CHAR(64) NOT NULL COMMENT '请求语义字段 SHA-256 指纹',
  `park_id` BIGINT DEFAULT NULL COMMENT '园区ID',
  `status` VARCHAR(16) NOT NULL DEFAULT 'PROCESSING' COMMENT '状态：PROCESSING/COMPLETED',
  `order_id` BIGINT DEFAULT NULL COMMENT '订单ID',
  `task_id` BIGINT DEFAULT NULL COMMENT '调度任务ID',
  `response_snapshot` TEXT DEFAULT NULL COMMENT '首次成功响应 JSON 快照',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idempotency_key` (`idempotency_key`),
  KEY `idx_park_id_created_at` (`park_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单创建幂等记录表';
