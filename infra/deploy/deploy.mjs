#!/usr/bin/env node
/**
 * Xilo production deploy CLI — traffic-minimized by default.
 *
 * Default BUILD_MODE=transfer:
 *   build app images locally → gzip tarball → resumable rsync → docker load → compose up --no-build
 * Never pulls/rebuilds postgres/redis/nats/meili/minio on sync.
 *
 * Resume: re-run the same sync command after a drop; rsync --partial continues the .tar.gz.
 * Skip: if Iran already has the same image digest for the deploy tag, upload is skipped.
 * Lock: only one deploy.mjs at a time (stale lock auto-cleared if PID is dead).
 *
 * Usage:
 *   node deploy.mjs <doctor|up|sync|sync-api|sync-web|sync-binary|logs|prune|rollback|proxy-install>
 *   [--dry-run] [--only=api,web] [--remote-build] [--force-transfer]
 */
import { spawn, execFileSync } from "node:child_process";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  writeFileSync,
  unlinkSync,
  renameSync,
  chmodSync,
  copyFileSync,
  statSync,
  readdirSync,
  createWriteStream,
} from "node:fs";
import { dirname, join, resolve, basename } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import { createHash } from "node:crypto";
import { createGzip } from "node:zlib";
import { Transform } from "node:stream";
import { pipeline } from "node:stream/promises";

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(__dirname, "../..");
const INFRA = join(REPO_ROOT, "infra");
const ENV_PATH = join(__dirname, ".env.deploy");
const TRANSFER_CACHE = join(__dirname, ".transfer-cache");
const DEPLOY_LOCK = join(__dirname, ".deploy.lock");

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
const forceTransfer = process.argv.includes("--force-transfer");
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

function sshTransport(target = "iran") {
  const host = target === "de" ? requireEnv("DE_SSH_HOST") : requireEnv("SSH_HOST");
  const port = target === "de" ? process.env.DE_SSH_PORT || "2385" : process.env.SSH_PORT || "2212";
  const user = target === "de" ? process.env.DE_SSH_USER || "root" : process.env.SSH_USER || "root";
  const key = target === "de" ? process.env.DE_SSH_KEY_PATH || process.env.SSH_KEY_PATH : process.env.SSH_KEY_PATH;
  const sshCmd = key
    ? `ssh -p ${port} -i ${key} -o IdentitiesOnly=yes -o BatchMode=yes`
    : `ssh -p ${port} -o BatchMode=yes`;
  return { host, port, user, key, sshCmd };
}

function processAlive(pid) {
  if (!pid || !Number.isFinite(pid)) return false;
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

function acquireDeployLock() {
  mkdirSync(__dirname, { recursive: true });
  if (existsSync(DEPLOY_LOCK)) {
    let prev = {};
    try {
      prev = JSON.parse(readFileSync(DEPLOY_LOCK, "utf8"));
    } catch {
      prev = {};
    }
    if (processAlive(prev.pid)) {
      throw new Error(
        `Another deploy is running (pid ${prev.pid}, cmd=${prev.cmd || "?"}, since=${prev.started || "?"}). ` +
          `Stop it or delete ${DEPLOY_LOCK} if stale.`
      );
    }
    console.warn(`→ clearing stale deploy lock (dead pid ${prev.pid || "?"})`);
    try {
      unlinkSync(DEPLOY_LOCK);
    } catch {
      /* ignore */
    }
  }
  writeFileSync(
    DEPLOY_LOCK,
    JSON.stringify({ pid: process.pid, cmd: cmd || "unknown", started: new Date().toISOString() }, null, 2)
  );
  const release = () => {
    try {
      if (existsSync(DEPLOY_LOCK)) {
        const cur = JSON.parse(readFileSync(DEPLOY_LOCK, "utf8"));
        if (cur.pid === process.pid) unlinkSync(DEPLOY_LOCK);
      }
    } catch {
      /* ignore */
    }
  };
  process.on("exit", release);
  process.on("SIGINT", () => {
    release();
    process.exit(130);
  });
  process.on("SIGTERM", () => {
    release();
    process.exit(143);
  });
}

function rsyncToRemote(localDir, remoteDir, target = "iran") {
  const { host, user, sshCmd } = sshTransport(target);
  const args = [
    "-az",
    "--delete",
    "--exclude", ".git",
    "--exclude", "node_modules",
    "--exclude", ".next",
    "--exclude", "android",
    "--exclude", "mobile",
    "--exclude", "infra/deploy/.env.deploy",
    "--exclude", "infra/deploy/.transfer-cache",
    "--exclude", "infra/deploy/.deploy.lock",
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
        "--exclude=infra/deploy/.transfer-cache",
        "--exclude=infra/deploy/.deploy.lock",
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

function formatBytes(n) {
  if (!Number.isFinite(n) || n < 0) return "?";
  if (n < 1024) return `${Math.round(n)} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  if (n < 1024 * 1024 * 1024) return `${(n / (1024 * 1024)).toFixed(1)} MB`;
  return `${(n / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function formatEta(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0 || seconds === Infinity) return "--:--";
  const s = Math.round(seconds);
  const m = Math.floor(s / 60);
  const r = s % 60;
  if (m >= 60) {
    const h = Math.floor(m / 60);
    return `${h}h${String(m % 60).padStart(2, "0")}m`;
  }
  return `${m}:${String(r).padStart(2, "0")}`;
}

function renderProgressLine({ label, done, total, startedAt, baseline = 0 }) {
  const pct = total > 0 ? Math.min(100, (done / total) * 100) : 0;
  const remaining = Math.max(0, total - done);
  const elapsed = Math.max(0.001, (Date.now() - startedAt) / 1000);
  const speed = Math.max(0, done - baseline) / elapsed;
  const eta = speed > 0 ? remaining / speed : NaN;
  const barW = 22;
  const filled = Math.round((pct / 100) * barW);
  const bar = `${"█".repeat(filled)}${"░".repeat(barW - filled)}`;
  return (
    `${label} [${bar}] ${pct.toFixed(1)}%  ` +
    `${formatBytes(done)} / ${formatBytes(total)}  ` +
    `left ${formatBytes(remaining)}  ` +
    `${formatBytes(speed)}/s  ETA ${formatEta(eta)}`
  );
}

function remoteFileSizeBytes(remoteFile, target = "iran") {
  const r = tryRun("ssh", [
    ...sshArgs(target),
    `stat -c%s -- '${remoteFile}' 2>/dev/null || stat -f%z -- '${remoteFile}' 2>/dev/null || echo 0`,
  ]);
  const n = parseInt(String(r.out || "0").trim().split(/\s+/).pop() || "0", 10);
  return Number.isFinite(n) ? n : 0;
}

/** Resumable single-file upload with live total / remaining progress. */
async function rsyncFileToRemote(localFile, remoteFile, target = "iran") {
  const { host, user, sshCmd } = sshTransport(target);
  const remoteDir = remoteFile.replace(/\/[^/]+$/, "");
  ssh(`mkdir -p ${remoteDir}`, target);

  const total = statSync(localFile).size;
  const already = Math.min(total, remoteFileSizeBytes(remoteFile, target));
  const remaining = Math.max(0, total - already);
  console.log(`→ upload ${basename(localFile)} → ${remoteFile}`);
  console.log(
    `   total ${formatBytes(total)} | on server ${formatBytes(already)} | remaining ${formatBytes(remaining)}`
  );

  if (dryRun) {
    console.log(`[dry-run] rsync --partial ${localFile}`);
    return;
  }
  if (remaining === 0 && already === total && total > 0) {
    console.log(`   already complete (${formatBytes(total)})`);
    return;
  }

  const args = [
    "-a",
    "--partial",
    "--append-verify",
    "--info=progress2",
    "-e",
    sshCmd,
    localFile,
    `${user}@${host}:${remoteFile}`,
  ];

  const startedAt = Date.now();
  const baseline = already;

  await new Promise((resolveP, reject) => {
    const child = spawn("rsync", args, {
      cwd: REPO_ROOT,
      stdio: ["ignore", "pipe", "pipe"],
    });

    let lastPrint = 0;
    const print = (done) => {
      const now = Date.now();
      if (now - lastPrint < 200 && done < total) return;
      lastPrint = now;
      process.stdout.write(
        `\r   ${renderProgressLine({ label: "↑", done, total, startedAt, baseline })}   `
      );
    };
    print(already);

    // Prefer parsing rsync progress2; also poll remote size as fallback.
    const onChunk = (buf) => {
      const text = buf.toString("utf8");
      // progress2: " 12,345,678  56%  1.23MB/s    0:01:23" (bytes transferred this run may be absolute file bytes)
      const m = text.match(/(\d[\d,]*)\s+(\d+)%/);
      if (m) {
        const bytes = parseInt(m[1].replace(/,/g, ""), 10);
        if (Number.isFinite(bytes)) print(Math.min(total, Math.max(already, bytes)));
      }
    };
    child.stdout.on("data", onChunk);
    child.stderr.on("data", onChunk);

    const ticker = setInterval(() => {
      try {
        print(Math.min(total, Math.max(already, remoteFileSizeBytes(remoteFile, target))));
      } catch {
        /* ignore poll errors mid-transfer */
      }
    }, 1500);

    child.on("error", (err) => {
      clearInterval(ticker);
      reject(err);
    });
    child.on("close", (code) => {
      clearInterval(ticker);
      if (code === 0) {
        print(total);
        process.stdout.write("\n");
        const secs = ((Date.now() - startedAt) / 1000).toFixed(1);
        console.log(`   upload done ${formatBytes(total)} in ${secs}s`);
        resolveP();
      } else {
        process.stdout.write("\n");
        reject(new Error(`rsync exited ${code}`));
      }
    });
  }).catch(async (e) => {
    console.warn("WARN: rsync failed; falling back to scp (not resumable):", e.message || e);
    await scpWithProgress(localFile, remoteFile, target, total);
  });
}

async function scpWithProgress(localFile, remoteFile, target, total) {
  const { host, port, user, key } = sshTransport(target);
  const startedAt = Date.now();
  console.log(`→ scp ${basename(localFile)} (${formatBytes(total)} total, not resumable)`);

  await new Promise((resolveP, reject) => {
    const args = ["-P", String(port), "-o", "BatchMode=yes"];
    if (key) args.push("-i", key, "-o", "IdentitiesOnly=yes");
    args.push(localFile, `${user}@${host}:${remoteFile}`);

    const child = spawn("scp", args, { stdio: ["ignore", "pipe", "pipe"] });
    let lastPrint = 0;
    const ticker = setInterval(() => {
      const done = Math.min(total, remoteFileSizeBytes(remoteFile, target));
      const now = Date.now();
      if (now - lastPrint < 200) return;
      lastPrint = now;
      process.stdout.write(
        `\r   ${renderProgressLine({ label: "↑", done, total, startedAt, baseline: 0 })}   `
      );
    }, 1000);

    child.on("error", (err) => {
      clearInterval(ticker);
      reject(err);
    });
    child.on("close", (code) => {
      clearInterval(ticker);
      process.stdout.write("\n");
      if (code === 0) {
        console.log(`   scp done ${formatBytes(total)}`);
        resolveP();
      } else reject(new Error(`scp exited ${code}`));
    });
  });
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
        "src",
        "public",
        "package.json",
        "package-lock.json",
        "next.config.ts",
        "next.config.mjs",
        "next.config.js",
        "tsconfig.json",
      ]);
      hashDirFingerprint(hash, join(REPO_ROOT, "infra/docker"), ["Dockerfile.web"]);
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

function dockerImageId(ref) {
  const out = run("docker", ["image", "inspect", "-f", "{{.Id}}", ref]).trim();
  if (!out) throw new Error(`docker image inspect failed for ${ref}`);
  return out;
}

function remoteDockerImageId(ref) {
  const r = tryRun("ssh", [
    ...sshArgs("iran"),
    `docker image inspect -f '{{.Id}}' ${ref} 2>/dev/null || true`,
  ]);
  return (r.out || "").trim();
}

function imageShortId(id) {
  return id.replace(/^sha256:/, "").slice(0, 12);
}

async function dockerSaveGzip(refs, outFile) {
  mkdirSync(dirname(outFile), { recursive: true });
  const partial = `${outFile}.partial`;
  console.log(`→ pack ${basename(outFile)} ← ${refs.join(", ")}`);
  if (dryRun) {
    console.log(`[dry-run] docker save ${refs.join(" ")} | gzip > ${outFile}`);
    return;
  }
  if (existsSync(partial)) {
    try {
      unlinkSync(partial);
    } catch {
      /* ignore */
    }
  }
  const save = spawn("docker", ["save", ...refs], {
    cwd: REPO_ROOT,
    stdio: ["ignore", "pipe", "inherit"],
  });
  const saveDone = new Promise((resolveP, reject) => {
    save.on("error", reject);
    save.on("close", (code) =>
      code === 0 ? resolveP() : reject(new Error(`docker save exited ${code}`))
    );
  });

  const startedAt = Date.now();
  let written = 0;
  let lastPrint = 0;
  const counter = new Transform({
    transform(chunk, _enc, cb) {
      written += chunk.length;
      const now = Date.now();
      if (now - lastPrint >= 200) {
        lastPrint = now;
        const elapsed = Math.max(0.001, (now - startedAt) / 1000);
        const speed = written / elapsed;
        process.stdout.write(
          `\r   ▣ packing  written ${formatBytes(written)}  ${formatBytes(speed)}/s   `
        );
      }
      cb(null, chunk);
    },
  });

  await Promise.all([
    pipeline(save.stdout, createGzip({ level: 1 }), counter, createWriteStream(partial)),
    saveDone,
  ]);
  process.stdout.write("\n");

  // atomic replace
  if (existsSync(outFile)) unlinkSync(outFile);
  renameSync(partial, outFile);
  const size = statSync(outFile).size;
  const secs = ((Date.now() - startedAt) / 1000).toFixed(1);
  console.log(`→ packed ${basename(outFile)} — total ${formatBytes(size)} in ${secs}s`);
}

function cachePaths(imageName, tag, imageId) {
  const short = imageShortId(imageId);
  const base = `${imageName.replace(/\//g, "_")}-${tag}-${short}`;
  return {
    archive: join(TRANSFER_CACHE, `${base}.tar.gz`),
    meta: join(TRANSFER_CACHE, `${base}.id`),
  };
}

async function ensurePackedImage(imageName, tag) {
  const ref = imageRef(imageName, tag);
  const id = dockerImageId(ref);
  const { archive, meta } = cachePaths(imageName, tag, id);
  mkdirSync(TRANSFER_CACHE, { recursive: true });
  if (
    existsSync(archive) &&
    existsSync(meta) &&
    readFileSync(meta, "utf8").trim() === id &&
    statSync(archive).size > 1024
  ) {
    console.log(`→ reuse local cache ${basename(archive)} (${formatBytes(statSync(archive).size)})`);
    return { ref, id, archive, imageName, tag };
  }
  // Drop stale packs for this imageName+tag (different digest)
  for (const name of readdirSync(TRANSFER_CACHE)) {
    if (name.startsWith(`${imageName.replace(/\//g, "_")}-${tag}-`) && name.endsWith(".tar.gz")) {
      try {
        unlinkSync(join(TRANSFER_CACHE, name));
      } catch {
        /* ignore */
      }
      const idFile = name.replace(/\.tar\.gz$/, ".id");
      try {
        unlinkSync(join(TRANSFER_CACHE, idFile));
      } catch {
        /* ignore */
      }
    }
  }
  // Save tagged ref only (not :latest duplicate) — smaller metadata, same layers
  await dockerSaveGzip([ref], archive);
  writeFileSync(meta, id);
  return { ref, id, archive, imageName, tag };
}

async function transferOneImage(imageName, tag, remoteDir) {
  const ref = imageRef(imageName, tag);
  const localId = dockerImageId(ref);
  const remoteId = remoteDockerImageId(ref);

  if (!forceTransfer && remoteId && remoteId === localId) {
    console.log(`→ skip upload ${ref} (already on Iran @ ${imageShortId(localId)})`);
    ssh(
      `docker tag ${ref} xilo/${imageName}:latest 2>/dev/null || true; echo SKIP_OK`
    );
    return { skipped: true, ref, id: localId };
  }
  if (remoteId && remoteId !== localId) {
    console.log(
      `→ remote ${ref} differs (${imageShortId(remoteId)} → ${imageShortId(localId)}); uploading`
    );
  }

  const packed = await ensurePackedImage(imageName, tag);
  const remoteImages = `${remoteDir}/images`;
  const remoteFile = `${remoteImages}/${basename(packed.archive)}`;

  await rsyncFileToRemote(packed.archive, remoteFile, "iran");

  console.log(`→ docker load ${basename(packed.archive)} on Iran`);
  ssh(`
set -e
test -f ${remoteFile}
# size sanity: remote file must match local
REMOTE_SIZE=$(stat -c%s ${remoteFile} 2>/dev/null || stat -f%z ${remoteFile})
LOCAL_SIZE=${statSync(packed.archive).size}
if [ "$REMOTE_SIZE" != "$LOCAL_SIZE" ]; then
  echo "ERROR: remote size $REMOTE_SIZE != local $LOCAL_SIZE — re-run sync to resume"
  exit 1
fi
gunzip -c ${remoteFile} | docker load
docker tag ${ref} xilo/${imageName}:latest
# free VPS disk after successful load (local cache kept for resume/rebuild)
rm -f ${remoteFile}
echo LOADED ${ref}
`);
  return { skipped: false, ref, id: localId, archive: packed.archive };
}

/**
 * Transfer app images to Iran:
 *  - one gzipped tarball per service (api-gateway / web)
 *  - resumable rsync (--partial --append-verify)
 *  - skip when remote already has the same image digest
 */
async function transferImages(services, tag) {
  const remoteDir = process.env.REMOTE_DIR || "/opt/xilo";
  const names = [];
  if (services.includes("api")) names.push("api-gateway");
  if (services.includes("web")) names.push("web");

  console.log(`→ transfer images (gzip+rsync, resumable) tag=${tag} services=${names.join(",")}`);
  if (forceTransfer) console.log("→ --force-transfer: ignoring remote digests");

  for (const name of names) {
    await transferOneImage(name, tag, remoteDir);
  }
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

const LOCKED_COMMANDS = new Set([
  "up",
  "sync",
  "sync-api",
  "sync-web",
  "sync-binary",
]);

async function main() {
  if (!cmd || !commands[cmd]) {
    console.log(
      `Usage: node deploy.mjs <${Object.keys(commands).join("|")}> [--dry-run] [--only=api,web] [--remote-build] [--force-transfer]`
    );
    console.log(
      `Default BUILD_MODE=transfer: local build → gzip → resumable rsync → docker load. Re-run the same command to resume.`
    );
    process.exit(cmd ? 1 : 0);
  }
  try {
    if (LOCKED_COMMANDS.has(cmd)) acquireDeployLock();
    await commands[cmd]();
  } catch (e) {
    console.error("ERROR:", e.message || e);
    if (e.stderr) console.error(e.stderr.toString());
    if (e.stdout) console.error(e.stdout.toString());
    process.exit(1);
  }
}

main();
