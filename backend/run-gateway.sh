#!/bin/bash
cd "$(dirname "$0")"
export DATABASE_URL="postgres://xilo:xilo@localhost:5432/xilo?sslmode=disable"
export REDIS_URL="localhost:6379"
export NATS_URL="nats://localhost:4222"
export MEILISEARCH_URL="http://localhost:7700"
export MEILISEARCH_KEY="xilo-meili-key"
export STORAGE_DRIVER="minio"
export STORAGE_ENDPOINT="localhost:9000"
export STORAGE_ACCESS_KEY="minioadmin"
export STORAGE_SECRET_KEY="minioadmin"
export STORAGE_BUCKET="xilo-media"
export STORAGE_USE_SSL=false
export JWT_PRIVATE_KEY_PATH="private.pem"
export JWT_PUBLIC_KEY_PATH="public.pem"
export JWT_ISSUER="xilo"
export BASE_URL="http://localhost:3000"
export PORT="8000"
exec ./api-gateway.exe