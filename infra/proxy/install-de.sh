#!/usr/bin/env bash
# Native proxy stack on Germany VPS (NO Docker).
# Host: learn.xilo.ir (Cloudflare Proxied) → this origin.
set -euo pipefail

PROXY_HOST="${PROXY_HOST:-learn.xilo.ir}"
WG_PORT="${WG_PORT:-51820}"
XRAY_WS_PATH="${XRAY_WS_PATH:-/api/v1/updates}"
INSTALL_DIR="${INSTALL_DIR:-/opt/aile-proxy}"
CLIENTS_DIR="${CLIENTS_DIR:-$INSTALL_DIR/clients}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y curl ca-certificates openssl nginx wireguard wireguard-tools qrencode ufw fail2ban jq unzip

mkdir -p "$INSTALL_DIR" "$CLIENTS_DIR" /var/log/aile-proxy
chmod 700 "$INSTALL_DIR" "$CLIENTS_DIR"

# ── Secrets ──
if [[ ! -f "$INSTALL_DIR/secrets.env" ]]; then
  UUID=$(cat /proc/sys/kernel/random/uuid)
  SOCKS_USER="aile$(openssl rand -hex 3)"
  SOCKS_PASS="$(openssl rand -base64 24 | tr -d '=+/')"
  HTTP_USER="pull$(openssl rand -hex 3)"
  HTTP_PASS="$(openssl rand -base64 24 | tr -d '=+/')"
  cat >"$INSTALL_DIR/secrets.env" <<EOF
UUID=$UUID
XRAY_WS_PATH=$XRAY_WS_PATH
SOCKS_USER=$SOCKS_USER
SOCKS_PASS=$SOCKS_PASS
HTTP_USER=$HTTP_USER
HTTP_PASS=$HTTP_PASS
WG_PORT=$WG_PORT
PROXY_HOST=$PROXY_HOST
EOF
  chmod 600 "$INSTALL_DIR/secrets.env"
fi
# shellcheck disable=SC1091
source "$INSTALL_DIR/secrets.env"

# ── Origin TLS for Cloudflare Full (Strict) ──
CERT_DIR=/etc/ssl/aile-proxy
mkdir -p "$CERT_DIR"
if [[ ! -f "$CERT_DIR/fullchain.pem" ]]; then
  openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
    -keyout "$CERT_DIR/privkey.pem" \
    -out "$CERT_DIR/fullchain.pem" \
    -subj "/CN=$PROXY_HOST" \
    -addext "subjectAltName=DNS:$PROXY_HOST"
  # Prefer Cloudflare Origin CA if later uploaded to $CERT_DIR/origin.pem
fi
if [[ -f "$CERT_DIR/origin.pem" && -f "$CERT_DIR/origin.key" ]]; then
  FULLCHAIN="$CERT_DIR/origin.pem"
  PRIVKEY="$CERT_DIR/origin.key"
else
  FULLCHAIN="$CERT_DIR/fullchain.pem"
  PRIVKEY="$CERT_DIR/privkey.pem"
  echo "WARN: using self-signed origin cert. Set Cloudflare SSL to Full (not Strict) or upload Origin CA."
fi

# ── Xray ──
if ! command -v xray >/dev/null 2>&1; then
  bash -c "$(curl -L https://github.com/XTLS/Xray-install/raw/main/install-release.sh)" @ install
fi

cat >/usr/local/etc/xray/config.json <<EOF
{
  "log": { "loglevel": "warning", "access": "/var/log/xray/access.log", "error": "/var/log/xray/error.log" },
  "inbounds": [
    {
      "listen": "127.0.0.1",
      "port": 10086,
      "protocol": "vless",
      "settings": {
        "clients": [{ "id": "$UUID", "level": 0 }],
        "decryption": "none"
      },
      "streamSettings": {
        "network": "ws",
        "wsSettings": { "path": "$XRAY_WS_PATH" }
      }
    },
    {
      "listen": "10.66.66.1",
      "port": 1080,
      "protocol": "socks",
      "settings": {
        "auth": "password",
        "accounts": [{ "user": "$SOCKS_USER", "pass": "$SOCKS_PASS" }],
        "udp": true
      }
    },
    {
      "listen": "10.66.66.1",
      "port": 3128,
      "protocol": "http",
      "settings": {
        "accounts": [{ "user": "$HTTP_USER", "pass": "$HTTP_PASS" }]
      }
    }
  ],
  "outbounds": [{ "protocol": "freedom" }]
}
EOF

systemctl enable xray
# Restart after WireGuard brings up 10.66.66.1 (see below)

# ── Nginx front for Cloudflare → Xray WS ──
cat >/etc/nginx/sites-available/learn.xilo.ir <<EOF
server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name $PROXY_HOST;

    ssl_certificate     $FULLCHAIN;
    ssl_certificate_key $PRIVKEY;
    ssl_protocols TLSv1.2 TLSv1.3;

    # Camouflage landing
    location / {
        default_type text/plain;
        return 200 'ok\\n';
    }

    location $XRAY_WS_PATH {
        proxy_redirect off;
        proxy_pass http://127.0.0.1:10086;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_read_timeout 300s;
    }
}

server {
    listen 80;
    listen [::]:80;
    server_name $PROXY_HOST;
    return 301 https://\$host\$request_uri;
}
EOF

ln -sfn /etc/nginx/sites-available/learn.xilo.ir /etc/nginx/sites-enabled/learn.xilo.ir
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable nginx
systemctl reload nginx

# ── WireGuard ──
WG_DIR=/etc/wireguard
mkdir -p "$WG_DIR"
if [[ ! -f "$WG_DIR/server_private.key" ]]; then
  umask 077
  wg genkey | tee "$WG_DIR/server_private.key" | wg pubkey >"$WG_DIR/server_public.key"
fi
SERVER_PRIV=$(cat "$WG_DIR/server_private.key")
SERVER_PUB=$(cat "$WG_DIR/server_public.key")

# Client peer for operator + Iran app server
gen_peer() {
  local name="$1"
  local ip="$2"
  if [[ ! -f "$WG_DIR/${name}_private.key" ]]; then
    umask 077
    wg genkey | tee "$WG_DIR/${name}_private.key" | wg pubkey >"$WG_DIR/${name}_public.key"
  fi
  local priv pub
  priv=$(cat "$WG_DIR/${name}_private.key")
  pub=$(cat "$WG_DIR/${name}_public.key")
  cat >"$CLIENTS_DIR/${name}.conf" <<PEER
[Interface]
PrivateKey = $priv
Address = $ip/32
DNS = 1.1.1.1

[Peer]
PublicKey = $SERVER_PUB
Endpoint = 95.217.205.246:$WG_PORT
AllowedIPs = 0.0.0.0/0, ::/0
PersistentKeepalive = 25
PEER
  chmod 600 "$CLIENTS_DIR/${name}.conf"
  echo "$pub|$ip"
}

PEER_YOU=$(gen_peer you 10.66.66.2)
PEER_IRAN=$(gen_peer iran 10.66.66.3)
YOU_PUB=${PEER_YOU%%|*}
IRAN_PUB=${PEER_IRAN%%|*}

cat >"$WG_DIR/wg0.conf" <<EOF
[Interface]
Address = 10.66.66.1/24
ListenPort = $WG_PORT
PrivateKey = $SERVER_PRIV
PostUp = iptables -A FORWARD -i wg0 -j ACCEPT; iptables -t nat -A POSTROUTING -o \$(ip route | awk '/default/ {print \$5; exit}') -j MASQUERADE
PostDown = iptables -D FORWARD -i wg0 -j ACCEPT; iptables -t nat -D POSTROUTING -o \$(ip route | awk '/default/ {print \$5; exit}') -j MASQUERADE

[Peer]
PublicKey = $YOU_PUB
AllowedIPs = 10.66.66.2/32

[Peer]
PublicKey = $IRAN_PUB
AllowedIPs = 10.66.66.3/32
EOF
chmod 600 "$WG_DIR/wg0.conf"
sysctl -w net.ipv4.ip_forward=1
grep -q '^net.ipv4.ip_forward=1' /etc/sysctl.conf || echo 'net.ipv4.ip_forward=1' >>/etc/sysctl.conf
systemctl enable wg-quick@wg0
systemctl restart wg-quick@wg0
# HTTP/SOCKS bind to WG address
systemctl restart xray

# ── Firewall ──
ufw --force reset || true
ufw default deny incoming
ufw default allow outgoing
ufw allow 2385/tcp comment 'ssh'
ufw allow 80/tcp
ufw allow 443/tcp
ufw allow "$WG_PORT"/udp
ufw --force enable

# ── journald + logrotate ──
mkdir -p /etc/systemd/journald.conf.d
cat >/etc/systemd/journald.conf.d/size.conf <<'EOF'
[Journal]
SystemMaxUse=200M
MaxRetentionSec=14day
EOF
systemctl restart systemd-journald || true
journalctl --vacuum-size=200M || true

cat >/etc/logrotate.d/aile-proxy <<'EOF'
/var/log/aile-proxy/*.log {
  daily
  rotate 7
  compress
  delaycompress
  missingok
  notifempty
  copytruncate
}
EOF

# ── Client shareables ──
VLESS_URL="vless://${UUID}@${PROXY_HOST}:443?encryption=none&security=tls&type=ws&host=${PROXY_HOST}&path=$(python3 -c "import urllib.parse; print(urllib.parse.quote('$XRAY_WS_PATH', safe=''))")#aile-learn"
cat >"$CLIENTS_DIR/vless.txt" <<EOF
$VLESS_URL
EOF
cat >"$CLIENTS_DIR/http-proxy.txt" <<EOF
# From Iran / your PC after WireGuard is connected:
HTTP_PROXY=http://${HTTP_USER}:${HTTP_PASS}@10.66.66.1:3128
HTTPS_PROXY=http://${HTTP_USER}:${HTTP_PASS}@10.66.66.1:3128
NO_PROXY=localhost,127.0.0.1,aile.ir,brain.aile.ir
SOCKS5=${SOCKS_USER}:${SOCKS_PASS}@10.66.66.1:1080
EOF
chmod 600 "$CLIENTS_DIR"/*

echo "=== Install complete ==="
echo "VLESS: $CLIENTS_DIR/vless.txt"
echo "WG you: $CLIENTS_DIR/you.conf"
echo "WG iran: $CLIENTS_DIR/iran.conf"
echo "HTTP/SOCKS: $CLIENTS_DIR/http-proxy.txt"
echo "Ensure Cloudflare SSL mode is Full (or Full Strict with Origin CA)."
