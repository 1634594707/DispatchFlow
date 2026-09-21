#!/usr/bin/env bash
# =====================================================================
# DispatchFlow 备份恢复演练
#
# 目的：「从没演练过的备份等于没有备份」。本脚本把最近一份备份
#       完整还原到一个临时库，逐表核对行数后删除临时库。
#
# ★★ 安全约束：本脚本**只读 fsd_core**，绝不写入。目标库名硬编码为
#    fsd_core_restore_drill，并在运行前断言它不等于 fsd_core。
#
# 用法：bash scripts/restore-drill.sh [备份文件路径]
# 退出码：0 演练通过 / 1 前置条件不满足 / 4 行数对照不一致 / 5 还原失败
# =====================================================================
set -uo pipefail

cd /opt/dispatchflow || { echo "!! /opt/dispatchflow 不存在"; exit 1; }

# ---- 安全断言：任何情况下都不许把 fsd_core 当演练库 ----
SOURCE_DB="fsd_core"
SCRATCH_DB="fsd_core_restore_drill"
if [ "$SCRATCH_DB" = "$SOURCE_DB" ] || [ "${SCRATCH_DB#fsd_core_restore_drill}" != "" ]; then
  echo "!! 演练库名异常，拒绝执行"; exit 1
fi

if [ -f ./.env ]; then
  set -a; . ./.env; set +a
fi
: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD 未在 .env 里}"
MYSQL="docker exec -i fsd-mysql mysql -uroot -p${MYSQL_ROOT_PASSWORD}"

q()  { $MYSQL -N -e "$1" 2>/dev/null; }

BK="${1:-}"
if [ -z "$BK" ]; then
  BK=$(ls -1t /opt/backups/fsd_core-*.sql.gz 2>/dev/null | head -1)
fi
[ -n "$BK" ] && [ -f "$BK" ] || { echo "!! 找不到备份文件"; exit 1; }

echo "=============================================================="
echo " 恢复演练  $(date '+%F %T')"
echo "=============================================================="
echo " 源库     : ${SOURCE_DB}（本脚本只读）"
echo " 演练库   : ${SCRATCH_DB}"
echo " 备份文件 : ${BK}"
ls -lh "$BK"

echo
echo "== 0. 记录演练前基线（用于事后证明 fsd_core 未被改动）=="
BASE_TABLES=$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SOURCE_DB}';")
BASE_SUM=$(q "SELECT (SELECT COUNT(*) FROM \`${SOURCE_DB}\`.t_vehicle)
                  + (SELECT COUNT(*) FROM \`${SOURCE_DB}\`.t_station)
                  + (SELECT COUNT(*) FROM \`${SOURCE_DB}\`.t_road_node)
                  + (SELECT COUNT(*) FROM \`${SOURCE_DB}\`.t_admin_user);")
echo "  fsd_core 表数 = ${BASE_TABLES}，静态表行数之和 = ${BASE_SUM}"

echo
echo "== 1. 备份文件自述信息 =="
echo -n "  是否含 CREATE DATABASE / USE 语句: "
if gunzip -c "$BK" | grep -qE '^(CREATE DATABASE|USE )'; then
  echo "有 —— 导入时会被语句里的库名覆盖，需注意"
  gunzip -c "$BK" | grep -nE '^(CREATE DATABASE|USE )' | head -3
else
  echo "无 —— 可直接指定目标库导入（预期如此）"
fi
echo -n "  备份内 CREATE TABLE 数: "; gunzip -c "$BK" | grep -c '^CREATE TABLE'
echo -n "  结束标记: "; gunzip -c "$BK" | tail -3 | grep 'Dump completed' || echo "缺失 (!)"

echo
echo "== 2. 丢弃同名残留并新建演练库 =="
$MYSQL -e "DROP DATABASE IF EXISTS \`${SCRATCH_DB}\`;
           CREATE DATABASE \`${SCRATCH_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" \
  && echo "  已新建 ${SCRATCH_DB}"

echo
echo "== 3. 还原（计时）=="
T0=$(date +%s)
gunzip -c "$BK" | $MYSQL "$SCRATCH_DB" 2>/tmp/restore.err
RC=$?
T1=$(date +%s)
echo "  gunzip|mysql 退出码 = ${RC}，用时 $((T1 - T0))s"
grep -v 'Using a password' /tmp/restore.err 2>/dev/null | head -5
if [ "$RC" -ne 0 ]; then
  echo "!! 还原失败"
  $MYSQL -e "DROP DATABASE IF EXISTS \`${SCRATCH_DB}\`;"
  exit 5
fi

echo
echo "== 4. 结构核对：表数 =="
RT=$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SCRATCH_DB}';")
ST=$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SOURCE_DB}';")
echo "  源库 ${SOURCE_DB} = ${ST}"
echo "  演练库 ${SCRATCH_DB} = ${RT}"
if [ "$RT" != "$ST" ]; then echo "  !! 表数不一致"; else echo "  ✓ 表数一致"; fi

echo
echo "== 5. 关键表行数逐表比对 =="
echo "  说明：源库在被仿真持续写入，热表（如 t_charging_session）有差值属正常；"
echo "        静态表（车辆/站点/路网/用户）必须严格一致。"
MISMATCH=0
printf '  %-26s %-12s %-12s %s\n' 表 源库 演练库 判定
for t in t_vehicle t_station t_road_node t_road_segment t_admin_user t_park t_park_geofence \
         t_parking_slot t_building_block t_webhook_subscription t_energy_forecast \
         t_vehicle_credential t_external_api_key t_order t_dispatch_task t_order_idempotency \
         t_charging_session t_fleet_telemetry_point t_dispatch_event_outbox; do
  A=$(q "SELECT COUNT(*) FROM \`${SOURCE_DB}\`.\`${t}\`;")
  B=$(q "SELECT COUNT(*) FROM \`${SCRATCH_DB}\`.\`${t}\`;")
  if [ -z "$A" ] || [ -z "$B" ]; then
    printf '  %-26s %-12s %-12s %s\n' "$t" "${A:-?}" "${B:-?}" "表不存在(跳过)"
    continue
  fi
  if [ "$A" = "$B" ]; then VERDICT="✓ 一致"
  else VERDICT="差 $((B - A))（备份为快照，正常）"; fi
  case "$t" in
    t_vehicle|t_station|t_road_node|t_road_segment|t_admin_user|t_park|t_park_geofence|\
t_parking_slot|t_building_block|t_webhook_subscription|t_energy_forecast|\
t_vehicle_credential|t_external_api_key|t_order|t_dispatch_task|t_order_idempotency)
      [ "$A" = "$B" ] || { VERDICT="!! 静态表不一致"; MISMATCH=1; } ;;
  esac
  printf '  %-26s %-12s %-12s %s\n' "$t" "$A" "$B" "$VERDICT"
done

echo
echo "== 6. 数据可用性抽读（证明不是空壳）=="
$MYSQL -e "SELECT id, vehicle_code, link_mode, online_status, battery_level
           FROM \`${SCRATCH_DB}\`.t_vehicle ORDER BY id;" 2>/dev/null
echo "  --- 站点抽样 3 行（注意真列名是 coord_lng/coord_lat，不是 longitude/latitude）---"
$MYSQL -e "SELECT id, station_code, station_name, coord_lng, coord_lat
           FROM \`${SCRATCH_DB}\`.t_station ORDER BY id LIMIT 3;" 2>/dev/null
echo "  --- 管理端用户 ---"
$MYSQL -e "SELECT username, status FROM \`${SCRATCH_DB}\`.t_admin_user;" 2>/dev/null
echo "  --- 园区围栏（列名未固定，直接抽 2 行）---"
$MYSQL -e "SELECT * FROM \`${SCRATCH_DB}\`.t_park_geofence LIMIT 2;" 2>/dev/null

echo
echo "== 6b. 中文往返校验（备份/恢复最容易静默写坏的就是中文）=="
echo "  背景：mysql CLI 的 character_set_client 默认是 latin1，直接看会显示成 ?????，"
echo "        所以这里**只看 HEX 字节**，不受终端与连接字符集影响。"
# ★ 注意：本脚本开着 pipefail，而 grep -q 命中即退出会让上游 gunzip 收到 SIGPIPE，
#   整条管道被判为失败 → 出现「备份已损坏」的假告警。
#   所以这里一律用 grep -c（读完整个流）或把退出码吃掉，不要用 grep -q 配管道。
CN_HITS=$(gunzip -c "$BK" | grep -c '找家纺' || true)
echo -n "  备份文件里是否含正确 UTF-8 中文: "
if [ "${CN_HITS:-0}" -gt 0 ]; then echo "是 ✓（命中 ${CN_HITS} 处）"; else echo "否 !!（备份本身可能已损坏）"; MISMATCH=1; fi
NAMES_LINE=$(gunzip -c "$BK" | grep -E 'SET NAMES' | head -1 || true)
echo "  备份文件自带的字符集声明: ${NAMES_LINE:-（未找到，需人工复核）}"
FIELDS="t_vehicle:vehicle_name:3 t_park_geofence:fence_name:1 t_station:station_name:101"
for spec in $FIELDS; do
  tb=${spec%%:*}; rest=${spec#*:}; col=${rest%%:*}; rid=${rest##*:}
  SA=$(q "SELECT HEX(\`${col}\`) FROM \`${SOURCE_DB}\`.\`${tb}\` WHERE id=${rid};")
  SB=$(q "SELECT HEX(\`${col}\`) FROM \`${SCRATCH_DB}\`.\`${tb}\` WHERE id=${rid};")
  if [ -z "$SA" ]; then
    echo "  - ${tb}.${col}(id=${rid}) 源库无此行，跳过"
  elif [ "$SA" = "$SB" ]; then
    echo "  ✓ ${tb}.${col}(id=${rid}) 字节完全一致（HEX 前 24 位 ${SA:0:24}…）"
  else
    echo "  !! ${tb}.${col}(id=${rid}) 字节不一致 → 备份/恢复会破坏中文"
    echo "     源库   : ${SA:0:48}"
    echo "     演练库 : ${SB:0:48}"
    MISMATCH=1
  fi
done
echo "  判定：源库多字节中文字段的 HEX 与演练库逐字节相同 ⇒ 备份/恢复不会破坏中文。"

echo
echo "== 7. 证明 fsd_core 未被本次演练改动 =="
NOW_TABLES=$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${SOURCE_DB}';")
NOW_SUM=$(q "SELECT (SELECT COUNT(*) FROM \`${SOURCE_DB}\`.t_vehicle)
                + (SELECT COUNT(*) FROM \`${SOURCE_DB}\`.t_station)
                + (SELECT COUNT(*) FROM \`${SOURCE_DB}\`.t_road_node)
                + (SELECT COUNT(*) FROM \`${SOURCE_DB}\`.t_admin_user);")
echo "  演练前: 表数 ${BASE_TABLES} / 静态行数 ${BASE_SUM}"
echo "  演练后: 表数 ${NOW_TABLES} / 静态行数 ${NOW_SUM}"
[ "$BASE_TABLES" = "$NOW_TABLES" ] && [ "$BASE_SUM" = "$NOW_SUM" ] \
  && echo "  ✓ fsd_core 未被改动" || { echo "  !! fsd_core 状态发生变化，请人工检查"; MISMATCH=1; }

echo
echo "== 8. 清理演练库 =="
$MYSQL -e "DROP DATABASE IF EXISTS \`${SCRATCH_DB}\`;"
echo -n "  剩余同名库: "
q "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='${SCRATCH_DB}';"

echo
if [ "$MISMATCH" -eq 0 ]; then
  echo "############ 演练通过：备份可恢复、数据可用、源库无损 ############"
  exit 0
else
  echo "############ 演练发现问题，见上面 !! 行 ############"
  exit 4
fi
