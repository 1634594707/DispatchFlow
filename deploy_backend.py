"""修复后端部署：停止旧容器，重建并启动"""
import paramiko

SERVER = "64.90.12.129"
PORT = 22
USER = "root"
PASSWORD = "XMnYC5wGyVz5"
REMOTE_DIR = "/opt/dispatchflow"

def ssh_exec(ssh, cmd, timeout=600):
    print(f">>> {cmd}")
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    exit_code = stdout.channel.recv_exit_status()
    out = stdout.read().decode('utf-8', errors='replace')
    err = stderr.read().decode('utf-8', errors='replace')
    if out:
        print(out[-3000:] if len(out) > 3000 else out)
    if err:
        print(f"[stderr] {err[-2000:]}" if len(err) > 2000 else f"[stderr] {err}")
    print(f"<<< exit code: {exit_code}")
    return exit_code, out, err

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    print(f"Connecting to {SERVER}:{PORT}...")
    ssh.connect(SERVER, port=PORT, username=USER, password=PASSWORD, timeout=30)
    print("Connected!")

    # 停止旧容器
    ssh_exec(ssh, "docker stop fsd-frontend 2>/dev/null; docker rm fsd-frontend 2>/dev/null; echo done")

    # 重建后端（用新的 docker compose 配置）
    print("\n=== Rebuilding backend ===")
    ssh_exec(ssh, f"cd {REMOTE_DIR}/back && docker compose down 2>/dev/null; echo 'Containers stopped'")

    # 重新构建后端镜像
    ssh_exec(ssh, f"cd {REMOTE_DIR}/back && docker compose build --no-cache fsd-core-server 2>&1 | tail -30", timeout=600)

    # 启动后端
    ssh_exec(ssh, f"cd {REMOTE_DIR}/back && docker compose up -d 2>&1", timeout=120)

    # 等待后端启动
    print("\n=== Waiting for backend to start ===")
    ssh_exec(ssh, "sleep 30 && docker ps --format 'table {{.Names}}\\t{{.Status}}\\t{{.Ports}}'")
    ssh_exec(ssh, "curl -s http://localhost:8080/actuator/health 2>/dev/null || echo 'Backend still starting...'")

    # 重启前端
    print("\n=== Restarting frontend ===")
    ssh_exec(ssh, f"cd {REMOTE_DIR}/front && docker build -t dispatchflow-frontend . 2>&1 | tail -5", timeout=300)
    ssh_exec(ssh, "docker run -d --name fsd-frontend --network host -e NGINX_PORT=8081 dispatchflow-frontend 2>/dev/null || (docker rm -f fsd-frontend && docker run -d --name fsd-frontend --network host -e NGINX_PORT=8081 dispatchflow-frontend)")

    # 最终检查
    print("\n=== Final check ===")
    ssh_exec(ssh, "sleep 5 && docker ps --format 'table {{.Names}}\\t{{.Status}}\\t{{.Ports}}'")
    ssh_exec(ssh, "curl -s http://localhost:8081/ | head -5")
    ssh_exec(ssh, "curl -s http://localhost:8080/actuator/health 2>/dev/null || echo 'Backend not ready'")

    ssh.close()
    print("\n=== Done ===")

if __name__ == "__main__":
    main()
