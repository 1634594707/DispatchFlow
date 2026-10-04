#!/usr/bin/env python
"""读 tmp/perf 里的 cgroup 采样与 k6 summary，输出每档的 CPU/内存/延迟与业务计数。

用法：
    python scripts/perf/load-ab-report.py p0-after
    python scripts/perf/load-ab-report.py pre-P0 p0-after     # 两列并排，便于 A/B

判读纪律（写在代码里防误读）：
  * cores 来自 cpu.stat 差分，不是 docker stats 的百分比（WSL2 下那个不可信）。
  * k6 的 `http_req_failed` 是 Rate：`passes` 计的是"请求失败"的次数，`fails` 才是正常请求，
    所以失败率 = passes / http_reqs.count —— 拿 passes 当分母会得出"全部失败"的假结论。
  * `task_assigned` 低不等于平台慢：35 台车在 2.7-5 单/s 下几分钟饱和是运力事实（方案 §4.3）。
"""
import csv
import json
import statistics
import sys
from pathlib import Path

CONTAINERS = ("fsd-core-server", "fsd-mysql", "fsd-redis", "fsd-rabbitmq")
LATENCY = (
    ("order_create", "http_req_duration{name:order_create}"),
    ("track_poll", "http_req_duration{name:mobile_track_poll}"),
    ("wholepark_poll", "http_req_duration{name:whole_park_poll}"),
)


def repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def read_csv(path: Path):
    cores, mem = {}, {}
    with path.open(encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            value = float(row["cores_in_window"])
            if value >= 0:  # 负值 = 期间容器重启过，计数器归零，这一窗作废
                cores.setdefault(row["container"], []).append(value)
            mem.setdefault(row["container"], []).append(float(row["mem_mb"]))
    return cores, mem


def cpu_line(out_dir: Path, side: str, tier: str):
    path = out_dir / f"{tier}-{side}.csv"
    if not path.exists():
        return "(missing)"
    cores, mem = read_csv(path)
    parts = []
    for name in CONTAINERS:
        vals = cores.get(name)
        if vals:
            parts.append(f"{name.split('-', 1)[1]} avg={statistics.mean(vals):.2f}/peak{max(vals):.2f}")
    total = sum(statistics.mean(v) for v in cores.values())
    mem_total = sum(max(v) for v in mem.values()) if mem else 0
    windows = max((len(v) for v in cores.values()), default=0)
    return f"{windows} windows  " + "  ".join(parts) + f"  total_avg={total:.2f} cores  mem={mem_total / 1024:.2f}GB"


def k6_line(out_dir: Path, side: str, tier: str):
    path = out_dir / f"{tier}-{side}.json"
    if not path.exists():
        return "(missing)"
    metrics = json.loads(path.read_text(encoding="utf-8"))["metrics"]
    bits = []
    for label, key in LATENCY:
        d = metrics.get(key)
        if d:
            bits.append(f"{label} p95={d['p(95)']:.0f}ms")
    accepted = metrics.get("order_accepted", {})
    assigned = metrics.get("task_assigned", {})
    failed = metrics.get("http_req_failed", {})
    reqs = metrics.get("http_reqs", {}).get("count", 0)
    bytes_avg = metrics.get("whole_park_bytes", {}).get("avg", 0)
    bits.append(f"accepted={accepted.get('passes', 0)}/{accepted.get('passes', 0) + accepted.get('fails', 0)}")
    bits.append(f"assigned={assigned.get('passes', 0)}/{assigned.get('passes', 0) + assigned.get('fails', 0)}")
    bits.append(f"http_failed={failed.get('passes', 0)}/{reqs}")
    bits.append(f"whole_park={bytes_avg / 1024:.0f}KB")
    return "  ".join(bits)


def main(argv):
    sides = argv[1:] or [""]
    out_dir = repo_root() / "tmp" / "perf"
    for side in sides:
        print(f"===== {side or '(pass a side tag)'}")
        for tier, cn in (("idle", "idle"), ("std", "standard 30/60"), ("2x", "2x 60/120")):
            print(f"  {cn:14s} {cpu_line(out_dir, side, tier)}")
            if tier != "idle":
                print(f"  {'':14s} {k6_line(out_dir, side, tier)}")


if __name__ == "__main__":
    main(sys.argv)
