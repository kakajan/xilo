#!/usr/bin/env bash
# Run from your PC once Iran SSH (and Germany) accept key login again.
set -euo pipefail
cd "$(dirname "$0")"
npm install
node deploy.mjs doctor
node deploy.mjs proxy-install || echo "WARN: Germany proxy-install failed — check DE password/port"
# Sync + bootstrap Iran
node deploy.mjs up
node deploy.mjs doctor
echo "If docker pull fails on Iran, copy infra/proxy/clients/iran.conf to the server, wg-quick up, then:"
echo "  bash /opt/xilo/infra/server/configure-docker-proxy-iran.sh 'http://USER:PASS@10.66.66.1:3128'"
