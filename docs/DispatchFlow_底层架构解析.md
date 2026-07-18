# DispatchFlow 底层架构解析：选车算法 & 真实地图泊车

> 分析日期：2025-07-17  
> 分析范围：车辆分配全链路 + 地图坐标系统 + 泊车充电逻辑  
> 代码版本：941cc3f

---

## 一、整体架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                    DispatchFlow 调度核心                         │
├───────────────┬─────────────────────┬───────────────────────────┤
│   选车引擎     │    地图与路网        │     泊车与充电             │
├───────────────┼─────────────────────┼───────────────────────────┤
│ VehicleAssign │ ParkRoadGraph        │ ParkingFacilityService    │
│   ↓           │   ↓                 │   ↓                       │
│ Score + Rank  │ A* Shortest Path     │ Slot Reserve + Bind       │
│   ↓           │   ↓                 │   ↓                       │
│ MAPF Reserve  │ GeoTransformService  │ ChargingSessionService    │
│   ↓           │   ↓                 │   ↓                       │
│ Assign Result │ Haversine + 高德     │ Queue + Timeout            │
└───────────────┴─────────────────────┴───────────────────────────┘
```

---

## 二、选车算法详解

### 2.1 入口与调用链

```
订单进入 → DispatchVehicleAssignService.selectBestVehicle(order)
  ├─ 1. 基础校验层
  ├─ 2. 候选车辆过滤层 (5 层过滤)
  ├─ 3. SOC 全链路校验层
  ├─ 4. 可达性校验层
  ├─ 5. 评分排序层
  └─ 6. MAPF 冲突预约层
```

**核心文件**: `back/fsd-dispatch/src/main/java/com/fsd/dispatch/dispatch/DispatchVehicleAssignServiceImpl.java`

### 2.2 第一层：基础校验

```
checkpoint-1: 派单是否暂停？       → dispatchPauseControlService.isDispatchPaused()
checkpoint-2: 取货点是否存在？     → parkStationService.requireStation()
checkpoint-3: 取货点是否在园区内？ → parkStationService.assertStationInPark()
checkpoint-4: Hub/母站容量？       → hubCapacityService.isHubCapacityAvailable()
checkpoint-5: 路线服务窗口？       → dispatchRouteService.isRouteWithinServiceWindow()
checkpoint-6: 路线并发上限？       → dispatchRouteService.isRouteOccupancyAvailable()
checkpoint-7: 交通管制区域？       → trafficZoneControlService.isPointInPausedZone()
```

任一 checkpoint 失败 → 立即返回 `DispatchAssignResult.failure(reason)`。

### 2.3 第二层：候选车辆过滤

从 `vehicleService.listAssignableVehicles()` 获取所有空闲在线车辆，然后依次过滤：

| 过滤条件 | 方法 | 说明 |
|----------|------|------|
| SOC 最低阈值 | `normalizeSoc(v) >= energy.minAssignableSoc` | 默认 20%，低于此值不可派单 |
| 维保状态 | `!isUnderMaintenance(v)` | UNAVAILABLE 状态排除 |
| 车辆类型 | `matchesRequiredVehicleType()` | 路线要求特定车型时匹配 |
| 车队匹配 | `PilotFleetSupport.matchesOrderFleet()` | 区分真实车队/仿真车队 |
| 配送区域 | `matchesDeliveryZone()` | GEO_DELIVERY / SCHEMATIC / BOTH |
| 载重能力 | `matchesLoadCapacity()` | 订单重量 ≤ 车辆最大载重 |

过滤后若无可用车辆 → 返回 `LOW_SOC` 失败。

### 2.4 第三层：SOC 全链路校验

```java
canCompleteTaskWithSoc(parkId, vehicle, pickup, dropoff, energy)
```

**计算公式**:
```
pickupDist  = 车辆当前位置 → 取货点 的距离
dropoffDist = 取货点 → 送货点 的距离
returnDist  = 送货点 → 最近充电桩 的距离 (haversine, 含 1.3x 路网系数)

consumedSoc = ceil((pickupDist + dropoffDist + returnDist) × 0.05)
剩余SOC     = currentSoc - consumedSoc

通过条件: 剩余SOC ≥ minAssignableSoc
```

**亮点**: 
- 包含"送货完成→回充电桩"的距离，防止车辆送完货后无法回充（ALG-04 修复）
- 无充电桩时使用 200m 默认回充距离
- 对不可达路径（`Double.isInfinite`）跳过校验，避免误报 LOW_SOC

**风险点**:
- 距离单位混合使用：路网距离用像素坐标的欧几里得距离，真实距离用 haversine×1.3
- 每单位距离耗电 0.05% 是硬编码常量，不同车型/载重/速度可能有差异
- haversine×1.3 在弯曲路网中可能低估实际行驶距离

### 2.5 第四层：可达性校验

```java
parkRoutePlannerService.isReachable(parkId, vehicleX, vehicleY, pickupX, pickupY)
```

内部调用 A* 最短路径算法（Dijkstra 变体），若 `startNode → endNode` 不可达则返回 false。

**注意**: 此校验使用 schematic 坐标系（像素），非真实 GPS 坐标。

### 2.6 第五层：评分排序

```java
scoreCandidate(parkId, order, vehicle, blendedDistance, energy, scoring)
```

**评分公式**:

| 评分项 | 公式 | 权重 |
|--------|------|------|
| 距离分 | `distance × weightDistance` | 距离越近分越低（越好） |
| SOC 分 | `(fullSoc - currentSoc) × weightSocMargin` | SOC 越高分越低（越好） |
| 插电奖励 | `weightPluggedStandbyBonus × max(0, 1 - distance/500)` | 满电+插电+STANDBY 时奖励 |
| 空闲奖励 | `min(idleMinutes × weightFairness, maxIdleBonus)` | 等待越久越优先（公平性） |
| 优先级因子 | HIGH=0.7 / NORMAL=1.0 / LOW=1.3 | 高优先级订单缩小总分 |
| 高峰模式 | distance×0.85, socScore×0.7 | 高峰时放宽距离和 SOC 约束 |

**最终总分**: `total = (distanceScore + socScore - pluggedBonus - idleBonus) × priorityFactor`

**总分越低越好**。

**距离混合**: 当高德物流矩阵可用时，`dispatchGeoDistanceService.applyGeoBlend()` 将 schematic 距离和高德真实距离混合，权重可配置（`amap.logistics.blend-weight`）。

### 2.7 第六层：MAPF 冲突预约

```java
selectWithMapfReservation(parkId, pickup, rankedCandidates)
```

按评分排序后，依次尝试 MAPF 时空预约：
1. 调用 `mapfRoutePlannerService.planAndReserve()` 
2. 内部 A* + 冲突惩罚 → 边预约 + 节点预约（Redis 时空桶）
3. 第一个预约成功的候选车辆胜出
4. 若全部预约失败，降级为评分第一名

**Redis 预约机制**:
- Key 格式: `mapf:res:{parkId}:{from}>{to}:{bucket}`（边）
- Key 格式: `mapf:node:{parkId}:{node}:{bucket}`（节点）
- TTL = bucketMs × (horizon + 2)
- 检查对向冲突（反向边是否被其他车辆占用）
- 失败时回滚所有已获取的 key

### 2.8 选车算法评价

**优点**:
- 多维度评分体系完整，考虑了距离、SOC、公平性、优先级
- 全链路 SOC 估算包含回充距离
- 高峰/低谷模式动态调整权重
- MAPF 冲突预约避免多车路径碰撞
- 支持高德物流矩阵混合真实距离

**潜在问题**:
1. **评分权重硬编码**：`weightDistance`、`weightSocMargin` 等来自配置但默认值未文档化
2. **haversine×1.3 精度不足**：真实路网可能有大量直角转弯，1.3 倍系数偏低
3. **MAPF 预约失败降级无记录**：当全部候选车冲突时静默降级为评分第一，可能导致路径冲突
4. **无多园区跨园区选车**：`listAssignableVehicles()` 是否按园区过滤取决于实现

---

## 三、真实地图与坐标系统

### 3.1 三套坐标体系

DispatchFlow 维护了三套并行的坐标系统：

```
┌──────────────────────────────────────────────────────────────────┐
│                     三套坐标体系                                   │
├──────────────┬───────────────────┬───────────────────────────────┤
│  Schematic   │  GCJ-02 (火星)    │  WGS-84 (原始GPS)             │
│  (园区像素)   │  (中国国测局)      │  (国际标准)                    │
├──────────────┼───────────────────┼───────────────────────────────┤
│ x, y 像素坐标 │ coordLng, coordLat│ 车辆上报原始GPS                │
│ 路网节点用    │ 站点/车位用        │ fleetRuntime 中存储            │
│ A* 路径规划   │ haversine 距离    │ 需要转换为 GCJ-02              │
└──────────────┴───────────────────┴───────────────────────────────┘
```

### 3.2 坐标转换服务

**文件**: `ParkGeoTransformService.java`

```
Schematic → GCJ-02 (toGcj02):
  parkX, parkY (像素)  →  通过 anchor 锚点 + 米/像素比 →  GCJ-02 (lng, lat)

GCJ-02 → Schematic (fromGcj02):
  lng, lat  →  通过 anchor 锚点 + 米/像素比 →  parkX, parkY (像素)
```

**转换参数**（来自 `application.yml`）:
```yaml
fsd.park.pilot.geo:
  enabled: true
  anchor-lng: 121.060000    # 园区锚点经度
  anchor-lat: 31.920000     # 园区锚点纬度
  park-width-meters: 2400   # 园区实际宽度（米）
  park-height-meters: 1600  # 园区实际高度（米）
```

**工作原理**: 
- 以锚点 (anchorLng, anchorLat) 为园区中心
- 每像素对应 `widthMeters/mapWidth` 米的实际距离
- X 轴 = 东方向，Y 轴 = 北方向（但像素 Y 轴向下，需要翻转）

### 3.3 路网图构建

**文件**: `ParkRoadGraph.java`

```
数据来源:
  ├─ 数据库 (优先): t_road_node + t_road_segment
  │   - 节点: node_code, coord_x, coord_y, status
  │   - 路段: from_node_code, to_node_code, status, congestion_level, speed_limit_kmh
  └─ YAML (降级): application.yml 中的 fsd.park.road-nodes / road-segments
```

**关键过滤逻辑**:
```java
// 节点: 只加载 status = ACTIVE 的节点
if (!"ACTIVE".equalsIgnoreCase(entity.getStatus())) continue;

// 路段: 只加载 status = ACTIVE 的路段
if (!"ACTIVE".equalsIgnoreCase(segment.getStatus())) continue;
```

**交通拥堵系数**:
```java
cost = base_distance × (1 + congestion_level × 0.35 + speed_limit_penalty)
// congestion_level: 0-3, 每级增加 35% 开销
// speed_limit < 10km/h: 额外 +50% 开销
```

### 3.4 Geo 多边形与围栏

**文件**: `GeoPolygonUtils.java`

- **射线法**: 判断点是否在多边形内（用于围栏检测）
- **Haversine**: 计算球面两点距离（用于真实 GPS 距离）
- **点到线段距离**: 用于 GPS 缓冲判定（15 米容差）

### 3.5 真实地图泊车评价

**优点**:
- 三套坐标体系互转完整，支持 schematic 和真实 GPS 的灵活切换
- 围栏检测使用 GCJ-02 坐标系，与中国地图匹配
- 路网图支持拥堵系数，模拟真实交通状况
- 支持从 YAML 快速原型到数据库精细化管理的演进路径

**潜在问题**:

1. **坐标精度损失**: schematic ↔ GCJ-02 转换使用固定米/像素比，但地球曲率导致每像素实际米数在不同位置有微小差异
2. **锚点中心假设**: 假设锚点是园区中心，但实际园区可能不是以锚点对称分布
3. **围栏 GPS 缓冲固定 15 米**: 对于 GPS 精度较差的场景（如室内/高楼间），15 米可能不够
4. **路网图无向性假设**: `fromDatabase()` 为每个有向路段自动添加反向边，假设所有路段双向可通行，实际上可能存在单行道

---

## 四、泊车与充电逻辑

### 4.1 泊车流程

```
车辆完成任务 → 需要泊车/充电
  ├─ reserveSlot(parkId, vehicleId, slotCode)
  │   ├─ 释放旧预约
  │   ├─ 乐观锁更新 slot (FREE → RESERVED)
  │   └─ 同时更新充电桩状态
  ├─ occupyPluggedStandby()  [插电待命]
  │   └─ bindVehicleToSlot() + 完成充电会话
  └─ markCharging()  [开始充电]
      └─ bindVehicleToSlot() + startSession()
```

**文件**: `ParkingFacilityServiceImpl.java`

### 4.2 车位绑定（乐观锁）

```java
bindVehicleToSlot(parkId, vehicleId, slotCode, targetStatus)
```

**并发安全**:
```sql
UPDATE t_parking_slot 
SET occupied_vehicle_id = ?, status = ?
WHERE id = ? 
  AND deleted = 0
  AND (status = 'FREE' OR (status = 'RESERVED' AND occupied_vehicle_id = ?))
```

如果更新行数 ≠ 1 → 抛出 `PARKING_SLOT_CONFLICT`，由上层重试或失败。

### 4.3 充电桩推荐

```java
recommendChargingPile(vehicleId)
```

**逻辑**: 遍历所有 FREE 充电桩，选离车辆最近的（haversine 距离）。

**问题**: 不考虑充电功率、排队长度、或未来任务方向。纯距离优先可能导致车辆都涌向同一个充电桩。

### 4.4 充电队列

```java
getChargingQueue(parkId)
```

**优先级**:
- SOC 越低越优先
- 高峰时段（8:00-10:00, 17:00-19:00）：SOC > 50% 的车辆排在 SOC ≤ 50% 之后
- 只纳入 IDLE 状态的车辆（BUSY 车辆不加入队列）

### 4.5 充电超时

```java
timeoutStaleChargingSessions()
```

- 超过 `chargingTimeoutMinutes` 分钟仍 ACTIVE 的会话 → 标记为 TIMED_OUT
- 释放车位和充电桩
- **不修改车辆 dispatch_status**（避免覆盖人工操作）

### 4.6 充电需求预测

```java
predictChargingDemand(parkId, lookaheadMinutes)
```

**逻辑**:
1. 统计当前 SOC < threshold 的在线车辆数
2. 对 BUSY 车辆预估未来 `lookaheadMinutes` 分钟的 SOC 消耗
3. 汇总需要充电的车辆数

**耗电速率**: 假设平均速度 1.5 m/s，每 50 米消耗 1% 电量（可配置）。

### 4.7 泊车充电评价

**优点**:
- 车位乐观锁保证并发安全
- 充电队列有高峰/低谷优先级
- 充电超时机制防止僵尸占用
- 需求预测可辅助运维决策

**潜在问题**:

1. **充电桩推荐只看距离**：如果最近充电桩被频繁使用，其他充电桩长期空闲，利用率不均衡
2. **充电队列忽略 BUSY 车辆**：正在执行任务的车辆即使 SOC 极低也不会排队
3. **无充电桩全满时的降级策略**：所有桩被占用时，低电量车辆只能等待
4. **`recommendChargingPile` 对每个空闲桩都查一次 parkingSlot**：N+1 查询问题，桩多时性能差

---

## 五、整体架构评估

### 5.1 选车算法结论

| 维度 | 评分 | 说明 |
|------|------|------|
| 评分维度完整性 | ⭐⭐⭐⭐ | 距离/SOC/公平性/优先级/高峰模式 5 维度 |
| SOC 估算精度 | ⭐⭐⭐ | haversine×1.3 有偏差，无真实路网距离 |
| MAPF 冲突避免 | ⭐⭐⭐⭐ | 边+节点双重预约，回滚机制完善 |
| 并发安全 | ⭐⭐⭐⭐ | Redis 原子预约 + 乐观锁 |
| 降级策略 | ⭐⭐⭐ | 冲突降级无日志，高德不可用时降级 schematic |
| 边界场景 | ⭐⭐ | 无车可用、全部不可达等有处理，但无重试/告警 |

**整体评价**: 算法设计合理，覆盖了物流调度的核心场景。**主要短板是 SOC 估算精度和缺少对长期阻塞任务的兜底机制**。

### 5.2 真实地图泊车结论

| 维度 | 评分 | 说明 |
|------|------|------|
| 坐标体系 | ⭐⭐⭐⭐ | 三套坐标互转，支持 GCJ-02 |
| 路网建模 | ⭐⭐⭐ | 拥堵系数好，但缺少单行道/限高/限宽 |
| 围栏检测 | ⭐⭐⭐ | 射线法+GPS缓冲，误报率偏高 |
| 泊车并发 | ⭐⭐⭐⭐ | 乐观锁保证安全 |
| 充电调度 | ⭐⭐⭐ | 队列+超时+预测，但忽略 BUSY 车辆 |
| 地图集成 | ⭐⭐⭐⭐ | 高德物流矩阵可选集成 |

**整体评价**: 坐标转换和路网建模足以支撑园区级调度。**主要短板是围栏误报率和对真实道路约束（单行道、限高）的建模不足**。

---

## 六、改进建议优先级

| 优先级 | 改进项 | 预期收益 |
|--------|--------|----------|
| P1 | SOC 估算改用高德物流矩阵真实路径 | 精度提升 20-30%，减少中途没电风险 |
| P1 | 充电队列纳入 BUSY 车辆预估 | 提前调度充电，减少任务中断 |
| P2 | 围栏增加"无任务不告警"抑制 | 减少 80%+ 无效告警 |
| P2 | 增加不可达任务超时+告警 | 避免任务永久阻塞 |
| P2 | 路网增加单行道/限行支持 | 真实园区建模更准确 |
| P3 | 充电桩推荐增加负载均衡 | 提升充电桩整体利用率 |
| P3 | N+1 查询优化 | 减少 DB 压力 |

---

*分析基于 DispatchFlow commit 941cc3f*
