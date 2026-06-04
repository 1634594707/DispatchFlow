-- V3: 找家纺 L1 试点从川姜迁至叠石桥家纺产业园（OSM 导出范围 · GCJ-02）

UPDATE `t_park`
SET
  `park_name` = '找家纺网 · 叠石桥短驳试点',
  `center_lng` = 121.080354,
  `center_lat` = 31.961977,
  `map_provider` = 'AMAP',
  `remark` = 'ZJF_DIESHIQIAO_PILOT：家纺产业园短驳演示区（约 1570m×470m）'
WHERE `park_code` = 'DEFAULT' AND `deleted` = 0;

UPDATE `t_park_geofence`
SET
  `fence_name` = '找家纺网送货区（叠石桥试点）',
  `polygon_json` = JSON_ARRAY(
    JSON_ARRAY(121.072051, 31.959885),
    JSON_ARRAY(121.088673, 31.959902),
    JSON_ARRAY(121.088674, 31.964101),
    JSON_ARRAY(121.072051, 31.964084)
  ),
  `remark` = 'V27 L1 试点围栏（GCJ-02 · OSM bbox 转换）'
WHERE `fence_code` = 'DEFAULT-BOUNDARY' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_x` = 225.0, `coord_y` = 694.3, `coord_lng` = 121.075160, `coord_lat` = 31.960424,
    `station_name` = '南通家纺城门市', `remark` = '取货 · 南通家纺城（叠石桥南排）'
WHERE `station_code` = 'ZJF-PICK-01' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_x` = 46.2, `coord_y` = 652.2, `coord_lng` = 121.072682, `coord_lat` = 31.960646,
    `station_name` = '成品展示中心门市', `remark` = '取货 · 成品展示中心'
WHERE `station_code` = 'ZJF-PICK-02' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_x` = 513.2, `coord_y` = 107.1, `coord_lng` = 121.079152, `coord_lat` = 31.963523,
    `station_name` = '找家纺代发仓', `remark` = '送货 · 园区中心代发仓'
WHERE `station_code` = 'ZJF-DROP-01' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_x` = 1153.5, `coord_y` = 428.8, `coord_lng` = 121.088022, `coord_lat` = 31.961825,
    `station_name` = '找家纺代拿仓', `remark` = '送货 · 叠石桥三期'
WHERE `station_code` = 'ZJF-DROP-02' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_x` = 105.2, `coord_y` = 670.4, `coord_lng` = 121.073500, `coord_lat` = 31.960550,
    `remark` = '仓库⇌快递网点接驳 · 西排支路'
WHERE `station_code` = 'ZJF-EXPRESS-01' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_x` = 600.0, `coord_y` = 400.0, `coord_lng` = 121.080354, `coord_lat` = 31.961977,
    `remark` = '充电/等待派单 · 试点几何中心'
WHERE `station_code` = 'ZJF-IDLE-01' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_x` = 560.0, `coord_y` = 433.5, `coord_lng` = 121.079800, `coord_lat` = 31.961800,
    `remark` = 'L1 快充 4 桩 · 待命区旁'
WHERE `station_code` = 'ZJF-CHG-01' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_x` = 466.2, `coord_y` = 149.3, `coord_lng` = 121.078500, `coord_lat` = 31.963300,
    `remark` = 'L1 快充 6 桩 · 代发仓旁'
WHERE `station_code` = 'ZJF-CHG-02' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_x` = 83.6, `coord_y` = 642.0, `coord_lng` = 121.073200, `coord_lat` = 31.960700,
    `remark` = 'L1 慢充 2 桩 · 西排接驳旁'
WHERE `station_code` = 'ZJF-CHG-03' AND `deleted` = 0;
