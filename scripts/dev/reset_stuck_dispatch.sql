-- 本地演示：清理卡住的派单任务、释放车辆、关闭 OPEN 异常
-- 使用前请确认连接的是开发库，勿在生产环境直接执行。

-- 1) 取消未完结的调度任务（人工待处理 / 待派 / 已派 / 执行中）
UPDATE t_dispatch_task
SET status = 'CANCELLED',
    finish_time = NOW(),
    fail_reason_code = NULL,
    fail_reason_msg = NULL
WHERE deleted = 0
  AND status IN ('PENDING', 'MANUAL_PENDING', 'ASSIGNED', 'EXECUTING');

-- 2) 同步取消关联订单（不含已成功）
UPDATE t_order
SET status = 'CANCELLED'
WHERE deleted = 0
  AND status NOT IN ('SUCCESS', 'CANCELLED');

-- 3) 释放车辆占用
UPDATE t_vehicle
SET dispatch_status = 'IDLE',
    current_task_id = NULL,
    current_order_id = NULL
WHERE deleted = 0;

-- 4) 关闭未处理异常（仅清队列展示，任务已在上面取消）
UPDATE t_dispatch_exception_record
SET exception_status = 'RESOLVED',
    resolved_time = NOW(),
    resolve_action = 'CLOSE',
    resolve_remark = 'dev reset_stuck_dispatch'
WHERE exception_status = 'OPEN';

-- 5) 释放充电桩/车位预留（如有）
UPDATE t_parking_slot
SET status = 'FREE', occupied_vehicle_id = NULL
WHERE deleted = 0 AND occupied_vehicle_id IS NOT NULL;

UPDATE t_charging_pile
SET status = 'FREE', occupied_vehicle_id = NULL
WHERE deleted = 0 AND occupied_vehicle_id IS NOT NULL;
