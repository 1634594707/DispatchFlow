-- V38: 叠石桥家纺试点真实路网（GCJ-02 GPS 坐标）+ 站点坐标复核
-- 替换 V09 中基于像素示意图的路网（R1~R24），改用叠石桥家纺产业园真实道路网格的
-- GCJ-02 经纬度作为路网节点的权威坐标来源。
--
-- 坐标体系说明：
--   * coord_lng / coord_lat（DECIMAL(12,7)）：GCJ-02 真实经纬度，本迁移起新增，为权威坐标。
--   * coord_x / coord_y（DECIMAL(12,4)）：旧示意图坐标系，仅用于向后兼容；
--     按近似映射公式生成：x = (lng - 121.072) * 77000, y = (31.9645 - lat) * 150000。
--   * 园区中心：lng=121.080354, lat=31.961977；园区范围约 1570m × 470m。
--
-- 道路网格（叠石桥家纺城 · OSM bbox · GCJ-02）：
--   横向（东西向）道路：
--     金洲大道（南边界）  lat ≈ 31.9600
--     南排街（南排门市）  lat ≈ 31.9606
--     中排路（中排主轴）  lat ≈ 31.9619
--     北排街（北排仓库）  lat ≈ 31.9633
--     纺都大道（北边界）  lat ≈ 31.9640
--   纵向（南北向）道路：
--     西排路（西边界）    lng ≈ 121.0726
--     绣女路（中西列）    lng ≈ 121.0750
--     志远路（中轴线）    lng ≈ 121.0793
--     叠石桥路（东列）    lng ≈ 121.0838
--     东外环（东边界）    lng ≈ 121.0880
--   节点编码：RN01~RN55（55 个节点，5×5 交叉路口 + 街区中段 + 园区出入口）
--   路段编码：4101~4186（86 条路段，横向 + 纵向 + 枢纽对角转向连接）
--
-- 幂等性：本迁移可安全重复执行（ALTER 用 information_schema 判重；INSERT 用 ON DUPLICATE KEY UPDATE）。

USE `fsd_core`;

-- ============================================================
-- 1. t_road_node 新增 GCJ-02 经纬度列（幂等）
-- ============================================================
SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'fsd_core' AND TABLE_NAME = 't_road_node' AND COLUMN_NAME = 'coord_lng') = 0,
    'ALTER TABLE `t_road_node` ADD COLUMN `coord_lng` DECIMAL(12,7) DEFAULT NULL COMMENT ''GCJ-02 经度'' AFTER `coord_y`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = 'fsd_core' AND TABLE_NAME = 't_road_node' AND COLUMN_NAME = 'coord_lat') = 0,
    'ALTER TABLE `t_road_node` ADD COLUMN `coord_lat` DECIMAL(12,7) DEFAULT NULL COMMENT ''GCJ-02 纬度'' AFTER `coord_lng`',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 2. 停用旧示意图路网（R1~R24 节点 + 其路段），保留不删除以备回溯
--    正则 ^R[0-9]+$ 仅匹配 R1~R24，不会误伤新节点 RN01~RN55
-- ============================================================
UPDATE `t_road_node`
SET `status` = 'DISABLED', `updated_at` = NOW()
WHERE `park_id` = 1
  AND `node_code` REGEXP '^R[0-9]+$'
  AND `deleted` = 0;

UPDATE `t_road_segment`
SET `status` = 'DISABLED', `updated_at` = NOW()
WHERE `park_id` = 1
  AND `from_node_code` REGEXP '^R[0-9]+$'
  AND `deleted` = 0;

-- ============================================================
-- 3. 写入真实路网节点（RN01~RN55）
--    坐标按近似公式由 GPS 映射至旧示意图坐标系：
--      x = (lng - 121.072) * 77000, y = (31.9645 - lat) * 150000
-- ============================================================

-- 3.1 横向道路与纵向道路的 5×5 交叉路口（RN01~RN25）
INSERT INTO `t_road_node`
  (`id`, `park_id`, `node_code`, `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `status`, `remark`, `version`, `deleted`)
VALUES
  -- 金洲大道（lat 31.9600）沿线交叉口
  (3101, 1, 'RN01', 46.2000, 675.0000, 121.0726000, 31.9600000, 'ACTIVE', '西排路×金洲大道', 0, 0),
  (3102, 1, 'RN02', 231.0000, 675.0000, 121.0750000, 31.9600000, 'ACTIVE', '绣女路×金洲大道', 0, 0),
  (3103, 1, 'RN03', 562.1000, 675.0000, 121.0793000, 31.9600000, 'ACTIVE', '志远路×金洲大道', 0, 0),
  (3104, 1, 'RN04', 908.6000, 675.0000, 121.0838000, 31.9600000, 'ACTIVE', '叠石桥路×金洲大道', 0, 0),
  (3105, 1, 'RN05', 1232.0000, 675.0000, 121.0880000, 31.9600000, 'ACTIVE', '东外环×金洲大道', 0, 0),
  -- 南排街（lat 31.9606）沿线交叉口
  (3106, 1, 'RN06', 46.2000, 585.0000, 121.0726000, 31.9606000, 'ACTIVE', '西排路×南排街', 0, 0),
  (3107, 1, 'RN07', 231.0000, 585.0000, 121.0750000, 31.9606000, 'ACTIVE', '绣女路×南排街', 0, 0),
  (3108, 1, 'RN08', 562.1000, 585.0000, 121.0793000, 31.9606000, 'ACTIVE', '志远路×南排街', 0, 0),
  (3109, 1, 'RN09', 908.6000, 585.0000, 121.0838000, 31.9606000, 'ACTIVE', '叠石桥路×南排街', 0, 0),
  (3110, 1, 'RN10', 1232.0000, 585.0000, 121.0880000, 31.9606000, 'ACTIVE', '东外环×南排街', 0, 0),
  -- 中排路（lat 31.9619）沿线交叉口
  (3111, 1, 'RN11', 46.2000, 390.0000, 121.0726000, 31.9619000, 'ACTIVE', '西排路×中排路', 0, 0),
  (3112, 1, 'RN12', 231.0000, 390.0000, 121.0750000, 31.9619000, 'ACTIVE', '绣女路×中排路', 0, 0),
  (3113, 1, 'RN13', 562.1000, 390.0000, 121.0793000, 31.9619000, 'ACTIVE', '志远路×中排路（园区中心）', 0, 0),
  (3114, 1, 'RN14', 908.6000, 390.0000, 121.0838000, 31.9619000, 'ACTIVE', '叠石桥路×中排路', 0, 0),
  (3115, 1, 'RN15', 1232.0000, 390.0000, 121.0880000, 31.9619000, 'ACTIVE', '东外环×中排路', 0, 0),
  -- 北排街（lat 31.9633）沿线交叉口
  (3116, 1, 'RN16', 46.2000, 180.0000, 121.0726000, 31.9633000, 'ACTIVE', '西排路×北排街', 0, 0),
  (3117, 1, 'RN17', 231.0000, 180.0000, 121.0750000, 31.9633000, 'ACTIVE', '绣女路×北排街', 0, 0),
  (3118, 1, 'RN18', 562.1000, 180.0000, 121.0793000, 31.9633000, 'ACTIVE', '志远路×北排街', 0, 0),
  (3119, 1, 'RN19', 908.6000, 180.0000, 121.0838000, 31.9633000, 'ACTIVE', '叠石桥路×北排街', 0, 0),
  (3120, 1, 'RN20', 1232.0000, 180.0000, 121.0880000, 31.9633000, 'ACTIVE', '东外环×北排街', 0, 0),
  -- 纺都大道（lat 31.9640）沿线交叉口
  (3121, 1, 'RN21', 46.2000, 75.0000, 121.0726000, 31.9640000, 'ACTIVE', '西排路×纺都大道', 0, 0),
  (3122, 1, 'RN22', 231.0000, 75.0000, 121.0750000, 31.9640000, 'ACTIVE', '绣女路×纺都大道', 0, 0),
  (3123, 1, 'RN23', 562.1000, 75.0000, 121.0793000, 31.9640000, 'ACTIVE', '志远路×纺都大道', 0, 0),
  (3124, 1, 'RN24', 908.6000, 75.0000, 121.0838000, 31.9640000, 'ACTIVE', '叠石桥路×纺都大道', 0, 0),
  (3125, 1, 'RN25', 1232.0000, 75.0000, 121.0880000, 31.9640000, 'ACTIVE', '东外环×纺都大道', 0, 0)
ON DUPLICATE KEY UPDATE
  `coord_x` = VALUES(`coord_x`), `coord_y` = VALUES(`coord_y`),
  `coord_lng` = VALUES(`coord_lng`), `coord_lat` = VALUES(`coord_lat`),
  `status` = VALUES(`status`), `deleted` = VALUES(`deleted`), `remark` = VALUES(`remark`);

-- 3.2 横向道路街区中段节点（志远路-叠石桥路 / 叠石桥路-东外环 之间）
INSERT INTO `t_road_node`
  (`id`, `park_id`, `node_code`, `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `status`, `remark`, `version`, `deleted`)
VALUES
  -- 志远路-叠石桥路之间中段（lng 121.08155）
  (3126, 1, 'RN26', 735.4000, 675.0000, 121.0815500, 31.9600000, 'ACTIVE', '金洲大道中段(志远-叠石桥)', 0, 0),
  (3127, 1, 'RN27', 735.4000, 585.0000, 121.0815500, 31.9606000, 'ACTIVE', '南排街中段(志远-叠石桥)', 0, 0),
  (3128, 1, 'RN28', 735.4000, 390.0000, 121.0815500, 31.9619000, 'ACTIVE', '中排路中段(志远-叠石桥)', 0, 0),
  (3129, 1, 'RN29', 735.4000, 180.0000, 121.0815500, 31.9633000, 'ACTIVE', '北排街中段(志远-叠石桥)', 0, 0),
  (3130, 1, 'RN30', 735.4000, 75.0000, 121.0815500, 31.9640000, 'ACTIVE', '纺都大道中段(志远-叠石桥)', 0, 0),
  -- 叠石桥路-东外环之间中段（lng 121.0859）
  (3131, 1, 'RN31', 1070.3000, 675.0000, 121.0859000, 31.9600000, 'ACTIVE', '金洲大道中段(叠石桥-东外环)', 0, 0),
  (3132, 1, 'RN32', 1070.3000, 585.0000, 121.0859000, 31.9606000, 'ACTIVE', '南排街中段(叠石桥-东外环)', 0, 0),
  (3133, 1, 'RN33', 1070.3000, 390.0000, 121.0859000, 31.9619000, 'ACTIVE', '中排路中段(叠石桥-东外环)', 0, 0),
  (3134, 1, 'RN34', 1070.3000, 180.0000, 121.0859000, 31.9633000, 'ACTIVE', '北排街中段(叠石桥-东外环)', 0, 0),
  (3135, 1, 'RN35', 1070.3000, 75.0000, 121.0859000, 31.9640000, 'ACTIVE', '纺都大道中段(叠石桥-东外环)', 0, 0)
ON DUPLICATE KEY UPDATE
  `coord_x` = VALUES(`coord_x`), `coord_y` = VALUES(`coord_y`),
  `coord_lng` = VALUES(`coord_lng`), `coord_lat` = VALUES(`coord_lat`),
  `status` = VALUES(`status`), `deleted` = VALUES(`deleted`), `remark` = VALUES(`remark`);

-- 3.3 纵向道路街区中段节点（南排街-中排路 / 中排路-北排街 之间）
INSERT INTO `t_road_node`
  (`id`, `park_id`, `node_code`, `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `status`, `remark`, `version`, `deleted`)
VALUES
  -- 南排街-中排路之间中段（lat 31.96125）
  (3136, 1, 'RN36', 46.2000, 487.5000, 121.0726000, 31.9612500, 'ACTIVE', '西排路中段(南排-中排)', 0, 0),
  (3137, 1, 'RN37', 231.0000, 487.5000, 121.0750000, 31.9612500, 'ACTIVE', '绣女路中段(南排-中排)', 0, 0),
  (3138, 1, 'RN38', 562.1000, 487.5000, 121.0793000, 31.9612500, 'ACTIVE', '志远路中段(南排-中排)', 0, 0),
  (3139, 1, 'RN39', 908.6000, 487.5000, 121.0838000, 31.9612500, 'ACTIVE', '叠石桥路中段(南排-中排)', 0, 0),
  (3140, 1, 'RN40', 1232.0000, 487.5000, 121.0880000, 31.9612500, 'ACTIVE', '东外环中段(南排-中排)', 0, 0),
  -- 中排路-北排街之间中段（lat 31.9626）
  (3141, 1, 'RN41', 46.2000, 285.0000, 121.0726000, 31.9626000, 'ACTIVE', '西排路中段(中排-北排)', 0, 0),
  (3142, 1, 'RN42', 231.0000, 285.0000, 121.0750000, 31.9626000, 'ACTIVE', '绣女路中段(中排-北排)', 0, 0),
  (3143, 1, 'RN43', 562.1000, 285.0000, 121.0793000, 31.9626000, 'ACTIVE', '志远路中段(中排-北排)', 0, 0),
  (3144, 1, 'RN44', 908.6000, 285.0000, 121.0838000, 31.9626000, 'ACTIVE', '叠石桥路中段(中排-北排)', 0, 0),
  (3145, 1, 'RN45', 1232.0000, 285.0000, 121.0880000, 31.9626000, 'ACTIVE', '东外环中段(中排-北排)', 0, 0)
ON DUPLICATE KEY UPDATE
  `coord_x` = VALUES(`coord_x`), `coord_y` = VALUES(`coord_y`),
  `coord_lng` = VALUES(`coord_lng`), `coord_lat` = VALUES(`coord_lat`),
  `status` = VALUES(`status`), `deleted` = VALUES(`deleted`), `remark` = VALUES(`remark`);

-- 3.4 园区出入口 / 道路延伸节点
INSERT INTO `t_road_node`
  (`id`, `park_id`, `node_code`, `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `status`, `remark`, `version`, `deleted`)
VALUES
  (3146, 1, 'RN46', 46.2000, 780.0000, 121.0726000, 31.9593000, 'ACTIVE', '西排路南延(南门)', 0, 0),
  (3147, 1, 'RN47', 1232.0000, 780.0000, 121.0880000, 31.9593000, 'ACTIVE', '东外环南延(东南门)', 0, 0),
  (3148, 1, 'RN48', 46.2000, -15.0000, 121.0726000, 31.9646000, 'ACTIVE', '西排路北延(北门)', 0, 0),
  (3149, 1, 'RN49', 1232.0000, -15.0000, 121.0880000, 31.9646000, 'ACTIVE', '东外环北延(东北门)', 0, 0),
  (3150, 1, 'RN50', 562.1000, 780.0000, 121.0793000, 31.9593000, 'ACTIVE', '志远路南延(主南门)', 0, 0),
  (3151, 1, 'RN51', 562.1000, -15.0000, 121.0793000, 31.9646000, 'ACTIVE', '志远路北延(主北门)', 0, 0),
  (3152, 1, 'RN52', 231.0000, 780.0000, 121.0750000, 31.9593000, 'ACTIVE', '绣女路南延', 0, 0),
  (3153, 1, 'RN53', 908.6000, 780.0000, 121.0838000, 31.9593000, 'ACTIVE', '叠石桥路南延', 0, 0),
  (3154, 1, 'RN54', -77.0000, 75.0000, 121.0710000, 31.9640000, 'ACTIVE', '纺都大道西延(西门)', 0, 0),
  (3155, 1, 'RN55', -77.0000, 675.0000, 121.0710000, 31.9600000, 'ACTIVE', '金洲大道西延', 0, 0)
ON DUPLICATE KEY UPDATE
  `coord_x` = VALUES(`coord_x`), `coord_y` = VALUES(`coord_y`),
  `coord_lng` = VALUES(`coord_lng`), `coord_lat` = VALUES(`coord_lat`),
  `status` = VALUES(`status`), `deleted` = VALUES(`deleted`), `remark` = VALUES(`remark`);

-- ============================================================
-- 4. 写入真实路网路段（4101~4186，共 86 条）
--    横向相邻节点 + 纵向相邻节点 + 枢纽对角转向连接
--    主干道（金洲大道/纺都大道/志远路/叠石桥路）限速 20km/h、拥堵 1；
--    次干道限速 15km/h、拥堵 0；对角转向连接限速 10km/h、拥堵 0。
-- ============================================================

-- 4.1 金洲大道（东西向主干 · 南边界）RN55→RN01→RN02→RN03→RN26→RN04→RN31→RN05
INSERT INTO `t_road_segment`
  (`id`, `park_id`, `from_node_code`, `to_node_code`, `status`, `speed_limit_kmh`, `congestion_level`, `remark`, `version`, `deleted`)
VALUES
  (4101, 1, 'RN55', 'RN01', 'ACTIVE', 20, 1, '金洲大道', 0, 0),
  (4102, 1, 'RN01', 'RN02', 'ACTIVE', 20, 1, '金洲大道', 0, 0),
  (4103, 1, 'RN02', 'RN03', 'ACTIVE', 20, 1, '金洲大道', 0, 0),
  (4104, 1, 'RN03', 'RN26', 'ACTIVE', 20, 1, '金洲大道', 0, 0),
  (4105, 1, 'RN26', 'RN04', 'ACTIVE', 20, 1, '金洲大道', 0, 0),
  (4106, 1, 'RN04', 'RN31', 'ACTIVE', 20, 1, '金洲大道', 0, 0),
  (4107, 1, 'RN31', 'RN05', 'ACTIVE', 20, 1, '金洲大道', 0, 0)
ON DUPLICATE KEY UPDATE
  `status` = VALUES(`status`), `speed_limit_kmh` = VALUES(`speed_limit_kmh`),
  `congestion_level` = VALUES(`congestion_level`), `remark` = VALUES(`remark`),
  `deleted` = VALUES(`deleted`);

-- 4.2 南排街（东西向 · 南排门市）RN06→RN07→RN08→RN27→RN09→RN32→RN10
INSERT INTO `t_road_segment`
  (`id`, `park_id`, `from_node_code`, `to_node_code`, `status`, `speed_limit_kmh`, `congestion_level`, `remark`, `version`, `deleted`)
VALUES
  (4108, 1, 'RN06', 'RN07', 'ACTIVE', 15, 0, '南排街', 0, 0),
  (4109, 1, 'RN07', 'RN08', 'ACTIVE', 15, 0, '南排街', 0, 0),
  (4110, 1, 'RN08', 'RN27', 'ACTIVE', 15, 0, '南排街', 0, 0),
  (4111, 1, 'RN27', 'RN09', 'ACTIVE', 15, 0, '南排街', 0, 0),
  (4112, 1, 'RN09', 'RN32', 'ACTIVE', 15, 0, '南排街', 0, 0),
  (4113, 1, 'RN32', 'RN10', 'ACTIVE', 15, 0, '南排街', 0, 0)
ON DUPLICATE KEY UPDATE
  `status` = VALUES(`status`), `speed_limit_kmh` = VALUES(`speed_limit_kmh`),
  `congestion_level` = VALUES(`congestion_level`), `remark` = VALUES(`remark`),
  `deleted` = VALUES(`deleted`);

-- 4.3 中排路（东西向 · 中排主轴）RN11→RN12→RN13→RN28→RN14→RN33→RN15
INSERT INTO `t_road_segment`
  (`id`, `park_id`, `from_node_code`, `to_node_code`, `status`, `speed_limit_kmh`, `congestion_level`, `remark`, `version`, `deleted`)
VALUES
  (4114, 1, 'RN11', 'RN12', 'ACTIVE', 15, 0, '中排路', 0, 0),
  (4115, 1, 'RN12', 'RN13', 'ACTIVE', 15, 0, '中排路', 0, 0),
  (4116, 1, 'RN13', 'RN28', 'ACTIVE', 15, 0, '中排路', 0, 0),
  (4117, 1, 'RN28', 'RN14', 'ACTIVE', 15, 0, '中排路', 0, 0),
  (4118, 1, 'RN14', 'RN33', 'ACTIVE', 15, 0, '中排路', 0, 0),
  (4119, 1, 'RN33', 'RN15', 'ACTIVE', 15, 0, '中排路', 0, 0)
ON DUPLICATE KEY UPDATE
  `status` = VALUES(`status`), `speed_limit_kmh` = VALUES(`speed_limit_kmh`),
  `congestion_level` = VALUES(`congestion_level`), `remark` = VALUES(`remark`),
  `deleted` = VALUES(`deleted`);

-- 4.4 北排街（东西向 · 北排仓库）RN16→RN17→RN18→RN29→RN19→RN34→RN20
INSERT INTO `t_road_segment`
  (`id`, `park_id`, `from_node_code`, `to_node_code`, `status`, `speed_limit_kmh`, `congestion_level`, `remark`, `version`, `deleted`)
VALUES
  (4120, 1, 'RN16', 'RN17', 'ACTIVE', 15, 0, '北排街', 0, 0),
  (4121, 1, 'RN17', 'RN18', 'ACTIVE', 15, 0, '北排街', 0, 0),
  (4122, 1, 'RN18', 'RN29', 'ACTIVE', 15, 0, '北排街', 0, 0),
  (4123, 1, 'RN29', 'RN19', 'ACTIVE', 15, 0, '北排街', 0, 0),
  (4124, 1, 'RN19', 'RN34', 'ACTIVE', 15, 0, '北排街', 0, 0),
  (4125, 1, 'RN34', 'RN20', 'ACTIVE', 15, 0, '北排街', 0, 0)
ON DUPLICATE KEY UPDATE
  `status` = VALUES(`status`), `speed_limit_kmh` = VALUES(`speed_limit_kmh`),
  `congestion_level` = VALUES(`congestion_level`), `remark` = VALUES(`remark`),
  `deleted` = VALUES(`deleted`);

-- 4.5 纺都大道（东西向主干 · 北边界）RN54→RN21→RN22→RN23→RN30→RN24→RN35→RN25
INSERT INTO `t_road_segment`
  (`id`, `park_id`, `from_node_code`, `to_node_code`, `status`, `speed_limit_kmh`, `congestion_level`, `remark`, `version`, `deleted`)
VALUES
  (4126, 1, 'RN54', 'RN21', 'ACTIVE', 20, 1, '纺都大道', 0, 0),
  (4127, 1, 'RN21', 'RN22', 'ACTIVE', 20, 1, '纺都大道', 0, 0),
  (4128, 1, 'RN22', 'RN23', 'ACTIVE', 20, 1, '纺都大道', 0, 0),
  (4129, 1, 'RN23', 'RN30', 'ACTIVE', 20, 1, '纺都大道', 0, 0),
  (4130, 1, 'RN30', 'RN24', 'ACTIVE', 20, 1, '纺都大道', 0, 0),
  (4131, 1, 'RN24', 'RN35', 'ACTIVE', 20, 1, '纺都大道', 0, 0),
  (4132, 1, 'RN35', 'RN25', 'ACTIVE', 20, 1, '纺都大道', 0, 0)
ON DUPLICATE KEY UPDATE
  `status` = VALUES(`status`), `speed_limit_kmh` = VALUES(`speed_limit_kmh`),
  `congestion_level` = VALUES(`congestion_level`), `remark` = VALUES(`remark`),
  `deleted` = VALUES(`deleted`);

-- 4.6 西排路（南北向 · 西边界）RN46→RN01→RN06→RN36→RN11→RN41→RN16→RN21→RN48
INSERT INTO `t_road_segment`
  (`id`, `park_id`, `from_node_code`, `to_node_code`, `status`, `speed_limit_kmh`, `congestion_level`, `remark`, `version`, `deleted`)
VALUES
  (4133, 1, 'RN46', 'RN01', 'ACTIVE', 15, 0, '西排路', 0, 0),
  (4134, 1, 'RN01', 'RN06', 'ACTIVE', 15, 0, '西排路', 0, 0),
  (4135, 1, 'RN06', 'RN36', 'ACTIVE', 15, 0, '西排路', 0, 0),
  (4136, 1, 'RN36', 'RN11', 'ACTIVE', 15, 0, '西排路', 0, 0),
  (4137, 1, 'RN11', 'RN41', 'ACTIVE', 15, 0, '西排路', 0, 0),
  (4138, 1, 'RN41', 'RN16', 'ACTIVE', 15, 0, '西排路', 0, 0),
  (4139, 1, 'RN16', 'RN21', 'ACTIVE', 15, 0, '西排路', 0, 0),
  (4140, 1, 'RN21', 'RN48', 'ACTIVE', 15, 0, '西排路', 0, 0)
ON DUPLICATE KEY UPDATE
  `status` = VALUES(`status`), `speed_limit_kmh` = VALUES(`speed_limit_kmh`),
  `congestion_level` = VALUES(`congestion_level`), `remark` = VALUES(`remark`),
  `deleted` = VALUES(`deleted`);

-- 4.7 绣女路（南北向 · 中西列）RN52→RN02→RN07→RN37→RN12→RN42→RN17→RN22
INSERT INTO `t_road_segment`
  (`id`, `park_id`, `from_node_code`, `to_node_code`, `status`, `speed_limit_kmh`, `congestion_level`, `remark`, `version`, `deleted`)
VALUES
  (4141, 1, 'RN52', 'RN02', 'ACTIVE', 15, 0, '绣女路', 0, 0),
  (4142, 1, 'RN02', 'RN07', 'ACTIVE', 15, 0, '绣女路', 0, 0),
  (4143, 1, 'RN07', 'RN37', 'ACTIVE', 15, 0, '绣女路', 0, 0),
  (4144, 1, 'RN37', 'RN12', 'ACTIVE', 15, 0, '绣女路', 0, 0),
  (4145, 1, 'RN12', 'RN42', 'ACTIVE', 15, 0, '绣女路', 0, 0),
  (4146, 1, 'RN42', 'RN17', 'ACTIVE', 15, 0, '绣女路', 0, 0),
  (4147, 1, 'RN17', 'RN22', 'ACTIVE', 15, 0, '绣女路', 0, 0)
ON DUPLICATE KEY UPDATE
  `status` = VALUES(`status`), `speed_limit_kmh` = VALUES(`speed_limit_kmh`),
  `congestion_level` = VALUES(`congestion_level`), `remark` = VALUES(`remark`),
  `deleted` = VALUES(`deleted`);

-- 4.8 志远路（南北向主干 · 中轴线）RN50→RN03→RN08→RN38→RN13→RN43→RN18→RN23→RN51
INSERT INTO `t_road_segment`
  (`id`, `park_id`, `from_node_code`, `to_node_code`, `status`, `speed_limit_kmh`, `congestion_level`, `remark`, `version`, `deleted`)
VALUES
  (4148, 1, 'RN50', 'RN03', 'ACTIVE', 20, 1, '志远路', 0, 0),
  (4149, 1, 'RN03', 'RN08', 'ACTIVE', 20, 1, '志远路', 0, 0),
  (4150, 1, 'RN08', 'RN38', 'ACTIVE', 20, 1, '志远路', 0, 0),
  (4151, 1, 'RN38', 'RN13', 'ACTIVE', 20, 1, '志远路', 0, 0),
  (4152, 1, 'RN13', 'RN43', 'ACTIVE', 20, 1, '志远路', 0, 0),
  (4153, 1, 'RN43', 'RN18', 'ACTIVE', 20, 1, '志远路', 0, 0),
  (4154, 1, 'RN18', 'RN23', 'ACTIVE', 20, 1, '志远路', 0, 0),
  (4155, 1, 'RN23', 'RN51', 'ACTIVE', 20, 1, '志远路', 0, 0)
ON DUPLICATE KEY UPDATE
  `status` = VALUES(`status`), `speed_limit_kmh` = VALUES(`speed_limit_kmh`),
  `congestion_level` = VALUES(`congestion_level`), `remark` = VALUES(`remark`),
  `deleted` = VALUES(`deleted`);

-- 4.9 叠石桥路（南北向主干 · 东列）RN53→RN04→RN09→RN39→RN14→RN44→RN19→RN24
INSERT INTO `t_road_segment`
  (`id`, `park_id`, `from_node_code`, `to_node_code`, `status`, `speed_limit_kmh`, `congestion_level`, `remark`, `version`, `deleted`)
VALUES
  (4156, 1, 'RN53', 'RN04', 'ACTIVE', 20, 1, '叠石桥路', 0, 0),
  (4157, 1, 'RN04', 'RN09', 'ACTIVE', 20, 1, '叠石桥路', 0, 0),
  (4158, 1, 'RN09', 'RN39', 'ACTIVE', 20, 1, '叠石桥路', 0, 0),
  (4159, 1, 'RN39', 'RN14', 'ACTIVE', 20, 1, '叠石桥路', 0, 0),
  (4160, 1, 'RN14', 'RN44', 'ACTIVE', 20, 1, '叠石桥路', 0, 0),
  (4161, 1, 'RN44', 'RN19', 'ACTIVE', 20, 1, '叠石桥路', 0, 0),
  (4162, 1, 'RN19', 'RN24', 'ACTIVE', 20, 1, '叠石桥路', 0, 0)
ON DUPLICATE KEY UPDATE
  `status` = VALUES(`status`), `speed_limit_kmh` = VALUES(`speed_limit_kmh`),
  `congestion_level` = VALUES(`congestion_level`), `remark` = VALUES(`remark`),
  `deleted` = VALUES(`deleted`);

-- 4.10 东外环（南北向 · 东边界）RN47→RN05→RN10→RN40→RN15→RN45→RN20→RN25→RN49
INSERT INTO `t_road_segment`
  (`id`, `park_id`, `from_node_code`, `to_node_code`, `status`, `speed_limit_kmh`, `congestion_level`, `remark`, `version`, `deleted`)
VALUES
  (4163, 1, 'RN47', 'RN05', 'ACTIVE', 15, 0, '东外环', 0, 0),
  (4164, 1, 'RN05', 'RN10', 'ACTIVE', 15, 0, '东外环', 0, 0),
  (4165, 1, 'RN10', 'RN40', 'ACTIVE', 15, 0, '东外环', 0, 0),
  (4166, 1, 'RN40', 'RN15', 'ACTIVE', 15, 0, '东外环', 0, 0),
  (4167, 1, 'RN15', 'RN45', 'ACTIVE', 15, 0, '东外环', 0, 0),
  (4168, 1, 'RN45', 'RN20', 'ACTIVE', 15, 0, '东外环', 0, 0),
  (4169, 1, 'RN20', 'RN25', 'ACTIVE', 15, 0, '东外环', 0, 0),
  (4170, 1, 'RN25', 'RN49', 'ACTIVE', 15, 0, '东外环', 0, 0)
ON DUPLICATE KEY UPDATE
  `status` = VALUES(`status`), `speed_limit_kmh` = VALUES(`speed_limit_kmh`),
  `congestion_level` = VALUES(`congestion_level`), `remark` = VALUES(`remark`),
  `deleted` = VALUES(`deleted`);

-- 4.11 枢纽对角转向连接（限速 10km/h）——连接志远路/叠石桥路沿线相邻交叉口，
--      便于在园区中心枢纽(RN13)及周边交叉口做斜向转向，避免绕行路口。
INSERT INTO `t_road_segment`
  (`id`, `park_id`, `from_node_code`, `to_node_code`, `status`, `speed_limit_kmh`, `congestion_level`, `remark`, `version`, `deleted`)
VALUES
  (4171, 1, 'RN02', 'RN08', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0),
  (4172, 1, 'RN03', 'RN07', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0),
  (4173, 1, 'RN03', 'RN09', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0),
  (4174, 1, 'RN04', 'RN08', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0),
  (4175, 1, 'RN07', 'RN13', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0),
  (4176, 1, 'RN08', 'RN12', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0),
  (4177, 1, 'RN08', 'RN14', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0),
  (4178, 1, 'RN09', 'RN13', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0),
  (4179, 1, 'RN12', 'RN18', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0),
  (4180, 1, 'RN13', 'RN17', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0),
  (4181, 1, 'RN13', 'RN19', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0),
  (4182, 1, 'RN14', 'RN18', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0),
  (4183, 1, 'RN17', 'RN23', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0),
  (4184, 1, 'RN18', 'RN22', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0),
  (4185, 1, 'RN18', 'RN24', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0),
  (4186, 1, 'RN19', 'RN23', 'ACTIVE', 10, 0, '对角连接(枢纽转向)', 0, 0)
ON DUPLICATE KEY UPDATE
  `status` = VALUES(`status`), `speed_limit_kmh` = VALUES(`speed_limit_kmh`),
  `congestion_level` = VALUES(`congestion_level`), `remark` = VALUES(`remark`),
  `deleted` = VALUES(`deleted`);

-- ============================================================
-- 5. 站点 GPS 坐标复核（沿用 V37 已校准的 GCJ-02 坐标）
--    共 13 个 ZJF- 站点：取货2 / 送货4 / 快递1 / 待命1 / 充电5
--    此处仅复核坐标，确保路网切换后站点落点与真实路网一致。
-- ============================================================

-- 5.1 取货站点（门市 · 南排核心区）
UPDATE `t_station` SET
  `coord_lng` = 121.074453, `coord_lat` = 31.960396,
  `coord_x` = 225.0, `coord_y` = 694.3
WHERE `station_code` = 'ZJF-PICK-01' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.072610, `coord_lat` = 31.960726,
  `coord_x` = 46.2, `coord_y` = 652.2
WHERE `station_code` = 'ZJF-PICK-02' AND `deleted` = 0;

-- 5.2 送货站点（仓库）
UPDATE `t_station` SET
  `coord_lng` = 121.079762, `coord_lat` = 31.963627,
  `coord_x` = 513.2, `coord_y` = 107.1
WHERE `station_code` = 'ZJF-DROP-01' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.087005, `coord_lat` = 31.961780,
  `coord_x` = 1153.5, `coord_y` = 428.8
WHERE `station_code` = 'ZJF-DROP-02' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.074367, `coord_lat` = 31.963548,
  `coord_x` = 105.2, `coord_y` = 670.4
WHERE `station_code` = 'ZJF-DROP-03' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.083893, `coord_lat` = 31.962833,
  `coord_x` = 863.2, `coord_y` = 400.0
WHERE `station_code` = 'ZJF-DROP-04' AND `deleted` = 0;

-- 5.3 快递接驳点
UPDATE `t_station` SET
  `coord_lng` = 121.073200, `coord_lat` = 31.963800,
  `coord_x` = 83.6, `coord_y` = 642.0
WHERE `station_code` = 'ZJF-EXPRESS-01' AND `deleted` = 0;

-- 5.4 车辆待命区
UPDATE `t_station` SET
  `coord_lng` = 121.080055, `coord_lat` = 31.961922,
  `coord_x` = 600.0, `coord_y` = 400.0
WHERE `station_code` = 'ZJF-IDLE-01' AND `deleted` = 0;

-- 5.5 充电站（V37 仅校准 lng/lat，此处复核经纬度）
UPDATE `t_station` SET
  `coord_lng` = 121.080069, `coord_lat` = 31.961850
WHERE `station_code` = 'ZJF-CHG-01' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.079780, `coord_lat` = 31.963518
WHERE `station_code` = 'ZJF-CHG-02' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.072610, `coord_lat` = 31.963700
WHERE `station_code` = 'ZJF-CHG-03' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.074442, `coord_lat` = 31.960671
WHERE `station_code` = 'ZJF-CHG-04' AND `deleted` = 0;

UPDATE `t_station` SET
  `coord_lng` = 121.084334, `coord_lat` = 31.962890
WHERE `station_code` = 'ZJF-CHG-05' AND `deleted` = 0;

-- ============================================================
-- 完成：叠石桥试点真实路网（55 节点 / 86 路段）已上线，
--       旧示意图路网 R1~R24 已停用（保留可回溯），13 个站点坐标已复核。
-- ============================================================
