-- V46: Correct the three base vehicles after V45 exposed the legacy XY-to-geo fallback.

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

UPDATE `t_parking_slot`
SET `status` = 'FREE', `occupied_vehicle_id` = NULL, `updated_at` = NOW()
WHERE `park_id` = 1 AND `deleted` = 0;

UPDATE `t_charging_pile`
SET `status` = 'FREE',
    `occupied_vehicle_id` = NULL,
    `reservation_state` = 'FREE',
    `estimated_release_at` = NULL,
    `updated_at` = NOW()
WHERE `park_id` = 1 AND `deleted` = 0;
