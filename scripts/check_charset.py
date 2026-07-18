"""Check MySQL charset and table encoding."""
from ssh_helper import connect

client = connect()

cmds = [
    "docker exec fsd-mysql sh -lc 'MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot -e \"SHOW VARIABLES LIKE \\\"character_set_%\\\";\"'",
    "docker exec fsd-mysql sh -lc 'MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot -e \"SHOW VARIABLES LIKE \\\"collation_%\\\";\"'",
    "docker exec fsd-mysql sh -lc 'MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot fsd_core -e \"SHOW CREATE TABLE t_park_geofence;\"'",
    "docker exec fsd-mysql sh -lc 'MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot fsd_core -e \"SELECT id, fence_name, hex(fence_name) FROM t_park_geofence LIMIT 5;\"'",
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
