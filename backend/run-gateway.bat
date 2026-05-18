@echo off
cd /d %~dp0
set DATABASE_URL=postgres://xilo:xilo@localhost:5432/xilo?sslmode=disable
set REDIS_URL=localhost:6379
set NATS_URL=nats://localhost:4222
set MEILISEARCH_URL=http://localhost:7700
set MEILISEARCH_KEY=xilo-meili-key
set STORAGE_DRIVER=minio
set STORAGE_ENDPOINT=localhost:9000
set STORAGE_ACCESS_KEY=minioadmin
set STORAGE_SECRET_KEY=minioadmin
set STORAGE_BUCKET=xilo-media
set STORAGE_USE_SSL=false
set JWT_PRIVATE_KEY_PATH=private.pem
set JWT_PUBLIC_KEY_PATH=public.pem
set JWT_ISSUER=xilo
set BASE_URL=http://localhost:3000
set PORT=8000
start /B api-gateway.exe > logs.txt 2>&1
