#!/usr/bin/env bash
# 把 back/sql/seed 的园区地理当前态灌进压测库。
#
# 两件事必须先钉死，否则这个 Job 会给出假绿：
#   1) **顺序**：seed 之间有依赖（t_park → 其余；t_road_node → t_road_segment；
#      t_parking_slot → t_charging_pile）。顺序的唯一事实来源是 GEO_SEEDS 数组
#      （scripts/dev/verify-geo-init-paths.sh），由 run-perf.sh 抽出来塞进 ConfigMap
#      `seed-order` 的 key `order`，本脚本只照它执行 —— 不在这里再抄一份清单。
#   2) **时机**：必须在后端 Flyway 跑完之后。V64 给订单加了 pickup_lng 等列，seed 里
#      那些列名要是先进去，报错是 1054 Unknown column，看起来像 seed 写坏了。
#      所以这里对着镜像里的 /sql/migrations 反查"最新迁移号"，和 flyway_schema_history
#      的末条比；不等就 FAIL，绝不"先灌了再说"。
set -euo pipefail

DB_HOST="${DB_HOST:-mysql}"
DB_PORT="${DB_PORT:-3306}"
MYSQL_DATABASE="${MYSQL_DATABASE:-fsd_core}"

mysql_q() {  # 只跑一条查询，-N -B 便于进变量
  mysql -N -B --default-character-set=utf8mb4 \
    -h "$DB_HOST" -P "$DB_PORT" -uroot -p"$MYSQL_ROOT_PASSWORD" -D "$MYSQL_DATABASE" -e "$1"
}

# 1) 等 MySQL 可连
ready=0
for _ in $(seq 1 60); do
  if mysqladmin ping -h "$DB_HOST" -P "$DB_PORT" -uroot -p"$MYSQL_ROOT_PASSWORD" --silent >/dev/null 2>&1; then
    ready=1; break
  fi
  sleep 3
done
if [ "$ready" != 1 ]; then
  echo "  [FAIL] 等 $DB_HOST:$DB_PORT 就绪超时（60×3s）" >&2
  exit 1
fi

# 2) Flyway 必须已到最新迁移
if [ -z "${SEED_ORDER:-}" ]; then
  echo "  [FAIL] SEED_ORDER 为空：ConfigMap seed-order/key order 没挂上，拒绝自己猜顺序" >&2
  exit 1
fi
want=$(for f in /sql/migrations/V*__*.sql; do basename "$f"; done | grep -oE '^V[0-9]+__' | tr -d 'V_' | sort -n | tail -1)
have=$(mysql_q "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1" 2>/dev/null || true)
if [ -z "${have:-}" ]; then
  echo "  [FAIL] flyway_schema_history 里没有版本 —— 后端 Flyway 还没跑过（FLYWAY_ENABLED?）" >&2
  exit 1
fi
# have 可能是 '13b' 这类 Flyway 认不出的号（本仓没有，但别拿假设当断言）：只比数字前缀
have_num=$(printf '%s' "$have" | grep -oE '^[0-9]+' || echo 0)
if [ "$have_num" -lt "$want" ]; then
  echo "  [FAIL] 迁移只到 V$have（镜像里最新是 V$want），seed 会往缺列的表里写" >&2
  exit 1
fi
echo "  迁移已到 V$have（镜像最新 V$want）—— 可以灌 seed"

# 3) 逐份应用。走 stdin 而不是 -e：SQL 里有中文注释和多语句，-e 既受 ARG_MAX 限制又会把
#    分号当语句边界咬错。--default-character-set=utf8mb4 不能省，否则中文注释/围栏名
#    按 GBK 解出来是 '????'，把内容问题伪装成"数据坏了"。
for f in $SEED_ORDER; do
  path="/sql/seed/$f"
  if [ ! -f "$path" ]; then
    echo "  [FAIL] 镜像里没有 $path" >&2
    exit 1
  fi
  if ! mysql --default-character-set=utf8mb4 -h "$DB_HOST" -P "$DB_PORT" \
       -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" < "$path"; then
    echo "  [FAIL] 应用 $f 出错" >&2
    exit 1
  fi
  echo "  [ok] $f"
done

# 4) 灌完自证：这几张表的行数就是后端能不能派单、k6 能不能取到坐标的前提。
#    任一条为 0 直接 FAIL —— "命令跑完了"不等于"地图起来了"。
#    标签里**不能有空格**：下面的 awk 取的是第二列，标签带空格的话 $2 就成了标签的尾词，
#    零值检查会永远数不到 0（一条自己不会红的仪表）。
census_sql="
SELECT 't_park', COUNT(*) FROM t_park WHERE deleted=0
UNION ALL SELECT 'fence_ZJF-ZONE_ACTIVE', COUNT(*) FROM t_park_geofence WHERE deleted=0 AND status='ACTIVE' AND fence_code LIKE 'ZJF-ZONE-%'
UNION ALL SELECT 't_road_node', COUNT(*) FROM t_road_node WHERE deleted=0
UNION ALL SELECT 'station_ACTIVE', COUNT(*) FROM t_station WHERE deleted=0 AND status='ACTIVE'
UNION ALL SELECT 'station_SWAP_ACTIVE', COUNT(*) FROM t_station WHERE deleted=0 AND status='ACTIVE' AND station_type='SWAP_CABINET'
UNION ALL SELECT 'station_CHARGING_ACTIVE', COUNT(*) FROM t_station WHERE deleted=0 AND status='ACTIVE' AND station_type='CHARGING_STATION'
UNION ALL SELECT 'slot_STANDBY', COUNT(*) FROM t_parking_slot WHERE deleted=0 AND slot_type='STANDBY'
UNION ALL SELECT 't_charging_pile', COUNT(*) FROM t_charging_pile WHERE deleted=0
UNION ALL SELECT 't_battery_swap_cabinet', COUNT(*) FROM t_battery_swap_cabinet WHERE deleted=0"
mysql_q "$census_sql" | sed 's/^/  /'
zero=$(mysql_q "$census_sql" | awk '$2+0==0 {c++} END{print c+0}')
if [ "${zero:-1}" != "0" ]; then
  echo "  [FAIL] 上面有 $zero 项为 0 —— 地理内容没起来，别把这轮当成可用环境" >&2
  exit 1
fi

# 5) 一条会被"指纹相同"掩盖的差异，必须显式说出来：冷库里 GEO_POINT 站数为 0。
#    它是 OrderEndpointResolver 在**下单时**才落库的路网落点，不在任何 seed 里
#    （线上那 8 个是历史订单跑出来的）。所以演示模式/取送货点必须靠坐标下单，
#    k6 的 OD 对也不能从 GEO_POINT 里取 —— 冷启动时那里是空的。
geo=$(mysql_q "SELECT COUNT(*) FROM t_station WHERE deleted=0 AND station_type='GEO_POINT'")
echo "  [note] GEO_POINT 落点数 = $geo（冷库应为 0；下单一次后才会出现）"
