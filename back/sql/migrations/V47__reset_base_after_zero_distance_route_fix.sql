-- V47: Restore base vehicles after zero-distance charging routes polluted schematic XY coordinates.

USE `fsd_core`;

UPDATE `t_vehicle`
SET `current_longitude` = 668.4370,
    `current_latitude` = 624.4500,
    `current_task_id` = NULL,
    `current_order_id` = NULL,
    `online_status` = 'ONLINE',
    `dispatch_status` = 'IDLE',
    `current_speed_kmh` = 0,
    `current_heading` = 74,
    `last_report_time` = NOW(),
    `updated_at` = NOW()
WHERE `vehicle_code` LIKE 'ZJF-AV-%' AND `deleted` = 0;
