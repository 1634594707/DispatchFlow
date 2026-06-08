USE `fsd_core`;

ALTER TABLE `t_dispatch_exception_record`
    ADD COLUMN `agg_count` INT NOT NULL DEFAULT 1 COMMENT '聚合计数: 同一车辆同类异常聚合次数' AFTER `resolve_action`;