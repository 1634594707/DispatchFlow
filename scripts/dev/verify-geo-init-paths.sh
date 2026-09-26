#!/bin/bash
# =====================================================================
# §7.5「新库与已有库两条路径各测一遍」自动化
#
#   bash scripts/dev/verify-geo-init-paths.sh
#
# 做两件事：
#   路径 A（新库）：起一个一次性 MySQL 容器 -> 裸跑 V01-V20 基线 -> Flyway 从 V20
#                   迁移到最新 -> 应用 back/sql/seed/zjf_geo.sql
#   路径 B（已有库）：本地 fsd_core 直接应用同一份 seed
#   然后按业务列做指纹比对（不含 created_at/updated_at，那两列本来就随时间变）。
#
# 指纹两侧都要求非空且行数一致，避免"两边都查不到东西也算通过"的假绿。
# 用完自动清理探针容器。
# =====================================================================
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PROBE="${GEO_PROBE_CONTAINER:-fsd-geo-probe}"
LIVE="${GEO_LIVE_CONTAINER:-fsd-mysql}"
NET="${GEO_PROBE_NETWORK:-dispatchflow_default}"
FLYWAY_IMAGE="${FLYWAY_IMAGE:-flyway/flyway:10.10-alpine}"
FP_SQL="$(mktemp)"
# 定义"地图当前态"的全部 seed（顺序即应用顺序）
GEO_SEEDS=("zjf_geo.sql" "zjf_road_network.sql" "zjf_amap_terminal_links.sql" "zjf_service_area.sql" "zjf_swap_cabinets.sql" "zjf_retire_nearfield_piles.sql" "zjf_facility_v2.sql" "zjf_retire_extra_swap_cabinets.sql" "zjf_charging_points.sql" "zjf_standby_slots.sql" "zjf_energy_sites.sql")
# GEO_KEEP_PROBE=1：比对完不删探针容器，留给人进去逐行 diff
# （只报"DIFF"不给是哪一行时，这个开关就是唯一的出路）
cleanup() {
  if [ "${GEO_KEEP_PROBE:-0}" = "1" ]; then
    echo "  [i] GEO_KEEP_PROBE=1：探针容器 $PROBE 保留，逐行查："
    echo "      docker exec -it $PROBE mysql --default-character-set=utf8mb4 -uroot -proot -D fsd_core"
  else
    docker rm -f "$PROBE" >/dev/null 2>&1
  fi
  rm -f "$FP_SQL"
}
trap cleanup EXIT

case "${MYSQL_HOST:-127.0.0.1}" in
  127.0.0.1|localhost|::1) ;;
  *) echo "[ERROR] 本脚本只对本机容器做实验" >&2; exit 1 ;;
esac

# 只看业务列；改地图后这些指纹会变，两条路径必须一起变
cat > "$FP_SQL" <<'SQL'
SET SESSION group_concat_max_len = 67108864;
-- ⚠ 多边形要比**规范化后的 JSON**，不是列里的原始文本。
--   `polygon_json` 是 JSON 列时 MySQL 会把 `121.075820` 存成 `121.07582`（实测 `CAST(... AS JSON)`
--   同样把尾零削掉）⇒ 同一个几何在"JSON 列"和"文本列"里文本长度不同（活库 264 / 探针 275）。
--   原先 MD5(原文) 把这件事报成 fence 内容 DIFF，查半天是仪表在比序列化写法而不是比内容。
SELECT 'fence' k, COUNT(*) n, IFNULL(MD5(GROUP_CONCAT(CONCAT(fence_code,'|',status,'|',
    IF(polygon_json IS NULL OR polygon_json='', '-', CAST(CAST(polygon_json AS JSON) AS CHAR)),
    '|',IFNULL(response_level,''),'|',IFNULL(fence_type,'')) ORDER BY fence_code SEPARATOR '#')),'-') fp FROM t_park_geofence WHERE deleted=0
-- ⚠ 排除 `GEO_POINT` / `GEO-*`：那是受理侧为"任意点下单"临时落的**派生**站点（OrderEndpointResolver
--   每单一插，前端 stationLayers.isAutoGeoEndpointStation() 也把它们排除在可下单站点之外）。
--   它们不在任何 seed 里 ⇒ 不排除的话，只要本机下过一单，station 指纹就永久 DIFF（假警报）。
UNION ALL SELECT 'station', COUNT(*), IFNULL(MD5(GROUP_CONCAT(CONCAT(station_code,'|',station_name,'|',station_type,'|',IFNULL(coord_lng,''),'|',IFNULL(coord_lat,''),'|',IFNULL(anchor_node_code,''),'|',status) ORDER BY station_code SEPARATOR '#')),'-') FROM t_station WHERE deleted=0 AND station_type <> 'GEO_POINT' AND station_code NOT LIKE 'GEO-%'
UNION ALL SELECT 'node', COUNT(*), IFNULL(MD5(GROUP_CONCAT(CONCAT(node_code,'|',IFNULL(coord_x,''),'|',IFNULL(coord_y,''),'|',IFNULL(coord_lng,''),'|',IFNULL(coord_lat,''),'|',status) ORDER BY node_code SEPARATOR '#')),'-') FROM t_road_node WHERE deleted=0
UNION ALL SELECT 'segment', COUNT(*), IFNULL(MD5(GROUP_CONCAT(CONCAT(from_node_code,'>',to_node_code,'|',IFNULL(speed_limit_kmh,''),'|',status,'|',direction,'|',IFNULL(access_state,'')) ORDER BY from_node_code,to_node_code SEPARATOR '#')),'-') FROM t_road_segment WHERE deleted=0
-- ⚠ slot / pile 的指纹**不含 `status`**：这两张表的 status 是**运行时列**（仿真器每 tick 在写
--   FREE/OCCUPIED/RESERVED，实测本机下过两单之后就是 5 OCCUPIED + 1 RESERVED）。含进来的话
--   "活库用过没有"会变成指纹输入 —— 只要本机跑过单，这两类就永久 DIFF，而 DIFF 报的是
--   "seed 或迁移路径有问题"，于是这条闸门早晚被整体无视（同 §13.105 那个不匹配的守门值）。
--   seed 真正管的是 slot_code/slot_type/entry_node_code，这些两条路径必须一起变。
--   运行时占用改由下面的 advisory 单独打印，不参与判定。
UNION ALL SELECT 'slot', COUNT(*), IFNULL(MD5(GROUP_CONCAT(CONCAT(slot_code,'|',IFNULL(slot_type,''),'|',IFNULL(entry_node_code,'')) ORDER BY slot_code SEPARATOR '#')),'-') FROM t_parking_slot WHERE deleted=0
UNION ALL SELECT 'pile', COUNT(*), IFNULL(MD5(GROUP_CONCAT(CONCAT(pile_code,'|',IFNULL(entry_node_code,'')) ORDER BY pile_code SEPARATOR '#')),'-') FROM t_charging_pile WHERE deleted=0
UNION ALL SELECT 'building', COUNT(*), IFNULL(MD5(GROUP_CONCAT(CONCAT(block_code,'|',status) ORDER BY block_code SEPARATOR '#')),'-') FROM t_building_block WHERE deleted=0
UNION ALL SELECT 'park', COUNT(*), IFNULL(MD5(GROUP_CONCAT(CONCAT(park_code,'|',park_name,'|',IFNULL(anchor_lng,''),'|',IFNULL(map_width,'')) ORDER BY park_code SEPARATOR '#')),'-') FROM t_park WHERE deleted=0
UNION ALL SELECT 'schema', COUNT(*), IFNULL(MD5(GROUP_CONCAT(CONCAT(TABLE_NAME,'.',COLUMN_NAME,'=',DATA_TYPE) ORDER BY TABLE_NAME,COLUMN_NAME SEPARATOR '#')),'-') FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='t_park_geofence' AND COLUMN_NAME IN ('fence_code','status','polygon_json')) OR (TABLE_NAME='t_road_node' AND COLUMN_NAME IN ('node_code','coord_lng','coord_lat','status')) OR (TABLE_NAME='t_road_segment' AND COLUMN_NAME IN ('from_node_code','to_node_code','polyline_geojson','status','direction')) OR (TABLE_NAME='t_station' AND COLUMN_NAME IN ('station_code','coord_lng','coord_lat','anchor_node_code')));
SQL

run_fp() { # $1=container
  docker exec -i "$1" sh -c 'exec mysql --default-character-set=utf8mb4 -N -uroot -p"$MYSQL_ROOT_PASSWORD" -D fsd_core' < "$FP_SQL" 2>/dev/null
}

seed_to() { # $1=container —— 按依赖顺序应用**全部**地理 seed
  # 顺序不能改：zjf_service_area.sql 里那条 UPDATE 要停用 zjf_geo.sql 刚建的旧 ZJF-ZONE-* 片，
  # 而它自己的围栏又是按拆边后的路网图生成的 ⇒ 必须排在两张图 seed 之后。
  # ⚠ 以前这里只灌 zjf_geo.sql —— W2-c/W3-c 之后那只是**这九份 seed 里的一份**，
  #   于是探针库起来是"430 节点 + 10 片旧围栏"，与活库（723 节点 + 1 片 SVC）必然 DIFF，
  #   报的是仪表的过期，不是两条路径的真不一致。
  local c="$1" f
  for f in "${GEO_SEEDS[@]}"; do
    [ -f "$REPO_ROOT/back/sql/seed/$f" ] || { echo "  [FAIL] 缺少 seed $f" >&2; return 1; }
    docker exec -i "$c" sh -c 'exec mysql --default-character-set=utf8mb4 -uroot -proot -D fsd_core' \
      < "$REPO_ROOT/back/sql/seed/$f" || { echo "  [FAIL] 应用 $f 出错" >&2; return 1; }
  done
}

echo "[1/4] 路径 B：已有库 $LIVE 应用 seed ..."
# 指纹比对两侧都用同一份 seed，所以它永远查不出"seed 把运行时状态当成了权威内容"。
# 这一条是补那个盲区：泊位/充电桩一旦被 seed 带着占用态，新库起来就是 6 个假占用。
# ⚠ 扫描面必须覆盖**全部** seed —— 只扫 zjf_geo.sql 会漏掉后来拆出来的那三份。
polluted=0
for f in "${GEO_SEEDS[@]}"; do
  # 判据只取 grep -q 的退出码：写成 `grep ... | head` 的话 `$?` 是 head 的，永远为 0 ⇒ 每份 seed 都假红
  if grep -qE "^INSERT INTO (t_parking_slot|t_charging_pile) .*'(OCCUPIED|RESERVED|CHARGING|FAULT)'" \
       "$REPO_ROOT/back/sql/seed/$f"; then
    echo "  [FAIL] $f 里含运行时占用状态（应为 FREE/NULL）—— 地图内容被仿真快照污染了" >&2
    grep -nE "^INSERT INTO (t_parking_slot|t_charging_pile) .*'(OCCUPIED|RESERVED|CHARGING|FAULT)'" \
      "$REPO_ROOT/back/sql/seed/$f" | head -2 >&2
    polluted=1
  fi
done
if [ "$polluted" -ne 0 ]; then exit 1; fi
if ! seed_to "$LIVE" > /tmp/geo-live-seed.log 2>&1; then
  echo "  [FAIL] 已有库应用 seed 报错："; grep -av "Using a password" /tmp/geo-live-seed.log | head -3; exit 1
fi
run_fp "$LIVE" > /tmp/geo-fp-live.txt
# advisory（不参与判定）：把"泊位/桩的运行时占用"照出来。slot/pile 的指纹已不含 status，
# 没有这一行的话，"闸门为什么看不见占用"就成了一句只有注释里才有的话。
# ⚠ 判空要显式做：`docker … | sed` 之后 `$?` 是 sed 的，查询报错会表现成"什么都没印"。
docker exec -i "$LIVE" sh -c 'exec mysql --default-character-set=utf8mb4 -N -uroot -p"$MYSQL_ROOT_PASSWORD" -D fsd_core' \
  > /tmp/geo-runtime-occupancy.txt 2>/dev/null <<'SQL'
SELECT CONCAT('活库 slot ', IFNULL(status,'NULL'), '=',COUNT(*)) FROM t_parking_slot WHERE deleted=0 GROUP BY status
UNION ALL SELECT CONCAT('活库 pile ', IFNULL(status,'NULL'), '=',COUNT(*)) FROM t_charging_pile WHERE deleted=0 GROUP BY status;
SQL
if [ -s /tmp/geo-runtime-occupancy.txt ]; then
  sed 's/^/  [advisory] 运行时占用（不比）: /' /tmp/geo-runtime-occupancy.txt
else
  echo "  [WARN] 运行时占用没读到（查询失败或表为空）—— 这条 advisory 失效，别把 slot/pile 的 same 读成\"占用也一致\""
fi

echo "[2/4] 路径 A：起一次性探针容器 $PROBE ..."
docker rm -f "$PROBE" >/dev/null 2>&1
# 注意 -v 的宿主机路径必须是 Windows 风格；Git Bash 的 /d/... 会静默挂不上
WIN_ROOT="$(cd "$REPO_ROOT" && pwd -W)"
docker run -d --name "$PROBE" --network "$NET" \
  -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=fsd_core \
  -v "$WIN_ROOT/back/sql:/sql:ro" \
  mysql:8.4 --character-set-server=utf8mb4 --collation-server=utf8mb4_general_ci >/dev/null
for _ in $(seq 1 40); do
  docker exec "$PROBE" mysqladmin ping -h127.0.0.1 -uroot -proot >/dev/null 2>&1 && break
  sleep 3
done
docker exec "$PROBE" sh -c 'test -f /sql/migrations/V01__init_schema.sql' || {
  echo "  [FAIL] /sql 没挂上（-v 需 Windows 风格路径）"; exit 1; }

echo "[3/4] 裸跑 V01-V20 基线 + Flyway 迁移到最新 ..."
docker exec -i "$PROBE" sh -c 'exec bash -s' <<'SH' 2>&1 | grep -av "Using a password"
n=0
for f in $(ls /sql/migrations/V0[1-9]__*.sql /sql/migrations/V1[0-9]__*.sql /sql/migrations/V13b__*.sql /sql/migrations/V20__*.sql /sql/migrations/V20b__*.sql 2>/dev/null | sort); do
  if ! mysql --default-character-set=utf8mb4 -uroot -proot fsd_core < "$f" 2>/tmp/e.txt; then
    echo "  [FAIL] 基线 $(basename "$f")："; head -2 /tmp/e.txt; exit 1
  fi
  n=$((n+1))
done
echo "  基线文件 $n 个"
SH
[ "${PIPESTATUS[0]}" -eq 0 ] || exit 1

docker run --rm --network "$NET" -v "$WIN_ROOT/back/sql/migrations:/flyway/sql:ro" "$FLYWAY_IMAGE" \
  -url="jdbc:mysql://$PROBE:3306/fsd_core?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai" \
  -user=root -password=root -locations=filesystem:/flyway/sql \
  -baselineVersion=20 -baselineOnMigrate=true migrate 2>&1 \
  | grep -aE "Successfully applied|now at version|ERROR" | sed 's/^/  /'
docker exec -i "$PROBE" sh -c 'exec mysql -N -uroot -proot -D fsd_core -e "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"' 2>/dev/null \
  | sed 's/^/  探针末条迁移: /'

if ! seed_to "$PROBE" > /tmp/geo-probe-seed.log 2>&1; then
  echo "  [FAIL] 新库应用 seed 报错："; grep -av "Using a password" /tmp/geo-probe-seed.log | head -3; exit 1
fi

echo "[4/4] 指纹比对 ..."
run_fp "$PROBE" > /tmp/geo-fp-probe.txt
live_lines=$(wc -l < /tmp/geo-fp-live.txt); probe_lines=$(wc -l < /tmp/geo-fp-probe.txt)
if [ "$live_lines" -lt 9 ] || [ "$probe_lines" -lt 9 ]; then
  echo "  [FAIL] 指纹行数 live=$live_lines probe=$probe_lines，比对无效（拒绝把空结果当通过）"; exit 1
fi
paste /tmp/geo-fp-live.txt /tmp/geo-fp-probe.txt | awk '{printf "  %-9s live=%-4s probe=%-4s %s\n",$1,$2,$5,($3==$6?"same":"DIFF")}'
if diff -q /tmp/geo-fp-live.txt /tmp/geo-fp-probe.txt > /dev/null; then
  echo "[OK] 两条路径地理当前态一致（seed 为唯一内容来源）"
else
  echo "[FAIL] 两条路径不一致，seed 或迁移路径有问题"; diff /tmp/geo-fp-live.txt /tmp/geo-fp-probe.txt
  # 只报"哪一类 DIFF"会逼人手查逐行 —— 这里把围栏这一类的差异行直接点名。
  # ⚠ 已知的一类**良性**差异：JSON 列保留写入时的小数标度，同一个几何可能是
  #   `121.075820`（275 字符）或规范化后的 `121.07582`（264 字符）。数值与几何完全相同。
  #   判"良性"的凭据是**两边各自 CAST 成 DOUBLE 后逐点相等**，不是"看着差不多"：
  #   下面的 `_len` 列给出两侧长度，配合 `-e` 手工比对即可判定。
  #   已实测排除：upsert 失效（探针上能把长度 275 改成 39）、seed 写两遍（各 1 次）、
  #   迁移在 seed 之后（seed 最后灌）、列类型分叉（'schema' 指纹 same）。
  for pair in "fence:t_park_geofence:fence_code:polygon_json"; do
    name="${pair%%:*}"; rest="${pair#*:}"; tbl="${rest%%:*}"; rest="${rest#*:}"
    keycol="${rest%%:*}"; col="${rest#*:}"
    # ⚠ 必须走 stdin：`docker exec c sh -c '...' -e "SQL"` 里的 `-e` 是给 sh 的 $0，
    #   不是给 mysql 的 —— 那样两条查询都静默返回空，DIFF 报告反而变成"看不出差异"。
    rowq="SELECT $keycol, CHAR_LENGTH(IFNULL($col,'')), MD5(IFNULL($col,'')) FROM $tbl WHERE deleted=0 ORDER BY $keycol;"
    docker exec -i "$LIVE" sh -c 'exec mysql --default-character-set=utf8mb4 -N -uroot -p"$MYSQL_ROOT_PASSWORD" -D fsd_core' \
      <<< "$rowq" > /tmp/rows_live.txt 2>/dev/null
    docker exec -i "$PROBE" sh -c 'exec mysql --default-character-set=utf8mb4 -N -uroot -proot -D fsd_core' \
      <<< "$rowq" > /tmp/rows_probe.txt 2>/dev/null
    if [ ! -s /tmp/rows_live.txt ] || [ ! -s /tmp/rows_probe.txt ]; then
      echo "  [WARN] $name 类逐行对比取到了空结果，报告不可信（别把'没打印差异'读成'没差异'）"
    elif ! diff -q /tmp/rows_live.txt /tmp/rows_probe.txt >/dev/null; then
      echo "  $name 类逐行差异（join 后：\$1 键 | \$2 \$3 = live 长度/MD5 | \$4 \$5 = probe 长度/MD5）："
      # ⚠ 字段号要按 join 的实际列数算：两份各 3 列 join 完是 5 列，写成 $5/$6 会把
      #   probe 的 MD5 当成"长度"比，于是**全部行都报成差异**（假红，比漏报更难查）。
      join -j1 /tmp/rows_live.txt /tmp/rows_probe.txt \
        | awk '$2!=$4 || $3!=$5 {printf "    %-22s live len=%-6s md5=%.8s | probe len=%-6s md5=%.8s\n",$1,$2,$3,$4,$5}'
    fi
  done
  exit 1
fi
