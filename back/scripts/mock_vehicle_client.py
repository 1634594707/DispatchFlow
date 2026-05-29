#!/usr/bin/env python3
"""Mock real vehicle client for Phase 3 acceptance."""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone


def request_json(method: str, url: str, headers: dict[str, str], payload: dict | None = None) -> dict:
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method)
    for key, value in headers.items():
        req.add_header(key, value)
    if payload is not None:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=10) as resp:
        body = resp.read().decode("utf-8")
        result = json.loads(body) if body else {}
    if result.get("success") is False:
        raise RuntimeError(f"{result.get('code')}: {result.get('message')}")
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description="DispatchFlow mock real vehicle")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--vehicle-code", default="REAL-001")
    parser.add_argument("--vehicle-token", default="real-demo-key-001")
    parser.add_argument("--fail-command", action="store_true")
    args = parser.parse_args()

    headers = {
        "X-Vehicle-Code": args.vehicle_code,
        "X-Vehicle-Token": args.vehicle_token,
    }
    base = args.base_url.rstrip("/")

    poll = request_json("GET", f"{base}/api/vehicle-gateway/commands/next", headers)
    command = poll.get("data")
    if not command:
        print("No pending command")
        return 0

    command_id = command["commandId"]
    task_id = command["taskId"]
    order_id = command["orderId"]
    print(f"Received command {command_id} for task {task_id}")

    if args.fail_command:
        request_json(
            "POST",
            f"{base}/api/vehicle-gateway/commands/{command_id}/fail",
            headers,
            {"reason": "mock vehicle rejected route"},
        )
        print("Command failed as requested")
        return 0

    request_json("POST", f"{base}/api/vehicle-gateway/commands/{command_id}/ack", headers, {})
    now = datetime.now().replace(tzinfo=None).isoformat(timespec="seconds")

    telemetry = {
        "vehicleCode": args.vehicle_code,
        "runtimeStage": "TO_PICKUP",
        "pluggedIn": False,
        "targetCode": command.get("pickupStationCode"),
        "targetType": "PICKUP",
        "soc": 92,
        "x": 220,
        "y": 170,
        "reportTime": now,
        "eventSeq": int(time.time()),
    }
    request_json("POST", f"{base}/api/vehicle-gateway/telemetry", headers, telemetry)

    start_report = {
        "vehicleCode": args.vehicle_code,
        "onlineStatus": "ONLINE",
        "dispatchStatus": "BUSY",
        "taskId": task_id,
        "orderId": order_id,
        "reportType": "START_EXECUTE",
        "reportTime": now,
        "latitude": 170,
        "longitude": 220,
        "batteryLevel": 92,
        "resultMessage": "Mock vehicle started",
    }
    request_json("POST", f"{base}/api/vehicle-gateway/reports", headers, start_report)

    success_report = dict(start_report)
    success_report["reportType"] = "TASK_SUCCESS"
    success_report["resultMessage"] = "Mock vehicle completed delivery"
    success_report["reportTime"] = datetime.now().replace(tzinfo=None).isoformat(timespec="seconds")
    request_json("POST", f"{base}/api/vehicle-gateway/reports", headers, success_report)

    print("Mock vehicle flow completed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except urllib.error.HTTPError as exc:
        print(exc.read().decode("utf-8"), file=sys.stderr)
        raise SystemExit(1)
