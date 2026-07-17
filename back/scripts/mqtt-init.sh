#!/usr/bin/env bash
# SEC-08 fix: bootstrap Mosquitto password file for authenticated MQTT access.
# Usage:
#   bash back/scripts/mqtt-init.sh
# Reads credentials from environment variables (or .env):
#   MQTT_FMS_USERNAME  (default: dispatchflow-fms)
#   MQTT_FMS_PASSWORD  (required)
#   MQTT_VEHICLE_USERNAME (default: dispatchflow-vehicle)
#   MQTT_VEHICLE_PASSWORD (required)
# After running, restart the mosquitto container so it picks up the new passwd file.

set -euo pipefail

FMS_USER="${MQTT_FMS_USERNAME:-dispatchflow-fms}"
FMS_PASS="${MQTT_FMS_PASSWORD:?MQTT_FMS_PASSWORD is required}"
VEH_USER="${MQTT_VEHICLE_USERNAME:-dispatchflow-vehicle}"
VEH_PASS="${MQTT_VEHICLE_PASSWORD:?MQTT_VEHICLE_PASSWORD is required}"

CONTAINER="${MQTT_CONTAINER:-fsd-mosquitto}"
PASSWD_PATH="/mosquitto/config/passwd"

if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
  echo "[mqtt-init] container ${CONTAINER} is not running; start it first with:"
  echo "  docker compose -f back/docker-compose.mqtt.yml up -d"
  exit 1
fi

echo "[mqtt-init] creating password file in container ${CONTAINER}"
docker exec "${CONTAINER}" sh -c "mosquitto_passwd -c -b '${PASSWD_PATH}' '${FMS_USER}' '${FMS_PASS}'"
docker exec "${CONTAINER}" sh -c "mosquitto_passwd -b '${PASSWD_PATH}' '${VEH_USER}' '${VEH_PASS}'"

echo "[mqtt-init] reloading mosquitto config"
docker exec "${CONTAINER}" sh -c 'kill -HUP 1 || true'

echo "[mqtt-init] done. Test with:"
echo "  mosquitto_sub -h 127.0.0.1 -p 1883 -u '${FMS_USER}' -P '${FMS_PASS}' -t 'uagv/v2/#' -C 1"
