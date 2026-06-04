-- V30: 全量 OSM 道路吸附（GCJ-02 · 与 pilot_osm_geo.json / V4 ROADMAP §3.2 一致）
-- 保留 V29 已吸附 4 站；其余 9 站重选至道路上；历史厂内示意站停用

-- 保留（V29 · 距路 ≈0 m）— 无需变更
-- ZJF-PICK-01 · ZJF-DROP-01 · ZJF-DROP-03 · ZJF-CHG-02

UPDATE `t_station`
SET `coord_lng` = 121.072610, `coord_lat` = 31.960726,
    `coord_x` = 41.0, `coord_y` = 637.0,
    `remark` = '取货 · 成品展示中心（OSM 道路吸附）'
WHERE `station_code` = 'ZJF-PICK-02' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.087005, `coord_lat` = 31.961780,
    `coord_x` = 1080.1, `coord_y` = 437.3,
    `remark` = '送货 · 找家纺代拿仓（OSM 道路吸附）'
WHERE `station_code` = 'ZJF-DROP-02' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.083893, `coord_lat` = 31.962833,
    `coord_x` = 855.5, `coord_y` = 237.8,
    `remark` = '送货 · 东排集散仓（OSM 道路吸附）'
WHERE `station_code` = 'ZJF-DROP-04' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.072610, `coord_lat` = 31.960726,
    `coord_x` = 41.0, `coord_y` = 637.0,
    `remark` = '仓库⇌快递网点接驳（OSM 道路吸附）'
WHERE `station_code` = 'ZJF-EXPRESS-01' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.080055, `coord_lat` = 31.961922,
    `coord_x` = 578.4, `coord_y` = 410.4,
    `remark` = '充电/等待派单 · 中排道路（OSM 道路吸附）'
WHERE `station_code` = 'ZJF-IDLE-01' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.080069, `coord_lat` = 31.961850,
    `coord_x` = 579.4, `coord_y` = 424.1,
    `remark` = 'L1 快充 4 桩 · 待命区旁（OSM 道路吸附）'
WHERE `station_code` = 'ZJF-CHG-01' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.072610, `coord_lat` = 31.960726,
    `coord_x` = 41.0, `coord_y` = 637.0,
    `remark` = 'L1 慢充 2 桩 · 西排接驳旁（OSM 道路吸附）'
WHERE `station_code` = 'ZJF-CHG-03' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.074442, `coord_lat` = 31.960671,
    `coord_x` = 173.2, `coord_y` = 647.5,
    `remark` = 'L1 快充 4 桩 · 南通家纺城门市旁（OSM 道路吸附）'
WHERE `station_code` = 'ZJF-CHG-04' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.084334, `coord_lat` = 31.962890,
    `coord_x` = 887.3, `coord_y` = 227.0,
    `remark` = 'L1 快充 4 桩 · 东排主干道（OSM 道路吸附）'
WHERE `station_code` = 'ZJF-CHG-05' AND `deleted` = 0;

-- V4-W4: 历史厂内示意站停用（非 ZJF 区域 · 避免 layout API 返回满屏黄点）
UPDATE `t_station`
SET `status` = 'INACTIVE',
    `remark` = CONCAT(IFNULL(NULLIF(`remark`, ''), '厂内示意站'), ' · V30 停用')
WHERE `deleted` = 0
  AND `station_code` NOT LIKE 'ZJF-%'
  AND (`area` IS NULL OR `area` NOT IN ('ZJF'));
