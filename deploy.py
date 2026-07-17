import paramiko
import os
import stat

SERVER = '64.90.12.129'
PORT = 22
USER = 'root'
PASSWORD = 'XMnYC5wGyVz5'
REMOTE_BASE = '/opt/dispatchflow'
LOCAL_BASE = r'd:\Administrator\Desktop\Project\DispatchFlow'

# Files to upload: (local_path, remote_path)
FILES = [
    # Backend - SQL migration
    (r'back\sql\migrations\V37__delivery_zone_partition_and_station_service.sql',
     f'{REMOTE_BASE}/back/sql/migrations/V37__delivery_zone_partition_and_station_service.sql'),
    # Backend - Entity
    (r'back\fsd-dispatch\src\main\java\com\fsd\dispatch\entity\StationEntity.java',
     f'{REMOTE_BASE}/back/fsd-dispatch/src/main/java/com/fsd/dispatch/entity/StationEntity.java'),
    # Backend - VO
    (r'back\fsd-dispatch\src\main\java\com\fsd\dispatch\vo\ParkStationResponse.java',
     f'{REMOTE_BASE}/back/fsd-dispatch/src/main/java/com/fsd/dispatch/vo/ParkStationResponse.java'),
    # Backend - Service
    (r'back\fsd-dispatch\src\main\java\com\fsd\dispatch\service\impl\ParkStationServiceImpl.java',
     f'{REMOTE_BASE}/back/fsd-dispatch/src/main/java/com/fsd/dispatch/service/impl/ParkStationServiceImpl.java'),
    # Backend - PilotForbiddenZones
    (r'back\fsd-dispatch\src\main\java\com\fsd\dispatch\geo\local\PilotForbiddenZones.java',
     f'{REMOTE_BASE}/back/fsd-dispatch/src/main/java/com/fsd/dispatch/geo/local/PilotForbiddenZones.java'),
    # Backend - LocalPilotRoadGraphService
    (r'back\fsd-dispatch\src\main\java\com\fsd\dispatch\geo\local\LocalPilotRoadGraphService.java',
     f'{REMOTE_BASE}/back/fsd-dispatch/src/main/java/com/fsd/dispatch/geo/local/LocalPilotRoadGraphService.java'),
]

# Frontend dist files to upload
FRONT_DIST = os.path.join(LOCAL_BASE, 'front', 'dist')

def upload_file(sftp, local, remote):
    """Upload a single file, creating remote dirs as needed."""
    remote_dir = os.path.dirname(remote).replace('\\', '/')
    parts = remote_dir.split('/')
    path = ''
    for part in parts:
        if not part:
            continue
        path += '/' + part
        try:
            sftp.stat(path)
        except FileNotFoundError:
            sftp.mkdir(path)
    sftp.put(local, remote)
    print(f'  Uploaded: {os.path.basename(local)} -> {remote}')

def upload_dir(sftp, local_dir, remote_dir):
    """Upload a directory recursively."""
    try:
        sftp.stat(remote_dir)
    except FileNotFoundError:
        sftp.mkdir(remote_dir)
    
    for item in os.listdir(local_dir):
        local_path = os.path.join(local_dir, item)
        remote_path = remote_dir + '/' + item
        if os.path.isdir(local_path):
            upload_dir(sftp, local_path, remote_path)
        else:
            sftp.put(local_path, remote_path)
            print(f'  Uploaded: {item}')

def run_cmd(ssh, cmd, timeout=300):
    """Run a command and return output."""
    print(f'\n>>> {cmd}')
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode()
    err = stderr.read().decode()
    exit_code = stdout.channel.recv_exit_status()
    if out:
        print(out[-3000:] if len(out) > 3000 else out)
    if err:
        print('STDERR:', err[-2000:] if len(err) > 2000 else err)
    print(f'Exit code: {exit_code}')
    return exit_code, out, err

def main():
    print('=== Connecting to server ===')
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(SERVER, port=PORT, username=USER, password=PASSWORD, timeout=30)
    sftp = ssh.open_sftp()
    
    print('\n=== Uploading backend files ===')
    for local_rel, remote_path in FILES:
        local_path = os.path.join(LOCAL_BASE, local_rel)
        if os.path.exists(local_path):
            upload_file(sftp, local_path, remote_path)
        else:
            print(f'  SKIP (not found): {local_path}')
    
    print('\n=== Uploading frontend dist ===')
    upload_dir(sftp, FRONT_DIST, f'{REMOTE_BASE}/front/dist')
    
    sftp.close()
    
    print('\n=== Rebuilding backend Docker image ===')
    run_cmd(ssh, f'cd {REMOTE_BASE} && docker compose -f docker-compose.prod.yml build backend --no-cache', timeout=600)
    
    print('\n=== Rebuilding frontend Docker image ===')
    run_cmd(ssh, f'cd {REMOTE_BASE} && docker compose -f docker-compose.prod.yml build frontend --no-cache', timeout=600)
    
    print('\n=== Restarting containers ===')
    run_cmd(ssh, f'cd {REMOTE_BASE} && docker compose -f docker-compose.prod.yml up -d backend frontend', timeout=120)
    
    print('\n=== Waiting for backend to start ===')
    import time
    time.sleep(15)
    
    print('\n=== Checking container status ===')
    run_cmd(ssh, 'docker ps --format "table {{.Names}}\t{{.Status}}" | head -10')
    
    print('\n=== Testing API ===')
    run_cmd(ssh, 'curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/park/stations')
    run_cmd(ssh, 'curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/')
    
    print('\n=== Checking V37 migration applied ===')
    run_cmd(ssh, 'docker exec fsd-mysql mysql -uroot -p$(grep MYSQL_ROOT_PASSWORD /opt/dispatchflow/.env | cut -d= -f2) -e "SELECT version, description, success FROM flyway_schema_history WHERE version=\'37\'" fsd_core 2>/dev/null')
    
    ssh.close()
    print('\n=== Deployment complete ===')

if __name__ == '__main__':
    main()
