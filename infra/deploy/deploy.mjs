#!/usr/bin/env node
/**
 * Xilo production deploy CLI — traffic-minimized by default.
 *
 * Default BUILD_MODE=transfer:
 *   build app images on your machine → docker save | ssh docker load → compose up --no-build
 * Never pulls/rebuilds postgres/redis/nats/meili/minio on sync.
 *
 * Usage:
 *   node deploy.mjs <doctor|up|sync|sync-api|sync-web|sync-binary|logs|prune|rollback|proxy-install>
 *   [--dry-run] [--only=api,web] [--remote-build]
 */
import { spawn, execFileSync } from "node:child_process";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  chmodSync,
  copyFileSync,
  statSync,
  readdirSync,
} from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import { createHash } from "node:crypto";

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, "../..");
const INFRA = join(REPO_ROOT, "infra");
const ENV_PATH = join(__dirname, ".env.deploy");

const require = createRequire(import.meta.url);
try {
  require("dotenv").config({ path: ENV_PATH });
} catch {
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
const forceRemoteBuild = process.argv.includes("--remote-build");
const cmd = process.argv.slice(2).find((a) => !a.startsWith("-"));

const APP_SERVICES = ["api", "web"];
const DEP_IMAGES = [
  "postgres:16-alpine",
  "redis:7-alpine",
  "nats:2.10-alpine",
  "getmeili/meilisearch:v1.9",
  "minio/minio:latest",
  "alpine:3.21",
];

function parseOnly() {
  const arg = process.argv.find((a) => a.startsWith("--only="));
  if (arg) {
    return arg
      .slice("--only=".length)
      .split(",")
      .map((s) => s.trim().toLowerCase())
      .filter((s) => APP_SERVICES.includes(s));
  }
  if (cmd === "sync-api") return ["api"];
  if (cmd === "sync-web") return ["web"];
  if (cmd === "sync-binary") return ["api"];
  const fromEnv = (process.env.DEPLOY_SERVICES || "api,web")
    .split(",")
    .map((s) => s.trim().toLowerCase())
    .filter((s) => APP_SERVICES.includes(s));
  return fromEnv.length ? fromEnv : ["api", "web"];
}

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

function tryRun(bin, args, opts = {}) {
  try {
    return { ok: true, out: run(bin, args, opts) };
  } catch (e) {
    return { ok: false, out: "", error: e };
  }
}

function hasLocalDocker() {
  return tryRun("docker", ["version", "--format", "{{.Server.Version}}"]).ok;
}

function ssh(remoteCmd, target = "iran") {
  const args = [...sshArgs(target), remoteCmd];
  console.log(`→ ssh ${target}: ${remoteCmd.slice(0, 140)}${remoteCmd.length > 140 ? "…" : ""}`);
  return run("ssh", args, { inherit: optsInherit(remoteCmd) });
}

function optsInherit(remoteCmd) {
  return (
    remoteCmd.includes("docker compose") ||
    remoteCmd.includes("docker build") ||
    remoteCmd.includes("docker load") ||
    remoteCmd.startsWith("bash ") ||
    remoteCmd.includes("set -e")
  );
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
    "--exclude", "**/api-gateway.linux",
    "-e", sshCmd,
    `${localDir}/`,
    `${user}@${host}:${remoteDir}/`,
  ];
  console.log(`→ rsync → ${remoteDir}`);
  try {
    return run("rsync", args, { inherit: true });
  } catch {
    console.log("rsync unavailable; using tar+ssh fallback");
    const tarArgs = [...sshArgs(target), `mkdir -p ${remoteDir} && tar -xzf - -C ${remoteDir}`];
    if (dryRun) {
      console.log("[dry-run] tar pipe");
      return "";
    }
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
        "--exclude=**/api-gateway.linux",
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

function imageRef(name, tag) {
  return `xilo/${name}:${tag}`;
}

function resolveBuildMode() {
  if (forceRemoteBuild) return "remote";
  const mode = (process.env.BUILD_MODE || "transfer").toLowerCase();
  if (mode === "proxy" || mode === "remote") return "remote";
  if (mode === "local" || mode === "transfer") {
    if (!hasLocalDocker()) {
      console.warn("WARN: local Docker not available — falling back to BUILD_MODE=remote (high traffic)");
      return "remote";
    }
    return "transfer";
  }
  return mode;
}

function contentTag(services) {
  if (process.env.XILO_IMAGE_TAG && process.env.XILO_IMAGE_TAG !== "latest") {
    return process.env.XILO_IMAGE_TAG;
  }
  const parts = [];
  try {
    parts.push(run("git", ["-C", REPO_ROOT, "rev-parse", "--short", "HEAD"]).trim());
  } catch {
    parts.push("nogit");
  }
  const hash = createHash("sha1");
  for (const s of services) {
    hash.update(s);
    if (s === "api") {
      hashDirFingerprint(hash, join(REPO_ROOT, "backend"), [
        "cmd",
        "internal",
        "pkg",
        "go.mod",
        "go.sum",
      ]);
    }
    if (s === "web") {
      hashDirFingerprint(hash, join(REPO_ROOT, "web"), [
        "app",
        "components",
        "lib",
        "public",
        "package.json",
        "package-lock.json",
        "next.config.ts",
        "next.config.mjs",
        "next.config.js",
        "tsconfig.json",
      ]);
      hash.update(process.env.NEXT_PUBLIC_API_URL || "https://brain.aile.ir");
      hash.update(process.env.NEXT_PUBLIC_WS_URL || "wss://brain.aile.ir");
      hash.update(process.env.NEXT_PUBLIC_URL || "https://aile.ir");
    }
  }
  parts.push(hash.digest("hex").slice(0, 10));
  return `deploy-${parts.join("-")}`;
}

function hashDirFingerprint(hash, root, entries) {
  for (const ent of entries) {
    const p = join(root, ent);
    if (!existsSync(p)) continue;
    hashPath(hash, p);
  }
}

function hashPath(hash, p) {
  let st;
  try {
    st = statSync(p);
  } catch {
    return;
  }
  hash.update(p);
  if (st.isFile()) {
    hash.update(readFileSync(p));
    return;
  }
  if (!st.isDirectory()) return;
  for (const name of readdirSync(p).sort()) {
    if (name === "node_modules" || name === ".next" || name === "vendor" || name === ".git") continue;
    hashPath(hash, join(p, name));
  }
}

async function doctor() {
  console.log("== doctor ==");
  if (!existsSync(ENV_PATH)) throw new Error(`Create ${ENV_PATH} from .env.deploy.example`);
  try {
    chmodSync(ENV_PATH, 0o600);
  } catch {
    /* windows */
  }

  console.log("BUILD_MODE effective:", resolveBuildMode());
  console.log("local Docker:", hasLocalDocker() ? "yes" : "no");

  const iran = run("ssh", [
    ...sshArgs("iran"),
    "echo OK; df -h /; docker --version; docker compose version; test -d /opt/xilo && echo XILO_DIR_OK || echo XILO_DIR_MISSING; command -v vnstat >/dev/null && vnstat -d 2>/dev/null | tail -5 || echo NO_VNSTAT",
  ]);
  console.log(iran);
  diskWarn(iran);

  try {
    const de = run("ssh", [
      ...sshArgs("de"),
      "echo OK; df -h /; command -v xray || echo NO_XRAY; command -v docker && echo UNEXPECTED_DOCKER || echo NO_DOCKER_OK",
    ]);
    console.log(de);
  } catch (e) {
    console.warn("Germany SSH failed:", e.message);
  }

  try {
    const curl = run("ssh", [
      ...sshArgs("iran"),
      "curl -sS -o /dev/null -w '%{http_code}' https://aile.ir/ || true; echo; curl -sS -o /dev/null -w '%{http_code}' https://brain.aile.ir/health || true; echo",
    ]);
    console.log("HTTP smoke:", curl);
  } catch {
    /* ignore */
  }
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

function publicBuildArgs() {
  return {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL || "https://brain.aile.ir",
    NEXT_PUBLIC_WS_URL: process.env.NEXT_PUBLIC_WS_URL || "wss://brain.aile.ir",
    NEXT_PUBLIC_URL: process.env.NEXT_PUBLIC_URL || "https://aile.ir",
  };
}

function buildApiLocal(tag) {
  const bin = join(REPO_ROOT, "backend", "api-gateway.linux");
  const dest = join(REPO_ROOT, "backend", "api-gateway");
  const go = tryRun("go", ["env", "GOROOT"], { cwd: join(REPO_ROOT, "backend") });
  if (go.ok) {
    console.log("→ local Go cross-compile api-gateway (linux/amd64)");
    run(
      "go",
      ["build", "-ldflags=-s -w", "-o", bin, "./cmd/api-gateway"],
      {
        inherit: true,
        cwd: join(REPO_ROOT, "backend"),
        env: {
          ...process.env,
          CGO_ENABLED: "0",
          GOOS: "linux",
          GOARCH: "amd64",
        },
      }
    );
    copyFileSync(bin, dest);
    run(
      "docker",
      [
        "build",
        "-f",
        "infra/docker/Dockerfile.api-gateway.runtime",
        "-t",
        imageRef("api-gateway", tag),
        "-t",
        imageRef("api-gateway", "latest"),
        "backend",
      ],
      { inherit: true }
    );
    return;
  }

  console.log("→ local Docker build api-gateway (full Dockerfile)");
  run(
    "docker",
    [
      "build",
      "-f",
      "infra/docker/Dockerfile.api-gateway.prod",
      "-t",
      imageRef("api-gateway", tag),
      "-t",
      imageRef("api-gateway", "latest"),
      "backend",
    ],
    { inherit: true }
  );
}

function buildWebLocal(tag) {
  const args = publicBuildArgs();
  console.log("→ local Docker build web");
  run(
    "docker",
    [
      "build",
      "-f",
      "infra/docker/Dockerfile.web",
      "--build-arg",
      `NEXT_PUBLIC_API_URL=${args.NEXT_PUBLIC_API_URL}`,
      "--build-arg",
      `NEXT_PUBLIC_WS_URL=${args.NEXT_PUBLIC_WS_URL}`,
      "--build-arg",
      `NEXT_PUBLIC_URL=${args.NEXT_PUBLIC_URL}`,
      "-t",
      imageRef("web", tag),
      "-t",
      imageRef("web", "latest"),
      "web",
    ],
    { inherit: true }
  );
}

function buildLocal(services, tag) {
  if (services.includes("api")) buildApiLocal(tag);
  if (services.includes("web")) buildWebLocal(tag);
}

function transferImages(services, tag) {
  const refs = [];
  if (services.includes("api")) refs.push(imageRef("api-gateway", tag), imageRef("api-gateway", "latest"));
  if (services.includes("web")) refs.push(imageRef("web", tag), imageRef("web", "latest"));
  // Unique refs for save (latest may point at same id as tag — docker save accepts duplicates)
  const unique = [...new Set(refs)];
  console.log(`→ transfer images → Iran: ${unique.join(", ")}`);
  if (dryRun) {
    console.log("[dry-run] docker save | ssh docker load");
    return Promise.resolve();
  }

  return new Promise((resolveP, reject) => {
    const save = spawn("docker", ["save", ...unique], {
      cwd: REPO_ROOT,
      stdio: ["ignore", "pipe", "inherit"],
    });
    const remote = spawn("ssh", [...sshArgs("iran"), "docker load"], {
      stdio: [save.stdout, "inherit", "inherit"],
    });
    let rejected = false;
    const fail = (err) => {
      if (rejected) return;
      rejected = true;
      reject(err);
    };
    save.on("error", fail);
    remote.on("error", fail);
    remote.on("close", (code) => {
      if (rejected) return;
      if (code === 0) resolveP();
      else reject(new Error(`docker load via ssh exited ${code}`));
    });
    save.on("close", (code) => {
      if (code !== 0 && !rejected) fail(new Error(`docker save exited ${code}`));
    });
  });
}

function composeServiceNames(services) {
  const names = [];
  if (services.includes("api")) names.push("api-gateway");
  if (services.includes("web")) names.push("web");
  return names;
}

function snapshotPrevious(services) {
  ssh(`
set -e
${services.includes("web") ? "docker tag xilo/web:latest xilo/web:previous 2>/dev/null || true" : ""}
${services.includes("api") ? "docker tag xilo/api-gateway:latest xilo/api-gateway:previous 2>/dev/null || true" : ""}
echo snapshot_ok
`);
}

function remotePostDeploy(remoteDir, services, tag) {
  const svc = composeServiceNames(services).join(" ");
  const migrate = services.includes("api")
    ? `
chmod +x ${remoteDir}/infra/server/apply-migrations.sh || true
bash ${remoteDir}/infra/server/apply-migrations.sh ${remoteDir}
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env up -d --no-build --pull never --no-deps --force-recreate api-gateway
`
    : "";

  ssh(`
set -e
cd ${remoteDir}/infra
set -a
. ./.compose.secrets.env
set +a
export XILO_IMAGE_TAG=${tag}

# Ensure tag aliases after docker load
${services.includes("web") ? `docker tag xilo/web:${tag} xilo/web:latest 2>/dev/null || true` : ""}
${services.includes("api") ? `docker tag xilo/api-gateway:${tag} xilo/api-gateway:latest 2>/dev/null || true` : ""}

# App only — never rebuild/pull deps
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env up -d --no-build --pull never --no-deps ${svc}
${migrate}
bash ${remoteDir}/infra/server/apply-nginx-proxy.sh ${remoteDir}/infra/nginx || true
curl -sS -o /dev/null -w 'aile:%{http_code}\\n' https://aile.ir/ || true
curl -sS -o /dev/null -w 'brain:%{http_code}\\n' https://brain.aile.ir/health || true

echo ${tag} > .prev_tag
echo CURRENT_TAG=${tag}

# Keep latest + previous + current tag; drop other xilo app tags (no base-image wipe)
for repo in xilo/web xilo/api-gateway; do
  docker images "$repo" --format '{{.Repository}}:{{.Tag}}' | while read -r ref; do
    case "$ref" in
      *:latest|*:previous|*:${tag}) ;;
      *) docker rmi "$ref" 2>/dev/null || true ;;
    esac
  done
done
docker image prune -f >/dev/null 2>&1 || true
docker system df
`);
}

function ensureDepImagesOnce(remoteDir) {
  // Only pull missing dependency images (first boot). Never on routine sync.
  const checks = DEP_IMAGES.map(
    (img) => `docker image inspect ${img} >/dev/null 2>&1 || { echo "PULL_MISSING ${img}"; docker pull ${img}; }`
  ).join("\n");
  ssh(`
set -e
echo "=== ensure dependency images (pull only if missing) ==="
${checks}
cd ${remoteDir}/infra
set -a; . ./.compose.secrets.env; set +a
export XILO_IMAGE_TAG=latest
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env up -d --no-build --pull never postgres redis nats meilisearch minio
`);
}

async function deployTransfer(services, { firstBoot = false } = {}) {
  const remoteDir = process.env.REMOTE_DIR || "/opt/xilo";
  const tag = contentTag(services);
  console.log(`== transfer deploy == services=${services.join(",")} tag=${tag}`);

  await syncCode();
  if (firstBoot) {
    ssh(`bash ${remoteDir}/infra/server/disk-hygiene-iran.sh || true`);
    ensureDepImagesOnce(remoteDir);
  }

  buildLocal(services, tag);
  snapshotPrevious(services); // before load overwrites :latest
  await transferImages(services, tag);
  remotePostDeploy(remoteDir, services, tag);
}

async function deployRemote(services, { firstBoot = false } = {}) {
  const remoteDir = process.env.REMOTE_DIR || "/opt/xilo";
  const tag = contentTag(services);
  const svc = composeServiceNames(services).join(" ");
  console.warn("== REMOTE BUILD (high traffic) ==");
  console.warn("Prefer BUILD_MODE=transfer with local Docker. Use --remote-build only as fallback.");

  await syncCode();
  if (firstBoot) {
    ssh(`bash ${remoteDir}/infra/server/disk-hygiene-iran.sh || true`);
  }

  ssh(`
set -e
cd ${remoteDir}/infra
set -a
. ./.compose.secrets.env
set +a
export XILO_IMAGE_TAG=${tag}

# Snapshot previous
${services.includes("web") ? "docker tag xilo/web:latest xilo/web:previous 2>/dev/null || true" : ""}
${services.includes("api") ? "docker tag xilo/api-gateway:latest xilo/api-gateway:previous 2>/dev/null || true" : ""}

# Build ONLY requested app services; never pull base images if present
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env build --pull=false ${svc}
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env up -d --no-build --pull never --no-deps ${svc}

${
  services.includes("api")
    ? `
chmod +x ${remoteDir}/infra/server/apply-migrations.sh || true
bash ${remoteDir}/infra/server/apply-migrations.sh ${remoteDir}
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env up -d --no-build --pull never --no-deps --force-recreate api-gateway
`
    : ""
}

bash ${remoteDir}/infra/server/apply-nginx-proxy.sh ${remoteDir}/infra/nginx || true
curl -sS -o /dev/null -w 'aile:%{http_code}\\n' https://aile.ir/ || true
curl -sS -o /dev/null -w 'brain:%{http_code}\\n' https://brain.aile.ir/health || true
echo ${tag} > .prev_tag
echo CURRENT_TAG=${tag}

# Drop old deploy tags + build cache from remote builds (keep latest/previous/current)
for repo in xilo/web xilo/api-gateway; do
  docker images "$repo" --format '{{.Repository}}:{{.Tag}}' | while read -r ref; do
    case "$ref" in
      *:latest|*:previous|*:${tag}) ;;
      *) docker rmi "$ref" 2>/dev/null || true ;;
    esac
  done
done
docker builder prune -af >/dev/null 2>&1 || true
docker image prune -f >/dev/null 2>&1 || true
docker system df
`);
}

async function deploy(services, opts = {}) {
  const mode = resolveBuildMode();
  if (mode === "transfer") return deployTransfer(services, opts);
  return deployRemote(services, opts);
}

async function up() {
  await deploy(parseOnly(), { firstBoot: true });
}

async function sync() {
  await deploy(parseOnly(), { firstBoot: false });
}

/** Upload a locally cross-compiled api-gateway binary; runtime image only (tiny). */
async function syncBinary() {
  const remoteDir = process.env.REMOTE_DIR || "/opt/xilo";
  const localBin = process.env.API_GATEWAY_BIN || join(REPO_ROOT, "backend", "api-gateway.linux");
  if (!existsSync(localBin)) {
    console.log("→ building api-gateway.linux locally");
    run(
      "go",
      ["build", "-ldflags=-s -w", "-o", localBin, "./cmd/api-gateway"],
      {
        inherit: true,
        cwd: join(REPO_ROOT, "backend"),
        env: { ...process.env, CGO_ENABLED: "0", GOOS: "linux", GOARCH: "amd64" },
      }
    );
  }
  const tag = contentTag(["api"]);
  await syncCode();
  scp(localBin, `${remoteDir}/backend/api-gateway`);

  // Prefer local runtime image + transfer (uses local alpine cache, not Iran npm/go)
  if (hasLocalDocker() && resolveBuildMode() === "transfer") {
    copyFileSync(localBin, join(REPO_ROOT, "backend", "api-gateway"));
    run(
      "docker",
      [
        "build",
        "-f",
        "infra/docker/Dockerfile.api-gateway.runtime",
        "-t",
        imageRef("api-gateway", tag),
        "-t",
        imageRef("api-gateway", "latest"),
        "backend",
      ],
      { inherit: true }
    );
    snapshotPrevious(["api"]);
    await transferImages(["api"], tag);
    remotePostDeploy(remoteDir, ["api"], tag);
    return;
  }

  ssh(`
set -e
cd ${remoteDir}
docker tag xilo/api-gateway:latest xilo/api-gateway:previous 2>/dev/null || true
docker build --pull=false -f infra/docker/Dockerfile.api-gateway.runtime -t xilo/api-gateway:${tag} -t xilo/api-gateway:latest backend
cd infra
set -a; . ./.compose.secrets.env; set +a
export XILO_IMAGE_TAG=${tag}
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env up -d --no-build --pull never --no-deps api-gateway
chmod +x ${remoteDir}/infra/server/apply-migrations.sh || true
bash ${remoteDir}/infra/server/apply-migrations.sh ${remoteDir}
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env up -d --no-build --pull never --no-deps --force-recreate api-gateway
bash ${remoteDir}/infra/server/apply-nginx-proxy.sh ${remoteDir}/infra/nginx || true
curl -sS -o /dev/null -w 'aile:%{http_code}\\n' https://aile.ir/ || true
curl -sS -o /dev/null -w 'brain:%{http_code}\\n' https://brain.aile.ir/health || true
echo ${tag} > .prev_tag
# prune old api tags only
docker images xilo/api-gateway --format '{{.Repository}}:{{.Tag}}' | while read -r ref; do
  case "$ref" in
    *:latest|*:previous|*:${tag}) ;;
    *) docker rmi "$ref" 2>/dev/null || true ;;
  esac
done
`);
}

async function logs() {
  const remoteDir = process.env.REMOTE_DIR || "/opt/xilo";
  ssh(
    `cd ${remoteDir}/infra && docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env logs --tail=200`
  );
}

async function prune() {
  // Safe prune: dangling + old xilo deploy tags + builder cache. Keep dependency images.
  ssh(`
set -e
docker container prune -f
docker image prune -f
docker builder prune -af
for repo in xilo/web xilo/api-gateway; do
  docker images "$repo" --format '{{.Repository}}:{{.Tag}}' | while read -r ref; do
    case "$ref" in
      *:latest|*:previous) ;;
      *) docker rmi "$ref" 2>/dev/null || true ;;
    esac
  done
done
journalctl --vacuum-size=200M || true
df -h /
docker system df
`);
}

async function rollback() {
  const remoteDir = process.env.REMOTE_DIR || "/opt/xilo";
  ssh(`
set -e
cd ${remoteDir}/infra
set -a; . ./.compose.secrets.env; set +a
if docker image inspect xilo/web:previous >/dev/null 2>&1; then
  docker tag xilo/web:previous xilo/web:latest
fi
if docker image inspect xilo/api-gateway:previous >/dev/null 2>&1; then
  docker tag xilo/api-gateway:previous xilo/api-gateway:latest
fi
export XILO_IMAGE_TAG=latest
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env up -d --no-build --pull never --no-deps api-gateway web
echo Rolled back to :previous
curl -sS -o /dev/null -w 'aile:%{http_code}\\n' https://aile.ir/ || true
curl -sS -o /dev/null -w 'brain:%{http_code}\\n' https://brain.aile.ir/health || true
`);
}

async function proxyInstall() {
  const local = join(INFRA, "proxy", "install-de.sh");
  scp(local, "/root/install-de.sh", "de");
  ssh("bash /root/install-de.sh", "de");
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
  const secrets = ssh(
    "grep HTTP_USER\\|HTTP_PASS /opt/aile-proxy/clients/http-proxy.txt 2>/dev/null || cat /opt/aile-proxy/clients/http-proxy.txt",
    "de"
  );
  console.log("Configure Iran docker proxy manually after WG is up. Secrets from DE:\n", secrets);
}

const commands = {
  doctor,
  up,
  sync,
  "sync-api": () => deploy(["api"]),
  "sync-web": () => deploy(["web"]),
  "sync-binary": syncBinary,
  logs,
  prune,
  rollback,
  "proxy-install": proxyInstall,
  "docker-proxy": configureDockerProxy,
};

async function main() {
  if (!cmd || !commands[cmd]) {
    console.log(`Usage: node deploy.mjs <${Object.keys(commands).join("|")}> [--dry-run] [--only=api,web] [--remote-build]`);
    console.log(`Default BUILD_MODE=transfer (local build → docker load). Avoids Iran npm/go downloads.`);
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
