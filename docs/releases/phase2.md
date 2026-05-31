# Phase 2 完整更新说明（A + B 合并）

> 更新日期：2026-05-27  
> 范围：P2-01 ~ P2-08（基线）+ **P2-09 ~ P2-SLOT（后端 A）** + **P2-12 ~ P2-14 / P1-UI（协作者 B）**  
> 验收：[../acceptance/phase2.md](../acceptance/phase2.md) · 联调：[../integration/unreachable.md](../integration/unreachable.md) · [../integration/conflict.md](../integration/conflict.md)

---

## 1. 一句话总结

从「管理页拼接 + YAML 路网」升级为：**DB 路网 + 评分派单可达性 + 充电位抢占 + 工作台运营指标 + SSE 实时车态 + 可解释派单失败码**；仿真与派单、监控、工作台已打通。

---

## 2. 任务与负责人对照

| 编号 | 内容 | 负责人 | 状态 |
|------|------|--------|------|
| P2-01~02 | 车位 / 充电桩实体与状态枚举 | A | ✅ |
| P2-03 | `ParkingFacilityService` 车辆↔车位绑定 | A | ✅ |
| P2-04 | 充电会话 `t_charging_session` | A | ✅ |
| P2-05~07 | 评分派单、`DispatchAssignFailReason`、workbench 查询 API | A | ✅ |
| P2-08 | `GET /api/admin/dispatch/workbench` 聚合 | A | ✅ |
| **P2-09** | SQL **V9** 路网节点/边 | A | ✅ |
| **P2-10** | 规划器 DB 优先、YAML fallback | A | ✅ |
| **P2-11** | 派单可达性 → `UNREACHABLE` | A | ✅ |
| **P2-SLOT** | 充电位 `RESERVED`、多车抢桩 | A | ✅ |
| **P2-12** | SSE `GET /api/admin/fleet/telemetry/stream` | B | ✅ |
| **P2-13** | `Tracking.vue` 订阅 + 轮询兜底 | B | ✅ |
| **P2-14** | 工作台 fleet 指标 + 派单可解释 UI | B | ✅ |
| P1-UI | Phase 1 手工验收 2 项 | B | ☐ 待走查 |

---

## 3. 能力变更（按用户可见）

### 3.1 调度工作台

- Header 展示：**可派车** / **插枪待命** / **充电中** / **在线车辆**（来自 `fleetMetrics`）。
- 单次请求 workbench API 复用 `parkLayout`、`vehicles`，减少重复拉 park 接口。
- 自动派车成功：展示 `assignExplanation`、`selectedVehicleCode`、评分。
- 自动派车失败：按 `failReasonCode` 显示中文（无车 / 低电量 / 不可达 / 冲突）。

### 3.2 车辆监控

- 优先 **SSE** 订阅车态（`STREAM` 徽章），断线指数退避重连。
- Stream 不可用时回退原轮询（`LIVE`），完全失败为 `OFFLINE`。
- 推送 payload 与 `GET /api/admin/park/vehicles` 车辆快照结构一致。

### 3.3 派单与路网（后端）

- 自动派车按距离 + SOC + 插枪待命加分选车（`DispatchVehicleAssignService`）。
- 取货点在路网中不可达 → `failReasonCode=UNREACHABLE`（断边可复现，见 D1 文档）。
- 占车/占桩冲突 → `CONFLICT` + `MANUAL_PENDING`。
- 路网以 **`t_road_node` / `t_road_segment`** 为准；无 DB 数据时仍 fallback `application.yml`。

### 3.4 仿真充电

- 前往充电桩前 **`reserveSlot`**；失败则尝试其他桩或 `WAIT_CHARGING` 重试。
- 充电会话写入 `t_charging_session`，与车位状态联动。

---

## 4. API 与契约

### 4.1 已有增强（P2-05~08，B 消费）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/dispatch/workbench` | `intervention` + `fleetMetrics` + `parkLayout` + `vehicles` |
| POST | `/api/admin/tasks/{taskId}/auto-assign` | 见下表响应字段 |
| POST | `/api/admin/tasks/{taskId}/manual-assign` | 人工派车（`AdminTaskManualAssignRequest`） |

**自动派车响应（节选）**

| 字段 | 说明 |
|------|------|
| `success` | 是否派车成功 |
| `failReasonCode` | `NO_VEHICLE` / `LOW_SOC` / `UNREACHABLE` / `CONFLICT` |
| `assignExplanation` | 评分说明（成功时） |
| `selectedVehicleCode` | 选中车辆 |
| `assignScore` | 综合得分 |

### 4.2 新增（P2-12，B）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/fleet/telemetry/stream?parkId=1` | SSE，`text/event-stream`，约 1s 推送 |

**事件 payload（`event: telemetry`）**

```json
{
  "parkId": 1,
  "ts": "2026-05-27T10:00:00Z",
  "vehicles": [ /* ParkVehicleSnapshotResponse[] */ ]
}
```

前端 URL 构造：`getFleetTelemetryStreamUrl()` → `{VITE_API_BASE_URL}/api/admin/fleet/telemetry/stream`。

### 4.3 failReason 中文映射（B · `statusMap.ts`）

| `failReasonCode` | 中文 |
|------------------|------|
| `NO_VEHICLE` | 无可用车辆 |
| `LOW_SOC` | 电量不足 |
| `UNREACHABLE` | 取货点不可达 |
| `CONFLICT` | 占车/占桩冲突 |

---

## 5. 数据库迁移

| 版本 | 文件 | 内容 |
|------|------|------|
| V7 | `back/sql/init/V7__parking_slot_and_charging_pile.sql` | `t_parking_slot`、`t_charging_pile` + 默认园区种子 |
| V8 | `back/sql/init/V8__charging_session.sql` | `t_charging_session` |
| V9 | `back/sql/init/V9__road_network.sql` | `t_road_node`、`t_road_segment` + park_id=1 路网种子 |

**已有库**需手动执行 V7/V8/V9，见 [../DEPLOYMENT.md](../DEPLOYMENT.md)。

---

## 6. 文件变更清单（合并双方）

### 6.1 前端（协作者 B · P2-12~14）

| 路径 | 变更说明 |
|------|----------|
| `front/src/views/workbench/Index.vue` | Fleet 指标 + 在线车辆；复用 workbench 的 layout/vehicles；自动派车成功/失败反馈 |
| `front/src/views/vehicle/Tracking.vue` | SSE 订阅、断线重连、轮询兜底；STREAM/LIVE/OFFLINE 徽章 |
| `front/src/stores/workbench.ts` | `parkLayout` / `parkVehicles` / `onlineVehicleCount`；workbench 一次拉取 |
| `front/src/api/dispatch.ts` | **新建** `getDispatchWorkbench`、`getFleetTelemetryStreamUrl` 等 |
| `front/src/api/task.ts` | `TaskAssignResponse` 增加 `assignExplanation`、`failReasonCode` 等 |
| `front/src/constants/statusMap.ts` | `DISPATCH_FAIL_REASON` 四类中文 |
| `front/src/types/stream.d.ts` | **新建** `FleetTelemetryPayload`、SSE 客户端类型 |
| `front/src/utils/sseClient.ts` | **新建** EventSource 封装、指数退避重连 |
| `front/src/types/task.d.ts` | 任务/派单类型与后端对齐 |
| `front/src/types/exception.d.ts` | 异常列表类型 |
| `front/src/layouts/BasicLayout.vue` | 工作台路由/导航 |
| `front/src/router/index.ts` | 工作台路由 |
| `front/src/components/workbench/ParkMiniMap.vue` | **新建** 工作台园区小地图（若有） |

### 6.2 后端 · 推送（协作者 B · P2-12）

| 路径 | 变更说明 |
|------|----------|
| `back/fsd-admin-api/.../FleetTelemetryStreamController.java` | **新建** SSE 端点 |
| `back/fsd-admin-api/.../FleetTelemetryStreamService.java` | **新建** 连接管理接口 |
| `back/fsd-admin-api/.../FleetTelemetryStreamServiceImpl.java` | **新建** 多连接广播 |
| `back/fsd-admin-api/.../FleetTelemetryScheduler.java` | **新建** 定时拉 `listVehicleSnapshots` 并 push |

### 6.3 后端 · 路网 / 派单 / 车位（后端 A · P2-01~11、P2-SLOT）

| 路径 | 变更说明 |
|------|----------|
| `back/sql/init/V7__*.sql` / `V8__*.sql` / `V9__*.sql` | 车位、充电会话、路网 |
| `back/fsd-dispatch/.../RoadNodeEntity.java` / `RoadSegmentEntity.java` | **新建** |
| `back/fsd-dispatch/.../RoadNodeMapper.java` / `RoadSegmentMapper.java` | **新建** |
| `back/fsd-dispatch/.../road/ParkRoadGraph.java` | **新建** 内存图 + DB/YAML 构建 |
| `back/fsd-dispatch/.../ParkRoutePlannerServiceImpl.java` | `buildRoute(parkId,…)`、`isReachable`；DB 优先 |
| `back/fsd-dispatch/.../ParkRoutePlannerService.java` | 接口增加 `parkId` |
| `back/fsd-dispatch/.../dispatch/DispatchVehicleAssignServiceImpl.java` | 评分选车 + `UNREACHABLE` |
| `back/fsd-dispatch/.../DispatchScoringProperties.java` | **新建** 权重配置 |
| `back/fsd-dispatch/.../ParkingFacilityServiceImpl.java` | `reserveSlot` / `reserveChargingSlot` / `releaseReservation` |
| `back/fsd-dispatch/.../ParkPilotSimulationServiceImpl.java` | 充电前 reserve；`WAIT_CHARGING` |
| `back/fsd-dispatch/.../ParkPilotServiceImpl.java` | 路径规划传入 `parkId` |
| `back/fsd-dispatch/.../ChargingSession*` / `ParkingSlot*` / `ChargingPile*` | P2-01~04 实体与服务 |
| `back/fsd-dispatch/vo/DispatchWorkbenchResponse.java` 等 | Workbench 聚合 VO |
| `back/fsd-common/.../DispatchAssignFailReason.java` 等枚举 | **新建** |
| `back/scripts/gen_v9_seed.py` | 从 `application.yml` 生成 V9 种子 |

### 6.4 后端 · 管理 API / 集成测试（共用）

| 路径 | 变更说明 |
|------|----------|
| `back/fsd-admin-api/.../AdminDispatchController.java` | workbench、auto-assign、查询增强 |
| `back/fsd-admin-api/.../AdminQueryFacadeServiceImpl.java` | 委派 dispatch 查询 |
| `back/fsd-admin-api/.../AdminTaskManualAssignRequest.java` | **新建** |
| `back/fsd-bootstrap/.../integration/IntegrationTestSchema.java` | **新建** H2 含 V7~V9 表 |
| `back/fsd-bootstrap/.../DispatchFlowIntegrationTest.java` | 使用共享 schema；Mock FleetRuntime |
| `back/fsd-dispatch/src/test/java/.../*Test.java` | 路网、派单、车位、Phase1 验收等单测 |

### 6.5 文档

| 路径 | 说明 |
|------|------|
| `docs/releases/phase2.md` | Phase 2 合入说明 |
| `docs/archive/roadmap-closed-20260527/` | 已关闭路线图归档 |
| `docs/acceptance/phase2.md` | Phase 2 联合验收 |
| `docs/integration/unreachable.md` | D1 · UNREACHABLE |
| `docs/integration/conflict.md` | D2 · 抢桩 / CONFLICT |
| `docs/DEPLOYMENT.md` | V7~V9 迁移命令 |

---

## 7. 配置说明

| 配置项 | 位置 | 说明 |
|--------|------|------|
| `fsd.park.road-nodes` / `road-segments` | `application.yml` | **无 DB 路网时 fallback**；有 V9 数据后以 DB 为准 |
| `fsd.dispatch.scoring.*` | `application.yml` | 派单距离/SOC/插枪加分权重 |
| `VITE_API_BASE_URL` | `front/.env` | 前端 API 与 SSE 基址 |

---

## 8. 本地启动与验证

```bash
# 基础设施
docker compose up -d

# 已有 MySQL 补迁移 V7/V8/V9（见 DEPLOYMENT.md）

# 后端
cd back && mvn -pl fsd-bootstrap -am spring-boot:run

# 前端
cd front && npm install && npm run dev
```

**自动化**

```bash
cd back
mvn -pl fsd-dispatch -am test -q
mvn -pl fsd-bootstrap -am test -q
cd front && npm run build
```

**手工（推荐顺序）** — 见 [../acceptance/README.md](../acceptance/README.md)

1. 工作台指标 + 自动派车文案（#2）  
2. 监控 STREAM + 延迟（#3）  
3. 断边 UNREACHABLE（#4 / D1）  
4. 多车抢桩（#1 / D2）  
5. Phase 1 手工 2 项  

**Stream 自检**

```bash
curl -N "http://localhost:8080/api/admin/fleet/telemetry/stream?parkId=1"
```

---

## 9. 建议 PR / 合并策略

| PR | 标题建议 | 包含 |
|----|----------|------|
| 1 | `P2-01~08 dispatch workbench foundation` | V7/V8、车位、评分派单、workbench API |
| 2 | `P2-09~11 road-network-v9` | V9、规划器 DB、UNREACHABLE |
| 3 | `P2-SLOT charging-reserve` | reserve / 仿真抢桩 |
| 4 | `P2-12~14 telemetry-workbench-ui` | SSE 后端 + 前端工作台/监控 |

若团队希望 **单次合并**，可用标题：`Phase 2: road network + fleet telemetry + workbench UI`，正文链到本文。

---

## 10. 合并后待办

- [ ] 全员执行 **V9**（及缺失的 V7/V8）  
- [ ] 走 [../acceptance/README.md](../acceptance/README.md) 并填写结论表  
- [ ] 协作者完成 P1-UI 两项截图  
- [ ] （可选）生产环境配置 CORS / 反向代理对 SSE 的长连接支持  
