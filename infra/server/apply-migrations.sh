#!/usr/bin/env bash
# Apply pending backend/*.up.sql migrations into Postgres before API traffic.
# Idempotent via schema_migrations(version). Safe to re-run on existing DBs.
set -euo pipefail

ROOT="${1:-/opt/xilo}"
MIG_DIR="${ROOT}/backend/migrations"
COMPOSE_DIR="${ROOT}/infra"
ENV_FILE="${COMPOSE_DIR}/.compose.secrets.env"

if [[ ! -d "$MIG_DIR" ]]; then
  echo "migrations dir missing: $MIG_DIR" >&2
  exit 1
fi
if [[ ! -f "$ENV_FILE" ]]; then
  echo "compose secrets missing: $ENV_FILE" >&2
  exit 1
fi

# shellcheck disable=SC1090
set -a
. "$ENV_FILE"
set +a

cd "$COMPOSE_DIR"

psql_exec() {
  docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env exec -T postgres \
    psql -U "${POSTGRES_USER:-xilo}" -d "${POSTGRES_DB:-xilo}" -v ON_ERROR_STOP=1 "$@"
}

psql_exec -c "CREATE TABLE IF NOT EXISTS schema_migrations (
  version BIGINT PRIMARY KEY,
  applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);"

# Bootstrap: existing DB already has schema but no tracking rows → mark all current files applied.
users_exists="$(psql_exec -tAc "SELECT to_regclass('public.users') IS NOT NULL" | tr -d '[:space:]')"
mig_count="$(psql_exec -tAc "SELECT count(*) FROM schema_migrations" | tr -d '[:space:]')"
if [[ "$users_exists" == "t" && "$mig_count" == "0" ]]; then
  echo "bootstrap schema_migrations from existing database"
  shopt -s nullglob
  for f in "$MIG_DIR"/*.up.sql; do
    base="$(basename "$f")"
    ver="${base%%_*}"
    ver_num=$((10#$ver))
    psql_exec -c "INSERT INTO schema_migrations(version) VALUES (${ver_num}) ON CONFLICT DO NOTHING;"
  done
fi

shopt -s nullglob
for f in "$MIG_DIR"/*.up.sql; do
  base="$(basename "$f")"
  ver="${base%%_*}"
  ver_num=$((10#$ver))
  applied="$(psql_exec -tAc "SELECT 1 FROM schema_migrations WHERE version = ${ver_num}" | tr -d '[:space:]')"
  if [[ "$applied" == "1" ]]; then
    echo "skip ${base}"
    continue
  fi
  echo "apply ${base}"
  psql_exec < "$f"
  psql_exec -c "INSERT INTO schema_migrations(version) VALUES (${ver_num}) ON CONFLICT DO NOTHING;"
done

echo "migrations up to date"
