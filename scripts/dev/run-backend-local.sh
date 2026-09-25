#!/bin/bash
# =====================================================================
# 本机起后端（§M0 环境收口）
#
#   bash scripts/dev/run-backend-local.sh
#
# 为什么需要这个脚本：application.yml 里的主机默认值刻意是"安全的错值"
# （DB_PASSWORD=changeme、RABBITMQ_USERNAME=fsd_user/changeme、端口 5673），
# 而 back/docker-compose.yml 起的本地容器用的是另一套值。走 `docker compose up backend`
# 由 compose 注入这些变量所以没事；在宿主机直接 `mvn spring-boot:run` 就会在
# Flyway / RabbitMQ 监听器启动阶段失败。这里把两套值对齐到一处，别靠口口相传。
#
# 只面向本机容器：任何一项指向非 127.0.0.1/localhost 都会被拒绝。
#
# 为什么要先 install（2026-09-22 加，起因是一次整轮作废的压测）：
#   `mvn -pl fsd-bootstrap spring-boot:run` **不带 -am**，同级模块（fsd-dispatch / fsd-admin-api …）
#   是从 ~/.m2 里那个 SNAPSHOT jar 解析的，不是从 back/*/target/classes。实测踩过一次：
#   jar 是 20:49 装的，之后加进 fsd-dispatch 的 MapfReservationMetrics / EnergyForecastMetrics
#   根本不在 jar 里 ⇒ 起出来的后端跑的是旧代码，`/actuator/metrics` 里查不到这两个计数器，
#   而所有单元测试都绿（测试走 reactor，不受这个影响）。跑派单时延基准时这会让整轮测量作废。
#   所以这里默认先 install 一遍把 jar 刷新。急着重启可 `RUN_BACKEND_FRESH=0` 跳过。
# =====================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

export DB_HOST="${DB_HOST:-127.0.0.1}"
export DB_PORT="${DB_PORT:-3307}"
export REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
export REDIS_PORT="${REDIS_PORT:-6380}"
export RABBITMQ_HOST="${RABBITMQ_HOST:-127.0.0.1}"
# 5673 而不是 5672：back/docker-compose.yml 把容器内 5672 发布到宿主机 5673，
# application.yml 的默认值本就是照这个映射写的，不要"纠正"成 5672。
export RABBITMQ_PORT="${RABBITMQ_PORT:-5673}"

for pair in "DB_HOST=$DB_HOST" "REDIS_HOST=$REDIS_HOST" "RABBITMQ_HOST=$RABBITMQ_HOST"; do
  host="${pair#*=}"
  case "$host" in
    127.0.0.1|localhost|::1) ;;
    *) echo "[ERROR] $pair 指向非本机地址，拒绝启动本地后端" >&2; exit 1 ;;
  esac
done

# 与 back/docker-compose.yml 的容器凭据保持一致（不是生产值）
export DB_PASSWORD="${DB_PASSWORD:-root}"
export DB_USERNAME="${DB_USERNAME:-root}"
export RABBITMQ_USERNAME="${RABBITMQ_USERNAME:-guest}"
export RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:-guest}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-}"
# 与 back/docker-compose.yml 一致：本地关掉管理端鉴权与移动端下单密钥，便于冒烟派单
export FSD_ADMIN_AUTH_ENABLED="${FSD_ADMIN_AUTH_ENABLED:-false}"
export MOBILE_ORDER_REQUIRE_KEY="${MOBILE_ORDER_REQUIRE_KEY:-false}"
# ⚠ 光有上面那行**不够**：`validateMobileOrderKey` 要同时满足 require-api-key=false
#   与 unsafe-no-auth=true 才放行，而后者以前读的是 JVM 系统属性 ⇒ 环境变量拨不动它，
#   手机下单页因此在本地和线上都拿不到站点（全回 MOBILE_ORDER_KEY_REQUIRED）。
#   现在它改成了 Spring 属性，这一行才是那句注释承诺的行为。**只对本机默认开。**
export FSD_MOBILE_ORDER_UNSAFE_NO_AUTH="${FSD_MOBILE_ORDER_UNSAFE_NO_AUTH:-true}"

missing=()
# Redis 不在这张名单里：它由下面的 127.0.0.1:$REDIS_PORT 探针判定。按容器名判定会把
# "fsd-redis 由别的 compose 项目带起、没发布宿主机端口"这种真实可用的配置（改用
# fsd-redis-localdev，见下方提示）挡在门外 —— 而名字在、端口不在时它也挡不住。
for container in fsd-mysql fsd-rabbitmq; do
  if [ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null || echo false)" != "true" ]; then
    missing+=("$container")
  fi
done
if [ "${#missing[@]}" -gt 0 ]; then
  echo "[ERROR] 以下本地容器未运行：${missing[*]}" >&2
  echo "        先执行：docker compose -f back/docker-compose.yml up -d mysql redis rabbitmq" >&2
  exit 1
fi

# 容器在跑但宿主机端口没发布，是另一类失败：后端连不上 Redis 时只会在调度线程里
# 反复抛 RedisConnectionException，派单看起来"静默失败"，很难归因。
if ! (exec 3<>"/dev/tcp/127.0.0.1/$REDIS_PORT") 2>/dev/null; then
  echo "[ERROR] 127.0.0.1:$REDIS_PORT 上没有 Redis。" >&2
  echo "        若 fsd-redis 由别的 compose 项目起过（没发布端口），用下面这条补一个只监听本机的：" >&2
  echo "        docker run -d --name fsd-redis-localdev -p 127.0.0.1:$REDIS_PORT:6379 redis:7.4" >&2
  exit 1
fi

echo "本机后端启动参数（口令按本地 compose 注入，不打印值）："
printf '  %s\n' \
  "DB=$DB_HOST:$DB_PORT/fsd_core  Redis=$REDIS_HOST:$REDIS_PORT  RabbitMQ=$RABBITMQ_HOST:$RABBITMQ_PORT" \
  "FSD_ADMIN_AUTH_ENABLED=$FSD_ADMIN_AUTH_ENABLED  MOBILE_ORDER_REQUIRE_KEY=$MOBILE_ORDER_REQUIRE_KEY"

# 兄弟 jar 陈旧检查：任一模块 src/ 比 ~/.m2 里那个 jar 新，就说明"起出来的后端不是当前代码"。
# 只做提醒不做判断依据 —— 真正保证新鲜的是下面那次 install。
stale=""
for mod in fsd-common fsd-order fsd-vehicle fsd-dispatch fsd-admin-api; do
  jar="$HOME/.m2/repository/com/fsd/$mod/0.1.0-SNAPSHOT/$mod-0.1.0-SNAPSHOT.jar"
  [ -f "$jar" ] || { stale="$stale $mod(未装)"; continue; }
  newer="$(find "$REPO_ROOT/back/$mod/src" -newer "$jar" -name '*.java' -print -quit 2>/dev/null || true)"
  [ -n "$newer" ] && stale="$stale $mod"
done
if [ -n "$stale" ] && [ "${RUN_BACKEND_FRESH:-1}" != "0" ]; then
  echo "  [i] 以下模块的源码比 ~/.m2 的 jar 新：$stale —— 先 install 刷新（RUN_BACKEND_FRESH=0 可跳过）"
  (cd "$REPO_ROOT/back" && mvn -B -q -DskipTests -pl fsd-bootstrap -am install)
elif [ -n "$stale" ]; then
  echo "  [WARN] jar 陈旧但 RUN_BACKEND_FRESH=0：起出来的后端可能不是当前代码 —— $stale" >&2
fi

cd "$REPO_ROOT/back"
exec mvn -B -pl fsd-bootstrap spring-boot:run
