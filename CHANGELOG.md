# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
