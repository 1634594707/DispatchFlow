# DispatchFlow 产品路线图

> **待办清单**：仅保留未完成项（`[ ]`）。Phase 1–14 核心能力已上线，见 [在线演示](https://www.aplicity.online)。  
> **文档创建**：2026-05-29  
> **最后更新**：2026-05-31  
> **调研范围**：经纬恒润、新石器 Neolix、Cyngn Insight、Meili FMS、Geek+ 极智嘉、海康机器人、快仓 Quicktron、Forza Fleet Manager、AWS IoT RoboRunner、Open-RMF、**找家纺 × 新石器**

---

## 一、行业对标与差距

| 平台 | 核心特色 | 落点 Phase |
|------|---------|------------|
| **经纬恒润** | 灰度策略、交通调度、作业引擎 | 11–12 ✅ |
| **新石器 Neolix** | RMS 大规模车队、语音/批量 | 12、14 ✅ |
| **Cyngn Insight** | IF-THEN 自动规则（低电回充） | 14 ✅（REAL 车队待 15） |
| **Meili FMS** | 2FA、紧急停止、权限 UI 一致 | 11、13 ✅ |
| **Geek+ 极智嘉** | RMS 混编、MAPF 避堵 | 混编 11–14 ✅ · MAPF → 15 |
| **海康机器人** | AI 预测派车 | 14+ |
| **快仓 Quicktron** | 引擎级仿真 | 12 ✅ |
| **Forza** | VDA5050、MES、轨迹分析 | 轨迹 12–13 ✅ · VDA5050 → 15 |
| **AWS RoboRunner** | 站点级数字孪生 | 12 ✅ |
| **Open-RMF** | 区域限行、交通协商 | 11 ✅ |
| **找家纺 × 新石器** | 线路、枢纽、母港分流、拥堵预警、换电、旺季 | 11–14 ✅ |

### 家纺垂直 — 找家纺 × 新石器

| 维度 | 差距 | Phase |
|------|------|-------|
| 规模 | 线路 CRUD + 拖拽路网 MVP | 14 ✅ |
| 枢纽 | 容量 + 分流 + 运维视图 | 14 ✅ |
| 精细化调度 | 拥堵可处置 + 规则 evaluate | 14 ✅（REAL 低电/换电规则 ✅） |
| 能量 | 充电/换电 runtime + 策略配置 | 14 ✅（REAL 换电会话 ✅ · 换电柜管理 ✅） |
| 管控 | FIELD_OPS 工单 + 大屏运维视图 | 14 ✅ |
| 开放 | 调用统计/投递日志 UI | 13 ✅ |
| 旺季 | 高峰模式 + 对比报表 | 14 ✅（cron 自动切换 ✅） |

**定位**：DispatchFlow = FMS 调度中台；不复制找家纺分拣线 WCS，补齐调度层即可。

---

## 二、现状摘要

**已具备（Phase 1–14）**：核心调度、基础设施 UI（含路网拖拽 MVP）、SSE 告警、运营分析（PDF/定时邮件/链路 KPI/高峰对比）、多园区与策略配置、Open API/Webhook、规则型快捷指令、数字孪生态势、轨迹回放（含异常停留标注）、系统健康、RBAC + TOTP 2FA、家纺垂直（线路/枢纽/旺季/换电仿真/自动化规则/FleetAdapter 注册表）。

**Phase 14 收尾项**（MVP 已 demo，以下待 Phase 15 深化）见第三节。

---

## 三、待办路线图

### Phase 15 — 垂直深化 · 开放协议 · 规模化

#### 15.1 垂直深化（Phase 14 收尾）

_已完成（2026-05-31）_：REAL 换电会话（`RealFleetSwapCoordinator`）、REAL 低电/换电自动化规则（`FleetAutomationScheduler`）。

#### 15.2 开放协议

_已完成（2026-05-31）_：VDA5050 MQTT 适配器 MVP、车辆 VDA5050 配置 UI、Mosquitto 联调栈 + 模拟 AGV 脚本。详见 [`docs/phase15/VDA5050-EVALUATION.md`](./phase15/VDA5050-EVALUATION.md)。

#### 15.3 规模化

- [ ] 1000+ 车 MAPF 实时（需架构升级：图分区 / 预约表 / 外置求解器）
- [ ] Redis 热点预加载（Fleet 位置批量读）

_已完成（2026-05-31）_：任务池 DB 索引（V20）、服务端分页（`POST /admin/dispatch/task-pool/query`）、工作台加载更多。

---

## 四、明确不做 / 延后

- [ ] 大模型调度助手（本阶段；用规则 + IF-THEN 替代）
- [ ] 分拣线 / NC 交叉带 WCS（非 FMS 边界）
- [ ] 真 3D / VR 数字孪生
- [ ] 强化学习派车（缺数据）
- [ ] 深度 ERP 单据同步（先 Webhook + 模板）

---

## 五、技术债务

### 架构
- [ ] 版本号统一（`front/package.json` 0.1.0 vs `CHANGELOG.md` 0.2.0）
- [ ] 引入 Flyway 替代手动 SQL（现 V01–V17 手动迁移）
- [ ] Swagger 注解补全（springdoc 已配置，Controller 基本无注解）

### 性能
- [ ] Redis 热点预加载
- [ ] 前端代码分割与懒加载（路由已 lazy，缺 Vite `manualChunks`）
- [ ] SSE 连接池防泄漏

### 代码质量
- [ ] 单测覆盖率 → 80%
- [ ] 关键流程集成测试（垂直/换电/规则 evaluate）
- [ ] ESLint + Prettier / Checkstyle + SpotBugs

### 安全
- [ ] 敏感数据加密
- [ ] SQL/XSS 审计

### 运维
- [ ] ELK 日志聚合
- [ ] Prometheus + Grafana
- [ ] Zipkin / Jaeger 链路追踪
- [ ] CI/CD 优化

---

## 六、里程碑与成功指标

| 里程碑 | Phase | 目标 | 验收 |
|--------|-------|------|------|
| **M1** | 11 | +2 周 | 园区一致；VIEWER 零误点；拥堵可处置 | ✅ |
| **M2** | 12 | +5 周 | 4h 配园区；引擎仿真；轨迹回放 | ✅ |
| **M3** | 13 | +7 周 | Webhook 可查；PDF；链路 KPI | ✅ |
| **M4** | 14 | +10 周 | 线路+枢纽+旺季 demo | ✅ 2026-05-30 |
| **M5** | 14+ | +12 周 | 路网拖拽；换电；2FA；运维视图 | ✅ 2026-05-31 |
| **M6** | 15 | +14 周 | VDA5050 demo；垂直收尾；100 车稳定 | 15.1 ✅ · 15.2 ✅ 2026-05-31 |

---

## 七、参考与代码锚点

**技术栈**：Java 21 · Spring Boot 3.3 · MySQL · Redis · RabbitMQ · Vue 3 · TS · Leaflet · SSE · Docker。

| 主题 | 路径 |
|------|------|
| FleetAdapter | `back/fsd-dispatch/.../fleet/FleetAdapterRegistry.java` |
| 自动化规则 | `DispatchAutomationRuleServiceImpl.java` |
| 高峰模式 | `PeakModeServiceImpl.java` · `PeakModeCronScheduler.java` |
| 换电 REAL | `RealFleetSwapCoordinator.java` · `RealFleetAdapter.java` |
| VDA5050 MQTT | `fleet/vda5050/Vda5050MqttGateway.java` · `Vda5050FleetAdapter.java` |
| 换电柜管理 | `SwapCabinetList.vue` · `/infrastructure/swap-cabinets` |
| 现场工单 | `FieldOpsTicketAdminServiceImpl.java` |
| 运维快照 | `OpsSnapshotAdminServiceImpl.java` |
| 定时邮件 | `ReportEmailScheduler.java` |
| 2FA | `AdminAuthServiceImpl.java` |
| 路网拖拽 | `front/.../ParkInfraPreview.vue` |
| 垂直 UI | `front/src/views/vertical/` |
| VDA5050 评估 | `docs/phase15/VDA5050-EVALUATION.md` |

---

**维护**：项完成后从本文删除对应 `- [ ]` 行。PR 前缀：`[Phase15]`、`[Vertical-找家纺]`。
