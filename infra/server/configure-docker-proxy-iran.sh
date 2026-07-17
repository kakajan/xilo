#!/usr/bin/env bash
# After WireGuard iran.conf is up, point Docker daemon at DE HTTP proxy.
set -euo pipefail

HTTP_PROXY_URL="${1:?usage: $0 http://user:pass@10.66.66.1:3128}"

mkdir -p /etc/systemd/system/docker.service.d
cat >/etc/systemd/system/docker.service.d/http-proxy.conf <<EOF
[Service]
Environment="HTTP_PROXY=${HTTP_PROXY_URL}"
Environment="HTTPS_PROXY=${HTTP_PROXY_URL}"
Environment="NO_PROXY=localhost,127.0.0.1,aile.ir,brain.aile.ir,.aile.ir"
EOF

systemctl daemon-reload
systemctl restart docker
echo "Docker proxy configured: ${HTTP_PROXY_URL%%@*}@…"
