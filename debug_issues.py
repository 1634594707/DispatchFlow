"""Diagnose login + amap + delivery zone issues."""
import paramiko

SERVER = '64.90.12.129'
PORT = 22
USER = 'root'
PASSWORD = 'XMnYC5wGyVz5'
MYSQL_PASSWORD = 'Fsd_Mysql_2026!Str0ng'

def run_cmd(ssh, cmd, timeout=120):
    print(f'\n>>> {cmd}')
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode()
    err = stderr.read().decode()
    exit_code = stdout.channel.recv_exit_status()
    if out:
        print(out[-3000:] if len(out) > 3000 else out)
    if err:
        print('STDERR:', err[-1000:] if len(err) > 1000 else err)
    print(f'Exit code: {exit_code}')
    return exit_code, out, err

def main():
    print('=== Connecting to server ===')
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(SERVER, port=PORT, username=USER, password=PASSWORD, timeout=30)

    # 1. Check admin users
    print('\n=== 1. Check admin users ===')
    run_cmd(ssh, f'docker exec fsd-mysql mysql -uroot -p"{MYSQL_PASSWORD}" -e "SELECT id, username, email, status, password_hash FROM t_admin_user LIMIT 5" fsd_core 2>/dev/null')

    # 2. Test login API directly
    print('\n=== 2. Test login API ===')
    run_cmd(ssh, 'curl -s -X POST http://localhost:8080/api/admin/auth/login -H "Content-Type: application/json" -d \'{"username":"admin","password":"admin123"}\' | head -c 500')
    run_cmd(ssh, 'curl -s -X POST http://localhost:8080/api/admin/auth/login -H "Content-Type: application/json" -d \'{"username":"admin","password":"admin"}\' | head -c 500')

    # 3. Check backend logs for login errors
    print('\n=== 3. Backend login logs ===')
    run_cmd(ssh, 'docker logs fsd-backend 2>&1 | grep -i "login\\|auth\\|credential\\|password" | tail -20')

    # 4. Check nginx config for amap proxy
    print('\n=== 4. Nginx config ===')
    run_cmd(ssh, 'docker exec fsd-frontend cat /etc/nginx/conf.d/default.conf 2>/dev/null || docker exec fsd-frontend cat /etc/nginx/nginx.conf 2>/dev/null')

    # 5. Check frontend env for amap key
    print('\n=== 5. Frontend env/config ===')
    run_cmd(ssh, 'docker exec fsd-frontend cat /usr/share/nginx/html/config.js 2>/dev/null | head -30')

    # 6. Check current delivery zone geofences (polygons)
    print('\n=== 6. Current delivery zone geofences ===')
    run_cmd(ssh, f'docker exec fsd-mysql mysql -uroot -p"{MYSQL_PASSWORD}" -e "SELECT fence_code, fence_name, JSON_LENGTH(polygon_json) as points FROM t_park_geofence WHERE fence_code LIKE \'ZJF-ZONE-%\'" fsd_core 2>/dev/null')

    ssh.close()
    print('\n=== Diagnosis complete ===')

if __name__ == '__main__':
    main()
