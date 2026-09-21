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

### 12.5 待回填（执行 §12.3 后填这里）

2026-09-22 第一轮部署（V51 → V56）执行后回填，全部为实测值：

| 项 | 生产实测值 | 与本地的差异 / 结论 |
| --- | --- | --- |
| 生产 Flyway 末条版本 | **V56**（`road node component`，2026-09-22 04:21:47）；V52–V56 五条 `success=1`，`Successfully applied 5 migrations … now at version v56 (0.524s)` | 追平代码版本。部署前对照：仓库磁盘校验和与生产 history **逐条一致**（`check-migration-checksums.js` → OK），因此 **没有跑 repair，也不需要** |
| 生产 `DEFAULT-BOUNDARY` | **ACTIVE，4 个顶点**；其余四个园区围栏 9–11 顶点（CORE-SOUTH 11 / CORE-NORTH 10 / HUB 10 / EAST 10 / EXPRESS 9） | **与本地不同**（本地 §1.6 记的是 9 顶点 / 17.33 km²）⇒ 这是 §1.5/§7.5「地理内容改走 seed」尚未把生产追平的直接证据，不是猜测 |
| 生产站点坐标叠置 | 只有 **1 组** 重复坐标 | 本地是 14 个对象叠在同一点（§1.9）⇒ 本地的叠置是后续实验引入的，生产的坑小得多；两边都该由 seed 统一 |
| 生产 `t_vehicle.current_longitude` | **确认是像素值**：ZJF-AV-01…06 = `668.437`、07…20 = `578.400`（20 台仿真车） | §7.2「坐标语义污染」**在生产同样成立**，且 `VehicleAdminDetailResponse` 原样透出 ⇒ 该条不是本地洁癖，是线上问题（仍是待办） |
| 生产容器日志 | 五个容器全部 `json-file` 且 **max-size 为空**（未设轮转）；当前合计仅 7.0 MB | §11.3 第 1 条**未落地**；眼下不紧迫但无上限，按月增长会吃掉磁盘 ⇒ 留作第二轮 |

**这一轮顺带查出的四件事（都是新事实，不是复述既有待办）**

1. `scripts/deploy.sh` 的预检把「已解析但尚未应用」的迁移判成 validate 失败，并且给的排错方向（改过已应用迁移 → `repair`）在这种情况下是错的、`repair` 也修不了 pending ⇒ 每次正常部署都会被自己的前置检查挡住。已修（validate/info 都带 `-ignoreMigrationPatterns='*:pending'`，只拦真不一致，并把待应用数量打出来）。
2. `deploy.sh` 第 7 步用 `curl http://127.0.0.1:8080` 探活，但 backend 的 8080 **没有发布到宿主机**（端口语义：只 `8080/tcp`）⇒ 每次都打印「后端健康检查未通过」的假警报，而 `docker inspect` 明明是 healthy。核验要放在容器内做（本轮实测容器内 `health=200`、`/actuator/metrics` 里 **24 个 `dispatchflow` 指标在位**，含本轮新加的 snapshot/mapf/peak 计数器）。
3. **裸域 `aplicity.online` 此前默认落到 codefolio 的 8082**（nginx 只有 `code.*` 与 `www/app` 两个 server_name，无匹配时取 443 的第一个块 = code 块）。所以下线 codefolio 后裸域直接 410。已把 `aplicity.online` 加进 app 块的 `server_name`（现裸域 200，标题 `DispatchFlow 无人车调度平台`；`code.aplicity.online` 保持 410）。原文件备份在 `/root/dsh-apps-https.conf.pre-bare-domain-fix`。
4. `back/sql/migrations/` 里有 **两个永远不会被 Flyway 应用的文件**：`V13b__road_segment_traffic_columns.sql`、`V20b__report_history.sql`（`V<n>b__` 不符合命名约定，Flyway 明确报「detected but not run」）。生产实测 `t_report_history` 已存在但**列名与 V20b 不同**（`dataset/date/file_size_bytes/generated_at/generated_by`），即它来自 dump 而非这条迁移。两个文件都是幂等写法所以暂无危害，但它们是迁移图里的死信，要么改成 V57/V58 的守卫式迁移、要么删掉并把 schema 归位到 seed —— 未动，留待决定。

**回滚参照**：DB `/opt/backups/fsd_core-predeploy-V51toV56-20260922-035412.sql.gz`（gzip 校验通过、47 张表）；
镜像 `fsd-backend sha256:0328d0d90db7…` / `fsd-frontend sha256:b02e24f19286…`；
compose 与仓库 HEAD 逐字一致（同步前已 `cmp` 验证，故本轮没有覆盖任何服务器独有配置）。
`.env` 未被触碰（md5 `59ec612e…` 前后一致）。代码基线 tag：`pre-deploy-v52-56`。

---

## 13. 执行记录（2026-09-21 起，按"止于部署前"执行）

### 13.1 已落地，每条都有命令或测试证据

| 项 | 落地内容 | 证据 |
| --- | --- | --- |
| §7.5 V33/V34 漂移 | 两文件 `git checkout HEAD --` 还原；幂等保护移到 **V52** | 修复前实测：磁盘 `-1648458402` vs 库内 `-1786803832`（V33）、`-269008413` vs `-1556581673`（V34）。还原后 `scripts/check-migration-checksums.js` 对 28 条已应用迁移全绿 |
| 校验和守卫 | 新增 `scripts/check-migration-checksums.js`（纯 node 无依赖，复现 Flyway 的"逐行去换行 CRC32"算法） | 算法先用 V21/V30/V44/V45/V46/V47 六条**已应用**迁移实测，6/6 与库内 checksum 完全一致后才采用 |
| §7.5 repair 前置 | `scripts/deploy.sh` 新增第 3 步：Flyway `validate`，失败即退出；`repair` 只在 `DEPLOY_FLYWAY_REPAIR=1` 时执行（它会删失败的迁移记录，不给默认权） | 口令经 `--env-file` 传入，不进命令行参数；`bash -n` 通过 |
| §7.5 迁移纪律 | `CONTRIBUTING.md` 新增「Migration discipline」：禁止修改已应用迁移、迁移只放 DDL、地理内容进 seed | 同时纠正了原文错误：`back/sql/init/` 并非迁移目录 |
| §M0 本地补齐 | 本地 `fsd-mysql` V47 → **V54**（V48/V49/V50/V51/V52/V53/V54 全部经 Flyway 应用，含历史行与 checksum） | `flyway migrate` 输出 `now at version v54`；随后 §0.2 复跑：3 台车 `park_id` 全部回填、`t_order_idempotency` 与 `t_energy_forecast` 均存在，围栏 6 / 节点 79 / 路段 124 / 站点 9 **不变** |
| §7.2 灰度重掷 | `DispatchStrategyRuntimeService` 改为**一次解析返回一对**：`strategyForAssign(parkId, bucketKey) → AssignStrategy(energy, scoring, profileId, type, grayPercent, bucket, productionSide)`；随机数换成稳定分桶 `floorMod(bucketKey.hashCode(),100)`，无键时落 0 号桶而不是重掷 | `DispatchStrategyRuntimeServiceImplTest` 7 条：同键必同侧（300 次）、灰度比例落在 30%±4%、w=0 不命中实验、一单的 energy 与 scoring 必须来自同一档案 |
| §7.2 `toEnergy` 丢字段 | `toEnergy`/`toScoring` 改为从 YAML bean 全量 `BeanUtils.copyProperties` 后再按档案覆盖 | 同测试断言 `busyDrainMetersPerPercent=220`、`chargeRatePerTick=7` 在档案未覆盖时必须存活 |
| §7.2 死权重 | 删除 `weightPriority`、`weightCongestion`（全仓 `*.java/*.ts/*.vue/*.yml/*.sql` 检索零读取点，界面亦无该项） | grep 结果只有定义处本身 |
| §7.2 Webhook 熔断 | 新增列 `t_webhook_subscription.last_failure_at`（**V53**），`isCircuitOpen` 改为带冷却窗口的半开：`fsd.webhook.circuit-cooldown-seconds`（默认 300 s），`markFailure` 写时间戳 | `WebhookCircuitBreakerTest` 4 条：阈值下关闭 / 刚达阈值打开 / **窗口过后转半开（修复前此处恒为 true）** / 无时间戳的历史行仍按打开 |
| §7.3 决策快照 | **V54** 建 `t_dispatch_decision_snapshot`（task/park、候选清单与分项分数 JSON、策略标识与版本、臂标签 `gray_bucket`+`experiment_side`、路网图版本、撮合算法、耗时、`generated_at`、`score_gap`、`tie_count`）；写入点包住选车全流程，失败也留痕；快照写失败只降级为日志，不影响派单 | `DispatchDecisionSnapshotServiceImplTest` 5 条。**其中一条抓到实现真 bug**：次优分初值误用选中车自身分数，导致分差恒 ≥0、MAPF 让步场景无法表达 → 已改为"升序列表里第一个非选中者"，MAPF 让步时分差为负并注释说明这是有意信息 |
| §7.3 快照在集成/压测里真正跑通 | H2 夹具 `IntegrationTestSchema` 补 `t_dispatch_decision_snapshot`（V54+V55 全列，此前那份手写 DDL 停在 V53 ⇒ 快照写入一直失败并静默降级）；`DispatchDecisionSnapshotServiceImpl` 加 `dispatchflow.decision_snapshot.write{result=ok|failed}`；`DispatchFlowIntegrationTest` 端到端断言"一次派单留一行、赢家车号、`score_gap`/`policy_id` 非空" | 修后全量日志里 `T_DISPATCH_DECISION_SNAPSHOT not found` **0 次**（修前每轮压测都刷）；`DispatchDecisionSnapshotServiceImplTest` 5 → 6 条 |
| §M5 路网图缓存 | `ParkRoutePlannerServiceImpl` 增加进程内 `ConcurrentHashMap<parkId, CachedGraph>` + TTL（`fsd.park.route-plan.graph-cache-ttl-ms`，默认 60 s，**≤0 即回退到每次全量重查**，留作回滚开关）；用 `compute` 而非 get+put 以免 20 台候选车同时过期时一起打库；新增 `graphVersion()`/`invalidateGraphCache()` 为 default 方法，既有实现不破 | `ParkRoadGraphCacheTest` 6 条：12 次取图只打库 1 次、TTL=0 保持旧行为、跨园区分槽、失效后重载、版本指纹不再触发建图、空路网回退 YAML 且命中缓存 |
| §2.1 / §7.1 决策插槽 | 新增 `com.fsd.dispatch.core`：`DecisionPolicy` / `DecisionInput` / `DecisionWeights` / `DecisionOutcome` / `RankedCandidate` / `RulePolicy`；打分算式从 `DispatchVehicleAssignServiceImpl` 原样搬进 `RulePolicy`，装配点 `DecisionPolicyConfiguration` 出 bean，优先级系数 0.7/1.3 与高峰阻尼 0.7、插电衰减 500 m 从硬编码变进入 `DecisionWeights`。`DecisionTrace` 只留标量，**不把带 Spring 注解的配置 bean 带进内核** | `RulePolicyTest` 9 条按原算式手算的黄金值（含 4 处有意保留的可疑行为：高峰只削弱 SOC 项、插电奖励要求恰好等于满电、距离超衰减长度后奖励夹 0、空闲为负不奖励），`DecisionCorePurityTest` 1 条扫描内核包全部 import 守住"无 Spring / 无 mapper / 无 entity"；`DispatchVehicleAssignServiceImplTest` 原有 250 条断言零改动通过 => 迁移等价 |
| §M0-2 演示数据重置 | `scripts/dev/reset-demo-dispatchable.sh`：遥测刷到当前、SOC 分布化、车辆位置落到 ACTIVE 节点上、`ZJF-IDLE-01` 待命位 20→28；**不接受任何 `--url/--host/--port` 参数，`DOCKER_HOST`/`MYSQL_HOST` 等一旦指向远端即拒绝执行**；幂等、可 `--dry-run`、自带 `--verify` | 本机实跑 `PASS=13 WARN=0 FAIL=0`：3 台车遥测 1 s ≤ 30 s 阈值、最近节点距离 0 px、位置互不重合、ACTIVE 路网 1 个连通分量、6/6 车→取货位 `ROUTE_OK`；5 种指向外部的调用全部被拒；重复运行产物逐字节一致 |
| 全量门 | `mvn -pl fsd-bootstrap -am test` | **BUILD SUCCESS**：fsd-dispatch **317** / 依赖模块 **92** / bootstrap 集成 9 / common 8，全绿（含实验台 15 条、§13.15 预测可观测性 6 条、§13.17 ETA 5 条、§13.19 MAPF 单位 1 条、快照端到端断言）；本轮日志 `[ERROR]` **0 行**（不是只看 BUILD SUCCESS） |
| §M3 仿真实验台 | `com.fsd.dispatch.sim.ScenarioBench`（纯函数，驱动生产 `RulePolicy`）+ `scripts/dev/scenario-bench.sh` 一条命令 + 三份产物：`reports/scenario-bench/m-tier-bench.md`（12 指标 + 失败分布 + 9 条假设）、`charge-timing-m-tier.md`（补能时机四臂：齐次/压力推迟/高峰推迟/极端敏感性）、`batch-matching-m-tier.md`（撮合三段对照）、`demand-m-tier.md`（站点×小时画像）、`repositioning-m-tier.md`（热区预置对照） | 12 次重复带 95% t-CI：完成率 0.93 [0.90,0.95]、里程 138.2 km、空驶 0.35、补能被挡 209.7 车·分钟、regret 0.29 [0.24,0.34]。结论见 §13.10 / §13.12 / §13.13 |
| §M5 批量撮合 | `Config.matchStrategy`（贪心/匈牙利）+ `matchWindowTicks`（决策窗口）+ 端到端完成时长口径（新增 `pickup_wait_mean_s`） | 匈牙利解与穷举最优在 60 个随机矩阵上逐一相等（`hungarianMatchesBruteForceOptimum`）；对照结论：无窗口时分不出来（0.23 单/tick），同窗口配对更好（里程 −3.2 km、端到端 −9.4 s），开 2 分钟窗口则 +51 s 等派换 −4.4% 里程 —— 见 §13.13 |
| §M6 需求时空结构 | `Config.arrival`（`ArrivalProfile`：FLAT / 第 2 小时 ×2）+ `Demand.ZJF_STATIONS`（站点坐标来自库内 ACTIVE PICKUP/DROPOFF 实测经纬度）+ `demandProfile()` + 热区预置策略 `idleRepositionMinutes` | 站点×小时画像与名义到达量误差 <2%（测试断言）；预置：接驾 **−164 m**、端到端 **−32.6 s**，代价 regret **+6.9pp**、抢桩 **+37.5 车·分钟**；有峰段场景里错峰返充 **完成率 +7.38pp [+6.35, +8.42]**、排队 **−14.1 车·分钟** —— 见 §13.14。**这两组数字都是 teleport 口径**：补上"开去充电"这条腿后修正为 错峰 +10.65pp、预置 −91.6 m / −18.6 s 而抢桩代价仍在，见 §13.16-a / §13.16-c |
| §M4-3 预测退化可观测 | `EnergyForecastService.parkForecastStatus()` 五档状态 + `explain()` 根因；`parkPressure()` 改为委托同一解析器（两条路径不可能漂移）；`EnergyForecastMetrics` 两个计数器 + 限速 2 分钟 WARN；新增 `fsd.energy-forecast.min-peak-pressure-ratio`（默认 1.5）把"高峰"从纯绝对阈值改成 绝对 ∧ 相对 prominence | `EnergyForecastServiceImplTest` 25 条 + `EnergyForecastMetricsTest` 6 条。其中 `flatProfileMustNotDeferEvenThoughAbsoluteThresholdPasses` 复现的是实测出来的真坑：2026-09-17 那份剖面 pressure_p95 只在 152.7~159.85 动（峰/中位 1.02），绝对阈值 2.0 对它**恒成立** ⇒ 日作业一被调度就整天推迟返充。实测见 §13.15 |
| §M4 选桩与补能行驶 | `ScenarioBench` 补上"开去充电"这条腿：`ChargePoint`/`PileChoice`/`withChargeLayout`，桩位池改为带坐标的点集，新增 `charge_travel_m_session`、`pile_session_spread` 两条指标；`chargeLayout` 为空即历史 teleport 口径；`validate()` 强制布局桩数合计=`chargeSlots` | 四张对照表 + 判语见 §13.16（`reports/scenario-bench/pile-selection-m-tier.md`）：漏这条腿抬高完成率约 3pp、摊开桩位 +0.50pp **分不出来**、排队感知选址逐字相同、分散优先更差 4.1 km。自抓并修掉一个"无位置布局把车瞬移到 (0,0)"的口径污染 bug，并加**基线守门断言**（默认臂 0.93±0.005 / 138.2 km±2 km） |
| §ETA 落后端 | `RouteMetricsCalculator` 改逐段 `Σ(段长/段限速)`，未覆盖段按**实测** 13.19 km/h 补（原写死 15）；生产调用点补回真实 `parkId`+节点（此前传 `null`/`List.of()` ⇒ 逐段限速那段码从未执行）；`collectRiskPoints` 的园区过滤罩住 OR 两支；前端 `formatDeliveryEta` 的无出处 `2.5 m/s` 统一到 `MEASURED_NETWORK_SPEED_MPS=3.66` | 新增 `RouteMetricsCalculatorTest` 5 条；`RoadRouteContractTest` 的 2 处 `isNull()` stub 改成 `eq(1L)` 并 verify 入参真的传下去（**这条测试此前把空值当契约固化，所以一直没红过**）。实测：现役 124 条边限值分布 10/15/20 = 16/79/29，加权调和 13.19 vs 算术 15.76 ⇒ ETA 原偏快 12.1%；前后端速度常数相差 46%。见 §13.17 |
| §M4 充电曲线敏感性 | `Config.chargeCurve = ChargeCurve(kneeSoc, taperDivisor)`：SOC 高于拐点的部分按 1/倍率 速率充；默认 `LINEAR` 逐字等于历史折算式；`taperDivisor<1` 构造期即拒 | 17 项指标断言"不传曲线 == 显式 LINEAR"（加开关不动默认口径）；扫描：慢 2× ⇒ 完成率 **−3.26pp [−4.64,−1.89]**、补能排队 **+46%**；慢到 1/3 ⇒ **−7.17pp**、**+80%**。与能耗带（14.63pp）合计 **≈22pp 不确定带**，已写进 M3/M4 闸门引用限定。另查实：`chargeRatePerTick` 全仓仅 2 个读取点且都在冻结的仿真器里 ⇒ **生产侧无落点**，见 §13.18 |
| §M5 MAPF 单位统一 | `MapfProperties.vehicleSpeedMetersPerSecond=3.66`（=实测 13.19 km/h 加权均值）取代 px/s，并去掉 MAPF 借用 `fsd.park.vehicle-speed-px-per-second`；`ParkRoadGraph.distanceMetersTo()` 恒为米（px 分支按横 1.2263 / 纵 0.7390 m/px 换算），A\* 边权同口径；新增 `dispatchflow.mapf.reservation{result=reserved\|conflict\|disabled}` | 单位契约测试 1 条：横向 120 px 边第二条必须落在第 80 桶（旧口径 30 桶 = 只覆盖 37.5%）；现役图上路径选择零变化由 `ParkRoutePlannerAStarTest` 15 + `ParkRoadGraphCacheTest` 6 条零改动通过证明。实测比值区间 0.741–1.226（86 条 ACTIVE 边）、ACTIVE 节点 GPS 覆盖 55/55。见 §13.19 |
| §M5 L 档退化曲线 | `ScenarioBench` 新增 `candidates_per_decision`（每次派单尝试扫到的空闲车数，含 `NO_VEHICLE` 那一支）+ `Config.withFleetSize/withOrdersPerHour/lTier`，两个正交轴（需求固定 / 需求同比）各 3 档 ×12 重复 | `reports/scenario-bench/l-tier-degradation.md`。A 轴完成率 0.5269→0.9264→**1.0000（CI 宽度 0，已饱和）**，边际 +39.95 pp→+7.36 pp；B 轴完成率不退化（0.8620→0.9537）但补能阻塞 10.9→209.6→**1313.7 车·分（车 4×、阻塞 120×）**、候选 2.7→5.0→**13.1（40 台是 20 台的 2.6×）**。生产同形状已核到行：候选集=全量在线空闲车、每台跑两次路径规划 ⇒ 减候选是 L 档最直接的杠杆。**范围/桩数仍为现役值，不是 §1.3 目标 L 档**。另抓出 `pickup_wait_mean_s` 在窗口=1 下结构性恒为 0（等待未建模），已从该表删除并写明。见 §13.20 |
| §7.2 高峰档出口 + 调度器自覆盖 | 无 `schedule_end_cron` 的 PEAK 超 `fsd.peak-mode.max-peak-duration-minutes`（默认 120）回落 NORMAL；`dispatchflow.peak.schedule{state=no_time_source\|peak_without_schedule\|invalid_cron}` 让「没有时间表」与「cron 写错」可区分；**三条分支原先在 `setMode` 之后用本轮快照 `updateById` 整行覆盖，把刚设的档口撤销** ⇒ 改为只打时间戳列 | 5 条 `PeakModeCronSchedulerTest`（回落 / 上限内不动手 / 有 cron 不越权 / no_time_source / invalid_cron）+ 一条「禁止 updateById(快照)」回归守卫；本机 park 2 由永久 PEAK 落到 NORMAL 并停住。见 §13.24 | | §7.2 MQTT 重订阅 | `Vda5050MqttGateway` 由 `MqttCallback` 改 `MqttCallbackExtended`，重连时在 `connectComplete` 里重新订阅；订阅收敛成 `subscribeStateTopic(via)` 且吞掉 `MqttException` 不外抛； `dispatchflow.vda5050.mqtt.connection{event=lost|resubscribed|resubscribe_failed}` | 4 条单测（重连必订 / 首连不重订 / 订阅异常不外抛并计数 / 掉线计数）。**无 live 证据**：本机 `vda5050.enabled=false` 且 compose 里没有 broker ⇒ 列入部署前置。见 §13.26 | | §7.2 处置人不再写死 | `front/src/views/exception/Index.vue`：`resolverIdentity()` 取 `authStore.user?.username` 与 `displayName`，单条与批量两处调用点都改用它；拿不到身份 ⇒ 先报错拦下，不发请求（后端只校验 `@NotBlank`、不校验来源，所以来源只能由前端保证） | `npx vue-tsc --noEmit` 通过；仓库内 `u1001` 仅剩注释里的历史说明。**未做浏览器实测**（本机 `FSD_ADMIN_AUTH_ENABLED=false`，登录态不启用 ⇒ 无法在界面上验证真实用户名会落库），这条属部署后需复验项 | | §7.2 并列裁决显式化 | `RulePolicy.rankOrder()` = 总分 → `vehicleCode` 字典序 → `vehicleId`（空值 nullsLast），替代「稳定排序 + DB 返回顺序」的偶然裁决 | 3 条 `RulePolicyTest`（并列与入参顺序无关 / 次级键不得压过分数差 / 空代码按 id 兜底）。影响面实测：558 条成功派单里 4 条（0.7%）原先由顺序决定；9 条黄金值、250 条服务断言、实验台默认臂基线（0.93±0.005 / 138.2 km±2 km）零改动通过。见 §13.25 | | §7.2 失败原因标签拆开 | `DispatchAssignFailReason` 新增 `NO_MATCHING_VEHICLE`（`fail_reason VARCHAR(32)`，无需迁移）；SOC 与五个约束过滤器拆成两层，约束做成命名过滤器表 `orderConstraintFilters()`，**筛选与诊断共用同一张表**并逐层记存活数；`DispatchFailExplainSupport` 补中文与三条建议、`suggestionLinks` 指向 vehicles；前端 `constants/dispatchFail.ts` 补标签与跳转 | 单测 2 + 3 条（约束不匹配必须报新编码且消息点名 binding 层；低电+维保混合要分开报；有原文时保留诊断、无原文时不退化成 `LOW_BATTERY`）。**live 复现**：本机把 20 台 IDLE 车 `delivery_zone` 改 `ONLY_NORTH` → 快照读到 `NO_MATCHING_VEHICLE / candidate_total=20 / soc_eligible=7 / survivors {MAINTENANCE=7, VEHICLE_TYPE=7, FLEET_POOL=7, …}`（旧口径报 LOW_SOC），探针后逐行还原、残留 0。限定：本文档 §13.23 之前的所有 `LOW_SOC` 计数只能读作"SOC 或约束不满足"。见 §13.23 |
| §M5 重压测双口径 + 仪表两处缺陷 | 两臂各 8 轮 × 20 单（图缓存 ON / `GRAPH_CACHE_TTL_MS=0`），同机、同刷新后的 jar、同 seed 权威图；`run-backend-local.sh` 改为在「源码比 jar 新」时先 `mvn -DskipTests -pl fsd-bootstrap -am install` 刷新（`RUN_BACKEND_FRESH=0` 跳过）；`ScenarioBench.T_975` 与 `tval()` 的 t 表由 t(df=n) 修正为 t(df=n−1) | `reports/latency/m5-two-calibers-post-mapf.md`：P50 77.61 [72.20,83.03] vs 103.96 [96.41,111.50] ⇒ **缓存贡献 +33.9%、8×8 逐轮全分离**；P95 区间重叠 ⇒ 分不出来；**MAPF 修复不吃时延**（修复前 78.19 / 陈旧 jar 意外复测 77.78 / 修复后 77.61）；`dispatchflow.mapf.reservation` 首读 reserved 267 / conflict 86 ⇒ 冲突率 24.4%。两处仪表缺陷见 §13.22；新增 `studentTUsesNMinusOneDegreesOfFreedom` 守卫 | 
| §M2E 装载器修正 | way 按提取框**裁断** + bbox 判定改回 WGS84；新增 `detour_factor()` 实测绕行系数；seed 头部落 `components/largest_share/detour_factor_*` 与 ODbL 出处 | 裁断后 91 节点 / 113 边 / **24 354 m**（裁断前 40 157 m，多算 39%）、加权均速 17.84、绕行系数均值 1.481（5486 对）。详见 §13.9.2 |
| §M2E 换图可重放 | 新增 `scripts/geo/swap-road-network.sh`（停用旧图 → 灌裁断版 OSM 图 → 重吸附 → 重置车辆 → 可达率验证，`--restore` 回退；mysqldump 备份、密码不进 argv、非本机库直接拒绝） | 双向都实跑过：灌图后 `91 ACTIVE 节点 / 40/40 ROUTE_OK / PASS=12 WARN=1 FAIL=0`；`--restore` 回退后 `55 ACTIVE / 1 个强连通分量 / 40/40 / PASS=11 WARN=2 FAIL=0`，且六段业务列指纹与 seed 权威态逐字符一致。A/B 判语见 §13.11 |
| §M2E 时延基准口径 | `bench-dispatch-latency.sh` 重写：**每轮前重置车队** + **id 窗口只取本轮行** + **分位数只对成功决策算** + **多轮 t-CI** + 单轮内前半/后半漂移 | 旧口径的失真逐个实测暴露：失败路径 6 ms 混进 P50（旧 P50 97 ms 是混样结果）、`--verify` 不写库导致回退后车辆错映射、字段列错位把"前半均值"当成"候选峰值"打印。新口径下**单轮内确实越跑越慢**：扩范围前半程 62.06 → 后半程 111.78 ms（1.80×）、扩范围后 85.55 → 166.20 ms（1.94×）。判定见 §13.11 |

### 13.2 本轮新查出、且改变判断的两条事实

1. **撤回 §13 早期一版写下的"A\* 没在工作"结论——它错了，同时修正 §0.2 的一行。** 我当时只看到"24/79 节点无经纬度"，没有按 `status` 分组。实测（`GROUP BY status`）：
   - `t_road_node`：**ACTIVE 55 个，全部带 GPS**；那 24 个无经纬度的节点**全是 `status=DISABLED`**，`resolveGraph` 的 `eq("status","ACTIVE")` 已把它们排除。
   - `t_road_segment`：ACTIVE 86 条（不是 §0.2 写的 124 条全 ACTIVE）。
   - 因此 `isMetricConsistent()` 返回 true，**A\* 当前确实在米制上工作**。§0.2"79 节点 / 124 路段全部 ACTIVE"这一行需按 55 ACTIVE + 24 DISABLED / 86 ACTIVE 路段理解。
   - 仍然成立的机制：`ParkRoutePlannerServiceImpl:321-333` 在"部分节点有 GPS、部分只有 schematic"时会**静默**回退 Dijkstra，日志只在 `debug` 级打。这是一个真实的隐性降级开关，M2E 重取 OSM 时若产生无坐标节点会立刻触发，届时要让它可观测（`isMetricConsistent` 进指标或快照），而不是当性能优化项。
   - 附带查出的一条：把 §10.1 的"改写合法 GCJ-02"现在就执行会**更糟**——整个园区 GPS 外接框（`121.071–121.088 / 31.960–31.964`）内所有点到 `nearestNode()` 的像素式欧氏距离都会吸附到同一个节点 `RN21`（误差 86.4 px），等于把所有车折叠到一个角。GCJ-02 改写必须与 §7.6 的坐标解析改造同批做。

2. **`V13b__*.sql` / `V20b__*.sql` 被 Flyway 静默忽略**（版本号不符合 `数字(.数字)*` 语法）。它们只在 `back/sql/init/00-run-migrations.sh` 裸跑时生效。§0.1 写"53 文件（V01–V51 + V13b + V20b）"是对的，但**迁移文件数 ≠ Flyway 认知数**，做"新库 vs 已有库"两条路径验证时必须分别核对。

### 13.3 关于"生产 Flyway 版本"——§10.2 第 2 条已有答案

`docs/DispatchFlow_部署整改任务路线图_2026-09-21.md` §执行记录实测：生产 `flyway_schema_history` **只有 2 行**——`50 / Pre-Flyway migrations V01-V50 / checksum NULL` 与 `51`。含三点推论：

- 生产基线在 **V50** 而非仓库配置的 V20，即生产的 V01–V50 是**裸 SQL 灌进去**的（早于当前 `00-run-migrations.sh` 的 V01–V20 版本）。
- 因此 §7.5「新库与已有库两条路径各测一遍」里的"已有库"必须按 **V50 基线**这一形态来测，而不只是 V20。
- 下一轮部署会给生产补 **V52/V53/V54**；V52 是幂等列守卫、V53 是可空列、V54 是 `CREATE TABLE IF NOT EXISTS`，三条对 V50 形态的库都是安全的。

### 13.4 本地端到端实测（M0 闸门 + M1 闸门）

起 `back/docker-compose.yml` 的 mysql/redis/rabbitmq + 宿主机后端（`scripts/dev/run-backend-local.sh`），对 `ZJF-PICK-01 → ZJF-DROPOFF-01` 建单并触发 `/api/dispatch/tasks/65/auto-assign`：

- 结果：`status=ASSIGNED`、`selectedVehicleCode=ZJF-AV-03`、`assignScore=779.7502` —— **"本地能派出一单"成立**。
- 决策快照落库一行，M1 闸门"当时为什么选这台车、差多少分"可直接回答：
  - 漏斗 `3 → 遥测未过期 3 → SOC 通过 3 → 全链路 SOC 通过 3 → 可达 3 → 完成打分 3`，`fail_reason=NULL`；
  - 三台车**路网距离完全相同 778.2502 m**，胜负只由 SOC 余量决定：`ZJF-AV-03 socMargin=1.5` / `ZJF-AV-01 3.15` / `ZJF-AV-02 5.4`，总分 `779.7502 / 781.4002 / 783.6502`；
  - `score_gap=1.65`、`tie_count=1`、`policy_id=RULE`、`match_algorithm=GREEDY`、`road_graph_version=nodes=55,edges=172`（**独立印证 §13.2：参与建图的是 55 个 ACTIVE 节点，不是 79**）、决策耗时 `58.79 ms`。
- 顺带证伪/证实的三件事：
  1. 演示数据重置脚本的坐标决策（写 schematic 而非 GCJ-02）是对的 —— 派单真的通了，而 `road_graph_version` 显示 A\* 在 55 节点米制图上工作。
  2. 宿主机跑后端的本地开发链原本是**断的**：`application.yml` 的 `DB_PASSWORD` 默认 `changeme`、RabbitMQ 默认用户 `fsd_user/changeme`，与本地容器不一致（走 compose 才由容器环境变量注入），已在 `scripts/dev/run-backend-local.sh` + `CONTRIBUTING.md` 修好并对齐。
  3. 本地库补到 V50 之后，移动端下单契约开始生效：`idempotencyKey` 必填、`X-Mobile-Api-Key` 必填（且库里 `t_external_api_key` 没有 ACTIVE 行可取）。**`scripts/dev/test_zjf_order_flow.ps1` 因此已过时**，M0 收尾时要一并更新。
- 已知缺口（下一轮补，均为本人本轮新引入的口径问题）：
  - `t_dispatch_decision_snapshot.task_id` 在**首次自动派单**这一路径上是 NULL —— 决策发生时 `order.dispatch_task_id` 尚未回填。`order_id` 可作为可靠连接键；要坐实 `task_id` 需在 `DispatchTaskServiceImpl` 派单成功后补一次 `attachTask(orderId, taskId)`。
  - 候选漏斗各层的通过数（`fresh_telemetry_count` 等 4 列）由 **V55** 补齐；本轮先落表与实体，未进漏斗时写 NULL 而不是 0，避免"没车可派"和"暂停被挡下"在失败原因分布里混成一个形状。

### 13.5 闸门现状

| 闸门 | 状态 |
| --- | --- |
| M0 | 本地能派出一单 ✅、`mvn -pl fsd-bootstrap -am test` 全绿 ✅（317 + 92 + 9 + 8）、三环境"版本一致"按 **代码 V56 / 本地 V56 / 生产 V51（待部署追平）** 理解；剩 `提交/丢弃未提交改动` + tag |
| M1 | §7.2 前四项 ✅ 带测试、§7.3 快照表 + 写入点 ✅ 并有真实一行、§2.1 `DecisionPolicy` + `RulePolicy` 等价迁移 ✅ 带黄金值与内核纯度守卫 ；**失败原因标签已拆开（§13.23）** ⇒ §7.3 的"派单失败原因分布"自此可读 → **M1 四项任务齐**，缺 `ForecastAwarePolicy`（属 §2.1 实现 B）与任务详情页 top-3 展示（属 M7） |
| M2 | §7.5 前三条 ✅（§13.6：地理内容改走 seed、两条初始化路径指纹一致、迁移只留 DDL）、§7.6 主体 ✅（两条尾巴有意留，§13.7）、地理池 20 台 ✅、compose 透传 ✅。**未做**：§1.5 的 polyline 目前只在裁断版装载器里产出、还没进权威 seed；4 个新站点排在 M2E 之后；闸门里"绕行系数由假设变实测"已达成（1.481），"前端零硬编码"一行未动 |
| M2E | 装载器/重吸附/分量表/换图脚本 ✅，A/B 已出判语：**闸门不通过**（P50 显著劣化 +42%、新增 `UNREACHABLE` 34/100），扩范围图不进 seed、不上线；两条前置（车辆落到最近可通行节点、画布重定标）见 §13.11。本地库已回 seed 权威态并逐项验过 |
| M3 | ✅ **闸门通过**：`bash scripts/dev/scenario-bench.sh` 一条命令产出 12 次重复 + 95% CI 的指标表与假设清单，`sameSeedIsFullyDeterministic` 钉住可复现。两处口径偏离已写在 §8/M3（范围用现役 1613×500 m、无站点维度） |
| M5 | 图缓存 ✅ `ParkRoadGraphCacheTest` 6 条；批量撮合 ✅ 已量（§13.13 三段对照表，匈牙利解对过穷举）—— **真实链路待本人定 SLA 口径**；**MAPF 单位统一 ✅（§13.19：预约窗口原只覆盖 37%–62%，现按米÷米每秒；拦单与否仍未定，已加冲突率计数器）**；**L 档退化曲线 ✅（§13.20：完成率 40 台前就饱和，退化集中在补能阻塞 120× 与候选集 2.6×；范围/桩数仍为现役值，不作容量证明）**；**重压测双口径 ✅（§13.21）** —— 图缓存贡献 P50 +33.9%（8×8 全分离，且这是 JVM 内缓存不是 Redis）；**MAPF 修复本身不吃时延**（77.61 vs 修复前 78.19）；冲突率 24.4% 首次可读 |
| M4 | 错峰返充 **代码已接入真实链路**并有测试（§13.14③ 数字：完成率 +7.38pp、排队 −14.1 车·分钟；换电支路明确不推迟）；**回退已可观测（§13.15：五档状态 + 两个计数器 + 限速 WARN）**，且顺带查出差两个数量级不是笔误而是**绝对阈值不可标定**，于是加了 `min-peak-pressure-ratio` 相对判据堵住"日作业一调度就整天推迟返充"这个坑；**选桩这条已量完，判语是三条"不值得做"（§13.16）** —— 6 根桩同点使选址成为空命题、摊开桩位对完成率无收益，而"开去充电"这条腿此前一直被漏算（漏算抬高完成率约 3pp、把补能价值高估 2.84pp，并让"接驾距离"那一项方向翻号）；**时机与预置两条对照已在有腿模型里重跑完（§13.16-a/c），能耗敏感性也扫完（§13.16-b：单常数 100→250 m/1% 推动完成率 14.63pp，成为全部完成率结论的扰动上限）**。**仍不算生效/仍待做**：`t_energy_forecast` 无行（日作业未调度，属部署动作）、真实高峰曲线与电价。**已完成**：ETA 那条（§13.17，含"调用点漏传参数导致逐段限速从未执行"+ 前后端两个不一致速度常数）、充电曲线（§13.18，生产侧无落点 ⇒ 做成敏感性并与能耗带合成 ≈22pp 引用限定） |
| M6 | 站点×小时画像 ✅（§13.14①，含"各格合计=名义到达量"的一致性断言）；热区预置 ✅ 已量，并且**已在"有补能行驶这条腿"的模型里重算（§13.16-c：接驾 −91.6 m、端到端 −18.6 s、完成率分不出来、抢桩代价 +25.2 车·分钟仍在）** ⇒ 这条是取舍题，不是"实测有收益"；贝叶斯基线 vs GBM、P90 覆盖率 77.78% 的对外声明仍待做 |

### 13.6 M2 第一批：地理内容改走 seed（§7.5 前三条）

| 产出 | 内容 | 实测 |
| --- | --- | --- |
| `scripts/dev/export-geo-seed.sh` | 从实库导出地理当前态为**按业务键幂等 upsert**；自增 id 一律写成按 `park_code`/`station_code`/`slot_code` 回查的子查询，所以新库与已有库都能收敛到同一份状态 | 270 条 upsert，覆盖 9 张地理表；`t_park` 1 / 围栏 6 / 站点 13 / 节点 79 / 路段 124 / 泊位 6 / 桩 6 / 建筑块 7 |
| `back/sql/seed/zjf_geo.sql` | 地图内容的唯一来源，替代"再开一个 V*.sql 改坐标" | 对已有库连跑两次，`CHECKSUM TABLE` 九张表全部不变（值相等 -> 行不更新 -> `updated_at` 也不跳）**—— 这条证据的仪表后来被否证：见 §13.11 第 2 条，`t_parking_slot`/`t_charging_pile` 有仿真器每 tick 在写的运行时列，整表校验和不能当权威态指纹；权威态判据改用 `verify-geo-init-paths.sh` 那套"只比业务列"的指纹（已在 §13.11 重跑，逐字符一致）** |
| 幂等 ≠ 空操作 的反证 | 故意把 `ZJF-PICK-01.station_name` 改成坏值、把 `ZJF-ZONE-CORE-NORTH.status` 改成 `DISABLED`，再跑一次 seed | 两项都被还原（`南通家纺城门市` / `ACTIVE`），且中文未被写坏。**注**：还原会把 `updated_at` 顶到当前时间，这是 `ON UPDATE CURRENT_TIMESTAMP` 的正常行为 |
| `scripts/dev/verify-geo-init-paths.sh` | 把 §7.5「两条路径各测一遍」变成一条命令：路径 B 已有库 + seed；路径 A 一次性探针容器裸跑 V01–V20（与 `00-run-migrations.sh` 同一套文件挑选规则）→ Flyway baseline 20 迁到 V55 → 同一份 seed；随后按业务列做指纹比对 | 路径 A 结果：基线 22 个文件、`Successfully applied 35 migrations ... now at version v55`；8 张表指纹与已有库**逐字节相同**；脚本对"两侧都查空"加了行数下限守卫（早期一版就因为 `-v` 用了 Git-Bash 风格路径挂不上而**假绿过一次**，已修） |
| §M2 compose 透传 | `back/docker-compose.yml` 补 `FSD_AMAP_WEB_SERVICE_KEY` | 顺带核对出：**根 `docker-compose.yml` 没有 backend 服务**，它只 `include: back/docker-compose.yml`，所以 §0.2 的"两处未透传"实际是一处 |

还没做完的 §7.5 尾巴：`geo-py/scripts/seed_from_migrations.py` 仍在用正则解析 Flyway SQL（seed 收敛后必须改成读 seed，否则直接失效）、14 对象叠一点的微偏移、`DEFAULT-BOUNDARY` 按 §1.6 四条命名街道重画、前端硬编码副本（属 M7 §6.4）。

**§1.8 四个新站点的顺序修正（实测后决定，别按原顺序做）**：当前 ACTIVE 路网节点的 GPS 实测范围是 `lng 121.0710–121.0880 / lat 31.9593–31.9646`。把 §1.8 的四个点放进去比：

| 点 | 坐标 | 是否落在现有路网内 |
| --- | --- | --- |
| `ZJF-DROP-05` | `121.082213, 31.959503` | 在（且只贴近南边界 20 m） |
| `ZJF-PICK-03` | `121.074588, 31.966227` | **不在**，偏北 180 m |
| `ZJF-DROP-06` | `121.071107, 31.965644` | **不在**，偏北 115 m |
| `ZJF-EXPRESS-02` | `121.081861, 31.969579` | **不在**，偏北 550 m |

而 `nearestNode()` 用的是**像素**坐标欧氏距离（`ParkRoadGraph:226-230`），路网点不够密时它不会返回"不可达"，而是把圈外的点**吸附到最近的边界节点**上 —— 结果是路线看起来通、实际把车折叠到路网一角（§13.2 那条 RN21 塌陷就是同一机制）。所以 §M2 里"建 4 个新派车站点"必须挪到 **M2E 重取 OSM 之后**，否则做出来的 3 个点是假可达。M2E 的提取框 `31.9550–31.9715` 覆盖得到这三个点。

### 13.7 §7.6 示意调度删除：逐条落地与实测

数据侧先复核（本地库实测，只读）：`t_station` 13 行（9 ACTIVE + 4 INACTIVE）**全部 `delivery_zone='GEO_DELIVERY'`，零条 SCHEMATIC** —— §7.6 里"4 条 INACTIVE 需核"这一条已经结掉；`t_vehicle` 3 台全 `ZJF-AV-*`、`delivery_zone=BOTH`；`t_route_audit` **零行**（所以删 `SCHEMATIC` 路线模式没有历史包袱）；`t_order` 里 SCHEMATIC 订单 0 条。另：§7.6 末条说"t_park 2 条记录中是否仍有非 ZJF 园区"，**实测只有 1 个园区 `DEFAULT:ACTIVE`**，这条已经过时。

| §7.6 删除项 | 状态 | 落点 |
| --- | --- | --- |
| 示意池装配 `ensurePilotFleet("PARK-", ...)` 与压力恢复分支 | ✅ | `initializeVehiclesIfNeeded()` 只剩地理池一次调用；tick 里的双池恢复合成一条 `hasGeoDispatchDemand()`，顺带**去掉每 tick 逐单查订单+两个站点**的开销 |
| 车辆族判定 | ✅ | `PilotFleetSupport` 删 `SCHEMATIC_VEHICLE_PREFIX`、`isSchematicPilotVehicleCode`、`isSchematicPilotVehicle`、`isSchematicDeliveryStation`；`matchesOrderFleet` 从三参降到一参（只认地理池）；`PilotFleetSupportTest` 重写为钉"其他前缀不可派单" |
| 派单侧双模分支 | ✅ | `DispatchVehicleAssignServiceImpl` 两处 `GEO_DELIVERY / SCHEMATIC` 三元判定收敛为恒 `GEO_DELIVERY`；`matchesDeliveryZone` 的 `pickup` 参数随之删除 |
| 配置项 `simulation.vehicleCount` | ✅ | 全仓零读取后删掉 `ParkPilotProperties` 字段与 `application.yml` 的 `vehicle-count` 行（`geo-vehicle-count` 保留并定为 20） |
| 路线模式枚举 SCHEMATIC | ✅（仅注释） | 代码从不产出该值，只在 5 处 javadoc/字段注释里列着，已改为 `REAL_ROAD / STRAIGHT_LINE`；`StationEntity`/`ParkStationResponse`/`VehicleEntity` 的 `delivery_zone` 注释同步标注停用。**DB 列注释留在 V37 不动**（改已应用迁移是禁区） |
| 助手车号解析 `PARK-` 族 | ✅ | `DispatchAssistantAdminServiceImpl.VEHICLE_CODE_PATTERN` 去掉 `PARK-` 分支 |
| 数据库侧 | ✅（复核结掉） | SCHEMATIC 站点 0 条、`t_park` 只有 DEFAULT，无需清理脚本 |
| 像素坐标路径 `parkXYToGcj02` + `useDeliveryGeo.ts` 兜底 | ⏸ **有意不删** | 后端这两列此刻仍写 schematic 像素（§7.2 未做），`vehicleToGeoPosition`/`stationToGeoPosition` 的兜底是车标和站点标能上图的唯一依赖；现在删 = 地图空白。必须与坐标改写、M2E 扩路网同批做 |
| 前端 `dev/MapPoc.vue` | ✅ | 文件删除 + 路由 `dev/map-poc` 摘掉（无导航引用）；`npm run build` 通过（dist + sw.js 产出） |
| 前端 `components/demo/DemoModePanel.vue` | ⏸ | 被冻结文件 `Tracking.vue:133/605` 引用；Tracking 的解冻例外只覆盖"坐标解析与地图层"，删演示面板属 §6.2 监控台重建 → 归 M7 |
| `digital-twin/Index.vue` 降级为回放抽屉 | ⏸ | §6.2 明确入口，归 M7 |

**闸门实测**：`mvn -pl fsd-bootstrap -am test` → **263 + 87 + 9 + 8 全绿**（改坏过 3 处、已修：规模压测与两个控制器测试的 `PARK-` 夹具、`PilotFleetSupportTest` 的示意断言）。本地起后端后，仿真器自动补足 **20 台 `ZJF-AV-*`**（零台 `PARK-*`，SOC 35–100），建单 `ORD…1933134421` **直接 `ASSIGNED` 到 `ZJF-AV-06`**；决策快照 `20 → 20 → 20 → 20 → 20 → 20` 全漏斗通过、`road_graph_version=nodes=55,edges=172`、耗时 109 ms。
→ "示意调度删除后测试全绿且地理池仍可派单"成立。

**顺带撞出两条新问题**（已写进 §7.2 清单）：`LOW_SOC` 标签掩盖了"车号前缀/车型/载重不匹配"（§7.3 的失败原因分布因此不可信）；20 台同泊位时出现 `score_gap=0 / tie_count=2`，并列由 DB 返回顺序决定。

### 13.9 M2E 第一批：OSM 扩范围实测（含两条必须先解决的阻塞）

**先纠正 §1.6 路 A 的一句话**："`scripts/carla/osm_to_pilot_geo.py` → `data/pilot_osm_geo.json` → 节点/路段入库"这条链**只到 JSON 为止**。JSON 是给运行时画图的（`OsmPilotGeoRepository` / `PilotGridRoads`），而 `t_road_node`/`t_road_segment` 的行是 **V38 手写的 5×5 网格交叉口**（备注写着"西排路×金洲大道"，线性映射 `x=(lng-121.072)*77000, y=(31.9645-lat)*150000`）。所以扩范围得先有真正的 OSM→图 装载器：新写 `scripts/geo/osm_to_road_graph.py`，产出 `back/sql/seed/zjf_road_network.sql`。

**取数实测**：Overpass 主站首两次分别 406 / 504，带 UA + 重试后 **200，112 KB**（`osm_base=2026-09-21`，ODbL）；镜像 `overpass.kumi.systems` 直接连不通。原始抽取落 `data/map.expanded.osm`（未覆盖 `data/map.osm`）。

| 实测项 | 值 | 影响 |
| --- | --- | --- |
| 目标 bbox 内 highway way | 66（原始 105 条里其余在框外） | §1.6 预估"400–600 节点"落在**同一量级**：346 节点 / 321 边 / 36.99 km |
| 边带 `polyline_geojson` | **321/321** | §1.5"124/124 路段无 polyline"由这条路径根治 |
| 方向 | 291 双向 / **30 单向** | §1.5"124/124 全 BIDIRECTIONAL 与现实不符"修正为实测 9.3% 单向 |
| 限速按长度加权均速 | **16.64 km/h** | 顶替 §1.1 的假设 15.52 km/h（该假设本就标注"非实测"）；绕行系数也就能从假设转实测（M2 闸门那条） |
| 图完整性 | 0 条悬空边端点；同一份 SQL 连跑两次不产生重复行 | 装载器可按业务键幂等重放 |

**两条阻塞，先解决才能真扩范围**：

1. **连通性塌了**：28 个连通分量，最大的只含 86/346 = **24.9%** 节点（第二名 82）。成因是 OSM 里两条 way 相交却不共享节点（立交/近似交叉都很常见），不是数据缺失。不先做**邻近节点 snapping**（按距离阈值合点）就灌库，扩范围后约 3/4 的点 `isReachable=false` → 大面积 `UNREACHABLE`。**这正是 §1.6 路 C 要避免的后果，只是换了成因**：靠"重取 OSM"并不自动等于"图连通"。
2. **示意画布装不下**：346 个节点里 **278 个**落在现有 schematic 画布（1300×800 px）之外，其中含负坐标（新框北界 31.9715 映射到 `y≈-1050`）。所以扩范围必须同时重定义 schematic 映射 —— `t_park.map_width/map_height` + `anchor_lng/lat` + `park_width_meters` 三者要与前端 §6.4 那批硬编码（`zjfPilotGeo.ts`、`zjfStationAnchors.ts`）一起改，否则前端图与 `nearestNode()` 的像素吸附双双错位。这条与 §7.2 的坐标语义改写、§7.6 的像素路径删除是**同一批活**。

**已顺手补上的测量工具**：`scripts/dev/bench-dispatch-latency.sh`（每单一次真实 MySQL + 真实路网 + 真实图缓存的选车，读 §7.3 快照的 `duration_micros` 算分位数）。扩范围**前**的基线：21 次成功决策，**P50 97.28 / P95 164.18 / P99=max 174.51 ms**，候选峰值 18 台。闸门"扩范围后 P95 不得高于扩范围前"从此有了可比口径。附带查出：本地下单有 `MOBILE_ORDER_RATE_LIMIT`，40 单里 10 单被限流 → 基准必须带节流。

**装载器自己的实现债**（下一步与 snapping 一起修）：只有 39/321 = 12% 的边折出了中间形状点，说明链折叠对"整条 way 只有一个交叉口"的情形处理不足；`--report-only` 与写文件共用同一条统计路径，验收时要分别核对。

**M2E 因此重排为**：装载器（已交）→ 邻近 snapping pass → 重定标 schematic 映射（与 §7.2/§7.6/§6.4 同批）→ 灌库 + 并 seed + `verify-geo-init-paths.sh` → 测 P95-after 与可达率 → 再建 §1.8 那 4 个站点。

#### 13.9.1 M2E 已试跑一遍并主动回退 —— 闸门当前"不可评估"

上一段的重排顺序里，"snapping 是必需的"这一条被实测否证了，而且撞出一个更要紧的问题。先记下已交付的机制：`scripts/geo/osm_to_road_graph.py`（含 `--snap-meters`）、`scripts/geo/reanchor_facilities.py`（站点/泊位/桩/服务位重吸附到**最大连通分量**内的最近节点，超阈值只报告不改）、**V56** `t_road_node_component`（§1.8"回退到可达子集并记录断点"的落点：`component_id/component_size/is_largest`），以及 `scripts/dev/bench-dispatch-latency.sh`。

试跑过程与实测：

| 步骤 | 结果 |
| --- | --- |
| 图碎裂是否靠 snapping 修 | **不靠。** 阈值扫 0/5/10/15/25/40/60 m，分量数恒为 2（84+9 → 55+2）。碎裂的真实成因是我第一版把度数算成"节点出现在几条 way 里"（一条 way 的内部点算 1 → 被误当交叉口），改成"去重后的无向邻接点数"后：节点 346→93、分量 **28→2**、最大分量 **24.9%→90.6%**。那个 9 点小分量是 OSM 里真被隔开的独立路网片段，合并不掉 —— 所以 `is_largest` 子集 + 记录断点是正确解，snapping 只是 5 m 量生的微调（默认 10 m） |
| 无向规范化 | 必须做：`t_road_segment` 一行 `BIDIRECTIONAL` 会被 `ParkRoadGraph` 展成两个方向，所以 (a,b)/(b,a) 得合成一行，否则边数与总里程双双翻倍（曾测出 224 边 / 77.9 km，实为 119 边 / 40.65 km） |
| 扩范围图灌进本地库 | 93 节点全带 GPS → 度量一致 → A\* 生效；重吸附 38 处锚点全部落在 63–147 m，无超阈值；`reset-demo-dispatchable.sh --verify` 从 **34/40 ROUTE_OK 修到 40/40**，`FAIL=0 WARN=1`（那个 WARN 就是 2 分量本身，被记录而不是被藏掉） |
| **M2E 时延闸门** | **无法判定。** 同一份扩范围图 P95=464.48 ms；回退到原 55 节点网格后，同一份代码同一份数据重测，P95 第一次 **164.18 ms**、第二次 **320.34 ms**（P50 97.28 → 126.89 ms）。**同配置两次运行差近 2 倍**，所以"扩范围后 P95 不得高于扩范围前实测值"这句话在单次取样的口径下没有意义 —— 我既不能宣称通过，也不能宣称失败 |

**结论与对 §8 顺序的修正**：M2E 的验收依赖 N 次重复 + 置信区间，而那正是 §5/M3 的产出物。所以 **M3 必须排在 M2E 的验收之前**（不是排在实现之前：装载器、重吸附、分量表都可以先落地，但没有带置信区间的基准就没有"扩范围是否让演示变卡"的判断依据）。§8 的"依赖与顺延"里那句"M5 图缓存完成前不得执行 M2E 扩范围"要补一条：**M3 实验台建成前，M2E 只能做到"图可灌、可达率可测"，时延闸门挂起**。

**当前状态**：本地演示库已回到 seed 权威态（`PASS=13 WARN=0 FAIL=0`、55 ACTIVE 节点、`ZJF-PICK-01` 锚点回到 `RN07`）。顺带把 §7.5 的机制验了一遍：我手动改坏路网与锚点后，**应用 `back/sql/seed/zjf_geo.sql` 就把状态完整还原了** —— 这正是 seed 作为唯一内容来源要买到的性质。扩范围产物留在 `back/sql/seed/zjf_road_network.sql` + `data/map.expanded.osm`，原网格的 `data/backup/road_network_v44_grid.sql` 也留着。

**基准工具自身的债**：40 单里有 11 单被拒（`INTERNAL_ERROR` / `MOBILE_ORDER_RATE_LIMIT`），所以每轮实际样本只有 17–21 次成功决策；M3 用这条路径之前要先解决节流与首个 INTERNAL_ERROR 的归因。

#### 13.9.2 装载器的第二个缺陷：way 没有按提取框裁断 —— 13.9 表里的扩范围数字全部作废

写 M3 时发现要把场景范围换成"扩范围后的真实外接框"，一测 extent 得到 **5441 × 2529 m = 13.76 km²**，而 §1.6 定的提取框只有 2.12 × 1.82 km = 3.88 km²。追下去是 `osm_to_road_graph.py::load()` 的两处独立错误：

| 错误 | 实测后果 |
| --- | --- |
| `kept = [w for w in ways if any(in_bbox(...) for r in w.refs)]` —— **整条 way 保留**，只要它有一个节点在框内 | Overpass 返回的是完整 way，于是穿过园区的城市道路整条灌进来：**26/93 = 28% 的节点在框外**，离框边缘最远 **2530 m**、中位 1204 m |
| `in_bbox` 拿 **GCJ-02** 坐标去比 **WGS84** 的查询框 | 园区中心实测偏移 +421 m（经）/ −201 m（纬）（`wgs84_to_gcj02(121.07925, 31.96325)`）—— 生效框整体东移 421 m，等于又放宽了一遍 |

**修法**：`load()` 内按 WGS84 原始坐标把每条 way 拆成"框内连续段"（一条路进出提取框各成一段），框外段丢弃；判定一律用 WGS84，`BBOX` 注释写明它是 Overpass 查询框。

**裁断前后对照**（同一份 `data/map.expanded.osm`、`--snap-meters 10`）：

| 指标 | 裁断前 | 裁断后 | 说明 |
| --- | --- | --- | --- |
| 节点 / 边 | 93 / 114 | **91 / 113** | 节点数几乎没变，**里程差了 39%** |
| 边总长 | 40 157 m | **24 354 m** | §13.9 那句"36.99 km"和 seed 头部的 40.16 km 都作废 |
| 按长度加权均速 | 16.42 km/h | **17.84 km/h** | 框外多是低速 `service` 路，裁掉之后均速上抬 8.8% |
| 连通分量 | 2（84 + 9） | **4（78 + 9 + 2 + 2）** | 边界处被裁断产生的新端点，最大分量占比 90.6% → **85.7%** |
| 图外接框 | 5441 × 2529 m（13.76 km²） | **2104 × 1781 m（3.75 km²）** | 与 §1.6 的 3.88 km² 提取框对齐（差的正是 GCJ 偏移） |
| 画布外节点 | 72 / 93 | 72 / 91 | §13.9 阻塞 2 未变，仍与 §6.4/§7.2 同批 |

**顺手把 §1.1 的绕行系数从假设变成实测**：新增 `detour_factor()`，对最大连通分量的 **5486 个可通行点对**跑 Dijkstra，统计"路网最短路 / 大圆直线"：均值 **1.481**、中位 **1.374**、P90 **1.946**。§1.1 的假设 1.3 相当于中位数，M2 闸门那条"绕行系数转实测"就此有数可引；统计随 seed 头部一起落盘。

**当时的状态**：`back/sql/seed/zjf_road_network.sql` 已用裁断版重生成（头部统计含 `components/largest_share/detour_factor_*`，并加了 ODbL 出处行）。那一版还留着两个未验证的假设 —— "本地库灌图"没做、"设施一定还在最大分量里"没查 —— **两条都在 §13.9.3 里被实测改掉了一个、证实了一个**。

#### 13.9.3 可达率口径必须是有向：无向分量高估了 3 个节点

真把裁断版灌进本地库（新增 `scripts/geo/swap-road-network.sh`：停旧图 → 灌新图 → 重吸附 → 重置车辆 → 验证，`--restore` 回退）之后，第一版验证只有 **36/40 ROUTE_OK**。查下来是两层问题叠在一起：

| 问题 | 实测 | 处置 |
| --- | --- | --- |
| `swap-road-network.sh` 只跑了 `reset-demo-dispatchable.sh --verify`，而 **`--verify` 不写库** | 车还停在旧 RN 网格的像素上，"最近 ACTIVE 节点"是跨图乱映射 | 换图脚本里改成先 `--yes` 真重置、再 `--verify`；`--restore` 分支同样补上 |
| `reanchor_facilities.py::components_of_graph()` 用并查集算的是**无向**连通，而 `ParkRoadGraph` 把 `FORWARD` 只展成一个方向 | 裁断版图 113 条边里 **17 条单向**：无向最大分量 78 节点，**有向强连通最大分量只有 75**，另有 6 个"单点分量"（只进不出或只出不进）。用无向挑锚点会高估可达性 | 改成 Kosaraju 求 **SCC**，`t_road_node_component` 从此存强连通归属；`reset-demo-dispatchable.sh` 的车辆铺放与取货位配对判定都优先吃这张表（表空的老库仍回退到无向标签传播） |

**重跑后的实测（扩范围图）**：ACTIVE 节点 91（全部带 GPS → `isMetricConsistent=true` → 走 A*）、ACTIVE 路段 113、分量 **无向 4 个 / 有向 9 个（最大 SCC 75）**、设施锚点 **改 0 处 / 已正确 38 处 / 超阈值跳过 0 处**（裁断没有把任何设施挤出最大 SCC）、`reset-demo-dispatchable.sh --verify` = **PASS=12 WARN=1 FAIL=0，车-取货位 40/40 ROUTE_OK**（唯一 WARN 就是碎裂本身，按 §1.8"记录断点"处理而不是藏掉）。

**闸门第二条由此可判**：扩范围内 A* 可达率 **40/40 = 扩范围前 40/40**，不退化。第一条（时延）见 §13.10 的 A/B。

### 13.10 M3 仿真实验台落地（§5）

**产出**：`back/fsd-dispatch/src/main/java/com/fsd/dispatch/sim/ScenarioBench.java`（纯函数、无 Spring，驱动的是**生产同一份** `RulePolicy`）、`ScenarioBenchTest`（5 条）、`scripts/dev/scenario-bench.sh`（一条命令，`bash scripts/dev/scenario-bench.sh [重复次数]`）。闸门"同配置两次运行逐指标一致 + 换种子必须变 + 报告落盘"由测试钉住，CI 里同时是"基准没漂"的守卫。

**M 档实测**（20 台 / 56 单每小时 / 2 小时 / 6 桩 / 12 次重复，`reports/scenario-bench/m-tier-bench.md`）：

| 指标 | 均值 | 95% CI |
| --- | --- | --- |
| 完成率 | 0.93 | 0.90 .. 0.95 |
| 单均完成时长 / P95 | 742.6 s / 982.1 s | 728..757 / 948..1016 |
| 总里程 / 空驶率 | 138.17 km | 126.2..150.1 km |
| 充电次数 / 补能被挡（车·分钟） | 68.1 / **209.7** | 63..73 / 192..227 |
| regret（对事后下界） | **0.29** | 0.24 .. 0.34 |
| 失败分布 | `NO_VEHICLE` 7.92 单/次运行、`LOW_SOC` 0.67 | — |

**三条要写进判断的结论**：

1. **§1.3 的"6 桩不扩"在 M 档被打穿**：2 小时 68 次补能（34 次/小时）对 6 根桩，累计 **209.7 车·分钟**想补却没空位。§1.3 那句"充电需求 13.5 次/小时 > 现有 12"低估了近 3 倍，因为它按"充到满"算一次，而实际口径是"充到 `chargeCompleteSoc=90` 就恢复派单"的**多次小额补能**。
2. **M 档的瓶颈是占用而不是电量**：落单里 92% 是 `NO_VEHICLE`，`LOW_SOC` 只占 8%。所以 M5 的批量撮合应该优先于 M4 的补能策略拿收益，反过来不成立。
3. **贪心最近车留了 29% 的里程在桌上**（regret 0.24..0.34，分母是"每单都给最优车"的事后下界，所以 29% 是**乐观上界**）—— 这就是 M5 匈牙利对照实验要吃的量。

**我写的这三条断言各自抓到一个实现缺陷**（都是先红后修，不是补测）：

| 断言 | 抓到的缺陷 | 修法 |
| --- | --- | --- |
| `charge_sessions > 0` | `releaseIfReady` 以 `chargeForTicks > 0` 为条件，而**排队等待为 0 时该值是 0** → 车充完电 SOC 永远不复位；且更根本的是：只有"派单后 SOC 低于地板"才触发充电，而全链路 SOC 前置检查（对齐 `canCompleteTaskWithSoc`）根本不允许把车派到低于 30%，所以**充电这条支路在模型里永远走不到** | 改成独立的"空闲补能"一 pass，阈值全部对齐 `FleetEnergyProperties`（`returnToChargeThreshold=20` 必充可排队；`idleChargeWhenNoDemand` 语义下无落单时低于 `chargeCompleteSoc=90` 顺势补、只抢空位不排队），充电时长按 1800 s × 补能深度比例线性折算 |
| `0 < regret < 1` | 事后基线**漏了载货段**，且把**没被接下的单**也算进基线 → 实测 regret = **−0.15**，即"下界"比实际里程还大，口径完全反了 | 基线改为逐笔"被接下的单"计算：全车队最近直线接驾 + 同样载货段，且在移动车位之前取；指标更名 `hindsight_distance_served_m` |
| `charge_blocked_car_min > 0` | `stranded`（§5 的"SOC 抛锚次数"）在本模型里**构造性恒为 0** —— 前置检查不允许接跑不完的单，所以那个指标只会误导 | 删掉 `stranded`，改导"补能被挡的车·分钟"，并把"SOC 抛锚不可观测"写进假设声明页 |

**§5 逐条状态**：配置外置 ✅（含 `roadGraphVersion` 串，报告能看出用的是哪版路网）、固定种子可复现 ✅、指标导出 ✅（抛锚那条按上面说明改口径）、N 次重复 + CI ✅（小样本用 t 分位数，`studentT(12)=2.179` 有测试钉住）、事后最优基线 ✅（并明确它是**下界**，regret 偏乐观）、假设声明页 ✅（9 条，随报告落盘）、档位标签 ✅。

**M3 没覆盖到的两件事**（别把它当已经解决）：
- **§5 的"时段分布"没做**：到达过程是齐次泊松。高峰曲线属 M4（错峰返充）与 M6（需求预测）的输入，现在加进去只会让上面这些数字失去可比性。
- **M2E 的时延闸门不在 M3 里判**：M3 给的是**仿真指标**的 CI；闸门要的是**真实派单热路径**的采样。同一套"N 轮 + t 分布 CI"已搬进 `bench-dispatch-latency.sh`，判定结果见 **§13.11（结论：闸门不通过）**。

### 13.11 M2E 时延 A/B 实测：闸门**不通过**，扩范围图不得按现状上线

口径：`scripts/dev/bench-dispatch-latency.sh 20 5`，两组各 5 轮 × 20 单，每轮前重置车队，**分位数只对成功决策算**，跨轮用 t 分布 95% CI（`studentT(5)=2.776`）。A = 现役 RN 网格（55 节点 / 172 边），B = 裁断版 OSM 图（91 节点 / 113 边 / 17 条单向）。

| 指标 | A 扩范围前 | B 扩范围后 | 判定 |
| --- | --- | --- | --- |
| P50 | **78.19 ms** [72.52, 83.87] | **110.69 ms** [95.29, 126.09] | 区间不重叠 → **显著变慢 +42%** |
| 均值 | 86.07 ms [80.59, 91.54] | 124.74 ms [100.41, 149.07] | 区间不重叠 → +45% |
| P95 | 206.47 ms [131.39, 281.55] | 241.33 ms [168.53, 314.12] | 区间重叠 → **P95 本身仍不可判定**（5 轮不够，噪声 ±75 ms） |
| 成功样本 / 轮 | 15–20 | 12–15 | 明显掉 |
| 失败分布 | 仅 `NO_VEHICLE` / `LOW_SOC` | 新增 **`UNREACHABLE` 34/100** | 见下 |

**闸门判语**：§M2E 的"扩范围后派单 P95 不得高于扩范围前实测值"**未通过**（P95 单指标不可判定，但 P50/均值两路区间分离、方向一致变差，且吞吐下降）。**结论不依赖 P95 的分辨率** —— 只要 P50 显著劣化就足以否决"按现状上线"。

**`UNREACHABLE` 的机制（读代码 + 库内定位确认，不是猜）**：
1. 仿真车位置是**连续插值**的地理坐标：`ParkPilotSimulationServiceImpl.moveVehicleAlongRoute → syncParkCoordsFromGeo`（`:884`）把 GCJ 位置经 `parkGeoTransformService.fromGcj02` 换算成像素，**不吸附到节点**；
2. 派单侧 `nearestNode()` 取最近 ACTIVE 节点。实测 20 台车里 2 台的最近节点落在 **9 节点小分量（OSM0056）** 上，即"车物理上停在断头口袋里，但那个口袋与主分量不通"；
3. 于是这些车**永远派不出去**：快照漏斗显示 `candidate_total 8–11 → fresh 8–11 → soc_eligible 8–11 → soc_chain 1–4 → **reachable 0**`，即电量筛完还剩车，可达性把最后几台也清空了。
4. 上一节的 `40/40 ROUTE_OK` 之所以没抓到：它只测"取货位锚点 ↔ 车辆所在节点"，车辆锚点是重置铺出来的**合规节点**；真跑起来之后车会被沿路径开到任意坐标。**这是可达性验证的口径漏洞，不是图的偶然毛病。**

**因此扩范围要上线，缺的不是"再测一次"，是两件事**（都记进 §8 的前置）：
1. **车辆落位必须走"最近可通行节点"**（nearest *routable* node），并与 §7.2 的坐标语义改写、§7.6 的像素路径删除同批做 —— 现在这条链是"连续 GCJ 位置 → 像素 → 最近节点"，跨分量时会把车判成不可派；同时可达性验证要按**运行时同一套落位规则**取样，不能只测站点对。
2. **画布重定标**（91 节点里 72 个在 1300×800 之外）与 §6.4 前端硬编码同批，否则前端与 `nearestNode()` 双双错位。
3. 重测时至少 **8 轮**：5 轮的 P95 CI 宽 ±75 ms，分不开 30 ms 量级的差异（P50 能分开是因为轮内样本都参与、轮间方差小）。

**已做的收尾（全部实测）**：
1. `bash scripts/geo/swap-road-network.sh --restore` 把本地演示库退回 RN 网格权威态：删 OSM* 行（不是留着 DISABLED —— 留着会让"本地库"与"全新初始化 + seed"不等价）→ 用 `data/backup/road_network_v44_grid.sql` 改写成 REPLACE 落回 → 重吸附 → 重置 → 验证 = **55 ACTIVE 节点 / 1 个强连通分量 / 40/40 ROUTE_OK / PASS=11 WARN=2 FAIL=0**。
2. 回退之后又量出**两条工具自身的口径缺陷**，都已改：
   - `reanchor_facilities.py` **不是 seed-幂等的**：它按"最近节点"重吸附，在 RN 网格上把 38 个锚点全改成自己的选择（`改 38 处 / 已正确 0 处`），与 seed 记的值不同。**跑过 reanchor 的库必须重放一次 `back/sql/seed/zjf_geo.sql` 才算回到权威态。**
   - `CHECKSUM TABLE` 不能当权威态指纹：`t_parking_slot` / `t_charging_pile` 含占用与会话这类**运行时列**，仿真器每个 tick 都在写，实测同一份 seed 连跑两次整表校验和仍在变（`2760627026 → 3807217212`），照它判会**假报漂移**。改用"只比对 seed 拥有的列"的六段指纹（活动节点 / 活动路段 / 站点锚点 / 泊位进出 / 桩进出 / 服务位）后：**重放 seed 前后逐字符一致**，本地库确认等于 seed 权威态。§13.6 当初用 `CHECKSUM TABLE` 得出的"九张表全部不变"要按这个口径重读。
   - 顺带修掉 `reset-demo-dispatchable.sh --verify` 的假 WARN：刚重置完 `TIMESTAMPDIFF` 向零取整会出 `-1s`，旧逻辑要求 `age>=0`，于是把它报成"超过阈值 30s"；负年龄现按"刚刚上报"处理。
3. **扩范围图当前状态**：产物完整留在 `back/sql/seed/zjf_road_network.sql`（91 节点 / 113 边 / 24 354 m，分量与绕行系数统计在文件头）+ `data/map.expanded.osm`，**一条 `bash scripts/geo/swap-road-network.sh` 就能再灌回去**；等上面两件事落地后重测再上线。§5 的仿真与本节 A/B 都不依赖本地库当前是哪张图（仿真跑纯函数，A/B 两组各测各的）。

### 13.12 M4 前置证据：补能时机的开关值 24.5 个百分点（但这条**不是**"错峰返充的收益"）

M3 引擎里有专门的策略位（`Config.chargeTiming` 三档：`NEVER` / `OPPORTUNISTIC` / `DEFER_UNDER_PRESSURE`），所以 M4 第一条"错峰返充接线"的**接前 vs 接后**可以立刻出数字。方法：各臂同种子、同 12 次重复，**逐次配对相减**后再取均值与 95% CI（配对方差远小于两臂独立，不配对会把有效应误判成"分不出来"）。产物：`reports/scenario-bench/charge-timing-m-tier.md`（三段表），由 `ScenarioBenchTest.chargeTimingArmsAreComparedWithPairedConfidence` 生成并钉住判语规则。

| 指标 | `OPPORTUNISTIC`（空闲即补能，现状 `idleChargeWhenNoDemand=true`） | `NEVER`（只在必充阈值以下才回桩） | 配对差 | 95% CI | 判定 |
| --- | --- | --- | --- | --- | --- |
| **完成率** | 0.9264 | 0.6813 | **+0.2451** | +0.2144 .. +0.2758 | 可分，补能臂高 |
| 完成单数 | 103.4 | 75.7 | +27.75 | +23.35 .. +32.15 | 可分 |
| 空驶率 | 0.3475 | 0.3907 | −0.0433 | −0.0737 .. −0.0129 | 可分，补能臂低 |
| regret | 0.2888 | 0.3678 | −0.0790 | −0.1403 .. −0.0178 | 可分，补能臂低 |
| 充电次数 | 68.1 | 3.0 | +65.1 | +60.2 .. +69.9 | 可分 |
| 补能被挡（车·分钟） | 209.6 | 0.0 | +209.6 | +192.3 .. +226.9 | 可分 |

**三条判语纪律（已写进报告正文，防误读）**：
1. 只有 CI 不跨 0 才写方向（本表七项都可分，但这条规则必须先立着）；
2. **两臂完成量不同时绝对量不可比**：不补能臂里程低 3.6 万米不是"更省"，而是少成了 28 单；能读的是比率型指标；
3. 各臂之间只差策略位（`chargeTiming` 与其两个参数），换任何其它参数都要各臂一起重跑。

**第三臂已经建好并量完（`Config.chargeTiming` 三档 + 敏感性检查）**，结论比"接线"更要紧：

| 对照 | 完成率差 | 充电次数差 | 补能被挡差（车·分钟） | 其余指标 |
| --- | --- | --- | --- | --- |
| `OPPORTUNISTIC` − `NEVER`（补不补） | **+0.2451** [+0.2144, +0.2758] | +65.1 | +209.6 | 空驶 −0.043、regret −0.079，均可分 |
| `DEFER_UNDER_PRESSURE` − `OPPORTUNISTIC`（近 20 tick 落单 ≥3 就推迟） | +0.0009 [−0.0039, +0.0056] | −0.25 | −3.6 | **七项全部"分不出来"** |
| 敏感性：窗口=整段、阈值=1（落过一单就再也不顺势补） | **−0.0541** [−0.0981, −0.0101] | −55.0 | −94.8 | 里程 −13.6 km（可分，但少成 6.5 单，不可当"更省"读） |

三条读法：
1. **补能量是主效应**（+24.5pp），**推迟时机不是**（0）；把推迟推到极端则**显著有害**（−5.4pp）。所以 M4 第一条"错峰返充接线"按现状语义**没有可宣称的收益**，反而要先解决"何时允许补"的上界问题。
2. 温和推迟之所以测不出差异，机制清楚：现状语义里顺势补本来就要求"本 tick 无落空单"（`idleChargeWhenNoDemand`），推迟档只是把这个条件从"当前 tick"扩到"近 20 tick"，多让出的机会极少 —— 敏感性臂证明分支确实触发过（各指标都动了），不是"没接上"。
3. **限定：模型是齐次泊松到达、无电价**。真实园区有早晚高峰与分时电价，那种需求曲线"错峰"才可能有正收益 —— 这条结论**不能外推到"错峰无用"**，只能说"在 §5 当前口径下无收益，且 M4 若要拿它当收益项必须先补时段分布"。

**据此调整 M4 的做法**（写进 §8 的 M4 条目）：`shouldDeferReturnToCharge` 的接线**降级**为"桩位削峰的可选项"，不再作为收益项；把优先级让给 **选桩粒度降到桩**（`ChargingSessionServiceImpl.java:164-224`）与 **§1.3 的桩位确认**，因为真正卡住的是 6 桩容量（209.6 车·分钟排队）而不是补能时机。

> **⚠ 本节的"错峰无收益"限定在齐次到达场景，前提已被 §13.14 补掉**：给需求加上高峰段之后，同一策略（`DEFER_IN_PEAK`）测出**完成率 +7.38pp [+6.35, +8.42]、桩位排队 −14.1 车·分钟**，方向整个翻正。引用本节时必须带上这条。

> **读这一节数字的限定（§13.16 之后才成立）**：本节的对照全部产自**没有"开去充电"这条腿**的模型。补上这条腿后，同一个时机对照（顺势补 vs 只必充，站点场景）的完成率差从 **+0.4033 [0.3859,0.4208]** 缩到 **+0.3750 [0.3629,0.3871]** —— 高估 2.84pp ≈ 效应量的 7%，且两区间不重叠 ⇒ 不是轮间噪声；而"接驾距离"这一项的**符号随模型翻转**（无腿 +80.0 m、有腿 −52.7 m）。所以：下面这些判语作为"两臂同模型的相对结论"仍然成立，但**绝对量与"接驾距离"的方向不可脱离模型口径引用**。→ 时机与预置两条对照已在有腿模型里重跑，修正数见 §13.16-a / §13.16-c。

### 13.13 M5 批量撮合实测：配对算法确实更好，但收益要靠决策窗口承载，而且是权衡不是免费午餐

实验台加了两样东西：`Config.matchStrategy`（`SEQUENTIAL_GREEDY` / `HUNGARIAN`）与 `Config.matchWindowTicks`（攒多久再一次性配对，1 = 现状"来一单派一单"）。匈牙利解**先对过穷举**：60 个随机矩阵（1–4 行 × 1–6 列）逐一与"所有注入映射取最小"比较，总代价必须相等，否则"最优臂"是假的（`hungarianMatchesBruteForceOptimum`）。代价矩阵用生产同一份 `RulePolicy` 的总分，不可行对用有限大数 `1e12` 挡掉而不是 ∞。产物：`reports/scenario-bench/batch-matching-m-tier.md`（三段表）。

同时补了一个口径修正：**完成时长改为端到端（含等派时间）**，否则"攒窗口再配对"看起来白拿。新增指标 `pickup_wait_mean_s`，指标表从 11 行变 12 行。

| 对照 | 完成率 | 端到端完成时长 | 等派时长 | 总里程 | 空驶率 | regret |
| --- | --- | --- | --- | --- | --- | --- |
| ① 无窗口：匈牙利 − 贪心 | +0.0007 分不出 | −2.55 s 分不出 | 0 对 0 | −1240 m 分不出 | −0.0038 分不出 | −0.0086 分不出 |
| ② 固定 8 tick 窗口：匈牙利 − 贪心（**纯配对效应**） | +0.0044 分不出 | **−9.41 s** [−15.2, −3.6] | **−1.81 s** [−2.5, −1.1] | **−3203 m** [−5662, −745] | **−0.0185** [−0.029, −0.008] | **−0.0340** [−0.054, −0.014] |
| ③ 窗口+匈牙利 − 现状即时贪心（**总效应**） | **−0.0071** [−0.0138, −0.0003] | **+41.65 s** [+33.1, +50.2] | **+51.21 s** [+49.3, +53.1] | **−6108 m** [−10682, −1534] | **−0.0235** [−0.043, −0.004] | **−0.0453** [−0.084, −0.006] |

**四条结论**：
1. **①全部分不出来，而且是结构性的**：M 档 56 单/小时 = 每 tick（15 s）平均到货 **0.23 单**，同一时刻根本没有"一批单"可配 —— 没有窗口时，换更好的配对算法无从发挥。**以后谁拿"我们用了匈牙利匹配"当卖点，先问决策窗口在哪。**
2. **②固定窗口下配对确实更好，方向全部有利、5/9 项可分**，而且**端到端时长反而更短**（−9.4 s）、等派更短（−1.8 s）：贪心把最优车浪费在先到那单上，后面的单既等得更久又绕更远。完成率 +0.44pp 的 CI 是 [−0.05pp, +0.93pp]，差一点点跨 0 → 只能说"未见变差"，不能说"更好"。
3. **③加上窗口就有代价**：等派 +51.2 s、端到端 +41.6 s、完成率 −0.71pp（三项均可分且方向不利），换来里程 −4.4%、空驶 −2.35pp、regret −4.53pp。**这是权衡，不是收益**；能不能开取决于业务口径是"客户等待"还是"里程/能耗成本"，这个决定不归算法（见下）。
4. regret 头寸被吃掉的比例：0.2888 → 0.2434 = **16%**（②的纯配对部分是 12%）。§13.10 说"贪心留了 29% 里程在桌上"，这里量到了其中可回收的一部分，也量到"全部回收"要付的等待代价。

**本组没测、别外推**：不含 MAPF/路口冲突（§5 假设第 5 条）；窗口取 8 tick（2 分钟）是**一次抽样**，不同窗口的权衡曲线还没扫；L 档密度下收益/代价都会变形（§9 禁止跨档引用）。

**需要本人定的口径**（决定 M5 这条是"接"还是"记"）：派单 SLA 优先（现状即时）还是里程/能耗优先（开 2 分钟窗口）？没有这个决定，`DispatchVehicleAssignServiceImpl` 的按单派单不动，本节数字作为决策材料留在 §13.13。

### 13.14 M6 落地：需求有了时间结构之后，"错峰返充"从"无收益"翻成 +7.4pp

先补 §5 缺的那一块：`Config.arrival`（`ArrivalProfile`），默认 `FLAT` 保持上面所有已归档数字不变；`PEAK_SECOND_HOUR` 把第 2 个小时的到达强度乘 2，于是同一次仿真里同时有平段和峰段可比。同时补上空间维度 `Demand.ZJF_STATIONS`（站点坐标由库内 ACTIVE 的 PICKUP/DROPOFF 实测经纬度平移而来），M6 的站点×小时画像才有对象。

**① 站点×小时需求画像**（`reports/scenario-bench/demand-m-tier.md`，skew=0.9 的热区假设）：

| 取货站 | 小时段 | 均值 | P50 | P90 |
| --- | --- | --- | --- | --- |
| ZJF-PICK-01 | 0-1 | 34.17 | 34.00 | 39.00 |
| ZJF-PICK-01 | 1-2 | 37.00 | 35.00 | 51.00 |
| ZJF-PICK-02 | 0-1 | 21.42 | 20.00 | 28.00 |
| ZJF-PICK-02 | 1-2 | 19.25 | 19.00 | 24.00 |

两站合计 71.2 + 40.7 = **111.9 单/次运行**，与名义到达 56×2=112 对得上（一致性由测试断言，误差 <2%）。**必须写清的限定**：这张表跑的是 `FLAT` 到达，所以**小时之间的差是抽样噪声，不是峰谷**；只有 P90 与 P50 的差（如 51 vs 35）能当"波动幅度"用。要拿时段做容量决策，得先切 `PEAK_SECOND_HOUR`。

**② 热区预置**（`reports/scenario-bench/repositioning-m-tier.md`；空闲超 10 分钟的车挪到"在线计数最多"的取货站，SOC 地板 45%，站点场景 skew=0.9）：

| 指标 | 预置 − 不预置 | 95% CI | 判定 |
| --- | --- | --- | --- |
| 平均接驾距离 | **−164.1 m** | −202.7 .. −125.4 | 可分，预置更短 |
| 端到端完成时长 | **−32.6 s** | −42.0 .. −23.2 | 可分，预置更快 |
| 挪车次数 | +11.2 次/次运行 | +8.9 .. +13.5 | 可分（策略确实在干活） |
| 补能被挡 | **+37.5 车·分钟** | +23.0 .. +52.0 | 可分，**预置臂更差** |
| regret | **+6.9pp** | +1.8 .. +12.0 | 可分，**预置臂更差** |
| 完成率 | +0.41pp | −0.76 .. +1.58 | 分不出来 |
| 总里程 | +1882 m | −1026 .. +4790 | 分不出来 |

读法：**预置对它瞄准的指标有效**（接驾距离、端到端时长），**但代价真实**：车队被集中到一个点，跨站的单更远（regret 升）、且集中地离桩更远导致抢桩更凶（排队 +37.5 车·分钟）。所以 M6 第 2 条不能只报"接驾距离降 164 m"，要么与桩位一起调，要么给预置加"分散度上限"。

**③ 把 §13.12 的"错峰无收益"重测一遍 —— 结论翻过来了**（`charge-timing-m-tier.md` 第四段表；同一有峰段场景里 `DEFER_IN_PEAK` vs `OPPORTUNISTIC`）：

| 指标 | 高峰推迟 − 现状 | 95% CI | 判定 |
| --- | --- | --- | --- |
| 完成率 | **+7.38pp** | +6.36 .. +8.41 | 可分，推迟臂高 |
| 完成单数 | +12.75 | +11.28 .. +14.22 | 可分 |
| 充电次数 | **−37.25** | −40.85 .. −33.65 | 可分（峰段补能从 61 次压到 24 次） |
| 补能被挡 | **−14.06 车·分钟** | −16.48 .. −11.64 | 可分，排队更少 |
| 平均接驾距离 | −70.1 m | −93.1 .. −47.1 | 可分 |
| 端到端完成时长 | −16.2 s | −20.1 .. −12.2 | 可分 |
| 空驶率 / regret | −2.64pp / −6.17pp | 均不跨 0 | 可分 |
| 总里程 | +8258 m | +6384 .. +10132 | 可分，**但推迟臂多成了 12.8 单，绝对里程不可当"更费"读**（§13.12 判语纪律第 2 条） |

**机制**：`DEFER_IN_PEAK` 只压"顺势补能"，必充档不受影响；峰段里少占 37 次桩位 = 多留一批可派车辆，于是完成率与接驾同时改善。**这不是电价套利，是"高峰期别把车用去充电"** —— 也正是生产 `shouldDeferReturnToCharge` 的语义（pressure 高就不让返充，SOC 低于安全地板仍强制返充）。

**所以对 §13.12 的更正是**：那条"错峰无收益"的**前提**（齐次到达里没有高峰）不成立时结论就翻转 —— §13.12 的写法本身没错（它明确限定在齐次场景），但**不能继续拿它当"错峰不值得做"的证据**。M4 第一条因此**重新升级为该接**：`EnergyForecastServiceImpl.shouldDeferReturnToCharge` 接进真实链路，且这次有可引用的数字（+7.4pp [6.4, 8.4] 完成率、桩位排队 −14.1 车·分钟）。

**接线已落地（同日）**：`DispatchAutomationRuleServiceImpl` 在 `SOC_BELOW`+`CREATE_CHARGE_TASK` 命中后先问 `shouldDeferReturnToCharge`，推迟时**不记异常、不建补能任务**并打一条 INFO（带 vehicleId/SOC/规则名/压力值）；**换电支路明确不推迟**（`AUTO_SWAP_REQUIRED` 照旧），因为换电不排队。测试：`evaluateFleetEnergyRulesShouldDeferChargeDuringPeak`、`evaluateFleetEnergyRulesShouldStillSwapWhenPeakDefersCharge` + 原有 3 条不变（`mvn -pl fsd-bootstrap -am test` 全绿 275/87/9）。

**接线之后的真实生效条件还没满足**：`t_energy_forecast` 本地为 0 行、`scripts/ml/refresh_energy_forecast.py` 需人工跑 —— 也就是 M4 第 3 条（调度化 + 把静默回退变成可观测事件）不做，这段接线在真实环境里**永远是 `shouldDefer=false`**（无数据即不推迟，安全侧不动）。这就是它仍列在 M4 而未算完成的原因。

> **后续（同日 §13.15）**：可观测那一半已落地，且查出"无数据"其实在掩护一个更糟的状态 —— 一旦有数据，绝对阈值 2.0 对现有剖面**恒成立**，"永远 false"会直接翻成"整天推迟"。现已加相对判据 `min-peak-pressure-ratio=1.5` 挡住这条，剩下的缺口只有"日作业未被调度"本身。

> **读本节数字的第二条限定（§13.16）**：本节的对照同样产自没有"开去充电"这条腿的模型。这条腿量出来是 **每次补能 745.8 m 空驶**、并把完成率整体抬高约 3pp；时机类对照的效应在补上腿之后**变小**（+0.4033 → +0.3750）。所以这里的 +7.38pp 是"两臂同漏一条腿"的相对结论，**方向可信、幅度偏乐观**。

**其它仍未做**：真实园区的高峰曲线与电价（skew/×2 都是情景假设）；L 档同口径复测；`ArrivalProfile` 只支持单峰矩形块，不是实测时段分布。

### 13.15 M4 第 3 条（可观测那一半）落地，并且它改写了第 2 条的结论

**做了什么**：`EnergyForecastService` 新增 `parkForecastStatus()`，把原先"一个 0 含义四种"的读取口
拆成五档 —— `DISABLED / NO_ROWS / NOT_THIS_HOUR / STALE / FRESH`，每档带 `explain()` 一行根因
（含最新 `generated_at`、行数、超期窗口）。`parkPressure()` 改为**委托**给同一个解析器，
两条路径不可能再各算一套口径。落地面在 `EnergyForecastMetrics`（fsd-dispatch 已有
`DispatchOutboxMetrics` 先例，故不引入新依赖）：

- `dispatchflow.energy_forecast.availability{park,state}` 计数器 —— 每次解析都记，包括 `FRESH`（否则算不出退化占比）。
- WARN 日志按 (park, state) 限速 2 分钟；限速只作用于日志，计数器不受影响（否则 `rate()` 告警失真）。
- `dispatchflow.energy_forecast.flat_profile{park}` —— 见下，独立一档。

**新查出的事实：这条比"没调度"更要紧。** 顺手要标定 `pressureThreshold`（默认 2.0，§8 里写着"与实测峰值 159.85 差两个数量级"）时，把 `reports/energy_features.csv` + `energy_forecast_result.sql` 实测了一遍（`scripts/analysis/forecast-flatness.js`）：

| 实测项 | 值 |
| --- | --- |
| 特征序列 | 529 站·小时，单站点（归属塌缩，与 §13.14 的"仅 3/6 在用"一致） |
| 小时到达均值 / CV | **150.1 / 0.06** |
| 观测小时剖面峰谷比 | **1.08**（峰 154.3 @ h07 vs 谷 143.1 @ h01），剖面自身 CV 0.016 |
| 模型 `pressure_p95` 范围 | 152.7 ~ 159.85，**极差 7.15 = 基线的 4.6%** |
| 阈值 ≤152.7 时 | **24/24 小时恒触发** |
| 阈值 >159.85 时 | 0/24 恒不触发 |

结论有两层。**第一层**：`pressureThreshold` **无法在这份数据上标定** —— 绝对阈值只有"恒开/恒关"两种结局，中间那 7.15 是噪声排序不是高峰。这条不是没做，是数据不支持（没有真车 ⇒ 没有带昼夜起伏的到站序列）。

**第二层，也是最要紧的**：把这两半合起来看，M4 第 2、3 条之间有地雷 —— **§13.14 的接线目前是空操作，全靠"表里没有行"撑着**；一旦日作业被调度起来（第 3 条的另一半），绝对阈值 2.0 立刻对真实剖面恒成立，错峰返充就从"无操作"翻成**整天推迟**，唯一兜底只剩 SOC 临界阈值（5+10=15）。所以本轮不是"先调度后标定"，而是先补相对判据：

> 高峰 = `pressure ≥ pressureThreshold` **且** `pressure ≥ 当日逐小时园区压力中位 × min-peak-pressure-ratio(默认 1.5)`；`≤0` 即关闭该判据退回旧行为（回滚开关）。中位为 0 而当前小时非 0 算作最尖锐的高峰（`+∞`），不算"没有高峰"。

剖面太平因此退化成"不推迟"（安全侧），而不是"整天不返充"。**§13.14③ 的 bench 数字不被这条推翻**：那里的峰是 `ArrivalProfile` 的 2.0 倍矩形块，prominence 2.0 > 1.5 判据成立。

**测试**：`EnergyForecastServiceImplTest` 13 → **25**（五档各一条、超期须优先于缺小时、`parkPressure` 与 `parkForecastStatus` 同口径、SOC 安全地板不得记成预测退化、中位为 0 的尖峰、开关可关、以及 `flatProfileMustNotDeferEvenThoughAbsoluteThresholdPasses` 直接复现上面那个地雷），`EnergyForecastMetricsTest` **6** 条。全量门 `mvn -pl fsd-bootstrap -am test` **BUILD SUCCESS**。

**同一族问题的第二例（已顺手修掉）**：门运行日志里 `DispatchScaleLoadTest` 持续报 `T_DISPATCH_DECISION_SNAPSHOT not found`，测试却是绿的 —— 因为 H2 夹具 `spring.flyway.enabled:false`、表由 `IntegrationTestSchema` 手写，而那份 DDL 停在 V53，快照写失败按 §7.3 的设计只降级成一条 WARN。也就是说 **§7.3 的"决策可证明性"在两个集成测试与压测里从未真正跑过**，降级本身也没有指标面。已修：夹具补上 `t_dispatch_decision_snapshot`（V54+V55 全列）、`DispatchDecisionSnapshotServiceImpl` 加 `dispatchflow.decision_snapshot.write{result=ok|failed}` 计数器、`DispatchFlowIntegrationTest` 增加端到端断言（一次派单留一行、赢家车号、`score_gap` 与 `policy_id` 必须落库）。修完复跑门：**BUILD SUCCESS，fsd-dispatch 294 / 87 + 9 + 8，且全日志里 `T_DISPATCH_DECISION_SNAPSHOT not found` 出现 0 次**（修前每轮压测都刷）。**注**：这暴露的是一条通用纪律 —— 夹具手写的 DDL 与迁移目录会漂移，凡"写失败只降级为日志"的路径都要配计数器，否则它同时躲掉了故障和发现故障的机会。

**第三例，也是最要紧的一条：M4 补能侧的"选桩"根本不在线上链路里。** 顺着 §8 写的"选桩粒度从园区降到桩（`ChargingSessionServiceImpl.java:164-224`）"去读代码，逐跳实测：

| 环节 | 事实 | 检索依据 |
| --- | --- | --- |
| `recommendChargingPile(vehicleId)`（那条 line reference 指向的方法，带园区级负载因子 `/10`） | **全仓零调用方**（除接口声明本身），是死代码 | `grep -rn recommendChargingPile --include=*.java` 只命中 `ChargingSessionService.java:28` |
| 规则动作 `CREATE_CHARGE_TASK` | **不建任何任务**：只 `recordVehicleException(AUTO_CHARGE_REQUIRED)` 然后 `return true` | `DispatchAutomationRuleServiceImpl.java:114-128` |
| 异常码 `AUTO_CHARGE_REQUIRED` / `AUTO_SWAP_REQUIRED` | **写了没人读**（后端、前端、SQL 全无消费点） | 全仓检索只有写入处那一行 |
| 桩位占用 `reserveChargingSlot` / `markCharging`（真正会挑 `sort_order` 最小空闲位并锁行的那条路） | **只被仿真 `ParkPilotSimulationServiceImpl` 调用**，线上一行都不走 | 调用点仅 `ParkPilotSimulationServiceImpl.java:1234 / 1327` |
| 换电侧 `RealFleetSwapCoordinator` | 存在，但它是**后视镜**：车辆上报 `TO_SWAP/SWAPPING` 之后才补记一条 session，柜位取"该园区第一个 ACTIVE 柜"，不是决策 | `RealFleetSwapCoordinator.java:44-66, 97-104` |

结论：**真实链路里"低电车去哪儿充电"这个决策当前不存在。** §8 里"选桩粒度从园区降到桩"这条的立论点是错的（它指的是一段没人调的代码），M4 的第一优先级要按这张表重排：要么承认"没有真车 ⇒ 补能落地指令无执行方"而把选桩策略留在实验台里做（推荐，见 §8/M4 改写），要么先补"补能任务→车端指令"的整条下发链路（那是新建能力，不是"让已有能力生效"）。**本轮只改文档与判据，不动这条链路**：在没有真车、也没有指令下发通道的前提下补一个假的建任务函数，只会多一处看着能用其实不能用的代码。

顺带一条同源观察（不改）：`listChargingSlotCodes` 里对每个桩做 `parkingSlotMapper.selectById`，是 Phase 5.5 声称已消除的 N+1 写法在另一处的复现；它只在仿真路径上被调用，所以不影响线上时延。

**仍未做（这一条只算完成一半）**：日作业调度本身（需要 SSH 进生产 + 本机 `.venv-ml`，注册计划任务属部署动作，按"止于部署前"交回本人）；`ArrivalProfile` 的矩形块换成实测时段分布；电价。运行手册已同步到 `scripts/ml/README.md`（"运行状态怎么判断"一节）。

### 13.16 M4 选桩：先把"开去充电"这条腿补进模型，再量它让之前的结论偏了多少

因为 §13.15 查出线上根本没有选桩代码可改，M4 这条转向实验台。做之前先量了设施事实（本地库 `t_station`/`t_charging_pile`/`t_parking_slot`）：

| 实测 | 值 |
| --- | --- |
| CHARGING_STATION 站点 | 5 个，其中 **只有 ZJF-CHG-01 是 ACTIVE**（511/512/515/516 全 INACTIVE） |
| 在用桩 | 6 根（CP1..CP6），**全部挂在 ZJF-CHG-01 上** |
| 六个车位 P1..P6 的坐标 | **逐字相同**（668.4 / 624.5 px，即同一个点） |
| 演示库当前桩位状态 | 6 根全 OCCUPIED、FREE 0 根（另有 2 根 deleted=1 在 park 2） |

⇒ "选桩粒度从园区降到桩"在现役设施下是个**空命题**：六个候选桩在空间上是同一个位置，任何"选近的"策略都不产生差别。真正从没建模的是**开去充电这一腿**（假设清单里那条"回桩位移不计"）。所以本轮补的是它。

**实验台改动**（`ScenarioBench`）：新增 `ChargePoint(code,x,y,piles,measured)` + `PileChoice{NEAREST_FREE,NEAREST_INCL_QUEUE,LEAST_LOADED}` + `withChargeLayout(...)`；桩位池从"一排无位置的槽"变成"若干带坐标的点，各带自己的槽"，补能时按路网算单程、计空驶里程与耗电、车落在桩点上。`chargeLayout` 为空即退回历史 teleport 语义；新增两条指标 `charge_travel_m_session`、`pile_session_spread`（汇总表 14→16 行、配对表 11→13 行）。`validate()` 强制"布局各点桩数合计 = `chargeSlots`"，否则比的就不是策略而是多装了桩。

**四张表的判语**（M 档 · 站点场景 skew=0.9 · 12 次重复 · 配对 CI，全文见 `reports/scenario-bench/pile-selection-m-tier.md`）：

| # | 对照 | 结果 | 判语 |
| --- | --- | --- | --- |
| ① | 现役布局（6 桩同点，要开过去）vs 历史 teleport | 完成率 **−2.92pp [−3.98,−1.86]**、补能空驶 **+745.8 m/次**、总里程 +18.7 km、空驶率 **+5.44pp**、接驾距离 **−127.6 m**（可分，方向相反） | 这条腿不是小项：漏掉它，完成率被系统性抬了约 3 个百分点；而车充完电停在"居中的主站"反而让下一单接驾更近 —— 两个效应方向相反，不能只挑一个说 |
| ② | 同样 6 根桩摊到 5 个真实站址（2/1/1/1/1） | 完成率 **+0.50pp [−0.17,+1.17] 分不出来**；接驾 −42.0 m 可分；补能空驶、排队车·分钟、总里程均**分不出来**；`pile_session_spread` 0→10.25（构造性） | **摊开桩位不是运力解药**。§1.8 那 4 个站点的价值不能按"缓解抢桩"来论证，实测它没缓解 |
| ③ | 最近桩 vs 最近+排队 | **13 项指标逐字相同（差值全为 0）** | 机制清楚：顺势补那一支按定义只在"现在就有位"的点里选，排队项恒为 0；必充那一支在 12 次重复里排序从未被改变。⇒ 6 桩/5 点/这个需求密度下，**排队感知的选址不值得实现** |
| ③b | 最闲桩（分散优先）vs 最近桩 | 完成率分不出来；总里程 **+4.1 km 可分（更差）**、补能空驶 **+74.7 m/次 可分（更差）**；而分散度 `pile_session_spread` 反而**没**改善（9.42 vs 10.25，分不出来） | 为分散而分散：多花里程，还没把车摊平。这条直接否掉"负载均衡因子"那类想当然的做法 |
| ④ | 时机对照（顺势补 vs 只必充）在两种模型里各跑一遍 | 完成率差 **无这条腿 +0.4033 [0.3859,0.4208]** vs **有这条腿 +0.3750 [0.3629,0.3871]** | 漏掉这一腿会把"补能值多少"**高估 2.84pp，即效应量的约 7%**；两臂 CI 不重叠 ⇒ 偏差不是轮间噪声。方向也解释得通：漏掉的里程按 `次数 × 746 m` 计，补得越多的臂被抬得越高（本例 +45.3 次） |

**因此对 §13.12 / §13.14 的既有数字要这样读**：它们全部产自 teleport 模型。方向不变（时机、撮合、预置都是两臂同模型的配对对照），但**凡是与"补能次数"相关的绝对量都偏乐观**，其中"接驾距离"一项在本场景里方向直接翻号（④a +80.0 m vs ④b −52.7 m）—— 引用 §13.12 那条"接驾距离 −70 m"之前必须带上这条限定。要在有腿的模型里重跑全部时机/预置对照，是 M4 剩余项，不是本轮结论。

**本轮自抓的实现 bug（值得记，因为它差点被当成"结论变了"）**：给 `beginChargingAt` 无条件赋值 `x=point.x()` 时，无位置布局那个合成点是 `(0,0)` ⇒ 每次充电后车被瞬移到园区角上，默认臂完成率从 0.93 掉到 0.89、里程从 138.2 km 涨到 172.6 km，而**测试全绿**。是我核对 `m-tier-bench.md` 与 §13.10 记录不一致才发现的。已修（`ChargingPool.positioned()` 为假时不挪车），并在测试里加了**基线守门断言**：默认臂必须复现完成率 0.93±0.005、总里程 138.2 km±2 km，任何改动默认口径的写法都会被这条挡住。

#### 13.16-a 在有腿模型里重跑 §13.14③：错峰返充的收益不是变小，是变大

同一对臂（`DEFER_IN_PEAK` vs `OPPORTUNISTIC`、同种子、M 档齐次 OD + 第 2 小时 ×2 峰段），只是补上"开去充电"这条腿（表在 `reports/scenario-bench/charge-timing-m-tier.md` 第 ⑤ 张）：

| 指标 | teleport 模型（§13.14③ 原数） | 有腿模型（修正） |
| --- | --- | --- |
| 完成率 | +7.38pp [+6.35, +8.42] | **+10.65pp [+9.38, +11.92]** |
| 完成单数 | +12.75 | **+18.42 [+16.65, +20.18]** |
| 充电次数 | −37.25 | −35.25 |
| 补能被挡（车·分钟） | −14.06 | **−8.98**（更小） |
| 平均接驾距离 | −70.1 m | −59.0 m |
| 空驶率 | −2.64pp | **−8.53pp** |
| regret | −6.17pp | −25.77pp |
| 总里程 | +8258 m（更费） | **−7011 m（更省，方向翻号）** |

机制解释得通：推迟返充少做的就是"开去充电站"这一段，teleport 模型里这段是 0，所以它**低估**了推迟的收益；补上腿之后每次被省掉的补能连带省掉约 747 m 空驶。反过来说，"抢桩排队"这一项的收益在修正后**变小**（−14.1 → −9.0 车·分钟）—— 腿占掉了车的一部分时间，排队不再是唯一瓶颈。**结论方向不变，`§13.14③` 的 +7.38pp 应读作保守值。**

#### 13.16-b 能耗敏感性把一条硬话摆到台面上：**单个未标定常数能推动完成率 14.6pp**

M4 最后一条 bench 项（能耗只做参数化 + 敏感性，§9 禁止称"预测模型"）。`busyDrainMetersPerPercent` 从 100 扫到 250 m/1%（`reports/scenario-bench/energy-sensitivity-m-tier.md`）：

| m/1% | 完成率 [95% CI] | LOW_SOC 次/运行 | NO_VEHICLE 次/运行 | 补能次数 | 补能排队车·分钟 |
| --- | --- | --- | --- | --- | --- |
| 100（最费电） | **0.6970** [0.6498, 0.7441] | 27.92 | 2.83 | 36.3 | 412.1 |
| 150（现行默认） | 0.7967 [0.7598, 0.8337] | 5.08 | 15.58 | 48.3 | 215.0 |
| 200 | 0.8230 [0.7852, 0.8608] | 1.17 | 16.08 | 59.4 | 165.7 |
| 250（最省） | **0.8433** [0.8037, 0.8829] | 0.33 | 14.92 | 64.8 | 144.3 |

**总跨度 +14.63pp**，而两端各自的 CI 宽度只有约 ±0.047。对照本项目到目前为止量出来的所有策略效应：错峰 **+7.4 ~ +10.7pp**、预置与撮合只在里程/秒级、M2E 换图是时延不是完成率 —— 也就是说：

> **任何一个小于 14.6pp 的完成率结论，都可以被"每 1% SOC 到底跑多少米"这一个未标定常数解释掉。** 仓库里没有真车真能耗数据（§0.2），所以这个常数只能取假设值；因此对外讲 M3/M4 的任何完成率数字时，必须同时给这条扰动带，否则就是把参数不确定性说成策略效应。

顺带两条结构观察（同表）：① 失败**归因**会随参数换类别 —— 100 m/1% 时主要是 `LOW_SOC`（27.9 次/运行，车根本不敢派），250 时主要是 `NO_VEHICLE`（14.9 次，运力真不够）。所以"LOW_SOC 落单数"不能当独立证据用，它同时是能耗参数的函数；② 能耗越省补能次数越多（36.3 → 64.8 次/运行），因为顺势补能的门槛更容易满足 —— §13.12 那条"补不补能值 +24.51pp"（站点场景 ④a 是 +40.33pp）的效应量里有相当一部分是这个机制在放大，不是运力本身。

**这条不推翻任何已落的代码判断**（可观测性、相对判据、接线本身都与它无关），但它改变**引用方式**：M3/M4 的完成率类结论今后一律要带"能耗参数 ±(100~250) 扰动带 14.6pp"这句限定，已写进 §8 的 M3/M4 闸门。

#### 13.16-c 预置对照（§13.14②）在有腿模型里的修正数：**收益变小、代价不变，这笔交易比原来记的更不划算**

同一对臂（开/关 `idleRepositionMinutes=10`）、同种子，补上"开去充电"这条腿之后（表在同一份 `repositioning-m-tier.md` 第二张）：

| 指标 | teleport 模型（§13.14② 原数） | 有腿模型（修正） |
| --- | --- | --- |
| 平均接驾距离 | −164 m | **−91.6 m [−122.9, −60.3]** |
| 端到端完成时长 | −32.6 s | **−18.6 s [−24.3, −12.9]** |
| 完成率 | （原表未列为可分） | +0.44pp **[−0.56, +1.43] 分不出来** |
| 抢桩代价（补能被挡） | +37.5 车·分钟 | **+25.2 车·分钟 [+10.5, +39.9]（可分，更差）** |
| 挪车次数 | —— | +7.67 次 |

即：**两项收益各缩水约四成，代价仍是同一个量级，完成率仍然分不出来。** 结合 §13.16 表 ②（把桩摊开也换不来完成率）与 ③b（为分散而分散更费里程），M6 预置这一条的正确读法是"它把车挪到热区，但热区的车同时挡在主站桩位附近"—— 所以它能不能做，取决于本人要不要为接驾距离买单，而不是"实测有收益"。§13.14② 原表保留作为 teleport 口径的记录，引用时以本表为准。

### 13.17 ETA 落后端：这一条比原文写的更糟 —— 逐段限速那段代码是死的，而且两端各有一个速度常数

原文写的是"`RouteMetricsCalculator.java:115` 的 15 km/h 换成路段限速"。读代码 + 查库之后，实际是**三件事**：

**① 唯一的生产调用点把两个关键入参传成了空值。** `RoadRouteValidateAdminService:170` 传的是
`compute(null, polyline, List.of(), null, null, null)` —— `parkId=null` 让路段索引直接返回空 Map，
`nodePath=List.of()` 让逐段限速那个分支一步都不进，`collectRiskPoints` 也因 parkId 为空提前返回。
于是**逐段限速的实现早就写好了，但从来没有人喂它数据**：ETA 恒等于"总长 ÷ 写死的 15 km/h"，风险点恒为空数组。
两个入参在同方法上下文中都拿得到（审计记录就在用 `request.getParkId()`、`segmentPath` 就在用 `nodePath`），是漏传。
`RoadRouteContractTest` 里那条 stub 恰好写成 `compute(isNull(), ...)`，把空值当契约固化了下来 —— 所以它一直是绿的；
**改成断言"parkId 与吸附节点必须传下去"之后，这条测试第一次真正守住了这个入口。**

**② "15 km/h" 这个常数本身是错的，而且有实测值可换。** 现役 124 条边的限值只有三档：10/15/20 = 16/79/29 条，
无缺失。按边长加权的调和平均（`Σlen / Σ(len/v)`，边长由两端节点经纬度算）= **13.19 km/h**；
按条数取算术平均 = 15.76 km/h。差在慢边虽少但每条更长，算术平均把它们稀释掉了 ——
所以 ETA 系统性偏快 **12.1%**（15→13.19），而原代码里那条"取平均限速"的路子若被激活会偏快 **19.5%**。
本轮把 ETA 改成真正逐段 `Σ(段长/段限速)`，未覆盖部分按实测 13.19 补，回退常数从"拍脑袋 15"换成这个有出处的数。

**③ 前端另有一个自己的速度常数，且与后端不是一个量级。** `src/maps/geoDistance.ts` 的
`formatDeliveryEta(meters, speedMps = 2.5)` —— 2.5 m/s = **9 km/h**，注释里只写着"~2.5 m/s ≈ 9 km/h along campus roads"，无出处。
**同一个物理量在两端各写了一个常数，差 46%**，所以界面上的"约 X 分钟"与接口返回的 ETA 永远对不上。
已统一到实测的 `MEASURED_NETWORK_SPEED_MPS = 3.66`（13.19 km/h），并在注释里点明：
如果产品要的是"比平均更保守的承诺"，应该显式加一个承诺余量参数，而不是把速度常数调慢了当余量用（那种余量无人知道它是余量，也不知道它该多大）—— **这个余量要不要加、加多少，留给本人定**。

**顺带查出的两处事实（避免以后误判）**：
- `t_road_segment.polyline_geojson` 在现役 seed 里 **124 条全为 NULL**，路段表也没有长度列 ⇒ 边长只能由节点坐标算。这条正是 §1.5"polyline 进权威 seed"未闭合的直接后果，本轮已把它从"锦上添花"升级为"ETA 精度的前置"。
- 现役 124 条边的 `direction` **全是 BIDIRECTIONAL**，`node_code` 也不跨园区重复 ⇒ §13.9.3 那 17 条单向边属于未上线的扩范围图。所以"按反向索引路段"在现役数据下不会出错，本轮**没有**为此改代码（只把 `collectRiskPoints` 的 `park_id` 用 `and(...or...)` 罩住两个分支 —— 原写法 `(park AND from) OR (to AND deleted)` 第二支无园区约束，属latent 缺陷，等 park 2 有路段就会真的串）。

**测试**：新增 `RouteMetricsCalculatorTest` **5** 条（逐段加权 ≠ 单常数、未覆盖段按 13.19 补、部分覆盖只计已测段、parkId 缺失时不打库、风险点查询的园区过滤必须罩住 OR 两支）；`RoadRouteContractTest` 的 2 处 stub 从 `isNull()` 改成 `eq(1L)` 并新增"入参必须传下去"的 verify。全量门 `mvn -pl fsd-bootstrap -am test` **BUILD SUCCESS**：fsd-dispatch **301** / 87 / 9 / 8；前端 `npm run typecheck` 通过（**未跑浏览器实测**：该改动只影响数字换算，但界面呈现我没有在浏览器里看过，如实标注）。

**剩余半条（下一轮或有指示时做）**：要让 ETA 精确到"真的经过哪些边"，得让 `RoadRouteResult` 把规划器的 `nodePath` 带出来 —— 现在 `RoadRouteService.planDrivingRoute` 只回 polyline，节点编码在 `ParkRoutePlannerServiceImpl.buildRouteFromNodePath` 那一步被压成坐标（`segmentPath` 字段存在但全仓无人填充，是空列表）。在这之前，生产上能测到的 ETA 改善只有"回退常数由 15 改成实测 13.19"这一项（−12.1% 时间高估），逐段加权目前是"接口通了、数据没到齐"的状态。

### 13.18 M4 充电曲线分段：又是"配置有、消费者只有仿真器"，所以做成敏感性而不是拟合

先按 §13.15 那条纪律查了调用方：`chargeRatePerTick`（`FleetEnergyProperties.java:36`，值 4）在全仓只有**两个读取点**，
且都在被冻结的 `ParkPilotSimulationServiceImpl`（`:448`、`:949`）里 —— **线上补能链路一次都不读它**
（真实链路根本没有"边充电边涨 SOC"的模拟，`t_charging_session` 的 `end_soc` 来自上报）。
所以"把充电曲线分段"在生产侧没有可改的落点：改了只影响仿真器，而仿真器是 §9 明令不动本体的。
这一条因此和选桩那条一样转到实验台（`Config.chargeCurve`），并且**只做敏感性、不做拟合**——没有真车真充电记录，
拐点与倍率都是情景设定，随报告声明。

**参数**：`ChargeCurve(kneeSoc, taperDivisor)`，SOC 高于拐点的部分按 `1/倍率` 的速率充；
默认 `LINEAR`（拐点 = +∞）逐字等于历史折算式 `1800 × (90−SOC)/70`，测试里对全部 17 项指标断言"不传曲线的臂 == 显式 LINEAR 臂"，
外加三条算术/定义域守卫（`taperDivisor < 1` 直接抛异常，慢充不可能比快充快）。

**扫描结果**（M 档 · 站点场景 · 现役单点 6 桩布局 · 12 次重复，`reports/scenario-bench/charge-curve-m-tier.md`）：

| 情景 | 完成率 | 补能被挡（车·分钟） | 补能次数 | 总里程 |
| --- | --- | --- | --- | --- |
| `LINEAR`（现状） | 0.7967 | 215.0 | 48.3 | 226.3 km |
| 拐点 80%、慢 2× | **−3.26pp [−4.64, −1.89]** | **+99.1（+46%）** | −15.9 | −16.0 km |
| 拐点 80%、慢到 1/3 | **−7.17pp [−8.37, −5.98]** | **+172.0（+80%）** | −23.0 | −30.9 km |
| 两个情景之间 | −3.91pp [−5.19, −2.63] | +72.8 | −7.1 | −14.9 km |

机制：尾部变慢 ⇒ 每次占桩更久 ⇒ 6 根桩更挤 ⇒ 低电车等着而不是在跑 ⇒ 完成率掉、"想补补不上"的车·分钟涨。
**注意别把总里程下降读成"更省"**：这一臂少成了 15–23 单，绝对里程随完成量一起缩（判语纪律第 2 条，§13.12）。

**把两条敏感性合起来看，这是本项目目前最重要的一条口径结论**：
`busyDrainMetersPerPercent` 单独能推动完成率 **14.63pp**（§13.16-b），`chargeCurve` 从"线性"到"80% 后慢 3 倍"能推动 **7.17pp**（本节），
两者都是"取一个说得过去的假设值"级别的自由度 ⇒ **合计约 22pp 的不确定带**，而本项目所有策略效应里最大的一条是错峰 +10.65pp。
所以任何对外引用的完成率差，必须先说明它落在这条带里的哪个位置；否则"策略有效"和"参数换了个假设"无法区分。
**要收窄它，只有两条路**：拿到真车能耗/充电曲线数据（本项目没有），或者把结论限定为"同一参数集下的相对排序"（本项目实际做到的）。

### 13.19 M5：MAPF 的单位不一致 —— 预约窗口只覆盖真实占位时间的 37%–62%

§7.2 那条写的是"`MapfReservationService` 时间片用 `vehicleSpeedPxPerSecond=8.0` 除**米**"。查准之后是这样：

| 环节 | 实测事实 |
| --- | --- |
| 分子 | `ParkRoadGraph.NodeView.distanceTo(other)`：**两端有 GPS 时是 haversine 米，否则是示意 px** —— 同一个方法两种单位 |
| 现役图走哪一支 | ACTIVE 节点 **55/55 全有 GPS**（实测）⇒ 在线图上分子确实是米；38 条 px 缺失的边两端点都是 DISABLED，已被建图的 `status=ACTIVE` 过滤挡掉，所以 A\* 今天没被污染 |
| 分母 | `MapfProperties.vehicleSpeedPxPerSecond = 8.0`，且 `MapfRoutePlannerService` 会**优先取 `fsd.park.vehicle-speed-px-per-second`（默认 8）覆盖它** —— 那是示意坐标上的**动画/仿真速度**（前端 `ParkList.vue` 表单、`ParkPilotSimulationServiceImpl.moveVehicle` 在用 px/s） |
| 净效果 | 米 ÷ (px/s) 单位不同类，且本 seed 的 px→米**各向异性**：横向 1.2263、纵向 0.7390 m/px（86 条 ACTIVE 边实测比值 min 0.741 / 均值 0.999 / max 1.226，σ 0.231）。均值≈1 所以平时看不出，单边误差 ±23% |
| 预约实际覆盖多少 | 旧桶数 `px/8/0.5`，新桶数 `米/3.66/0.5`（3.66 m/s = §13.17 实测的 13.19 km/h）⇒ 覆盖比 = 0.4575 ÷ (米/px)，落在 **37.3%–61.8%，均值 45.8%**。横向 120 px 的边旧算法给 30 桶、正确 80 桶；纵向 100 px 给 25 桶、正确 40 桶 |

即 MAPF 的每条边预约**提前约一半时间失效**：车还在边上跑， reservation 已过期，后来的车查不到冲突 —— MAPF 看着在跑，实际几乎不挡车。这不是参数调优，是单位错。

**改了什么**
1. `MapfProperties.vehicleSpeedPxPerSecond` → **`vehicleSpeedMetersPerSecond = 3.66`**（与 §13.17 的 ETA 常数同一实测出处），并**去掉 MAPF 对 `fsd.park.vehicle-speed-px-per-second` 的借用** —— 显示/仿真速度不该决定物理预约窗口；`ParkPilotProperties` 因此不再是 `MapfRoutePlannerService` 的依赖（`MapfZonePartitioner` 仍用它的 width/height，那是它该用的）。
2. `ParkRoadGraph` 新增 **`distanceMetersTo()`：恒为米**，px 分支按两轴分别乘实测系数；`edgeCost`（A\* 边权）改用它。现役数据下**不改变任何路径选择**（`ParkRoutePlannerAStarTest` 15 条、`ParkRoadGraphCacheTest` 6 条零改动通过），它防的是"以后出现没回填经纬度的 ACTIVE 节点"。旧 `distanceTo` 留给"只需相对比较"的最近节点查找，javadoc 写明单位取决于数据。
3. 桶数计算显式 `edgeMeters / mps / bucketSeconds`，`bucketMs` 加下限保护。

**行为变化是"MAPF 开始真的挡车"**：占位时长 ×2.2 ⇒ 冲突与重规划次数上升、`maxReplanAttempts=4` 更常跑满 —— 这正是 M5「同硬件重压测双口径」要测的东西。**本轮没有测时延影响**（`MapfLoadTest` 只测吞吐），修复前后的时延数字不可混引。→ **时延影响已在 §13.21 测完：P50 没有变化，冲突率 24.4% 第一次可读。**

**另半边没动，只加了可观测**：重规划用尽后 `planAndReserve` **仍返回一条未预约路线、派单照走**（`reserved=false`）—— MAPF 从不拦单。该不该拦是 SLA 口径（等本人定）；但今天连"多少比例没预约成功"都看不见，故新增 `dispatchflow.mapf.reservation{result=reserved|conflict|disabled}`，三项之和即总调用数，可直接算冲突率。

**测试**：`MapfRoutePlannerServiceTest` 新增 1 条单位契约测试（横向 120 px 边的第二条边必须落在第 80 桶；同时断言旧口径 30/80=37.5% 以钉住 bug 量级），4 处构造点随之更新；MAPF/路由面 40 条全绿。

### 13.20 M5：L 档退化曲线 —— 完成率在 40 台前就饱和了，退化藏在补能与候选集里

**做了什么**：给实验台加了两个正交的轴（`ScenarioBenchTest.lTierDegradationCurveIsMeasuredNotExtrapolated`，12 次重复 × 6 配置 = 72 轮，`reports/scenario-bench/l-tier-degradation.md`），并新增一条指标 `candidates_per_decision`（每次派单尝试扫到几台空闲车，含 `NO_VEHICLE` 那一支）作为**决策成本的规模代理**。

**先说限定（这条比数字重要）**：范围仍是现役 1613×500 m、桩仍 6 根，只有车队与需求在动 —— 它**不是 §1.3 的 L 目标规模档**（≥1.9 km² / ≥10 桩 / ≥48 待命位 / ≥300 节点）。本表读作"运力边际收益 + 规模放大时的不变性"，**不得当 40 台的容量证明**（§1.3 档位不可混用、§9 禁止外推到 500 台）。

**A 轴（需求固定 56 单每小时，只加车）**：完成率 0.5269 [0.4924, 0.5614] → 0.9264 [0.9011, 0.9517] → **1.0000 [1.0000, 1.0000]**。边际收益 +39.95 pp → +7.36 pp（次段只有首段的 18%），**20→40 台这一档买到的不是单量而是空闲车**（40 台时每次派单平均扫到 24.0 台空闲车 ≈ 60% 车队闲着，完成率 CI 宽度 0 ⇒ 该指标在这个配置下已失去分辨力）。

**B 轴（需求随车数同比放大，密度不变）**：完成率 0.8620 → 0.9264 → 0.9537 —— **规模本身不吃完成率**。但同表两列超线性恶化：

| 指标 | 10 台/28 单 | 20 台/56 单 | 40 台/112 单 | 倍数 |
| --- | --- | --- | --- | --- |
| 补能被挡（车·分） | 10.9 | 209.6 | 1313.7 | 车 4×、阻塞 **120×** |
| 候选/次派单 | 2.7 | 5.0 | 13.1 | 车 4×、候选 **4.9×**；40 台是 20 台的 **2.6×** |
| 总里程（km） | 71.5 | 138.2 | 273.9 | 与需求量同比（不是退化） |
| 空驶率 | 0.41 | 0.35 | 0.30 | 规模越大越省空驶 |

**结论（可直接指导 §1.3 的 L 前置）**：L 档先撑不住的两处都在完成率看不见的位置 —— **桩侧排队**（现役 6 根桩在 40 台×112 单下阻塞 1313.7 车·分，≈ 21.9 车·小时/120 分钟窗口，即平均 11 台车常年堵在补能上，而 §1.3 要求的 ≥10 桩正是这个量级的约束）与**决策成本**（候选 ×2.6）。所以"≥10 桩"不是拍的，且**减候选是 L 档最直接的杠杆**。

**生产侧同形状，已核实到行**：`DispatchVehicleAssignServiceImpl:165` 的候选集就是**全量在线空闲车**（`listAssignableVehicles()`，没有距离/半径预筛、没有 top-N 截断），而每台候选要跑**两次路径规划**（`canCompleteTaskWithSoc` 的可达性 + `estimateRouteDistance`，:198-216）⇒ 候选 ×2.6 直接就是热路径 ×2.6。这条给 §1.4 那个 277 ms 的"必须按候选集重测"补上了机制解释，也给出了 L 档的具体做法：距离预筛 / 空间索引（现役 `t_road_node` 只有 55 个节点，围栏级网格分桶就够，不需要 PostGIS —— 与 §9 一致）。

**仪表发现（本轮又抓到一个 0 列）**：报告草稿里原有"等派"列，六行全为 `0.0 s`。核实主循环：窗口=1 时 backlog 每 tick 必冲、冲不动的单直接记 `NO_VEHICLE` **丢弃而不入队** ⇒ `pickup_wait_mean_s` 结构性恒为 0。它测的不是"没有等待"而是"**等待没有被建模**"，所以从本表删掉，并在报告里写明原因与适用条件（只有窗口 >1 的 batch 配置下才有值）。**缺单的代价记在完成率里，不在等派里** —— 以后引用本实验台的"体验"指标时别把等派当体验。

**没做**：没有跑时延（本表候选列只是规模代理）；没有改生产代码的候选过滤（那是 L 档落地时的独立决策，且属 §8 M5 之后）；没有把 40 台写进任何对外口径（§9）。

**测试**：实验台 14 条（新增 1 条，其中 5 条断言钉住上面每个方向：候选同比涨、A 轴 40 台饱和、边际收益递减、B 轴不退化、补能阻塞超线性）。断言放在报告落盘**之后**，方向不成立时先留下数字再红。

### 13.21 M5 同硬件重压测（MAPF 修复后）· 双口径：缓存贡献 +33.9%，而 MAPF 修复本身不吃时延

产物：`reports/latency/m5-two-calibers-post-mapf.md`（两臂各 8 轮 × 20 单、每轮前重置、分位数只对成功决策算、跨轮 t-CI）。同一台机器、同一套本地容器、同一个刷新后的 jar、现役 seed 权威图（55 ACTIVE 节点 / 86 ACTIVE 边）。

| 口径 | P50 | 95% CI（df=7） | P95 | 95% CI | 均值 |
| --- | --- | --- | --- | --- | --- |
| A 图缓存 ON（默认 60 s TTL） | **77.61 ms** | [72.20, 83.03] | 156.41 | [126.58, 186.25] | 82.54 |
| B 图缓存 OFF（`...GRAPH_CACHE_TTL_MS=0`） | **103.96 ms** | [96.41, 111.50] | 185.05 | [165.07, 205.03] | 110.21 |

1. **缓存贡献可测且显著**：P50 差 **+26.35 ms（+33.9%）**、均值差 +33.5%，两指标区间都分离；逐轮更硬 —— A 组最大 86.43 对 B 组最小 91.86，**8×8 完全分离无交错**。
2. **P95 分不出来**（A [126.58,186.25] vs B [165.07,205.03] 重叠，SD 35.7 ⇒ 8 轮不够）。要看 P95 得 ≥20 轮或交叉设计。
3. **表述纪律守住的那条**：本轮量的是 **JVM 进程内**图缓存（`ConcurrentHashMap`+TTL），**不是 Redis** —— 不许把 +33.9% 写成"Redis 贡献"。Redis 在派单热路径上承担暂停标志 / `FleetChargePolicy` 热更新 / 事件幂等，本轮没为它设臂。§8/M5 那句纪律的原意就是"没测的层不许蹭已测层的收益"。
4. **MAPF 单位统一对时延没有影响**：修复前 78.19 [72.52,83.87]（§13.11，5 轮）、本轮意外复测 77.78（陈旧 jar 单轮，见 §13.22）、修复后 77.61 [72.20,83.03] —— 三组互相落在对方区间内。机制：现役图只 55 节点，`maxReplanAttempts=4` 的重规划代价远小于"每台候选跑两次路径规划 ×20 台"。
5. **行为变化第一次可读**：`dispatchflow.mapf.reservation{result}` 在口径 A 累计 `reserved=267 / conflict=86` ⇒ **冲突率 24.4%**；`disabled` 计数为空（从未走关闭分支）。两臂冲突率之差**不可读**（各 JVM 从 0 计数、窗口不等长、含探测单）。这给"该不该因冲突拦单"这个待本人定口径的半条提供了实数：约 1/4 的选车调用会遇到占位冲突。
6. 两臂都仍有**单轮内顺序漂移**：A 前半 61.58 → 后半 104.88（1.70×）、B 87.51 → 133.49（1.53×）；两臂非交叉安排（A 先 B 后），所以"缓存贡献"的方向靠机制 + 8×8 全分离支撑，不靠随机化。
7. **测不了**：500 台/更大围栏（§9 禁止外推）、并发派单（基准是串行 20 单）、Redis 层贡献。

### 13.22 本轮推翻的两处仪表缺陷（都不是"数据变了"，是"仪器错了"）

**① `run-backend-local.sh` 起的是陈旧 jar —— 一整轮压测因此在旧代码上作废。**
`mvn -pl fsd-bootstrap spring-boot:run` 不带 `-am` ⇒ 同级模块从 `~/.m2` 的 SNAPSHOT jar 解析。实测该 jar 装于 20:49，而 `MapfReservationMetrics` / `EnergyForecastMetrics` 是之后才加进 `fsd-dispatch` 的 ⇒ 起出来的后端里根本没有这些类，`/actuator/metrics` 查 `dispatchflow.mapf.reservation` 直接 404，**而全套单元测试仍然绿**（测试走 reactor，不受影响）。第一整轮口径 A 就是这样测完 8 轮、P50 出来 77.61 ms 却一个计数器都读不到 —— 整轮作废、修脚本、重跑。
修法（已落地）：脚本按"任一模块 `src/` 比 jar 新"自动先 `mvn -DskipTests -pl fsd-bootstrap -am install` 刷新（`RUN_BACKEND_FRESH=0` 跳过），并打印是哪几个模块陈旧（实测准确点出 `fsd-dispatch fsd-admin-api`）；验收规则 = **打完一单后对应指标必须 200**。修后 `reserved/conflict` 两档、`decision_snapshot.write{ok}` 全部立刻可见。
顺带查实：`dispatchflow.energy_forecast.*` 在本机仍无指标 —— 与陈旧无关，是因为 `shouldDeferReturnToCharge` 只在"车辆 SOC 已低于返充阈值"或规则命中时才被调，本机窗口内还没跑到那一步（不是没接线：调用点在 `DispatchAutomationRuleServiceImpl:141` 与 `ParkPilotSimulationServiceImpl:788`，且 `FleetAutomationScheduler` 有 `@Scheduled`）。

**② t 分位数小表整段错位（两份：`ScenarioBench.T_975` 与 `bench-dispatch-latency.sh::tval`）。**
`studentT(n)` 按 n 查表，但表里 n≥6 的档位写的是 **t(df=n)** 而不是 t(df=n−1)：n=12 给 2.179（正确 2.201）、n=8 给 2.306（正确 2.365）。⇒ 所有 6–20 次重复的区间**系统性偏窄 1%–5%**，方向是反保守的（只会把"分不出来"说成"分得出来"）。更糟的是 `writesReportWithConfidenceIntervals` 里有一行 `assertEquals(2.179, studentT(12))` —— **测试把错的常数当契约钉住**，所以这个错位不会自己暴露。
已修：两份表都改成 df=n−1；表外的 n（21–24 / 26–29）退回不超过它的最大档（只偏宽不偏窄）；新增 `studentTUsesNMinusOneDegreesOfFreedom` 逐档对已知值 + 单调性守卫；原断言改指正确值并留注说明它曾钉着错值。
**对既有结论的影响**：CI 宽度 +1%–5%，本文件与全部实验台报告的判定方向不变 —— 所有"A 高于 B/低于 B"的分离幅度最小也是百分之几量级，远大于 5%；已记录的两处窄分离（预置的完成率分不出来、摊开桩位 +0.50pp 分不出来）本来就是"重叠"结论，加宽只会更稳。**但报告正文的 CI 数字以修正后重跑的版本为准**（门会自动重生成 `reports/scenario-bench/*.md`；本次重跑后已复核默认臂基线 0.93 [0.90,0.95]、138.2 km 未变）。

### 13.23 §7.2：`LOW_SOC` 一直在吞掉别的失败成因 —— 拆开之后标签才可读

**原来什么样**：`DispatchVehicleAssignServiceImpl` 把 SOC 阈值与**五个约束过滤器**（维保、线路要求车型、试点车队池、配送区、载重）挤在同一个 `filter` 链里，链子空了就统一报 `LOW_SOC`。⇒ 前端与管理端看到的"电量不足"里混着车型/池子/区域/载重不匹配；**§7.3 要的"派单失败原因分布"在这个标签下不可信**。§7.6 实测已经撞出两例：100 车规模压测里车号前缀不对报"电量低于可派车阈值"、`tasks/64` 因池子过滤失败也是同一句。

**改成什么**：SOC 单独一层（空 ⇒ 仍报 `LOW_SOC`）；约束层拆成一张**命名过滤器表** `orderConstraintFilters(order)`，**筛选与诊断共用同一张表**（顺序即诊断顺序：MAINTENANCE → VEHICLE_TYPE → FLEET_POOL → DELIVERY_ZONE → LOAD_CAPACITY），逐层记录存活数，第一个把候选清零的层写进消息 ⇒ 标签不骗人、定位不用加数据库列。约束清空时新增编码 `DispatchAssignFailReason.NO_MATCHING_VEHICLE`（`fail_reason VARCHAR(32)`，无需迁移），`DispatchFailExplainSupport` 补中文说法与三条可操作建议，前端 `constants/dispatchFail.ts` 补标签"车辆约束不满足（非电量）"与跳转链接（车辆列表 / 线路管理）。

**live 验证（不是只看单测）**：本机演示库把 20 台 IDLE 车的 `delivery_zone` 临时改成 `ONLY_NORTH`（订单侧默认 `GEO_DELIVERY`），下一单后新快照读到
`fail_reason=NO_MATCHING_VEHICLE, candidate_total=20, soc_eligible_count=7, remark="No idle vehicle satisfies the order constraints (SOC-passing candidates: 7; survivors per filter {MAINTENANCE=7, VEHICLE_TYPE=7, FLEET_POOL=7, DELIVERY_…}"`
—— 旧口径这一单会报 `LOW_SOC`。探针跑完按备份逐行还原，复核 `ONLY_NORTH` 残留 **0** 行、区域分布回到 `BOTH×20`。
顺带在真实进程里确认了两件事：① 候选集就是全量在线空闲车（`candidate_total=20` = 车队规模），与 §13.20 的读法一致；② `MAINTENANCE` 那层在实践上几乎不可能成为 binding —— `listAssignableVehicles()` 已在 SQL 侧按 `IDLE` 过滤，`UNAVAILABLE` 只是防御性兜底。

**对既有数字的限定**：本文档在 §13.23 之前所有 `LOW_SOC` 计数（含 §13.11 的失败分布、§13.21 末轮 "LOW_SOC 5"）都**可能混有约束不匹配**，只能读作"SOC 或约束不满足"，不能读作"电量不够"。`NO_VEHICLE` 与 `UNREACHABLE` 不受影响。

**测试**：`DispatchVehicleAssignServiceImplTest` 新增 2 条（约束不匹配必须报新编码且消息点名 binding 层；混合车队里"低电+维保"要分开报、真·低电仍报 `LOW_SOC`）+ `DispatchFailExplainSupportTest` 3 条（有原文时保留诊断、无原文时中文兜底且不退化成 `LOW_BATTERY`、建议链接指向 vehicles）。全量门 **310** / 87 / 9 / 8 BUILD SUCCESS，日志 `[ERROR]` 0 行；`npm run typecheck` 通过。

### 13.24 §7.2：高峰档没有「结束」这条腿 —— 顺手挖出调度器把自己写回去的坑

**原状（实测）**：`t_peak_mode_state` 两行 park 1 / park 2 的 `schedule_cron` 与 `schedule_end_cron` **全是 NULL**，
而 `PeakModeCronScheduler.shouldFire` 对空 cron 直接 `return false` ⇒ 定时器永不翻档：
**park 1 永远 NORMAL**（`RulePolicy` 的 `peakSocDamping=0.7` / `peakDistanceFactor` 在真实链路上从未生效），
**park 2 反向永久 PEAK**（`template_code=TEXTILE_PROMO`，仓库里没有任何 SQL 写它 ⇒ 是历史上有人从管理端手工开过，
而没有任何 cron 能关掉它）。任何写「高峰策略已生效」的表述都不成立。

**改了什么**

1. **没有 `schedule_end_cron` 的 PEAK 必须有上限时长**：超过 `fsd.peak-mode.max-peak-duration-minutes`（默认 120，
   新增到 `application.yml`，与 `cron-check-ms` 同处）就回落 NORMAL，并计数 `dispatchflow.peak.auto_reset{park}` + WARN。
   有结束 cron 时兜底**不越权**（档口的出口归 cron 管）。
2. **把「没有时间表」从静默变成可读**：`dispatchflow.peak.schedule{park,state}`，
   `state ∈ {no_time_source, peak_without_schedule, invalid_cron}` —— cron 写错与没配 cron 以前长得一模一样（都静默不触发）。
3. **顺手修掉一个更要紧的老 bug**（本轮 live 验证时才撞出来）：三条分支原来都在 `setMode(...)` 之后再
   `peakModeStateMapper.updateById(state)`，而 `state` 是本轮开头读出的快照 —— `setMode` 自己已经读库、写库并更新了
   `mode/enabledAt`，紧接着用旧快照整行覆盖回去，等于**把刚设的档口撤销**。后果不是「兜底不灵」而是
   **整套 cron 翻档机制在真实库里根本落不了地**：激活写 PEAK 会被立刻覆盖回 NORMAL，结束写 NORMAL 会被覆盖回 PEAK。
   改成只打时间戳那一列（`LambdaUpdateWrapper.set(lastSchedulePeakAt / lastScheduleEndAt)`），不再整行覆盖。

**live 验证（上面第 3 条就是这一步暴露的）**：本机演示库 park 2 停在 `PEAK / 两条 cron 均 NULL`。
T0（新构建起来后第一轮调度，03:07:14）：park 1 `NORMAL`（cron 仍全 NULL，所以它本来就翻不了档）；
park 2 由 `PEAK` 落到 `NORMAL`，`last_schedule_end_at` 与 `enabled_at` 都等于回落那一刻（03:07:14）。
95 s 后再读（又过了 1–2 个调度周期）：park 2 **仍是 `NORMAL`，两个时间戳没再变动** ⇒ 修好之后不会被写回。
`auto-reset` 的 WARN **整轮只出现 1 次**（03:07:13），不是每 60 s 刷一次 ⇒ 兜底只在真的超时那一次动手。
指标：`dispatchflow.peak.auto_reset{park=2}` = **1.0**；
`dispatchflow.peak.schedule{state=no_time_source}` = **9.0**（两园都没有任何 cron，每轮各计一次 × 4–5 轮）；
两个指标 `GET /internal/actuator/metrics/...` 都返回 **200**（修前的陈旧 jar 上这两个指标根本不存在 ⇒ §13.22 那条"计数器要在真进程里活着"的验收规则在这里生效）。

**这一条是怎么把老 bug 逼出来的**：第一次复验（旧构建，02:29:51）时 park 2 也是 PEAK、
`last_schedule_end_at` 也被写上了，但 `mode` 没变 —— 日志每 60 s 报一次"已回落 NORMAL"而库里永远是 PEAK。
顺着这条差异读到 `setMode` 自己会读库写库、调度器又用本轮快照整行 `updateById` 覆盖回去，
才确认**三条分支（激活 / 结束 / 兜底）全都落不了地**，不只是我新加的兜底。

**测试**：`PeakModeCronSchedulerTest` 5 条 —— 超时必回落并计数；上限之内不动手但仍计数（`peak_without_schedule`）；
**有结束 cron 时兜底绝不越权**；NORMAL 且无 cron ⇒ 记 `no_time_source`；cron 写错 ⇒ 记 `invalid_cron`（与「没配」可区分）。
回落那条同时钉住「**never `updateById(快照)`**、必须走只更新时间戳的 update」——就是第 3 条的回归守卫。

**没做**：没有替两个园区决定高峰时段（业务口径 + 部署动作）；没有把高峰判定改成需求导出（M6 那条线）。
建议 cron 与「手工开的档怎么退出」已记进 §8 前置，等本人定。

### 13.25 §7.2：并列裁决改成显式规则 —— 赢家不再由 DB 返回顺序决定

**实测影响面（本机快照 558 条成功决策）**：`tie_count≥2`（有另一台与赢家分数相差 ≤ `TIE_EPSILON=1e-9`）**4 条 = 0.7%** ——
这 4 条才是"赢家由候选数组来路顺序决定"的行；修复前 `ranked.sort(comparingDouble(totalScore))` 只比总分，
`List.sort` 稳定 ⇒ 并列保持入参顺序，而入参顺序来自 `listAssignableVehicles()` 的 DB 返回顺序。
样本形如 `id=751 candidate_total=9 tie_count=2 gap=0.0000 winner=ZJF-AV-09`。

**但要一起记下的另一组数**：`score_gap` 显示 `0.0000` 的有 **57 条 = 10.2%**，其中只有 4 条是真并列，
另 **53 条的原始差值落在 1e-9 与 5e-5 之间** —— 因为 `score_gap` 这个列存的是 `money()` 四舍五入到 **4 位小数**之后的值，
而 `tie_count` 用 1e-9。⇒ **看报表会把 10% 的派单误读成"并列选出来的"**，实际新规则只影响 0.7%。
（另有 24 行 `score_gap<0`，那是 MAPF 为避冲突主动让步，§13.1 已注明是有意保留的信息。）

**改成什么**：`RulePolicy.rankOrder()` = 总分升序 → **`vehicleCode` 字典序最小** → `vehicleId` 升序（空代码走 `nullsLast`，
脏数据不会抢赢正常车，也不会 NPE 成派单失败）。这是**相对修复前的有意偏离**，只可能改变并列情形的赢家，
非并列情形的排序逐字不变。

**为什么次级键选车号而不是"派单次数最少优先"**（roadmap 给的另一个选项）：公平轮转要把每台车的历史派单数带进
`DecisionInput.CandidateState`，而 §13.21 已实测**候选集就是全量在线空闲车、每台还要跑两次路径规划** ⇒
为并列那约 1/10 的分支在热路径加一次查询不划算。公平性该由 `idleMinutes`（空闲越久越优先，已在打分里）承担；
真要"轮转"得先有派单计数这一等数据，属新能力，不在"让已有能力生效"范围内 —— 记在这里免得下次又当成免费选项。

**基线没有漂**：全量门 **313** / 92 / 9 / 8 BUILD SUCCESS（新增 3 条 `RulePolicyTest`），
其中原 `RulePolicyTest` 9 条黄金值、`DispatchVehicleAssignServiceImplTest` 250 条断言、
以及实验台的**默认臂守门断言**（完成率 0.93±0.005 / 里程 138.2 km±2 km）全部零改动通过
⇒ 已记录的 M/L 档数字不受这次改动影响（并列在合成场景里不产生可观测差异，因为并列的两台车本就同泊位同 SOC）。

**顺手记下的一处口径不一致（本次已撞到）**：快照的 `tie_count` 用 `TIE_EPSILON=1e-9`（几乎等于严格相等），
而 `score_gap` 存的是四舍五入到 **4 位小数**后的差值 ⇒ 判据不同一：实测 57 行显示 `0.0000`，
其中 **53 行并不是真并列**（差值在 1e-9~5e-5 之间）。以后要把"并列率"当指标看，
**必须先统一这两个判据**（要么 `score_gap` 保留原始精度，要么把 epsilon 放宽到与显示口径一致的 1e-4）。
本轮只修裁决规则，没有改快照列的精度（那会动 §7.3 已记录的读数口径，属另行决定）。

**没做**：没有把并列规则显示到管理端（那属 §7.3「任务详情页展示 top-3 候选」这条待办，尚未做）；
没有改成"并列时随机"（随机会让决策不可复现，与 §7.3 的可证明性目标相反）。

### 13.26 §7.2：MQTT 自动重连后不重订阅 —— 断一次网就永久失聪

**实测到的机制**（读代码 + Paho 语义，非推测）：`Vda5050MqttGateway.connect()` 里
`options.setAutomaticReconnect(true)` 与 `setCleanSession(true)` 同时开着 —— Paho 的自动重连**只把 TCP 会话连回来，
不会恢复订阅**（clean session 意味着 broker 侧也不保留订阅）。而旧类只实现了 `MqttCallback`，
**这个接口根本没有 `connectComplete` 回调**，`connectionLost` 里只有一行 WARN ⇒ 结果：断网恢复后
`isConnected()` 返回 true、`publish()` 照常成功，但 `messageArrived` 再也不被调用，
车辆状态（VDA5050 state topic）从此进不来。**连接看起来是健康的，实际是失聪** —— 属于最难点的一类故障。

**改了什么**
1. 改实现 `MqttCallbackExtended`，在 `connectComplete(reconnect=true, uri)` 里重新订阅；首次连接仍走启动路径
   （`connectComplete(false)` 明确不重复订阅，避免双份）。
2. 订阅动作收敛成 `subscribeStateTopic(via)`，**内部吞掉 `MqttException` 不外抛**：抛回 Paho 回调线程会让客户端
   停在不可预期状态（这条路径以前既没人看也没人计数）。
3. 加计数器 `dispatchflow.vda5050.mqtt.connection{event=lost|resubscribed|resubscribe_failed}` —— 正是
   §13.15/§13.22 立的规则：「降级为日志必须配指标点」。有了 `lost` 与 `resubscribed` 两条曲线，
   运维能直接算出「重连后没恢复订阅」的比例（差值即异常），不用翻日志。

**验证边界（如实标注）**：本机 `fsd.vda5050.mqtt.enabled=false`，且 `back/docker-compose.yml` 里没有 MQTT broker
（grep `mosquitto|emqx` 无命中）⇒ **这条只能在真实 broker 环境验证**，本轮没有 live 证据。
已落的是 4 条单测：重连必订阅 + 计数、首连不重复订阅、订阅抛异常时不外抛且记 `resubscribe_failed`、
掉线本身计入 `lost`。接线正确性另有两条静态依据：接口换成 `MqttCallbackExtended` 后编译期就保证了回调存在；
`connect()` 里 `setCallback(this)` 早于 `client.connect(options)`，Paho 才会在连接完成时回调到我们。

**部署时要做的**（属部署动作，未替本人执行）：真机接入前需要 (a) 一个可达的 `ssl://` broker，
(b) `fsd.vda5050.mqtt.username/password`（SEC-09 已拒绝匿名），(c) 压一次「断 broker 30 s 再恢复」并确认
`resubscribed` +1 且状态重新入库。这条已作为条件项列进 §12.4 部署动作（仅当真车接入开启时执行），等你交代。

**测试**：`Vda5050MqttGatewayTest` 新增 4 条；全量门 **317** / 92 / 9 / 8 BUILD SUCCESS，日志 `[ERROR]` 0 行。

### 13.27 本文件全部工作中**没有**做的事

- **未执行任何服务器动作**：§12.3 只读核查、§12.4 部署动作、SSH `dispatch` 全部未碰，按"止于部署前"的要求交回本人。
- 未提交、未打 tag：`提交/丢弃其余未提交改动` 这条含逐文件裁决（`.gh-check.js` 是 CI 探针草稿、`front/Dockerfile` 的 `chmod +x` 是构建绕过、未跟踪的 `geo-py/` + `GeoQueryService` 三件套属 §9 的"要么接进围栏补 N≥5,000 压测、要么 README 写明规模预案未接线"），等本人定口径。
- **本轮收尾自查（2026-09-22，命令级）**：文档 §13.x 全部交叉引用都指向存在的小节（脚本比对：26 个小节、引用 0 处悬空），五份报告产物与 `scripts/analysis/forecast-flatness.js` 均在位；`git status` 共 124 项工作区变更，**未提交、未打 tag**（按 §13.27 第一条交回本人）。
- **§7.2「乐观锁是装饰品」的当前状态证据（已复核，尚未修）**：全仓 main 里 `@Version` 与 `OptimisticLockerInnerInterceptor` **零命中**，而 `AdminUserServiceImpl.java:60`、`DispatchStrategyAdminServiceImpl.java:89`、`InfrastructureAdminServiceImpl.java:104/:173` 等仍在 `setVersion(0)` —— 即版本号只被写死成初值，既不递增也不参与 WHERE ⇒ `t_dispatch_task.version` 的注释「乐观锁版本号」是假的。修法二选一（接上插件并证明并发下确有一次更新失败重试，或删列 + 删注释），需要一轮独立工作：接插件会改变该实体所有 UPDATE 的语义，必须先用 `DispatchConcurrencyIntegrationTest` 钉住前后差异，不能盲接。本轮未动它。
- 未动 `Tracking.vue`、`digital-twin/Index.vue`、`ParkPilotSimulationServiceImpl` 本体（冻结清单）；`ParkPilotSimulationServiceImpl` 与 `RealFleetSwapCoordinator` 只改了 §7.2 点名的调用行（方法签名换 `strategyForAssign`），未触算法。

---

## 附：取证方式

- **代码**：本地仓库直读，结论均带 `path:line`
- **数据**：`docker start fsd-mysql` 后只读 `SELECT`，未写任何业务表
- **配置**：`.env`、`.env.production`、`front/.env.local` 仅检查 `FSD_AMAP_WEB_SERVICE_KEY` 的**存在性**，未读取、未输出其值
- **地图坐标**：高德 `v3/place/text` 实测（本人授权后执行），`infocode=10000`，22 次调用，详见 §1.9；脚本 `C:\Users\Administrator\.qoder-cn\tmp\amap_probe.py`、`amap_scan.py`、`amap_scan2.py`
- **服务器规格**：本人提供 8 核 / 8 GB / 80 GB 系统盘 / 45 Mbps / 1 IP；对照 `docker-compose.prod.yml` 的实测内存限制，见 §11
- **几何与产能模型**：`C:\Users\Administrator\.qoder-cn\tmp\zjf_polygon_check.js`（Shoelace 面积 / 外接框 / 自交 / 逐顶点转向）、`zjf_area_check.js`（各围栏面积与比值）、`capacity_check.js`（单车产能与设施上限）
- **Jev**：`https://docs.typesafe.ai/llms.txt` 全站索引及 Introduction / Quick start / `model-jaggedness/jev-1.13` / cookbooks 页面；只引用文档内数字
