-- FSD-Core MVP 初始化数据库脚本

CREATE DATABASE IF NOT EXISTS `fsd_core`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE `fsd_core`;

CREATE TABLE IF NOT EXISTS `t_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
  `external_order_no` VARCHAR(64) DEFAULT NULL COMMENT '外部订单号',
  `source_type` VARCHAR(32) NOT NULL COMMENT '订单来源',
  `biz_type` VARCHAR(32) NOT NULL COMMENT '业务类型',
  `pickup_point_id` BIGINT NOT NULL COMMENT '取货点ID',
  `dropoff_point_id` BIGINT NOT NULL COMMENT '送达点ID',
  `priority` VARCHAR(32) NOT NULL COMMENT '优先级',
  `status` VARCHAR(32) NOT NULL COMMENT '订单状态',
  `dispatch_task_id` BIGINT DEFAULT NULL COMMENT '关联调度任务ID',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_external_order_no` (`external_order_no`),
  KEY `idx_status_created_at` (`status`, `created_at`),
  KEY `idx_dispatch_task_id` (`dispatch_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='订单表';

CREATE TABLE IF NOT EXISTS `t_dispatch_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_no` VARCHAR(64) NOT NULL COMMENT '调度任务号',
  `order_id` BIGINT NOT NULL COMMENT '订单ID',
  `vehicle_id` BIGINT DEFAULT NULL COMMENT '车辆ID',
  `dispatch_type` VARCHAR(32) NOT NULL COMMENT '派单方式',
  `status` VARCHAR(32) NOT NULL COMMENT '任务状态',
  `fail_reason_code` VARCHAR(64) DEFAULT NULL COMMENT '失败原因编码',
  `fail_reason_msg` VARCHAR(255) DEFAULT NULL COMMENT '失败原因说明',
  `assign_time` DATETIME DEFAULT NULL COMMENT '派单时间',
  `start_time` DATETIME DEFAULT NULL COMMENT '执行开始时间',
  `finish_time` DATETIME DEFAULT NULL COMMENT '执行完成时间',
  `manual_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否人工处理任务',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_no` (`task_no`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_vehicle_id` (`vehicle_id`),
  KEY `idx_status_created_at` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='调度任务表';

CREATE TABLE IF NOT EXISTS `t_vehicle` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `vehicle_code` VARCHAR(64) NOT NULL COMMENT '车辆编码',
  `vehicle_name` VARCHAR(128) NOT NULL COMMENT '车辆名称',
  `vehicle_type` VARCHAR(32) DEFAULT NULL COMMENT '车辆类型',
  `online_status` VARCHAR(32) NOT NULL COMMENT '在线状态',
  `dispatch_status` VARCHAR(32) NOT NULL COMMENT '调度状态',
  `current_task_id` BIGINT DEFAULT NULL COMMENT '当前任务ID',
  `current_order_id` BIGINT DEFAULT NULL COMMENT '当前订单ID',
  `current_latitude` DECIMAL(10,6) DEFAULT NULL COMMENT '当前纬度',
  `current_longitude` DECIMAL(10,6) DEFAULT NULL COMMENT '当前经度',
  `battery_level` INT DEFAULT NULL COMMENT '电量百分比',
  `last_report_time` DATETIME DEFAULT NULL COMMENT '最后上报时间',
  `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vehicle_code` (`vehicle_code`),
  KEY `idx_online_dispatch_status` (`online_status`, `dispatch_status`),
  KEY `idx_current_task_id` (`current_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='车辆表';

CREATE TABLE IF NOT EXISTS `t_dispatch_task_operate_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` BIGINT NOT NULL COMMENT '调度任务ID',
  `operate_type` VARCHAR(32) NOT NULL COMMENT '操作类型',
  `before_status` VARCHAR(32) DEFAULT NULL COMMENT '变更前状态',
  `after_status` VARCHAR(32) DEFAULT NULL COMMENT '变更后状态',
  `operator_type` VARCHAR(32) NOT NULL COMMENT '操作人类型',
  `operator_id` VARCHAR(64) DEFAULT NULL COMMENT '操作人ID',
  `operator_name` VARCHAR(64) DEFAULT NULL COMMENT '操作人名称',
  `operate_remark` VARCHAR(255) DEFAULT NULL COMMENT '操作备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id_created_at` (`task_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='调度任务操作日志表';

CREATE TABLE IF NOT EXISTS `t_dispatch_exception_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` BIGINT DEFAULT NULL COMMENT '调度任务ID',
  `order_id` BIGINT DEFAULT NULL COMMENT '订单ID',
  `vehicle_id` BIGINT DEFAULT NULL COMMENT '车辆ID',
  `exception_type` VARCHAR(32) NOT NULL COMMENT '异常类型',
  `exception_status` VARCHAR(32) NOT NULL COMMENT '异常处理状态',
  `exception_msg` VARCHAR(255) DEFAULT NULL COMMENT '异常说明',
  `occur_time` DATETIME NOT NULL COMMENT '异常发生时间',
  `resolved_time` DATETIME DEFAULT NULL COMMENT '处理完成时间',
  `resolver_id` VARCHAR(64) DEFAULT NULL COMMENT '处理人ID',
  `resolve_remark` VARCHAR(255) DEFAULT NULL COMMENT '处理备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_exception_type_status` (`exception_type`, `exception_status`),
  KEY `idx_occur_time` (`occur_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='调度异常记录表';
