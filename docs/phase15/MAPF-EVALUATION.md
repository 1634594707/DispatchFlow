# 1000+ 车 MAPF 实时 — 架构评估

> Phase 15.3 · 最后更新：2026-05-31  
> 状态：**评估完成，实现延后至 Phase 16+**

---

## 1. 现状

| 能力 | 当前实现 | 规模上限（经验值） |
|------|----------|-------------------|
| 单路径规划 | `ParkRoutePlannerServiceImpl`（A* on 路网图） | 单车 / 单次派车 |
| 可达性校验 | 派车前 `isReachable` | 百级节点路网 OK |
| 交通管控 | 路段禁行 / 降级 / 暂停区 | 规则驱动，非协同避障 |
| 仿真运动 | `ParkPilotSimulationServiceImpl` tick | 数十车 SSE 可接受 |

**缺口**：多车同时规划时无 **冲突检测 / 时空预约 / 分区求解**，无法保证 1000+ 车无死锁、无对向堵塞。

## 2. 目标定义

| 指标 | Phase 16 目标 |
|------|---------------|
| 车队规模 | 单园区 1000 AGV，其中 200 同时运动 |
| 规划延迟 | P95 &lt; 200ms（分区 batch） |
| 冲突率 | 对向 / 节点占用冲突 &lt; 0.1% |
| 降级 | 求解超时 → 路段限流 + 人工处置（已有 Traffic UI） |

## 3. 推荐架构（三阶段）

### 3.1 图分区（Spatial Partition）

- 按 `t_road_segment` / 枢纽将路网划分为 **Zone**（10–50 个/园区）
- 每 Zone 独立 **预约表**（Redis Hash：`mapf:zone:{id}:reservations`）
- 跨 Zone 边界走 **Open-RMF 式协商**（已有 pause-zone 可演进为 zone lock）

### 3.2 时空预约表（Reservation Table）

```text
Key: mapf:reservation:{segmentId}:{timeBucket}
Value: vehicleId, direction, priority
TTL: bucket 时长（如 500ms）
```

- 派车 / 路径重算时 **预占未来 N 个 segment×time bucket**
- 冲突 → 重规划或排队（优先级：任务 P 级 &gt; 回充 &gt; 空闲巡游）

### 3.3 外置求解器（可选 Phase 16+）

| 方案 | 说明 |
|------|------|
| **内置启发式** | 扩展 A* + 预约表，适合 &lt;300 并发 |
| **CBS / ECBS** | 开源库或自研 Java 模块，适合 300–1000 |
| **gRPC 求解服务** | Python OR-Tools / 商业 MAPF，FMS 只消费 path |

## 4. 与现有代码锚点

| 模块 | 演进方向 |
|------|----------|
| `ParkRoutePlannerServiceImpl` | 增加 `buildRouteWithReservations(parkId, vehicleId, ...)` |
| `DispatchVehicleAssignServiceImpl` | 派车前调用 MAPF 可达 + 预约 |
| `TrafficAdminService` | 拥堵路段与 MAPF 冲突联动 |
| Redis | 预约表 + Fleet 位置（已 `multiGet`） |
| SSE 遥测 | 1000 车需 **园区级聚合推送**（Phase 16） |

## 5. 里程碑建议

| 阶段 | 交付 | 验收 |
|------|------|------|
| **M16.1** | Zone 模型 + Redis 预约表 MVP | 50 车仿真零对向冲突 |
| **M16.2** | 派车链路集成 + 冲突重规划 | 200 车压测 P95 &lt; 500ms |
| **M16.3** | 外置求解器 / 分区并行 | 1000 车登记，200 并发 |

## 6. 结论

Phase 15 已完成 **数据层与读路径规模化**（任务池分页、Redis 批量读）。MAPF 属于 **算法 + 分布式状态** 级改造，不在 Phase 15 范围内一次性交付；本文作为 Phase 16 需求设计输入。

**下一步**：见 [`docs/REQUIREMENTS-DESIGN.md`](../REQUIREMENTS-DESIGN.md)。
