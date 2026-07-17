"""Fix double-encoded UTF-8 data in MySQL tables (Mojibake)."""
import paramiko

HOST = "64.90.12.129"
PORT = 22
USER = "root"
PASSWORD = "XMnYC5wGyVz5"
MYSQL_PASS = "Fsd_Mysql_2026!Str0ng"

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=30)

# Fix double-encoded UTF-8 in t_park_geofence.fence_name
# The data was stored as UTF-8 but connection charset was latin1, causing double encoding
# Fix: CONVERT(BINARY CONVERT(fence_name USING latin1) USING utf8mb4)

cmds = [
    f"docker exec fsd-mysql mysql -uroot -p'{MYSQL_PASS}' fsd_core -e \"SELECT id, fence_name, hex(fence_name) FROM t_park_geofence LIMIT 5;\"",
    f"docker exec fsd-mysql mysql -uroot -p'{MYSQL_PASS}' fsd_core -e \"UPDATE t_park_geofence SET fence_name = CONVERT(BINARY CONVERT(fence_name USING latin1) USING utf8mb4);\"",
    f"docker exec fsd-mysql mysql -uroot -p'{MYSQL_PASS}' fsd_core -e \"SELECT id, fence_name, hex(fence_name) FROM t_park_geofence LIMIT 5;\"",
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
