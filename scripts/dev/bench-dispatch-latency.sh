#!/bin/bash
# =====================================================================
# 派单选车时延分位数实测（§M2E / §M5 闸门用）
#
#   bash scripts/dev/bench-dispatch-latency.sh [每轮单量，默认 20] [轮数，默认 5]
#
# 原理：每建一单就走一次自动派单，选车决策耗时以微秒写进
#       t_dispatch_decision_snapshot.duration_micros（§7.3），
#       这里把它取出来算 P50/P95/P99，并按候选车数分组。
#       所以这个基准读的是真实 MySQL + 真实路网 + 真实图缓存，不是 H2 合成图。
#
# 为什么要多轮 + 置信区间（§13.9.1 的教训）：单轮 P95 曾经测出 164 / 320 ms 两个"同配置"值，
#   所以任何"扩范围前后"的对比都必须带 CI；单轮内还有明显的**顺序漂移**（同一轮里越往后越慢，
#   20:05:21 起的那轮前 6 单均值 ~85 ms、后 6 单 ~200 ms），因此这里同时输出前半/后半均值。
# 采样口径（§13.10 修的第二个坑）：每轮开始前记下 MAX(id)，只统计 id 大于它的快照 ——
#   之前按 remark 前缀筛，会把**历史轮次**的行混进本次样本，前后对比自然对不上。
#
# 前置：back 端已起（scripts/dev/run-backend-local.sh），本地库已重置到可派单
#       （scripts/dev/reset-demo-dispatchable.sh）。
#  runtime：每单 1.5 s（避开演示 key 的每分钟限流）+ 撞限流时等 20 s，默认 5 轮 × 20 单 ≈ 4 分钟。
# 说明：只写本地演示库；下单要带 X-Mobile-Api-Key，密钥取本地库 t_external_api_key
#       的第一条 ACTIVE 行 —— 那是仓库里 V25 造的演示夹具，不是生产凭据。
# =====================================================================
set -euo pipefail

N="${1:-20}"
ROUNDS="${2:-5}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BASE="${FSD_LOCAL_BASE:-http://127.0.0.1:8080}"
CONTAINER="${BENCH_CONTAINER:-fsd-mysql}"
PICKUP_ID="${BENCH_PICKUP_ID:-504}"
DROPOFF_ID="${BENCH_DROPOFF_ID:-506}"

if [ -n "${MYSQL_HOST:-}" ] && [ "${MYSQL_HOST}" != "127.0.0.1" ] && [ "${MYSQL_HOST}" != "localhost" ]; then
  echo "[ERROR] MYSQL_HOST=$MYSQL_HOST 非本机，拒绝跑基准（会往别的库写单）" >&2
  exit 1
fi
if [ "$N" -lt 2 ] || [ "$ROUNDS" -lt 2 ]; then
  echo "[ERROR] 单量与轮数都要 >= 2，否则算不出置信区间" >&2
  exit 1
fi

db() { docker exec -i "$CONTAINER" sh -c 'exec mysql --default-character-set=utf8mb4 -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -D fsd_core' <<<"$1" 2>/dev/null; }
# 聚合查询走这个：SQL 报错必须看得见。上一版把所有 mysql stderr 都吞掉，
# 采样窗口写错时不会报错、只会静默给出错误的样本量（§13.9.1 那次 164/320 ms 就有这个因素）。
db_strict() { docker exec -i "$CONTAINER" sh -c 'exec mysql --default-character-set=utf8mb4 -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -D fsd_core' <<<"$1"; }

API_KEY="$(db "SELECT api_key FROM t_external_api_key WHERE deleted=0 AND status='ACTIVE' ORDER BY id LIMIT 1;" | tr -d '\r' | head -1)"
if [ -z "$API_KEY" ]; then
  echo "[ERROR] 本地库没有 ACTIVE 的演示 api key，派单接口会拒绝（MOBILE_ORDER_KEY_REQUIRED）" >&2
  exit 1
fi

if ! curl -s -o /dev/null --max-time 5 "$BASE/internal/actuator/health"; then
  echo "[ERROR] 后端没起：先跑 bash scripts/dev/run-backend-local.sh" >&2
  exit 1
fi

# 双侧 95% 的 t 分位数小表，**自变量 = 轮数 n、取 df = n-1**，与 ScenarioBench.studentT 同口径。
# 2026-09-22 修：原来 n>=6 的整段写成了 t(df=n)（n=8 给 2.306 而 t(df=7)=2.365），区间系统性偏窄 1%–5%。
tval() {
  case "$1" in
    2) echo 12.706 ;; 3) echo 4.303 ;; 4) echo 3.183 ;; 5) echo 2.776 ;; 6) echo 2.571 ;;
    7) echo 2.447 ;; 8) echo 2.365 ;; 9) echo 2.306 ;; 10) echo 2.262 ;; 11) echo 2.228 ;;
    12) echo 2.201 ;; 13) echo 2.179 ;; 14) echo 2.160 ;; 15) echo 2.145 ;; 16) echo 2.131 ;;
    17) echo 2.120 ;; 18) echo 2.110 ;; 19) echo 2.101 ;; 20) echo 2.093 ;;
    25) echo 2.064 ;; 30) echo 2.045 ;;
    *) if [ "$1" -ge 30 ]; then echo 1.96
       elif [ "$1" -ge 25 ]; then echo 2.064      # 26..29 退回 25 档（偏宽不偏窄）
       elif [ "$1" -gt 20 ]; then echo 2.093      # 21..24 退回 20 档
       else echo 2.201; fi ;;
  esac
}

STAMP=$(date +%s)
PER_ROUND="$(mktemp)"
trap 'rm -f "$PER_ROUND"' EXIT

echo "=== 派单时延基准：$ROUNDS 轮 × $N 单，取货 $PICKUP_ID -> 送货 $DROPOFF_ID ==="
for r in $(seq 1 "$ROUNDS"); do
  # 每轮开始前把车队恢复到可派单态：否则第 2 轮起车辆全在忙，样本退化成"6 ms 的 NO_VEHICLE 快速失败"，
  # 与"200 ms 的完整评估"混在一个分位数里 —— §13.9 那个 164/320 ms 说不清的基线就有这个因素。
  if [ "${BENCH_NO_RESET:-}" != "1" ]; then
    bash "$REPO_ROOT/scripts/dev/reset-demo-dispatchable.sh" >/dev/null
  fi
  FROM_ID="$(db "SELECT IFNULL(MAX(id),0) FROM t_dispatch_decision_snapshot;" | tr -d '\r' | head -1)"
  ok=0; fail=0; rate_limited=0
  for i in $(seq 1 "$N"); do
    attempt=0
    while :; do
      RESP=$(curl -s --max-time 20 -X POST "$BASE/api/admin/park/orders" \
          -H "Content-Type: application/json" -H "X-Mobile-Api-Key: $API_KEY" \
          -d "{\"parkId\":1,\"pickupStationId\":$PICKUP_ID,\"dropoffStationId\":$DROPOFF_ID,\"priority\":\"P2\",\"remark\":\"bench-$STAMP-$r-$i\",\"idempotencyKey\":\"bench-$STAMP-$r-$i\"}")
      if printf '%s' "$RESP" | grep -q 'MOBILE_ORDER_RATE_LIMIT'; then
        # 演示库的 key 带每分钟限流（t_external_api_key.rate_limit_per_minute），
        # 不限速就会把样本量压掉 1/4，基准读数随之失真 -> 撞限就等一个窗口再试
        rate_limited=$((rate_limited+1))
        [ "$attempt" -ge 5 ] && break
        attempt=$((attempt+1)); sleep 20; continue
      fi
      break
    done
    if printf '%s' "$RESP" | grep -q '"success":true'; then ok=$((ok+1)); else fail=$((fail+1)); fi
    sleep "${BENCH_INTERVAL_SEC:-1.5}"
  done

  # 只取本轮写入的行：id 窗口 = 本轮开始前的 MAX(id)。
  # 分位数**只对成功决策算**：失败路径（NO_VEHICLE / LOW_SOC）在漏斗前段就返回，6 ms 量级，
  # 混进来会把 P50 拉到没有意义的位置 —— 闸门问的是"选车这一趟多慢"。失败样本单独计数并输出。
  # 一次取原始行、分位数在 awk 里算：不用 GROUP_CONCAT（有截断）、不用 CONCAT_WS（转义不可靠）。
  SAMPLE="$(db_strict "SELECT duration_micros, IFNULL(candidate_total,0), IFNULL(fail_reason,'-')
                      FROM t_dispatch_decision_snapshot
                      WHERE id > $FROM_ID AND duration_micros IS NOT NULL ORDER BY id;" | tr -d '\r')"
  LINE="$(printf '%s\n' "$SAMPLE" | awk '
    { if ($3 == "-") { d[++n] = $1 / 1000; if ($2 + 0 > maxcand) maxcand = $2 + 0 } else failed++ }
    END {
      if (n == 0) { print "0"; exit }
      for (i = 1; i <= n; i++) for (j = i + 1; j <= n; j++)   # 插入排序：n<=40，可移植（不依赖 gawk 的 asort）
        if (d[j] < d[i]) { t = d[i]; d[i] = d[j]; d[j] = t }
      tot = 0; for (i = 1; i <= n; i++) tot += d[i]
      h = int((n + 1) / 2)
      printf "%d\t%d\t%.2f\t%.2f\t%.2f\t%.2f\t%d\t%.2f\t%.2f\n",
        n, failed + 0, d[rank(n, 0.50)], d[rank(n, 0.95)], d[n], tot / n, maxcand, avg(d, 1, h), avg(d, h + 1, n)
    }
    function rank(m, q) { r = int(m * q); if (r < m * q) r++; if (r < 1) r = 1; if (r > m) r = m; return r }
    function avg(a, from, to,   i, t, c) { t = 0; c = 0; for (i = from; i <= to; i++) { t += a[i]; c++ } return c ? t / c : 0 }')"
  if [ -z "$LINE" ] || [ "$LINE" = "0" ]; then
    echo "[ERROR] 第 $r 轮没有一次成功决策（建单成功 $ok / 失败 $fail）—— 车队/围栏状态不对，先跑 reset-demo-dispatchable.sh" >&2
    exit 1
  fi
  SAMPLES="$(printf '%s' "$LINE" | cut -f1)"
  if [ "$SAMPLES" -lt 5 ]; then
    echo "  [WARN] 轮 $r 只有 $SAMPLES 个成功样本，P95 在这个量级上没有意义" >&2
  fi
  printf '%s\t%s\n' "$r" "$LINE" >> "$PER_ROUND"
  LAST_FROM_ID="$FROM_ID"
  echo "  轮 $r：成功样本 $SAMPLES / 落空 $(printf '%s' "$LINE" | cut -f2)  P50 $(printf '%s' "$LINE" | cut -f3) ms  P95 $(printf '%s' "$LINE" | cut -f4) ms  候选峰值 $(printf '%s' "$LINE" | cut -f7)  建单 $ok 单（接口失败 $fail，撞限流 $rate_limited）"
done

echo
echo "  —— 跨轮汇总（$ROUNDS 轮；95% 置信区间**按轮**取，小样本用 t 分位数，口径同 ScenarioBench）"
echo "     注：CI 覆盖的是「轮间波动」，也就是 §13.9.1 里 164 vs 320 ms 那种差异的来源；"
echo "         只有当前后两组的 CI 不重叠，才允许说扩范围让时延变了。"
printf "  %-16s %10s %-24s %8s\n" "指标" "均值" "95% CI" "标准差"
for col in 4 5 6 7; do
  NAME="$(case $col in 4) echo p50_ms ;; 5) echo p95_ms ;; 6) echo max_ms ;; 7) echo avg_ms ;; esac)"
  awk -v c="$col" -v name="$NAME" -v tv="$(tval "$ROUNDS")" '
    { v[++n] = $c }
    END {
      if (n < 2) { printf "  %-16s %10s %-24s %8s\n", name, "n/a", "(轮数不足)", "n/a"; exit }
      m = 0; for (i = 1; i <= n; i++) m += v[i]; m /= n
      sq = 0; for (i = 1; i <= n; i++) sq += (v[i]-m)*(v[i]-m)
      sd = sqrt(sq/(n-1))
      half = tv * sd / sqrt(n)
      printf "  %-16s %10.2f %11.2f .. %-10.2f %8.2f\n", name, m, m-half, m+half, sd
    }' "$PER_ROUND"
done

awk '
  { first += $9; second += $10; n++ }
  END {
    if (n == 0) exit
    printf "  单轮内顺序漂移：前半均值 %.2f ms -> 后半均值 %.2f ms（比值 %.2f，>1.3 说明同一轮里就在变慢，前后对比必须交叉安排轮次顺序）\n",
      first/n, second/n, second / (first == 0 ? 1 : first)
  }' "$PER_ROUND"

echo
echo "  —— 最后一轮的失败原因分布（本轮窗口内）"
db_strict "SELECT IFNULL(fail_reason,'(success)') r, COUNT(*) FROM t_dispatch_decision_snapshot WHERE id > $LAST_FROM_ID GROUP BY fail_reason;" | sed 's/^/    /'
