# FSD-Core MVP 调度域任务文档

## 1. 文档目标

本文档用于冻结 `fsd-dispatch` 在 MVP 阶段的需求边界、接口设计、状态流转、数据设计和实施任务，作为后续开发、联调和测试的执行基线。

## 2. 需求整理

### 2.1 目标范围

调度域 MVP 需要覆盖以下能力：

1. 订单进入待调度后，系统可以自动尝试派单
2. 自动派单失败后，任务进入人工待处理
3. 调度员可以对待处理任务执行人工派单
4. 车辆执行过程中可以上报状态，驱动任务和订单状态推进
5. 调度员可以查看任务态势、失败原因和异常记录
6. 所有关键状态迁移可追踪、可审计、可排查

### 2.2 非目标范围

当前 MVP 不做以下复杂能力：

1. 不做多任务拼单
2. 不做一个订单拆分多个主调度任务
3. 不做执行中任务改派
4. 不做复杂路径规划和智能优化算法
5. 不做 Redis 或 RabbitMQ 作为业务最终真相

### 2.3 关键业务规则

1. 一个订单只对应一个主调度任务
2. 订单进入调度域后，必须先存在 `DispatchTask`
3. 自动派单只允许从在线且可调度的车辆中选车
4. `OFFLINE` 或 `UNAVAILABLE` 车辆不得参与自动派单
5. 自动派单失败后，任务默认进入 `MANUAL_PENDING`
6. 执行中任务默认不允许改派
7. 所有状态流转必须通过 Service 层校验
8. MySQL 是最终业务真相源
9. Redis 仅用于幂等、短锁、缓存
10. RabbitMQ 仅用于异步事件通知和解耦，不承担状态真相

## 3. 模块职责

### 3.1 fsd-order

- 负责订单创建和订单查询
- 负责订单状态变更
- 对外提供订单状态推进能力

### 3.2 fsd-dispatch

- 负责调度任务创建
- 负责自动派单
- 负责人工派单
- 负责调度任务状态流转
- 负责调度异常记录和操作日志记录

### 3.3 fsd-vehicle

- 负责车辆在线状态维护
- 负责车辆调度状态维护
- 负责车辆执行状态回传入口
- 对外提供车辆可用性校验能力

### 3.4 fsd-admin-api

- 提供调度员视角的聚合接口
- 提供任务态势、异常列表、人工派单操作入口

### 3.5 fsd-bootstrap

- 负责统一装配、配置加载、异常处理、组件集成

## 4. 核心状态流转

### 4.1 订单状态

主链路：

`CREATED -> WAITING_DISPATCH -> DISPATCHED -> IN_PROGRESS -> COMPLETED`

异常链路：

- `WAITING_DISPATCH -> FAILED`
- `DISPATCHED -> FAILED`
- `IN_PROGRESS -> FAILED`
- `CREATED -> CANCELLED`
- `WAITING_DISPATCH -> CANCELLED`

约束：

1. 订单创建成功后应尽快推进到 `WAITING_DISPATCH`
2. 只有调度任务成功分配车辆后，订单才能进入 `DISPATCHED`
3. 只有车辆开始执行后，订单才能进入 `IN_PROGRESS`
4. 只有任务执行成功后，订单才能进入 `COMPLETED`

### 4.2 调度任务状态

主链路：

`PENDING -> ASSIGNING -> ASSIGNED -> EXECUTING -> SUCCESS`

异常链路：

- `ASSIGNING -> MANUAL_PENDING`
- `ASSIGNING -> FAILED`
- `ASSIGNED -> FAILED`
- `EXECUTING -> FAILED`
- `PENDING -> CANCELLED`
- `MANUAL_PENDING -> ASSIGNED`
- `MANUAL_PENDING -> CANCELLED`

约束：

1. 订单进入待调度时，先创建 `PENDING` 任务
2. 自动派单开始时，任务进入 `ASSIGNING`
3. 选车并落库成功后，任务进入 `ASSIGNED`
4. 车辆开始执行后，任务进入 `EXECUTING`
5. 自动派单失败默认进入 `MANUAL_PENDING`
6. 执行中任务不允许改派

### 4.3 车辆状态

在线状态：

- `ONLINE`
- `OFFLINE`

调度状态：

- `IDLE`
- `BUSY`
- `UNAVAILABLE`

约束：

1. 自动派单只可选择 `ONLINE + IDLE`
2. 任务分配成功后，车辆置为 `BUSY`
3. 任务完成或失败回收后，车辆恢复为 `IDLE` 或 `UNAVAILABLE`
4. 车辆离线时不得新接自动派单任务

## 5. 核心业务闭环设计

### 5.1 自动派单流程

1. 订单进入 `WAITING_DISPATCH`
2. 创建主调度任务，初始状态为 `PENDING`
3. 调度服务发起自动派单，任务改为 `ASSIGNING`
4. 查询可用车辆候选集
5. 过滤 `OFFLINE`、`BUSY`、`UNAVAILABLE` 车辆
6. 选中一辆车并进行状态校验
7. 在同一事务内更新：
   - `t_dispatch_task.vehicle_id`
   - `t_dispatch_task.status = ASSIGNED`
   - `t_order.dispatch_task_id`
   - `t_order.status = DISPATCHED`
   - `t_vehicle.current_task_id`
   - `t_vehicle.current_order_id`
   - `t_vehicle.dispatch_status = BUSY`
8. 写入操作日志
9. 发送派单成功事件
10. 若无可用车或竞争失败，任务进入 `MANUAL_PENDING`

### 5.2 人工派单流程

1. 调度员查询 `MANUAL_PENDING` 任务
2. 调度员指定车辆执行人工派单
3. 服务层再次校验任务状态和车辆可用性
4. 在同一事务内完成任务、订单、车辆状态联动更新
5. 写入操作日志，记录操作人和备注
6. 发送人工派单成功事件

### 5.3 车辆执行状态回传流程

1. 车辆上报在线状态、执行状态、位置、电量、任务进度
2. 服务层先做幂等校验
3. 更新车辆最新状态
4. 若上报表示开始执行，则：
   - `DispatchTask -> EXECUTING`
   - `Order -> IN_PROGRESS`
5. 若上报表示执行成功，则：
   - `DispatchTask -> SUCCESS`
   - `Order -> COMPLETED`
   - `Vehicle.dispatch_status -> IDLE`
6. 若上报表示执行失败，则：
   - `DispatchTask -> FAILED`
   - `Order -> FAILED`
   - 记录异常
   - 车辆状态回收
7. 写入操作日志和异常记录

### 5.4 异常处理流程

1. 自动派单无车可用
2. 车辆离线导致任务不可执行
3. 车辆执行失败
4. 车辆长时间未回传导致任务超时
5. 调度员人工介入处理

异常处理动作：

- 记录 `t_dispatch_exception_record`
- 必要时将任务转为 `MANUAL_PENDING`
- 写入 `t_dispatch_task_operate_log`
- 保证订单、任务、车辆状态最终一致

## 6. 接口设计

### 6.1 调度域内部接口

#### 6.1.1 创建调度任务

- 方法：`POST /api/dispatch/tasks`
- 用途：为待调度订单创建主调度任务

请求 DTO：

- `orderId`
- `dispatchType`
- `remark`

响应 VO：

- `taskId`
- `taskNo`
- `status`

#### 6.1.2 自动派单

- 方法：`POST /api/dispatch/tasks/{taskId}/auto-assign`
- 用途：触发某个调度任务自动派单

响应 VO：

- `taskId`
- `status`
- `vehicleId`
- `message`

#### 6.1.3 人工派单

- 方法：`POST /api/dispatch/tasks/{taskId}/manual-assign`
- 用途：调度员手动指定车辆派单

请求 DTO：

- `vehicleId`
- `operatorId`
- `operatorName`
- `remark`

响应 VO：

- `taskId`
- `status`
- `vehicleId`
- `assignTime`

#### 6.1.4 查询任务详情

- 方法：`GET /api/dispatch/tasks/{taskId}`

响应 VO：

- `taskId`
- `taskNo`
- `orderId`
- `vehicleId`
- `dispatchType`
- `status`
- `failReasonCode`
- `failReasonMsg`
- `assignTime`
- `startTime`
- `finishTime`
- `manualFlag`
- `retryCount`

#### 6.1.5 查询人工待处理列表

- 方法：`GET /api/dispatch/tasks/manual-pending`

查询参数：

- `pageNo`
- `pageSize`
- `orderNo`
- `taskNo`

#### 6.1.6 查询任务态势摘要

- 方法：`GET /api/dispatch/summary`

响应 VO：

- `pendingCount`
- `assigningCount`
- `manualPendingCount`
- `executingCount`
- `failedCount`

### 6.2 车辆状态回传接口

#### 6.2.1 车辆执行状态回传

- 方法：`POST /api/vehicles/reports`

请求 DTO：

- `vehicleCode`
- `onlineStatus`
- `dispatchStatus`
- `taskId`
- `orderId`
- `reportType`
- `reportTime`
- `latitude`
- `longitude`
- `batteryLevel`
- `resultCode`
- `resultMessage`

说明：

`reportType` 建议包括：

- `ONLINE`
- `OFFLINE`
- `START_EXECUTE`
- `TASK_SUCCESS`
- `TASK_FAILED`
- `HEARTBEAT`

### 6.3 异常处理接口

#### 6.3.1 查询异常列表

- 方法：`GET /api/dispatch/exceptions`

#### 6.3.2 异常人工处理

- 方法：`POST /api/dispatch/exceptions/{id}/resolve`

请求 DTO：

- `resolverId`
- `resolverName`
- `action`
- `remark`

`action` 建议包括：

- `RETRY_ASSIGN`
- `MANUAL_ASSIGN`
- `CANCEL_TASK`
- `MARK_FAILED`

## 7. 数据设计建议

### 7.1 t_dispatch_task

建议字段：

- `id`
- `task_no`
- `order_id`
- `vehicle_id`
- `dispatch_type`
- `status`
- `fail_reason_code`
- `fail_reason_msg`
- `assign_time`
- `start_time`
- `finish_time`
- `manual_flag`
- `retry_count`
- `remark`
- `created_at`
- `updated_at`
- `version`
- `deleted`

建议索引：

- `uk_task_no(task_no)`
- `uk_order_id(order_id)` 用于保证一个订单只有一个主任务
- `idx_vehicle_id(vehicle_id)`
- `idx_status_created_at(status, created_at)`

### 7.2 t_dispatch_task_operate_log

建议补充规范字段：

- `operate_type`
- `before_status`
- `after_status`
- `operator_type`
- `operator_id`
- `operator_name`
- `operate_remark`

建议操作类型：

- `CREATE_TASK`
- `AUTO_ASSIGN`
- `MANUAL_ASSIGN`
- `START_EXECUTE`
- `FINISH_SUCCESS`
- `FINISH_FAILED`
- `ENTER_MANUAL_PENDING`
- `CANCEL_TASK`

### 7.3 t_dispatch_exception_record

建议补充规范字段：

- `exception_type`
- `exception_status`
- `exception_msg`
- `occur_time`
- `resolved_time`
- `resolver_id`
- `resolve_remark`

建议异常类型：

- `AUTO_ASSIGN_NO_VEHICLE`
- `AUTO_ASSIGN_CONFLICT`
- `VEHICLE_OFFLINE`
- `TASK_EXECUTE_FAILED`
- `TASK_TIMEOUT`

建议异常状态：

- `OPEN`
- `PROCESSING`
- `RESOLVED`
- `CLOSED`

### 7.4 t_vehicle

为支持调度，车辆表至少要稳定维护：

- `online_status`
- `dispatch_status`
- `current_task_id`
- `current_order_id`
- `current_latitude`
- `current_longitude`
- `battery_level`
- `last_report_time`

## 8. 服务层设计建议

### 8.1 DispatchTaskService

建议职责：

- 创建任务
- 自动派单
- 人工派单
- 任务详情查询
- 任务态势统计

### 8.2 DispatchTaskStateService

建议职责：

- 封装任务状态迁移校验
- 统一管理 `PENDING / ASSIGNING / ASSIGNED / EXECUTING / SUCCESS / FAILED / MANUAL_PENDING / CANCELLED` 转换规则

### 8.3 DispatchExceptionService

建议职责：

- 异常记录创建
- 异常处理流转
- 异常列表查询

### 8.4 VehicleReportService

建议职责：

- 处理车辆状态回传
- 幂等校验
- 任务推进
- 订单推进
- 车辆状态回收

### 8.5 OperateLogService

建议职责：

- 记录关键操作日志
- 补充操作者、前后状态、备注

## 9. 事务、Redis、RabbitMQ、幂等、锁风险

### 9.1 事务风险

风险：

1. 派单时订单、任务、车辆跨表更新，若不在一个事务内容易出现脏状态
2. 事务外发 MQ 事件会出现“数据库成功但消息失败”问题

建议：

1. 派单成功和任务执行终态推进必须单事务提交
2. 事件发送建议采用本地消息表或至少显式补偿机制

### 9.2 Redis 风险

风险：

1. Redis 缓存与 MySQL 状态不一致
2. 锁超时导致并发重复派单
3. 用 Redis 状态替代数据库判断会造成真相漂移

建议：

1. Redis 仅作辅助，最终状态必须回源 MySQL 校验
2. 锁只保护短临界区，核心校验仍依赖数据库状态和唯一约束
3. 缓存失效不影响主业务真相

### 9.3 RabbitMQ 风险

风险：

1. 重复消费导致重复推进状态
2. 消费失败导致事件丢失或积压
3. 先发消息后落库会造成下游读不到真实状态

建议：

1. 消费端必须幂等
2. 失败重试、死信和人工补偿路径要提前设计
3. 业务真相先落 MySQL，再异步发事件

### 9.4 幂等风险

风险：

1. 车辆重复上报 `TASK_SUCCESS` 或 `TASK_FAILED`
2. 调度员重复点击人工派单
3. 自动派单重试导致重复绑定车辆

建议：

1. 车辆回传使用 `vehicleCode + reportType + reportTime` 或业务流水做幂等键
2. 人工派单接口对任务终态和车辆占用状态做二次校验
3. 为订单主任务建立唯一约束

### 9.5 锁与并发风险

风险：

1. 多个线程同时为同一任务自动派单
2. 多个任务同时竞争同一辆空闲车
3. 人工派单和车辆回传并发造成状态覆盖

建议：

1. 任务维度加短锁
2. 车辆占用校验必须基于数据库当前状态
3. 必要时使用乐观锁字段 `version`

## 10. 高风险逻辑测试建议

### 10.1 自动派单

- [ ] 测试在线且空闲车辆可以被成功分配
- [ ] 测试离线车辆不会进入候选集
- [ ] 测试忙碌车辆不会进入候选集
- [ ] 测试无可用车辆时任务进入 `MANUAL_PENDING`
- [ ] 测试并发自动派单不会为同一订单创建多个主任务
- [ ] 测试并发竞争同一车辆时只有一个任务分配成功

### 10.2 人工派单

- [ ] 测试 `MANUAL_PENDING` 任务可以人工派单成功
- [ ] 测试非 `MANUAL_PENDING` 任务不能人工派单
- [ ] 测试人工指定离线车辆时被拒绝
- [ ] 测试人工重复提交不会重复分配

### 10.3 车辆回传

- [ ] 测试 `START_EXECUTE` 推进任务到 `EXECUTING`、订单到 `IN_PROGRESS`
- [ ] 测试 `TASK_SUCCESS` 推进任务到 `SUCCESS`、订单到 `COMPLETED`
- [ ] 测试 `TASK_FAILED` 推进任务到 `FAILED`、订单到 `FAILED`
- [ ] 测试重复回传具备幂等性
- [ ] 测试无效状态迁移被拒绝

### 10.4 异常处理

- [ ] 测试自动派单失败会创建异常记录
- [ ] 测试车辆离线导致异常时任务进入人工待处理
- [ ] 测试异常处理完成后记录 `resolved_time` 和处理备注

## 11. 实施任务清单

### 11.1 数据层

- [x] 为 `t_dispatch_task.order_id` 增加唯一约束，保证一个订单一个主任务
- [x] 校验并补充 `t_dispatch_task` 索引
- [ ] 校验并补充 `t_dispatch_task_operate_log` 字段和索引
- [x] 校验并补充 `t_dispatch_exception_record` 字段和索引
- [x] 补充车辆表调度相关索引

### 11.2 fsd-dispatch 模块

- [x] 新增 `DispatchTaskEntity`
- [x] 新增 `DispatchTaskOperateLogEntity`
- [x] 新增 `DispatchExceptionRecordEntity`
- [x] 新增对应 `Mapper`
- [x] 新增 `DispatchTaskService`
- [x] 新增 `DispatchTaskStateService`
- [x] 新增 `DispatchExceptionService`
- [x] 新增自动派单 Service 实现
- [x] 新增人工派单 Service 实现
- [x] 新增任务详情查询 Service
- [x] 新增任务态势统计 Service

### 11.3 fsd-vehicle 模块

- [x] 新增车辆查询和可用性校验 Service
- [x] 新增车辆状态回传 DTO 和 VO
- [x] 新增 `VehicleReportService`
- [x] 实现车辆在线状态更新
- [x] 实现车辆执行状态推进
- [x] 实现任务完成后的车辆状态回收

### 11.4 fsd-order 模块

- [x] 补充订单状态流转 Service
- [x] 将订单状态推进收敛到 Service 校验
- [ ] 对 `DISPATCHED / IN_PROGRESS / COMPLETED / FAILED / CANCELLED` 增加显式迁移规则

### 11.5 Controller 接口

- [x] 新增创建调度任务接口
- [x] 新增自动派单接口
- [x] 新增人工派单接口
- [x] 新增任务详情接口
- [x] 新增人工待处理列表接口
- [x] 新增任务态势摘要接口
- [x] 新增车辆回传接口
- [x] 新增异常列表接口
- [x] 新增异常处理接口

### 11.6 Redis、RabbitMQ 与幂等

- [x] 设计任务维度短锁 Key
- [x] 设计车辆回传幂等 Key
- [x] 设计调度事件模型
- [x] 设计消费端幂等策略
- [x] 设计失败重试和补偿方案

### 11.7 日志与排障

- [ ] 统一关键操作日志结构
- [ ] 明确 traceId 透传方案
- [ ] 记录状态迁移前后值
- [ ] 记录派单失败原因码和原因说明

### 11.8 测试

- [x] 为自动派单补充单元测试
- [x] 为人工派单补充单元测试
- [x] 为车辆回传补充单元测试
- [x] 为异常处理补充单元测试
- [x] 为主链路补充集成测试
- [x] 为高并发派单补充并发测试

## 12. 实施顺序建议

1. 先补齐 `DispatchTask / Exception / OperateLog` 数据模型
2. 再实现任务创建和自动派单
3. 再实现人工派单和任务态势接口
4. 再实现车辆状态回传和异常处理
5. 最后补 Redis、MQ、测试和补偿

## 13. 结论

调度域 MVP 的核心不是做复杂算法，而是先把订单、任务、车辆三者之间的状态联动做对，并保证可追踪、可人工介入、可排查。后续实现必须优先保证状态迁移正确性，再考虑异步化和性能优化。
