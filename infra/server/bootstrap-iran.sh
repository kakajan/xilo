#!/usr/bin/env bash
# One-shot bootstrap on Iran as root. Repo must already be at /opt/xilo.
set -euo pipefail

REMOTE_DIR="${REMOTE_DIR:-/opt/xilo}"
cd "$REMOTE_DIR"

echo "== Docker =="
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
# compose plugin
apt-get update -y
apt-get install -y docker-compose-plugin openssl curl || true
usermod -aG docker aile || true
systemctl enable docker
systemctl start docker

bash "$REMOTE_DIR/infra/server/disk-hygiene-iran.sh"

echo "== Secrets =="
mkdir -p "$REMOTE_DIR/infra/secrets/jwt" "$REMOTE_DIR/infra/env" "$REMOTE_DIR/logs"
if [[ ! -f $REMOTE_DIR/infra/secrets/jwt/private.pem ]]; then
  openssl genrsa -out "$REMOTE_DIR/infra/secrets/jwt/private.pem" 2048
  openssl rsa -in "$REMOTE_DIR/infra/secrets/jwt/private.pem" -pubout -out "$REMOTE_DIR/infra/secrets/jwt/public.pem"
  chmod 600 "$REMOTE_DIR/infra/secrets/jwt/"*.pem
fi

if [[ ! -f $REMOTE_DIR/infra/.compose.secrets.env ]]; then
  PG=$(openssl rand -base64 24 | tr -d '=+/')
  MEILI=$(openssl rand -base64 24 | tr -d '=+/')
  MINIO_U=xilo$(openssl rand -hex 2)
  MINIO_P=$(openssl rand -base64 24 | tr -d '=+/')
  cat >"$REMOTE_DIR/infra/.compose.secrets.env" <<EOF
POSTGRES_USER=xilo
POSTGRES_PASSWORD=$PG
POSTGRES_DB=xilo
MEILI_MASTER_KEY=$MEILI
MINIO_ROOT_USER=$MINIO_U
MINIO_ROOT_PASSWORD=$MINIO_P
NEXT_PUBLIC_API_URL=https://brain.aile.ir
NEXT_PUBLIC_WS_URL=wss://brain.aile.ir
NEXT_PUBLIC_URL=https://aile.ir
XILO_IMAGE_TAG=latest
EOF
  cat >"$REMOTE_DIR/infra/env/prod.env" <<EOF
DATABASE_URL=postgres://xilo:$PG@postgres:5432/xilo?sslmode=disable
REDIS_URL=redis:6379
NATS_URL=nats://nats:4222
JWT_ISSUER=xilo
JWT_PRIVATE_KEY_PATH=/secrets/jwt/private.pem
JWT_PUBLIC_KEY_PATH=/secrets/jwt/public.pem
MEILISEARCH_URL=http://meilisearch:7700
MEILISEARCH_KEY=$MEILI
STORAGE_DRIVER=minio
STORAGE_ENDPOINT=minio:9000
STORAGE_PUBLIC_ENDPOINT=brain.aile.ir
STORAGE_ACCESS_KEY=$MINIO_U
STORAGE_SECRET_KEY=$MINIO_P
STORAGE_BUCKET=xilo-media
STORAGE_USE_SSL=false
STORAGE_PUBLIC_USE_SSL=true
SMS_DRIVER=ippanel
SMS_IPPANEL_API_KEY=
SMS_FROM_NUMBER=+983000505
ZARINPAL_MERCHANT_ID=
ZARINPAL_SANDBOX=true
BASE_URL=https://aile.ir
WS_ALLOWED_ORIGINS=https://aile.ir,https://www.aile.ir
PORT=8000
EOF
  chmod 600 "$REMOTE_DIR/infra/.compose.secrets.env" "$REMOTE_DIR/infra/env/prod.env"
fi

# Compose reads .env beside compose file for variable substitution
cp -f "$REMOTE_DIR/infra/.compose.secrets.env" "$REMOTE_DIR/infra/.env"

echo "== Compose up =="
cd "$REMOTE_DIR/infra"
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env up -d --build

echo "== nginx proxy =="
bash "$REMOTE_DIR/infra/server/apply-nginx-proxy.sh" "$REMOTE_DIR/infra/nginx"

echo "== smoke =="
sleep 3
curl -sS -o /dev/null -w 'aile:%{http_code}\n' https://aile.ir/ || true
curl -sS -o /dev/null -w 'brain:%{http_code}\n' https://brain.aile.ir/health || true
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env ps
echo "Bootstrap complete."
