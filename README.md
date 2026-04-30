# DispatchFlow

DispatchFlow 是一个面向园区短驳配送场景的无人车调度系统原型，目标是把：

- 移动端下单
- 调度任务生成
- 自动派车
- 车辆状态机执行
- 低电量返充
- 监控大屏联动
- RabbitMQ 事件驱动

串成一个完整的后端闭环。

这个项目的重点不是“地图上有小车在动”，而是用 Java 后端把订单、任务、车辆、状态机和事件流转真正接起来。

## 项目亮点

### 1. 订单驱动调度

移动端或管理端下单后，不是直接操作车辆，而是先创建订单，再生成调度任务，最后触发自动派车。

核心代码：

- [back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotCommandServiceImpl.java](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotCommandServiceImpl.java)

### 2. 自动派车与选车策略

调度任务自动分配时，系统会：

- 先获取任务锁，避免并发重复派车
- 查询可分配车辆
- 根据取货点选择最近车辆
- 派车失败时降级到人工介入

核心代码：

- [back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/DispatchTaskServiceImpl.java](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/DispatchTaskServiceImpl.java)
- [back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotServiceImpl.java](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotServiceImpl.java)

### 3. 车辆状态机

车辆不会瞬间完成任务，而是按状态机推进：

- `STANDBY`
- `TO_PICKUP`
- `LOADING`
- `TO_DROPOFF`
- `UNLOADING`
- `TO_CHARGING`
- `CHARGING`
- `RETURNING_TO_STANDBY`
- `OFFLINE`

送货完成后，系统会根据电量决定：

- 返回待命位
- 或直接进入充电位

核心代码：

- [back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotSimulationServiceImpl.java](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotSimulationServiceImpl.java)

### 4. 车辆回报驱动任务状态变更

任务状态不是后端随意修改，而是由车辆执行回报驱动：

- `START_EXECUTE`
- `TASK_SUCCESS`
- `TASK_FAILED`

这让系统结构更接近真实车端接入模式。

核心代码：

- [back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/VehicleReportServiceImpl.java](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/VehicleReportServiceImpl.java)

### 5. RabbitMQ + Outbox 事件驱动

任务创建、任务派车、任务执行、任务完成、异常处理等关键节点都会发布事件。

项目没有直接在业务事务里裸发消息，而是采用：

- RabbitMQ
- Outbox 本地消息表
- 事务提交后发送
- 失败后定时重试

核心代码：

- [back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/RabbitDispatchEventPublisher.java](back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/RabbitDispatchEventPublisher.java)
- [back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/impl/DispatchEventOutboxServiceImpl.java](back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/impl/DispatchEventOutboxServiceImpl.java)
- [back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/impl/DispatchEventRetryScheduler.java](back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/impl/DispatchEventRetryScheduler.java)

## 技术栈

### 前端

- Vue 3
- TypeScript
- Vite
- Ant Design Vue
- Leaflet

### 后端

- Java 21
- Spring Boot 3.3
- MyBatis-Plus

### 中间件

- MySQL
- Redis
- RabbitMQ

## 仓库结构

```text
DispatchFlow/
├─ front/   前端项目，包含大屏、订单、车辆、移动端下单页
├─ back/    后端多模块项目
└─ README.md
```

后端模块说明：

- `fsd-common`：公共模型、异常、枚举
- `fsd-order`：订单域
- `fsd-dispatch`：调度域、状态机、事件驱动、园区仿真
- `fsd-vehicle`：车辆域、车辆回报
- `fsd-admin-api`：管理端聚合接口
- `fsd-bootstrap`：启动模块

## 当前已实现能力

### 管理端 / 大屏

- 园区布局加载
- 车辆实时位置展示
- 订单链路展示
- 停车充电观察层
- 车辆状态、电量、目标点展示

### 移动端

- 选择取货点和送货点
- 创建订单
- 在同页查看配送过程

### 后端

- 园区布局接口
- 车辆快照接口
- 订单快照接口
- 简化下单接口
- 自动派车
- 车辆状态机仿真
- 低电量返充
- RabbitMQ 事件发布与重试

## 核心接口

### 园区监控接口

- `GET /api/admin/park/layout`
- `GET /api/admin/park/stations`
- `GET /api/admin/park/vehicles`
- `GET /api/admin/park/orders`

### 园区下单接口

- `POST /api/admin/park/orders`

请求示例：

```json
{
  "externalOrderNo": "PARK-DEMO-001",
  "pickupStationId": 101,
  "dropoffStationId": 201,
  "priority": "P1",
  "remark": "mobile order"
}
```

## 本地运行

### 环境要求

- Node.js 18+
- JDK 21
- Maven 3.9+
- MySQL / Redis / RabbitMQ

### 默认依赖端口

后端默认读取：

- MySQL：`127.0.0.1:3307`
- Redis：`127.0.0.1:6380`
- RabbitMQ：`127.0.0.1:5673`

配置文件：

- [back/fsd-bootstrap/src/main/resources/application.yml](back/fsd-bootstrap/src/main/resources/application.yml)

### 启动后端

注意：根 `back/pom.xml` 是聚合工程，不能直接在 `back/` 根目录裸跑 `mvn spring-boot:run`。

正确方式：

```bash
cd back
mvn -pl fsd-bootstrap -am spring-boot:run
```

默认访问地址：

- Backend: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`
- OpenAPI: `http://localhost:8080/api-docs`

### 启动前端

```bash
cd front
npm install
npm run dev
```

默认访问地址：

- Frontend: `http://localhost:3000`

前端通过 Vite 代理访问后端 `/api`。

## 测试

推荐执行：

```bash
cd back
mvn -pl fsd-admin-api -am test
mvn -pl fsd-bootstrap -am test
```

## 为什么这个项目适合调度后端岗位

这个项目和普通 CRUD 项目最大的区别在于：

- 有明确的订单 -> 任务 -> 车辆执行闭环
- 有自动派车和失败降级
- 有车辆状态机
- 有低电量返充逻辑
- 有设备回报驱动状态流转
- 有 RabbitMQ + Outbox 事件驱动设计

如果要继续往真实生产系统演进，下一步最自然的方向是：

- 更复杂的选车算法
- 故障重调度
- 候补排队
- WebSocket 实时推送
- Spring Cloud 微服务拆分

## 当前最值得阅读的代码入口

如果只想快速理解这个项目，建议按下面顺序看：

1. [back/fsd-admin-api/src/main/java/com/fsd/admin/controller/AdminDispatchController.java](back/fsd-admin-api/src/main/java/com/fsd/admin/controller/AdminDispatchController.java)
2. [back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotCommandServiceImpl.java](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotCommandServiceImpl.java)
3. [back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/DispatchTaskServiceImpl.java](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/DispatchTaskServiceImpl.java)
4. [back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotSimulationServiceImpl.java](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotSimulationServiceImpl.java)
5. [back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/VehicleReportServiceImpl.java](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/VehicleReportServiceImpl.java)
6. [back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/RabbitDispatchEventPublisher.java](back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/RabbitDispatchEventPublisher.java)
