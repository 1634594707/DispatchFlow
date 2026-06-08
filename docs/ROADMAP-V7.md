# DispatchFlow V7 产品路线图

> **继承**：[ROADMAP-V6.md](./ROADMAP-V6.md)（V6 链路可靠性专项）  
> **审计来源**：用户视角源码审视（2026-06-08）  
> **证据范围**：前端 `front/src/views`、`front/src/api`、`front/scripts/e2e`；后端 `back/fsd-admin-api`、`back/fsd-dispatch`、`back/fsd-bootstrap`；CI 配置 `.github/workflows/ci.yml`  
> **最后更新**：2026-06-08

---

## 〇、V7 交付焦点

V6 已把公开下单、mock/fallback、批量撤销、通知跳转、移动端追踪提示、控制台错误和 E2E 门禁纳入修复闭环。V7 聚焦 **真实操作闭环、运营可信度、运行可靠性、质量门禁硬化**。本路线图只纳入源码可直接证明的问题，不纳入未验证推断。

| # | 优先级 | 问题 | 用户影响 | 来源 |
|---|--------|------|----------|------|
| 1 | **P0** | 任务列表派单、改派、取消仅提示成功，没有调用已有任务 API | 调度员以为操作已生效，实际任务状态未改变 | 源码审计 |
| 2 | **P0** | 任务列表派单、改派车辆选项硬编码 | 调度员无法选择真实在线空闲车辆 | 源码审计 |
| 3 | **P0** | 订单列表取消订单仅提示成功，前端订单 API 未提供取消方法 | 运营人员以为订单已取消，实际订单状态未改变 | 源码审计 |
| 4 | **P0** | 异常处理“重新派单”未提交用户选择的车辆 | 值班人员选择车辆后，后端无法按所选车辆处理 | 源码审计 |
| 5 | **P1** | 系统健康详细指标返回硬编码正常值 | 值班人员会误判 DB、Redis、SSE、API 延迟状态 | 源码审计 |
| 6 | **P1** | 公开健康接口固定返回 `UP` | 部署和监控无法区分应用存活与依赖可用 | 源码审计 |
| 7 | **P1** | 系统健康页扩展指标加载没有隔离失败 | 单个扩展接口失败会影响整体健康页加载 | 源码审计 |
| 8 | **P1** | SSE 连接无超时、无容量上限、无分区隔离 | 长连接泄漏或集中广播会影响监控稳定性 | 源码审计 |
| 9 | **P1** | 管理 token 可通过 URL query 传递 | 导出、SSE 链路增加 URL 日志泄露面 | 源码审计 |
| 10 | **P1** | Webhook 投递同步串行发送 | 单个订阅慢响应会拖慢后续订阅投递 | 源码审计 |
| 11 | **P2** | Redis 派单锁 TTL 固定 10 秒 | 派单耗时异常时缺少配置与观测空间 | 源码审计 |
| 12 | **P2** | 后端质量 profile 未进入 CI，Checkstyle/SpotBugs 不阻断 | 技术债可持续进入主分支 | 源码审计 |
| 13 | **P2** | 登录页公开展示默认账号密码 | 生产环境降低安全信任感 | 源码审计 |

### V7 聚焦方向

> **从“链路可达”到“操作真实、状态可信、运行可控”** — 先消除用户误导，再补齐健康与可靠性，最后硬化质量门禁。

---

## 一、V6 继承说明

| 项 | 说明 |
|----|------|
| **继承对象** | [`ROADMAP-V6.md`](./ROADMAP-V6.md) |
| **V6 重点** | 链路修复、体验修复、E2E 门禁 |
| **V7 重点** | 操作闭环、健康可信、可靠性治理、质量门禁硬化 |
| **原则** | 不使用 mock 成功，不展示硬编码健康，不让质量检查只产生日志 |

---

## 二、V7-A — 真实操作闭环（P0）

> **用户痛点**：按钮点击后出现成功提示，但业务状态没有被后端改变。  
> **目标**：所有写操作必须调用真实 API；成功提示必须来自接口成功响应；失败时保留当前状态并展示失败原因。

### 2.1 待办

- [x] **V7-A1** 任务列表派单接入真实 API
  - 问题证据：`front/src/views/task/List.vue:L322-L331` 只显示 `派单成功` 并刷新；`front/src/api/task.ts:L40-L42` 已存在 `manualAssignTask`
  - 前端：`handleDispatch` 调用 `manualAssignTask(currentTask.taskId, { vehicleId, remark })`
  - 验收：点击任务列表“派单”后，Network 出现 `/api/admin/tasks/{taskId}/manual-assign`；接口失败时不显示成功提示

- [x] **V7-A2** 任务列表改派接入真实 API
  - 问题证据：`front/src/views/task/List.vue:L350-L363` 只显示 `改派成功` 并刷新；`front/src/api/task.ts:L48-L50` 已存在 `reassignTask`
  - 前端：`handleReassign` 调用 `reassignTask(currentTask.taskId, { vehicleId: newVehicleId, remark: reason })`
  - 验收：点击任务列表“改派”后，Network 出现 `/api/admin/tasks/{taskId}/reassign`；接口失败时不关闭弹窗

- [ ] **V7-A3** 任务列表取消接入真实 API
  - 问题证据：`front/src/views/task/List.vue:L371-L373` 只显示 `任务已取消` 并刷新；`front/src/api/task.ts:L44-L46` 已存在 `cancelTask`
  - 前端：`handleCancel` 调用 `cancelTask(record.taskId, remark)`
  - 验收：点击任务列表“取消”后，Network 出现 `/api/admin/tasks/{taskId}/cancel`；接口失败时不显示成功提示

- [ ] **V7-A4** 任务派单与改派车辆选项改为真实可派车辆
  - 问题证据：`front/src/views/task/List.vue:L141-L142`、`front/src/views/task/List.vue:L174` 使用硬编码车辆选项
  - 前端：复用或抽取工作台已有可派车辆来源，禁止继续显示固定 `VH-001`、`VH-002`
  - 验收：车辆选项来自后端或已加载车辆状态；无可派车辆时显示空状态并禁止提交

- [x] **V7-A5** 订单列表取消接入真实订单取消能力
  - 问题证据：`front/src/views/order/List.vue:L212-L214` 只显示 `订单已取消` 并刷新；`front/src/api/order.ts:L5-L15` 当前仅包含列表、查询、详情
  - 前端：新增订单取消 API 封装并在 `handleCancel` 中调用
  - 后端：如现有后端无对应接口，补充订单取消接口并保持状态机约束
  - 验收：点击订单列表“取消”后，Network 出现订单取消请求；接口失败时不显示成功提示

- [x] **V7-A6** 异常处理重新派单提交所选车辆
  - 问题证据：`front/src/views/exception/Index.vue:L155-L162` 允许选择 `vehicleId`；`front/src/views/exception/Index.vue:L320-L325` 提交 payload 未包含 `vehicleId`
  - 前端：`REASSIGN` 动作提交 `vehicleId`，并对未选车辆阻断提交
  - 类型：补齐异常处理请求类型字段，禁止使用未声明字段绕过类型检查
  - 验收：选择“重新派单”并提交后，请求体包含用户选择的 `vehicleId`

---

## 三、V7-H — 健康可信与降级展示（P1）

> **用户痛点**：系统健康页和健康探针存在“看起来正常”的固定值。  
> **目标**：健康数据必须来自真实依赖或明确显示未接入；页面局部失败不拖垮整体健康页。

### 3.1 待办

- [ ] **V7-H1** 系统健康详细指标接入真实数据
  - 问题证据：`back/fsd-admin-api/src/main/java/com/fsd/admin/controller/AdminSystemHealthController.java:L46-L56` 返回 DB、Redis、SSE、API 延迟固定 `0` 和 `OK`
  - 后端：接入连接池、Redis 内存、SSE 连接数、API 延迟统计
  - 前端：当后端返回未接入状态时显示“未接入后端”，不显示正常值
  - 验收：断开 Redis 或 DB 时，系统健康页对应组件不显示 `OK`

- [ ] **V7-H2** 公开健康接口拆分 liveness/readiness
  - 问题证据：`back/fsd-admin-api/src/main/java/com/fsd/admin/controller/HealthController.java:L16-L22` 固定返回 `status=UP`
  - 后端：保留轻量 liveness；新增或改造 readiness 检查 MySQL、Redis、RabbitMQ
  - 运维：部署探针使用 readiness 判断依赖可用性
  - 验收：依赖不可用时 readiness 返回非健康状态

- [x] **V7-H3** 系统健康页扩展接口失败隔离
  - 问题证据：`front/src/views/system/SystemHealth.vue:L313-L320` 使用 `Promise.all` 同时加载健康、指标、时间线
  - 前端：核心健康、详细指标、时间线分别加载和降级
  - 验收：`/api/admin/system/health/metrics` 返回 404/500 时，核心健康信息仍可展示

---

## 四、V7-R — 运行可靠性治理（P1/P2）

> **用户痛点**：长连接、Webhook、派单锁、鉴权 token 的运行边界不清晰。  
> **目标**：关键运行链路可配置、可观测、可隔离，减少单点慢请求和泄露面。

### 4.1 待办

- [ ] **V7-R1** SSE 连接治理
  - 问题证据：`back/fsd-admin-api/src/main/java/com/fsd/admin/service/impl/AdminDispatchStreamServiceImpl.java:L17-L30` 使用 `DEFAULT_TIMEOUT = 0L` 和全局 `CopyOnWriteArrayList`
  - 后端：配置连接超时、最大连接数、连接指标；按用户或园区维度隔离广播范围
  - 验收：超过连接上限时返回明确错误；系统健康指标可看到当前 SSE 连接数

- [ ] **V7-R2** URL query token 治理
  - 问题证据：`back/fsd-bootstrap/src/main/java/com/fsd/bootstrap/config/AdminAuthInterceptor.java:L100-L105` 从 query 读取 `token`；`front/src/views/analytics/Index.vue:L286-L297` 导出时拼接 token
  - 后端：保留兼容前必须明确迁移方案；新增短期一次性导出凭证或服务端下载代理
  - 前端：导出不再把长期管理 token 写入 URL
  - 验收：浏览器地址、Network URL、后端访问日志不出现长期管理 token

- [ ] **V7-R3** 登录失效状态码统一
  - 问题证据：`back/fsd-bootstrap/src/main/java/com/fsd/bootstrap/config/AdminAuthInterceptor.java:L47-L52` 抛出 `ADMIN_AUTH_FAILED`；`back/fsd-bootstrap/src/main/java/com/fsd/bootstrap/config/GlobalExceptionHandler.java:L65-L69` 未映射为 401
  - 后端：将 `ADMIN_AUTH_FAILED` 映射为 `401 Unauthorized`
  - 前端：统一触发重新登录和会话清理
  - 验收：无效 token 访问管理接口返回 401，不返回 400

- [ ] **V7-R4** Webhook 异步投递与重试治理
  - 问题证据：`back/fsd-dispatch/src/main/java/com/fsd/dispatch/integration/WebhookDeliveryService.java:L46-L86` 在订阅循环内同步发送
  - 后端：改为队列化投递、失败退避、投递耗时指标、失败熔断
  - 验收：单个订阅 5 秒超时不阻塞其他订阅投递

- [ ] **V7-R5** 派单 Redis 锁 TTL 配置化与观测
  - 问题证据：`back/fsd-dispatch/src/main/java/com/fsd/dispatch/infra/impl/RedisDispatchLockService.java:L15-L35` 固定 `LOCK_TTL = 10s`
  - 后端：将 TTL 放入配置；记录获取锁失败次数和锁持有耗时
  - 验收：配置文件可调整派单锁 TTL；锁冲突可在日志或指标中定位

---

## 五、V7-Q — 质量门禁硬化（P2）

> **用户痛点**：真实用户流程和后端质量检查仍存在漏网空间。  
> **目标**：P0 操作闭环进入 E2E；后端质量检查进入 CI 并具备阻断能力。

### 5.1 待办

- [ ] **V7-Q1** P0 操作闭环 E2E 覆盖
  - 覆盖范围：任务派单、任务改派、任务取消、订单取消、异常重新派单
  - 要求：不能只直接 `fetch` 接口；必须从页面按钮触发用户路径
  - 验收：`npm run test:e2e` 覆盖上述路径并在 CI 执行

- [ ] **V7-Q2** 后端 quality profile 接入 CI
  - 问题证据：`.github/workflows/ci.yml:L23-L25` 只运行 `mvn -pl fsd-bootstrap -am test -B`
  - CI：增加 `mvn -pl fsd-bootstrap -am verify -Pquality -B`
  - 验收：CI 日志出现 Checkstyle 与 SpotBugs 执行结果

- [ ] **V7-Q3** 关键质量检查改为阻断
  - 问题证据：`back/pom.xml:L133-L144` 配置 `failOnViolation=false` 和 `failOnError=false`
  - 后端：逐步将新增问题设为阻断；历史问题先记录基线，不让新增问题进入主分支
  - 验收：新增高风险 SpotBugs 或 Checkstyle 违规会导致 CI 失败

- [ ] **V7-Q4** 移除生产登录默认账号提示
  - 问题证据：`front/src/views/auth/Login.vue:L74-L76` 显示 `默认账号：admin / admin123`
  - 前端：仅开发环境展示默认账号提示，生产构建不展示
  - 验收：生产环境登录页不出现默认账号密码

---

## 六、建议实施顺序

```text
Phase 1（P0 操作闭环）：
  V7-A1 → V7-A2 → V7-A3 → V7-A4 → V7-A5 → V7-A6

Phase 2（P1 健康可信）：
  V7-H1 → V7-H2 → V7-H3

Phase 3（P1/P2 可靠性治理）：
  V7-R1 → V7-R2 → V7-R3 → V7-R4 → V7-R5

Phase 4（P2 门禁硬化）：
  V7-Q1 → V7-Q2 → V7-Q3 → V7-Q4
```

---

## 七、代码锚点

| 主题 | 路径 |
|------|------|
| 任务派单/改派/取消 | `front/src/views/task/List.vue` · `front/src/api/task.ts` |
| 订单取消 | `front/src/views/order/List.vue` · `front/src/api/order.ts` |
| 异常重新派单 | `front/src/views/exception/Index.vue` · `front/src/types/exception.d.ts` |
| 系统健康详细指标 | `back/fsd-admin-api/src/main/java/com/fsd/admin/controller/AdminSystemHealthController.java` |
| 公开健康接口 | `back/fsd-admin-api/src/main/java/com/fsd/admin/controller/HealthController.java` |
| 健康页加载 | `front/src/views/system/SystemHealth.vue` |
| SSE 连接治理 | `back/fsd-admin-api/src/main/java/com/fsd/admin/service/impl/AdminDispatchStreamServiceImpl.java` |
| URL token | `back/fsd-bootstrap/src/main/java/com/fsd/bootstrap/config/AdminAuthInterceptor.java` · `front/src/views/analytics/Index.vue` |
| 登录失效状态码 | `back/fsd-bootstrap/src/main/java/com/fsd/bootstrap/config/GlobalExceptionHandler.java` |
| Webhook 投递 | `back/fsd-dispatch/src/main/java/com/fsd/dispatch/integration/WebhookDeliveryService.java` |
| 派单 Redis 锁 | `back/fsd-dispatch/src/main/java/com/fsd/dispatch/infra/impl/RedisDispatchLockService.java` |
| CI 门禁 | `.github/workflows/ci.yml` · `back/pom.xml` |
| 登录默认账号提示 | `front/src/views/auth/Login.vue` |

---

## 八、Release 判定（草案）

| 版本 | 条件 |
|------|------|
| **v6.1.0** | V7-A1 至 V7-A6（P0 操作闭环） |
| **v6.2.0** | + V7-H1 至 V7-H3（健康可信） |
| **v6.3.0** | + V7-R1 至 V7-R5（可靠性治理） |
| **v7.0.0** | + V7-Q1 至 V7-Q4（质量门禁硬化） |

---

## 九、验收证据清单

| 验收项 | 必须提供的证据 |
|--------|----------------|
| 任务派单/改派/取消 | 浏览器 Network 请求、接口响应、列表刷新后的状态 |
| 订单取消 | 浏览器 Network 请求、接口响应、订单详情状态 |
| 异常重新派单 | 请求体中的 `vehicleId`、异常处理结果、任务状态 |
| 系统健康 | `/api/health`、`/api/admin/system/health`、`/api/admin/system/health/metrics` 响应样本 |
| SSE 治理 | 连接数指标、超限响应、断连回收日志 |
| Webhook 投递 | 投递日志、失败重试记录、慢订阅隔离结果 |
| CI 门禁 | GitHub Actions 日志、Checkstyle/SpotBugs 执行结果、E2E 报告 |

---

**维护**：V7 开始后，当前路线图应以 [`ROADMAP-V7.md`](./ROADMAP-V7.md) 为准。PR 前缀：`[V7-Action]`、`[V7-Health]`、`[V7-Reliability]`、`[V7-Quality]`。
