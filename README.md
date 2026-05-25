# DispatchFlow

DispatchFlow 是一个面向**园区短驳配送**场景的无人车调度系统原型。它用真实后端架构的方式，跑通从下单到车辆执行、充电、异常处置的完整业务闭环：

`下单 → 订单 → 调度任务 → 自动/手动派车 → 车辆状态机执行 → 回报联动 → 完成/失败 → 待命或充电`

当前版本已完成 **Phase 1 领域整合**：仿真与业务解耦、Fleet 运行态 Redis 持久化、统一充电策略、异常分级与自动闭环。

---

## 亮点能力

| 模块 | 说明 |
|------|------|
| 多园区调度 | `t_park / t_station`，订单绑定园区，监控与下单按园区隔离 |
| Fleet 运行态 | `FleetRuntimeService` + Redis，重启后监控页仍可恢复位置/电量/插枪状态 |
| 充电语义 | 满电插枪 = **待命中（STANDBY）**，不掉电、可接单；接单自动拔枪 |
| 能量策略 | `FleetChargePolicy` 统一低电阈值、可派单 SOC、充电速率与空闲充电规则 |
| 仿真 Adapter | `SimulationFleetAdapter` 负责 tick 推进，经统一入口写运行态，便于后续接真实车端 |
| 异常闭环 | 分级（INFO/WARN/ERROR/CRITICAL）、OPEN 去重、派车成功自动 resolve |
| 事件驱动 | RabbitMQ + Outbox，保证任务/异常等关键事件可靠投递 |

---

## 技术栈

**前端：** Vue 3 · TypeScript · Vite · Ant Design Vue · Leaflet

**后端：** Java 21 · Spring Boot 3.3 · MyBatis-Plus

**中间件：** MySQL · Redis · RabbitMQ

---

## 项目结构

```text
DispatchFlow/
├─ front/          前端（监控大屏、管理端、移动下单）
├─ back/           后端 Maven 多模块
│  ├─ fsd-common       公共枚举、响应、异常
│  ├─ fsd-order        订单域
│  ├─ fsd-dispatch     调度、Fleet、仿真、异常、事件
│  ├─ fsd-vehicle      车辆占用与回报
│  ├─ fsd-admin-api    管理端 API 聚合
│  ├─ fsd-bootstrap    启动模块
│  └─ sql/init/        Flyway 风格初始化脚本
└─ README.md
```

---

## 架构概览

```text
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  前端监控页  │────▶│  fsd-admin-api   │────▶│  fsd-dispatch   │
└─────────────┘     └──────────────────┘     └────────┬────────┘
                                                       │
                       ┌───────────────────────────────┼───────────────────────────────┐
                       ▼                               ▼                               ▼
              FleetRuntimeService            DispatchTaskService              DispatchExceptionService
              (Redis 运行态)                  (自动/手动派车)                    (分级 / 去重 / resolve)
                       ▲                               │
                       │                               ▼
              SimulationFleetAdapter          FleetChargePolicy
              (仿真 tick 写遥测)                 (派单 SOC / 充电规则)
                       │
              ParkPilotSimulationServiceImpl
              (状态机推进，非瞬时完成)
```

### Fleet 运行态语义（Phase 1）

| 状态 | 含义 |
|------|------|
| `STANDBY + pluggedIn + SOC=100%` | 插枪待命中，不掉电，可被派单 |
| 接单瞬间 | `pluggedIn=false`，拔枪出发 |
| 低电 / 无单 | 自动前往充电，充至 `fullSoc` 后回待命 |
| 忙碌 / 空闲 | 差异化耗电；充电与前往充电过程不掉电 |

核心包：`back/fsd-dispatch/src/main/java/com/fsd/dispatch/fleet/`

---

## 核心业务链路

### 1. 园区下单

1. 校验取货/送货站点 → 2. 创建订单 → 3. 创建调度任务 → 4. 自动派车

→ [ParkPilotCommandServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotCommandServiceImpl.java)

### 2. 自动派车

任务锁 → 状态校验 → 可分配车辆（经 `FleetChargePolicy` 过滤 SOC）→ 最近车 → 占用 → `ASSIGNED`；失败则 `MANUAL_PENDING`

→ [DispatchTaskServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/DispatchTaskServiceImpl.java)

### 3. 车辆执行（状态机）

`TO_PICKUP → LOADING → TO_DROPOFF → UNLOADING → TO_CHARGING → CHARGING → RETURNING_TO_STANDBY`

仿真 tick 经 `SimulationFleetAdapter` 写 Redis，监控 API 读 `FleetRuntimeService` 组装快照。

→ [ParkPilotSimulationServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotSimulationServiceImpl.java)

### 4. 异常与人工介入

- 仿真随机离线默认 **INFO 审计**，不刷 OPEN 异常
- 派车失败产生 OPEN 异常（去重）
- 重新派车成功 → 自动 `resolveOpenExceptionsForTask`
- 支持处置动作：`REASSIGN` / `MARK_FAILED` / `CLOSE` / `VEHICLE_OFFLINE`

→ [DispatchExceptionServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/DispatchExceptionServiceImpl.java)

### 5. 事件驱动

Outbox + 事务后发送 + 失败重试

→ [RabbitDispatchEventPublisher](back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/RabbitDispatchEventPublisher.java)

---

## 核心 API

### 园区与监控

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/parks` | 园区列表 |
| GET | `/api/admin/park/layout?parkId=` | 底图与路网 |
| GET | `/api/admin/park/stations?parkId=` | 站点列表 |
| GET | `/api/admin/park/vehicles` | 车辆运行态快照 |
| GET | `/api/admin/park/orders` | 园区订单 |

### 园区下单

`POST /api/admin/park/orders`

```json
{
  "parkId": 1,
  "externalOrderNo": "PARK-DEMO-001",
  "pickupStationId": 101,
  "dropoffStationId": 201,
  "priority": "P1",
  "remark": "mobile order"
}
```

---

## 本地运行

### 环境要求

Node.js 18+ · JDK 21 · Maven 3.9+ · MySQL · **Redis** · RabbitMQ

### 默认端口

| 服务 | 地址 |
|------|------|
| MySQL | `127.0.0.1:3307` |
| Redis | `127.0.0.1:6380` |
| RabbitMQ | `127.0.0.1:5673` |
| 后端 API | `http://localhost:8080` |
| 前端 | `http://localhost:3000` |
| Swagger | `http://localhost:8080/swagger-ui.html` |

配置：[application.yml](back/fsd-bootstrap/src/main/resources/application.yml)

Fleet 能量策略（`fsd.fleet.energy.*`）：

```yaml
fsd:
  fleet:
    energy:
      low-soc-threshold: 25
      min-assignable-soc: 30
      full-soc: 100
      plugged-standby-no-drain: true
      idle-charge-when-no-demand: true
```

### 数据库迁移

除 V4/V5 多园区脚本外，Phase 1 需执行：

```powershell
docker cp back\sql\init\V6__exception_severity.sql fsd-mysql:/tmp/V6.sql
docker exec fsd-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 fsd_core < /tmp/V6.sql"
```

### 启动后端

> `back/pom.xml` 是聚合工程，**必须带 `-am`**，否则园区接口会 404。

```bash
cd back
mvn -pl fsd-bootstrap -am clean install -DskipTests
mvn -pl fsd-bootstrap spring-boot:run
```

Windows 快捷方式：

```powershell
cd back
.\run-dev.ps1
```

### 启动前端

```bash
cd front
npm install
npm run dev
```

---

## 测试

```bash
cd back
mvn -pl fsd-dispatch -am test
mvn -pl fsd-bootstrap -am test
```

Phase 1 新增：`FleetChargePolicyImplTest` 等 Fleet/异常相关单测。

---

## 推荐阅读顺序

1. [AdminDispatchController](back/fsd-admin-api/src/main/java/com/fsd/admin/controller/AdminDispatchController.java) — API 入口
2. [FleetRuntimeService](back/fsd-dispatch/src/main/java/com/fsd/dispatch/fleet/service/FleetRuntimeService.java) — 运行态抽象
3. [RedisFleetRuntimeServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/fleet/service/impl/RedisFleetRuntimeServiceImpl.java) — Redis 持久化
4. [FleetChargePolicyImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/fleet/policy/FleetChargePolicyImpl.java) — 充电/派单策略
5. [SimulationFleetAdapter](back/fsd-dispatch/src/main/java/com/fsd/dispatch/fleet/simulation/SimulationFleetAdapter.java) — 仿真适配
6. [DispatchTaskServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/DispatchTaskServiceImpl.java) — 派车逻辑
7. [ParkPilotSimulationServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotSimulationServiceImpl.java) — 状态机 tick
8. [DispatchExceptionServiceImpl](back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/DispatchExceptionServiceImpl.java) — 异常闭环

---

## 演进路线

| 阶段 | 状态 | 内容 |
|------|------|------|
| Phase 1 | 后端已完成 | Fleet 运行态、仿真 Adapter、能量策略、异常闭环 |
| Phase 1 | 待做 | 调度工作台前端（任务池 + 异常快捷处置） |
| Phase 2 | 规划中 | 充电位占用、WebSocket 实时推送、评分派单 |
| Phase 3+ | 规划中 | 真实车端接入、运营报表、权限与审计 |

---

## 项目定位

DispatchFlow 是一个**可联调、可扩展的无人车调度后端原型**，重点在于：

- 订单驱动的任务调度，而非单纯地图动画
- Fleet 运行态与业务状态分离，仿真可替换为真实遥测
- 充电、派单、异常等规则集中在领域层，避免逻辑散落
- 面向真实调度系统的模块划分与事件驱动设计

适合作为调度系统后端的学习参考、面试项目或进一步对接真实车端的起点。
