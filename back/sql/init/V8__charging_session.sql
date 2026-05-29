USE `fsd_core`;

CREATE TABLE IF NOT EXISTS `t_charging_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `park_id` BIGINT NOT NULL COMMENT '园区ID',
  `vehicle_id` BIGINT NOT NULL COMMENT '车辆ID',
  `charging_pile_id` BIGINT DEFAULT NULL COMMENT '充电桩ID',
  `parking_slot_id` BIGINT DEFAULT NULL COMMENT '车位ID',
  `session_status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/COMPLETED',
  `start_soc` INT NOT NULL COMMENT '开始SOC',
  `end_soc` INT DEFAULT NULL COMMENT '结束SOC',
  `start_time` DATETIME NOT NULL COMMENT '开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '结束时间',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_vehicle_status` (`vehicle_id`, `session_status`),
  KEY `idx_park_start_time` (`park_id`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='充电会话表';
