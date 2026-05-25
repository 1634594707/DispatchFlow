USE `fsd_core`;

ALTER TABLE `t_dispatch_exception_record`
    ADD COLUMN `severity` VARCHAR(16) NOT NULL DEFAULT 'WARN' COMMENT 'INFO/WARN/ERROR/CRITICAL' AFTER `exception_msg`;

ALTER TABLE `t_dispatch_exception_record`
    ADD COLUMN `resolve_action` VARCHAR(32) DEFAULT NULL COMMENT 'REASSIGN/MARK_FAILED/CLOSE/VEHICLE_OFFLINE' AFTER `resolve_remark`;
