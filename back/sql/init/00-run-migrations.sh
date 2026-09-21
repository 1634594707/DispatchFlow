#!/bin/bash
set -euo pipefail

# 初始化顺序（§7.5，改地图内容前先读这段）：
#   1) 本脚本：只裸跑 V01-V20，给 Flyway 铺一个非空基线；
#   2) 后端启动时 Flyway：baseline-version=20，从 V21 迁移到最新 —— 迁移目录只放 DDL；
#   3) 园区地理内容：back/sql/seed/zjf_geo.sql（按业务键幂等 upsert），
#      重新生成：bash scripts/dev/export-geo-seed.sh
#      两条初始化路径是否等价：bash scripts/dev/verify-geo-init-paths.sh
# 地图内容不要再开新的 V*.sql 迭代：V21-V47 里那 13 个纯 DML 文件就是"迁移太多 + 范围怪"的病根。

# Flyway baselines this database at V20. Only apply the schema needed for that
# baseline here; V21+ is owned by Flyway and must never run a second time.
echo "DispatchFlow: applying pre-Flyway migrations V01-V20 from /migrations ..."

for f in $(ls /migrations/V0[1-9]__*.sql /migrations/V1[0-9]__*.sql /migrations/V13b__*.sql /migrations/V20__*.sql /migrations/V20b__*.sql 2>/dev/null | sort); do
  echo ">> $(basename "$f")"
  mysql --default-character-set=utf8mb4 \
    -uroot -p"${MYSQL_ROOT_PASSWORD}" \
    "${MYSQL_DATABASE}" < "$f"
done

echo "DispatchFlow: pre-Flyway migrations complete."
