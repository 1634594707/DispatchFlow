#!/usr/bin/env python3
"""Minimal VDA5050 v2 AGV simulator for DispatchFlow MQTT integration demo.

Requires: pip install paho-mqtt

Example:
  python scripts/vda5050-sim-agv.py --broker tcp://127.0.0.1:1883 \\
    --manufacturer DispatchFlow --serial AGV-001
"""

from __future__ import annotations

import argparse
import json
import math
import time
from datetime import datetime, timezone

import paho.mqtt.client as mqtt


def iso_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def build_state(args, header_id: int, x: float, y: float, driving: bool, soc: float) -> dict:
    return {
        "headerId": header_id,
        "timestamp": iso_now(),
        "version": "2.0.0",
        "manufacturer": args.manufacturer,
        "serialNumber": args.serial,
        "orderId": args.active_order_id,
        "orderUpdateId": args.active_order_update_id,
        "lastNodeId": "pickup" if driving else None,
        "driving": driving,
        "paused": False,
        "operatingMode": "AUTOMATIC",
        "nodeStates": [],
        "edgeStates": [],
        "actionStates": [],
        "batteryState": {
            "batteryCharge": max(0.0, min(1.0, soc)),
            "batteryVoltage": 48.0,
            "charging": False,
        },
        "errors": [],
        "safetyState": {"eStop": "NONE", "fieldViolation": False},
        "agvPosition": {"x": x, "y": y, "theta": 0.0, "mapId": "default"},
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="DispatchFlow VDA5050 demo AGV")
    parser.add_argument("--broker", default="tcp://127.0.0.1:1883")
    parser.add_argument("--interface", default="uagv/v2")
    parser.add_argument("--manufacturer", default="DispatchFlow")
    parser.add_argument("--serial", default="AGV-001")
    parser.add_argument("--interval", type=float, default=2.0)
    parser.add_argument("--start-x", type=float, default=100.0)
    parser.add_argument("--start-y", type=float, default=120.0)
    args = parser.parse_args()

    args.active_order_id = ""
    args.active_order_update_id = 0
    target = {"x": args.start_x, "y": args.start_y}
    pos = {"x": args.start_x, "y": args.start_y}
    header_id = 1
    soc = 0.9

    prefix = f"{args.interface}/{args.manufacturer}/{args.serial}"
    state_topic = f"{prefix}/state"
    order_topic = f"{prefix}/order"

    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"sim-{args.serial}")

    def on_connect(client, userdata, flags, reason_code, properties=None):
        print(f"connected to {args.broker}, subscribe {order_topic}")
        client.subscribe(order_topic, qos=1)

    def on_message(client, userdata, msg):
        nonlocal target, header_id, soc
        order = json.loads(msg.payload.decode("utf-8"))
        args.active_order_id = order.get("orderId", "")
        args.active_order_update_id = int(order.get("orderUpdateId", 0))
        nodes = order.get("nodes") or []
        dropoff = next((n for n in nodes if n.get("nodeId") == "dropoff"), nodes[-1] if nodes else None)
        if dropoff and dropoff.get("nodePosition"):
            target["x"] = float(dropoff["nodePosition"]["x"])
            target["y"] = float(dropoff["nodePosition"]["y"])
        print(f"received order {args.active_order_id} -> target ({target['x']}, {target['y']})")

    client.on_connect = on_connect
    client.on_message = on_message
    host_port = args.broker.replace("tcp://", "").split(":")
    client.connect(host_port[0], int(host_port[1] if len(host_port) > 1 else 1883), keepalive=30)
    client.loop_start()

    try:
        while True:
            dx = target["x"] - pos["x"]
            dy = target["y"] - pos["y"]
            dist = math.hypot(dx, dy)
            driving = dist > 1.0 and bool(args.active_order_id)
            if driving:
                step = min(dist, 8.0)
                pos["x"] += dx / dist * step
                pos["y"] += dy / dist * step
                soc = max(0.05, soc - 0.002)
            payload = build_state(args, header_id, pos["x"], pos["y"], driving, soc)
            client.publish(state_topic, json.dumps(payload), qos=1)
            print(f"state x={pos['x']:.1f} y={pos['y']:.1f} driving={driving} soc={soc:.2f}")
            header_id += 1
            time.sleep(args.interval)
    except KeyboardInterrupt:
        print("stopped")
    finally:
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()
