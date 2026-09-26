#!/usr/bin/env bash
# 压测库基线：裸跑 V01–V20，给后端 Flyway（baseline-version=20）铺一个非空基线 —— §7.5 的路径 A。
#
# 为什么不直接让 Flyway 从 V01 跑：application.yml 钉了 `baseline-version: 20` +
# `baseline-on-migrate: true`，空库起来会被 baseline 到 20，V01–V20 永远不会被重放，
# 于是表都不存在。`back/sql/init/00-run-migrations.sh` 就是为此而生的那份共享脚本，
# 这里复用它（不复制它的文件挑选规则，避免两处清单漂移）。
#
# 复用要付的代价：那份脚本里的 mysql 调用连的是**本地 socket**（compose 场景下它就在
# mysql 容器里跑）。本 Job 在另一个容器，所以用 PATH 前置一个 mysql shim 补 -h/-P，
# 而不是去改那份被 CONTRIBUTING/back-README 引用着的脚本。
set -euo pipefail

DB_HOST="${DB_HOST:-mysql}"
DB_PORT="${DB_PORT:-3306}"
MYSQL_DATABASE="${MYSQL_DATABASE:-fsd_core}"

# 1) 等 MySQL 可连。Job 建得比 Service endpoints 就绪还早，不重试就是假红。
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

# 2) 已经基线过就跳过：让 run-perf.sh 能对着热 PVC 重跑。
#    探针选 t_park（V04 建的），它同时是 seed 的第一依赖 —— 这张表在，V01–V20 就在。
exists=$(mysql -N -B -h "$DB_HOST" -P "$DB_PORT" -uroot -p"$MYSQL_ROOT_PASSWORD" -D "$MYSQL_DATABASE" -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='t_park'")
if [ "$exists" != "0" ]; then
  tables=$(mysql -N -B -h "$DB_HOST" -P "$DB_PORT" -uroot -p"$MYSQL_ROOT_PASSWORD" -D "$MYSQL_DATABASE" -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")
  echo "  [skip] $MYSQL_DATABASE 已有 t_park（全库 $tables 张表），V01–V20 不重跑"
  exit 0
fi

# 3) shim：只改主机/端口，其余参数照旧由脚本自己传
mkdir -p /opt/shim
printf '#!/bin/sh\nexec /usr/bin/mysql -h "%s" -P "%s" "$@"\n' "$DB_HOST" "$DB_PORT" > /opt/shim/mysql
chmod 755 /opt/shim/mysql

# 4) 那份共享脚本写死了 /migrations 这个目录（compose 里是把 sql/migrations 挂到那儿）。
#    本镜像里内容在 /sql/migrations ⇒ 建一个软链，而不是去改脚本里的路径。
#    这一步必须显式检查：脚本用 `ls /migrations/V0*__*.sql 2>/dev/null` 取文件，
#    目录空的话 it 一个文件都不跑、照样打印 "complete" 并退 0 —— 实测就是这样静默空跑了一轮，
#    靠第 6 步的表数守卫才被抓出来。
if [ ! -f /sql/migrations/V01__init_schema.sql ]; then
  echo "  [FAIL] /sql/migrations 里没有 V01__init_schema.sql —— 镜像内容不对或挂载是空的" >&2
  exit 1
fi
ln -sfn /sql/migrations /migrations
picked=$(ls /migrations/V0[1-9]__*.sql /migrations/V1[0-9]__*.sql /migrations/V13b__*.sql \
  /migrations/V20__*.sql /migrations/V20b__*.sql 2>/dev/null | wc -l | tr -d ' ')
if [ "${picked:-0}" -lt 20 ]; then
  echo "  [FAIL] V01–V20 只匹配到 ${picked:-0} 个文件（应 ≥20），基线不会成立" >&2
  exit 1
fi
echo "  基线目标：$DB_HOST:$DB_PORT/$MYSQL_DATABASE，待跑 $picked 个基线文件"
PATH="/opt/shim:/usr/local/bin:/usr/bin:/bin" bash /sql/init/00-run-migrations.sh

# 5) 收尾自证：基线到底留下了多少张表（0 张的话上面会报错，但"报了错还退 0"是仪表自己的坑）
after=$(mysql -N -B -h "$DB_HOST" -P "$DB_PORT" -uroot -p"$MYSQL_ROOT_PASSWORD" -D "$MYSQL_DATABASE" -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")
echo "  基线后表数量：$after"
if [ "${after:-0}" -lt 20 ]; then
  echo "  [FAIL] 基线后只有 $after 张表（V01–V20 应给出 20+），后面的 Flyway/seed 都会踩空" >&2
  exit 1
fi
