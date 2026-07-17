#!/usr/bin/env python3
"""Check Amap API key config on server and test route planning."""
import paramiko
import subprocess
import json

HOST = '64.90.12.129'
PORT = 22
USER = 'root'
PASSWORD = 'XMnYC5wGyVz5'
AMAP_KEY = 'c746963931df5904d0a23d1d1405643a'

def run(client, cmd, timeout=30):
    stdin, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode('utf-8', errors='replace')
    err = stderr.read().decode('utf-8', errors='replace')
    return out, err

def main():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=20)
    print('Connected.\n')

    # Check .env on server
    print('=== Server .env file (Amap keys) ===')
    out, _ = run(client, 'grep -i amap /opt/dispatchflow/.env 2>/dev/null || echo "no .env or no amap keys"')
    print(f'  {out.strip()}')

    # Check docker-compose.prod.yml for Amap env
    print('\n=== docker-compose.prod.yml Amap config ===')
    out, _ = run(client, 'grep -i amap /opt/dispatchflow/docker-compose.prod.yml 2>/dev/null || echo "no amap in compose"')
    print(f'  {out.strip()}')

    # Check if backend has the Amap key
    print('\n=== Backend env (Amap) ===')
    out, _ = run(client, 'docker inspect fsd-backend --format "{{range .Config.Env}}{{println .}}{{end}}" | grep -i amap')
    print(f'  {out.strip()}')

    # Test Amap API directly
    print('\n=== Test Amap driving API ===')
    # Use a simple test: from Nantong DieShiQiao to a nearby point
    # Nantong DieShiQiao: approximately 121.080354,31.961977
    test_url = f'https://restapi.amap.com/v3/direction/driving?key={AMAP_KEY}&origin=121.080354,31.961977&destination=121.090000,31.965000&extensions=base'
    out, _ = run(client, f'curl -s "{test_url}" 2>&1 | head -5')
    print(f'  {out.strip()[:500]}')

    # Check backend route health endpoint
    print('\n=== Backend route health ===')
    out, _ = run(client, 'curl -s http://127.0.0.1:8080/internal/admin/road-route-health 2>&1')
    print(f'  {out.strip()[:500]}')

    # Check backend logs for Amap
    print('\n=== Backend logs (Amap) ===')
    out, _ = run(client, 'docker logs fsd-backend 2>&1 | grep -i "amap\\|driving\\|route\\|fallback" | tail -10')
    print(f'  {out.strip()}')

    client.close()

if __name__ == '__main__':
    main()
