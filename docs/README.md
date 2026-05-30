# DispatchFlow 文档索引

> 更新：2026-05-30 · 验收入口见 **[acceptance/README.md](./acceptance/README.md)**

## 常用（先看这些）

| 文档 | 说明 |
|------|------|
| **[acceptance/README.md](./acceptance/README.md)** | **验收总方案**（环境、顺序、勾选表） |
| [DEPLOYMENT.md](./DEPLOYMENT.md) | Docker / 本地启动、SQL 迁移 V01–V14 |
| [ARCHITECTURE.md](./ARCHITECTURE.md) | 模块划分、Fleet 模型、事件流 |
| [ROADMAP-V2.md](./ROADMAP-V2.md) | **产品路线图**（Phase 11 M1 已完成，Phase 12–14 待办） |
| [releases/phase2.md](./releases/phase2.md) | Phase 2 合入说明与文件清单 |

## 验收细则

| 文档 | 范围 |
|------|------|
| [acceptance/phase1.md](./acceptance/phase1.md) | Phase 1 自动化 + 手工项 |
| [acceptance/phase2.md](./acceptance/phase2.md) | Phase 2 四项标准 + P1 收尾 |
| [acceptance/p1-ui-checklist.md](./acceptance/p1-ui-checklist.md) | 工作台 / 监控目视步骤（截图清单） |

## 联调参考

| 文档 | 场景 |
|------|------|
| [integration/unreachable.md](./integration/unreachable.md) | 断边 → `UNREACHABLE` |
| [integration/conflict.md](./integration/conflict.md) | 抢桩 / `CONFLICT` |
| [integration/telemetry-stream.md](./integration/telemetry-stream.md) | SSE 推送契约 |
| [integration/fleet-runtime.md](./integration/fleet-runtime.md) | Redis 运行态字段说明 |

## 归档

历史 MVP 草稿、**已关闭的 Phase 1–2 路线图**、园区 Pilot 需求等已移至 **[archive/](./archive/)**。

```
archive/
  roadmap-closed-20260527/   Phase 1–2 路线图终稿（开发已完成，待新路线图）
  mvp/                       早期 fsd-core-mvp 设计稿
  park-pilot/                园区仿真需求与坐标说明
  legacy-docs/               旧版 handoff / release-checklist
  front-prompts/             前端 AI 提示词草稿
  misc/                      其他一次性笔记
```

**Phase 3+ 路线图**：见 [ROADMAP-V2.md](./ROADMAP-V2.md)。
