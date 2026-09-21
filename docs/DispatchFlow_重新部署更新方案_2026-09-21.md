# DispatchFlow 重新部署更新方案

> ## ⚠️ 2026-09-21 执行后修正（先读这段，再读下文）
>
> 本文有 6 处与真实环境不符，**照原文执行会在错误的地方损坏或空转**。已执行的整改与逐条证据见
> [[DispatchFlow_部署整改任务路线图_2026-09-21]] 的 §11「事实修正」。
>
> 1. `/opt/dispatchflow` **不是 git 仓库** → §4/§5 Step 2/§8 里所有 `git` 命令都不可执行；真实方式是 sftp 同步 + `docker compose up -d`。
> 2. **P0-1 不成立**：生产 `flyway_schema_history` 只有 `v50`(baseline) 与 `v51`，低于 baseline 的 V01–V50 被 Flyway 忽略 → 改 V33/V34 不会导致 `Validate failed`（实测重建日志 `Successfully validated 52 migrations`）。
> 3. **P0-2 现网从未断**：服务器 `.env` 用的就是 `RABBITMQ_USER`/`RABBITMQ_PASS`，与 compose 匹配；它只是「新环境照 `.env.production` 模板部署才会踩」的潜伏 bug。
> 4. §3 **`DB_USE_SSL` 默认是 `true` 不是 `false`**（`application.yml:8`），照原文接线会把 TLS 从 PREFERRED 静默降为 DISABLED。
> 5. §5 Step 0 **宿主机没有 `mysqldump`**（exit=127），必须 `docker exec -i fsd-mysql mysqldump ...`；否则产出 20 B 空备份。
> 6. §1① 把 `geo-py/` 列为「必须上」与 §7/§10「本次不开开关」自相矛盾 → 本次**按后者执行，geo 全部未部署**。
>
> 另外 §9 的检查点域名应为 `app.aplicity.online`（无 `admin.` 子域），后端健康检查必须从容器内打（8080 不映射宿主机）。

> 日期：2026-09-21　适用：把当前工作区部署到生产服务器（`/opt/dispatchflow`，域名 aplicity.online）
> 证据来源：`docker-compose.prod.yml`、`scripts/deploy.sh`、`back/fsd-bootstrap/pom.xml`、
> `back/sql/migrations/`、`.env.production`、`geo-py/`，以及 2026-09-21 的 `git status` / `git diff`。
> 相关既有文档：[[DEPLOYMENT]]、`DispatchFlow_生产运维清单_2026-07-18.md`、`实施记录-PostGIS地理服务-2026-09-20.md`

---

## 0. 结论先行

**现在不能直接部署。** 有 3 个 P0 会让服务起不来或功能静默失效，其中第 1 条是**启动即失败**。

| # | 级别 | 问题 | 后果 | 必须先修 |
|---|---|---|---|---|
| 1 | 🔴 P0 | **V33 / V34 迁移被就地改写**（未提交） | 若服务器已应用过 V33/V34，Flyway 校验和失配 → **后端启动失败**，健康检查 90s 后超时 | ✅ 是 |
| 2 | 🔴 P0 | **RabbitMQ 环境变量名不匹配**：compose 用 `${RABBITMQ_USER}`，`.env.production` 定义的是 `RABBITMQ_USERNAME` | RabbitMQ 以**空口令**建用户、后端用空口令连 → 事件链路（审计/SSE/Webhook）静默不通 | ✅ 是 |
| 3 | 🔴 P0 | **10 个已定义的 env 键没有传进 backend 容器** | MQTT/VDA5050、字段加密、Token HMAC 全部回落默认值，功能不可用或安全性下降 | ✅ 是 |
| 4 | 🟠 P1 | Redis 容器未配 `requirepass`，backend 也不传密码 | 同 bridge 网络内任意容器可无鉴权读写缓存（当前只绑 127.0.0.1，风险有限但不合规） | 建议同批 |
| 5 | 🟡 P2 | geo-py / PostGIS 本轮新增，**默认关闭** | 不上不影响现网 | 可后置 |

另外：`.env.production` 我逐键核过掩码形态，**没有真实密钥被提交**（HMAC key / 高德 key / 加密 key 都是 `please…key`、`you…key` 这类占位），只有 `FSD_VDA5050_MQTT_BROKER` 是真实外部 broker 地址 —— 属基础设施信息，不算泄密，但要不要留在仓库里你定。

---

## 1. 本次要部署什么（先把工作区清点干净）

`git status` 现在是 **9 改 / 2 删 / 7 新增**，混着三类东西。**别 `git pull` 一把梭**，先按下面归类：

### ① 必须上（本轮真实功能）

```
back/fsd-dispatch/src/main/java/com/fsd/dispatch/config/GeoServiceProperties.java   （新增）
back/fsd-dispatch/src/main/java/com/fsd/dispatch/geo/GeoServiceClient.java          （新增）
back/fsd-dispatch/src/main/java/com/fsd/dispatch/geo/GeoQueryService.java           （新增）
back/fsd-dispatch/src/test/java/com/fsd/dispatch/geo/GeoQueryServiceTest.java       （新增）
back/fsd-bootstrap/src/main/resources/application.yml                               （+14 行 geo-service 块）
geo-py/                                                                             （新增整个目录）
docs/实施记录-PostGIS地理服务-2026-09-20.md                                          （新增）
```

### ② 要上，但**先决策**（见 §2 与 §3）

```
back/sql/migrations/V33__webhook_channel_type.sql      ← 被改写了，先决定回滚还是补 V52
back/sql/migrations/V34__alert_aggregation_count.sql   ← 同上
back/sql/init/00-run-migrations.sh                     ← 收窄到 V01–V20，方向正确，可上
front/Dockerfile                                       ← chmod +x node_modules/.bin/*，可上
```

### ③ 别上（本地垃圾，先删或进 .gitignore）

```
.gh-check.js          ← 被改动的临时脚本
decoded.txt           ← 调试产物
vite-dev.log          ← 日志
docs/ 下两份 D        ← 底层架构解析.md、ROADMAP-FRONTEND-IMPROVEMENT.md 的删除是**有意的**还是误删？
                        部署前必须确认，否则服务器上会永久少这两份运维文档
```

> ⚠️ **`docs/前端站点与后端逻辑审查路线图.md` 被删了 434 行**（不是整文件删除，是内容被大幅裁掉）。
> 这个量级的删改混在一次"部署"里风险很高 —— 建议**先单独提交它，与部署解耦**。

---

## 2. 🔴 P0-1：Flyway 校验和失配（唯一的"启动即失败"项）

**根因链（已验证）**：

1. `back/fsd-bootstrap/pom.xml:117-118` 把 `../sql/migrations` 映射到 `targetPath: db/migration`；
2. `application.yml:29` `flyway.locations: classpath:db/migration`；
3. 所以 **`back/sql/migrations/V33.sql` 就是 Flyway 的迁移文件本体**，改它＝改校验和；
4. 工作区把 V33/V34 从裸 `ALTER TABLE` 改成了 `information_schema` 判断 + `PREPARE/EXECUTE` 的幂等写法 —— 文件内容变了，**checksum 变了**。

**触发条件**：服务器的 `flyway_schema_history` 里已有 V33/V34 成功记录。
**表现**：后端启动日志 `Validate failed: Migration checksum mismatch for migration version 33`，容器不健康，`deploy.sh` 第 5 步 30 次等待全部超时。

### 先查，别猜

在服务器上执行（只读）：

```bash
docker exec -i fsd-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -N -e \
 "SELECT version, checksum, success FROM \`fsd_core\`.flyway_schema_history
  WHERE version IN ('33','34') ORDER BY version;"
```

### 三种情况三种处置

| 查询结果 | 处置 | 命令 |
|---|---|---|
| **无记录**（服务器还没跑到 V33） | 幂等版可以直接上，无需额外动作 | —— |
| **有记录且 success=1**（大概率） | **回滚这两个文件**，把幂等需求另开新版本 | `git checkout HEAD -- back/sql/migrations/V33__webhook_channel_type.sql back/sql/migrations/V34__alert_aggregation_count.sql` |
| 有记录但你想保留幂等写法 | 只能 `flyway repair` 重写校验和 —— **不推荐**，它会让"历史行与文件一致"这个保证失效，下次再改同样炸 | 见 §6 迁移纪律 |

**推荐第二种。** 幂等本身是对的方向，但**已应用的迁移文件永远不许改**；要幂等就写成 `V52__make_v33_v34_columns_idempotent.sql` 这种新文件（新文件不改旧校验和）。

> 顺带说明 `00-run-migrations.sh` 那个改动**是修复不是风险**：原来它 `ls /migrations/V*.sql` 会把 V21+ 也重放一遍，和 Flyway 抢；收窄到 V01–V20 与 `baseline-version: 20` 对齐了。
> 但它只在 **MySQL 数据目录为空的首次 init** 时执行，存量服务器不会重跑 → 对现网零影响，可放心上。
> ⚠️ 由此推论：**绝对不要删 `back_mysql-data` 卷**，一删就退化成"只建 V01–V20 的空库"。

---

## 3. 🔴 P0-2 / P0-3：环境变量接线

### P0-2 命名不匹配（必须二选一，不能两边都留）

```
docker-compose.prod.yml  引用  ${RABBITMQ_USER} / ${RABBITMQ_PASS}
.env.production          定义  RABBITMQ_USERNAME / RABBITMQ_PASSWORD
```

**推荐改 compose**（因为 `.env.production` 是已分发的模板，改它会让所有存量服务器 `.env` 失效）：

```yaml
# docker-compose.prod.yml  rabbitmq.environment
RABBITMQ_DEFAULT_USER: ${RABBITMQ_USERNAME}
RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD}
# backend.environment
RABBITMQ_USERNAME: ${RABBITMQ_USERNAME}
RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
```

> ⚠️ 如果服务器上的 RabbitMQ **已经用空口令建好了**，改完 compose 不会自动改密码
> （数据卷里的定义是持久的）。要么进容器 `rabbitmqctl change_password`，要么这次一并重建 RabbitMQ 卷（它不存业务数据，可重建）。

### P0-3 已定义但没传进 backend 的键（10 个）

| 键 | 不传的后果 |
|---|---|
| `FSD_ADMIN_TOKEN_HMAC_KEY` | 管理端 token 用默认/随机密钥 → **每次重启全员掉线**，且可能被弱默认值保护 |
| `FSD_FIELD_ENCRYPTION_ENABLED` / `_KEY` | 字段加密静默关闭；若库里已有密文，读出来是乱码 |
| `MQTT_FMS_USERNAME` / `_PASSWORD`、`MQTT_VEHICLE_USERNAME` / `_PASSWORD` | MQTT 对接不可用 |
| `FSD_VDA5050_MQTT_ENABLED` / `_BROKER` / `_USERNAME` / `_PASSWORD` | VDA5050 真实车接入不可用 |
| `REDIS_PASSWORD` | 见 §3.5 |
| `DB_USE_SSL` / `DB_REQUIRE_SSL` / `DB_VERIFY_SERVER_CERT` | 数据库连接不校验 TLS |

**修法**：在 `backend.environment` 里补齐，全部走 `${VAR:-默认值}` 形式，**默认值一律取"当前线上行为"**，这样漏配也只是回到今天的样子，不会突然变严：

```yaml
      FSD_ADMIN_TOKEN_HMAC_KEY: ${FSD_ADMIN_TOKEN_HMAC_KEY}
      FSD_FIELD_ENCRYPTION_ENABLED: ${FSD_FIELD_ENCRYPTION_ENABLED:false}
      FSD_FIELD_ENCRYPTION_KEY: ${FSD_FIELD_ENCRYPTION_KEY}
      MQTT_FMS_USERNAME: ${MQTT_FMS_USERNAME}
      MQTT_FMS_PASSWORD: ${MQTT_FMS_PASSWORD}
      MQTT_VEHICLE_USERNAME: ${MQTT_VEHICLE_USERNAME}
      MQTT_VEHICLE_PASSWORD: ${MQTT_VEHICLE_PASSWORD}
      FSD_VDA5050_MQTT_ENABLED: ${FSD_VDA5050_MQTT_ENABLED:false}
      FSD_VDA5050_MQTT_BROKER: ${FSD_VDA5050_MQTT_BROKER}
      FSD_VDA5050_MQTT_USERNAME: ${FSD_VDA5050_MQTT_USERNAME}
      FSD_VDA5050_MQTT_PASSWORD: ${FSD_VDA5050_MQTT_PASSWORD}
      DB_USE_SSL: ${DB_USE_SSL:false}
      DB_REQUIRE_SSL: ${DB_REQUIRE_SSL:false}
      DB_VERIFY_SERVER_CERT: ${DB_VERIFY_SERVER_CERT:false}
```

> ⚠️ **`FSD_FIELD_ENCRYPTION_ENABLED` 要单独确认**：如果生产库里**已经存在加密字段**，那这次必须把 `_KEY` 传对，否则一开就解不开旧数据。
> 部署前跑一次：`SELECT COUNT(*) FROM t_order WHERE <加密列> LIKE ...` 之类，先搞清库里到底有没有密文。

### P1-4 Redis 鉴权

```yaml
  redis:
    command: ["redis-server", "--requirepass", "${REDIS_PASSWORD}", "--appendonly", "yes"]
  backend:
    environment:
      REDIS_PASSWORD: ${REDIS_PASSWORD}
```

> 现状 Redis 只映射 `127.0.0.1:6380`，外网打不进来，所以这是**合规项不是紧急项**。
> 但注意：**加 `requirepass` 会让现有缓存全部失效一次**（旧客户端连不上），建议和业务低峰一起上。
> 另外现在 Redis 没开 AOF，重启丢缓存 —— 阈值热更新和 MAPF 时空预约都在 Redis 里，丢了会退化成 YAML 默认值。

---

## 4. 部署前：服务器现状盘点（只读，先跑这个）

把下面这段存成 `scripts/predeploy-audit.sh` 在服务器执行，**结果贴回来再决定动不动**：

```bash
#!/usr/bin/env bash
set -uo pipefail
cd /opt/dispatchflow || exit 1

echo "== 1. 当前运行版本 =="
git -C /opt/dispatchflow rev-parse --short HEAD
git -C /opt/dispatchflow status --short | head

echo "== 2. Flyway 已到第几版（决定 §2 走哪条分支）=="
docker exec -i fsd-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -N -e \
 "SELECT version, checksum, success FROM \`fsd_core\`.flyway_schema_history
  ORDER BY installed_rank DESC LIMIT 6;"

echo "== 3. RabbitMQ 现有用户（决定要不要重建卷）=="
docker exec fsd-rabbitmq rabbitmqctl list_users
docker exec fsd-rabbitmq rabbitmqctl list_permissions

echo "== 4. 数据量（决定备份耗时窗口）=="
docker exec -i fsd-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" -N -e \
 "SELECT table_name, table_rows FROM information_schema.tables
  WHERE table_schema='fsd_core' ORDER BY table_rows DESC LIMIT 10;"

echo "== 5. 卷与磁盘 =="
docker volume ls | grep -E 'back_|fsd-'
df -h /var/lib/docker

echo "== 6. 备份是否真的在跑 =="
ls -lh /opt/backups 2>/dev/null | tail -5
docker exec fsd-backend env | grep -c FLYWAY_ENABLED

echo "== 7. 宿主机 Nginx 与证书 =="
nginx -t && certbot certificates 2>/dev/null | grep -E 'Certificate Name|Expiry'
```

**判定规则**：
- 第 2 步出现 `33` / `34` 且 `success=1` → 走 §2「回滚文件」分支。
- 第 3 步 RabbitMQ 用户列表里是空的 `fsd` 用户或只有 `guest` → 本次要一并处理口令。
- 第 6 步 `/opt/backups` 最近一份超过 24h → **先补一次全量备份再部署**，不要赌。

---

## 5. 部署流程（分五步，每步都有退出条件）

### Step 0　冻结与备份（不可跳过）

```bash
cd /opt/dispatchflow
DATE=$(date +%Y%m%d-%H%M)
mysqldump -h127.0.0.1 -P3307 -uroot -p"${MYSQL_ROOT_PASSWORD}" \
  --single-transaction --routines --triggers --set-gtid-purged=OFF \
  fsd_core | gzip > "/opt/backups/fsd_core-${DATE}.sql.gz"
ls -lh "/opt/backups/fsd_core-${DATE}.sql.gz"      # 必须非零字节，且大小合理
git rev-parse HEAD > "/opt/backups/revision-${DATE}.txt"
```

> 也可以直接用仓库现成的 `scripts/backup-mysql.sh`（已核过：带 `--single-transaction --quick --routines --triggers`，
> 不锁表，保留最近 7 天）。**但它读的是 `MYSQL_USER` / `MYSQL_PASSWORD`，不是 root**，
> 手工执行前先确认这两个变量在 `.env` 里有值 —— 这正是 §3 P0-3 那批「定义了但没接线」的键之一。

### Step 1　修 P0（在本地修完，提交后再部署）

```bash
# 本地
git checkout HEAD -- back/sql/migrations/V33__webhook_channel_type.sql \
                     back/sql/migrations/V34__alert_aggregation_count.sql
# 改 docker-compose.prod.yml：RABBITMQ_* 改名 + 补齐 §3 那 14 个变量
# 清理工作区垃圾：.gh-check.js / decoded.txt / vite-dev.log
cd back && mvn -q -pl fsd-bootstrap -am test && cd ..   # 全仓 334 个测试必须全绿
git add -A && git commit -m "fix(ops): 对齐 RabbitMQ 环境变量命名并补齐 backend 未接线配置"
```

### Step 2　服务器取代码并预检

```bash
cd /opt/dispatchflow
git fetch origin && git status            # 确认服务器工作区是干净的，别 merge 本地脏改动
git merge --ff-only origin/main
cp .env.production .env.new               # 对比新增键，手工合并，不要直接覆盖 .env
diff <(grep -oE '^[A-Z_]+' .env | sort -u) <(grep -oE '^[A-Z_]+' .env.new | sort -u)
```

> ⚠️ **`.env` 千万别用 `.env.production` 覆盖** —— 里面是真实口令。只做**键的并集**，新键手工填值。

### Step 3　构建与滚动启动

```bash
docker compose -f docker-compose.prod.yml build backend frontend
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps          # 等 backend 变 healthy
docker logs fsd-backend 2>&1 | grep -Ei 'flyway|migrat|checksum|ERROR' | tail -20
```

**Flyway 日志必须看到** `Successfully applied N migrations` 或 `Schema ... is up to date`，
出现 `Validate failed` 立刻进 §8 回滚，不要重试。

### Step 4　验收

```bash
bash scripts/prod-healthcheck.sh          # 见脚本头部用法：BASE_URL + ADMIN_TOKEN
curl -fsS http://127.0.0.1:8080/internal/actuator/health
docker exec fsd-rabbitmq rabbitmqctl list_queues name messages consumers
# 事件链路必须真跑一遍：下单 → 派车 → SSE 收到推送 → Webhook 收到投递
```

---

## 6. 迁移纪律（写进团队约定，避免 P0-1 复发）

1. **已应用到任何环境的 `V*.sql` 永不修改**，包括改注释、改空白、加幂等判断。
   要改行为 → 新开 `V52__xxx.sql`。
2. **只有 V01–V20 归 `00-run-migrations.sh`，V21+ 归 Flyway**，两边不许重叠（这次已经修对了，守住）。
3. 迁移必须**向前兼容**：加列用 `NOT NULL DEFAULT`，不删列不改名，观察一个发布周期后再单独清理。
4. 带 `UPDATE` / `DELETE` 的数据迁移（V45–V47 那几类 reset）必须在**低峰**执行，并预估行数量级。
5. **禁止删 `back_mysql-data` 卷**（`external: true`，删了 Flyway 历史一起没）。

---

## 7. PostGIS / geo-py 怎么接（建议：这次先加服务、不开开关）

本轮新增的读侧地理服务默认 `FSD_GEO_SERVICE_ENABLED=false`，**关闭时所有空间判断走原 `GeoPolygonUtils` Java 手算，生产行为与接入前完全一致**。所以：

**这次只做"能开"，不做"开"**：

1. 在 `docker-compose.prod.yml` 里用 **profile** 加两个服务，默认不启动：

```yaml
  geo-postgis:
    profiles: ["geo"]
    image: postgis/postgis:16-3.4
    container_name: fsd-geo-postgis
    environment:
      POSTGRES_DB: ${GEO_PG_DB:-fsd_geo}
      POSTGRES_USER: ${GEO_PG_USER:-postgres}
      POSTGRES_PASSWORD: ${GEO_PG_PASSWORD:?必须显式提供}
    volumes:
      - fsd-geo-pgdata:/var/lib/postgresql/data
      - ./geo-py/sql/001_schema.sql:/docker-entrypoint-initdb.d/001_schema.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${GEO_PG_USER:-postgres} -d ${GEO_PG_DB:-fsd_geo}"]
      interval: 10s
      timeout: 5s
      retries: 10
    networks: [fsd-network]
    restart: unless-stopped

  geo-api:
    profiles: ["geo"]
    build: ./geo-py
    container_name: fsd-geo-api
    depends_on:
      geo-postgis: { condition: service_healthy }
    environment:
      GEO_PG_HOST: geo-postgis
      GEO_PG_PORT: 5432
      GEO_PG_DB: ${GEO_PG_DB:-fsd_geo}
      GEO_PG_USER: ${GEO_PG_USER:-postgres}
      GEO_PG_PASSWORD: ${GEO_PG_PASSWORD}
    # 不映射宿主机端口：只给 backend 容器内网调用
    networks: [fsd-network]
    restart: unless-stopped
```

   并在 `backend.environment` 加 `FSD_GEO_SERVICE_BASE_URL: ${FSD_GEO_SERVICE_BASE_URL:http://fsd-geo-api:8090}`
   两边端口一致（`geo-py` 与 `application.yml` 默认都是 8090），
   但**容器内互访要用服务名 `fsd-geo-api`，不能沿用本地的 `127.0.0.1`** —— 这是接进 compose 时唯一容易写错的地方。

2. **数据灌入方式要换**：本地是用 `geo-py/scripts/seed_from_migrations.py` 从 Flyway 迁移文件解析出 13 站点 / 6 围栏 / 55 节点。
   服务器上**MySQL 才是真相源**，应该改成从 MySQL 导（读 `t_park/t_station/t_geofence/t_road_node` 的 GCJ-02 经纬度列），
   否则服务器上的真实运营数据导不进去。**这一步没做完之前，别开开关。**

3. 开开关的顺序（未来）：`--profile geo up -d` → seed → 用 `/geo/distance/compare` 与 Java 手算对一遍 →
   再把 `FSD_GEO_SERVICE_ENABLED=true`，且**只开读侧展示，不接派单热路径**（见 `实施记录-PostGIS地理服务-2026-09-20.md` §6）。

4. 诚实预期：基准实测交叉点约 **N≈5000**，当前站点量级下 PostGIS **比手算慢**。
   这次上的理由是**能力补齐 + 事件驱动重排需要"影响域半径查询"**，不是提速。别在运维记录里写成性能优化。

---

## 8. 回滚

**触发条件**：`Validate failed` / backend 不健康 / 下单-派单主链路跑不通 / SSE 或 Webhook 断。

```bash
cd /opt/dispatchflow
# 1. 代码回到部署前那个版本
git reset --hard $(cat /opt/backups/revision-<DATE>.txt)
# 2. 重建并起旧镜像（--build 会拿旧代码，所以别在回滚时改 Dockerfile）
docker compose -f docker-compose.prod.yml up -d --build backend frontend
# 3. 只有"数据迁移本身要回退"才恢复库
gunzip -c /opt/backups/fsd_core-<DATE>.sql.gz | \
  docker exec -i fsd-mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" fsd_core
# 4. 复验
bash scripts/prod-healthcheck.sh
```

> ⚠️ **不要动 `flyway_schema_history` 里已成功的行**（`DEPLOYMENT.md` 也是这么写的）。
> 恢复备份 = 连历史一起回滚；只回代码不回库时，Flyway 会因为"库里版本比代码新"而拒绝，
> 这种情况**只能把代码版本追上去**，不能删历史行。这是本次必须把 P0-1 修干净的真实原因。

---

## 9. 部署后验收清单

- [ ] `docker compose ps` 四个服务全 `healthy`（backend 的 healthcheck 是 90s start_period，别提前判死）
- [ ] 后端日志出现 `Successfully applied` 或 `up to date`，**无 `Validate failed`**
- [ ] `prod-healthcheck.sh` 全绿；`ADMIN_TOKEN` 用**部署前就存在的账号**验，确认 HMAC key 没换错
- [ ] RabbitMQ：`list_queues` 能看到 audit / webhook 两个具名队列 + 每实例一个匿名队列，`consumers ≥ 1`
- [ ] 端到端跑一单：下单 → 自动派车 → 车辆回报 → SSE 大屏实时刷新 → Webhook 收到 HMAC 签名投递
- [ ] 阈值热更新：改一次 Redis 里的能量阈值，5s 内生效（这条能验出 Redis 密码是否接对）
- [ ] 若开了字段加密：读一条**部署前就写入的**加密记录，确认能正常解密
- [ ] 前端 `https://admin.<domain>` 可登录；Cloudflare Full(strict) 下无 521/525
- [ ] 备份任务恢复运行，且**做一次恢复演练**（从没跑过恢复演练的备份等于没有备份）

---

## 10. 这次明确不做

| 不做 | 原因 |
|---|---|
| 打开 `FSD_GEO_SERVICE_ENABLED` | 服务器侧 seed 链路还没从"解析迁移文件"改成"从 MySQL 导"（§7.2） |
| 接 Elasticsearch | 应用侧零 ES 客户端，只有日志采集侧 ELK；本轮不扩范围 |
| 给 geo-api 映射宿主机端口 | 只给 backend 内网调用，多一个暴露面就多一份风险 |
| 重建 MySQL 卷 / 改 `external: true` 卷名 | 那是唯一数据源，动它等于赌上全部运营数据 |
| 把 docs 的大删改和这次部署混在一个提交里 | §1 ③：先单独提交、单独 review |
