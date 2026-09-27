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
#
# 第一次跑要有耐心，也第一次大概率会踩网络：backend 镜像里是**容器内冷缓存的 `mvn clean package`**，
#   本机实测约 35 分钟；2026-09-26 第一次跑就在依赖下载上红了一次，同样的 build 重跑就过了。
#   所以 build 失败先分辨是不是下载失败（`docker build --progress=plain` 看得到），别急着改代码；
#   pom 不动之后重跑加 `--skip-build` 直接沿用镜像。
set -euo pipefail

NS=dispatchflow-perf
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && (pwd -W 2>/dev/null || pwd))"
KDIR="$REPO_ROOT/deploy/k8s"
TMP="$REPO_ROOT/tmp/perf"
BUILD_TAG="${BUILD_TAG:-perf}"
K6_IMAGE="${K6_IMAGE:-grafana/k6:1.6.1}"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.4}"
REDIS_IMAGE="${REDIS_IMAGE:-redis:7.4}"
RABBIT_IMAGE="${RABBIT_IMAGE:-rabbitmq:3.13-management}"
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
# Docker Desktop 的 k8s 是 kind 模式（`docker desktop kubernetes status` 的 Mode 字段）：
# 宿主机 build/pull 的镜像**不在集群的 containerd 里**，Pod 只会 ImagePullBackOff，
# 而 `imagePullPolicy: IfNotPresent` 不会区分"没这个镜像"和"镜像坏了"。
# 所以要 `docker save | ctr -n k8s.io images import`，并且导入后回查。
#
# ⚠ 节点容器 `desktop-control-plane` **不出现在 `docker ps` 里**（Docker Desktop 把它放在系统作用域），
#   但 `docker inspect` 查得到。2026-09-26 首次实跑就栽在这：按 `docker ps` 找节点找不到，
#   而旧兜底写的是"找不到就当共享镜像存储、无需导入" ⇒ 四个镜像全被报成"集群可见"，
#   Pod 集体 ImagePullBackOff。现在的规则：**找不到节点就中止**，不替集群下结论。
kind_node() {
  if [ -n "${KIND_NODE:-}" ]; then printf '%s' "$KIND_NODE"; return; fi
  local n
  for n in desktop-control-plane docker-desktop-control-plane; do
    if [ "$(docker inspect -f '{{.State.Status}}' "$n" 2>/dev/null || echo missing)" = "running" ]; then
      printf '%s' "$n"; return
    fi
  done
  # 自己起的 kind 集群也在这一支里找到（名字以 -control-plane 结尾）
  docker ps -a --format '{{.Names}}	{{.Image}}'     | awk 'tolower($2) ~ /kindest\/node/ || $1 ~ /control-plane$/ {print $1; exit}'
}

canonical_ref() {  # 镜像短名 → containerd 里的规范名（library 与非 library 不是一回事）
  case "$1" in
    */*/*) printf '%s' "$1" ;;                              # 已带 registry
    */*)   printf 'docker.io/%s' "$1" ;;                    # grafana/k6 → docker.io/grafana/k6
    *)     printf 'docker.io/library/%s' "$1" ;;            # mysql:8.4 → docker.io/library/mysql:8.4
  esac
}

image_in_cluster() {  # image_in_cluster <节点> <镜像短名>
  # 曾经这里一律拼 `docker.io/library/…`，于是 grafana/k6 明明导入成功却被判成"集群仍看不到它"
  # （假红）。非 library 命名空间是 `docker.io/<org>/<repo>`，必须分开拼。
  local ref
  ref=$(canonical_ref "$2")
  docker exec "$1" ctr -n k8s.io images ls 2>/dev/null | grep -q "$ref "
}

load_image() {
  local img="$1" force="${2:-}" node
  node=$(kind_node)
  if [ -z "$node" ]; then
    die "找不到 k8s 节点容器（试过 desktop-control-plane 与 docker ps 里的 kindest/control-plane）。
        镜像送不进集群。先确认 docker desktop kubernetes status 是 running；
        节点名不一样就 KIND_NODE=<容器名> 重跑。这里不再假设「集群与 docker 共享镜像存储」。"
  fi
  # `force=1` 给三个自建镜像：它们在集群里**永远叫同一个 tag**（dispatchflow-backend:perf），
  # 而 containerd 按名字判定"已在集群内" ⇒ 宿主机重 build 之后，旧镜像会原样留下并被用上。
  # 这个坑的形状是"改了代码、重跑了压测、数字一模一样"，读的人只会得出"修了没用"。
  # 拉来的基础设施镜像（mysql/k6/…）没有重 build 的问题，才允许按名字跳过。
  if [ "$force" != "1" ] && image_in_cluster "$node" "$img"; then
    echo "  [ok] $img 已在集群内（节点 $node）"
    return 0
  fi
  echo "  导入 $img → $node（宿主机 image ID $(docker image inspect -f '{{.Id}}' "$img" | cut -c8-19)）"
  docker save "$img" | docker exec -i "$node" ctr --namespace=k8s.io images import - >/dev/null
  image_in_cluster "$node" "$img" || die "$img 导入后集群仍看不到它"
}

pull_if_missing() {  # 宿主机没有才去拉；拉不到就明说，不静默降级
  local img="$1"
  docker image inspect "$img" >/dev/null 2>&1 && return 0
  echo "  宿主机没有 $img，正在拉取"
  docker pull -q "$img" >/dev/null     || die "拉取 $img 失败：这台机器到 Docker Hub 的鉴权偶发 EOF（auth.docker.io），重试或走镜像源"
}

# 要进集群的全部镜像：三个自建的 + k6 + 三个基础设施。
# 基础设施那几个宿主机早就有（本机 dev 容器在用），但 kind 节点的 containerd 里没有，
# 而在集群里拉 Docker Hub 又受这台机器的网络摆布 ⇒ 一律本地导入，不赌网络。
load_all_images() {
  local img
  # 第二个参数 1 = 每次都重导入，见 load_image 里那段"同名 tag 换内容"的注释
  for img in "dispatchflow-backend:$BUILD_TAG" "dispatchflow-frontend:$BUILD_TAG" \
             "dispatchflow-sql:$BUILD_TAG"; do
    pull_if_missing "$img"
    load_image "$img" 1
  done
  for img in "$K6_IMAGE" "$MYSQL_IMAGE" "$REDIS_IMAGE" "$RABBIT_IMAGE"; do
    pull_if_missing "$img"
    load_image "$img"
  done
}

build_images() {
  if [ "$SKIP_BUILD" = 1 ]; then
    echo "  [skip] --skip-build（沿用已有镜像；pom 没动就该走这条）"
    return 0
  fi
  log "  构建三个镜像（backend 是容器内冷缓存 mvn，本机实测约 35 分钟）"
  docker build -q -t "dispatchflow-backend:$BUILD_TAG" -f "$REPO_ROOT/back/Dockerfile" "$REPO_ROOT/back"
  docker build -q -t "dispatchflow-frontend:$BUILD_TAG" -f "$REPO_ROOT/front/Dockerfile" "$REPO_ROOT/front"
  docker build -q -t "dispatchflow-sql:$BUILD_TAG" -f "$KDIR/Dockerfile.sql" "$REPO_ROOT/back/sql"
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
  local d="$1" t="${2:-420s}" waited=0 state avail want
  local gen='' obs='' upd='' rdy=''
  # `600s` 这种写法是给 kubectl --timeout 用的，轮询自己要的是秒数
  local secs="${t%s}"
  case "$secs" in ('' | *[!0-9]*) die "wait_deploy 的超时参数不是秒数：$t" ;; esac
  # 不用 `kubectl rollout status`：它读 Deployment 的进度判定历史 —— 一旦某次因镜像拉不到触发过
  # ProgressDeadlineExceeded，之后即使 Pod 全部 Available 它照样报 "exceeded its progress deadline"
  # （2026-09-26 首次实跑正是：三个 pod 已 1/1 Running，脚本却红在等 mysql）。
  # 也不用 `--for=condition=available`：旧 ReplicaSet 撑着 available 时，刚 `set env` 触发的
  # 新 rollout 会被误判成"已经好了"。所以自己轮四件事：observedGeneration 跟上、
  # available / updated / ready 都等于期望副本数。
  while [ "$waited" -lt "$secs" ]; do
    state=$(kne get deploy "$d" -o jsonpath='{.metadata.generation} {.status.observedGeneration} {.spec.replicas} {.status.availableReplicas} {.status.updatedReplicas} {.status.readyReplicas}' 2>/dev/null)
    read -r gen obs want avail upd rdy <<<"${state:-}"
    if [ -n "$gen" ] && [ "$gen" = "${obs:-}" ] && [ "${avail:-0}" = "$want" ] \
       && [ "${upd:-0}" = "$want" ] && [ "${rdy:-0}" = "$want" ]; then
      echo "  [ok] deployment/$d Ready（$want/$want，observedGeneration=$obs）"
      return 0
    fi
    sleep 5; waited=$((waited + 5))
  done
  kne get pods -l app="$d" -o wide | sed 's/^/  | /' >&2
  kne describe deploy "$d" | tail -12 >&2
  die "deployment/$d 没在 $t 内真正滚动完成（最后状态：gen=$gen obs=${obs:-?} want=$want avail=${avail:-?} upd=${upd:-?} ready=${rdy:-?}）"
}

wait_job() {  # 失败要立刻看出来，不能等满超时才报 —— 所以自己轮两个条件
  local name="$1" t="${2:-900s}" waited=0
  local secs="${t%s}"
  case "$secs" in ('' | *[!0-9]*) die "wait_job 的超时参数不是秒数：$t" ;; esac
  while [ "$waited" -lt "$secs" ]; do
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
    *) die "job/$name 在 $t 内没结束" ;;
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
  load_all_images
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
  # 上一轮的教训：这个计数是**下界**。容器日志会轮，15 分钟的窗口里后端只剩最后 10 分钟的行，
  # 那 1 358 个 500 的栈就是这么丢的。把可读到的最早时间戳打出来，截断就看得见。
  local first
  # 每条 `| head -N` 都要 `|| true`：head 提前关管道会让上游 kubectl 吃到 SIGPIPE（141），
  # 而 `set -o pipefail` 把它变成整条命令的失败 —— 这一段就因此把一轮**全绿**的压测报成 rc=1。
  first=$(kne logs deployment/backend --since=15m --timestamps 2>/dev/null | head -1 | cut -d' ' -f1 || true)
  echo "  （日志可读起点：${first:-无}；起点晚于压测开始 = 这段已被轮掉，计数只作下界看）"
  # 异常分类比计数有用：计数只能说"红"，这一行说"红在哪"。
  kne logs deployment/backend --since=15m 2>/dev/null | grep -aoE '[A-Za-z0-9_.]+Exception' | sort | uniq -c | sort -rn | head -5 | sed 's/^/    /' || true
  # nginx 的访问日志是这一轮**唯一**活过日志轮的现场（后端 stdout 没活过）：按状态码给出总数。
  local bad
  bad=$(kne logs deployment/frontend --since=15m 2>/dev/null | awk '$0 ~ / 5[0-9][0-9] /{print $7}' | sed -E 's/\?.*//' | sort | uniq -c | sort -rn | head -5 || true)
  echo "  入口 5xx 按路径：$(echo "${bad:-无}" | tr '\n' ' ')"
}

main "$@"
