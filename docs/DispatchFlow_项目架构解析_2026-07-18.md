# DispatchFlow 项目架构解析

> 文档日期：2026-07-18
> 代码版本：v0.5.0（commit 在本轮提交后生成）
> 适用范围：整体架构、模块划分、核心功能实现、数据流程、技术栈选型、扩展性、关键代码使用示例

---

## 目录

1. [整体架构设计](#1-整体架构设计)
2. [模块划分](#2-模块划分)
3. [核心功能实现原理](#3-核心功能实现原理)
4. [数据流程与交互逻辑](#4-数据流程与交互逻辑)
5. [技术栈选型说明](#5-技术栈选型说明)
6. [扩展性设计与未来更新指南](#6-扩展性设计与未来更新指南)
7. [关键代码模块说明与使用示例](#7-关键代码模块说明与使用示例)

---

## 1. 整体架构设计

### 1.1 架构总览

DispatchFlow 采用 **前后端分离 + 多模块 Maven 工程 + 容器化部署** 的分层架构：

```
┌─────────────────────────────────────────────────────────────┐
│  浏览器 / 移动端 WebView                                       │
│  Vue 3.5 SPA（管理端 / 监控 / 移动端三态合一）                  │
└──────────────────────┬──────────────────────────────────────┘
                       │ HTTPS / SSE / WebSocket
┌──────────────────────▼──────────────────────────────────────┐
│  Nginx 反向代理（aplicity.online）                            │
│  - 静态资源：/ → frontend 容器（8081→80）                       │
│  - API 请求：/api/* → backend 容器（8080）                     │
└──────────────────────┬──────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────────┐
│  fsd-bootstrap（Spring Boot 3.3.12 启动入口）                 │
│  - FsdCoreApplication                                          │
│  - 全局拦截器 / 过滤器 / OpenAPI / 异常处理                     │
└──────────────────────┬──────────────────────────────────────┘
                       │
       ┌───────────────┼───────────────┬───────────────┐
       ▼               ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ fsd-admin-api│ │ fsd-dispatch │ │  fsd-order   │ │  fsd-vehicle │
│ 管理聚合 API  │ │ 调度+地图+   │ │  订单域       │ │  车辆域       │
│              │ │ Fleet+事件   │ │              │ │              │
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
       │                │                │                │
       └────────────────┴────────┬───────┴────────────────┘
                                 │
                                 ▼
                       ┌──────────────────┐
                       │    fsd-common    │
                       │ 枚举 / 异常 / 响应 │
                       │ 安全 / 坐标转换    │
                       └──────────────────┘
                                 │
       ┌─────────────────────────┼─────────────────────────┐
       ▼                         ▼                         ▼
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│   MySQL      │         │    Redis     │         │RabbitMQ/MQTT │
│ fsd_core DB  │         │ Fleet 运行态  │         │ 事件+车辆集成 │
│ Flyway 迁移  │         │ 会话 / 缓存   │         │ Outbox 模式   │
└──────────────┘         └──────────────┘         └──────────────┘
```

### 1.2 分层职责

| 层级 | 模块 | 职责 |
| --- | --- | --- |
| 接入层 | `front`、Nginx | 用户交互、PWA、地图渲染、移动端壳；路由分发与 TLS 终止 |
| API 聚合层 | `fsd-admin-api` | 管理端聚合 API、SSE 推送、运营分析、系统管理、Webhook |
| 业务层 | `fsd-dispatch`、`fsd-order`、`fsd-vehicle` | 调度核心（选车/路线/碰撞/MAPF）、订单生命周期、车辆指令与回报 |
| 公共层 | `fsd-common` | 枚举、异常、响应包装、坐标转换、字段加密 |
| 启动层 | `fsd-bootstrap` | Spring Boot 启动、全局拦截器、OpenAPI、JSON 日志 |
| 数据层 | MySQL、Redis、RabbitMQ | 业务持久化、运行态缓存、事件总线 |

### 1.3 部署架构

生产环境采用 Docker Compose 单机多容器编排（`docker-compose.prod.yml`）：

| 容器 | 镜像 | 端口 | 作用 |
| --- | --- | --- | --- |
| `fsd-frontend` | `dispatchflow-frontend:latest` | 8081→80 | Nginx 托管 Vue 构建产物，反向代理 `/api/*` 到 backend |
| `fsd-backend` | `dispatchflow-backend:latest` | 8080（仅内网） | Spring Boot 应用 |
| `fsd-mysql` | `mysql:8.0` | 3306（仅内网） | 业务数据库（`fsd_core`） |
| `fsd-redis` | `redis:7-alpine` | 6379（仅内网） | Fleet 运行态、会话 |
| `fsd-rabbitmq` | `rabbitmq:3.13-management` | 5672/15672 | 事件总线 |

外部访问统一通过 `https://www.aplicity.online`，由 Nginx 终止 TLS 后转发到对应容器。

---

## 2. 模块划分

### 2.1 后端 Maven 多模块

```
back/
├── fsd-common/         公共枚举 / 异常 / 响应模型 / 安全 / 坐标转换
├── fsd-order/          订单域：OrderEntity、OrderController、OrderService
├── fsd-vehicle/        车辆域：VehicleEntity、VehicleController、车辆回报
├── fsd-dispatch/       调度核心（最大模块）
│   ├── controller/     对外 API（DispatchController）
│   ├── entity/        20+ 实体（Dispatch/Park/Station/Road/Building/Route...）
│   ├── mapper/        MyBatis-Plus Mapper
│   ├── geo/            地理与路线（RoadRouteService、RouteEndpointSnapper、RouteAuditService...）
│   ├── road/           ParkRoadGraph 道路图
│   ├── mapf/           多智能体路径规划（MapfRoutePlannerService、MapfZonePartitioner）
│   ├── fleet/          FleetAdapter 仿真/真实车辆适配
│   ├── event/          DispatchDomainEvent、Outbox 模式
│   ├── service/        业务服务（DispatchTaskService、GeofenceBreachService...）
│   ├── scheduler/      TaskTimeoutScheduler
│   ├── config/         AmapProperties、Vda5050Configuration、ParkPilotProperties
│   └── dispatch/       DispatchAssignResult
├── fsd-admin-api/      管理端聚合 API（控制器 70+，DTO 30+，VO 50+）
├── fsd-bootstrap/      Spring Boot 启动入口、全局拦截器、OpenAPI
├── sql/migrations/     Flyway 迁移脚本（V1 → V43）
└── Dockerfile          Maven 多阶段构建
```

### 2.2 前端 Vue 工程

```
front/src/
├── api/               23 个 API 封装模块（按业务域拆分）
├── components/        业务组件（analytics/brand/command/common/infrastructure/layout/map/mobile...）
├── composables/       20 个组合式函数（useDeliveryGeo、useVehicleTracking、usePWAInstall...）
├── config/            全局配置（导航、主题、tokens）
├── constants/         常量与状态映射
├── layouts/           BasicLayout 布局骨架
├── maps/              地图核心模块（parkGeoMapLayers、vehicleMapIcon、routeValidation...）
├── router/            Vue Router 路由配置
├── stores/            Pinia 状态管理（auth/parkScope/workbench/vehicle/task...）
├── styles/            全局样式与主题变量
├── types/             TypeScript 类型定义（按业务域拆分）
├── utils/             通用工具（request、sseClient、dispatchStreamClient...）
├── views/             页面视图（按业务域分目录）
├── App.vue            根组件 + PWA 提示条
├── main.ts            入口
├── sw.ts              Service Worker
└── vite-env.d.ts      Vite 环境类型
```

### 2.3 视图模块划分

| 视图目录 | 页面 | 职责 |
| --- | --- | --- |
| `views/workbench/` | Index.vue、OperationsCockpit.vue | 调度工作台、运营驾驶舱 |
| `views/dashboard/` | Index.vue | 总览仪表盘 |
| `views/order/` | List.vue、Detail.vue | 订单列表与详情 |
| `views/task/` | List.vue、Detail.vue | 调度任务列表与详情 |
| `views/vehicle/` | List.vue、Detail.vue、Tracking.vue | 车辆管理、详情、实时追踪 |
| `views/digital-twin/` | Index.vue | 数字孪生（轨迹回放四态） |
| `views/infrastructure/` | 9 个页面 | 园区、站点、路网、停车位、充电桩、换电柜、围栏、交通管控、园区设置向导 |
| `views/analytics/` | 4 个页面 | 运营分析、充电报表、自定义报表、报表历史 |
| `views/system/` | 9 个页面 | 用户管理、操作日志、集成、系统健康、调度策略、告警设置等 |
| `views/vertical/` | 5 个页面 | 线路、枢纽、高峰、自动化规则、运营快照 |
| `views/field-ops/` | Tickets.vue | 现场工单 |
| `views/exception/` | Index.vue | 异常处置 |
| `views/mobile/` | 3 个页面 | 移动端订单、追踪、个人中心 |
| `views/auth/` | Login.vue | 登录 |
| `views/gis/` | ParkOverview.vue | 园区地理总览 |
| `views/dev/` | MapPoc.vue | 地图 PoC 调试页 |

---

## 3. 核心功能实现原理

### 3.1 调度选车算法

入口：`DispatchVehicleAssignServiceImpl.selectBestVehicle(order)`

调用链：

```
订单进入 → selectBestVehicle(order)
  ├─ 1. 基础校验（7 个 checkpoint）
  │     ├─ dispatchPauseControlService.isDispatchPaused()
  │     ├─ parkStationService.requireStation()
  │     ├─ parkStationService.assertStationInPark()
  │     ├─ hubCapacityService.isHubCapacityAvailable()
  │     ├─ dispatchRouteService.isRouteWithinServiceWindow()
  │     ├─ dispatchRouteService.isRouteOccupancyAvailable()
  │     └─ trafficZoneControlService.isPointInPausedZone()
  ├─ 2. 候选车辆过滤（5 层：SOC/维保/类型/车队/载重）
  ├─ 3. SOC 全链路校验（pickup + dropoff + return 充电桩）
  ├─ 4. 可达性校验（ParkRoadGraph A*）
  ├─ 5. 评分排序（SOC/距离/最近空闲时间/优先级）
  └─ 6. MAPF 冲突预约
```

任一 checkpoint 失败立即返回 `DispatchAssignResult.failure(reason)`。

### 3.2 路线安全闭环（v0.5.0 新增）

完整流程在 `RoadRouteValidateAdminService.validate(request)`：

```
请求进入 → 生成 routeId（UUID）
  ├─ 1. 加载 VehicleRoutingProfile（从 VehicleEntity）
  ├─ 2. 起终点吸附 RouteEndpointSnapper.snapToNearestNode()
  │     ├─ 超阈值 → 返回 START_OFF_ROAD / END_OFF_ROAD
  │     └─ 通过 → 记录 snapDistanceMeters
  ├─ 3. 路线规划
  │     ├─ 首选：ChainedRoadRouteService（高德驾车 + 本地路网）
  │     └─ 兜底：STRAIGHT_LINE（仅 allowStraightLine=true 时）
  ├─ 4. 碰撞校验 RoadRouteCollisionValidator.applyValidation(result, profile)
  │     ├─ 中心线碰撞校验
  │     └─ 车辆包络膨胀校验（按 safetyBufferMeters 膨胀建筑块）
  ├─ 5. 直线回退拦截（STRAIGHT_LINE + allowStraightLine=false → invalid）
  ├─ 6. 保存审计 RouteAuditService.saveRouteAudit(...)
  └─ 7. 返回 RoadRouteValidateResponse（16 字段契约）
```

关键设计：

- **空路线保护**：空 polyline 由 `valid(0D)` 改为 `invalid(false, false, 0D)`，避免空路线误判通过
- **两层碰撞**：中心线 + 车辆包络膨胀
- **审计表**：每次规划都写入 `t_route_audit`，支持规划 vs 实际对比与偏航分析

### 3.3 可通行地图模型（v0.4.0 引入，v0.5.0 增强）

四层统一地图模型：

| 层级 | 含义 | 数据载体 |
| --- | --- | --- |
| 地理事实 | 真实经纬度、建筑 Polygon、道路几何 | `t_road_segment.polyline_geojson`、`t_building_block.polygon_geojson` |
| 通行语义 | 道路等级、宽度、禁行状态、时间窗 | `t_road_segment.road_class/access_state/blocked_from/blocked_until` |
| 运营对象 | 站点、服务位、停车位、充电桩 | `t_station_service_position`、`t_parking_slot`、`t_charging_pile` |
| 展示层 | 前端图层（L0/L1/L2）、车辆状态色、订单目标点 | `AmapGeoMap.vue`、`vehicleMapIcon.ts` |

**ParkRoadGraph.fromDatabase(nodes, segments, now)**：动态过滤 `BLOCKED / PEDESTRIAN_ONLY` 路段与时间窗临时封路。

**GeoPolygonUtils.expandPolygon(polygon, expansionMeters)**：质心外推算法，按纬度修正米转经纬度增量。

### 3.4 Fleet 运行态

- **FleetAdapter** 抽象接口，区分 `SIM` 仿真与 `REAL` 真实车辆链路
- **PilotFleetSupport** 仿真适配器，按 tick 推进车辆位置
- **VDA5050 适配器** 通过 MQTT 与真实车辆通信
- **运行态存储**：Redis（`fsd.fleet.*` key），支持批量读取与高频率更新
- **车辆状态机**：`assignable_idle / delivering / heading_charge / loading_unloading / waiting / charging / off_route / offline / manual_override`

### 3.5 事件驱动与 Outbox 模式

```
业务操作 → DispatchDomainEvent
  ├─ 写入 t_outbox（同事务，保证一致性）
  └─ 异步投递 RabbitMQ
       ├─ WebhookDispatchListener（外部 Webhook）
       ├─ FleetAdapter.onDispatchEvent（车辆指令）
       └─ AdminStreamController SSE 推送到前端
```

事件类型（`DispatchEventType`）涵盖任务派发、状态变更、车辆回报、异常上报、能源事件等。

### 3.6 移动端与 PWA

- **Capacitor 6** 包装 Android 原生壳，复用 Vue 代码
- **vite-plugin-pwa** 生成 Service Worker（`sw.ts`），缓存策略：
  - 仅放行 GET 请求
  - 排除敏感路径（`/api/auth/`、`/api/admin/users/`、`/api/admin/operate-log/`）
  - maxAge 1 小时，maxEntries 50
- **PWA 4 状态模型**（`usePWAInstall.ts`）：`idle / dismissed / later / installed / never`，localStorage 持久化
- **iOS 安全区域**：`env(safe-area-inset-top)` 适配

### 3.7 实时推送

- **SSE**：`AdminStreamController` 暴露 `/api/admin/stream`，按 `parkId` 维度订阅事件流
- **SSE 连接注册表**：`sseConnectionRegistry.ts` 管理多连接、断线重连、心跳
- **数字孪生轨迹**：四态（PLAN/ACTUAL/PREDICTED/HISTORY）按颜色与线型区分

---

## 4. 数据流程与交互逻辑

### 4.1 下单 → 派单 → 完成的完整链路

```
┌─────────────┐
│ 移动端下单   │  POST /api/park/orders
└──────┬──────┘
       ▼
┌──────────────────────────────────────────┐
│ fsd-dispatch.ParkOrderService             │
│  - 创建 OrderEntity（status=PENDING）     │
│  - 发布 OrderCreatedEvent → Outbox        │
└──────┬───────────────────────────────────┘
       ▼
┌──────────────────────────────────────────┐
│ DispatchTaskService.autoDispatch()       │
│  - 调用 DispatchVehicleAssignService     │
│  - 选车成功 → 创建 DispatchTaskEntity     │
│  - 发布 TaskAssignedEvent                 │
└──────┬───────────────────────────────────┘
       ▼
┌──────────────────────────────────────────┐
│ FleetAdapter.dispatch(task, vehicle)     │
│  - SIM：PilotFleetSupport.tick 推进       │
│  - REAL：VDA5050 MQTT 下发车辆指令        │
└──────┬───────────────────────────────────┘
       ▼
┌──────────────────────────────────────────┐
│ VehicleController.reportTelemetry()      │
│  - 接收车辆位置/速度/SOC 上报             │
│  - 写入 Redis 运行态                      │
│  - 触发 GeofenceBreachService（围栏检测）  │
│  - 触发 TaskTimeoutScheduler（超时检测）   │
└──────┬───────────────────────────────────┘
       ▼
┌──────────────────────────────────────────┐
│ DispatchTaskService.markCompleted()      │
│  - 更新 Order status=DELIVERED            │
│  - 更新 DispatchTask status=COMPLETED     │
│  - 发布 TaskCompletedEvent                │
│  - 触发 FleetChargePolicy（低电量回充）   │
└──────────────────────────────────────────┘
```

### 4.2 前端实时数据流

```
浏览器
  ├─ Pinia Store（auth/parkScope/workbench/vehicle/task/realtime）
  │   ↑
  ├─ Composables（useVehicleTracking、useSseConnection、useDeliveryGeo...）
  │   ↑
  ├─ API 封装（axios + 拦截器）
  │   └─ RESTful：GET/POST/PUT/DELETE
  └─ SSE 连接（sseClient）
       └─ /api/admin/stream?parkId=1
            └─ 接收：VEHICLE_TELEMETRY / TASK_STATUS_CHANGED / EXCEPTION_RAISED
                 └─ 推送到 Pinia → 组件响应式更新
```

### 4.3 地图渲染数据流

```
后端 API
  ├─ /api/admin/park/snapshot          → ParkVehicleSnapshot / ParkOrderSnapshot / ParkStation
  └─ /api/admin/park/geo-transform     → GCJ-02 坐标转换
       ▼
前端 composables/useDeliveryGeo.ts
  ├─ buildVehicleGeoMarkers(vehicles)  → 车辆 markers（8 状态色）
  ├─ buildOrderTargetOverlays(orders)  → 订单目标三层（环 + 芯点 + 接入虚线）
  ├─ buildStationOverlays(stations)   → 站点 markers
  └─ buildRoutePolylines(routes)       → 路线 polylines
       ▼
前端 components/map/AmapGeoMap.vue
  ├─ L0_COVERAGE  层（产业带 10km）
  ├─ L1_CORE      层（试点 14）
  ├─ L1_CONNECTOR 层（连接器）
  ├─ L2_EXTERNAL  层（外部 16）
  └─ showLevelSwitcher：用户切换层级
       ▼
高德地图 JS SDK 渲染
```

### 4.4 数据库迁移流程

```
fsd-bootstrap 启动 → Flyway 扫描 classpath:db/migration
  ├─ 对比 flyway_schema_history 表
  ├─ 按版本号顺序执行未应用的迁移
  ├─ V1 ~ V43 累计 43 个迁移脚本
  └─ 失败 → 容器启动失败（docker-compose 重启策略触发重试）
```

**幂等设计**：V42/V43 所有 `ALTER TABLE` 通过 `information_schema.COLUMNS` 判重，可重复执行。

---

## 5. 技术栈选型说明

### 5.1 前端技术栈

| 技术 | 版本 | 选型理由 |
| --- | --- | --- |
| Vue 3.5 | 3.5.x | Composition API + `<script setup>` + 性能优化；社区生态成熟 |
| TypeScript | 5.7 | 类型安全，重构友好；与 Vite 配合快 |
| Vite | 6 | 极快 HMR；按需 polyfill；PWA 插件集成 |
| Ant Design Vue | 4.x | 企业级管理后台标准组件库，表格/表单/抽屉开箱即用 |
| Pinia | 2.x | Composition-friendly 状态管理，DevTools 友好 |
| Vue Router | 4.x | 官方路由，懒加载与导航守卫 |
| Leaflet / 高德 JS SDK | - | 地图渲染：高德主图 + Leaflet 辅助图层 |
| Axios | 1.x | HTTP 客户端，拦截器机制成熟 |
| @fontsource | - | 自托管字体，规避 Google Fonts CDN 在中国大陆的延迟与 CSP 违规 |
| vite-plugin-pwa | 0.x | 渐进式 Web App，离线缓存与可安装性 |
| Capacitor | 6 | 移动端原生壳，复用 Vue 代码降低维护成本 |
| Playwright | - | E2E 测试，跨浏览器支持 |

### 5.2 后端技术栈

| 技术 | 版本 | 选型理由 |
| --- | --- | --- |
| Java | 21 | LTS，虚拟线程 + 模式匹配 + Record，性能与开发体验双赢 |
| Spring Boot | 3.3.12 | 主流应用框架，自动配置与生态完备 |
| MyBatis-Plus | 3.5.x | 增强 MyBatis，通用 CRUD + 条件构造器 + 代码生成 |
| SpringDoc OpenAPI | 1.x | OpenAPI 3 文档自动生成，Swagger UI |
| Lombok | - | 样板代码消除（getter/setter/equals） |
| MapStruct | - | 编译期 DTO/Entity 映射，性能优于运行期反射 |
| Flyway | - | 数据库版本控制，迁移脚本可追溯 |
| H2 | - | 测试数据库，无外部依赖 |
| Maven 多模块 | - | 模块化构建，依赖管理统一 |

### 5.3 数据与消息

| 技术 | 选型理由 |
| --- | --- |
| MySQL 8.0 | 业务持久化主库；JSON 字段支持 Polygon 与 polyline 存储 |
| Redis 7 | Fleet 运行态高频读写；会话管理；分布式锁 |
| RabbitMQ 3.13 | 事件总线；Outbox 模式保证事件可靠投递 |
| Mosquitto | MQTT Broker，对接 VDA5050 协议的真实车辆 |

### 5.4 工程化与运维

| 技术 | 选型理由 |
| --- | --- |
| Maven 多阶段 Docker 构建 | 编译环境与运行环境隔离，最终镜像精简 |
| Docker Compose | 单机编排，部署简单，适合当前规模 |
| GitHub Actions | CI/CD，与 GitHub 仓库无缝集成 |
| Prometheus + Filebeat | 指标与日志采集 |
| ESLint + Prettier + vue-tsc | 前端代码质量门禁 |
| Checkstyle + SpotBugs + JaCoCo | 后端代码质量门禁，覆盖率统计 |

---

## 6. 扩展性设计与未来更新指南

### 6.1 横向扩展点

| 扩展场景 | 实现方式 |
| --- | --- |
| 新增园区 | `t_park` 插入记录 → 初始化站点/路网/服务位 → 配置 MapDataVersion |
| 新增车辆类型 | 扩展 `VehicleType` 枚举 → 在 `VehicleRoutingProfile` 配置通行档案 |
| 新增道路等级 | 扩展 `t_road_segment.road_class` → 在 `ParkRoadGraph.fromDatabase` 配置过滤规则 |
| 新增建筑障碍物 | `t_building_block` 插入 Polygon → 设置 `is_hard_forbidden / default_expansion_buffer_meters` |
| 新增事件类型 | 扩展 `DispatchEventType` → 实现 `DispatchEventPublisher.publish` 分发逻辑 |
| 新增车辆链路 | 实现 `FleetAdapter` 接口 → 注册到 `FleetAdapterRegistry` |
| 新增管理 API | 在 `fsd-admin-api/controller` 添加控制器 → 实现 service → 暴露 OpenAPI |

### 6.2 数据库迁移规范

**新增迁移脚本**：

1. 文件名：`V{next}__{feature_name}.sql`，放在 `back/sql/migrations/`
2. 所有 `ALTER TABLE` 必须通过 `information_schema.COLUMNS` 判重
3. `CREATE TABLE` 使用 `IF NOT EXISTS`
4. `INSERT` 使用 `WHERE NOT EXISTS` 防重
5. **不要使用** `CHAR(N)` 作为 `LPAD` 的长度参数（会返回 ASCII 控制字符）
6. 多行生成使用 `UNION ALL` 派生数字辅助表，避免依赖会话变量

**示例模板**：

```sql
USE `fsd_core`;

-- 幂等 ALTER
SET @sql := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_xxx'
     AND COLUMN_NAME = 'new_field') = 0,
  'ALTER TABLE `t_xxx` ADD COLUMN `new_field` VARCHAR(64) DEFAULT NULL COMMENT ''新字段''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 幂等 CREATE
CREATE TABLE IF NOT EXISTS `t_xxx` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  ...
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 幂等 INSERT
INSERT INTO `t_xxx` (...) SELECT ... FROM (SELECT 1 AS seq UNION ALL SELECT 2) AS s
WHERE NOT EXISTS (SELECT 1 FROM `t_xxx` WHERE ... LIMIT 1);
```

### 6.3 前端扩展指南

**新增页面**：

1. 在 `views/<domain>/` 创建 `.vue` 文件
2. 在 `router/index.ts` 添加路由配置（懒加载 `() => import(...)`)
3. 在 `config/navigation.ts` 添加菜单项
4. 在 `api/<domain>.ts` 添加 API 封装
5. 在 `types/<domain>.d.ts` 添加类型定义
6. 在 `stores/<domain>.ts` 添加 Pinia store（如需要）

**新增地图图层**：

1. 在 `maps/parkGeoMapLayers.ts` 添加图层构造函数
2. 在 `maps/types.ts` 扩展 `GeoMapMarker / GeoMapCircle / GeoMapPolyline` 类型
3. 在 `components/map/AmapGeoMap.vue` 添加图层挂载逻辑
4. 在 `composables/useDeliveryGeo.ts` 暴露图层构造方法

**新增车辆状态**：

1. 在 `maps/vehicleMapIcon.ts` 的 `AvMapStatus` 类型添加状态值
2. 在 `ICON_BY_STATUS` 添加 SVG 图标
3. 在 `COLOR_BY_STATUS` 添加状态色（参考视觉规范 §5）
4. 在 `resolveAvMapStatus` 添加优先级判断
5. 在 `LEGACY_TO_NEW` 添加旧状态映射（如需向后兼容）

### 6.4 后端扩展指南

**新增业务模块**：

1. 在 `fsd-dispatch` 下创建 `entity/`、`mapper/`、`service/`、`controller/`（或新建 Maven 模块）
2. 实体继承 ` BaseEntity`（含 `id/createdAt/updatedAt/version/deleted`）
3. Mapper 继承 `BaseMapper<T>`
4. Service 实现 `IService<T>`
5. 在 `fsd-admin-api` 添加管理 API 控制器
6. 在 `fsd-bootstrap` 配置拦截器与 OpenAPI
7. 新增 Flyway 迁移脚本

**新增路线策略**：

1. 实现 `RoadRouteService` 接口
2. 在 `ChainedRoadRouteService` 链中注册顺序
3. 在 `RoadRouteSource` 枚举添加来源标识
4. 在 `RouteUnreachableReason` 添加不可达原因
5. 在 `RouteMetricsCalculator` 补充指标计算（如需）

### 6.5 部署扩展指南

**升级现有部署**：

```bash
# 1. 上传新代码到服务器
sftp deploy@your-server.example.com
  put -r back/ /opt/dispatchflow/back/
  put -r front/dist/ /opt/dispatchflow/front/dist/

# 2. 重建后端镜像（含 SQL 迁移）
cd /opt/dispatchflow/back
docker compose -f ../docker-compose.prod.yml build --no-cache backend

# 3. 重建前端镜像
cd /opt/dispatchflow/front
docker compose -f ../docker-compose.prod.yml build --no-cache frontend

# 4. 重启容器
docker compose -f /opt/dispatchflow/docker-compose.prod.yml up -d

# 5. 验证
curl http://localhost:8080/internal/actuator/health
curl http://localhost:8081/
curl https://www.aplicity.online/
```

**关键注意事项**：

- 修改 SQL 迁移脚本后必须 `--no-cache` 重建 backend 镜像，否则 jar 内嵌的旧 SQL 不会更新
- Flyway 失败后需先清理 `flyway_schema_history` 中失败的记录，再重试
- 健康端点为 `/internal/actuator/health`，返回 `{"status":"UP"}` 表示就绪

---

## 7. 关键代码模块说明与使用示例

### 7.1 后端关键模块

#### 7.1.1 DispatchVehicleAssignServiceImpl

**作用**：选车算法核心实现。

**位置**：`back/fsd-dispatch/src/main/java/com/fsd/dispatch/dispatch/DispatchVehicleAssignServiceImpl.java`

**使用示例**：

```java
@Autowired
private DispatchVehicleAssignService assignService;

public void handleNewOrder(OrderEntity order) {
    DispatchAssignResult result = assignService.selectBestVehicle(order);
    if (result.isSuccess()) {
        // 创建 DispatchTaskEntity，发布 TaskAssignedEvent
        dispatchTaskService.createTask(order, result.getVehicle());
    } else {
        // 进入任务池等待
        log.warn("订单 {} 派单失败：{}", order.getId(), result.getFailReason());
    }
}
```

#### 7.1.2 RoadRouteValidateAdminService

**作用**：路线合法性校验（v0.5.0 增强）。

**位置**：`back/fsd-admin-api/src/main/java/com/fsd/admin/service/`（实现类）

**使用示例**：

```java
@Autowired
private RoadRouteValidateAdminService validateService;

public void checkRoute(Long parkId, BigDecimal originLng, BigDecimal originLat,
                      BigDecimal destLng, BigDecimal destLat, Long vehicleId) {
    RoadRouteValidateRequest request = new RoadRouteValidateRequest();
    request.setParkId(parkId);
    request.setOriginLng(originLng);
    request.setOriginLat(originLat);
    request.setDestinationLng(destLng);
    request.setDestinationLat(destLat);
    request.setVehicleId(vehicleId);
    request.setAllowStraightLine(false);  // 禁止直线兜底
    request.setAvoidBuilding(true);

    RoadRouteValidateResponse response = validateService.validate(request);
    if (!response.isValid()) {
        log.warn("路线不可达：{} - {}", response.getUnreachableReason(), response.getUnreachableDetail());
    }
}
```

#### 7.1.3 ParkRoadGraph

**作用**：园区道路图，A* 寻路。

**位置**：`back/fsd-dispatch/src/main/java/com/fsd/dispatch/road/ParkRoadGraph.java`

**使用示例**：

```java
List<RoadNodeEntity> nodes = roadNodeMapper.selectList(...);
List<RoadSegmentEntity> segments = roadSegmentMapper.selectList(...);
LocalDateTime now = LocalDateTime.now();

// 动态过滤 BLOCKED/PEDESTRIAN_ONLY 与时间窗封路
ParkRoadGraph graph = ParkRoadGraph.fromDatabase(nodes, segments, now);

RoadNodeEntity start = graph.findNearestNode(originLng, originLat);
RoadNodeEntity end = graph.findNearestNode(destLng, destLat);

List<RoadSegmentEntity> path = graph.findShortestPath(start, end, vehicleRoutingProfile);
```

#### 7.1.4 RouteEndpointSnapper（v0.5.0 新增）

**作用**：起终点吸附到最近道路节点。

**位置**：`back/fsd-dispatch/src/main/java/com/fsd/dispatch/geo/RouteEndpointSnapper.java`

**使用示例**：

```java
@Autowired
private RouteEndpointSnapper snapper;

SnapResult startSnap = snapper.snapToNearestNode(parkId, originLng, originLat, 50.0);
if (!startSnap.isSnapped()) {
    return RoadRouteValidateResponse.unreachable(START_OFF_ROAD,
        "起点距离最近道路节点 " + startSnap.getDistanceMeters() + " 米，超过 50 米阈值");
}
```

#### 7.1.5 RouteAuditService（v0.5.0 新增）

**作用**：路线规划审计，支持规划 vs 实际对比。

**位置**：`back/fsd-dispatch/src/main/java/com/fsd/dispatch/geo/RouteAuditService.java`

**使用示例**：

```java
@Autowired
private RouteAuditService auditService;

// 1. 规划阶段保存
RouteAuditEntity audit = auditService.saveRouteAudit(
    routeId, taskId, vehicleId, parkId,
    mapVersionId, "REAL_ROAD", "LOCAL_GRAPH",
    originLng, originLat, destLng, destLat,
    plannedPolyline, plannedLengthMeters,
    true,  // collisionChecked
    false, // crossesBuilding
    false  // crossesRiver
);

// 2. 执行阶段回填实际轨迹
auditService.updateActualPolyline(routeId, actualPolyline, actualLengthMeters, deviationMeters);

// 3. 重规划计数
auditService.incrementRerouteCount(routeId);

// 4. 查询某车辆的所有审计记录
List<RouteAuditEntity> history = auditService.listByVehicle(vehicleId, 100);
```

#### 7.1.6 DispatchPauseStateService（v0.5.0 新增）

**作用**：暂停派单全局开关。

**位置**：`back/fsd-dispatch/src/main/java/com/fsd/dispatch/.../DispatchPauseStateService.java`

**使用示例**：

```java
@Autowired
private DispatchPauseStateService pauseService;

// 自动派单前校验
pauseService.requireNotPaused(parkId);  // 抛 BusinessException 如已暂停

// 切换暂停状态
pauseService.setPaused(parkId, true, "维护窗口");
pauseService.setPaused(parkId, false, null);

// 查询状态
boolean isPaused = pauseService.isPaused(parkId);
```

#### 7.1.7 GeoPolygonUtils

**作用**：多边形膨胀算法。

**位置**：`back/fsd-dispatch/src/main/java/com/fsd/dispatch/geo/GeoPolygonUtils.java`

**使用示例**：

```java
// 建筑块原始 Polygon（GCJ-02 坐标点数组）
List<double[]> buildingPolygon = buildingBlockEntity.getPolygonCoordinates();

// 按车辆安全缓冲膨胀（如 0.5 米 + 默认缓冲 0.5 米 = 1.0 米）
List<double[]> expandedPolygon = GeoPolygonUtils.expandPolygon(buildingPolygon, 1.0);

// 用于碰撞校验
boolean intersects = GeoPolygonUtils.polylineIntersectsPolygon(routePolyline, expandedPolygon);
```

### 7.2 前端关键模块

#### 7.2.1 vehicleMapIcon.ts（v0.5.0 重写）

**作用**：车辆 8 状态视觉映射。

**位置**：`front/src/maps/vehicleMapIcon.ts`

**使用示例**：

```typescript
import { resolveAvMapStatus, getVehicleIcon, getVehicleColor } from '@/maps/vehicleMapIcon'

const status = resolveAvMapStatus({
  onlineStatus: vehicle.onlineStatus,
  manualOverride: vehicle.manualOverride,
  routeInvalid: vehicle.routeInvalid,
  runtimeStage: vehicle.runtimeStage,
  charging: vehicle.charging,
  lowBattery: vehicle.batteryStatus === 'LOW',
  dispatchStatus: vehicle.dispatchStatus,
  currentTaskId: vehicle.currentTaskId,
})
// status: 'assignable_idle' | 'delivering' | 'heading_charge' | 'loading_unloading' | 'waiting' | 'charging' | 'off_route' | 'offline' | 'manual_override'

const icon = getVehicleIcon(status)      // 返回 SVG 字符串
const color = getVehicleColor(status)    // 返回 hex 颜色值
```

#### 7.2.2 useDeliveryGeo.ts

**作用**：配送地理组合式函数，封装覆盖物构造。

**位置**：`front/src/composables/useDeliveryGeo.ts`

**使用示例**：

```typescript
import { useDeliveryGeo } from '@/composables/useDeliveryGeo'

const {
  buildVehicleGeoMarkers,
  buildOrderTargetOverlays,
  buildStationOverlays,
  buildRoutePolylines,
} = useDeliveryGeo()

// 1. 车辆 markers（8 状态色）
const vehicleMarkers = buildVehicleGeoMarkers(vehicles)
// 返回：L.Marker[] with AvMapStatus icon

// 2. 订单目标点三层结构
const { circles, markers, polylines } = buildOrderTargetOverlays(orders, {
  showTargetRing: true,
  showCore: true,
  showAccessLine: false, // 待 V44 引入 accessNode 后启用
})

// 3. 站点 markers
const stationMarkers = buildStationOverlays(stations)

// 4. 路线 polylines
const routeLines = buildRoutePolylines(routes)
```

#### 7.2.3 AmapGeoMap.vue

**作用**：高德地图主组件，支持 L0/L1/L2 三层切换。

**位置**：`front/src/components/map/AmapGeoMap.vue`

**使用示例**：

```vue
<template>
  <AmapGeoMap
    :center="[121.0825, 31.9620]"
    :zoom="14"
    :show-level-switcher="true"
    :level="'L1_CORE'"
    :markers="vehicleMarkers"
    :circles="orderTargetCircles"
    :polylines="routeLines"
    :polygons="buildingPolygons"
    @map-ready="onMapReady"
    @level-change="onLevelChange"
  />
</template>

<script setup lang="ts">
import AmapGeoMap from '@/components/map/AmapGeoMap.vue'
import { ref } from 'vue'

const vehicleMarkers = ref([])
const orderTargetCircles = ref([])
const routeLines = ref([])
const buildingPolygons = ref([])

function onMapReady(map) {
  console.log('地图就绪', map)
}
function onLevelChange(level) {
  console.log('切换层级', level)
}
</script>
```

#### 7.2.4 usePWAInstall.ts

**作用**：PWA 安装提示 4 状态模型。

**位置**：`front/src/composables/usePWAInstall.ts`

**使用示例**：

```typescript
import { usePWAInstall } from '@/composables/usePWAInstall'

const {
  state,              // 'idle' | 'dismissed' | 'later' | 'installed' | 'never'
  canShowPrompt,      // computed boolean
  showPrompt,         // 显示提示
  dismissInstall,     // 暂不（24 小时冷却）
  laterInstall,       // 稍后提醒（7 天冷却）
  neverShowAgain,     // 不再提醒
  triggerInstall,     // 触发浏览器安装
} = usePWAInstall()
```

#### 7.2.5 sseClient.ts

**作用**：SSE 客户端，与后端 `/api/admin/stream` 对接。

**位置**：`front/src/utils/sseClient.ts`

**使用示例**：

```typescript
import { createSseClient } from '@/utils/sseClient'

const client = createSseClient({
  url: '/api/admin/stream',
  params: { parkId: 1 },
  withCredentials: true,
  onMessage: (event) => {
    switch (event.type) {
      case 'VEHICLE_TELEMETRY':
        vehicleStore.updateTelemetry(event.data)
        break
      case 'TASK_STATUS_CHANGED':
        taskStore.updateStatus(event.data)
        break
      case 'EXCEPTION_RAISED':
        exceptionStore.add(event.data)
        break
    }
  },
  onError: (err) => console.error('SSE 错误', err),
})

client.connect()
// client.disconnect()  // 组件卸载时断开
```

### 7.3 数据库关键表

#### 7.3.1 核心业务表

| 表名 | 用途 |
| --- | --- |
| `t_park` | 园区主表 |
| `t_order` | 订单 |
| `t_dispatch_task` | 调度任务 |
| `t_vehicle` | 车辆 |
| `t_station` | 站点 |
| `t_road_node` / `t_road_segment` | 路网节点与路段 |
| `t_charging_pile` / `t_charging_session` | 充电桩与会话 |
| `t_parking_slot` | 停车位 |
| `t_park_geofence` | 地理围栏 |

#### 7.3.2 v0.4.0+ 引入的扩展表

| 表名 | 版本 | 用途 |
| --- | --- | --- |
| `t_station_service_position` | V42 | 站点服务位子表 |
| `t_station_service_position_reservation` | V42 | 服务位预约 |
| `t_map_data_version` | V42 | 地图数据版本 |
| `t_building_block` | V43 | 建筑物与障碍物真实 Polygon |
| `t_route_audit` | V43 | 路线规划审计 |
| `t_route_health_metric` | V43 | 路线健康指标 |
| `t_dispatch_pause_state` | V43 | 暂停派单状态 |

#### 7.3.3 关键查询示例

```sql
-- 查询某车辆的所有路线审计
SELECT * FROM t_route_audit
WHERE vehicle_id = ?
ORDER BY planned_at DESC
LIMIT 100;

-- 查询某园区当前激活的地图版本
SELECT * FROM t_map_data_version
WHERE park_id = ? AND is_active = 1;

-- 查询所有 A 级站点（公开 POI 核验）
SELECT * FROM t_station
WHERE station_confidence = 'A' AND deleted = 0;

-- 查询当前未暂停的园区
SELECT * FROM t_dispatch_pause_state
WHERE is_paused = 0;
```

---

## 附录

### A. 关键 API 端点速查

| 端点 | 方法 | 用途 |
| --- | --- | --- |
| `/api/auth/login` | POST | 管理端登录 |
| `/api/park/orders` | POST | 移动端下单 |
| `/api/dispatch/workbench` | GET | 调度工作台数据 |
| `/api/dispatch/tasks/pool` | GET | 任务池查询 |
| `/api/dispatch/tasks/{id}/assign` | POST | 手动派单 |
| `/api/admin/stream` | GET (SSE) | 实时事件流 |
| `/api/admin/park/road-route/validate` | POST | 路线合法性校验（v0.5.0 增强） |
| `/api/admin/park/road-route/health` | GET | 道路路径健康检查 |
| `/api/admin/infrastructure/stations/{id}/service-positions` | GET | 站点服务位列表 |
| `/api/admin/infrastructure/map-versions/active` | GET | 当前激活地图版本 |
| `/internal/actuator/health` | GET | 健康检查（仅内网） |

### B. 关键配置项

| 配置 | 文件 | 默认值 |
| --- | --- | --- |
| `fsd.pwa.install.state.v1` | localStorage | idle |
| `fsd.fleet.energy.minAssignableSoc` | application.yml | 20 |
| `fsd.fleet.energy.lowBatteryThreshold` | application.yml | 30 |
| `fsd.dispatch.lock.timeout` | application.yml | 30s |
| `fsd.amap.api-key` | application.yml | 高德 API Key |
| `fsd.vda5050.broker-url` | application.yml | MQTT Broker URL |
| `JAVA_OPTS` | Dockerfile | -Xms1024m -Xmx1536m |

### C. 部署与运维命令

```bash
# 查看容器状态
docker compose -f /opt/dispatchflow/docker-compose.prod.yml ps

# 查看后端日志
docker logs -f --tail 200 fsd-backend

# 查看后端启动日志（搜索 Started）
docker logs fsd-backend 2>&1 | grep "Started FsdCoreApplication"

# 查看 Flyway 迁移历史
docker exec fsd-mysql mysql -uroot -p"Fsd_Mysql_2026!Str0ng" fsd_core \
  -e "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 10;"

# 重启单个容器
docker compose -f /opt/dispatchflow/docker-compose.prod.yml restart backend

# 强制重建镜像（修改 SQL 后必须）
docker compose -f /opt/dispatchflow/docker-compose.prod.yml build --no-cache backend

# 健康检查
curl http://localhost:8080/internal/actuator/health
curl https://www.aplicity.online/

# 进入 MySQL
docker exec -it fsd-mysql mysql -uroot -p"Fsd_Mysql_2026!Str0ng" fsd_core
```

### D. 文档参考

| 文档 | 用途 |
| --- | --- |
| `docs/DispatchFlow_最终更新路线图_2026-07-18.md` | 本期迭代路线图（P0/P1/P2 优先级） |
| `docs/真实配送范围与地图视觉规范_2026-07-18.md` | 配送范围与地图视觉规范（L0/L1/L2、车辆 8 状态、订单目标点） |
| `docs/DispatchFlow_底层架构解析.md` | 选车算法与地图泊车专题分析 |
| `docs/运维手册-园区配置与围栏设计.md` | 园区配置与围栏设计运维手册 |
| `docs/运维手册-评分权重与能量阈值.md` | 评分权重与能量阈值运维手册 |
| `docs/坐标基准-叠石桥家纺城.md` | 叠石桥家纺城坐标基准 |
| `CHANGELOG.md` | 版本变更记录（v0.1.0 → v0.5.0） |
| `README.md` | 项目总览与快速启动 |

---

**文档维护**：本文档随项目迭代同步更新，最新版本对应 `v0.5.0`。新增功能模块或架构调整时，请在对应章节追加说明并更新日期。
