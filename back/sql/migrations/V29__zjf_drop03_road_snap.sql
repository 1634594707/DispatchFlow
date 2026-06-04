-- V29: 站点坐标吸附至 OSM 道路（不穿建筑面 · GCJ-02）

UPDATE `t_station`
SET `coord_lng` = 121.074367, `coord_lat` = 31.963548,
    `coord_x` = 83.6, `coord_y` = 107.1,
    `remark` = '送货 · 西排北仓（OSM 道路吸附）'
WHERE `station_code` = 'ZJF-DROP-03' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.079780, `coord_lat` = 31.963518,
    `coord_x` = 466.2, `coord_y` = 149.3,
    `remark` = 'L1 快充 6 桩 · 代发仓旁（OSM 道路吸附）'
WHERE `station_code` = 'ZJF-CHG-02' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.074453, `coord_lat` = 31.960396,
    `coord_x` = 225.1, `coord_y` = 694.3,
    `remark` = '取货 · 南通家纺城（OSM 道路吸附）'
WHERE `station_code` = 'ZJF-PICK-01' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.079762, `coord_lat` = 31.963627,
    `coord_x` = 513.2, `coord_y` = 107.1,
    `remark` = '送货 · 园区中心代发仓（OSM 道路吸附）'
WHERE `station_code` = 'ZJF-DROP-01' AND `deleted` = 0;
