#!/bin/bash
# =====================================================================
# DispatchFlow 一键部署脚本（P0-12）
# 在已初始化的服务器上执行：
#   bash scripts/deploy.sh
# 前置条件：
#   1. 已执行 scripts/init-server.sh
#   2. 项目代码已上传到 /opt/dispatchflow
#   3. /opt/dispatchflow/.env 已配置实际密码
# =====================================================================
set -e

PROJECT_DIR="${PROJECT_DIR:-/opt/dispatchflow}"
ENV_FILE="${PROJECT_DIR}/.env"
COMPOSE_FILE="${PROJECT_DIR}/docker-compose.prod.yml"
DOMAIN="${FSD_DOMAIN:-aplicity.online}"

cd "$PROJECT_DIR"

echo "===================================================="
echo "  DispatchFlow 一键部署"
echo "  项目目录: $PROJECT_DIR"
echo "  域名:     $DOMAIN"
echo "===================================================="

# ---------- 1. 检查 .env ----------
echo "[1/8] 检查环境变量配置..."
if [ ! -f "$ENV_FILE" ]; then
  if [ -f "${PROJECT_DIR}/.env.production" ]; then
    cp "${PROJECT_DIR}/.env.production" "$ENV_FILE"
    echo "  已从 .env.production 复制为 .env，请编辑填入实际密码后重新运行"
    exit 1
  else
    echo "[ERROR] 未找到 .env 或 .env.production" >&2
    exit 1
  fi
fi
# shellcheck disable=SC1090
set -a; . "$ENV_FILE"; set +a
if [ -z "${MYSQL_ROOT_PASSWORD:-}" ] || [ "${MYSQL_ROOT_PASSWORD}" = "your-strong-mysql-password" ]; then
  echo "[ERROR] 请在 $ENV_FILE 中设置真实的 MYSQL_ROOT_PASSWORD" >&2
  exit 1
fi
echo "  .env OK"

# ---------- 2. 创建 Docker 卷 ----------
echo "[2/8] 创建 Docker 数据卷..."
docker volume create back_mysql-data 2>/dev/null || true
docker volume create back_redis-data 2>/dev/null || true
docker volume create back_rabbitmq-data 2>/dev/null || true
echo "  数据卷已就绪"

# ---------- 3. Flyway 前置检查 ----------
# 已应用迁移的校验和一旦与磁盘文件不一致，后端在 FLYWAY_ENABLED=true 下启动即失败。
# 默认只 validate；repair 会删除失败的迁移记录并对齐校验和，因此必须显式开启。
echo "[3/8] Flyway 前置检查..."
FLYWAY_IMAGE="${FLYWAY_IMAGE:-flyway/flyway:10.10-alpine}"
if docker inspect -f '{{.State.Running}}' fsd-mysql 2>/dev/null | grep -q true; then
  FLYWAY_NET="$(docker inspect -f '{{range $k, $v := .NetworkSettings.Networks}}{{$k}}{{break}}{{end}}' fsd-mysql)"
  # 口令只走 --env-file，不进命令行参数、不落任何记录
  FLYWAY_ENV="$(mktemp)"
  chmod 600 "$FLYWAY_ENV"
  printf 'FLYWAY_URL=jdbc:mysql://fsd-mysql:3306/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai\nFLYWAY_USER=root\nFLYWAY_PASSWORD=%s\n' \
    "${MYSQL_DATABASE:-fsd_core}" "$MYSQL_ROOT_PASSWORD" > "$FLYWAY_ENV"
  flyway_cli() {
    docker run --rm --network "$FLYWAY_NET" --env-file "$FLYWAY_ENV" \
      -v "$PROJECT_DIR/back/sql/migrations:/flyway/sql:ro" "$FLYWAY_IMAGE" \
      -locations=filesystem:/flyway/sql -baselineVersion=20 -baselineOnMigrate=true "$@"
  }
  # 注意：validate 默认会把「已解析但尚未应用」的迁移也算失败 —— 那是每一次正常部署的常态，
  # 不是有人在改已应用的迁移。这里显式忽略 pending，只保留真正要拦的那一类：
  # 已应用的迁移与磁盘文件校验和/描述不一致（= 有人改了历史迁移）。
  PENDING="$(flyway_cli -ignoreMigrationPatterns='*:pending' info 2>/dev/null | grep -cP '^\|\s*Pending' || true)"
  if flyway_cli -ignoreMigrationPatterns='*:pending' validate; then
    echo "  已应用迁移的校验和一致；${PENDING:-0} 个待应用迁移将由后端启动时的 Flyway 应用。可以启动。"
  elif [ "${DEPLOY_FLYWAY_REPAIR:-0}" = "1" ]; then
    echo "  validate 失败，DEPLOY_FLYWAY_REPAIR=1 → 执行 repair 后重新 validate..."
    flyway_cli repair && flyway_cli -ignoreMigrationPatterns='*:pending' validate
  else
    rm -f "$FLYWAY_ENV"
    echo "[ERROR] Flyway validate 失败（已忽略待应用迁移后仍失败）⇒ 有人改了**已应用**的迁移：" >&2
    echo "        正确做法是 git 还原那个文件、改动另开新迁移号；" >&2
    echo "        只有确认是 history 里残留失败行时，才用 DEPLOY_FLYWAY_REPAIR=1 重跑（repair 会改历史表）。" >&2
    exit 1
  fi
  rm -f "$FLYWAY_ENV"
else
  echo "  [WARN] fsd-mysql 未运行，跳过 validate（首次部署由容器初始化时自行迁移）"
fi

# ---------- 4. 申请 SSL 证书 ----------
echo "[4/8] 检查 SSL 证书..."
CERT_DIR="/etc/letsencrypt/live/${DOMAIN}"
if [ ! -d "$CERT_DIR" ]; then
  echo "  申请 SSL 证书（standalone 模式，需先停止占用 80 端口的服务）..."
  # 临时停止前端容器（若存在）
  docker stop fsd-frontend 2>/dev/null || true
  certbot certonly --standalone \
    -d "${DOMAIN}" \
    -d "www.${DOMAIN}" \
    -d "admin.${DOMAIN}" \
    --non-interactive --agree-tos \
    -m "admin@${DOMAIN}" \
    || {
      echo "[WARN] SSL 证书申请失败，请检查 80 端口是否可访问、DNS 是否解析到本机" >&2
      echo "       可稍后手动执行：certbot certonly --standalone -d ${DOMAIN} -d www.${DOMAIN} -d admin.${DOMAIN}" >&2
    }
else
  echo "  SSL 证书已存在: $CERT_DIR"
fi

# ---------- 5. 构建并启动 ----------
echo "[5/8] 构建并启动容器（首次构建可能耗时较长）..."
docker compose -f "$COMPOSE_FILE" up -d --build

# ---------- 6. 等待健康检查 ----------
echo "[6/8] 等待后端健康检查通过..."
for i in $(seq 1 30); do
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' fsd-backend 2>/dev/null || echo "starting")
  if [ "$STATUS" = "healthy" ]; then
    echo "  后端已就绪"
    break
  fi
  echo "  等待中... ($i/30) 状态: $STATUS"
  sleep 10
done

# ---------- 7. 验证 ----------
echo "[7/8] 验证服务状态..."
docker compose -f "$COMPOSE_FILE" ps

echo ""
echo "  健康检查:"
curl -fsS "http://127.0.0.1:8080/internal/actuator/health" 2>/dev/null && echo "" || echo "  [WARN] 后端健康检查未通过，请查看日志: docker logs fsd-backend"

# ---------- 8. 完成 ----------
echo "[8/8] 部署完成"
echo "===================================================="
echo "  访问地址:"
echo "    主域名（手机端）: https://${DOMAIN}"
echo "    管理端:           https://admin.${DOMAIN}"
echo ""
echo "  常用命令:"
echo "    查看日志:   docker compose -f $COMPOSE_FILE logs -f"
echo "    重启服务:   docker compose -f $COMPOSE_FILE restart"
echo "    停止服务:   docker compose -f $COMPOSE_FILE down"
echo "    MySQL备份:  /opt/scripts/backup-mysql.sh"
echo "===================================================="
