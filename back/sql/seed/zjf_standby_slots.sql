-- 基地/站点待命车位 P1..P35（空闲车"回待命区"的落点）
--
-- 为什么要这一片：仿真器给空闲车挑待命点的顺序是
--   ① `ZJF-IDLE-01` 站点（设施 v2 把它连同所有 GENERAL 站点一起置了 INACTIVE ⇒ 接口不返回 ⇒ 找不到）
--   ② 回退到 `application.yml` 的 `parking-spots P1..P6`，那是**老示意图的像素坐标**
--      （x=80..200 / y=700..740，对现役 1600×1854 画布没有意义）
-- 结果 20 台车被分到 6 个凭空点上，实测有 3 台因此落在服务围栏外、且三台叠在同一个坐标。
--
-- 现在的口径分两段：
--
-- **P1..P6 = 母港桩的锚点**（下面 6 条 INSERT）。它们带着 `CP1..CP6` 六根桩
-- （`t_charging_pile.parking_slot_id` 指向这几行），所以坐标**一个都不动** —— 动一位等于搬一根桩。
--
-- **P7..P35 = 车场待命位**（最后一条 INSERT，2026-09-27 改）。原来的选点规则是
-- "以基地锚点为圆心，取最大强连通分量里的 ACTIVE 路网节点按距离升序前 35 个" —— 那取的是
-- **离基地最近的路**，不是**站场**：实测 35 个位里只有 1 个贴着设施，其余 33 个距最近设施 >100 m、
-- 中位 406 m、最远 618 m。于是空闲车沿路散开，本人两次反馈"车为什么随便停在马路上"。
-- 新规则：**每个待命位落在一个设施点的坐标上**（总仓库 + 8 个充电站），`coord_x/coord_y` 与
-- `coord_lng/coord_lat` **成对取自同一行 `t_station`**（GCJ↔像素互转只在 `ParkGeoTransformService`
-- 一处，脚本里重算必漂）；`entry/exit_node_code` 取该设施最近的路网节点，只为给管理页一个可读流线，
-- 不参与路由（代码里只有 `InfrastructureAdminServiceImpl` 读它）。
-- 分布：总仓库 6 + 基地充电站 3 + 其余 7 站（3/3/3/3/3/3/2）= 29。
--
-- ⚠ 这是地理内容，走 seed 不走迁移；灌 seed 前必须先停后端（否则仿真器每 tick 在写车位状态，
--   灌完立刻被覆盖）。P1..P6 原有的 OCCUPIED/occupied_vehicle_id 不在本文件的更新列里，不会被解开。
INSERT INTO t_parking_slot (`park_id`, `slot_code`, `slot_name`, `slot_type`, `facing_direction`, `entry_node_code`, `exit_node_code`, `blocking_main_road`, `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `status`, `occupied_vehicle_id`, `sort_order`, `remark`, `deleted`) SELECT (SELECT id FROM t_park WHERE park_code='DEFAULT'), 'P1', '基地待命位 01', 'STANDBY', NULL, 'OSM0017', 'OSM0017', 0, 550.1236, 406.6709, 121.0800810, 31.9605916, 'FREE', NULL, 1, '就近路网节点 OSM0017（距基地 0 m）', 0 FROM DUAL ON DUPLICATE KEY UPDATE `slot_name`=VALUES(`slot_name`), `slot_type`=VALUES(`slot_type`), `entry_node_code`=VALUES(`entry_node_code`), `exit_node_code`=VALUES(`exit_node_code`), `coord_x`=VALUES(`coord_x`), `coord_y`=VALUES(`coord_y`), `coord_lng`=VALUES(`coord_lng`), `coord_lat`=VALUES(`coord_lat`), `sort_order`=VALUES(`sort_order`), `remark`=VALUES(`remark`), `deleted`=0;
INSERT INTO t_parking_slot (`park_id`, `slot_code`, `slot_name`, `slot_type`, `facing_direction`, `entry_node_code`, `exit_node_code`, `blocking_main_road`, `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `status`, `occupied_vehicle_id`, `sort_order`, `remark`, `deleted`) SELECT (SELECT id FROM t_park WHERE park_code='DEFAULT'), 'P2', '基地待命位 02', 'STANDBY', NULL, 'OSM0267', 'OSM0267', 0, 536.9986, 405.8584, 121.0793900, 31.9606279, 'FREE', NULL, 2, '就近路网节点 OSM0267（距基地 66 m）', 0 FROM DUAL ON DUPLICATE KEY UPDATE `slot_name`=VALUES(`slot_name`), `slot_type`=VALUES(`slot_type`), `entry_node_code`=VALUES(`entry_node_code`), `exit_node_code`=VALUES(`exit_node_code`), `coord_x`=VALUES(`coord_x`), `coord_y`=VALUES(`coord_y`), `coord_lng`=VALUES(`coord_lng`), `coord_lat`=VALUES(`coord_lat`), `sort_order`=VALUES(`sort_order`), `remark`=VALUES(`remark`), `deleted`=0;
INSERT INTO t_parking_slot (`park_id`, `slot_code`, `slot_name`, `slot_type`, `facing_direction`, `entry_node_code`, `exit_node_code`, `blocking_main_road`, `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `status`, `occupied_vehicle_id`, `sort_order`, `remark`, `deleted`) SELECT (SELECT id FROM t_park WHERE park_code='DEFAULT'), 'P3', '基地待命位 03', 'STANDBY', NULL, 'OSMS0072', 'OSMS0072', 0, 518.0008, 376.7705, 121.0783898, 31.9619275, 'FREE', NULL, 3, '就近路网节点 OSMS0072（距基地 218 m）', 0 FROM DUAL ON DUPLICATE KEY UPDATE `slot_name`=VALUES(`slot_name`), `slot_type`=VALUES(`slot_type`), `entry_node_code`=VALUES(`entry_node_code`), `exit_node_code`=VALUES(`exit_node_code`), `coord_x`=VALUES(`coord_x`), `coord_y`=VALUES(`coord_y`), `coord_lng`=VALUES(`coord_lng`), `coord_lat`=VALUES(`coord_lat`), `sort_order`=VALUES(`sort_order`), `remark`=VALUES(`remark`), `deleted`=0;
INSERT INTO t_parking_slot (`park_id`, `slot_code`, `slot_name`, `slot_type`, `facing_direction`, `entry_node_code`, `exit_node_code`, `blocking_main_road`, `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `status`, `occupied_vehicle_id`, `sort_order`, `remark`, `deleted`) SELECT (SELECT id FROM t_park WHERE park_code='DEFAULT'), 'P4', '基地待命位 04', 'STANDBY', NULL, 'OSM0265', 'OSM0265', 0, 509.6754, 424.7961, 121.0779515, 31.9597818, 'FREE', NULL, 4, '就近路网节点 OSM0265（距基地 221 m）', 0 FROM DUAL ON DUPLICATE KEY UPDATE `slot_name`=VALUES(`slot_name`), `slot_type`=VALUES(`slot_type`), `entry_node_code`=VALUES(`entry_node_code`), `exit_node_code`=VALUES(`exit_node_code`), `coord_x`=VALUES(`coord_x`), `coord_y`=VALUES(`coord_y`), `coord_lng`=VALUES(`coord_lng`), `coord_lat`=VALUES(`coord_lat`), `sort_order`=VALUES(`sort_order`), `remark`=VALUES(`remark`), `deleted`=0;
INSERT INTO t_parking_slot (`park_id`, `slot_code`, `slot_name`, `slot_type`, `facing_direction`, `entry_node_code`, `exit_node_code`, `blocking_main_road`, `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `status`, `occupied_vehicle_id`, `sort_order`, `remark`, `deleted`) SELECT (SELECT id FROM t_park WHERE park_code='DEFAULT'), 'P5', '基地待命位 05', 'STANDBY', NULL, 'OSM0259', 'OSM0259', 0, 541.5287, 454.1191, 121.0796285, 31.9584717, 'FREE', NULL, 5, '就近路网节点 OSM0259（距基地 238 m）', 0 FROM DUAL ON DUPLICATE KEY UPDATE `slot_name`=VALUES(`slot_name`), `slot_type`=VALUES(`slot_type`), `entry_node_code`=VALUES(`entry_node_code`), `exit_node_code`=VALUES(`exit_node_code`), `coord_x`=VALUES(`coord_x`), `coord_y`=VALUES(`coord_y`), `coord_lng`=VALUES(`coord_lng`), `coord_lat`=VALUES(`coord_lat`), `sort_order`=VALUES(`sort_order`), `remark`=VALUES(`remark`), `deleted`=0;
INSERT INTO t_parking_slot (`park_id`, `slot_code`, `slot_name`, `slot_type`, `facing_direction`, `entry_node_code`, `exit_node_code`, `blocking_main_road`, `coord_x`, `coord_y`, `coord_lng`, `coord_lat`, `status`, `occupied_vehicle_id`, `sort_order`, `remark`, `deleted`) SELECT (SELECT id FROM t_park WHERE park_code='DEFAULT'), 'P6', '基地待命位 06', 'STANDBY', NULL, 'OSM0018', 'OSM0018', 0, 602.7281, 406.3173, 121.0828505, 31.9606074, 'FREE', NULL, 6, '就近路网节点 OSM0018（距基地 263 m）', 0 FROM DUAL ON DUPLICATE KEY UPDATE `slot_name`=VALUES(`slot_name`), `slot_type`=VALUES(`slot_type`), `entry_node_code`=VALUES(`entry_node_code`), `exit_node_code`=VALUES(`exit_node_code`), `coord_x`=VALUES(`coord_x`), `coord_y`=VALUES(`coord_y`), `coord_lng`=VALUES(`coord_lng`), `coord_lat`=VALUES(`coord_lat`), `sort_order`=VALUES(`sort_order`), `remark`=VALUES(`remark`), `deleted`=0;

INSERT INTO t_parking_slot (`park_id`, `slot_code`, `slot_name`, `slot_type`, `facing_direction`,
  `entry_node_code`, `exit_node_code`, `blocking_main_road`, `coord_x`, `coord_y`, `coord_lng`, `coord_lat`,
  `status`, `occupied_vehicle_id`, `sort_order`, `remark`, `deleted`)
SELECT s.park_id, m.slot_code, CONCAT('车场待命位 ', SUBSTRING(m.slot_code, 2) + 0), 'STANDBY', NULL,
  (SELECT n.node_code FROM t_road_node n
    WHERE n.park_id = s.park_id AND n.status = 'ACTIVE'
    ORDER BY POW(n.coord_x - s.coord_x, 2) + POW(n.coord_y - s.coord_y, 2) ASC LIMIT 1),
  (SELECT n.node_code FROM t_road_node n
    WHERE n.park_id = s.park_id AND n.status = 'ACTIVE'
    ORDER BY POW(n.coord_x - s.coord_x, 2) + POW(n.coord_y - s.coord_y, 2) ASC LIMIT 1),
  0, s.coord_x, s.coord_y, s.coord_lng, s.coord_lat,
  'FREE', NULL, m.sort_order, CONCAT('落在设施 ', s.station_code, ' 位内（同点位）'), 0
FROM (
  SELECT 'P7' AS slot_code, 7 AS sort_order, 'FSD-HUB-01' AS station_code
  UNION ALL
  SELECT 'P8' AS slot_code, 8 AS sort_order, 'FSD-HUB-01' AS station_code
  UNION ALL
  SELECT 'P9' AS slot_code, 9 AS sort_order, 'FSD-HUB-01' AS station_code
  UNION ALL
  SELECT 'P10' AS slot_code, 10 AS sort_order, 'FSD-HUB-01' AS station_code
  UNION ALL
  SELECT 'P11' AS slot_code, 11 AS sort_order, 'FSD-HUB-01' AS station_code
  UNION ALL
  SELECT 'P12' AS slot_code, 12 AS sort_order, 'FSD-HUB-01' AS station_code
  UNION ALL
  SELECT 'P13' AS slot_code, 13 AS sort_order, 'FSD-CHG-01' AS station_code
  UNION ALL
  SELECT 'P14' AS slot_code, 14 AS sort_order, 'FSD-CHG-01' AS station_code
  UNION ALL
  SELECT 'P15' AS slot_code, 15 AS sort_order, 'FSD-CHG-01' AS station_code
  UNION ALL
  SELECT 'P16' AS slot_code, 16 AS sort_order, 'FSD-CHG-11' AS station_code
  UNION ALL
  SELECT 'P17' AS slot_code, 17 AS sort_order, 'FSD-CHG-11' AS station_code
  UNION ALL
  SELECT 'P18' AS slot_code, 18 AS sort_order, 'FSD-CHG-11' AS station_code
  UNION ALL
  SELECT 'P19' AS slot_code, 19 AS sort_order, 'FSD-CHG-02' AS station_code
  UNION ALL
  SELECT 'P20' AS slot_code, 20 AS sort_order, 'FSD-CHG-02' AS station_code
  UNION ALL
  SELECT 'P21' AS slot_code, 21 AS sort_order, 'FSD-CHG-02' AS station_code
  UNION ALL
  SELECT 'P22' AS slot_code, 22 AS sort_order, 'FSD-CHG-12' AS station_code
  UNION ALL
  SELECT 'P23' AS slot_code, 23 AS sort_order, 'FSD-CHG-12' AS station_code
  UNION ALL
  SELECT 'P24' AS slot_code, 24 AS sort_order, 'FSD-CHG-12' AS station_code
  UNION ALL
  SELECT 'P25' AS slot_code, 25 AS sort_order, 'FSD-CHG-03' AS station_code
  UNION ALL
  SELECT 'P26' AS slot_code, 26 AS sort_order, 'FSD-CHG-03' AS station_code
  UNION ALL
  SELECT 'P27' AS slot_code, 27 AS sort_order, 'FSD-CHG-03' AS station_code
  UNION ALL
  SELECT 'P28' AS slot_code, 28 AS sort_order, 'FSD-CHG-04' AS station_code
  UNION ALL
  SELECT 'P29' AS slot_code, 29 AS sort_order, 'FSD-CHG-04' AS station_code
  UNION ALL
  SELECT 'P30' AS slot_code, 30 AS sort_order, 'FSD-CHG-04' AS station_code
  UNION ALL
  SELECT 'P31' AS slot_code, 31 AS sort_order, 'FSD-CHG-05' AS station_code
  UNION ALL
  SELECT 'P32' AS slot_code, 32 AS sort_order, 'FSD-CHG-05' AS station_code
  UNION ALL
  SELECT 'P33' AS slot_code, 33 AS sort_order, 'FSD-CHG-05' AS station_code
  UNION ALL
  SELECT 'P34' AS slot_code, 34 AS sort_order, 'FSD-CHG-06' AS station_code
  UNION ALL
  SELECT 'P35' AS slot_code, 35 AS sort_order, 'FSD-CHG-06' AS station_code
) m
JOIN t_station s ON s.station_code = m.station_code AND s.deleted = 0
ON DUPLICATE KEY UPDATE `slot_name` = VALUES(`slot_name`), `slot_type` = VALUES(`slot_type`),
  `entry_node_code` = VALUES(`entry_node_code`), `exit_node_code` = VALUES(`exit_node_code`),
  `coord_x` = VALUES(`coord_x`), `coord_y` = VALUES(`coord_y`),
  `coord_lng` = VALUES(`coord_lng`), `coord_lat` = VALUES(`coord_lat`),
  `sort_order` = VALUES(`sort_order`), `remark` = VALUES(`remark`), `deleted` = 0;

-- 自检（必须打印，否则"写了但坐标没改"看不见）：待命位距最近**设施** >100 m 的个数，期望 0。
-- 只算 MOTHERSHIP/CHARGING_STATION/SWAP_CABINET：`t_station` 里还有下单时物化出来的 GEO_POINT
-- 落点，把它们算进来会让这条自检虚绿（本机实测第一版就是这样过的：路边点位旁边恰好有个送货落点）。
SELECT CONCAT('[check] 待命位距最近设施 >100 m 的个数 = ', COUNT(*)) AS standby_slot_check FROM (
  SELECT sl.slot_code FROM t_parking_slot sl
   WHERE sl.deleted = 0 AND sl.slot_type = 'STANDBY'
     AND NOT EXISTS (
       SELECT 1 FROM t_station st
        WHERE st.park_id = sl.park_id AND st.deleted = 0
          AND st.station_type IN ('MOTHERSHIP', 'CHARGING_STATION', 'SWAP_CABINET')
          AND POW(st.coord_x - sl.coord_x, 2) + POW(st.coord_y - sl.coord_y, 2) <= 10000)
) x;
