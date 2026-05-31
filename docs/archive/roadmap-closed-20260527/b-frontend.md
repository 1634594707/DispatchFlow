# 协作者路线 — B（推送 + 前端 + 联调）

> 协作者文档：[a-backend.md](./a-backend.md)  
> 总览索引：[overview.md](./overview.md) · 验收：[../acceptance/README.md](../acceptance/README.md)  
> 更新：2026-05-27

---

## 1. 我负责什么

| 范围 | 任务编号 | 验收项 |
|------|----------|--------|
| 工作台指标 + 派单反馈 UI | P2-14 | Phase 2 验收 #2 |
| 实时推送 | P2-12 | — |
| 监控页订阅 | P2-13 | Phase 2 验收 #3 |
| Phase 1 手工验收收尾 | P1-UI | [../acceptance/p1-ui-checklist.md](../acceptance/p1-ui-checklist.md) |

**我不做**：路网 SQL（P2-09）、规划器（P2-10）、可达性后端（P2-11）、充电位 RESERVED（P2-SLOT）、`ParkRoutePlannerServiceImpl`。

---

## 2. 文件归属（避免冲突）

### 仅我改

```
front/src/views/workbench/Index.vue
front/src/views/vehicle/Tracking.vue
front/src/stores/workbench.ts
front/src/api/dispatch.ts          （仅新增 stream 客户端时可改）
front/src/constants/statusMap.ts   （failReasonCode 中文）
front/src/types/*.d.ts             （stream 类型）

back/fsd-admin-api/.../FleetTelemetryStreamController.java   （新建，名称可自定）
back/fsd-admin-api/.../FleetTelemetryStreamService.java      （新建）
back/fsd-dispatch/.../FleetSnapshotAssembler.java            （若需小改，勿动派单/路网）
```

### 只读、不改

```
ParkRoutePlannerServiceImpl.java
ParkingFacilityServiceImpl.java      （充电位 RESERVED 归后端 A）
DispatchVehicleAssignServiceImpl.java
back/sql/init/V9__*.sql
```

### 后端 API 已就绪（直接对接，无需等 A）

| API | 方法 | 用途 |
|-----|------|------|
| `/api/admin/dispatch/workbench` | GET | 任务池 + `fleetMetrics` + `parkLayout` + `vehicles` |
| `/api/admin/tasks/{id}/auto-assign` | POST | 自动派车；成功见 `assignExplanation`；失败见 `failReasonCode` |
| `/api/admin/park/vehicles` | GET | 轮询兜底；payload 类型与 stream 保持一致 |

---

## 3. 阶段路线

```
阶段 0  [已完成]  P2-08 workbench API；store 已拉 fleetMetrics 但未展示
    │
阶段 1  [1.5 天]  P2-14 + failReason UI + Phase 1 手工验收
    │                    │
    │                    └──► 【对接点 E0】仅用现有 API，不等 A
    │
阶段 2  [1.5 天]  P2-12 SSE/WS stream
    │                    │
    │                    └──► 【对接点 E1】stream 自测通过即可上线 P2-13
    │
阶段 3  [1 天]    P2-13 Tracking 订阅 + 延迟验证
    │
阶段 4  [0.5 天]  【对接点 E2】等 A 的 P2-11 → 联调 UNREACHABLE 中文
    │
阶段 5  [缓冲]    验收截图、workbench 优化（可选）
```

**与 A 并行规则**：阶段 1~3 **完全不依赖** A 的路网 PR；阶段 4 才需要 A 的 D1 文档。

---

## 4. 任务清单

### 阶段 1 — 工作台 + Phase 1 联调（P2-14 / P1-UI）

#### P2-14 · 工作台 fleet 指标 `[x]`

**现状**：`workbench.ts` 已从 workbench API 写入 `assignableVehicleCount` / `pluggedStandbyCount` / `chargingCount`，**header 未展示**。

**交付**

- [x] `Index.vue` header 增加：可派车 / 插枪待命 / 充电中（绑定 store 三个 ref）
- [x] 可选：展示 `fleetMetrics.onlineVehicleCount`

**完成标准**：刷新工作台可见指标数字与监控页车辆状态一致。

**对接**：【对接点 E0】**开工即可做**，无需等 A。

---

#### 派单可解释 UI `[x]`

**交付**

- [x] 自动派车**成功**：`message.success` 或卡片展示 `assignExplanation`、`selectedVehicleCode`
- [x] 自动派车**失败**：展示 `failReasonCode` 中文（见下表）
- [x] `statusMap.ts` 增加 `DISPATCH_FAIL_REASON` 映射

| failReasonCode | 中文 |
|----------------|------|
| `NO_VEHICLE` | 无可用车辆 |
| `LOW_SOC` | 电量不足 |
| `UNREACHABLE` | 取货点不可达 |
| `CONFLICT` | 占车/占桩冲突 |

**完成标准**：故意无可用车时看到「无可用车辆」；成功派车看到评分说明。

**对接**：`UNREACHABLE` / `CONFLICT` 的**真实触发**分别等对接点 E2 / A 的 D2；映射可先写好，用 mock 或 `NO_VEHICLE` 先验 UI。

---

#### Phase 1 手工验收 2 项 `[~]`

见 [../acceptance/p1-ui-checklist.md](../acceptance/p1-ui-checklist.md)（工作台路径）与 [../acceptance/phase1.md](../acceptance/phase1.md)：

- [ ] **有订单 · 拔枪出发**：监控页目视确认
- [ ] **自动派单失败 · 单条 OPEN**：工作台 + 查库确认

**完成标准**：验收表两项从 `☐` 改为 `✅`，附截图或录屏。

---

### 阶段 2 — 实时推送（P2-12）

#### P2-12 · telemetry stream `[x]`

**交付**

- [x] `GET /api/admin/fleet/telemetry/stream`（SSE 推荐；若 WS 需在 PR 说明）
- [x] 定时（如 1s）调用 `ParkPilotService.listVehicleSnapshots(parkId)` 推送
- [x] Payload 契约（见 [p2-12-telemetry-stream.md](./p2-12-telemetry-stream.md)）：

```json
{
  "parkId": 1,
  "ts": "2026-05-27T10:00:00+08:00",
  "vehicles": [ /* ParkVehicleSnapshotResponse[] */ ]
}
```

- [x] PR 描述：URL、鉴权、断线重连、curl 示例（见 [p2-12-telemetry-stream.md](./p2-12-telemetry-stream.md)）

**约束**

- **不修改** `DispatchVehicleAssignService`、`ParkRoutePlannerService`、`ParkingFacilityService`
- 新建类放 `fsd-admin-api` 独立包，便于 review

**完成标准**：curl / 浏览器 EventSource 能持续收到 JSON；字段与 `GET /api/admin/park/vehicles` 一致。

**对接**：【对接点 E1】P2-12 合并后即可开始 P2-13；**通知 A「stream 已上线」即可（B1），A 无需改代码**。

---

### 阶段 3 — 监控订阅（P2-13）

#### P2-13 · Tracking.vue 订阅 `[x]`

**前置**：P2-12 已部署且 URL 稳定。

**交付**

- [x] `Tracking.vue`：优先 EventSource 订阅 stream
- [x] 断线指数退避重连；stream 不可用时 fallback 原轮询
- [x] 监控页展示推送延迟（`Tracking.vue` header）；环境走查见 [../acceptance/phase2.md](../acceptance/phase2.md) §#3

**完成标准**：仿真 tick 后监控页 2s 内更新；关 stream 后轮询仍可用。

---

### 阶段 4 — 与 A 联调（可选并行于阶段 3 之后）

#### UNREACHABLE / CONFLICT 真实场景 `[~]`

**前置**：A 已提供 D1/D2 文档。

**交付**

- [ ] 按 [../integration/unreachable.md](../integration/unreachable.md) 断边，工作台显示「取货点不可达」
- [ ] （可选）按 [conflict-integration.md](./conflict-integration.md) 验证「占车/占桩冲突」

**对接**：【对接点 E2】按 [../acceptance/phase2.md](../acceptance/phase2.md) 联合走查。

---

### 阶段 5 — 可选优化

- [x] workbench 使用 API 返回的 `parkLayout` / `vehicles`（`workbench.ts` + `ParkMiniMap`）
- [x] header 含「在线车辆」；手动派车优先用 workbench `vehicles` 列表

---

## 5. 对接点（协作者 ↔ 后端 A）

| 编号 | 方向 | 触发时机 | 做什么 |
|------|------|----------|--------|
| **E0** | 独立 | **立即** | 用现有 workbench / auto-assign API 做 P2-14、Phase 1 |
| **E1** | 我 → A | P2-12 合并 | 告知 stream URL（B1）；A 无需动作 |
| **E2** | A → 我 | 收到 **D1 文档** | 联调 `UNREACHABLE` 真实场景 |
| **E3** | 联合 | Phase 2 验收前 | 与 A 一起走验收 #1~#4 |

### 我什么时候需要等 A？

| 任务 | 是否等 A |
|------|----------|
| P2-14 指标展示 | **不等** |
| failReason 映射 + NO_VEHICLE / LOW_SOC UI | **不等** |
| Phase 1 手工验收 | **不等** |
| P2-12 stream | **不等** |
| P2-13 订阅 | **不等**（只等自己的 P2-12） |
| UNREACHABLE 真实联调 | **等** A 的 P2-11 + D1 文档 |
| CONFLICT 真实联调 | **等** A 的 P2-SLOT（可选） |

---

## 6. 建议 PR 顺序

| 顺序 | PR | 内容 | 依赖 |
|------|-----|------|------|
| 1 | `P2-14 workbench-metrics` | header 指标 + assignExplanation | 无 |
| 2 | `P1-UI acceptance` | Phase 1 手工 2 项 | 无 |
| 3 | `P2-12 telemetry-stream` | SSE 后端 | 无 |
| 4 | `P2-13 tracking-subscribe` | 前端订阅 | PR 3 |
| 5 | `P2-14 failReason-e2e` | UNREACHABLE 联调补截图 | A 的 D1 |

1 和 2 可同一个 PR。

---

## 7. 每日计划（参考）

| 天 | 任务 | 是否需 A |
|----|------|----------|
| D1 | P2-14 指标 + failReason UI + Phase 1 验收 | 否 |
| D2 | P2-12 stream 开发与自测 | 否 |
| D3 | P2-13 Tracking 订阅 + 延迟测试 | 否 |
| D4 | 若 A 已合 P2-11：E2 UNREACHABLE 联调 | 是（D1 文档） |
| D5 | 验收截图、可选 workbench 优化 | 可选 |

---

## 8. Definition of Done（协作者）

- [x] 工作台 header 展示可派车 / 插枪待命 / 充电中
- [x] 自动派车成功/失败有可读反馈
- [ ] Phase 1 手工验收 2 项有截图
- [x] stream 可用，Tracking 订阅 + 轮询兜底
- [x] 推送延迟 UI 已就绪（监控页显示 ms）；环境验收见 [../acceptance/phase2.md](../acceptance/phase2.md) §#3
- [ ] `UNREACHABLE` 联调通过（[../integration/unreachable.md](../integration/unreachable.md)）
- [ ] PR 标题含 `P2-xx`

---

## 9. 本地开工

```bash
docker compose up -d
cd back && mvn -pl fsd-bootstrap -am spring-boot:run
cd front && npm run dev
```

**前端入口文件**

- `front/src/views/workbench/Index.vue`
- `front/src/stores/workbench.ts`
- `front/src/views/vehicle/Tracking.vue`
- `front/src/constants/statusMap.ts`
- `front/src/api/dispatch.ts`

**类型参考**

- `DispatchTaskAssignResponse`：`assignExplanation`, `failReasonCode`, `selectedVehicleCode`, `assignScore`
- `WorkbenchResponse`：`fleetMetrics`, `intervention`, `parkLayout`, `vehicles`
