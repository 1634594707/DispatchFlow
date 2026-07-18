# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.5.0] - 2026-07-18

按 `docs/DispatchFlow_最终更新路线图_2026-07-18.md` 的 P0/P1 优先级与 `docs/真实配送范围与地图视觉规范_2026-07-18.md` 视觉规范，本轮迭代完成路线安全闭环、建筑 Polygon 数据补齐、车辆 8 状态视觉与订单目标点三层结构。

### Added

- 数据库迁移 `V43__route_safety_and_building_polygon.sql`：
  - 新表 `t_building_block`：真实 GeoJSON Polygon（非近似矩形），含 `block_type / polygon_geojson / source / map_version_id / is_hard_forbidden / default_expansion_buffer_meters / height_meters / gate_code`；初始化 7 个 ZJF-BLD-001~007 建筑块
  - 新表 `t_route_audit`：路线规划审计表，保存 `route_id / task_id / vehicle_id / park_id / map_version_id / route_mode / source / origin/destination_lng/lat / planned_polyline / actual_polyline / planned_length_meters / actual_length_meters / deviation_meters / reroute_count / collision_checked / crosses_building / crosses_river / unreachable_reason / status / planned_at / executed_at / completed_at`
  - 新表 `t_route_health_metric`：路线健康指标表，记录 `EMPTY_ROAD_NETWORK / DISCONNECTED / NOT_SNAPPED / OFF_ROAD / CROSSES_BUILDING / STRAIGHT_LINE_FALLBACK / RESERVATION_CONFLICT` 等指标
  - 新表 `t_dispatch_pause_state`：暂停派单全局开关，按园区维度控制；初始化 park_id=1/2 均为未暂停
  - `t_station_service_position` 增加 `stop_heading / enter_direction / leave_direction`，精确控制车辆到达服务位时的朝向与进出方向
  - `t_station` 增加 `station_confidence`（A/B/C 级，默认 C）；A=公开 POI 核验、B=候选待核验、C=合成点
  - `t_vehicle` 新增 8 字段：`height_cm / max_speed_kmh / current_speed_kmh / current_heading / manual_override / emergency_mode / safety_buffer_meters / current_map_version_id`
  - 所有 ALTER 通过 `information_schema.COLUMNS` 判重，可重复执行（幂等）
- 后端新增服务：
  - `RouteEndpointSnapper`（P0-3.3）：起终点吸附到最近道路节点（默认 50 米阈值）；超阈值返回 `START_OFF_ROAD / END_OFF_ROAD`
  - `RouteAuditService`（P0-3.5）：路线审计服务，支持 `saveRouteAudit / updateActualPolyline / incrementRerouteCount / listByVehicle`，规划 vs 实际对比与偏航分析
  - `DispatchPauseStateService`（P1-5）：统一控制自动派车 / 批量派车 / 紧急插队；提供 `isPaused / setPaused / requireNotPaused`
  - `GeoPolygonUtils.expandPolygon(polygon, expansionMeters)`（P0-4.3）：质心外推算法，将米转经纬度增量按纬度修正
- 后端管理 API 控制器：
  - POST `/api/admin/park/road-route/validate`：路线合法性校验（V43 增强契约）
  - GET `/api/admin/park/road-route/health`：道路路径健康检查
- 前端 `vehicleMapIcon.ts` 由 5 状态扩展为 8 状态（`assignable_idle / delivering / heading_charge / loading_unloading / waiting / charging / off_route / offline / manual_override`）；`COLOR_BY_STATUS` 按视觉规范 §5 映射状态色；`resolveAvMapStatus` 综合 `onlineStatus / manualOverride / routeInvalid / runtimeStage / charging / lowBattery / dispatchStatus / currentTaskId` 推断，优先级：offline > manual_override > off_route > loading_unloading > waiting > charging > heading_charge > delivering > assignable_idle；`LEGACY_TO_NEW` 提供旧状态向后兼容映射
- 前端 `useDeliveryGeo.ts` 新增 `buildOrderTargetOverlays(orders, options)` 函数（视觉规范 §6 订单目标点三层结构）：
  - 目标环：25m 半径 circle，状态色 + 8% fillOpacity
  - 服务位芯点：marker，status='core'
  - 道路接入虚线：待 V44 引入 accessNode 字段后补全
  - 新增辅助函数 `defaultOrderStageColor(stage)`
- 前端 `OperationsCockpit.vue` 新增地图数据状态栏（坐标系 / 地图版本 / 路线来源 / 层级 / 更新时间），删除"上线后的页面分工"占位板块
- 前端 `parkGeoMapLayers.ts` 的 `buildVehicleGeoMarkers` 传递 `currentTaskId / runtimeStage / routeInvalid` 字段到 `toAvGeoMarker`

### Changed

- `RoadRouteCollisionValidator`（P0-3.1 / P0-3.2）：
  - 对 `RoadRouteSource.STRAIGHT_LINE` + `allowStraightLine=false` 强制返回 `invalid`，禁止直线兜底进入执行队列
  - 空 polyline 校验由 `valid(0D)` 改为 `invalid(false, false, 0D)`，避免空路线误判通过
  - 新增 `applyValidation(result, profile)` 两层碰撞校验（中心线 + 车辆包络膨胀），按车辆 `safetyBufferMeters` 膨胀建筑块多边形（P0-4.4）
- `RoadRouteResult` 契约扩展为 16 字段（P1-2）：`routeId / mapVersion / segmentPath / routeMode / vehicleFit / collisionChecked / reservationStatus / snapDistanceMeters / maxOffRoadMeters`，向后兼容旧构造器
- `RoadRouteValidateRequest` 新增 13 字段（P1-3）：`parkId / mapVersion / coordSystem / originType / destinationType / routeMode / allowStraightLine / avoidBuilding / avoidRiver / avoidPedestrianZone / snapDistanceMeters / vehicleId / originAccessNodeCode / destinationAccessNodeCode`
- `RoadRouteValidateResponse` 新增 11 字段
- `RoadRouteValidateAdminService` 重写（P1-4）：集成 `RouteEndpointSnapper / RouteAuditService / VehicleRoutingProfile / MapDataVersionMapper`；流程：生成 routeId → 加载档案 → 起终点吸附 → 规划/解析 → 碰撞校验 → 直线回退拦截 → 保存审计 → 返回完整契约
- `VehicleRoutingProfile`（P1-1）从 `VehicleEntity` 加载（宽度 / 长度 / 转弯半径 / 允许道路等级 / 安全缓冲），注入每次建图与碰撞校验
- `RouteAuditService.java` 添加 `import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint`，修复编译错误

### Fixed

- V43 SQL 中 `LPAD(CONVERT(@rownum := @rownum + 1 USING utf8mb4), CHAR(3), '0')` 使用 `CHAR(3)` 返回 ASCII 控制字符 `\x03` 而非数字 3，导致 Flyway 迁移失败 "Data truncation: Truncated incorrect INTEGER value: '\x03'"；改用 `UNION ALL` 派生数字辅助表生成 7 行 + `LPAD(seq, 3, '0')` 替代
- Docker 镜像构建缓存导致旧 SQL 未更新；使用 `docker compose -f docker-compose.prod.yml build --no-cache backend` 强制无缓存重建后 Flyway 成功执行
- `RouteAuditService` 编译时报 `找不到符号 类 GeoPoint`；补充 import 后编译通过

### Deploy Notes

- 已部署到生产服务器 64.90.12.129，`/opt/dispatchflow`
- 4 个新表创建：`t_building_block / t_dispatch_pause_state / t_route_audit / t_route_health_metric`
- 7 个建筑块初始化：ZJF-BLD-001~007
- V43 迁移成功（success=1, execution_time=546ms）
- 外部 HTTPS 访问正常（`https://www.aplicity.online`，状态码 200）
- 前端工作台页面、后端健康检查均通过

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

[0.5.0]: https://github.com/1634594707/DispatchFlow/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/1634594707/DispatchFlow/compare/v0.3.1...v0.4.0
[0.3.0]: https://github.com/1634594707/DispatchFlow/compare/v2.0.0...v3.0.0
[0.2.0]: https://github.com/1634594707/DispatchFlow/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/1634594707/DispatchFlow/releases/tag/v0.1.0
