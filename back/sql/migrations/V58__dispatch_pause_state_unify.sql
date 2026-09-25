-- ============================================================
-- V58: 暂停派单开关合一（路线图 §7.4）
--
-- 现状：真正生效的开关只有 Redis 的 fsd:dispatch:pause:global / :park:{id}（无 TTL、Redis 一flush 就
-- 静默恢复派单），而 V43 建的真相表 t_dispatch_pause_state 全文零读者 —— 一个开关两处存、其中一处是死的。
--
-- 本迁移把该表扶成真相：park_id 用 0 这一行表示"全园暂停"（NOT NULL + uk_park 都不用动就能表达全局档），
-- 并补一个初值行，让审计字段（reason/by/at）从一开始就有锚点。Redis 侧降级为可重建缓存。
-- ============================================================

USE `fsd_core`;

ALTER TABLE `t_dispatch_pause_state`
    MODIFY COLUMN `park_id` BIGINT NOT NULL COMMENT '园区ID；0 = 全局暂停档（所有园区一并暂停）';

INSERT IGNORE INTO `t_dispatch_pause_state` (`park_id`, `is_paused`, `pause_reason`, `paused_by`)
    VALUES (0, 0, NULL, 'system');
