#!/bin/bash
# =====================================================================
# M3 仿真实验台：一条命令产出带 95% 置信区间的指标表（§5 / §8 M3 闸门）
#
#   bash scripts/dev/scenario-bench.sh [重复次数，默认 12]
#
# 为什么要有它：仓库里没有真车（§0.2），所以"某个算法改进有没有收益"只能来自可复现的仿真；
# 而 §0.1 已经证明单次取样的点值方差大到不能当证据。所以这里强制 N 次重复 + 置信区间，
# 并把假设清单随报告一起落盘。
#
# 不需要后端、不需要数据库：跑的是纯函数决策内核（com.fsd.dispatch.core.RulePolicy），
# 与生产选车用的是同一份实现。
#
# 产出：reports/scenario-bench/m-tier-bench.md
# 退出码非 0 = 有断言失败（含"同种子必须逐数字一致"这条可复现守卫）。
# =====================================================================
set -euo pipefail

REPEATS="${1:-12}"
cd "$(dirname "$0")/../.."

if [[ ! "$REPEATS" =~ ^[0-9]+$ ]] || (( REPEATS < 2 )); then
  echo "重复次数必须是 >=2 的整数（置信区间至少要有 2 个样本）" >&2
  exit 2
fi

# 注意：这里刻意不加 `|| true`。set -e + pipefail 会让 mvn 的失败码传出来，
# 否则就是"测试红了但脚本绿了"的假通过（本仓库踩过一次）。
mvn -B -f back/pom.xml -pl fsd-dispatch -am -o test \
    -Dtest=ScenarioBenchTest \
    -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false \
    -Dbench.repeats="$REPEATS" 2>&1 | grep -aE 'Tests run:|BUILD|  [a-z0-9_]+ +-[0-9]|  [a-z0-9_]+ +[0-9]|scenario-bench'

REPORT="reports/scenario-bench/m-tier-bench.md"
if [[ ! -s "$REPORT" ]]; then
  echo "报告没写出来：$REPORT" >&2
  exit 1
fi
echo
echo "==> $REPORT"
