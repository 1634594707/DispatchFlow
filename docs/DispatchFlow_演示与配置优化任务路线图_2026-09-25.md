# DispatchFlow 演示与配置优化任务路线图（2026-09-25）

> 状态：**M0–M5 已全部完成并上生产**（§6 五项已于 2026-09-25 裁定，见 §6.1；逐项验收见 §10，部署取证见 §11）。
> 本文件仍是唯一的执行清单：完成项只留"一行结论 + 指回本文档内的证据节"，未做项才展开写。
> 纪律：每项任务给"证据出处 → 改动点 → 验收闸门（可执行命令/可观察断言）→ 范围外"。闸门未过不许勾。
> 常数口径标注：**[业务]**=本人提供的车辆事实、**[库]**=仓库实测现值、**[模型]**=均匀需求模型值不是实测。

---

## §0 基线（开工前钉死，所有任务引用以此为准）

### 0.1 车辆与补能 [业务]（新石器 L4）

| 事实 | 值 | 对外口径 |
| --- | --- | --- |
| 最长续航 | 200 km | 不引用 |
| X3 满载续航 | ≈180 km | **对外用 180** |
| 一次充电 | ≈2 h | 物理口径；演示节奏见 §0.3 |
| 防爆换电柜 | 35 个 | 已落库（`zjf_swap_cabinets.sql`，§0.2） |
| 快速换电 | 30 s | 对外口径（车端自动完成，无人力） |
| RMS 补能策略 | **<30% 自动回母港补能** | 本路线图配置对齐的锚 |

### 0.2 设施与范围现值 [库]（回读活库口径，2026-09-24 起）

| 量 | 现值 |
| --- | --- |
| 可下单面积（受理判据下） | **47.18 km²**，围栏 **1 片** `ZJF-ZONE-SVC-01`（60 顶点），13 片旧 `ZJF-ZONE-*` DISABLED |
| 展示包络 | `DEFAULT-BOUNDARY`，名字已改"展示包络（不参与受理判据）"，**不参与受理** |
| 围栏内吸附成功率 | **85.1%**（闸门 ≥95% **未达**，缺口 ≈28–29 个洞、7.33 km²） |
| ACTIVE 设施 | 1 总仓库（`ZJF-IDLE-01`＝基地点 121.080681,31.960337）+ 35 换电柜 + 6 充电桩；35 柜覆盖：中位 572 m / P90 855 m / 最远 1,210 m，100% ≤1.5 km |
| 车队 | **20 台** `ZJF-AV-01..20`，`vehicle_type='L4_DELIVERY'`，启动时 `ensurePilotFleet()` 补建（`ParkPilotSimulationServiceImpl.java:161-193`），初始电量 80–100 **只在首次创建时随机** |
| 生产 | 设施 v2 + 匿名下单（密钥两开关只在服务器 .env）；域名 aplicity.online（admin. 返回 410） |

### 0.3 仿真/能量常数现值 [库]（本路线图改动前的实测）

| 常数 | 现值 | 出处 |
| --- | --- | --- |
| tick 间隔 | yml 默认 **1000 ms**（未改），键 `fsd.park.simulation.tick-interval-ms`；**演示档已在 compose / 启动脚本里钉成 500 ms**（T0-b，实测定档） | `ParkPilotSimulationServiceImpl.java:210`、`docker-compose.prod.yml`、`scripts/dev/run-demo-local.sh` |
| 车速 | 8 px/s（env `FSD_PARK_VEHICLE_SPEED_PX_PER_SECOND`），折 GCJ ≈39.8 m/tick | `application.yml:105`、`:1029-1056` |
| 每 1% SOC 行驶 | **1,800 m**（＝满电 180 km，✅ 与 [业务] 对齐） | `FleetEnergyProperties.java:49`、yml:393 |
| 派单门槛（能量侧） | `minAssignableSoc=30` ✅ | yml:395、`FleetEnergyProperties.java`；**生效值还要过一层 DB 档案**（见 §3.1） |
| 派单门槛（仿真侧） | ~~25 ❌ 分叉~~ **yml 默认已改 30**（T1-a）；但该键**无读取方**，是死键 | `application.yml:379`、`ParkPilotProperties.java:218` |
| 回补触发线 | ~~20 ❌ 与 RMS 30% 差 10pp~~ **已改 30 ✅**（T1-b，Q1 裁定对齐 RMS） | yml:394、`FleetEnergyProperties.returnToChargeThreshold` |
| 低电量展示线 | ~~20~~ **已改 30**（与回补线取齐；前端两处 `LOW_SOC_THRESHOLD` 本就是 30） | yml:393、`useChargingAwareness.ts:9`、`usePredictiveAlert.ts:16` |
| 充电速率（运行时） | +4 %/tick ⇒ 20→90 ≈ **17.5 s**（演示节奏，非物理 2 h） | yml:377/389 |
| 换电时长（运行时） | `swapDurationTicks=5` ≈ **5 s**（seed `avg_service_seconds='30'` 运行时不消费） | `FleetEnergyProperties.java:60` |
| 补满线 | `chargeCompleteSoc=90` | `FleetEnergyProperties.java:34` |
| 倍速开关 | **不存在**（全仓 grep `speedMultiplier|倍速` 零匹配） | 2026-09-25 核实 |
| 移动端位置更新 | 3 s HTTP 轮询（失败退避封顶 30 s）；SSE 仅 PC 端 | `ParkOrder.vue:510-526`、`dispatchStream.ts` |

### 0.4 产能引用纪律 [模型]
单趟/吞吐类数字一律引 `scripts/geo/trip_mileage_sampler.py` 现行值：**中位单趟 14,595 m、换电 1.10 单/车·h、同吞吐 2.51× 车队（−60%）**；这是均匀需求模型值，本机 996 单坐标全空，**无从按真实需求加权**。面积只说 **47.18**，不说 55/66/32.2。

---

## §1 任务总表（依赖与顺序）

| 阶段 | 任务 | 依赖 | 等级 | 改动面 |
| --- | --- | --- | --- | --- |
| M0 | 演示倍速定档 | 无 | P0 | 纯配置 |
| M1 | 车辆配置与 RMS 口径对齐 | Q1 定案 | P0 | 一行 yml + 可能一行 |
| M2 | 手机端展示补强 | M1（换电柜图层讲故事用 30% 口径） | P1/P2 | 前端 4 项 |
| M3 | 服务范围收尾 | Q3 定案 | P1 | 文案/过滤，不动几何 |
| M4 | 车辆规格对外展示文案 | 无 | P2 | 前端静态 |
| M5 | 演示彩排与预案 | M0–M2 | P0（演示前必做） | 操作脚本，不改码 |

```
M0 ──┐
M1 ──┼─→ M5（彩排：全流程计时 + 断点预案）
M2 ──┤
M3 ──┘   M4 独立，可插队
```

---

## §2 M0 演示倍速（P0，纯配置零代码）

**背景**：演示嫌慢。所有节奏都按 tick 计（行驶/充电/换电/扣电），所以缩 tick ＝ 全世界等比加速，物理自洽；仓库没有现成倍速开关。

### T0-a 倍速两档实测
- 改动点：不改码，起后端时加环境变量 `FSD_PARK_SIMULATION_TICK_INTERVAL_MS=250`（4×）与 `=500`（2×）各跑一轨（Spring 对 `${fsd.park.simulation.tick-interval-ms}` 做 relaxed 绑定）。
- 观测项（每档记三个数）：① 一单"下单→送达"全程墙钟时长；② 追踪地图上车每 3 s 跳变格是否连贯（3 s 轮询是固定观察粒度）；③ tick 线程是否追得上（`@Scheduled fixedDelay` 下看后端日志节拍，若 250 ms 档实际间隔明显 >250 ms 即超载）。
- **闸门**：两档各一条真实订单走完且拒单原因为空；记录表填进本文档 §9。

### T0-b 定档与车速微调
- 推荐默认：tick=250 ms；车速 `FSD_PARK_VEHICLE_SPEED_PX_PER_SECOND` 保持 8 起步，观感仍慢再 8→12。
- 代价声明（写进讲解口径）：单独调车速会失真——扣电按 tick 不按里程速度，车变快而耗电不变 ⇒ **优先缩 tick，其次才动车速**。
- **闸门**：`docker-compose`/启动脚本里倍速以环境变量固化，`grep -n TICK_INTERVAL` 能在演示启动清单里看到（不留"口头设置"）。

### T0-c（可选）演示前端节奏
- 若 4× 下 3 s 轮询跳变太碎：`ParkOrder.vue` 轮询基准 3 s→1.5 s（一个常数额度，env 化最好）。属 M2-e 范畴，演示前视观感决定。
- 范围外：不给移动端接 SSE（流式鉴权/重连是新增复杂度；何时改口：需要"秒级跟车特写"再上）。

---

## §3 M1 车辆配置与 RMS 口径对齐（P0）

### T1-a 消掉 25/30 分叉
- 证据：`application.yml:375` `min-assignable-battery: ${...:25}` vs 代码默认 30（`ParkPilotProperties.java:218`）vs 能量侧 30（yml:387）。
- 改动点：yml:375 默认 25→**30**，三处同值。
- **闸门**：`grep -n "MIN_ASSIGNABLE" back/fsd-bootstrap/src/main/resources/application.yml` 两行都输出 30；后端启动后 `SELECT` 任一低电车在 25–29% 区间不被派单（日志断言 `minAssignable` 拒绝原因出现）。

### T1-b 回补触发线 20→30（等 Q1）
- 证据：`return-to-charge-threshold=20`（yml:386、`FleetEnergyProperties.java:19`）；[业务] RMS＝低于 30% 回母港。
- 改动点（若 Q1 裁"对齐"）：yml:386 默认 20→**30**；`lowSocThreshold`（:13，现 20）同步复核，别留中间值。
- 代价：车辆离场补能更频繁，演示在场车变少——用 M0 倍速对冲（回港→换电 5 tick→回场全程在 4× 下 ≈几十秒）。
- **闸门**：配置生效后跑一单压低某车 SOC 至 29%，观察其被调度回补且日志带阈值判定原因；`grep` 确认无第二份 20 残留。

### T1-c 钉死"演示节奏 vs 物理口径"注释（等 Q5）
- 证据：运行时充电 17.5 s、换电 5 s vs 物理 2 h、30 s；7,200 s 只在离线 `ScenarioBench.java:185`；seed `avg_service_seconds='30'` 运行时零消费（`zjf_swap_cabinets.sql`，且 `zjf_retire_nearfield_piles.sql:5` 注释自认 1800/3600 口径混写）。
- 改动点：不改任何仿真值。在 `FleetEnergyProperties.java` 类注释 + `zjf_swap_cabinets.sql` 头注释写明："**30 s / 2 h 是对外物理口径；运行时仿真用 swapDurationTicks / chargeRatePerTick 的演示节奏，二者不一致是有意设计**"。
- **闸门**：下次任何人（含 AI）审到这两组数不一致时，注释即终审依据；`grep -rn "avg_service_seconds" back/` 输出里消费方仍为零，符合预期。
- 范围外：不改回物理值（演示全程车会卡在充电）。

### T1-d（核查项）minAssignableBattery 到底谁在用
- 开工前 `grep` 仿真侧 `minAssignableBattery` 与能量侧 `minAssignableSoc` 各自的读取方，确认 T1-a 不是改了个没人读的死常数（待办点名函数先查调用方）。若其中一份是死的，路线图为它单独记录"仅统一数值防误读"，不虚构行为变化。

### 3.1 T1-d 取证结论：这条判据链上到底谁在生效（2026-09-25 实测）

| 结论 | 证据 |
| --- | --- |
| **仿真侧 `minAssignableBattery` 是死键**：全仓（含 `front/`）只有 `ParkPilotProperties.java:218` 的字段声明与 `application.yml:379` 的绑定，**没有任何读取方**；同段 `low-battery-threshold` 同样无人读 | 全仓 case-insensitive grep 该标识符，命中仅"声明 + yml + 本文档"三类 |
| ∴ **T1-a 的 25→30 只是消除 yml 与代码默认值的分叉、防误读，零行为变化**（本路线图原本把它写成"消掉 25/30 分叉"，现按取证降级为防误读项） | 同上 |
| 真正生效的是能量侧 `minAssignableSoc`，且它**还要过两层覆盖**：① Redis `fsd:config:energy:min-assignable-soc`（`FleetEnergyThresholdResolver`，仅 `FleetChargePolicyImpl` 走这条路）；② DB 档案 `t_dispatch_strategy_profile.min_assignable_soc`（`DispatchStrategyRuntimeServiceImpl.toEnergy`，派单链路走这条路） | `FleetEnergyThresholdResolver.java:61-66`、`DispatchStrategyRuntimeServiceImpl.java:114-130` |
| 实测现值：本机与生产 Redis **都没有** `fsd:config:energy:*` 键 ⇒ 这条路回落 yml。生产 DB 档案 `id=1 PRODUCTION active=1 min_assignable_soc=30`、`id=2 EXPERIMENT active=0`；**本机两条都 active=1（EXPERIMENT 的 35 也 active）**，本机/生产在此处不一致，本机派单门槛可能被档案的 35 覆盖 | `SELECT ... FROM t_dispatch_strategy_profile`（本机 + `ssh dispatch` 各一次） |
| `returnToChargeThreshold`（T1-b 改的那个）**不在档案表里** ⇒ 只受 yml 与 Redis 影响，Redis 无键 ⇒ **yml 改动确实生效**；`ChargingSessionServiceImpl:130/243` 直接吃 yml bean，`FleetChargePolicyImpl:33` 吃 resolver，两条路本次都落在 30 | 表列清单：档案表只有 `min_assignable_soc / full_soc / energy_recovery_mode` 三个能量字段进覆盖 |
| ⚠ 顺带记录一处**未修的分叉**（不在本路线图范围）：回补阈值一个消费方走 Redis 可覆盖、另一个只读 yml，所以"用 Redis 热更回补线"只对一半链路生效。要收口的话另立任务 | 同上两行 |

---

## §4 M2 手机端下单与展示补强（P1/P2）

**下单链路已闭环，本阶段零后端改动**（任意点下单：V64 坐标入口 + 250 m 吸附 + 三判据拒单；前端 `OrderEndpointInput.vue` 服务点/地图点选二选一 + 手输经纬度兜底 + 常驻拒单原因 `[data-testid=order-rejection]`）。以下全部是展示层。

### T2-a 追踪地图画全部车辆 + 高亮指派车（P1，演示关键屏）
- 证据：现只画取/送两站 + 被追踪的 1 台车（`ParkOrder.vue:251-287`）；而全量车辆数据**已经在拉**（`getParkVehicles`，`ParkOrder.vue:491-508`）⇒ 纯渲染层缺口。
- 改动点：地图加全部 20 台车 marker（低优先级样式），被指派车用醒目样式 + 起终点连线；点击车辆 marker 显示 编号/SOC/状态。
- 注意：SIM 车坐标语义逐行契约（`VehiclePositionResolver` 取位口），**只消费接口返回的现成坐标，不在前端做任何像素↔GCJ 换算**。
- **闸门**：`npm run test:e2e`（或 visual-smoke）新增/扩展一条 spec：下单后追踪页可见 marker 数 ≥ 在场车辆数（mock `getParkVehicles` 返回 N 台断言渲染 N 个）；截图人工核一次。

### T2-b 围栏只画受理围栏（P1，等 Q4）
- 证据：移动端把所有 ACTIVE 围栏都画（`ParkOrder.vue:472-479` → `maps/parkGeoMapLayers.ts:303-337`）⇒ 实线 `ZJF-ZONE-SVC-01` 与青色虚线 `DEFAULT-BOUNDARY` 同屏两条线；`DEFAULT-BOUNDARY` 不参与受理，PC 工作台仍消费它（`OperationsCockpit.vue:376-378`）。
- 改动点（Q4 推荐=移动端隐藏）：移动端渲染处过滤（按 fenceCode 前缀 `ZJF-ZONE-`），**不改共用函数 `buildGeofencePolygons` 的默认行为**，加可选参数由移动端传入。
- **闸门**：移动端地图只有 1 条服务边界；PC 工作台包络仍在（跑一次工作台截图对比）。

### T2-c 换电柜/充电桩图层上移动端（P2）
- 证据：柜图层只在 `front/src/maps/stationLayers.ts:106-128`（showSwap 默认 true）；移动端完全没有 SWAP_CABINET。
- 改动点：移动端地图复用 station 图层（35 柜 + 6 桩），只在追踪地图画，**不进下单点下拉**（既有不变量：`isEnergyFacilityStation()` / `isAutoGeoEndpointStation()` 排除，改动不得绕过）。
- 讲故事：配合 T1-b，演示"电量到 30% → 车自己去柜子换电（30 s 口径）"。
- **闸门**：地图上柜子 marker 数 = 接口返回 `SWAP_CABINET` 数（本机应为 35）；`e2e` 下单页站点下拉里断言零个 `SWAP_CABINET/ZJF-CHG-/GEO-`（防回归）。

### T2-d 车辆规格文案卡（P2）
- 证据：`t_vehicle` 无续航/容量列（`V01__init_schema.sql:61-83`），加列要走迁移+seed+部署，性价比低。
- 改动点：车辆详情/追踪 marker 弹层写静态文案："新石器 L4 · X3 满载续航 180 km · 30 s 快速换电 · 35 柜"。数字只从 §0.1 抄，**不许出现 200 km**。
- **闸门**：文案 grep 无 `200`；无新增库列（`git diff` 不含 sql/migrations）。

### T2-e 轮询 3 s→1.5 s（P2，视 M0 观感）
- 改动点：`ParkOrder.vue:510-526` 基准间隔常量化为 1500 ms（或 env/构建参数化）；退避封顶 30 s 不动。
- 代价：`/admin/park/*` 匿名路径不过限流（生产=密钥开关在 .env），QPS×2 ——演示时长内可接受；**若上生产常开，先评估网关日志再定**。
- **闸门**：Network 面板实测间隔；后端日志无 429/超时。

### T2-f 拒单引导 + 围栏高亮（P2）
- 现状：拒单原因是常驻 `[data-testid=order-rejection]` 带 `data-code`（非 toast，设计如此别改）。缺的是"那我该点哪"。
- 改动点：吸附失败/围栏外两类 `data-code` 的文案追加"请在高亮的服务范围内选点"；触发时围栏描边闪一次（仅样式）。
- **闸门**：`scripts/e2e/v13-arbitrary-endpoint.spec.ts` 两条原闸门仍绿（受理端 mock 读载荷现判的行为不许动）。

---

## §5 M3 服务范围收尾（P1）

### T3-a 洞的处置（等 Q3；推荐**不补**）
- 证据：吸附成功率 85.1%<95%，缺口 7.33 km² ≈ 28–29 个洞；描洞可做到 ~100% 但地图重新出现 28 块飞地。
- 若裁"不补"：本任务只改**说法**——对外仍是 47.18 km²，围栏是"可服务性判据"不是"承诺全覆盖"；下单吸附失败即拒单给原因（已实现），不静默兜底。
- 若裁"补"：改动在 `back/sql/seed/zjf_service_area.sql` 几何 + `verify-geo-init-paths.sh` 八份 seed 顺序复跑，**部署走服务器侧流程且灌 seed 前必须停后端**——工作量和风险都另立子任务，不进本路线图默认路径。
- **闸门（不补路径）**：全文档/前端文案 grep 无"覆盖 100%"/"55 km²"字样。

### T3-b 数字纪律巡检
- 把仍流通的旧口径在本仓库全文（docs 之外主要是 `front/` 文案与 `reports/`）过一遍：`32.2`、`66 km²`、`5.31`、`12,267`、`17.84`、`0.727` 一律不许作为现值出现（历史执行记录里除外）。
- **闸门**：grep 清单每条给"出现处 + 定性（历史/现行/违规）"，违规处清零。

### T3-c（不动项声明）
- 图/围栏几何、设施布点 2026-09-24 已定案（§13.99/§13.101/§13.102），**本路线图不再动几何**；harness 那个 fence 2 行红是有意保留的良性 DIFF，勿重查。

---

## §6 待本人裁定（开工前定；未裁项按"默认值"执行并在此记录）

| # | 问题 | 选项 | 推荐 | 未裁默认 |
| --- | --- | --- | --- | --- |
| Q1 | `return-to-charge-threshold` 20→30？ | 对齐 RMS（回补更勤）/ 保 20（在场更久） | **改 30**，用 M0 倍速对冲 | 不动，T1-b 挂起 |
| Q2 | 倍速档位 | 250/500 ms 实测后定 | 250 ms 起步 | 500 ms（保守） |
| Q3 | 围栏 28 洞补不补（85.1%→~100%，代价 28 飞地） | 补 / 不补 | **不补** | 不补 |
| Q4 | 移动端 `DEFAULT-BOUNDARY` 虚线 | 隐藏 / 图例开关 / 保留 | **隐藏** | 隐藏（随 T2-b） |
| Q5 | 换电 5 s（演示节奏）与 seed 30 s（展示口径）并存，注释钉死？ | 钉 / 不钉 | **钉**（T1-c） | 钉 |

### 6.1 裁定结果（2026-09-25 本人裁，开工前提）

| # | 裁定 | 落点 |
| --- | --- | --- |
| Q1 | **改 30**（对齐 RMS），非"未裁默认"的"不动" | T1-b 执行：yml:394 与 `FleetEnergyProperties.returnToChargeThreshold` 均 20→30 |
| Q2 | **实测后定**，250 ms 优先 | T0-a 先跑 1000/500/250 三档，数字见 §9.1；定档后写进 compose 与演示启动清单 |
| Q3 | 不补洞（= 未裁默认） | T3-a 只改说法 |
| Q4 | 移动端隐藏（= 未裁默认） | T2-b 移动端渲染处过滤 |
| Q5 | 钉（= 未裁默认） | T1-c 注释落 `FleetEnergyProperties` 类注释 + `zjf_swap_cabinets.sql` 头 |

> 附带一条与 Q1 同源的现值修正：`lowSocThreshold`（yml:393）也由 20→30。理由是"别留中间值"——
> 回补线 30 而展示线 20 会让"地图上看着正常、其实已经在回桩"的车出现；前端
> `useChargingAwareness.ts` / `usePredictiveAlert.ts` 的 `LOW_SOC_THRESHOLD` 早就是 30，这次是后端向它对齐。

---

## §7 明确不做（防止范围漏出去）

1. ❌ 充电改回真实 2 h、换电改 30 s tick 级物理值——演示节奏优先。
2. ❌ 移动端 SSE/websocket 改造——轮询提频即可覆盖需求。
3. ❌ `t_vehicle` 加续航/容量列——迁移+seed+部署成本 > 展示收益。
4. ❌ 围栏几何补洞描洞（除非 Q3 裁补，且另立子任务）。
5. ❌ 动 SIM 行像素坐标语义 / 给车辆坐标做全局翻转或迁移——逐行契约，读取只走 `VehiclePositionResolver`。
6. ❌ 修 harness 那个 fence 2 行良性红——已定性，有意保留。
7. ❌ 产能数字改口径重跑——`trip_mileage_sampler.py` 现行值就是口径。
8. ❌ 顺手"统一"休眠常数（`chargeCompleteSoc`、`criticalSoc` 等不在本次判据链上的值）。

---

## §8 压缩预案（演示日期临时提前时，按序砍）

1. 先砍 M2-c/d/e/f（展示锦上添花），**保 M0 + M1-a + M2-a/b**（倍速、口径一致、车队可见 = 演示三要素）。
2. 再砍 T1-c 注释（改天补）。
3. 不可压缩：M5 彩排至少 1 轮全流程计时；Q1–Q5 至少给默认值（本表已给）。
4. 顺延：演示完把未做项回写本路线图，不另开新文档。

---

## §9 演示彩排脚本（M5，配置定档后跑）

| 步骤 | 动作 | 通过判据 |
| --- | --- | --- |
| 0 | 起服务前把 20 台车电量拉高：`UPDATE t_vehicle SET battery_level=95 WHERE vehicle_code LIKE 'ZJF-AV-%';`（**注意：`ensurePilotFleet` 只在首次创建时随机 80–100，重启不满电**——更正上午方案里该说法） | `SELECT MIN(battery_level) ...` ≥90 |
| 1 | 移动端在服务区中心点下单（预存常用点） | 30 s 内接单，无拒单块 |
| 2 | 追踪页：看全部车 + 指派车高亮 + 围栏单线 | 截图目视 |
| 3 | 挑一台车跑到 <30%（等 T1-b 生效的车或手动 UPDATE 压低） | 该车回补、换电、回场全程 ≤90 s 墙钟 |
| 4 | 演示中翻车预案：吸附失败 → 现场改点选常用服务点；地图加载失败 → 手输经纬度兜底（已实现） | 兜底路径各演练 1 次 |
| 5 | 记录：全程墙钟时长、各段耗时，回填本文档 | 数字进表 |

### 9.1 T0-a 倍速实测（2026-09-25，本机 20 台 SIM 车队，同一条 OD 对）

同一路线：`GEO-OSM0093 (121.078,31.962) → GEO-AMWL04 (121.101763,31.918297)`，直线 5,352 m。
测量脚本 `scripts/dev/m0-tick-gear-test.mjs`（驱动 `scripts/dev/m0-gear-sweep.sh 1000 500 250`），产物 `tmp/m0/gear-<tick>.json`。

| tick 档 | ① 下单→送达墙钟 | 相对 1× | ③ 单车位置变更中位间隔 | 车队 px/s | ② 3 s 重采样步长（中位 / 该档期望） | 终态 |
| --- | --- | --- | --- | --- | --- | --- |
| 1000 ms（1×） | **367.3 s** | 1.00× | 1,712 ms | 21.6 | 16.0 px / 24 px | COMPLETED |
| 500 ms（2×） | **156.9 s** | 2.34× | 449 ms | 133.4 | 36.9 px / 48 px | COMPLETED |
| 250 ms（4×） | **202.4 s** | 1.81× | 874 ms | 39.7 | 26.7 px / 96 px | COMPLETED |

**定档：500 ms（2×）。** 依据：250 ms 这一档实测**不比 500 ms 快**（202 s vs 157 s），把"更快"当卖点去拨到 4× 拿不到收益；拒单原因三档全空、三档都跑到 COMPLETED（闸门 ① 达标）。

必须写清的测量边界（别把这张表读成因果结论）：
1. **每档 n=1**。表里的排序（500 快于 250）是一次采样的结果，够用来定"演示拨 500"，**不够用来断言"4× 一定更慢"**。谁要主张后者，得先各跑 ≥3 次。
2. ③ 那一列**不是** tick 周期的无偏估计：一台车在一个 tick 内可能连过多个路径点，于是"相邻位置变更间隔"会**短于**真实 tick 周期（500 ms 档读出 449 ms 就是这个原因，不是"跑得比配置还快"）。因此**不能**据此断言调度线程超载——本报告没有established这个结论。
3. 车队 px/s 是全车队聚合量，随同时行驶台数变化，只用于同档内的相对观感，不能跨档当速度计。
4. ② 观感列：三档实测中位步长都**低于**该档期望值，因为 3 s 窗口里含装货/让行停留（stationary 样本 1–3 个）。人眼可读性结论以 §9 步骤 2 的浏览器目视为准，不以此列为准。

代价声明（T0-b 原文，仍然成立）：单独调车速会失真——扣电按 tick 不按里程速度；**先缩 tick，其次才动车速**。演示现场要临时回到原速：`DEMO_TICK_MS=1000 bash scripts/dev/run-demo-local.sh`。

### 9.2 M5 彩排实测（2026-09-25，tick=500 ms 定档后）

脚本 `scripts/dev/m5-rehearsal.sh`，产物 `tmp/m5/rehearsal-20260925-100112.txt`。

| §9 步骤 | 实测 | 判定 |
| --- | --- | --- |
| 0 电量拉高 | `UPDATE ... battery_level=95` 后 `MIN(battery_level)=95` | **PASS**（≥90）；顺带证实"重启不满电"那句更正是对的 |
| 1 中心点下单 | orderId=1096，**1.555 s** 派给 ZJF-AV-11，`status=DISPATCHED`，无拒单块 | **PASS**（闸门 30 s） |
| 3 压低一台到 29% | ZJF-AV-01 起始 29%，**2.1 s** 后电量开始上升（29→33），`t_charging_session` 新增一行 | 见下方"这条证明了什么" |
| 4 翻车预案 | 手输经纬度兜底路径下单成功（步骤 1 就是纯坐标单）；吸附失败改点常用点＝前端交互，随 M2 一起验 | 部分（坐标兜底已跑通） |
| 5 计时回填 | 本表 + §9.1 | **PASS** |

**步骤 3 证明了什么、没证明什么**（这条必须说清，否则是假绿）：
- 它是"低电车会进补能"的**一致性观察**，**不是** T1-b 阈值的证明。反证：按 `start_soc` 统计近 2 h 的充电会话，起始电量散布在 29/30/35/38/…/84 各档——因为 `idleChargeWhenNoDemand=true` 会把空闲车顶到满，所以"它去充电了"无法区分是回补线触发还是空闲补能触发。
- T1-b/T1-a 的**判据级**证据是下面这条边界实验：把 20 台车**全部**压到 29%，下单后订单 48 s 内始终 `WAITING_DISPATCH`、`NONE-ASSIGNED`（旧门槛 25 下这单会立刻派出去）。⇒ **30 这条派单线在生产代码路径上生效**。
- 附带发现（本 harness 自己的坑，不是产品缺陷）：Git Bash 里 curl 的 JSON 带中文 `remark` 会按 GBK 送出，后端回 `HttpMessageNotReadableException: Invalid UTF-8 start byte 0xb2`，看起来像下单口 500。脚本已改 ASCII remark 并留注释。

---

## §10 执行记录（做一项填一行：完成日期 + 实测数字 + 证据指针）

| 任务 | 状态 | 完成日期 | 实测/证据 |
| --- | --- | --- | --- |
| T0-a | ✅ | 2026-09-25 | 三档同路线墙钟 367.3 / 156.9 / 202.4 s，三档均 COMPLETED、拒单原因全空；数字与**测量边界**见 §9.1（每档 n=1，③ 列不是 tick 周期无偏估计） |
| T0-b | ✅ | 2026-09-25 | **定档 500 ms**（非推荐的 250）。固化在两处：`docker-compose.prod.yml` 后端 environment 与 `scripts/dev/run-demo-local.sh`；`grep -n TICK_INTERVAL docker-compose.prod.yml scripts/dev/run-demo-local.sh` 均命中，不留口头设置。车速保持 8 未动 |
| T1-a | ✅（降级） | 2026-09-25 | yml:379 默认 25→30。**取证后降级为"仅统一数值防误读"**：该键全仓无读取方（§3.1）。生效门槛的真实证据是边界实验：20 台车全压 29% ⇒ 48 s `NONE-ASSIGNED`（§9.2） |
| T1-b | ✅ | 2026-09-25 | Q1 裁"对齐 RMS"。yml:394 与 `FleetEnergyProperties.returnToChargeThreshold` 20→30，`lowSocThreshold` 同步 20→30；`grep FSD_FLEET_ENERGY application.yml` 三条能量线现值 30/30/30，无第二份 20 残留。该字段不进 DB 档案 ⇒ yml 改动确实生效（§3.1） |
| T1-c | ✅ | 2026-09-25 | 注释钉死在两处：`FleetEnergyProperties` 类 javadoc（"口径纪律：对外物理值 ≠ 运行时仿真值"）+ `zjf_swap_cabinets.sql` 头【T1-c 口径终审】。`grep -rn avg_service_seconds back/` 消费方仍为 0（ScenarioBench:1000 是报告文本、IntegrationTestSchema:83 是建表列，均非消费） |
| T1-d | ✅ | 2026-09-25 | 结论进 §3.1：仿真侧两个电量键是死键；能量侧 `minAssignableSoc` 受 Redis + DB 档案双层覆盖（生产档案=30 且 EXPERIMENT 已停用；本机 EXPERIMENT 仍 active=1/35，本机≠生产）；Redis 无 `fsd:config:energy:*` 键（本机与生产各核一次） |
| T2-a | ✅ | 2026-09-25 | 渲染层缺口补上（数据一直在拉）：`buildVehicleGeoMarkers(modeVehicles, {selectedId})` + 被追单 OD 连线（`includeOrderLines` 由 false 翻过来）。**浏览器实测真后端真数据：`vehicleMarkers=20`、`positionUnknown=0`**、selected 恰 1 台、只有被追那台显示标签。坐标只消费 `vehicleGeoPosition()` 现成返回值，无像素↔GCJ 换算（§7.5） |
| T2-b | ✅ | 2026-09-25 | Q4 裁"移动端隐藏"。`buildGeofencePolygons` 加可选参数 + `MOBILE_SERVICE_FENCE_PREFIX='ZJF-ZONE-'`，**默认行为不动**（e2e 有"PC 工作台包络照旧画、DISABLED 照旧不画"断言）；移动端只剩 1 条边界，青色虚线包络不再同屏 |
| T2-c | ✅ | 2026-09-25 | 新 `mobileEnergyFacilityStations()` 只喂追踪地图。**实测 `swapMarkers=35`**（= 接口 `SWAP_CABINET` 数）、`chargingMarkers=0` 是**设计如此**：6 根基地桩与柜同坐标，收进柜徽标以免叠成墨点（e2e 专测这条）。不变量有零泄漏断言：下单下拉里柜/桩/`GEO-` 自动落点全为 0；整页 57 marker 仍在 `MARKER_BUDGET` 内 |
| T2-d | ✅ | 2026-09-25 | `constants/vehicleSpec.ts` 单一文案源，页面 `[data-testid=vehicle-spec]` 实测渲染 `新石器 L4 · X3 满载续航 180 km · 30 s 快速换电 · 35 柜`；闸门：文案里不出现标称最长续航那档数字，`git diff` 不含新增库列 |
| T2-e | ✅ | 2026-09-25 | 基准间隔具名 `TRACKING_POLL_BASE_MS = 1500`，退避封顶 30 s 未动（e2e 断言两点）。代价按 §4 原文记账：匿名路径不过限流、QPS×2，仅演示时长内可接受 |
| T2-f | ✅ | 2026-09-25 | `ORDER_REJECT_SERVICE_AREA_GUIDANCE='请在高亮的服务范围内选点'` 只挂在吸附失败/围栏外两类码上（其余原因码不给，e2e 断言）；围栏描边闪一次走 `flashOutline`，**只改样式**：e2e 比对闪前/闪后的围栏集合与几何完全相同。`[data-testid=order-rejection]` 仍是常驻元素，没改成 toast |
| 前端总闸门 | ✅ | 2026-09-25 | `vue-tsc --noEmit` 干净；`npm run build` 成功；**全量 e2e+perf 66 passed / 0 failed**（`--workers=2`）。注：并行首跑曾因 vite 冷启动误报一条 v13 超时，单跑与串行均绿 ⇒ 判为抢资源不是回归 |
| 彩排目视（§9 步 2） | ✅（非像素级） | 2026-09-25 | 真浏览器驱动真页面下单后读 DOM 计数器取证（见 T2-a/T2-c 行）；**PNG 截图没拿到**——沙箱内浏览器不可见、直连 playwright 的浏览器未安装，故这条是结构取证而非目视，演示前建议人工再扫一眼 |
| T3-a | ✅（零改动） | 2026-09-25 | 裁定"不补洞"，且**现网文案本就没有**过度声明：`覆盖 100%`/`100% 覆盖` 全仓 0 命中；`全覆盖`/`55 km` 命中处都是闸门自述或"洞不描、洞内拒单"的反向陈述 ⇒ 闸门通过，无需改文案。见 §5 T3-a 与下方巡检表 |
| T3-b | ✅ | 2026-09-25 | 六个旧数全仓逐处定性（70 处命中 / 28 文件）：`front/` 与 `README.md` **0 命中**；唯一把旧面积写成现值的活文案 = `ParkPilotProperties.java:126`"服务范围因此定成 32.2 km²" ⇒ 已改为历史量 + 现值 47.18 km²。其余 历史/无关 或 落在禁改区：`V64__order_arbitrary_points.sql:13`（**不能改**，改注释会破已应用迁移的 Flyway 校验和）、`ScenarioBench.java:984` 的 17.84 出处陈述（要干净修只能重跑 bench，§7.7 禁）、`scripts/geo/amap_route_diff.py:49,52`（第二份产能算式，§7.7 禁）。明细见 §5.1 |
| M5 彩排 | ✅ | 2026-09-25 | §9.2 表：步骤 0/1/5 PASS（接单 1.555 s），步骤 3 达成但**按证据强度重新表述**（空闲补能是混淆项），步骤 4 只验了坐标兜底那半 |
| 部署 | ✅ | 2026-09-25 | 见 §11 部署记录：容器内 `FSD_PARK_SIMULATION_TICK_INTERVAL_MS=500` 已生效、能量三档无覆盖键⇒吃 yml 的 30/30/30、线上 `ParkOrder-CYslWLZ3.js` 里查到 `tracking-map-legend`／"满载续航 180 km"／"请在高亮的服务范围内选点"三个 M2 痕迹、生产真单 orderId=28 走完 `DISPATCHED→IN_PROGRESS→COMPLETED` 约 180 s |

## §11 部署记录（2026-09-25，本轮 M0–M5 上生产）

**前置**：本轮**无迁移变更**（本机迁移最高 V65 == 生产 flyway_schema_history 现值 65）、**无 seed 导入**、
**无围栏几何变更**（T3-a 裁"不补洞"）⇒ 不触发"灌 seed 前必须停后端"那条铁律，生产地图内容不动。

| 步骤 | 动作 | 实测 |
| --- | --- | --- |
| 1 | `scripts/pack-deploy-tree.sh` 按清单打包 | 1247 文件 / 2.0 MB；反证 grep 命中 **0** 条禁入路径（`.env*`、`data/backup`、`tmp`、`node_modules`、`target`、`*.sql.gz`） |
| 2 | scp 上传 + 两端 sha256 比对 | 本地=远端=`3542f451452134aa…` |
| 3 | **build 之前**先打回滚 tag | `dispatchflow-{backend,frontend}:rollback-20260925-102311` 两条都在 |
| 4 | 生产库备份 | 首次拿到 **20 字节**——mysqldump 漏了 `-uroot -p` 且 `2>/dev/null` 把报错吞了，gzip 了个空文件。补凭据后重做：14.9 MB SQL ⇒ 1.6 MB gz、50 张 `CREATE TABLE`、46 段 `INSERT`、尾部 `Dump completed` 在，与上一份已知可用备份（1.53 MB）同量级 |
| 5 | 解包 | 先解到 `/tmp/x` 空跑一遍，确认 `TICK_INTERVAL` 命中 2、yml 回补线 30、Java 默认 30、M2 helper 命中 2，再 `cp -a` 落位（`.env` 不在包里 ⇒ mtime 仍是 Sep 24 22:13，未被覆盖） |
| 6 | `bash scripts/deploy.sh` | 两镜像重建成功、`[6/8] 后端已就绪`、`docker ps` 里 fsd-backend `Up (healthy)`、后端日志 `Started FsdCoreApplication in 28.611 s` 无 ERROR/Exception |
| 7 | 落地取证 | 容器 env：`FSD_PARK_SIMULATION_TICK_INTERVAL_MS=500`；无任何 `FSD_FLEET_ENERGY_*` ⇒ 新 yml 默认值生效；`/internal/actuator/health` 从容器内取到 `{"status":"UP"}`；线上 bundle `ParkOrder-CYslWLZ3.js` 含三个 M2 痕迹；`https://aplicity.online` 与 `/mobile/order` 均 200 |
| 8 | 真单端到端 | 匿名 `POST /api/admin/park/orders`（坐标入口）⇒ orderId=28 立即 `ASSIGNED / Auto assign success`，15 s 时 `HEADING_TO_PICKUP`、105 s 起 `HEADING_TO_DROPOFF`、**180 s `COMPLETED`**（与本机 500 ms 档实测 157 s 同量级） |

**顺手修掉的一个假警报**：`deploy.sh` 第 7 步的健康探针 curl 宿主机 `127.0.0.1:8080`，
而 prod compose 从不给 backend 发布宿主端口 ⇒ 这句**每次部署都必然失败**、喷"[WARN] 后端健康检查未通过"，
而同一屏 `docker ps` 写着 healthy。探针改为容器内取 `/internal/actuator/health`（打 `/actuator/health` 会 404），
并在拿不到 `"status":"UP"` 时 `exit 1`——让"红"真的意味着红。

**没测的那条，别当成测过**：生产的 tick 实际周期我**没有**独立量过。15 s 密集采样只看到首车电量变化 1 次
（那台车在充电接近上限，步长会被 cap 吃掉），这个仪表不足以判周期。节拍数字来自**本机同代码同配置**的三档实测。

**留了一条**：orderId=28 这单留在生产库里当部署证据，没删（生产本来就是演示夹具）。

### 11.1 第二轮部署（同日，因为第一次推上去 CI 是红的）

第一版部署**确实成功**（镜像起来了、真单跑完了），但推上去之后 CI 三条红：

| 红在 | 真因 | 处置 |
| --- | --- | --- |
| Frontend `v9` ×3 | `ParkOverview.vue` 把"地图瓦片不可用"和"数据不可用"混成一件事：① `map-status-bar` 整块挂在 `v-if="geoMapAvailable"` 下 ⇒ 没有 Key 时连"数据已停止更新"都看不到；② `v-if="geoMapAvailable && routeWarning"` 配 `v-else` ⇒ 地图健康且无告警时也显示"高德 Key 未配置"；③ 测试用裸 `getByText('短驳地理')`，与兜底面板标题"短驳地理图未加载"撞串（CI 无 `front/.env.local` ⇒ provider 回落 `PARK_DIAGRAM` ⇒ 那块面板才出现） | 新鲜度条改为无条件渲染、两支 v-if 分明、选择器限定到 `.ant-segmented-item` |
| Backend `spotbugs:check` ×10（Low） | 前序在制代码从没进过 CI，一提交就被 analyzer 抓到 | 逐条改：4 处 `Locale.ROOT`（其中 `AdminDecisionPolicyController` 那处是**真 bug**：比对 `registeredPolicyIds()` 的两端归一化不同源）、`parsePolygon` 收窄捕获且保持"整片作废"语义、`TrafficZone` 两处（读收窄 / 写留痕）、`ScenarioBench` 溢出与重复条件 |

**我这边为什么没提前发现（记着，下次别再犯）**：本机 e2e 是在 **`.env.local` 在场 + vite 代理指向真后端** 下跑的，
所以那三个环境依赖问题一个都露不出来；本地报"66 passed"是**假绿**。
后来用"把 `front/.env.local` 移走再跑"复现了 CI 的文件状态才对齐。⇒ 本机绿 ≠ CI 绿，判"前端没问题"之前必须按 CI 的 env 跑一遍。

**复验**（本机）：`mvn -o -fae compile spotbugs:check` 七模块全 SUCCESS；`fsd-dispatch`+`fsd-admin-api` 测试全绿；
`npm run lint`（0 error / 48 warning，上限 50）+ `npm run build` + `npx playwright test scripts/e2e` **63 passed**；
`.env.local` 移走后 v9 **12 passed**，跑完 `cmp` 确认还原逐字节相同。
**CI 复验**：sha `d36e0c8` ⇒ Doc Links / Backend Tests / Frontend Build **三项全 success**。

**第二轮部署取证**：包 `fsd-tree-20260925-105945`（两端 sha `68c27cba42ded24c`）、
回滚 tag `rollback-20260925-110016`×2、库备份 50 张表 / 1.62 MB gz；
**落位前先在暂存目录 grep 断言两处修复都在包里**才 `cp -a`（避免"部署了个没修的版本"）；
`.env` mtime 仍是 2026-09-24 22:13（未被覆盖）；`deploy.sh` 新探针输出 `[OK] 后端健康：{"status":"UP"}`；
线上产物 `ParkOrder-DjWaQXKs.js` 含 `tracking-map-legend`＋规格文案、`ParkOverview-B3BVd-JK.js` 含"数据已停止更新"；
容器 `FSD_PARK_SIMULATION_TICK_INTERVAL_MS=500`、重启后后端日志 ERROR/Exception **0 行**、站点与移动页均 200。

> **本轮同步修掉的死链**（《已完成工作记录》《调度算法与地理收敛任务路线图》《部署整改任务路线图》三份文档已退场后遗留）：`README.md` 四处（徽章、"文档只剩三份"导语、生产部署段、文档表三行）与 `scripts/dev/reset-demo-dispatchable.sh:6` 一处改指现存文档；守卫 `node scripts/check-doc-links.mjs` 复跑 `[OK] 检查 21 条引用`。已删文档**未恢复**，其内容按路径可在 `git log --diff-filter=D -- docs/` 查到。

### 10.1 T3-b 旧口径巡检明细（每条：出现处 → 定性）

| 旧数 | 出现处 | 定性 |
| --- | --- | --- |
| `32.2` | `ParkPilotProperties.java:125-127` | ~~违规~~ **已修**：改成"66.27 km² 提取框里**当时**只有 32.2 km²…现值 47.18 km²" |
| `32.2` | `V64__order_arbitrary_points.sql:13`、`back/fsd-bootstrap/target/classes/db/migration/…` | 违规但**禁改**（已应用迁移改注释＝破校验和；后者是构建产物） |
| `32.2` | `scripts/geo/{swap_cabinet_placement,osm_to_road_graph,trip_mileage_sampler,service_area_from_snapping}.py` | 历史（各自都带日期或"拆边前"上下文；`service_area_from_snapping.py:21` 那张自校验表是 **W2-c 前图**标定，今天复现不了） |
| `32.2` | `VehicleDetailEnrichmentServiceTest.java:98`（纬度 32.200000）、`repositioning-m-tier.md:65`（`220232.2994`） | 无关（数字撞脸） |
| `66` | `ParkPilotProperties.java:125`、`V64:13`、`reports/amap-scope-probe.json:13` 的 `66.27` | 现行(正确)——量纲是 **OSM 提取框**不是可下单面积；`66` 裸字必须带"图框/box"限定词判别，否则 `13.66/1066/66%` 全是假阳性 |
| `66` | `data/backup/zjf_road_network.pre-*.sql` 头部统计 | 历史备份（禁改区） |
| `5.31` | `m-tier-bench.md:15` 的 std 列、`data/carla/*` 顶点、`tmp/m0/*.json` | 无关（不是 5.31 km²/单） |
| `12,267` | `trip_mileage_sampler.py:133,137,153` | 历史（三处都明写"已被 12,998 m 实测取代"） |
| `17.84` | `reports/scenario-bench/*.md` 共 23 处 | 历史（每份自带 `roadGraphVersion=osm-expanded-2026-09-21`，旧图 seed 头部确为 17.84，与自身 Config 自洽）；`tmp/reports_n1800_baseline_20260924/*` 同批快照不计 |
| `17.84` | `ScenarioBench.java:185`（bench 输入常数） | 现行(正确)，改它＝重跑产能＝§7.7 禁 |
| `17.84` | `ScenarioBench.java:984-985` 现在时写"来自 seed 头部"，而现 seed 头部是 **18.73** | 违规（陈述的出处错了）。**未改**：只改文案不改 :185 会让报告自己打脸，干净修法只有重跑，被 §7.7 禁 ⇒ 记为待裁 |
| `17.84` | `scripts/geo/amap_route_diff.py:49,52` 及其产物 `reports/amap-route-diff.md` 的产能表 | 违规候选：**第二份产能算式**，与 §0.4 指定现行口径（sampler：18.73 + 14,595 m / 1.10 / 2.51×）不同源。对外只引 sampler；建议在该表抬头标"旧算式对照，非现行值"⇒ 记为待裁 |
| `0.727` | `tmp/roadmap-orig.md:33,175`、`tmp/rmap-p1.md`（gitignored 草稿，存的是已删旧路线图副本；旧义=提取框面积） | 历史（不在流通） |

> 完成后按仓库惯例：`[x]` 只留一行结论 + 指向《已完成工作记录》对应 §13.x 的指针，执行细节写进记录文档。服务器侧部署（env 变量进 `.env`、任何 seed/迁移上生产）由本人执行或个案授权后执行。
