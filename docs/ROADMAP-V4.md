# DispatchFlow V4 产品路线图

> **继承**：[archive/ROADMAP-V3-closed.md](./archive/ROADMAP-V3-closed.md)（V3 已关闭 · 2026-06-04）  
> **试点规范**：[`require.md`](./require.md) · [`v3/ZJF-DELIVERY-ZONE.md`](./v3/ZJF-DELIVERY-ZONE.md)  
> **贴路验收**：[`v3/M8-R7-ACCEPTANCE.md`](./v3/M8-R7-ACCEPTANCE.md) · [`scripts/m8-r7-accept.ps1`](../scripts/m8-r7-accept.ps1)  
> **OSM 路网真源**：[`data/pilot_osm_geo.json`](../data/pilot_osm_geo.json) · [`scripts/dev/analyze_station_snap.py`](../scripts/dev/analyze_station_snap.py)  
> **最后更新**：2026-06-05

---

## 〇、V4 交付焦点

V3 已合入贴路骨架、移动/PC 下单与双地图分层，但 **演示可信度** 仍被四类问题阻塞：

1. **坐标分裂债** — DB / 文档 / 前端锚点 / 简化网格不一致；V29 仅吸附 4 站。
2. **态势图过密** — 工作台「园区态势」`ParkMiniMap` 绘制 **全量** `layout.stations`（含历史厂内站 + 全部 ZJF 站 + 充电站），视觉上「满屏黄点」，不符合 L1 短驳 **8 个运营点**  reality。
3. **移动页信息架构陈旧** — `/mobile/order` 首屏暴露 API Key、展示「24 个站点」、长表单 + 跟车图混排，不像商户侧「选线路 → 下单 → 看配送」。
4. **校验过宽** — ~~站点距 OSM 道路 ≤120 m 即通过~~ → V4-S2 已收紧为 **30 m**。

**P0 四件事（v3.1.0）**：**V4-S · V4-W · V4-M · V4-R7 · V4-O** 已合入。

| 优先级 | 里程碑 | 状态 |
|--------|--------|------|
| **P0** | V4-S / W / M / R7 / O | 已关闭 |
| **P1** | **V4-E** 能源充电 | 进行中（E1/E2/E3/E4/E5/E7 已落地） |
| **P1** | **V4-D** 演示与配置 | 进行中（D1–D4/D5/D6/D13/Q4 已落地） |
| **P2** | **V4-Q** 质量 | 待办 |

---

## 一、V3 关闭说明

| 项 | 说明 |
|----|------|
| **关闭日** | 2026-06-04 |
| **完整快照** | [`archive/ROADMAP-V3-closed.md`](./archive/ROADMAP-V3-closed.md) |
| **入口跳转** | [`ROADMAP-V3.md`](./ROADMAP-V3.md) → 指向 V4 |
| **未关闭里程碑** | M8-R7 · M9-E（部分）· M9-O（部分）· M10 · M6 覆盖率 → **§八 全量迁入 V4** |

### V3 已交付摘要（不再在 V4 重复开发）

- M1–M5 地图/MAPF 基建 · M7 L1 试点围栏与 ZJF 种子站 · M8-P/R1–R6/R8 · 移动下单闭环 · PC `ParkDeliveryOrderModal` · 订单/任务地图 deep link · 低电回充仿真骨架

---

## 二、技术债务清单（需偿还）

### 2.1 P0

| ID | 债务项 | V4 动作 |
|----|--------|---------|
| **TD-P0-1** | 站点坐标四套分裂 | **V4-S1** Flyway V30 + 四源同步 |
| **TD-P0-2** | V29 局部 OSM 吸附 | **V4-S1** §三 全量重选 |
| **TD-P0-3** | 校验阈值 120 m 过宽 | **V4-S2** → 30 m |
| **TD-P0-4** | 三套路网并行 | **V4-S3** OSM 为准 |
| **TD-P0-5** | M8-R7 未验收 | **V4-R7** ✓ |
| **TD-P0-6** | 工作台态势全量站点 | **V4-W** ✓ |
| **TD-P0-7** | 移动页站点/UX 过载 | **V4-M** ✓ |

### 2.2 P1 / P2

见 §四 **V4-E**、**V4-O**、**V4-D**、**V4-Q**；TD-P1-1～P1-5、TD-P2-1～P2-4 与 V3 归档一致，不重复展开。

### 2.3 坐标数据流（目标态）

`pilot_osm_geo.json` → V30 DB → 前端锚点 / 文档 **单一真源**；工作台/移动/地理图 **同一套「可下单站点」白名单**。

---

## 三、L1 站点重选方案（叠石桥 · GCJ-02）

> 距路分析：[`scripts/dev/analyze_station_snap.py`](../scripts/dev/analyze_station_snap.py)

### 3.1 运营站点白名单（演示 / 态势 / 移动下单共用）

**可下单（8）**：`ZJF-PICK-01/02` · `ZJF-DROP-01/02/03/04` · `ZJF-EXPRESS-01`  
**仅调度/回充（6）**：`ZJF-IDLE-01` · `ZJF-CHG-01`～`05` — **不在**工作台态势默认图层、**不在**移动「站点数」统计、**不可**作为移动下单取送货选项（充电回充由仿真/规则触发）

**应隐藏或停用（V4-W1）**：`area` 非 `ZJF` 且非 `ZJF-*` 的历史厂内种子站（导致态势图额外黄点）；DB `status=INACTIVE` 或 API `displayScope=SCHEMATIC_ONLY`。

### 3.2 坐标表

#### 保留（V29 · 距路 ≈0 m）

| 编码 | GCJ-02 |
|------|--------|
| `ZJF-PICK-01` | 121.074453, 31.960396 |
| `ZJF-DROP-01` | 121.079762, 31.963627 |
| `ZJF-DROP-03` | 121.074367, 31.963548 |
| `ZJF-CHG-02` | 121.079780, 31.963518 |

#### V30 目标（需重选）

| 编码 | V30 目标 GCJ-02 | 距路（改前） |
|------|-----------------|-------------|
| `ZJF-PICK-02` | 121.072610, 31.960726 | 11 m |
| `ZJF-DROP-02` | 121.087005, 31.961780 | 96 m |
| `ZJF-DROP-04` | 121.083893, 31.962833 | 96 m |
| `ZJF-EXPRESS-01` | 121.072610, 31.960726 | 86 m |
| `ZJF-IDLE-01` | 121.080055, 31.961922 | 29 m |
| `ZJF-CHG-01` | 121.080069, 31.961850 | 26 m |
| `ZJF-CHG-03` | 121.072610, 31.960726 | 56 m |
| `ZJF-CHG-04` | 121.074442, 31.960671 | 68 m |
| `ZJF-CHG-05` | 121.084334, 31.962890 | 111 m |

### 3.3 L1 围栏（不变）

见 [`v3/ZJF-DELIVERY-ZONE.md`](./v3/ZJF-DELIVERY-ZONE.md) §2。

---

## 四、V4-W — 调度工作台 · 园区态势（P0）

> **现状**：[`ParkMiniMap.vue`](../front/src/components/workbench/ParkMiniMap.vue) 遍历 `layout.stations` **无过滤**，叠加厂内 A/B 区站与全部 ZJF 站，导致截图中黄点过密、不符合实际。

### 4.1 目标体验

- 态势缩略图：**≤10 个** 可见站点（8 可下单 + 可选 1 待命点）；充电站 **默认隐藏**，图例开关「显示充电站」
- 车辆三角 + 路网线保留；站点按类型分色：**取货** / **送货** / **接驳**（与 `stationLayers` 语义一致）
- 角标统计：「**8 个运营站点** · N 车在线」，**不**显示 DB 全量 24
- 点击「全屏监控」→ 地理场景时与 V4-S 坐标一致

### 4.2 待办

- [x] **V4-W1** 新增 `filterWorkbenchSituationStations()`（或复用 `filterGeoDeliveryStations` + 排除 `ZJF-CHG-*` + 排除非 ZJF 厂内站）
- [x] **V4-W2** `ParkMiniMap` 仅绘制过滤后站点；充电站可选第二层
- [x] **V4-W3** 图例：取货 / 送货 / 接驳 / 车辆 / 充电（可选）
- [x] **V4-W4** 后端或迁移：非演示厂内站 `INACTIVE` / `display_on_workbench=0`（避免 layout API 继续返回历史点）
- [x] **V4-W5** 与 **V4-S1** 同步：态势点坐标 = V30

**PR 前缀**：`[V4-Workbench]` · `[V4-Map]`

---

## 五、V4-M — 移动下单界面重构（P0）

> **现状**：[`ParkOrder.vue`](../front/src/views/mobile/ParkOrder.vue) — 首屏 API Key、hero 显示 `stations.length`（全量）、创建订单长表单与跟车图同页。

### 5.1 目标体验（商户 / 演示）

```text
[叠石桥试点]  像看外卖一样看短驳配送
────────────────────────────────────
① 进行中订单（若有）→ 全宽跟车地图（仅本单 取/送/车）
② 典型线路卡片（7 条 · parkDeliveryDemoRoutes）一键下单
③ 高级：自定义取送货（分组下拉，仅 8 站）
────────────────────────────────────
设置（折叠）：API Key 仅 DEBUG / ADMIN
```

- **站点 pill**：`8 个可下单站点`（非 24）
- **跟车地图**：仅 `trackedOrder` 的 pickup / dropoff / vehicle + 围栏 + 路线 polyline，**不**铺全站
- **所属园区**：L1 单园区时 **隐藏** 或锁定「叠石桥试点」
- **完成单**：跟车区显示摘要，引导「再下一单」

### 5.2 待办

- [x] **V4-M1** 信息架构拆分：`OrderTrackingPanel` + `QuickOrderPanel`（或 Tab：跟车 / 下单）
- [x] **V4-M2** 站点数据源：`filterMobileOrderStations()` = ZJF 且非 CHG/IDLE；统计与下拉一致
- [x] **V4-M3** 典型线路卡片置顶；自定义取送收入「更多选项」
- [x] **V4-M4** 取货/送货 `a-select` **分组**（门市 / 代发仓 / 接驳）+ `show-search`
- [x] **V4-M5** API Key：默认 `VITE_MOBILE_API_KEY`；`import.meta.env.DEV` 或角色开关才展示输入框
- [x] **V4-M6** 跟车 `AmapGeoMap` markers 仅本单三点；移除全站 marker
- [x] **V4-M7** 视觉重构：移动端安全区、字号、卡片间距（对齐叠石桥试点品牌）
- [x] **V4-M8**（可选）剩余路程 / 简易 ETA（polyline 长度）

**PR 前缀**：`[V4-Mobile]` · `[V4-Map]`

---

## 六、V4 里程碑与待办（技术 · 运营 · 质量）

### 6.1 V4-S — 站点坐标与校验（P0）

- [x] **V4-S1** `V30__zjf_osm_station_snap.sql` + §3.2 坐标
- [x] **V4-S1b** `zjfStationAnchors.ts` · `ZJF-DELIVERY-ZONE.md` §3
- [x] **V4-S1c** `StationCoordinateValidatorTest` 更新
- [x] **V4-S2** 校验阈值 120 → **30** m
- [x] **V4-S3** 文档：`PilotGridRoads` 仅 OSM 缺失时降级
- [x] **V4-S4** 管理端保存站点：snap + 校验 4xx

### 6.2 V4-R7 — 贴路验收（P0 · 继承 M8-R7）

- [x] **V4-R7-1** 前置 V4-S1 后跑 `m8-r7-accept.ps1`（`-JsonReport`）；管理端 token 绑定 park 可选鉴权路径 + OSM 吸附点入网
- [x] **V4-R7-2** `EXPECT_LOCAL_GRAPH_ONLY=1` 模式（`-ExpectLocalGraphOnly` / `EXPECT_LOCAL_GRAPH_ONLY=1`）
- [x] **V4-R7-3** 人工录屏 R7-5 · 园区调度分层 R7-6b（代码就绪 · checklist 见 [`v4/V4-R7-CLOSURE.md`](./v4/V4-R7-CLOSURE.md)）
- [x] **V4-R7-4** 移动跟车 + `?mode=geo&orderId=` 通过 R7 标准（`includeOrderLines=false` · 深链一致）

### 6.3 V4-O — 运营链路（P1 · 继承 M9-O）

- [x] **V4-O1** 工作台快捷下单入口（`ParkDeliveryOrderModal`）
- [x] **V4-O2** 任务详情 ZJF 站名（`TaskAdminDetailService` 富化）
- [x] **V4-O3** 订单/任务/车辆列表 `EmptyState` 统一
- [x] **V4-O4** 大屏 ↔ 列表状态一致（ParkOverview 侧栏 3s 刷新 · 订单/任务/车辆列表静默轮询）
- [x] **V4-O5** 任务池服务端默认排序；localStorage 仅个人偏好（「恢复服务端排序」）
- [x] **V4-O6** 短驳场景范围文案：「**叠石桥 L1 试点**」（替换川姜）
- [x] **V4-O7** `ParkOverview` → 大屏官方演示链路文档化（[`v4/PARK-OVERVIEW-DEMO.md`](./v4/PARK-OVERVIEW-DEMO.md)）

### 6.4 V4-E — 能源充电（P1 · 继承 M9-E）

- [x] **V4-E1** `StationType.CHARGING_STATION` + V32 迁移 ZJF-CHG 类型
- [x] **V4-E2** 地理图电量：Marker 标签 SOC · 低电/危急图标（`vehicleMapIcon`）
- [x] **V4-E3** 调度图电量：`ParkMiniMap` 车辆三角下 SOC%
- [x] **V4-E4** `Tracking.vue` 低电量 stat + 筛选（`LOW_BATTERY`）
- [x] **V4-E5** 充电站地图：地理图 `⚡ ZJF-CHG-*` + 充电图层占用提示
- [ ] **V4-E6** 管理端充电站 CRUD + L1 贴路校验
- [x] **V4-E7** 回充：最近可达 `ZJF-CHG-*`（`selectNearestChargingSpot` 按 GCJ-02 距路选站）
- [ ] **V4-E8** 回充路径 `RoadRouteService`
- [ ] **V4-E9** `ChargingStationOccupancyService`（Redis）
- [ ] **V4-E10** 派单避让：SOC &lt;30% 优先回充；全车队低电挂起提示
- [ ] **V4-E11** 充电站角标「空闲/总数」

### 6.5 V4-D — 演示与配置（P2 · 继承 M8/M10）

- [x] **V4-D1** 管理端配置自检页 `/system/config-check`（JS / 移动 Key + road-route health）
- [x] **V4-D2** 园区调度场景 tooltip「内部路网示意，非真实道路」
- [x] **V4-D3** 短驳地理顶栏「当前：叠石桥 L1 试点」
- [x] **V4-D4** Key 缺失引导 → 自检页（`AmapGeoMap` 错误态链接）
- [x] **V4-D5** 找家纺品牌叙事（登录/工作台副标题）
- [x] **V4-D6** 演示官方入口文档化（[`v4/PARK-OVERVIEW-DEMO.md`](./v4/PARK-OVERVIEW-DEMO.md)）
- [ ] **V4-D7** `/vehicle-tracking` 默认场景策略（审查 schematic / 演示 geo）
- [ ] **V4-D8** 一键演示模式（2 单循环 · ≤3 min 录屏）
- [ ] **V4-D9** `/dev/map-poc` 与生产短驳地理统一
- [ ] **V4-D10** 录屏脚本（园区 1 段 + 短驳 1 段 + 可选回充）
- [ ] **V4-D11** 演示脚本：低电 → 回充 → 继续接单
- [ ] **V4-D12** 统计面板「试点充电站 X 座 / 快充 Y 桩」
- [x] **V4-D13** 恢复根目录 `.env.example`（指向 `front/.env.example`）

### 6.6 V4-Q — 质量（P2 · 继承 M6）

- [ ] **V4-Q1** JaCoCo 兼容 Java 21
- [ ] **V4-Q2** 单测覆盖率 → 80%；优先 M8 贴路 / M9-E / 移动下单
- [ ] **V4-Q3** `Tracking.vue` / `ParkOrder.vue` composables 拆分
- [x] **V4-Q4** 生产去除 `sseClient.ts` 调试 log（仅 DEV `console.debug`）
- [ ] **V4-Q5** L0 双 20 km 圈可选常驻（大屏复用 ParkOverview）

---

## 七、建议实施顺序

```text
1. V4-S1 + W1/W2 + M2        → 坐标 + 态势/移动站点白名单一致
2. V4-M1–M7                  → 移动下单重构（可与 S 并行 UI）
3. V4-S2 + S4                → 校验收紧
4. V4-R7                     → 贴路验收 → v3.1.0
5. V4-O / V4-E / V4-D / V4-Q → v4.0.0
```

---

## 八、V3 未完成项迁入对照表

> 来源：[`archive/ROADMAP-V3-closed.md`](./archive/ROADMAP-V3-closed.md) 全部 `- [ ]` · 2026-06-04 快照。

| V3 位置 | 原待办摘要 | V4 ID |
|---------|------------|-------|
| §5.2 | 管理端配置自检页 | V4-D1 |
| §5.2 | 园区调度 tooltip | V4-D2 |
| §5.2 | 短驳顶栏 L1 试点文案 | V4-D3 / V4-O6 |
| §6.0 | 地图贴路联动验收（移动+geo） | V4-R7-4 |
| §6.1 | 地理图电量展示 | V4-E2 |
| §6.1 | 调度图电量 | V4-E3 |
| §6.1 | 电量面板 | V4-E4 |
| §6.2 | 充电站类型 | V4-E1 |
| §6.2 | 充电站地图渲染 | V4-E5 |
| §6.2 | 充电站 CRUD | V4-E6 |
| §6.3 | 充电路径规划 | V4-E8 |
| §6.4 | 桩位 Redis | V4-E9 |
| §6.4 | 派单避让低电 | V4-E10 |
| §6.4 | 充电站占用可视化 | V4-E11 |
| §7.1 | 工作台快捷下单 | V4-O1 |
| §7.2 | 任务详情 ZJF 站名 | V4-O2 |
| §7.3 | EmptyState 统一 | V4-O3 |
| §7.3 | 大屏列表一致 | V4-O4 |
| §7.4 | 任务池排序 | V4-O5 |
| §7.4 | 短驳范围提示 | V4-O6 |
| §7.4 | ParkOverview 演示链路 | V4-O7 |
| §7.5 | 隐藏移动 API Key | V4-M5 |
| §7.5 | 站点分组/搜索/最近使用 | V4-M4 / M3 |
| §7.5 | 配送进度 ETA | V4-M8 |
| §8.1 | 品牌叙事 | V4-D5 |
| §8.1 | 演示官方入口 | V4-D6 |
| §8.1 | 监控默认场景策略 | V4-D7 |
| §8.1 | 一键演示模式 | V4-D8 |
| §8.1 | map-poc 统一 | V4-D9 |
| §8.1 | 录屏脚本 | V4-D10 |
| §8.2 | 配置自检页（系统管理） | V4-D1 |
| §8.2 | Key 缺失引导 | V4-D4 |
| §8.2 | front README | V4-D13 |
| §8.3 | 低电回充演示脚本 | V4-D11 |
| §8.3 | 充电站统计 | V4-D12 |
| §8.3 | 演示模式自动回充 | V4-D8 |
| §8.4 | Tracking/ParkOrder 拆分 | V4-Q3 |
| §8.4 | SSE 调试 log | V4-Q4 |
| §8.4 | L0 图例 | V4-Q5 |
| §9.1 | 覆盖率 80% | V4-Q2 |
| **（新增）** | 工作台态势点过密 | **V4-W** |
| **（新增）** | 移动下单界面重构 | **V4-M** |
| V3 §5.2 R7 | M8-R7 七项验收 | **V4-R7** |
| V3 §三 TD | OSM 坐标 / 校验 / 路网 | **V4-S** |

---

## 九、代码锚点

| 主题 | 路径 |
|------|------|
| 工作台态势图 | `front/src/components/workbench/ParkMiniMap.vue` |
| 工作台页 | `front/src/views/workbench/Index.vue` |
| 移动下单 | `front/src/views/mobile/ParkOrder.vue` |
| R7 关闭说明 | `docs/v4/V4-R7-CLOSURE.md` |
| 大屏演示链路 | `docs/v4/PARK-OVERVIEW-DEMO.md` |
| 试点配置自检 | `front/src/views/system/ConfigCheck.vue` |
| 站点分层 | `front/src/maps/stationLayers.ts` |
| 典型线路 | `front/src/constants/parkDelivery.ts` |
| OSM 校验 | `back/fsd-dispatch/.../StationCoordinateValidator.java` |
| 计划迁移 | `back/sql/migrations/V30__zjf_osm_station_snap.sql` |

---

## 十、Release 判定（草案）

| 版本 | 条件 |
|------|------|
| **v3.1.0** | V4-S + V4-W + V4-M + V4-R7 + V4-O（代码已合入；发版前跑 `m8-r7-accept.ps1`） |
| **v4.0.0** | 上列 + V4-E 核心（E6/E8–E11）+ V4-D 演示模式 + 文档四源一致 |

---

**维护**：当前路线图 **仅** [`ROADMAP-V4.md`](./ROADMAP-V4.md) 保留 `- [ ]`。V3 归档只读。坐标/白名单变更同步 §3.1 四处真源。PR 前缀：`[V4-Station]`、`[V4-Workbench]`、`[V4-Mobile]`、`[V4-Road]`、`[V4-Energy]`、`[V4-Ops]`、`[V4-Demo]`、`[V4-Q]`。
