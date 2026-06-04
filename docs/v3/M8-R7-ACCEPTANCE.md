# M8-R7 贴路闭环验收指南

> 对应 [`ROADMAP-V3.md`](../ROADMAP-V3.md) §5.2 **R7 验收**（M8-R 关闭条件）  
> 自动化脚本：[`scripts/m8-r7-accept.ps1`](../../scripts/m8-r7-accept.ps1) · [`scripts/m8-r7-accept.sh`](../../scripts/m8-r7-accept.sh)

---

## 前置条件

1. 后端已启动：`http://localhost:8080`（或设置 `FSD_API_BASE`）
2. MySQL 已应用 **V23–V26**（川姜试点站点 + 坐标修正 + 充电站可选）
3. 仿真开启：`fsd.park.simulation.enabled=true`（默认 dev）
4. 管理账号：`admin` / `admin123`（或 `FSD_ADMIN_USER` / `FSD_ADMIN_PASSWORD`）

**路径能力（二选一或并存）**：

| 模式 | 环境变量 | 说明 |
|------|----------|------|
| 高德驾车 | `FSD_AMAP_WEB_SERVICE_KEY` | 主路径，`health.amapDriving=true` |
| 本地路网 | 无 Web Key 或 Amap 失败 | `health.localGraph=true`，见 [`ZJF-DELIVERY-ZONE.md`](./ZJF-DELIVERY-ZONE.md) §6 |

---

## 一键自动化（推荐）

### Windows (PowerShell)

```powershell
# 默认：本机 8080 + admin 登录 + 门市 A→代发仓下单
.\scripts\m8-r7-accept.ps1

# 输出 JSON 报告
.\scripts\m8-r7-accept.ps1 -JsonReport

# R7-3：仅验收本地路网（须先清空 Web Key 并重启后端）
.\scripts\m8-r7-accept.ps1 -ExpectLocalGraphOnly

# 仅检查 health / 车队，不下单
.\scripts\m8-r7-accept.ps1 -SkipOrder
```

### Linux / macOS

```bash
chmod +x scripts/m8-r7-accept.sh
./scripts/m8-r7-accept.sh

# 本地路网模式
EXPECT_LOCAL_GRAPH_ONLY=1 ./scripts/m8-r7-accept.sh

SKIP_ORDER=1 ./scripts/m8-r7-accept.sh
```

**退出码**：`0` = 自动化项全部通过；`1` = 存在 `[FAIL]`（人工项不影响退出码）。

---

## R7 检查项对照表

| # | 主题 | 自动化 | 操作说明 |
|---|------|--------|----------|
| **1** | 4+ 仿真车贴路 | 脚本 `R7-1` | `GET /park/vehicles`：BUSY 车 `plannedRouteGeo` 顶点 ≥4，`routeSource≠STRAIGHT_LINE`，`routeInvalid≠true` |
| **2** | 下单跟车 | 脚本 `R7-2` | `POST /park/orders`（门市 A→代发仓）→ 轮询派车 → 校验指派车路线；**人工**打开 `/vehicle-tracking?mode=geo&orderId=` 目视沿道路移动 |
| **3** | 仅本地路网 | 脚本 `-ExpectLocalGraphOnly` | 清空 `FSD_AMAP_WEB_SERVICE_KEY`，重启后端，再跑脚本 |
| **4** | health 接口 | 脚本 `R7-4` | `GET /api/admin/park/road-route/health`：`amapDriving` 或 `localGraph` 至少一项 true；`fallbackCount` 不应持续飙升 |
| **5** | 录屏 30s | **人工** | 短驳地理场景：仅 `plannedRouteGeo` + `geoTrajectory`，**无**取货→送货虚线直连 |
| **6** | 园区调度分层 | 脚本 `R7-6a` + **人工** `R7-6b` | 数据层 ZJF 站点存在；UI：默认「园区调度」仅 `park-map.svg`，无 ZJF 站点/短驳折线 |
| **7** | 配置自检页 | **人工**（M10） | JS Key / Web 服务 Key / 移动 Key；当前可用 health + `.env.example` 代替 |
| **8** | 防穿模 | 脚本 `R7-8` | `POST /park/road-route/validate`：试点线段 `invalid=false`，顶点 ≥4 |

**常用参数**：`-SkipOrder` 跳过下单；`-ExpectLocalGraphOnly` 验收 R7-3；`-JsonReport` 输出 `dist/m8-r7-report.json`；`-TimeoutSec 10` 连接超时（默认 10s）。

---

## R7-3 本地路网专项步骤

1. 停止后端，从环境或 `application.yml` 中**移除** `FSD_AMAP_WEB_SERVICE_KEY`（或设为空）
2. 确认 `fsd.park.geo.enabled=true`
3. 重启后端，等待仿真 tick
4. 执行：

```powershell
.\scripts\m8-r7-accept.ps1 -ExpectLocalGraphOnly
```

5. 应看到 `health.localGraph=true`、`validate.source=LOCAL_GRAPH`（或类似），且 R7-1/R7-2 仍通过

---

## R7-5 录屏清单（人工 · 约 30s）

1. 打开前端 → **车辆监控** → 场景选 **短驳地理**（或 `?mode=geo`）
2. 确认地图上 **≥4** 台车在道路上（非穿楼直线）
3. 若有进行中订单：计划线为道路折线，已走轨迹与车 Marker 重合
4. **不得**出现取货点与送货点之间的**虚线直连**（`includeOrderLines=false`）
5. 可选：切换至 **园区调度**，确认仅为 schematic，无 ZJF 站点

---

## 常见问题

| 现象 | 排查 |
|------|------|
| `R7-4` 双 false | 配置 `FSD_AMAP_WEB_SERVICE_KEY` 或确认 `LocalPilotRoadGraphService` 已加载 |
| `routeSource=STRAIGHT_LINE` | Web/本地均未命中；查 health.fallbackCount、站点是否在 L1 内 |
| `routeInvalid=true` | R8 面域碰撞未通过；调整路线或黑名单，见 validate 接口 |
| 无 BUSY 车 | 先跑脚本下单（R7-2）或等待自动派单 |
| 登录失败 | 检查 `FSD_ADMIN_AUTH_ENABLED`、V11 种子用户 |

---

## 关闭 R7 的判定

- 自动化脚本 **无 `[FAIL]`**
- 人工完成 **R7-5、R7-6b、R7-7**（R7-3 若纳入发版范围则另跑 `-ExpectLocalGraphOnly`）
- 产品在 ROADMAP §5.2 将 R7 勾选为已验收

**维护**：路线锚点变更时同步 [`LocalPilotRoadGraphService`](../../back/fsd-dispatch/src/main/java/com/fsd/dispatch/geo/local/LocalPilotRoadGraphService.java) 与 [`textileParkGeo.ts`](../../front/src/maps/textileParkGeo.ts)。
