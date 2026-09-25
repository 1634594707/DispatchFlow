#!/bin/bash
# =====================================================================
# M0 / T0-a 倍速两档实测驱动
#
#   bash scripts/dev/m0-gear-sweep.sh              # 跑 500 与 250 两档
#   bash scripts/dev/m0-gear-sweep.sh 250          # 只跑指定档
#
# 每档：重置本地演示库 → 以该 tick 起后端 → 跑一条真实订单到终态 → 收三个观测数
#       （下单→送达墙钟、tick 实际节拍、3 s 重采样位移连贯性）→ 关后端。
# 产物：tmp/m0/gear-<tick>.json + tmp/m0/backend-<tick>.log
# 测量本身不改任何仿真常数；定档与固化见 T0-b（run-demo-local.sh）。
# =====================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"
OUT=tmp/m0
GEARS=("${@:-500 250}")
[ $# -eq 0 ] && GEARS=(500 250)
mkdir -p "$OUT"
BASE="${M0_BASE_URL:-http://127.0.0.1:8080/api}"

mysql_q() {
  printf '%s\n' "$1" | docker exec -i fsd-mysql mysql --default-character-set=utf8mb4 -uroot -proot -N fsd_core 2>/dev/null
}

stop_backend() {
  # 只按命令行特征收本项目 fork 出来的后端进程，绝不按进程名批量 kill
  powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe' AND CommandLine LIKE '%com.fsd.bootstrap%'\" | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force -ErrorAction SilentlyContinue }" >/dev/null 2>&1
  sleep 3
}

wait_ready() {
  local deadline=$((SECONDS + 420))
  while [ $SECONDS -lt $deadline ]; do
    code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/admin/park/vehicles" 2>/dev/null || echo 000)
    if [ "$code" = "200" ]; then
      echo "  [i] 后端就绪（${SECONDS}s 起）"
      return 0
    fi
    sleep 3
  done
  echo "  [ERROR] 后端 420 s 内未就绪" >&2
  return 1
}

for TICK in "${GEARS[@]}"; do
  echo "================ gear tick=${TICK}ms ================"
  stop_backend
  bash scripts/dev/reset-demo-dispatchable.sh --yes >/dev/null 2>&1 || echo "  [WARN] reset 脚本非零退出，继续"

  PICKUP=$(mysql_q "SELECT COUNT(*) FROM t_station WHERE deleted=0 AND station_type IN ('PICKUP','DROPOFF') AND status='ACTIVE';" | tr -d '\r')
  echo "  [i] 可用作起终点的 ACTIVE 作业站数=$PICKUP（起终点 ID 由测试脚本自己调 /admin/park/stations 解析）"

  echo "  [i] 起后端 tick=$TICK ms（首档含 mvn install，约数分钟）"
  RUN_BACKEND_FRESH="${RUN_BACKEND_FRESH:-1}" \
  FSD_PARK_SIMULATION_TICK_INTERVAL_MS="$TICK" \
    nohup bash scripts/dev/run-backend-local.sh >"$OUT/backend-$TICK.log" 2>&1 &
  export RUN_BACKEND_FRESH=0

  if ! wait_ready; then
    tail -30 "$OUT/backend-$TICK.log" >&2
    continue
  fi

  node scripts/dev/m0-tick-gear-test.mjs "$TICK" 2>&1 | tail -60
  echo "  [i] 产物：$OUT/gear-$TICK.json"
done

stop_backend
echo "================ sweep done ================"
