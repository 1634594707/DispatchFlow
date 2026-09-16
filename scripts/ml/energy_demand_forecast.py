#!/usr/bin/env python3
"""ALG-FC 站点补能需求预测：梯度提升分位数回归（P50 / P90）+ 滑窗 P95 到站压力。

产出（默认输出到 reports/）：
  - energy_forecast_predictions.csv   逐行预测（含 P50/P90/压力/模型版本）
  - energy_forecast_result.sql        可导入 t_energy_forecast 的 INSERT 语句
  - energy_forecast_report.md         评估报告（pinball loss / P90 覆盖率 / 基线对比）

设计约束（与 Java 侧 EnergyForecastService 的契约一致）：
  1. 纯离线：只读特征 CSV，不连接生产库；结果以文件形式交付导入
  2. 可复现：固定随机种子，时间序切分（不用随机切分，避免未来数据泄漏）
  3. 诚实标注：--synthesize 生成的数据集在报告与 model_version 中显式标记 synthetic

用法：
  # 1) 用真实导出数据训练
  python scripts/ml/energy_demand_forecast.py --input reports/energy_features.csv

  # 2) 没有历史数据时，用可复现的仿真数据跑通全链路（报告中标注 synthetic）
  python scripts/ml/energy_demand_forecast.py --synthesize

  # 3) 指定后端（默认 auto：优先 xgboost，不可用时退 sklearn 的 quantile GBM）
  python scripts/ml/energy_demand_forecast.py --synthesize --backend sklearn
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import sys
from dataclasses import dataclass

import numpy as np
import pandas as pd

SEED = 42
QUANTILES = (0.5, 0.9)
TARGET = "arrivals"
PRESSURE_WINDOW_HOURS = 24
PRESSURE_QUANTILE = 0.95
TEST_FRACTION = 0.2


@dataclass
class ForecastResult:
    backend: str
    model_version: str
    rows: pd.DataFrame
    metrics: dict
    train_rows: int
    test_rows: int


# --------------------------------------------------------------------------- #
# 数据准备
# --------------------------------------------------------------------------- #

def synthesize_features(days: int = 60, station_count: int = 4) -> pd.DataFrame:
    """生成可复现的仿真特征集（找不到真实历史数据时使用）。

    形态贴近园区补能：工作日早晚两个补能峰、周末整体走低、站点规模差异。
    所有随机量都由固定种子驱动，同样的参数必然得到同样的数据。
    """
    rng = np.random.default_rng(SEED)
    end = dt.datetime.now().replace(minute=0, second=0, microsecond=0)
    start = end - dt.timedelta(days=days)
    slots = pd.date_range(start, end, freq="h")

    records = []
    for station_index in range(station_count):
        station_id = 101 + station_index * 101
        base = 0.6 + 0.35 * station_index
        for slot in slots:
            hour = slot.hour
            weekday = slot.weekday()
            morning_peak = np.exp(-((hour - 9.5) ** 2) / 6.0)
            evening_peak = np.exp(-((hour - 16.0) ** 2) / 8.0)
            weekend_factor = 0.55 if weekday >= 5 else 1.0
            intensity = base * (0.45 + 2.1 * morning_peak + 1.7 * evening_peak) * weekend_factor
            arrivals = int(rng.poisson(max(intensity, 0.05)))
            energy = float(max(0.0, rng.normal(1.35, 0.35)) * arrivals)
            records.append({
                "park_id": 1,
                "station_id": station_id,
                "station_code": f"ZJF-CHG-{station_index + 1:02d}",
                "slot_start": slot,
                "arrivals": arrivals,
                "energy_kwh": round(energy, 3),
            })
    return pd.DataFrame.from_records(records)


def load_features(path: str) -> pd.DataFrame:
    """读取特征 CSV。

    兼容两种导出形态：
      - 逗号分隔的 CSV（手工整理 / 其他导出工具）
      - 制表符分隔文本（README 记录的标准流程：`mysql --batch --raw ... > energy_features.csv`）

    mysql 的 batch 模式固定用 Tab 作列分隔符，因此这里先按逗号尝试，
    缺列时再用分隔符探测重读，避免"按文档导出却读不进"的隐性断裂。
    """
    required = {"park_id", "station_id", "slot_start", TARGET}
    frame = pd.read_csv(path)
    if not required <= set(frame.columns):
        frame = pd.read_csv(path, sep=None, engine="python")
    missing = required - set(frame.columns)
    if missing:
        raise SystemExit(f"特征 CSV 缺少必需列: {sorted(missing)}")
    if "station_code" not in frame.columns:
        frame["station_code"] = frame["station_id"].astype(str)
    if "energy_kwh" not in frame.columns:
        frame["energy_kwh"] = 0.0
    return frame


def build_feature_frame(raw: pd.DataFrame) -> pd.DataFrame:
    """补齐小时空洞并构造滞后 / 滑动窗口特征。

    关键点：所有滞后与滑窗特征都先 shift 再聚合，保证第 t 小时的特征只用到 t-1 及更早的数据，
    避免把当前小时的观测泄漏进特征。
    """
    raw = raw.copy()
    raw["slot_start"] = pd.to_datetime(raw["slot_start"])
    frames = []
    for station_id, group in raw.groupby("station_id", sort=True):
        group = group.set_index("slot_start").sort_index()
        full_index = pd.date_range(group.index.min(), group.index.max(), freq="h")
        group = group.reindex(full_index)
        group["station_id"] = station_id
        group["park_id"] = group["park_id"].ffill().bfill()
        group["station_code"] = group["station_code"].ffill().bfill()
        group[TARGET] = group[TARGET].fillna(0).astype(float)
        group["energy_kwh"] = group["energy_kwh"].fillna(0).astype(float)
        group["slot_start"] = group.index
        frames.append(group.reset_index(drop=True))

    frame = pd.concat(frames, ignore_index=True).sort_values(["station_id", "slot_start"])
    frame["hour_of_day"] = frame["slot_start"].dt.hour
    frame["day_of_week"] = frame["slot_start"].dt.dayofweek
    frame["is_weekend"] = (frame["day_of_week"] >= 5).astype(int)
    frame["is_morning_peak"] = frame["hour_of_day"].between(8, 11).astype(int)
    frame["is_evening_peak"] = frame["hour_of_day"].between(14, 18).astype(int)

    grouped = frame.groupby("station_id", sort=False)[TARGET]
    frame["lag_1h"] = grouped.shift(1)
    frame["lag_2h"] = grouped.shift(2)
    frame["lag_24h"] = grouped.shift(24)
    frame["lag_168h"] = grouped.shift(168)
    frame["roll_mean_12h"] = grouped.shift(1).rolling(12).mean().reset_index(level=0, drop=True)
    frame["roll_mean_24h"] = grouped.shift(1).rolling(24).mean().reset_index(level=0, drop=True)
    frame["roll_max_24h"] = grouped.shift(1).rolling(24).max().reset_index(level=0, drop=True)
    # 滑窗 P95 到站压力：过去 24 小时到站量的 95 分位（同样先 shift，避免泄漏）
    frame["pressure_p95"] = (
        grouped.shift(1)
        .rolling(PRESSURE_WINDOW_HOURS)
        .quantile(PRESSURE_QUANTILE)
        .reset_index(level=0, drop=True)
    )
    frame["energy_lag_24h"] = frame.groupby("station_id", sort=False)["energy_kwh"].shift(24)

    feature_columns = [
        "hour_of_day", "day_of_week", "is_weekend", "is_morning_peak", "is_evening_peak",
        "lag_1h", "lag_2h", "lag_24h", "lag_168h",
        "roll_mean_12h", "roll_mean_24h", "roll_max_24h", "pressure_p95", "energy_lag_24h",
    ]
    frame = frame.dropna(subset=feature_columns + [TARGET]).reset_index(drop=True)
    return frame


# --------------------------------------------------------------------------- #
# 模型
# --------------------------------------------------------------------------- #

def resolve_backend(requested: str) -> str:
    if requested != "auto":
        return requested
    try:
        import xgboost  # noqa: F401
        return "xgboost"
    except ImportError:
        return "sklearn"


def train_quantile_model(backend: str, train_x: pd.DataFrame, train_y: pd.Series, alpha: float):
    if backend == "xgboost":
        from xgboost import XGBRegressor
        model = XGBRegressor(
            objective="reg:quantileerror",
            quantile_alpha=alpha,
            n_estimators=400,
            learning_rate=0.06,
            max_depth=4,
            min_child_weight=3.0,
            subsample=0.9,
            colsample_bytree=0.9,
            reg_lambda=1.0,
            random_state=SEED,
            n_jobs=4,
        )
        model.fit(train_x, train_y)
        return model
    # 回退：scikit-learn 的 quantile 损失梯度提升（同样的分位数回归家族）
    from sklearn.ensemble import GradientBoostingRegressor
    model = GradientBoostingRegressor(
        loss="quantile", alpha=alpha,
        n_estimators=300, learning_rate=0.06, max_depth=3, random_state=SEED,
    )
    model.fit(train_x, train_y)
    return model


def pinball_loss(actual: np.ndarray, predicted: np.ndarray, alpha: float) -> float:
    delta = actual - predicted
    return float(np.mean(np.maximum(alpha * delta, (alpha - 1) * delta)))


def run_forecast(frame: pd.DataFrame, backend: str, model_version: str) -> ForecastResult:
    feature_columns = [
        "hour_of_day", "day_of_week", "is_weekend", "is_morning_peak", "is_evening_peak",
        "lag_1h", "lag_2h", "lag_24h", "lag_168h",
        "roll_mean_12h", "roll_mean_24h", "roll_max_24h", "pressure_p95", "energy_lag_24h",
    ]
    frame = frame.sort_values("slot_start").reset_index(drop=True)
    split_index = int(len(frame) * (1 - TEST_FRACTION))
    train, test = frame.iloc[:split_index], frame.iloc[split_index:]

    models = {alpha: train_quantile_model(backend, train[feature_columns], train[TARGET], alpha)
              for alpha in QUANTILES}
    predictions = test[["park_id", "station_id", "station_code", "slot_start",
                        TARGET, "pressure_p95"]].copy()
    predictions["demand_p50"] = models[0.5].predict(test[feature_columns])
    predictions["demand_p90"] = models[0.9].predict(test[feature_columns])

    actual = test[TARGET].to_numpy(dtype=float)
    p50 = predictions["demand_p50"].to_numpy(dtype=float)
    p90 = predictions["demand_p90"].to_numpy(dtype=float)

    # 基线：上周同一小时（lag_168h），用于证明模型确实带来增益
    baseline = test["lag_168h"].to_numpy(dtype=float)
    naive_mae = float(np.mean(np.abs(actual - baseline)))
    model_mae = float(np.mean(np.abs(actual - p50)))

    metrics = {
        "pinball_p50": pinball_loss(actual, p50, 0.5),
        "pinball_p90": pinball_loss(actual, p90, 0.9),
        "p90_coverage": float(np.mean(actual <= p90)),
        "p50_mae": model_mae,
        "baseline_lag168_mae": naive_mae,
        "baseline_improvement_pct": float((naive_mae - model_mae) / naive_mae * 100.0) if naive_mae > 0 else 0.0,
        "pressure_p95": float(predictions["pressure_p95"].max()),
        "rows": int(len(predictions)),
    }
    return ForecastResult(backend, model_version, predictions, metrics,
                          train_rows=int(len(train)), test_rows=int(len(test)))


# --------------------------------------------------------------------------- #
# 产出
# --------------------------------------------------------------------------- #

def write_outputs(result: ForecastResult, out_dir: str, dataset_label: str,
                  forecast_date: dt.date) -> dict:
    os.makedirs(out_dir, exist_ok=True)
    predictions_path = os.path.join(out_dir, "energy_forecast_predictions.csv")
    sql_path = os.path.join(out_dir, "energy_forecast_result.sql")
    report_path = os.path.join(out_dir, "energy_forecast_report.md")

    rows = result.rows.copy()
    rows["demand_p50"] = rows["demand_p50"].clip(lower=0).round(4)
    rows["demand_p90"] = rows["demand_p90"].clip(lower=0).round(4)
    rows["pressure_p95"] = rows["pressure_p95"].round(4)
    rows.to_csv(predictions_path, index=False, encoding="utf-8")

    generated_at = dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    with open(sql_path, "w", encoding="utf-8") as handle:
        handle.write("-- ALG-FC 预测导入（由 scripts/ml/energy_demand_forecast.py 生成）\n")
        handle.write(f"-- model_version={result.model_version} backend={result.backend}\n")
        handle.write(f"-- dataset={dataset_label} rows={len(rows)}\n\n")
        handle.write("USE `fsd_core`;\n\n")
        for _, row in rows.iterrows():
            hour = int(pd.Timestamp(row["slot_start"]).hour)
            handle.write(
                "INSERT INTO `t_energy_forecast` "
                "(`park_id`,`station_id`,`station_code`,`forecast_date`,`hour_of_day`,"
                "`demand_p50`,`demand_p90`,`pressure_p95`,`sample_count`,`model_version`,"
                "`generated_at`,`remark`,`deleted`)\n"
                f"VALUES ({int(row['park_id'])},{int(row['station_id'])},"
                f"'{row['station_code']}','{forecast_date}',{hour},"
                f"{row['demand_p50']:.4f},{row['demand_p90']:.4f},{row['pressure_p95']:.4f},"
                f"{result.train_rows},'{result.model_version}','{generated_at}',"
                f"'{dataset_label}','0')\n"
                "ON DUPLICATE KEY UPDATE "
                "`demand_p50`=VALUES(`demand_p50`),`demand_p90`=VALUES(`demand_p90`),"
                "`pressure_p95`=VALUES(`pressure_p95`),`sample_count`=VALUES(`sample_count`),"
                "`generated_at`=VALUES(`generated_at`),`remark`=VALUES(`remark`);\n"
            )

    with open(report_path, "w", encoding="utf-8") as handle:
        handle.write("# 站点补能需求预测评估报告（ALG-FC）\n\n")
        handle.write(f"- 生成时间：{generated_at}\n")
        handle.write(f"- 数据集：{dataset_label}\n")
        handle.write(f"- 训练后端：{result.backend}\n")
        handle.write(f"- 模型版本：{result.model_version}\n")
        handle.write(f"- 训练 / 测试样本：{result.train_rows} / {result.test_rows}（时间序切分，后 {int(TEST_FRACTION * 100)}% 为测试）\n")
        handle.write(f"- 目标变量：站点小时到站补能次数（arrivals）\n")
        handle.write(f"- 分位数：P50 / P90；压力指标：过去 {PRESSURE_WINDOW_HOURS}h 到站量的 P{int(PRESSURE_QUANTILE * 100)}\n\n")
        handle.write("## 指标\n\n| 指标 | 数值 |\n| --- | --- |\n")
        label_map = {
            "pinball_p50": "Pinball loss (α=0.5)",
            "pinball_p90": "Pinball loss (α=0.9)",
            "p90_coverage": "P90 覆盖率（实际值 ≤ P90 的比例）",
            "p50_mae": "P50 MAE",
            "baseline_lag168_mae": "基线（上周同时刻）MAE",
            "baseline_improvement_pct": "相对基线 MAE 改善",
            "pressure_p95": "全站最大 P95 到站压力",
            "rows": "预测行数",
        }
        for key, value in result.metrics.items():
            formatted = f"{value:.4f}" if isinstance(value, float) else str(value)
            handle.write(f"| {label_map.get(key, key)} | {formatted} |\n")
        handle.write("\n## 数据说明\n\n")
        if dataset_label == "synthetic":
            handle.write("> ⚠️ 本报告基于**仿真数据**（固定种子可复现）训练，用于验证"
                         "「特征 → 训练 → 评估 → 落表 → 服务读取」链路；接入真实 "
                         "`t_charging_session` 历史后需重跑并替换本报告数字。\n")
        else:
            handle.write(f"> 数据来源：`t_charging_session` 导出（{dataset_label}）。\n")
        handle.write("\n## 产出文件\n\n")
        handle.write("- `energy_forecast_predictions.csv`：逐行预测\n")
        handle.write("- `energy_forecast_result.sql`：可导入 `t_energy_forecast`\n")

    return {
        "predictions": predictions_path,
        "sql": sql_path,
        "report": report_path,
        "metrics": result.metrics,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="站点补能需求预测（分位数回归 + 滑窗 P95 压力）")
    parser.add_argument("--input", help="特征 CSV 路径（scripts/ml/export_energy_features.sql 导出）")
    parser.add_argument("--synthesize", action="store_true", help="无历史数据时用可复现仿真数据")
    parser.add_argument("--backend", default="auto", choices=["auto", "xgboost", "sklearn"])
    parser.add_argument("--days", type=int, default=60, help="仿真数据天数（--synthesize）")
    parser.add_argument("--stations", type=int, default=4, help="仿真站点数（--synthesize）")
    parser.add_argument("--out-dir", default="reports")
    parser.add_argument("--model-version", default=None)
    args = parser.parse_args()

    if args.synthesize or not args.input:
        raw = synthesize_features(days=args.days, station_count=args.stations)
        dataset_label = "synthetic"
    else:
        raw = load_features(args.input)
        dataset_label = os.path.basename(args.input)

    backend = resolve_backend(args.backend)
    frame = build_feature_frame(raw)
    if frame.empty:
        raise SystemExit("特征构造后无有效样本：请检查数据量是否足够（至少需要 8 天连续小时数据）")

    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M")
    model_version = args.model_version or f"energy-demand-{backend}-{dataset_label}-{stamp}"

    result = run_forecast(frame, backend, model_version)
    outputs = write_outputs(result, args.out_dir, dataset_label, dt.date.today())

    print(json.dumps({
        "backend": result.backend,
        "dataset": dataset_label,
        "model_version": result.model_version,
        "train_rows": result.train_rows,
        "test_rows": result.test_rows,
        "metrics": result.metrics,
        "outputs": outputs,
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
