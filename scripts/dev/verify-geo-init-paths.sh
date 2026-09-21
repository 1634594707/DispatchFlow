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
cleanup() { docker rm -f "$PROBE" >/dev/null 2>&1; rm -f "$FP_SQL"; }
trap cleanup EXIT

case "${MYSQL_HOST:-127.0.0.1}" in
  127.0.0.1|localhost|::1) ;;
  *) echo "[ERROR] 本脚本只对本机容器做实验" >&2; exit 1 ;;
esac

# 只看业务列；改地图后这些指纹会变，两条路径必须一起变
cat > "$FP_SQL" <<'SQL'
SET SESSION group_concat_max_len = 67108864;
SELECT 'fence' k, COUNT(*) n, IFNULL(MD5(GROUP_CONCAT(CONCAT(fence_code,'|',status,'|',IFNULL(polygon_json,''),'|',IFNULL(response_level,''),'|',IFNULL(fence_type,'')) ORDER BY fence_code SEPARATOR '#')),'-') fp FROM t_park_geofence WHERE deleted=0
UNION ALL SELECT 'station', COUNT(*), IFNULL(MD5(GROUP_CONCAT(CONCAT(station_code,'|',station_name,'|',station_type,'|',IFNULL(coord_lng,''),'|',IFNULL(coord_lat,''),'|',IFNULL(anchor_node_code,''),'|',status,'|',IFNULL(delivery_zone,'')) ORDER BY station_code SEPARATOR '#')),'-') FROM t_station WHERE deleted=0
UNION ALL SELECT 'node', COUNT(*), IFNULL(MD5(GROUP_CONCAT(CONCAT(node_code,'|',IFNULL(coord_x,''),'|',IFNULL(coord_y,''),'|',IFNULL(coord_lng,''),'|',IFNULL(coord_lat,''),'|',status) ORDER BY node_code SEPARATOR '#')),'-') FROM t_road_node WHERE deleted=0
UNION ALL SELECT 'segment', COUNT(*), IFNULL(MD5(GROUP_CONCAT(CONCAT(from_node_code,'>',to_node_code,'|',IFNULL(speed_limit_kmh,''),'|',status,'|',direction,'|',IFNULL(access_state,'')) ORDER BY from_node_code,to_node_code SEPARATOR '#')),'-') FROM t_road_segment WHERE deleted=0
UNION ALL SELECT 'slot', COUNT(*), IFNULL(MD5(GROUP_CONCAT(CONCAT(slot_code,'|',IFNULL(slot_type,''),'|',status,'|',IFNULL(entry_node_code,'')) ORDER BY slot_code SEPARATOR '#')),'-') FROM t_parking_slot WHERE deleted=0
UNION ALL SELECT 'pile', COUNT(*), IFNULL(MD5(GROUP_CONCAT(CONCAT(pile_code,'|',status,'|',IFNULL(entry_node_code,'')) ORDER BY pile_code SEPARATOR '#')),'-') FROM t_charging_pile WHERE deleted=0
UNION ALL SELECT 'building', COUNT(*), IFNULL(MD5(GROUP_CONCAT(CONCAT(block_code,'|',status) ORDER BY block_code SEPARATOR '#')),'-') FROM t_building_block WHERE deleted=0
UNION ALL SELECT 'park', COUNT(*), IFNULL(MD5(GROUP_CONCAT(CONCAT(park_code,'|',park_name,'|',IFNULL(anchor_lng,''),'|',IFNULL(map_width,'')) ORDER BY park_code SEPARATOR '#')),'-') FROM t_park WHERE deleted=0;
SQL

run_fp() { # $1=container
  docker exec -i "$1" sh -c 'exec mysql --default-character-set=utf8mb4 -N -uroot -p"$MYSQL_ROOT_PASSWORD" -D fsd_core' < "$FP_SQL" 2>/dev/null
}

seed_to() { # $1=container
  docker exec -i "$1" sh -c 'exec mysql --default-character-set=utf8mb4 -uroot -proot -D fsd_core' \
    < "$REPO_ROOT/back/sql/seed/zjf_geo.sql"
}

echo "[1/4] 路径 B：已有库 $LIVE 应用 seed ..."
if ! seed_to "$LIVE" > /tmp/geo-live-seed.log 2>&1; then
  echo "  [FAIL] 已有库应用 seed 报错："; grep -av "Using a password" /tmp/geo-live-seed.log | head -3; exit 1
fi
run_fp "$LIVE" > /tmp/geo-fp-live.txt

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
if [ "$live_lines" -lt 8 ] || [ "$probe_lines" -lt 8 ]; then
  echo "  [FAIL] 指纹行数 live=$live_lines probe=$probe_lines，比对无效（拒绝把空结果当通过）"; exit 1
fi
paste /tmp/geo-fp-live.txt /tmp/geo-fp-probe.txt | awk '{printf "  %-9s live=%-4s probe=%-4s %s\n",$1,$2,$5,($3==$6?"same":"DIFF")}'
if diff -q /tmp/geo-fp-live.txt /tmp/geo-fp-probe.txt > /dev/null; then
  echo "[OK] 两条路径地理当前态一致（seed 为唯一内容来源）"
else
  echo "[FAIL] 两条路径不一致，seed 或迁移路径有问题"; diff /tmp/geo-fp-live.txt /tmp/geo-fp-probe.txt; exit 1
fi
