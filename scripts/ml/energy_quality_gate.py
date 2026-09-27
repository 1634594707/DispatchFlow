#!/usr/bin/env python3
"""ALG-FC 数据质量门禁（面试路线图 P0-2）：训练/发布前对特征数据做硬检查。

告警码（前四个是路线图点名的，后两条是数据级补充）：
  NO_ROWS           特征 CSV 没有任何有效数据行
  STALE             最新数据距今天超过 --stale-days（默认 3）天——预测"明天"却拿上周数据训练
  FLAT_PROFILE      小时需求剖面完全平坦（峰值/均值 < --flat-ratio，默认 1.1）——
                    平坦剖面做不出错峰决策，用它训出来的"错峰"是编的
  LOW_COVERAGE      非零 (站点,小时) 占比低于 --min-coverage（默认 0.3）——
                    绝大多数格子是 0，模型学到的只是"几乎总是 0"
  LOW_VARIANCE      目标变量方差 ≈ 0——模型无可学信号
  DUPLICATE_COORDS  同一 park 内多个 station 共用完全相同坐标——"最近站"归属把多站
                    塌成一站（README 已知的"站点归属塌缩"问题的可检测形态）

退出码：0 = 通过；2 = 阻断（任一告警码命中）。
发布语义：命中即阻断训练/落表，Java 侧继续用纯阈值策略——错误数据不能变成可消费的预测版本。

用法：
  python scripts/ml/energy_quality_gate.py --input reports/energy_features.csv
  python scripts/ml/energy_quality_gate.py --input reports/energy_features.csv --out reports/energy_quality_gate_2026-09.md
"""

from __future__ import annotations

import argparse
import json
import os
import sys

import pandas as pd

TARGET = "arrivals"


def evaluate_quality(raw: pd.DataFrame, *, today=None, stale_days: int = 3,
                     min_coverage: float = 0.3, flat_ratio: float = 1.1,
                     variance_floor: float = 1e-9) -> dict:
    """对特征数据做全量硬检查。返回 {ok, codes, checks, details}；不抛异常，缺列按 N/A 处理。"""
    codes: list[str] = []
    checks: dict[str, object] = {}
    details: list[str] = []

    if raw is None or len(raw) == 0:
        return {"ok": False, "codes": ["NO_ROWS"], "checks": {"rows": 0},
                "details": ["特征数据为空：导出失败或会话表为空，禁止训练/发布"]}

    frame = raw.copy()
    frame["slot_start"] = pd.to_datetime(frame["slot_start"], errors="coerce")
    valid = frame.dropna(subset=["slot_start"])
    checks["rows"] = int(len(valid))
    if valid.empty:
        codes.append("NO_ROWS")
        details.append("slot_start 全部不可解析，等于没有数据")
        return {"ok": False, "codes": codes, "checks": checks, "details": details}

    # ---- STALE：最新数据距今天的天数 ----
    today = pd.Timestamp(today) if today is not None else pd.Timestamp.now().normalize()
    max_ts = valid["slot_start"].max()
    age_days = int((today.normalize() - max_ts.normalize()).days)
    checks["latest_slot"] = str(max_ts)
    checks["age_days"] = age_days
    if age_days > stale_days:
        codes.append("STALE")
        details.append(f"最新数据 {max_ts} 距今 {age_days} 天（> {stale_days}）：预测今天/明天却在用过期数据训练")

    # ---- 目标变量方差 ----
    if TARGET in valid.columns:
        target = pd.to_numeric(valid[TARGET], errors="coerce").fillna(0.0)
        variance = float(target.var())
        checks["target_variance"] = variance
        if variance <= variance_floor:
            codes.append("LOW_VARIANCE")
            details.append(f"目标变量方差 {variance:.3e} ≈ 0：模型无可学信号")

        # ---- LOW_COVERAGE：非零 (站点,小时) 占比 ----
        nonzero_share = float((target > 0).mean())
        checks["nonzero_share"] = nonzero_share
        if nonzero_share < min_coverage:
            codes.append("LOW_COVERAGE")
            details.append(f"非零 (站点,小时) 占比 {nonzero_share:.3f} < {min_coverage}：模型学到的只是'几乎总是 0'")
    else:
        checks["target_variance"] = "N/A（缺 arrivals 列）"

    # ---- FLAT_PROFILE：小时剖面平坦度（峰值/均值）----
    hourly = valid.assign(hour=valid["slot_start"].dt.hour).groupby("hour")[TARGET].mean()
    if len(hourly) >= 24 and float(hourly.mean()) > 0:
        peak_to_mean = float(hourly.max() / hourly.mean())
        checks["hourly_peak_to_mean"] = peak_to_mean
        if peak_to_mean < flat_ratio:
            codes.append("FLAT_PROFILE")
            details.append(
                f"小时剖面峰值/均值 = {peak_to_mean:.3f} < {flat_ratio}：需求在小时维度是平的，"
                "任何'错峰'结论都只是噪声")
    else:
        checks["hourly_peak_to_mean"] = "N/A（小时覆盖不足 24 格）"

    # ---- DUPLICATE_COORDS：站点归属塌缩 ----
    if {"station_id", "station_lng", "station_lat", "park_id"} <= set(valid.columns):
        coords = (valid[["park_id", "station_id", "station_lng", "station_lat"]]
                  .dropna().drop_duplicates())
        dup_groups = (coords.groupby(["park_id", "station_lng", "station_lat"])["station_id"]
                      .nunique())
        collapsed = int(dup_groups[dup_groups > 1].shape[0])
        checks["stations"] = int(coords["station_id"].nunique())
        checks["collapsed_coord_groups"] = collapsed
        if collapsed > 0:
            codes.append("DUPLICATE_COORDS")
            details.append(
                f"{collapsed} 组坐标被多个 station 共用：'最近站'归属把多站塌成一站，"
                "站点维度的需求结论不可信")
    else:
        checks["collapsed_coord_groups"] = "N/A（导出无坐标列，请用新版 export_energy_features.sql 重导）"

    return {"ok": not codes, "codes": codes, "checks": checks, "details": details}


def to_markdown(verdict: dict, dataset: str) -> str:
    lines = [
        "# ALG-FC 数据质量门禁报告\n",
        f"- 数据集：`{dataset}`",
        f"- 判定：**{'✅ 通过' if verdict['ok'] else '⛔ 阻断'}**"
        f"（命中码：{'、'.join(verdict['codes']) if verdict['codes'] else '无'}）",
        "",
        "| 检查项 | 值 |",
        "| --- | --- |",
    ]
    for key, value in verdict["checks"].items():
        if isinstance(value, float):
            lines.append(f"| {key} | {value:.6g} |")
        else:
            lines.append(f"| {key} | {value} |")
    if verdict["details"]:
        lines.append("\n## 阻断理由\n")
        for detail in verdict["details"]:
            lines.append(f"- {detail}")
    lines.append("\n> 门禁语义：命中即阻断训练/落表，Java 侧继续纯阈值策略；"
                 "错误或平坦的数据不能变成可消费的预测版本。\n")
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description="ALG-FC 数据质量门禁")
    parser.add_argument("--input", required=True, help="特征 CSV 路径")
    parser.add_argument("--stale-days", type=int, default=3)
    parser.add_argument("--min-coverage", type=float, default=0.3)
    parser.add_argument("--flat-ratio", type=float, default=1.1)
    parser.add_argument("--out", help="同时把 Markdown 判定写到该路径")
    parser.add_argument("--json", action="store_true", help="只输出 JSON 判定")
    args = parser.parse_args()

    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from energy_demand_forecast import load_features  # 同一份读入口径，避免两套解析

    raw = load_features(args.input)
    verdict = evaluate_quality(raw, stale_days=args.stale_days,
                               min_coverage=args.min_coverage, flat_ratio=args.flat_ratio)
    if args.out:
        os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
        with open(args.out, "w", encoding="utf-8") as handle:
            handle.write(to_markdown(verdict, os.path.basename(args.input)))
    if args.json:
        print(json.dumps(verdict, ensure_ascii=False, indent=2))
    else:
        print(to_markdown(verdict, os.path.basename(args.input)))
    return 0 if verdict["ok"] else 2


if __name__ == "__main__":
    sys.exit(main())
