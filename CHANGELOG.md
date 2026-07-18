# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.4.0] - 2026-07-18

按 `docs/地图与交互问题清单.md` 的 P0/P1/P2 优先级建立可通行地图模型闭环。

### Added

- 数据库迁移 `V42__navigable_map_model.sql`：
  - `t_road_segment` 增加 `width_meters / road_class / access_state / polyline_geojson / allowed_vehicle_types / turn_restriction / gate_code / block_reason / blocked_from / blocked_until`
  - `t_station` 增加 `anchor_node_code / service_direction / allowed_vehicle_types / unreachable_reason / unreachable_until`
  - `t_charging_pile` 增加 `entry_node_code / exit_node_code / plug_type / reservation_state / estimated_release_at`
  - `t_vehicle` 增加 `width_cm / length_cm / turning_radius_m / allowed_road_classes`
  - `t_parking_slot` 增加 `facing_direction / entry_node_code / exit_node_code / blocking_main_road`
  - 新表 `t_station_service_position`、`t_station_service_position_reservation`、`t_map_data_version`
  - 初始化 ZJF 试点园区的道路等级、宽度、站点接入节点
- 后端新增实体：`StationServicePositionEntity`、`StationServicePositionReservationEntity`、`MapDataVersionEntity` 与对应 Mapper
- 后端新增服务：
  - `StationServicePositionLockService`（P1-10）：`lockServicePosition / reserveServicePosition / releaseServicePosition / sweepExpiredReservations / findAvailablePositions / findActiveReservationByVehicle`
  - `StationServicePositionAdminService` 与实现（P0-5/P1-7/P1-10/P2-6）：站点服务位 CRUD + 地图数据版本 CRUD + 激活切换
  - `RouteMetricsCalculator`（P1-5）：计算路线长度、ETA、等待时间、充电时间、风险点
  - `RouteUnreachableReason`（P1-4）：15 种细分不可达原因枚举
  - `VehicleRoutingProfile`（P1-3）：车辆通行档案，按类型 / 等级 / 宽度过滤
- 后端 `ParkRoadGraph.fromDatabase(nodes, segments, now)`（P1-2）：跳过 `BLOCKED / PEDESTRIAN_ONLY` 路段与时间窗临时封路
- 后端管理 API 控制器 `StationServicePositionController`：
  - GET `/api/admin/infrastructure/stations/{stationId}/service-positions`
  - GET `/api/admin/infrastructure/stations/{stationId}/service-positions/available`
  - POST/PUT `/api/admin/infrastructure/service-positions[/{id}]`
  - POST `/api/admin/infrastructure/service-positions/{id}/toggle-status|delete`
  - GET `/api/admin/infrastructure/map-versions[?parkId=]`
  - GET `/api/admin/infrastructure/map-versions/active?parkId=`
  - POST `/api/admin/infrastructure/map-versions` / POST `/api/admin/infrastructure/map-versions/{id}/activate`
- 前端类型扩展：
  - `phase10.d.ts` 新增 `lastUpdatedAt / mapDataVersion` 字段
  - `park.d.ts` 新增 `TrajectoryPointType` 与车辆尺寸字段
  - `infrastructure.d.ts`、`vehicle.d.ts` 补齐所有 V42 新增字段
- 前端数字孪生增强（`views/digital-twin/Index.vue`）：
  - 区分 PLAN / ACTUAL / PREDICTED / HISTORY 四类轨迹（不同颜色与线型）
  - 运行阶段进度可视化
  - 顶部显示数据最后更新时间戳（>30s 红色警示）与地图数据版本标签
  - 车辆详情抽屉新增尺寸与通行约束分区
- 前端基础设施列表扩展（P2-5）：`RoadNetwork.vue / StationList.vue / ChargingPileList.vue / ParkingSlotList.vue / vehicle/List.vue / vehicle/Detail.vue` 均新增字段列
- 编码扫描脚本 `scripts/scan_encoding.py`（P0-2 / P2-10）：扫描 BOM / 非 UTF-8 / GBK 误解码 / U+FFFD 替换符；当前 1073 文件 0 问题

### Changed

- `RoadRouteValidateAdminService` 接入 `RouteMetricsCalculator` 与 `RouteUnreachableReason`，返回扩展后的 `RoadRouteValidateResponse`
- `AdminRoadSegmentResponse / AdminStationResponse / AdminChargingPileResponse / AdminParkingSlotResponse` 补齐对应实体新增字段
- `InfrastructureAdminServiceImpl` 的 `toRoadSegmentResponse / toStationResponse / toChargingPileResponse / toParkingSlotResponse` 输出 V42 新增字段
- `App.vue` PWA 提示条改为 4 状态模型 UI（安装 / 暂不 / 不再提醒），`role="dialog"`、`tabindex="-1"`、`:focus-visible` 键盘可访问
- `usePWAInstall.ts` 引入 localStorage 持久化：`idle / dismissed / later / installed / never` 五态，24 小时与 7 天冷却期

### Fixed

- P0-1 PWA 安装提示「暂不」按钮点击后状态未持久化，刷新或路由切换后提示再次出现
- `beforeinstallprompt` 事件重触发覆盖用户已选择的冷却状态
- 站点默认落在建筑中心点导致路径穿越建筑（V42 新增 `t_station_service_position` 子表后站点可配置服务位）
- 路段无方向 / 宽度 / 转向限制 / 通行语义（V42 新增字段后 `ParkRoadGraph` 能按 `access_state` 与时间窗过滤）

## [0.3.1] - 2026-07-18

### Added

- 自托管字体：通过 `@fontsource` 引入 Geist / Geist Mono / Plus Jakarta Sans，替代 Google Fonts CDN
- 全局错误处理器：Vue `errorHandler`、`unhandledrejection`、资源加载错误三类捕获
- PWA Manifest 完善：`id`、`maskable` 图标 purpose、桌面快捷方式（调度工作台 / 车辆监控 / 运营分析）
- Guard Mode 动态 `theme-color`：切换值守模式时同步更新 Android 地址栏颜色
- iOS 安全区域适配：移动端 header 增加 `env(safe-area-inset-top)` padding，避免状态栏遮挡

### Changed

- CSP 策略：新增 `worker-src 'self' blob:` 修复高德地图矢量瓦片 Worker 创建
- CSP 策略：`connect-src` 中 `wss: ws:` 收紧为 `wss://*.aplicity.online`
- Vite 构建配置：新增 `target: 'es2020'`、`chunkSizeWarningLimit: 1000`、`sourcemap` 按 analyze 模式开启
- iOS 状态栏：`apple-mobile-web-app-status-bar-style` 由 `black-translucent` 改为 `black`

### Fixed

- Google Fonts 样式表被 CSP `style-src` 阻断（生产环境字体回退到系统字体）
- 高德地图 Worker 创建被 CSP 阻断，矢量瓦片渲染降级到主线程导致卡顿
- `App.vue` 模板中 ref 误用 `.value`，改用 composable 暴露的 `dismissInstall()` 方法
- `request.ts` 响应拦截器内 `const pinia` 重复声明遮蔽外层变量
- 移动端 `background-attachment: fixed` 触发滚动重绘卡顿（`@media (pointer: coarse)` 禁用）

### Security

- Service Worker API 缓存仅放行 GET 请求
- 排除敏感路径缓存：`/api/auth/`、`/api/admin/users/`、`/api/admin/operate-log/`
- API 缓存条目数 100 → 50，缓存时长 1 天 → 1 小时

## [0.3.0] - 2026-05-31

### Added

- Phase 12–14: trajectory replay, engine simulation, vertical (routes/hub/peak/automation rules), swap cabinets, field ops, 2FA
- Phase 15: VDA5050 MQTT adapter, REAL swap sessions, task pool server pagination (V20), Redis fleet batch read
- Flyway migration baseline, Swagger OpenAPI tags, ESLint/Prettier, JaCoCo, Prometheus metrics
- MAPF architecture evaluation and VDA5050 delivery documentation

### Changed

- Frontend version 0.3.0, Vite manualChunks, SSE connection registry
- GitHub Release **v3.0.0** supersedes v2.0.0 as latest

## [0.2.0] - 2026-05-25

### Added

- Fleet runtime layer: `FleetRuntimeService` with Redis persistence
- Simulation adapter: `SimulationFleetAdapter` decouples tick simulation from business state
- Unified energy policy: `FleetChargePolicy` and `fsd.fleet.energy.*` configuration
- Exception severity (`INFO` / `WARN` / `ERROR` / `CRITICAL`) and resolve actions
- Auto-resolve OPEN exceptions on successful reassignment
- SQL migration `V6__exception_severity.sql`
- Project documentation: architecture, deployment guide, roadmap
- GitHub Actions CI for backend tests and frontend build
- Root `docker-compose.yml` for one-command infrastructure startup

### Changed

- Vehicle monitoring reads fleet runtime from Redis instead of in-memory simulation map
- Full-charge plugged-in vehicles enter STANDBY (assignable, no drain)
- README rewritten as formal project documentation
- Branding unified to **DispatchFlow** across frontend and backend metadata

### Fixed

- Simulation charging: vehicles now charge to full SOC before leaving
- Battery drain during charging and transit to charger
- Exception spam: deduplication and INFO-level audit-only offline events
- Tracking panel layout and header overlap

## [0.1.0] - 2026-05

### Added

- Multi-park support (`t_park`, `t_station`)
- Admin dashboard, order/task/vehicle/exception management
- Full-screen vehicle tracking map with Leaflet
- Mobile park order page
- Auto dispatch, manual dispatch, vehicle state machine simulation
- RabbitMQ event publishing with Outbox pattern
- Docker Compose for MySQL, Redis, RabbitMQ, and backend

[0.3.0]: https://github.com/1634594707/DispatchFlow/compare/v2.0.0...v3.0.0
[0.2.0]: https://github.com/1634594707/DispatchFlow/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/1634594707/DispatchFlow/releases/tag/v0.1.0
