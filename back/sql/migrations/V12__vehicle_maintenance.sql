-- Vehicle maintenance records (Phase 5.3)
-- Prerequisite: V01__init_schema.sql (t_vehicle)

USE `fsd_core`;

CREATE TABLE IF NOT EXISTS `t_vehicle_maintenance` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `vehicle_id` BIGINT NOT NULL COMMENT '车辆ID',
  `maintenance_type` VARCHAR(32) NOT NULL COMMENT '维护类型: ROUTINE/REPAIR/INSPECTION',
  `description` VARCHAR(512) NOT NULL COMMENT '维护描述',
  `maintenance_at` DATETIME NOT NULL COMMENT '维护时间',
  `operator_name` VARCHAR(64) DEFAULT NULL COMMENT '操作人',
  `status` VARCHAR(32) NOT NULL DEFAULT 'COMPLETED' COMMENT '状态: PLANNED/COMPLETED',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  KEY `idx_vehicle_maintenance_at` (`vehicle_id`, `maintenance_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='车辆维护记录表';
