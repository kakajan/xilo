#!/usr/bin/env bash
# Start / recover the Xilo compose stack after boot or Docker restart.
# Safe for cron/systemd: never pulls images, never touches volumes.
set -euo pipefail

REMOTE_DIR="${REMOTE_DIR:-/opt/xilo}"
COMPOSE_FILE="$REMOTE_DIR/infra/docker-compose.prod.yml"
ENV_FILE="$REMOTE_DIR/infra/.compose.secrets.env"
LOG_TAG="xilo-boot"

log() { echo "[$LOG_TAG] $*"; logger -t "$LOG_TAG" "$*" 2>/dev/null || true; }

wait_docker() {
  local i
  for i in $(seq 1 60); do
    if docker info >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  log "ERROR: docker not ready after 120s"
  return 1
}

compose() {
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
}

project_ids() {
  docker ps -aq --filter "label=com.docker.compose.project=xilo" 2>/dev/null || true
}

purge_broken_project_containers() {
  local id
  log "purging broken project containers"
  for id in $(project_ids); do
    docker rm -f "$id" 2>/dev/null || true
  done
  # Ghost metadata (RWLayer nil): docker rm fails but ps still lists them.
  if [[ -n "$(project_ids)" ]]; then
    log "ghost containers remain — restarting docker once"
    systemctl restart docker
    sleep 3
    wait_docker
    for id in $(project_ids); do
      docker rm -f "$id" 2>/dev/null || true
    done
  fi
}

stack_healthy() {
  local code
  code=$(curl -sS -o /dev/null -w '%{http_code}' --connect-timeout 2 http://127.0.0.1:18000/health 2>/dev/null || echo 000)
  [[ "$code" == "200" ]]
}

start_stack() {
  cd "$REMOTE_DIR/infra"
  if [[ ! -f "$ENV_FILE" ]]; then
    log "ERROR: missing $ENV_FILE"
    return 1
  fi
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
  # Always prefer :latest on boot. Deploy pins tags in compose env during sync;
  # a stale XILO_IMAGE_TAG (missing image) must not keep the site down after reboot.
  if docker image inspect "xilo/api-gateway:latest" >/dev/null 2>&1 \
    && docker image inspect "xilo/web:latest" >/dev/null 2>&1; then
    export XILO_IMAGE_TAG=latest
  else
    export XILO_IMAGE_TAG="${XILO_IMAGE_TAG:-latest}"
  fi
  compose up -d --no-build --pull never
}

main() {
  if [[ ! -f "$COMPOSE_FILE" ]]; then
    log "ERROR: missing $COMPOSE_FILE"
    exit 1
  fi

  wait_docker
  log "starting compose stack"

  if ! start_stack; then
    log "compose up failed — attempting recovery"
    purge_broken_project_containers
    start_stack
  fi

  local i
  for i in $(seq 1 30); do
    if stack_healthy; then
      log "healthy (api /health 200)"
      exit 0
    fi
    sleep 2
  done

  log "api still unhealthy — recreate project containers"
  purge_broken_project_containers
  start_stack

  for i in $(seq 1 30); do
    if stack_healthy; then
      log "healthy after recreate"
      exit 0
    fi
    sleep 2
  done

  log "ERROR: stack still unhealthy"
  compose ps || true
  exit 1
}

main "$@"
