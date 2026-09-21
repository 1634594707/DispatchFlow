#!/bin/bash
# =====================================================================
# 本地演示库换路网：现役 RN* 网格 -> OSM 扩范围图（§M2E / §1.6 路 A）
#
#   bash scripts/geo/swap-road-network.sh            # 换成正在用的裁断版 OSM 图
#   bash scripts/geo/swap-road-network.sh --restore  # 换回 data/backup 里的 RN* 网格
#
# 为什么要有这个脚本：§13.9.1 那次扩范围是手工灌库 + 手工回退，过程不可重放，
#   前后 A/B 因此无法复现。这里把"停用旧图 -> 灌新图 -> 重吸附 -> 可达率验证"钉成一条命令。
#
# 顺序不能换：
#   1. 先把旧图整片置 DISABLED（`ParkRoutePlannerServiceImpl.loadGraphFromSource` 只读
#      status=ACTIVE 的节点与路段，混着留会让 A* 在两张图之间跳）；
#   2. 再灌新图（seed 是按业务键幂等的 upsert，重放安全）；
#   3. 然后才重吸附 —— 站点/泊位/桩的 anchor_node_code 指的是旧节点，不重吸附会整片 UNREACHABLE；
#   4. 最后 reset-demo-dispatchable.sh --verify 量可达率。
#
# 只打本机：容器名或 MYSQL_HOST 指向非本机就拒绝（会改到生产库）。
# =====================================================================
set -euo pipefail

MODE="apply"
[ "${1:-}" = "--restore" ] && MODE="restore"

CONTAINER="${BENCH_CONTAINER:-fsd-mysql}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STAMP_DIR="$REPO_ROOT/data/backup"
NETWORK_SQL="$REPO_ROOT/back/sql/seed/zjf_road_network.sql"
GRID_BACKUP="$STAMP_DIR/road_network_v44_grid.sql"

if [ -n "${MYSQL_HOST:-}" ] && [ "${MYSQL_HOST}" != "127.0.0.1" ] && [ "${MYSQL_HOST}" != "localhost" ]; then
  echo "[ERROR] MYSQL_HOST=$MYSQL_HOST 非本机，拒绝改路网" >&2
  exit 1
fi
docker inspect "$CONTAINER" >/dev/null 2>&1 || { echo "[ERROR] 找不到容器 $CONTAINER" >&2; exit 1; }

db() { docker exec -i "$CONTAINER" sh -c 'exec mysql --default-character-set=utf8mb4 -N -B -uroot -p"$MYSQL_ROOT_PASSWORD" -D fsd_core' <<<"$1"; }
db_file() { docker exec -i "$CONTAINER" sh -c 'exec mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" -D fsd_core' < "$1"; }
db_stdin() { docker exec -i "$CONTAINER" sh -c 'exec mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" -D fsd_core'; }
db_sql() { docker exec -i "$CONTAINER" sh -c 'exec mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" -D fsd_core' <<<"$1"; }
# 备份走 mysqldump：手搓 INSERT 会漏列（polyline_geojson / road_class 都是 NOT NULL 或有默认值），
# 漏了就没有可用的回退。--replace 让回退时对已存在的行做 REPLACE，不用先删。
# 密码经 MYSQL_PWD 环境变量传给容器内进程，不出现在 argv 里。
dump_tables() {
  docker exec -i "$CONTAINER" sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot \
      --no-create-info --complete-insert --replace --skip-extended-insert fsd_core "$@"' sh "$@"
}

counts() {
  db "SELECT CONCAT('ACTIVE nodes=', SUM(status='ACTIVE'), ' ACTIVE segments=',
        (SELECT COUNT(*) FROM t_road_segment WHERE status='ACTIVE' AND deleted=0))
      FROM t_road_node WHERE deleted=0;" | tr -d '\r'
}

if [ "$MODE" = "restore" ]; then
  [ -f "$GRID_BACKUP" ] || { echo "[ERROR] 没有备份 $GRID_BACKUP" >&2; exit 1; }
  echo "=== 回退到 RN* 网格：$GRID_BACKUP ==="
  # 扩范围图整行删掉而不是留着 DISABLED：back/sql/seed/zjf_geo.sql 里没有 OSM* 行，
  # 留着会让"本地库"与"全新初始化 + seed"不等价，scripts/dev/verify-geo-init-paths.sh 的指纹对不上。
  # 删了不丢东西 —— 它们能由 back/sql/seed/zjf_road_network.sql 完整重建。
  db_sql "DELETE FROM t_road_node WHERE node_code LIKE 'OSM%';
          DELETE FROM t_road_segment WHERE from_node_code LIKE 'OSM%' OR to_node_code LIKE 'OSM%';
          DELETE FROM t_road_node_component WHERE node_code LIKE 'OSM%';"
  # 老备份是 plain INSERT（会撞唯一键），统一改写成 REPLACE 再落，避免"先 DELETE"可能丢行的风险
  sed 's/^INSERT INTO/REPLACE INTO/' "$GRID_BACKUP" | db_stdin
  python "$REPO_ROOT/scripts/geo/reanchor_facilities.py" --max-snap-meters 250
  bash "$REPO_ROOT/scripts/dev/reset-demo-dispatchable.sh" --yes >/dev/null
  bash "$REPO_ROOT/scripts/dev/reset-demo-dispatchable.sh" --verify
  echo "  现状: $(counts)"
  exit 0
fi

[ -f "$NETWORK_SQL" ] || { echo "[ERROR] 没有 $NETWORK_SQL，先跑 scripts/geo/osm_to_road_graph.py" >&2; exit 1; }

echo "=== 换图前: $(counts) ==="
# 备份现役图（整表倒出，回退时用 REPLACE 落回原值）；已存在就不覆盖，避免把原始状态冲掉
mkdir -p "$STAMP_DIR"
if [ ! -f "$GRID_BACKUP" ]; then
  dump_tables t_road_node t_road_segment > "$STAMP_DIR/road_network_pre_swap.sql"
  echo "  现役图已备份: data/backup/road_network_pre_swap.sql（$(wc -l < "$STAMP_DIR/road_network_pre_swap.sql") 行）"
fi

db_sql "UPDATE t_road_node SET status='DISABLED' WHERE deleted=0;
        UPDATE t_road_segment SET status='DISABLED' WHERE deleted=0;"
db_file "$NETWORK_SQL"

echo "=== 重吸附设施到最大连通分量 ==="
python "$REPO_ROOT/scripts/geo/reanchor_facilities.py" --max-snap-meters 250

echo "=== 重铺车辆位置（--verify 不写库，必须先真跑一次重置，否则车还停在旧图的像素上）==="
bash "$REPO_ROOT/scripts/dev/reset-demo-dispatchable.sh" --yes >/dev/null

echo "=== 可达率验证 ==="
bash "$REPO_ROOT/scripts/dev/reset-demo-dispatchable.sh" --verify
echo "  换图后: $(counts)"
