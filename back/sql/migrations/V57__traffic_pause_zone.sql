USE `fsd_core`;

-- §7.4 状态与消息边界：交通管制暂停区改「MySQL = 真相，Redis = 带 TTL 的运行态缓存」。
-- 原先只有 Redis 裸 set（无 TTL、重启即丢）+ JVM 内存兜底（跨副本/重启不一致），无落库来源。
-- 本表是唯一真相；Redis 只做缓存，缺失时直接从本表重建。
CREATE TABLE IF NOT EXISTS `t_traffic_pause_zone` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '园区ID（NULL 归一化为 0）',
  `min_x` DOUBLE NOT NULL COMMENT '矩形左下角 X',
  `min_y` DOUBLE NOT NULL COMMENT '矩形左下角 Y',
  `max_x` DOUBLE NOT NULL COMMENT '矩形右上角 X',
  `max_y` DOUBLE NOT NULL COMMENT '矩形右上角 Y',
  `label` VARCHAR(128) NOT NULL DEFAULT '管制区' COMMENT '管制区标签',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_park_id` (`park_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='交通管制暂停区（真相表，Redis 为 TTL 缓存）';
