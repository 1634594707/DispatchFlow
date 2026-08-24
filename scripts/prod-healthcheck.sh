#!/usr/bin/env bash
# DispatchFlow 生产健康检查（路线图 Phase 5-120）
# 用法: BASE_URL=https://app.aplicity.online ADMIN_TOKEN=xxx ./scripts/prod-healthcheck.sh
# 覆盖: 后端健康 / 前端首页 / 登录可达 / SSE ticket / 工作台快照 / 车辆监控端点

set -uo pipefail

BASE_URL="${BASE_URL:-https://app.aplicity.online}"
API_BASE="${API_BASE:-$BASE_URL/api}"
ADMIN_TOKEN="${ADMIN_TOKEN:-}"

pass=0
fail=0
skip=0

pass_check() {
  echo "[PASS] $1"
  pass=$((pass + 1))
}

fail_check() {
  echo "[FAIL] $1"
  fail=$((fail + 1))
}

skip_check() {
  echo "[SKIP] $1"
  skip=$((skip + 1))
}

echo "== DispatchFlow 生产健康检查 $(date '+%F %T') =="
echo "   BASE_URL=$BASE_URL"

# 1. 后端健康检查
body=$(curl -fsS --max-time 10 "$BASE_URL/internal/actuator/health" 2>/dev/null || true)
if printf '%s' "$body" | grep -q 'UP'; then
  pass_check "后端 /internal/actuator/health UP"
else
  fail_check "后端 /internal/actuator/health 未返回 UP"
fi

# 2. 前端首页
code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 "$BASE_URL/" 2>/dev/null || true)
if [ "$code" = "200" ]; then
  pass_check "前端首页 HTTP 200"
else
  fail_check "前端首页 HTTP $code"
fi

# 3. 登录接口可达（无效凭据应得到 4xx，而非连接失败）
code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 \
  -X POST -H 'Content-Type: application/json' \
  -d '{"username":"__probe__","password":"__probe__"}' \
  "$API_BASE/admin/auth/login" 2>/dev/null || true)
case "$code" in
  400|401|403) pass_check "登录接口可达 HTTP $code" ;;
  *) fail_check "登录接口异常 HTTP $code" ;;
esac

# 4/5. 需要 ADMIN_TOKEN 的深度检查
if [ -n "$ADMIN_TOKEN" ]; then
  body=$(curl -fsS --max-time 10 -X POST -H "X-Admin-Token: $ADMIN_TOKEN" \
    "$API_BASE/admin/sse-ticket" 2>/dev/null || true)
  if printf '%s' "$body" | grep -q 'ticket'; then
    pass_check "SSE ticket 签发成功"
  else
    fail_check "SSE ticket 签发失败"
  fi

  code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 \
    -H "X-Admin-Token: $ADMIN_TOKEN" \
    "$API_BASE/admin/dispatch/workbench?parkId=1" 2>/dev/null || true)
  if [ "$code" = "200" ]; then
    pass_check "工作台快照 HTTP 200"
  else
    fail_check "工作台快照 HTTP $code"
  fi
else
  skip_check "SSE ticket / 工作台快照（未提供 ADMIN_TOKEN）"
fi

# 6. 车辆监控遥测流端点存在性（无 ticket 应为 4xx 而非 404/502）
code=$(curl -sS -o /dev/null -w '%{http_code}' --max-time 10 \
  "$API_BASE/admin/fleet/telemetry/stream" 2>/dev/null || true)
case "$code" in
  000|404|502|503) fail_check "车辆监控流端点异常 HTTP $code" ;;
  *) pass_check "车辆监控流端点存在 HTTP $code" ;;
esac

echo "== 结果: PASS=$pass FAIL=$fail SKIP=$skip =="
[ "$fail" -eq 0 ]