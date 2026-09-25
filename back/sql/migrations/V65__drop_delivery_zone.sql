-- V65: 删掉 delivery_zone —— "园区内部"这套分区不再要了
--
-- 判据（2026-09-23 实测，本地与生产同口径）：
--   站点 27 行全部 GEO_DELIVERY、车辆 20 行全部 BOTH、订单 delivery_zone 全部 NULL
--   ⇒ 派单过滤链里那条 DELIVERY_ZONE 判据（DispatchVehicleAssignServiceImpl.matchesDeliveryZone）
--     在真实数据上恒真：它只在车辆 zone 为 null/空/BOTH 时返回 true，而所有车辆都是 BOTH。
--   所以这不是"删一条还在生效的规则"，是"删一条永远放行、但会误导下一个改代码的人"的规则。
--
-- ⚠ 删列必须同时收尾四处手写列清单，否则 seed 导入直接失败（§13.86 那次删 version 列踩过同一条）：
--   back/sql/seed/zjf_geo.sql（27 条 t_station INSERT）、scripts/geo/draw_zone.py、
--   scripts/dev/reset-demo-dispatchable.sh、scripts/dev/verify-geo-init-paths.sh。
--   seed 由 scripts/dev/export-geo-seed.sh 从库重生成，不要手改。

SET @db := DATABASE();

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'delivery_zone') = 1,
  'ALTER TABLE `t_order` DROP COLUMN `delivery_zone`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_station' AND COLUMN_NAME = 'delivery_zone') = 1,
  'ALTER TABLE `t_station` DROP COLUMN `delivery_zone`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 't_vehicle' AND COLUMN_NAME = 'delivery_zone') = 1,
  'ALTER TABLE `t_vehicle` DROP COLUMN `delivery_zone`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
