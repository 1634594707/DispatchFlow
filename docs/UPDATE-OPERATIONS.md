# DispatchFlow 云服务器更新操作手册

> 适用场景：已在云服务器通过 **Docker Compose** 部署 DispatchFlow，需要滚动升级后端、前端或数据库结构。  
> 首次部署请参阅 [DEPLOYMENT.md](./DEPLOYMENT.md)。  
> 最后更新：2026-05-31

---

## 快速检查清单

更新前按顺序确认：

- [ ] 已阅读目标版本的 [CHANGELOG.md](../CHANGELOG.md) 或 [docs/releases/](./releases/) 说明
- [ ] 确认本次升级是否需要 **SQL 迁移**（见 [§4](#4-数据库迁移)）
- [ ] 已备份 MySQL（见 [§3](#3-更新前备份)）
- [ ] **禁止** 在生产环境执行 `docker compose down -v`（会清空数据库卷）
- [ ] 更新完成后执行 [§7 验证清单](#7-更新后验证)

---

## 1. 典型部署架构

云服务器上常见的目录与组件如下（路径可按实际安装调整）：

```text
/opt/dispatchflow/              # 项目根目录（git clone 或 Release 解压）
├── docker-compose.yml          # 引用 back/docker-compose.yml
├── back/
│   ├── docker-compose.yml      # MySQL / Redis / RabbitMQ / 后端
│   ├── docker-compose.ghcr.yml # 可选：使用 GHCR 预构建镜像
│   └── sql/migrations/         # V01–V18 迁移脚本
├── front/
│   └── dist/                   # 生产静态资源（npm run build 产出）
└── .env                        # 端口、密码等（可选）

Nginx（宿主机）                 # 443/80 → 前端静态 + /api 反代后端
Docker 数据卷                   # mysql-data / redis-data / rabbitmq-data
```

| 容器 | 名称 | 说明 |
|------|------|------|
| MySQL 8.4 | `fsd-mysql` | 业务数据，**持久化在卷中** |
| Redis 7.4 | `fsd-redis` | Fleet 运行态缓存 |
| RabbitMQ 3.13 | `fsd-rabbitmq` | 事件 Outbox 投递 |
| 后端 API | `fsd-core-server` | Spring Boot，默认 **8080** |

前端无官方 Docker 镜像，生产环境通常由 **Nginx** 托管 `front/dist` 静态文件，并将 `/api` 反向代理到后端。

---

## 2. 确认当前版本

SSH 登录云服务器后：

```bash
# 进入部署目录
cd /opt/dispatchflow   # 替换为你的实际路径

# 查看 Git 版本（若从仓库部署）
git describe --tags --always
git log -1 --oneline

# 查看正在运行的后端镜像
docker inspect fsd-core-server --format '{{.Config.Image}}'

# 健康检查
curl -s http://127.0.0.1:8080/api/health
# 期望：{"code":0,"data":{"service":"fsd-core-server","status":"UP",...}}
```

若通过域名访问，也可直接：

```bash
curl -s https://你的域名/api/health
```

---

## 3. 更新前备份

### 3.1 MySQL（必做）

```bash
BACKUP_DIR=/opt/dispatchflow/backups/$(date +%Y%m%d-%H%M)
mkdir -p "$BACKUP_DIR"

docker exec fsd-mysql mysqldump \
  -uroot -p'你的root密码' \
  --default-character-set=utf8mb4 \
  --single-transaction \
  --routines \
  fsd_core > "$BACKUP_DIR/fsd_core.sql"

ls -lh "$BACKUP_DIR/fsd_core.sql"
```

> 默认密码为 `root`（见 `back/docker-compose.yml`）。生产环境请使用 `.env` 或 compose 中已修改的强密码。

### 3.2 前端静态资源（建议）

```bash
tar -czf "$BACKUP_DIR/front-dist.tar.gz" -C front dist
```

### 3.3 配置文件

```bash
cp .env "$BACKUP_DIR/.env" 2>/dev/null || true
cp back/docker-compose.yml "$BACKUP_DIR/docker-compose.yml"
```

### 3.4 Redis / RabbitMQ

- **Redis**：主要为 Fleet 运行态，重启后会由仿真/遥测重建，一般无需备份。
- **RabbitMQ**：Outbox 未投递消息在 MySQL 中，队列本身通常可不备份。

---

## 4. 数据库迁移

DispatchFlow **不使用 Flyway 自动迁移**。MySQL 容器**仅在首次初始化数据卷时**通过 `back/sql/init/00-run-migrations.sh` 顺序执行 `V01`–`V18`。

**已有数据卷升级时，必须手动补跑缺失的迁移脚本。**

### 4.1 迁移脚本一览

| 版本 | 文件 | 主要内容 |
|------|------|----------|
| V01 | `V01__init_schema.sql` | 初始表结构 |
| V02–V05 | … | 调度约束、Outbox、园区站点、字符集修复 |
| V06 | `V06__exception_severity.sql` | 异常分级 |
| V07–V08 | … | 停车位、充电桩、充电会话 |
| V09–V10 | … | 路网、车辆网关 |
| V11–V12 | … | 管理员用户、维保 |
| V13–V13b | … | Phase 8/9、路段交通字段 |
| V14 | `V14__ensure_default_park_active.sql` | 默认园区激活 |
| V15 | `V15__phase12_phase13.sql` | Phase 12/13 |
| V16 | `V16__phase14.sql` | Phase 14 家纺垂直 |
| V17 | `V17__roadmap_remaining.sql` | 路线图剩余项 |
| V18 | `V18__phase15.sql` | Phase 15 高峰 cron 等 |
| V19 | `V19__vda5050_vehicle_binding.sql` | VDA5050 manufacturer / serialNumber 绑定 |
| V20 | `V20__task_pool_index.sql` | 任务池查询索引 |

完整列表见 `back/sql/migrations/`。

### 4.2 判断是否需要迁移

1. 阅读目标版本 Release 说明（如 [releases/v2.0.0.md](./releases/v2.0.0.md)）中的「数据库迁移」章节。
2. 对照 CHANGELOG，若含 **Added SQL migration** 或 **数据库** 相关说明，则需补跑。
3. 可通过检查表/列是否存在辅助判断，例如 V18：

```bash
docker exec fsd-mysql mysql -uroot -p'密码' -e \
  "SHOW COLUMNS FROM fsd_core.t_peak_mode_state LIKE 'schedule_end_cron';"
```

无结果则表示 V18 尚未应用。

### 4.3 执行单条迁移（标准流程）

以 `V18__phase15.sql` 为例，**在后端重启前**执行：

```bash
cd /opt/dispatchflow

docker cp back/sql/migrations/V18__phase15.sql fsd-mysql:/tmp/V18.sql

docker exec fsd-mysql sh -c \
  "mysql -uroot -p'密码' --default-character-set=utf8mb4 fsd_core < /tmp/V18.sql"
```

批量补跑多条（按文件名排序，跳过已执行的）：

```bash
for f in V15 V16 V17 V18; do
  FILE=$(ls back/sql/migrations/${f}__*.sql 2>/dev/null | head -1)
  [ -n "$FILE" ] || continue
  echo ">> Applying $(basename "$FILE")"
  docker cp "$FILE" fsd-mysql:/tmp/migrate.sql
  docker exec fsd-mysql sh -c \
    "mysql -uroot -p'密码' --default-character-set=utf8mb4 fsd_core < /tmp/migrate.sql"
done
```

> 含中文数据的脚本务必加 `--default-character-set=utf8mb4`。

### 4.4 迁移失败处理

| 现象 | 处理 |
|------|------|
| `Duplicate column name` | 该迁移已执行过，跳过即可 |
| `Table doesn't exist` | 可能缺少更早的 Vxx，按顺序从缺失版本补跑 |
| 中文乱码 | 用 utf8mb4 重新导入；必要时参考 V05 修复脚本 |

**切勿** 因迁移失败而 `docker compose down -v`，除非可以接受**全量丢数据**并重新初始化。

---

## 5. 后端更新

任选一种方式，推荐 **方式 A**。

### 方式 A — 拉取 GHCR 预构建镜像（推荐）

GitHub Release 发布后会自动推送镜像到 `ghcr.io/1634594707/dispatchflow`。

```bash
cd /opt/dispatchflow/back

# 拉取指定版本（替换为目标 tag，如 v2.0.0）
export FSD_IMAGE=ghcr.io/1634594707/dispatchflow:v2.0.0
docker pull "$FSD_IMAGE"

# 使用 ghcr 覆盖本地 build，滚动重启后端
docker compose -f docker-compose.yml -f docker-compose.ghcr.yml up -d fsd-core-server

# 查看启动日志
docker logs -f fsd-core-server --tail 100
```

若镜像为私有 Package，需先登录：

```bash
echo "你的GitHub_PAT" | docker login ghcr.io -u 你的GitHub用户名 --password-stdin
```

### 方式 B — 服务器 git pull + 本地构建

适合无法访问 GHCR、或需要自行改代码的场景。

```bash
cd /opt/dispatchflow

git fetch origin
git checkout main          # 或目标 release tag：git checkout v2.0.0
git pull origin main

# 先执行 §4 中的 SQL 迁移

cd back
docker compose build --no-cache fsd-core-server
docker compose up -d fsd-core-server

docker logs -f fsd-core-server --tail 100
```

### 方式 C — GitHub Release 离线包

在 [GitHub Releases](https://github.com/1634594707/DispatchFlow/releases) 下载 `DispatchFlow-<版本>-bundle.zip`：

```bash
cd /opt/dispatchflow
unzip DispatchFlow-v2.0.0-bundle.zip -d /tmp/df-release

# 替换 JAR 并重建镜像，或仅更新 compose 中的 jar 挂载（需自行调整）
cp /tmp/df-release/v2.0.0/backend/fsd-core-server.jar back/fsd-bootstrap/target/  # 若走 Dockerfile 需调整

# 迁移脚本
cp /tmp/df-release/v2.0.0/sql/migrations/*.sql back/sql/migrations/
# 然后按 §4 补跑新迁移，再按方式 B 构建
```

Release 包内包含 `frontend/`、`sql/migrations/`、`deploy/`，可直接用于前端替换（§6）。

---

## 6. 前端更新

前端为 Vite 静态构建，更新步骤与后端独立，但**建议同一版本一起发布**。

### 6.1 构建

```bash
cd /opt/dispatchflow/front

# 若 API 与页面同域（Nginx 反代 /api），通常无需设置 VITE_API_BASE_URL
# 若 API 在独立域名，构建前设置：
# echo 'VITE_API_BASE_URL=https://api.example.com' > .env.production

npm ci
npm run build
```

产物在 `front/dist/`。

### 6.2 部署到 Nginx

```bash
# 示例：站点根目录为 /var/www/dispatchflow
sudo rsync -a --delete front/dist/ /var/www/dispatchflow/
sudo nginx -t && sudo systemctl reload nginx
```

### 6.3 Nginx 参考配置

同域反代（推荐，与线上演示 [aplicity.online](https://www.aplicity.online) 类似）：

```nginx
server {
    listen 443 ssl http2;
    server_name your-domain.com;

    root /var/www/dispatchflow;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        # SSE 监控流
        proxy_buffering off;
        proxy_read_timeout 3600s;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

后端端口若通过 `.env` 改过 `APP_HOST_PORT`，请同步修改 `proxy_pass`。

---

## 7. 更新后验证

按顺序检查，全部通过再对外宣告完成。

| # | 检查项 | 命令 / 操作 |
|---|--------|-------------|
| 1 | 容器健康 | `docker compose ps` 全部 `running` / `healthy` |
| 2 | 后端健康 | `curl -s http://127.0.0.1:8080/api/health` → `status: UP` |
| 3 | Swagger | 浏览器打开 `/swagger-ui.html`（或内网访问 8080） |
| 4 | 登录 | 管理端登录、TOTP（若已启用） |
| 5 | 园区上下文 | 顶栏切换园区，车辆/异常列表一致过滤 |
| 6 | 调度 | 创建测试任务、自动/手动派车 |
| 7 | 监控 SSE | 打开 `/vehicle-tracking`，车辆位置实时刷新 |
| 8 | 新功能 | 对照 Release 说明验收本次新增页面/API |

```bash
# 一键冒烟
curl -sf http://127.0.0.1:8080/api/health | grep UP
docker compose -f back/docker-compose.yml ps
```

---

## 8. 回滚

### 8.1 后端回滚

**GHCR 方式：** 改回旧 tag 并重启

```bash
cd /opt/dispatchflow/back
export FSD_IMAGE=ghcr.io/1634594707/dispatchflow:v2.0.0   # 上一稳定版本
docker pull "$FSD_IMAGE"
docker compose -f docker-compose.yml -f docker-compose.ghcr.yml up -d fsd-core-server
```

**本地构建方式：** `git checkout <旧tag>` 后重新 `build` + `up -d`。

### 8.2 前端回滚

```bash
tar -xzf /opt/dispatchflow/backups/YYYYMMDD-HHMM/front-dist.tar.gz -C /opt/dispatchflow/front
sudo rsync -a --delete front/dist/ /var/www/dispatchflow/
sudo systemctl reload nginx
```

### 8.3 数据库回滚

SQL 迁移**一般不可逆**。若新迁移导致严重问题：

1. 停止后端：`docker compose stop fsd-core-server`
2. 从 §3.1 备份恢复：

```bash
docker exec -i fsd-mysql mysql -uroot -p'密码' fsd_core < /path/to/fsd_core.sql
```

3. 回滚后端/前端到与数据库 schema **匹配**的旧版本。

> 最佳实践：重大版本先在 staging 环境验证迁移，再在生产执行。

---

## 9. 标准更新流程（汇总）

以从 `v2.0.0` 升级到更新版为例：

```bash
cd /opt/dispatchflow

# 1. 备份
BACKUP_DIR=backups/$(date +%Y%m%d-%H%M)
mkdir -p "$BACKUP_DIR"
docker exec fsd-mysql mysqldump -uroot -p'密码' --default-character-set=utf8mb4 \
  --single-transaction fsd_core > "$BACKUP_DIR/fsd_core.sql"

# 2. 拉代码 / 确认 Release 说明
git pull origin main
# 阅读 docs/releases/<版本>.md 与 CHANGELOG

# 3. SQL 迁移（按 Release 说明补跑 Vxx）
docker cp back/sql/migrations/V18__phase15.sql fsd-mysql:/tmp/V18.sql
docker exec fsd-mysql sh -c \
  "mysql -uroot -p'密码' --default-character-set=utf8mb4 fsd_core < /tmp/V18.sql"

# 4. 后端
cd back
export FSD_IMAGE=ghcr.io/1634594707/dispatchflow:v2.1.0   # 目标版本
docker pull "$FSD_IMAGE"
docker compose -f docker-compose.yml -f docker-compose.ghcr.yml up -d fsd-core-server

# 5. 前端
cd ../front
npm ci && npm run build
sudo rsync -a --delete dist/ /var/www/dispatchflow/
sudo nginx -t && sudo systemctl reload nginx

# 6. 验证
curl -s http://127.0.0.1:8080/api/health
```

---

## 10. 常见问题

| 现象 | 原因 | 处理 |
|------|------|------|
| 园区相关 API 404 | 后端 JAR 模块未完整打包 | 使用 `-pl fsd-bootstrap -am` 构建；Docker 镜像需重新 build |
| 升级后页面空白 / 旧 UI | 浏览器或 Nginx 缓存 | 强制刷新；确认 `dist/` 已 rsync；检查 Nginx `root` 路径 |
| SSE 监控不推送 | Nginx 缓冲未关闭 | 配置 `proxy_buffering off`（见 §6.3） |
| 中文乱码 | 迁移 charset 不对 | 导入时加 `--default-character-set=utf8mb4` |
| `docker compose up` 重建 MySQL | 误删数据卷 | **禁止** `down -v`；从备份恢复 |
| 拉 GHCR 镜像 401 | 私有包或未登录 | `docker login ghcr.io` |
| 后端启动后立刻退出 | 迁移未跑 / DB 连不上 | `docker logs fsd-core-server`；检查 MySQL 健康与密码 |

更多部署细节见 [DEPLOYMENT.md](./DEPLOYMENT.md)。

---

## 11. 相关文档

| 文档 | 用途 |
|------|------|
| [DEPLOYMENT.md](./DEPLOYMENT.md) | 首次部署、本地开发、端口说明 |
| [CHANGELOG.md](../CHANGELOG.md) | 版本变更摘要 |
| [docs/releases/](./releases/) | 各版本迁移与验收说明 |
| [SECURITY.md](../SECURITY.md) | 公网暴露前的安全建议 |
