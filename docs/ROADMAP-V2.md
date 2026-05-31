# DispatchFlow 产品路线图

> **V2（Phase 1–15）已交付** · [在线演示](https://www.aplicity.online)  
> **V3 待办**见第二节 · 新需求设计：[`REQUIREMENTS-DESIGN.md`](./REQUIREMENTS-DESIGN.md)  
> **最后更新**：2026-05-31

---

## 一、V2 已交付（Phase 1–15）✅

| 阶段 | 里程碑 | 亮点 |
|------|--------|------|
| Phase 11 | M1 | 全站园区一致、VIEWER 只读、派车可解释、交通可行动 |
| Phase 12 | M2 | 轨迹回放、引擎仿真、路网基础、配置向导 |
| Phase 13 | M3 | Webhook 日志、PDF/定时邮件、链路 KPI、2FA |
| Phase 14 | M4–M5 | 家纺垂直（线路/枢纽/旺季/规则/换电仿真）、路网拖拽、运维视图 |
| Phase 15 | M6 | REAL 换电会话、VDA5050 MQTT MVP、任务池分页、Redis 批量读 |

**Release**：[`v3.0.0`](./releases/v3.0.0.md)（GitHub Tag `v3.0.0`）

---

## 二、V3 待办路线图

> 以下项归入 **V3** 版本规划；定稿后写入 [`REQUIREMENTS-DESIGN.md`](./REQUIREMENTS-DESIGN.md) 并拆 Sprint。

### 2.1 规模化

- [ ] MAPF 实时避障（图分区 + 时空预约表 + 可选外置求解器）— 架构见 [`phase15/MAPF-EVALUATION.md`](./phase15/MAPF-EVALUATION.md)

### 2.2 质量与安全

- [ ] 单测覆盖率 → 80%（JaCoCo 报告已接入 CI）
- [ ] 字段级敏感数据加密（TOTP / Webhook secret）
- [ ] Checkstyle / SpotBugs 静态规则（`mvn -Pquality`）

### 2.3 可观测性

- [ ] ELK 日志聚合
- [ ] Zipkin / Jaeger 全链路追踪  
  _已有：Prometheus + Grafana 可选栈 [`docker-compose.observability.yml`](../back/docker-compose.observability.yml)_

### 2.4 明确不做 / 延后

- [ ] 大模型调度助手
- [ ] 分拣线 / NC 交叉带 WCS
- [ ] 真 3D / VR 数字孪生
- [ ] 强化学习派车
- [ ] 深度 ERP 单据同步

---

## 三、V2 技术债务 — 已偿还

| 类别 | 项 |
|------|-----|
| 架构 | 版本 0.3.0 · Flyway baseline V20 · Swagger `@Tag` 全覆盖 |
| 性能 | Redis `multiGet` · Vite `manualChunks` · SSE 防泄漏 |
| 质量 | 自动化规则/换电单测 · 集成测试修复 · ESLint + Prettier · JaCoCo |
| 安全 | [`SECURITY-AUDIT.md`](./SECURITY-AUDIT.md) 基线 checklist |
| 运维 | Prometheus actuator · CI lint + JaCoCo 产物 |

---

## 四、代码锚点

**技术栈**：Java 21 · Spring Boot 3.3 · MySQL · Redis · RabbitMQ · Vue 3 · TS · Leaflet · SSE · Docker · Flyway · Prometheus · VDA5050 MQTT。

| 主题 | 路径 |
|------|------|
| FleetAdapter | `fleet/FleetAdapterRegistry.java` |
| VDA5050 | `fleet/vda5050/Vda5050MqttGateway.java` |
| 自动化规则 | `DispatchAutomationRuleServiceImpl.java` |
| 换电 REAL | `RealFleetSwapCoordinator.java` |
| 需求模板 | `docs/REQUIREMENTS-DESIGN.md` |

---

**维护**：V3 项完成后从第二节删除对应 `- [ ]` 行。PR 前缀：`[V3]`、`[Vertical-找家纺]`。
