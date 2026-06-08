USE `fsd_core`;

ALTER TABLE `t_dispatch_exception_record`
  ADD COLUMN `agg_count` INT NOT NULL DEFAULT 1 COMMENT 'Aggregated duplicate exception count' AFTER `severity`;
