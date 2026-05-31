# V2 产品路线图（已关闭）

> **状态**：✅ 已交付 · GitHub Release [`v3.0.0`](../releases/v3.0.0.md)  
> **当前路线图**：[`ROADMAP-V3.md`](../ROADMAP-V3.md)

---

## 已交付阶段（Phase 1–15）

| 阶段 | 里程碑 | 亮点 |
|------|--------|------|
| Phase 11 | M1 | 全站园区一致、VIEWER 只读、派车可解释、交通可行动 |
| Phase 12 | M2 | 轨迹回放、引擎仿真、路网基础、配置向导 |
| Phase 13 | M3 | Webhook 日志、PDF/定时邮件、链路 KPI、2FA |
| Phase 14 | M4–M5 | 家纺垂直、路网拖拽、运维视图、换电仿真 |
| Phase 15 | M6 | REAL 换电、VDA5050 MQTT MVP、任务池分页、Redis 批量读 |

## 已偿还技术债务

| 类别 | 项 |
|------|-----|
| 架构 | 版本 0.3.0 · Flyway baseline V20 · Swagger `@Tag` 全覆盖 |
| 性能 | Redis `multiGet` · Vite `manualChunks` · SSE 防泄漏 |
| 质量 | 自动化规则/换电单测 · 集成测试修复 · ESLint + Prettier · JaCoCo |
| 安全 | [`SECURITY-AUDIT.md`](../SECURITY-AUDIT.md) 基线 checklist |
| 运维 | Prometheus actuator · CI lint + JaCoCo 产物 |

## 相关文档

| 文档 | 说明 |
|------|------|
| [releases/v2.0.0.md](../releases/v2.0.0.md) | Phase 10–11 Release |
| [releases/v3.0.0.md](../releases/v3.0.0.md) | Phase 12–15 Release |
| [archive/phase15/VDA5050-EVALUATION.md](./phase15/VDA5050-EVALUATION.md) | VDA5050 适配评估 |
