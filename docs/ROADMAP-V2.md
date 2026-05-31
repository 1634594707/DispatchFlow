# DispatchFlow 产品路线图

> **待办清单**：仅保留未完成项（`[ ]`）。Phase 1–15 核心能力已上线，见 [在线演示](https://www.aplicity.online)。  
> **文档创建**：2026-05-29  
> **最后更新**：2026-05-31  
> **新需求设计入口**：[`REQUIREMENTS-DESIGN.md`](./REQUIREMENTS-DESIGN.md)

---

## 一、Phase 15 收尾状态 ✅

| 子项 | 状态 | 文档 / 锚点 |
|------|------|-------------|
| 15.1 REAL 换电 / 低电规则 | ✅ | `RealFleetSwapCoordinator` · `FleetAutomationScheduler` |
| 15.2 VDA5050 MQTT MVP | ✅ | [`phase15/VDA5050-EVALUATION.md`](./phase15/VDA5050-EVALUATION.md) |
| 15.3 任务池分页 / Redis 批量读 | ✅ | V20 · `task-pool/query` |
| 15.3 MAPF 1000+ 车 | 📋 评估完成，实现 → Phase 16 | [`phase15/MAPF-EVALUATION.md`](./phase15/MAPF-EVALUATION.md) |

---

## 二、Phase 16 候选（待需求设计）

> 在 [`REQUIREMENTS-DESIGN.md`](./REQUIREMENTS-DESIGN.md) 中填写后拆任务。

- [ ] MAPF 实时避障（图分区 + 时空预约表 + 可选外置求解器）
- [ ] 单测覆盖率 → 80%（JaCoCo 报告已接入 CI）
- [ ] 字段级敏感数据加密（TOTP / Webhook secret）
- [ ] ELK / Zipkin 全链路（Prometheus + Grafana 可选栈已提供）

---

## 三、明确不做 / 延后

- [ ] 大模型调度助手（规则 + IF-THEN 替代）
- [ ] 分拣线 / NC 交叉带 WCS
- [ ] 真 3D / VR 数字孪生
- [ ] 强化学习派车
- [ ] 深度 ERP 单据同步

---

## 四、技术债务 — 已偿还（2026-05-31）

| 类别 | 项 |
|------|-----|
| 架构 | 版本 0.2.0 · Flyway baseline V20 · Swagger `@Tag` 全覆盖 |
| 性能 | Redis `multiGet` · Vite `manualChunks` · SSE 防泄漏 |
| 质量 | 自动化规则单测 · 换电单测 · 集成测试 mail 修复 · ESLint + Prettier · JaCoCo |
| 安全 | [`SECURITY-AUDIT.md`](./SECURITY-AUDIT.md) 基线 checklist |
| 运维 | Prometheus actuator · [`docker-compose.observability.yml`](../back/docker-compose.observability.yml) · CI lint + JaCoCo 产物 |

**仍可选**：Checkstyle / SpotBugs 静态规则（`mvn -Pquality` 待加）。

---

## 五、里程碑

| 里程碑 | Phase | 目标 | 验收 |
|--------|-------|------|------|
| **M6** | 15 | VDA5050 · 垂直收尾 · 100 车稳定 | ✅ 2026-05-31 |
| **M7** | 16 | MAPF MVP · 覆盖率 80% | 待需求设计 |

---

## 六、代码锚点

**技术栈**：Java 21 · Spring Boot 3.3 · MySQL · Redis · RabbitMQ · Vue 3 · TS · Leaflet · SSE · Docker · Flyway · Prometheus。

| 主题 | 路径 |
|------|------|
| FleetAdapter | `back/fsd-dispatch/.../fleet/FleetAdapterRegistry.java` |
| 自动化规则 | `DispatchAutomationRuleServiceImpl.java` |
| VDA5050 | `fleet/vda5050/Vda5050MqttGateway.java` |
| 路网 / 规划 | `ParkRoutePlannerServiceImpl.java` |
| 需求模板 | `docs/REQUIREMENTS-DESIGN.md` |
| 安全基线 | `docs/SECURITY-AUDIT.md` |

---

**维护**：Phase 16 项定稿后从「候选」迁入正式章节。PR 前缀：`[Phase16]`、`[Vertical-找家纺]`。
