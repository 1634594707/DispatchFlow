#!/usr/bin/env bash
# M8-R7 贴路闭环验收（Bash 版，与 m8-r7-accept.ps1 等价）
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE_URL="${FSD_API_BASE:-http://localhost:8080}"
USERNAME="${FSD_ADMIN_USER:-admin}"
PASSWORD="${FSD_ADMIN_PASSWORD:-admin123}"
PICKUP_CODE="${PICKUP_CODE:-ZJF-PICK-01}"
DROPOFF_CODE="${DROPOFF_CODE:-ZJF-DROP-01}"
MIN_VEHICLES="${MIN_VEHICLES:-4}"
MIN_VERTICES="${MIN_ROUTE_VERTICES:-4}"
EXPECT_LOCAL="${EXPECT_LOCAL_GRAPH_ONLY:-0}"
SKIP_ORDER="${SKIP_ORDER:-0}"
REPORT_PATH="${REPORT_PATH:-$ROOT/dist/m8-r7-report.json}"

PASS=0
FAIL=0
WARN=0

log() { echo "[$(date +%H:%M:%S)] $*"; }
pass() { PASS=$((PASS+1)); echo "[OK]   $1 — $2"; }
fail() { FAIL=$((FAIL+1)); echo "[FAIL] $1 — $2" >&2; }
warn() { WARN=$((WARN+1)); echo "[WARN] $1 — $2"; }
manual() { echo "[人工] $1 — $2"; }

api() {
  local method="$1" path="$2"
  shift 2
  local body="${1:-}"
  local curl_args=(-sS -X "$method" "$BASE_URL$path" -H "Content-Type: application/json")
  [[ -n "${ADMIN_TOKEN:-}" ]] && curl_args+=(-H "X-Admin-Token: $ADMIN_TOKEN")
  [[ -n "$body" ]] && curl_args+=(-d "$body")
  local resp
  resp="$(curl "${curl_args[@]}")"
  local ok
  ok="$(echo "$resp" | jq -r '.success')"
  if [[ "$ok" != "true" ]]; then
    echo "$resp" | jq -r '.message // .code // .' >&2
    return 1
  fi
  echo "$resp" | jq -c '.data'
}

log "M8-R7 贴路验收 · $BASE_URL"

login_body="$(jq -nc --arg u "$USERNAME" --arg p "$PASSWORD" '{username:$u,password:$p}')"
login_resp="$(curl -sS -X POST "$BASE_URL/api/admin/auth/login" -H "Content-Type: application/json" -d "$login_body")"
if [[ "$(echo "$login_resp" | jq -r '.success')" != "true" ]]; then
  fail "AUTH" "登录失败"
  exit 1
fi
ADMIN_TOKEN="$(echo "$login_resp" | jq -r '.data.token')"
pass "AUTH" "登录 $USERNAME"

health="$(api GET /api/admin/park/road-route/health)" || { fail "R7-4" "health 请求失败"; health="{}"; }
amap="$(echo "$health" | jq -r '.amapDriving')"
local="$(echo "$health" | jq -r '.localGraph')"
fb="$(echo "$health" | jq -r '.fallbackCount // 0')"
if [[ "$amap" != "true" && "$local" != "true" ]]; then
  fail "R7-4" "无可用路径源"
elif [[ "$EXPECT_LOCAL" == "1" ]]; then
  if [[ "$local" == "true" && "$amap" != "true" ]]; then pass "R7-4" "LOCAL_GRAPH only"; else fail "R7-4" "非本地路网模式"; fi
else
  pass "R7-4" "amap=$amap local=$local fallback=$fb"
  [[ "$fb" != "0" ]] && warn "R7-4b" "fallbackCount=$fb"
fi

stations="$(api GET /api/admin/park/stations)"
pick_lng="$(echo "$stations" | jq -r --arg c "$PICKUP_CODE" '.[] | select(.stationCode==$c) | .coordLng' | head -1)"
pick_lat="$(echo "$stations" | jq -r --arg c "$PICKUP_CODE" '.[] | select(.stationCode==$c) | .coordLat' | head -1)"
drop_lng="$(echo "$stations" | jq -r --arg c "$DROPOFF_CODE" '.[] | select(.stationCode==$c) | .coordLng' | head -1)"
drop_lat="$(echo "$stations" | jq -r --arg c "$DROPOFF_CODE" '.[] | select(.stationCode==$c) | .coordLat' | head -1)"
pick_id="$(echo "$stations" | jq -r --arg c "$PICKUP_CODE" '.[] | select(.stationCode==$c) | .stationId' | head -1)"
drop_id="$(echo "$stations" | jq -r --arg c "$DROPOFF_CODE" '.[] | select(.stationCode==$c) | .stationId' | head -1)"

validate_body="$(jq -nc --argjson ol "$pick_lng" --argjson oa "$pick_lat" --argjson dl "$drop_lng" --argjson da "$drop_lat" \
  '{originLng:$ol,originLat:$oa,destinationLng:$dl,destinationLat:$da}')"
vr="$(api POST /api/admin/park/road-route/validate "$validate_body")" || vr="{}"
inv="$(echo "$vr" | jq -r '.invalid // true')"
vc="$(echo "$vr" | jq -r '.vertexCount // 0')"
src="$(echo "$vr" | jq -r '.source // "?"')"
if [[ "$inv" == "true" || "$vc" -lt "$MIN_VERTICES" ]]; then
  fail "R7-8" "validate invalid=$inv vertices=$vc source=$src"
else
  pass "R7-8" "$PICKUP_CODE→$DROPOFF_CODE vertices=$vc source=$src"
fi

vehicles="$(api GET /api/admin/park/vehicles)"
online="$(echo "$vehicles" | jq '[.[] | select(.onlineStatus != "OFFLINE")] | length')"
if [[ "$online" -lt "$MIN_VEHICLES" ]]; then
  fail "R7-1a" "在线车 $online < $MIN_VEHICLES"
else
  pass "R7-1a" "在线 $online 台"
fi

bad_routes="$(echo "$vehicles" | jq -r --argjson m "$MIN_VERTICES" '
  [.[] | select(.dispatchStatus=="BUSY" or .currentTaskId != null) |
    . as $v |
    (($v.plannedRouteGeo // []) | length) as $p |
    (($v.geoTrajectory // []) | length) as $g |
    (if $p >= $m then $p elif $g >= $m then $g else 0 end) as $n |
    select($n < $m or $v.routeSource == "STRAIGHT_LINE" or $v.routeInvalid == true) |
    "\($v.vehicleCode): verts=\($n) source=\($v.routeSource // "?") invalid=\($v.routeInvalid)"
  ] | join(" | ")
')"
busy_n="$(echo "$vehicles" | jq '[.[] | select(.dispatchStatus=="BUSY" or .currentTaskId != null)] | length')"
if [[ "$busy_n" -eq 0 ]]; then
  warn "R7-1b" "无 BUSY 车；建议执行下单"
elif [[ -z "$bad_routes" ]]; then
  pass "R7-1b" "BUSY $busy_n 台路线合格"
else
  fail "R7-1b" "$bad_routes"
fi

if [[ "$SKIP_ORDER" != "1" ]]; then
  order_body="$(jq -nc --argjson p "$pick_id" --argjson d "$drop_id" '{pickupStationId:$p,dropoffStationId:$d,priority:"P1",remark:"M8-R7 bash"}')"
  created="$(api POST /api/admin/park/orders "$order_body")" || created="{}"
  oid="$(echo "$created" | jq -r '.orderId // empty')"
  if [[ -z "$oid" ]]; then
    fail "R7-2" "下单失败"
  else
    pass "R7-2a" "orderId=$oid"
    ok_assign=0
    for _ in $(seq 1 30); do
      sleep 3
      orders="$(api GET /api/admin/park/orders)"
      vid="$(echo "$orders" | jq -r --argjson id "$oid" '.[] | select(.orderId==$id) | .vehicleId // empty' | head -1)"
      [[ -z "$vid" ]] && continue
      vehicles="$(api GET /api/admin/park/vehicles)"
      bad="$(echo "$vehicles" | jq -r --argjson v "$vid" --argjson m "$MIN_VERTICES" '
        .[] | select(.vehicleId==$v) |
        (($.plannedRouteGeo // []) | length) as $p |
        select($p < $m or .routeSource=="STRAIGHT_LINE" or .routeInvalid==true) |
        .vehicleCode
      ')"
      if [[ -z "$bad" ]]; then ok_assign=1; pass "R7-2b" "vehicleId=$vid 贴路 OK"; break; fi
    done
    [[ "$ok_assign" -eq 0 ]] && fail "R7-2b" "90s 内未达贴路标准"
    manual "R7-2c" "/vehicle-tracking?mode=geo&orderId=$oid&vehicleId=$vid"
  fi
else
  manual "R7-2" "SKIP_ORDER=1"
fi

zjf_n="$(echo "$stations" | jq '[.[] | select(.area=="ZJF" or (.stationCode|startswith("ZJF-")))] | length')"
[[ "$zjf_n" -ge 2 ]] && pass "R7-6a" "ZJF 站点 $zjf_n" || fail "R7-6a" "ZJF 不足"
manual "R7-6b" "园区调度 UI 无 ZJF"
manual "R7-5" "录屏无取送货虚线"
manual "R7-7" "配置自检页 M10"
[[ "$EXPECT_LOCAL" != "1" ]] && manual "R7-3" "可选: EXPECT_LOCAL_GRAPH_ONLY=1"

log "通过=$PASS 失败=$FAIL 警告=$WARN"
[[ "$FAIL" -gt 0 ]] && exit 1
exit 0
