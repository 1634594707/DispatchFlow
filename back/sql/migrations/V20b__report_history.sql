-- V20b: Add report history table for V5-A5 historical report browsing

USE `fsd_core`;

CREATE TABLE IF NOT EXISTS `t_report_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `report_type` VARCHAR(32) NOT NULL COMMENT 'PDF / CSV',
  `report_name` VARCHAR(256) NOT NULL COMMENT '文件名/报表名称',
  `dataset` VARCHAR(64) DEFAULT NULL COMMENT 'CSV dataset 类型',
  `period` VARCHAR(16) DEFAULT NULL COMMENT '时间周期',
  `date` DATE DEFAULT NULL COMMENT 'PDF 日报日期',
  `park_id` BIGINT DEFAULT NULL COMMENT '园区 ID',
  `file_size_bytes` BIGINT DEFAULT NULL COMMENT '文件大小',
  `generated_by` VARCHAR(64) DEFAULT NULL COMMENT '生成人',
  `generated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  PRIMARY KEY (`id`),
  KEY `idx_generated_at` (`generated_at` DESC),
  KEY `idx_report_type` (`report_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报表历史记录';