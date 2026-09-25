-- ============================================================
-- V63: 把三处还在宣传 `SCHEMATIC` 的**列注释**改成如实描述（路线图 §7.6 剩下那半条 ①）
--
-- 取证（2026-09-23，本地 `fsd_core` 的 information_schema + 全仓 grep + 派单代码逐行读）：
--   * `fsd-*/src/main` 已无 `"SCHEMATIC"` **字面量**（§13.70 ③），Java 侧那三处 Javadoc
--     也已经写成"SCHEMATIC 已随 §7.6 停用"（`StationEntity.java:72`、`ParkStationResponse.java:43`、
--     `VehicleEntity.java:56`）—— **唯一还在把 SCHEMATIC 当现行值域的，是库里的列注释**，
--     即 V36 建列、V43 建表时写进去的那三行。这三处 Javadoc 自己留了尾巴说"列注释仍留在 V37"，
--     本迁移就是去结这条尾巴。
--   * 现役数据（**含软删的全量**，只查 deleted=0 会漏）：
--       `t_vehicle.delivery_zone`  BOTH 22 / SCHEMATIC 4   —— 未删的 20 台**全是 BOTH**
--       `t_station.delivery_zone`  GEO_DELIVERY 27 / SCHEMATIC 15 —— 未删的 27 个全是 GEO_DELIVERY
--     ⇒ SCHEMATIC 只剩软删历史行。**本迁移不改数据**：改写历史行属数据动作（等 §10.2），
--       且 §7.6 对 `CAMPUS-B` 已定过"软删即处置完毕"的口径，同一先例。
--   * 谁在读这列（这是注释必须写准的原因，不是文风问题）：
--       `DispatchVehicleAssignServiceImpl.java:570-582 matchesDeliveryZone()` —— 车辆列若为
--         null / 空 / **BOTH** ⇒ 放行所有订单；否则要与订单列**逐字 equals** 才放行
--         （订单列空值在 `:191-192` 被归一成 `GEO_DELIVERY`）。
--         ⇒ 一台车的这列若留着 `SCHEMATIC`，它去和订单的 `GEO_DELIVERY` 比、**永远不相等**，
--           这台车就在派单里**静默不可选**，且不会有任何报错。所以"SCHEMATIC 只是个无害的历史标签"
--           是会让下次排工踩坑的读法，注释里必须写明"别再往这列写"。
--       `ParkStationServiceImpl.java:255` 只把站点列回显进 DTO ⇒ **站点列不参与派单筛选**。
--   * `t_route_audit.route_mode` 实测 **0 行**；唯一写入方是 `RouteAuditService.java:64`，
--     值来自 `RoadRouteValidateAdminService.java:268-271`（把请求值大写化），现行只有
--     `REAL_ROAD` / `STRAIGHT_LINE` 两条实到路径。
--   * `t_order.delivery_zone` 注释只写了"配送区域"、没宣传 SCHEMATIC ⇒ **不在本迁移范围内**，
--     不顺手扩。
--
-- 用 `MODIFY COLUMN` 而不是 `ALTER COLUMN ... COMMENT`：后者 MySQL 8 不支持（实测 1064，V61 已记）。
-- 列定义按 information_schema 实测逐字重述：三列都是 `varchar(32)`；`t_route_audit.route_mode`
-- 是 `NOT NULL DEFAULT 'REAL_ROAD'`，另两张业务表可空、默认分别 `'BOTH'` / `'GENERAL'`
-- ⇒ 本迁移**只改注释**，不动类型 / 可空性 / 默认值，重复执行结果一致。
-- 不含 DML，地理内容与 seed 无关 ⇒ 不需要重生成 `back/sql/seed/zjf_geo.sql`。
-- ============================================================

USE `fsd_core`;

SET NAMES utf8mb4;

ALTER TABLE `t_vehicle` MODIFY COLUMN `delivery_zone` varchar(32) DEFAULT 'BOTH' COMMENT '配送区域: BOTH(默认,放行所有订单)/GEO_DELIVERY。SCHEMATIC 已随园区示意调度停用(§7.6/§13.7)，不要再写入本列: 该值与订单的 GEO_DELIVERY 永不相等, 会使该车在 matchesDeliveryZone 中静默不可派单; 库内仅软删历史行仍带该值';

ALTER TABLE `t_station` MODIFY COLUMN `delivery_zone` varchar(32) DEFAULT 'GENERAL' COMMENT '配送区域: GEO_DELIVERY(现役全部)/GENERAL(默认)。SCHEMATIC 已停用, 仅软删历史行残留; 本列只回显进接口, 派单筛选不读它';

ALTER TABLE `t_route_audit` MODIFY COLUMN `route_mode` varchar(32) NOT NULL DEFAULT 'REAL_ROAD' COMMENT '路线模式: REAL_ROAD/STRAIGHT_LINE。SCHEMATIC 已停用(Java 侧字面量与枚举均已清, §13.70 ③); 本表实测 0 行';
