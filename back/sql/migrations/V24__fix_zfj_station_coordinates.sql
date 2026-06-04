-- V3 M8-R5: 修复 ZJF 种子站点坐标 — 对齐真实道路网格
-- 原坐标（V23）是从示意图 x/y 线性映射到 GCJ-02，未验证是否在道路上
-- 新坐标全部位于模拟道路网格的交叉口/路段上：
--
--   纬一路 (lat=31.9142)  EXPRESS-01 ─── ╳ ──── DROP-01 ──── DROP-02
--   金川大道 (lat=31.9125)            ╳ ──── IDLE-01 ── ╳
--   南海路 (lat=31.9110)              PICK-01 ── ╳ ──── PICK-02
--                        经一路     经二路   经三路   经四路
--                        (121.0585) (121.0605) (121.0625) (121.0655)

UPDATE `t_station`
SET `coord_lng` = 121.060500, `coord_lat` = 31.911000, `remark` = '找家纺试点取货门市 · 南海路×经二路'
WHERE `station_code` = 'ZJF-PICK-01' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.065500, `coord_lat` = 31.911000, `remark` = '找家纺试点取货门市 · 南海路×经四路'
WHERE `station_code` = 'ZJF-PICK-02' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.061200, `coord_lat` = 31.914200, `remark` = '主送货点 · 纬一路（经二路~经三路之间）'
WHERE `station_code` = 'ZJF-DROP-01' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.064000, `coord_lat` = 31.914200, `remark` = '辅送货点 · 纬一路（经三路~经四路之间）'
WHERE `station_code` = 'ZJF-DROP-02' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.058500, `coord_lat` = 31.914000, `remark` = '仓库⇌快递网点接驳 · 纬一路×经一路'
WHERE `station_code` = 'ZJF-EXPRESS-01' AND `deleted` = 0;

UPDATE `t_station`
SET `coord_lng` = 121.062500, `coord_lat` = 31.912500, `remark` = '充电/等待派单 · 金川大道×经三路'
WHERE `station_code` = 'ZJF-IDLE-01' AND `deleted` = 0;