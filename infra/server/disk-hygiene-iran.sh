#!/usr/bin/env bash
# Disk hygiene + Docker log limits on Iran app server (root).
# IMPORTANT: do NOT prune all images (-af) — that forces multi‑GB re-pulls on next deploy.
set -euo pipefail

echo "=== Disk before ==="
df -h /
docker system df 2>/dev/null || true
journalctl --disk-usage 2>/dev/null || true

mkdir -p /etc/docker
if [[ -f /etc/docker/daemon.json ]]; then
  cp -a /etc/docker/daemon.json "/etc/docker/daemon.json.bak.$(date +%s)"
fi

cat >/etc/docker/daemon.json <<'EOF'
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
EOF

systemctl restart docker || true

mkdir -p /etc/systemd/journald.conf.d
cat >/etc/systemd/journald.conf.d/size.conf <<'EOF'
[Journal]
SystemMaxUse=200M
MaxRetentionSec=14day
EOF
systemctl restart systemd-journald || true
journalctl --vacuum-size=200M || true

cat >/etc/logrotate.d/xilo <<'EOF'
/opt/xilo/logs/*.log
/var/log/virtualmin/aile.ir_*.log
/var/log/virtualmin/brain.aile.ir_*.log {
  daily
  rotate 7
  compress
  delaycompress
  missingok
  notifempty
  copytruncate
}
EOF

# Safe only: stopped containers + dangling layers. Keep base images (postgres/node/…).
docker container prune -f || true
docker image prune -f || true

apt-get autoremove -y || true
apt-get clean || true

cat >/etc/cron.weekly/xilo-disk <<'EOF'
#!/bin/sh
journalctl --vacuum-size=200M >/dev/null 2>&1 || true
docker container prune -f >/dev/null 2>&1 || true
docker image prune -f >/dev/null 2>&1 || true
EOF
chmod +x /etc/cron.weekly/xilo-disk

echo "=== Disk after ==="
df -h /
docker system df 2>/dev/null || true
echo "Done."
