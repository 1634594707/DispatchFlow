-- 配送区域字段
ALTER TABLE `t_vehicle` ADD COLUMN `delivery_zone` VARCHAR(32) DEFAULT 'BOTH' COMMENT '配送区域: GEO_DELIVERY/SCHEMATIC/BOTH';
ALTER TABLE `t_vehicle` ADD COLUMN `max_load_capacity` INT DEFAULT NULL COMMENT '最大载重(kg)';
ALTER TABLE `t_vehicle` ADD COLUMN `current_load` INT DEFAULT 0 COMMENT '当前载重(kg)';

ALTER TABLE `t_order` ADD COLUMN `delivery_zone` VARCHAR(32) DEFAULT NULL COMMENT '配送区域';
ALTER TABLE `t_order` ADD COLUMN `weight` DECIMAL(10,2) DEFAULT NULL COMMENT '货物重量(kg)';

ALTER TABLE `t_station` ADD COLUMN `delivery_zone` VARCHAR(32) DEFAULT 'GENERAL' COMMENT '配送区域: GEO_DELIVERY/SCHEMATIC/GENERAL';

-- 数据初始化：按编码前缀批量更新
UPDATE `t_vehicle` SET `delivery_zone` = 'GEO_DELIVERY' WHERE `vehicle_code` LIKE 'ZJF-AV-%';
UPDATE `t_vehicle` SET `delivery_zone` = 'SCHEMATIC' WHERE `vehicle_code` LIKE 'PARK-%';
UPDATE `t_station` SET `delivery_zone` = 'GEO_DELIVERY' WHERE `station_code` LIKE 'ZJF-%';
UPDATE `t_station` SET `delivery_zone` = 'SCHEMATIC' WHERE `station_code` NOT LIKE 'ZJF-%' AND `station_code` IS NOT NULL;
