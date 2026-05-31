# M4 物流路径增强 — 边界说明

> V3 · M4 · 最后更新：2026-05-31

---

## 1. 定位

| 能力 | 用途 | **不参与** |
|------|------|-----------|
| 高德物流距离矩阵（N-1） | REAL 派单**距离评分**辅助 | 园区路网 `isReachable` |
| 公开道路路径 | 运维大屏**展示 / 估算** | `ParkRoutePlanner` 执行路径 |
| Web 服务 Key | 后端 `distance` API | 前端 JS API Key |

**原则**：L1 调度图（A* / MAPF）仍是派车与仿真的唯一执行层；L2 地理图与物流 API 只做辅助。

---

## 2. 配置

### 2.1 高德控制台

1. 另建 Key，**服务平台**选 **Web 服务**（不是 JS API）
2. 开通「距离测量 / 路径规划」类 API
3. 配置 IP 白名单或服务端调用限制

### 2.2 后端 `application.yml`

```yaml
fsd:
  amap:
    web-service-key: ${FSD_AMAP_WEB_SERVICE_KEY:}
    logistics:
      enabled: ${FSD_AMAP_LOGISTICS_ENABLED:false}
      blend-weight: 0.3   # 0=仅园区路网，1=完全采用矩阵距离
```

环境变量示例：

```bash
FSD_AMAP_WEB_SERVICE_KEY=你的Web服务Key
FSD_AMAP_LOGISTICS_ENABLED=true
FSD_AMAP_LOGISTICS_BLEND_WEIGHT=0.3
```

### 2.3 前端 Key（不变）

前端仍使用 `VITE_AMAP_KEY`（JS API），见 [`AMAP-SETUP.md`](./AMAP-SETUP.md)。

---

## 3. 代码锚点

| 模块 | 路径 |
|------|------|
| Web 服务配置 | `AmapProperties` |
| 距离矩阵客户端 | `AmapLogisticsDistanceService` |
| 派单混合评分 | `DispatchGeoDistanceService` |
| 派车入口 | `DispatchVehicleAssignServiceImpl` |

---

## 4. 评分流程

```text
候选车辆 → 园区路网 isReachable（必须）
         → 园区 A* 路径长度（主距离）
         → [可选] 高德 N-1 矩阵（GCJ-02）
         → blend = parkPx * (1-w) + geoPx * w
         → SOC / 插枪加成 → 选最优车
```

GCJ-02 来源优先级：

1. 站点 `coord_lng / coord_lat`
2. 车辆 Redis `FleetRuntime.longitude/latitude`
3. `ParkGeoTransformService` 由园区 x/y 推导

---

## 5. 风险与降级

| 场景 | 行为 |
|------|------|
| 未配置 Web 服务 Key | 自动降级为纯园区路网评分 |
| API 超时 / 配额 | 记录 warn 日志，使用园区距离 |
| 坐标缺失 | 跳过矩阵，不影响派单 |

---

## 6. 验收

- [x] N-1 矩阵接口封装 + 单元测试
- [x] 派单评分集成（blend-weight 可配置）
- [x] 文档明确与 `ParkRoutePlanner` 边界

**相关**：[`MAP-PROVIDER-EVALUATION.md`](./MAP-PROVIDER-EVALUATION.md) · [`ROADMAP-V3.md`](../ROADMAP-V3.md) §五
