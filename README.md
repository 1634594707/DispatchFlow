# DispatchFlow

DispatchFlow 是一个面向园区短驳配送场景的无人车调度系统原型，覆盖了移动端下单、调度任务生成、自动派车、车辆状态机执行、低电量返充、监控大屏联动以及 RabbitMQ 事件驱动等核心能力。

项目目标不是单纯完成一个“地图上小车移动”的演示，而是尽可能用真实后端系统的方式，构建一条完整的业务闭环：

`下单 -> 订单创建 -> 调度任务生成 -> 自动派车 -> 车辆执行 -> 车辆回报 -> 任务完成/失败 -> 回待命位或充电`

---

## 一、项目背景

在园区无人配送、工厂物料短驳、仓储周转配送这类场景中，调度系统的核心不是展示页面，而是后端如何管理以下对象之间的关系：

- 订单
- 调度任务
- 车辆
- 站点
- 路网
- 停车/充电位
- 异常与人工介入

DispatchFlow 围绕这几个核心对象，搭建了一个可运行、可联调、可扩展的调度系统雏形。

---

## 二、项目能力概览

当前版本已经具备以下能力：

### 1. 园区监控与可视化

- 园区底图加载
- 车辆实时位置展示
- 订单链路展示
- 停车/充电观察层
- 车辆当前状态、电量、目标点展示

### 2. 园区简化下单

- 支持移动端选择取货点和送货点
- 下单后自动创建订单
- 自动生成调度任务
- 自动触发派车

### 3. 调度任务管理

- 调度任务创建
- 自动派车
- 手动派车
- 派车失败进入人工介入
- 调度异常记录

### 4. 车辆状态机执行

车辆执行过程不是瞬时完成，而是经历：

- 待命
- 前往取货
- 装货
- 前往送货
- 卸货
- 前往充电
- 充电
- 返回待命
- 离线

### 5. 低电量返充

- 任务执行过程中持续消耗电量
- 任务完成后自动评估电量
- 电量不足时自动转入充电流程
- 电量恢复后返回待命位

### 6. 事件驱动

系统关键节点支持事件发布：

- 任务创建
- 任务已分配
- 任务执行中
- 任务成功
- 任务失败
- 异常创建
- 异常解决

同时引入 Outbox 模式，降低业务状态与消息投递不一致的问题。

---

## 三、技术栈

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

---

## 四、项目结构

```text
DispatchFlow/
├─ front/                前端工程
├─ back/                 后端工程
└─ README.md
```

### 前端目录

`front/` 主要包含：

- 管理端页面
- 车辆监控大屏
- 订单、任务、车辆列表与详情
- 移动端简化下单页

### 后端目录

`back/` 是 Maven 多模块工程，模块划分如下：

- `fsd-common`
  公共模型、异常、枚举、统一响应结构

- `fsd-order`
  订单域，负责订单创建、订单状态流转、订单查询

- `fsd-dispatch`
  调度域，负责调度任务、自动派车、异常处理、事件驱动、园区仿真、状态机推进

- `fsd-vehicle`
  车辆域，负责车辆状态、车辆占用与释放、车辆回报处理

- `fsd-admin-api`
  管理端聚合接口层，对前端统一暴露订单、任务、车辆、异常、园区调度相关能力

- `fsd-bootstrap`
  启动模块，负责应用装配与运行

---

## 五、核心业务链路

### 1. 园区下单链路

移动端或管理端发起下单后，系统会执行：

1. 校验取货点和送货点
2. 创建订单
3. 创建调度任务
4. 自动派车
5. 返回订单号、任务号、派车结果

核心代码：

- [ParkPilotCommandServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotCommandServiceImpl.java)

### 2. 自动派车链路

自动派车时，系统会执行：

1. 获取任务锁，避免并发重复派车
2. 校验任务状态
3. 查询可分配车辆
4. 根据取货点选择最近车辆
5. 占用车辆
6. 任务状态推进为 `ASSIGNED`
7. 若派车失败，则进入 `MANUAL_PENDING`

核心代码：

- [DispatchTaskServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/DispatchTaskServiceImpl.java)
- [ParkPilotServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotServiceImpl.java)

### 3. 车辆执行链路

车辆任务执行采用状态机推进，不是直接瞬时完成。系统通过定时任务驱动车辆经历完整生命周期：

- `TO_PICKUP`
- `LOADING`
- `TO_DROPOFF`
- `UNLOADING`
- `TO_CHARGING`
- `CHARGING`
- `RETURNING_TO_STANDBY`

核心代码：

- [ParkPilotSimulationServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotSimulationServiceImpl.java)

### 4. 车辆回报链路

车辆通过执行回报驱动任务状态变化，例如：

- `START_EXECUTE`
- `TASK_SUCCESS`
- `TASK_FAILED`

任务、订单和车辆状态由回报统一联动推进。

核心代码：

- [VehicleReportServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/VehicleReportServiceImpl.java)

### 5. 事件驱动链路

关键业务动作会发布领域事件，并通过 RabbitMQ 分发。为了保证消息发送可靠性，项目使用：

- 本地消息表（Outbox）
- 事务提交后发送
- 失败重试调度

核心代码：

- [RabbitDispatchEventPublisher](back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/RabbitDispatchEventPublisher.java)
- [DispatchEventOutboxServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/impl/DispatchEventOutboxServiceImpl.java)
- [DispatchEventRetryScheduler](back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/impl/DispatchEventRetryScheduler.java)

---

## 六、核心接口

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

---

## 七、本地运行说明

### 1. 环境要求

- Node.js 18+
- JDK 21
- Maven 3.9+
- MySQL
- Redis
- RabbitMQ

### 2. 默认依赖端口

后端默认使用以下本地依赖：

- MySQL：`127.0.0.1:3307`
- Redis：`127.0.0.1:6380`
- RabbitMQ：`127.0.0.1:5673`

配置文件：

- [back/fsd-bootstrap/src/main/resources/application.yml](back/fsd-bootstrap/src/main/resources/application.yml)

### 3. 启动后端

注意：`back/pom.xml` 是聚合工程，不能直接在 `back/` 根目录执行 `mvn spring-boot:run`。

正确方式：

```bash
cd back
mvn -pl fsd-bootstrap -am spring-boot:run
```

默认访问地址：

- API：`http://localhost:8080`
- Swagger：`http://localhost:8080/swagger-ui.html`
- OpenAPI：`http://localhost:8080/api-docs`

### 4. 启动前端

```bash
cd front
npm install
npm run dev
```

默认访问地址：

- Frontend：`http://localhost:3000`

前端通过 Vite 代理访问 `/api` 到后端服务。

---

## 八、测试

推荐执行以下测试命令：

```bash
cd back
mvn -pl fsd-admin-api -am test
mvn -pl fsd-bootstrap -am test
```

---

## 九、推荐阅读代码顺序

如果希望快速理解整个项目，建议按以下顺序阅读代码：

1. [AdminDispatchController](back/fsd-admin-api/src/main/java/com/fsd/admin/controller/AdminDispatchController.java)
2. [ParkPilotCommandServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotCommandServiceImpl.java)
3. [DispatchTaskServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/DispatchTaskServiceImpl.java)
4. [ParkPilotServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotServiceImpl.java)
5. [ParkPilotSimulationServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotSimulationServiceImpl.java)
6. [VehicleReportServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/VehicleReportServiceImpl.java)
7. [RabbitDispatchEventPublisher](back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/RabbitDispatchEventPublisher.java)

---

## 十、项目定位

这个项目更适合被定义为：

**一个面向园区短驳配送场景的无人车调度系统后端原型。**

它的重点不在于“可视化大屏”，而在于：

- 订单驱动调度
- 任务状态流转
- 自动派车
- 车辆状态机
- 低电量返充
- RabbitMQ 事件驱动
- 面向真实调度系统的后端结构设计
