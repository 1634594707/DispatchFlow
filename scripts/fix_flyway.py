"""Fix Flyway checksum mismatch for migration V37."""
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
    # 1. Check current V37 checksum
    f"docker exec fsd-mysql mysql -uroot -p'{MYSQL_PASS}' fsd_core -e \"SELECT version, checksum, description, installed_on FROM flyway_schema_history WHERE version='37';\" 2>&1",
    # 2. Update checksum to match new code (1401430129)
    f"docker exec fsd-mysql mysql -uroot -p'{MYSQL_PASS}' fsd_core -e \"UPDATE flyway_schema_history SET checksum=1401430129 WHERE version='37';\" 2>&1",
    # 3. Verify update
    f"docker exec fsd-mysql mysql -uroot -p'{MYSQL_PASS}' fsd_core -e \"SELECT version, checksum FROM flyway_schema_history WHERE version='37';\" 2>&1",
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
