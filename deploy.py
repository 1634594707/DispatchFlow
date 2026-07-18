"""
DispatchFlow 全量部署脚本（Phase 1-9）

功能：
  1. 通过 git 获取所有已修改 + 未跟踪的源文件
  2. 通过 SFTP 上传到服务器 /opt/dispatchflow（保留目录结构）
  3. 在服务器上重建 backend / frontend Docker 镜像（--no-cache）
  4. 重启容器并等待健康检查
  5. 验证后端 API、前端页面、Flyway 迁移版本

使用：
  python deploy.py
"""

import os
import subprocess
import sys
import time
from scripts.ssh_helper import HOST as SERVER, PORT, USER, connect

# ==================== 服务器配置 ====================
REMOTE_BASE = '/opt/dispatchflow'
LOCAL_BASE = os.path.dirname(os.path.abspath(__file__))

# ==================== 本地不部署的文件 ====================
# 这些文件仅用于本地部署/验证流程，不需要上传到服务器
SKIP_FILES = {
    'deploy.py',
    'deploy_p0p1_fixes.py',
    'verify_deployment.py',
}


def get_changed_files():
    """通过 git 获取所有需要部署的文件（已修改 + 未跟踪）。"""
    files = set()

    # 已修改但未提交的文件（tracked）
    result = subprocess.run(
        ['git', 'diff', '--name-only', 'HEAD'],
        capture_output=True, text=True, cwd=LOCAL_BASE
    )
    if result.returncode != 0:
        print(f'[ERROR] git diff 失败: {result.stderr}')
        sys.exit(1)
    for line in result.stdout.strip().splitlines():
        line = line.strip()
        if line:
            files.add(line.replace('\\', '/'))

    # 未跟踪的文件（untracked）
    result = subprocess.run(
        ['git', 'ls-files', '--others', '--exclude-standard'],
        capture_output=True, text=True, cwd=LOCAL_BASE
    )
    if result.returncode != 0:
        print(f'[ERROR] git ls-files 失败: {result.stderr}')
        sys.exit(1)
    for line in result.stdout.strip().splitlines():
        line = line.strip()
        if line:
            files.add(line.replace('\\', '/'))

    # 过滤：跳过本地部署工具脚本
    filtered = []
    for f in sorted(files):
        basename = os.path.basename(f)
        if basename in SKIP_FILES:
            continue
        local_path = os.path.join(LOCAL_BASE, f.replace('/', os.sep))
        if not os.path.isfile(local_path):
            print(f'  [SKIP] 文件不存在: {f}')
            continue
        filtered.append(f)

    return filtered


def ensure_remote_dir(sftp, remote_dir):
    """递归创建远程目录。"""
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


def upload_file(sftp, local_path, remote_path):
    """上传单个文件。"""
    remote_dir = os.path.dirname(remote_path)
    ensure_remote_dir(sftp, remote_dir)
    sftp.put(local_path, remote_path)


def run_cmd(ssh, cmd, timeout=900):
    """运行远程命令并打印输出。"""
    print(f'\n>>> {cmd}')
    stdin, stdout, stderr = ssh.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode('utf-8', errors='replace')
    err = stderr.read().decode('utf-8', errors='replace')
    exit_code = stdout.channel.recv_exit_status()
    if out:
        # 截断过长输出
        print(out[-5000:] if len(out) > 5000 else out)
    if err:
        print('STDERR:', err[-3000:] if len(err) > 3000 else err)
    print(f'[Exit: {exit_code}]')
    return exit_code, out, err


def main():
    print('=' * 60)
    print('  DispatchFlow 全量部署（Phase 1-9）')
    print('=' * 60)

    # 1. 获取变更文件列表
    print('\n[1/6] 获取变更文件列表...')
    files = get_changed_files()
    print(f'  共 {len(files)} 个文件待上传：')
    for f in files:
        print(f'    - {f}')

    # 2. 连接服务器
    print('\n[2/6] 连接服务器...')
    ssh = connect()
    sftp = ssh.open_sftp()
    print(f'  已连接: {USER}@{SERVER}:{PORT}')

    # 3. 上传文件
    print('\n[3/6] 上传变更文件...')
    success = 0
    failed = 0
    for rel_path in files:
        local_path = os.path.join(LOCAL_BASE, rel_path.replace('/', os.sep))
        remote_path = f'{REMOTE_BASE}/{rel_path}'
        try:
            upload_file(sftp, local_path, remote_path)
            success += 1
            print(f'  [OK] {rel_path}')
        except Exception as e:
            failed += 1
            print(f'  [FAIL] {rel_path}: {e}')

    sftp.close()
    print(f'\n  上传完成: 成功 {success}, 失败 {failed}')
    if failed > 0:
        print('[WARN] 部分文件上传失败，请检查后重试')

    # 4. 重建 Docker 镜像
    print('\n[4/6] 重建 Docker 镜像（--no-cache，可能耗时较长）...')
    print('  [4a] 重建 backend 镜像...')
    code, _, _ = run_cmd(
        ssh,
        f'cd {REMOTE_BASE} && docker compose -f docker-compose.prod.yml build backend --no-cache',
        timeout=1800
    )
    if code != 0:
        print('[ERROR] backend 镜像构建失败')
        ssh.close()
        sys.exit(1)

    print('\n  [4b] 重建 frontend 镜像...')
    code, _, _ = run_cmd(
        ssh,
        f'cd {REMOTE_BASE} && docker compose -f docker-compose.prod.yml build frontend --no-cache',
        timeout=1800
    )
    if code != 0:
        print('[ERROR] frontend 镜像构建失败')
        ssh.close()
        sys.exit(1)

    # 5. 重启容器并等待健康检查
    print('\n[5/6] 重启容器...')
    run_cmd(
        ssh,
        f'cd {REMOTE_BASE} && docker compose -f docker-compose.prod.yml up -d backend frontend',
        timeout=180
    )

    print('\n  等待后端健康检查（最多 5 分钟）...')
    healthy = False
    for i in range(30):
        time.sleep(10)
        code, out, _ = run_cmd(
            ssh,
            'docker inspect --format="{{.State.Health.Status}}" fsd-backend 2>/dev/null || echo "starting"',
            timeout=15
        )
        status = out.strip().split('\n')[-1].strip() if out else 'unknown'
        print(f'  [{i+1}/30] 后端状态: {status}')
        if status == 'healthy':
            healthy = True
            break

    if not healthy:
        print('[WARN] 后端未在 5 分钟内通过健康检查，查看最近日志：')
        run_cmd(ssh, 'docker logs --tail 50 fsd-backend', timeout=30)

    # 6. 验证部署
    print('\n[6/6] 验证部署...')

    print('\n  [6a] 容器状态:')
    run_cmd(ssh, 'docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | head -10', timeout=15)

    print('\n  [6b] 后端健康检查 (/internal/actuator/health):')
    run_cmd(ssh, 'curl -s -o /dev/null -w "HTTP %{http_code}\\n" http://127.0.0.1:8080/internal/actuator/health', timeout=15)

    print('\n  [6c] 后端 API 测试 (/api/park/stations):')
    run_cmd(ssh, 'curl -s -o /dev/null -w "HTTP %{http_code}\\n" http://127.0.0.1:8080/api/park/stations', timeout=15)

    print('\n  [6d] 前端页面测试 (http://127.0.0.1:8081/):')
    run_cmd(ssh, 'curl -s -o /dev/null -w "HTTP %{http_code}\\n" http://127.0.0.1:8081/', timeout=15)

    print('\n  [6e] Flyway 迁移历史（最近 5 条）:')
    run_cmd(
        ssh,
        f'docker exec fsd-mysql mysql -uroot -p$(grep MYSQL_ROOT_PASSWORD {REMOTE_BASE}/.env | cut -d= -f2) '
        f'-e "SELECT version, description, success, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5" '
        f'fsd_core 2>/dev/null',
        timeout=30
    )

    print('\n  [6f] 后端最近日志（最后 30 行）:')
    run_cmd(ssh, 'docker logs --tail 30 fsd-backend 2>&1', timeout=30)

    ssh.close()
    print('\n' + '=' * 60)
    print('  部署完成')
    print('=' * 60)


if __name__ == '__main__':
    main()
