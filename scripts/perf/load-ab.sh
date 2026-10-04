#!/bin/bash
# 一轮可复现的负载测量：热身 → 空转 60s → 标准档 → 2× 档，每步都带 cgroup 采样与数据态前后照。
#
# 用法：bash scripts/perf/load-ab.sh <侧标签>
#   侧标签例如 pre-P0 / p0-after / 2026-10-05；产物落在 tmp/perf/（已 gitignore）。
# 可调（环境变量）：
#   DF_BASE_URL      被测地址，默认 http://host.docker.internal:8080（直连后端，不过前端 nginx）
#   DF_HOLD_S        每档保持时长，默认 150
#   DF_SKIP_2X=1     只跑标准档
#
# 前置（缺一不可，否则数字不可比）：
#   1) FSD_PARK_SIMULATION_TICK_INTERVAL_MS=500 —— 与 prod/k8s 同节拍；本地默认是代码的 1000ms
#   2) FSD_AMAP_DRIVING_ENABLED=false          —— 开着就是拿高德的延迟与配额当我们的 p95
#   3) FSD_MOBILE_ORDER_UNSAFE_NO_AUTH=true    —— 场景是匿名链路，带 key 会被限流硬顶
#   4) 没有其他流量（前端 dev/浏览器大屏都会改变读数）
#
# 为什么要固定"侧标签"这套流程：性能结论只有在同一节奏、同一数据态下才可比，
# 而本项目的仿真与全池扫成本都随积压涨 —— 所以每轮都自动记录 db 前后状态。
set -u
SIDE="${1:?用法: load-ab.sh <侧标签>}"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$REPO/tmp/perf"
BASE_URL="${DF_BASE_URL:-http://host.docker.internal:8080}"
HOLD_S="${DF_HOLD_S:-150}"
mkdir -p "$OUT"
OUTWIN="$(cygpath -w "$OUT" 2>/dev/null || echo "$OUT")"

bash "$REPO/scripts/perf/db-state.sh" "start-$SIDE"

# 热身：10 条串行下单吃掉 JIT 冷启动。不热身的话第一档的 p95 里一半是编译器在干活。
for i in $(seq 1 10); do
  curl -s -o /dev/null -X POST -H "Content-Type: application/json" \
    -d "{\"idempotencyKey\":\"warm-$SIDE-$i-$RANDOM\",\"parkId\":1,\"pickupLng\":121.0815,\"pickupLat\":31.9252,\"dropoffLng\":121.0902,\"dropoffLat\":31.9301,\"orderPriority\":\"NORMAL\",\"remark\":\"warm\"}" \
    "$BASE_URL/api/admin/park/orders"
  sleep 0.4
done

cd "$REPO" || exit 1

run_tier() {
  local name="$1" ov="$2" pv="$3"
  DF_CONTAINERS="fsd-core-server fsd-mysql fsd-redis fsd-rabbitmq" \
    nohup bash scripts/k8s/cgroup-sampler.sh 10 "$OUT/$name-$SIDE.csv" >/dev/null 2>&1 &
  local pid=$!
  # MSYS_NO_PATHCONV：Git Bash 会把 `-w /scripts` 改写成本机路径，容器里就没有这个目录
  MSYS_NO_PATHCONV=1 docker run --rm \
    -v "$(cygpath -w "$REPO")/deploy/k8s/k6:/scripts" \
    -v "$OUTWIN:/out" -w /scripts \
    -e TARGET_BASE_URL="$BASE_URL" -e PARK_ID=1 -e MODE=full \
    -e ORDER_VUS="$ov" -e POLL_VUS="$pv" -e RAMP_S=60 -e HOLD_S="$HOLD_S" \
    grafana/k6:1.6.1 run /scripts/order-load.js --summary-export "/out/$name-$SIDE.json" \
    > "$OUT/k6-$name-$SIDE.log" 2>&1
  echo "  k6 $name ($ov/$pv VU) rc=$?"
  sleep 2
  kill "$pid" 2>/dev/null
}

echo "--- idle 60s"
timeout 70 bash scripts/k8s/cgroup-sampler.sh 10 "$OUT/idle-$SIDE.csv" >/dev/null 2>&1
echo "--- standard 30/60"
run_tier std 30 60
if [ "${DF_SKIP_2X:-0}" != "1" ]; then
  echo "--- 2x 60/120"
  run_tier 2x 60 120
fi
sleep 5
bash "$REPO/scripts/perf/db-state.sh" "end-$SIDE"
echo "产物在 $OUT：idle/std/2x-$SIDE.csv + std/2x-$SIDE.json"
echo "读数：python scripts/perf/load-ab-report.py $SIDE"
