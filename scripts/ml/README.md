# scripts/ml — 补能需求预测（ALG-FC）

离线训练与导入链路，产出 `t_energy_forecast`，供 Java 侧 `EnergyForecastService` 读取，
用于**返充时机错峰**与站点容量判断。

## 链路

```
t_charging_session ──export_energy_features.sql──▶ energy_features.csv
                                                        │
                                    energy_demand_forecast.py（分位数回归 P50/P90 + 滑窗 P95 压力）
                                                        │
                        ┌───────────────────────────────┼───────────────────────────────┐
                        ▼                               ▼                               ▼
        energy_forecast_predictions.csv   energy_forecast_result.sql      energy_forecast_report.md
                        │                               │
                        └──▶ 导入 t_energy_forecast ────┘
```

## 用法

```bash
# 0) 依赖（隔离 venv，勿污染全局环境）
python -m venv .venv-ml
.venv-ml/Scripts/python -m pip install xgboost pandas scikit-learn

# 1) 从数据库导出特征（只读查询）
mysql -h127.0.0.1 -P3306 -uroot -p fsd_core --batch --raw --default-character-set=utf8mb4 \
  -e "source scripts/ml/export_energy_features.sql" > reports/energy_features.csv

# 注意：mysql 的 --batch 模式固定以 Tab 作列分隔符，导出的文件虽名为 .csv 实为 TSV。
#       load_features() 对两种分隔符都做兼容（先试逗号，缺列则自动探测重读）。

# 2) 训练 + 评估 + 产出导入 SQL
.venv-ml/Scripts/python scripts/ml/energy_demand_forecast.py --input reports/energy_features.csv

# 无历史数据时（链路自检 / 演示）：固定种子的仿真数据，报告中标注 synthetic
.venv-ml/Scripts/python scripts/ml/energy_demand_forecast.py --synthesize --days 60 --stations 4

# 3) 导入预测
mysql -h127.0.0.1 -P3306 -uroot -p fsd_core < reports/energy_forecast_result.sql

# 4) 生产日作业：上面 1)–3) 一次跑完（推荐入口，供 cron / 自动化调用）
#    凭证只从环境变量读取，不落盘
FSD_PROD_HOST=<host> FSD_PROD_PASS=<pwd> \
  python scripts/ml/refresh_energy_forecast.py --forecast-days 2
#    → 导出 → 训练 → 落表 → 按服务读取口径自校验（service_visible=0 时退出码 2）
```

> **落库日期契约（重要）**：`EnergyForecastServiceImpl` 按 `forecast_date = CURDATE()` +
> `hour_of_day = HOUR(NOW())` 读取，而模型产出是**逐小时需求剖面**。因此剖面必须盖在
> **它将被消费的那天**上——只盖生成当天会让预测在**午夜立即失效**
> （实测 23:50 生成、00:01 即 `service_visible=0`，服务静默回退阈值策略）。
> `--forecast-days 2` 会同时盖「今天 + 明天」，使日作业无论跑在午夜前还是午夜后都不留空档。

## 运行状态怎么判断（可观测性）

预测失效在 2026-09-21 之前是**静默**的：`parkPressure()` 对「开关关闭 / 表里没有行 / 缺当前小时 /
已超期 / 确实空闲」一律返回 0，服务只是安静地按纯阈值策略跑。现在这五种情形各有名字并可被告警：

| 出口 | 内容 |
| --- | --- |
| 计数器 `dispatchflow.energy_forecast.availability{park,state}` | `state ∈ {DISABLED, NO_ROWS, NOT_THIS_HOUR, STALE, FRESH}`，每次解析 +1。持续非 `FRESH` ⇒ 日作业没跑 |
| 计数器 `dispatchflow.energy_forecast.flat_profile{park}` | 绝对阈值过了、但当日剖面太平（见下），单独一档，不与 `FRESH` 混 |
| WARN 日志（同一 park+state 限速 2 分钟） | 内容即 `ForecastStatus.explain()`，直接写明根因与最新 `generated_at` |
| `refresh_energy_forecast.py` 退出码 | `0` 成功 / `2` 当前小时没有可见预测（`REFRESH_WARN`）；cron 里 `|| ` 即可告警 |

日作业本身**仍未调度**：它需要 SSH 进生产并从本机 `.venv-ml` 调 xgboost，注册计划任务属于
部署动作。在注册之前，`NO_ROWS`/`STALE` 这两条信号就是判断「上次跑成功没有」的唯一途径。

### 为什么加了 `min-peak-pressure-ratio`（1.5）

`pressure_threshold=2.0` 的单位是「该站该小时开始的充电会话数」。真实规模下 2.0 合理，
但现有这份剖面（2026-09-17 生产库导出）是 **150 次/小时的平地**：

| 实测 | 值 |
| --- | --- |
| 特征小时到达均值 / CV | 150.1 / 0.06 |
| 观测剖面峰谷比 | 1.08（154.3 @ h7 vs 143.1 @ h1） |
| 模型 `pressure_p95` 极差 | 152.7 ~ 159.85，峰/中位 = 1.02 |
| 阈值 ≤152.7 时触发小时数 | **24/24（整天恒开）** |
| 阈值 >159.85 时触发小时数 | 0/24（整天恒关） |

也就是说：**绝对阈值单独用作门控时没有可标定的区间** —— 中间只有 7.15 的噪声带。
所以判定高峰改为「绝对阈值 **且** 当前小时 ≥ 当日逐小时中位 × 1.5」。
少了这道判据，日作业一旦被调度起来，错峰返充就从「无操作」翻成「整天推迟」，
而它唯一的兜底只剩 SOC 临界阈值。设 `min-peak-pressure-ratio<=0` 可退回旧行为（回滚开关）。

推论：**`pressure_threshold` 在这份数据上无法标定**，它需要一条真的有昼夜起伏的需求序列；
本项目的数据全是压测夹具（见下表「真实数据集的边界」），所以这一条是被数据卡住，不是没做。

## 设计约束

| 约束 | 说明 |
| --- | --- |
| 只读训练源 | 特征导出仅 `SELECT`，不写任何业务表 |
| 结果独立落表 | 预测只写 `t_energy_forecast`（自己的表），不触碰订单/充电/车辆表 |
| 时间序切分 | 训练/测试按时间先后切分（后 20% 为测试），避免未来数据泄漏 |
| 特征无泄漏 | 所有滞后与滑窗特征先 `shift` 再聚合，第 t 小时特征只用 t-1 及更早数据 |
| 可复现 | 固定随机种子（`SEED=42`），相同输入必然得到相同结果 |
| 落库日期契约 | 剖面必须盖在**将被消费的那天**（服务读 `forecast_date = CURDATE()`）；日作业用 `--forecast-days 2` 覆盖今天+明天，避免午夜失效 |
| 同日重跑幂等 | 日作业默认按日期生成 `model_version`，同一天重跑是 `UPDATE` 而非新增行 |
| 诚实标注 | 仿真数据集的 `model_version` 与报告均显式标记 `synthetic` |
| 缺数据可退化 | 预测缺失/超期时 Java 侧一律判定"无压力"，行为与接入前一致；**但退化必须可观测**，见上节 |

## 指标口径

- **Pinball loss**：分位数损失，α=0.5 时等价于 MAE × 0.5 量级
- **P90 coverage**：`mean(actual ≤ P90)`，理想值接近 0.9；低于 0.8 说明上界系统性偏低
- **基线对比**：与"上周同一小时"（`lag_168h`）比较 MAE，用于证明模型确有增益

## 状态

| 阶段 | 状态 | 证据 |
| --- | --- | --- |
| 特征导出 SQL | 已落地 | `scripts/ml/export_energy_features.sql` |
| 训练脚本（XGBoost 分位数回归） | 已落地 | `scripts/ml/energy_demand_forecast.py`（xgboost 3.4.1，`objective=reg:quantileerror`） |
| Java 侧读取与回退 | 已落地 | `EnergyForecastService` / `EnergyForecastServiceImpl`；回退已分类为五档可观测状态 —— `EnergyForecastServiceImplTest` 25 + `EnergyForecastMetricsTest` 6 |
| 高峰判据 | **双判据** | 绝对阈值 `pressure-threshold=2.0` **且** 当前小时 ≥ 当日逐小时中位 × `min-peak-pressure-ratio=1.5`；后者是防"剖面太平→整天推迟"（见上节实测） |
| 返充错峰接入 | **真实链路已接** | 2026-09-21 起 `DispatchAutomationRuleServiceImpl.evaluateFleetEnergyRules` 在建补能任务前问 `shouldDeferReturnToCharge`（安全优先：SOC 余量不足时不推迟；换电支路不推迟）；仿真侧 `ParkPilotSimulationServiceImpl` 是原有调用方 |
| 真实历史数据训练 | **已跑通** | 生产库 `t_charging_session` 79,189 条（21 天）→ 528 特征行 → 训练 288 / 测试 72；落表 `t_energy_forecast`，服务已实际读取（`service_visible=1`） |
| 日作业（每日重跑） | **脚本已落地，调度未落地** | `scripts/ml/refresh_energy_forecast.py`：导出→训练→落表→自校验一次跑完；凭证走环境变量、不落盘；默认盖今天+明天。**注册计划任务属部署动作，未执行** —— 在其之前，靠上节的 `NO_ROWS`/`STALE` 信号判断上次有没有跑成功 |
| 真实数据集的边界 | **须随指标一并说明** | 该数据集为压测/夹具数据：充电柱共用坐标、仅 3/6 在用、SOC 恒为 89（`end_soc == start_soc`）⇒ `energy_kwh` 退化、归属塌缩为单站点；P90 覆盖率约 77.8–79.2% < 0.8 警戒线。**且剖面是平的**：小时到达均值 150.1、CV 0.06、峰谷比 1.08 ⇒ 没有可标定的绝对压力阈值（见上节） |
| 指标非比特稳定 | **已知** | 源数据仍在持续写入，导出窗口随时滑动 ⇒ 同一天多次重跑指标会小幅变化（非随机性 bug） |
