-- V43: 路线安全闭环与建筑 Polygon 数据补齐
--
-- 本迁移对应 docs/DispatchFlow_最终更新路线图_2026-07-18.md 中 P0/P1 数据层要求：
--   * P0-3.1 禁止错误回退：新增 t_route_audit 审计表，记录路线规划与实际轨迹
--   * P0-3.2 起终点吸附：t_station_service_position 增加 stop_heading / enter_direction / leave_direction
--   * P0-4.2 建筑和障碍物：新增 t_building_block 真实 Polygon 表，替换固定近似矩形
--   * P0-5 站点可信度：t_station 增加 station_confidence（A/B/C 级）
--   * P1-3 车辆路由参数：t_vehicle 增加 height_cm / max_speed_kmh / current_speed_kmh /
--          current_heading / manual_override / emergency_mode / safety_buffer_meters
--   * P1-10 暂停派单：新增 t_dispatch_pause_state 全局开关表
--   * P2-11 路线健康指标：新增 t_route_health_metric 表
--
-- 设计原则：
--   * 所有 ALTER 通过 information_schema 判重，可重复执行（幂等）。
--   * 不删除既有列；既有实体仅追加属性。
--   * 坐标系沿用 V38/V42 约定：coord_lng/coord_lat 为 GCJ-02 经纬度。

USE `fsd_core`;

-- ============================================================
-- 1. t_building_block 真实建筑物与障碍物 Polygon（P0-4.2）
--    替换 PilotForbiddenZones.BUILDING_BLOCKS 固定近似矩形，
--    支持按车辆宽度/长度/朝向膨胀，记录来源/版本/生效时间/硬禁行标识。
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_building_block` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '所属园区ID',
  `block_code` VARCHAR(64) NOT NULL COMMENT '建筑/障碍物编码（园区内唯一）',
  `block_name` VARCHAR(128) DEFAULT NULL COMMENT '建筑/障碍物名称',
  `block_type` VARCHAR(32) NOT NULL DEFAULT 'BUILDING' COMMENT '类型：BUILDING/WALL/RIVER/GREENBELT/CONSTRUCTION/PARKING_OBSTACLE/GATEHOUSE',
  `polygon_geojson` JSON NOT NULL COMMENT 'Polygon GeoJSON（GCJ-02 坐标系，外环逆时针）',
  `centroid_lng` DECIMAL(12,7) DEFAULT NULL COMMENT '中心经度（GCJ-02，便于查询）',
  `centroid_lat` DECIMAL(12,7) DEFAULT NULL COMMENT '中心纬度（GCJ-02，便于查询）',
  `source` VARCHAR(32) NOT NULL DEFAULT 'OSM' COMMENT '数据来源：OSM/MANUAL/SURVEY/AERIAL',
  `map_version_id` BIGINT DEFAULT NULL COMMENT '关联地图数据版本ID（t_map_data_version.id）',
  `valid_from` DATETIME DEFAULT NULL COMMENT '生效起始时间（NULL=永久）',
  `valid_until` DATETIME DEFAULT NULL COMMENT '生效结束时间（NULL=永久有效）',
  `is_hard_forbidden` TINYINT NOT NULL DEFAULT 1 COMMENT '是否硬禁行（1=车辆包络不得进入；0=仅警告）',
  `default_expansion_buffer_meters` DECIMAL(5,2) NOT NULL DEFAULT 0.50 COMMENT '默认膨胀缓冲（米），按车辆宽度/朝向额外膨胀',
  `height_meters` DECIMAL(6,2) DEFAULT NULL COMMENT '建筑物高度（米），用于限高检查',
  `gate_code` VARCHAR(64) DEFAULT NULL COMMENT '关联门禁编码（block_type=GATEHOUSE 时）',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DEPRECATED/SUPERSEDED',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_park_block_code` (`park_id`, `block_code`),
  KEY `idx_park_type` (`park_id`, `block_type`),
  KEY `idx_park_status` (`park_id`, `status`),
  KEY `idx_map_version` (`map_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='建筑物与障碍物 Polygon 表（真实几何，非近似矩形）';

-- 1.1 从既有 V37 五大配送分区建筑块初始化（保留向后兼容）
--     将 PilotForbiddenZones.BUILDING_BLOCKS 的硬编码 Polygon 迁移到 t_building_block，
--     便于后续现场核验与版本管理。
--     使用 UNION ALL 派生数字辅助表生成 7 行数据；避免依赖 MySQL 会话变量。
INSERT INTO `t_building_block`
  (`park_id`, `block_code`, `block_name`, `block_type`, `polygon_geojson`,
   `source`, `map_version_id`, `is_hard_forbidden`, `default_expansion_buffer_meters`, `status`, `remark`)
SELECT
  1,
  CONCAT('ZJF-BLD-', LPAD(seq, 3, '0')),
  CONCAT('ZJF 试点建筑块 ', seq),
  'BUILDING',
  -- 7 个建筑块的近似 Polygon（沿用 V37 五分区硬编码，待现场核验替换为真实几何）
  CASE seq
    WHEN 1 THEN JSON_ARRAY(
      JSON_ARRAY(121.080000, 31.961500), JSON_ARRAY(121.080500, 31.961500),
      JSON_ARRAY(121.080500, 31.962000), JSON_ARRAY(121.080000, 31.962000),
      JSON_ARRAY(121.080000, 31.961500))
    WHEN 2 THEN JSON_ARRAY(
      JSON_ARRAY(121.081000, 31.961500), JSON_ARRAY(121.081500, 31.961500),
      JSON_ARRAY(121.081500, 31.962000), JSON_ARRAY(121.081000, 31.962000),
      JSON_ARRAY(121.081000, 31.961500))
    WHEN 3 THEN JSON_ARRAY(
      JSON_ARRAY(121.082000, 31.961500), JSON_ARRAY(121.082500, 31.961500),
      JSON_ARRAY(121.082500, 31.962000), JSON_ARRAY(121.082000, 31.962000),
      JSON_ARRAY(121.082000, 31.961500))
    WHEN 4 THEN JSON_ARRAY(
      JSON_ARRAY(121.083000, 31.961500), JSON_ARRAY(121.083500, 31.961500),
      JSON_ARRAY(121.083500, 31.962000), JSON_ARRAY(121.083000, 31.962000),
      JSON_ARRAY(121.083000, 31.961500))
    WHEN 5 THEN JSON_ARRAY(
      JSON_ARRAY(121.084000, 31.961500), JSON_ARRAY(121.084500, 31.961500),
      JSON_ARRAY(121.084500, 31.962000), JSON_ARRAY(121.084000, 31.962000),
      JSON_ARRAY(121.084000, 31.961500))
    WHEN 6 THEN JSON_ARRAY(
      JSON_ARRAY(121.080000, 31.962500), JSON_ARRAY(121.080500, 31.962500),
      JSON_ARRAY(121.080500, 31.963000), JSON_ARRAY(121.080000, 31.963000),
      JSON_ARRAY(121.080000, 31.962500))
    WHEN 7 THEN JSON_ARRAY(
      JSON_ARRAY(121.081000, 31.962500), JSON_ARRAY(121.081500, 31.962500),
      JSON_ARRAY(121.081500, 31.963000), JSON_ARRAY(121.081000, 31.963000),
      JSON_ARRAY(121.081000, 31.962500))
  END,
  'OSM',
  1,
  1,
  0.50,
  'ACTIVE',
  'V37 五大配送分区建筑块（初始化，待现场核验）'
FROM (
  SELECT 1 AS seq UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
  UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
) AS s
WHERE NOT EXISTS (SELECT 1 FROM `t_building_block` WHERE `park_id` = 1 LIMIT 1);

-- ============================================================
-- 2. t_station_service_position 扩展：到站朝向 / 进入方向 / 离开方向（P0-3.2 / P0-5）
--    车辆到达服务位时的精确朝向，避免在建筑出入口阻塞。
-- ============================================================

-- 2.1 到站车头朝向（度，0=北，顺时针）；NULL=不限制
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_station_service_position'
     AND COLUMN_NAME = 'stop_heading') = 0,
  'ALTER TABLE `t_station_service_position` ADD COLUMN `stop_heading` DECIMAL(6,2) DEFAULT NULL COMMENT ''到站车头朝向（度，0=北，顺时针；NULL=不限制）'' AFTER `service_direction`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.2 进入方向（车辆进入服务位时的方向编码：FORWARD/REVERSE/LEFT/RIGHT）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_station_service_position'
     AND COLUMN_NAME = 'enter_direction') = 0,
  'ALTER TABLE `t_station_service_position` ADD COLUMN `enter_direction` VARCHAR(16) DEFAULT NULL COMMENT ''进入方向：FORWARD/REVERSE/LEFT/RIGHT'' AFTER `stop_heading`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2.3 离开方向（车辆离开服务位时的方向编码）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_station_service_position'
     AND COLUMN_NAME = 'leave_direction') = 0,
  'ALTER TABLE `t_station_service_position` ADD COLUMN `leave_direction` VARCHAR(16) DEFAULT NULL COMMENT ''离开方向：FORWARD/REVERSE/LEFT/RIGHT'' AFTER `enter_direction`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 3. t_station 扩展：站点可信度 A/B/C 级（P0-5 / 视觉规范 §2.2）
--    A 级：高德公开 POI/地址核验，仅用于展示和区域判断。
--    B 级：道路或区域候选点，用于路线预览，现场确认后才能派单。
--    C 级：项目合成的投影基点、内部待命点、充电位或服务位，必须绑定运营台账和道路接入。
-- ============================================================
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_station'
     AND COLUMN_NAME = 'station_confidence') = 0,
  'ALTER TABLE `t_station` ADD COLUMN `station_confidence` VARCHAR(2) NOT NULL DEFAULT ''C'' COMMENT ''站点可信度：A=公开POI核验/B=候选待核验/C=合成点'' AFTER `unreachable_until`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3.1 既有 ZJF 站点默认置为 C 级（合成点），需现场核验后升级
UPDATE `t_station` SET `station_confidence` = 'C'
WHERE `station_confidence` = 'C' AND `deleted` = 0 AND `park_id` = 1;

-- ============================================================
-- 4. t_vehicle 扩展：车辆动态字段与运行态（P1-3 / 路线图 §6）
--    补齐路线规划每次必读的车辆参数。
-- ============================================================

-- 4.1 车辆高度（厘米）— 用于限高检查
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_vehicle'
     AND COLUMN_NAME = 'height_cm') = 0,
  'ALTER TABLE `t_vehicle` ADD COLUMN `height_cm` INT DEFAULT NULL COMMENT ''车辆高度（厘米），用于限高检查'' AFTER `length_cm`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.2 最大速度（km/h）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_vehicle'
     AND COLUMN_NAME = 'max_speed_kmh') = 0,
  'ALTER TABLE `t_vehicle` ADD COLUMN `max_speed_kmh` INT DEFAULT NULL COMMENT ''最大速度（km/h），用于 ETA 估算'' AFTER `allowed_road_classes`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.3 当前速度（km/h）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_vehicle'
     AND COLUMN_NAME = 'current_speed_kmh') = 0,
  'ALTER TABLE `t_vehicle` ADD COLUMN `current_speed_kmh` DECIMAL(5,2) DEFAULT NULL COMMENT ''当前速度（km/h），运行态上报'' AFTER `max_speed_kmh`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.4 当前车头朝向（度，0=北，顺时针）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_vehicle'
     AND COLUMN_NAME = 'current_heading') = 0,
  'ALTER TABLE `t_vehicle` ADD COLUMN `current_heading` DECIMAL(6,2) DEFAULT NULL COMMENT ''当前车头朝向（度，0=北，顺时针）'' AFTER `current_speed_kmh`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.5 人工接管状态（1=人工接管，0=自动）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_vehicle'
     AND COLUMN_NAME = 'manual_override') = 0,
  'ALTER TABLE `t_vehicle` ADD COLUMN `manual_override` TINYINT NOT NULL DEFAULT 0 COMMENT ''人工接管状态（1=人工接管，0=自动）'' AFTER `current_heading`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.6 紧急模式（1=紧急停车，0=正常）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_vehicle'
     AND COLUMN_NAME = 'emergency_mode') = 0,
  'ALTER TABLE `t_vehicle` ADD COLUMN `emergency_mode` TINYINT NOT NULL DEFAULT 0 COMMENT ''紧急模式（1=紧急停车，0=正常）'' AFTER `manual_override`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.7 安全缓冲（米）— 车辆包络膨胀，用于碰撞检查
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_vehicle'
     AND COLUMN_NAME = 'safety_buffer_meters') = 0,
  'ALTER TABLE `t_vehicle` ADD COLUMN `safety_buffer_meters` DECIMAL(4,2) NOT NULL DEFAULT 0.50 COMMENT ''安全缓冲（米），车辆包络膨胀，用于碰撞检查'' AFTER `emergency_mode`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4.8 当前地图数据版本ID（关联 t_map_data_version.id）
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_vehicle'
     AND COLUMN_NAME = 'current_map_version_id') = 0,
  'ALTER TABLE `t_vehicle` ADD COLUMN `current_map_version_id` BIGINT DEFAULT NULL COMMENT ''当前地图数据版本ID（关联 t_map_data_version.id）'' AFTER `safety_buffer_meters`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============================================================
-- 5. t_route_audit 路线审计表（P0-3.1 / P2-11）
--    每次路线规划保存审计信息，支持规划路线与实际轨迹对比。
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_route_audit` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `route_id` VARCHAR(64) NOT NULL COMMENT '路线ID（UUID 或业务编码）',
  `task_id` BIGINT DEFAULT NULL COMMENT '关联任务ID',
  `vehicle_id` BIGINT DEFAULT NULL COMMENT '关联车辆ID',
  `park_id` BIGINT NOT NULL COMMENT '所属园区ID',
  `map_version_id` BIGINT DEFAULT NULL COMMENT '地图数据版本ID',
  `map_version_code` VARCHAR(64) DEFAULT NULL COMMENT '地图版本编码（冗余）',
  `route_mode` VARCHAR(32) NOT NULL DEFAULT 'REAL_ROAD' COMMENT '路线模式：REAL_ROAD/SCHEMATIC/STRAIGHT_LINE',
  `source` VARCHAR(32) NOT NULL DEFAULT 'LOCAL_GRAPH' COMMENT '路线来源：AMAP/LOCAL_GRAPH/STRAIGHT_LINE',
  `origin_lng` DECIMAL(12,7) NOT NULL COMMENT '起点经度（GCJ-02）',
  `origin_lat` DECIMAL(12,7) NOT NULL COMMENT '起点纬度（GCJ-02）',
  `destination_lng` DECIMAL(12,7) NOT NULL COMMENT '终点经度（GCJ-02）',
  `destination_lat` DECIMAL(12,7) NOT NULL COMMENT '终点纬度（GCJ-02）',
  `planned_polyline` JSON DEFAULT NULL COMMENT '规划路线 polyline（GCJ-02 坐标点数组）',
  `actual_polyline` JSON DEFAULT NULL COMMENT '实际轨迹 polyline（运行后回填）',
  `planned_length_meters` DECIMAL(10,2) DEFAULT NULL COMMENT '规划路线长度（米）',
  `actual_length_meters` DECIMAL(10,2) DEFAULT NULL COMMENT '实际轨迹长度（米）',
  `deviation_meters` DECIMAL(10,2) DEFAULT NULL COMMENT '最大偏航距离（米）',
  `reroute_count` INT NOT NULL DEFAULT 0 COMMENT '重规划次数',
  `collision_checked` TINYINT NOT NULL DEFAULT 0 COMMENT '是否经过碰撞校验（1=是）',
  `crosses_building` TINYINT NOT NULL DEFAULT 0 COMMENT '是否穿越建筑（1=是）',
  `crosses_river` TINYINT NOT NULL DEFAULT 0 COMMENT '是否穿越河道（1=是）',
  `unreachable_reason` VARCHAR(64) DEFAULT NULL COMMENT '不可达原因编码（RouteUnreachableReason）',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PLANNED' COMMENT '状态：PLANNED/EXECUTING/COMPLETED/FAILED/DEVIATED',
  `planned_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '规划时间',
  `executed_at` DATETIME DEFAULT NULL COMMENT '执行开始时间',
  `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_route_id` (`route_id`),
  KEY `idx_task` (`task_id`),
  KEY `idx_vehicle` (`vehicle_id`),
  KEY `idx_park_status` (`park_id`, `status`),
  KEY `idx_planned_at` (`planned_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='路线审计表（规划 vs 实际对比）';

-- ============================================================
-- 6. t_route_health_metric 路线健康指标（P2-11）
--    持续监控路线系统的健康度。
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_route_health_metric` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '所属园区ID',
  `metric_code` VARCHAR(64) NOT NULL COMMENT '指标编码：EMPTY_ROAD_NETWORK/DISCONNECTED/NOT_SNAPPED/OFF_ROAD/CROSSES_BUILDING/STRAIGHT_LINE_FALLBACK/RESERVATION_CONFLICT',
  `metric_value` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '指标值（次数或百分比）',
  `metric_detail` JSON DEFAULT NULL COMMENT '指标详情（结构化 JSON）',
  `recorded_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_park_metric_time` (`park_id`, `metric_code`, `recorded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='路线健康指标表';

-- ============================================================
-- 7. t_dispatch_pause_state 暂停派单全局开关（P1-10）
--    统一控制自动派车、批量派车、紧急插队。
-- ============================================================
CREATE TABLE IF NOT EXISTS `t_dispatch_pause_state` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '所属园区ID',
  `is_paused` TINYINT NOT NULL DEFAULT 0 COMMENT '是否暂停派单（1=暂停，0=正常）',
  `pause_reason` VARCHAR(128) DEFAULT NULL COMMENT '暂停原因',
  `paused_by` VARCHAR(64) DEFAULT NULL COMMENT '暂停操作人',
  `paused_at` DATETIME DEFAULT NULL COMMENT '暂停时间',
  `resumed_at` DATETIME DEFAULT NULL COMMENT '恢复时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_park` (`park_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='暂停派单全局开关';

-- 7.1 为既有园区初始化暂停状态记录（默认未暂停）
INSERT INTO `t_dispatch_pause_state` (`park_id`, `is_paused`)
SELECT p.`id`, 0
FROM `t_park` p
WHERE NOT EXISTS (SELECT 1 FROM `t_dispatch_pause_state` s WHERE s.`park_id` = p.`id`);

-- ============================================================
-- 完成：路线安全闭环数据层已建立。
--   - 建筑物 Polygon 表（真实几何 / 来源 / 版本 / 硬禁行 / 膨胀缓冲）
--   - 服务位到站朝向 / 进入方向 / 离开方向
--   - 站点可信度 A/B/C 级
--   - 车辆动态字段（高度 / 限速 / 当前速度 / 朝向 / 人工接管 / 紧急模式 / 安全缓冲 / 地图版本）
--   - 路线审计表（规划 vs 实际对比 / 偏航 / 重规划 / 碰撞校验）
--   - 路线健康指标表（空路网 / 断连 / 未吸附 / 离路 / 穿建筑 / 直线兜底 / 预约冲突）
--   - 暂停派单全局开关表
-- ============================================================
