#!/bin/bash
set -euo pipefail

echo "DispatchFlow: applying SQL migrations from /migrations ..."

for f in $(ls /migrations/V*.sql 2>/dev/null | sort); do
  echo ">> $(basename "$f")"
  mysql --default-character-set=utf8mb4 \
    -uroot -p"${MYSQL_ROOT_PASSWORD}" \
    "${MYSQL_DATABASE}" < "$f"
done

echo "DispatchFlow: migrations complete."
