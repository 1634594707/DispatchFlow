#!/bin/bash
# =====================================================================
# DispatchFlow 服务器初始化脚本（P0-8）
# - 系统更新、时区、基础工具
# - 防火墙、fail2ban
# - Docker + Docker Compose
# - Swap 2GB（P0-6）
# - Docker 日志轮转（P0-11）
# - 部署目录
# 使用方式（root）：
#   bash scripts/init-server.sh
# =====================================================================
set -e

echo "===================================================="
echo "  DispatchFlow 服务器初始化"
echo "===================================================="

# ---------- 1. 系统更新 ----------
echo "[1/9] 更新系统包..."
export DEBIAN_FRONTEND=noninteractive
apt update -y
apt upgrade -y

# ---------- 2. 时区 ----------
echo "[2/9] 设置时区 Asia/Shanghai..."
timedatectl set-timezone Asia/Shanghai

# ---------- 3. 基础工具 ----------
echo "[3/9] 安装基础工具..."
apt install -y \
  curl wget git vim unzip \
  fail2ban ufw \
  ca-certificates gnupg lsb-release \
  software-properties-common \
  apt-transport-https \
  certbot python3-certbot-nginx \
  logrotate

# ---------- 4. 防火墙 ----------
echo "[4/9] 配置 UFW 防火墙..."
ufw --force reset
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp comment 'SSH'
ufw allow 80/tcp comment 'HTTP'
ufw allow 443/tcp comment 'HTTPS'
ufw --force enable

# ---------- 5. fail2ban ----------
echo "[5/9] 配置 fail2ban（SSH 防暴力破解）..."
cat > /etc/fail2ban/jail.local <<'EOF'
[DEFAULT]
bantime = 3600
findtime = 600
maxretry = 5
backend = systemd

[sshd]
enabled = true
port = 22
EOF
systemctl enable fail2ban
systemctl restart fail2ban

# ---------- 6. Docker ----------
echo "[6/9] 安装 Docker..."
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
else
  echo "  Docker 已安装: $(docker --version)"
fi
systemctl enable docker
systemctl start docker

# Docker Compose v2 插件（随 docker 安装包提供）
if ! docker compose version >/dev/null 2>&1; then
  echo "  安装 docker-compose-plugin..."
  apt install -y docker-compose-plugin
fi

# ---------- 7. Swap（P0-6）----------
echo "[7/9] 配置 2GB Swap..."
if [ "$(swapon --show --noheadings | wc -l)" -eq 0 ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  if ! grep -q '/swapfile' /etc/fstab; then
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
  fi
  echo '  Swap 已启用'
else
  echo '  Swap 已存在，跳过'
fi
sysctl vm.swappiness=10

# ---------- 8. Docker 日志轮转（P0-11）----------
echo "[8/9] 配置 Docker 日志轮转..."
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "5"
  },
  "live-restore": true,
  "userland-proxy": false
}
EOF
systemctl restart docker

# ---------- 9. 部署目录 ----------
echo "[9/9] 创建部署目录..."
mkdir -p /opt/dispatchflow /opt/scripts /backup/mysql/dispatchflow

# 拷贝项目脚本（若存在）
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "${SCRIPT_DIR}/backup-mysql.sh" ]; then
  cp "${SCRIPT_DIR}/backup-mysql.sh" /opt/scripts/
  chmod +x /opt/scripts/backup-mysql.sh
fi

# 配置 cron 定时任务
echo "配置 cron 定时任务..."
CRON_MARKER="# dispatchflow-backup"
if ! crontab -l 2>/dev/null | grep -q "$CRON_MARKER"; then
  (crontab -l 2>/dev/null; echo "$CRON_MARKER"; echo "0 2 * * * /opt/scripts/backup-mysql.sh >> /var/log/dispatchflow-backup.log 2>&1") | crontab -
  echo "  MySQL 备份 cron 已添加（每日 02:00）"
fi

# SSL 证书自动续期 cron
CERTBOT_MARKER="# dispatchflow-certbot-renew"
if ! crontab -l 2>/dev/null | grep -q "$CERTBOT_MARKER"; then
  (crontab -l 2>/dev/null; echo "$CERTBOT_MARKER"; echo "0 3 * * * certbot renew --quiet --renew-hook 'docker exec fsd-frontend nginx -s reload'") | crontab -
  echo "  SSL 证书续期 cron 已添加（每日 03:00）"
fi

echo "===================================================="
echo "  初始化完成"
echo "===================================================="
echo "下一步："
echo "  1. 将项目代码上传到 /opt/dispatchflow"
echo "  2. cp /opt/dispatchflow/.env.production /opt/dispatchflow/.env"
echo "  3. 编辑 /opt/dispatchflow/.env 填写实际密码"
echo "  4. 申请 SSL 证书：certbot certonly --standalone -d aplicity.online -d www.aplicity.online -d admin.aplicity.online"
echo "  5. cd /opt/dispatchflow && docker compose -f docker-compose.prod.yml up -d"
echo ""
echo "系统资源："
free -h
echo ""
df -h /
