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
| 前端 | Vue 3.5、TypeScript 5.7、Vite 6、Ant Design Vue、Pinia、Vue Router、Leaflet、Axios、@fontsource 自托管字体、vite-plugin-pwa |
| 移动端 | Capacitor 6、Android 原生构建 |
| 后端 | Java 21、Spring Boot 3.3.12、MyBatis-Plus、SpringDoc OpenAPI、Lombok、MapStruct |
| 数据与消息 | MySQL、Redis、RabbitMQ、H2 测试数据库 |
| 工程化 | Maven 多模块、ESLint、Prettier、vue-tsc、Playwright、JaCoCo、Checkstyle、SpotBugs |
| 部署与运维 | Docker Compose、GitHub Actions、Prometheus、Filebeat、Mosquitto |

## 近期前端改进

本轮迭代聚焦 CSP 安全、字体自托管、PWA 完善与移动端体验：

- **CSP 修复**：新增 `worker-src 'self' blob:` 放行高德地图矢量瓦片 Worker；收紧 `connect-src` 中 WebSocket 白名单至 `wss://*.aplicity.online`
- **字体自托管**：通过 `@fontsource` 引入 Geist / Geist Mono / Plus Jakarta Sans，移除 Google Fonts CDN 依赖，消除中国大陆访问延迟与 CSP 违规
- **PWA 完善**：Manifest 补全 `id` / `maskable` 图标 / 桌面快捷方式；iOS 状态栏改为 `black` 并适配 `env(safe-area-inset-top)` 防遮挡
- **Service Worker 加固**：API 缓存仅放行 GET 请求，排除 `/api/auth/`、`/api/admin/users/`、`/api/admin/operate-log/` 等敏感路径，maxAge 由 1 天缩短至 1 小时
- **全局错误处理**：新增 Vue `errorHandler`、`unhandledrejection`、资源加载错误三类全局捕获
- **移动端性能**：触屏设备禁用 `background-attachment: fixed`，避免滚动重绘卡顿
- **值守模式增强**：Guard Mode 切换时同步更新 `<meta name="theme-color">`，Android 地址栏颜色跟随主题
- **代码质量**：修正 `App.vue` 模板中 ref 的 `.value` 误用；清理 `request.ts` 中重复的 pinia 变量声明；Vite 构建补全 `target: 'es2020'` 与 sourcemap 控制

## 可通行地图模型（v0.4.0 新增）

按 `docs/地图与交互问题清单.md` 要求，本轮迭代建立"地理事实 / 通行语义 / 运营对象 / 展示层"四层统一地图模型，让前端展示、后端路网与车辆轨迹共用同一套数据源：

- **P0-1 PWA 安装提示 4 状态模型**：`usePWAInstall.ts` 引入 `idle / dismissed / later / installed / never` 五态持久化（localStorage `fsd.pwa.install.state.v1`），「暂不」24 小时冷却、「稍后提醒」7 天冷却、「不再提醒」永久拒绝；`beforeinstallprompt` 重触发不再覆盖冷却状态；`App.vue` 提示条支持 `role="dialog"`、`tabindex="-1"`、`:focus-visible` 键盘可访问
- **P0-2 UTF-8 编码扫描**：新增 `scripts/scan_encoding.py`，扫描 1073 个源文件的 BOM、UTF-8 解码失败、GBK 误解码、U+FFFD 替换符等模式；当前仓库 0 问题
- **P0-3~P0-5 + P1-1~P1-2 统一可通行地图迁移（V42）**：新增 `V42__navigable_map_model.sql`，为 `t_road_segment` 增加 `width_meters / road_class / access_state / polyline_geojson / allowed_vehicle_types / turn_restriction / gate_code / block_reason / blocked_from / blocked_until`；为 `t_station` 增加 `anchor_node_code / service_direction / allowed_vehicle_types / unreachable_reason / unreachable_until`；为 `t_charging_pile` 增加 `entry_node_code / exit_node_code / plug_type / reservation_state / estimated_release_at`；为 `t_vehicle` 增加 `width_cm / length_cm / turning_radius_m / allowed_road_classes`；为 `t_parking_slot` 增加 `facing_direction / entry_node_code / exit_node_code / blocking_main_road`；并初始化 ZJF 试点园区的道路等级 / 宽度 / 站点接入节点数据
- **P0-5 站点服务位子表**：新增 `t_station_service_position` 与 `t_station_service_position_reservation`，避免站点默认落在建筑中心点；一个站点可配置多个服务位，每个服务位有坐标、接入节点、服务方向、容量、状态、预约车辆与过期时间
- **P1-2 道路图动态过滤**：`ParkRoadGraph.fromDatabase(nodes, segments, now)` 跳过 `access_state=BLOCKED / PEDESTRIAN_ONLY` 的路段，并按 `blocked_from / blocked_until` 时间窗跳过临时封路
- **P1-3 车辆通行档案**：新增 `VehicleRoutingProfile`，包含 `isVehicleTypeAllowed / isRoadClassAllowed / fitsRoadWidth`，预留 `DEFAULT_SAFETY_BUFFER_METERS=0.5`
- **P1-4 不可达原因枚举**：新增 `RouteUnreachableReason`，覆盖 15 种细分原因（`ROAD_NETWORK_EMPTY / NO_PATH_ON_GRAPH / START_OFF_ROAD / END_OFF_ROAD / CROSSES_BUILDING / CROSSES_RIVER / VEHICLE_TOO_WIDE_FOR_ROAD / VEHICLE_TYPE_NOT_ALLOWED_ON_ROAD / ROAD_CLASS_NOT_ALLOWED_FOR_VEHICLE / ROAD_TEMPORARILY_BLOCKED / GATE_CLOSED / NO_SERVICE_POSITION_CONFIGURED / STATION_OUT_OF_SERVICE / SERVICE_POSITION_OCCUPIED / INSUFFICIENT_SOC`）
- **P1-5 路线指标**：新增 `RouteMetrics` 与 `RouteMetricsCalculator`，按段长度 + 限速计算 `totalLengthMeters / estimatedTravelSeconds / waitingSeconds / chargingSeconds / riskPoints`；`RoadRouteValidateAdminService` 返回扩展后的 `RoadRouteValidateResponse`
- **P1-6 充电桩进出站**：充电桩实体增加 `entry_node_code / exit_node_code / plug_type / reservation_state / estimated_release_at`
- **P1-8 待命区约束**：停车位实体增加 `facing_direction / entry_node_code / exit_node_code / blocking_main_road`
- **P1-10 服务位预约 / 锁定服务**：新增 `StationServicePositionLockService`，提供 `lockServicePosition / reserveServicePosition / releaseServicePosition / sweepExpiredReservations / findAvailablePositions / findActiveReservationByVehicle`；同一服务位最多一个 ACTIVE 预约，同一车辆最多持有一个 ACTIVE 服务位预约
- **P2-1~P2-5 数字孪生增强**：前端 `views/digital-twin/Index.vue` 区分 PLAN / ACTUAL / PREDICTED / HISTORY 四类轨迹（不同颜色与线型）；运行阶段进度可视化；顶部显示数据最后更新时间戳（>30s 红色警示）与地图数据版本标签；车辆详情抽屉新增尺寸与通行约束分区
- **P2-5 基础设施列表扩展**：`RoadNetwork.vue / StationList.vue / ChargingPileList.vue / ParkingSlotList.vue / vehicle/List.vue / vehicle/Detail.vue` 均新增字段列，覆盖宽度、道路等级、通行语义、转向限制、门禁、接入节点、服务方向、不可达原因、进出站节点、充电枪类型、预约状态、车位朝向、阻塞主路、车辆尺寸与允许道路等级
- **P2-6 地图数据版本表**：新增 `t_map_data_version` 与 `MapDataVersionEntity / Mapper / AdminService`，能追溯某次调度使用的路网版本；支持按园区查询、激活版本切换（自动取消同园区其他版本激活态）
- **P2-7~P2-10 PWA 与构建前检查**：PWA 4 状态已写入 localStorage；提示条键盘可操作（`tabindex` / `:focus-visible`）；所有抽屉与通知统一使用 `v-model:open` 父级状态；`scripts/scan_encoding.py` 可作为构建前编码扫描入口

### 新增管理 API（v0.4.0）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/infrastructure/stations/{stationId}/service-positions` | 列出某站点的所有服务位 |
| GET | `/api/admin/infrastructure/stations/{stationId}/service-positions/available` | 查询某站点当前可用服务位 |
| POST | `/api/admin/infrastructure/service-positions` | 新增服务位 |
| PUT | `/api/admin/infrastructure/service-positions/{positionId}` | 更新服务位 |
| POST | `/api/admin/infrastructure/service-positions/{positionId}/toggle-status` | 切换服务位状态 |
| POST | `/api/admin/infrastructure/service-positions/{positionId}/delete` | 删除服务位（软删除） |
| GET | `/api/admin/infrastructure/map-versions` | 列出地图数据版本 |
| GET | `/api/admin/infrastructure/map-versions/active?parkId=1` | 查询某园区当前激活的地图版本 |
| POST | `/api/admin/infrastructure/map-versions` | 新增地图数据版本 |
| POST | `/api/admin/infrastructure/map-versions/{versionId}/activate` | 激活某地图版本 |

`RoadRouteValidateResponse` 新增字段：`unreachableReason / unreachableDetail / totalLengthMeters / estimatedTravelSeconds / waitingSeconds / chargingSeconds / riskPoints`

`AdminRoadSegmentResponse / AdminStationResponse / AdminChargingPileResponse / AdminParkingSlotResponse` 均补齐对应实体新增字段，前端基础设施列表已展示

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
