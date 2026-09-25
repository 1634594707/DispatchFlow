-- ============================================================
-- V62: 物理删除 22 张表上那个"乐观锁版本号"遗留列（路线图 §7.2 乐观锁条目的第二半）
--
-- 取证（2026-09-22 复核，命令与结果都记在《已完成工作记录》§13.48 / §13.62）：
--   * 全仓 `@Version` 0 处、`OptimisticLockerInnerInterceptor` 0 处、`getVersion()` 0 处、
--     `setVersion(` 0 处 —— V61 那一轮已把实体字段与写入点清空，本条把列本身删掉。
--   * 本轮又抓到一处漏网：`DispatchRouteEntity` 仍声明 `private Integer version;`
--     （MyBatis-Plus 按实体字段生成 INSERT 列表 ⇒ 不删字段就删列，派单落路线会直接
--     Unknown column）。已随本迁移一并删除。
--   * 派单真正的并发保护是**每任务 Redis 锁**（fail-closed）+ 上报侧"前置状态进 WHERE、
--     影响 0 行即抛冲突"（§13.41）。**不接** `@Version`：那会变成第三套真相，
--     且漏带版本号时从"报错"退化成"静默丢更新"。
--
-- 为什么现在才敢删（V61 的注释里挂了这条前置）：`back/sql/seed/zjf_geo.sql` 是由
-- `scripts/dev/export-geo-seed.sh` 从活库反生成的，列清单里有 481 处 `version`。
-- 初始化顺序是 V01-V20 裸跑 -> Flyway V21..N -> 应用 seed（见 `back/sql/init/00-run-migrations.sh`），
-- 所以 V62 之后的 seed 必须不再引用该列，否则"本地全绿、新库初始化才炸"。
-- 本迁移提交时 seed 已同步重生成，并用 `scripts/dev/verify-geo-init-paths.sh` 两条路径各建一遍比对。
--
-- 逐表一个守卫块：MySQL 没有 DROP COLUMN IF EXISTS。列不存在时执行 `SELECT 1`，
-- 因此本文件可重复执行（本地库此前被手工跑过等价 DDL 的情况也吃得下）。
-- `flyway_schema_history.version` 是 Flyway 自己的列，不在名单里，也不许动。
-- ============================================================

USE `fsd_core`;

-- t_admin_user
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_admin_user` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_admin_user' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_building_block
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_building_block` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_building_block' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_charging_pile
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_charging_pile` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_charging_pile' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_charging_session
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_charging_session` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_charging_session' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_dispatch_pause_state
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_dispatch_pause_state` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_dispatch_pause_state' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_dispatch_route
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_dispatch_route` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_dispatch_route' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_dispatch_strategy_profile
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_dispatch_strategy_profile` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_dispatch_strategy_profile' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_dispatch_task
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_dispatch_task` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_dispatch_task' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_energy_forecast
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_energy_forecast` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_energy_forecast' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_map_data_version
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_map_data_version` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_map_data_version' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_order
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_order` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_order' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_park
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_park` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_park' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_park_geofence
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_park_geofence` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_park_geofence' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_parking_slot
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_parking_slot` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_parking_slot' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_road_node
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_road_node` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_road_node' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_road_segment
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_road_segment` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_road_segment' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_route_audit
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_route_audit` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_route_audit' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_station
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_station` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_station' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_station_service_position
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_station_service_position` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_station_service_position' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_station_service_position_reservation
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_station_service_position_reservation` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_station_service_position_reservation' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_vehicle
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_vehicle` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_vehicle' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_vehicle_maintenance
SET @sql := (SELECT IF(COUNT(*) > 0,
    'ALTER TABLE `t_vehicle_maintenance` DROP COLUMN `version`',
    'SELECT 1')
  FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND table_name = 't_vehicle_maintenance' AND column_name = 'version');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 收尾自检：删完之后这条应当返回 0 行（放在迁移里，失败会让人看见而不是默默过）
SET @left := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = 'fsd_core' AND column_name = 'version'
    AND table_name <> 'flyway_schema_history');
SELECT CONCAT('legacy version columns left: ', @left) AS v62_result;
