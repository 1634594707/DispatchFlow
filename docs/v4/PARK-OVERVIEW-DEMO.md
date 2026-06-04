# 大屏官方演示链路（V4-O7）

> 入口：**多园区总览** `/gis/park-overview`（导航「多园区总览」）  
> 关联：[`ParkOverview.vue`](../../front/src/views/gis/ParkOverview.vue) · [`Tracking.vue`](../../front/src/views/vehicle/Tracking.vue) · [`ParkOrder.vue`](../../front/src/views/mobile/ParkOrder.vue)

---

## 推荐演示顺序（约 3–5 分钟）

### 1. 总览大屏（30s）

1. 登录管理端 → 打开 **多园区总览**
2. 确认右侧卡片：**叠石桥 L1 试点** 车数 / 在线 / 执行与地图车队一致（每 3s 自动刷新）
3. 地图上可见 L1 围栏、试点仿真车道路折线（非穿楼直线）

### 2. 短驳地理监控（60s）

1. 点击 **进入监控大屏** → `/vehicle-tracking`
2. 场景切换为 **短驳地理**（或 URL `?mode=geo`）
3. 确认：≥4 台 `PARK-*` 车在道路上；计划线 `plannedRouteGeo` 顶点 ≥4；**无**取货→送货虚线直连
4. 若有进行中订单：使用 `?mode=geo&orderId={id}` 跟车（与 R7-2c 脚本输出链接一致）

### 3. 移动下单跟车（60s）

1. 点击 **移动下单** → `/mobile/order`
2. 首屏：**8 个可下单站点**、典型线路卡片一键下单
3. 下单后跟车区仅显示本单取/送/车 + 围栏 + 路线 polyline（`includeOrderLines=false`）

### 4. 园区调度分层（30s）

1. 回到 **车辆监控** → 场景 **园区调度**（默认 schematic）
2. 确认：仅 `park-map.svg` 厂内示意，**无** ZJF 站点与短驳折线（R7-6b）

### 5. 运营闭环（可选）

1. **调度工作台** → **创建短驳订单**（V4-O1）或 **订单管理** 创建
2. **任务详情** 查看 `ZJF-PICK-xx · 站名`（V4-O2）
3. 自动化验收：`.\scripts\m8-r7-accept.ps1 -JsonReport`（见 [`V4-R7-CLOSURE.md`](./V4-R7-CLOSURE.md)）

---

## 深链速查

| 场景 | URL |
|------|-----|
| 总览 | `/gis/park-overview` |
| 短驳地理 | `/vehicle-tracking?mode=geo` |
| 订单跟车 | `/vehicle-tracking?mode=geo&orderId={orderId}` |
| 移动下单 | `/mobile/order` |
| 工作台 | `/workbench` |

---

**维护**：坐标/白名单变更时同步 [`ZJF-DELIVERY-ZONE.md`](../v3/ZJF-DELIVERY-ZONE.md) 与 [`ROADMAP-V4.md`](../ROADMAP-V4.md) §3.1。
