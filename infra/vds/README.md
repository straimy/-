# GunGloryOnline single-node VDS

This directory is the production single-node deployment for GunGloryOnline.

The target is intentionally simple: one affordable VDS hosts as much of GGO as practical while keeping every service separable later.

## Current DNS

Point these records at the VDS (`2.26.100.125` for the current deployment):

- `ggo.kvicloud.ru` — website + GGO Account API. Cloudflare proxy may be enabled.
- `updates.ggo.kvicloud.ru` — launcher/game payloads and updater metadata. Keep DNS-only initially.
- `play.ggo.kvicloud.ru` — raw game TCP endpoint. Must be DNS-only unless a TCP proxy product is deliberately configured.

The launcher knows the game port, so an SRV record is not required. An SRV record is only useful if legacy/manual Minecraft clients should connect without typing a port.

## What one VDS hosts

- public website and launcher download page;
- GGO Account registration/login/device authorization;
- GGO skin storage;
- PostgreSQL;
- Redis;
- launcher news/server/status APIs;
- immutable mods/resource packs/configs/game assets;
- signed launcher self-update artifacts;
- game manifests;
- optional Forge 1.20.1 game server on the same machine.

PostgreSQL and Redis have no public ports.

## Player install experience

A player downloads only the GunGloryOnline launcher. Registration is not required to download it.

The launcher is responsible for:

- Minecraft 1.20.1 runtime files required by Runtime v1;
- Forge 47.4.10;
- current GGO Core/UI jars;
- mandatory resource pack;
- managed configs/content;
- later updates and repairs.

Players should not manually download or maintain a modpack/resource pack.

## First VDS bootstrap

From the unpacked deployment directory:

```bash
sudo bash bootstrap_ubuntu.sh
bash deploy_all.sh
```

`bootstrap_ubuntu.sh` installs Docker and opens only SSH/HTTP/HTTPS plus the GGO game port.

`deploy_all.sh`:

- creates `.env` from `.env.example` if necessary;
- generates random PostgreSQL/JWT secrets;
- validates Docker Compose;
- starts Caddy + account API + PostgreSQL + Redis;
- starts the game server automatically only when exactly one GGO Core jar and `game-server/world/level.dat` are present.

## Game server profile

The game server uses the same Compose project under profile `game` and is intentionally capped so the current 8 GB VDS still has room for infrastructure.

Current defaults:

- port `24842`;
- Java 17;
- Forge `1.20.1-47.4.10`;
- Xms `1G`;
- Xmx `4G`;
- container memory cap ~4.5 GB;
- command blocks disabled in the new production target.

The command-block-disabled target is deliberate. Legacy maps must have gameplay command chains migrated into versioned GGO server code before they are considered production-ready.

## Publish a game build

`publish_release.py` accepts the client READY-PACK/FULL-INSTALL package and an optional independently versioned resource pack.

Example:

```bash
python3 publish_release.py /path/GunGloryOnline-v40-READY-PACK.zip \
  --public-dir ./public \
  --base-url https://updates.ggo.kvicloud.ru \
  --version v40 \
  --channel beta \
  --resource-pack /path/GunGloryOnline-ResourcePack-1.20.1-v5-swittie-social.zip
```

If a later code release does not change the resource pack, omit `--resource-pack`; the publisher inherits the currently managed RP instead of making players download it again.

## Publish launcher downloads/self-update

`publish_launcher_update.py` publishes signed Windows/Linux updater artifacts and writes both:

- `public/launcher/latest.json` for Tauri self-update;
- `site/downloads.json` for the public website Download buttons.

That means the public website always points to the same signed build distributed by the updater.

## Security

- Never commit the production `.env`.
- Never put Microsoft/GGO access tokens in manifests or static files.
- Never commit launcher signing private keys.
- Keep PostgreSQL and Redis internal-only.
- Use server-authoritative online progression; clients and offline Training cannot mint XP/currency/rank.
- After the first deployment, replace temporary SSH password access with an SSH key and rotate any password that has been shared during setup.
