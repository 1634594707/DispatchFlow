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
| 2 | 追踪页：看全部车 + 指派车高亮 + 围栏单线 | 截图目视 ⇒ 已落档 [`docs/assets/demo-tracking-map-500ms.png`](assets/demo-tracking-map-500ms.png)（430×1080，tick=500 演示档；同屏计数器 `车队 20 台 / 补能点 35 处`，规格行 `新石器 L4 · X3 满载续航 180 km · 30 s 快速换电 · 35 柜` 也在画面里）。芯片文字与 L0/L1/L2 均可读——深色字压深色底那处缺陷已按方案①修掉并二次补齐，见 §12.2 |
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
| 前端总闸门 | ✅ | 2026-09-25 | `vue-tsc --noEmit` 干净；`npm run build` 成功；`npx playwright test scripts/e2e` **63 passed / 0 failed**（`--workers=2`，与 CI 同范围）。⚠ 本行原先写"66 passed"是**假绿**：那次跑的是整个 playwright 配置（含 `scripts/perf`）且带着 `.env.local` 与真后端代理 ⇒ 掩盖了三个环境依赖缺陷，详见 §11.1 |
| 彩排目视（§9 步 2） | ✅ | 2026-09-25 | 截图已落档 `docs/assets/demo-tracking-map-500ms.png`（430×1080，tick=500 档，同屏 `vehicleMarkers=20 / swapMarkers=35 / positionUnknown=0`）。图中芯片文字可读——标签隐形缺陷已按方案①修掉，见 §12.2 |
| T3-a | ✅（零改动） | 2026-09-25 | 裁定"不补洞"，且**现网文案本就没有**过度声明：`覆盖 100%`/`100% 覆盖` 全仓 0 命中；`全覆盖`/`55 km` 命中处都是闸门自述或"洞不描、洞内拒单"的反向陈述 ⇒ 闸门通过，无需改文案。见 §5 T3-a 与下方巡检表 |
| T3-b | ✅ | 2026-09-25 | 六个旧数全仓逐处定性（70 处命中 / 28 文件）：`front/` 与 `README.md` **0 命中**；唯一把旧面积写成现值的活文案 = `ParkPilotProperties.java:126`"服务范围因此定成 32.2 km²" ⇒ 已改为历史量 + 现值 47.18 km²。其余 历史/无关 或 落在禁改区：`V64__order_arbitrary_points.sql:13`（**不能改**，改注释会破已应用迁移的 Flyway 校验和）、`ScenarioBench.java:984` 的 17.84 出处陈述（要干净修只能重跑 bench，§7.7 禁）、`scripts/geo/amap_route_diff.py:49,52`（第二份产能算式，§7.7 禁）。明细见 §10.1 |
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

### 11.2 第 3–6 轮（同一天的增量上线，逐轮只记差异）

| 轮 | 时间 | 上的是什么 | 取证 |
| --- | --- | --- | --- |
| 3 | 12:29 | 「园区调度」示意场景连根拔（§12.1） | 容器内 `find / -name park-map.svg` 为空、产物无 `park-map` 引用 |
| 4 | 13:30 | 越界监控不再吃展示包络 + 异常类型中文标签（§13） | 后端 `Started FsdCoreApplication`、异常队列不再新增 `GEOFENCE_EXIT` |
| 5 | 14:58 | 深色浮层文字钉 `--fsd-text-on-overlay*`（§12.2 第一轮） | 线上 `AmapGeoMap-C-DOnqkU.css` 含新 token |
| 6 | 15:17–15:22 | 同一族的**强调色/语义色**补钉（§12.2 第二轮） | 见下 |

**第 6 轮明细**：包 `fsd-tree-20260925-151736.tgz`（1247 文件 / 2.0 MB，两端 sha `e7b648d353b72555`）；
反证 grep 命中 **0**（`.env.example` 两份是纯注释模板，已逐行看过，不算泄漏面）；
回滚 tag `rollback-20260925-151809`×2 在 build **之前**打好；
库备份 `fsd_core-20260925_151834.sql.gz` 1.6 MB / 50 表 / `dump_completed=yes`（脚本按 `MAX_BACKUPS=7` 轮转掉了 5 份旧的，含 `preV64` 与 `pregeoexpand`——这是脚本设计行为，但意味着**再往前的手工安全副本已经没了**）；
落位前在 `/tmp/fsd6` 断言包里三个新 on-overlay token、组件根 9 条钉、v15 四条测试齐备才 `cp -a`；
`.env` mtime 仍是 2026-09-24 22:13（未被覆盖）；`deploy.sh` `[OK] 后端健康：{"status":"UP"}`；
容器内产物换成 `AmapGeoMap-D31rW2QX.css` / `index-BaiUrKs9.css` 且含 `accent-strong-on-overlay`；
**生产页真机复量**（38 个标签 + 三支层级按钮，按 alpha 逐层合成后算 WCAG）：
标签 **15.54**、未选中 **7.69**、**选中档 7.56**（修前 2.30）、L2 **7.69** ⇒ 与本机逐项一致。
CI 在 `93e0b6b` 三项全 success。本轮**无迁移、无 seed、无围栏几何变更**；后端容器未被重建（只动前端，`docker ps` 显示 Up 2 hours 属正常）。

> **⚠ 部署侧有一条必须在演示前知道**：`front/src/sw.ts` 用 `NavigationRoute` 把 **HTML 本身**也放进了
> workbox precache（`precacheAndRoute(self.__WB_MANIFEST)` + `cleanupOutdatedCaches()`），
> 所以**上线后的第一次导航仍会拿到旧壳**——我第 6 轮复量时先撞上的就是这个：URL 加了 cache-buster 也没用
> （SW 直接答了自己的 precache，根本没走网络），量到的是旧 hash 的 CSS 和 2.30:1。
> 注销 SW + 清空 CacheStorage 后重新导航，才拿到 `AmapGeoMap-D31rW2QX.css` 与 7.56。
> `sw.js` 自己是 `Cache-Control: no-store`、`cf-cache-status: BYPASS`（边缘不缓存，新 SW 一次导航就能装上下一个接管），
> 所以代价固定是**"老访客慢一次"**：演示前请先开页面 → 刷新一次 → 再刷新一次，确认层级按钮是亮青色再开始。

## §15 第三批：围栏扩界落地 + 35 台车与补能网络（本人 2026-09-25 17:5x 起追加）

本人给了车辆配置（新石器 L4、X3 满载 180 km、充满约 2 h、30 s 快速换电、RMS 低于 30% 回母港），
并裁定：**母港统一停车位 + 地图选几个"可停车+充电"点 + 其中部分支持快速换电 + 一共 35 台车 +
容量按点位算（一个点能停很多辆）+ 对外文案按新拓扑重写**。

### 15.1 围栏扩界：数字与代价（已本机落库，生产随第 10 轮上线）

| 口径 | 扩界前 | 扩界后 |
| --- | --- | --- |
| 可下单面积（R=250） | 47.16 km²（文档现记 47.18） | **50.08 km²**（+2.92） |
| 外沿多边形 | 55.05 km²（seed 抬头旧记 55.42） | 57.71 km² |
| 设施越界 | 5 柜 + 2 桩 | **0**（47/47 全进框） |
| 单均里程（`trip_mileage_sampler --tier fence`，300 样本、seed 20260924、两次同参对照） | 中位 14,887.5 m / P90 21,051.6 m | 中位 **15,143.7 m** / P90 **21,252.1 m**（+1.7% / +1.0%） |

**产能口径没被改动**：§0.4 指定现行值来自 sampler（18.73 km 基准 + 14,595 m 实测那一档），
本次只重算了 sampler 的里程分布做对照，**没有重跑 `ScenarioBench`**（§7.7 禁止）。
中位 +1.7% / P90 +1.0% 这个量不改任何对外数字（引用的 14,595 m 是另一档口径，见 §0.4 出处），
所以产能表述维持原值；若以后要引用扩界后的里程档，必须同时改 §0.4 的出处标注。

两处工具缺陷在重跑时被抓出来并修掉（都在生成器里，回归干净：默认 seed 仍 1 片 / 47.16 km²）：
① `load_fences()` 把所有停用 `UPDATE` 全局套用 ⇒ 任何新增输入片被自己那条 W3-b 语句误杀，
改成按 seed 先后**重放**；② 退化碎片守卫用 `len(ring) >= 4` 数顶点，而环是闭合存储的
（首点在末尾重复一次）⇒ 3 顶点细条被当成 4 点放过，实测漏出一块 0.055 km² 的三角；
改为按**去重后顶点数**判 + 最小片面积 `--min-piece-km2`（默认 0.25），并把"发布 N 片 / 丢弃 M 片"
写进 seed 抬头。另外给 emit 补了一条 `DELETE ... ZJF-ZONE-SVC-% NOT IN (发布清单)`——
只 upsert 不会收掉上一版发布过的片，那条三角在库里是 ACTIVE、会继续参与受理。

### 15.2 35 台车 + 母港 35 位 + 2 个卫星补能点

| 项 | 内容 | 实测 |
| --- | --- | --- |
| 车队 | `geo-vehicle-count` 20 → **35** | 本机 35 台 `ZJF-AV-*` ONLINE/IDLE |
| 母港停车位 | `zjf_standby_slots.sql` P1..P20 → **P1..P35**（一车一位，像素与经纬度成对取自同一节点） | 35 个 STANDBY 位、35 个不同坐标、**全部在服务围栏内** |
| 卫星补能点 | 新增 `zjf_energy_sites.sql`：**E1 西南 AMCJ11（离基地 5,585 m）、E2 东北 OSM0301（3,613 m）**，每点 8 个"车位+桩"，E1 配 30 s 换电柜（`slot_count=8`） | 站点 2 · 车位 16（CHARGING_ONLY）· 桩 16 · 柜 1 ⇒ 全库桩位 22、柜 2 |
| 容量语义 | 按**点位**计容量：`findSwapCabinet` 从"取第一条 ACTIVE 柜"改成"先用 `countActiveAtCabinet < slot_count` 筛掉满柜、再取最近"；满员时退回最近满柜**开过去排队**，不再原地不动 | 空闲车不再叠在同一个坐标 |
| 对外文案 | `PILOT_VEHICLE_SPEC` 去掉"35 柜"（那 35 个 `FSD-SWAP-*` 只是柜的**位置**，真柜在 `t_battery_swap_cabinet`），改为 `新石器 L4 · X3 满载续航 180 km · 约 2 h 充满 · 30 s 快速换电 · 35 台 · 母港 + 2 个补能点` | v14 的规格断言同步；闸门仍禁止出现 200 km 那档 |

**"约 2 h 充满"与仿真节拍不是一回事**，已写进常量注释：厂商标称是物理值，仿真里充满一台是
`charge-rate-per-tick=4`（≈12.5 s），否则演示几分钟内看不到电量回来。别拿其中一个去校准另一个。

### 15.3 范围外不能下单：三点探测

| 探测点 | 期望 | 实测 |
| --- | --- | --- |
| `121.553210, 31.209870`（远在外） | 拒 | `ORDER_ENDPOINT_OUT_OF_SERVICE_AREA`，消息带坐标 ✓ |
| `121.133249, 31.903863`（= 原越界的 FSD-SWAP-02 锚点） | 扩界后应可下单 | **下单成功**（orderId 1100）⇒ 扩界真的把东侧放进来 |
| `121.080081, 31.960592`（母港） | 可下单 | 成功（orderId 1101） |

> 这两条探测单当时把 `dispatchDemandActive` 抬成 true，**抑制了空闲车的补能路径**，
> 导致我一度误判"低电车卡在 20% 不动"是补能逻辑坏了。测量前先确认没有自己造的并发需求。
> （两条已 CANCEL，本机 `t_battery_swap_session` 的测试数据已清空。）

### 15.4 ⛔ 换电仍然实际不可用：会话抖动（新开待办 #13，AUTO 档因此撤回）

把补能入口改成按 `energy_recovery_mode` 分流之后（原来 `routeToStandby`/默认分支**直接调
`routeToCharging`**，模式形同虚设，`t_battery_swap_session` 至今 0 行），AUTO 档确实能走到换电了——
然后暴露出下一层：**每台车 6 分钟产生 86 条换电会话**（≈每 4 s 一条，17 台车共 735 条），
等于"换完立刻又被判需要补能、再进一次 `routeToSwap`"。
怀疑点是 `completeSwap` 写回的电量与 `needsCharging` 读取的不是同一份视图（DB 行 vs 运行时快照）。

因此：**V66（把 PRODUCTION 档改成 AUTO）已删除**，本机策略档恢复 `CHARGE`，抖动会话清空。
代码侧的三项改动（按模式分流、最近柜带容量、`countActiveAtCabinet`）**保留**——在 CHARGE 档下
它们与改前行为一致（`routeToEnergyRecovery` 的 CHARGE 分支就是原来的 `routeToCharging`），
所以是惰性且安全的。等 #13 修好、证明"一次换电 = 一条会话"之后，才允许把 AUTO 打开。

> 这条要如实说：演示文案里的"30 s 快速换电"目前是**厂商能力 + 已建模的柜**，
> 仿真里还没有真实发生的换电记录。

### 15.5 空闲车占位衰减：已定位并修完（本人 2026-09-25 22:4x 追加"先让车辆全部归位"）

上线后复量：生产 35 台全部 ONLINE/IDLE、SOC 全部 >30（**"一堆故障车"这个症状消失了**），
但**只有 6 台占着车位，29 台 IDLE 无位**；截图里就是"一排车散在路上"。

追到底是两件事：

1. **`recordPoint()` 把车的位置读成"上一轮停在哪儿就是哪儿"** ⇒ 重启后车留在旧位置，
   要一路爬回待命位。修法：`createIdleState()` 里把本进程**第一次见到**这台车的位置直接摆到它的
   待命位上（运行中完成任务的车走 stage 迁移、不经过这里，所以不会出现"送完货瞬移"）。
2. **真正的元凶是 `idleChargeWhenNoDemand=true`**：它让**全部**空闲车同时涌向有限的桩位——
   抢到位的车放掉待命位开过去、抢不到的原地等，所以母港车位长期只有个位数被占。
   默认改成 **false**（Java 与 `application.yml` 两处一起）。这恰好才是 RMS 对外的说法：
   "电量低于 30% 时自动调度车辆返回母港补能"，而不是"没单就全体去充电"。

**复测（本机，tick=500 ms，开机 2 分钟）**：占位车辆 **35/35**、IDLE 无位 **0**、
35 台落在 **35 个不同坐标**、SOC 全部 ≥30。闸门：`fsd-dispatch` + `fsd-admin-api` 全绿、`spotbugs:check` 干净。

> 中途我先假设"释放车位后留着旧 `standbyPoint` 引用"是原因，加了 `standbyPoint = null`
> （`ec863f0`）——那条改动本身是对的（旧引用指向一个已被自己放掉、随时被别人占走的位），
> 但它**没有**让绑定数涨回来；判清第 2 点才真正解决。记下来：修完没效果就说明假设不完整，
> 别把它当"已修"。

## §12 本轮追加的两件事（不在原 M0–M5 清单内）

### 12.1 「园区调度」示意场景连根拔（本人 2026-09-25 追加指令）

**为什么该删**：对着活库数过——`PARK-*` 仿真车 **0 台**、`^[AB][1-4]$` 示意站 **0 个**，
所以移动端与 PC 大屏各留着一个"点了是空图 + 空下拉"的场景开关；
更糟的是 `config/index.ts` 里 `DEFAULT_TRACKING_SCENE = 'park'` ⇒ **车辆监控大屏默认打开的就是那张空园区图**。

**删了什么**：`front/public/park-map.svg`、`MobileOrderMode` 及其持久化、`Tracking.vue` 的 Leaflet 示意画布与
`L.imageOverlay`（该文件净减 804 行）、`stationLayers` 的 `isSchematic*`/`filterSchematic*`/`orderableStationsForMode`、
`OrderTrackingPanel` 的示意图分支，以及 `ParkMiniMap.vue`（grep 全站**零引用**，早已是死组件）。
合计 19 文件 **+86 / −1747**。历史订单按本人裁定不做兼容。

**没动**：能量设施与 `GEO-` 自动落点绝不进下单下拉那组不变量、`filterWorkbenchSituationStations`、
`workbenchStationRole` 的类型优先判定、逐行坐标契约。剩余 `schematic` 命中全是解释性注释
（`api/park.ts` 的坐标互转接口仍被基础设施选点用着，不属于这个场景）。

**验证**：`vue-tsc` 干净；lint 0 error / 47 warning（上限 50）；`npm run build` 成功；
`npx playwright test scripts/e2e` 全量 **63 passed**（与删除前同数）；
两条被改 spec 的 `test()`/`expect()` 逐个比对 12/12、8/8、36/36、29/29 ⇒ 是改判据不是删断言。
CI 在 `bcfa1a4` 三项全绿；第三轮部署后容器内 `find / -name park-map.svg` 为空、
`/usr/share/nginx/html/assets/*.js` 无 `park-map` 引用。

> 排查这条时踩到一个**仪表假信号**：`curl https://aplicity.online/park-map.svg` 返回 200 且
> `Content-Type: image/svg+xml`、正文是旧图 —— 看着像"没删干净"。实际本机 curl 走的是环境里的 HTTP 代理缓存；
> 直连容器 `127.0.0.1:8081` 取到的是 `text/html` / 1693 B / 与首页同尺寸，即 nginx `try_files … /index.html` 的 SPA 兜底。
> 结论：**判"资源是否还在"要看到容器内的文件系统与响应 Content-Type，不能只看 HTTP 状态码**（200 也可能是兜底页）。

### 12.2 已修：marker 标签深色字压深色底（本人裁"选①，保持暗色主题一致"）

**真正的根因不是"文字色没赢过 AMap"**——我第一版判断错了，探针打脸后才看清：
标签是个**没有子节点**的扁平 `div.amap-marker-label`，文字直接在里面，我们的规则确实生效了
（底色 `rgb(21,26,33)` 就是 `--fsd-surface-overlay`）。问题在于 **token 被外层改写了**：

```
:root            --fsd-text-primary = #eef2f6   ← 暗色档
.map-wrap.geo    --fsd-text-primary = #1a1a1a   ← 移动端亮色页在 ParkOrder.vue:703 局部重定
```

所以 `color: var(--fsd-text-primary)` 忠实地解析成了深色，落在永远深色的芯片上 ⇒ 隐形。
同一个组件里还有 **4 处**同病：`--fsd-text-secondary`(2 处)、`-tertiary`、`-muted`，
以及 `--fsd-bg-hover`（移动端把它改成了浅色 `#edf2f3`）——层级按钮实测只剩 **#666 on #151a21 ≈ 2.9:1**。

**修法**：新增一组不可被外层改写的 `--fsd-text-on-overlay{,-secondary,-tertiary,-muted}`，
在 `.amap-geo-map` **组件根上把 5 个通用名钉回暗色档**（含 `--fsd-bg-hover: var(--fsd-surface-hover)`）。
选钉根而不是逐条换 token：这样组件里**以后新写**的规则也一并罩住，不会再漏。

**实测结果**（真浏览器、真高德、37 个标签）：标签对比度 **2.9 → 15.5:1**，层级按钮 **→ 7.7:1**；
截图已重生成，`docs/assets/demo-tracking-map-500ms.png` 里芯片文字可读。

**但"层级按钮 7.7:1"这个数当时只覆盖了未选中的两支，选中态没进样本**——第一轮部署后我拿同一套探针
去生产页复量，才抓到同族的第二处渗漏：`ParkOrder.vue:714-720` 不只改了文字，还改了**强调色与语义色**
（`--fsd-accent #438f9b`、`--fsd-accent-strong #326f78`、`--fsd-error #c45868`），
而选中档的「L1试点」按钮文字吃的正是 `--fsd-accent-strong` ⇒ 深青字压在 16% 青色高亮上，**实测 2.30:1**；
`--fsd-accent`（浮层里的 `code` 与检查链接）4.69、`--fsd-error`（错误浮层文字）4.11 也都在 AA 线下。
⇒ 再补 `--fsd-{accent,accent-strong,error}-on-overlay` 三个值并在组件根钉上。补完本机与生产一致：
**标签 15.54、未选中按钮 7.69、选中按钮 7.56**。

> 这里仪表错了两次，记下来别再犯：① 第一版合成把 `rgba(86,185,200,0.16)` 当**不透明**色算，
> 报出 2.49 的假数（真值 2.30）——半透明底色必须按 alpha 逐层叠到祖先不透明底上；
> ② 样本是按"看得见的元素"随手取的，没枚举状态（active/hover/disabled），
> 于是"7.7:1"被我说成了整个按钮组的结论。**判一个组件的可读性要按状态取全样本。**

**守卫的边界要说清**：新增的 `v15-overlay-contrast.spec.ts` 是**源码级**断言（钉 token 那段话在不在），
不是渲染断言。原因：marker 标签 DOM 由高德 JSAPI 注入，而 e2e 为稳定会 `abort` `*.amap.com`
（见 v14 的 `seedMobilePage`）⇒ CI 里根本没有 `.amap-marker-label` 节点，测不到。
15.5:1 / 7.7:1 这两个数是手工在真浏览器里量的，CI 只保证"钉 token 的契约不被悄悄删掉"，
并额外钉住前提（移动端确实还在局部重定主题）。
第二条测试把**钉回去的那几个色值本身**按 WCAG 公式重算（浮层底色与 16% 高亮底两种情形都要求 ≥4.5:1），
所以将来谁把 `*-on-overlay` 改成一个看着顺眼但过不了 AA 的值，CI 会红。
`--fsd-text-on-overlay-tertiary/-muted`（4.99 / 3.67）不在这条下限里——它们是主题刻意保留的"安静档"，
PC 深色页同样只用它们做次要标注，移动端与 PC 表现一致，不是这次要修的渗漏。

> 更正一处我先前的判断：我原以为"改完上线后边缘最长 4 h 才干净"。那条只对**固定文件名**成立
> （`park-map.svg` 那种）；CSS/JS 走内容 hash 文件名，`index.html` 一改就指向新文件，没有 4 h 尾巴。

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

## §13 异常任务队列清理（2026-09-25，本人追加指令"还有很多异常任务清理一下"）

### 13.1 先查原因再动数据：33 条里 19 条是**还在流血的 bug**

生产 `t_dispatch_exception_record` 33 行全 OPEN（`GEOFENCE_EXIT` 19 / `TASK_TIMEOUT` 7 / `UNREACHABLE` 6 / `ZONE_PAUSED` 1），
最老 2026-08-28、最新 **2026-09-25 12:41** ⇒ 当天仍在产生，不是纯历史包袱。逐类归因：

| 类型 | 根因 | 状态 |
| --- | --- | --- |
| `GEOFENCE_EXIT` ×19 | `GeofenceBreachServiceImpl` 取"**所有 ACTIVE 围栏**"逐条判越界，而 `DEFAULT-BOUNDARY` 也是 ACTIVE+BOUNDARY。展示包络只有 **4.74 km²**、受理范围 **47.18 km²** ⇒ 车在合法服务区内正常跑也被记"驶出围栏"。且与受理判据自相矛盾（`OrderEndpointResolver` 明确不吃这层） | **已修并上线**（`872f820`，第四轮部署 13:30） |
| `UNREACHABLE` ×6 + `ZONE_PAUSED` ×1 | 09-23~24 有人框选过"管制区"，把取货点评成不可达。现已解除：`t_traffic_pause_zone` 空表、Redis `fsd:traffic:pause:1` 现值 `[]`（TTL 710 s），当时那批多边形只剩在 `/opt/backups/redis_fsd:traffic:pause:1-20260924.json` | 原因已消失，只剩陈旧行 |
| `TASK_TIMEOUT` ×7 | 09-23~24 卡在 `MANUAL_PENDING` 超 30 min 的历史任务 | 陈旧行 |

本机那 3 条 OPEN 更是**本轮测量自己造的**：`#563` 内容正是"车驶出围栏「展示包络（不参与受理判据）」(DEFAULT-BOUNDARY)"
—— 上面那条 bug 的实物证据；`#564 LOW_SOC`（All idle vehicles are below minimum assignable SOC）是 T1-a 边界实验
（20 台车全压 29% ⇒ 判无可派车）的预期产物；`#565` 是该实验里那单超时。**都不是缺陷**。

### 13.2 处置方式：关闭而非删除

> ⚠ **本节的做法已被本人 2026-09-25 16:00 的后续指令推翻**："以前的记录都删一下" ⇒ 队列改为**直接删除**，
> 见 §14.4。下面保留的是"当时为什么选关闭"的判断与代价分析，不是当前状态。


表本身有完整生命周期字段（`exception_status / resolved_time / resolver_id / resolve_action / resolve_remark`），
删行会毁掉审计链，所以按 `markResolved()` 的列语义**置为 RESOLVED**，`resolve_remark` 里逐条写清"为什么这不是当前故障"。
生产 33 条 + 本机 3 条 ⇒ 两边 `remaining_open=0`。
清理前单独备份了异常表：`/opt/backups/exceptions-pre-clean-20260925-132816.sql.gz`（整库备份同时做了一份）。

> **没走 HTTP 批量接口**（`POST /api/admin/dispatch/exceptions/batch-resolve`）的原因：那需要生产管理员口令，
> 而 `.env` 里的 `FSD_ADMIN_USER/PASSWORD` 后端**根本不读**（`AdminAuthServiceImpl` 只认 `t_admin_user.password_hash`）。
> 代价要说清：这样关闭的行**不产生** `t_dispatch_task_operate_log` 与 `EXCEPTION_RESOLVED` 事件。
> 行数据与人工点"处理"一致，但运营审计事件缺失。
>
> 另外记一次自己踩的坑：第一次 UPDATE 我照旧把 stderr 丢进 `/dev/null`，`exit=1` 被埋掉、
> 表面像执行了其实一行没改（与 §11 那次 20 字节空备份同源）。改成"SQL 落文件 + 不吞 stderr + 回读计数"才确认。

### 13.3 顺带修掉的显示缺陷

异常页每条都渲染成"未知异常类型(TASK_TIMEOUT)"。根因是**两套枚举对不上**：
前端 `exceptionTypeMap` 只有 `TASK_EXECUTE_FAILED/VEHICLE_OFFLINE/EXECUTE_TIMEOUT/STATUS_REPORT_ERROR` 四项，
而后端 `exception_type` 是**裸字符串、没有 Java 枚举**，实际写库 8 类（从 `recordException` 调用点收齐）。
已补齐 8 项中文标签；`Record<ExceptionType, …>` 的类型约束会强制以后新增成员必须配标签，漏写在 `vue-tsc` 就报错。

### 13.4 验证与**未验证**

- 已验：`GeofenceBreachServiceImplTest` 9/9（含新增两条回归：包络必须零异常；`RESTRICTED` 即使编码不带 `ZJF-ZONE-` 前缀也仍告警）；
  后端全量 **541 tests** 全绿；`mvn -o -fae compile spotbugs:check` 七模块 SUCCESS；`vue-tsc` 干净、build 成功、e2e **63 passed**；
  CI 在 `872f820` 三项全绿；**上线后复查**：13:00 之后生产再无新异常行，后端日志唯一一条越界是
  `GEOFENCE_EXIT suppressed … fence=ZJF-ZONE-SVC-01`（真服务围栏、IDLE 无任务被正确抑制），
  `DEFAULT-BOUNDARY` 不再出现 ⇒ 修复确实生效，而不是"没触发"。
- **未验**：异常页渲染后的中文标签**没在浏览器里看到**。本机 `/api/admin/auth/login` 返 **HTTP 500**
  （独立于本次改动的本地环境问题，未追），生产管理员口令我没有。
  静态层面已确认新标签进了构建产物（`statusMap-*.js`）、映射覆盖全部 8 类；**页面实际渲染请你登录后扫一眼**。
- 夹具纠正：`GeofenceBreachServiceImplTest` 原用 `fenceCode="TEST-FENCE"`，而按既有 `resolveScopeCode` 推导它本来就被归成展示包络 ⇒ 改为 `ZJF-ZONE-TEST`。


## §14 第二批：演示可用性（本人 2026-09-25 16:00 追加四条指令）

四条指令：① 以前的记录都删（=§14.4）；② "怎么很多充电桩在外面"；③ "我还是不能看演示，车还是随便在停"；
④ "手机下单界面得优化，参考美团或顺风，用户只需要知道车到哪里"。

### 14.1 先取证：②③ 各是什么性质

| 指令 | 取证 | 定性 |
| --- | --- | --- |
| ② 桩在外面 | 服务围栏 `ZJF-ZONE-SVC-01`（60 顶点、东界 121.128887）与 41 个 ACTIVE 设施做包含判定，**MySQL `ST_Contains` 与独立射线法两套实现同结论**：35 柜里 30 在内 5 在外、6 桩里 4 在内 2 在外，7 个全挤在东界外 300–400 m 一条窄带（`FSD-SWAP-02/04/12/14/35`、`FSD-CHG-02/04`）。生产与本机逐字一致 | **真数据**，不是渲染 bug。且这 7 个的锚点节点（OSM0397/0342/0203/0399/OSMS0263）实测**都在最大强连通分量（685 节点）里** ⇒ 那片地方"250 m 内吸得到、能派单"，是围栏画小了 |
| ② 附带发现 | 从浏览器读**真正传给地图图层**的 props 再算：38 个标签、`swap IN 30 / swap OUT 5`、`vehicle IN 17 / vehicle OUT 3`，而 3 台越界车**叠在同一个坐标** [121.05533, 31.947486] | 与③同源，见 14.3 |
| ③ 演示点不动 | `useDemoMode.ts` 按**编码前缀** `ZJF-PICK-/ZJF-DROP-` 分组，而 `zjf_facility_v2.sql:26-31` 把所有 `PICKUP/DROPOFF/GENERAL` 置了 INACTIVE、接口只返 ACTIVE ⇒ `pickups.length===0` → `resolveDemoRoute` 返回 null → 报错并立刻 `stopDemo()`。后端**没有**任何 demo 专用端点（`back/**/*.java` grep `demo/startDemo/DemoStation` 零命中） | 与③的另一半同源 |
| ③ 车乱停 | `getGeoStandbySpot()` 先找 `ZJF-IDLE-01`（**它也是 GENERAL 站点，被同一条 UPDATE 顺手置灰**）→ 找不到就回退 `application.yml:146-164` 的 `parking-spots P1..P6`，那是**老示意图像素坐标**（x=80..200 / y=700..740，对现役 1600×1854 画布无意义）。`t_parking_slot` 里真车位只有 6 个 STANDBY，且**六个坐标完全相同** | 有实现但数据源被打掉；`repositioning` 那套 M 档只活在离线 `ScenarioBench`，热路径零引用 |

> 仪表在这一步错过两次，都记下来：① 拿 `t_vehicle.current_longitude` 去比经纬度围栏 ⇒ 报"24/24 台越界"，
> 而 SIM 行那一列存的是**像素**（逐行契约 §7.5），这个数毫无意义，车辆越界只能以浏览器实测为准；
> ② 一条 SQL 里把临时表开两次触发 `Can't reopen table`、以及把 WKT 字符串留在聚合表达式里重复解析触发
> `Invalid GIS data`（正解：先 `SET @poly := ST_GeomFromText(...)` 物化一次）。

### 14.2 ④ 移动端追踪图改成乘客视角

- 删掉设施层：`ParkOrder.vue` 不再把 35 柜 + 6 桩喂给追踪地图，`mobileEnergyFacilityStations()` 随之**整体删除**
  （它只有这一个消费方，留着就是死出口）。这张图现在只有：本单取点 + 本单送点 + **被指派那台车** + 路线。
- `OrderTrackingPanel.vue` 传 `:show-level-switcher="false" :show-layer-switcher="false"` 关掉 L0/L1/L2 与图层面板；
  四个 PC 调用点不传 ⇒ 大屏/工作台照旧全开（运营叙事留在 PC）。
- 图例撤掉"车队 20 台 / 补能点 35 处"两片，只保留"位置未知"那片（§7.5 的诚实要求：不画点必须说出来）。
- **判据是翻转不是删除**：`data-swap-markers / data-charging-markers / data-facility-points` 三个字段留在
  DOM 上，v14 钉它们**恒为 0** —— 谁把设施层接回来，CI 立刻红。
  另加一条新页面门：被指派那台车没有真经纬度时，`data-vehicle-markers=0` 且必须显示"1 台位置未知"。
- ⚠ 这片读数原来挂在 `div.map-legend` 上；两片 chip 都撤掉后它变成零高度，`toBeVisible()` 当场把
  "为了测试留一个隐形 div"这件事照出来了 ⇒ 现在挂在常驻可见的 `.map-shell` 上，
  且**不能**挂进 `v-if="geoMapAvailable"` 的地图容器（CI 没配高德 Key，那种钩子会整个消失）。

### 14.3 ③ 空闲车回真车位 + 演示单改坐标下单

- **车位 seed 扩到 20 个真位**：新增 `back/sql/seed/zjf_standby_slots.sql`（P1..P20）。选点规则写在文件头：
  以 `OSM0017`（现役基地锚点）为圆心，取**在最大强连通分量里**的 ACTIVE 路网节点按距离升序前 20 个，
  像素与经纬度**成对取自同一个节点**（不在脚本里重算 GCJ↔像素互转，那份实现只该有 `ParkGeoTransformService` 一处）。
  实测：20 个车位、20 个互不相同的坐标、**20/20 全部在服务围栏内**（越界 0）。
  已登记进 `verify-geo-init-paths.sh` 的 `GEO_SEEDS`（现 9 份）与 `pack-deploy-tree.sh` 的 seed 反证清单。
- **仿真器待命点三级顺序**改为：① `ParkingFacilityService.reserveStandbySlot()` 原子占 `STANDBY` 位
  （幂等：已经占着就续用，否则空闲态每 tick 问一次会让车在车位之间来回跳）→ ② `ZJF-IDLE-01`（若仍 ACTIVE）
  → ③ yml 那组假坐标降为最后兜底。车行还不存在的首次铺队（`ensurePilotFleet`）走 `listStandbySlots()` 按序号轮转。
  释放沿用既有生命周期（`releaseByVehicle/releaseReservation`，派单时已经会调）。
- **演示单改走 V64 坐标入口**：`useDemoMode.ts` 的取送货点改成后端自己发布的路网落点 `GEO_POINT` 的坐标
  （`pickupLng/Lat`、`dropoffLng/Lat`），不再吃 `stationId`；凑不满 2 个落点时报**带原因**的话
  （"需要 ≥2 个路网落点 GEO_POINT"）而不是静默自停。
  `v6-critical-flows` 两条：一条钉 POST 体是坐标且 `pickupStationId` 必须 undefined，
  一条用**现网真实站点形态**（只有总仓库）钉"报错可见且一单没下"。

### 14.4 异常队列：从"关闭"改成"删除"（本人 2026-09-25 追加指令"以前的记录都删一下"）

| 项 | 实测 |
| --- | --- |
| 删除范围 | `exception_status='RESOLVED'` 的全部行（谓词写成状态而不是"id < X"，OPEN/ESCALATED 结构性不会被误删） |
| 生产 | 33 行（2026-08-28 14:56 → 09-25 12:41；GEOFENCE_EXIT 19 / TASK_TIMEOUT 7 / UNREACHABLE 6 / ZONE_PAUSED 1）⇒ 删后 `COUNT(*)=0` |
| 本机 | 504 行（09-21 → 09-25；GEOFENCE_EXIT 247 / LOW_SOC 136 / NO_VEHICLE 70 / UNREACHABLE 34 / TASK_TIMEOUT 16 / NO_MATCHING_VEHICLE 1）⇒ 删后 `COUNT(*)=0` |
| 删除前备份 | `/opt/backups/fsd_core-20260925_155828.sql.gz`（1.63 MB、`gzip -t` 通过、50 张 `CREATE TABLE`、尾部 `Dump completed` 在） |
| 为什么 UI 只显示 14 条而库里有 33 条 | 异常页列的是**挂了订单**的行：生产 `SUM(order_id IS NOT NULL)=14`、`with_task=31`、`orphan=2`。截图里"共 14 条"与库里的 33 不矛盾，是两套口径 |

**代价（与 §13.2 同一条，现在更彻底）**：删除连审计痕迹一起抹掉——被删行上的 `resolver_id='system-cleanup'`、
`resolve_remark`、`agg_count` 聚合次数都不再可查；历史原因只能在本文档 §13 里读到。
生产演示夹具本来就要重置，本人明确要"删"，故按指令执行并在此记账。

### 14.5 ② 的处置：扩围栏东界（本人 2026-09-25 17:5x 裁"范围扩大把设施包含进来"，已落库）

`zjf_service_area.sql` 抬头明写"由 `service_area_from_snapping.py` 生成…不要手改"，所以"扩东界"只能走重跑。
重跑又撞上一个工具缺陷：`load_fences()` 把所有 `UPDATE … status='DISABLED'` **全局**套用，
于是任何排在 `zjf_service_area.sql` 之后的输入片都会被自己那条 UPDATE 误杀 ⇒ 已把它改成
**按 seed 先后顺序重放**（=MySQL 的执行语义），回归干净：默认两份 seed 仍是 1 片 / 47.16 km²。
新增输入 `back/sql/seed/zjf_zone_east_input.sql`（东侧 121.128887–121.1345 的窄带，运行时被 UPDATE 停用、
只参与生成）后实测：

| 口径 | 现值 | 扩东界后 |
| --- | --- | --- |
| 可下单面积（R=250） | 47.16 km²（文档现记 47.18） | **50.08 km²**（+2.92） |
| 外沿多边形 | 55.05 km²（seed 抬头旧记 55.42） | 57.32 km² |
| 设施越界 | 5 柜 2 桩 | **0**（46/46 全在内，`ST_IsValid=1`、68 顶点、环闭合） |

**为什么没直接落库**：`trip_mileage_sampler.py` 与生成器**共用同一份围栏加载器**，
面积是产能口径的输入 ⇒ 扩围栏会连带改动对外的 `12,998 m/单`、`2.51×` 那一组数字，
而 §7.7 禁止重跑 bench。这一改的爆炸半径远超"7 个图标进框"，留给本人裁：
① 认账重跑（面积 + 产能口径一起换）；② 回退成"把 7 个设施坐标挪进围栏"（47.18 不动）；
③ 维持现状（对外只说"围栏 = 可下单范围，补能网络是基础设施、不必在其中"）。

### 14.6 追出来的第二层：等桩的车根本不动（`70c70b0`）

灌完车位后本机复测仍是 **20 台里只有 7 台占到位、16 台停在假坐标区间** ⇒ "车随便停"不止待命点一处。
顺着查是两个原因叠在一起：

| # | 缺陷 | 证据 |
| --- | --- | --- |
| ① | `listZjfChargingSpots()` 按 `stationCode.startsWith("ZJF-CHG")` 过滤，而设施 v2 之后基地充电桩编码是 `FSD-CHG-*` ⇒ **恒返回空**，`getChargingSpot` 一路回退到 yml 那组假像素坐标 | 活库 ACTIVE 充电站编码逐条是 `FSD-CHG-01..06`；`ZJF-CHG` 前缀 0 命中 |
| ② | `routeToCharging()` **无条件** `releaseByVehicle` 之后才去抢桩；抢不到就置 `WAIT_CHARGING` 原地返回，而 `WAIT_CHARGING` 每 tick 只是重试抢桩、不会移动 ⇒ 车停在"决定去充电那一刻"的位置上 | `idleChargeWhenNoDemand` 开着时**全部**空闲车先进这条路径，而桩位只有 6 个（`t_charging_pile` 挂在 P1..P6 上）⇒ 一次实测 13–16 台是这个形态 |

**改法**：① 改按 `stationType='CHARGING_STATION'` 判，旧前缀只留历史兜底；② 释放挪到"真抢到桩"之后，
抢不到则**带着自己的待命位回到位上等**（`atStandbyPoint` 守卫，避免到位后每 tick 重规划零长度路线）。

**复测（本机，tick=500 ms）**：停在假坐标区间（x∈[70,210] ∧ y∈[690,750]）的车 **16 → 3**；
20 台 IDLE 车落在 **11 个不同坐标**（此前是叠在 6 个凭空点上）。
闸门：`fsd-dispatch` 426 + `fsd-admin-api` 96 tests 全绿，`spotbugs:check` 干净。

> **还剩最后一层没动，因为它是策略选择**：`idleChargeWhenNoDemand=true` 让 20 台空闲车全去抢
> **6 个桩位**，抢不到的回待命位等 —— 画面正确，但"整个车队都在充电"本身失真。
> 要么演示时关掉它（车回待命位，桩位留给真低电的车），要么扩桩位。与 §14.5 一并待裁。

### 14.7 第三层：抢位失败会顺手释放已占的位（`0c3dd40`，已上线）

②改完后本机仍只有 7/20 占到位、生产 6/20 ⇒ 还有一处。真因在 `ParkingFacilityServiceImpl.reserveSlot()`：
方法**开头**无条件 `releaseReservation(vehicleId)`，于是"试着去抢一个桩位"这个动作本身
就把该车已经占着的待命位释放掉；而调用方只在 `standbyPoint == null` 时才重新取位 ⇒ 位丢了不再补。

改法：条件更新先跑，失败立即返回（一行都不动）；成功后再释放该车的**其它** RESERVED 位。

| 度量 | 改前 | 改后 |
| --- | --- | --- |
| 本机：占到位的车 | 7/20（13 台无位） | **20/20，20 个不同坐标** |
| 本机：停在假坐标区间的车队车 | 16 | **0**（剩下的 3 台是 `PARK-01..03`，`UNAVAILABLE/OFFLINE` 的历史夹具行，不在 ZJF 车队里） |
| 生产（第 9 轮上线后约 3 分钟） | 6/20 | **20/20 占位、20 个不同坐标**（6 OCCUPIED + 14 RESERVED），后端 ERROR 0 行；20 台 IDLE 落在 17 个不同坐标（3 台仍在开过去的路上） |

回归钉两条：抢位失败时 `t_parking_slot` 只被更新一次且充电桩一行不动；成功时才释放其它位。
`fsd-dispatch` 428 tests + `fsd-admin-api` 96 tests 全绿，`spotbugs:check` 干净。

### 14.8 第 7–9 轮上线（同日，纯增量）

| 轮 | 时间 | 内容 | 取证 |
| --- | --- | --- | --- |
| 7 | 16:52–16:56 | 移动端乘客视角 + 演示坐标下单 + 车位 seed 上生产 | 包 sha 两端 `a6622efdf30eb0b8`；rollback×2 `165235`；库备份 `165235`（1.6 MB/50 表/Dump completed）；**灌 seed 前先 `docker stop fsd-backend`**（铁律），灌后回读 20 位 20 个不同坐标；`.env` mtime 未动；`[OK] 后端健康 UP` |
| 8 | 17:20–17:24 | 充电桩死前缀 + 等桩不移动 | 包 sha `eebf6c0650d857f2`；rollback `172015`；落位前 grep 断言 `atStandbyPoint` 在包里 |
| 9 | 17:37–17:41 | 抢位失败不再释放已占位 | 包 sha `11d6950b3babe7d6`；rollback `173754`；断言 `releaseOtherReservations` 在包里 |

**上线后在生产页复量移动端**（全新浏览器上下文，绕开 §11.2 那条 SW 尾巴）：
`data-vehicle-markers=1`、`data-swap-markers=0`、`data-charging-markers=0`、`data-facility-points=0`，
L0/L1/L2 与图层面板均不在 DOM 里，图上只剩三个标签：`取 FSD-HUB-01` / `送 GEO-OSM0130` / `ZJF-AV · 89%`。
> ⚠ 第一次量仍拿到旧壳（`swap=35`、面板在）—— 就是 §11.2 记的那条：`sw.ts` 的 `NavigationRoute`
> 把 HTML 也放进了 precache，**老访客固定慢一次导航**。演示前务必刷两次。

---

> **本文件的记录惯例（2026-09-25 更新）**：《已完成工作记录》已退场，执行细节**就地写进本文档的 §10–§13**，
> 不再往外部记录文档迁；`[x]` 行只留"一行结论 + 指回本文档内的证据节"。
> 已删文档不恢复，内容按路径可查：`git log --diff-filter=D -- docs/`。死链守卫：`node scripts/check-doc-links.mjs`。
> 服务器侧动作（env 进 `.env`、seed/迁移上生产、生产库写操作）由本人执行或个案授权后执行。
