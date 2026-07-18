"""Fix double-encoded UTF-8 data in MySQL tables (Mojibake)."""
from ssh_helper import connect

client = connect()

# Fix double-encoded UTF-8 in t_park_geofence.fence_name
# The data was stored as UTF-8 but connection charset was latin1, causing double encoding
# Fix: CONVERT(BINARY CONVERT(fence_name USING latin1) USING utf8mb4)

cmds = [
    "docker exec fsd-mysql sh -lc 'MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot fsd_core -e \"SELECT id, fence_name, hex(fence_name) FROM t_park_geofence LIMIT 5;\"'",
    "docker exec fsd-mysql sh -lc 'MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot fsd_core -e \"UPDATE t_park_geofence SET fence_name = CONVERT(BINARY CONVERT(fence_name USING latin1) USING utf8mb4);\"'",
    "docker exec fsd-mysql sh -lc 'MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot fsd_core -e \"SELECT id, fence_name, hex(fence_name) FROM t_park_geofence LIMIT 5;\"'",
]

for i, cmd in enumerate(cmds, 1):
    print(f"\n=== Step {i} ===")
    print(f"$ {cmd[:80]}...")
    stdin, stdout, stderr = client.exec_command(cmd, timeout=60)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    rc = stdout.channel.recv_exit_status()
    if out.strip():
        print(out.rstrip())
    if err.strip():
        print(f"[stderr] {err.rstrip()}")
    print(f"[rc={rc}]")

client.close()
