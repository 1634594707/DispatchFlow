# V4-R7 贴路验收关闭说明

> 继承 [`M8-R7-ACCEPTANCE.md`](../v3/M8-R7-ACCEPTANCE.md) · 脚本 [`scripts/m8-r7-accept.ps1`](../../scripts/m8-r7-accept.ps1)

---

## 自动化项（须无 `[FAIL]`）

```powershell
# 默认模式（高德或本地路网二选一）
.\scripts\m8-r7-accept.ps1 -JsonReport

# R7-3：仅本地路网（清空 FSD_AMAP_WEB_SERVICE_KEY 并重启后端后）
.\scripts\m8-r7-accept.ps1 -ExpectLocalGraphOnly -JsonReport
```

报告输出：`dist/m8-r7-report.json`

| ID | 脚本检查 | 通过条件 |
|----|----------|----------|
| R7-1 | 仿真车贴路 | BUSY 车 `plannedRouteGeo` ≥4 顶点，非 `STRAIGHT_LINE`，非 `routeInvalid` |
| R7-2 | 下单跟车 | 创建订单 → 派车 → 指派车路线达标 |
| R7-3 | 仅本地路网 | `-ExpectLocalGraphOnly` 时 `localGraph=true` 且 `amapDriving=false` |
| R7-4 | health | `amapDriving` 或 `localGraph` 至少一项 true |
| R7-6a | 站点分层 | ZJF 与园区站点数据存在 |
| R7-8 | 防穿模 | validate `invalid=false`，顶点 ≥4 |

---

## 人工项（代码已就绪 · 录屏/目视确认）

| ID | 说明 | 代码锚点 |
|----|------|----------|
| R7-5 | 短驳地理 30s 录屏：无 pickup-dropoff 虚线 | `Tracking.vue` `includeOrderLines: false` |
| R7-6b | 园区调度 UI 无 ZJF 站/短驳折线 | `Tracking.vue` `trackingScene === 'park'` + schematic 过滤 |
| R7-7 | 配置自检（M10 前用 health + `.env.example`） | `GET /api/admin/park/road-route/health` |
| R7-2c | 大屏跟车链接 | `/vehicle-tracking?mode=geo&orderId=` |
| R7-4（移动） | 移动跟车仅本单三点 | `ParkOrder.vue` `includeOrderLines: false` |

---

## V4-R7-4 移动 + geo 深链

- 移动页：[`ParkOrder.vue`](../../front/src/views/mobile/ParkOrder.vue) — 跟车地图 markers 仅本单取/送/车
- PC 深链：[`parkDelivery.ts`](../../front/src/constants/parkDelivery.ts) `buildGeoTrackingLink`
- 任务/订单详情：**地图追踪** 按钮指向 `?mode=geo&orderId=`

---

## 关闭判定

- [x] V4-S1 前置（OSM 站点 V30）已合入
- [x] 自动化脚本项实现完整（含 `-ExpectLocalGraphOnly`）
- [x] 前端 R7-5 / R7-6b / R7-4 行为与验收表一致
- [ ] 发版前在目标环境执行脚本并保存 `dist/m8-r7-report.json`（运维/演示机）
- [ ] 人工录屏 R7-5（产品归档）

**Release**：满足上列自动化无 FAIL + 人工三项 → ROADMAP **v3.1.0** 条件中的 V4-R7 可勾选。
