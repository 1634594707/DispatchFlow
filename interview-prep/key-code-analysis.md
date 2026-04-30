# 重点代码解析

## 1. 最该讲的 6 个代码入口

如果要快速介绍这个项目，我最推荐围绕下面 6 个类展开：

1. `AdminDispatchController`
2. `ParkPilotCommandServiceImpl`
3. `DispatchTaskServiceImpl`
4. `ParkPilotSimulationServiceImpl`
5. `VehicleReportServiceImpl`
6. `RabbitDispatchEventPublisher`

它们分别对应：

- 系统入口
- 下单转任务
- 自动派车
- 车辆状态机
- 回报驱动任务流转
- 事件驱动

## 2. `AdminDispatchController`

文件：

- `back/fsd-admin-api/src/main/java/com/fsd/admin/controller/AdminDispatchController.java`

### 核心作用

这是系统对外的聚合入口，负责把订单、任务、异常、车辆和园区调度相关接口统一暴露出来。

### 值得讲的点

- 管理端接口统一收口
- 园区监控接口和移动端下单接口都从这里进入
- 前端不是写死数据，而是围绕真实接口联动

### 面试表达

“这个类本身逻辑不重，但它能很好地体现系统对外提供了哪些能力，也能让人快速看到项目的业务边界。”

## 3. `ParkPilotCommandServiceImpl`

文件：

- `back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotCommandServiceImpl.java`

### 核心作用

负责把园区下单请求接入正式后端链路。

### 关键流程

1. 校验取货点和送货点
2. 创建订单
3. 创建调度任务
4. 自动派车
5. 返回订单、任务、派车结果

### 值得讲的点

- 下单不会直接操作车辆
- 订单域和调度域分开
- 前端请求真正落到后端业务闭环

### 面试表达

“这一层证明我做的不是演示页面，而是把下单真正接入了订单、任务、派车这条正式链路。”

## 4. `DispatchTaskServiceImpl`

文件：

- `back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/DispatchTaskServiceImpl.java`

### 核心作用

负责任务创建、自动派车、手动派车、人工介入和任务摘要。

### 最关键的方法

- `createTask`
- `autoAssignTask`
- `manualAssignTask`
- `moveToManualPending`

### `autoAssignTask` 核心逻辑

1. 获取任务锁
2. 校验任务状态
3. 进入 `ASSIGNING`
4. 查询可分配车辆
5. 调用选车逻辑
6. 占用车辆
7. 更新任务状态为 `ASSIGNED`
8. 更新订单状态
9. 发布任务已分配事件

### 值得讲的点

- 有并发保护
- 有自动派车
- 有失败降级
- 有事件发布
- 有任务与订单联动

### 面试表达

“这个类是调度核心服务的中心，它把任务状态流转、派车控制、人工介入和业务事件真正结合在一起。”

## 5. `ParkPilotServiceImpl`

文件：

- `back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotServiceImpl.java`

### 核心作用

负责园区布局、站点、车辆快照、订单快照，以及最近车选择。

### 最关键的方法

- `getLayout`
- `listVehicleSnapshots`
- `listOrderSnapshots`
- `selectNearestVehicle`

### `selectNearestVehicle` 的意义

自动派车没有把选车逻辑写死在任务服务里，而是单独放在园区服务中。

当前策略是：

- 根据取货站点拿目标位置
- 计算候选车辆到目标点的路径距离
- 选出距离最短车辆

### 值得讲的点

- 选车逻辑和调度流程解耦
- 优先使用路网距离
- 能体现“策略层”的概念

### 面试表达

“我把派车流程和选车策略拆开了，这样后续替换成更复杂的策略会更自然。”

## 6. `ParkPilotSimulationServiceImpl`

文件：

- `back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotSimulationServiceImpl.java`

### 核心作用

负责车辆运行时仿真和状态机推进。

### 关键状态

- `STANDBY`
- `TO_PICKUP`
- `LOADING`
- `TO_DROPOFF`
- `UNLOADING`
- `TO_CHARGING`
- `CHARGING`
- `RETURNING_TO_STANDBY`
- `OFFLINE`

### 核心方法

- `tick`
- `tickVehicle`
- `processBusyVehicle`
- `processIdleStage`
- `routeAfterDelivery`

### 值得讲的点

- 用定时 tick 推进车辆执行
- 忙碌车辆和空闲车辆逻辑分开
- 送货完成后按电量回待命或去充电
- 停车位和充电位统一建模
- 运行时状态和数据库状态分离

### 面试表达

“这个类是项目最贴近真实无人车调度场景的部分，它把任务分配之后的执行过程真正表达出来了。”

## 7. `VehicleReportServiceImpl`

文件：

- `back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/VehicleReportServiceImpl.java`

### 核心作用

负责接收车辆执行回报，并驱动任务、订单、车辆状态更新。

### 支持的关键回报

- `START_EXECUTE`
- `TASK_SUCCESS`
- `TASK_FAILED`
- `OFFLINE`

### 值得讲的点

- 任务状态变化由车辆回报驱动
- 有回报幂等处理
- 会联动订单状态
- 会释放车辆资源
- 会触发异常记录和事件发布

### 面试表达

“这块的设计是为了让调度系统更接近真实设备接入模式，而不是让后端自己随意推进任务状态。”

## 8. `RabbitDispatchEventPublisher`

文件：

- `back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/RabbitDispatchEventPublisher.java`

### 核心作用

负责统一发布调度域事件。

### 核心逻辑

1. 生成领域事件
2. 先保存到 outbox
3. 如果有事务，则等事务提交后再发 MQ
4. 成功则标记已发布
5. 失败则标记失败

### 值得讲的点

- 事务后发布消息
- 不是简单裸发 MQ
- 和 Outbox 模式配合使用

### 面试表达

“我在这块重点考虑的是业务事务和消息一致性问题，所以没有直接在 Service 里发消息。”

## 9. `DispatchEventOutboxServiceImpl`

文件：

- `back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/impl/DispatchEventOutboxServiceImpl.java`

### 核心作用

负责本地消息表的持久化与重建。

### 值得讲的点

- 保存待发送事件
- 标记成功
- 标记失败
- 生成重试时间
- 从表记录重建领域事件

### 面试表达

“这部分体现的是可靠消息设计，而不是单纯会用 RabbitMQ。”

## 10. `DispatchEventRetryScheduler`

文件：

- `back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/impl/DispatchEventRetryScheduler.java`

### 核心作用

定时扫描 outbox 中可重试事件并重新发布。

### 值得讲的点

- 失败事件自动补偿
- 和 outbox 一起形成可靠消息闭环

## 11. 最推荐的项目讲解顺序

如果现场讲项目，我建议按这个顺序：

1. `AdminDispatchController`
2. `ParkPilotCommandServiceImpl`
3. `DispatchTaskServiceImpl`
4. `ParkPilotServiceImpl`
5. `ParkPilotSimulationServiceImpl`
6. `VehicleReportServiceImpl`
7. `RabbitDispatchEventPublisher`
8. `DispatchEventOutboxServiceImpl`

## 12. 一句话总结

这个项目最值得讲的，不是“页面完成度”，而是：

“我已经把一个缩小版无人车调度系统最核心的后端链路用代码真正串起来了。”
