#!/bin/bash
# 数据态前后照：待派单积压、任务池状态、车辆忙闲。
# 为什么要有它：本仓库的 CPU/p95 数字强烈依赖积压规模（全池扫一类成本随积压线性涨），
# 没有这一份"当次数据态"，任何档位读数都不能跨轮比较。
# 用法：bash scripts/perf/db-state.sh [标签]     容器名可用 DF_MYSQL_CONTAINER 覆盖
set -u
LABEL="${1:-state}"
MYSQL_C="${DF_MYSQL_CONTAINER:-fsd-mysql}"

cat > /tmp/df-db-state.sql <<'SQL'
SELECT 'order_WAITING_DISPATCH', COUNT(*) FROM fsd_core.t_order WHERE deleted=0 AND status='WAITING_DISPATCH'
UNION ALL SELECT 'order_active',        COUNT(*) FROM fsd_core.t_order   WHERE deleted=0 AND status IN ('DISPATCHED','IN_PROGRESS')
UNION ALL SELECT 'task_PENDING',        COUNT(*) FROM fsd_core.t_dispatch_task WHERE deleted=0 AND status='PENDING'
UNION ALL SELECT 'task_ASSIGNING',      COUNT(*) FROM fsd_core.t_dispatch_task WHERE deleted=0 AND status='ASSIGNING'
UNION ALL SELECT 'task_MANUAL_PENDING', COUNT(*) FROM fsd_core.t_dispatch_task WHERE deleted=0 AND status='MANUAL_PENDING'
UNION ALL SELECT 'task_ASSIGNED',       COUNT(*) FROM fsd_core.t_dispatch_task WHERE deleted=0 AND status='ASSIGNED'
UNION ALL SELECT 'task_EXECUTING',      COUNT(*) FROM fsd_core.t_dispatch_task WHERE deleted=0 AND status='EXECUTING'
UNION ALL SELECT 'vehicle_IDLE',        COUNT(*) FROM fsd_core.t_vehicle WHERE deleted=0 AND dispatch_status='IDLE'
UNION ALL SELECT 'vehicle_BUSY',        COUNT(*) FROM fsd_core.t_vehicle WHERE deleted=0 AND dispatch_status='BUSY'
UNION ALL SELECT 't_order_rows',        COUNT(*) FROM fsd_core.t_order;
SQL

echo "--- data state: $LABEL  $(date '+%H:%M:%S')"
docker exec -i "$MYSQL_C" mysql -N -B -uroot -proot < /tmp/df-db-state.sql 2>/dev/null
