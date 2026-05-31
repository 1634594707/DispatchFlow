# 我的路线 — 后端 A（路网 + 充电冲突）

> 协作者文档：[b-frontend.md](./b-frontend.md)  
> 总览索引：[overview.md](./overview.md) · 验收：[../acceptance/README.md](../acceptance/README.md)  
> 更新：2026-05-27

---

## 1. 我负责什么

| 范围 | 任务编号 | 验收项 |
|------|----------|--------|
| 路网入库 + 规划器 | P2-09 ~ P2-10 | Phase 2 验收 #4 |
| 派单可达性 | P2-11 | 与 `UNREACHABLE` 对齐 |
| 充电位冲突 | P2-SLOT（扩展） | Phase 2 验收 #1 |

**我不做**：SSE/WebSocket（P2-12）、前端任何页面、监控订阅（P2-13）、工作台 UI（P2-14）。

---

## 2. 文件归属（避免冲突）

### 仅我改

```
back/sql/init/V9__road_network.sql
back/fsd-dispatch/.../RoadNodeEntity.java
back/fsd-dispatch/.../RoadSegmentEntity.java
back/fsd-dispatch/.../RoadNodeMapper.java
back/fsd-dispatch/.../RoadSegmentMapper.java
back/fsd-dispatch/.../ParkRoutePlannerServiceImpl.java
back/fsd-dispatch/.../DispatchVehicleAssignServiceImpl.java  （P2-11 连通性部分）
back/fsd-dispatch/.../ParkingFacilityServiceImpl.java        （充电位 RESERVED 部分）
docs/DEPLOYMENT.md                                           （V9 迁移说明）
```

### 只读、不改

```
ParkPilotService / listVehicleSnapshots          ← 协作者 P2-12 用
AdminDispatchController.getDispatchWorkbench     ← 协作者 P2-14 用
front/**                                         ← 全部归协作者
fsd-admin-api 下除 dispatch 查询外的推送相关      ← 协作者新建
```

### 共用但各改各段

| 文件 | 我改什么 | 协作者改什么 |
|------|----------|--------------|
| `DispatchTaskServiceImpl.java` | 可达性调用、reserve 逻辑 | **不改** |
| `application.yml` | 路网 seed 导出参考 | **不改** |

---

## 3. 阶段路线

```
阶段 0  [已完成]  P2-01~08 车位 / 派单 / workbench API
    │
阶段 1  [3 天]    P2-09 → P2-10 → P2-11  路网 + 可达性
    │                    │
    │                    └──► 【对接点 D1】P2-11 合并后通知协作者
    │
阶段 2  [2 天]    充电位 RESERVED / 多车抢桩
    │                    │
    │                    └──► 【对接点 D2】可选联调：CONFLICT 失败码 UI
    │
阶段 3  [缓冲]    CR、帮协作者验证 UNREACHABLE、Phase 2 验收 #1 #4
```

---

## 4. 任务清单

### 阶段 1 — 路网（P2-09 ~ P2-11）

#### P2-09 · SQL V9 + 种子数据 `[x]`

**交付**

- [x] `back/sql/init/V9__road_network.sql`
  - 表 `t_road_node`：`park_id`, `node_code`, `coord_x`, `coord_y`, `status`
  - 表 `t_road_segment`：`park_id`, `from_node_code`, `to_node_code`（无向，服务层双向建图）
- [x] 默认园区 `park_id=1` seed（从 `application.yml` 的 `road-nodes` / `road-segments` 导出）
- [x] `RoadNodeEntity` / `RoadSegmentEntity` + Mapper
- [x] `DEPLOYMENT.md` 增加 V9 手动执行命令（格式同 V7/V8）

**完成标准**：空库 / 已有 V8 库均可执行 V9；`mvn -pl fsd-dispatch -am compile` 通过。

**对接**：本阶段 **无需** 与协作者联调；合并后群里发一句「V9 已合，请执行迁移」即可。

---

#### P2-10 · 规划器读 DB `[x]`

**交付**

- [x] `ParkRoutePlannerServiceImpl`：**DB 有该 park 路网 → 只用 DB**；无数据 → fallback YAML
- [x] 单测：DB 有数据时不读 YAML；DB 无数据时 YAML 仍可用

**完成标准**：去掉 YAML 路网配置、仅保留 SQL seed，路径规划仍正常。

**对接**：仍 **无需** 协作者参与。

---

#### P2-11 · 可达性 → UNREACHABLE `[x]`

**交付**

- [x] `DispatchVehicleAssignService`：取货点不在路网连通分量内 → `DispatchAssignFailReason.UNREACHABLE`
- [x] 单测 / 集成测：人为 `DELETE` 一条 `t_road_segment` 后自动派车返回 `UNREACHABLE`
- [x] 写清 **UNREACHABLE 复现步骤**（见 [../integration/unreachable.md](../integration/unreachable.md)）

**完成标准**：`mvn -pl fsd-dispatch -am test` 全绿。

---

### 阶段 2 — 充电位冲突（P2-SLOT）

#### 充电位 RESERVED / 多车抢桩 `[x]`

**交付**

- [x] `ParkingFacilityService.reserveSlot(parkId, vehicleId, slotCode)` / `releaseReservation(vehicleId)`
- [x] 仿真 `routeToCharging` 前 reserve；冲突时换下一桩或等待
- [x] 冲突继续用 `PARKING_SLOT_CONFLICT`；派单侧已有 `CONFLICT` 枚举可复用
- [x] 单测：两辆车同时抢同一 `FREE` 桩，仅一辆成功 reserve

**完成标准**：Phase 2 验收 #1「多车抢同一充电位有冲突处理」可演示。

**对接**：【对接点 D2】若协作者已合入 failReason UI，可一起验证 `CONFLICT` 中文展示；**不阻塞** 协作者主线。

---

## 5. 对接点（我 → 协作者）

| 编号 | 触发时机 | 我交付什么 | 协作者用来做什么 |
|------|----------|------------|------------------|
| **D0** | 开工当天 | 无 | 双方各自开发，**零依赖** |
| **D1** | **P2-11 PR 合并后** | UNREACHABLE 复现文档 + `failReasonCode=UNREACHABLE` 确认 | 工作台自动派车失败时展示「取货点不可达」 |
| **D2** | 充电位 PR 合并后（可选） | [../integration/conflict.md](../integration/conflict.md) | 验证 `CONFLICT` 中文 + 验收 #1 |
| **D3** | Phase 2 验收前 | [../acceptance/phase2.md](../acceptance/phase2.md) | 联合走完整 Phase 2 验收清单 |

### 对接点 D1 交付物（P2-11 合并时必须给）

```markdown
## UNREACHABLE 联调步骤

1. 执行 V9 迁移
2. 确认取货站点 node 在路网中：SELECT * FROM t_road_node WHERE park_id=1;
3. 断边：DELETE FROM t_road_segment WHERE park_id=1 AND from_node_code='...' AND to_node_code='...';
4. 创建订单并 POST /api/admin/tasks/{id}/auto-assign
5. 期望：HTTP 200 + body.failReasonCode = "UNREACHABLE"
6. 恢复：重新执行 V9 或 INSERT 该 segment
```

---

## 6. 协作者 → 我的对接（我仅消费、不阻塞）

| 编号 | 触发时机 | 内容 | 我需要做什么 |
|------|----------|------|--------------|
| **B1** | 协作者 P2-12 合并 | stream URL + payload 示例 | **无需改代码**；知晓即可 |
| **B2** | 协作者 P2-14 合并 | 工作台 UI 截图 | 帮看 `assignExplanation` 文案是否可读 |

**重要**：协作者的 P2-12~14 **不依赖** 我的 P2-09~11，我可以慢于或快于协作者，互不等。

---

## 7. 建议 PR 顺序

| 顺序 | PR | 内容 | 合并后动作 |
|------|-----|------|------------|
| 1 | `P2-09 road-v9` | SQL + Entity/Mapper | 通知协作者执行 V9 |
| 2 | `P2-10 planner-db` | 规划器读 DB | — |
| 3 | `P2-11 reachability` | UNREACHABLE | **发对接点 D1 文档** |
| 4 | `P2-SLOT reserve` | 充电位冲突 | 可选发 D2 |

可将 1~3 合并为一个 PR，按团队习惯。

---

## 8. 每日计划（参考）

| 天 | 任务 | 是否需对接 |
|----|------|------------|
| D1 | P2-09 V9 SQL + seed + Entity | 否 |
| D2 | P2-10 Planner 读 DB + 单测 | 否 |
| D3 | P2-11 可达性 + 单测 + **D1 文档** | **是**（发文档即可，不必开会） |
| D4 | 充电位 RESERVED | 否 |
| D5 | [../acceptance/phase2.md](../acceptance/phase2.md) 联合走查 #1~#4 | 与协作者联调 |

---

## 9. Definition of Done（我）

- [x] V9 迁移文档在 `DEPLOYMENT.md`
- [x] 仅 SQL 路网、无 YAML 仍可规划
- [x] 断边后派单返回 `UNREACHABLE`（有测例）
- [x] 多车抢桩有 RESERVED 或换桩逻辑
- [x] `mvn -pl fsd-dispatch -am test` 通过
- [x] 对接点 D1 文档已发给协作者（见 [../integration/unreachable.md](../integration/unreachable.md)）

---

## 10. 本地开工

```bash
docker compose up -d
# 已有库需手动 V7/V8/V9，见 DEPLOYMENT.md
cd back && mvn -pl fsd-bootstrap -am spring-boot:run
```

PR 标题请带 `P2-09` / `P2-11` / `P2-SLOT` 便于总览勾选。
