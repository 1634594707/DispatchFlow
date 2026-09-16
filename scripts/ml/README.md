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

# 2) 训练 + 评估 + 产出导入 SQL
.venv-ml/Scripts/python scripts/ml/energy_demand_forecast.py --input reports/energy_features.csv

# 无历史数据时（链路自检 / 演示）：固定种子的仿真数据，报告中标注 synthetic
.venv-ml/Scripts/python scripts/ml/energy_demand_forecast.py --synthesize --days 60 --stations 4

# 3) 导入预测
mysql -h127.0.0.1 -P3306 -uroot -p fsd_core < reports/energy_forecast_result.sql
```

## 设计约束

| 约束 | 说明 |
| --- | --- |
| 只读训练源 | 特征导出仅 `SELECT`，不写任何业务表 |
| 结果独立落表 | 预测只写 `t_energy_forecast`（自己的表），不触碰订单/充电/车辆表 |
| 时间序切分 | 训练/测试按时间先后切分（后 20% 为测试），避免未来数据泄漏 |
| 特征无泄漏 | 所有滞后与滑窗特征先 `shift` 再聚合，第 t 小时特征只用 t-1 及更早数据 |
| 可复现 | 固定随机种子（`SEED=42`），相同输入必然得到相同结果 |
| 诚实标注 | 仿真数据集的 `model_version` 与报告均显式标记 `synthetic` |
| 缺数据可退化 | 预测缺失/超期时 Java 侧一律判定"无压力"，行为与接入前一致 |

## 指标口径

- **Pinball loss**：分位数损失，α=0.5 时等价于 MAE × 0.5 量级
- **P90 coverage**：`mean(actual ≤ P90)`，理想值接近 0.9；低于 0.8 说明上界系统性偏低
- **基线对比**：与"上周同一小时"（`lag_168h`）比较 MAE，用于证明模型确有增益

## 状态

| 阶段 | 状态 | 证据 |
| --- | --- | --- |
| 特征导出 SQL | 已落地 | `scripts/ml/export_energy_features.sql` |
| 训练脚本（XGBoost 分位数回归） | 已落地 | `scripts/ml/energy_demand_forecast.py`（xgboost 3.4.1，`objective=reg:quantileerror`） |
| Java 侧读取与回退 | 已落地 | `EnergyForecastService` / `EnergyForecastServiceImpl` + 9 个单测 |
| 返充错峰接入 | 已落地 | `ParkPilotSimulationServiceImpl.shouldReturnToCharge`（安全优先：SOC 余量不足时不推迟） |
| 真实历史数据训练 | **待办** | 需在有 `t_charging_session` 历史的环境重跑并替换报告数字 |
