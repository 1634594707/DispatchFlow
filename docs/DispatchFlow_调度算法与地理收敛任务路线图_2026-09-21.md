# DispatchFlow 调度算法与地理收敛任务路线图

**日期：** 2026-09-21　**版本：** v2（v1 的超集，同一文件更新，不另开新档）
**范围：** 在现有仓库内改，不新建项目、不重写平台。不含工期，只含任务与验收。
**目标：** 对齐目标岗位六大调度模块与六项算法方法；引入 Jev（TypeSafe System One）与 Spring AI + Agent 层；收敛前端功能与后端架构。

---

## 0. 证据基线（全部实测，非推断）

### 0.1 体量

| 项 | 实测值 |
| --- | --- |
| Java main / test | 572 文件 39,320 行（均值 69 行/文件）／10,243 行 309 个 `@Test` |
| 前端 | 43,676 行 / 46 视图 / 24 个 api 模块 |
| 后端 API 面 | 31 个 Controller |
| Python | geo-py 1,436 行 + `scripts/ml` 2 脚本 |
| 迁移 | 53 文件（V01–V51 + V13b + V20b），Flyway baseline 在 V20 |
| 派单端到端 | P50 13 ms / **P95 277 ms** / P99 331 ms（100 车 500 单 1000 节点，REAL 口径，H2 内存库） |
| Redis 往返 | P50 165 µs / P95 356 µs |

### 0.2 本地库实测（`fsd-mysql`，只读 `SELECT`，2026-09-21）

| 项 | 实测值 |
| --- | --- |
| Flyway 已应用 | 末条 **V47**（代码有 V51）→ 本地落后 4 个迁移 |
| 围栏 | 6 条 ACTIVE：1 × `DEFAULT-BOUNDARY`（不派单）+ 5 × `ZJF-ZONE-*`（可派单）。**无脏数据** |
| 可派单面积 | 5 个围栏合计 **0.612 km²**，并集外接框 **1.613 × 0.500 km** |
| 展示范围面积 | `DEFAULT-BOUNDARY` **17.33 km²**，外接框 5.478 × 6.192 km，**填充率 51.1%**，无自交，顶点 7/8 两处转向翻转 |
| **两者比值** | 展示范围是派单范围的 **28.3 倍**；派单面积只占展示的 **3.53%** |
| 路网 | 79 节点 / 124 路段（全部 ACTIVE、全部 BIDIRECTIONAL）；路网地理跨度 **1.793 × 0.586 km** |
| **路网窄的真实原因** | `data/map.osm` 的 `<bounds>` 为 `minlat=31.9617 maxlat=31.9659 minlon=121.0674 maxlon=121.0840` → 提取框只有 **1.568 × 0.464 km = 0.727 km²**。派单围栏 0.612 km² 已占其 **84%** —— 范围窄不是设计选择，是**当初 OSM 就只抠了这么一条** |
| **高德路径 API：key 已填，但两处断链** | `fsd.amap.driving.enabled` 默认 `true`、`base-url=restapi.amap.com/v3/direction/driving`、`timeout-ms=4000`、`cache-ttl=3600`。`FSD_AMAP_WEB_SERVICE_KEY` **在 `.env`、`.env.production`、`front/.env.local` 中均已填写**（本轮核实，只查存在性未读取值）。真正的断链是两处：① 只有 `docker-compose.prod.yml:165` 透传该变量，**根 `docker-compose.yml` 与 `back/docker-compose.yml` 未透传** → 本地起服务时 `AmapRoadRouteService.java:56-61` 的 `isAvailable()` 仍为 false；② key 有效 ≠ 路径参与派单，见下一行。**key 是否真的可用（配额/域名白名单）本轮未测**——网络校验被拦，需本人自行执行一次 driving 请求 |
| **派单热路径不使用路径服务** | `RoadRouteService` 的调用方只有 admin 校验/健康度与仿真器；派单选车用 `parkRoutePlannerService.isReachable/buildRoute`（自建 DB 图 A*），与 `ChainedRoadRouteService`（`@Primary`）**不在同一条链上** |
| **直线兜底守卫是死代码** | `RoadRouteResult.java:77 isForbiddenFallback()` 全仓**零调用** → 路径不可达时静默退化为 `STRAIGHT_LINE`（空 polyline + 0 米），车会画成穿墙直线 |
| **路网数据缺陷** | **24/79 节点无经纬度**；**124/124 路段无 `polyline_geojson`** |
| 限速构成 | 15 km/h × 79（SECONDARY）、20 km/h × 29（ARTERIAL）、10 km/h × 16（SERVICE_ROAD）→ 加权均速 **15.52 km/h** |
| 站点 | 9 ACTIVE + 4 INACTIVE；装货均值 **210 s**、卸货均值 **264 s**、充电 **1,800 s** |
| 设施容量 | 待命位 `ZJF-IDLE-01` capacity **20**；充电桩 **6**（3 FREE）；车位 **6** |
| **坐标叠置** | `121.080681, 31.960337` 上叠 **14 个对象**：`ZJF-CHG-01` + `ZJF-IDLE-01` + 6 车位 + 6 充电桩（entry/exit 全为 `RN27`） |
| 车辆 | 3 台，全部 `current_longitude/latitude = 668.437 / 624.45`（**像素值，非经纬度**），`last_report_time` 停在 2026-08-22 |
| 业务数据 | `t_dispatch_task` 0 行、`t_order` 0 行、`t_fleet_telemetry_point` 0 行、`t_charging_session` 3 行 |

> **结论：本地库是空壳且落后 4 个迁移，当前不可用于产出任何算法结论。** 车辆遥测过期会直接触发 `TELEMETRY_STALE` 拒派。

### 0.3 对早期结论的两处撤回

1. 撤回"用 `t_fleet_telemetry_point` 回归能耗 wh/m"：该表只由 `RealFleetAdapter.java:83` 写入，vda5050 `enabled:false`（`application.yml:451`），仿真只写 Redis。**无真车 = 空表，监督式能耗模型不可做。**
2. 撤回"库里可能有遗留脏围栏"：实测 6 条，语义与 `ParkGeofenceServiceImpl.java:210-223` 分层规则一致。问题在**尺度差 28 倍的两层被画成同一层**，不在脏数据。

---

## 范围声明：冻结清单与主链路白名单（全程有效）

### 冻结清单（本路线图内不碰）

- [ ] `front/src/views/vehicle/Tracking.vue`（3,193 行）—— **例外**：仅允许 §7.6 与 §6.3 列出的坐标解析与地图层改动
- [ ] `front/src/views/digital-twin/Index.vue`（1,611 行）—— **例外**：§7.6 允许其降级为轨迹回放抽屉
- [ ] `back/fsd-dispatch/.../service/impl/ParkPilotSimulationServiceImpl.java`（1,405 行）—— **例外**：M4 接线时允许改其预测调用点；§7.6 允许删除 `PARK-*` 示意池分支。**本体不得删除**：没有真车，它是地图上唯一动力源
- [ ] 前端视觉重构、主题、字体、PWA、Capacitor 安卓壳
- [ ] 微服务拆分、换框架、换 ORM

### 主链路白名单（只在这些文件内动算法）

- [ ] `dispatch/DispatchVehicleAssignServiceImpl.java`
- [ ] `service/impl/DispatchStrategyRuntimeServiceImpl.java`
- [ ] `service/impl/DispatchTaskServiceImpl.java`
- [ ] `service/impl/ChargingSessionServiceImpl.java`
- [ ] `service/impl/DispatchAutomationRuleServiceImpl.java`
- [ ] `service/impl/ParkRoutePlannerServiceImpl.java` + `road/ParkRoadGraph.java`
- [ ] `fleet/policy/FleetChargePolicyImpl.java`
- [ ] 新增：`DecisionPolicy` 族（§2.1）、批量撮合、决策快照、`fsd-agent` 模块
- [ ] `scripts/ml/*`、`geo-py/fsd_geo/*`、`data/`（OSM 提取）
- [ ] 前端仅 `views/workbench/`、`views/analytics/`、`maps/`、`composables/useDeliveryGeo.ts`、`stores/realtime.ts`

范围外一律不改；确需改动时先在本文件追加条目，再动手。

---

## 1. 规模定义：用多少车、多大范围

### 1.1 单车产能推导（基于 §0.2 实测参数）

直线距离由 9 个 ACTIVE 站点坐标算出（haversine）：基地→取货 **675 m**、取货→卸货 **739 m**（最远 1,363 m）、卸货→基地 **581 m**。

| 假设 | 取值 | 依据 |
| --- | --- | --- |
| 绕行系数 | **1.3（假设，非实测）** | 124/124 路段无 `polyline_geojson`、24/79 节点无坐标 → **无法从数据算出**，必须先补 §1.5 的数据才能替换 |
| 平均速度 | 15.52 km/h | 路段限速加权，实测 |
| 可用 SOC | 70%（100%→30%） | `minAssignableSoc=30` |
| 耗电 | 150 m / 1% SOC | `FleetEnergyProperties.java:43`，**注意 §7 的 `toEnergy` 缺陷使该值不可配** |

| 结果 | 数值 |
| --- | --- |
| 单趟路径长度 | **1,704 m** |
| 单趟耗时 | **988 s ≈ 16.5 min**（行驶 6.6 + 装卸 7.9 + 半程空驶） |
| 单次充电可完成订单 | 10,500 m 续航 ÷ 1,704 m = **6.2 单** |
| **单车持续产能** | **2.81 单/车·小时**（含充电占空） |

### 1.2 设施给出的硬上限

| 约束 | 计算 | 车队上限 |
| --- | --- | --- |
| 充电桩周转 | 6 桩 × 3600/1800 = 12 次充电/小时；单车每小时需 `2.81/6.2 = 0.453` 次 | **26 台** |
| 待命位 | `ZJF-IDLE-01` capacity_limit = 20 | **20 台** |
| 车位 | 6 个物理泊位 | 影响夜间停放，不影响在途 |

> **当前设施下可支撑的车队上限是 20 台（待命位 capacity=20 先卡住；把它提到 28 即解除，桩侧上限 26 台仍在）。不是 100 台，更不是 500 台。**

### 1.3 三档规模（后续所有实验与表述都按档标注）

| 档 | 车辆 | 站点 | 派单范围 | 充电桩 | 待命位 | 用途 | 允许出的结论 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **S 现况** | 3 | 9 | 0.612 km² | 6 | 20 | 冒烟回归 | 无（仅功能正确性） |
| **M 主战场（定为 20 台）** | **20** | **13**（9 现役 + §1.8 新增 4） | **≈1.35 km²**（4 个业务片区并集，§1.6/§1.8） | **6（不扩）** | **28**（capacity 20→28） | **算法对照实验默认档** | 撮合收益、错峰补能、预置收益、regret |
| **L 规模档** | 40 | ≥22 | ≥1.9 km² | **≥10** | ≥48 | 只做压测与退化观察 | 时延曲线、MAPF 冲突率、吞吐上限 |

**M 档定 20 台的依据（三条同时成立）**：

1. **设施零改造**：20 台充电需求 `20 × 2.81 ÷ 6.2 = 9.06 次/小时`，现有 6 桩周转 12 次/小时 → 占用率 75%，**不用新增桩**。唯一要改的是 `ZJF-IDLE-01` 的 `capacity_limit` 20→28（一个数字），否则满载无待命位。
2. **密度合理**：20 ÷ 1.35 km² = **15 台/km²**，落在园区短驳 10–25 台/km² 的中间带，MAPF 时空预约（`horizonBuckets=6`）冲突不失控。
3. **撮合有戏**：20 × 2.81 = **56 单/小时**，此时并发待派单量 8–14 单，正是"批量撮合 vs 贪心"差异最显著的区间。

**为什么不停在 15 台**：15 台吞吐 42 单/小时，待派池常只有 3–5 单，匈牙利与贪心的差异小于运行间噪声——**实验做不出结论**。20 台是"能出结论"的最低档。

**要到 30 台的前置（防止随口扩）**：充电需求 13.5 次/小时 > 现有 12 → **必须新增 ≥1 个真实内部充电点，坐标需现场确认**（§1.8 的限制）；待命位 ≥36；范围 ≥1.4 km²。**内部桩位未确认前不建议超过 26 台**（6 桩硬上限）。

> **§13.10 已把这句打穿，读 §1.3 时一并看**：M3 仿真在 **20 台（不到 30 台）** 就测出 2 小时 68 次补能（34 次/小时）对 6 根桩、**209.7 车·分钟**想补没空位。原因是这句按"一次充到满"算需求，而生产阈值是"充到 `chargeCompleteSoc=90` 就恢复派单"的**多次小额补能** —— 需求次数被低估了近 3 倍。"6 桩不扩"在 M 档不成立。

**L 档 40 台的最低前置**：范围 ≥1.9 km²、待命位 ≥48、**充电桩 ≥10**、路网节点 ≥300。扩范围必须先补路网（§1.6 路 A），**不能靠画大多边形**。

> **§13.20 的实测退化曲线支持"≥10 桩"这一条，但不是它的证明**：在现役 6 桩、范围不变的前提下把车队与需求同比放到 40 台/112 单每小时，补能阻塞 **1313.7 车·分钟**（=120 分钟窗口里平均 11 台车常年堵在补能上），而完成率不退化（0.9537）—— 即桩侧压力**不出现在完成率里**。所以"≥10 桩"由仿真反推成立、量级吻合，但真做 L 档仍要按 §13.20 的限定重测（范围与桩数都得先动）。

### 1.4 500 台的口径（只用于对话，不用于宣称）

按 §1.1 的产能线性外推到 500 台：

- 吞吐 **1,405 单/小时**
- 充电需求 226.6 次/小时 → **≥114 个充电桩**
- 服务泊位 1,405 ÷ 7.6 单/泊位·小时 → **≥185 个装卸泊位**
- 维持 25 台/km² 密度 → **≥20 km² 派单范围**
- 热路径时延：当前 P95 277 ms 的成因是每台候选车重查全量路网（`ParkRoutePlannerServiceImpl.java:133-148`），500 台候选下**必须靠图缓存 + 预筛把候选集降到常数级**，否则不是线性退化而是平方退化

> 面试用法：给出这套推导和"我在 M 档 15 台上验证了算法、在 L 档 40 台上验证了退化曲线、500 台我只给出外推所需的前置条件"。**这比暗示自己有 500 台数据强得多，也抗追问。**

### 1.5 规模成立前必须补的数据（阻塞项）

- [ ] 24 个无经纬度节点补齐坐标（OSM 已有工具链：`scripts/carla/osm_to_pilot_geo.py`、`data/`）
- [ ] 124 条路段补 `polyline_geojson` → 才能把绕行系数从假设 1.3 换成实测值，并让"路段长度"进入成本函数
- [ ] 全部路段 `direction = BIDIRECTIONAL` 与现实不符 → 至少把单行段与时间窗限制标出来（`access_state`、`turn_restriction` 列已存在但无数据）
- [ ] 车辆 `current_longitude/latitude` 的语义污染修正（§7.2）

### 1.6 扩范围只有三条路，描点不在其中

**描点能描的是站点与围栏，描不出可通行路径。** 车要沿街道走，路径来源只有三种：

| 路 | 做法 | 代价 | 结论 |
| --- | --- | --- | --- |
| **A. 重新提取 OSM 建图** | 按新 bbox 重取 `data/map.osm`，跑既有工具链（`scripts/carla/osm_to_pilot_geo.py` → `data/pilot_osm_geo.json` → 节点/路段入库） | 一次性建图；"24 节点无坐标 / 124 路段无 polyline"的问题按面积比例放大；**派单每次全量重查路网（`ParkRoutePlannerServiceImpl.java:133-148`）会从优化项变成阻塞项** | **推荐打底**：离线可跑、来源与许可明确（OSM/ODbL）、面试问"路网哪来的"有确定答案、结果可复现 |
| **B. 派单改走高德路径 API** | 填 `FSD_AMAP_WEB_SERVICE_KEY`，把派单路径源从自建 DB 图切到 `ChainedRoadRouteService`（`@Primary`，顺序 amap → localGraph → 直线） | 热路径时延受公网支配（`timeout-ms=4000`，而当前 P95 仅 277 ms）；配额与不可复现；**必须先修 §0.2 的直线兜底死守卫**，否则不可达时静默画穿墙直线 | **只做对照**：用高德真实路网距离 vs 自建图距离做差异分析，不进主链路 |
| **C. 画大多边形** | 直接改围栏坐标 | 派单立刻大面积 `UNREACHABLE`（图里没有那些路） | **禁止**。V44 那个 17.33 km² / 填充率 51.1% 的假包络就是这条路的产物 |

**目标范围（v4 定稿：坐标来自高德实测，见 §1.9）**

| 层 | 定义 | 尺寸 |
| --- | --- | --- |
| **L1 展示外包络** | 四条**命名街道**围合的整块：西 **大岛西路** `≈121.0705`、东 **震蒙大道** `≈121.0880`、北 **现代大道** `≈31.9695`、南 **圩角河路—圣绣路** `≈31.9575` | **1.65 × 1.33 km = 2.19 km²** |
| **L1 自动派单围栏** | 展示块内的 **4 个业务片区**并集（门市交易区 / 现役南排带 / 叠石桥物流园区 / 东北快递带） | **≈1.35 km²** |
| **OSM 提取框** | 展示块每边外扩约 250 m：`minlon=121.0680 maxlon=121.0905 minlat=31.9550 maxlat=31.9715` | **2.12 × 1.82 km = 3.88 km²** |
| **L0 产业带** | 叠石桥 + 川姜/志浩双中心分析圈，**只统计不派单** | 半径 20 km（现役常量，需加"不参与派单"标注） |

**为什么用命名街道而不是画多边形**：这四条路是从高德 POI 的 `address` 字段直接读出来的真实道路名（"大岛西路361号""震蒙大道43号""现代大道180号""圩角河路22号""圣绣路西侧"），**边界可被人现场复核**。V44 那个 17.33 km² 的假包络之所以难看，就是因为它不是任何一条真实边界，是手画的。

**量级预估**：现框 0.727 km² → 232 KB / 79 节点；3.88 km² ≈ **1.2 MB / 400–600 节点**。

**密度核对**：20 台 ÷ 1.35 km² = **15 台/km²**，落在园区短驳 10–25 台/km² 的中间带。

**顺序硬约束**：`M5 的图缓存` 必须在 `M2 的扩范围` 之前或同时完成。否则节点从 79 涨到 1,000+ 时，每台候选车两次全量加载会让派单 P95 从 277 ms 恶化到秒级——**范围大了但演示更卡，适得其反**。

### 1.7 演示效果不好看的真因不是范围

| 现状 | 实测 | 影响 |
| --- | --- | --- |
| 在途车辆 | **3 台**，SOC 全 100，`last_report_time` 停在 2026-08-22 | 遥测过期直接触发 `TELEMETRY_STALE` 拒派 → **车根本不动** |
| 仿真池配置 | `ensurePilotFleet` 分两池：`PARK-*`（示意）与 `ZJF-AV-*`（地理） | 地理池车数由 `parkPilotProperties.simulation.geoVehicleCount` 决定，是演示动感的直接旋钮 |
| 事件流 | `t_dispatch_task` 0 行、`t_dispatch_exception_record` 0 行 | 没有派单、没有异常、没有充电会话 → 地图是静态的 |

> 把范围扩到 2.19 km² 而车不动，只会得到"更大的空白地图 + 3 个静止图标"。**观感优先级：车在动（M0 + 地理池车数提到 20）> 路径沿真实街道（图缓存 + 扩 OSM）> 有事件流（异常/充电/改派在时间轴上发生）> 范围大小。**

### 1.8 描点方案（v4：坐标已实测）

**两条前置事实**

1. **v2 那版坐标表作废**（来源文档已被你判定有问题并删除）。本节坐标是**重新实测的**，与 v2 无继承关系。
2. **本轮已获授权出网**，坐标取自高德 `v3/place/text`（`infocode=10000`，2026-09-21 实测，共 22 次调用），返回即 **GCJ-02**，与库内坐标系一致（`geo-py/fsd_geo/datum.py:25`）→ **可直接入库，无需换算**。

**描点清单（4 个新增派车点，坐标已实测）**

现役 9 个 ACTIVE 站点保持，新增 4 个 → 合计 13，配比 **取货 3 / 送货 6 / 接驳 2 / 待命 1 / 充电 1**。

| 编码 | 名称 | 类型 | 坐标 `[lng, lat]`（GCJ-02） | 高德 POI 名称与地址 | 距基地 | 为什么选它 |
| --- | --- | --- | --- | --- | --- | --- |
| `ZJF-PICK-03` | 家纺城贸易区·取货 | PICKUP | **`121.074588, 31.966227`** | 叠石桥贸易区（购物服务;专卖店）／"中国叠石桥国际家纺城A区北1门西80米" | ≈870 m | 门市最密集处，且**在现役站点带以北 650 m**——现有取货点全在 31.9604/31.9607，根本没覆盖家纺城本体 |
| `ZJF-DROP-05` | 叠石桥物流园区·送货 | DROPOFF | **`121.082213, 31.959503`** | 叠石桥物流园区（生活服务;物流速递;**物流仓储场地**）／"圩角河路" | ≈172 m | 高德在该框内**唯一**的"物流仓储场地"类型 POI，紧贴基地，是天然的入仓点 |
| `ZJF-DROP-06` | 鑫峰家纺仓储·送货 | DROPOFF | **`121.071107, 31.965644`** | 鑫峰家纺仓储店（购物服务;家居建材市场;**布艺市场**）／"大岛路与叠林路交叉口西200米" | ≈1079 m | **全框内最西的仓储点**，把取送对向西拉开；类型是仓储而非快递网点，作 DROPOFF 语义才对 |
| `ZJF-EXPRESS-02` | 顺丰现代大道·接驳 | GENERAL | **`121.081861, 31.969579`** | 顺丰速运／"叠石东路与现代大道交叉口西北220米" | ≈1029 m | **全框内最北**，与 `ZJF-EXPRESS-01`（121.0732, 31.9638）分处西北/东北两端，制造跨区订单 |

四点到基地：**172 m（东南·物流园）/ 870 m（西北·门市）/ 1029 m（北·快递）/ 1079 m（西·仓储）** —— 四个正方向、四段距离，撮合与路径优化才有戏。

**性质声明（必须保留）**：这 4 个点是**演示夹具**——坐标与 POI 是真实实测的，但它们与找家纺**没有签约关系**，不代表真实业务收发货点。对外表述一律说"仿真/演示数据"，不说"线上业务数据"。

**四个业务片区（L1 派单围栏，逐片描）**

| 片区 | 覆盖内容 | 实测范围 |
| --- | --- | --- |
| L1-A 门市交易区（北） | `PICK-03`、贸易区、绣品城A区、C城垫类交易大厅、主市场、三期 | lng 121.0705–121.0834 / lat 31.9636–31.9681 |
| L1-B 现役南排带（中） | 现有 5 个 `ZJF-ZONE-*` 围栏，**不动** | lng 121.0718–121.0889 / lat 31.9597–31.9642 |
| L1-C 物流园区（东南） | `DROP-05` + 海骆驼/中远/天诚/星达物流 | lng 121.0815–121.0877 / lat 31.9575–31.9605 |
| L1-D 东北快递带 | `EXPRESS-02` + 圆通/中通/极兔现代大道网点 | lng 121.0765–121.0820 / lat 31.9683–31.9697 |

**片区顶点仍需在地图上沿真实街道校正**——上面给的是 POI 实测外接框，不是最终多边形。围栏顶点画成斜穿建筑的直线，是 §1.6 明令禁止的路线 C。

**描点工具：产品里已经有，不用写代码。**

| 事实 | 证据 |
| --- | --- |
| 管理端已有地图选点器 | `front/src/components/infrastructure/AmapPointPicker.vue`，挂在 **`front/src/views/infrastructure/StationList.vue:136`**，面板标题就是"点击地图选择站点位置（自动填充经纬度和园区坐标）" |
| 用的是高德 JS API 2.0 | `AmapPointPicker.vue:71-76`，key 与安全密钥走 `VITE_AMAP_KEY` / `VITE_AMAP_SECURITY_CODE`（已确认在 `front/.env.local` 中填写） |
| **产出即 GCJ-02，可直接入库** | 库内几何以 GCJ-02 存储（`geo-py/fsd_geo/datum.py:25 STORED_DATUM = Datum.GCJ02`），非 GCJ-02 一律拒绝；高德本身就是 GCJ-02 → **不需要任何坐标换算** |

> 也就是说：**描点 = 打开管理端 → 基础设施 → 站点 → 新建 → 展开"地图选点" → 点一下。** 每个点约 30 秒。

**描点清单（点位与数量由 §1.3 的 20 台档位反推，坐标由你在地图上点）**

现役 9 个 ACTIVE 站点保持，新增 4 个，合计 13：

| 编码 | 名称 | 类型 | 选点要求（不看坐标，看这些条件） |
| --- | --- | --- | --- |
| `ZJF-PICK-03` | 主市场取货点 | PICKUP | 落在主市场建筑群临街侧；门口有可停车的路段；**必须能吸附到 OSM 节点** |
| `ZJF-DROP-05` | 北侧门市送货点 | DROPOFF | 与 PICK-01/02 不同片区，用于拉开取送对 |
| `ZJF-DROP-06` | 东/东北集散送货点 | DROPOFF | 现役 `DROP-02` 偏东南，本次补一个不同方向，让撮合有空间 |
| `ZJF-EXPRESS-02` | 快递接驳点（第二处） | GENERAL | 与 `EXPRESS-01` 分处两端，制造跨区订单 |

配比结果：**取货 3 / 送货 6 / 接驳 2 / 待命 1 / 充电 1 = 13 个 ACTIVE 站点**。

**每个新点必须填的字段（缺一个就会在派单时炸）**

- [ ] `coord_lng` / `coord_lat`：地图选点器自动填充，**不要手敲**
- [ ] `coord_x` / `coord_y`：由坐标变换服务反算，不手填（现役数据里这两列与经纬度混用过，V44 就是例证）
- [ ] `anchor_node_code`：**必须吸附到 OSM 路网节点**。没有吸附点 = `isReachable` 恒 false = 该站点永远 `UNREACHABLE`（`DispatchVehicleAssignServiceImpl.java:250-252`）
- [ ] `delivery_zone = 'GEO_DELIVERY'`（否则被 §7.6 删掉的示意分支语义纠缠）
- [ ] `avg_service_seconds`、`capacity_limit`、`service_hours` 按现场实际填
- [ ] `station_confidence`：地图目视确认门口=中；现场走过=高；只是建筑轮廓里选的点=低

**验收（逐点）**

- [ ] 每个新点都能 A* 通到 `ZJF-IDLE-01`，路径 polyline 顶点数 ≥ 4（少于 4 说明退化成直线，见 §0.2 死守卫）
- [ ] 地图上目视：车标与路径贴在真实街道上，不穿建筑 Polygon（`t_building_block` 有 7 条，会做碰撞校验）
- [ ] 13 个 ACTIVE 站点两两可达率 ≥ 90%，否则回退到可达子集并记录断点

**内部充电桩：不靠地图描，靠现场数。** 公开 POI 的充电站是**社会车辆用的公共桩**，不能当项目内部桩位。§1.3 里"提到 30 台需要新增内部桩"这一项，只有你现场确认真实桩位数量才能推进。

**顺带一个小隐患**：`AmapPointPicker.vue:77-79` 写成 `props.center ?? props.modelValue ? (modelValue.lng, modelValue.lat) : defaultCenter`，`??` 优先级低于三元 → **一旦有人传 `center` 而不传 `modelValue` 就会解引用 null 报错**。当前调用点没传 `center` 所以没暴露。加括号修掉，别留坑。

**围栏动作（同样在地图上描，不写死坐标）**：

- [ ] 新增一个北侧/东侧片区围栏，顶点**沿真实街道逐点描**（不得画矩形、不得画斜穿建筑的直线），包住本次新增站点与其间道路
- [ ] 现役 5 个 L1_CORE 围栏维持不动；新片区与 `ZJF-ZONE-CORE-NORTH` 之间若有道路断点，由 §1.5 的 OSM 重提取解决
- [ ] `DEFAULT-BOUNDARY` 那层 17.33 km² 假包络按 §7.5 删除或重画
- [ ] 站点服务位表 `t_station_service_position`：**在门口/接入点被目视或现场确认前不写入**，避免"有站点无接入点"导致 A* 不可达

### 1.9 高德实测记录与四条结论

**取证方式**：`v3/place/text`（`extensions=all`、`city=海门`、`citylimit=true`），key 从 `.env` 读取（长度 32，值不落文档），**`infocode=10000 / info=OK`**，共 22 次调用，2026-09-21。返回坐标即 GCJ-02。脚本：`C:\Users\Administrator\.qoder-cn\tmp\amap_probe.py`、`amap_scan.py`、`amap_scan2.py`。

| 关键词 | 目标框内命中 | 备注 |
| --- | --- | --- |
| 中国叠石桥国际家纺城（子 POI） | 12 | 凸包面积 **0.135 km²**，外接框 **0.859 × 0.410 km**，`lng 121.074257..121.083349 / lat 31.963624..31.967329` |
| 物流 | 29 | 含**唯一一个** `物流仓储场地` 类型：叠石桥物流园区 `121.082213,31.959503` |
| 快递 | 28 | 集中在**北侧现代大道带**（lat 31.9683–31.9697）与三期一层 |
| 家纺城 交易区 | 8 | 贸易区 / C城垫类交易大厅 / 绣品城A区，集中在 `121.0705–121.0757 / 31.9656–31.9681` |
| 仓储 | 4 | 全部在北侧（`31.9678–31.9739`），南侧 0 |
| 停车场 | 3 | 步行街店 `121.079425,31.964558`、大岛国际酒店 `121.078914,31.966545`、百汇小区 `121.082017,31.968949` |
| **充电站** | **0** | 关键词检索在框内无命中 |

**四条结论**：

1. **现有站点全在家纺城建筑群以南。** 家纺城本体在 `lat 31.9636–31.9673`，9 个现役站点在 `31.9603–31.9638`，**差 600 m 以上**。所以"服务范围窄"的业务真相不是范围小，是**没覆盖到门市本体**——`PICK-03` 就是补这个，这也是演示"好看"的关键：车要开到家纺城门口，不是开到它南边。
2. **AOI 官方边界拿不到。** 三个主 POI 详情均无 `polyline` 字段、`indoor_map=0` → 免费 v3 接口不返回 AOI 轮廓。**这就是服务范围用命名街道围合的原因**，不是偷懒。
3. **QPS 限制真实存在。** 连续调用到第 11 次左右开始返回 `10021 CUQPS_HAS_EXCEEDED_THE_LIMIT`，加 0.6 s 间隔 + 退避重试才跑通。→ **任何把高德接进派单链路的设计必须带限速、缓存与并发闸**，这条给 §1.6 路 B「只做对照、不进热路径」补了实测依据。
4. **框内公共充电站 0 命中。** 不能据此断言现场没有公共桩（关键词检索有局限，可能需 `types` 分类码），但"内部充电桩只能靠现场清点"这条不受影响。


---

## 2. 决策架构：把"算法"变成可插拔的一层

当前打分写死在 `DispatchVehicleAssignServiceImpl.java:353-376`，5 个权重里 2 个是死配置（`DispatchScoringProperties.java:22,31` 全仓无读取点），优先级系数硬编码 0.7/1.3（`:378-385`）。**这是接入任何新算法（学习模型、Jev）的真正阻塞点**——不是缺模型，是没有插槽。

### 2.1 `DecisionPolicy` 抽象

- [x] 抽出接口：`decide(orderState, candidateVehicles[], networkSnapshot, forecast) → RankedDecision`，**纯函数**，不碰 DB、不碰 Redis —— 落为 `core.DecisionPolicy.decide(DecisionInput) → DecisionOutcome`
- [x] 实现 A `RulePolicy`：现有 `:353-376` 等价迁移，行为逐位不变（先由 §7.1 的断言测试钉住）
- [ ] 实现 B `ForecastAwarePolicy`：在 A 之上叠加 `t_energy_forecast` 的站点压力项
- [ ] 实现 C `JevPolicy`：见 §3
- [x] 决策结果统一携带：候选清单、分项分数、命中策略标识、策略版本、耗时、置信度 → 全部进 §7.3 的快照表

### 2.2 三阶段晋级（任何新策略都必须走完）

- [ ] **SHADOW**：新策略只记录、不影响决策；与在位策略逐单对比，产出一致率与 regret
- [ ] **GRAY**：按稳定分桶灰度（`taskId`/`orderId` 哈希，**不用 `ThreadLocalRandom`**，见 §7.2），每单只解析一次策略并向下传递
- [ ] **PRIMARY**：只有 SHADOW + GRAY 两阶段数字达标才允许；回退开关必须一分钟内可切

### 2.3 批量撮合（JD"多目标优化"的落点）

- [ ] 待派池 + 周期撮合新模块：成本矩阵 + 匈牙利（或按节省量贪心）
- [ ] 旧签名 `DispatchVehicleAssignService.java:7` 只接单任务，**不改它**，在旁边加批量入口，配置开关切换，贪心保留为 fallback
- [ ] 多目标证据：权重敏感性扫描 + 帕累托前沿（距离 / SOC / 空闲公平 / 预测压力四维）
- [ ] 收益指标在 M 档 15 台上出：总行驶里程、平均完成时间、峰时未响应单量、撮合自身耗时

---

## 3. Jev（TypeSafe System One）接入设计

### 3.1 定位：影子决策与低频高价值判断，**不进 277 ms 热路径**

量化理由：当前派单 P95 已是 277 ms，官方**未公布时延 SLA**。若公网 RTT 为 100 ms 量级，热路径时延直接翻倍以上。因此 Jev 先以 SHADOW 身份接入，**用实测 RTT 决定它是否有资格进任何在线路径**。

### 3.2 原语映射表

| Jev 原语 | 返回 | 在本项目的用途 | 挂载点 |
| --- | --- | --- | --- |
| **Noul** | yes 概率 | 安全门："该车能否在不充电情况下完成取-送-回桩" | 与 `canCompleteTaskWithSoc`（`:319-351`）并行，低置信回落规则 |
| **Score** | 各等级概率 + confidence | 原子软约束：门市当前占用可能性、路段拥堵体感、司机/站点服务延迟风险 | 权重仍由代码合成（官方 composite scoring 模式），进 `JevPolicy` |
| **Choice** | 选中项 + 各项概率 + confidence | 改派选车、异常处置动作选择、意图路由分类 | 处置台低频决策，不进自动派单 |
| 批量提问 | 一次调用多问题 | 官方实测：13 问批量单次调用 **12.2× 更便宜、10.0× 更快且答案不变** | 影子实验的批处理必须按此写法 |

### 3.3 接入任务

- [ ] `JevPolicy` 实现：`pip install typesafe-sdk` 侧先做 Python 影子服务，或 Java 侧直连 `https://api.typesafe.ai/v1/systemone`（payload `state`/`model`/`questions`，响应 `answers`/`usage`）
- [ ] **state 契约文档化**：喂给 Jev 的状态 schema 与 §7.3 快照表字段一一对应，保证可复现
- [ ] **降级契约**：超时 / 低置信 / 熔断 → 立即回落 `RulePolicy`；这条必须复用 §7.2 修好的熔断（当前 `WebhookDeliveryService.java:94-98` 只开不关，不能拿它当基础）
- [ ] **成本闸**：`usage` 进 Prometheus 指标，设每小时预算上限，超限自动降回 `RulePolicy` 并告警
- [ ] 影子实验三指标：top-1 一致率、regret（相对 §5 的事后最优基线）、单位成本与尾时延
- [ ] 逐条复现官方 `model-jaggedness/jev-1.13` 列出的缺陷，产出"什么规模/什么场景不该用它"的判据
- [ ] **口径纪律**：只引用文档内数字（12.2× / 10.0× / 重排 top-1 5%→18%、top-10 38%→62%）。**新闻口径的 193×、444× 不进简历、不进文档**

---

## 4. Spring AI + Agent 层（对齐"下一代调度系统"）

### 4.1 分层原则

- [ ] 新建 `back/fsd-agent` 模块，**不污染业务模块**；依赖 Spring AI，Java 21 / Spring Boot 3.3.12 满足其版本前提
- [ ] 职责切分：LLM 负责**慢推理与自然语言交互**；Jev 负责**快、结构化、带概率的判断**；`DecisionPolicy` 负责**热路径决策**；确定性代码负责**一切写操作与状态机**
- [ ] Agent **不得直接改状态**：只能调用受控工具，写操作带幂等键（复用 `RedisDispatchEventConsumeIdempotencyService` 与 `t_order_idempotency` 的既有模式），并落决策快照

### 4.2 落地顺序（严格递进，不跳步）

- [ ] ① 工具化：把 8–10 个调度动作注册为 Spring AI tool —— 查询车辆/围栏/预测/快照、创建任务、批量撤销、改派、暂停派单、生成报表
- [ ] ② 助手换代：`AdminDispatchAssistantController.java:23` 自述 "Rule-based voice/text command interpretation" → 换成 Spring AI 编排 + **Jev Choice 做第一级意图路由**，低置信转人工
- [ ] ③ 决策解释：Agent 读 §7.3 快照表，用自然语言回答"这单为什么给了 A 车不给 B 车、差多少分"
- [ ] ④ 建议式自动化：Agent 产出"建议动作 + 置信度 + 依据"，人确认后执行
- [ ] ⑤ 有限自动执行：只允许低风险动作（排序、通知、查询、报表）

### 4.3 前端配套

- [ ] 助手抽屉从"命令解析器"升级为"对话 + 建议卡片 + 一键执行"，每条建议必须展示依据（快照 ID）与置信度
- [ ] 未做 ④ 之前，界面上不出现"AI 自动派单"字样

---

## 5. 仿真实验台（没有真车时唯一能出可信数字的东西）

- [x] 场景配置外置：车辆数（按 §1.3 档位）、需求到达过程、**时段分布**、围栏与路网版本、随机种子 —— 时段分布后来补上了（`Config.arrival` / `ArrivalProfile`，§13.14），默认 `FLAT` 保持既有归档数字不变；真实园区高峰曲线仍是假设
- [x] 固定种子可复现：同配置两次运行逐指标一致
- [x] 指标导出：完成时间、总行驶里程、空驶率、~~SOC 抛锚次数~~、充电排队、派单失败原因分布 —— **抛锚次数在本模型里构造性恒为 0**（全链路 SOC 前置检查不允许接跑不完的单），改导"补能被桩位挡住的车·分钟"，见 §13.10
- [x] **N 次重复 + 置信区间**（当前只有单次压测点值，无法区分改进与噪声——§0.1 的 REAL/FAKE 差异小于运行间方差就是证据）
- [x] **事后最优基线**：给定全局已知信息算每任务最优可选车，作为 regret 分母 —— 明确它只是**下界**（不看 SOC/占用），regret 因此偏乐观
- [x] 假设声明页：绕行系数 1.3、均速 15.52 km/h、耗电 150 m/1% 全部标注为假设及其来源 —— 前两项已换成实测（1.481 / 17.84，§13.9.2），9 条假设随报告落盘
- [x] 规模口径：任何输出都带档位标签（S/M/L），**禁止跨档混用数字**

---

## 6. 前端整合方案（现状：功能杂乱）

### 6.1 诊断（每条都有代码位置）

| 问题 | 证据 |
| --- | --- |
| **5 个页面各自组织地图，两套地图栈并用** | `views/vehicle/Tracking.vue`(3,193 行，唯一用 Leaflet)、`gis/ParkOverview.vue`、`workbench/OperationsCockpit.vue`、`digital-twin/Index.vue`(1,611 行)、`dev/MapPoc.vue`；其余走高德 |
| **有接口没入口** | `api/task.ts:73,77,81` 的 `batchCancelTasks/batchReassignTasks/batchUnassignTasks` **零 UI 调用**，只有 `batchAutoAssign` 接在 `DispatchAssistantDrawer.vue:149`；撤销只存在于 e2e 的裸 `fetch()`（`scripts/e2e/v6-critical-flows.spec.ts:113-126`） |
| **死代码** | `composables/useWorkbenchShortcuts.ts:10`（R/A/M/方向键）**无任何 importer** |
| **不刷新的页面** | 数字孪生只在挂载/切园区取数，1 秒定时器只画时钟（`digital-twin/Index.vue:1113-1135`）；异常中心不订阅刷新（`exception/Index.vue:483-497`）；dashboard 趋势与 analytics 各取一次（`dashboard/Index.vue:260-263`、`analytics/Index.vue:374`） |
| **SSE 断线后永久放弃** | `utils/dispatchStreamClient.ts:17,56` 重连 10 次即止，且无页面可见性恢复（对比 `utils/sseClient.ts:207` 有） |
| **硬编码地理与规模** | `maps/zjfPilotGeo.ts:52` 注释自述分区多边形"与后端一致（**V37 迁移**）"，但 V39/V44 已两轮改动；`fleetSize: 264`（`:87`）；`maps/zjfStationAnchors.ts:39-49` 硬编码 10+ 站点经纬度；L0 圆半径 20,000 m（`zjfPilotGeo.ts:77,79`） |
| **静默吞错** | `stores/workbench.ts:108-112,135-140` 只 `console.error`；`dashboard/Index.vue:159-161` catch 成 `[]`；`gis/ParkOverview.vue:330` interval 内 async 未捕获 |
| **假审计** | `exception/Index.vue:418,466` 写死 `resolverId:'u1001', resolverName:'管理员'` |
| **地图无聚合** | `components/map/AmapGeoMap.vue:285-291` 每个 tick 深 watch 后全量重建 marker |
| **视野取错图层** | `Tracking.vue:1021-1026` 初始视野用 `DEFAULT-BOUNDARY`（17.33 km²）而非派单围栏并集（0.612 km²）→ 车必然挤成一点 |

### 6.2 信息架构收敛：从"按后端模块分菜单"改为"按调度员任务流分三个台"

| 目标工作区 | 吸收的现有视图 | 说明 |
| --- | --- | --- |
| **监控台** | `vehicle/Tracking` + `gis/ParkOverview` + `vehicle/List` + `exception/Index` | 一屏：地图 + 车辆列表 + 异常流；不再三个页面各画一遍图 |
| **处置台** | `workbench/OperationsCockpit` + `task/List` + `task/Detail` + 助手抽屉 | 待派池 + 批量操作 + 改派 + **决策解释（读快照表）** |
| **分析台** | `dashboard/Index` + `analytics/*` + `ChargingReport` + `vertical/OpsSnapshot` | KPI + 预测面板 + 策略对照实验结果 |
| **配置** | `infrastructure/*` + `system/*` | 低频，收进二级区，不占主导航 |
| **移动端** | `mobile/*` | 独立壳，不与管理端共享布局 |
| 降级为抽屉/Tab | `digital-twin/Index`(1,611 行)、`vertical/*`、`field-ops/Tickets` | 保留功能，不保留一级入口 |
| 删除候选 | `dev/MapPoc`、`components/demo/*` | 生产导航内不应存在 |

- [ ] 出目标路由表并与现有 46 视图逐一映射，形成"合并/降级/删除"三态清单
- [ ] 主导航一级项从当前数量收敛到 **≤5**

### 6.3 地图与实时统一

- [ ] 单一 `MapCanvas` + 图层注册表（marker / polygon / polyline / circle / heat），底图只留高德；`Tracking.vue` 的 Leaflet 路径迁移或删除
- [ ] 图层语义按 §7.4 分层（L1 实线可派单 / L1_CANDIDATE_ENVELOPE 虚线带"不参与派单"角标 / L0 圆默认关闭）
- [ ] 视野策略：以**派单围栏并集**为准，不以展示包络为准
- [ ] marker 聚合阈值与规模档位挂钩：M 档 15 台可全量，L 档 40 台起必须聚合
- [ ] 单一 realtime store：SSE 优先 → 轮询兜底 → 断线在界面上可见 → 页面可见性恢复时强制重连；所有页面订阅同一 tick，不允许各自 `setInterval`
- [ ] 取数失败必须产生用户可见状态（"数据已停止更新 · N 分钟前"），禁止 catch 成空数组

### 6.4 契约与清理

- [ ] 地理/站点/围栏/车队规模全部读接口，删除 §6.1 列出的硬编码
- [ ] 前端类型由后端 OpenAPI（`/api-docs` 已有 springdoc）生成，替换手写 24 个 api 模块中的重复类型定义
- [ ] 删除 `useWorkbenchShortcuts` 或接入处置台（二选一，不留死代码）
- [ ] 批量三接口补齐 UI 入口，撤销做成真按钮
- [ ] `resolverId` 改为当前登录用户
- [ ] 魔法数字集中到 `config`：3000 ms、30_000、250 ms、500 ms、`REQUEST_TIMEOUT=10000`
- [ ] 中英文混排兜底统一（`MobileOrders.vue:186-199`、`Tracking.vue:881-898` 等直接渲染英文枚举）

### 6.5 前端性能与验证预算

- [ ] 首屏请求数上限（`ParkOverview.vue:330-332` 现在每 3 秒无条件打 4 个端点）
- [ ] marker / polygon 数量上限与聚合阈值
- [ ] e2e 补齐三个台的主操作路径：**当前 cockpit 自动派车按钮、GIS 总览、数字孪生、移动端取消/取货通知、导出、撤销 UX 全部零覆盖**
- [ ] `scripts/perf/dom-metrics.spec.ts:62-63` 目前只断言侧边栏 DOM 数量，改为断言上述预算

---

## 7. 后端架构优化

### 7.1 决策内核独立

- [x] 抽 `dispatch-core` 包：`DecisionPolicy`（§2.1）+ 成本函数 + 图快照，**纯函数、无 Spring 依赖** —— 落为 `com.fsd.dispatch.core`，并由 `DecisionCorePurityTest` 守住（扫描该包全部 `import`，出现 Spring / mapper / entity / config 引用即失败）
- [ ] 收益：影子对照、离线回放、将来把内核移植到 Python 或独立服务都不必重写

### 7.2 缺陷修复（M1 内容，按严重度排）

- [x] **灰度重掷**：`DispatchStrategyRuntimeServiceImpl.java:57-66` 每次调用各掷一次随机数，而 `DispatchVehicleAssignServiceImpl.java:94`/`:95` 分两次调用 → 同一单可能混用"实验侧能量阈值 + 生产侧权重"。改为每单解析一次并向下传递，分桶键用稳定哈希
- [x] **`toEnergy` 丢字段**：`:102-115` 只拷 3 个字段，其余回落类默认值 → `FSD_FLEET_ENERGY_BUSY_DRAIN_METERS_PER_PERCENT`（`application.yml:387`）在派单链路失效，SOC 校验恒按 150 m/1% 计算；同样波及 `RealFleetSwapCoordinator.java:69`、`ParkPilotSimulationServiceImpl.java:700`
- [x] **死权重**：`weightPriority`、`weightCongestion` 进公式或删除配置与界面项，不许留着不生效
- [x] **Webhook 熔断只开不关**：`:94-98` 命中即 `continue`，唯一归零在 `:149` 成功分支（熔断后不可达），另一处只有管理端编辑 `IntegrationAdminServiceImpl.java:63` → 连续 5 次失败后订阅**永久静默**。加冷却窗口半开探测。**这是接 Jev 的前置条件**
- [x] **MQTT 重连不重订阅**（→ §13.26 已修，但本机无 broker，live 验证待部署环境）：`Vda5050MqttGateway.java:110-111` + `subscribe()` 只在 `connect()` 内 `:128` + `connectionLost` 只记日志 `:51-53` → 断网重连后 FMS 失聪。改用 `MqttCallbackExtended.connectComplete`
- [ ] **坐标语义污染**：`t_vehicle.current_longitude/latitude` 存像素（实测 668.437/624.45，与 V44 的 `coord_x/coord_y` 一致），`DispatchVehicleAssignServiceImpl.java:256-257` 注释自认，而 `VehicleAdminDetailResponse.java:40` 原样透出管理端。二选一：改列名为 `schematic_x/y`，或写合法 GCJ-02 并同步改 `:245-260`
- [x] **MAPF 单位不一致**（→ §13.19 已修）：`MapfRoutePlannerService` 拿 **haversine 米** 除以 `vehicleSpeedPxPerSecond=8.0`，而那个值实际取自 `fsd.park.vehicle-speed-px-per-second` —— 前端动画/仿真器的 px/s。加上本 seed px→米各向异性（横 1.2263 / 纵 0.7390 m/px，86 条边实测比值 0.741–1.226），**每条边的预约只覆盖真实占位时间的 37%–62%（均值 45.8%）**，MAPF 看着在跑其实几乎不挡车。现改成 `vehicleSpeedMetersPerSecond=3.66`（=§13.17 那个实测 13.19 km/h）、`ParkRoadGraph.distanceMetersTo()` 恒为米、A\* 边权同口径。
  **本条另一半仍待本人定口径**：重规划用尽后**仍返回未预约路线并照常派单**（`reserved=false`）—— 该不该拦是 SLA 问题；本轮先把比例变成可观测（`dispatchflow.mapf.reservation{result}`），未改行为
- [ ] **乐观锁是装饰品**：`t_dispatch_task.version` 注释写"乐观锁版本号"（`V01:52`），但全仓 main 无 `@Version`、无 `OptimisticLockerInnerInterceptor`，前端不带 If-Match → 要么接上，要么删列与注释
- [x] **异常处置人**：前端写死 `u1001`（§6.4）→ 已改为真实登录身份（`useAuthStore().user.username` + `displayName`），取不到身份时直接拒绝提交而不是塞假值；`front/src/views/exception/Index.vue` 两处调用点均走 `resolverIdentity()`

- [x] **失败原因标签错位**（本轮 §7.6 实测撞出 → **§13.23 已修**）：`socEligible` 这一层同时混了 SOC、维保、车型、车队池、配送区、载重六个过滤器，任何一条不满足都对外报 `LOW_SOC`。实测两处：100 车规模压测里车号前缀不对 → 报"空闲车辆电量低于可派车阈值"；`tasks/64` 因池子过滤失败也是同一句。要么给 `DispatchAssignFailReason` 加一个"无匹配车辆（约束不满足）"并在 `DispatchFailExplainSupport` 补译，要么把这六个过滤器拆开各报各的。**§7.3 要的"派单失败原因分布"在这个标签下不可信**
- [~] **高峰模式没有任何时间来源**（本轮 §13.12 实测撞出 → **§13.24 已补出口与可观测，两园的实际 cron 仍待本人定**）：`t_peak_mode_state` 两行 park 1/2 的 `schedule_cron` 与 `schedule_end_cron` **全是 NULL**（`SELECT ... FROM t_peak_mode_state`），而 `PeakModeCronScheduler.java:62-74` 的 `shouldFire` 对空 cron 直接 `return false` → 定时器永不翻档。后果：**park 1 永远 `NORMAL`**，`RulePolicy` 里的高峰分支（`peakSocDamping=0.7`、`peakDistanceFactor`）**在真实链路上从未生效**；park 2 反过来永久卡在 `PEAK`，也没有结束 cron 能把它关掉。M4 的错峰返充虽不吃这个信号（它吃 `t_energy_forecast.pressure_p95`，见 `EnergyForecastServiceImpl.java:79-96`），但**任何写"高峰策略已生效"的表述都不成立**。修法二选一：给两行配上 cron（并让结束档可靠触发），或把高峰判定改成需求导出（属 M6 预测线），并给 park 2 的永久 PEAK 一个兜底复位
- [x] **并列裁决没有显式规则** → **§13.25 已修**（总分 → `vehicleCode` 字典序 → `vehicleId`；实测影响面 4/558 = 0.7% 的成功派单）：M 档 20 台实测出现 `score_gap=0.0000 / tie_count=2`（同泊位、同 SOC 的两台车），当前由稳定排序的**列表原序**（即 DB 返回顺序）决定谁中选 —— 结果可复现但语义上是"碰巧"。要么显式规定并列时的次级键（如 vehicleCode / 累计派单数最少优先），要么把它做成有意的公平性轮转

### 7.3 决策可证明性

- [x] 新迁移（V52 起）`t_dispatch_decision_snapshot`：task_id、park_id、候选清单与分项分数、**命中策略标识与版本**、臂标签、路网图版本、撮合算法标识、耗时、`generated_at` —— 实际落在 **V54**（V52 给了列守卫、V53 给熔断冷却），漏斗四列在 **V55**；`task_id` 在首次自动派单路径上仍为 NULL，见 §13.4 已知缺口
- [x] 写入点：`DispatchVehicleAssignServiceImpl.java:200-212` 的 `explanation` 目前只进 response VO（`DispatchTaskServiceImpl.java:364` → `DispatchTaskAssignResponse.java:33`），**落库即丢** → 现由 `selectBestVehicle` 外层统一写快照，成功与失败都留痕，实测有行
- [ ] 任务详情页展示 top-3 候选对比（`views/task/Detail.vue` 已有 `failReasonMsg` 展示位可扩展）
- [ ] 算法侧指标补齐：派单成功率、选车时延、失败原因分布、预测命中、策略降级次数、Jev RTT 与成本。当前 23 项 `dispatchflow.*` 全在 outbox/基础设施/SSE，且 `DispatchLockMetrics.java:10-13` 用裸 `AtomicLong` 未注册 MeterRegistry

### 7.4 状态与消息边界

- [ ] 划清边界：Redis = 运行态缓存（**必须有 TTL 且可从 MySQL 重建**）；MySQL = 真相
- [ ] 两处暂停开关合一：Redis `fsd:dispatch:pause:global`（`DispatchPauseControlServiceImpl.java:10-11`）与 MySQL `DispatchPauseStateService`（`V43:314`）
- [ ] 交通管制加 TTL 并落库：`TrafficZoneControlServiceImpl.java:18,88` 现在裸 `set` 无 TTL，还有 JVM 内存兜底 `:22,96`
- [ ] RabbitMQ：补 DLQ（现三队列裸声明 `DispatchMessagingConfig.java:27-44`，全仓无 `x-dead-letter`）+ 手动 ack + `WebhookDispatchListener.java:24-35` 加异常处理，避免 DB 抛错在 durable 队列上无限重投
- [ ] 无界表归档：`t_fleet_telemetry_point`、`t_webhook_delivery_log`（每次投递最多 3 行）、`t_dispatch_event_outbox`（PUBLISHED 不删）、`t_order_idempotency`；全仓迁移无 `PARTITION BY`
- [ ] 索引补齐：`OpenExceptionEscalationScheduler.java:43-47` 按 `(exception_status, escalated_at, occur_time)` 过滤，表上只有 `(exception_type, exception_status)` + `occur_time`（`V01:114-118`）
- [ ] 多副本（ShedLock / 消除 JVM 内存态）**只在决定主张水平扩展时才做**，否则 README 明确写单副本

### 7.5 地理数据治理（同时解决"迁移太多"与"范围怪"）

**根因：用 Flyway 迁移迭代地图内容。** V21–V47 有 13 个纯 DML 文件，同一批坐标被 `fix`/`snap`/`recalibrate`/`reset` 至少 4 轮（V24 `fix_zfj_station_coordinates`（文件名还把 ZJF 拼成 ZFJ）→ V29 → V30 → V39 → V44 → V45/V46/V47 三连 reset）。

- [x] 把 V21–V47 的地理 DML 收敛为当前态快照 `back/sql/seed/zjf_geo.sql`（幂等 upsert，业务键 `fence_code`/`station_code`/`slot_code`）—— 270 条 upsert，由 `scripts/dev/export-geo-seed.sh` 从实库生成；9 张地理表的业务键上本来就有 UNIQUE 索引，无需改表
- [x] 迁移目录此后只留 DDL；seed 与迁移的执行顺序写进 `back/sql/init/` 与 `docs/DispatchFlow_部署整改任务路线图_2026-09-21.md`（**注：原 `docs/DEPLOYMENT.md` 等 20 份文档已于 2026-09-21 删除，本文件与部署整改路线图是仅存的两份**）—— 顺序已写进 `00-run-migrations.sh` 头部
- [x] 新库初始化路径 = 「V01–V20 基线 + DDL 迁移 + seed」，**新库与已有库两条路径各测一遍** → `scripts/dev/verify-geo-init-paths.sh`，实测两侧指纹逐字节相同
- [x] 回退 `V33`/`V34`（当前各 +18/−2 行，checksum 已漂移，Flyway validate 会阻断启动），幂等保护另开新迁移，`flyway repair` 写进 `scripts/deploy.sh` 前置检查 → 见 §13.1；幂等保护落在 **V52**，repair 需显式 `DEPLOY_FLYWAY_REPAIR=1`
- [x] `CONTRIBUTING.md` 增加硬规则：**禁止修改已应用迁移** → 新增「Migration discipline」一节，并顺手纠正了原文把 `back/sql/init/` 说成迁移目录的错误
- [ ] geo-py 的种子脚本 `seed_from_migrations.py` 靠正则解析 Flyway SQL——**本项收敛后必须同步改**，否则直接失效
- [ ] 消除 14 对象叠一点：同原点设施按 bay 微偏移，或前端聚合为"基地 POI + 计数徽标"（数据层保留真实坐标）
- [ ] 展示范围处置：`DEFAULT-BOUNDARY` 重画成路网可达边界，**或直接删掉这一层**（只留 L1 可派单 + L0 分析圆）。当前 17.33 km² / 填充率 51.1% / 两处凹角，是"范围怪"的直接来源
- [ ] 前端删除硬编码副本（§6.4），`isInsideZjfBase` 分流（`Tracking.vue:732-736`）在坐标语义修正后移除或改成显式越界告警

### 7.6 删除园区示意调度（只删分支，不删仿真器）

**先分清两件事**：`ParkPilotSimulationServiceImpl.java`（1,405 行）**同时驱动两个池**——`ensurePilotFleet("PARK-", vehicleCount, true)` 与 `ensurePilotFleet("ZJF-AV-", geoVehicleCount, false)`（`:165-172`）。**没有真车，删掉这个类 = 地图上没有任何会动的东西。** 要删的是示意池与它的分支代码。

**数据侧其实已经空了**：实测 9 个 ACTIVE 站点 `delivery_zone` 全为 `GEO_DELIVERY`，3 台车全为 `ZJF-AV-*`，无 `PARK-*` 车、无 SCHEMATIC 站点。所以这是**删代码路径，不是删功能**，风险主要在编译期与测试期。

删除清单：

- [ ] 示意池装配：`ParkPilotSimulationServiceImpl.java:165-167` 的 `ensurePilotFleet(SCHEMATIC_VEHICLE_PREFIX, ...)` 调用，以及 `:228-229` 的示意池压力恢复分支
- [ ] 车辆族判定：`PilotFleetSupport.java:9-10`（`SCHEMATIC_VEHICLE_PREFIX = "PARK-"`）、`:20` `isSchematicVehicleCode`、`:46-48` `isSchematicDeliveryStation`、`:1370-1371` 的 `PARK-` 编号解析
- [ ] 派单侧双模分支：`DispatchVehicleAssignServiceImpl.java:102` 与 `:428` 的 `GEO_DELIVERY / SCHEMATIC` 三元判定 → 收敛为只有 GEO 一条路
- [ ] 配置项：`parkPilotProperties.simulation.vehicleCount`（示意池数量）；保留 `geoVehicleCount`
- [ ] 像素坐标路径：`parkXYToGcj02` 及其在 `useDeliveryGeo.ts:vehicleToGeoPosition` 的兜底分支；`t_vehicle.current_longitude/latitude` 改写为合法 GCJ-02（与 §7.2 合并做）
- [ ] 前端：`dev/MapPoc.vue`、`components/demo/*`、`maps/zjfPilotGeo.ts` 中仅服务示意模式的常量；`digital-twin/Index.vue`(1,611 行) 降级为轨迹回放抽屉而非独立页（§6.2）
- [ ] 路线模式枚举：`REAL_ROAD / SCHEMATIC / STRAIGHT_LINE` 中的 `SCHEMATIC`（`RoadRouteResult.java:14`、`RouteAuditEntity.java:34`、`RoadRouteValidateRequest/Response`、`StationEntity.java:73`、`VehicleEntity.java:57`）—— **注意这是历史审计数据字段，删枚举值前先确认 `t_route_audit` 无 SCHEMATIC 行（实测 0 行）**
- [ ] 数据库：`t_station` 的 SCHEMATIC 站点（实测 0 条 ACTIVE，但 4 条 INACTIVE 需核）；`t_park` 2 条记录中是否仍有非 ZJF 园区

**保留清单（明确不删）**：`ParkPilotSimulationServiceImpl` 本体与地理池、`FleetChargePolicyImpl`、`ChargingSessionServiceImpl`、MAPF、围栏与站点服务位模型、`t_parking_slot`/`t_charging_pile`。

**闸门：** 删除后 `mvn test` 全绿、地理池 15 台仍可正常派单与充电、前端无一处引用被删常量。


---

## 8. 任务清单（按依赖顺序）

### M0　环境与工作区收口（阻塞一切）

- [x] 三环境 Flyway 版本对齐：更新为 **代码 V56 / 本地 V56 / 生产 V51（已核，见 §13.3，基线在 V50）**
- [x] 本地库补齐 V48–V51 并复跑 §0.2 查询，记录差异 → 差异见 §13.1；其中"79 节点全部 ACTIVE"一行本身有误，见 §13.2
- [x] 一条命令把本地演示数据重置到"可派单"状态（车辆遥测刷新、SOC 分布化、坐标合法），**脚本须显式禁止指向生产** → `scripts/dev/reset-demo-dispatchable.sh`，实测 `PASS=13 WARN=0 FAIL=0`；坐标一项的处置见 §13.2 第 1 条的修正
- [ ] §7.5 的 V33/V34 回退 + repair 前置 —— 两项均已完成（§13.1），**只剩 tag 基线**：它依附于"提交/丢弃未提交改动"这条待本人裁决的项
- [ ] 提交/丢弃其余未提交改动

**闸门：** 三环境版本一致；本地能派出一单；`mvn -pl fsd-bootstrap -am test` 全绿。

### M1　决策可证明性

- [ ] §7.2 打分算式断言先行（先钉住当前行为，含错误行为）+ 并列裁决断言
- [ ] §7.2 前四项缺陷修复
- [ ] §7.3 快照表与写入
- [ ] §2.1 `DecisionPolicy` 抽象 + `RulePolicy` 等价迁移

**闸门：** 任一历史任务可回答"当时为什么选这台车、差多少分"；每个修复有对照测试。

### M2　地理与设施收敛

- [ ] §7.5 全部
- [x] §7.6 删除园区示意调度 —— **两条尾巴有意留着**：像素坐标路径（`parkXYToGcj02` 兜底）必须与 §7.2 坐标改写同批改，现在删会让地图上没有车；`digital-twin` 降级与 `DemoModePanel`（被冻结文件 `Tracking.vue:133/605` 引用）属 §6.2/M7 的监控台重构。逐条状态见 §13.7
- [x] 地理池车数从 3 提到 **20（M 档，§1.3）**（`FSD_PARK_SIMULATION_GEO_VEHICLE_COUNT` 默认与 `ParkPilotProperties.geoVehicleCount` 都改为 20），`ZJF-IDLE-01` 的 `capacity_limit` 20→28（由 `scripts/dev/reset-demo-dispatchable.sh` 落库并 `--verify` 断言），本地演示数据自洽（§1.7）
- [ ] 按 §1.8 建 4 个新派车站点 + 1 个 L2 展示点，`station_confidence` 如实分级；未现场确认进出口的点**不写服务位表** —— **顺序挪到 M2E 之后**：实测 4 个点里有 3 个落在现有路网外，硬建会得到"假可达"（论证见 §13.6 末）
- [x] 根 `docker-compose.yml` 与 `back/docker-compose.yml` 补透传 `FSD_AMAP_WEB_SERVICE_KEY` —— **根 compose 其实没有 backend 服务**，它只 `include: back/docker-compose.yml`（§0.2 那条"两处未透传"应读作"一处"），已补进 `back/docker-compose.yml`
- [ ] §1.5 路网数据补齐（节点坐标、路段 polyline、方向与时间窗）
- [ ] 设施扩容到 §1.3 M 档所需（待命位 28、桩维持 6）；**桩数不动是 20 台这个档位的前提**，扩到 30 台必须先有现场真实桩位（§1.8 限制）—— **这条前提已被 §13.10 打穿**：M 档 20 台仿真 2 小时里 68 次补能对 6 桩，209.7 车·分钟排队不上。"桩维持 6"要么改口径（少派单/接受排队），要么把新增桩位确认提到 M4 之前

**闸门：** 前端零硬编码；图上能一眼区分"能送/不能送"；~~绕行系数由假设变实测~~ **已达成**（§13.9.2：5486 对实测均值 1.481 / 中位 1.374）；示意调度删除后测试全绿且地理池仍可派单 ✅（§13.7）。

### M2E　扩范围（**依赖 M5 的图缓存，不得先做**）

- [x] 按 §1.6 路 A 重取 OSM：bbox `121.068–121.094 / 31.956–31.972` → 实取 `121.0680–121.0905 / 31.9550–31.9715`，Overpass 200 / 112 KB / ODbL，落 `data/map.expanded.osm`（§1.6 预估 1.4 MB / 400–600 节点，量级同一：**原始抽取** 346 节点 / 321 way 引用；**但"36.99 km"是未裁断的口径，作废** —— 裁断后入库 91 节点 / 113 边 / 24.35 km，见 §13.9.2）
- [ ] 跑既有工具链入库：`scripts/carla/osm_to_pilot_geo.py` → `data/pilot_osm_geo.json` → `t_road_node` / `t_road_segment` —— **这条链不存在**：JSON 只给运行时画图，DB 里是 V38 手写的 5×5 网格。已新写 `scripts/geo/osm_to_road_graph.py` + `back/sql/seed/zjf_road_network.sql`（见 §13.9）；**本条原写的两道关已被实测改写**：连通性不是靠 snapping 修的（ snapping 扫 0–60 m 分量数不变，真因是度数算错，见 §13.9.1），但换了裁断版之后仍有 **4 个分量、最大 85.7%**（§13.9.2）→ 用 `is_largest` 可达子集 + `t_road_node_component` 记录断点处理；**剩下真正卡住的只有画布重定标**（91 节点里 72 个在现有 1300×800 示意画布外，与 §6.4/§7.2/§7.6 同批）
- [ ] 新范围内重新描点：**只描站点与围栏**，路径一律来自 OSM 图，禁止手画路线
- [ ] 围栏按新范围重划（L1 可派单多边形），`DEFAULT-BOUNDARY` 那层假包络按 §7.5 处置
- [ ] 高德路径 API 走 §1.6 路 B 的**对照用途**：key 已在 `.env`/`.env.production`/`front/.env.local` 填好，缺的是 ① 本地 compose 透传（已列入 M2）② **一次真实 driving 请求验证配额与域名白名单**（本轮网络校验被拦，需本人执行）；通过后比较"高德真实路网距离 vs 自建图距离"的差异分布，产出偏差报告；**不接入派单热路径**
- [ ] 接入前先修 §0.2 的直线兜底死守卫（`RoadRouteResult.java:77` 零调用），否则不可达时静默画穿墙直线

**闸门：** 扩范围后派单 P95 不得高于扩范围前实测值；新范围内 A* 可达率 ≥ 扩范围前水平。
→ **2026-09-21 实测判定（§13.11）：未通过。** P50 78.19 [72.52,83.87] → 110.69 [95.29,126.09]（区间分离、+42%），均值同向 +45%，P95 因只有 5 轮而不可判定；可达率站点对测 40/40 持平，但**真跑起来新增 `UNREACHABLE` 34/100**（车被连续插值开到单向断头口袋里，`nearestNode` 落到异分量）。扩范围图暂不上线，两条前置（车辆落到"最近可通行节点"、画布重定标）见 §13.11。

### M3　仿真实验台

- [x] §5 全部，默认 M 档 **20 台 / 13 站 / 1.35 km²** —— 20 台 ✅；**两处口径与本文不同，别当已对齐**：① 范围用的是**现役** 5 个派单围栏外接框 1613 × 500 m，1.35 km² 要等 §1.8 那 4 个站点进库后重跑；② **"13 站"没有进模型** —— M3 的 OD 是园区内随机点而非站点对（站点级需求分布属 M6 输入），所以撮合/空驶/regret 的结论成立，**站点维度的结论不成立**。见 §13.10

**闸门：** 一条命令产出带置信区间的指标表；他人 clone 可复现。 → `bash scripts/dev/scenario-bench.sh`（12 次重复、95% CI、报告 `reports/scenario-bench/m-tier-bench.md`），复现性由 `sameSeedIsFullyDeterministic` 钉住。

**引用限定（M3 输出的所有完成率数字通用，2026-09-21 实测）**：两个**没有真车数据可标定**的假设参数各自就能推动完成率 —— `busyDrainMetersPerPercent` 100→250 m/1% ⇒ **14.63pp**（§13.16-b），`chargeCurve` 线性→80% 后慢 3× ⇒ **7.17pp**（§13.18），合计 **≈22pp 的不确定带**；而本项目最大的一条策略效应是错峰 +10.65pp。所以小于该带的完成率差不可归因给策略，M3/M4 的结论只能读作"**同一参数集下的相对排序**"。

### M4　让已有能力生效

- [x] 错峰返充接线：`shouldDeferReturnToCharge`（`EnergyForecastServiceImpl.java:79-96`，已带安全兜底）此前唯一调用方是仿真 `ParkPilotSimulationServiceImpl.java:808`，真实链路一行都没查 —— **现已接入 `DispatchAutomationRuleServiceImpl.evaluateFleetEnergyRules`**：有峰段场景里高峰推迟 = 完成率 **+7.38pp [+6.35, +8.42]**、桩位排队 **−14.1 车·分钟**、接驾距离 −70 m，且必充档不受影响。（曾一度按 §13.12 降级，那是齐次到达下的结论，前提已补掉）
  - **修正数（§13.16-a，补上"开去充电"这条腿之后重跑同一对臂）**：完成率 **+10.65pp [+9.38, +11.92]**、完成单数 +18.42、空驶率 −8.53pp、总里程由 **+8.3 km 翻成 −7.0 km**、而排队收益变小（−14.1 → **−8.98 车·分钟**）。引用这条时以 §13.16-a 为准，并带 §13.16-b 的 14.6pp 参数扰动带。
- [ ] **同一条的前置**：真实高峰曲线与电价还没实测，`ArrivalProfile` 目前是矩形块假设
- [ ] **高峰档要先有"时间来源"（§13.24）**：`t_peak_mode_state` 两行的 `schedule_cron` / `schedule_end_cron` 至今全 NULL ⇒ `RulePolicy` 的高峰分支（`peakSocDamping=0.7`、`peakDistanceFactor`）在真实链路上从未生效；而 park 2 历史上被手工开成 PEAK 后**没有任何出口**（本轮已加 120 分钟兜底回落 + `dispatchflow.peak.auto_reset`，并在 live 复验时挖出调度器"用本轮快照 `updateById` 把自己刚设的档口覆盖回去"的老 bug，三条分支都中招 ⇒ 以前即使配了 cron 也落不了地）。**部署前需要你定三件事**：① 两园的高峰窗口 cron（一对起、一对止，时段按运营口径，仓库里不替你决定）；② cron 由谁维护 —— 管理端表单有这两个字段但一直没人填；③ 是否允许"长期高峰不带结束 cron"（超过 `FSD_PEAK_MODE_MAX_MINUTES` 会被自动回落，要更长就调这个值）。**这三条定下来之前，任何"高峰策略已生效"的表述都不许写进材料。**
- [x] **（§13.12 换来的新优先级，§13.15 改过落点，本轮已量）选桩策略实验 → 结论是"三条都不值得做"**：库里实测**6 根桩全挂在 ZJF-CHG-01、六个车位坐标逐字相同**，所以"选近桩"在现役设施下是空命题（§13.16）。真正缺的是"开去充电"这条腿，已补进实验台并量出：漏掉它**系统性抬高完成率约 3pp**、每次补能漏计 745.8 m 空驶；把 6 根桩摊到 5 个真实站址 **完成率只 +0.50pp 且分不出来**（⇒ §1.8 的价值不能按"缓解抢桩"论证）；排队感知选址与另一个点**逐指标完全相同**、"最闲桩"分散反而多花 4.1 km 里程且没摊平。四张表在 `reports/scenario-bench/pile-selection-m-tier.md`，判语与自抓的 (0,0) 瞬移 bug 见 §13.16。**已完成**：时机与预置两条对照都已在有腿模型里重跑，修正数见 §13.16-a / §13.16-c
- [x] ~~选桩粒度从园区降到桩（`ChargingSessionServiceImpl.java:164-224`，负载因子现为"园区活跃会话/10" `:215-217`）~~ —— **立论已推翻（§13.15 第三例）**：那段代码全仓零调用方，改它不影响任何线上行为。原文留此划线是为了不再被当成待办
- [~] `pressureThreshold` 标定：**→ 2026-09-21 实测判定"在这份数据上不可标定"，并已改为双判据（§13.15）**。默认 2.0 与实测峰值 159.85 差两个数量级不是笔误，是量纲：`pressure_p95` 的口径是"该站该小时开始的会话数"，而特征序列是压测夹具的 **150 次/小时平地（峰谷比 1.08、剖面 CV 0.016）**，模型剖面只在 152.7~159.85 之间动 —— 绝对阈值只有"24/24 恒开"和"0/24 恒关"两种结局。危险在于：**§13.14 那条接线目前是空操作，全靠表里没行撑着；日作业一被调度起来，它就翻成"整天推迟返充"，兜底只剩 SOC 地板**。所以新增相对判据 `min-peak-pressure-ratio`（默认 1.5，`≤0` 为回滚开关）：高峰 = 过绝对阈值 **且** 当前小时 ≥ 当日逐小时中位 × 1.5，剖面太平退化为"不推迟"。要在真实昼夜曲线上重标定，仍待有实测时段分布（本项保持未闭合）。
- [~] 预测日作业调度化（`scripts/ml/refresh_energy_forecast.py` 现需人工；未调度时是**静默回退**，要把回退变成可观测事件）—— **可观测这一半已落地（§13.15）**：`parkForecastStatus()` 把"一个 0 四种含义"拆成 `DISABLED/NO_ROWS/NOT_THIS_HOUR/STALE/FRESH` 五档带根因说明，配 `dispatchflow.energy_forecast.availability{park,state}` 计数器 + 限速 2 分钟 WARN，另立 `flat_profile` 一档。**调度本身未做**：脚本要 SSH 进生产并从本机 `.venv-ml` 调 xgboost，注册计划任务属部署动作，按"止于部署前"交回本人。
- [~] 充电曲线分段（`chargeRatePerTick=4` 线性，`FleetEnergyProperties.java:36`）—— **生产侧没有落点**：`chargeRatePerTick` 全仓只有 2 个读取点，都在被冻结的 `ParkPilotSimulationServiceImpl`（`:448/:949`），线上补能链路一次都不读它（§13.18）。因此改成实验台敏感性 `Config.chargeCurve`：默认 `LINEAR` 与历史逐字相同（17 项指标全等断言），拐点 80% 后慢 2× ⇒ 完成率 **−3.26pp**、补能排队 **+46%**；慢到 1/3 ⇒ **−7.17pp**、**+80%**。**与 §13.16-b 的能耗带合起来约 22pp**，已写进 M3/M4 闸门的引用限定。真要做"分段"需要真车充电曲线数据（没有），或先给生产链路加一个消费点（属新能力，不在"让已有能力生效"范围内）。
- [~] ETA 落后端：原文只说"`RouteMetricsCalculator.java:115` 的 15 km/h 换成路段限速"，**实际是三层问题（§13.17）**：① 唯一生产调用点把 `parkId`/`nodePath` 传成 `null`/`List.of()` ⇒ 逐段限速那段代码从未执行、风险点恒为空（契约测试的 stub 恰好断言 `isNull()`，所以一直绿）；② 常数本身有实测可换 —— 现役 124 条边长度加权调和平均 = **13.19 km/h**，写死的 15 偏快 12.1%，而"按条数算术平均"会偏快 19.5%；③ 前端另有一个无出处的 `2.5 m/s`（=9 km/h），与后端差 **46%** ⇒ 界面 ETA 与接口 ETA 永远对不上。
  已做：调用点传真实 `parkId`+节点、ETA 改逐段 `Σ(段长/段限速)`+未覆盖段按实测补、契约测试改成断言入参、前端统一到 `MEASURED_NETWORK_SPEED_MPS=3.66`（承诺余量是否要加、加多少 **留给本人定**）。**剩余半条**：`RoadRouteResult` 不带 `nodePath`（`segmentPath` 字段全仓无人填充），所以生产上目前只享受到"常数换对"这一项改善，逐段加权是"接口通了、数据没到齐"。ETA 精度还前置依赖 §1.5 的 polyline 进 seed（现在 124 条 `polyline_geojson` 全 NULL，边长只能由节点坐标算）。
- [x] 能耗只做**参数化 + 敏感性分析**（`busyDrainMetersPerPercent` 在 100–250 m/1% 对派单可行域的影响），**不得称"预测模型"** —— **已扫（§13.16-b）**：完成率 0.6970 → 0.8433，**总跨度 14.63pp**，且失败归因会从 `NO_VEHICLE` 换成 `LOW_SOC`。这条同时给整个 M3/M4 的完成率结论钉了一条**扰动上限**：小于 14.6pp 的完成率差不该归因给策略

**闸门：** 每条有"接前 vs 接后"数字差，全部来自 M3。 → 两条 bench 项已交（§13.16 选桩四表、§13.16-b 能耗敏感性）；其余项的"接后"要么被数据卡住（压力阈值无高峰可标定、能耗无真车数据），要么被部署动作卡住（日作业调度），要么需要先有下发链路（选桩在线上无人执行）。

### M5　多目标撮合与规模

- [x] 路网图缓存（`ParkRoutePlannerServiceImpl.java:133-148` 每次全量重查；本地 79/124 与压测 1000 节点两种规模都测）→ `ParkRoadGraphCacheTest` 6 条，见 §13.1
- [x] 待派池 + 批量撮合（§2.3）—— **算法与对照表已完成（§13.13）**：`Config.matchStrategy` + `matchWindowTicks`，匈牙利解对过穷举。**真实链路未接**，因为②"同窗口内换配对"确实更好（里程 −3.2 km、端到端 −9.4 s，均可分）而③"开 2 分钟窗口"要用 +51 s 等派去换 → 属于 SLA 与成本的业务取舍，等本人定口径（§13.13 末）
- [x] MAPF 单位统一（§7.2）—— 已修，见 §13.19：预约窗口原来只覆盖真实占位时间的 37%–62%，现按米÷米每秒统一，A\* 边权同口径（路径选择在现役数据上零变化，由 21 条既有测试证明）。**"冲突仍照常派单"未改**，只加了冲突率计数器，等本人定 SLA 口径
- [x] 同硬件重压测，双口径留档 → §13.21（MAPF 修复后重测，8 轮 × 20 单 × 两臂）：**进程内图缓存把 P50 从 103.96 ms 降到 77.61 ms（+33.9%，8×8 逐轮全分离）**，P95 两臂区间重叠 ⇒ 分不出来；MAPF 单位统一**没有**改变热路径耗时（修复前 78.19 / 意外复测 77.78 / 修复后 77.61，互相落在对方区间内），但冲突率第一次可读：**24.4%**。**表述纪律守住**：这一轮量的是 JVM 内图缓存，不是 Redis ⇒ 不得写成"Redis 贡献"（Redis 在热路径上承担的是暂停标志/充电策略热更新/事件幂等，本轮未单独设臂）
- [x] L 档 40 台退化曲线 → §13.20：需求固定时 10→20 台 +39.95 pp、20→40 台只 +7.36 pp 且完成率饱和（1.0000，CI 宽度 0）；需求同比放大时完成率不退化（0.862→0.954），**退化在别处** —— 补能阻塞 4×车 ⇒ 120×、候选/次派单 ⇒ 2.6×。范围/桩数仍取现役值，**不得当 §1.3 目标 L 档的容量证明**

**闸门：** 给出"贪心 vs 批量"在 M 档场景集上带置信区间的对照表。 → **已交付**：`reports/scenario-bench/batch-matching-m-tier.md` 三段表（无窗口 / 固定窗口只比配对 / 含窗口总效应），每组 9 个指标带配对 95% CI，判定列只在 CI 不跨 0 时给方向。

### M6　需求预测与热区预置（"引力波"最小可信版）

- [x] 站点×小时订单需求 P50/P90（数据由 M3 生成，本地 `t_dispatch_task` 现为 0 行）→ `ScenarioBench.demandProfile` + `reports/scenario-bench/demand-m-tier.md`；一致性断言卡住"各格合计=名义到达量"。**限定**：默认 `FLAT` 到达下小时差是抽样噪声，要测峰谷必须切 `ArrivalProfile.PEAK_SECOND_HOUR`（§13.14①）
- [x] 空闲超阈值车辆向热区预置；收益指标：平均接驾距离、接驾时延、峰时未响应单量 → 实验台已实现并量完（§13.14②）：接驾距离 **−164 m**、端到端 **−32.6 s** 可分，但 **regret +6.9pp、补能排队 +37.5 车·分钟** 同样可分 —— 预置把车队聚到一个点，代价真实，**不能只报有利的那一列**；真实链路接入待与选桩/分散度一起调
- [ ] 补一个贝叶斯线性回归或状态空间基线，与现有 GBM **同题对照**（JD 列了贝叶斯线性回归与时序预测，当前两者皆无）
- [ ] 保留并对外声明：真实（压测）数据集 P90 覆盖率 **77.78%**，低于自定 0.8 警戒线，按 P90 做容量规划会低估——**这条不藏，是加分项**

### M7　前端整合

- [ ] §6.2 信息架构收敛（先出路由表与三态清单，再动代码）
- [ ] §6.3 地图与实时统一
- [ ] §6.4 契约与清理
- [ ] §6.5 性能预算与 e2e 补齐

**闸门：** 一级导航 ≤5；地图栈唯一；三个台的主操作路径各有 e2e；无静默吞错。

### M8　Jev + Spring AI + Agent

- [ ] §3.3 Jev 影子接入（SHADOW 阶段，只记录）
- [ ] §4.2 ①②③（工具化 → 助手换代 → 决策解释）
- [ ] 影子数字达标后才允许进入 §2.2 的 GRAY
- [ ] §7.4 消息可靠性（可与 M8 并行，独立可砍）

**闸门：** 未拿到实测 RTT 与一致率之前，Jev 不出现在任何在线决策路径上。

### 依赖与顺延

- 顺序：M0 → M1 → M2 → **M5 图缓存** → **M2E 扩范围** → M3 → M4 → M5 余下 → M6 → M7 → M8
- 可并行：M2 与 M1；M7 的 §6.3/§6.4 与 M4
- 硬阻塞：**M3 未完成前不得启动任何模型训练类任务**；**§7.2 熔断修复完成前不得接入任何外部决策 API**；**M5 图缓存完成前不得执行 M2E 扩范围**；~~（本轮实测新增）M3 的"N 次重复 + 置信区间"没建成前，M2E 的时延闸门挂起~~ **该挂起已解除并出判语（§13.11）：闸门不通过**；新增两条上线前置 —— **① 车辆落位改"最近可通行节点"（与 §7.2 坐标改写、§7.6 像素路径同批）② schematic 画布重定标（与 §6.4 硬编码同批）**，两者未落地前扩范围图不得进 seed、不得上线
- 可整块砍：M8、M7 的 §6.2 重构、M5 的匈牙利（只做图缓存）、M2E 的扩范围（维持 0.612 km² 也能演示）
- 砍了就要同步做：删除 README 与简历中对应表述

---

## 9. 明确不做

- [ ] 新建平台 / 重写调度系统
- [ ] **删除 `ParkPilotSimulationServiceImpl` 本体**（§7.6 只删 `PARK-*` 示意池分支）
- [ ] **靠画大多边形扩范围**（§1.6 路 C）—— V44 的 17.33 km² 假包络就是这条路的产物
- [ ] **在图缓存之前扩范围**（§1.6 顺序硬约束）
- [ ] 为凑 JD 关键词引入 Elasticsearch（当前业务代码零引用，只有 Filebeat 送日志）
- [ ] 把 PostGIS 强行开进派单热路径：`GeoQueryService` 生产零调用、`fsd.geo-service.enabled=false`（`application.yml:397`）、geo-py 不在主 compose；实测当前 13 站线性扫描 p95 0.057 ms vs PostGIS 1.71–2.28 ms，交叉点 N≈5,000 → **要么接进围栏并补 N≥5,000 合成压测，要么在 README 写明"规模预案，未接线"**
- [ ] 把 Jev 放进 277 ms 派单热路径
- [ ] 微服务化、换框架、换 ORM、前端视觉重构
- [ ] 在 M3 实验台建成前训练任何"需要真车数据"的模型
- [ ] 宣称支持 500 台——只给 §1.4 的外推前置条件

---

## 10. 本轮已定与待决策

### 10.1 本轮已定（授权："范围你自己判断、车数量可以多一些、送货点你选择"）

| 项 | 定值 | 依据 |
| --- | --- | --- |
| 车队规模 | **M 档 20 台**（`ZJF-AV-01..20`） | §1.3：桩侧零改造（占用 75%）、密度 15 台/km²、吞吐 56 单/h 能让撮合出结论 |
| 待命位 | `ZJF-IDLE-01` `capacity_limit` **20 → 28** | 20 台满载需留位 |
| 充电桩 | **维持 6 个**，不扩 | 扩到 30 台才需要；且 §1.9 实测：目标框内高德**检索不到公共充电站 POI**，内部桩只能靠现场 |
| 派单范围 | **≈1.35 km²**（4 个业务片区并集） | §1.6：20 台 ÷ 15 台/km² |
| L1 展示外包络 | **1.65 × 1.33 km = 2.19 km²**，边界=四条命名街道（大岛西路／震蒙大道／现代大道／圩角河路—圣绣路） | §1.9：街道名直接取自高德 POI 的 `address` 字段，**可现场复核**，替代 17.33 km² 假包络 |
| OSM 提取框 | **`121.0680–121.0905 / 31.9550–31.9715` = 3.88 km²**（预估 1.2 MB / 400–600 节点） | §1.6：展示块每边外扩 250 m |
| 新增派车站点 | 4 个，**坐标已实测、性质为演示夹具**：`PICK-03` 121.074588,31.966227｜`DROP-05` 121.082213,31.959503｜`DROP-06` 121.071107,31.965644｜`EXPRESS-02` 121.081861,31.969579 | §1.8：配比 取3/送6/接驳2/待命1/充电1 = 13；四点到基地 172/870/1029/1079 m，四个正方向 |
| 描点工具 | 管理端已有 `AmapPointPicker`（`StationList.vue:136`），产出即 GCJ-02 | `datum.py:25` 确认库内为 GCJ-02，**无需换算** |
| 高德 key | **有效**：`infocode=10000 / info=OK`，22 次调用成功 | §1.9；同时暴露 QPS 限制 `10021`，见 §1.9 结论 |
| v2 坐标表 | **全部作废**（来源文档已被判定有问题并删除） | §1.8 前置事实 1 |
| 园区示意调度 | **删除 `PARK-*` 分支，保留仿真器本体** | §7.6 |
| `DEFAULT-BOUNDARY` 处置 | **重画成 §1.6 的四条命名街道（2.19 km²），不删这一层** | 演示需要一层"服务范围"视觉，删掉后图上只剩几个小围栏反而更空；2.19 km² 是可现场复核的真边界，比 17.33 km² 的假包络既小又真 |
| `t_vehicle.current_longitude/latitude` | **保留列名，改写为合法 GCJ-02** | §7.6 删掉示意模式后不再存在"像素坐标系"，这两个字段本就该是真经纬度；改列名反而与删除方向重复 |
| 第三方网点作收发货点 | **不追问业务关系，按演示夹具定** | 本人明确"只是展示功能"；已在 §1.8 加性质声明，对外一律称"仿真/演示数据" |

### 10.2 仍需你回答（只剩三条，都要外部事实）

1. **Jev 的 console key**：`api.typesafe.ai` 的 key 是否已拿到？拿不到则 M8 只能做到"设计 + 离线契约"。
2. **生产库 Flyway 实际版本**：本地 V47、代码 V51，生产未核。
3. **基地现场到底有几个内部充电桩**：决定车队能否从 20 提到 30。公开数据帮不上（§1.9 框内公共充电站 0 命中）。

---

## 11. 服务器容量与"好看"的可行性（8 核 / 8 GB / 80 GB / 45 Mbps / 1 IP）

### 11.1 当前编排实测（`docker-compose.prod.yml`）

| 服务 | limit | reservation | 端口绑定 |
| --- | --- | --- | --- |
| mysql | 1 G | 512 M | `127.0.0.1:3307` |
| redis | 256 M | 128 M | `127.0.0.1:6380` |
| rabbitmq | 512 M | 256 M | `127.0.0.1:15673`（管理台） |
| backend | 2 G | 1536 M | **不对外暴露**，仅容器网 |
| frontend | 256 M | 128 M | `127.0.0.1:8081:80` |
| **合计** | **4.01 G** | **2.56 G** | |

- 8 GB 减去 OS + dockerd（约 0.8–1.2 GB）→ **当前还剩约 3 GB 空闲**。
- 全部服务绑 `127.0.0.1`，单 IP 够用，需要宿主机反代提供 80/443。**这是安全上的正解**，也让"再加一个容器"没有端口冲突。

### 11.2 JVM 是第一个要动的

`back/Dockerfile:29`：`-Xms1024m -Xmx1536m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+HeapDumpOnOutOfMemoryError`

堆 1536 M 而容器上限 2 G → **元空间 + 线程栈 + 堆外只剩 512 M**。M 档 20 台车 + MAPF 的 Redis 调用 + SSE 长连接 + §1.6 要加的路网图缓存，全在这个 2 G 里。

- [ ] backend limit 2 G → **3 G**，`Xmx` 1536 M → **2048 M**（余量够，见 11.4）
- [ ] 图缓存**优先放 JVM 内**（`Caffeine`/普通 `ConcurrentHashMap` + 版本号），不放 Redis：400–600 节点 + 124→约 900 路段的邻接表在 MB 量级，走 Redis 反而把 §0.1 的 277 ms 拖高

### 11.3 80 GB 系统盘会先被什么吃掉（四条，都可验证）

1. **容器日志无上限**：`docker-compose.prod.yml` 全文**没有 `logging:` 配置**（grep `logging:` / `max-size` 零命中）→ 默认 `json-file` **不轮转、不限大小**。backend 每次派单都打日志，SSE 心跳也打，这是最快的死法。
   - [ ] 五个服务统一加 `logging: driver: json-file, options: {max-size: "20m", max-file: "5"}`
2. **无界业务表**（§7.4）：`t_fleet_telemetry_point`、`t_webhook_delivery_log`、`t_dispatch_event_outbox`、`t_order_idempotency` 均无清理任务。
3. **镜像堆积**：每次 build 出新 tag 都留在本地。
   - [ ] `deploy.sh` 收尾加 `docker image prune -f --filter "until=168h"`
4. **heapdump 1.5 G**：`HeapDumpPath=/app/logs/heapdump.hprof`，若 `/app/logs` 不是挂载卷，一次 OOM 就在可写层写 1.5 G。
   - [ ] 确认 `/app/logs` 已挂卷；备份产物（§13 自动备份）也要算进 80 G

### 11.4 加什么能让演示变好看，加什么不能加

| 组件 | 加不加 | 内存 | 理由 |
| --- | --- | --- | --- |
| Prometheus | **加** | ~512 M | §7.3 的算法指标没有它就只能看日志；45 Mbps 下抓取开销可忽略 |
| Grafana | 可选 | ~300 M | 做一块调度大屏很出效果，但**别当简历主图**——面试官会问数据哪来，而数据是仿真的 |
| **Elasticsearch** | **不加** | 起步 1–2 G 堆 | §9 已定不引入；且 8 GB 上开 ES 必须挤掉 MySQL 的余量，得不偿失 |
| Filebeat | 不加 | ~200 M | 没有 ES 就没有去处 |
| PostGIS / geo-py | 暂不加 | ~400 M | §9：当前 13 站规模实测无收益，等 N≥5,000 再开 |
| Zipkin | 可选 | ~300 M | `application.yml:79-85` 的 tracing 现在是关的；要看派单链路耗时才开 |

**建议的内存分配（合计 6.4 G + OS ≈ 7.4 G，8 G 刚好满）**：mysql 1.5 G（路网节点涨到 400–600、快照表高频写入，1 G 偏紧）｜backend 3 G（Xmx 2 G）｜redis 512 M｜rabbitmq 768 M（Erlang VM 基础占用就有 ~400 M）｜frontend 128 M｜Prometheus 512 M。

**结论一句话**：这台机器撑得起「20 台车 + 3.88 km² 路网 + 决策快照 + Prometheus」，撑不起 Elasticsearch。**"好看"的瓶颈不是服务器，是 §1.7 那三条：车没在动、路径没贴街道、没有事件流。**

---

## 12. 服务器接入与部署核查

### 12.1 连接事实

生产服务器 = SSH 别名 **`dispatch`**，定义在 `C:\Users\Administrator\.ssh\config`（含 HostName / Port / User / IdentityFile / IdentitiesOnly）。

**本文件刻意不记录 IP、端口、用户名、私钥路径。** 原因：仓库远端是 `github.com/1634594707/DispatchFlow`，README 带公开 CI 徽章与 MIT 许可，属**公开作品集**；把 root 的 SSH 端点写进被跟踪的文件，等于向招聘方与爬虫同时公开管理入口。别名 `dispatch` 本身不含地址信息，写进来是安全的，也够用。

host key 曾在 **2026-08-26** 轮换，旧指纹备份为 `~/.ssh/known_hosts.before-dispatch-rekey`。若将来再遇到指纹告警，先比对这两个文件再决定，不要直接删 `known_hosts`。

### 12.2 密钥卫生现状（本轮已核，维持不变）

| 文件 | 是否被 git 跟踪 | 内容 |
| --- | --- | --- |
| `.env` | 否（`.gitignore:60`） | 8 个真实值，含高德 web 服务 key |
| `front/.env.local` | 否（`.gitignore:37`） | 9 个真实值，含 `VITE_AMAP_KEY` 与安全码 |
| `.env.production` | **是** | **全占位符**（`your-strong-mysql-password`、`your-amap-webservice-key` 等），真实值只存在于服务器上的 `.env` |

- [ ] 规则写进 `CONTRIBUTING.md`：新增任何环境变量，必须同时在 `.env.example` / `.env.production` 补**占位行**，真实值只进本地与服务器

### 12.3 部署前只读核查（`ssh dispatch` 登录后执行）

```bash
# 1) 生产 Flyway 到第几版（本地 V47、代码 V51，生产未核 → §10.2 第 2 条）
docker exec fsd-mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -e \
   "SELECT version,success,installed_on FROM fsd_core.flyway_schema_history ORDER BY installed_rank DESC LIMIT 6;"'

# 2) 资源实况（核 §11 的内存与 80 G 磁盘推断）
docker compose -f docker-compose.prod.yml ps
free -m && df -h / && docker system df

# 3) 容器日志是否真的在无限增长（§11.3 第 1 条）
docker inspect --format '{{.Name}} {{json .HostConfig.LogConfig}}' fsd-backend fsd-frontend fsd-mysql

# 4) 本地测出的三个问题在生产是否同样存在（别只修了本地）
docker exec fsd-mysql sh -c \
  'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D fsd_core --table -e \
   "SELECT fence_code,status,JSON_LENGTH(polygon_json) pts FROM t_park_geofence WHERE deleted=0;
    SELECT coord_lng,coord_lat,COUNT(*) n FROM t_station WHERE deleted=0 GROUP BY coord_lng,coord_lat HAVING COUNT(*)>1;
    SELECT vehicle_code,current_longitude,current_latitude,last_report_time FROM t_vehicle WHERE deleted=0;"'
```

密码一律从容器自带环境变量取，**不出现在命令行参数、不落到文件、不输出到任何记录**。

### 12.4 部署动作（与 M0 一一对应，顺序不能换）

- [x] ~~V33/V34 回退 + flyway repair~~ → **2026-09-22 实测判定不需要**：生产 history 只有 `V50 BASELINE` + `V51`，V33/V34 的 `channel_type`/`agg_count` 是 dump 带进来的（所以谈不上「改了就应用的迁移」），部署前用 `check-migration-checksums.js` 逐条比对**校验和全部一致** ⇒ 直接 validate 通过、未跑 repair。原条保留划线以免后人又当成待办 —— 否则 `FLYWAY_ENABLED=true` 下启动即校验失败（`docs/DispatchFlow_部署整改任务路线图_2026-09-21.md` §1.1 已列为阻断项）
- [ ] backend limit 2 G → 3 G、`Xmx` 1536 M → 2048 M（§11.2）—— **本轮实测数据更新了这条的判断**：backend 1.03 GiB / 2 GiB（51%）尚有裕度，**真正紧的是 MySQL 775 MiB / 1 GiB（76%）**，且 `docker-compose.prod.yml` 里根本没有 `mem_limit`/`Xmx` 字面值（限额来自别处/默认），所以先定位限额来源再调，别按旧文照改
- [ ] 五个服务统一加 `logging: json-file, max-size 20m, max-file 5`（§11.3）
- [ ] 服务器 `.env` 补齐 `FSD_ADMIN_TOKEN_HMAC_KEY`（`docker-compose.prod.yml:137-139` 注释已写明：不设置会导致每次重启随机 key、全员掉线）
- [ ] **若启用真车接入（`FSD_VDA5050_MQTT_ENABLED=true`）**：部署后压一次「断 broker 30 s 再恢复」，确认 `dispatchflow.vda5050.mqtt.connection{event=resubscribed}` +1 且车辆状态重新入库（§13.26：本机没有 MQTT broker，这条是唯一能证明重订阅真的生效的验证）
- [x] 部署后跑 §12.3 第 1、3、4 条复验并回填 §12.5 —— **已完成**（V56、日志未轮转、生产 DEFAULT-BOUNDARY 4 顶点、车辆坐标确认为像素）
- [ ] **还差的最后一条复验**：生产快照管道真正写入 —— `t_dispatch_decision_snapshot` 现为 **0 行**（表与 34 列已建、指标已注册），需要一次真实派单才算证明。要不要我用一个 remark 标记为 `post-deploy-verify` 的演示单去打这一单（会往生产写一行订单 + 一行快照），还是你在手机上点一单？

### 12.5 生产实测已迁出

> 第一轮部署（V51 → V56）的核查真实值已迁到
> `DispatchFlow_已完成工作记录_2026-09-22.md`（同目录）。生产现状要点：**Flyway 已到 V56**、
> 快照表已建（34 列、0 行）、24 个 `dispatchflow` 指标在位、容器日志仍未设轮转、
> `t_vehicle.current_longitude` 在生产同样是像素值。

## 13. 执行记录（正文已迁至《已完成工作记录》，此处只留指针与未做清单）

### 13.1–13.26 执行记录已迁出

> 全部完成内容与证据见 `DispatchFlow_已完成工作记录_2026-09-22.md`（本文档内所有「见 §13.x」都指向该文件）。
> 迁出原因：本路线图只保留目标、约束、闸门与**未完成项**；执行记录单独成文，避免一半计划一半流水账。
> 摘要（供在此处决策用）：M0–M5 全部关闭；§7.2 修掉 5 条（失败原因标签、高峰档出口 + 调度器自覆盖、
> 并列裁决、MQTT 重订阅、MAPF 单位）；两个仪表缺陷（陈旧 jar 启动脚本、t 分位数表 df 错位）已修并加守卫；
> 关键实测数字：图缓存使派单 P50 从 103.96 → 77.61 ms；MAPF 冲突率 24.4% 首次可读；
> L 档完成率在 40 台前即饱和；两个未标定参数合成约 22pp 的完成率不确定带。
### 13.27 本文件全部工作中**没有**做的事

- **未执行任何服务器动作**：§12.3 只读核查、§12.4 部署动作、SSH `dispatch` 全部未碰，按"止于部署前"的要求交回本人。
- 未提交、未打 tag：`提交/丢弃其余未提交改动` 这条含逐文件裁决（`.gh-check.js` 是 CI 探针草稿、`front/Dockerfile` 的 `chmod +x` 是构建绕过、未跟踪的 `geo-py/` + `GeoQueryService` 三件套属 §9 的"要么接进围栏补 N≥5,000 压测、要么 README 写明规模预案未接线"），等本人定口径。
- **本轮收尾自查（2026-09-22，命令级）**：文档 §13.x 全部交叉引用都指向存在的小节（脚本比对：26 个小节、引用 0 处悬空），五份报告产物与 `scripts/analysis/forecast-flatness.js` 均在位；`git status` 共 124 项工作区变更，**未提交、未打 tag**（按 §13.27 第一条交回本人）。
- **§7.2「乐观锁是装饰品」的当前状态证据（已复核，尚未修）**：全仓 main 里 `@Version` 与 `OptimisticLockerInnerInterceptor` **零命中**，而 `AdminUserServiceImpl.java:60`、`DispatchStrategyAdminServiceImpl.java:89`、`InfrastructureAdminServiceImpl.java:104/:173` 等仍在 `setVersion(0)` —— 即版本号只被写死成初值，既不递增也不参与 WHERE ⇒ `t_dispatch_task.version` 的注释「乐观锁版本号」是假的。修法二选一（接上插件并证明并发下确有一次更新失败重试，或删列 + 删注释），需要一轮独立工作：接插件会改变该实体所有 UPDATE 的语义，必须先用 `DispatchConcurrencyIntegrationTest` 钉住前后差异，不能盲接。本轮未动它。
- 未动 `Tracking.vue`、`digital-twin/Index.vue`、`ParkPilotSimulationServiceImpl` 本体（冻结清单）；`ParkPilotSimulationServiceImpl` 与 `RealFleetSwapCoordinator` 只改了 §7.2 点名的调用行（方法签名换 `strategyForAssign`），未触算法。

---

## 附：取证方式（规则不变）

> 具体取证清单随执行记录迁到 `DispatchFlow_已完成工作记录_2026-09-22.md`。
> 长期规则照旧：**证据面只有本仓库与本地 docker**，不打开线上站点、不读线上凭据，
> 所有部署动作由本人执行；密码不进命令行与日志。
