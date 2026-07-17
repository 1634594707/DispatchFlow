"""Check MySQL charset and table encoding."""
import paramiko

HOST = "64.90.12.129"
PORT = 22
USER = "root"
PASSWORD = "XMnYC5wGyVz5"
MYSQL_PASS = "Fsd_Mysql_2026!Str0ng"

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=30)

cmds = [
    f"docker exec fsd-mysql mysql -uroot -p'{MYSQL_PASS}' -e \"SHOW VARIABLES LIKE 'character_set_%';\"",
    f"docker exec fsd-mysql mysql -uroot -p'{MYSQL_PASS}' -e \"SHOW VARIABLES LIKE 'collation_%';\"",
    f"docker exec fsd-mysql mysql -uroot -p'{MYSQL_PASS}' fsd_core -e \"SHOW CREATE TABLE t_park_geofence;\"",
    f"docker exec fsd-mysql mysql -uroot -p'{MYSQL_PASS}' fsd_core -e \"SELECT id, fence_name, hex(fence_name) FROM t_park_geofence LIMIT 5;\"",
]

for cmd in cmds:
    print(f"\n=== {cmd[:60]}... ===")
    stdin, stdout, stderr = client.exec_command(cmd, timeout=60)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    if out.strip():
        print(out.rstrip())
    if err.strip():
        print(f"[stderr] {err.rstrip()}")

client.close()
