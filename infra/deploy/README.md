# Xilo deploy CLI (low traffic)

Default path builds app images **on your machine**, packs them as **gzip tarballs**, then
**resumable rsync** to Iran and `docker load`. The server does **not** run `npm ci` /
`go mod download` / `--build` on routine syncs.

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

## Resume after disconnect

If Wi‑Fi/SSH drops mid-upload, **re-run the same command** (e.g. `node deploy.mjs sync`).

| Piece | Behavior |
|-------|----------|
| Local pack | Cached under `.transfer-cache/*.tar.gz` (kept until digest changes) |
| Upload | `rsync -avP --partial --append-verify` continues the file |
| Already on server | Same image digest for `xilo/<svc>:<tag>` → **skip upload** |
| Concurrent sync | `.deploy.lock` blocks a second `sync` while one is running |

Force a re-upload even when digests match:

```bash
node deploy.mjs sync --force-transfer
```

Typical packed sizes (gzip level 1): API ~30–40 MB, web ~90–120 MB — far smaller than
raw `docker save | ssh docker load`.

During upload you get a live line: percent, bar, **done / total**, **remaining**, speed, ETA.
Packing shows bytes written so far (total known after gzip finishes).

## Traffic rules

| Mode | When | Iran download |
|------|------|----------------|
| `BUILD_MODE=transfer` (default) | Local Docker available | Only the compressed app tarball(s) you push |
| `BUILD_MODE=remote` or `--remote-build` | Fallback | High — npm/go/base layers on the VPS |

Dependency images (`postgres`, `redis`, `nats`, `meilisearch`, `minio`) use `pull_policy: missing` and are **never** pulled on sync.

## Requirements for transfer mode

- Docker Desktop (or local Docker engine) on the deploy machine
- For API: Go toolchain recommended (`CGO_ENABLED=0 GOOS=linux` cross-compile); otherwise full Dockerfile build locally
- SSH key auth to Iran (and DE for `proxy-install`)
- `rsync` recommended (Git Bash / WSL). Without it, upload falls back to non-resumable `scp`.

Secrets stay in `.env.deploy` (gitignored). Client proxy configs land in `infra/proxy/clients/` (gitignored).
Local packs live in `.transfer-cache/` (gitignored).
