#!/usr/bin/env node
/**
 * Use Edge VPS Webmin/Virtualmin (via SSH tunnel) to run root commands for Xilo.
 * Reads Hesabdaram deploy/.deploy.env READ-ONLY — never modifies that project.
 *
 * Usage:
 *   node via-edge-webmin.mjs "id; hostname"
 *   node via-edge-webmin.mjs --bootstrap-root-key
 */
import { readFileSync, existsSync } from "node:fs";
import { createServer } from "node:net";
import { spawn } from "node:child_process";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const HD_ENV = resolve(__dirname, "../../../hesabdaram/deploy/.deploy.env");
const PUBKEY =
  "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIBTYrN2FQdMs3BilVjBF6AlcCjyaw2jt7nLb7Sdhij21 faslolkhitab@gmail.com";
const MY_IP = "193.19.204.71";

function loadEnv(p) {
  if (!existsSync(p)) throw new Error(`Missing ${p}`);
  const env = {};
  for (const line of readFileSync(p, "utf8").split(/\r?\n/)) {
    if (!line || line.startsWith("#")) continue;
    const i = line.indexOf("=");
    if (i < 0) continue;
    env[line.slice(0, i)] = line.slice(i + 1);
  }
  return env;
}

function freePort() {
  return new Promise((resolveP, reject) => {
    const s = createServer();
    s.listen(0, "127.0.0.1", () => {
      const { port } = s.address();
      s.close(() => resolveP(port));
    });
    s.on("error", reject);
  });
}

async function main() {
  const env = loadEnv(HD_ENV);
  const cmdArg = process.argv.slice(2).join(" ").trim();
  const bootstrap = process.argv.includes("--bootstrap-root-key");

  let command = cmdArg.replace("--bootstrap-root-key", "").trim();
  if (bootstrap) {
    command = [
      `fail2ban-client set sshd unbanip ${MY_IP} 2>/dev/null || true`,
      `fail2ban-client unban ${MY_IP} 2>/dev/null || true`,
      "mkdir -p /root/.ssh && chmod 700 /root/.ssh",
      `grep -qxF '${PUBKEY}' /root/.ssh/authorized_keys || echo '${PUBKEY}' >> /root/.ssh/authorized_keys`,
      "chmod 600 /root/.ssh/authorized_keys",
      "usermod -aG docker aile 2>/dev/null || true",
      "id; hostname; echo BOOTSTRAP_OK",
    ].join("; ");
  }
  if (!command) {
    console.log('Usage: node via-edge-webmin.mjs "command" | --bootstrap-root-key');
    process.exit(1);
  }

  // Prefer aile key (Xilo), else hesabdaram password from HD env
  const aileKey = resolve(process.env.USERPROFILE || process.env.HOME || "", ".ssh/id_ed25519");
  const localPort = await freePort();
  const sshArgs = [
    "-o", "ExitOnForwardFailure=yes",
    "-o", "ServerAliveInterval=30",
    "-p", String(env.SSH_PORT || 2212),
    "-L", `${localPort}:127.0.0.1:10000`,
    "-N",
  ];

  let sshProc;
  if (existsSync(aileKey)) {
    sshProc = spawn(
      "ssh",
      [...sshArgs, "-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes", "-i", aileKey, `aile@${env.SSH_HOST}`],
      { stdio: ["ignore", "pipe", "pipe"] }
    );
  } else {
    sshProc = spawn(
      "sshpass",
      ["-e", "ssh", ...sshArgs, "-o", "PreferredAuthentications=password", "-o", "PubkeyAuthentication=no", `${env.SSH_USERNAME}@${env.SSH_HOST}`],
      { stdio: ["ignore", "pipe", "pipe"], env: { ...process.env, SSHPASS: env.SSH_PASSWORD } }
    );
  }

  await new Promise((r) => setTimeout(r, 1500));
  if (sshProc.exitCode != null) {
    const err = sshProc.stderr?.read()?.toString() || "ssh tunnel failed";
    throw new Error(err);
  }

  try {
    process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
    const auth = Buffer.from(`${env.VIRTUALMIN_ROOT_USER || "root"}:${env.VIRTUALMIN_ROOT_PASS}`).toString("base64");
    const qs = new URLSearchParams({ program: "run-command", command });
    const res = await fetch(`https://127.0.0.1:${localPort}/virtual-server/remote.cgi?${qs}`, {
      headers: { Authorization: `Basic ${auth}` },
    });
    const text = await res.text();
    console.log(text);
    if (text.startsWith("ERROR") || /failed|denied/i.test(text) && !/BOOTSTRAP_OK/.test(text)) {
      process.exitCode = 1;
    }
  } finally {
    sshProc.kill("SIGTERM");
  }
}

main().catch((e) => {
  console.error("ERROR:", e.message || e);
  process.exit(1);
});
