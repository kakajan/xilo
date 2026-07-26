#!/usr/bin/env node
/**
 * One-shot low-traffic finish deploy:
 * - API image already on Iran → recreate only (0 MB upload)
 * - Web: pack + upload once + load
 * - Push nginx snippet only + apply
 * No full-repo tar sync.
 */
import { config } from "dotenv";
import {
  createWriteStream,
  existsSync,
  mkdirSync,
  readFileSync,
  renameSync,
  statSync,
  unlinkSync,
  writeFileSync,
} from "node:fs";
import { spawn, execFileSync } from "node:child_process";
import { createGzip } from "node:zlib";
import { pipeline } from "node:stream/promises";
import { Transform } from "node:stream";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(__dirname, "../..");
const CACHE = join(__dirname, ".transfer-cache");
config({ path: join(__dirname, ".env.deploy") });

const WEB_TAG = "deploy-b2a54925-web";
const API_TAG = "deploy-8abea82c-e0c7044e8b"; // already on Iran
const REMOTE = process.env.REMOTE_DIR || "/opt/xilo";

function fmt(n) {
  if (n < 1024) return `${n} B`;
  if (n < 1024 ** 2) return `${(n / 1024).toFixed(1)} KB`;
  if (n < 1024 ** 3) return `${(n / 1024 ** 2).toFixed(1)} MB`;
  return `${(n / 1024 ** 3).toFixed(2)} GB`;
}

function sshArgs() {
  const host = process.env.SSH_HOST;
  const port = process.env.SSH_PORT || "2212";
  const user = process.env.SSH_USER || "root";
  const key = process.env.SSH_KEY_PATH;
  if (!host) throw new Error("SSH_HOST missing");
  const args = [
    "-p",
    String(port),
    "-o",
    "BatchMode=yes",
    "-o",
    "StrictHostKeyChecking=accept-new",
  ];
  if (key) args.push("-i", key, "-o", "IdentitiesOnly=yes");
  args.push(`${user}@${host}`);
  return args;
}

function ssh(cmd, { inherit = true } = {}) {
  const args = [...sshArgs(), cmd];
  console.log(`→ ssh: ${cmd.slice(0, 120)}${cmd.length > 120 ? "…" : ""}`);
  return execFileSync("ssh", args, {
    encoding: "utf8",
    stdio: inherit ? "inherit" : ["ignore", "pipe", "pipe"],
    maxBuffer: 32 * 1024 * 1024,
  });
}

function dockerId(ref) {
  return execFileSync("docker", ["image", "inspect", ref, "--format", "{{.Id}}"], {
    encoding: "utf8",
  }).trim();
}

async function packWeb() {
  mkdirSync(CACHE, { recursive: true });
  const ref = "xilo/web:latest";
  const id = dockerId(ref);
  const short = id.replace(/^sha256:/, "").slice(0, 12);
  const out = join(CACHE, `web-${WEB_TAG}-${short}.tar.gz`);
  const meta = `${out}.id`;
  if (existsSync(out) && existsSync(meta) && readFileSync(meta, "utf8").trim() === id) {
    console.log(`→ reuse pack ${fmt(statSync(out).size)}  ${out}`);
    return { out, id, short };
  }
  const partial = `${out}.partial`;
  if (existsSync(partial)) unlinkSync(partial);
  console.log(`→ packing ${ref} (local only, no upload yet)…`);
  const started = Date.now();
  let written = 0;
  const counter = new Transform({
    transform(chunk, _e, cb) {
      written += chunk.length;
      if (written === chunk.length || written % (8 * 1024 * 1024) < chunk.length) {
        process.stdout.write(`\r   ▣ packing  ${fmt(written)}  ${((written / ((Date.now() - started) / 1000)) / (1024 * 1024)).toFixed(1)} MB/s   `);
      }
      cb(null, chunk);
    },
  });
  const save = spawn("docker", ["save", ref], { stdio: ["ignore", "pipe", "inherit"] });
  const saveDone = new Promise((res, rej) => {
    save.on("error", rej);
    save.on("close", (c) => (c === 0 ? res() : rej(new Error(`docker save exit ${c}`))));
  });
  await Promise.all([
    pipeline(save.stdout, createGzip({ level: 1 }), counter, createWriteStream(partial)),
    saveDone,
  ]);
  process.stdout.write("\n");
  if (existsSync(out)) unlinkSync(out);
  renameSync(partial, out);
  writeFileSync(meta, id);
  console.log(`→ packed ${fmt(statSync(out).size)} in ${((Date.now() - started) / 1000).toFixed(1)}s`);
  return { out, id, short };
}

function scpWithProgress(localFile, remoteFile) {
  const total = statSync(localFile).size;
  console.log(`→ upload ${fmt(total)}  →  ${remoteFile}`);
  console.log(`   budget this step: ${fmt(total)} (web image only; API upload SKIPPED)`);
  // Prefer scp - progress via pv if available, else plain scp
  const args = [
    ...sshArgs().flatMap((a, i, arr) => {
      // rebuild scp args from ssh-style: scp -P port -i key local user@host:remote
      return [];
    }),
  ];
  void args;
  const host = process.env.SSH_HOST;
  const port = process.env.SSH_PORT || "2212";
  const user = process.env.SSH_USER || "root";
  const key = process.env.SSH_KEY_PATH;
  const scpArgs = ["-P", String(port), "-o", "BatchMode=yes"];
  if (key) scpArgs.push("-i", key, "-o", "IdentitiesOnly=yes");
  scpArgs.push(localFile, `${user}@${host}:${remoteFile}`);

  // Track remote size growth for crude progress
  const started = Date.now();
  const timer = setInterval(() => {
    try {
      const out = execFileSync(
        "ssh",
        [...sshArgs(), `stat -c%s ${remoteFile} 2>/dev/null || echo 0`],
        { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }
      ).trim();
      const cur = Number(out) || 0;
      const pct = Math.min(100, (cur / total) * 100);
      const elapsed = (Date.now() - started) / 1000;
      const speed = elapsed > 0 ? cur / elapsed : 0;
      const left = Math.max(0, total - cur);
      const eta = speed > 0 ? left / speed : NaN;
      const bar = "█".repeat(Math.floor(pct / 5)) + "░".repeat(20 - Math.floor(pct / 5));
      process.stdout.write(
        `\r   ↑ [${bar}] ${pct.toFixed(1)}%  ${fmt(cur)} / ${fmt(total)}  left ${fmt(left)}  ${(speed / (1024 * 1024)).toFixed(1)} MB/s  ETA ${Number.isFinite(eta) ? eta.toFixed(0) + "s" : "--"}   `
      );
    } catch {
      /* ignore */
    }
  }, 1500);

  try {
    execFileSync("scp", scpArgs, { stdio: "inherit" });
  } finally {
    clearInterval(timer);
    process.stdout.write("\n");
  }
  const remoteSize = Number(
    execFileSync("ssh", [...sshArgs(), `stat -c%s ${remoteFile}`], {
      encoding: "utf8",
    }).trim()
  );
  if (remoteSize !== total) {
    throw new Error(`size mismatch local=${total} remote=${remoteSize}`);
  }
  console.log(`→ upload complete ${fmt(total)}`);
  return total;
}

async function main() {
  console.log("== lean finish deploy ==");
  console.log("Plan:");
  console.log("  1) API: already on Iran as", API_TAG, "→ recreate only (0 MB)");
  console.log("  2) Web: pack + upload current local image (~90–100 MB)");
  console.log("  3) nginx snippet only (<1 KB) + apply body-size 55m");
  console.log("  4) compose recreate api-gateway + web + health");
  console.log("");

  // Confirm API present remotely
  const apiCheck = execFileSync(
    "ssh",
    [
      ...sshArgs(),
      `docker image inspect xilo/api-gateway:${API_TAG} --format '{{.Id}}' 2>/dev/null || echo MISSING`,
    ],
    { encoding: "utf8" }
  ).trim();
  if (apiCheck === "MISSING") {
    throw new Error(`Remote missing xilo/api-gateway:${API_TAG} — abort to avoid surprise API re-upload`);
  }
  console.log(`✓ remote API image present: ${apiCheck.slice(0, 19)}…`);

  const packed = await packWeb();
  const uploadBytes = statSync(packed.out).size;
  console.log("");
  console.log(`UPLOAD BUDGET: ${fmt(uploadBytes)} web + ~1 KB nginx  (API = 0)`);
  console.log("");

  ssh(`mkdir -p ${REMOTE}/images ${REMOTE}/infra/nginx`);
  const remoteTar = `${REMOTE}/images/${packed.out.split(/[/\\]/).pop()}`;
  const uploaded = scpWithProgress(packed.out, remoteTar);

  console.log("→ docker load web on Iran");
  ssh(`
set -e
gunzip -c ${remoteTar} | docker load
docker tag xilo/web:latest xilo/web:${WEB_TAG}
docker tag xilo/web:latest xilo/web:previous 2>/dev/null || true
docker tag xilo/web:${WEB_TAG} xilo/web:latest
rm -f ${remoteTar}
echo LOADED_WEB
`);

  // nginx snippet only
  const snippetLocal = join(REPO, "infra/nginx/brain.aile.ir.proxy.snippet.conf");
  const snippetRemote = `${REMOTE}/infra/nginx/brain.aile.ir.proxy.snippet.conf`;
  console.log(`→ scp nginx snippet ${fmt(statSync(snippetLocal).size)}`);
  {
    const host = process.env.SSH_HOST;
    const port = process.env.SSH_PORT || "2212";
    const user = process.env.SSH_USER || "root";
    const key = process.env.SSH_KEY_PATH;
    const scpArgs = ["-P", String(port), "-o", "BatchMode=yes"];
    if (key) scpArgs.push("-i", key, "-o", "IdentitiesOnly=yes");
    scpArgs.push(snippetLocal, `${user}@${host}:${snippetRemote}`);
    execFileSync("scp", scpArgs, { stdio: "inherit" });
  }

  console.log("→ apply nginx + recreate containers");
  ssh(`
set -e
cd ${REMOTE}/infra
set -a
. ./.compose.secrets.env
set +a
# Point compose at tags we know exist on the host
docker tag xilo/api-gateway:${API_TAG} xilo/api-gateway:latest
docker tag xilo/web:${WEB_TAG} xilo/web:latest 2>/dev/null || docker tag xilo/web:latest xilo/web:${WEB_TAG}
export XILO_IMAGE_TAG=latest
docker compose -f docker-compose.prod.yml --env-file .compose.secrets.env up -d --no-build --pull never --no-deps --force-recreate api-gateway web
bash ${REMOTE}/infra/server/apply-nginx-proxy.sh ${REMOTE}/infra/nginx || true
echo ${WEB_TAG} > .prev_tag
curl -sS -o /dev/null -w 'aile:%{http_code}\\n' https://aile.ir/ || true
curl -sS -o /dev/null -w 'brain:%{http_code}\\n' https://brain.aile.ir/health || true
docker ps --format '{{.Names}}\\t{{.Image}}\\t{{.Status}}' | grep -E 'api-gateway|web' || true
grep -n client_max_body_size /etc/nginx/sites-enabled/*brain* 2>/dev/null || grep -n client_max_body_size /etc/nginx/sites-available/*brain* 2>/dev/null || echo 'WARN: body_size not found in brain vhost'
echo DONE
`);

  console.log("");
  console.log("== summary ==");
  console.log(`  uploaded: ${fmt(uploaded)} (web) + nginx snippet`);
  console.log("  API upload: 0 (reused image already on Iran)");
  console.log("  full-repo tar: skipped");
}

main().catch((e) => {
  console.error("ERROR:", e.message || e);
  process.exit(1);
});
