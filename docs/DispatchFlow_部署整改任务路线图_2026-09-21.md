# DispatchFlow 部署整改任务路线图

> 日期：2026-09-21　配套方案：[[DispatchFlow_重新部署更新方案_2026-09-21]]（**为什么**看那份，**做什么**看这份）
> 执行环境：`/opt/dispatchflow`，域名 aplicity.online
>
> ## ⚠️ 前提修正（本路线图据此重排优先级）
>
> **当前部署全为仿真车队（`link_mode = SIM`），没有真实车辆。**
> 因此：真实车相关的 4 项配置从 P0 直接**移出范围**，不再算阻断项；
> 但 Flyway 校验和与 RabbitMQ 变量名这两条**与车队模式无关**，仍是阻断项。

| 原判定 | 全 SIM 下的新判定 |
|---|---|
| 🔴 P0-3 里的 `FSD_VDA5050_MQTT_*`、`MQTT_FMS_*`、`MQTT_VEHICLE_*` 未接线 | ⚪ **移出范围**（没有真实车，这三组本来就不该开）。见 §6 |
| 🟠 P1-4 Redis 无鉴权 | 🔵 **降到 P3**（只绑 127.0.0.1，仿真数据无敏感性）。见 §5 |
| 🔴 P0-1 Flyway 校验和 | 🔴 **仍是 P0**，与 SIM/REAL 无关 |
| 🔴 P0-2 RabbitMQ 变量名 | 🔴 **仍是 P0** —— 事件链路在仿真里是**真跑**的（审计 / SSE 大屏 / Webhook 全走 RabbitMQ） |
| 🟠 字段加密 `_KEY` | 🟡 **降为"先查再定"**：仿真库里大概率没有密文，但要确认，别猜 |

---

## M1　🔴 阻断项（做完才允许部署）　预计 40 min

### 1.1 Flyway 校验和

- [ ] **先查再动**：在服务器执行只读探测，确认 V33/V34 是否已被应用
  ```bash
  docker exec -i fsd-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -N -e \
   "SELECT version, checksum, success FROM \`fsd_core\`.flyway_schema_history
    WHERE version IN ('33','34') ORDER BY version;"
  ```
- [ ] 记录查询结果到本文件末尾的「执行记录」——**这一步的结论决定 1.2 走哪个分支**
- [ ] **分支 A（有记录且 success=1，大概率）**：回滚两个文件，保住校验和
  ```bash
  git checkout HEAD -- back/sql/migrations/V33__webhook_channel_type.sql \
                       back/sql/migrations/V34__alert_aggregation_count.sql
  ```
- [ ] **分支 B（无记录）**：幂等版可直接上，跳到 1.3
- [ ] 若确实需要幂等 → 新开 `V52__make_v33_v34_columns_idempotent.sql`，**不改已应用文件**
- [ ] 确认没有任何人打算用 `flyway repair` / 手删 `flyway_schema_history` 行（方案 §6 迁移纪律第 1 条）

### 1.2 RabbitMQ 变量名对齐

- [ ] 改 `docker-compose.prod.yml`：`RABBITMQ_USER → RABBITMQ_USERNAME`、`RABBITMQ_PASS → RABBITMQ_PASSWORD`（rabbitmq 与 backend 两处共 4 个引用）
- [ ] 查服务器上 RabbitMQ 现有用户，判断是否已被空口令建过
  ```bash
  docker exec fsd-rabbitmq rabbitmqctl list_users
  ```
- [ ] 若已建过：进容器 `rabbitmqctl change_password <user> <新密码>`，**或**直接删 `rabbitmq-data` 卷重建
      （RabbitMQ 卷不存业务数据，删了安全；**MySQL 卷绝对不能删**）
- [ ] 重建后确认三个队列都在：`docker exec fsd-rabbitmq rabbitmqctl list_queues name consumers`

### 1.3 提交与验证

- [ ] `cd back && mvn -q -pl fsd-bootstrap -am test` 全绿（基线是 334 个测试）
- [ ] 把 M1 的改动**单独一个 commit**，不与 §3 的配置接线混提
- [ ] 工作区垃圾清掉或进 `.gitignore`：`.gh-check.js`、`decoded.txt`、`vite-dev.log`

---

## M2　🟠 配置接线（本次一并做）　预计 30 min

- [ ] `backend.environment` 补齐**与 SIM 无关但仍需要**的键：
  - [ ] `FSD_ADMIN_TOKEN_HMAC_KEY` —— 不传的后果最实际：**每次重启全员掉线**
  - [ ] `DB_USE_SSL` / `DB_REQUIRE_SSL` / `DB_VERIFY_SERVER_CERT`（默认 `false`，与现网行为一致）
  - [ ] `REDIS_PASSWORD`（先只传，是否启用 requirepass 见 §5）
- [ ] 全部写成 `${VAR:-默认值}`，**默认值一律等于"今天的线上行为"**，漏配只是回到现状
- [ ] 字段加密先查再定：
  ```bash
  docker exec -i fsd-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -N -e \
   "SELECT COUNT(*) FROM \`fsd_core\`.t_order;"
  ```
  - [ ] 确认 `t_order` 里被标为加密的那些列，**存量是明文还是密文**
  - [ ] 若全明文 → `FSD_FIELD_ENCRYPTION_ENABLED` 保持 `false`，本次不接线，只留 TODO
  - [ ] 若已有密文 → 必须把 `_KEY` 传对，并**先用一条旧记录验证能解密**再部署
- [ ] `.env` 只做**键的并集**，不覆盖：
  ```bash
  cp .env.production .env.new
  diff <(grep -oE '^[A-Z_]+' .env | sort -u) <(grep -oE '^[A-Z_]+' .env.new | sort -u)
  ```
  - [ ] 新键手工填值后删掉 `.env.new`（**永远不要 `cp .env.production .env`**）

---

## M3　🚀 部署执行　预计 60 min（含构建）

- [ ] **Step 0 备份**（不可跳过，且必须看到非零字节）
  ```bash
  cd /opt/dispatchflow && DATE=$(date +%Y%m%d-%H%M)
  mysqldump -h127.0.0.1 -P3307 -uroot -p"${MYSQL_ROOT_PASSWORD}" \
    --single-transaction --quick --routines --triggers --set-gtid-purged=OFF \
    fsd_core | gzip > "/opt/backups/fsd_core-${DATE}.sql.gz"
  ls -lh "/opt/backups/fsd_core-${DATE}.sql.gz"
  git rev-parse HEAD > "/opt/backups/revision-${DATE}.txt"
  ```
- [ ] 确认服务器工作区干净，**只 ff 不 merge**
  ```bash
  git fetch origin && git status && git merge --ff-only origin/main
  ```
- [ ] 构建并起服务
  ```bash
  docker compose -f docker-compose.prod.yml build backend frontend
  docker compose -f docker-compose.prod.yml up -d
  ```
- [ ] 盯 Flyway 日志，**出现 `Validate failed` 立刻进 M6 回滚，不要重试**
  ```bash
  docker logs fsd-backend 2>&1 | grep -Ei 'flyway|migrat|checksum|ERROR' | tail -20
  ```
- [ ] backend 变 `healthy`（`start_period` 是 90s，别提前判死）

---

## M4　✅ 验收（按"全 SIM"重写过的检查点）

**基础设施**
- [ ] `docker compose ps` 四个服务全 healthy
- [ ] `curl -fsS http://127.0.0.1:8080/internal/actuator/health` 返回 UP
- [ ] RabbitMQ：`audit` / `webhook` 两个具名队列 + **每实例一个匿名队列**，`consumers ≥ 1`

**主链路（仿真口径，这才是本项目的真实场景）**
- [ ] 下单 → 自动派车 → 仿真环驱动执行 → 车辆回报 → SUCCESS 闭环跑通
- [ ] SSE 大屏实时刷新（这条同时验 RabbitMQ 口令对不对）
- [ ] Webhook 收到带 HMAC 签名的投递
- [ ] 管理端登录成功，且**重启 backend 后旧 token 仍有效**（验 HMAC key 接对了）

**仿真引擎本身（全 SIM 部署的主业务，最容易漏验）**
- [ ] `FSD_PARK_SIMULATION_ENABLED` 为 true，仿真 tick 在跑（日志里能看到 1s 周期的推进）
- [ ] `FSD_PARK_SIMULATION_VEHICLE_COUNT` 服务器上实际是多少？**默认值是 0** —— 若是 0，仿真车队规模全靠 `geo-vehicle-count` 与既有 SIM 车辆，别以为"开了仿真就有一百台车在跑"
- [ ] 阈值热更新：改一次 Redis 里的能量阈值，观察 5s 内是否生效（这条也验 Redis 接线）

**数据与运维**
- [ ] `bash scripts/prod-healthcheck.sh` 全绿
- [ ] 备份任务在跑，且**这次做一次真实恢复演练**（从没演练过的备份等于没有备份）
- [ ] 前端 `https://admin.aplicity.online` 可登录，Cloudflare Full(strict) 下无 521/525

---

## M5　🟡 SIM 专属发现（本次查到的，要么修要么写清楚）—— **已按「选 A」执行，2026-09-21**

- [x] **`FleetAutomationScheduler` 在纯 SIM 部署下是空转的。**
      证据（2026-09-21 复核，含确切行号）：
      `back/fsd-dispatch/src/main/java/com/fsd/dispatch/scheduler/FleetAutomationScheduler.java`
      `:45-46` `@Scheduled(fixedDelayString = "${fsd.automation.fleet-check-ms:120000}")` → `evaluateRealFleetRules()`；
      `:49-51` 查询条件是 `.in(VehicleEntity::getLinkMode, VehicleLinkMode.REAL.name(), VehicleLinkMode.VDA5050.name())`；
      `:61` 是 `automationRuleService.evaluateFleetEnergyRules(...)` 在**全仓的唯一生产调用方**（其余命中都在测试里，已 grep 全仓确认）。
      → 服务器上这个 120s 定时任务每轮都扫到 0 台车。
- [x] 已 grep 出阈值判定的**全部消费方**，读侧与动作侧不是一回事，别混：

| 消费方 | 作用 | 覆盖 SIM 吗 |
|---|---|---|
| `FleetSnapshotAssembler`（`fleet/service/FleetSnapshotAssembler.java:67` 的 `lowBattery(...)`；`:128-131` 的 `resolveLinkMode` 还把空值默认成 SIM） | 大屏快照上标 `lowBattery` / `critical` / `charging` | ✅ **覆盖**（这条链不按 `linkMode` 过滤） |
| `DispatchAdminQueryServiceImpl` | 管理端只读统计 | ✅ 覆盖 |
| `FleetAutomationScheduler` → `evaluateFleetEnergyRules`（`:61`） | **自动生成返充/换电任务**（动作侧） | ❌ **不覆盖**，查询条件写死 `REAL/VDA5050` |
| `RealFleetSwapCoordinator` | 换电协调 | ❌ REAL only |

  → 所以准确表述是：**阈值判定在仿真里"看得见"（大屏标注生效），但"会动作"的那条链不作用于 SIM**；
    仿真车队的实际补能由 `ParkPilotSimulationServiceImpl` 自己的需求/恢复逻辑驱动
    （`hasFleetDispatchDemand` / `recoverFleetUnderDispatchPressure`）。
- [x] 处置二选一：
  - [x] **选 A（已执行，零风险）**：不改代码，只在本文件与 `docs/运维手册-评分权重与能量阈值.md` 里写清"阈值动作链只作用于 REAL/VDA5050，仿真走仿真环"
        → 已在运维手册新增 **§2.5「阈值『看得见』与『会动作』是两条不同的链」**（含消费方表格 + 确切行号），并修正了 §4 验证清单里"改 Redis 阈值后观察车辆行为变化"这条**在纯 SIM 下会误判**的判据。
  - [ ] **选 B（未做，保持不做）**：把 SIM 纳入 `FleetAutomationScheduler` 扫描范围 —— 但**必须先确认不会与仿真环自己的补能逻辑打架**，否则会出双重返充指令
- [x] 口径修正：说"分级补能阈值策略"时，**别说成"已作用于全部车队的自动返充"** —— 全 SIM 部署下动作链一条都没触发过

---

## M6　🔙 回滚（准备好再动手，别出事才找）

- [ ] 回滚命令抄在手边：
  ```bash
  cd /opt/dispatchflow
  git reset --hard $(cat /opt/backups/revision-<DATE>.txt)
  docker compose -f docker-compose.prod.yml up -d --build backend frontend
  # 只有数据迁移本身要回退时才恢复库：
  gunzip -c /opt/backups/fsd_core-<DATE>.sql.gz | \
    docker exec -i fsd-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" fsd_core
  ```
- [ ] 明确一件事并写下来：**只回代码不回库时，Flyway 会因"库里版本比代码新"而拒绝启动**；
      这种情况只能把代码追上去，**不许删 `flyway_schema_history` 的行**
- [ ] 回滚后重跑 M4 全部检查

---

## M7　📦 可选后置：PostGIS / geo-py（**本次可以完全不做**）

- [ ] 决定：这次上不上 `--profile geo` 那两个服务？（默认 `FSD_GEO_SERVICE_ENABLED=false`，不上对现网零影响）
- [ ] 若上，**先解决 seed 链路**：`geo-py/scripts/seed_from_migrations.py` 是从 Flyway 迁移文件解析坐标的（本地拿到 13 站点 / 6 围栏 / 55 节点）。
      服务器上 MySQL 才是真相源，**必须改成从 MySQL 导**，否则导进去的是迁移里的初始夹具而不是运营数据
- [ ] geo-api **不映射宿主机端口**，只让 backend 走容器内网 `http://fsd-geo-api:8090`
- [ ] 开开关的顺序：起服务 → seed → 调 `/geo/distance/compare` 与 Java 手算对一遍 → 才置 `enabled=true`，
      且**只开读侧展示，不接派单热路径**
- [ ] 记录一句诚实预期：基准实测交叉点约 N≈5000，当前站点量级下 **PostGIS 比手算慢**，
      做的是能力补齐 + 为"事件驱动重排的影响域查询"打地基，**不是性能优化**

---

## 8. ⛔ 明确不做（全 SIM 前提下的范围收缩）

| 不做 | 原因 | 什么时候再谈 |
|---|---|---|
| `FSD_VDA5050_MQTT_*` 接线 | 没有真实车 | 真接 VDA5050 车那天 |
| `MQTT_FMS_*` / `MQTT_VEHICLE_*` 接线 | 同上 | 同上 |
| Redis `requirepass` | 只绑 127.0.0.1，仿真数据无敏感性 | 若把 6380 暴露到外网 / 接入真实数据 |
| 给 ES 建索引、装应用侧客户端 | 本轮不扩范围，且一周内补不出来 | 需要"自由文本聚合异常现象"时 |
| 删 `back_mysql-data` 卷 / 改 `external: true` 卷名 | 唯一数据源 | 永不 |
| 把 docs 的大删改混进这次部署 | 见 §9 | 单独一个 commit |

---

## 9. 仍悬而未决（需要本人回答，不要我替你猜）

- [ ] `docs/DispatchFlow_底层架构解析.md` 与 `docs/ROADMAP-FRONTEND-IMPROVEMENT.md` 的**整文件删除**是有意的吗？
- [ ] `docs/前端站点与后端逻辑审查路线图.md` 被删掉 434 行 —— 是重构还是误删？
      这三处和部署无关，建议**先单独提交、单独 review**，别在部署窗口里改文档
- [ ] 服务器上 `.env` 里的真实口令，**你有没有本地备份**？丢了的话 RabbitMQ/MQTT 口令怎么找回

---

## 10. 建议执行顺序（一次做完，约 2.5 h）

```
M1 阻断（40min）→ M2 接线（30min）→ M3 部署（60min）→ M4 验收（30min）
                                                    ↓ 不通过
                                                  M6 回滚
M5 SIM 发现  → 单独排期，不占本次窗口
M7 PostGIS   → 本次不做，或做但不开开关
```

---

## 执行记录（跑的时候填这里，别只打勾）

> 执行时间：2026-09-21 12:04–12:09（本地 11:58 起做只读盘点）
> 部署窗口内**未重新构建镜像**：本次改动只有 `environment:` 与 `.env`，不含任何 Java/SQL。

| 时间 | 步骤 | 结果 / 关键输出 |
|---|---|---|
| 11:58 | 1.1 `flyway_schema_history` 查询 | **只有 2 行**：`50 / Pre-Flyway migrations V01-V50 / checksum NULL`、`51 / energy demand forecast / checksum 580216233`。V33、V34 **无记录** → 按路线图应走**分支 B**；但更硬的结论是：低于 baseline 的 V01–V50 被 Flyway 忽略，**只有 V51 的校验和被校验**，所以 P0-1 在本服务器**根本不成立** |
| 11:58 | 11.2 `rabbitmqctl list_users` | `dispatch [administrator]`；队列 `fsd.dispatch.audit.queue` / `fsd.dispatch.webhook.queue`（durable, consumers=1）+ `spring.gen-*` 匿名队列 —— **事件链路本来就是通的**，P0-2 在本服务器只是「新环境才会踩」的潜伏 bug |
| 12:05 | Step 0 备份 | `/opt/backups/fsd_core-20260921-120555.sql.gz`，**1,404,056 B / 47 张表 / 带 `-- Dump completed` 结束标记** |
| 12:05 | rollback 资源 | `dispatchflow-{backend,frontend}:rollback-20260921-120555`；`.env.bak-20260921-120555`；`docker-compose.prod.yml.bak-20260921-120456` |
| 12:05 | M1 commit | `f5c504c` fix(ops): 对齐 RabbitMQ 环境变量命名，兼容存量与新建环境 |
| 12:05 | M2 commit | `3dce48f` fix(ops): 补齐 backend 容器缺失的配置键，默认值锁定「今天的线上行为」 |
| 12:07 | 重建 | **只有 fsd-backend 被 Recreate**；mysql / redis / rabbitmq / frontend 保持 `Running`（配置解析结果未变 ⇒ 无需重建），**RabbitMQ 零中断** |
| 12:07 | Flyway 日志关键行 | `Successfully validated 52 migrations` / `Current version of schema fsd_core: 51` / `Schema fsd_core is up to date. No migration necessary.` —— **无 `Validate failed`** |
| 12:07 | HMAC 告警 | 新启动日志中**已消失**（旧的 `2026-09-17 01:30:18 WARN ... using a random per-instance HMAC key` 不再出现） |
| 12:08 | M4 验收 | `prod-healthcheck.sh` **PASS=4 FAIL=0 SKIP=1**（SKIP 是未给 ADMIN_TOKEN 的 SSE / 工作台两项）；`{"status":"UP"}`；三个域名全 200 |
| 12:08 | M4 未通过项 | **无**。但下列项**未验**（缺凭据/前置条件，不是失败）：SSE 大屏实时刷新、Webhook HMAC 投递（`t_webhook_subscription` 为空表）、阈值热更新（需管理员 token）、下单→派车→SUCCESS 端到端闭环 |
| 12:08 | 副作用（预期内） | HMAC key 由「随机 per-instance」换成稳定值 ⇒ **`t_admin_session` 里 36 条既有会话全部失效，需要重新登录一次**；此后跨重启保持有效 |

### 部署后实测状态

- 容器：`fsd-backend` Up(healthy) / `fsd-frontend` Up / `fsd-mysql` Up(healthy, 3 weeks) / `fsd-redis` Up(healthy, 3 weeks) / `fsd-rabbitmq` Up(healthy, 3 weeks)；**`codefolio-*` 三个容器仍是 Up 3 weeks，未受影响**
- 仿真引擎**确认在跑**：`ZJF-AV-01/02/03` 均 `online_status=ONLINE`、`last_report_time=2026-09-21 12:08:17`（检查时刻前 ~1 分钟）；`t_charging_session` 从 11:58 的 94,428 行涨到 12:08 的 94,986 行
- 数据完整性：`t_vehicle` 5 / `t_admin_user` 3 / `t_admin_session` 36 / `t_station` 28 —— 与部署前一致
- 对外：`app.` / `www.` / `code.` 三个域全 200；证书有效期至 **2026-11-23**

---

## 11. ⚠️ 事实修正（2026-09-21 实测，本文件与配套方案里有 9 处与真实环境不符）

> 下面每条都有可复现的证据。**照原文执行会在错误的地方损坏或空转，请以本节为准。**

| # | 原文说法 | 实测事实 | 影响 |
|---|---|---|---|
| 1 | M3「`git fetch origin && git merge --ff-only origin/main`」、M6「`git reset --hard $(cat revision.txt)`」、§4「`git -C /opt/dispatchflow rev-parse`」 | **`/opt/dispatchflow` 不是 git 仓库**（`IS_GIT_REPO=no`，目录里只有 `back/ front/ docs/ scripts/ .gitignore .env`，无 `.git`） | 所有 git 式部署/回滚命令**无法执行**。实际方式是 sftp 同步 + `docker compose up -d` |
| 2 | P0-1「改 V33/V34 → Flyway 校验和失配 → **启动即失败**」 | 生产 `flyway_schema_history` 只有 `v50`(baseline, checksum NULL) 与 `v51`；低于 baseline 的 V01–V50 被 Flyway 忽略 | 这条 P0 **在本服务器不成立**；本次重建日志为 `Successfully validated 52 migrations`。V33/V34 因此**未回滚也未提交**，留给本人决定 |
| 3 | P0-2「RabbitMQ 以空口令建用户、后端用空口令连 → 事件链路静默不通」 | 服务器 `.env` 用的是 `RABBITMQ_USER`/`RABBITMQ_PASS`，与 compose 的 `${RABBITMQ_USER}` **本来就匹配**；用户 `dispatch` 正常、三个队列都有 consumer | 现网**从来没有断**。这是「新环境照 `.env.production` 模板部署才会踩」的潜伏 bug，修法用嵌套回退即可，存量零变更 |
| 4 | §3 建议 `DB_USE_SSL: ${DB_USE_SSL:false}`，并称「默认 false 与现网行为一致」 | `application.yml:8` 实为 `useSSL=${DB_USE_SSL:true}` | 照原文接线会**静默把 TLS 从 PREFERRED 降成 DISABLED**。已改为默认 `true` |
| 5 | §3「字段加密静默关闭」 | 开关默认值其实是 **`true`**；之所以没生效是因为 `.env` 没有 `_KEY`，`deriveKey("")` 返 null ⇒ `isActive()==false` | 结论（本轮不开）一致，但**原因不同**。已实测两处加密落点 0 行密文 |
| 6 | Step 0「宿主机 `mysqldump -h127.0.0.1 -P3307 ...`」 | 宿主机**没有** `mysqldump`/`mysql` 客户端（`command not found`, exit=127），首跑产出 **20 B 空文件** | 必须走 `docker exec -i fsd-mysql mysqldump ...`。已在部署脚本里加「体积 <200 KB 或表数 <30 就中止」的闸门，空备份直接拦住 |
| 7 | M4「`curl -fsS http://127.0.0.1:8080/internal/actuator/health`」 | `fsd-backend` 的端口映射是 `8080/tcp`（**不映射宿主机**） | 宿主机上必然 connection refused。要 `docker exec fsd-backend curl ...`；仓库自带的 `prod-healthcheck.sh` 已经是这个写法 |
| 8 | M4「前端 `https://admin.aplicity.online` 可登录」 | Nginx 里根本没有 `admin` 这个 server_name；实际是 `www.aplicity.online` + **`app.aplicity.online`** → 127.0.0.1:8081 | 检查点域名写错，会误判为失败 |
| 9 | M4「备份任务在跑，且这次做一次真实恢复演练」 | **根本没有任何自动备份**：`crontab -l` = no crontab、`/etc/cron.d/` 只有 certbot/e2scrub_all、systemd timers 全是系统自带；`/opt/backups` 目录原先不存在 | 这条不是「在跑」，是「不存在」。见 §12 |

另有两处**内部矛盾**未按原文执行：

- 配套方案 §1① 把 `geo-py/` 列为「必须上（本轮真实功能）」，而 §7/§10 又说「这次只做到能开、不做开」。**本次按后者办**：geo 相关文件（含 `application.yml` 的 geo-service 块、4 个新增 Java 文件、`geo-py/`）**全部未部署**，避免为一个默认关闭的开关去动后端构建。
- §8「不做 VDA5050/MQTT 接线」是对的，但台账里的依据要修正：`t_vehicle` 确有 `REAL-001` / `VDA5050-001` 两行，只是它们是 **2026-08-26 建后 2 秒就再未更新、且 `park_id=NULL` 的夹具**。所以「全 SIM」是**运营判断成立**，但表述要写「REAL/VDA5050 仅有未启用夹具」，不能写「不存在」。

---

## 12. 本次没做、但建议马上排期的事

| # | 事项 | 为什么现在提 | 建议动作 |
|---|---|---|---|
| 1 | ~~**建设自动备份**（最高优先）~~ **✅ 已于同日完成，见 §13** | 全库备份原先**完全依赖人手**，最近一份曾停在 5 天前；本文件自己写着「从没演练过的备份等于没有备份」 | 已完成：脚本入服务器 + root crontab 每日 02:00 + **真实恢复演练通过** |
| 2 | ~~管理员凭据对齐~~ **✅ 已于同日完成，见 §14** | 根因不是口令写错，而是 `.env` 的 `FSD_ADMIN_USER`/`FSD_ADMIN_PASSWORD` **后端从不读取**（死配置）；口令唯一权威在 `t_admin_user.password_hash` | 已完成：走 `change-password` 接口把 admin 口令对齐为 `.env` 里的值，容器内 + 公网双验收通过 |
| 3 | 端到端闭环验收 | M4 里 SSE 大屏、Webhook HMAC 投递、阈值热更新这三项因为没有管理员 token 被 SKIP | **已解除阻塞**（§14 起口令可用）：把 `.env` 的 `FSD_ADMIN_PASSWORD` 作为 `ADMIN_TOKEN` 来源登录后补验这三项；注意 `t_webhook_subscription` 是空表，Webhook 投递仍需先建一条订阅才能验 |
| 4 | `docs/` 的整文件删除与 434 行裁删 | §9 悬而未决项，与部署无关，不该混进部署提交 | 单独提交、单独 review |
| 5 | 本地提交未推送 | 本地 `main` 领先 `origin/main` 若干提交（本次整改结束时为 36 个，含本次 3 个）；服务器不依赖 git，所以不影响部署 | 决定何时 push |
| 6 | ~~M5（SIM 专属发现）~~ **✅ 已按「选 A」完成，见 §M5** | 已核实 `FleetAutomationScheduler:49-51` 的查询条件写死 `REAL/VDA5050`，全 SIM 下该 120s 任务每轮扫到 0 台车 | 已写入 `docs/运维手册-评分权重与能量阈值.md` §2.5；**未**改代码（不选 B，避免与仿真环补能逻辑冲突） |
| 7 | 凭证落盘 | 服务器 root 密码目前**明文**躺在本机 `~/.workbuddy/tmp/df_import_*.py`、`df_sync_ml.py` 里 | 迁到环境变量并轮换 |

---

## 13. ✅ 已建立：自动备份 + 真实恢复演练（2026-09-21 同日完成）

### 13.1 自动备份

| 项 | 值 |
|---|---|
| 脚本 | `scripts/backup-mysql.sh`（已重写并同步到 `/opt/dispatchflow/scripts/`） |
| 调度 | root crontab：`0 2 * * * /opt/dispatchflow/scripts/backup-mysql.sh >> /var/log/fsd-backup.log 2>&1` |
| 产出 | `/opt/backups/fsd_core-<YYYYmmdd_HHMMSS>.sql.gz`（实测 **1.4 MB / 47 张表**） |
| 保留 | 最近 **7** 份，超出的按 mtime 轮转删除 |
| 守护进程 | `cron.service` = `active` + `enabled`（已确认真的在跑，不是死 crontab） |

**相对原脚本修掉的两个真问题：**

1. **原脚本「先轮转、后不管成败」**：备份失败时旧备份照样被删 —— 连续失败几天就会把好备份全挤掉。
   现在改成**先自校验、通过才轮转**：校验项 = 体积 ≥200 KB + `CREATE TABLE` 数 ≥30 + 文件尾含 `-- Dump completed`。
   不合格就删掉残file、以退出码 3 结束，**一份既有备份都不动**。
2. **宿主机没有 `mysqldump`**（见 §11 第 6 条）。脚本本来就走 `docker exec fsd-mysql mysqldump`，这点是对的，已在脚本头部写明原因，防止后人"顺手改成宿主命令"。

### 13.2 真实恢复演练

新增 `scripts/restore-drill.sh`：把最近一份备份还原到**临时库** `fsd_core_restore_drill`，核对后删除。
安全约束：脚本开头硬断言目标库 ≠ `fsd_core`，全程只读源库。

实测结果（2026-09-21 12:15 起跑，三次重跑均通过）：

| 检查项 | 结果 |
|---|---|
| 备份文件自述 | 无 `CREATE DATABASE`/`USE` 语句（可直接指定目标库导入）；`SET NAMES utf8mb4` 已声明 |
| 还原 | `gunzip \| mysql` 退出码 0，用时 **10–15s** |
| 表数 | 源库 **47** / 演练库 **47** ✓ |
| 行数比对 | 19 张表逐表比对：14 张静态表**全部严格一致**（车辆 5 / 站点 28 / 路网节点 79 / 路段 124 / 用户 3 / 园区 2 / 围栏 6 / 车位 8 / 楼块 7 / 工单 16 / 派车 16 / 幂等 16 / 预测 48 / 出站事件 100）；`t_charging_session` 差 −4~−6（备份是快照，源库在被仿真持续写入，**属于正常**） |
| **中文往返** | `t_vehicle.vehicle_name` / `t_park_geofence.fence_name` / `t_station.station_name` 三处的 **HEX 字节逐字节相同** ⇒ 备份/恢复不会破坏中文 |
| 源库无损 | 演练前后 `fsd_core` 表数 47、静态表行数之和 115，**完全一致** ✓ |
| 清理 | 演练库已删除，`information_schema` 里残留 0 ✓ |

### 13.3 顺带查清的两个易误判点

1. **`mysql` CLI 看到中文是 `?????` 不是数据坏了**：容器内 `character_set_client` / `character_set_connection` 默认是 **`latin1`**（服务器与库都是 `utf8mb4`）。
   实测 `HEX(vehicle_name)` = `E79FADE9A9B3E4BBBFE79C9F…`（合法多字节 UTF-8）。
   → 排查中文问题必须用 `HEX()` 或 `--default-character-set=utf8mb4`，别用肉眼看 CLI 输出。
2. **`scripts/restore-drill.sh` 里的 `pipefail` + `grep -q` 会造假告警**：`grep -q` 命中即退出 → 上游 `gunzip` 收到 SIGPIPE → 整条管道被判失败 → 脚本报"备份可能已损坏"。
   已改成 `grep -c`（读完整个流）并注明原因。**假警报会摧毁对备份的信任，比没有告警更糟。**

### 13.4 仍需人工确认的运维事实（顺手记录，未改动）

- `t_station` 28 个站点里有 **15 个 `coord_lng`/`coord_lat` 为 NULL**（id 101–104 的 `A* PICKUP`、201–204 的 `B* DROPOFF` 等）—— 与既有认知"站点双坐标无权威"一致，改造时需一并处理。
- 库里有张遗留表 **`flyway_schema_history_pre_reconcile`**，加上历史行 baseline=50（而 `application.yml` 写的是 `baseline-version: 20`），可推定当年做过一次手工 baseline 对账。**别删这张表、也别"修正"配置**。
- `t_park_geofence.fence_name` 里写的是「找家纺网送货区（**忠**石桥试点）」；真实地名是**叠**石桥。疑似数据录入错别字，建议与业务确认后再改。

---

## 14. ✅ 已解决：管理端口令对齐（2026-09-21 同日完成）

对应 §12 第 2 条。**根因不是「口令写错了」，而是那两行配置从来就没被后端读过。**

### 14.1 根因（附证据）

| 事实 | 证据 |
|---|---|
| `.env` 的 `FSD_ADMIN_USER` / `FSD_ADMIN_PASSWORD` 被后端**完全忽略** | 全仓搜索 `FSD_ADMIN_USER` / `FSD_ADMIN_PASSWORD` 在 `back/**/*.java`、`*.yml` 中**零命中**；`application.yml` 的 `fsd.security.admin` 块只有 `enabled` / `token-hmac-key` / `tokens` 三个键 |
| 口令唯一权威是数据库 | `AdminAuthServiceImpl#login`：`selectOne(username, deleted=0)` → `passwordEncoder.matches(明文, user.getPasswordHash())`，编码器为 `BCryptPasswordEncoder` |
| 所以 `.env` 里的口令一直没生效 | 用 `.env` 凭据登录返回 `ADMIN_LOGIN_FAILED`；用 V11 种子口令 `admin123` 返回 `SUCCESS` |

> 即：这两个键从加入 compose 起就是**死配置**。任何"改 `.env` 就会改口令"的假设都是错的（本文件原 §12 第 2 条也隐含了这个错误假设）。

### 14.2 处置方式

**没有手搓 BCrypt 哈希**，而是走应用自身的
`POST /api/admin/auth/change-password`（`old=admin123` → `new=` `.env` 里的 `FSD_ADMIN_PASSWORD`），
由 `BCryptPasswordEncoder` 自己算哈希 —— 避免手写哈希参数（cost / `$2a$` vs `$2b$`）与线上不一致。
明文全程**不落日志、不回显**（脚本内用 `os.environ` 读取，只输出长度/首末字符）。

改前先做了**校验通过的备份**，并把旧哈希写入 `/root/df_admin_reset_rollback_<TS>.txt`。

### 14.3 验收（全部通过）

| 检查 | 结果 |
|---|---|
| 容器内直连 `127.0.0.1:8080` 用 `.env` 凭据登录 | `code=SUCCESS` / `role=ADMIN` / token 97 字符 ✓ |
| **公网 `https://app.aplicity.online/api/admin/auth/login`** 用 `.env` 凭据登录 | `code=SUCCESS` / `role=ADMIN` ✓ |
| 旧种子口令 `admin123` | `code=ADMIN_LOGIN_FAILED`（已失效）✓ |
| `password_hash` | 前缀 `$2a$10$` / 长度 60（`$2b$` → `$2a$`，说明确由 Spring Security 编码器重新生成）✓ |
| 账号表 | 仍为 3 行（admin / operator / viewer），未增删 ✓ |
| 会话 | `changePassword` 按设计清空了该用户既有会话；验收登录新建 2 条 |

**回滚**：把 `admin` 的 `password_hash` 换回 V11 种子值
`$2b$10$vojppRI7O0uN8xZxVnWB2OyQ1lJBU2te3G1XKNHewk7zuor1ffBVK`，口令即恢复为 `admin123`。

### 14.4 顺手修掉的误导源

`docker-compose.prod.yml` 里那两行**原样保留**（删掉有未知风险，且对运行无副作用），但补了醒目注释说明
"后端不读、改这里不生效、正确改法是 `change-password` 或直接改 `t_admin_user.password_hash`"；
同时把文件头的 Cloudflare 域名单按**实际 Nginx 生效值**改写（原文写 `admin` 记录，实际是 `app`）。
→ 仅改注释，`docker compose config` 的解析结果**逐字节不变**，不触发任何容器重建。

### 14.5 仍然存在的弱口令（未动，需你决定）

`operator` / `viewer` 仍是 V11 种子口令（`operator123` / `viewer123`，对应 `OPERATOR` / `VIEWER` 角色）。
本轮**没有**改动它们 —— 这属于权限面的认证变更，超出"对齐 admin"的授权范围。
建议尽快改掉，或确认这两个账号是否需要保留启用。

