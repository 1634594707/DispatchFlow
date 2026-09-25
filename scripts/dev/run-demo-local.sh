#!/bin/bash
# =====================================================================
# M0 / T0-b 演示启动清单（倍速以环境变量固化，不留"口头设置"）
#
#   bash scripts/dev/run-demo-local.sh              # 用定档值起后端
#   DEMO_TICK_MS=1000 bash scripts/dev/run-demo-local.sh   # 临时回到 1× 对照
#
# 为什么倍速只拨 tick：行驶 / 充电 / 换电 / 扣电全部按 **tick 计数**（不是墙钟 dt）——
# `ParkPilotSimulationServiceImpl.java:877` 每 tick 走 `vehicleSpeedPxPerSecond` 个像素的**定步长**。
# 所以缩 tick = 全世界等比加速、物理自洽；而单独把车速调快会让"跑得快但耗电不变"，
# 因为扣电挂在 tick 上不随速度变。**先缩 tick，观感还不够快才动车速。**
#
# 实测三档的墙钟与节拍见《演示与配置优化任务路线图》§9.1（T0-a）。
# =====================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

# 定档值（T0-b）＝ **500 ms（2×）**，不是 T0-a 一开始推荐的 250 ms。
# 三档同路线实测（每档 n=1，见路线图 §9.1）：1000 ms→367 s、500 ms→157 s（2.34×）、250 ms→202 s。
# 250 ms 不比 500 ms 快，且该档实际节拍中位 874 ms（配置 250 ms）＝调度线程跟不上，
# 白烧 CPU 还叠出更多车同时抢桩。改这里之前先重跑 `bash scripts/dev/m0-gear-sweep.sh 1000 500 250`。
export FSD_PARK_SIMULATION_TICK_INTERVAL_MS="${DEMO_TICK_MS:-500}"
# 车速保持 8：T0-b 明确"8 起步，观感仍慢再 8→12"。
export FSD_PARK_VEHICLE_SPEED_PX_PER_SECOND="${DEMO_SPEED_PX_PER_SECOND:-8}"

echo "[demo] tick=${FSD_PARK_SIMULATION_TICK_INTERVAL_MS}ms  speed=${FSD_PARK_VEHICLE_SPEED_PX_PER_SECOND}px/tick"
echo "[demo] 测量/复现：bash scripts/dev/m0-gear-sweep.sh 1000 500 250"
exec bash scripts/dev/run-backend-local.sh
