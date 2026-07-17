#!/bin/bash
# =====================================================================
# DispatchFlow MySQL 自动备份脚本
# - 每日 mysqldump，保留最近 7 天
# - 使用方式：crontab -e 添加：0 2 * * * /opt/dispatchflow/scripts/backup-mysql.sh
# - 需要先在 /opt/dispatchflow/.env 中配置 MYSQL_ROOT_PASSWORD
# =====================================================================
set -e

# 加载环境变量
ENV_FILE="${1:-/opt/dispatchflow/.env}"
if [ -f "$ENV_FILE" ]; then
  # shellcheck disable=SC1090
  set -a
  . "$ENV_FILE"
  set +a
fi

BACKUP_DIR="/backup/mysql/dispatchflow"
DATE=$(date +%Y%m%d_%H%M%S)
MAX_BACKUPS=7
MYSQL_CONTAINER="fsd-mysql"
MYSQL_DB="fsd_core"
MYSQL_USER="root"
MYSQL_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"

if [ -z "$MYSQL_PASSWORD" ]; then
  echo "[ERROR] MYSQL_ROOT_PASSWORD 未配置，请在 $ENV_FILE 中设置" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"

BACKUP_FILE="${BACKUP_DIR}/backup_${DATE}.sql"
COMPRESSED_FILE="${BACKUP_FILE}.gz"

echo "[INFO] 开始备份 MySQL 数据库 $MYSQL_DB → $COMPRESSED_FILE"

# 检查容器是否运行
if ! docker ps --format '{{.Names}}' | grep -q "^${MYSQL_CONTAINER}$"; then
  echo "[ERROR] MySQL 容器 $MYSQL_CONTAINER 未运行" >&2
  exit 1
fi

# 执行备份并压缩
docker exec "${MYSQL_CONTAINER}" \
  mysqldump -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" \
  --single-transaction --quick --routines --triggers \
  "${MYSQL_DB}" | gzip > "$COMPRESSED_FILE"

# 清理超过 MAX_BACKUPS 的旧备份
cd "$BACKUP_DIR"
# shellcheck disable=SC2012
ls -t backup_*.sql.gz 2>/dev/null | tail -n +$((MAX_BACKUPS + 1)) | xargs -r rm -f

# 输出备份摘要
SIZE=$(du -h "$COMPRESSED_FILE" | cut -f1)
COUNT=$(ls -1 backup_*.sql.gz 2>/dev/null | wc -l)
echo "[OK] 备份完成: $COMPRESSED_FILE ($SIZE)"
echo "[INFO] 当前保留备份数: $COUNT / $MAX_BACKUPS"
