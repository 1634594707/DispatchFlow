# DispatchFlow

[![CI](https://github.com/1634594707/DispatchFlow/actions/workflows/ci.yml/badge.svg)](https://github.com/1634594707/DispatchFlow/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](back/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.12-6DB33F.svg)](back/pom.xml)
[![Vue](https://img.shields.io/badge/Vue-3.5-42B883.svg)](front/package.json)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.7-3178C6.svg)](front/package.json)

DispatchFlow 是面向园区短驳配送场景的无人车调度平台。项目采用前后端分离架构：前端提供调度工作台、车辆监控、移动下单、运营分析与系统管理界面；后端基于 Spring Boot 多模块组织订单、车辆、调度、管理 API 与启动入口。

在线演示：[aplicity.online](https://www.aplicity.online)

## 目录

- [功能范围](#功能范围)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [项目结构](#项目结构)
- [快速启动](#快速启动)
- [开发命令](#开发命令)
- [质量门禁](#质量门禁)
- [接口与入口](#接口与入口)
- [文档与治理](#文档与治理)

## 功能范围

| 模块 | 说明 |
| --- | --- |
| 订单与任务 | 移动端下单、订单查询、调度任务创建、任务列表与详情 |
| 调度工作台 | 任务池、自动派车、手动派车、改派、取消、异常重新派单 |
| 车辆管理 | 车辆列表、车辆详情、车辆回报、车辆运行态监控 |
| 园区基础设施 | 园区、站点、路网、地理围栏、停车位、充电桩、换电柜 |
| 能源与异常 | 充电会话、换电会话、异常记录、异常处置、告警聚合 |
| 运营分析 | 运营概览、充电报表、自定义报表、报表历史 |
| 系统管理 | 登录认证、用户管理、系统健康、集成配置、操作日志、通知设置 |
| 现场与垂直场景 | 现场工单、线路、枢纽、高峰预案、自动化规则、运营快照 |
| Fleet 集成 | SIM / REAL 车辆链路、VDA5050 MQTT 配置与模拟脚本 |

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 前端 | Vue 3.5、TypeScript 5.7、Vite 6、Ant Design Vue、Pinia、Vue Router、Leaflet、Axios |
| 移动端 | Capacitor 6、Android 原生构建 |
| 后端 | Java 21、Spring Boot 3.3.12、MyBatis-Plus、SpringDoc OpenAPI、Lombok、MapStruct |
| 数据与消息 | MySQL、Redis、RabbitMQ、H2 测试数据库 |
| 工程化 | Maven 多模块、ESLint、Prettier、vue-tsc、Playwright、JaCoCo、Checkstyle、SpotBugs |
| 部署与运维 | Docker Compose、GitHub Actions、Prometheus、Filebeat、Mosquitto |

## 系统架构

```text
┌──────────────────────┐
│ front Vue SPA        │
│ 管理端 / 监控 / 移动端 │
└──────────┬───────────┘
           │ HTTP / SSE
┌──────────▼───────────┐
│ fsd-admin-api        │
│ 管理端聚合 API        │
└──────────┬───────────┘
           │
┌──────────▼────────────────────────────────────────────┐
│ back Maven modules                                     │
│ fsd-order / fsd-vehicle / fsd-dispatch / fsd-common    │
└──────────┬──────────────────────┬─────────────────────┘
           │                      │
┌──────────▼───────────┐  ┌───────▼────────┐
│ MySQL                │  │ Redis          │
│ 业务状态与迁移脚本     │  │ Fleet 运行态    │
└──────────────────────┘  └───────┬────────┘
                                  │
                         ┌────────▼────────┐
                         │ RabbitMQ / MQTT │
                         │ 事件与车辆集成    │
                         └─────────────────┘
```

后端入口模块为 `fsd-bootstrap`。业务模块通过 Maven Reactor 一起构建，启动和测试命令必须使用 `-pl fsd-bootstrap -am`，确保依赖模块按当前源码参与构建。

## 项目结构

```text
DispatchFlow/
├── back/                         后端 Maven 多模块工程
│   ├── fsd-common/               公共枚举、异常、响应模型、安全配置
│   ├── fsd-order/                订单域
│   ├── fsd-vehicle/              车辆域
│   ├── fsd-dispatch/             调度、Fleet、园区设施、事件、地理与 MAPF
│   ├── fsd-admin-api/            管理端聚合 API
│   ├── fsd-bootstrap/            Spring Boot 启动模块
│   ├── sql/init/                 数据库初始化入口
│   ├── sql/migrations/           数据库迁移脚本
│   ├── mqtt/                     Mosquitto 配置
│   ├── observability/            Prometheus / Filebeat 配置
│   └── scripts/                  后端模拟与辅助脚本
├── front/                        Vue 管理端与监控前端
│   ├── android/                  Capacitor Android 移动端工程
│   ├── src/api/                  前端 API 封装
│   ├── src/components/           通用与业务组件（含移动端组件）
│   ├── src/views/                页面视图（含移动端页面）
│   ├── src/stores/               Pinia 状态管理
│   ├── src/router/               路由配置
│   ├── scripts/e2e/              Playwright 端到端测试
│   └── scripts/perf/             性能与导航测试脚本
├── data/                         OSM、CARLA 与园区地理数据
├── docs/                         项目路线图与文档
├── scripts/                      根级开发、验收、CARLA 与发布脚本
│   ├── carla/                    CARLA 仿真相关脚本
│   ├── dev/                      开发辅助脚本
│   ├── backup-mysql.sh           MySQL 备份脚本
│   ├── ssh_helper.py             SSH 连接与文件上传工具
│   ├── deploy.sh                 部署脚本
│   ├── check_charset.py          MySQL 字符集检查脚本
│   ├── fix_charset.py            乱码数据修复脚本
│   └── verify_api.py             API 验证脚本
├── .github/workflows/            CI、镜像发布、Release 工作流
├── docker-compose.yml            根级本地编排入口
├── CHANGELOG.md                  版本变更记录
├── CONTRIBUTING.md               贡献说明
├── SECURITY.md                   安全说明
└── LICENSE                       MIT License
```

## 快速启动

### 环境要求

| 依赖 | 版本 |
| --- | --- |
| JDK | 21 |
| Maven | 3.9+ |
| Node.js | 20（CI 使用版本） |
| Docker | 24+ |

### 1. 启动后端与基础设施

```bash
docker compose up -d
```

默认后端 API 地址为 `http://localhost:8080`。

如需本地源码方式启动后端：

```bash
cd back
mvn -pl fsd-bootstrap -am clean install -DskipTests
mvn -pl fsd-bootstrap spring-boot:run
```

### 2. 启动前端

```bash
cd front
npm install
npm run dev
```

默认前端地址为 `http://localhost:3000`，开发代理将 `/api` 转发到后端服务。

### 3. 登录信息

系统启动后，使用以下默认账号密码登录：

| 角色 | 用户名 | 密码 | 权限 |
| --- | --- | --- | --- |
| 系统管理员 | admin | admin123 | 完整权限，包括用户管理、系统配置、数据管理 |
| 调度员 | operator | operator123 | 调度操作权限，包括派车、改派、异常处理 |
| 观察员 | viewer | viewer123 | 只读权限，可查看监控和报表 |

> **注意**：生产环境请务必修改默认密码！登录后可在系统管理页面修改密码。

## 开发命令

### 后端

| 命令 | 说明 |
| --- | --- |
| `mvn -pl fsd-bootstrap -am test` | 运行后端测试 |
| `mvn -pl fsd-bootstrap -am verify -Pquality` | 运行后端质量门禁 |
| `mvn -pl fsd-bootstrap spring-boot:run` | 启动后端应用 |

### 前端

| 命令 | 说明 |
| --- | --- |
| `npm run dev` | 启动 Vite 开发服务 |
| `npm run build` | TypeScript 检查并构建生产包 |
| `npm run typecheck` | 仅运行 TypeScript 检查 |
| `npm run lint` | 运行 ESLint |
| `npm run format:check` | 检查 Prettier 格式 |
| `npm run test:e2e` | 运行 Playwright 端到端测试 |
| `npm run perf:nav` | 运行导航性能测试 |
| `npm run perf:lighthouse` | 运行 Lighthouse 路由性能脚本 |

## 质量门禁

GitHub Actions 已配置两条主要流水线：

| Job | 关键步骤 |
| --- | --- |
| Backend Tests | JDK 21、`mvn -pl fsd-bootstrap -am test -B`、`mvn -pl fsd-bootstrap -am verify -Pquality -B`、上传 JaCoCo 报告 |
| Frontend Build | Node.js 20、`npm ci`、`npm run lint -- --max-warnings 50`、`npm run build`、安装 Playwright Chrome、运行 `npm run test:e2e` |

本地提交前建议至少运行：

```bash
cd front
npm run lint -- --max-warnings 50
npm run typecheck

cd ../back
mvn -pl fsd-bootstrap -am test
```

## 接口与入口

| 类型 | 地址 |
| --- | --- |
| 前端首页 | `http://localhost:3000` |
| 调度工作台 | `http://localhost:3000/workbench` |
| 调度看板 | `http://localhost:3000/dashboard` |
| 车辆监控 | `http://localhost:3000/vehicle-tracking` |
| 移动下单 | `http://localhost:3000/mobile/order` |
| 异常管理 | `http://localhost:3000/exception` |
| 移动端订单 | `http://localhost:3000/mobile/orders` |
| API 文档 | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/api-docs` |

## 配置说明

| 文件 | 说明 |
| --- | --- |
| `.env.example` | 根级环境变量示例 |
| `front/.env.example` | 前端环境变量示例 |
| `back/fsd-bootstrap/src/main/resources/application.yml` | 后端主配置 |
| `back/docker-compose.yml` | 后端目录内 Docker Compose 配置 |
| `back/docker-compose.mqtt.yml` | MQTT 相关 Compose 配置 |
| `back/docker-compose.observability.yml` | 可观测性相关 Compose 配置 |
| `back/docker-compose.ghcr.yml` | 镜像发布部署相关 Compose 配置 |

不要提交真实密钥、账号密码或本地 `.env` 文件。

## 文档与治理

| 文件 | 说明 |
| --- | --- |
| [back/README.md](back/README.md) | 后端模块、启动、测试与 Docker 说明 |
| [front/README.md](front/README.md) | 前端页面、开发、构建与环境说明 |
| [CHANGELOG.md](CHANGELOG.md) | 版本变更记录 |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 贡献流程 |
| [SECURITY.md](SECURITY.md) | 安全问题报告方式 |

## License

[MIT License](LICENSE) © 2026 DispatchFlow Contributors
