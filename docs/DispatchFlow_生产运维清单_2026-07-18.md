# DispatchFlow 生产运维清单

适用环境：`www.aplicity.online`、`app.aplicity.online`、`/opt/dispatchflow`。

## 每日巡检

1. 检查 `fsd-backend` 为 `healthy`，前端、MySQL、Redis、RabbitMQ 均为 `running`。
2. 检查 `/internal/actuator/health` 返回 `UP`，手机下单、工作台、车辆监控页面均为 HTTP 200。
3. 检查待派任务、开放异常、离线车辆和低电车辆；正常清空状态应为任务 0、异常 0、短驳车 3 辆在线。
4. 检查磁盘使用率。根分区超过 70% 时处理日志和旧构建缓存，超过 85% 时立即停止发布。
5. 检查最近一次数据库备份文件非空，并抽查 gzip 完整性。

## 数据保留

- 订单、任务、异常和路线审计保留 90 天；按月归档后删除，不直接长期堆积在生产库。
- Outbox 已成功事件保留 14 天；失败事件保留至问题关闭后 30 天。
- 车队遥测保留 30 天，聚合报表保留 12 个月。
- 项目发布备份保留最近 5 个版本；数据库每日备份保留 14 天、每周备份保留 8 周。
- 禁止把测试订单、诊断订单写入生产库。生产验收使用独立测试库或 API 模拟数据。

## 告警阈值

| 指标 | 预警 | 严重 |
| --- | --- | --- |
| 后端健康 | 连续 2 次失败 | 连续 5 分钟不可用 |
| API P95 | 超过 800 ms | 超过 2 s |
| 数据库连接池 | 使用率超过 70% | 超过 90% |
| 磁盘 | 超过 70% | 超过 85% |
| 车辆离线 | 单车超过 60 s | 全部车辆离线 |
| 订单等待派单 | 超过 5 分钟 | 超过 15 分钟 |
| 路线异常 | 10 分钟内 3 次 | 同分区持续触发 |

## 发布流程

1. 本地通过前端 typecheck、生产 build、后端相关测试和浏览器回归。
2. 发布前同时备份 `/opt/dispatchflow` 和 `fsd_core`，记录文件大小与 SHA-256。
3. 上传源码包时排除 `.env`、`node_modules`、`dist`、`target` 和 `.git`。
4. 无缓存构建新镜像，停止旧后端后清理旧任务锁、MAPF 预约和 `fleet:runtime:*`。
5. 启动后确认 Flyway 版本、容器健康、核心表数据和正式域名页面。
6. 验收通过 24 小时后再清理旧镜像和构建缓存，保留至少一个可回退版本。

## 回滚原则

- 前端故障：恢复上一个项目归档并重建 `frontend`。
- 后端代码故障：恢复上一个项目归档并重建 `backend`，不要回退已成功执行的 Flyway 文件。
- 数据迁移故障：停止后端，恢复对应发布前 SQL 备份，再恢复上一版本代码。
- 回滚后必须重新验证健康检查、Flyway 历史、订单创建和车辆列表。

## 安全

- 服务器使用 SSH 密钥登录，关闭 root 密码远程登录；发布账号仅授予 `/opt/dispatchflow` 和 Docker 所需权限。
- `.env` 仅保存在服务器，权限为 `600`，不得进入源码包或聊天记录。
- 每 90 天轮换数据库、RabbitMQ、管理端密钥；人员变更时立即轮换。
- Nginx、Docker、MySQL 和系统安全更新按月评估，先备份再执行。

## 实时链路与导出监控（2026-08-22 路线图新增）

Prometheus 端点：`/internal/actuator/prometheus`（默认已暴露）。本路线图新增指标及建议告警：

| 指标 | 说明 | 告警建议 |
| --- | --- | --- |
| `dispatchflow_sse_connections_active` / `...telemetry.connections.active` | 调度流 / 遥测流活跃连接数 | 突降为 0 且工作台有人使用 |
| `dispatchflow_sse_connections_rejected`（含 telemetry） | 连接被拒（达到 max-connections 上限） | > 0 即需扩容或调大上限 |
| `dispatchflow_sse_connections_closed_by_reason` | 断开原因分组（completed/timeout/error/send-failure/heartbeat-failure） | error/send-failure 持续增长 |
| `dispatchflow_sse_connections_reconnects` | 同用户 60s 内重连次数 | 持续 > 0 提示网络或服务端不稳 |
| `dispatchflow_outbox_events_dead_letter`（死信数量） | Outbox 超过最大重试进入 DEAD_LETTER | > 0 立即排查失败原因 |
| `dispatchflow_rabbitmq_queue_backlog{queue=...}` | 各业务队列积压深度 | > 100 WARNING；> 500 CRITICAL |
| `dispatchflow_redis_available` / `dispatchflow_redis_ping_latency_ms` | Redis 可用性与探测延迟 | available = 0；延迟 > 200 ms |
| `dispatchflow_export_requests{dataset,result}` / `dispatchflow_export_rows` | 导出请求与行数；result=EXPORT_ROW_LIMIT_EXCEEDED 表示超限被拒 | limit_exceeded 频发提示用户缩小范围或配置异步报表 |

### 导出与报表

- CSV 导出行数上限 `fsd.admin.export.max-rows`（默认 50000）；超限返回 `EXPORT_ROW_LIMIT_EXCEEDED` 并提示改用报表计划/历史报表。
- 大数据量导出一律引导至"定时报表 + 报表历史"，避免同步导出拖垮工作台。

### SSE 双流策略速查

- 认证：两流均需一次性 ticket（`POST /api/admin/sse-ticket`，Redis 存储，60 s TTL，消费即失效）。
- 超时：共用 `fsd.admin.sse.timeout-ms`；连接上限：共用 `fsd.admin.sse.max-connections`。
- 心跳：每 30 s 下发 ping 注释帧；前端指数退避重连（1s→30s，最多 10 次）。
- 多实例：事件队列为每实例匿名自动删除队列；ticket 在 Redis 跨实例共享；Outbox 租约/fencing 保证不重复投递。

### 回滚补充

- 本路线图新增迁移 V48（车辆园区作用域）、V49（Outbox 租约）、V50（订单幂等表）：回滚代码版本时**不要删除**已执行的 Flyway 历史；旧代码遇到新表无影响。
- 流队列改名后首次启动会自动声明新的匿名队列并绑定交换机；旧 `fsd.dispatch.stream.queue` 可在确认无消费后手动清理。
