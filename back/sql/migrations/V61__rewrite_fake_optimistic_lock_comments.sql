-- ============================================================
-- V61: 把 20 处"乐观锁版本号"假注释改成如实描述（路线图 §7.2 乐观锁条目的第一半）
--
-- 取证（2026-09-22，本地 fsd_core information_schema + 全仓 grep）：
--   * 20 张表的 `version` 列注释写着"乐观锁版本号"，但 main 里 `@Version` 0 处、
--     `OptimisticLockerInnerInterceptor` 0 处、`getVersion()` 0 处，只有 19 处插入时硬写
--     `setVersion(0)` ⇒ 它从不参与并发控制，注释是假的。本轮已把这些实体字段与写入点全部删除
--     （22 个字段 / 19 处调用，见 §13.48），Java 侧再没有一处读写它。
--   * 派单侧真正的并发保护是**每任务 Redis 锁**（`acquireTaskLock`，fail-closed）+
--     上报侧的"前置状态进 WHERE、影响 0 行即抛冲突"（§13.41）。
--     所以本条**不接** `@Version`：接上会变成第三套真相，且一旦有人漏带版本号就是静默丢更新。
--   * `t_dispatch_route` / `t_dispatch_strategy_profile` 的 `version` 没有乐观锁注释、
--     也不在本迁移范围内；`t_map_data_version` 的业务版本走 `version_code`/`version_label`，
--     与被删的 `version` 列无关。
--
-- 为什么本轮只改注释、不 DROP：`back/sql/seed/zjf_geo.sql` 的 481 处列清单里带 `version`
-- （seed 由 `scripts/dev/export-geo-seed.sh` 从活库反生成）。先删列会让这份 seed 在
-- 新库上直接报 Unknown column —— 那正是"改完本地全绿、下一次初始化才炸"的形态。
-- 正解是「先改注释止住误读 → 重生成 seed → 再补一条 DROP 迁移」，第二步归入 §13.48 的待办。
--
-- 用 `MODIFY COLUMN` 而不是 `ALTER COLUMN ... COMMENT`：后者 MySQL 8 不支持（实测 1064），
-- 列定义按 information_schema 实测逐字重述（这 20 列一律 `int NOT NULL DEFAULT 0`），
-- 所以本迁移只改注释，不动类型/可空性/默认值；重复执行结果一致。
-- ============================================================

USE `fsd_core`;

SET NAMES utf8mb4;

ALTER TABLE `t_admin_user` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_building_block` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_charging_pile` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_charging_session` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_dispatch_pause_state` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_dispatch_task` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_energy_forecast` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_map_data_version` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_order` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_park` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_park_geofence` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_parking_slot` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_road_node` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_road_segment` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_route_audit` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_station` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_station_service_position` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_station_service_position_reservation` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_vehicle` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
ALTER TABLE `t_vehicle_maintenance` MODIFY COLUMN `version` int NOT NULL DEFAULT 0 COMMENT '遗留列：乐观锁从未接线（@Version/OptimisticLockerInnerInterceptor 全仓 0 处），代码已不再读写；物理删除见路线图 §7.2 与执行记录 §13.48';
