-- 设施模型 v2（本人 2026-09-24 看图后指定）：**一个总的发货仓库 + 分布在地图上的充电/换电点**
-- 生成与判据见《已完成工作记录》§13.107。
--
-- ⚠ 三件事按顺序，不能倒：本文件在 zjf_geo.sql（它把 18 个散点写成 ACTIVE）之后灌。
-- ⚠ 散点是 **置 INACTIVE**，不是 DELETE：`t_order` 里 996 条历史订单的 pickup_point_id/dropoff_point_id
--    仍要能解析出站名；且 `/api/admin/park/stations` 只返回 ACTIVE ⇒ 置灰就等于从地图和下拉里消失。
-- ⚠ 与 W4-b 的关系：W4-b（同日）刚把 ZJF-CHG-06..09 撤成 INACTIVE，理由是"换电柜替代近场 40 位"。
--    本人现在要求地图上有"充电的地方" ⇒ 这里复活 **02..05 这 4 个既有站**作为**低频兜底/长停充电**，
--    主补能仍是 35 个换电柜。06..09（那笔 40 位预算）**保持撤桩**，不随本条回滚。

-- 1) 唯一的发货仓库：落在叠石桥基地节点 OSM0017（与 trip_mileage_sampler 的基地同一点）
INSERT INTO t_station (`park_id`, `station_code`, `station_name`, `station_type`, `coord_x`, `coord_y`,
  `coord_lng`, `coord_lat`, `area`, `status`, `sort_order`, `capacity_limit`, `service_hours`,
  `avg_service_seconds`, `anchor_node_code`, `service_direction`, `allowed_vehicle_types`,
  `unreachable_reason`, `unreachable_until`, `station_confidence`, `remark`, `deleted`)
SELECT (select id from t_park where park_code='DEFAULT'), 'FSD-HUB-01', '总发货仓库（叠石桥基地）', 'MOTHERSHIP',
  '550.1236', '406.6709', '121.080081', '31.960592', 'ZJF', 'ACTIVE', '0', '200', '00:00-24:00', '210',
  'OSM0017', 'BIDIRECTIONAL', NULL, NULL, NULL, 'C',
  '设施模型 v2：全图唯一发货点。取货固定在此，送货由用户任意点下单（V64 坐标入口）决定。', 0
FROM DUAL ON DUPLICATE KEY UPDATE `station_name`=VALUES(`station_name`), `station_type`=VALUES(`station_type`),
  `coord_x`=VALUES(`coord_x`), `coord_y`=VALUES(`coord_y`), `coord_lng`=VALUES(`coord_lng`),
  `coord_lat`=VALUES(`coord_lat`), `anchor_node_code`=VALUES(`anchor_node_code`),
  `capacity_limit`=VALUES(`capacity_limit`), `remark`=VALUES(`remark`), `status`='ACTIVE', `deleted`=0;

-- 2) 散落的取货/送货/通用点退场（保留行，只为让历史订单可解析）
UPDATE t_station
   SET status='INACTIVE',
       remark=CONCAT(IFNULL(remark,''),' [设施模型v2 退场：发货点收敛到 FSD-HUB-01]')
 WHERE deleted=0 AND station_type IN ('PICKUP','DROPOFF','GENERAL')
   AND status='ACTIVE'
   AND (remark IS NULL OR remark NOT LIKE '%设施模型v2 退场%');

-- 3) 旧的 `ZJF-CHG-*` 全部退场，充电层交给 `zjf_charging_points.sql` 铺的 6 个点。
--    ⚠ 这里连着三次转向，如实记下：W4-b 撤 06..09（理由"换电柜替代近场 40 位"）→ 本人要求图上有充电点，
--    我一度复活 02..05 → 本人随后要求"分布"，而 02..05 全挤在老的 3.2 km² 核心区，铺不开也不该铺。
--    现在统一由同一把工具（farthest-point sampling，与换电柜同一套规则）按 k=6 重铺，旧的 9 个一并退场。
--    保留行不删：`t_charging_pile`/历史充电会话可能仍指向它们，删行会把历史扯断。
UPDATE t_station
   SET status='INACTIVE',
       remark=CONCAT(IFNULL(remark,''),' [设施模型v2：充电层改由 FSD-CHG-* 散布点接管]')
 WHERE deleted=0 AND station_type='CHARGING_STATION' AND station_code LIKE 'ZJF-CHG-%'
   AND status='ACTIVE'
   AND (remark IS NULL OR remark NOT LIKE '%设施模型v2%');
