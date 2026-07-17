#!/usr/bin/env node
/**
 * One-shot: DATA VPS -> hesabdaram@edge -> Webmin /proc/run.cgi (root).
 * Reads Hesabdaram deploy/.deploy.env READ-ONLY.
 *
 * Usage: node hop-webmin-bootstrap.mjs [extra-ip-to-unban]
 */
import { readFileSync, existsSync } from "node:fs";
import { createRequire } from "node:module";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const HD_DEPLOY = resolve(__dirname, "../../../hesabdaram/deploy");
const HD_ENV = resolve(HD_DEPLOY, ".deploy.env");
const require = createRequire(resolve(HD_DEPLOY, "package.json"));
const { Client } = require("ssh2");

const PUBKEY =
  "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIBTYrN2FQdMs3BilVjBF6AlcCjyaw2jt7nLb7Sdhij21 faslolkhitab@gmail.com";

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

function sshConnect(cfg) {
  return new Promise((resolveP, reject) => {
    const c = new Client();
    c.on("ready", () => resolveP(c));
    c.on("error", reject);
    c.connect(cfg);
  });
}

function sshExec(client, cmd, timeoutMs = 120000) {
  return new Promise((resolveP, reject) => {
    const t = setTimeout(() => reject(new Error(`exec timeout: ${cmd.slice(0, 80)}`)), timeoutMs);
    client.exec(cmd, (err, stream) => {
      if (err) {
        clearTimeout(t);
        return reject(err);
      }
      let out = "";
      let errOut = "";
      stream.on("data", (d) => (out += d));
      stream.stderr.on("data", (d) => (errOut += d));
      stream.on("close", (code) => {
        clearTimeout(t);
        resolveP({ code, out, errOut });
      });
    });
  });
}

function forwardOut(client, host, port) {
  return new Promise((resolveP, reject) => {
    client.forwardOut("127.0.0.1", 0, host, port, (err, stream) => {
      if (err) reject(err);
      else resolveP(stream);
    });
  });
}

/** Run root cmd via Webmin using Python on the edge host (localhost:10000). */
async function webminRunRoot(edgeClient, rootPass, cmd) {
  const payload = {
    rootPass,
    cmd,
  };
  const payloadB64 = Buffer.from(JSON.stringify(payload), "utf8").toString("base64");
  const py = `
import ssl, urllib.request, urllib.parse, re, html, http.cookiejar, base64, json
payload = json.loads(base64.b64decode("${payloadB64}").decode())
root_pass = payload["rootPass"]
cmd = payload["cmd"]
ctx = ssl._create_unverified_context()
base = "https://127.0.0.1:10000"
cj = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj), urllib.request.HTTPSHandler(context=ctx))
login_data = urllib.parse.urlencode({"user": "root", "pass": root_pass}).encode()
opener.open(urllib.request.Request(base+"/session_login.cgi", data=login_data, method="POST",
  headers={"Content-Type":"application/x-www-form-urlencoded","Cookie":"testing=1"}), timeout=30)
body = urllib.parse.urlencode({"user":"root","cmd":cmd}).encode()
res = opener.open(urllib.request.Request(base+"/proc/run.cgi", data=body, method="POST",
  headers={"Content-Type":"application/x-www-form-urlencoded","Referer":base+"/proc/index.cgi","Origin":base}), timeout=120)
text = res.read().decode("utf-8","replace")
pres = re.findall(r"<pre[^>]*>([\\s\\S]*?)</pre>", text, re.I)
print("\\n".join(html.unescape(p) for p in pres) if pres else text[:8000])
`;
  const pyB64 = Buffer.from(py, "utf8").toString("base64");
  const remote = `echo ${pyB64} | base64 -d > /tmp/xilo-wm-run.py && python3 /tmp/xilo-wm-run.py`;
  const r = await sshExec(edgeClient, remote, 180000);
  if (r.code !== 0) throw new Error(`webmin python exit ${r.code}: ${r.errOut || r.out}`);
  return r.out;
}

async function main() {
  const env = loadEnv(HD_ENV);
  const dataHost = env.DATA_SSH_HOST || "45.90.73.48";
  const dataPort = Number(env.DATA_SSH_PORT || 2212);
  const dataUser = env.DATA_SSH_USERNAME || "usher";
  const dataPass = env.DATA_SSH_PASSWORD;
  const edgeHost = env.SSH_HOST || "45.90.73.12";
  const edgePort = Number(env.SSH_PORT || 2212);
  const hdUser = env.SSH_USERNAME || "hesabdaram";
  const hdPass = env.SSH_PASSWORD;
  const rootPass = env.VIRTUALMIN_ROOT_PASS;
  if (!dataPass || !hdPass || !rootPass) throw new Error("Missing passwords in .deploy.env");

  const myIp = process.argv[2] || "";

  console.log(`Connecting DATA ${dataUser}@${dataHost}:${dataPort} ...`);
  const data = await sshConnect({
    host: dataHost,
    port: dataPort,
    username: dataUser,
    password: dataPass,
    readyTimeout: 30000,
  });

  console.log(`Forwarding to hesabdaram@${edgeHost}:${edgePort} ...`);
  const edgeStream = await forwardOut(data, edgeHost, edgePort);
  const edge = await new Promise((resolveP, reject) => {
    const c = new Client();
    c.on("ready", () => resolveP(c));
    c.on("error", reject);
    c.connect({
      sock: edgeStream,
      username: hdUser,
      password: hdPass,
      readyTimeout: 30000,
    });
  });

  const probe = await sshExec(edge, "hostname; id; echo HD_OK");
  console.log(probe.out || probe.errOut);

  const unbanIps = [
    "45.90.73.13",
    "45.90.73.48",
    "95.217.205.246",
    "193.19.204.71",
    "31.171.101.45",
    "31.171.101.126",
    "31.171.100.203",
    "31.171.100.139",
    "31.171.101.1",
    "31.171.101.203",
  ];
  if (myIp) unbanIps.push(myIp);

  const cmd = `
set -e
PUB='${PUBKEY}'
mkdir -p /root/.ssh /home/aile/.ssh
chmod 700 /root/.ssh /home/aile/.ssh
touch /root/.ssh/authorized_keys
grep -qxF "$PUB" /root/.ssh/authorized_keys || echo "$PUB" >> /root/.ssh/authorized_keys
chmod 600 /root/.ssh/authorized_keys
chown root:root /root/.ssh /root/.ssh/authorized_keys
grep -qxF "$PUB" /home/aile/.ssh/authorized_keys 2>/dev/null || echo "$PUB" >> /home/aile/.ssh/authorized_keys
chown -R aile:aile /home/aile/.ssh
chmod 600 /home/aile/.ssh/authorized_keys
usermod -aG docker aile 2>/dev/null || true
for ip in ${unbanIps.join(" ")}; do
  fail2ban-client set sshd unbanip $ip 2>/dev/null || true
  fail2ban-client unban $ip 2>/dev/null || true
done
mkdir -p /etc/fail2ban/jail.d
cat > /etc/fail2ban/jail.d/xilo-ignore.conf <<'F2B'
[DEFAULT]
ignoreip = 127.0.0.1/8 ::1 45.90.73.0/24 95.217.205.246 31.171.0.0/16 193.19.204.0/24
F2B
fail2ban-client reload 2>/dev/null || systemctl reload fail2ban 2>/dev/null || true
if grep -q '^PermitRootLogin no' /etc/ssh/sshd_config; then
  sed -i 's/^PermitRootLogin no/PermitRootLogin prohibit-password/' /etc/ssh/sshd_config
fi
grep -E '^(PermitRootLogin|PubkeyAuthentication|AllowUsers)' /etc/ssh/sshd_config /etc/ssh/sshd_config.d/* 2>/dev/null || true
sshd -t && (systemctl reload sshd 2>/dev/null || systemctl reload ssh 2>/dev/null || true)
echo '=== keys ==='
cat /root/.ssh/authorized_keys
ls -la /root/.ssh/authorized_keys /home/aile/.ssh/authorized_keys
id; groups aile; echo BOOTSTRAP2_OK
`.trim();

  console.log("Running Webmin root command...");
  const out = await webminRunRoot(edge, rootPass, cmd);
  console.log(out);

  edge.end();
  data.end();
  if (!out.includes("BOOTSTRAP2_OK")) process.exit(2);
  console.log("OK");
}

main().catch((e) => {
  console.error("ERROR:", e.message || e);
  process.exit(1);
});
