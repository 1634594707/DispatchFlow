# ALG-FC 数据质量门禁报告

- 数据集：`energy_features.csv`
- 判定：**⛔ 阻断**（命中码：STALE）

| 检查项 | 值 |
| --- | --- |
| rows | 13 |
| latest_slot | 2026-09-22 21:00:00 |
| age_days | 5 |
| target_variance | 3985.94 |
| nonzero_share | 1 |
| hourly_peak_to_mean | N/A（小时覆盖不足 24 格） |
| collapsed_coord_groups | N/A（导出无坐标列，请用新版 export_energy_features.sql 重导） |

## 阻断理由

- 最新数据 2026-09-22 21:00:00 距今 5 天（> 3）：预测今天/明天却在用过期数据训练

> 门禁语义：命中即阻断训练/落表，Java 侧继续纯阈值策略；错误或平坦的数据不能变成可消费的预测版本。
