import time
from scripts.ssh_helper import connect

ssh = connect()

print('=== Waiting 30s for backend to fully start ===')
time.sleep(30)

print('\n=== Backend logs (last 50 lines) ===')
stdin, stdout, stderr = ssh.exec_command('docker logs fsd-backend --tail 50 2>&1')
print(stdout.read().decode()[-3000:])

print('\n=== Testing API ===')
stdin, stdout, stderr = ssh.exec_command('curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/park/stations')
print('API status:', stdout.read().decode())

print('\n=== Testing frontend ===')
stdin, stdout, stderr = ssh.exec_command('curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/')
print('Frontend status:', stdout.read().decode())

print('\n=== Testing mobile order page ===')
stdin, stdout, stderr = ssh.exec_command('curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/mobile/order')
print('Mobile order page status:', stdout.read().decode())

print('\n=== Checking V37 migration ===')
stdin, stdout, stderr = ssh.exec_command('docker exec fsd-mysql mysql -uroot -p$(grep MYSQL_ROOT_PASSWORD /opt/dispatchflow/.env | cut -d= -f2) -e "SELECT version, description, success FROM flyway_schema_history WHERE version=\'37\'" fsd_core 2>/dev/null')
print(stdout.read().decode())

print('\n=== Checking station service_hours field ===')
stdin, stdout, stderr = ssh.exec_command('docker exec fsd-mysql mysql -uroot -p$(grep MYSQL_ROOT_PASSWORD /opt/dispatchflow/.env | cut -d= -f2) -e "SELECT station_code, station_name, service_hours, avg_service_seconds, capacity_limit FROM t_station WHERE station_code LIKE \'ZJF-%\' LIMIT 5" fsd_core 2>/dev/null')
print(stdout.read().decode())

print('\n=== Checking geofence zones ===')
stdin, stdout, stderr = ssh.exec_command('docker exec fsd-mysql mysql -uroot -p$(grep MYSQL_ROOT_PASSWORD /opt/dispatchflow/.env | cut -d= -f2) -e "SELECT fence_code, fence_name FROM t_park_geofence WHERE fence_code LIKE \'ZJF-ZONE-%\'" fsd_core 2>/dev/null')
print(stdout.read().decode())

ssh.close()
print('\n=== Verification complete ===')
