#!/usr/bin/env python3
"""ALG-FC 补能预测日作业：一次跑完「导出特征 → 训练 → 落表 → 校验」。

为什么需要它：`EnergyForecastServiceImpl` 按 `forecast_date = CURDATE()` + `hour_of_day = HOUR(NOW())`
读取预测，而模型产出的是逐小时需求剖面。因此**必须每天重跑**，否则预测会在午夜立即失效
（实测 23:50 生成、00:01 即不可见），服务静默回退到纯阈值策略。

安全与约定：
  1. 唯一的写入目标是 `t_energy_forecast`；特征导出 SQL 本身只读，不触碰业务表
  2. **凭证只从环境变量读取，绝不落盘、绝不写进本文件**
  3. 默认把剖面同时盖在「今天 + 明天」两天，容忍日作业跨过午夜执行

环境变量：
  FSD_PROD_HOST        生产服务器地址（必填）
  FSD_PROD_PASS        SSH 密码（必填）
  FSD_PROD_USER        SSH 用户（默认 root）
  FSD_PROD_PORT        SSH 端口（默认 22）
  FSD_MYSQL_CONTAINER  MySQL 容器名（默认 fsd-mysql）

用法（需在已安装 paramiko 的解释器下运行；训练会自动切到项目内的 .venv-ml）：
  python scripts/ml/refresh_energy_forecast.py --forecast-days 2
  python scripts/ml/refresh_energy_forecast.py --no-import
"""

from __future__ import annotations

import argparse
import datetime as dt
import os
import subprocess
import sys
import time

try:
    import paramiko
except ImportError:  # pragma: no cover - 取决于运行环境
    raise SystemExit(
        "缺少 paramiko：请用已安装 paramiko 的解释器运行本脚本"
        "（例如 workbuddy 受管 venv 的 Scripts/python.exe）"
    )

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
EXPORT_SQL = os.path.join(REPO_ROOT, "scripts", "ml", "export_energy_features.sql")
TRAIN_SCRIPT = os.path.join(REPO_ROOT, "scripts", "ml", "energy_demand_forecast.py")
REPORTS_DIR = os.path.join(REPO_ROOT, "reports")
FEATURES_CSV = os.path.join(REPORTS_DIR, "energy_features.csv")
RESULT_SQL = os.path.join(REPORTS_DIR, "energy_forecast_result.sql")

REMOTE_EXPORT_SQL = "/tmp/export_energy_features.sql"
REMOTE_FEATURES = "/tmp/energy_features.tsv"
REMOTE_RESULT_SQL = "/tmp/energy_forecast_result.sql"
REMOTE_EXPORT_ERR = "/tmp/export_features_err.log"


def shell_quote(text: str) -> str:
    return "'" + text.replace("'", "'\\''") + "'"


def ml_python() -> str:
    """定位训练用的 .venv-ml 解释器（xgboost 只装在这里）。"""
    for path in (os.path.join(REPO_ROOT, ".venv-ml", "Scripts", "python.exe"),
                 os.path.join(REPO_ROOT, ".venv-ml", "bin", "python")):
        if os.path.isfile(path):
            return path
    raise SystemExit("未找到 .venv-ml 解释器：请先按 scripts/ml/README.md 创建并安装 xgboost")


class Prod:
    """生产服务器连接。"""

    def __init__(self) -> None:
        self.host = os.environ.get("FSD_PROD_HOST")
        self.password = os.environ.get("FSD_PROD_PASS")
        if not self.host or not self.password:
            raise SystemExit("必须设置环境变量 FSD_PROD_HOST 与 FSD_PROD_PASS")
        self.user = os.environ.get("FSD_PROD_USER", "root")
        self.port = int(os.environ.get("FSD_PROD_PORT", "22"))
        self.container = os.environ.get("FSD_MYSQL_CONTAINER", "fsd-mysql")
        self.ssh = self._connect_with_retry()
        self.sftp = self.ssh.open_sftp()

    def _connect_with_retry(self, attempts: int = 4):
        """带退避的连接：sshd 的 MaxStartups 会在短时间多连接时直接关掉握手（EOFError），
        日作业不能因此失败。"""
        last_error: Exception | None = None
        for index in range(attempts):
            client = paramiko.SSHClient()
            client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
            try:
                client.connect(self.host, port=self.port, username=self.user,
                               password=self.password, timeout=30,
                               look_for_keys=False, allow_agent=False)
                return client
            except Exception as exc:  # noqa: BLE001 - 覆盖 EOFError / SSHException 等
                last_error = exc
                client.close()
                if index < attempts - 1:
                    time.sleep(3 * (index + 1))
        raise SystemExit(f"SSH 连接 {self.user}@{self.host}:{self.port} 失败"
                         f"（已重试 {attempts} 次）：{type(last_error).__name__}: {last_error}")

    def mysql(self, sql: str | None = None, stdin_file: str | None = None,
              out_file: str | None = None, err_file: str | None = None,
              bare: bool = False) -> str:
        """在 MySQL 容器里执行 SQL；sql 走 stdin 或从 stdin_file 重定向。

        `bare=True` 会加 `-N`（不输出列名），用于取标量结果。
        注意引号层次：内层命令必须**整体单引号**交给 `sh -c`，这样 `$MYSQL_ROOT_PASSWORD`
        才会在**容器内**展开。若在外层再套一层引号，宿主 shell 会先吃掉 `-p"..."` 的
        双引号，导致密码变成空值并报 `Access denied ... (using password: NO)`。
        """
        inner = ('mysql -uroot -p"$MYSQL_ROOT_PASSWORD" fsd_core '
                 '--batch --raw --default-character-set=utf8mb4')
        if bare:
            inner += " -N"
        quoted = shell_quote(inner)
        if stdin_file:
            cmd = f"docker exec -i {self.container} sh -c {quoted} < {stdin_file}"
        else:
            cmd = f"echo {shell_quote(sql or '')} | docker exec -i {self.container} sh -c {quoted}"
        if out_file:
            cmd += f" > {out_file}"
        if err_file:
            cmd += f" 2>{err_file}"
        _, out, err = self.ssh.exec_command(cmd)
        text = out.read().decode("utf-8", "replace")
        errtext = "\n".join(line for line in err.read().decode("utf-8", "replace").splitlines()
                            if "Using a password" not in line).strip()
        if errtext:
            print(f"   [mysql stderr] {errtext}", file=sys.stderr)
        return text

    def read_remote(self, path: str) -> str:
        _, out, _ = self.ssh.exec_command(f"cat {path} 2>/dev/null")
        return out.read().decode("utf-8", "replace").strip()

    def close(self) -> None:
        self.sftp.close()
        self.ssh.close()


def run_local(cmd: list[str]) -> str:
    proc = subprocess.run(cmd, capture_output=True, text=True,
                          encoding="utf-8", errors="replace")
    if proc.returncode != 0:
        raise SystemExit(f"命令失败（exit {proc.returncode}）：{' '.join(cmd)}\n{proc.stderr}")
    return proc.stdout


def main() -> int:
    parser = argparse.ArgumentParser(description="ALG-FC 补能预测日作业")
    parser.add_argument("--forecast-days", type=int, default=2,
                        help="从今天起连续落库的天数（默认 2）")
    parser.add_argument("--forecast-date", default=None,
                        help="落库起始日期 YYYY-MM-DD（默认今天）")
    parser.add_argument("--model-version", default=None,
                        help="模型版本号；默认按落库起始日期生成，使同一天重跑幂等（UPDATE 而非新增行）")
    parser.add_argument("--version-tag", default="zjf-chg01",
                        help="默认版本号里的场景标签（默认 zjf-chg01）")
    parser.add_argument("--prune-days", type=int, default=0,
                        help=">0 时删除 forecast_date 早于今天-N 天的失效预测行（默认 0=不删）")
    parser.add_argument("--no-import", action="store_true",
                        help="只导出与训练，不写入生产库")
    args = parser.parse_args()

    os.makedirs(REPORTS_DIR, exist_ok=True)
    prod = Prod()
    print(f"[0/4] 已连接 {prod.user}@{prod.host}:{prod.port}")

    print("[1/4] 导出站点×小时特征（SELECT，只读）")
    prod.sftp.put(EXPORT_SQL, REMOTE_EXPORT_SQL)
    prod.mysql(stdin_file=REMOTE_EXPORT_SQL, out_file=REMOTE_FEATURES,
               err_file=REMOTE_EXPORT_ERR)
    export_err = prod.read_remote(REMOTE_EXPORT_ERR)
    if export_err and "Using a password" not in export_err:
        print(f"   [导出告警] {export_err}", file=sys.stderr)
    prod.sftp.get(REMOTE_FEATURES, FEATURES_CSV)
    row_count = max(0, sum(1 for _ in open(FEATURES_CSV, encoding="utf-8")) - 1)
    print(f"   特征行数={row_count} -> {FEATURES_CSV}")
    if row_count == 0:
        raise SystemExit("特征为空：生产库可能没有可用的充电会话数据")

    print("[2/4] 训练分位数回归模型")
    base_date = (dt.date.fromisoformat(args.forecast_date) if args.forecast_date
                 else dt.date.today())
    # 版本号按日期确定：同一天重跑只更新已有 24×N 行，不会无限新增
    model_version = args.model_version or \
        f"energy-demand-xgboost-{args.version_tag}-{base_date:%Y%m%d}"
    train_cmd = [ml_python(), TRAIN_SCRIPT, "--input", FEATURES_CSV,
                 "--out-dir", REPORTS_DIR,
                 "--forecast-date", base_date.isoformat(),
                 "--forecast-days", str(args.forecast_days),
                 "--model-version", model_version]
    print(run_local(train_cmd).strip())

    if args.no_import:
        print("[3/4] --no-import：跳过写库")
    else:
        print("[3/4] 导入预测到生产 t_energy_forecast")
        prod.sftp.put(RESULT_SQL, REMOTE_RESULT_SQL)
        prod.mysql(stdin_file=REMOTE_RESULT_SQL)
        print(f"   导入完成（model_version={model_version}）")

    if args.prune_days > 0:
        pruned = prod.mysql(
            "DELETE FROM t_energy_forecast "
            f"WHERE forecast_date < CURDATE() - INTERVAL {args.prune_days} DAY;"
            " SELECT CONCAT('pruned=', ROW_COUNT());", bare=True).strip()
        print(f"   清理失效行：{pruned}")

    print("[4/4] 按服务读取口径校验")
    checks = prod.mysql(
        "SELECT CONCAT('total=', COUNT(*)) FROM t_energy_forecast WHERE deleted=0;"
        " SELECT CONCAT('curdate_rows=', COUNT(*), ' hours=', COUNT(DISTINCT hour_of_day))"
        "   FROM t_energy_forecast WHERE forecast_date=CURDATE() AND deleted=0;"
        " SELECT CONCAT('service_visible=', COUNT(*)) FROM t_energy_forecast"
        "   WHERE forecast_date=CURDATE() AND deleted=0"
        "     AND generated_at >= NOW() - INTERVAL 24 HOUR AND hour_of_day=HOUR(NOW());"
        " SELECT CONCAT('park_pressure_p95=', IFNULL(MAX(pressure_p95),0))"
        "   FROM t_energy_forecast WHERE forecast_date=CURDATE() AND deleted=0"
        "     AND generated_at >= NOW() - INTERVAL 24 HOUR;",
        bare=True)
    values = {}
    for line in checks.splitlines():
        line = line.strip()
        if "=" in line:
            key, _, value = line.partition("=")
            values[key] = value
    summary = " | ".join(f"{k}={v}" for k, v in values.items()) or "(无输出)"
    print(f"   {summary}")

    prod.close()
    # 服务取 max(generated_at) 那一行；同一小时存在多个 model_version 的行属正常，
    # 因此这里只要求"至少一行可见"，不要求恰好为 1
    if not args.no_import and int(values.get("service_visible", "0") or 0) <= 0:
        print("REFRESH_WARN: 当前小时没有可见预测，服务将回退阈值策略", file=sys.stderr)
        return 2
    print("REFRESH_DONE")
    return 0


if __name__ == "__main__":
    sys.exit(main())
