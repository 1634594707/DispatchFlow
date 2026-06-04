# DispatchFlow 文档索引

> 更新：2026-05-31 · 验收入口见 **[acceptance/README.md](./acceptance/README.md)**

---

## 路线图与需求（当前）

| 文档 | 说明 |
|------|------|
| **[ROADMAP-V4.md](./ROADMAP-V4.md)** | **当前产品路线图**（技术债 · OSM 站点 · 工作台态势 · 移动下单） |
| [ROADMAP-V3.md](./ROADMAP-V3.md) | V3 已关闭 → 跳转 V4 |
| [archive/ROADMAP-V3-closed.md](./archive/ROADMAP-V3-closed.md) | V3 完整归档快照 |
| [REQUIREMENTS-DESIGN.md](./REQUIREMENTS-DESIGN.md) | 新需求设计模板 |
| [v3/README.md](./v3/README.md) | V3 专题（地图 · MAPF） |
| [v4/V4-R7-CLOSURE.md](./v4/V4-R7-CLOSURE.md) | V4-R7 贴路验收关闭 |
| [v4/PARK-OVERVIEW-DEMO.md](./v4/PARK-OVERVIEW-DEMO.md) | 大屏官方演示链路（V4-O7） |
| [archive/ROADMAP-V2-closed.md](./archive/ROADMAP-V2-closed.md) | V2 已交付摘要 |

---

## 部署与运维

| 文档 | 说明 |
|------|------|
| [DEPLOYMENT.md](./DEPLOYMENT.md) | Docker / 本地启动、SQL 迁移 |
| **[UPDATE-OPERATIONS.md](./UPDATE-OPERATIONS.md)** | **云服务器 Docker 更新操作手册** |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 模块划分、Fleet 模型、事件流 |
| [SECURITY-AUDIT.md](./SECURITY-AUDIT.md) | 安全基线 checklist |

---

## Release Notes

| 文档 | 说明 |
|------|------|
| [releases/v3.0.0.md](./releases/v3.0.0.md) | **v3.0.0** — Phase 12–15（Latest） |
| [releases/v2.0.0.md](./releases/v2.0.0.md) | v2.0.0 — Phase 10–11 |
| [releases/phase2.md](./releases/phase2.md) | Phase 2 合入说明 |

---

## V3 评估

| 文档 | 说明 |
|------|------|
| [v3/AMAP-SETUP.md](./v3/AMAP-SETUP.md) | **高德 Key 配置与 PoC 验证** |
| [v3/MAP-PROVIDER-EVALUATION.md](./v3/MAP-PROVIDER-EVALUATION.md) | 真实地图选型（结论：高德） |
| [v3/MAPF-EVALUATION.md](./v3/MAPF-EVALUATION.md) | MAPF 实时避障架构 |

---

## 验收

| 文档 | 范围 |
|------|------|
| [acceptance/README.md](./acceptance/README.md) | 验收总方案 |
| [acceptance/phase1.md](./acceptance/phase1.md) | Phase 1 |
| [acceptance/phase2.md](./acceptance/phase2.md) | Phase 2 |
| [acceptance/p1-ui-checklist.md](./acceptance/p1-ui-checklist.md) | 工作台 / 监控目视 |

---

## 联调参考

| 文档 | 场景 |
|------|------|
| [integration/unreachable.md](./integration/unreachable.md) | 断边 → `UNREACHABLE` |
| [integration/conflict.md](./integration/conflict.md) | 抢桩 / `CONFLICT` |
| [integration/telemetry-stream.md](./integration/telemetry-stream.md) | SSE 推送契约 |
| [integration/fleet-runtime.md](./integration/fleet-runtime.md) | Redis 运行态字段 |

---

## 性能

| 文档 | 说明 |
|------|------|
| [perf/navigation-baseline.md](./perf/navigation-baseline.md) | 导航重构性能基线 |
| [perf/navigation-regression.md](./perf/navigation-regression.md) | 导航回归对比 |

---

## 归档

历史 MVP、V2 Phase 评估、已完成专项路线图等见 **[archive/](./archive/)**。

```
archive/
  ROADMAP-V2-closed.md       V2 交付摘要
  phase15/                   V2 阶段评估（VDA5050）
  roadmap-closed-20260527/   Phase 1–2 路线图
  mvp/                       早期 MVP 设计稿
  park-pilot/                园区仿真需求
  legacy-docs/               旧版 handoff
  front-prompts/             前端提示词草稿
  misc/                      其他笔记
```
