# DispatchFlow 真实调度后台 — 路线总览（已关闭）

> **状态**：Phase 1 + Phase 2 **开发项已全部完成**（2026-05-27）  
> **后续**：本路线图目录已删除，Phase 3+ 由新路线图承接；验收入口见 [../acceptance/README.md](../acceptance/README.md)

---

## 文档导航（归档前）

| 文档 | 内容 |
|------|------|
| [../acceptance/README.md](../acceptance/README.md) | **验收总方案（当前主入口）** |
| [a-backend.md](./a-backend.md) | 后端 A · P2-09~11、充电位 |
| [b-frontend.md](./b-frontend.md) | 前端 B · P2-12~14、P1-UI |
| [phase2-release-notes.md](./phase2-release-notes.md) | Phase 2 合入说明（已迁移至 `../releases/phase2.md`） |

---

## 当前基线（已完成）

- [x] 多园区 `t_park / t_station`，订单 `park_id`
- [x] 园区监控、移动下单、自动派车、车辆回报闭环
- [x] 仿真电量 / 充电 / 插枪待命（`pluggedIn`）
- [x] 异常 OPEN 去重、派单 SOC 门槛
- [x] RabbitMQ + Outbox、操作日志
- [x] **Phase 1** 领域整合 P1-01~P1-22
- [x] **Phase 2** P2-01~P2-08：车位、充电会话、评分派单、workbench API
- [x] **Phase 2** P2-09~P2-SLOT：路网 DB、UNREACHABLE、充电位 RESERVED
- [x] **Phase 2** P2-12~P2-14：SSE 推送、监控订阅、工作台指标与可解释派单
- [x] 仿真派单后 busy 状态同步（`ParkPilotSimulationServiceImpl` 修复插枪待命车不出发）

---

## Phase 2 开发任务（全部完成）

### 2.1~2.2 基线

- [x] P2-01~P2-08 车位、充电会话、评分派单、workbench API

### 2.3 路网（后端 A）

- [x] **P2-09** SQL **V9**：`t_road_node` / `t_road_segment`
- [x] **P2-10** `ParkRoutePlannerService` 优先读 DB，YAML fallback
- [x] **P2-11** 派单前可达性 → `UNREACHABLE`
- [x] **P2-SLOT** 充电位 RESERVED / 多车抢桩冲突

### 2.4 实时推送与 UI（前端 B）

- [x] **P2-12** SSE：`/api/admin/fleet/telemetry/stream`
- [x] **P2-13** 监控页订阅，轮询兜底
- [x] **P2-14** 工作台 fleet 指标 + 派单可解释 UI
- [x] **P1-UI 开发** failReason 中文、拔枪逻辑、工作台手动派车优化（**手工验收**见 acceptance）

### Phase 2 验收标准（开发就绪，待走查）

| # | 标准 | 开发 | 手工验收 |
|---|------|------|----------|
| 1 | 多车抢同一充电位有冲突处理 | ✅ | ☐ [phase2.md](../acceptance/phase2.md) #1 |
| 2 | 派单结果可解释 | ✅ | ☐ [phase2.md](../acceptance/phase2.md) #2 |
| 3 | 监控页延迟 &lt; 2s（推送模式） | ✅ | ☐ [phase2.md](../acceptance/phase2.md) #3 |
| 4 | 路网改 DB，断边 UNREACHABLE | ✅ | ☐ [phase2.md](../acceptance/phase2.md) #4 |

---

## Phase 1 — 领域整合（已完成）

<details>
<summary>展开 P1-01 ~ P1-22 清单</summary>

### 1.1 Fleet 运行态抽象

- [x] **P1-01** ~ **P1-05** FleetRuntime、Redis、监控 API、单测

### 1.2 仿真 Adapter 化

- [x] **P1-06** ~ **P1-09** SimulationFleetAdapter、tick 解耦

### 1.3 充电与能量策略

- [x] **P1-10** ~ **P1-13** `fsd.fleet.energy.*`、ChargePolicy

### 1.4 异常与人工闭环

- [x] **P1-14** ~ **P1-18** 分级、resolve、MANUAL_PENDING 关联

### 1.5 调度工作台 MVP

- [x] **P1-19** ~ **P1-22** 工作台页、任务池、异常队列

### Phase 1 验收

- [x] 无订单：插枪待命、不掉电、可派单（自动化 + Redis 抽检）
- [x] 有订单：拔枪出发（逻辑已测 + 仿真 sync 修复）
- [x] 派单失败：MANUAL_PENDING + 单条 OPEN（单测已覆盖）
- [x] 仿真离线不产生 OPEN
- [x] 重启后 Redis 恢复运行态

**手工目视**（截图）：见 [../acceptance/p1-ui-checklist.md](../acceptance/p1-ui-checklist.md)

</details>

---

## Phase 3 — 真实车端接入（已完成 · 2026-05-29）

**目标**：仿真可关，真实 AGV 可上报、可收指令。

- [x] **P3-01** ~ **P3-05** 车端网关、上报幂等、指令下发
- [x] **P3-06** ~ **P3-08** 双模式 SIM / REAL
- [x] **P3-09** ~ **P3-10** 车端鉴权、管理端 RBAC

**验收**：关闭仿真后用 mock 车端完成一单；指令失败进异常；SIM/REAL 同页展示。

| # | 标准 | 开发 | 手工验收 |
|---|------|------|----------|
| 1 | 关闭仿真，REAL 车 mock 客户端完成一单 | ✅ | ☐ |
| 2 | 指令失败产生 COMMAND_FAILED 异常 | ✅ | ☐ |
| 3 | 监控页 SIM/REAL 同屏展示 | ✅ | ☐ |

**验收入口**：`back/scripts/mock_vehicle_client.py`；网关 `/api/vehicle-gateway/**`；配置 `fsd.security.*`

---

## Phase 4 — 运营与扩展（未开始 · 待新路线图）

**目标**：多园区、多客户、长期运维。

- [ ] **P4-01** ~ **P4-04** 优先级、批量派单、动态重规划、策略热更新
- [ ] **P4-05** ~ **P4-07** traceId、指标、告警
- [ ] **P4-08** ~ **P4-10** Webhook、OpenAPI、WMS 映射
- [ ] **P4-11** ~ **P4-13** 车辆详情、园区配置编辑、报表

**验收**：500+ 单压测 P99 &lt; 500ms；UI 异常闭环；新园区 SQL + 配置 onboarding。

---

## 全局里程碑

```
[✅] Phase 1 领域整合
[✅] Phase 2 路网 + 推送 + 工作台
[☐] Phase 2 联合验收  ← 当前（acceptance/README.md）
[✅] Phase 3 车端
[ ] Phase 4 运营       ← 待新路线图
```

---

## SQL 版本约定

| 版本 | 内容 | 状态 |
|------|------|------|
| V7 | 车位 / 充电桩 | ✅ |
| V8 | 充电会话 | ✅ |
| V9 | 路网节点 / 边 | ✅ |
| V10 | 车端网关 link_mode / command / credential | ✅ |

---

## 变更记录

| 日期 | 说明 |
|------|------|
| 2026-05-27 | **路线图关闭**：Phase 1~2 开发完成；目录归档后删除，Phase 3+ 重新设计 |
| 2026-05-27 | 修复仿真派单后 STANDBY 车不出发（busy sync） |
| 2026-05-27 | 文档整理：`acceptance/`、`integration/`、`archive/` |
| 2026-05-25 | 初版 Phase 1–4 清单 |
