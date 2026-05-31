# DispatchFlow

[![CI](https://github.com/1634594707/DispatchFlow/actions/workflows/ci.yml/badge.svg)](https://github.com/1634594707/DispatchFlow/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](back/)
[![Vue](https://img.shields.io/badge/Vue-3-green.svg)](front/)

**DispatchFlow** 是一套面向园区短驳配送场景的**无人车智能调度平台（FMS 调度中台）**，覆盖移动端下单、调度工作台、自动/手动派车、车辆状态机执行、Fleet 运行态监控、充电/换电能量策略、异常处置、运营分析与 RabbitMQ 事件驱动，形成完整可运营的业务闭环。

**在线演示**：[aplicity.online](https://www.aplicity.online)

```text
下单 → 订单 → 调度任务 → 派车 → 车辆执行 → 回报联动 → 完成/失败 → 待命/充电/换电
```

---

## 核心能力

| 能力 | 说明 |
|------|------|
| 多园区调度 | 顶栏统一园区上下文（Park Scope 2.0），车辆/异常/分析/监控/孪生全站一致 |
| Fleet 运行态 | Redis 持久化位置、电量、阶段、插枪状态；SSE 实时推送监控大屏 |
| 智能派车 | 路网可达性 + SOC/距离评分；失败可解释（`reasonCode` / 建议 / 快捷跳转） |
| 调度工作台 | 任务池拖拽排序、批量派/改派/取消、异常队列、园区态势小地图 |
| 充电 / 换电 | 满电插枪待命、低电返充/换电（CHARGE / SWAP / AUTO）；换电柜 CRUD；REAL 遥测驱动换电会话 |
| 异常闭环 | 分级、去重、人工处置、派车成功自动 resolve；OPEN 超时升级 |
| 交通管制 | 地图框选区域暂停派车、路段降权/禁用、拥堵摘要条 |
| 运营分析 | 效率/异常趋势、每日摘要、跨园区对比、链路 KPI、高峰对比、PDF/定时邮件 |
| 开放集成 | Open API、Webhook、API Key、调用统计与投递日志 |
| 数字孪生 | 园区态势估算可视化 + 场景仿真预评估（标注非引擎回放） |
| 权限体系 | ADMIN / OPERATOR / VIEWER / FIELD_OPS；写操作 UI 与后端对齐；TOTP 2FA |
| 命令面板 | `Ctrl+K` 全局搜索导航；规则型「调度快捷指令」 |
| 家纺垂直 | 线路 CRUD、枢纽分流、高峰预案（含 cron 自动切换）、自动化 IF-THEN 规则、运维快照 |
| 现场运维 | FIELD_OPS 工单、大屏运维视图 |
| 事件驱动 | Outbox + RabbitMQ，保证关键业务事件可靠投递 |
| Fleet 适配 | `FleetAdapterRegistry`：SIM / REAL / **VDA5050 MQTT** |

---

## 版本与路线图

| 版本 | 状态 | 说明 |
|------|------|------|
| **v2.0.0** | ✅ | Phase 10–11：园区一致、交通管控、命令面板 |
| **v3.0.0**（当前） | ✅ | Phase 12–15：垂直/开放协议/规模化 MVP · [Release Notes](docs/releases/v3.0.0.md) |
| **V3 规划** | 待办 | MAPF、覆盖率 80%、可观测性深化 · [ROADMAP](docs/ROADMAP-V2.md) |

VDA5050 评估见 **[docs/phase15/VDA5050-EVALUATION.md](docs/phase15/VDA5050-EVALUATION.md)**。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 · TypeScript · Vite · Ant Design Vue · Leaflet · Pinia · SSE |
| 后端 | Java 21 · Spring Boot 3.3 · MyBatis-Plus |
| 存储 | MySQL 8 · Redis 7 |
| 消息 | RabbitMQ 3.13 |
| 部署 | Docker Compose · GitHub Actions CI |

---

## 快速开始

### 环境要求

JDK 21 · Maven 3.9+ · Node.js 18+ · Docker 24+（推荐）

### 1. 克隆并启动后端与中间件

```bash
git clone git@github.com:1634594707/DispatchFlow.git
cd DispatchFlow
docker compose up -d
```

启动 MySQL、Redis、RabbitMQ 及后端 API（默认 `http://localhost:8080`）。

### 2. 启动前端

```bash
cd front
npm install
npm run dev
```

访问 `http://localhost:3000`。

### 3. 常用入口

| 页面 | 地址 |
|------|------|
| 调度工作台 | http://localhost:3000/workbench |
| 调度看板 | http://localhost:3000/dashboard |
| 车辆监控大屏 | http://localhost:3000/vehicle-tracking |
| 运营分析 | http://localhost:3000/analytics |
| 交通态势 | http://localhost:3000/infrastructure/traffic |
| 线路管理 | http://localhost:3000/vertical/routes |
| 高峰预案 | http://localhost:3000/vertical/peak-mode |
| 换电柜管理 | http://localhost:3000/infrastructure/swap-cabinets |
| 现场工单 | http://localhost:3000/field-ops/tickets |
| 数字孪生 | http://localhost:3000/digital-twin |
| 移动下单 | http://localhost:3000/mobile/order |
| API 文档 | http://localhost:8080/swagger-ui.html |

本地开发（不构建 Docker 后端镜像）详见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)。

---

## 架构概览

```text
┌──────────────┐     ┌─────────────────┐     ┌──────────────────┐
│  Vue 管理端   │────▶│  fsd-admin-api  │────▶│   fsd-dispatch   │
│  工作台/监控  │     │  REST 聚合层     │     │  调度 · Fleet    │
└──────────────┘     └─────────────────┘     └────────┬─────────┘
                                                      │
          ┌───────────────────────────────────────────┼───────────────────────────┐
          ▼                     ▼                     ▼                           ▼
   FleetRuntimeService   DispatchTaskService   DispatchExceptionService    Event Outbox
   (Redis 运行态)         (自动/手动派车)        (分级 / 去重 / resolve)      (RabbitMQ)
          ▲
          │
   FleetAdapterRegistry ── SIM: SimulationFleetAdapter
                        └── REAL: RealFleetAdapter (+ RealFleetSwapCoordinator)
```

**设计原则：** 业务状态（MySQL）与 Fleet 运行态（Redis）分离；仿真与 REAL 均通过 `FleetAdapter` 注册，便于对接 VDA5050 MQTT（Phase 15）。

详细设计见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

---

## 项目结构

```text
DispatchFlow/
├── front/                 前端 SPA（管理端 + 监控 + 移动下单 + 垂直产业运营）
├── back/                  后端 Maven 多模块
│   ├── fsd-common             公共模型与枚举
│   ├── fsd-order              订单域
│   ├── fsd-dispatch           调度、Fleet、仿真、异常、事件、交通管制
│   ├── fsd-vehicle            车辆占用与回报
│   ├── fsd-admin-api          管理端 API
│   ├── fsd-bootstrap          启动模块
│   └── sql/migrations/        数据库迁移脚本 (V01–V18)
├── docs/                  文档（验收、架构、路线图、Phase 15 评估）
├── docker-compose.yml     一键启动基础设施与后端
├── .github/workflows/     CI 流水线
├── CHANGELOG.md
├── CONTRIBUTING.md
└── LICENSE
```

---

## 文档

| 文档 | 内容 |
|------|------|
| **[docs/acceptance/README.md](docs/acceptance/README.md)** | **验收总方案**（环境、顺序、勾选表） |
| **[docs/ROADMAP-V2.md](docs/ROADMAP-V2.md)** | **产品路线图**（Phase 15 待办） |
| [docs/phase15/VDA5050-EVALUATION.md](docs/phase15/VDA5050-EVALUATION.md) | VDA5050 MQTT 适配评估 |
| [docs/perf/navigation-baseline.md](docs/perf/navigation-baseline.md) | 导航重构性能基线 |
| [docs/README.md](docs/README.md) | 文档索引 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 模块划分、领域边界、Fleet 模型、事件流 |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Docker / 本地部署、迁移、故障排查 |
| **[docs/UPDATE-OPERATIONS.md](docs/UPDATE-OPERATIONS.md)** | **云服务器 Docker 更新操作手册** |
| [back/README.md](back/README.md) | 后端模块与测试说明 |
| [front/README.md](front/README.md) | 前端页面与开发说明 |
| [CHANGELOG.md](CHANGELOG.md) | 版本变更记录 |

---

## 开发

### 后端

```bash
cd back
mvn -pl fsd-bootstrap -am clean install -DskipTests
mvn -pl fsd-bootstrap spring-boot:run
```

> 必须使用 `-pl fsd-bootstrap -am`，否则可能加载旧模块 JAR 导致园区接口 404。

### 前端

```bash
cd front
npm run dev        # 开发
npm run build      # 生产构建（含 vue-tsc）
```

### 测试

```bash
cd back
mvn -pl fsd-bootstrap -am test
```

CI 在每次 push / PR 时自动运行后端测试与前端构建。

---

## 核心 API 示例

**园区下单** — `POST /api/admin/park/orders`

```json
{
  "parkId": 1,
  "externalOrderNo": "ORDER-20260525-001",
  "pickupStationId": 101,
  "dropoffStationId": 201,
  "priority": "P1",
  "remark": "园区配送"
}
```

**自动派车失败响应**（Phase 11.3）— 含可解释字段：

```json
{
  "taskId": 12,
  "status": "MANUAL_PENDING",
  "reasonCode": "NO_IDLE_VEHICLE",
  "reasonMessage": "当前无在线空闲车辆可派",
  "suggestions": ["打开车辆列表，确认在线且空闲车辆数量", "可对任务执行手动派车"]
}
```

**REAL 车队遥测** — `POST /api/open/vehicle/telemetry`（需 API Key，车辆 `linkMode=REAL`）

```json
{
  "vehicleCode": "REAL-001",
  "runtimeStage": "SWAPPING",
  "targetCode": "SWAP-01",
  "soc": 18,
  "x": 600.0,
  "y": 500.0,
  "reportTime": "2026-05-31T10:00:00",
  "eventSeq": 1001
}
```

**监控快照** — `GET /api/admin/park/vehicles?parkId=1`

完整接口列表见 Swagger UI。

---

## 贡献

欢迎提交 Issue 与 Pull Request。请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。

安全问题请按 [SECURITY.md](SECURITY.md) 私下报告。

---

## License

[MIT License](LICENSE) © 2026 DispatchFlow Contributors
