# DispatchFlow 项目代码解析（面试介绍版）

## 1. 这份文档的用途

这不是任务清单，也不是需求文档。

这份文档专门用来帮助我在面试里介绍这个项目，重点不是“做了多少页面”，而是：

- 这个项目的核心业务是什么
- 代码是怎么分层的
- 哪些代码板块最重要
- 下单、派车、状态机、事件驱动是怎么串起来的
- 我应该按什么顺序讲这个项目

## 2. 项目一句话定位

`DispatchFlow` 是一个面向园区短驳配送场景的无人车调度系统原型，后端重点实现了：

- 订单创建
- 调度任务生成
- 自动派车
- 车辆状态机执行
- 低电量返充
- RabbitMQ 事件驱动
- 监控大屏与移动端联动

如果面试官只给我 30 秒，我应该先讲这句。

## 3. 项目最重要的代码板块

这个项目最值得讲的不是所有模块，而是以下 6 块：

1. 管理端与园区接口入口
2. 移动端下单转订单与任务
3. 自动派车与选车逻辑
4. 车辆状态机与仿真执行
5. 车辆回报驱动任务状态变化
6. RabbitMQ + Outbox 事件驱动链路

面试介绍时，建议始终围绕这 6 块展开。

## 4. 第一层：接口入口怎么组织

最外层入口在：

- [AdminDispatchController.java](D:\Administrator\Desktop\Project\DispatchFlow\back\fsd-admin-api\src\main\java\com\fsd\admin\controller\AdminDispatchController.java:1)

这层代码的价值不是复杂逻辑，而是把系统能力按接口暴露出来。

面试里可以这样讲：

“我把管理端接口和园区调度相关接口统一收口在 `AdminDispatchController`，这里能看到整个系统对外提供的核心能力，包括订单、任务、异常、车辆、园区布局、园区车辆快照，以及移动端下单入口。”

这里最关键的接口有：

- `GET /api/admin/park/layout`
- `GET /api/admin/park/stations`
- `GET /api/admin/park/vehicles`
- `GET /api/admin/park/orders`
- `POST /api/admin/park/orders`

这组接口的意义是：

- 大屏通过它们拿布局、车辆、订单快照
- 移动端通过它们拿站点并发起下单
- 所有前端展示都围绕同一套后端接口

这能说明项目不是“写死页面”，而是接口驱动的。

## 5. 第二层：下单后如何变成调度任务

核心代码在：

- [ParkPilotCommandServiceImpl.java](D:\Administrator\Desktop\Project\DispatchFlow\back\fsd-dispatch\src\main\java\com\fsd\dispatch\service\impl\ParkPilotCommandServiceImpl.java:1)

这一层是面试中最容易讲清业务闭环的地方。

### 5.1 它做了什么

移动端下单后，不是直接“找车去送”，而是先走标准业务链路：

1. 校验取货点和送货点
2. 创建订单
3. 基于订单创建调度任务
4. 调用自动派车
5. 返回订单、任务、车辆分配结果

也就是：

`Park 下单请求 -> Order -> DispatchTask -> Auto Assign`

### 5.2 为什么这层重要

它把“业务请求”和“调度系统”真正接起来了。

面试里可以这样讲：

“我没有把园区下单写成一个前端假流程，而是把它接入了正式的订单域和调度任务域。`ParkPilotCommandServiceImpl` 负责把移动端下单请求转成内部标准订单，再创建调度任务，并立即触发自动派车，这样移动端、大屏和后端调度链路是统一的。”

### 5.3 这层体现的设计点

- 订单域和调度域分开
- 下单接口不直接操作车辆
- 先有订单，再有任务，再有派车
- 这个结构更接近真实系统

## 6. 第三层：自动派车与选车逻辑

核心代码在：

- [DispatchTaskServiceImpl.java](D:\Administrator\Desktop\Project\DispatchFlow\back\fsd-dispatch\src\main\java\com\fsd\dispatch\service\impl\DispatchTaskServiceImpl.java:1)
- [ParkPilotServiceImpl.java](D:\Administrator\Desktop\Project\DispatchFlow\back\fsd-dispatch\src\main\java\com\fsd\dispatch\service\impl\ParkPilotServiceImpl.java:1)
- [DispatchLockService.java](D:\Administrator\Desktop\Project\DispatchFlow\back\fsd-dispatch\src\main\java\com\fsd\dispatch\infra\DispatchLockService.java:1)

这是整个项目最像 JD 里“调度核心”的部分。

### 6.1 `DispatchTaskServiceImpl` 的职责

它负责两件关键事：

- 创建调度任务
- 自动派车 / 手动派车

其中 `autoAssignTask` 是最值得讲的方法。

### 6.2 `autoAssignTask` 的核心流程

这段代码大致是：

1. 先拿任务锁，避免并发重复派车
2. 校验任务是否允许自动派车
3. 把任务从 `PENDING` 改成 `ASSIGNING`
4. 查询可分配车辆
5. 如果没有可用车辆，进入 `MANUAL_PENDING`
6. 根据订单取货点调用选车逻辑
7. 占用车辆
8. 任务状态改成 `ASSIGNED`
9. 订单标记为已调度
10. 发布 `TASK_ASSIGNED` 事件

### 6.3 这段代码为什么重要

因为它完整体现了一个真实调度系统需要考虑的要素：

- 并发保护
- 状态流转
- 候选车辆筛选
- 失败降级到人工介入
- 任务与订单联动
- 事件通知

### 6.4 选车逻辑在哪里

`DispatchTaskServiceImpl` 本身不写具体“最近车算法”，而是把选车委托给：

- [ParkPilotServiceImpl.java](D:\Administrator\Desktop\Project\DispatchFlow\back\fsd-dispatch\src\main\java\com\fsd\dispatch\service\impl\ParkPilotServiceImpl.java:1)

这里的 `selectNearestVehicle` 做的是：

- 根据取货站点取到目标位置
- 对候选车辆逐台计算到目标点的路径距离
- 优先走路径规划算真实路网距离
- 如果路径规划失败，再退化为欧式距离
- 选出距离最短的车辆

面试里可以这样讲：

“我把选车分成两层：`DispatchTaskServiceImpl` 负责派车流程控制，`ParkPilotServiceImpl` 负责具体的选车策略。目前实现的是最近车策略，但不是简单算直线距离，而是优先基于路网路径长度选择车辆，这样更接近真实调度系统的选车方式。”

### 6.5 这一块最值得强调的亮点

- 有任务锁，考虑并发
- 有自动派车失败降级逻辑
- 有最近车策略
- 有路由距离而不是简单直线距离
- 有人工介入入口

这部分是你面试最该重点讲的后端核心。

## 7. 第四层：车辆状态机与仿真执行

核心代码在：

- [ParkPilotSimulationServiceImpl.java](D:\Administrator\Desktop\Project\DispatchFlow\back\fsd-dispatch\src\main\java\com\fsd\dispatch\service\impl\ParkPilotSimulationServiceImpl.java:1)

这是整个项目最有“无人车调度味道”的代码。

### 7.1 这层解决什么问题

如果只有订单和派车，系统还只是管理后台。

真正像无人车调度系统，是因为这里实现了车辆运行状态机，让车辆在时间维度上持续推进，而不是瞬间完成任务。

### 7.2 核心状态有哪些

这套状态机里最关键的状态包括：

- `STANDBY`
- `TO_PICKUP`
- `LOADING`
- `TO_DROPOFF`
- `UNLOADING`
- `TO_CHARGING`
- `CHARGING`
- `RETURNING_TO_STANDBY`
- `OFFLINE`

### 7.3 状态推进逻辑

`tick()` 是整个仿真引擎的核心入口。

它会定时执行，驱动所有试点车辆向前推进。

对忙碌车辆：

- 去取货点
- 到站后进入装货等待
- 再前往送货点
- 到站后进入卸货等待
- 卸货完成后通过车辆上报触发任务成功
- 然后评估电量，决定回待命位还是去充电

对空闲车辆：

- 可能随机离线
- 低电量时去充电
- 充电完成后返回待命位
- 正常时停在待命位

### 7.4 这层最重要的设计点

#### 1. 运行时状态和数据库状态分开

这里用 `runtimeStates` 保存车辆运行中的临时状态，例如：

- 当前阶段
- 当前目标点
- 下一目标点
- 当前路线
- 路线索引
- 轨迹 trail

这说明你不是把所有状态都硬塞数据库，而是区分了：

- 持久业务状态
- 运行时仿真状态

#### 2. 车辆是按路线一步步移动的

不是直接跳点，而是通过 `moveVehicleAlongRoute` 按路径逐步移动。

这让大屏上的车辆轨迹、阶段变化和任务执行更真实。

#### 3. 电量是任务完成后决策的一部分

`routeAfterDelivery` 这一段非常值得讲：

- 如果低电量，去充电
- 否则回待命位

这说明系统不是只关注“送完即结束”，而是考虑车辆生命周期。

#### 4. 停车和充电位已经合并

`getChargingSpot()` 直接复用 `getStandbySpot()`，这表示：

- 停车位就是充电位
- 车辆待命和补能在同一套点位体系中完成

这个设计和你现在的大屏/移动端表达是一致的。

### 7.5 面试里怎么讲这块

“`ParkPilotSimulationServiceImpl` 是这个项目最核心的运行时引擎。它通过定时 tick 驱动车辆状态机推进，车辆不会瞬间完成任务，而是会经历前往取货、装货、前往送货、卸货、返航或充电等状态。这里我重点处理了低电量返充、待命位与充电位统一、轨迹记录和离线模拟等逻辑，让调度系统更接近真实无人车执行过程。”

## 8. 第五层：车辆上报如何驱动任务状态变化

核心代码在：

- [VehicleReportServiceImpl.java](D:\Administrator\Desktop\Project\DispatchFlow\back\fsd-dispatch\src\main\java\com\fsd\dispatch\service\impl\VehicleReportServiceImpl.java:1)

这是项目里“执行反馈闭环”的关键。

### 8.1 它解决的问题

调度任务不是后端自己凭空改状态的，而是应该由车辆执行反馈来驱动状态变化。

这里的设计就是：

- 车辆上报 `START_EXECUTE`
- 任务进入 `EXECUTING`
- 车辆上报 `TASK_SUCCESS`
- 任务进入 `SUCCESS`，订单进入 `COMPLETED`
- 车辆上报 `TASK_FAILED`
- 任务进入 `FAILED`，订单进入 `FAILED`

### 8.2 这一层体现的价值

它说明系统不是“后端自娱自乐”，而是具备设备回报驱动的结构。

即使现在是仿真，这种代码组织已经很像真实无人车系统。

### 8.3 这段代码还能讲什么

- 有报告幂等处理
- 有任务状态校验
- 有订单状态联动
- 有车辆释放逻辑
- 有异常记录
- 有事件发布

面试里可以这样讲：

“我把任务执行状态变化统一收敛在 `VehicleReportServiceImpl`，由车辆报告来驱动任务进入执行、成功、失败等状态，这样系统更贴近真实设备接入模式，也方便后续从仿真平滑切换到真实车端上报。”

## 9. 第六层：RabbitMQ + Outbox 事件驱动

核心代码在：

- [RabbitDispatchEventPublisher.java](D:\Administrator\Desktop\Project\DispatchFlow\back\fsd-dispatch\src\main\java\com\fsd\dispatch\event\RabbitDispatchEventPublisher.java:1)
- [DispatchEventOutboxServiceImpl.java](D:\Administrator\Desktop\Project\DispatchFlow\back\fsd-dispatch\src\main\java\com\fsd\dispatch\event\impl\DispatchEventOutboxServiceImpl.java:1)
- [DispatchEventRetryScheduler.java](D:\Administrator\Desktop\Project\DispatchFlow\back\fsd-dispatch\src\main\java\com\fsd\dispatch\event\impl\DispatchEventRetryScheduler.java:1)

这块是和 JD 最直接对齐的部分之一。

### 9.1 发布器怎么做

`RabbitDispatchEventPublisher` 的关键做法是：

1. 先构建领域事件对象
2. 先把事件写入 outbox 表
3. 如果当前处于事务中，就注册 `afterCommit`
4. 事务提交后再真正发到 RabbitMQ
5. 发送成功则标记 `PUBLISHED`
6. 发送失败则标记 `FAILED`

这能说明你理解：

- 事务提交和消息发送不能随便混在一起
- 需要 outbox 模式保证可靠性

### 9.2 Outbox 服务做了什么

`DispatchEventOutboxServiceImpl` 负责：

- 保存待发送事件
- 标记成功
- 标记失败
- 查询可重试事件
- 把数据库记录重建成领域事件

这是典型的“本地消息表”模式。

### 9.3 重试调度器做了什么

`DispatchEventRetryScheduler` 定时扫描：

- `PENDING`
- `FAILED`

且到达下次重试时间的事件，然后重新发布。

这说明项目不是“用了 RabbitMQ”这么简单，而是考虑了：

- 发送失败
- 延迟重试
- 可靠投递

### 9.4 这块在面试里该怎么讲

“我在调度任务相关事件里采用了 RabbitMQ + Outbox 的方式，不是直接在业务代码里裸发消息。业务事务内先落本地事件表，事务提交后再发 MQ，发送失败进入 outbox 重试调度，这样能降低业务状态更新和消息发送不一致的风险。”

### 9.5 这块为什么很加分

因为它能直接证明：

- 你不只是会 Spring Boot CRUD
- 你理解事件驱动架构
- 你知道消息可靠性问题
- 你能往真实分布式系统思路靠

## 10. 这个项目的核心代码调用链

如果面试官问“你这个系统完整链路是什么”，推荐按下面这条线讲：

### 10.1 下单链路

`AdminDispatchController`

-> `ParkPilotCommandServiceImpl`

-> `OrderService.createOrder`

-> `DispatchTaskServiceImpl.createTask`

-> `DispatchTaskServiceImpl.autoAssignTask`

-> `ParkPilotServiceImpl.selectNearestVehicle`

-> 任务变成 `ASSIGNED`

### 10.2 执行链路

`ParkPilotSimulationServiceImpl.tick()`

-> 车辆进入 `TO_PICKUP / LOADING / TO_DROPOFF / UNLOADING`

-> 仿真里通过 `submitVehicleReport`

-> `VehicleReportServiceImpl.handleReport`

-> 任务进入 `EXECUTING / SUCCESS / FAILED`

-> 订单状态同步更新

### 10.3 事件链路

`DispatchTaskServiceImpl / VehicleReportServiceImpl / DispatchExceptionServiceImpl`

-> `RabbitDispatchEventPublisher.publish`

-> outbox 落库

-> 事务提交后发 RabbitMQ

-> 失败则由 `DispatchEventRetryScheduler` 重试

这三条链路能把项目的后端价值讲得很完整。

## 11. 面试时最值得重点介绍的 4 段代码

如果时间有限，我最推荐重点讲这 4 段：

### 11.1 `ParkPilotCommandServiceImpl.createParkOrder`

因为它能说明：

- 下单如何接入后端
- 订单和任务如何串起来

### 11.2 `DispatchTaskServiceImpl.autoAssignTask`

因为它能说明：

- 自动派车
- 候选车辆筛选
- 最近车策略
- 并发锁
- 人工介入降级

### 11.3 `ParkPilotSimulationServiceImpl.tick`

因为它能说明：

- 车辆状态机
- 低电量返充
- 待命/充电联动
- 真实执行节奏

### 11.4 `RabbitDispatchEventPublisher.publish`

因为它能说明：

- 事件驱动
- 事务后发消息
- outbox 可靠性设计

如果你把这 4 段讲清楚，这个项目的含金量会高很多。

## 12. 这个项目该怎么介绍给面试官

推荐介绍顺序如下。

### 第一步：先讲业务目标

“这是一个园区短驳配送场景下的无人车调度系统原型，目标是把移动端下单、后端派车、车辆执行、低电量返充和监控大屏串成一个完整闭环。”

### 第二步：再讲后端分层

“最外层是管理端和园区接口，里面拆成订单域、调度任务域、车辆域和园区仿真域。园区下单不会直接操作车辆，而是先创建订单，再生成调度任务，再自动派车。”

### 第三步：重点讲自动派车

“自动派车这块我专门做了任务锁、可分配车辆筛选、最近车策略和人工介入降级。”

### 第四步：重点讲状态机

“车辆不是瞬间送达，而是通过状态机和定时 tick 逐步推进，经历取货、装货、送货、卸货、返航、充电等状态。”

### 第五步：最后讲事件驱动

“任务创建、派车成功、任务执行、任务完成、任务失败这些关键节点都会发布 RabbitMQ 事件，并通过 outbox 模式保证可靠发送。”

这套讲法很贴近你目标 JD。

## 13. 这个项目最能体现我什么能力

通过这份代码，最应该向面试官传达的是：

- 我不只是会写接口，还会建模业务闭环
- 我理解调度系统的任务流转和车辆状态机
- 我知道自动派车需要并发保护和失败降级
- 我理解 RabbitMQ 事件驱动和 outbox 可靠消息模式
- 我能把前端演示、大屏监控和后端调度真正联通

## 14. 总结

这个项目最重要的不是“大屏做得像不像”，而是后端已经具备一个缩小版 FSD-Core 的核心结构：

- 有统一接口入口
- 有订单转任务
- 有自动派车
- 有最近车策略
- 有车辆状态机
- 有低电量返充
- 有设备回报驱动状态流转
- 有 RabbitMQ + Outbox 事件驱动链路

如果面试时围绕这些代码板块来讲，这个项目就不是“一个演示项目”，而会更像“一个有调度核心雏形的后端系统”。
