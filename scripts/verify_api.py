"""Verify backend API endpoints with clean HTTP requests."""
from ssh_helper import connect

client = connect()

# Use heredoc to avoid shell quoting issues
test_script = r'''
echo "=== Login API Test (valid JSON) ==="
curl -s -X POST http://127.0.0.1:8080/api/admin/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"test","password":"Test123!"}'
echo ""

echo "=== Health Endpoint ==="
curl -s http://127.0.0.1:8080/internal/actuator/health
echo ""

echo "=== Frontend Login Page (check no default creds) ==="
curl -s http://127.0.0.1:8081 | grep -c 'admin123'
echo "(0 = no default creds)"

echo "=== SSE without ticket (should be 400) ==="
curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/admin/fleet/telemetry/stream
echo ""

echo "=== Backend container uptime ==="
docker ps --format '{{.Names}}\t{{.Status}}' | grep fsd-backend
'''

stdin, stdout, stderr = client.exec_command(test_script, timeout=30)
out = stdout.read().decode("utf-8", errors="replace")
err = stderr.read().decode("utf-8", errors="replace")
rc = stdout.channel.recv_exit_status()
print(out)
if err.strip():
    print(f"[stderr] {err}")
print(f"[rc={rc}]")
client.close()
