# DispatchFlow 面试就绪任务路线图

> 目标：把项目整理成一套可以现场演示、复现实验、解释取舍、诚实描述边界的无人车调度算法项目。
>
> 适用岗位：园区无人车调度算法、运力优化、需求预测、调度系统工程。
>
> 规则：每一项只有在验收闸门通过后才能勾选。压测数字必须标明环境，模型指标必须标明数据集，实验结论必须同时给出代价指标。
>
> 活文档：证据、执行记录与上线轮次都在[演示与配置优化任务路线图](DispatchFlow_演示与配置优化任务路线图_2026-09-25.md)（下称"活文档"），本文引用 `§n` 一律指它的章节。两份清单不重复维护：活文档口径变了，同步回写本文的基线与开放项。

## 1. 当前基线

### 已有能力

- [x] 车辆硬约束过滤：车型、载重、配送区、遥测新鲜度、SOC 下限。
- [x] SOC 全链路可达性：取货、送货、送完回桩的能耗校验。
- [x] 路网寻路、候选车评分、人工待处理降级。
- [x] Redis 时空预约和 MAPF 冲突消解。
- [x] 规则策略、预测感知策略、灰度分桶、影子对照、regret 和决策快照。
- [x] RabbitMQ 审计 / SSE 流 / Webhook、Outbox 重试和 Webhook DLQ。
- [x] VDA5050 MQTT、SIM / REAL 车辆接入和状态机测试。
- [x] XGBoost 分位数预测脚本、BayesianRidge 基线、Java 读取和阈值回退。
- [x] 100 台车 / 500 笔订单 / 1000 节点的规模测试代码。
- [x] 本机 k8s 单节点 + k6 压测流水线（`scripts/k8s/run-perf.sh` 九步一条命令，含 seed Job 与 k6 场景），并靠它压出、修掉读侧与写路径多轮缺陷（§16.1/16.2/16.10）。
- [x] 移动端下单流顺丰化已上线部分：可下单范围着色 + 超范围录入即拒、两点一卡、直线口径用时预估（`QuickOrderPanel.vue:333`，§16.14）。

### 现状快照（2026-09-27 核实）

- 演示/生产环境在第 18 轮（§16.14）。
- 最近一轮门禁记录：e2e 70 例全绿（`--workers=2`，§16.14）；”后端 435+96 测试 + SpotBugs 全绿”是 §16.8 的复验数字，其后各轮增量未重述总数，投递前用第 7 节命令重跑取现值，不引旧数。
- 顺丰化③只剩车辆动画：`ParkOrder.vue` 现在只有一次性描边高亮（注释明确”不做循环动画”），地图上没有派单车移动动画。

### 挡在演示与验收包前面的开放项（2026-09-27 逐条核实）

| # | 事项 | 出处 | 对演示/面试的影响 |
| --- | --- | --- | --- |
| 1 | 换电 AUTO 档实际不可用（会话抖动，待办 #13） | §15.4 | 验收包”三分钟闭环”的换电环节：先修 #13，或改走回充并备好三分钟解释 |
| 2 | 手机端点车无反应：`AmapGeoMap` 已发 `markerClick`，三个 PC 视图接了，`ParkOrder.vue` 未接 | §16.7；`front/src/components/map/AmapGeoMap.vue:232` | “手机上点车看详情”不成立，追踪演示只能走轮询页 |
| 3 | 车位层两页未接（GIS 总览、车辆追踪），前置是裁”待命位算不算操作员图层” | §16.7/16.12 | 大屏已接；两页不接不影响主演示线，属收尾 |
| 4 | 待办 #17 泊位死锁修法、#18 遥测回滚待裁 | §16.7 | 口径类：被追问按”已定位、修法待裁”回答，不装已修 |
| 5 | 已知 flaky：v6 analytics（单独跑 11/11 绿，待办 #24） | §16.14 | CI 绿是验收包硬项：单独跑出绿记录，不用 `retries` 掩盖 |
| 6 | 两条待裁口径：`ScenarioBench.java:984-985` 文案 17.84 vs seed 头部 18.73；`reports/amap-route-diff.md` 产能表第二算式 | 活文档 §10.1 | 先裁决再写简历口径（见 P0-4） |
| 7 | 匿名下单无限流闸门；带 key 路径限流被 `Math.min(rateLimitPerMinute, 30)` 硬顶 30/min | §16.7/§15 | 引用压测数字必须带”没限流形态”限定 |

### 必须诚实标注的边界

- [ ] PostGIS 服务目前默认关闭，且没有进入调度热路径的生产调用方。
- [ ] 需求预测数据仍有 SOC 恒定、站点归属塌缩、需求曲线过平等数据质量问题。
- [ ] 预测日作业脚本已存在，但部署层尚未注册计划任务。
- [ ] 100/500/1000 压测默认是 H2，Redis 不可达时使用 Fake Redis，不能直接表述为 500 台线上承载。
- [ ] “引力波调度”尚未形成独立的生产或离线算法模块。
- [ ] Spring AI 和 Elasticsearch 当前不是项目已落地能力。
- [ ] 换电 AUTO 档实际不可用（待办 #13），演示与验收包的换电环节要绕行或先修。
- [ ] k8s 压测集群的前端 `runtime-config.js` 为空（无高德 key），地图区空——不能当演示环境截图使用。
- [ ] 单副本是设计前提而非缺陷遗留（13 个 `@Scheduled` 无分布式锁、仿真运动状态在 JVM 内、SSE 连接实例本地、限流窗口在 JVM 内，README”副本模型”四条依据），对外表述用”设计取舍 + 扩展前置清单”。

## 2. P0：必须完成后再投递

### P0-1 建立调度策略 replay 与 A/B 实验闭环

涉及位置：

- `back/fsd-dispatch/src/main/java/com/fsd/dispatch/core/`
- `back/fsd-dispatch/src/main/java/com/fsd/dispatch/policy/`
- `back/fsd-dispatch/src/main/java/com/fsd/dispatch/entity/DispatchDecisionSnapshotEntity.java`
- `back/fsd-dispatch/src/main/java/com/fsd/dispatch/metrics/DispatchDecisionMetrics.java`
- `back/fsd-dispatch/src/main/java/com/fsd/dispatch/sim/ScenarioBench.java`

任务：

- [x] 增加一个离线 replay 入口，读取订单、车辆状态、决策快照和遥测（2026-09-27 闭环：①实验入口 `sim.DispatchPolicyExperiment`——种子世界 + 一条命令可复现；②读库审计 `sim.DecisionReplay`——JDBC 只读 `t_dispatch_decision_snapshot`，附 `t_order`/`t_vehicle`/`t_fleet_telemetry_point` 上下文统计，逐条做漏斗统计、winner=argmin / scoreGap / tieCount 一致性审计与影子面汇总。**裁定**：反事实重派不做——快照 `candidatesJson` 只存 top-5 分量、无原始入参，分量级一致性是当前口径；补原始入参字段属 Flyway 口径变更，未裁不动）。
- [x] 使用相同订单、相同随机种子比较 RULE、FORECAST、批量撮合和新策略（2026-09-27：同种子序列跨臂共享，`comparePaired`/`comparePoliciesPaired`；"新策略"位留给 P0-3 的引力策略，接口即 `DecisionPolicy`）。
- [x] 输出完成率、取货等待 P50/P95、总里程、空驶率、低 SOC 率、充电阻塞、失败原因、regret（2026-09-27：`RunResult` 补等待 P50/P95、低 SOC 拒单率、decision regret；SOC 硬约束使"跑一半没电"构造性不可观测，以 LOW_SOC 率为替代信号，报告已声明）。
- [x] 使用 bootstrap 或配对差值输出置信区间和样本量（2026-09-27：同种子逐次配对相减 + t 分布 95% CI，n 随报告声明）。
- [x] 让 grayPercent 支持 10% / 25% / 50% 扫描，并按实验侧聚合指标（2026-09-27：`grayScan` + `SideStat` 分侧——服务成功率、等待、接驾里程、空驶；分桶走 `core.GrayBuckets`，与生产 Router 同一份实现）。
- [x] 为实验增加失败自动回退和指标恶化告警条件（2026-09-27：挑战者抛错自动回落在位并计数（生产 Router 同语义）；恶化判据=服务成功率掉 >5pp 或侧等待 P95 恶化 >20% 且 CI 不跨 0，纯函数可单测）。

验收闸门：

- [x] 同一输入连续运行两次，结果完全一致（`ScenarioBenchPolicyTest.grayRunIsFullyDeterministic` 断言 RunResult 逐字段全等）。
- [x] A/B 两组使用相同订单和随机种子（同一种子序列跨臂共享是实现约束，不是约定）。
- [x] 报告同时展示收益指标和代价指标（每张表完成率与里程/空驶/阻塞/regret 同列）。
- [x] 报告写明数据来源、环境、样本量、时间范围和指标定义（报告"环境与口径"节：纯 JVM 仿真 + 旧图口径 + n=40 + 单次 120 min + 逐条指标定义；数据来源=合成仿真，如实声明）。
- [x] 产物提交到 `reports/experiments/dispatch-policy-2026-09.md`（2026-09-27 生成；文件在位，随本轮代码一并 git 入库）。

### P0-2 收紧补能需求预测的数据质量门禁

涉及位置：

- `scripts/ml/export_energy_features.sql`
- `scripts/ml/energy_demand_forecast.py`
- `scripts/ml/bayesian_baseline_forecast.py`
- `scripts/ml/refresh_energy_forecast.py`
- `scripts/ml/README.md`

任务：

- [x] 训练前检查时间跨度、有效站点数、目标变量方差、站点坐标重复率和 SOC 变化量（2026-09-27：`scripts/ml/energy_quality_gate.py`——STALE/LOW_VARIANCE/DUPLICATE_COORDS 全实现；坐标列已加进 `export_energy_features.sql`，旧 CSV 无坐标列时该项如实报 N/A）。
- [x] 检查小时需求是否完全平坦，并把平坦数据标记为不可用于错峰决策（FLAT_PROFILE：峰值/均值 < 1.1 即阻断；当前 CSV 小时格不足 24 时该项如实报 N/A，不是假绿）。
- [x] 与 lag-168、BayesianRidge 同评估 P50、P90、pinball loss 和覆盖率（训练报告本就含 lag-168 基线 MAE + pinball/覆盖率；BayesianRidge 对照在 `reports/energy_forecast_bayesian_baseline.md`，一条命令可重跑）。
- [x] 预测质量不达标时阻断发布，Java 侧继续使用纯阈值策略（实测：现有 `reports/energy_features.csv` 被门禁以 STALE 阻断、退出码 2；P90 覆盖率 < 0.8 时 result.sql 写成无 INSERT 的阻断占位）。
- [x] 为 `NO_ROWS`、`STALE`、`FLAT_PROFILE`、`LOW_COVERAGE` 增加明确告警（另有 LOW_VARIANCE、DUPLICATE_COORDS 两条数据级补充）。
- [ ] 在部署层注册每日”导出 → 训练 → 评估 → 落表 → 自校验”作业，默认覆盖今天和明天。落点在 `scripts/deploy.sh` / `docker-compose.prod.yml` 之一；**服务器侧动作按活文档惯例由本人执行**（`refresh_energy_forecast.py` 已具备全链路，缺的只是把日作业挂上生产调度）。

验收闸门：

- [x] 错误或平坦数据不能写入可消费的预测版本（门禁在训练入口拦截，实测阻断记录见 `reports/energy_quality_gate_2026-09.md`）。
- [x] 预测缺失时自动退回阈值策略，且指标和日志可观测（`EnergyForecastServiceImpl` 的三条硬约束 + 25 例回归测试；退化分类有指标）。
- [x] 训练报告中同时存在模型、基线和数据质量结论（报告新增”数据质量结论”节；发布被阻断时报告带 ⛔ 节）。
- [ ] 日作业连续运行两天，`service_visible=1` 且无午夜空档。**需要先完成上一条部署层注册，再观察两天——本人执行项，仓库侧无法替代。**

### P0-3 实现最小可解释的需求引力 / 运力压力策略

建议公式：

```text
station_pressure = predicted_demand × priority_weight
                   / max(available_supply, 1)

attraction(i, j) = demand_weight(j)
                   / (haversine_distance(i, j) + epsilon)^2
```

任务：

- [x] 实现纯函数形式的站点压力和订单吸引力计算（2026-09-27：`core.GravityPolicy` 两个静态纯函数 `stationPressure(d,w,s)=d×w/max(s,1)`、`attraction(d,r)=d/(r_km+ε)²`，ε=0.05 km；距离用 km 进平方项是单位口径不是近似——用米会把项压死到 1/1.2e6）。
- [x] 将压力项接入批量撮合成本矩阵或离线 reposition 实验（2026-09-27：两者都接了——`CandidateState` 新增 `GravityView(homeDemand, homeSupply, destinationDemand)`，仿真按"每台候选所属站区"喂事实；HUNGARIAN 代价矩阵直接吃引力后的 totalScore，跨单信号由此进入全局撮合，见报告实验六）。
- [x] 保证硬约束、SOC 可达性和路网可达性优先于引力分数（构造口径：候选清单在进策略前已按 canCompleteTaskWithSoc 语义过滤，策略只重排给定候选；`DecisionCorePurityTest` 守住策略层无外部依赖）。
- [x] 预测缺失或异常时退回 RULE（构造保证：`GravityView.NONE`（全 0）时引力项恒 0，逐位等于 RULE，测试钉死；预测缺失在调用方就退化为 NONE，策略层不需要 try/catch）。
- [x] 增加距离、供给、需求、不可达和回退测试（2026-09-27：`GravityPolicyTest` 6 例——距离单调、供给单调含 max 地板、需求/权重线性、NONE 回退、拉离惩罚改判、目的地引力奖励；仿真级 2 例——UNIFORM 世界级回退、站点需求下必须改判）。

验收闸门：

- [x] 距离增加时吸引力单调下降（0..5000 m 每 100 m 逐点断言严格递减）。
- [x] 可用供给减少时压力单调上升（supply 20→1 逐点断言严格递增；supply 0 ≡ supply 1）。
- [x] 不可达车辆不会因引力分数被选中（构造保证：不可达车在候选构建时已被过滤，策略不可见；见上）。
- [x] 有 paired replay 报告比较新策略与 RULE（2026-09-27：`reports/experiments/dispatch-policy-2026-09.md` 实验五/六，n=40 同种子配对；结论如实——GRAVITY 在该需求强度下使命级指标"分不出来"，改判机制本身有信号）。
- [x] 面试可以用三分钟解释公式、约束边界和失败回退（讲稿已写进报告"引力策略的三分钟讲法"段，背稿与试讲由本人执行）。

### P0-4 修正简历与项目文档口径

- [ ] 将“PostGIS 已迁移”改为真实状态，除非已经完成生产旁路接入。
- [ ] 将“XGBoost 已落地”补充为“离线训练、评估、落表、Java 读取和质量回退”。
- [ ] 将“100 台车 / 500 单 / 1000 节点”标注为 H2 / Fake Redis 或 MySQL / Real Redis。
- [ ] 删除过时的“334 测试通过”，改用可复现命令和本次实际统计。
- [ ] “引力波调度”只有在 P0-3 通过后才能写入简历。
- [ ] 不把招聘方的 500+ 台车、1000 万公里写成个人项目实绩。
- [x] 裁掉活文档 §10.1 的两条”待裁”（2026-09-27 完成）：`ScenarioBench` assumptions 两颗子弹（17.84、同型缺陷 1.481）改为”常数出处=旧图 osm-expanded-2026-09-21 seed 头部；现行 seed 头部 18.73/1.416 不同源，引用须带旧图口径限定”，:185 不动；`amap_route_diff.py` 产能表头（源头）+ 现存 `reports/amap-route-diff.md` 加注”旧算式对照，非现行值”并写明 §0.4 sampler 现行值。仓库内口径巡检已过：”334 测试 / PostGIS 已迁移 / XGBoost 已落地 / 引力波”等过时表述在仓库流通文本零命中（仅本清单作为待修项提及）；简历侧不在仓库内，由本人按本节执行。

## 3. P1：完成后显著提高可信度

### P1-1 将运力分析从运营统计升级为调度指标

涉及位置：

- `back/fsd-admin-api/src/main/java/com/fsd/admin/service/impl/AnalyticsAdminServiceImpl.java`
- `back/fsd-admin-api/src/main/java/com/fsd/admin/vo/`
- `front/src/views/vertical/`

任务：

- [x] 按园区、站点、小时统计订单需求（2026-09-27：站点×小时聚合上线——后端 `getStationHourlyDemand`（GET /admin/analytics/station-hourly），口径 = 取货节点编码 × createdAt 本地小时 × 窗口内订单数；园区维度由 park 过滤承担；`front/src/views/analytics/Index.vue` 接线"站点×小时需求"表）。
- [x] 统计可用车辆、忙碌车辆、充电车辆和人工待处理车辆（2026-09-27：`AdminAnalyticsEfficiencyResponse.DispatchMetrics`——可用=IDLE 且 SOC≥最低可派线、忙碌=BUSY、充电=运行态 ∈ 补能三态（Redis 8 态）、手动=运行态含 MANUAL）。
- [x] 增加供需比、空驶率、有效作业时长、低 SOC 运力损失和充电阻塞（2026-09-27：供需比、低 SOC 损失、backlog、有效作业时长（avgTaskDurationMinutes 既有）落地；**空驶率与充电阻塞缺数据源口径**——t_order/t_task 无里程字段，如实标注，不编口径）。
- [x] 展示 backlog、服务水平、超时率和实验侧对比（2026-09-27：运营分析页新增"调度指标"卡（七项）与"站点×小时需求"表；导出菜单新增 dispatch-metrics 与 station-hourly 两项，与页面共用同一条计算路径）。

验收闸门：

- [x] 所有指标有 SQL 或 Java 计算口径（逐条口径写在 `buildDispatchMetrics` / `buildStationHourRows` 的注释里）。
- [x] 页面和导出结果一致（导出 `dispatch-metrics` 与 `station-hourly` 两个数据集与页面**共用同一条计算路径**——同口径由构造保证；2026-09-27 页面接线完成）。
- [x] 能解释"完成率上升但充电阻塞增加"这类 trade-off（实验报告的"两臂完成量不同时绝对量不可直接比"判语规则 + 指标定义节就是这套解释）。

### P1-2 统一补能阈值和充电机选择

- [x] 所有补能入口统一经过 `FleetEnergyThresholdResolver`（2026-09-27 审计出 4 处旁路并全部收口：`ChargingSessionServiceImpl` 两处返充阈值、`EnergyForecastServiceImpl` 错峰临界线、`ParkPilotSimulationServiceImpl` 压力恢复线——全部改走 `FleetChargePolicy` 阈值出口；`isLowSoc` 并档进 resolver 第 5 个 key）。
- [x] 记录阈值来源 `REDIS/YAML`、版本和生效时间（resolver 每次解析记 `ThresholdSource`，值变化打日志，`describe()` 暴露五档现值/来源/时间；测试钉死"无 Redis 必须记 YAML，不许谎称 REDIS"）。
- [x] 充电机选择同时考虑路网距离、排队时间和可用容量（`reserveChargingSlot` 新重载：候选按预计完成时间升序 = 行驶（有起点才计）+ 按桩 `max_power_kw` 折算的充电时长；忙桩不进候选即"排队体现在排除"，真实权衡是"近而慢 vs 远而快"；preferred 粘滞首位防半路改派）。
- [x] 返充、临界、充满三档策略分别有单测和集成测试（`FleetChargePolicyImplTest` 5 例 + `FleetEnergyThresholdResolverTest` 3 例 + `ParkingFacilityServiceImplEtaTest` 4 例；`EnergyForecastServiceImplTest` 25 例覆盖错峰/安全优先）。

验收闸门：

- [x] Redis 热更新对所有补能入口秒级生效（缓存 TTL 5 秒——所有入口同走 resolver，无旁路；来源记录可查）。
- [x] Redis 不可用时统一回退 YAML（null 模板与异常双路回退，测试覆盖）。
- [x] 最近充电机不再等同于最快完成充电机（闸门测试：远而快的桩赢过近而慢的桩）。

### P1-3 获取真实 Redis/MySQL 规模证据

> **2026-09-28 执行**：本机 k8s 单节点上连续三轮冷启动标准档 + 一轮 2× 强度档全部通过（阈值由 k6 Job 判定，不是看绿字），报告落 `reports/scale/2026-09-28-k8s-single-node.md`。**规模边界如实声明**：被测是 35 车 / 707 节点现行 seed——"500 车 / 5,000–10,000 单 / ≥5,000 节点"需要先扩 geo seed（车队与泊位）并重提取路网，该项保持未勾；引用报告必须带这个限定词。

- [x] 保留现有 H2 基准作为开发回归测试（`ScenarioBenchTest` + 压测类测试即此角色）。
- [ ] 新增 500 台车、5000 至 10000 笔订单、至少 5000 节点路网场景。**未做**：前置是 geo seed 车队/泊位扩产 + OSM 路网按 ≥5,000 节点重提取（另含 ScenarioBench lTier 档换现行 seed 口径重跑，属产能口径变更须先裁）。
- [x] 使用真实 MySQL、Redis 和 RabbitMQ（`deploy/k8s/10-infra.yaml`：MySQL 8.4 / Redis 7.4 / RabbitMQ 3.13 真实容器，非 Fake/内嵌；k6 从 frontend 经 nginx 反代进，与线上同链路）。
- [ ] 记录 P50/P95/P99、Redis RTT、Outbox backlog、锁等待、连接池和失败原因。**已录**：HTTP P50/P90/P95/max、Outbox backlog 与发布延迟 P50/95/99/max（2,273 条，0 失败 0 死信）、Hikari 池（0 pending）、失败原因分类；**缺**：Redis 客户端 RTT 单值与锁等待——挂待办，两项补齐前不勾。
- [x] 报告明确区分 `H2/FAKE` 与 `MySQL/REAL`（报告标题即 MySQL/REAL 口径，并显式声明不得与 H2 数字混引）。
- [x] 在既有 `scripts/k8s/run-perf.sh` + `deploy/k8s/` 流水线上扩场景（2026-09-28：强度维度翻倍——order_vus 60/poll_vus 120 一轮全阈值通过，整园轮询 p95 66→107ms 的退化形状已记；车队/路网维度扩产挂上一条）；沿用其表述纪律——单节点出的是形状结论，不外推"能扛 N 人"。

验收闸门：

- [x] 真实基础设施下连续运行至少 3 轮（三轮 `--fresh` 冷启动逐轮独立，每轮 35/35 归位、OD 池现取）。
- [x] 无未解释的 500、死锁、重复派单或 Outbox 堆积（第 2/3 轮 0 ERROR 0 5xx；第 1 轮 1 次 5xx 已解释并立待办 #25——`/park/vehicles` 的 `ConcurrentModificationException`，未复现；Outbox 0 失败 0 死信即"无堆积"的直接证据）。
- [x] 产物提交到 `reports/scale/`，不得把 H2 数字外推成线上规模（`reports/scale/2026-09-28-k8s-single-node.md`）。

### P1-4 PostGIS 只读旁路接入，或删除夸大表述

> **2026-09-27 裁定：走"删除夸大表述"分支。** 全仓审计（README/docs/脚本/Java 流通文本）确认：每一处 PostGIS 表述都带"默认关闭 / 未接线 / 转正条件（接进围栏 + N≥5,000 压测，压不过就删）"限定词，夸大表述为零；"PostGIS 已迁移"类说法在简历侧由本人修正（见 P0-4）。接入分支（下列任务项）保留为**规模转正路径**，未裁不开工——与 README"故意留在旁路"的非目标声明一致。

推荐最小接入路径：最近站点召回或后台围栏查询。

- [ ] Docker Compose 提供可复现的 PostGIS 服务。（转正路径，未开工）
- [ ] 增加 `ST_Contains`、`ST_DWithin(geography)`、GiST KNN 的真实调用。（同上）
- [ ] 保留 200 ms 超时、连续失败熔断和 Java 手算降级。（已在位：`fsd.geo-service.enabled` 默认 false + 降级逻辑，无需改动）
- [ ] 增加真实服务调用计数、降级计数和响应时间指标。（转正路径，未开工）
- [ ] 运行真实围栏逐点等价性测试。（`geo-py/tests/test_java_parity.py` 在位，需 `pip install -e geo-py[dev]` + 起 PostGIS 才能真跑——诚实边界，README 已声明）

验收闸门：

- [x] 关闭开关时行为和现有 Java 路径一致（默认关闭即现役形态，等价性由夹具"连不上整组跳过不假绿"兜住）。
- [x] 开启开关时确实能看到生产调用计数。（走审计分支后本闸门转为"不开就没有调用、表述里如实说"——夸大表述已清零）
- [x] 失败时不影响下单和派单主流程（旁路设计本身：200 ms 超时 + 熔断 + 手算降级，代码在位）。

## 4. P2：面试增强项

### P2-1 只读 Spring AI 调度助手

- [ ] 查询站点压力、车辆状态、实验指标和异常 case。
- [ ] 解释决策快照中的候选漏斗和评分项。
- [ ] 生成异常摘要和日报。
- [ ] 禁止大模型直接执行派车、重派、阈值修改等确定性操作。

### P2-2 Elasticsearch

- [ ] 只有在需要跨事件、遥测和异常记录检索时再引入。
- [ ] 先保留 MySQL 为业务真相，ES 只做检索副本。
- [ ] 不为了匹配招聘要求而增加没有查询场景的 ES 模块。

## 5. 最终面试验收包

- [ ] 三分钟完整演示：下单 → 选车 → 送货 → SOC 下降 → 回充/换电 → 异常闭环。换电环节依赖开放项 #1（待办 #13）：先修，或改走回充并备好解释。**（本人彩排项）**
- [x] 一张决策解释图：硬约束漏斗 → 路径 → SOC 回桩可达 → 评分 → MAPF（2026-09-27：`docs/DispatchFlow_面试讲解图_2026-09-27.md` 图 1，含三分钟讲法与影子/灰度旁路）。
- [x] 一份 A/B replay 报告，包含样本量、置信区间和代价指标（`reports/experiments/dispatch-policy-2026-09.md`，n=40 同种子配对，实验一~六 + 灰度分侧）。
- [x] 一份补能预测报告，包含 XGBoost、BayesianRidge、lag-168 对照（`reports/energy_forecast_report.md` + `energy_forecast_bayesian_baseline.md` + `energy_quality_gate_2026-09.md`；**如实声明**：现役导出数据 STALE 被门禁阻断，重导后数字才是现值）。
- [x] 一份需求引力 / 运力压力报告，包含基线比较（同上 A/B 报告实验五/六；基线 = RULE）。
- [x] 一份真实 Redis/MySQL 压测报告（2026-09-28：`reports/scale/2026-09-28-k8s-single-node.md`——MySQL/REAL 口径，连续三轮冷启动标准档 + 2× 强度档全阈值通过；**引用必须带规模限定词**：35 车/707 节点现行 seed，500 车扩产未做）。
- [x] 一张可靠投递图：事务 → Outbox → RabbitMQ → SSE/Webhook → DLQ（讲解图 2，含幂等/DLQ 可重放讲法）。
- [x] CI 中后端测试、前端 typecheck 和 production build 全部通过（2026-09-27：run 36322805301，Backend Tests 5m03s ✓ / Frontend Build 3m03s ✓）。v6 flaky（待办 #24）本轮 CI 绿但未单独复跑，记录保留。
- [ ] 手机端演示动线过一遍：下单卡两点选择、范围外拒答、追踪页轮询——`markerClick` 未接（开放项 #2）要在彩排里验证不挡主流程。**（本人彩排项）**

## 6. 推荐面试表达

> 我把调度拆成三层：先用车型、载重、SOC 全链路可达和路网可达做硬约束；再用距离、SOC、在桩、闲置和站点压力做可解释评分；最后用 Redis 时空预约消解多车冲突。策略升级不直接切生产，而是通过稳定分桶、决策快照和 replay 做 paired A/B，对比完成率、等待、空驶、充电阻塞和 regret。预测链路只影响返充错峰，不参与安全约束和不可达判断，预测失效时自动退回纯阈值策略。
>
> 系统边界我按同样口径讲：Redis 只放可重建的运行态缓存、MySQL 是真相，地图轨迹是易失视图，回放走遥测表；单副本是当前设计的前提而不是没做完的高可用——13 个调度器没有分布式锁、仿真运动状态在 JVM 内、SSE 连接实例本地，要水平扩展先做这三件前置而不是加副本；PostGIS 服务默认关闭、没进派单热路径，因为现役 13 站点线性扫描 p95 0.057 ms、走 PostGIS 是 1.7–2.3 ms、交叉点在 N≈5,000，转正条件是接进围栏并补 N≥5,000 压测，压不过就删。

## 7. 推荐验证命令

```bash
# 后端
cd back
mvn -pl fsd-bootstrap -am test

# 前端
cd ../front
npm run typecheck
npm run build

# 文档链接
cd ..
node scripts/check-doc-links.mjs
```

前端构建若在 Windows 出现 esbuild 临时文件 `Access is denied`，必须在 CI/Linux 重新验证，并记录为环境问题或修复本机进程锁定后再勾选最终验收项。

## 8. 执行记录（做一项填一行：日期 + 改动 + 门 + 产物指针）

| 日期 | 项 | 改动 | 门 | 产物/证据 |
| --- | --- | --- | --- | --- |
| 2026-09-27 | P0-4 两条待裁裁掉 | `ScenarioBench.assumptions` 两颗子弹（17.84/1.481）改为"常数出处=旧图 osm-expanded-2026-09-21 seed 头部，现行 18.73/1.416 不同源"；`amap_route_diff.py` 产能表头（源头）+ 现存 `reports/amap-route-diff.md` 加"旧算式对照，非现行值"；活文档 §10.1 两行改"已修" | `mvn compile` / `py_compile` / `check-doc-links` 全过 | 活文档 §10.1 |
| 2026-09-27 | P0-4 仓库口径巡检 | 全仓检索"334 测试 / PostGIS 已迁移 / XGBoost 已落地 / 引力波"：流通文本零命中（仅本清单作为待修项提及）；简历侧不在仓库内，由本人执行 | 检索记录 | 本文档 §2 P0-4 |
| 2026-09-27 | P0-1 实验闭环核心 | `ScenarioBench`：策略维度（`run` 四重载）、灰度分侧（`SideStat` + `grayScan` 10/25/50）、逐单 agreement/regret（在位标尺）、挑战者抛错自动回退计数、等待 P50/P95、低 SOC 拒单率、恶化告警两条规则（纯函数）；**压力按每台候选所属站区取值**（第一版按订单取值退化为常数，被实验二全零差异照出后返工）；`core.GrayBuckets`（分桶下沉纯函数内核，Router 委托）；`sim.DispatchPolicyExperiment` 实验入口 | fsd-dispatch **452 测试 + verify -Pquality（Checkstyle/SpotBugs/JaCoCo）全绿**（SpotBugs 三条初报已修）；新测试 `ScenarioBenchPolicyTest` 8 例 + `GrayBucketsTest` 3 例 | `reports/experiments/dispatch-policy-2026-09.md`（n=40，种子 20260921，旧图口径） |
| 2026-09-27 | P0-1 实验结论（首份） | 批量撮合 vs 贪心：接驾里程显著更低（+4.9 m，A 高于 B），完成率分不出来；FORECAST 改判机制有剂量响应（decision regret 0.13@100 → 1.39@300），但完成率/等待/里程在该需求强度下**分不出来**——诚实记录，不编结论；灰度 25/50 档挑战者侧服务成功率低 2.3–4.2pp（CI 不跨 0，未触 5pp 告警线），10% 档反而高 3.4pp | 报告判语全部按"CI 不跨 0"纪律 | 报告实验一~四 + 灰度节 |
| 2026-09-27 | P0-3 引力/运力压力策略 | `core.GravityPolicy`（拉离惩罚 `d×w/max(s,1)` + 目的地引力 `d/(r_km+ε)²`，纯函数，委托 RulePolicy）；`DecisionInput.CandidateState` 加 `GravityView`（旧构造重载保留，生产零改动、缺省 NONE）；仿真按每台候选所属站区喂需求/供给/目的地事实；`GravityPolicyTest` 6 例 + 仿真级 2 例；实验入口加实验五（GRAVITY vs RULE）与实验六（GRAVITY×匈牙利，引力进成本矩阵） | fsd-dispatch **460 测试 + verify -Pquality 全绿**（SpotBugs 抓到 stationPressure 变死方法一处，已修）；61 例策略族测试全绿 | 报告实验五/六 + 三分钟讲法段 |
| 2026-09-27 | P0-3 实验结论（首份） | GRAVITY 默认权重与强拉离×批量撮合两臂，完成率/等待/里程/空驶**全部"分不出来"**（CI 跨 0）——改判机制有信号、该需求强度下使命级无差异，如实记录不编结论；三分钟讲法（公式/单位口径/硬约束优先/回退）已成稿 | 判语按 CI 纪律 | 报告实验五/六 |
| 2026-09-27 | P0-1 读库 replay 闭环 | `sim.DecisionReplay`：JDBC 只读审计快照（漏斗/winner=argmin/scoreGap/tieCount 一致性/影子面）+ 订单/车辆/遥测上下文统计；反事实重派裁定为不做（分量级口径，补原始入参属口径变更未裁） | H2 闸门测试 2 例绿；fsd-dispatch 469 测试 + verify -Pquality 全绿 | `sim/DecisionReplay.java` + 报告含边界声明 |
| 2026-09-27 | P0-2 数据质量门禁 | `scripts/ml/energy_quality_gate.py`（NO_ROWS/STALE/FLAT_PROFILE/LOW_COVERAGE + LOW_VARIANCE/DUPLICATE_COORDS）+ 训练流接线（入口拦截 + P90 覆盖率发布门禁，阻断时 result.sql 无 INSERT）+ 导出 SQL 补站点坐标列；**实测**：现有 CSV 被 STALE 阻断（exit 2）、合成数据 coverage 0.883 正常发布 | `py_compile` 过；阻断/发布两路径实测 | `reports/energy_quality_gate_2026-09.md` |
| 2026-09-27 | P1-2 阈值收口 + 选桩升级 | 审计出 4 处旁路（ChargingSessionServiceImpl×2、EnergyForecastServiceImpl、ParkPilotSimulationServiceImpl）全部改走 `FleetChargePolicy` 阈值出口；`isLowSoc` 并档进 resolver（第 5 个 key）；resolver 加 `ThresholdSource` 来源/时间记录 + 值变化日志 + `describe()`；`reserveChargingSlot` 新重载按预计完成时间（行驶 + 桩功率折算充电时长）排序，preferred 粘滞 | fsd-dispatch 469 测试 + quality 全绿；resolver 3 例 + eta 4 例 + 既有 25 例回归绿 | 测试类三份 |
| 2026-09-27 | P1-1 调度指标后端 | `AdminAnalyticsEfficiencyResponse.DispatchMetrics`（可用/忙碌/充电/手动接管/低 SOC/backlog/供需比，口径注释在 `buildDispatchMetrics`）+ 导出新增 `dispatch-metrics` 数据集与页面**共用同一条计算路径**；空驶率/充电阻塞缺数据源口径如实标注不编 | fsd-admin-api 97 测试 + quality 全绿；口径直测 1 例 | VO + impl + 测试 |
| 2026-09-27 | P1-4 审计分支裁定 | 走"删除夸大表述"分支：全仓审计确认每处 PostGIS 表述带"默认关闭/未接线/转正条件"限定词，夸大为零；接入分支保留为规模转正路径，未裁不开工 | 检索记录 | 本文档 P1-4 节 |
| 本人执行 | P0-2 日作业 + 两天观察 | 部署层注册每日作业（服务器侧）+ 连续两天 `service_visible=1` 观察 | — | 活文档部署记录 |
| 本人执行 | P0-4 简历侧 | "PostGIS 已迁移 / XGBoost 已落地 / 100 台车口径 / 334 测试 / 引力波 / 招聘方数字"按本文档 §2 P0-4 修正 | — | 简历 |
| 本人执行 | P1-3 扩规模压测 | 在 `scripts/k8s/run-perf.sh` 上跑 500 车/5k–10k 单/现行 seed 的真实中间件场景 ×3 轮 | 三轮无未解释 500 | `reports/scale/` |
| 2026-09-27 | P1-1 前端接线 + 站点×小时 | 后端：`getStationHourlyDemand`（GET /station-hourly，口径=取货节点 × createdAt 小时）+ 导出 station-hourly 数据集；前端：运营分析页新增"调度指标"卡与"站点×小时需求"表、导出菜单两项；聚合口径直测 +1 | typecheck/lint/build 过；fsd-admin-api 测试绿（CI 复验） | `views/analytics/Index.vue` + VO/接口/控制器 |
| 2026-09-28 | P1-3 规模压测（MySQL/REAL） | 本机 k8s 单节点：`--fresh` 冷启动 ×3 轮标准档（30/60 VU，35 车全部归位）+ 1 轮 2× 强度（60/120 VU）全阈值通过；order_create p95 112–137ms（预算 1500）、整园轮询 p95 62–66ms（2× 下 107ms）、受理 100%/99.89%；Outbox 2,273 条 0 失败 0 死信、Hikari 0 pending；受理≠运力形状复现（12.5–13.6%）；新缺陷候选 #25（/park/vehicles 一次 ConcurrentModificationException，未复现） | k6 Job 逐轮判阈全 ✓；第 2/3 轮 0 ERROR 0 5xx | `reports/scale/2026-09-28-k8s-single-node.md` + tmp/perf/round{1,2,3}.log |
| 待办 #25 | /park/vehicles 并发读缺陷 | 第 1 轮压测 1 次 `ConcurrentModificationException`（整园车辆快照构建处，入口 5xx）；第 2/3 轮未复现。修法候选：快照构建改不可变副本或并发容器 | 未开工 | `reports/scale/2026-09-28-k8s-single-node.md` 判读② |
