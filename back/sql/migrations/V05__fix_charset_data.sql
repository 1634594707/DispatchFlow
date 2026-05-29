-- Fix mojibake park/station names (UTF-8 mis-import). Run with utf8mb4 client charset.

USE `fsd_core`;

SET NAMES utf8mb4;

UPDATE `t_park` SET `park_name` = '默认示范园区' WHERE `park_code` = 'DEFAULT';
UPDATE `t_park` SET `park_name` = 'B区仓储园' WHERE `park_code` = 'CAMPUS-B';

UPDATE `t_station` SET `station_name` = 'A1 Pickup' WHERE `park_id` = 1 AND `station_code` = 'A1';
UPDATE `t_station` SET `station_name` = 'A2 Pickup' WHERE `park_id` = 1 AND `station_code` = 'A2';
UPDATE `t_station` SET `station_name` = 'A3 Pickup' WHERE `park_id` = 1 AND `station_code` = 'A3';
UPDATE `t_station` SET `station_name` = 'A4 Pickup' WHERE `park_id` = 1 AND `station_code` = 'A4';
UPDATE `t_station` SET `station_name` = 'B1 Dropoff' WHERE `park_id` = 1 AND `station_code` = 'B1';
UPDATE `t_station` SET `station_name` = 'B2 Dropoff' WHERE `park_id` = 1 AND `station_code` = 'B2';
UPDATE `t_station` SET `station_name` = 'B3 Dropoff' WHERE `park_id` = 1 AND `station_code` = 'B3';
UPDATE `t_station` SET `station_name` = 'B4 Dropoff' WHERE `park_id` = 1 AND `station_code` = 'B4';

UPDATE `t_station` SET `station_name` = 'C1 取货' WHERE `park_id` = 2 AND `station_code` = 'C1';
UPDATE `t_station` SET `station_name` = 'C2 取货' WHERE `park_id` = 2 AND `station_code` = 'C2';
UPDATE `t_station` SET `station_name` = 'D1 送货' WHERE `park_id` = 2 AND `station_code` = 'D1';
UPDATE `t_station` SET `station_name` = 'D2 送货' WHERE `park_id` = 2 AND `station_code` = 'D2';
