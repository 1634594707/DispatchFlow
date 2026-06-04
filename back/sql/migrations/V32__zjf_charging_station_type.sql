-- V4-E1: ZJF 充电站站点类型

UPDATE `t_station`
SET `station_type` = 'CHARGING_STATION'
WHERE `station_code` LIKE 'ZJF-CHG-%'
  AND `deleted` = 0;
