#!/usr/bin/env node
/**
 * SSH/SCP helpers via DATA VPS → Edge root (direct :2212 often blocked from VPN).
 * Reads Hesabdaram deploy/.deploy.env READ-ONLY for DATA jump credentials only.
 * Never modifies Hesabdaram or APP/DATA hosts beyond SSH jump.
 */
import { readFileSync, existsSync, createReadStream } from "node:fs";
import { createRequire } from "node:module";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { PassThrough } from "node:stream";

const __dirname = dirname(fileURLToPath(import.meta.url));
const HD_DEPLOY = resolve(__dirname, "../../../hesabdaram/deploy");
const HD_ENV = resolve(HD_DEPLOY, ".deploy.env");
const XILO_ENV = resolve(__dirname, ".env.deploy");
const require = createRequire(resolve(HD_DEPLOY, "package.json"));
const { Client } = require("ssh2");

function loadEnv(p) {
  if (!existsSync(p)) throw new Error(`Missing ${p}`);
  const env = {};
  for (const line of readFileSync(p, "utf8").split(/\r?\n/)) {
    if (!line || line.startsWith("#")) continue;
    const i = line.indexOf("=");
    if (i < 0) continue;
    let v = line.slice(i + 1);
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
      v = v.slice(1, -1);
    }
    env[line.slice(0, i)] = v;
  }
  return env;
}

export function loadConfig() {
  const hd = loadEnv(HD_ENV);
  const xilo = existsSync(XILO_ENV) ? loadEnv(XILO_ENV) : {};
  const keyPath = xilo.SSH_KEY_PATH || resolve(process.env.USERPROFILE || process.env.HOME, ".ssh/id_ed25519");
  return {
    data: {
      host: hd.DATA_SSH_HOST || "45.90.73.48",
      port: Number(hd.DATA_SSH_PORT || 2212),
      username: hd.DATA_SSH_USERNAME || "usher",
      password: hd.DATA_SSH_PASSWORD,
    },
    edge: {
      host: xilo.SSH_HOST || hd.SSH_HOST || "45.90.73.12",
      port: Number(xilo.SSH_PORT || hd.SSH_PORT || 2212),
      username: xilo.SSH_USER || "root",
      privateKey: readFileSync(keyPath),
    },
    remoteDir: xilo.REMOTE_DIR || "/opt/xilo",
  };
}

function connect(cfg) {
  return new Promise((resolveP, reject) => {
    const c = new Client();
    c.on("ready", () => resolveP(c));
    c.on("error", reject);
    c.connect({ ...cfg, readyTimeout: 45000 });
  });
}

export async function withEdgeRoot(fn) {
  const cfg = loadConfig();
  if (!cfg.data.password) throw new Error("DATA_SSH_PASSWORD missing in hesabdaram .deploy.env");
  const data = await connect({
    host: cfg.data.host,
    port: cfg.data.port,
    username: cfg.data.username,
    password: cfg.data.password,
  });
  try {
    const stream = await new Promise((resolveP, reject) =>
      data.forwardOut("127.0.0.1", 0, cfg.edge.host, cfg.edge.port, (e, s) => (e ? reject(e) : resolveP(s)))
    );
    const edge = await connect({
      sock: stream,
      username: cfg.edge.username,
      privateKey: cfg.edge.privateKey,
    });
    try {
      return await fn(edge, cfg);
    } finally {
      edge.end();
    }
  } finally {
    data.end();
  }
}

export function exec(client, cmd, { timeoutMs = 600000, onData } = {}) {
  return new Promise((resolveP, reject) => {
    const t = setTimeout(() => reject(new Error(`timeout: ${cmd.slice(0, 100)}`)), timeoutMs);
    client.exec(cmd, (err, stream) => {
      if (err) {
        clearTimeout(t);
        return reject(err);
      }
      let out = "";
      let errOut = "";
      stream.on("data", (d) => {
        out += d;
        if (onData) onData(d.toString());
        else process.stdout.write(d);
      });
      stream.stderr.on("data", (d) => {
        errOut += d;
        if (onData) onData(d.toString());
        else process.stderr.write(d);
      });
      stream.on("close", (code) => {
        clearTimeout(t);
        resolveP({ code: code ?? 0, out, errOut });
      });
    });
  });
}

/** Stream a local tar.gz into remoteDir (mkdir -p first). */
export async function uploadTar(edge, localTarPath, remoteDir) {
  await exec(edge, `mkdir -p ${remoteDir}`, { onData: () => {} });
  return new Promise((resolveP, reject) => {
    edge.exec(`tar -xzf - -C ${remoteDir}`, (err, stream) => {
      if (err) return reject(err);
      let errOut = "";
      stream.stderr.on("data", (d) => {
        errOut += d;
        process.stderr.write(d);
      });
      stream.on("close", (code) => {
        if (code === 0) resolveP();
        else reject(new Error(`remote tar exit ${code}: ${errOut}`));
      });
      createReadStream(localTarPath).pipe(stream);
    });
  });
}
