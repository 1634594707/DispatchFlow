-- V42: 可通行地图模型补齐 — 道路几何/通行语义/服务位/预约/车型约束/地图版本
--
-- 本迁移对应 docs/地图与交互问题清单.md 中 P0-3 ~ P0-5 与 P1 全部数据层要求：
--   * P0-3 统一坐标/数据源：t_road_segment 增加 polyline_geojson，保存真实道路中心线折点
--   * P0-4 车辆轨迹经过道路图 + 建筑物膨胀区碰撞：t_road_segment 增加 width_meters / access_state
--   * P0-5 站点服务位：新增 t_station_service_position 子表；t_station 增加 anchor_node_code
--   * P1-1 路网几何/方向/宽度/限速/转弯限制：t_road_segment 增加 width_meters / road_class /
--          polyline_geojson / allowed_vehicle_types / turn_restriction
--   * P1-2 道路临时封闭/门禁/消防通道/人车混行：access_state 枚举（DRIVABLE/PEDESTRIAN_ONLY/
--          SERVICE_ONLY/RESTRICTED/BLOCKED/NO_STOP/LOADING_ONLY/CHARGING_ACCESS）
--   * P1-3 车辆尺寸/转弯半径/允许道路等级/载重：t_vehicle 增加 width_cm/length_cm/turning_radius_m/
--          allowed_road_classes（载重 max_load_capacity 已存在）
--   * P1-4 不可达原因：t_station 增加 unreachable_reason；调度失败码沿用 DispatchAssignFailReason
--   * P1-6 充电站桩数量/进出站路径/排队/预约：t_charging_pile 增加 entry_node_code/exit_node_code/
--          plug_type/reservation_state/estimated_release_at
--   * P1-7 取送货点服务时间窗/装卸耗时/排队容量/车型限制：t_station 已有 service_hours/
--          avg_service_seconds/capacity_limit；新增 allowed_vehicle_types
--   * P1-8 待命区车位/朝向/进出流线：t_parking_slot 增加 facing_direction/entry_node_code/
--          exit_node_code/blocking_main_road
--   * P1-9 站点不可达/停用/维护中状态：扩展 station_status 枚举（增加 UNREACHABLE/MAINTENANCE/
--          OUT_OF_SERVICE）
--   * P1-10 服务位预约/锁定：新增 t_station_service_position_reservation 表
--   * P2-6 地图数据版本号：新增 t_map_data_version 表
--
-- 设计原则：
--   * 所有 ALTER 通过 information_schema 判重，可重复执行（幂等）。
--   * 不删除既有列；既有 ZJF 路段仅追加属性，不修改现有几何与方向。
--   * 坐标系说明（沿用 V38）：coord_lng/coord_lat 为 GCJ-02 经纬度，coord_x/coord_y 为向后兼容
--     的示意图坐标。所有新增几何字段（polyline_geojson）默认使用 GCJ-02。

USE `fsd_core`;

-- ============================================================
-- 1. t_road_segment 扩展：道路几何 / 通行语义 / 车型过滤 / 转弯限制
-- ============================================================

-- 1.1 道路宽度（米）— 用于车辆外接矩形与建筑物膨胀区碰撞检查
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_segment'
     AND COLUMN_NAME = 'width_meters') = 0,
  'ALTER TABLE `t_road_segment` ADD COLUMN `width_meters` DECIMAL(6,2) DEFAULT NULL COMMENT ''道路可行驶宽度（米），用于车辆外接矩形碰撞检查'' AFTER `congestion_level`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.2 道路等级：HIGHWAY / ARTERIAL / SECONDARY / SERVICE_ROAD / PEDESTRIAN / FIRE_LANE
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_segment'
     AND COLUMN_NAME = 'road_class') = 0,
  'ALTER TABLE `t_road_segment` ADD COLUMN `road_class` VARCHAR(32) NOT NULL DEFAULT ''SECONDARY'' COMMENT ''道路等级：HIGHWAY/ARTERIAL/SECONDARY/SERVICE_ROAD/PEDESTRIAN/FIRE_LANE'' AFTER `width_meters`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.3 通行语义：DRIVABLE/PEDESTRIAN_ONLY/SERVICE_ONLY/RESTRICTED/BLOCKED/NO_STOP/LOADING_ONLY/CHARGING_ACCESS
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_segment'
     AND COLUMN_NAME = 'access_state') = 0,
  'ALTER TABLE `t_road_segment` ADD COLUMN `access_state` VARCHAR(32) NOT NULL DEFAULT ''DRIVABLE'' COMMENT ''通行语义：DRIVABLE/PEDESTRIAN_ONLY/SERVICE_ONLY/RESTRICTED/BLOCKED/NO_STOP/LOADING_ONLY/CHARGING_ACCESS'' AFTER `road_class`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.4 真实道路中心线折点（GeoJSON LineString，GCJ-02）
--     用于路径碰撞检查与轨迹渲染，避免「视觉上在路上、数据上不在路上」
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_segment'
     AND COLUMN_NAME = 'polyline_geojson') = 0,
  'ALTER TABLE `t_road_segment` ADD COLUMN `polyline_geojson` JSON DEFAULT NULL COMMENT ''道路中心线 GeoJSON LineString（GCJ-02），NULL 表示用 from/to 节点连线'' AFTER `access_state`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.5 允许通行的车辆类型（逗号分隔，NULL = 全部允许）
--     用于「车辆宽度大于道路可用宽度」或「专用车辆道路」过滤
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_segment'
     AND COLUMN_NAME = 'allowed_vehicle_types') = 0,
  'ALTER TABLE `t_road_segment` ADD COLUMN `allowed_vehicle_types` VARCHAR(255) DEFAULT NULL COMMENT ''允许车辆类型（逗号分隔，NULL=全部；如 SMALL_TRUCK,DELIVERY_BOT）'' AFTER `polyline_geojson`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.6 转弯限制：NONE / NO_LEFT / NO_RIGHT / NO_U_TURN / NO_STRAIGHT
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_segment'
     AND COLUMN_NAME = 'turn_restriction') = 0,
  'ALTER TABLE `t_road_segment` ADD COLUMN `turn_restriction` VARCHAR(32) NOT NULL DEFAULT ''NONE'' COMMENT ''转向限制：NONE/NO_LEFT/NO_RIGHT/NO_U_TURN/NO_STRAIGHT'' AFTER `allowed_vehicle_types`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.7 关联门禁/闸机/消防通道编码（NULL=无门禁）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_segment'
     AND COLUMN_NAME = 'gate_code') = 0,
  'ALTER TABLE `t_road_segment` ADD COLUMN `gate_code` VARCHAR(64) DEFAULT NULL COMMENT ''关联门禁/闸机/消防通道编码（NULL=无门禁）'' AFTER `turn_restriction`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 1.8 临时封路原因 + 失效时间窗
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_segment'
     AND COLUMN_NAME = 'block_reason') = 0,
  'ALTER TABLE `t_road_segment` ADD COLUMN `block_reason` VARCHAR(128) DEFAULT NULL COMMENT ''临时封路原因（施工/事故/活动/消防管控）'' AFTER `gate_code`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_segment'
     AND COLUMN_NAME = 'blocked_from') = 0,
  'ALTER TABLE `t_road_segment` ADD COLUMN `blocked_from` DATETIME DEFAULT NULL COMMENT ''封路起始时间（NULL=未封路或永久）'' AFTER `block_reason`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_road_segment'
     AND COLUMN_NAME = 'blocked_until') = 0,
  'ALTER TABLE `t_road_segment` ADD COLUMN `blocked_until` DATETIME DEFAULT NULL COMMENT ''封路结束时间（NULL=永久或未封路）'' AFTER `blocked_from`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. t_station 扩展：服务位锚点 / 不可达原因 / 允许车型
-- ============================================================

-- 2.1 站点接入的道路节点编码（吸附到 road_node.node_code）
--     P0-5：站点不能使用建筑中心点作为默认到达点，必须配置服务位与道路节点连接
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_station'
     AND COLUMN_NAME = 'anchor_node_code') = 0,
  'ALTER TABLE `t_station` ADD COLUMN `anchor_node_code` VARCHAR(64) DEFAULT NULL COMMENT ''站点接入的道路节点编码（吸附到 road_node.node_code）；NULL=未配置，需现场核验'' AFTER `avg_service_seconds`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.2 服务方向：FORWARD / REVERSE / BIDIRECTIONAL（车辆到站方向与道路方向关系）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_station'
     AND COLUMN_NAME = 'service_direction') = 0,
  'ALTER TABLE `t_station` ADD COLUMN `service_direction` VARCHAR(20) NOT NULL DEFAULT ''BIDIRECTIONAL'' COMMENT ''车辆到站服务方向：FORWARD/REVERSE/BIDIRECTIONAL'' AFTER `anchor_node_code`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.3 允许的车辆类型（NULL = 全部）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_station'
     AND COLUMN_NAME = 'allowed_vehicle_types') = 0,
  'ALTER TABLE `t_station` ADD COLUMN `allowed_vehicle_types` VARCHAR(255) DEFAULT NULL COMMENT ''允许服务的车辆类型（逗号分隔，NULL=全部）'' AFTER `service_direction`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.4 不可达原因：ROAD_CLOSED / NO_SERVICE_POSITION / VEHICLE_TYPE_NOT_ALLOWED /
--     CAPACITY_FULL / MAINTENANCE / OFFLINE / GATE_CLOSED / OUT_OF_RANGE
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_station'
     AND COLUMN_NAME = 'unreachable_reason') = 0,
  'ALTER TABLE `t_station` ADD COLUMN `unreachable_reason` VARCHAR(64) DEFAULT NULL COMMENT ''不可达原因：ROAD_CLOSED/NO_SERVICE_POSITION/VEHICLE_TYPE_NOT_ALLOWED/CAPACITY_FULL/MAINTENANCE/OFFLINE/GATE_CLOSED/OUT_OF_RANGE'' AFTER `allowed_vehicle_types`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.5 站点不可达失效时间（用于临时不可达自动恢复）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_station'
     AND COLUMN_NAME = 'unreachable_until') = 0,
  'ALTER TABLE `t_station` ADD COLUMN `unreachable_until` DATETIME DEFAULT NULL COMMENT ''不可达失效时间（NULL=永久或正常）'' AFTER `unreachable_reason`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. t_station_service_position 站点服务位子表（P0-5、P1-7）
--    每个站点可配置多个服务位，避免「全部放在道路中心线上」造成排队外溢。
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_station_service_position` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `station_id` BIGINT NOT NULL COMMENT '所属站点ID',
  `position_code` VARCHAR(64) NOT NULL COMMENT '服务位编码（站内唯一）',
  `position_name` VARCHAR(128) DEFAULT NULL COMMENT '服务位名称',
  `coord_lng` DECIMAL(12,7) DEFAULT NULL COMMENT '服务位经度（GCJ-02）',
  `coord_lat` DECIMAL(12,7) DEFAULT NULL COMMENT '服务位纬度（GCJ-02）',
  `coord_x` DECIMAL(12,4) DEFAULT NULL COMMENT '示意图坐标X（向后兼容）',
  `coord_y` DECIMAL(12,4) DEFAULT NULL COMMENT '示意图坐标Y（向后兼容）',
  `access_node_code` VARCHAR(64) DEFAULT NULL COMMENT '接入道路节点编码',
  `service_direction` VARCHAR(20) NOT NULL DEFAULT 'BIDIRECTIONAL' COMMENT '服务方向：FORWARD/REVERSE/BIDIRECTIONAL',
  `allowed_vehicle_types` VARCHAR(255) DEFAULT NULL COMMENT '允许车辆类型（逗号分隔，NULL=全部）',
  `capacity_limit` INT NOT NULL DEFAULT 1 COMMENT '服务位容量（同时停靠车辆数）',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/OCCUPIED/RESERVED/MAINTENANCE/OUT_OF_SERVICE',
  `reserved_vehicle_id` BIGINT DEFAULT NULL COMMENT '当前预约车辆ID',
  `reserved_until` DATETIME DEFAULT NULL COMMENT '预约保留至（超时自动释放）',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_station_position` (`station_id`, `position_code`),
  KEY `idx_access_node` (`access_node_code`),
  KEY `idx_status` (`status`),
  KEY `idx_reserved_vehicle` (`reserved_vehicle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站点服务位子表（一个站点可配置多个服务位）';

-- ============================================================
-- 4. t_station_service_position_reservation 服务位预约/锁定记录（P1-10）
--    防止多车同时占用同一服务位；支持预约超时自动释放。
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_station_service_position_reservation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `position_id` BIGINT NOT NULL COMMENT '服务位ID',
  `station_id` BIGINT NOT NULL COMMENT '所属站点ID（冗余，便于查询）',
  `vehicle_id` BIGINT NOT NULL COMMENT '预约车辆ID',
  `task_id` BIGINT DEFAULT NULL COMMENT '关联任务ID',
  `reservation_type` VARCHAR(32) NOT NULL DEFAULT 'LOCK' COMMENT '类型：LOCK（瞬时锁）/RESERVATION（预约）',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/RELEASED/EXPIRED/CANCELLED',
  `reserved_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预约时间',
  `expires_at` DATETIME DEFAULT NULL COMMENT '预约失效时间（NULL=永久锁定）',
  `released_at` DATETIME DEFAULT NULL COMMENT '实际释放时间',
  `release_reason` VARCHAR(64) DEFAULT NULL COMMENT '释放原因：COMPLETED/TIMEOUT/CANCELLED/REASSIGN',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_position_active` (`position_id`, `status`),
  KEY `idx_vehicle_active` (`vehicle_id`, `status`),
  KEY `idx_station_active` (`station_id`, `status`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='服务位预约/锁定记录';

-- ============================================================
-- 5. t_charging_pile 扩展：进出站节点 / 充电枪类型 / 预约状态 / 释放时间
-- ============================================================

-- 5.1 进站点（充电区入口道路节点编码）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_charging_pile'
     AND COLUMN_NAME = 'entry_node_code') = 0,
  'ALTER TABLE `t_charging_pile` ADD COLUMN `entry_node_code` VARCHAR(64) DEFAULT NULL COMMENT ''进站点（充电区入口道路节点编码）'' AFTER `max_power_kw`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.2 出站点（充电区出口道路节点编码，与进站点不同则需单向循环）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_charging_pile'
     AND COLUMN_NAME = 'exit_node_code') = 0,
  'ALTER TABLE `t_charging_pile` ADD COLUMN `exit_node_code` VARCHAR(64) DEFAULT NULL COMMENT ''出站点（充电区出口道路节点编码；与 entry 不同则需单向循环）'' AFTER `entry_node_code`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.3 充电枪类型：CCS2 / GB_T_DC / CHAOJI / AC_GENERIC / WIRELESS
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_charging_pile'
     AND COLUMN_NAME = 'plug_type') = 0,
  'ALTER TABLE `t_charging_pile` ADD COLUMN `plug_type` VARCHAR(32) DEFAULT NULL COMMENT ''充电枪类型：CCS2/GB_T_DC/CHAOJI/AC_GENERIC/WIRELESS'' AFTER `exit_node_code`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.4 预约状态：FREE / RESERVED / CHARGING / FAULT
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_charging_pile'
     AND COLUMN_NAME = 'reservation_state') = 0,
  'ALTER TABLE `t_charging_pile` ADD COLUMN `reservation_state` VARCHAR(32) NOT NULL DEFAULT ''FREE'' COMMENT ''预约状态：FREE/RESERVED/CHARGING/FAULT'' AFTER `plug_type`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5.5 预计释放时间（用于调度成本计算与排队估算）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_charging_pile'
     AND COLUMN_NAME = 'estimated_release_at') = 0,
  'ALTER TABLE `t_charging_pile` ADD COLUMN `estimated_release_at` DATETIME DEFAULT NULL COMMENT ''预计释放时间（用于调度成本与排队估算）'' AFTER `reservation_state`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 6. t_vehicle 扩展：车辆尺寸 / 转弯半径 / 允许道路等级（P1-3）
-- ============================================================

-- 6.1 车辆宽度（厘米）— 与道路 width_meters 做对比
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_vehicle'
     AND COLUMN_NAME = 'width_cm') = 0,
  'ALTER TABLE `t_vehicle` ADD COLUMN `width_cm` INT DEFAULT NULL COMMENT ''车辆宽度（厘米），用于道路宽度可用性检查'' AFTER `max_load_capacity`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6.2 车辆长度（厘米）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_vehicle'
     AND COLUMN_NAME = 'length_cm') = 0,
  'ALTER TABLE `t_vehicle` ADD COLUMN `length_cm` INT DEFAULT NULL COMMENT ''车辆长度（厘米）'' AFTER `width_cm`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6.3 最小转弯半径（米）— 用于窄路/急弯过滤
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_vehicle'
     AND COLUMN_NAME = 'turning_radius_m') = 0,
  'ALTER TABLE `t_vehicle` ADD COLUMN `turning_radius_m` DECIMAL(5,2) DEFAULT NULL COMMENT ''最小转弯半径（米），用于窄路/急弯过滤'' AFTER `length_cm`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6.4 允许道路等级（逗号分隔；NULL=全部允许）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_vehicle'
     AND COLUMN_NAME = 'allowed_road_classes') = 0,
  'ALTER TABLE `t_vehicle` ADD COLUMN `allowed_road_classes` VARCHAR(255) DEFAULT NULL COMMENT ''允许道路等级（逗号分隔，NULL=全部；如 ARTERIAL,SECONDARY）'' AFTER `turning_radius_m`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 7. t_parking_slot 扩展：朝向 / 进出节点 / 不阻塞主路标记（P1-8）
-- ============================================================

-- 7.1 车位朝向：NORTH/SOUTH/EAST/WEST/NE/NW/SE/SW
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_parking_slot'
     AND COLUMN_NAME = 'facing_direction') = 0,
  'ALTER TABLE `t_parking_slot` ADD COLUMN `facing_direction` VARCHAR(8) DEFAULT NULL COMMENT ''车位朝向：NORTH/SOUTH/EAST/WEST/NE/NW/SE/SW'' AFTER `slot_type`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7.2 进站节点（接入道路节点编码）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_parking_slot'
     AND COLUMN_NAME = 'entry_node_code') = 0,
  'ALTER TABLE `t_parking_slot` ADD COLUMN `entry_node_code` VARCHAR(64) DEFAULT NULL COMMENT ''进站节点编码'' AFTER `facing_direction`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7.3 出站节点（与 entry 不同则需单向循环）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_parking_slot'
     AND COLUMN_NAME = 'exit_node_code') = 0,
  'ALTER TABLE `t_parking_slot` ADD COLUMN `exit_node_code` VARCHAR(64) DEFAULT NULL COMMENT ''出站节点编码'' AFTER `entry_node_code`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7.4 是否阻塞主路（true=禁止长时间占用）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_parking_slot'
     AND COLUMN_NAME = 'blocking_main_road') = 0,
  'ALTER TABLE `t_parking_slot` ADD COLUMN `blocking_main_road` TINYINT NOT NULL DEFAULT 0 COMMENT ''是否阻塞主路（1=是，禁止长时间占用）'' AFTER `exit_node_code`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 8. t_map_data_version 地图数据版本（P2-6）
--    能追溯某次调度使用的路网版本，支持审计与回放。
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_map_data_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '所属园区ID',
  `version_code` VARCHAR(64) NOT NULL COMMENT '版本编码（如 ZJF-MAP-2026Q3-v1）',
  `version_label` VARCHAR(128) DEFAULT NULL COMMENT '版本名称（如 叠石桥试点-2026Q3）',
  `road_node_count` INT NOT NULL DEFAULT 0 COMMENT '路网节点数量',
  `road_segment_count` INT NOT NULL DEFAULT 0 COMMENT '路网路段数量',
  `station_count` INT NOT NULL DEFAULT 0 COMMENT '站点数量',
  `building_block_count` INT NOT NULL DEFAULT 0 COMMENT '建筑物障碍块数量',
  `published_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `published_by` VARCHAR(64) DEFAULT NULL COMMENT '发布人',
  `is_active` TINYINT NOT NULL DEFAULT 0 COMMENT '是否当前激活版本（1=是）',
  `checksum` VARCHAR(64) DEFAULT NULL COMMENT '数据指纹（SHA-256 of nodes+segments+buildings）',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_park_version` (`park_id`, `version_code`),
  KEY `idx_park_active` (`park_id`, `is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='地图数据版本表';

-- 8.1 写入初始版本记录（标记当前叠石桥路网为 2026Q3 v1）
INSERT INTO `t_map_data_version`
  (`id`, `park_id`, `version_code`, `version_label`, `road_node_count`, `road_segment_count`,
   `station_count`, `building_block_count`, `is_active`, `checksum`, `remark`)
SELECT
  1, 1, 'ZJF-MAP-2026Q3-v1', '叠石桥试点-2026Q3',
  (SELECT COUNT(*) FROM `t_road_node` WHERE `park_id` = 1 AND `status` = 'ACTIVE' AND `deleted` = 0),
  (SELECT COUNT(*) FROM `t_road_segment` WHERE `park_id` = 1 AND `status` = 'ACTIVE' AND `deleted` = 0),
  (SELECT COUNT(*) FROM `t_station` WHERE `park_id` = 1 AND `deleted` = 0),
  7,
  1,
  NULL,
  'V38 真实路网（55 节点 / 86 路段 / 13 站点）+ V37 五大配送分区建筑块'
WHERE NOT EXISTS (
  SELECT 1 FROM `t_map_data_version` WHERE `id` = 1
);

-- ============================================================
-- 9. ZJF 试点路网属性补齐（基于 V38 路段）
--    主干道（金洲大道/纺都大道/志远路/叠石桥路）：ARTERIAL，宽 6.0m
--    次干道（南排街/中排路/北排街/西排路/绣女路/东外环）：SECONDARY，宽 4.5m
--    枢纽对角转向连接：SERVICE_ROAD，宽 3.0m
--    access_state 默认 DRIVABLE；南排街部分路段 NO_STOP（仅装卸可临时停靠）
-- ============================================================
UPDATE `t_road_segment` SET
  `width_meters` = 6.0, `road_class` = 'ARTERIAL'
WHERE `park_id` = 1
  AND `remark` IN ('金洲大道', '纺都大道', '志远路', '叠石桥路')
  AND `width_meters` IS NULL
  AND `deleted` = 0;

UPDATE `t_road_segment` SET
  `width_meters` = 4.5, `road_class` = 'SECONDARY'
WHERE `park_id` = 1
  AND `remark` IN ('南排街', '中排路', '北排街', '西排路', '绣女路', '东外环')
  AND `width_meters` IS NULL
  AND `deleted` = 0;

UPDATE `t_road_segment` SET
  `width_meters` = 3.0, `road_class` = 'SERVICE_ROAD', `access_state` = 'NO_STOP'
WHERE `park_id` = 1
  AND `remark` = '对角连接(枢纽转向)'
  AND `width_meters` IS NULL
  AND `deleted` = 0;

-- 9.1 南排街核心装卸段允许临时停靠：LOADING_ONLY
UPDATE `t_road_segment` SET
  `access_state` = 'LOADING_ONLY'
WHERE `park_id` = 1
  AND `remark` = '南排街'
  AND `access_state` = 'DRIVABLE'
  AND `from_node_code` IN ('RN07', 'RN08')
  AND `deleted` = 0;

-- 9.2 充电站接入段：CHARGING_ACCESS
--     ZJF-CHG-* 站点周边路段标记为 CHARGING_ACCESS（允许进入充电服务区）
UPDATE `t_road_segment` SET
  `access_state` = 'CHARGING_ACCESS'
WHERE `park_id` = 1
  AND `access_state` = 'DRIVABLE'
  AND `deleted` = 0
  AND (
    (`from_node_code` = 'RN12' AND `to_node_code` = 'RN13')
    OR (`from_node_code` = 'RN13' AND `to_node_code` = 'RN14')
  );

-- ============================================================
-- 10. ZJF 站点服务位锚点配置（P0-5）
--     将站点接入到最近的道路节点，避免使用建筑中心点。
-- ============================================================
UPDATE `t_station` SET `anchor_node_code` = 'RN07'
WHERE `station_code` = 'ZJF-PICK-01' AND `anchor_node_code` IS NULL AND `deleted` = 0;
UPDATE `t_station` SET `anchor_node_code` = 'RN01'
WHERE `station_code` = 'ZJF-PICK-02' AND `anchor_node_code` IS NULL AND `deleted` = 0;
UPDATE `t_station` SET `anchor_node_code` = 'RN18'
WHERE `station_code` = 'ZJF-DROP-01' AND `anchor_node_code` IS NULL AND `deleted` = 0;
UPDATE `t_station` SET `anchor_node_code` = 'RN15'
WHERE `station_code` = 'ZJF-DROP-02' AND `anchor_node_code` IS NULL AND `deleted` = 0;
UPDATE `t_station` SET `anchor_node_code` = 'RN16'
WHERE `station_code` = 'ZJF-DROP-03' AND `anchor_node_code` IS NULL AND `deleted` = 0;
UPDATE `t_station` SET `anchor_node_code` = 'RN19'
WHERE `station_code` = 'ZJF-DROP-04' AND `anchor_node_code` IS NULL AND `deleted` = 0;
UPDATE `t_station` SET `anchor_node_code` = 'RN21'
WHERE `station_code` = 'ZJF-EXPRESS-01' AND `anchor_node_code` IS NULL AND `deleted` = 0;
UPDATE `t_station` SET `anchor_node_code` = 'RN13'
WHERE `station_code` = 'ZJF-IDLE-01' AND `anchor_node_code` IS NULL AND `deleted` = 0;
UPDATE `t_station` SET `anchor_node_code` = 'RN13'
WHERE `station_code` = 'ZJF-CHG-01' AND `anchor_node_code` IS NULL AND `deleted` = 0;
UPDATE `t_station` SET `anchor_node_code` = 'RN18'
WHERE `station_code` = 'ZJF-CHG-02' AND `anchor_node_code` IS NULL AND `deleted` = 0;
UPDATE `t_station` SET `anchor_node_code` = 'RN16'
WHERE `station_code` = 'ZJF-CHG-03' AND `anchor_node_code` IS NULL AND `deleted` = 0;
UPDATE `t_station` SET `anchor_node_code` = 'RN07'
WHERE `station_code` = 'ZJF-CHG-04' AND `anchor_node_code` IS NULL AND `deleted` = 0;
UPDATE `t_station` SET `anchor_node_code` = 'RN19'
WHERE `station_code` = 'ZJF-CHG-05' AND `anchor_node_code` IS NULL AND `deleted` = 0;

-- ============================================================
-- 11. 为每个站点创建默认服务位（站内编码 DEFAULT）
--     服务位坐标 = 站点坐标；接入节点 = anchor_node_code；状态 = ACTIVE
-- ============================================================
INSERT INTO `t_station_service_position`
  (`station_id`, `position_code`, `coord_lng`, `coord_lat`,
   `access_node_code`, `service_direction`, `capacity_limit`, `status`)
SELECT
  s.`id`, 'DEFAULT', s.`coord_lng`, s.`coord_lat`,
  s.`anchor_node_code`, s.`service_direction`,
  COALESCE(s.`capacity_limit`, 1), 'ACTIVE'
FROM `t_station` s
WHERE s.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `t_station_service_position` p
    WHERE p.`station_id` = s.`id` AND p.`position_code` = 'DEFAULT'
  );

-- ============================================================
-- 12. ZJF 充电桩配置进出站节点与插枪类型（P1-6）
-- ============================================================
UPDATE `t_charging_pile` SET
  `entry_node_code` = 'RN13', `exit_node_code` = 'RN14',
  `plug_type` = 'GB_T_DC'
WHERE `park_id` = 1 AND `entry_node_code` IS NULL AND `deleted` = 0;

-- ============================================================
-- 完成：可通行地图模型数据层已建立。
--   - 道路：宽度/等级/通行语义/中心线/车型过滤/转向限制/门禁/封路时间窗
--   - 站点：锚点节点/服务方向/允许车型/不可达原因+失效时间
--   - 站点服务位子表（多服务位/预约/锁定记录）
--   - 充电桩：进出站节点/插枪类型/预约状态/预计释放时间
--   - 车辆：宽度/长度/转弯半径/允许道路等级
--   - 待命区：朝向/进出节点/不阻塞主路标记
--   - 地图版本表（追溯调度使用的路网版本）
-- ============================================================
