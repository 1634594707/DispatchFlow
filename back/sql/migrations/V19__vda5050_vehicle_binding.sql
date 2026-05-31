-- Phase 15.2: VDA5050 MQTT vehicle binding (manufacturer / serialNumber / interface)

USE `fsd_core`;

ALTER TABLE `t_vehicle`
    ADD COLUMN `vda_manufacturer` VARCHAR(64) DEFAULT NULL COMMENT 'VDA5050 manufacturer' AFTER `link_mode`,
    ADD COLUMN `vda_serial_number` VARCHAR(64) DEFAULT NULL COMMENT 'VDA5050 serialNumber' AFTER `vda_manufacturer`,
    ADD COLUMN `vda_interface_name` VARCHAR(32) NOT NULL DEFAULT 'uagv/v2' COMMENT 'VDA5050 interfaceName' AFTER `vda_serial_number`;

CREATE INDEX `idx_vda_identity` ON `t_vehicle` (`vda_manufacturer`, `vda_serial_number`);

INSERT INTO `t_vehicle` (
  `vehicle_code`, `vehicle_name`, `vehicle_type`, `link_mode`,
  `vda_manufacturer`, `vda_serial_number`, `vda_interface_name`,
  `online_status`, `dispatch_status`,
  `current_latitude`, `current_longitude`, `battery_level`,
  `last_report_time`, `remark`, `version`, `deleted`
)
SELECT 'VDA5050-001', 'VDA5050 Demo AGV', 'AGV', 'VDA5050',
       'DispatchFlow', 'AGV-001', 'uagv/v2',
       'OFFLINE', 'IDLE', 100.000000, 120.000000, 90,
       NOW(), 'phase15-vda5050-demo', 0, 0
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `t_vehicle` WHERE `vehicle_code` = 'VDA5050-001' AND `deleted` = 0);
