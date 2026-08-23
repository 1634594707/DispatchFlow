# DispatchFlow 前端设计与功能改进优化路线图

本路线图基于对 DispatchFlow 前端源码与用户视角的深度梳理制定，旨在提升系统的操作效率、视觉统一性、数据可视化能力与实地移动端操作体验。

---

## 阶段规划概览

- [ ] **[Phase 1] 地图图层控制与工作台态势感知增强**
  - 目标：解决地图多图元重叠遮挡、提升调度大盘视觉层级与操作效率。
  - 核心模块：`front/src/components/map/AmapGeoMap.vue`、`front/src/views/workbench/OperationsCockpit.vue`、`front/src/views/vehicle/Tracking.vue`。

- [ ] **[Phase 2] 调度决策卡片与内联操作闭环**
  - 目标：减少调度员处理待干预任务时的页面跳转摩擦，实现工作台内一键调度与指派。
  - 核心模块：`front/src/views/workbench/OperationsCockpit.vue`、`front/src/components/park/ParkDeliveryOrderModal.vue`。

- [ ] **[Phase 3] 运营分析看板与图表交互升级**
  - 目标：增强趋势图表的时间粒度下钻、数值对比与可视化细腻度。
  - 核心模块：`front/src/components/analytics/TrendBarChart.vue`、`front/src/views/analytics/Index.vue`。

- [ ] **[Phase 4] 垂直业务页面设计系统对齐**
  - 目标：将母港分流、峰谷模式等原生表格页面统一到 `Operational Elegance` 暗色设计规范。
  - 核心模块：`front/src/views/vertical/HubOverview.vue`、`front/src/views/vertical/PeakMode.vue`。

- [ ] **[Phase 5] 移动端现场履约与常用点位交互优化**
  - 目标：提升商户与现场运维移动端下单的触控体验、常用点位记忆与断网草稿保护。
  - 核心模块：`front/src/views/mobile/ParkOrder.vue`、`front/src/views/mobile/MobileOrders.vue`。

---

## 详细执行路线清单

### [Phase 1] 地图图层与视觉层级优化
- [ ] `[P1-1]` 在 `AmapGeoMap.vue` 中封装图层显隐控制器（车辆、围栏、路径走廊、站点）。
- [ ] `[P1-2]` 在 `OperationsCockpit.vue` 提供图层控制胶囊面板，支持独立切换和记忆。
- [ ] `[P1-3]` 优化告警车辆 Marker 动画脉冲视觉效果，提升关键状态辨识度。

### [Phase 2] 工作台待干预任务闭环
- [ ] `[P2-1]` 在 `OperationsCockpit.vue` 任务卡片增加紧迫度视觉阶梯（SLA 风险标）。
- [ ] `[P2-2]` 任务卡片支持就近推荐空闲可用车辆与一键改派触发。

### [Phase 3] 分析图表交互升级
- [ ] `[P3-1]` 增强 `TrendBarChart.vue` 视觉渲染（渐变色条、数值 Tooltip、空态展示）。
- [ ] `[P3-2]` 在 `Analytics/Index.vue` 优化点击下钻联动交互与加载反馈。

### [Phase 4] 垂直业务页面规范化
- [ ] `[P4-1]` 重构 `HubOverview.vue`，用进度环/容量条替代纯文本展示母港占用率。
- [ ] `[P4-2]` 统一卡片与表格阴影、背景色及间距变量（采用 `tokens.css`）。

### [Phase 5] 移动端履约体验优化
- [ ] `[P5-1]` 优化 `ParkOrder.vue` 移动端大号触控靶心与常用点位快速点选标签。
- [ ] `[P5-2]` 增加移动端表单防丢草稿机制（Local Storage 暂存）。
