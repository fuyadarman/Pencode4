# Pencode Cloudflare Worker Backend

Backend for GitHub OAuth token exchange, App installation token generation, and MCP server.

### GitHub App Details:
- **App Name:** Pencode
- **App ID:** `5369463`
- **Client ID:** `Iv23liJ1YMYgjNpHUToe`
- **Public URL:** https://github.com/apps/pencode

---

## 🚀 2-Minute Deployment Guide

### Option 1: Cloudflare Dashboard (No CLI needed)
1. Go to [Cloudflare Dashboard](https://dash.cloudflare.com/) > **Workers & Pages** > **Create application** > **Create Worker**.
2. Name it `pencode-auth-worker` and click **Deploy**.
3. Click **Edit code**, paste the content of `worker.js` into the editor, and click **Deploy**.
4. Go to Worker **Settings** > **Variables and Secrets**:
   - Add Secret: `GITHUB_CLIENT_SECRET` (your GitHub App Client Secret)
   - Add Secret: `GITHUB_PRIVATE_KEY` (your downloaded `.pem` file content from GitHub App)
   - Environment Variable: `GITHUB_CLIENT_ID` = `Iv23liJ1YMYgjNpHUToe`
   - Environment Variable: `GITHUB_APP_ID` = `5369463`
5. Copy your worker URL (e.g. `https://pencode-auth-worker.<your-subdomain>.workers.dev`) and enter it in the Pencode App Build/Settings tab!

### Option 2: Wrangler CLI
```bash
cd cloudflare-worker
npx wrangler secret put GITHUB_CLIENT_SECRET
npx wrangler secret put GITHUB_PRIVATE_KEY
npx wrangler deploy
```

---

## 🔗 GitHub App Configuration
In your GitHub App settings at `https://github.com/settings/apps/pencode`:
- **Callback URL:** `https://<your-worker-url>/oauth/callback` and `pencode://oauth/callback`
- **Setup URL:** `https://<your-worker-url>/`
