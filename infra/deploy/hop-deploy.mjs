#!/usr/bin/env node
/**
 * Aile/Xilo deploy via DATA→Edge hop.
 * Scope: /opt/xilo + aile.ir / brain.aile.ir nginx only.
 * Does NOT touch Hesabdaram code, APP/DATA VMs, or other Virtualmin domains.
 *
 * Usage:
 *   node hop-deploy.mjs preflight
 *   node hop-deploy.mjs up
 *   node hop-deploy.mjs status
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync, existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { withEdgeRoot, exec, uploadTar, loadConfig } from "./hop-ssh.mjs";

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, "../..");
const cmd = process.argv[2] || "up";

function toUnixPath(p) {
  return p.replace(/\\/g, "/").replace(/^([A-Za-z]):/, (_, d) => `/${d.toLowerCase()}`);
}

function makeTar() {
  const dir = mkdtempSync(join(tmpdir(), "xilo-deploy-"));
  const tarPath = join(dir, "xilo.tgz");
  const excludes = [
    "--exclude=.git",
    "--exclude=node_modules",
    "--exclude=.next",
    "--exclude=android",
    "--exclude=mobile",
    "--exclude=infra/deploy/.env.deploy",
    "--exclude=infra/proxy/clients",
    "--exclude=infra/proxy/secrets",
    "--exclude=infra/.compose.secrets.env",
    "--exclude=infra/env/prod.env",
    "--exclude=infra/secrets",
    "--exclude=*.tgz",
  ];
  // Git Bash tar treats "D:..." as a remote host — use /d/... paths.
  const r = spawnSync(
    "tar",
    ["-czf", toUnixPath(tarPath), "-C", toUnixPath(REPO_ROOT), ...excludes, "."],
    { stdio: "inherit", shell: false }
  );
  if (r.status !== 0) throw new Error("local tar failed");
  return { dir, tarPath };
}

async function preflight(edge, cfg) {
  console.log("== preflight (Edge, Aile-only) ==");
  const r = await exec(
    edge,
    `
set -e
echo HOST=$(hostname)
echo DISK=$(df -h / | awk 'NR==2{print $3"/"$2" "$5}')
docker --version
docker compose version
echo '--- existing docker (names only; we will only manage project xilo) ---'
docker ps -a --format '{{.Names}}\t{{.Image}}\t{{.Status}}' 2>/dev/null | head -40 || true
echo '--- aile vhosts ---'
ls -la /etc/nginx/sites-available/aile.ir.conf /etc/nginx/sites-available/brain.aile.ir.conf 2>&1
echo '--- ports we need (must be free or already ours) ---'
ss -lntp | grep -E ':(13000|18000)\\b' || echo 'ports 13000/18000 free'
test -d ${cfg.remoteDir} && echo XILO_DIR_EXISTS || echo XILO_DIR_MISSING
# refuse if another compose owns those ports with different project
if ss -lntp | grep -q ':13000'; then
  docker ps --filter publish=13000 --format '{{.Names}}' || true
fi
echo PREFLIGHT_OK
`,
    { timeoutMs: 60000 }
  );
  if (r.code !== 0 || !r.out.includes("PREFLIGHT_OK")) throw new Error("preflight failed");
}

async function ensureSecrets(edge, remoteDir) {
  await exec(
    edge,
    `
set -euo pipefail
mkdir -p ${remoteDir}/infra/secrets/jwt ${remoteDir}/infra/env ${remoteDir}/logs
if [ ! -f ${remoteDir}/infra/secrets/jwt/private.pem ]; then
  openssl genrsa -out ${remoteDir}/infra/secrets/jwt/private.pem 2048
  openssl rsa -in ${remoteDir}/infra/secrets/jwt/private.pem -pubout -out ${remoteDir}/infra/secrets/jwt/public.pem
  chmod 600 ${remoteDir}/infra/secrets/jwt/*.pem
  echo JWT_GENERATED
else
  echo JWT_EXISTS
fi
if [ ! -f ${remoteDir}/infra/.compose.secrets.env ]; then
  PG=$(openssl rand -base64 24 | tr -d '=+/')
  MEILI=$(openssl rand -base64 24 | tr -d '=+/')
  MINIO_U=xilo$(openssl rand -hex 2)
  MINIO_P=$(openssl rand -base64 24 | tr -d '=+/')
  cat > ${remoteDir}/infra/.compose.secrets.env <<EOF
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
COMPOSE_PROJECT_NAME=xilo
EOF
  cat > ${remoteDir}/infra/env/prod.env <<EOF
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
  chmod 600 ${remoteDir}/infra/.compose.secrets.env ${remoteDir}/infra/env/prod.env
  echo SECRETS_GENERATED
else
  echo SECRETS_EXISTS
fi
# ensure project name isolation
grep -q COMPOSE_PROJECT_NAME ${remoteDir}/infra/.compose.secrets.env || echo COMPOSE_PROJECT_NAME=xilo >> ${remoteDir}/infra/.compose.secrets.env
cp -f ${remoteDir}/infra/.compose.secrets.env ${remoteDir}/infra/.env
`,
    { timeoutMs: 120000 }
  );
}

async function sync(edge, cfg) {
  console.log("== sync →", cfg.remoteDir, "==");
  const { dir, tarPath } = makeTar();
  try {
    await uploadTar(edge, tarPath, cfg.remoteDir);
    console.log("upload ok");
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
  await ensureSecrets(edge, cfg.remoteDir);
}

async function composeUp(edge, cfg) {
  console.log("== docker compose up (project xilo only) ==");
  const tag = `deploy-${Date.now()}`;
  const r = await exec(
    edge,
    `
set -euo pipefail
cd ${cfg.remoteDir}/infra
set -a
. ./.compose.secrets.env
set +a
export COMPOSE_PROJECT_NAME=xilo
export XILO_IMAGE_TAG=${tag}
# light hygiene only for unused data — never force-remove other projects' containers
docker container prune -f >/dev/null 2>&1 || true
echo "Building/pulling xilo stack..."
docker compose -p xilo -f docker-compose.prod.yml --env-file .compose.secrets.env up -d --build
echo ${tag} > .current_tag
docker compose -p xilo -f docker-compose.prod.yml --env-file .compose.secrets.env ps
echo COMPOSE_OK
`,
    { timeoutMs: 45 * 60 * 1000 }
  );
  if (r.code !== 0 || !r.out.includes("COMPOSE_OK")) throw new Error("compose up failed");
}

async function applyNginx(edge, cfg) {
  console.log("== nginx aile.ir / brain.aile.ir only ==");
  // Safer apply: only if confs exist; nginx -t before reload; restore bak on failure
  const r = await exec(
    edge,
    `
set -euo pipefail
AILE=/etc/nginx/sites-available/aile.ir.conf
BRAIN=/etc/nginx/sites-available/brain.aile.ir.conf
test -f "$AILE" && test -f "$BRAIN"
# snapshot once per run
TS=$(date +%s)
cp -a "$AILE" "/root/aile.ir.conf.bak.xilo.$TS"
cp -a "$BRAIN" "/root/brain.aile.ir.conf.bak.xilo.$TS"
bash ${cfg.remoteDir}/infra/server/apply-nginx-proxy.sh ${cfg.remoteDir}/infra/nginx
echo NGINX_OK
`,
    { timeoutMs: 60000 }
  );
  if (r.code !== 0 || !r.out.includes("NGINX_OK")) throw new Error("nginx apply failed");
}

async function smoke(edge) {
  console.log("== smoke ==");
  await exec(
    edge,
    `
sleep 5
curl -sS -o /dev/null -w 'local_web:%{http_code}\\n' http://127.0.0.1:13000/ || true
curl -sS -o /dev/null -w 'local_api:%{http_code}\\n' http://127.0.0.1:18000/health || true
curl -sS -o /dev/null -w 'aile:%{http_code}\\n' https://aile.ir/ || true
curl -sS -o /dev/null -w 'brain:%{http_code}\\n' https://brain.aile.ir/health || true
docker compose -p xilo -f /opt/xilo/infra/docker-compose.prod.yml --env-file /opt/xilo/infra/.compose.secrets.env ps
`,
    { timeoutMs: 60000 }
  );
}

async function status(edge, cfg) {
  await exec(
    edge,
    `
df -h /
docker compose -p xilo -f ${cfg.remoteDir}/infra/docker-compose.prod.yml --env-file ${cfg.remoteDir}/infra/.compose.secrets.env ps 2>/dev/null || echo 'stack not up'
ss -lntp | grep -E ':(13000|18000)\\b' || true
curl -sS -o /dev/null -w 'aile:%{http_code}\\n' https://aile.ir/ || true
curl -sS -o /dev/null -w 'brain:%{http_code}\\n' https://brain.aile.ir/health || true
`,
    { timeoutMs: 60000 }
  );
}

async function main() {
  if (!["preflight", "up", "status", "sync"].includes(cmd)) {
    console.log("Usage: node hop-deploy.mjs <preflight|sync|up|status>");
    process.exit(1);
  }
  await withEdgeRoot(async (edge, cfg) => {
    if (cmd === "preflight") return preflight(edge, cfg);
    if (cmd === "status") return status(edge, cfg);
    if (cmd === "sync") {
      await preflight(edge, cfg);
      return sync(edge, cfg);
    }
    // up
    await preflight(edge, cfg);
    await sync(edge, cfg);
    // disk hygiene for xilo paths only (script should be safe)
    if (existsSync(join(REPO_ROOT, "infra/server/disk-hygiene-iran.sh"))) {
      await exec(edge, `bash ${cfg.remoteDir}/infra/server/disk-hygiene-iran.sh || true`, {
        timeoutMs: 120000,
      });
    }
    await composeUp(edge, cfg);
    await applyNginx(edge, cfg);
    await smoke(edge);
    console.log("\nAile deploy finished (scope: /opt/xilo + aile/brain nginx).");
  });
}

main().catch((e) => {
  console.error("ERROR:", e.message || e);
  process.exit(1);
});
