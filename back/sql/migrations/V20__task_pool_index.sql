-- Phase 15.3: task pool query indexes (workbench PENDING / MANUAL_PENDING)

USE `fsd_core`;

ALTER TABLE `t_dispatch_task`
    ADD KEY `idx_task_pool_status` (`deleted`, `status`, `updated_at`);
