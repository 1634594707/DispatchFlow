-- Phase 15: peak cron end, automation fleet hooks

USE `fsd_core`;

ALTER TABLE `t_peak_mode_state`
  ADD COLUMN `schedule_end_cron` VARCHAR(64) DEFAULT NULL COMMENT '高峰结束 cron' AFTER `schedule_cron`,
  ADD COLUMN `last_schedule_peak_at` DATETIME DEFAULT NULL COMMENT '上次高峰 cron 触发' AFTER `schedule_end_cron`,
  ADD COLUMN `last_schedule_end_at` DATETIME DEFAULT NULL COMMENT '上次结束 cron 触发' AFTER `last_schedule_peak_at`;
