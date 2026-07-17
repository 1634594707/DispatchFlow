import paramiko
import sys

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('64.90.12.129', port=22, username='root', password='XMnYC5wGyVz5', timeout=15)

commands = [
    'ls -la /opt/dispatchflow/',
    'cat /opt/dispatchflow/docker-compose.prod.yml 2>/dev/null || echo "no compose file"',
    'docker ps --format "table {{.Names}}\\t{{.Status}}\\t{{.Ports}}"',
    'ls -la /opt/dispatchflow/back/ 2>/dev/null || echo "no back dir"',
    'ls -la /opt/dispatchflow/front/dist/ 2>/dev/null || echo "no front dist"',
]

for cmd in commands:
    print(f'=== {cmd} ===')
    stdin, stdout, stderr = ssh.exec_command(cmd)
    out = stdout.read().decode()
    err = stderr.read().decode()
    if out:
        print(out)
    if err:
        print('STDERR:', err)
    print()

ssh.close()
