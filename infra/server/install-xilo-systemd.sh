#!/usr/bin/env bash
# Install boot/shutdown integration for the Xilo stack (run as root on Iran).
set -euo pipefail

REMOTE_DIR="${REMOTE_DIR:-/opt/xilo}"
UNIT_SRC="$REMOTE_DIR/infra/server/xilo.service"
BOOT_SRC="$REMOTE_DIR/infra/server/xilo-boot.sh"

if [[ ! -f "$UNIT_SRC" || ! -f "$BOOT_SRC" ]]; then
  echo "ERROR: missing unit/script under $REMOTE_DIR/infra/server/"
  exit 1
fi

chmod +x "$BOOT_SRC"
cp -f "$UNIT_SRC" /etc/systemd/system/xilo.service

# Harden Docker for cleaner restores (merge with existing log limits).
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
  },
  "live-restore": true,
  "default-ulimits": {
    "nofile": {
      "Name": "nofile",
      "Hard": 64000,
      "Soft": 64000
    }
  }
}
EOF

# Keep compose env on :latest so boot never pins a missing deploy-* tag.
if [[ -f $REMOTE_DIR/infra/.compose.secrets.env ]]; then
  if grep -q '^XILO_IMAGE_TAG=' "$REMOTE_DIR/infra/.compose.secrets.env"; then
    sed -i 's/^XILO_IMAGE_TAG=.*/XILO_IMAGE_TAG=latest/' "$REMOTE_DIR/infra/.compose.secrets.env"
  else
    echo 'XILO_IMAGE_TAG=latest' >>"$REMOTE_DIR/infra/.compose.secrets.env"
  fi
  cp -f "$REMOTE_DIR/infra/.compose.secrets.env" "$REMOTE_DIR/infra/.env"
fi

systemctl daemon-reload
systemctl enable xilo.service
# live-restore lets containers survive a docker restart when possible.
systemctl restart docker
sleep 3
systemctl start xilo.service

echo "=== xilo.service ==="
systemctl status xilo.service --no-pager -l || true
echo "installed: xilo.service enabled (After=docker.service)"
echo "daemon.json: live-restore=true"
