#!/usr/bin/env bash
# 本机 k8s 单节点压测的一键编排：建 Secret → 起依赖 → 基线 → 起应用 → 灌 seed → 开仿真 → k6。
#
#   bash scripts/k8s/run-perf.sh --smoke        # 小流量端到端自检（约 1 分钟压测 + 起环境的时间）
#   bash scripts/k8s/run-perf.sh                # 定档压测
#   bash scripts/k8s/run-perf.sh --no-k6        # 只把环境立起来（前端自己点、k6 手动跑）
#   bash scripts/k8s/run-perf.sh --skip-build   # 沿用已有镜像
#   bash scripts/k8s/run-perf.sh --fresh        # 先拆 namespace 再跑（清 PVC = 冷启动）
#   bash scripts/k8s/run-perf.sh --down         # 只拆
#
# 为什么顺序不能动（跳步会得到一个"Pod 全绿但地图是空的"环境）：
#   空库 → V01–V20 裸基线 → 后端 Flyway V21→最新 → 11 份 seed → 才允许仿真开。
#   仿真先于 seed 起来，35 台车会落在 yml 兜底位而不是 STANDBY 泊位；V64 的坐标列还没建时
#   灌 seed 则是 1054 Unknown column，看起来像 seed 写坏了。
#
# 口令：这里**不从 .env 抄生产口令**。压测集群用一套一次性随机凭据，把生产口令复制进另一个
#   上下文没有收益，只有"它出现在不该出现的地方"的风险。口令落在 tmp/perf/（已 gitignore），
#   第二次跑复用同一把 —— MySQL 卷里存的还是第一把，换了就连不上。
set -euo pipefail

NS=dispatchflow-perf
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && (pwd -W 2>/dev/null || pwd))"
KDIR="$REPO_ROOT/deploy/k8s"
TMP="$REPO_ROOT/tmp/perf"
BUILD_TAG="${BUILD_TAG:-perf}"
SMOKE=0; DOWN=0; FRESH=0; SKIP_BUILD=0; NO_K6=0

for a in "$@"; do
  case "$a" in
    --smoke) SMOKE=1 ;;
    --down) DOWN=1 ;;
    --fresh) FRESH=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    --no-k6) NO_K6=1 ;;
    -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "[ERROR] 未知参数 $a（--smoke/--down/--fresh/--skip-build/--no-k6）" >&2; exit 2 ;;
  esac
done

log() { printf '\n== %s\n' "$*"; }
die() { printf '  [FAIL] %s\n' "$*" >&2; exit 1; }
kne() { kubectl -n "$NS" "$@"; }
MYSQL_PWD_CACHED=''

# ───────────────────────────── 前置检查 ─────────────────────────────
preflight() {
  command -v kubectl >/dev/null || die "没有 kubectl"
  if ! kubectl get nodes >/dev/null 2>&1; then
    cat >&2 <<'TXT'
[ERROR] kubectl 连不上集群。Docker Desktop 的 Kubernetes 只有 GUI 开关，CLI 里没有 enable 子命令
        （`docker desktop kubernetes` 只有 images/status/reset-cluster），也不要去猜 settings 里的键名：
        Docker Desktop → Settings → Kubernetes → 「Enable Kubernetes」→ Apply & Restart，
        等左下角变绿（首次会拉控制面镜像，几分钟）。
        自查：docker desktop kubernetes status      # State 要成 running
TXT
    exit 2
  fi
  ctx=$(kubectl config current-context 2>/dev/null || echo '(未知)')
  case "$ctx" in
    docker-desktop*|kind-*) ;;
    *) die "当前 kubectl 上下文是「$ctx」，不是本机集群 —— 这套清单只允许打在 docker-desktop/kind 上" ;;
  esac
  echo "  context=$ctx  nodes=$(kubectl get nodes --no-headers | wc -l | tr -d ' ')"
  kubectl get storageclass -o name 2>/dev/null | grep -q . \
    || die "集群没有 StorageClass，mysql 的 PVC 会一直 Pending"
  command -v docker >/dev/null || die "没有 docker"
}

# ───────────────────────────── 镜像 ─────────────────────────────
# Docker Desktop 的 k8s 现在是 kind 模式（见 `docker desktop kubernetes status` 的 Mode 字段）：
# 本机 build 出来的镜像**不一定**在集群可见，Pod 只会卡在 ImagePullBackOff，而 imagePullPolicy:
# IfNotPresent 不会告诉你"是没这个镜像"还是"镜像坏了"。所以先查一次，缺了就 docker save | ctr import。
kind_node() {
  if [ -n "${KIND_NODE:-}" ]; then printf '%s' "$KIND_NODE"; return; fi
  KIND_NODE="$(docker ps --format '{{.Names}}\t{{.Image}}' \
    | awk 'tolower($2) ~ /kindest|node/ || $1 ~ /control-plane$/ {print $1; exit}')"
  printf '%s' "${KIND_NODE:-}"
}

image_in_cluster() {
  local node img="$1"
  local node
  node=$(kind_node)
  # 找不到 kind 节点 ⇒ 当作"与 docker 共享镜像存储"，交给 kubelet 自己看
  if [ -z "$node" ]; then return 0; fi
  docker exec "$node" ctr -n k8s.io images ls 2>/dev/null | grep -q "docker.io/library/$img "
}

load_image() {
  local img="$1" node
  if image_in_cluster "$img"; then
    echo "  [ok] $img 集群可见"
    return 0
  fi
  node=$(kind_node)
  if [ -z "$node" ]; then
    echo "  [i] 没找到 kind 节点容器，按共享镜像存储处理：$img 无需导入"
    return 0
  fi
  echo "  导入 $img → $node"
  docker save "$img" | docker exec -i "$node" ctr --namespace=k8s.io images import - >/dev/null
  image_in_cluster "$img" || die "$img 导入后集群仍看不到它"
}

build_images() {
  if [ "$SKIP_BUILD" = 1 ]; then
    echo "  [skip] --skip-build"
  else
    log "  构建三个镜像（backend 走 maven、frontend 走 npm ci+vite，各几分钟）"
    docker build -q -t "dispatchflow-backend:$BUILD_TAG" -f "$REPO_ROOT/back/Dockerfile" "$REPO_ROOT/back"
    docker build -q -t "dispatchflow-frontend:$BUILD_TAG" -f "$REPO_ROOT/front/Dockerfile" "$REPO_ROOT/front"
    docker build -q -t "dispatchflow-sql:$BUILD_TAG" -f "$KDIR/Dockerfile.sql" "$REPO_ROOT/back/sql"
  fi
  load_image "dispatchflow-backend:$BUILD_TAG"
  load_image "dispatchflow-frontend:$BUILD_TAG"
  load_image "dispatchflow-sql:$BUILD_TAG"
  # k6 是公共镜像：本机没有就先 pull，再按同一条路径导入集群。
  # 不这么做的话，kind 模式下它会去 docker.io 拉 —— 集群里没有凭据也没关系，
  # 但"卡在 ImagePullBackOff 而日志只写 pull quota/exceeded"是这台机器上最难归因的一类红。
  docker image inspect grafana/k6:1.6.1 >/dev/null 2>&1 || docker pull -q grafana/k6:1.6.1
  load_image "grafana/k6:1.6.1"
}

# ───────────────────────────── Secret / ConfigMap ─────────────────────────────
one_time_secret() {
  local which="$1" f v
  mkdir -p "$TMP"
  f="$TMP/perf-secret-$which"
  if [ -s "$f" ]; then cat "$f"; return; fi
  case "$which" in
    mysql) v="mysql-${RANDOM}${RANDOM}" ;;
    rabbit) v="amqp-${RANDOM}${RANDOM}" ;;
    hmac) v="hmac-${RANDOM}${RANDOM}${RANDOM}" ;;
    *) die "unknown secret slot $which" ;;
  esac
  printf '%s' "$v" > "$f"
  printf '%s' "$v"
}

apply_infra_objects() {
  kubectl apply -f "$KDIR/00-namespace.yaml"
  kne create secret generic dispatchflow-perf \
    --from-literal=MYSQL_ROOT_PASSWORD="$(one_time_secret mysql)" \
    --from-literal=RABBITMQ_USERNAME=perf \
    --from-literal=RABBITMQ_PASSWORD="$(one_time_secret rabbit)" \
    --from-literal=FSD_ADMIN_TOKEN_HMAC_KEY="$(one_time_secret hmac)" \
    --dry-run=client -o yaml | kne apply -f -
  kne create configmap db-init-scripts \
    --from-file=run-baseline.sh="$KDIR/run-baseline.sh" \
    --from-file=load-seeds.sh="$KDIR/load-seeds.sh" \
    --dry-run=client -o yaml | kne apply -f -
  kne create configmap seed-order \
    --from-literal=order="$(seed_order)" \
    --dry-run=client -o yaml | kne apply -f -
}

# seed 顺序的唯一事实来源是 GEO_SEEDS 数组（scripts/dev/verify-geo-init-paths.sh）。
# 这里抽它而不是再抄一遍文件名 —— 两处清单迟早分叉，而分叉的表现是"seed 报 1054/依赖缺失"。
seed_order() {
  local line
  line=$(grep -m1 '^GEO_SEEDS=(' "$REPO_ROOT/scripts/dev/verify-geo-init-paths.sh") \
    || die "找不到 GEO_SEEDS，不敢自己猜 seed 顺序"
  printf '%s' "${line#*=(}" | tr -d '"()' | tr -s ' ' ' ' | sed 's/^ //; s/ $//'
}

# ───────────────────────────── 与压测库说话 ─────────────────────────────
db_scalar() {  # 一条查询，返回一行一列
  local sql="$1"
  if [ -z "$MYSQL_PWD_CACHED" ]; then
    MYSQL_PWD_CACHED=$(kne get secret dispatchflow-perf -o jsonpath='{.data.MYSQL_ROOT_PASSWORD}' | base64 -d)
  fi
  # MYSQL_PWD 而不是 -p…：后者会把"Using a password"打到 stderr，而这里的输出是要进断言的。
  # stderr **不能丢**：本函数曾被写成 `2>/dev/null`，于是列名打错时表现为"返回空"，
  # 断言拿到空串后报的是"数量 0"，把仪表故障读成了系统故障（本机预压时踩过，已改）。
  kne exec deploy/mysql -- env MYSQL_PWD="$MYSQL_PWD_CACHED" \
    mysql -N -B --default-character-set=utf8mb4 -uroot -D fsd_core -e "$sql"
}

# OD 坐标从**本次这套 seed 灌出来的库**里现取，不落版本库：
# STANDBY 泊位 + ACTIVE 站点的 coord_lng/coord_lat 本身就是路网节点或吸附过的点
# （热库里还会多出一批 `GEO-<路网节点码>` 站点 —— 那是下单时由 OrderEndpointResolver 落库的
#  落点，同样可下单，所以 OD 池比冷库大；冷启动时池子只有泊位 + 设施，实测 48 个）。
# "在围栏内、可吸附"由数据保证，而不是我手挑一串看着对的数字。
# 用 UNION（不是 UNION ALL）：基地那 6 根桩与 6 台柜在 seed 里就是**同一个坐标**，
# 不去重的话取送会被配成同址两点，后端按"路网上连不通 0m/0m"拒 400 —— 干跑实测踩过，
# 那是夹具脏，不是被测系统脏。
gen_od_pairs() {
  mkdir -p "$TMP"
  local sql
  sql="SELECT JSON_ARRAYAGG(JSON_OBJECT('lng', lng, 'lat', lat)) FROM (
    SELECT coord_lng AS lng, coord_lat AS lat FROM t_parking_slot
      WHERE deleted=0 AND slot_type='STANDBY' AND coord_lng IS NOT NULL
    UNION
    SELECT coord_lng, coord_lat FROM t_station
      WHERE deleted=0 AND status='ACTIVE' AND coord_lng IS NOT NULL
        AND station_type IN ('SWAP_CABINET','CHARGING_STATION','MOTHERSHIP','GEO_POINT')) p"
  db_scalar "$sql" | tr -d '\n' > "$TMP/od-pairs.json"
  local n
  n=$(grep -o '"lng"' "$TMP/od-pairs.json" | wc -l | tr -d ' ')
  [ "${n:-0}" -ge 4 ] || die "OD 坐标只取到 ${n:-0} 个（要 ≥4）—— 取送货点不成立，别压"
  echo "  OD 坐标 $n 个 → tmp/perf/od-pairs.json"
}

k6_objects() {
  local park mode ovus pvus ramp hold
  park=$(db_scalar "SELECT id FROM t_park WHERE park_code='DEFAULT' AND deleted=0")
  [ -n "$park" ] || die "库里没有 park_code='DEFAULT' 的园区"
  mode=full; ovus=30; pvus=60; ramp=90; hold=180
  if [ "$SMOKE" = 1 ]; then ovus=2; pvus=4; ramp=10; hold=25; fi
  kne create configmap k6-assets \
    --from-file=order-load.js="$KDIR/k6/order-load.js" \
    --from-file=od-pairs.json="$TMP/od-pairs.json" \
    --dry-run=client -o yaml | kne apply -f -
  kne create configmap k6-run-config \
    --from-literal=park_id="$park" \
    --from-literal=mode="$mode" \
    --from-literal=order_vus="$ovus" \
    --from-literal=poll_vus="$pvus" \
    --from-literal=ramp_s="$ramp" \
    --from-literal=hold_s="$hold" \
    --from-literal=tracking_polls=3 \
    --dry-run=client -o yaml | kne apply -f -
  echo "  park_id=$park 档位=${mode}/order_vus=$ovus/poll_vus=$pvus/ramp=${ramp}s/hold=${hold}s"
}

# ───────────────────────────── 等待原语 ─────────────────────────────
wait_deploy() {
  local d="$1" t="${2:-420s}"
  kne rollout status "deployment/$d" --timeout="$t" >/dev/null || die "deployment/$d 没在 $t 内 Ready"
  echo "  [ok] deployment/$d Ready"
}

wait_job() {  # 失败要立刻看出来，不能等满超时才报 —— 所以自己轮两个条件
  local name="$1" t="${2:-900s}" waited=0
  while [ "$waited" -lt "$t" ]; do
    if [ "$(kne get job "$name" -o jsonpath='{.status.succeeded}' 2>/dev/null)" = "1" ]; then return 0; fi
    if [ "$(kne get job "$name" -o jsonpath='{.status.conditions[?(@.type=="Failed")].status}' 2>/dev/null)" = "True" ]; then
      return 1
    fi
    sleep 5; waited=$((waited + 5))
  done
  return 2
}

run_job() {
  local name="$1" t="${2:-900s}" rc
  kne delete job "$name" --ignore-not-found --wait=true >/dev/null   # Job 的 template 不可变，重跑先删（--wait：否则 apply 会撞"对象正在删除"）
  kubectl apply -f "$3"
  rc=0; wait_job "$name" "$t" || rc=$?
  kne logs "job/$name" 2>/dev/null | sed 's/^/  | /'
  case "$rc" in
    0) echo "  [ok] job/$name" ;;
    1) die "job/$name 失败（上面是它的日志）" ;;
    *) die "job/$name 在 ${t}s 内没结束" ;;
  esac
}

teardown() {
  log "删除 namespace $NS（PVC 一起没，等于冷启动）"
  kubectl delete ns "$NS" --ignore-not-found --wait=true
  echo "  [ok] 已拆除"
}

# ───────────────────────────── 主流程 ─────────────────────────────
main() {
  preflight
  if [ "$DOWN" = 1 ]; then teardown; return 0; fi
  if [ "$FRESH" = 1 ]; then teardown; fi

  log "1/9 镜像 + Secret + ConfigMap"
  build_images
  apply_infra_objects

  log "2/9 依赖三件套（mysql/redis/rabbitmq）"
  kubectl apply -f "$KDIR/10-infra.yaml"
  wait_deploy mysql 600s
  wait_deploy redis 180s
  wait_deploy rabbitmq 300s

  log "3/9 新库基线 V01–V20"
  run_job db-baseline 900s "$KDIR/15-db-baseline.yaml"

  log "4/9 后端（仿真先关着）+ 前端"
  kubectl apply -f "$KDIR/20-app.yaml"
  wait_deploy backend 600s
  wait_deploy frontend 300s

  log "5/9 灌 11 份 seed"
  run_job db-seed 900s "$KDIR/30-db-seed.yaml"

  log "6/9 打开仿真并等车队就位"
  kne set env deployment/backend FSD_PARK_SIMULATION_ENABLED=true >/dev/null
  wait_deploy backend 600s
  sleep 45                          # 首个 tick 在 1s 后，留 45s 给 35 台车各自吸附到 STANDBY 泊位
  local v b
  v=$(db_scalar "SELECT COUNT(*) FROM t_vehicle WHERE deleted=0")
  b=$(db_scalar "SELECT COUNT(*) FROM t_parking_slot WHERE deleted=0 AND slot_type='STANDBY' AND occupied_vehicle_id IS NOT NULL")
  echo "  车队 ${v:-0} 台，绑在 STANDBY 泊位 ${b:-0} 个"
  [ "${v:-0}" -ge 35 ] || die "仿真只造出 ${v:-0} 台车（期望 ≥35）：geo-vehicle-count 或 seed 没生效"
  [ "${b:-0}" -ge 30 ] || echo "  [WARN] 归位 ${b:-0} < 30 —— 车没回完位就压，写路径的争用条件与线上不一致，结论要打折"

  log "7/9 k6 场景与数据"
  gen_od_pairs
  k6_objects
  if [ "$NO_K6" = 1 ]; then
    echo "  [skip] --no-k6：环境已立起来。看前端：kne port-forward svc/frontend 8080:80 → http://localhost:8080"
    return 0
  fi

  log "8/9 压测（阈值不达标 = Job 红）"
  kne delete job k6-order-load --ignore-not-found --wait=true >/dev/null
  kubectl apply -f "$KDIR/40-k6-job.yaml"
  local total=900
  if [ "$SMOKE" = 1 ]; then total=300; fi
  rc=0; wait_job k6-order-load "$total" || rc=$?
  kne logs job/k6-order-load 2>/dev/null | sed 's/^/  | /'
  readout

  # 阈值红不红由 k6 说了算，这里不"调门槛凑绿"：读数照打，退出码照非 0。
  [ "$rc" = 0 ] || die "k6 没通过（rc=$rc：1=阈值未达标，2=超时 ${total}s）—— 上面是它的判定与现场读数"
  echo
  echo "  [ok] 全部阈值达标。归档：把上面这段 summary + 读数 + 档位一起写进路线图，别只留一句\"压过了\""
  echo "  前端：kubectl -n $NS port-forward svc/frontend 8080:80  → http://localhost:8080"
}

# 压测后的现场读数。为什么必须有这一段：k6 只看得到 HTTP 层，而"下单受理成功"和"派到车"
# 是两件事（派单失败时接口照样返回 success=true，任务落进 MANUAL_PENDING）。
# 本机预压实测：order_accepted 99.6%，而库里 966/980 条任务其实是 MANUAL_PENDING。
# 没有这段读数，那轮压测会被误读成"系统能扛 2 单/秒"。
readout() {
  log "9/9 压测后的现场读数"
  local st tk ve err
  st=$(db_scalar "SELECT GROUP_CONCAT(CONCAT(x.status,'=',x.c) SEPARATOR ' ') FROM (SELECT status, COUNT(*) c FROM t_order WHERE deleted=0 GROUP BY status) x")
  tk=$(db_scalar "SELECT GROUP_CONCAT(CONCAT(x.status,'=',x.c) SEPARATOR ' ') FROM (SELECT status, COUNT(*) c FROM t_dispatch_task WHERE deleted=0 GROUP BY status) x")
  ve=$(db_scalar "SELECT CONCAT('车 idle=',SUM(dispatch_status='IDLE'),' busy=',SUM(dispatch_status='BUSY'),' soc<=30=',SUM(battery_level<=30),' 最低=',MIN(battery_level)) FROM t_vehicle WHERE deleted=0")
  echo "  订单：${st:-（空）}"
  echo "  任务：${tk:-（空）}"
  echo "  车队：${ve:-（空）}"
  # `grep -c` 无匹配时退出码是 1：这里必须 `|| true`，否则"零 ERROR"这种最好的结果会让脚本自己先退出
  err=$(kne logs deployment/backend --since=15m 2>/dev/null | grep -c ' ERROR ' || true)
  echo "  后端 15 分钟内 ERROR 行：${err:-0}（>0 就把时间戳贴进文档，别只记一个数）"
}

main "$@"
