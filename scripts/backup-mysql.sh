#!/bin/bash
# =====================================================================
# DispatchFlow MySQL 自动备份脚本
#
# 特性：
#   - 每日 mysqldump，保留最近 7 份
#   - **写完立即自校验**（体积 / 表数 / mysqldump 结束标记）后才算成功
#   - **只有校验通过才轮转删旧备份** —— 坏备份绝不允许挤掉好备份
#
# 用法：
#   手动：bash scripts/backup-mysql.sh
#   cron：0 2 * * * /opt/dispatchflow/scripts/backup-mysql.sh >> /var/log/fsd-backup.log 2>&1
#
# 依赖：/opt/dispatchflow/.env 里的 MYSQL_ROOT_PASSWORD
#
# ★ 重要：宿主机**没有** mysqldump / mysql 客户端（实测 command not found，exit=127），
#   所以必须走容器内的 mysqldump；恢复时同理走容器内的 mysql（见 scripts/restore-drill.sh）。
#
# 退出码：0 成功 / 1 配置缺失 / 2 容器未运行 / 3 备份自校验失败
# =====================================================================
set -uo pipefail

ENV_FILE="${1:-/opt/dispatchflow/.env}"
BACKUP_DIR="${FSD_BACKUP_DIR:-/opt/backups}"
MYSQL_CONTAINER="fsd-mysql"
MYSQL_DB="fsd_core"
MAX_BACKUPS="${FSD_BACKUP_KEEP:-7}"
MIN_BYTES="${FSD_BACKUP_MIN_BYTES:-200000}"
MIN_TABLES="${FSD_BACKUP_MIN_TABLES:-30}"

log() { echo "[$(date '+%F %T')] $*"; }
die() { log "[ERROR] $1" >&2; exit "${2:-1}"; }

if [ -f "$ENV_FILE" ]; then
  # shellcheck disable=SC1090
  set -a; . "$ENV_FILE"; set +a
fi
MYSQL_PASSWORD="${MYSQL_ROOT_PASSWORD:-}"
[ -n "$MYSQL_PASSWORD" ] || die "MYSQL_ROOT_PASSWORD 未在 $ENV_FILE 里配置" 1

docker ps --format '{{.Names}}' | grep -qx "$MYSQL_CONTAINER" || die "容器 $MYSQL_CONTAINER 未运行" 2

mkdir -p "$BACKUP_DIR"
STAMP=$(date +%Y%m%d_%H%M%S)
OUT="${BACKUP_DIR}/fsd_core-${STAMP}.sql.gz"
TMP="${OUT}.part"
ERR="${TMP}.err"

log "[INFO] 开始备份 ${MYSQL_DB} -> ${OUT}"
docker exec -i "$MYSQL_CONTAINER" mysqldump -uroot -p"$MYSQL_PASSWORD" \
  --single-transaction --quick --routines --triggers --set-gtid-purged=OFF \
  "$MYSQL_DB" 2>"$ERR" | gzip > "$TMP"

# ---------------- 自校验 ----------------
BYTES=$(stat -c%s "$TMP" 2>/dev/null || echo 0)
TABLES=$(gunzip -c "$TMP" 2>/dev/null | grep -c '^CREATE TABLE')
DONE=$(gunzip -c "$TMP" 2>/dev/null | tail -3 | grep -c 'Dump completed')

if [ "$BYTES" -lt "$MIN_BYTES" ] || [ "$TABLES" -lt "$MIN_TABLES" ] || [ "$DONE" -eq 0 ]; then
  log "[ERROR] 备份自校验不通过：bytes=${BYTES}（下限 ${MIN_BYTES}）tables=${TABLES}（下限 ${MIN_TABLES}）dump_completed=${DONE}"
  grep -v 'Using a password' "$ERR" 2>/dev/null | head -5 >&2
  rm -f "$TMP" "$ERR"
  log "[ERROR] 已删除不合格产物；**未轮转任何既有备份**"
  exit 3
fi

mv "$TMP" "$OUT"
rm -f "$ERR"
log "[OK] 备份完成 ${OUT}  $(du -h "$OUT" | cut -f1)  tables=${TABLES}  dump_completed=yes"

# ---------------- 轮转（仅在校验通过后）----------------
cd "$BACKUP_DIR" || exit 0
ls -1t fsd_core-*.sql.gz 2>/dev/null | tail -n +$((MAX_BACKUPS + 1)) | while IFS= read -r old; do
  log "[INFO] 轮转删除旧备份 $old"
  rm -f -- "$old"
done
log "[INFO] 当前保留 $(ls -1 fsd_core-*.sql.gz 2>/dev/null | wc -l) / ${MAX_BACKUPS} 份"
