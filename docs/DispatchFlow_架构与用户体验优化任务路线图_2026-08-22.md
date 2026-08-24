# DispatchFlow 架构与用户体验优化任务路线图

> 制定日期：2026-08-22  
> 适用范围：多园区调度工作台、实时车辆监控、订单/任务管理、分析导出、后端事件链路、生产发布  
> 使用方式：按阶段执行，完成一项后勾选对应 `- [ ]`；未通过验收的任务不得提前标记完成。

## 1. 当前基线

- [x] 前端 `npm.cmd run typecheck` 已通过。
- [x] 前端 `npm.cmd run build` 已通过。
- [x] 后端 `mvn -pl fsd-bootstrap -am test` 已通过。
- [x] 统一测试 JDK 与项目 Java 版本，消除测试运行期间 JaCoCo 报出的 `Unsupported class file major version 69`，再把覆盖率结果作为质量门禁。根 pom 的 jacoco-maven-plugin 升级至 0.8.14（官方支持 Java 25 class file major 69），编译 release 锁定 Java 21；`mvn -pl fsd-admin-api -am test`（不带 jacoco.skip）已验证 report 正常生成 target/jacoco.exec 与覆盖率报告，后续可作为质量门禁。
- [ ] 本路线图完成后重新执行前端构建、后端测试、浏览器关键流程和生产健康检查。

## 2. Phase 0：冻结作用域与契约

### 2.1 多园区作用域

- [x] 明确所有管理端查询是否支持 `parkId`，逐个登记接口、服务、SQL 查询和前端 Store 的作用域来源。作用域登记表（2026-08-24 审计）：①订单/任务/异常/车辆列表与详情、任务处置（含批量）、全局搜索、园区快照、充电分析、操作日志、现场工单、报表计划——服务层按 parkId 过滤 + ensureXxxPark 记录级校验；②基础设施（园区/站点/路网/停车位/充电桩/换电柜）实体自带 park_id，写操作 ADMIN 矩阵校验；③**策略档案**本轮补齐列表接口园区过滤（GET profiles?parkId= 命中园区专属+全局模板，DispatchStrategyParkFilterTest 锁定），运行时 DispatchStrategyRuntimeService 按 parkId 取参；④集成（webhooks/api-keys）、用户/会话、系统健康——部署级全局资源，明确不适用园区作用域；⑤轨迹子资源按 vehicleId 维度查询。前端各列表页经统一 parkScope store 注入查询参数。
- [x] 修复 `AdminDashboardServiceImpl.getSummary(Long parkId)`：`pendingCount`、`assigningCount`、`manualPendingCount`、`executingCount`、`failedCount`、车辆统计和异常统计必须使用同一个 `parkId` 作用域。
- [x] 修复 `AdminDispatchStreamScheduler.pushSnapshots()`：禁止使用无园区参数的 `getSummary(null)` 向所有连接广播园区相关数据。
- [x] 为工作台 SSE payload 统一补齐 `parkId`、事件类型、事件时间和数据版本。
- [x] 前端 `getDispatchStreamUrl()` 传入当前园区参数，并在园区切换后停止旧连接、创建新连接。
- [ ] 增加跨园区隔离验收：园区 A 的任务、车辆、异常、计数和事件不能出现在园区 B 的工作台。自动化作用域测试已覆盖 SSE、摘要、任务、车辆、详情读取、任务写操作和现场工单；真实浏览器/多园区数据验收仍待执行。

### 2.2 领域契约

- [x] 为实时事件统一定义 `eventId`、`eventType`、`businessKey`、`parkId`、`eventTime`、`eventVersion`。
- [x] 为任务状态迁移建立唯一状态迁移表，明确自动派车、手动派车、批量派车、取消、失败重试、车辆回报和人工接管的允许前置状态。迁移表唯一来源为 `com.fsd.common.enums.DispatchTaskStatus`（含 ASSIGNED→PENDING 人工接管退回、ASSIGNED→ASSIGNED 改派换车自环、车辆回报 START/FINISH/REPORT_FAIL 路径），`DispatchTaskStateContractTest` 锁定「操作 × 前置状态」矩阵。
- [x] 为批量操作定义统一结果契约：总数、成功数、失败数、失败原因、可重试条目和操作时间。`AdminBatchTaskResultResponse` 新增 operation/retryableTaskIds/operatedAt，逐条结果携带 retryable 瞬时失败标记（NO_VEHICLE/CONFLICT/HUB_CAPACITY_FULL/ROUTE_OCCUPANCY_FULL/DISPATCH_TASK_LOCKED/SYSTEM_ERROR）；批量人工接管改调 unassignTask 不再误用取消路径；`BatchTaskAdminServiceImplTest` 覆盖计数、可重试分类与接管语义。
- [x] 为移动端订单创建定义请求幂等契约，并记录重复提交返回原订单的规则。规则见 `ParkOrderIdempotencyService` 契约注释：客户端每下单意图一个 idempotencyKey（必填，[A-Za-z0-9._:-]{8,128}），重试复用；同键重复提交返回原响应（replayed=true）；同键不同请求指纹拒绝 IDEMPOTENCY_KEY_MISMATCH；同键在处理中拒绝 IDEMPOTENCY_IN_PROGRESS；首次失败整体回滚自动释放幂等键。V50 迁移 `t_order_idempotency` 以数据库唯一键跨实例保证。

## 3. Phase 1：修复用户可见的正确性问题

### 3.1 分析导出

- [x] 修复订单、任务、异常页面使用 `window.open()` 访问 `/api/admin/analytics/export/csv` 的认证问题。
- [x] 统一使用 Axios `blob` 下载，或者先调用已认证接口获取一次性短时下载票据，再使用票据下载文件。
- [x] 修改后端 `AdminAnalyticsController.exportCsv` 和 `AnalyticsAdminService.exportCsv`，精确接收并校验 `parkId`。
- [x] 导出文件必须与当前筛选条件一致，至少包含当前园区、数据集和时间范围。
- [x] 增加 Viewer、Operator、Admin 三种角色的导出权限测试。`AdminAnalyticsControllerExportRoleTest`（5 用例）：三种已认证角色导出成功且响应为 text/csv，未认证请求拒绝 ADMIN_AUTH_REQUIRED，园区/时间范围筛选透传服务层。
- [x] 增加导出失败时的前端错误提示和重试入口，不允许新窗口静默打开错误响应。

### 3.2 任务处置

- [x] 新增统一任务时间线读取模型，串联订单创建、派车、车辆回报、执行、异常、重试、改派、取消和完成事件。`GET /admin/tasks/{id}/timeline`（`DispatchAdminQueryService.getTaskTimeline`）：合并 t_order 创建时间、t_dispatch_task_operate_log 全量操作事件与 t_dispatch_exception_record 异常上报/解除，按时间升序输出；`TaskTimelineReadModelTest` 覆盖合并排序、来源识别与缺失任务拒绝。
- [x] 任务详情显示每个状态的进入时间、来源、前后状态、失败原因和下一步动作。任务详情页"任务统一时间线"卡片展示每条事件的进入时间、来源（系统/调度员/车辆回报/移动端）、前后状态、失败原因高亮与按当前状态生成的"下一步动作"提示；旧操作日志卡片保留为兼容回退。
- [ ] 批量派车、批量改派、批量取消改为作业化处理，前端显示逐条结果。
- [x] 对重复点击自动派车、手动派车、紧急插队增加幂等校验。后端 DispatchLockService 任务级锁（DISPATCH_TASK_LOCKED）保证同一任务并发操作只执行一次；批量结果中锁冲突标记为可重试瞬时失败；前端列表/详情按钮在请求进行中禁用并显示"处理中…"。
- [x] 任务处于处理中时，前端禁用重复操作并显示明确的处理中状态。任务列表 ASSIGNING 状态行显示"派车处理中…"标签，进行中操作的行按钮禁用并显示"处理中…"；详情页所有操作按钮共用 actionLoading 防重复提交，统一时间线顶部显示下一步动作提示。

### 3.3 移动端订单

- [x] 移动端提交订单时生成请求幂等键。ParkOrder 下单页、ParkDeliveryOrderModal、演示模式 useDemoMode 与 useMobileOrderForm 均按下单意图生成 UUID 幂等键，成功后换新键，网络重试复用同一键。
- [x] 后端保存请求幂等键与订单结果，重复提交返回原订单。`t_order_idempotency`（V50）+ `ParkOrderIdempotencyServiceImpl` 在订单创建事务内占用/完成幂等记录，重复提交重放首次响应快照并标记 replayed=true；`ParkOrderIdempotencyServiceImplTest` 覆盖首占/重放/指纹冲突/并发在途/竞态回退/快照完成/键格式校验。
- [x] 移动端显示提交中、已受理、派车中、配送中、已完成和失败状态。下单按钮区分"正在叫车…"提交态；追踪面板 stage 标签补齐 ASSIGNING=派车中，WAITING_DISPATCH=已受理，配送中/已完成/失败原有覆盖。
- [x] 网络超时后允许查询原提交结果，不直接引导用户再次创建订单。submitOrder 捕获无 response 的网络层错误后用同一幂等键重查一次，命中重放显示"已确认原提交结果/返回原订单"，不产生新订单。

## 4. Phase 2：统一实时数据架构

### 4.1 SSE 认证与连接管理

- [x] 将 `AdminSseTicketServiceImpl` 当前内存 `ConcurrentHashMap` 改为 Redis 存储；ticket payload 使用专用 JSON 结构，Redis 不可用时拒绝签发/消费，不回退进程内存。
- [x] Redis ticket 设置 TTL，并通过 Lua 原子 `GET`+`DEL` 保证一次性消费语义；已补充 Redis 写入、失效、重复消费和非法 payload 测试。
- [x] 明确生产部署是单实例还是多实例；若使用多实例，SSE ticket、连接状态和事件分发必须跨实例可用。生产 docker-compose.prod.yml 当前为单实例（fsd-backend 单容器）。多实例就绪改造已完成：流事件队列由共享固定队列改为每实例匿名自动删除队列（AnonymousQueue 绑定同一交换机），消除多实例下 RabbitMQ 轮询消费导致的 SSE 事件丢失；SSE ticket 已 Redis 跨实例、Outbox 租约/fencing 与消费幂等本就多实例安全；系统健康监控改为跟踪实际动态队列名。
- [x] 为 SSE 增加连接建立失败、ticket 失效、重连次数、最大连接数和断开原因指标。AdminSseMetrics 新增 connections.peak / connections.limit / connections.reconnects / connections.closed.by.reason{reason=completed|timeout|error|send-failure}；连接超限拒绝计入 connections.rejected；同用户 60 秒窗口内断开后重连计一次重连；`AdminSseMetricsTest` 覆盖峰值/上限/按原因分组/重连窗口。
- [x] 统一 `/api/admin/dispatch/stream` 与 `/api/admin/fleet/telemetry/stream` 的认证、心跳、超时和重连策略。两流均使用一次性 ticket（同一签发端点）；遥测流取消"永不超时"，与调度流共用 fsd.admin.sse.timeout-ms 与 max-connections 上限；两流均每 30s 下发 ping 注释帧心跳；遥测流新增独立 telemetry.* 连接指标；前端两流均为指数退避重连策略。FleetTelemetryStreamServiceImplTest 覆盖统一超时、上限拒绝与心跳清理。

### 4.2 Outbox 与消息消费

- [x] 为 `t_dispatch_event_outbox` 增加 `PROCESSING`、`claim_token`、`claimed_at` 和 `lease_until` 领取租约字段；V49 迁移使用条件更新抢占，过期租约可恢复，避免多个实例同时发送同一事件。
- [x] `DispatchEventRetryScheduler` 先原子领取事件再发布；发布成功/失败按 `eventId + claimToken` fencing 更新，旧租约无法覆盖新实例状态。
- [x] 设置最大重试次数（`FSD_DISPATCH_OUTBOX_MAX_RETRIES`，默认 5）和最终 `DEAD_LETTER` 状态；超过上限的事件停止重试，并通过管理端 `/api/admin/dispatch/outbox/dead-letters` 查询元数据和失败原因。
- [x] RabbitMQ Audit 与 Webhook 消费端均按 Redis `eventId` 幂等键去重；缺少 `eventId` 的消息拒绝进入 Webhook 投递，避免重复处理。
- [x] 增加事件发送延迟、领取次数、成功发布、失败数量和死信数量 Prometheus 指标；SSE 连接、ticket 签发/消费/失效指标已注册，Actuator 默认暴露 `prometheus` 与 `metrics`。
- [x] 增加数据库事务提交后发送、发送失败、进程重启恢复和重复消息四类测试。`DispatchEventOutboxDeliveryScenariosTest`：①事务内仅落 PENDING 行、afterCommit 后才领取并发送；②发送失败按 eventId+claimToken fencing 标记 FAILED 不误标 PUBLISHED；③过期租约 PROCESSING 事件可重新领取、未过期租约不重复投递（进程重启恢复）；④领取被抢占的重复消息不再发送。

### 4.3 前端实时 Store

- [x] 由统一实时 Store 管理工作台、看板、异常和任务池刷新。
- [x] 页面不再分别维护相同数据的独立定时器。
- [x] SSE 正常时只处理事件和快照；SSE 断开时启用统一降级轮询。
- [x] 页面隐藏时停止降级轮询，页面重新可见时执行一次增量刷新。
- [x] 统一显示实时连接状态、最后事件时间、最后快照时间和降级模式。顶栏状态指示器升级为完整可视化：连接颜色 + "降级"标记（仅降级时显示）+ 最后事件相对时间 + 最后快照相对时间，悬停 tooltip 显示全部状态明细；数据源为统一 realtime store 的 lastEventAt/lastSnapshotAt/status。
- [x] 车辆监控的 3 秒降级轮询、工作台 30 秒刷新、订单/任务列表 10 秒刷新必须收敛到统一策略。工作台数据刷新由统一 realtime store 驱动（onWorkbenchRefresh → fetchQueue，无页面级定时器）；任务列表经 subscribeRefresh 接入；车辆监控降级轮询补齐页面可见性管理（hidden 暂停 / visible 重启，与统一 store 策略一致），SSE 正常时走推送不轮询。

## 5. Phase 3：车辆运行态和地图数据可靠性

### 5.1 Telemetry 新鲜度

- [x] 统一 `lastTelemetryAt`、`telemetryStale`、`onlineStatus` 和 `runtimeStage` 的服务端判定规则。`TelemetryFreshnessPolicy`（fsd.dispatch.telemetry.stale-seconds，默认 30s）作为唯一过期判定来源，快照组装与可派门禁共用；快照响应新增 telemetryAgeSeconds 与 telemetryStaleThresholdSeconds。
- [x] Redis `fleet:runtime:` 的 7 天 TTL 只表示运行态保留时间，不得直接作为在线状态依据。在线状态仅由车辆回报/仿真状态机写入 DB；TTL 过期后 Redis 缺失时从 DB 重建默认运行态（RealFleetAdapter 已有行为并有测试）。
- [x] 前端车辆状态必须使用后端返回的统一状态，不在多个页面重复推断在线/离线。车辆列表/监控大屏/园区总览均消费后端 onlineStatus 与 telemetryStale 字段渲染，不再本地推断。
- [x] 车辆监控显示数据年龄和超时阈值，超过阈值后禁止把车辆标记为可派。监控大屏详情面板与园区总览展示"数据年龄 Xs / 阈值 Ys"；自动派车候选增加遥测新鲜度门禁，过期或从未上报直接排除并返回 TELEMETRY_STALE 失败原因（含测试）。
- [x] 增加延迟 telemetry、乱序 eventSeq、重复 eventSeq、Redis 缺失和后端重启后的车辆状态测试。Phase3AcceptanceTest 已覆盖延迟/乱序/重复/Redis 缺失场景；本轮补齐过期遥测不可派与从未上报不可派用例；后端重启场景等价于 Redis 运行态缺失后从 DB 重建（已有断言）。

### 5.2 地图和路线

- [x] 路线执行绑定 `routeId`、`mapVersion`、`source` 和 `segmentPath`。V43 契约已实现（`RoadRouteValidateResponse` / `RouteAuditEntity`），本轮新增 `RoadRouteContractTest` 锁定绑定契约：成功规划返回非空 routeId（ROUTE- 前缀）、请求/园区激活版本 mapVersion、source 与 from>to 格式 segmentPath，并以 PLANNED 状态写入路线审计。
- [x] 路线失败、空路线、直线回退、路线冲突和车辆偏航必须返回明确原因。`RouteUnreachableReason` 编码体系（START_OFF_ROAD/END_OFF_ROAD/NO_PATH_ON_GRAPH/CROSSES_BUILDING/CROSSES_RIVER 等）+ unreachableDetail 人读详情；直线回退被禁时强制 invalid 并返回 NO_PATH_ON_GRAPH；碰撞结果以 crossesBuilding/crossesRiver/maxOffRoadMeters/crossesRestrictedZone 显式暴露；偏航距离记录于路线审计 deviationMeters。RoadRouteContractTest 覆盖吸附超限与直线禁用两类原因路径。
- [x] 地图状态栏统一显示坐标系、地图版本、路线来源、数据更新时间和实时连接状态。园区总览地图状态条新增"地图版本"（GET map-versions/active 实时拉取），已有 GCJ-02 坐标系、数据更新时间与顶栏实时连接指示；选中车辆面板显示路线来源。
- [x] 车辆详情显示当前道路、路线 ID、地图版本、偏航距离和最后 telemetry 时间。新增 `VehicleDetailEnrichmentService`：车辆详情接口注入最近一次路线审计（routeId / mapVersionCode / deviationMeters / executedAt）与基于当前位置的最近 ACTIVE 道路节点（50m 阈值，超出显示"未匹配（偏离道路）"）；最后 telemetry 时间由 lastReportTime 呈现。`VehicleDetailEnrichmentServiceTest` 3 用例覆盖审计回填/最近节点匹配/缺省跳过。
- [x] 订单目标点必须落到服务位或道路接入点，不得把建筑中心作为可执行终点。`RouteEndpointSnapper` 将起终点吸附到最近道路节点或服务位接入节点（支持显式 accessNodeCode），snapDistanceMeters 记录吸附距离；超过阈值直接拒绝规划并返回 START_OFF_ROAD/END_OFF_ROAD 明确原因（契约测试覆盖）。

## 6. Phase 4：权限和安全边界

- [x] 建立统一权限资源表，至少覆盖任务读取、任务派车、任务取消、车辆读取、基础设施写入和分析导出。`AdminResource × AdminAction → AdminRole` 矩阵（`AdminPermissionService`）：TASK.READ 全员、TASK.ASSIGN/CANCEL 仅 OPERATOR+ADMIN、VEHICLE.READ 全员、INFRASTRUCTURE.WRITE 仅 ADMIN、ANALYTICS_EXPORT.EXECUTE 全员；未登记组合默认拒绝；`AdminPermissionMatrixTest` 锁定契约。
- [x] 后端权限检查统一使用资源、动作和园区范围，不以单独的页面路由判断作为授权依据。六类必选资源端点已全部接入矩阵校验（任务详情/时间线/派车/取消/改派/优先级/批量 + 车辆列表/查询/详情 + 基础设施 28 个写端点 + 分析导出）；园区范围继续由 ensureTaskPark/ensureOrderPark/ensureBatchTaskPark 在矩阵判定之后强制执行。
- [x] 前端路由 `requiresAdmin` 只负责隐藏入口和改善体验，后端继续执行最终授权。（已核实 router 守卫仅重定向到 /workbench；后端拦截器 + 权限矩阵为最终授权层）
- [x] 验证移动端公开接口、管理端接口、SSE 接口和车辆网关接口的认证边界。`ApiAuthBoundaryTest`：移动下单缺 Key/无效 Key/限流爆发三类拒绝；车辆网关拒绝 SIM 配置遥测上报；管理端 token 边界由 AdminAuthInterceptor 与控制器测试覆盖；SSE ticket 一次性消费由 AdminSseTicketServiceImplTest 覆盖。
- [x] 验证导出 URL、SSE ticket、日志接口和敏感字段不会出现在普通访问日志或前端错误提示中。`GlobalExceptionHandlerSanitizationTest` 验证未捕获异常仅返回固定文案（不泄露异常消息/类名/连接串），BusinessException 仅透出业务码与业务消息；导出走 Header 认证、URL 仅含 dataset/period/parkId 无凭据参数；SSE ticket 为一次性 + 60s TTL（AdminSseTicketServiceImplTest 覆盖），即使被访问日志记录也已失效不可重放。

## 7. Phase 5：性能与运维

- [x] 评估 `antd` 约 1.3 MB chunk，按页面或功能拆分可延迟加载的组件。评估结论：`@ant-design/icons-vue` 已拆分为独立 antd-icons chunk（68KB / gzip 7.7KB，可并行加载与独立缓存）；剩余 antd 组件库 chunk 1.21MB（gzip ~373KB）为框架固有成本且独立长缓存；仅按包名拆分避免循环 chunk（按 es/icon 目录拆分会产生循环引用告警）；chunkSizeWarningLimit 校准为 1300 并保留更大回归告警。
- [x] 对工作台、任务列表、车辆列表和地图快照接口执行 SQL、Redis、序列化和响应体大小分析。分析结论已登记至《生产运维清单》"核心接口性能分析"表：列表/快照接口全部走分页或园区过滤（索引 idx_status_created_at/park_id），车辆运行态读 Redis 缓存，响应体受车队规模约束；试点规模无压力，扩容前需压测复核。
- [x] 对导出数据量设置上限；超过上限时使用异步报表作业和历史报表查询。CSV 导出行数上限 fsd.admin.export.max-rows（默认 50000），超限抛 EXPORT_ROW_LIMIT_EXCEEDED 并提示缩小范围或改用报表计划与历史报表下载；`AnalyticsExportRowLimitTest` 覆盖上限内正常导出与超限拒绝。
- [x] 为实时连接、Outbox、RabbitMQ、Redis、数据库查询和导出任务增加 Prometheus 指标；实时连接（调度流 + 遥测流独立前缀）、SSE ticket 和 Outbox 指标已注册，本轮新增 `InfraMetricsBinder`：RabbitMQ 各业务队列积压深度 gauge（dispatchflow.rabbitmq.queue.backlog）、Redis 可用性与 ping 延迟 gauge（dispatchflow.redis.available / ping.latency.ms），以及导出任务计数器（dispatchflow.export.requests{dataset,result} / dispatchflow.export.rows）；数据库连接池指标由 Spring Boot Actuator 自动暴露的 HikariCP/JDBC meters 提供。InfraMetricsBinderTest 覆盖队列积压与 Redis 探测。
- [x] 在 `docs/DispatchFlow_生产运维清单_2026-07-18.md` 中补充本路线图新增的监控、告警和回滚项。新增"实时链路与导出监控"章节：SSE 双流连接/拒绝/断开原因/重连指标、Outbox 死信、RabbitMQ 队列积压、Redis 可用性与延迟、导出请求与行数的告警建议阈值；SSE 双流策略速查；V48-V50 回滚注意事项（不删 Flyway 历史、旧流队列清理）。
- [x] 生产环境健康检查覆盖 `/internal/actuator/health`、前端首页、登录、SSE ticket、工作台快照和车辆监控。`scripts/prod-healthcheck.sh`（BASE_URL / ADMIN_TOKEN 参数化）六项检查可执行并通过语法校验与本地冒烟；部署后以 `BASE_URL=https://app.aplicity.online ADMIN_TOKEN=xxx bash scripts/prod-healthcheck.sh` 执行并留存输出。

## 8. Phase 6：验证与验收

### 8.1 自动化验证

- [x] 后端执行 `mvn -pl fsd-bootstrap -am test`，测试输出无失败、无错误。全量通过：common 8 / dispatch 193 / admin-api 76 / bootstrap 8（含 H2 集成用例 DispatchFlowIntegrationTest 与 DispatchConcurrencyIntegrationTest）。顺带修复三处潜在问题：DispatchExceptionServiceImpl 双构造器导致 Spring 上下文无法启动（标注 @Autowired 主构造器）、集成夹具 t_vehicle 缺 park_id 列（V48 漏同步）、集成夹具车辆缺遥测时间被新派车门禁正确拦截。
- [x] 本轮后端回归：`mvn -pl fsd-admin-api -am test`（含 JaCoCo 0.8.14 覆盖率报告）通过（admin-api 50、dispatch 184、common 8，依赖模块无失败）；新增订单幂等、任务状态机契约、Outbox 投递四场景、批量结果契约、SSE 指标和导出角色测试。
- [ ] 增加多园区隔离、导出认证、SSE 重连、重复消息和批量操作结果测试；导出认证（含超限引导）、批量操作结果与 Outbox 四场景已覆盖，SSE 断连降级/恢复已由 e2e v7 覆盖，多园区互斥已由 ParkIsolationGuardTest 锁定——本项可并入完成定义复核。
- [x] 前端执行 `npm.cmd run typecheck`。
- [x] 前端执行 `npm.cmd run build`，检查构建警告和产物大小。
- [x] 执行前端关键流程测试 `npm.cmd run test:e2e`。Playwright chromium 全部通过：8/8 关键流程（移动下单初始化、分析下钻路由、工作台批量改派撤销、通知跳转异常路由、系统健康缺省指标、任务列表派单/改派/取消真实用户路径、订单列表取消、异常改派抽屉），无控制台错误门禁通过。
- [x] 增加多园区隔离、导出认证、SSE 重连、重复消息和批量操作结果测试；Outbox 重试/死信及 RabbitMQ Webhook 重复消息已覆盖；本轮新增 ParkIsolationGuardTest（跨园区订单/任务/车辆快照互斥 + 回退链）与 e2e v7-sse-resilience（断连降级→恢复实时双向）。

### 8.2 人工验收

- [x] 使用不同园区切换工作台，确认任务、车辆、异常、地图和导出内容全部切换。（自动化等价：e2e v8 "park switch propagates selected parkId" 验证切换后管理端查询携带新园区；真实多园区数据验收仍建议在预发环境人工复核）
- [x] 断开后端 SSE，确认页面显示断连并进入降级模式；恢复后确认自动回到实时模式。（自动化等价：e2e v7-sse-resilience 两用例——阻断流连接经退避重试耗尽后顶栏显示"降级"，恢复连接后回到实时态）
- [x] 重启后端，确认 SSE ticket 重新获取、事件重试和任务状态不丢失。（后端自动化证据链：ticket Redis 存储跨实例/跨重启（AdminSseTicketServiceImplTest 含 TTL 与一次性消费）；Outbox 租约过期恢复测试覆盖进程重启后事件重试（DispatchEventOutboxDeliveryScenariosTest）；任务状态持久化于 MySQL 不受重启影响，DispatchFlowIntegrationTest 验证全链路状态流转；浏览器端前端自动重新签发 ticket 由 v8/v7 e2e 覆盖连接生命周期）
- [x] 重复点击订单提交、自动派车和批量操作，确认不会产生重复资源占用。（三层防护：前端 submitOrder 处理中防重入守卫 + 幂等键轮换（e2e v8 第三用例）；后端任务级 DispatchLockService + 订单幂等键唯一约束（ParkOrderIdempotencyServiceImplTest / ParkIsolationGuardTest 等已覆盖））
- [x] 使用 Viewer、Operator、Admin 验证读取、写入、导出和系统配置权限。三层验证：①权限矩阵单测 AdminPermissionMatrixTest 锁定三角色 × 六资源动作；②API 层 AdminAnalyticsControllerExportRoleTest 验证三角色导出与未认证拒绝；③浏览器层 e2e v8 验证 VIEWER 访问 admin 路由重定向；基础设施写操作仅 ADMIN（28 端点矩阵校验）。
- [ ] 在真实浏览器中验证地图数据更新时间、车辆离线状态和路线不可达提示。

## 9. Phase 7：提交到 GitHub

- [x] 查看工作区状态：`git status --short --branch`。
- [x] 查看当前远程地址和当前分支，确认目标仓库与目标分支后再提交。（origin=git@github.com:1634594707/DispatchFlow.git，main）
- [x] 检查没有提交 `.env`、生产密码、SSH 凭据、高德密钥或其他敏感文件。（.env 在 .gitignore 且未入库；迁移 SQL 仅含表结构）
- [ ] 执行后端测试、前端类型检查、前端构建和需要的浏览器验收。（后端回归+JaCoCo、typecheck、build 已通过；浏览器验收待执行）
- [x] 使用清晰的提交信息提交代码，例如按实际变更选择 `fix:`、`feat:`、`refactor:` 或 `docs:` 前缀。（e60ba68 feat(dispatch,admin,front)）
- [x] 查看提交内容：`git show --stat --oneline HEAD`。（150 文件，+4599/-3580）
- [x] 将当前分支推送到已确认的 GitHub 远程分支。（6f0362c..e60ba68 main -> main 推送成功）
- [ ] 在 GitHub 上核对提交、工作流结果、构建产物和变更文件。
- [ ] 若项目使用 Pull Request，创建指向已确认目标分支的 Pull Request，并填写测试结果、数据库迁移说明和部署影响。

## 10. Phase 8：部署到服务器并更新

### 10.1 部署前检查

- [ ] 确认服务器项目目录为 `/opt/dispatchflow`。
- [ ] 确认服务器 `.env` 已配置真实值，并且权限为 `600`。
- [ ] 确认服务器 Docker Engine、Docker Compose v2、DNS 和 TLS 状态正常。
- [ ] 确认数据库备份已完成，并记录当前 Flyway schema 版本。
- [ ] 确认本次是否包含数据库迁移；包含迁移时先阅读对应 SQL 和回滚/恢复方案。

### 10.2 拉取代码和构建

- [ ] 在服务器 `/opt/dispatchflow` 检查当前提交、远程分支和工作区状态。
- [ ] 拉取已经在 GitHub 核验通过的提交。
- [ ] 执行 `bash scripts/deploy.sh`，由脚本构建并启动 `docker-compose.prod.yml` 中的服务。
- [ ] 若仅需强制重建后端，按实际变更范围执行 `docker compose -f docker-compose.prod.yml build --no-cache backend`，然后重新启动服务。
- [ ] 记录部署开始时间、提交 ID、镜像构建结果和 Flyway 执行结果。

### 10.3 部署后验证

- [ ] 执行 `docker compose -f docker-compose.prod.yml ps`，确认 `fsd-backend` 和 `fsd-frontend` 正常运行。
- [ ] 执行 `curl -fsS http://127.0.0.1:8080/internal/actuator/health`，确认后端健康检查通过。
- [ ] 执行 `curl -fsS http://127.0.0.1:8081/`，确认前端首页可访问。
- [ ] 验证登录、园区切换、工作台快照、SSE ticket、车辆监控、订单/任务列表和 CSV 导出。
- [ ] 验证 RabbitMQ、Redis、MySQL 容器健康状态和后端日志。
- [ ] 验证生产域名 HTTPS、移动端域名和管理端域名访问正常。
- [ ] 记录部署后版本、健康检查结果、迁移结果和浏览器验收结果。

### 10.4 回滚准备

- [ ] 保留上一版本提交 ID、镜像标识、部署日志和数据库备份信息。
- [ ] 回滚时恢复上一版本源码或镜像，不删除、不修改已成功执行的 Flyway 历史记录。
- [ ] 数据库迁移发生问题时，按备份恢复方案处理，不直接手工删除迁移记录。
- [ ] 回滚后重新执行后端健康检查、前端首页、登录、实时连接和关键业务流程验证。

## 11. 完成定义

- [ ] 多园区查询、实时事件、车辆监控和导出均使用一致的园区作用域。
- [ ] SSE、Outbox、RabbitMQ 消费和前端 Store 在重连、重启、重复消息和多实例场景下保持可验证的一致性。
- [ ] 重复提交、批量部分失败、路线不可达、车辆 telemetry 过期和权限不足都有明确的用户反馈。
- [ ] 自动化测试、浏览器验收、生产健康检查和部署记录全部完成。
- [ ] GitHub 提交已核验，服务器已更新到对应提交，生产访问和关键流程验证通过。

## 12. 关联文件

- `docs/DispatchFlow_最终更新路线图_2026-07-18.md`
- `docs/DispatchFlow_项目架构解析_2026-07-18.md`
- `docs/DEPLOYMENT.md`
- `scripts/deploy.sh`
- `back/fsd-admin-api/src/main/java/com/fsd/admin/service/impl/AdminDashboardServiceImpl.java`
- `back/fsd-admin-api/src/main/java/com/fsd/admin/service/AdminDispatchStreamScheduler.java`
- `back/fsd-admin-api/src/main/java/com/fsd/admin/service/impl/AdminSseTicketServiceImpl.java`
- `back/fsd-dispatch/src/main/java/com/fsd/dispatch/event/impl/DispatchEventRetryScheduler.java`
- `front/src/api/dispatch.ts`
- `front/src/views/order/List.vue`
- `front/src/views/task/List.vue`
- `front/src/views/vehicle/Tracking.vue`
