# DispatchFlow

[![CI](https://github.com/1634594707/DispatchFlow/actions/workflows/ci.yml/badge.svg)](https://github.com/1634594707/DispatchFlow/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](back/)
[![Vue](https://img.shields.io/badge/Vue-3-green.svg)](front/)

**DispatchFlow** 是一套面向园区短驳配送场景的**无人车智能调度平台**，覆盖移动端下单、调度任务管理、自动/手动派车、车辆状态机执行、Fleet 运行态监控、充电能量策略、异常处置与 RabbitMQ 事件驱动，形成完整可运营的业务闭环。

```text
下单 → 订单 → 调度任务 → 派车 → 车辆执行 → 回报联动 → 完成/失败 → 待命/充电
```

---

## 核心能力

| 能力 | 说明 |
|------|------|
| 多园区调度 | 园区、站点、路网数据化管理，订单按园区隔离 |
| Fleet 运行态 | Redis 持久化车辆位置、电量、阶段、插枪状态，重启可恢复 |
| 智能派车 | 任务锁防并发、最近车策略、SOC 门槛过滤 |
| 充电管理 | 满电插枪待命、低电自动返充、无单优先充电、差异化耗电 |
| 状态机执行 | 取货→装货→送货→卸货→充电→待命，非瞬时完成 |
| 异常闭环 | 分级、去重、人工处置、派车成功自动 resolve |
| 事件驱动 | Outbox + RabbitMQ，保证关键业务事件可靠投递 |
| 监控大屏 | Leaflet 地图实时展示车辆、订单链路与园区图层 |

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 · TypeScript · Vite · Ant Design Vue · Leaflet · Pinia |
| 后端 | Java 21 · Spring Boot 3.3 · MyBatis-Plus |
| 存储 | MySQL 8 · Redis 7 |
| 消息 | RabbitMQ 3.13 |
| 部署 | Docker Compose · GitHub Actions CI |

---

## 快速开始

### 环境要求

JDK 21 · Maven 3.9+ · Node.js 18+ · Docker 24+（推荐）

### 1. 启动后端与中间件

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
| 调度看板 | http://localhost:3000/dashboard |
| 车辆监控 | http://localhost:3000/vehicle-tracking |
| 移动下单 | http://localhost:3000/mobile/order |
| API 文档 | http://localhost:8080/swagger-ui.html |

本地开发（不构建 Docker 后端镜像）详见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)。

---

## 架构概览

```text
┌──────────────┐     ┌─────────────────┐     ┌──────────────────┐
│  Vue 管理端   │────▶│  fsd-admin-api  │────▶│   fsd-dispatch   │
│  监控 / 下单  │     │  REST 聚合层     │     │  调度 · Fleet    │
└──────────────┘     └─────────────────┘     └────────┬─────────┘
                                                      │
          ┌───────────────────────────────────────────┼───────────────────────────┐
          ▼                     ▼                     ▼                           ▼
   FleetRuntimeService   DispatchTaskService   DispatchExceptionService    Event Outbox
   (Redis 运行态)         (自动/手动派车)        (分级 / 去重 / resolve)      (RabbitMQ)
          ▲
          │
   SimulationFleetAdapter  ← 可替换为真实车端遥测 Adapter
```

**设计原则：** 业务状态（MySQL）与 Fleet 运行态（Redis）分离；仿真仅作为 Fleet Adapter 的一种实现，便于后续对接真实无人车。

详细设计见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

---

## 项目结构

```text
DispatchFlow/
├── front/                 前端 SPA（管理端 + 监控 + 移动下单）
├── back/                  后端 Maven 多模块
│   ├── fsd-common             公共模型与枚举
│   ├── fsd-order              订单域
│   ├── fsd-dispatch           调度、Fleet、仿真、异常、事件
│   ├── fsd-vehicle            车辆占用与回报
│   ├── fsd-admin-api          管理端 API
│   ├── fsd-bootstrap          启动模块
│   └── sql/init/              数据库初始化脚本 (V1–V8)
├── docs/                  文档（验收入口 acceptance/README.md）
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
| [docs/README.md](docs/README.md) | 文档索引 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 模块划分、领域边界、Fleet 模型、事件流 |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Docker / 本地部署、迁移、故障排查 |
| [docs/releases/phase2.md](docs/releases/phase2.md) | Phase 2 合入说明 |
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
npm run build      # 生产构建
npm run typecheck  # 类型检查
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

**监控快照** — `GET /api/admin/park/vehicles?parkId=1`

完整接口列表见 Swagger UI。

---

## 版本规划

| 版本 | 重点 |
|------|------|
| **v0.2**（当前） | Fleet 运行态、充电策略、异常闭环 |
| v0.3 | 充电位占用、WebSocket 实时推送、评分派单 |
| v0.4 | 真实车端接入、操作日志、权限审计 |
| v1.0 | 认证授权、Flyway 迁移、生产级部署 |

详见 [docs/acceptance/README.md](docs/acceptance/README.md)（验收）与 [docs/releases/phase2.md](docs/releases/phase2.md)（Phase 2 能力说明）。Phase 3+ 路线图待重新设计。

---

## 贡献

欢迎提交 Issue 与 Pull Request。请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。

安全问题请按 [SECURITY.md](SECURITY.md) 私下报告。

---

## License

[MIT License](LICENSE) © 2026 DispatchFlow Contributors
