#!/usr/bin/env node
/**
 * Xilo production deploy CLI.
 * Usage: node deploy.mjs <doctor|up|sync|logs|prune|rollback|proxy-install> [--dry-run]
 */
import { spawn, execFileSync } from "node:child_process";
import { existsSync, mkdirSync, readFileSync, chmodSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, "../..");
const INFRA = join(REPO_ROOT, "infra");
const ENV_PATH = join(__dirname, ".env.deploy");

const require = createRequire(import.meta.url);
try {
  require("dotenv").config({ path: ENV_PATH });
} catch {
  // optional until npm install
  if (existsSync(ENV_PATH)) {
    for (const line of readFileSync(ENV_PATH, "utf8").split(/\r?\n/)) {
      const m = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$/);
      if (!m || m[1].startsWith("#")) continue;
      let v = m[2].trim();
      if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
        v = v.slice(1, -1);
      }
      process.env[m[1]] ??= v;
    }
  }
}

const dryRun = process.argv.includes("--dry-run");
const cmd = process.argv.slice(2).find((a) => !a.startsWith("-"));

function requireEnv(name) {
  const v = process.env[name];
  if (!v) throw new Error(`Missing ${name} in .env.deploy`);
  return v;
}

function sshArgs(target = "iran") {
  const host = target === "de" ? requireEnv("DE_SSH_HOST") : requireEnv("SSH_HOST");
  const port = target === "de" ? process.env.DE_SSH_PORT || "2385" : process.env.SSH_PORT || "2212";
  const user = target === "de" ? process.env.DE_SSH_USER || "root" : process.env.SSH_USER || "root";
  const key = target === "de" ? process.env.DE_SSH_KEY_PATH || process.env.SSH_KEY_PATH : process.env.SSH_KEY_PATH;
  const args = ["-p", String(port), "-o", "BatchMode=yes", "-o", "StrictHostKeyChecking=accept-new"];
  if (key) args.push("-i", key, "-o", "IdentitiesOnly=yes");
  args.push(`${user}@${host}`);
  return args;
}

function run(bin, args, opts = {}) {
  if (dryRun) {
    console.log(`[dry-run] ${bin} ${args.join(" ")}`);
    return "";
  }
  const out = execFileSync(bin, args, {
    encoding: "utf8",
    stdio: opts.inherit ? "inherit" : ["ignore", "pipe", "pipe"],
    cwd: opts.cwd || REPO_ROOT,
    env: { ...process.env, ...(opts.env || {}) },
    maxBuffer: 32 * 1024 * 1024,
  });
  return typeof out === "string" ? out : "";
}

function ssh(remoteCmd, target = "iran") {
  const args = [...sshArgs(target), remoteCmd];
  console.log(`→ ssh ${target}: ${remoteCmd.slice(0, 120)}${remoteCmd.length > 120 ? "…" : ""}`);
  return run("ssh", args, { inherit: optsInherit(remoteCmd) });
}

function optsInherit(remoteCmd) {
  return remoteCmd.includes("docker compose") || remoteCmd.startsWith("bash ");
}

function scp(local, remotePath, target = "iran") {
  const host = target === "de" ? requireEnv("DE_SSH_HOST") : requireEnv("SSH_HOST");
  const port = target === "de" ? process.env.DE_SSH_PORT || "2385" : process.env.SSH_PORT || "2212";
  const user = target === "de" ? process.env.DE_SSH_USER || "root" : process.env.SSH_USER || "root";
  const key = target === "de" ? process.env.DE_SSH_KEY_PATH || process.env.SSH_KEY_PATH : process.env.SSH_KEY_PATH;
  const args = ["-P", String(port), "-o", "BatchMode=yes"];
  if (key) args.push("-i", key, "-o", "IdentitiesOnly=yes");
  args.push(local, `${user}@${host}:${remotePath}`);
  console.log(`→ scp ${local} → ${remotePath}`);
  return run("scp", args, { inherit: true });
}

function rsyncToRemote(localDir, remoteDir, target = "iran") {
  const host = target === "de" ? requireEnv("DE_SSH_HOST") : requireEnv("SSH_HOST");
  const port = target === "de" ? process.env.DE_SSH_PORT || "2385" : process.env.SSH_PORT || "2212";
  const user = target === "de" ? process.env.DE_SSH_USER || "root" : process.env.SSH_USER || "root";
  const key = target === "de" ? process.env.DE_SSH_KEY_PATH || process.env.SSH_KEY_PATH : process.env.SSH_KEY_PATH;
  const sshCmd = key
    ? `ssh -p ${port} -i ${key} -o IdentitiesOnly=yes -o BatchMode=yes`
    : `ssh -p ${port} -o BatchMode=yes`;
  const args = [
    "-az",
    "--delete",
    "--exclude", ".git",
    "--exclude", "node_modules",
    "--exclude", ".next",
    "--exclude", "android",
    "--exclude", "mobile",
    "--exclude", "infra/deploy/.env.deploy",
    "--exclude", "infra/proxy/clients",
    "--exclude", "infra/proxy/secrets",
    "-e", sshCmd,
    `${localDir}/`,
    `${user}@${host}:${remoteDir}/`,
  ];
  console.log(`→ rsync → ${remoteDir}`);
  try {
    return run("rsync", args, { inherit: true });
  } catch {
    console.log("rsync unavailable; using tar+ssh fallback");
    const tarArgs = [
      ...sshArgs(target),
      `mkdir -p ${remoteDir} && tar -xzf - -C ${remoteDir}`,
    ];
    if (dryRun) {
      console.log("[dry-run] tar pipe");
      return "";
    }
    // Keep the same excludes as rsync — full-tree tar is too large (android/, node_modules/, …).
    const tar = spawn(
      "tar",
      [
        "-czf",
        "-",
        "-C",
        localDir,
        "--exclude=.git",
        "--exclude=node_modules",
        "--exclude=.next",
        "--exclude=android",
        "--exclude=mobile",
        "--exclude=infra/deploy/.env.deploy",
        "--exclude=infra/proxy/clients",
        "--exclude=infra/proxy/secrets",
        "--exclude=**/build",
        "--exclude=**/.gradle",
        ".",
      ],
      { stdio: ["ignore", "pipe", "inherit"] }
    );
    const remote = spawn("ssh", tarArgs, { stdio: [tar.stdout, "inherit", "inherit"] });
    return new Promise((resolveP, reject) => {
      remote.on("close", (code) => (code === 0 ? resolveP("") : reject(new Error(`ssh tar exit ${code}`))));
      tar.on("error", reject);
      remote.on("error", reject);
    });
  }
}

function diskWarn(dfOut) {
  const m = dfOut.match(/(\d+)%\s+\//);
  if (m && Number(m[1]) >= 85) {
    console.warn(`WARN: root filesystem ${m[1]}% full`);
  }
}

async function doctor() {
  console.log("== doctor ==");
  if (!existsSync(ENV_PATH)) throw new Error(`Create ${ENV_PATH} from .env.deploy.example`);
  try {
    chmodSync(ENV_PATH, 0o600);
  } catch { /* windows */ }

  const iran = run("ssh", [...sshArgs("iran"), "echo OK; df -h /; docker --version; docker compose version; test -d /opt/xilo && echo XILO_DIR_OK || echo XILO_DIR_MISSING"]);
  console.log(iran);
  diskWarn(iran);

  try {
    const de = run("ssh", [...sshArgs("de"), "echo OK; df -h /; command -v xray || echo NO_XRAY; command -v docker && echo UNEXPECTED_DOCKER || echo NO_DOCKER_OK"]);
    console.log(de);
  } catch (e) {
    console.warn("Germany SSH failed:", e.message);
  }

  try {
    const curl = run("ssh", [...sshArgs("iran"), "curl -sS -o /dev/null -w '%{http_code}' https://aile.ir/ || true; echo; curl -sS -o /dev/null -w '%{http_code}' https://brain.aile.ir/health || true; echo"]);
    console.log("HTTP smoke:", curl);
  } catch { /* ignore */ }
}

function ensureRemoteSecrets(remoteDir) {
  const gen = `
set -euo pipefail
mkdir -p ${remoteDir}/infra/secrets/jwt ${remoteDir}/infra/env ${remoteDir}/logs
if [ ! -f ${remoteDir}/infra/secrets/jwt/private.pem ]; then
  openssl genrsa -out ${remoteDir}/infra/secrets/jwt/private.pem 2048
  openssl rsa -in ${remoteDir}/infra/secrets/jwt/private.pem -pubout -out ${remoteDir}/infra/secrets/jwt/public.pem
  chmod 600 ${remoteDir}/infra/secrets/jwt/*.pem
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
fi
`;
  ssh(gen);
}

async function syncCode() {
  const remoteDir = process.env.REMOTE_DIR || "/opt/xilo";
  ssh(`mkdir -p ${remoteDir}`);
  await rsyncToRemote(REPO_ROOT, remoteDir, "iran");
  ensureRemoteSecrets(remoteDir);
}

async function composeUp({ build = true } = {}) {
  const remoteDir = process.env.REMOTE_DIR || "/opt/xilo";
  const mode = process.env.BUILD_MODE || "proxy";
  const tag = process.env.XILO_IMAGE_TAG || `deploy-${Date.now()}`;

  if (mode === "local") {
    console.log("BUILD_MODE=local: building images locally…");
    run("docker", ["compose", "-f", "infra/docker-compose.prod.yml", "--env-file", "infra/.compose.secrets.env", "build"], { inherit: true, cwd: REPO_ROOT });
    // Save and load — requires local docker + secrets file; create temp if needed
    console.warn("Local build path expects docker on this machine and infra/.compose.secrets.env");
  }

  const buildFlag = build ? "--build" : "";
  const script = `
set -e
cd ${remoteDir}/infra
# load compose secrets
set -a
. ./.compose.secrets.env
set +a
export XILO_IMAGE_TAG=${tag}
# optional docker proxy via WG
if [ -f /etc/systemd/system/docker.service.d/http-proxy.conf ]; then
  systemctl daemon-reload || true
fi
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env up -d ${buildFlag}
bash ${remoteDir}/infra/server/apply-nginx-proxy.sh ${remoteDir}/infra/nginx || true
curl -sS -o /dev/null -w 'aile:%{http_code}\\n' https://aile.ir/ || true
curl -sS -o /dev/null -w 'brain:%{http_code}\\n' https://brain.aile.ir/health || true
echo PREV_TAG=$(cat .prev_tag 2>/dev/null || echo none)
echo ${tag} > .prev_tag
echo CURRENT_TAG=${tag}
`;
  ssh(script);
}

async function up() {
  await syncCode();
  const remoteDir = process.env.REMOTE_DIR || "/opt/xilo";
  ssh(`bash ${remoteDir}/infra/server/disk-hygiene-iran.sh || true`);
  await composeUp({ build: true });
}

async function sync() {
  await syncCode();
  await composeUp({ build: true });
}

/** Upload a locally cross-compiled api-gateway binary and rebuild only that image + compose. */
async function syncBinary() {
  const remoteDir = process.env.REMOTE_DIR || "/opt/xilo";
  const localBin = process.env.API_GATEWAY_BIN || join(REPO_ROOT, "backend", "api-gateway.linux");
  if (!existsSync(localBin)) {
    throw new Error(
      `Missing ${localBin}. Build with: cd backend && CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o api-gateway.linux ./cmd/api-gateway`
    );
  }
  await syncCode();
  scp(localBin, `${remoteDir}/backend/api-gateway`);
  const tag = process.env.XILO_IMAGE_TAG || `deploy-${Date.now()}`;
  ssh(`
set -e
cd ${remoteDir}
docker build -f infra/docker/Dockerfile.api-gateway.runtime -t xilo/api-gateway:${tag} -t xilo/api-gateway:latest backend
cd infra
set -a
. ./.compose.secrets.env
set +a
export XILO_IMAGE_TAG=${tag}
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env up -d --build --no-deps web
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env up -d --no-deps api-gateway
bash ${remoteDir}/infra/server/apply-nginx-proxy.sh ${remoteDir}/infra/nginx || true
# Apply brand + default-admin SQL (idempotent; host files piped into postgres)
. ./.compose.secrets.env
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
  < ${remoteDir}/backend/migrations/000017_platform_brand.up.sql || true
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 \
  < ${remoteDir}/backend/migrations/000018_seed_default_admin.up.sql || true
curl -sS -o /dev/null -w 'aile:%{http_code}\\n' https://aile.ir/ || true
curl -sS -o /dev/null -w 'brain:%{http_code}\\n' https://brain.aile.ir/health || true
echo ${tag} > .prev_tag
echo CURRENT_TAG=${tag}
`);
}

async function logs() {
  const remoteDir = process.env.REMOTE_DIR || "/opt/xilo";
  ssh(`cd ${remoteDir}/infra && docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env logs --tail=200`);
}

async function prune() {
  ssh(`docker container prune -f; docker image prune -af; docker builder prune -af; journalctl --vacuum-size=200M; df -h /`);
}

async function rollback() {
  const remoteDir = process.env.REMOTE_DIR || "/opt/xilo";
  ssh(`
set -e
cd ${remoteDir}/infra
PREV=$(cat .prev_tag 2>/dev/null || true)
if [ -z "$PREV" ] || [ "$PREV" = "none" ]; then echo "No previous tag"; exit 1; fi
set -a; . ./.compose.secrets.env; set +a
export XILO_IMAGE_TAG=$PREV
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env up -d
echo Rolled back to $PREV
`);
}

async function proxyInstall() {
  const local = join(INFRA, "proxy", "install-de.sh");
  scp(local, "/root/install-de.sh", "de");
  ssh("bash /root/install-de.sh", "de");
  const remoteDir = process.env.REMOTE_DIR || "/opt/xilo";
  // Fetch client configs to local gitignored folder
  const out = join(INFRA, "proxy", "clients");
  mkdirSync(out, { recursive: true });
  const host = requireEnv("DE_SSH_HOST");
  const port = process.env.DE_SSH_PORT || "2385";
  const user = process.env.DE_SSH_USER || "root";
  const key = process.env.DE_SSH_KEY_PATH || process.env.SSH_KEY_PATH;
  const args = ["-P", String(port), "-o", "BatchMode=yes"];
  if (key) args.push("-i", key, "-o", "IdentitiesOnly=yes");
  args.push(`${user}@${host}:/opt/aile-proxy/clients/*`, out);
  console.log("→ fetch client configs");
  if (!dryRun) run("scp", args, { inherit: true });
  console.log("Client configs in", out);
}

async function configureDockerProxy() {
  // Point Iran Docker daemon at DE HTTP proxy via WireGuard
  const secrets = ssh("grep HTTP_USER\\|HTTP_PASS /opt/aile-proxy/clients/http-proxy.txt 2>/dev/null || cat /opt/aile-proxy/clients/http-proxy.txt", "de");
  console.log("Configure Iran docker proxy manually after WG is up. Secrets from DE:\n", secrets);
  ssh(`
mkdir -p /etc/systemd/system/docker.service.d
# Placeholder — filled after WG connects to 10.66.66.1:3128
cat > /etc/wireguard/iran.conf <<'EOF' || true
# copy from infra/proxy/clients/iran.conf
EOF
`);
}

const commands = {
  doctor,
  up,
  sync,
  "sync-binary": syncBinary,
  logs,
  prune,
  rollback,
  "proxy-install": proxyInstall,
  "docker-proxy": configureDockerProxy,
};

async function main() {
  if (!cmd || !commands[cmd]) {
    console.log(`Usage: node deploy.mjs <${Object.keys(commands).join("|")}> [--dry-run]`);
    process.exit(cmd ? 1 : 0);
  }
  try {
    await commands[cmd]();
  } catch (e) {
    console.error("ERROR:", e.message || e);
    if (e.stderr) console.error(e.stderr.toString());
    if (e.stdout) console.error(e.stdout.toString());
    process.exit(1);
  }
}

main();
