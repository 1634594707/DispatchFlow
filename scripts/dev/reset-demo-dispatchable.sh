#!/usr/bin/env bash
# =====================================================================
# scripts/dev/reset-demo-dispatchable.sh
#
# 一条命令把「本地」演示库重置到可派单状态。
# 最早服务 §M0 第 3 项 与 §1.7（"车在动"是观感第一优先级）；那份路线图已退场，
# 口径以 docs/DispatchFlow_演示与配置优化任务路线图_2026-09-25.md §0.2 现值表为准。
#
# 用法（在仓库根目录；Windows Git Bash / Linux / macOS 通用）：
#   bash scripts/dev/reset-demo-dispatchable.sh            # 重置 + 自动验证
#   bash scripts/dev/reset-demo-dispatchable.sh --verify   # 只读验证，不写库
#   bash scripts/dev/reset-demo-dispatchable.sh --dry-run  # 只打印将执行的 SQL
#   bash scripts/dev/reset-demo-dispatchable.sh --yes      # 跳过交互确认（CI）
#
# ---------------------------------------------------------------------
# 它修什么
# ---------------------------------------------------------------------
#   1. 遥测刷新    last_report_time <- NOW()
#      库里 `ZJF-AV-*` 的 `last_report_time` 一停摆就远超阈值（最早实测停在 2026-08-22 20:57:56），
#      DispatchVehicleAssignServiceImpl.java:140-147 会直接把整批评成
#      TELEMETRY_STALE —— 这才是"派不出单"的第一现场，不是坐标也不是 SOC。
#   2. SOC 分布化  battery_level 按车号均匀铺到 35..100
#      （刚补齐车队时全是 100，打分里的 socScore 项在演示上恒为 0，看不出选车逻辑）
#      刻意保留 35 这种"过得了 min-assignable-soc 但过不了全链路 SOC"的车，
#      让 DispatchVehicleAssignServiceImpl.java:163-169 的链路过滤真的会触发。
#   3. 坐标合法    current_longitude / current_latitude 吸附到 ACTIVE 路网节点
#   4. 状态机复位  IDLE / ONLINE / 清 current_task_id、current_order_id、current_load
#   5. 卡死流程清理（等价于 scripts/dev/reset_stuck_dispatch.sql 的五步）
#   6. 设施扩容    ZJF-IDLE-01 capacity_limit -> 40（§1.2/§1.3 的 50 台档；旧默认 28 已作废）
#
# ---------------------------------------------------------------------
# 它不修什么（诚实声明）
# ---------------------------------------------------------------------
#   [!] 不写 GCJ-02 —— 这不是"推迟"，是**现行契约**（§1.5 / §7.2：列语义**逐行**由
#       `VehicleLinkMode.isSimulated` 判定，SIM 行存 schematic 像素、真车行存 GCJ-02，
#       读写都走唯一取位口 `geo/VehiclePositionResolver.toPark/toGeo`）。
#       旧叙述"保留列名、改写为合法 GCJ-02、与 §7.6 同批翻列"**已被 §13.40 否决**：
#       照它排工会把演示做废（20/20 吸附、可达率与 `isMetricConsistent` 全建立在
#       "仿真行存像素"之上）。本脚本只动 `ZJF-AV-*`，而本地实测这些行
#       **link_mode 全是 SIM**（`SELECT link_mode, COUNT(*) FROM t_vehicle WHERE deleted=0
#       GROUP BY link_mode` → SIM 20 / 无其它值）⇒ 写像素才是对的，写经纬度才是错的。
#       真车行为什么也不用这里管：写入侧已在 §13.49 按"上报即 GCJ-02"定约，
#       且 `fsd.vda5050.enabled` 恒 false、本机无 broker ⇒ 库里不存在真车行。
#       支撑证据（本地实测 + 源码原文）：
#         - back/fsd-dispatch/src/main/java/com/fsd/dispatch/road/ParkRoadGraph.java:226-230
#           NodeView.distanceTo(BigDecimal x, BigDecimal y) 只对 coord_x/coord_y
#           做欧氏距离，**没有 GPS 分支**；haversine 只存在于节点之间的
#           distanceTo(NodeView)（同文件 :236-241）。
#         - back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkRoutePlannerServiceImpl.java:368-373
#           nearestNode() 调用的正是上面那个"像素版"distanceTo。
#         - back/fsd-dispatch/src/main/java/com/fsd/dispatch/dispatch/DispatchVehicleAssignServiceImpl.java:256-257
#           源码注释原文：「车辆 currentLongitude/currentLatitude 实际存储 schematic x/y」。
#         - back/fsd-vehicle/src/main/java/com/fsd/vehicle/service/impl/VehicleServiceImpl.java:103-116
#           入库分支原文：「仿真车辆（linkMode=SIM）上报的是 schematic x/y，跳过转换」。
#         - 数值量级实测：ACTIVE 节点 coord_x in [-77,1232]、coord_y in [-15,780]（像素），
#           而 coord_lng in [121.071,121.088]、coord_lat in [31.96,31.97]（度）；
#           车辆现值 668.437000/624.450000 恰好等于 ZJF-IDLE-01 的 coord_x/coord_y。
#           若把 121.08/31.96 写进这两列，nearestNode 会把车吸附到画布左上角的
#           错误节点，派单距离与可达性全部失真 —— 演示当场就废。
#       所以本脚本写入的仍是像素坐标，而且做到"正好落在 ACTIVE 节点上"
#       （nearest-node distance = 0）。§7.6 的前端像素兜底删除（§13.55）改的是**读侧换算**，
#       不是这里的存储语义 —— 两件事别混。
#
#   [!] 不凭空造 20 台车。地理池车辆由仿真器自建：
#       back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkPilotSimulationServiceImpl.java:169-199
#       ensurePilotFleet(GEO_VEHICLE_PREFIX, geoVehicleCount) 会按 ZJF-AV-01..NN 补齐。
#       默认已是 20 台：back/fsd-bootstrap/src/main/resources/application.yml:369
#       （`geo-vehicle-count: ${FSD_PARK_SIMULATION_GEO_VEHICLE_COUNT:20}`）；
#       要改档就设这个环境变量后重启，仿真器会补齐 ZJF-AV-01..NN，然后再跑一次本脚本
#       即可把它们一起铺平。
#       （直接 INSERT 17 台假车会让仿真器的内存运动状态与库不一致。）
#
#   [!] 不动 schema、不建迁移、不改 application.yml —— 只改数据。
#   [!] 仿真器启动后会把车开回 ZJF-IDLE-01 待命位（同文件 :1195-1213），
#       那仍是同一像素坐标系内的合法位置，与本脚本不冲突。
#
# ---------------------------------------------------------------------
# 生产防护（§M0 明确要求"脚本须显式禁止指向生产"）
# ---------------------------------------------------------------------
#   唯一访问路径被写死为 `docker exec -i fsd-mysql mysql ...`（见 assert_local_only）。
#   脚本不接受任何 --url/--host/--port 参数；DOCKER_HOST、MYSQL_HOST、各类
#   DATABASE_URL 环境变量只要指向非本机，一律 exit 1。
#   本地开发口令 root 的引用方式与 back/docker-compose.yml:6 和
#   scripts/dev/test_zjf_order_flow.ps1:58 保持一致，且通过 -e MYSQL_PWD 注入，
#   不出现在命令行、不出现在本脚本任何输出里。
# =====================================================================
set -euo pipefail

# ---- 这些常量是防护的一部分，改动等同改安全边界，请同步评估 assert_local_only ----
ALLOWED_CONTAINER="fsd-mysql"          # 唯一允许的落地目标
ALLOWED_SCHEMA="fsd_core"              # 唯一允许的库名
ALLOWED_DB_USER="root"                 # 本地 dev 超级用户
LOCAL_PWD_ENV="MYSQL_ROOT_PASSWORD"    # 与 docker-compose 同名，默认 root
FSD_DB_PASSWORD="${!LOCAL_PWD_ENV:-root}"

# 门禁阈值来源：
#   min-assignable-soc  application.yml:382  默认 30
#   stale-seconds       TelemetryFreshnessPolicy.java:19-22 的 @Value 默认 30
#                       （application.yml 里**没有** fsd.dispatch.telemetry 段，故走代码默认）
MIN_SOC="${FSD_MIN_ASSIGNABLE_SOC:-30}"
STALE_SEC="${FSD_TELEMETRY_STALE_SEC:-30}"
IDLE_STATION="ZJF-IDLE-01"
# 40 = §1.2/§1.3 的 50 台档下限（旧默认 28 是按 20 台算的，随 §1.10-A 一并作废）
IDLE_CAPACITY_TARGET="${FSD_IDLE_CAPACITY_TARGET:-40}"
CC_ROUNDS="${FSD_CC_ROUNDS:-60}"       # 连通分量标签传播轮数（>= 路网直径即可）
# 标签在 t_lbl_a / t_lbl_b 间来回覆盖，只有偶数轮结束时结果才落在 t_lbl_a，
# 而下方所有统计语句都读 t_lbl_a —— 轮数必须取偶。
if [ $((CC_ROUNDS % 2)) -ne 0 ]; then CC_ROUNDS=$((CC_ROUNDS + 1)); fi
VEHICLE_PREFIX="ZJF-AV-"

PASS=0; FAIL=0; WARN=0
log()  { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"; }
ok()   { PASS=$((PASS+1)); printf '  [OK]   %s\n' "$*"; }
bad()  { FAIL=$((FAIL+1)); printf '  [FAIL] %s\n' "$*" >&2; }
warn() { WARN=$((WARN+1)); printf '  [WARN] %s\n' "$*"; }
die()  { printf '\n[ABORT] %s\n' "$*" >&2; exit 1; }

usage() {
  sed -n '3,15p' "$0"
}

# ---------------------------------------------------------------------
# 生产防护：唯一入口是本机 docker 容器 fsd-mysql
# ---------------------------------------------------------------------
assert_local_only() {
  # (1) 拒绝一切远程连接参数
  local a
  for a in "$@"; do
    case "$a" in
      --url|--url=*|--host|--host=*|--port|--port=*|--socket|--socket=*|--user|--user=*|--password=*|-h=*)
        die "本脚本只允许操作本地容器 $ALLOWED_CONTAINER，拒绝接收连接参数「$a」。需要连别的库请自己 mysql。"
        ;;
    esac
  done

  # (2) DOCKER_HOST 若指向远端 daemon，`docker exec` 就不再是本机 —— 直接拒绝
  if [ -n "${DOCKER_HOST:-}" ]; then
    case "$DOCKER_HOST" in
      unix://*|npipe://*|localhost*|127.0.0.1*) : ;;
      *) die "DOCKER_HOST=$DOCKER_HOST 指向远端 daemon，拒绝执行（这会让 docker exec 打到生产机）。" ;;
    esac
  fi

  # (3) 任何 DB 主机/URL 环境变量都必须落在本机回环
  local var v
  for var in MYSQL_HOST DB_HOST FSD_DB_HOST SPRING_DATASOURCE_HOST \
             MYSQL_URL FSD_DB_URL DATABASE_URL SPRING_DATASOURCE_URL JDBC_URL; do
    v="${!var:-}"
    [ -z "$v" ] && continue
    case "$var" in
      *_HOST)
        case "$v" in
          127.0.0.1|localhost|::1|0.0.0.0) : ;;
          *) die "$var=$v 不是本机回环地址，拒绝执行。" ;;
        esac ;;
      *)
        case "$v" in
          *127.0.0.1*|*localhost*|*::1*) : ;;
          *) die "$var 指向非本机地址（值已省略以免泄露连接串），拒绝执行。" ;;
        esac ;;
    esac
  done

  # (4) 目标容器必须存在、在跑、且确实是本地 mysql 镜像
  command -v docker >/dev/null 2>&1 || die "找不到 docker 命令"
  docker inspect "$ALLOWED_CONTAINER" >/dev/null 2>&1 \
    || die "本机不存在容器 $ALLOWED_CONTAINER（bash back/ && docker compose up -d mysql）"
  [ "$(docker inspect -f '{{.State.Running}}' "$ALLOWED_CONTAINER")" = "true" ] \
    || die "容器 $ALLOWED_CONTAINER 未运行"
  case "$(docker inspect -f '{{.Config.Image}}' "$ALLOWED_CONTAINER")" in
    mysql*|*mysql:*) : ;;
    *) die "容器 $ALLOWED_CONTAINER 的镜像不是 mysql，目标可疑，拒绝执行。" ;;
  esac

  # (5) 连上后二次确认 schema 与实例身份，防止同名容器被挪作他用
  local probe
  probe="$(printf "%s\n" \
    "SELECT CONCAT(@@hostname,'|',DATABASE(),'|',@@port) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='${ALLOWED_SCHEMA}' LIMIT 1;" \
    | sql_batch || true)"
  [ -n "$probe" ] || die "容器 $ALLOWED_CONTAINER 里没有 schema $ALLOWED_SCHEMA，本地库还没起来？"
  local hname hdb hport rest
  hname="${probe%%|*}"; rest="${probe#*|}"; hdb="${rest%%|*}"; hport="${rest##*|}"
  log "目标实例: hostname=$hname  默认库=$hdb  端口=$hport (容器内)"
  [ "$hdb" = "$ALLOWED_SCHEMA" ] || die "当前库是 $hdb 而不是 $ALLOWED_SCHEMA，拒绝执行。"

  # (6) 大声确认 + 交互兜底
  printf '\n'
  printf '  ##############################################\n'
  printf '  #  写入目标 = 本地容器 %s\n' "$ALLOWED_CONTAINER"
  printf '  #  schema   = %s\n' "$ALLOWED_SCHEMA"
  printf '  #  这不是生产库；生产口令/主机本脚本一概不接受\n'
  printf '  ##############################################\n'
  printf '\n'
  if [ "$AUTO_YES" -ne 1 ] && [ -t 0 ]; then
    local ans
    read -r -p "  确认对以上本地容器执行写库？[y/N] " ans
    case "$ans" in y|Y|yes|YES) : ;; *) die "用户取消" ;; esac
  fi
}

# ---------------------------------------------------------------------
# MySQL 通道：唯一实现，脚本里任何一条 SQL 都必须走 sql_batch
# ---------------------------------------------------------------------
sql_batch() {   # 多语句，-N -B（无表头 / tab 分隔）；本脚本唯一连库通道
  docker exec -i -e "MYSQL_PWD=${FSD_DB_PASSWORD}" "$ALLOWED_CONTAINER" \
    mysql -u"$ALLOWED_DB_USER" --default-character-set=utf8mb4 -N -B "$ALLOWED_SCHEMA"
}

# ---------------------------------------------------------------------
# 参数
# ---------------------------------------------------------------------
MODE="reset"; AUTO_YES=0
for arg in "$@"; do
  case "$arg" in
    # 生产防护第一道：连接参数一概不接受（与 assert_local_only 内第二道重复，故意冗余）
    --url|--url=*|--host|--host=*|--port|--port=*|--socket|--socket=*|--user|--user=*|--password=*)
      die "本脚本只允许操作本地容器 $ALLOWED_CONTAINER，拒绝接收连接参数「$arg」。需要连别的库请自己 mysql。" ;;
    --verify)  MODE="verify" ;;
    --dry-run) MODE="dry-run" ;;
    --yes|-y)  AUTO_YES=1 ;;
    --help)    usage; exit 0 ;;
    *)         usage; die "未知参数：$arg（可用 --verify / --dry-run / --yes）" ;;
  esac
done

[[ "$MIN_SOC"   =~ ^[0-9]+$ ]] || die "FSD_MIN_ASSIGNABLE_SOC 必须是整数"
[[ "$STALE_SEC" =~ ^[0-9]+$ ]] || die "FSD_TELEMETRY_STALE_SEC 必须是整数"
[[ "$CC_ROUNDS" =~ ^[0-9]+$ ]] || die "FSD_CC_ROUNDS 必须是整数"

# =====================================================================
# 写库
# =====================================================================
reset_sql() {
cat <<SQL
-- (1) 卡死流程清理（与 scripts/dev/reset_stuck_dispatch.sql 同语义）
UPDATE t_dispatch_task
   SET status='CANCELLED', finish_time=NOW(), fail_reason_code=NULL, fail_reason_msg=NULL
 WHERE deleted=0 AND status IN ('PENDING','MANUAL_PENDING','ASSIGNED','EXECUTING');

UPDATE t_order
   SET status='CANCELLED'
 WHERE deleted=0 AND status NOT IN ('SUCCESS','CANCELLED');

UPDATE t_dispatch_exception_record
   SET exception_status='RESOLVED', resolved_time=NOW(), resolve_action='CLOSE',
       resolve_remark='dev reset-demo-dispatchable'
 WHERE exception_status='OPEN';

UPDATE t_parking_slot
   SET status='FREE', occupied_vehicle_id=NULL
 WHERE deleted=0 AND occupied_vehicle_id IS NOT NULL;

UPDATE t_charging_pile
   SET status='FREE', occupied_vehicle_id=NULL
 WHERE deleted=0 AND occupied_vehicle_id IS NOT NULL;

-- (2) 车辆状态机复位：listAssignableVehicles 只认 deleted=0 + ONLINE + IDLE
--     （back/fsd-vehicle/.../VehicleServiceImpl.java:52-59）
UPDATE t_vehicle
   SET online_status='ONLINE',
       dispatch_status='IDLE',
       current_task_id=NULL,
       current_order_id=NULL,
       current_load=0,
       emergency_mode=0,
       manual_override=0
 WHERE deleted=0 AND vehicle_code LIKE '${VEHICLE_PREFIX}%';

-- (3) 铺平面包屑：给每台地理池车分配一个 ACTIVE 路网节点的 schematic 坐标 +
--     均匀 SOC。节点表与被更新表无关，故用临时表承接（幂等：纯赋值，无累加）。
DROP TEMPORARY TABLE IF EXISTS t_reset_plan;
CREATE TEMPORARY TABLE t_reset_plan AS
WITH veh AS (
  SELECT id, vehicle_code,
         ROW_NUMBER() OVER (ORDER BY vehicle_code) AS rn,
         COUNT(*)     OVER () AS total
    FROM t_vehicle
   WHERE deleted=0 AND vehicle_code LIKE '${VEHICLE_PREFIX}%'
),
nd AS (
  SELECT node_code, coord_x, coord_y,
         ROW_NUMBER() OVER (ORDER BY coord_x, coord_y, node_code) AS rn,
         COUNT(*)     OVER () AS cnt
    FROM t_road_node r
   WHERE deleted=0 AND status='ACTIVE' AND coord_x IS NOT NULL AND coord_y IS NOT NULL
     -- 只落在最大**强连通**分量上（t_road_node_component 由 scripts/geo/reanchor_facilities.py 按有向写）：
     -- 裁断版 OSM 图实测无向 4 个分量（78+9+2+2）、有向 9 个（最大 75）——落在小分量里的车跨分量取货必然
     -- UNREACHABLE（§1.8 回退到可达子集）。表为空时（还没跑过重吸附的老库）退回"不过滤"。
     -- 括号是必需的：AND/OR 混写会让 EXISTS 分支绕过 status/deleted 条件。
     AND (NOT EXISTS (SELECT 1 FROM t_road_node_component)
          OR EXISTS (SELECT 1 FROM t_road_node_component c
                      WHERE c.node_code = r.node_code AND c.is_largest = 1 AND c.deleted = 0))
)
SELECT v.id, v.vehicle_code, n.node_code AS snap_node, n.coord_x, n.coord_y,
       CAST(CASE WHEN v.total = 1 THEN 95
                 ELSE ROUND(${MIN_SOC} + 5 + (100 - (${MIN_SOC} + 5)) * (v.rn - 1) / (v.total - 1))
            END AS SIGNED) AS soc,
       -- 等分取中点：cnt>=total 时相邻取整值严格递增，天然无碰撞
       CAST(LEAST(GREATEST(ROUND((v.rn - 0.5) * n.cnt / v.total), 1), n.cnt) AS SIGNED) AS want_rn
  FROM veh v
  JOIN nd  n ON n.rn = LEAST(GREATEST(ROUND((v.rn - 0.5) * n.cnt / v.total), 1), n.cnt);

UPDATE t_vehicle v
  JOIN t_reset_plan p ON p.id = v.id
   SET v.battery_level    = p.soc,
       v.current_longitude= p.coord_x,   -- 像素 coord_x，与 nearestNode 同空间
       v.current_latitude = p.coord_y,   -- 像素 coord_y
       v.last_report_time = NOW();       -- 干掉 TELEMETRY_STALE

DROP TEMPORARY TABLE IF EXISTS t_reset_plan;

-- (4) 设施扩容：ZJF-IDLE-01 待命位 -> 40（§1.2/§1.3 的 50 台档下限）。
--     GREATEST 保证幂等且不会把人工调大的值压回去。
UPDATE t_station
   SET capacity_limit = GREATEST(capacity_limit, ${IDLE_CAPACITY_TARGET})
 WHERE deleted=0 AND station_code='${IDLE_STATION}'
   AND capacity_limit < ${IDLE_CAPACITY_TARGET};
SQL
}

# --dry-run 是纯打印，不连库，所以放在生产防护之前
if [ "$MODE" = "dry-run" ]; then
  log "dry-run：以下是本脚本将要执行的 SQL（未连库、未做任何校验）"
  printf -- '---------------------------------------------------------------------\n'
  reset_sql
  printf -- '---------------------------------------------------------------------\n'
  exit 0
fi

assert_local_only "$@"

if [ "$MODE" = "reset" ]; then
  log "重置本地演示数据 -> 可派单"
  reset_sql | sql_batch
  log "写库完成，开始验证"
  printf -- '---------------------------------------------------------------------\n'
fi

# =====================================================================
# 验证（全部只读 SELECT）
# =====================================================================
verify_sql() {
cat <<SQL
DROP TEMPORARY TABLE IF EXISTS t_v, t_s, t_edge, t_lbl_a, t_lbl_b, t_lbl_c, t_msg;

-- 每台车到最近 ACTIVE 路网节点的像素距离（= nearestNode 真实会做的事）
CREATE TEMPORARY TABLE t_v AS
SELECT v.id, v.vehicle_code vc, v.online_status os, v.dispatch_status ds,
       v.battery_level soc, v.link_mode lm,
       v.current_longitude cx, v.current_latitude cy,
       TIMESTAMPDIFF(SECOND, v.last_report_time, NOW()) age_sec,
       (SELECT n.node_code FROM t_road_node n
         WHERE n.deleted=0 AND n.status='ACTIVE' AND n.park_id=v.park_id
           AND v.current_longitude IS NOT NULL
         ORDER BY POW(n.coord_x-v.current_longitude,2)+POW(n.coord_y-v.current_latitude,2), n.node_code
         LIMIT 1) nn,
       SQRT(COALESCE((SELECT MIN(POW(n.coord_x-v.current_longitude,2)+POW(n.coord_y-v.current_latitude,2))
                        FROM t_road_node n
                       WHERE n.deleted=0 AND n.status='ACTIVE' AND n.park_id=v.park_id),0)) dist_px
  FROM t_vehicle v
 WHERE v.deleted=0 AND v.vehicle_code LIKE '${VEHICLE_PREFIX}%';

-- 每个 ACTIVE 取货位的最近节点
CREATE TEMPORARY TABLE t_s AS
SELECT s.station_code sc,
       (SELECT n.node_code FROM t_road_node n
         WHERE n.deleted=0 AND n.status='ACTIVE' AND n.park_id=s.park_id
         ORDER BY POW(n.coord_x-s.coord_x,2)+POW(n.coord_y-s.coord_y,2), n.node_code
         LIMIT 1) nn
  FROM t_station s
 WHERE s.deleted=0 AND s.status='ACTIVE' AND s.station_type IN ('PICKUP','MOTHERSHIP');

-- 图重建：与 ParkRoadGraph.fromDatabase 完全同规则（ACTIVE 节点/路段、
-- BLOCKED 与 PEDESTRIAN_ONLY 禁行、direction 暂忽略因全为 BIDIRECTIONAL）
CREATE TEMPORARY TABLE t_edge (KEY(src), KEY(dst)) AS
SELECT a.node_code src, b.node_code dst
  FROM t_road_segment s
  JOIN t_road_node a ON a.node_code=s.from_node_code AND a.deleted=0 AND a.status='ACTIVE'
  JOIN t_road_node b ON b.node_code=s.to_node_code  AND b.deleted=0 AND b.status='ACTIVE'
 WHERE s.deleted=0 AND s.status='ACTIVE'
   AND (s.access_state IS NULL OR UPPER(s.access_state) NOT IN ('BLOCKED','PEDESTRIAN_ONLY'))
UNION ALL
SELECT b.node_code, a.node_code
  FROM t_road_segment s
  JOIN t_road_node a ON a.node_code=s.from_node_code AND a.deleted=0 AND a.status='ACTIVE'
  JOIN t_road_node b ON b.node_code=s.to_node_code  AND b.deleted=0 AND b.status='ACTIVE'
 WHERE s.deleted=0 AND s.status='ACTIVE'
   AND (s.access_state IS NULL OR UPPER(s.access_state) NOT IN ('BLOCKED','PEDESTRIAN_ONLY'));

CREATE TEMPORARY TABLE t_lbl_a AS
SELECT node_code, node_code grp FROM t_road_node WHERE deleted=0 AND status='ACTIVE';

-- 连通分量：标签传播（MySQL 递归 CTE 会枚举所有简单路径而爆炸，故不用它）
SQL
local i src dst
for i in $(seq 1 "$CC_ROUNDS"); do
  if [ $((i % 2)) -eq 1 ]; then src=t_lbl_a; dst=t_lbl_b; else src=t_lbl_b; dst=t_lbl_a; fi
  cat <<SQL
DROP TEMPORARY TABLE IF EXISTS t_msg;
CREATE TEMPORARY TABLE t_msg AS
SELECT e.src node, MIN(l.grp) m FROM t_edge e JOIN $src l ON l.node_code=e.dst GROUP BY e.src;
DROP TEMPORARY TABLE IF EXISTS $dst;
CREATE TEMPORARY TABLE $dst AS
SELECT c.node_code, LEAST(c.grp, COALESCE(m.m, c.grp)) grp FROM $src c LEFT JOIN t_msg m ON m.node=c.node_code;
SQL
done

cat <<SQL
-- 临时表同语句只能引用一次，复制一份做第二次 join
CREATE TEMPORARY TABLE t_lbl_c AS SELECT node_code, grp FROM t_lbl_a;

SELECT 'K','vehicle_total',        COUNT(*)             FROM t_v;
SELECT 'K','assignable_state',     COUNT(*)             FROM t_v WHERE os='ONLINE' AND ds='IDLE';
SELECT 'K','soc_ok',               COUNT(*)             FROM t_v WHERE soc >= ${MIN_SOC};
SELECT 'K','soc_min',   COALESCE(MIN(soc),-1)           FROM t_v;
SELECT 'K','soc_max',   COALESCE(MAX(soc),-1)           FROM t_v;
SELECT 'K','age_max_sec', COALESCE(MAX(age_sec),-1)     FROM t_v;
SELECT 'K','stale_limit_sec', ${STALE_SEC};
SELECT 'K','on_graph_zero_px',   COUNT(*)               FROM t_v WHERE dist_px < 0.001;
SELECT 'K','distinct_positions', COUNT(DISTINCT cx, cy) FROM t_v;
SELECT 'K','idle_capacity', capacity_limit              FROM t_station WHERE deleted=0 AND station_code='${IDLE_STATION}';
SELECT 'K','idle_capacity_target', ${IDLE_CAPACITY_TARGET};
SELECT 'K','graph_nodes_active', COUNT(*)               FROM t_road_node WHERE deleted=0 AND status='ACTIVE';
SELECT 'K','graph_nodes_no_gps', COUNT(*)               FROM t_road_node WHERE deleted=0 AND status='ACTIVE' AND (coord_lng IS NULL OR coord_lat IS NULL);
SELECT 'K','allnodes_total',     COUNT(*)               FROM t_road_node WHERE deleted=0;
SELECT 'K','allnodes_no_gps',    COUNT(*)               FROM t_road_node WHERE deleted=0 AND (coord_lng IS NULL OR coord_lat IS NULL);
SELECT 'K','components',         COUNT(DISTINCT grp)     FROM t_lbl_a;
-- 可达判定优先用 t_road_node_component（**有向强连通**，由 scripts/geo/reanchor_facilities.py 写入），
-- 表里没有这个节点时才回退到上面的无向标签传播。原因：ParkRoadGraph 会把 FORWARD 只展成一个方向，
-- "无向连通"并不等于 Java 侧 isReachable 能成路（实测裁断版图 113 边含 17 条单向）。
SELECT 'K','scc_components',     COUNT(DISTINCT component_id) FROM t_road_node_component WHERE deleted=0;
SELECT 'K','scc_largest',        COUNT(*) FROM t_road_node_component WHERE deleted=0 AND is_largest=1;
-- 分量表是派生物（reanchor_facilities.py 写），路网 seed 加了新节点它不会自己跟上。
-- 少了行的节点在下面会被 COALESCE 退回无向标签，而 s<id> 与无向标签永远不相等
-- => 好端端的车被报成 NO_ROUTE。实测 AMWL/AMCJ 16 个节点就这样造出一个假 FAIL。
SELECT 'K','scc_missing',        COUNT(*) FROM t_road_node r
  WHERE r.deleted=0 AND r.status='ACTIVE' AND r.coord_lng IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM t_road_node_component c
                     WHERE c.deleted=0 AND c.node_code=r.node_code);
SELECT 'K','pickup_stations',    COUNT(*)                FROM t_s;
SELECT 'K','pair_total', COUNT(*) FROM t_v v JOIN t_s s
  JOIN t_lbl_a a ON a.node_code=v.nn JOIN t_lbl_c b ON b.node_code=s.nn;
SELECT 'K','pair_ok', IFNULL(SUM(
    COALESCE((SELECT CONCAT('s',c1.component_id) FROM t_road_node_component c1
               WHERE c1.deleted=0 AND c1.node_code=v.nn), a.grp)
 = COALESCE((SELECT CONCAT('s',c2.component_id) FROM t_road_node_component c2
               WHERE c2.deleted=0 AND c2.node_code=s.nn), b.grp)),0)
  FROM t_v v JOIN t_s s
  JOIN t_lbl_a a ON a.node_code=v.nn JOIN t_lbl_c b ON b.node_code=s.nn;

SELECT 'V', vc, os, ds, soc, age_sec, CONCAT(cx+0,' / ',cy+0), nn, ROUND(dist_px,2), lm
  FROM t_v ORDER BY vc;
SELECT 'P', sc, nn FROM t_s ORDER BY sc;
SELECT 'R', v.vc, s.sc, v.nn, s.nn, IF(COALESCE((SELECT CONCAT('s',c1.component_id) FROM t_road_node_component c1
                WHERE c1.deleted=0 AND c1.node_code=v.nn), a.grp)
     = COALESCE((SELECT CONCAT('s',c2.component_id) FROM t_road_node_component c2
                WHERE c2.deleted=0 AND c2.node_code=s.nn), b.grp),'ROUTE_OK','NO_ROUTE')
  FROM t_v v JOIN t_s s JOIN t_lbl_a a ON a.node_code=v.nn JOIN t_lbl_c b ON b.node_code=s.nn
 ORDER BY s.sc, v.vc;
SQL
}

log "验证（只读）"
RAW="$(verify_sql | sql_batch)" || die "验证查询执行失败"

getv() { printf '%s\n' "$RAW" | awk -F'\t' -v k="$1" '$1=="K" && $2==k {print $3; exit}'; }

log "关键指标"
VT="$(getv vehicle_total)"
if [ "${VT:-0}" = "0" ]; then
  bad "没有 $VEHICLE_PREFIX 车辆：地理池由仿真器 ensurePilotFleet 建，请先启服务或设 FSD_PARK_SIMULATION_GEO_VEHICLE_COUNT"
else
  ok "车辆数 = $VT（地理池前缀 $VEHICLE_PREFIX）"
  AS="$(getv assignable_state)"; [ "$AS" = "$VT" ] && ok "状态机 IDLE+ONLINE = $AS/$VT" || bad "仅 $AS/$VT 处于 IDLE+ONLINE"
  SO="$(getv soc_ok)";           [ "$SO" = "$VT" ] && ok "SOC >= $MIN_SOC 的车 = $SO/$VT" || warn "SOC >= $MIN_SOC 只有 $SO/$VT（分布化后属正常，需 >=1）"
  [ "${SO:-0}" -ge 1 ] || bad "没有任何车能过 min-assignable-soc=$MIN_SOC"
  SMIN="$(getv soc_min)"; SMAX="$(getv soc_max)"
  ok "SOC 分布 = $SMIN .. $SMAX（min-assignable-soc=$MIN_SOC）"
  [ "$SMIN" != "$SMAX" ] && ok "SOC 已分布化（min != max）" || bad "SOC 仍是单值 $SMIN，未分布化"
  AGE="$(getv age_max_sec)"; LIM="$(getv stale_limit_sec)"
  # 负数是容器时钟与 NOW() 的亚秒级错位（TIMESTAMPDIFF 向零取整），语义上就是"刚刚上报"，
  # 不能落进下面的"超过阈值"分支（实测刚重置完会读出 max=-1s）。
  if [ "${AGE:-0}" -lt 0 ] 2>/dev/null; then AGE=0; fi
  if [ "$AGE" -ge 0 ] 2>/dev/null && [ "$AGE" -le "$LIM" ] 2>/dev/null; then
    ok "遥测年龄 max=${AGE}s <= 阈值 ${LIM}s（TelemetryFreshnessPolicy）"
  elif [ "$MODE" = "verify" ]; then
    # --verify 不写库，车放着不动必然会变陈旧 —— 这是事实而不是脚本失败
    warn "遥测年龄 max=${AGE}s 已超过阈值 ${LIM}s：--verify 不写库，这是正常现象；去掉 --verify 跑一次重置即可刷新"
  else
    bad "遥测年龄 max=${AGE}s 超过阈值 ${LIM}s -> 派单会 TELEMETRY_STALE"
  fi
  ON="$(getv on_graph_zero_px)"
  ok "正好落在 ACTIVE 节点上的车 = $ON/$VT（nearest-node 像素距离 0）"
  DP="$(getv distinct_positions)"
  [ "$DP" = "$VT" ] && ok "位置互不重合 = $DP/$VT" || warn "位置只有 $DP/$VT 个不同值"
fi

IC="$(getv idle_capacity)"; ICT="$(getv idle_capacity_target)"
if [ "${IC:-0}" -ge "$ICT" ] 2>/dev/null; then
  ok "$IDLE_STATION capacity_limit = $IC (>= $ICT，§1.2/§1.3 待命位)"
else
  bad "$IDLE_STATION capacity_limit = ${IC:-NULL}，未达到 $ICT"
fi

log "路网度量一致性（决定 A* 还是回退 Dijkstra）"
GNA="$(getv graph_nodes_active)"; GNG="$(getv graph_nodes_no_gps)"
ANT="$(getv allnodes_total)";     ANG="$(getv allnodes_no_gps)"
if [ "${GNG:-1}" = "0" ]; then
  ok "参与建图的 ACTIVE 节点 $GNA 个，其中缺 coord_lng/coord_lat 的 = $GNG -> isMetricConsistent=true -> 走 A*（haversine 米）"
else
  warn "ACTIVE 节点里有 $GNG/$GNA 缺 GPS -> ParkRoutePlannerServiceImpl.isMetricConsistent=false -> 回退 Dijkstra"
fi
if [ "$ANG" != "$GNG" ]; then
  printf '       注：全表 %s 个节点中有 %s 个缺 GPS，但它们在 status=DISABLED 上，\n' "$ANT" "$ANG"
  printf '       而 resolveGraph()/ParkRoadGraph.fromDatabase 只装 status=ACTIVE，\n'
  printf '       所以路线图 §0.2 记录的「%s/%s 节点无经纬度 -> 强制 Dijkstra」并不成立。\n' "$ANG" "$ANT"
fi

log "几何前置条件：每台车 / 每个取货位 -> 最近 ACTIVE 节点"
printf '%s\n' "$RAW" | awk -F'\t' '$1=="V"{printf "  车 %-10s %-6s/%-6s SOC=%-4s age=%-3ss pos=(%s) 最近节点=%-6s 距离=%-6spx link=%s\n",$2,$3,$4,$5,$6,$7,$8,$9,$10}'
printf '%s\n' "$RAW" | awk -F'\t' '$1=="P"{printf "  取货位 %-10s -> 最近节点 %s\n",$2,$3}'
PS="$(getv pickup_stations)"
[ "${PS:-0}" -ge 1 ] && ok "ACTIVE 取货位（含设施v2的总仓库 MOTHERSHIP）= $PS" || bad "没有 ACTIVE 取货位/总仓库，派单无从谈起"

log "可达性（同一**有向强连通**分量 => Java 侧 isReachable/buildRoute 能成路）"
CMP="$(getv components)"
SCC="$(getv scc_components)"
SCCL="$(getv scc_largest)"
SCCM="$(getv scc_missing)"
if [ "${SCC:-0}" -gt 0 ] 2>/dev/null && [ "${SCCM:-0}" -gt 0 ] 2>/dev/null; then
  bad "t_road_node_component 落后于路网：$SCCM 个 ACTIVE 节点没有分量行 —— 下面的车-取货位判定会把它们当成不连通（假 NO_ROUTE）。先跑 python scripts/geo/reanchor_facilities.py --components-only 再验"
fi
if [ "${SCC:-0}" -gt 0 ] 2>/dev/null; then
  warn "路网分量：无向 $CMP 个 / 有向强连通 $SCC 个（最大 SCC $SCCL 节点）—— 跨强连通分量的取货必然 UNREACHABLE，无向数只作参考"
elif [ "${CMP:-99}" = "1" ]; then
  ok "ACTIVE 路网连通分量 = 1（$GNA 节点全部互通，$CC_ROUNDS 轮标签传播收敛）"
else
  warn "ACTIVE 路网有 $CMP 个连通分量 -> 跨分量的取货必然 UNREACHABLE"
fi
printf '%s\n' "$RAW" | awk -F'\t' '$1=="R"{printf "  %-10s -> %-12s %s (%s -> %s)\n",$2,$3,$6,$4,$5}'
PT="$(getv pair_total)"
POK="$(getv pair_ok)"
if [ "${PT:-0}" != "0" ] && [ "$PT" = "$POK" ]; then
  ok "车-取货位组合 $POK/$PT 全部 ROUTE_OK"
elif [ "${PT:-0}" = "0" ]; then
  bad "无法评估车-取货位组合（车辆或取货位为空）"
else
  bad "车-取货位组合只有 $POK/$PT ROUTE_OK"
fi

printf -- '---------------------------------------------------------------------\n'
log "验证结果: PASS=$PASS  WARN=$WARN  FAIL=$FAIL"
[ "$FAIL" -eq 0 ] || { printf '  派单链任一门禁失败即整单失败，请按上面 [FAIL] 逐条处理。\n' >&2; exit 1; }
if [ "$MODE" = "reset" ]; then
  log "本地演示库已处于可派单状态。下一步：起后端，跑 scripts/dev/test_zjf_order_flow.ps1 派一单。"
fi
exit 0
