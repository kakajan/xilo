#!/usr/bin/env bash
# Xilo Stack Auto-Healing Watchdog
# Periodically checks API (18000) and Web (13000). If down, auto-recovers via xilo-boot.sh.
set -u

REMOTE_DIR="${REMOTE_DIR:-/opt/xilo}"
BOOT_SCRIPT="$REMOTE_DIR/infra/server/xilo-boot.sh"
LOG_FILE="/var/log/xilo-watchdog.log"
LOCK_FILE="/run/xilo-watchdog.lock"
TAG="xilo-watchdog"

log() {
  local msg="[$(date '+%Y-%m-%d %H:%M:%S')] [$TAG] $*"
  echo "$msg"
  echo "$msg" >> "$LOG_FILE" 2>/dev/null || true
  logger -t "$TAG" "$*" 2>/dev/null || true
}

# Prevent overlapping watchdog executions
exec 200>"$LOCK_FILE"
if ! flock -n 200; then
  exit 0
fi

# Do not trigger recovery if a deploy is currently running (.deploy.lock)
if [[ -f "$REMOTE_DIR/infra/deploy/.deploy.lock" ]]; then
  exit 0
fi

check_endpoints() {
  local api_code web_code
  api_code=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 4 http://127.0.0.1:18000/health 2>/dev/null || echo "000")
  web_code=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 4 http://127.0.0.1:13000 2>/dev/null || echo "000")

  if [[ "$api_code" == "200" && "$web_code" =~ ^(200|301|302|304|307|308)$ ]]; then
    return 0
  fi
  return 1
}

# First check: If healthy, exit silently without writing to log
if check_endpoints; then
  exit 0
fi

# If unhealthy, give it 6 seconds and retry once to prevent false alarms during normal operations
sleep 6
if check_endpoints; then
  exit 0
fi

log "WARNING: Unhealthy endpoints detected (api or web not responding). Triggering auto-recovery..."

if [[ -f "$BOOT_SCRIPT" ]]; then
  /bin/bash "$BOOT_SCRIPT" >> "$LOG_FILE" 2>&1
  status=$?
  if [[ $status -eq 0 ]]; then
    log "SUCCESS: Auto-recovery completed successfully. Stack is back online."
  else
    log "ERROR: Auto-recovery script failed with exit code $status."
  fi
else
  log "ERROR: Boot script $BOOT_SCRIPT not found!"
fi
