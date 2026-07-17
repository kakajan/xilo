# Xilo deploy CLI

```bash
cd infra/deploy
cp .env.deploy.example .env.deploy   # then edit
npm install
node deploy.mjs doctor
node deploy.mjs proxy-install   # Germany learn.xilo.ir stack (once)
node deploy.mjs up              # first deploy on Iran
node deploy.mjs sync            # later updates
node deploy.mjs prune
node deploy.mjs rollback
```

Secrets stay in `.env.deploy` (gitignored). Client proxy configs land in `infra/proxy/clients/` (gitignored).
