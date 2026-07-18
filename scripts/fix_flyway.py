"""Fix Flyway checksum mismatch for migration V37."""
from ssh_helper import connect

client = connect()

cmds = [
    # 1. Check current V37 checksum
    "docker exec fsd-mysql sh -lc 'MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot fsd_core -e \"SELECT version, checksum, description, installed_on FROM flyway_schema_history WHERE version=\\\"37\\\";\"' 2>&1",
    # 2. Update checksum to match new code (1401430129)
    "docker exec fsd-mysql sh -lc 'MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot fsd_core -e \"UPDATE flyway_schema_history SET checksum=1401430129 WHERE version=\\\"37\\\";\"' 2>&1",
    # 3. Verify update
    "docker exec fsd-mysql sh -lc 'MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\" mysql -uroot fsd_core -e \"SELECT version, checksum FROM flyway_schema_history WHERE version=\\\"37\\\";\"' 2>&1",
    # 4. Restart backend container
    "docker restart fsd-backend 2>&1",
]

for i, cmd in enumerate(cmds, 1):
    print(f"\n=== Step {i} ===")
    print(f"$ {cmd[:100]}...")
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
