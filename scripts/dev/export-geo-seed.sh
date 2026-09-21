#!/bin/bash
# =====================================================================
# 导出园区地理当前态种子  back/sql/seed/zjf_geo.sql   （§7.5）
#
#   bash scripts/dev/export-geo-seed.sh [输出路径]
#
# 为什么需要：地图内容（园区、围栏、站点、路网、泊位、桩、建筑块、服务位）过去靠 Flyway
# 迁移一轮轮 fix/snap/recalibrate/reset 迭代，同一批坐标至少改过 4 轮，"迁移太多"和
# "范围怪"是同一个病根。收敛后：迁移只放 DDL，地图内容改这份 seed。
#
# 产出的语句按业务键 upsert（park_code / station_code / fence_code / node_code /
# 起止节点对 / slot_code / pile_code / block_code / position_code —— 这些列上本来就有
# UNIQUE 索引），自增 id 一律写成按业务键回查的子查询，所以新库与已有库都能收敛到
# 同一份当前态，且可重复执行。
#
# 只读本地容器；不接受任何指向远端的参数。
# =====================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="${1:-$REPO_ROOT/back/sql/seed/zjf_geo.sql}"
CONTAINER="${GEO_SEED_CONTAINER:-fsd-mysql}"
SCHEMA="${GEO_SEED_SCHEMA:-fsd_core}"

if [ -n "${DOCKER_HOST:-}" ]; then
  case "$DOCKER_HOST" in
    unix://*|*://127.0.0.1*|*://localhost*) ;;
    *) echo "[ERROR] DOCKER_HOST=$DOCKER_HOST 指向远端 daemon，拒绝执行" >&2; exit 1 ;;
  esac
fi

# SQL 一律走 stdin：早先用 -e "..." 传，嵌套引号会被 sh -c 吃掉并静默返回空结果
mysql_q() {
  docker exec -i "$CONTAINER" sh -c \
    'exec mysql --default-character-set=utf8mb4 --raw -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -D "'"$SCHEMA"'"' <<<"$1"
}

# 自增 id 列 -> 按业务键回查的表达式（$1 表名，$2 列名）
resolve_expr() {
  case "$1:$2" in
    t_park_geofence:park_id|t_station:park_id|t_road_node:park_id|t_road_segment:park_id|t_parking_slot:park_id|t_charging_pile:park_id|t_building_block:park_id)
      printf "concat('(select id from t_park where park_code=', quote(p.park_code), ')')" ;;
    t_charging_pile:parking_slot_id)
      printf "if(ps.slot_code is null, 'NULL', concat('(select id from t_parking_slot where slot_code=', quote(ps.slot_code), ' limit 1)'))" ;;
    t_station_service_position:station_id)
      printf "concat('(select id from t_station where station_code=', quote(st.station_code), ' limit 1)')" ;;
    *) printf '' ;;
  esac
}

extra_join() {
  case "$1" in
    t_park_geofence|t_station|t_road_node|t_road_segment|t_parking_slot|t_building_block)
      printf 'LEFT JOIN t_park p ON p.id = t.park_id' ;;
    t_station_service_position)
      printf 'LEFT JOIN t_station st ON st.id = t.station_id' ;;
    t_charging_pile)
      printf 'LEFT JOIN t_park p ON p.id = t.park_id LEFT JOIN t_parking_slot ps ON ps.id = t.parking_slot_id' ;;
    *) printf '' ;;
  esac
}

SKIP_COLS="'id','created_at','updated_at'"

seed_table() {
  local table="$1" cols col ref value_exprs=() col_list=() upd_list=() joined select_expr
  while IFS= read -r col; do
    [ -z "$col" ] && continue
    col_list+=("\`$col\`")
    upd_list+=("\`$col\`=VALUES(\`$col\`)")
    ref="$(resolve_expr "$table" "$col")"
    if [ -n "$ref" ]; then
      value_exprs+=("$ref")
    else
      value_exprs+=("if(t.\`$col\` is null, 'NULL', quote(t.\`$col\`))")
    fi
  done < <(mysql_q "SELECT COLUMN_NAME FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA='$SCHEMA' AND TABLE_NAME='$table'
                      AND COLUMN_NAME NOT IN ($SKIP_COLS)
                    ORDER BY ORDINAL_POSITION;")

  if [ "${#col_list[@]}" -eq 0 ]; then
    echo "[ERROR] $table 没解析出任何列" >&2
    exit 1
  fi

  # 列名与 ODKU 片段都不含空格，空格拼接后换成逗号即可
  cols=$(printf "%s" "${col_list[*]}" | sed 's/ /, /g')
  upd=$(printf '%s' "${upd_list[*]}" | sed 's/ /, /g')
  # CONCAT 会把相邻参数原样黏在一起，值之间必须自己插 ", " 分隔符，
  # 否则导出的语句会写成 VALUES ('AMAP''ACTIVE'...) 这种没有逗号的废 SQL
  local sep=""
  joined=""
  for expr in "${value_exprs[@]}"; do
    joined+="$sep$expr"
    sep=", ', ', "
  done
  select_expr="CONCAT('INSERT INTO $table ($cols) SELECT ', $joined, ' FROM DUAL ON DUPLICATE KEY UPDATE $upd;')"

  printf -- '-- ---------------- %s ----------------\n' "$table"
  mysql_q "SELECT $select_expr FROM \`$table\` t $(extra_join "$table") WHERE t.deleted = 0 ORDER BY t.id;"
  printf '\n'
}

TABLES=(t_park t_park_geofence t_station t_road_node t_road_segment t_parking_slot t_charging_pile t_building_block t_station_service_position)

mkdir -p "$(dirname "$OUT")"

{
  printf -- '-- =====================================================================\n'
  printf -- '-- DispatchFlow 园区地理当前态种子。自动生成，请勿直接编辑内容。\n'
  printf -- '--   生成：bash scripts/dev/export-geo-seed.sh\n'
  printf -- '--   生成时间：%s   来源库：%s@%s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$SCHEMA" "$CONTAINER"
  printf -- '-- 语义：按业务键幂等 upsert，可重复执行；迁移目录此后只放 DDL（见 CONTRIBUTING 迁移纪律）。\n'
  printf -- '-- 顺序依赖：t_park -> 其余（按 park_code 回查）；t_road_node -> t_road_segment；\n'
  printf -- '--            t_parking_slot -> t_charging_pile；t_station -> t_station_service_position。\n'
  printf -- '-- =====================================================================\n\n'
  printf 'SET NAMES utf8mb4;\n\n'
  for table in "${TABLES[@]}"; do
    seed_table "$table"
  done
} > "$OUT"

statements=$(grep -c '^INSERT INTO' "$OUT" || true)
if [ "${statements:-0}" -lt 50 ]; then
  echo "[ERROR] 只导出 $statements 条，疑似来源库为空或列解析失败" >&2
  exit 1
fi
echo "[OK] $OUT：$statements 条 upsert"
