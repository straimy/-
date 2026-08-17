# GunGloryOnline VDS content node

This directory is the production content/update node for the GunGloryOnline launcher.

## What it serves

- `/manifests/beta.json` and `/manifests/stable.json` — launcher game manifests.
- `/files/<version>/...` — immutable versioned client files (mods, configs, resource packs).
- `/api/servers.json` — launcher server catalog.
- `/api/news.json` — launcher news feed.
- `/api/status.json` — simple content-node health payload.

The game server itself stays separate. The launcher pings and joins the game server directly.

## Deployment model

1. Point a DNS name such as `updates.example.com` to the VDS.
2. Copy `.env.example` to `.env` and set `GGO_HOST` plus `GGO_PUBLIC_BASE_URL`.
3. Install Docker Engine + Docker Compose plugin.
4. Start with `docker compose up -d`.
5. Publish a FULL-INSTALL ZIP with `publish_release.py`.

Caddy terminates HTTPS automatically when DNS is correct and ports 80/443 are reachable.

## Publish a new game build

Run from `infra/vds`:

```bash
python3 publish_release.py /path/GunGloryOnline-v35-FULL-INSTALL.zip \
  --public-dir ./public \
  --base-url https://updates.example.com \
  --version v35 \
  --channel beta
```

The publisher extracts only `client/` files, rejects unsafe archive paths, hashes every file with SHA256, writes versioned immutable files under `public/files/<version>/`, and atomically replaces the channel manifest only after the new version is ready.

For v36/v37, only change the package and `--version`. Old versioned files can be retained for rollback.

## Launcher build configuration

Build the launcher with:

```bash
GGO_CONTENT_BASE_URL=https://updates.example.com npm run tauri build
```

The Rust bootstrap then exposes the production manifest/news/server endpoints to the UI. No VDS secret is embedded in the launcher; this value is only a public HTTPS base URL.

## Security notes

- Never place Microsoft tokens, signing private keys, SSH keys, passwords, or API secrets in `public/`.
- Launcher update signing private keys belong in CI secrets only.
- Game manifests are intentionally uncached; versioned payloads are cached as immutable.
- Keep SSH password login disabled once key authentication is configured.
