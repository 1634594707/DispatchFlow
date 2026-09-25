#!/bin/bash
# =====================================================================
# M5 演示彩排 + T1-b 运行时闸门（路线图 §9 步骤 0/1/3/5）
#
#   bash scripts/dev/m5-rehearsal.sh [tick-ms]
#
# 前提：后端已在跑（用 `bash scripts/dev/run-demo-local.sh` 起，tick 由那里固化）。
# 本脚本只做「可观察断言 + 计时」，不改任何配置；§9 步骤 2（追踪页目视）与步骤 4
# （翻车预案）由前端侧另跑，这里不代替。
#
# 步骤 3 同时是 T1-b 的闸门：把一台 IDLE 车压到 29%。
#   阈值=30 ⇒ 必须回补；若回补线还是旧值 20，29% 的车不会动。
#   所以"29% 回车"这一条本身就是判据生效的证明，不依赖日志措辞。
# =====================================================================
set -uo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"
OUT="tmp/m5"; mkdir -p "$OUT"
BASE="${M5_BASE_URL:-http://127.0.0.1:8080/api}"
KEY="${M5_MOBILE_KEY:-ZJF-MOBILE-DEMO-2026}"
LOG="${M5_BACKEND_LOG:-tmp/m0/backend-demo.log}"
T_REPORT="$OUT/rehearsal-$(date +%Y%m%d-%H%M%S).txt"

mq() { printf '%s\n' "$1" | docker exec -i fsd-mysql mysql --default-character-set=utf8mb4 -uroot -proot -N fsd_core 2>/dev/null | tr -d '\r'; }
say() { echo "$@" | tee -a "$T_REPORT"; }

say "# M5 彩排记录  $(date '+%F %T')"
say "后端日志：$LOG"

# ---------- 步骤 0：起服务前把 20 台车电量拉高 ----------
mq "UPDATE t_vehicle SET battery_level=95 WHERE vehicle_code LIKE 'ZJF-AV-%' AND deleted=0;"
MINB=$(mq "SELECT MIN(battery_level) FROM t_vehicle WHERE vehicle_code LIKE 'ZJF-AV-%' AND deleted=0;")
say "[步骤0] 车队最低电量 MIN(battery_level)=$MINB  →  闸门(≥90)：$([ "$MINB" -ge 90 ] && echo PASS || echo FAIL)"
say "        注意 ensurePilotFleet() 只在**首次创建**时随机 80-100，重启不会补满 ⇒ 这步必须显式做"

# ---------- 步骤 1：服务区中心点下单，30 s 内接单且无拒单 ----------
T0=$(date +%s%3N)
# remark 必须 ASCII：Git Bash 会把中文按 GBK 送出，后端 Jackson 直接
# `HttpMessageNotReadableException: Invalid UTF-8 start byte`，看着像下单接口 500 其实是脚本自己造的。
RESP="$(curl -s -X POST "$BASE/admin/park/orders" -H 'content-type: application/json' -H "X-Mobile-Api-Key: $KEY" \
  -d "{\"idempotencyKey\":\"m5-$(date +%s)\",\"parkId\":1,\"pickupLng\":121.095785,\"pickupLat\":31.936155,\"dropoffLng\":121.101763,\"dropoffLat\":31.918297,\"priority\":\"P1\",\"remark\":\"m5-rehearsal-step1\"}")"
OID=$(printf '%s' "$RESP" | sed -nE 's/.*"orderId":([0-9]+).*/\1/p')
if [ -z "$OID" ]; then
  say "[步骤1] FAIL 下单被拒：$RESP"
else
  ASSIGNED=""
  for i in $(seq 1 30); do
    ST=$(mq "SELECT status FROM t_order WHERE id=$OID;")
    VEHC=$(mq "SELECT v.vehicle_code FROM t_order o JOIN t_dispatch_task t ON t.id=o.dispatch_task_id JOIN t_vehicle v ON v.id=t.vehicle_id WHERE o.id=$OID;")
    if [ -n "$VEHC" ] && [ "$VEHC" != "NULL" ]; then ASSIGNED="$VEHC"; break; fi
    sleep 1
  done
  T1=$(date +%s%3N)
  say "[步骤1] orderId=$OID status=$ST 派给=${ASSIGNED:-未派} 用时=$((T1-T0))ms → 闸门(30s 内接单、无拒单)：$([ -n "$ASSIGNED" ] && echo PASS || echo FAIL)"
fi

# ---------- 步骤 3：把一台 IDLE 车压到 29%，看它是否回补 ----------
VICTIM=$(mq "SELECT vehicle_code FROM t_vehicle WHERE vehicle_code LIKE 'ZJF-AV-%' AND deleted=0 AND dispatch_status='IDLE' ORDER BY id LIMIT 1;")
mq "UPDATE t_vehicle SET battery_level=29, current_task_id=NULL, current_order_id=NULL, dispatch_status='IDLE' WHERE vehicle_code='$VICTIM';"
T2=$(date +%s%3N)
say "[步骤3] 受害者=$VICTIM 起始电量=29%（阈值 30 以下 ⇒ 新口径必须回补；旧值 20 则不会动）"
# 判据用**可观测量**而不是日志措辞：回补发生的定义是"这台车开始往上涨电"。
# 运行时阶段（runtime_stage）在 Redis 侧的 fleet runtime 里，库里没有对应表，不要拿库字段猜它。
S0=$(mq "SELECT battery_level FROM t_vehicle WHERE vehicle_code='$VICTIM';")
STATUS=""
RISEN=0
for i in $(seq 1 100); do
  B=$(mq "SELECT battery_level FROM t_vehicle WHERE vehicle_code='$VICTIM';")
  STATUS=$(mq "SELECT dispatch_status FROM t_vehicle WHERE vehicle_code='$VICTIM';")
  if [ "${B:-0}" -gt "${S0:-0}" ]; then RISEN=1; break; fi
  case "$STATUS" in CHARG*|BUSY) break ;; esac
  sleep 1
done
T3=$(date +%s%3N)
say "        等待 $((T3-T2))ms：dispatch_status=$STATUS battery $S0%→${B:-?}%"
if [ "$RISEN" = "1" ]; then
  say "        → 闸门(回补触发)：PASS（电量开始上升＝已进入补能；29% 在旧阈值 20 下不会触发）"
else
  say "        → 闸门(回补触发)：$([ -n "$STATUS" ] && case "$STATUS" in CHARG*|BUSY) echo PASS;; *) echo FAIL;; esac || echo FAIL)"
fi
CS=$(mq "SELECT COUNT(*) FROM t_charging_session WHERE vehicle_id=(SELECT id FROM t_vehicle WHERE vehicle_code='$VICTIM');")
BS=$(mq "SELECT COUNT(*) FROM t_battery_swap_session WHERE vehicle_id=(SELECT id FROM t_vehicle WHERE vehicle_code='$VICTIM');")
say "        会话记录：t_charging_session=$CS  t_battery_swap_session=$BS（>0 才说明真走了补能链路）"

# ---------- 步骤 5：拒单原因为空的反证 ----------
REJ=$(grep -icE "ORDER_ENDPOINT_(OUT_OF_|SNAP_FAILED)|拒单|reject" "$LOG" 2>/dev/null || echo 0)
ERR=$(grep -cE "\bERROR\b" "$LOG" 2>/dev/null || echo 0)
say "[步骤5] 后端日志 ERROR 行数=$ERR  拒单相关行数=$REJ"
say "        （日志里没有回补阈值的 info 级判定行；本彩排以**可观察状态**为准，不以日志措辞为准）"
say "产物：$T_REPORT"
