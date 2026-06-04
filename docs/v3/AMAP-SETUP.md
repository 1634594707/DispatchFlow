# 高德地图接入说明

> V3 M1 · 选型：**高德 JS API 2.0** · 场景：**叠石桥家纺产业带**（仿找家纺 ERP + AI 无人快递车短驳）

---

## 1. 控制台配置

1. 登录 [高德开放平台](https://console.amap.com/dev/key/app)
2. 创建应用 → 添加 Key
3. **服务平台**：选 **Web端(JS API)**（不是 Android/iOS）
4. 为该 Key 开启 **安全密钥（securityJsCode）**
5. **域名白名单** 添加：
   - `localhost`
   - `127.0.0.1`
   - 生产域名（如 `www.aplicity.online`）

## 2. 地理锚点（叠石桥）

| 项 | 值 |
|----|-----|
| 参考位置 | 南通市海门区 · 叠石桥国际家纺城片区 |
| 地图中心 GCJ-02 | `121.080354, 31.961977` |
| 园区 schematic | 1200×800 px ≈ 1570m × 470m |
| 后端配置 | `fsd.park.geo.*` in `application.yml` |
| 前端转换 | `front/src/maps/textileParkGeo.ts` |

Mock 仿真车（`PV01`…）的园区 `x/y` 会通过上述锚点映射到叠石桥附近经纬度。

## 3. 本地环境变量

```bash
cd front
cp .env.example .env.local
```

编辑 `front/.env.local`（**勿提交 Git**）：

```env
VITE_MAP_PROVIDER=AMAP
VITE_AMAP_KEY=你的Key
VITE_AMAP_SECURITY_CODE=你的安全密钥
VITE_AMAP_DEFAULT_CENTER=121.080354,31.961977
VITE_AMAP_DEFAULT_ZOOM=15
```

## 4. 验证 PoC

```bash
cd front && npm run dev
# 另开终端启动后端，确保仿真车在跑
cd back && docker compose up -d   # 或本地 mvn spring-boot:run
```

浏览器：**`/dev/map-poc`**（ADMIN 登录）

- 底图落在叠石桥家纺城附近
- 4 辆 **家纺无人快递车** marker 随仿真移动（3s 刷新）
- 后端 API 返回 `longitude` / `latitude`（GCJ-02）

## 5. 代码锚点

| 路径 | 说明 |
|------|------|
| `front/src/maps/textileParkGeo.ts` | 园区 x/y → GCJ-02 |
| `back/.../geo/ParkGeoTransformService.java` | 后端同源转换 |
| `back/sql/migrations/V21__park_geo_textile.sql` | 园区 geo 字段 + 名称 |
| `front/src/views/dev/MapPoc.vue` | PoC 页 + 车辆 marker |

## 6. 下一步（M2）

- `Tracking.vue` 双模式 Tab「调度图 | 地理图」
- REAL / VDA5050 经纬度统一落 Redis

## 7. Web 服务 Key（M4 / M8-R 驾车路径）

**与前端 `VITE_AMAP_KEY`（JS API）不是同一个 Key。** 后端驾车导航、仿真贴路均依赖 Web 服务 Key。

| 项 | 说明 |
|----|------|
| 控制台 | [高德 Key 管理](https://console.amap.com/dev/key/app) → 新建 Key → 服务平台选 **Web 服务** |
| 后端变量 | `FSD_AMAP_WEB_SERVICE_KEY` |
| 驾车路径 | 默认已开：`fsd.amap.driving.enabled=true`（`FSD_AMAP_DRIVING_ENABLED`） |
| 物流矩阵 | 可选：`FSD_AMAP_LOGISTICS_ENABLED=true`，见 [`LOGISTICS-PATH.md`](./LOGISTICS-PATH.md) |

### 本地启用驾车路径

项目根目录 `.env`（勿提交 Git）：

```env
FSD_AMAP_WEB_SERVICE_KEY=你的Web服务Key
```

或在 PowerShell 中：

```powershell
$env:FSD_AMAP_WEB_SERVICE_KEY = "你的Web服务Key"
cd back
.\run-dev.ps1
```

验证：

```powershell
.\scripts\dev\test_amap_route.ps1
```

期望：`health.amapDriving=True`，`validate` 的 `source=AMAP`，`vertexCount` ≥ 4。

链路说明：`ChainedRoadRouteService` 优先调用高德 `v3/direction/driving`；若路径被 OSM 建筑碰撞检测拒绝，会降级到本地路网（`source=LOCAL_GRAPH`）。
