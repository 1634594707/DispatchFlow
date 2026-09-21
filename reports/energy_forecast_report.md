# 站点补能需求预测评估报告（ALG-FC）

- 生成时间：2026-09-17 01:24:20
- 数据集：energy_features.csv
- 训练后端：xgboost
- 模型版本：energy-demand-xgboost-zjf-chg01-20260917
- 训练 / 测试样本：288 / 73（时间序切分，后 20% 为测试）
- 目标变量：站点小时到站补能次数（arrivals）
- 分位数：P50 / P90；压力指标：过去 24h 到站量的 P95
- 落库日期（forecast_date）：2026-09-17,2026-09-18（共 2 天 × 24 小时剖面）

## 指标

| 指标 | 数值 |
| --- | --- |
| Pinball loss (α=0.5) | 4.4731 |
| Pinball loss (α=0.9) | 1.5527 |
| P90 覆盖率（实际值 ≤ P90 的比例） | 0.7945 |
| P50 MAE | 8.9462 |
| 基线（上周同时刻）MAE | 10.5205 |
| 相对基线 MAE 改善 | 14.9643 |
| 全站最大 P95 到站压力 | 162.8500 |
| 预测行数 | 73 |

## 数据说明

> 数据来源：`t_charging_session` 导出（energy_features.csv）。

## 产出文件

- `energy_forecast_predictions.csv`：逐行预测
- `energy_forecast_result.sql`：可导入 `t_energy_forecast`
