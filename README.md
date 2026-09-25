# DispatchFlow

[![CI](https://github.com/1634594707/DispatchFlow/actions/workflows/ci.yml/badge.svg)](https://github.com/1634594707/DispatchFlow/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](back/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.12-6DB33F.svg)](back/pom.xml)
[![Vue](https://img.shields.io/badge/Vue-3.5-42B883.svg)](front/package.json)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.7-3178C6.svg)](front/package.json)
[![version](https://img.shields.io/badge/version-0.6.0-blue.svg)](CHANGELOG.md)

DispatchFlow 是面向园区短驳配送场景的无人车调度平台。前端提供调度工作台、车辆监控、移动下单与运营分析界面；后端基于 Spring Boot 多模块组织订单、车辆、调度与园区设施。

在线演示：[aplicity.online](https://www.aplicity.online)

<p align="center">
  <a href="docs/DispatchFlow_演示与配置优化任务路线图_2026-09-25.md">
    <img src="https://img.shields.io/badge/Project%20Summary-Read%20Docs-22D3EE?style=for-the-badge&logo=readthedocs&logoColor=white" alt="项目文档"/>
  </a>
</p>

> 📘 **`docs/` 下只剩一份活文档**：[演示与配置优化任务路线图](docs/DispatchFlow_演示与配置优化任务路线图_2026-09-25.md)（**还做什么**，每项带证据出处、改动点、可执行验收闸门）。
> 早期的《项目全面总结》《运维手册》×2《坐标基准》《视觉规范》已在 2026-09-22 的文档收敛中删除；《调度算法与地理收敛任务路线图》《部署整改任务路线图》《已完成工作记录》三份已于 2026-09-25 一并退场，**其结论仍可在 git 历史里按文件路径查到**（`git log --diff-filter=D -- docs/`）。部署与运维口径以 `scripts/deploy.sh` 为准。引用上述已删文件的注释与脚本由守卫 `node scripts/check-doc-links.mjs` 兜住。

## 目录

- [功能范围](#功能范围)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [项目结构](#项目结构)
- [快速启动](#快速启动)
- [开发命令](#开发命令)
- [质量门禁](#质量门禁)
- [预测能力与已知局限](#预测能力与已知局限)
- [接口与入口](#接口与入口)
- [版本与变更](#版本与变更)
- [文档与治理](#文档与治理)

## 功能范围

| 模块 | 说明 |
| --- | --- |
| 订单与任务 | 移动端下单、订单查询、调度任务创建、任务列表与详情、改派与取消 |
| 调度工作台 | 任务池、自动派车、手动派车、改派、批量撤销、异常重新派单、暂停派单全局开关 |
| 车辆管理 | 车辆列表、车辆详情、车辆回报、8 状态运行态监控（在线/配送/前往充电/装卸/等待/充电/偏离/离线/手动接管） |
| 园区基础设施 | 园区、站点（A/B/C 可信度）、服务位、路网（等级/通行语义/时间窗）、地理围栏、停车位、充电桩、换电柜 |
| 路线安全 | 起终点吸附、车辆包络碰撞校验、建筑 Polygon 障碍物膨胀、路线审计、路线健康指标 |
| 能源与异常 | 充电会话、换电会话、异常记录、异常处置、告警聚合 |
| 运营分析 | 运营概览、充电报表、自定义报表、报表历史、调度助手 |
| 系统管理 | 登录认证、用户管理、系统健康、集成配置、操作日志、通知设置 |
| GIS 总览 | L0/L1/L2 三层地图（产业带 10km / 试点 14 / 站点 16）、数字孪生轨迹回放 |
| Fleet 集成 | SIM / REAL 车辆链路、VDA5050 MQTT 配置与模拟脚本 |

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 前端 | Vue 3.5、TypeScript 5.7、Vite 6、Ant Design Vue 4.2、Pinia、Vue Router、高德地图 JS API、Leaflet、Axios、@fontsource 自托管字体、vite-plugin-pwa、Capacitor 6 |
| 后端 | Java 21、Spring Boot 3.3.12、MyBatis-Plus、SpringDoc OpenAPI、Lombok、MapStruct、Flyway |
| 数据与消息 | MySQL、Redis、RabbitMQ、Mosquitto MQTT |
| 工程化 | Maven 多模块、ESLint、Prettier、vue-tsc、Playwright、JaCoCo、Checkstyle、SpotBugs |
| 部署与运维 | Docker Compose、GitHub Actions、GitHub Container Registry、Prometheus、Filebeat |

## 系统架构

```text
┌──────────────────────┐
│ front Vue SPA        │
│ 管理端 / 监控 / 移动端 │
└──────────┬───────────┘
           │ HTTP / SSE
┌──────────▼───────────┐
│ fsd-admin-api        │
│ 管理端聚合 API        │
└──────────┬───────────┘
           │
┌──────────▼────────────────────────────────────────────┐
│ back Maven modules                                     │
│ fsd-order / fsd-vehicle / fsd-dispatch / fsd-common    │
└──────────┬──────────────────────┬─────────────────────┘
           │                      │
┌──────────▼───────────┐  ┌───────▼────────┐
│ MySQL                │  │ Redis          │
│ 业务状态与 Flyway 迁移 │  │ Fleet 运行态    │
└──────────────────────┘  └───────┬────────┘
                                  │
                         ┌────────▼────────┐
                         │ RabbitMQ / MQTT │
                         │ 事件与车辆集成    │
                         └─────────────────┘
```

**状态边界（改代码前先看这条）**：Redis 只放**运行态缓存**，12 个写点全部带 TTL 且可从 MySQL 重建；
MySQL 是真相。唯一例外是车辆运行态里的**实时轨迹与规划线**（`trajectory` / `plannedRouteGeo` /
`routeSource` / `routeInvalid`）—— 它们**只活在这份缓存里，是易失视图**：Redis flush 后轨迹线与
规划线在地图上消失，派单不受影响；位姿与 SOC 有 `t_vehicle` 兜底可回读。
所以地图上的轨迹线不是历史存档，要回放请走轨迹接口（`t_fleet_telemetry_point`，由保留期任务按天分批清理）。

**副本模型：单副本 —— 不要横向扩。**
这不是没做完的高可用，而是当前设计的前提。`docker-compose.prod.yml` 的 `backend` 服务没有 `replicas` /
`--scale` 配置（compose 默认一个容器），依据是四处实例内状态（命令可复现）：

- **13 个 `@Scheduled` 调度器没有任何分布式锁**（全仓 `shedlock` 大小写不敏感检索 **0** 处）——两副本会各跑一遍超时扫描、保留期清理、高峰翻档、围栏自检与邮件报表。
- `fsd-dispatch/fleet/simulation/SimulationMotionStore` 的仿真运动状态是 **JVM 内 `ConcurrentHashMap`**，不落 Redis ⇒ 第二个实例看不到车走到哪一步。
- 实时推送是 **5 个类持有的 `SseEmitter`**（`AdminDispatchStreamController` / `AdminStreamController` / `FleetTelemetryStreamController` 与两个服务实现，另有 2 个接口声明），连接是实例本地的；LB round-robin 会把工作台切成"半死不活"。
- `MobileOrderAuthServiceImpl` 的限流窗口也在 JVM 内 ⇒ 副本数直接乘倍允许速率。

**派单本身**是安全的：并发保护走每任务 Redis 锁（fail-closed），MySQL 为真相。真要主张水平扩展，前置工作是「给 13 个调度器加 ShedLock + 仿真运动状态外置 + SSE 改 Redis pub/sub 广播」，而不是加副本。

**未接线的实验件（读代码前先看这段，别按"已上线"理解）**：

| 组件 | 状态 | 可复现判据 |
| --- | --- | --- |
| `dispatch/geo/GeoQueryService`、`GeoServiceClient`、`GeoServiceProperties` | **读侧空间查询，零生产调用方** | `grep -rn "GeoServiceProperties" --include=*.java back/` 只命中 `geo/` 包内三个类自身与 `GeoQueryServiceTest`；包外无任何注入点 |
| PostGIS 读侧服务（`geo-py/`，默认 `http://127.0.0.1:8090`） | **默认关闭** | `application.yml` 的 `fsd.geo-service.enabled: ${FSD_GEO_SERVICE_ENABLED:false}`；开启后失败自动降级回 Java 手算（含 200 ms 超时与连续 3 次熔断） |
| 等价性证明 | **代码里有，本机跑不了**（诚实边界） | `geo-py/tests/test_java_parity.py`：每张真实围栏按 60×60 网格逐点比 Java 与 PostGIS 的内外判断。但 `conftest.py` 依赖 `psycopg_pool`，本机未安装即 `ModuleNotFoundError`（`tests/test_spatial.py` 同样失败 ⇒ 与仓库改动无关）；`test_java_parity.py` **需要 `pip install -e geo-py[dev]` + 起 `fsd-geo-postgis` 才能真跑**，本次没有 live 证据。夹具本身是"连不上就整组跳过"，不会假绿 |

这三件是**故意留在旁路**的：把 PostGIS 开进派单热路径是明确列出的**非目标**（见路线图的"不做什么"清单），依据是实测对比 —— 现役 13 个站点做线性扫描 p95 **0.057 ms**，而走 PostGIS 是 **1.71–2.28 ms**，交叉点在 **N≈5,000** 才出现（`back/fsd-dispatch/.../geo/DispatchGeoDistanceService` 的手算路径吃内存图，派单预算里没有一次跨进程查询的位置 —— 该预算按仪表分两档：H2 合成基准 P95 **277 ms**，本机真 MySQL 实测 P95 **696.5 ms** [95% CI 497.8–895.3]、P50 179.7 ms，两者不可混引）。要转正只有一条路：**接进围栏并补 N≥5,000 的合成规模压测**，压不过就删。在那之前，任何"我们用了 PostGIS/GeoTools 做空间查询"的表述都必须带"未接线 / 规模预案"这几个字。

后端入口模块为 `fsd-bootstrap`。业务模块通过 Maven Reactor 一起构建，启动和测试命令必须使用 `-pl fsd-bootstrap -am`，确保依赖模块按当前源码参与构建。

## 项目结构

```text
DispatchFlow/
├── back/                         后端 Maven 多模块工程
│   ├── fsd-common/               公共枚举、异常、响应模型、安全配置
│   ├── fsd-order/                订单域
│   ├── fsd-vehicle/              车辆域
│   ├── fsd-dispatch/             调度、Fleet、园区设施、事件、地理与 MAPF
│   ├── fsd-admin-api/            管理端聚合 API
│   ├── fsd-bootstrap/            Spring Boot 启动模块
│   ├── sql/init/                 数据库初始化入口
│   ├── sql/migrations/           Flyway 迁移脚本（V1 → V47）
│   ├── mqtt/                     Mosquitto 配置
│   └── observability/            Prometheus / Filebeat 配置
├── front/                        Vue 管理端与监控前端
│   ├── android/                  Capacitor Android 移动端工程
│   ├── src/api/                  前端 API 封装
│   ├── src/components/           通用与业务组件（含移动端组件）
│   ├── src/views/                页面视图（含移动端页面）
│   ├── src/stores/               Pinia 状态管理
│   ├── src/router/               路由配置
│   ├── public/icons/             车辆 8 状态与站点分类 SVG 图标
│   ├── scripts/e2e/              Playwright 端到端测试
│   └── scripts/perf/             性能与导航测试脚本
├── data/                         OSM、CARLA 与园区地理数据
├── docs/                         项目路线图、运维手册与治理文档
├── scripts/                      根级开发、验收、CARLA 与部署脚本
│   ├── carla/                    CARLA 仿真相关脚本
│   ├── dev/                      开发辅助脚本
│   ├── deploy.sh                 生产部署入口脚本
│   └── scan_encoding.py          UTF-8 编码扫描
├── .github/workflows/            CI、镜像发布、Release 工作流
├── docker-compose.yml            根级本地编排入口
├── docker-compose.prod.yml       生产编排（含 fsd-backend / fsd-frontend）
├── CHANGELOG.md                  版本变更记录
├── CONTRIBUTING.md               贡献说明
├── SECURITY.md                   安全说明
└── LICENSE                       MIT License
```

## 快速启动

### 环境要求

| 依赖 | 版本 |
| --- | --- |
| JDK | 21 |
| Maven | 3.9+ |
| Node.js | 20+ |
| Docker | 24+ |

### 1. 启动后端与基础设施

```bash
docker compose up -d
```

默认后端 API 地址为 `http://localhost:8080`。

如需本地源码方式启动后端：

```bash
cd back
mvn -pl fsd-bootstrap -am clean install -DskipTests
mvn -pl fsd-bootstrap spring-boot:run
```

### 2. 启动前端

```bash
cd front
npm install
npm run dev
```

默认前端地址为 `http://localhost:3000`，开发代理将 `/api` 转发到后端服务。

### 3. 登录信息

系统启动后，使用以下默认账号密码登录：

| 角色 | 用户名 | 密码 | 权限 |
| --- | --- | --- | --- |
| 系统管理员 | admin | admin123 | 完整权限，包括用户管理、系统配置、数据管理 |
| 调度员 | operator | operator123 | 调度操作权限，包括派车、改派、异常处理 |
| 观察员 | viewer | viewer123 | 只读权限，可查看监控和报表 |

> **注意**：生产环境请务必修改默认密码！登录后可在系统管理页面修改密码。

### 4. 生产部署

完整流程以 `scripts/deploy.sh` 为准（上线前置、Flyway 校验、RabbitMQ 变量名等阻断项都在脚本里逐段注释）；发布产物由 `.github/workflows/release.yml` 打包（会把本 `docs/` 整个带进 zip）。核心步骤：

```bash
cd /opt/dispatchflow
cp .env.production .env
chmod 600 .env
bash scripts/deploy.sh
docker compose -f docker-compose.prod.yml ps
curl -fsS http://127.0.0.1:8080/internal/actuator/health
```

## 开发命令

### 后端

| 命令 | 说明 |
| --- | --- |
| `mvn -pl fsd-bootstrap -am test` | 运行后端测试 |
| `mvn -pl fsd-bootstrap -am verify -Pquality` | 运行后端质量门禁（Checkstyle / SpotBugs / JaCoCo） |
| `mvn -pl fsd-bootstrap spring-boot:run` | 启动后端应用 |

### 前端

| 命令 | 说明 |
| --- | --- |
| `npm run dev` | 启动 Vite 开发服务 |
| `npm run build` | TypeScript 检查并构建生产包 |
| `npm run typecheck` | 仅运行 TypeScript 检查 |
| `npm run lint` | 运行 ESLint |
| `npm run format:check` | 检查 Prettier 格式 |
| `npm run test:e2e` | 运行 Playwright 端到端测试（`scripts/e2e`） |
| `npm run perf:nav` | 运行导航性能测试 |
| `npm run perf:lighthouse` | 运行 Lighthouse 路由性能脚本 |

## 质量门禁

GitHub Actions 已配置两条主要流水线：

| Job | 关键步骤 |
| --- | --- |
| Backend Tests | JDK 21、`mvn -pl fsd-bootstrap -am test -B`、`mvn -pl fsd-bootstrap -am verify -Pquality -B`、上传 JaCoCo 报告 |
| Frontend Build | Node.js 20、`npm ci`、`npm run lint -- --max-warnings 50`、`npm run build`、安装 Playwright Chrome、运行 `npm run test:e2e` |

本地提交前建议至少运行：

```bash
cd front
npm run lint -- --max-warnings 50
npm run typecheck

cd ../back
mvn -pl fsd-bootstrap -am test
```

## 预测能力与已知局限

补能需求预测（ALG-FC，`scripts/ml/`）刻意把"能做到什么"和"做不到什么"都摆出来，不藏短板：

- **分位数 P90 覆盖率 0.7945（79.45%）**，低于自设的 0.8 警戒线（`reports/energy_forecast_report.md`，xgboost 后端、288/73 时间序切分）。含义：**用 P90 做充电桩容量规划会系统性低估尾部需求** —— 这是已知、可复现、被明说的局限，不是隐藏项。
- **贝叶斯线性回归基线 vs GBM 同题对照**（`scripts/ml/bayesian_baseline_forecast.py`、`reports/energy_forecast_bayesian_baseline.md`）：同一 holdout 上线性基线的**中心趋势（P50 MAE 7.90）反而优于分位数 GBM（8.78）**，但**尾部分位（P90）GBM 明显更准**（覆盖率 0.84 vs 线性高斯区间的过宽欠校准）。结论是分工而非替代：**均值可用线性，做容量约束的 P90 上界必须用分位数回归**。
- **数据面限制**：特征集是小样本合成/导出（60 天 × 少量站点），且园区**没有真实车辆遥测、没有真实高峰可标定**（`t_charging_session` 仅 3 行、遥测表为空），因此所有"完成率/覆盖率"只读作**同一参数集下的相对排序**，不代表生产绝对精度。**另有一条时效限制**：`reports/energy_features.csv` 的行是仿真在旧的每 1% SOC 行驶里程常数（比真车规格低一个数量级）下写入的，且"补能到达率"由 `空闲即补能` 这条策略语义主导 —— 两者都待定稿后重导出，故**本节上面两条数字（P90 覆盖率 0.7945、P50 MAE 7.90/8.78）目前是历史口径，不能当现值引**。
- **可复现性**：`reports/energy_forecast_bayesian_baseline.md` 可 `python scripts/ml/bayesian_baseline_forecast.py` 一条命令重跑（纯 sklearn/scipy，无需 xgboost）。

## 接口与入口

| 类型 | 地址 |
| --- | --- |
| 前端首页 | `http://localhost:3000` |
| 调度工作台 | `http://localhost:3000/workbench` |
| 调度看板 | `http://localhost:3000/dashboard` |
| 园区 GIS 总览 | `http://localhost:3000/gis/park` |
| 车辆监控 | `http://localhost:3000/vehicle-tracking` |
| 移动下单 | `http://localhost:3000/mobile/order` |
| 异常管理 | `http://localhost:3000/exceptions` |
| 数字孪生 | `http://localhost:3000/digital-twin` |
| API 文档 | `http://localhost:8080/swagger-ui.html` |
| OpenAPI JSON | `http://localhost:8080/api-docs` |

## 配置说明

| 文件 | 说明 |
| --- | --- |
| `.env.example` | 根级环境变量示例 |
| `.env.production` | 生产环境变量模板（部署时复制为 `.env`） |
| `front/.env.example` | 前端环境变量示例 |
| `back/fsd-bootstrap/src/main/resources/application.yml` | 后端主配置 |
| `back/docker-compose.yml` | 后端目录内 Docker Compose 配置 |
| `back/docker-compose.mqtt.yml` | MQTT 相关 Compose 配置 |
| `back/docker-compose.observability.yml` | 可观测性相关 Compose 配置 |
| `back/docker-compose.ghcr.yml` | 镜像发布部署相关 Compose 配置 |

不要提交真实密钥、账号密码或本地 `.env` 文件。

## 版本与变更

当前版本：**0.6.0**（2026-07-19）。

本版本聚焦 ZJF 基地单一原点收敛、园区配置校正与端到端测试修复：

- **V44–V47 Flyway 迁移**：扩展 ZJF 实际服务范围、清理历史演示数据、重置基地车辆坐标、修复零距离路线污染
- **E2E 测试修复**：ant-design-vue 4.x Modal 选择器与 zhCN locale 适配，CI 全绿
- **前端视觉规范**：车辆 8 状态与站点 5 类 SVG 图标资源补齐
- **文档治理**：新增 11 份项目治理与运维文档，删除冗余 Python 部署脚本

完整变更记录见 [CHANGELOG.md](CHANGELOG.md)。

## 文档与治理

| 文件 | 说明 |
| --- | --- |
| [back/README.md](back/README.md) | 后端模块、启动、测试与 Docker 说明 |
| [front/README.md](front/README.md) | 前端页面、开发、构建与环境说明 |
| [docs/DispatchFlow_演示与配置优化任务路线图_2026-09-25.md](docs/DispatchFlow_演示与配置优化任务路线图_2026-09-25.md) | 当前唯一的活任务清单：每项带证据出处、改动点与可执行验收闸门 |
| [CHANGELOG.md](CHANGELOG.md) | 版本变更记录 |
| [CONTRIBUTING.md](CONTRIBUTING.md) | 贡献流程 |
| [SECURITY.md](SECURITY.md) | 安全问题报告方式 |

## License

[MIT License](LICENSE) © 2026 DispatchFlow Contributors
