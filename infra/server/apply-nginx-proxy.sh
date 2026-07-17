#!/usr/bin/env bash
# Point aile.ir / brain.aile.ir Virtualmin vhosts at local Docker ports.
set -euo pipefail

SNIPPET_DIR="${1:-/opt/xilo/infra/nginx}"
AILE_CONF=/etc/nginx/sites-available/aile.ir.conf
BRAIN_CONF=/etc/nginx/sites-available/brain.aile.ir.conf

backup() {
  local f="$1"
  cp -a "$f" "${f}.bak.$(date +%s)"
}

inject_proxy() {
  local conf="$1"
  local snippet="$2"
  local marker="# XILO_DOCKER_PROXY"
  backup "$conf"
  # Remove previous injection
  if grep -q "$marker" "$conf"; then
    awk -v m="$marker" '
      $0 ~ m" BEGIN" {skip=1; next}
      $0 ~ m" END" {skip=0; next}
      !skip {print}
    ' "$conf" >"${conf}.tmp" && mv "${conf}.tmp" "$conf"
  fi
  # Insert after server_name line (first occurrence)
  awk -v snip="$(cat "$snippet")" -v m="$marker" '
    BEGIN {done=0}
    {
      print
      if (!done && $1=="server_name") {
        print m" BEGIN"
        print snip
        print m" END"
        done=1
      }
    }
  ' "$conf" >"${conf}.tmp" && mv "${conf}.tmp" "$conf"
}

inject_proxy "$AILE_CONF" "$SNIPPET_DIR/aile.ir.proxy.snippet.conf"
inject_proxy "$BRAIN_CONF" "$SNIPPET_DIR/brain.aile.ir.proxy.snippet.conf"

nginx -t
systemctl reload nginx
echo "nginx reloaded with Xilo proxy snippets"
