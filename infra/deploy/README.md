# Xilo deploy CLI (low traffic)

Default path builds app images **on your machine**, then `docker save | ssh docker load` to Iran.
The server does **not** run `npm ci` / `go mod download` / `--build` on routine syncs.

```bash
cd infra/deploy
cp .env.deploy.example .env.deploy   # then edit
npm install

node deploy.mjs doctor

# First boot only (may pull missing postgres/redis/… once if absent)
node deploy.mjs up

# Later updates — transfer only changed app images
node deploy.mjs sync                 # api + web
node deploy.mjs sync-api             # API only (Go binary + tiny runtime image)
node deploy.mjs sync-web             # web only
node deploy.mjs sync --only=api
node deploy.mjs sync-binary          # same as sync-api via local go build

node deploy.mjs rollback             # :previous → :latest
node deploy.mjs prune                # old xilo tags + builder cache (keeps base images)
```

## Traffic rules

| Mode | When | Iran download |
|------|------|----------------|
| `BUILD_MODE=transfer` (default) | Local Docker available | Only the compressed app image tarball you push |
| `BUILD_MODE=remote` or `--remote-build` | Fallback | High — npm/go/base layers on the VPS |

Dependency images (`postgres`, `redis`, `nats`, `meilisearch`, `minio`) use `pull_policy: missing` and are **never** pulled on sync.

## Requirements for transfer mode

- Docker Desktop (or local Docker engine) on the deploy machine
- For API: Go toolchain recommended (`CGO_ENABLED=0 GOOS=linux` cross-compile); otherwise full Dockerfile build locally
- SSH key auth to Iran (and DE for `proxy-install`)

Secrets stay in `.env.deploy` (gitignored). Client proxy configs land in `infra/proxy/clients/` (gitignored).
