# GunGloryOnline Closed Beta deployment (Ubuntu 22.04/24.04)

Production targets:

- website/account/content: `https://ggo.kvicloud.ru`
- official game hostname: `play.kvicloud.ru:24842`
- current origin: `2.26.100.125:24842`
- web root: `/var/www/gungloryonline`
- private account API: `127.0.0.1:8787`
- account DB: `/var/lib/ggo-auth/auth.db`
- game runtime: Minecraft 1.20.1 + Forge 47.4.10 + Java 17 (hidden Runtime v1)

## 1. DNS

Required A records:

```text
Type: A
Name: ggo
Content: 2.26.100.125

Type: A
Name: play
Content: 2.26.100.125
```

The launcher is intentionally locked to `play.kvicloud.ru:24842`. Do not reintroduce the numeric IP into player-facing launcher code as a workaround for missing DNS.

During certificate setup prefer DNS-only mode if your DNS provider has an HTTP proxy feature. The game hostname must expose raw TCP 24842 rather than an HTTP-only proxy.

## 2. Back up account state before web/auth updates

Do not overwrite or copy the live SQLite DB from a build artifact.

Before changing the service, make a protected backup using SQLite backup tooling or while the service is stopped. Example:

```bash
systemctl stop ggo-auth
cp -a /var/lib/ggo-auth/auth.db /var/lib/ggo-auth/auth.db.backup-$(date +%Y%m%d-%H%M%S)
systemctl start ggo-auth
```

Protect backups like credentials.

## 3. Install/update portal and account service

The Stage72 VDS payload contains `web/site/` and `web/services/ggo-auth/`. From the extracted `payload/` directory run:

```bash
cd web/site
chmod +x install-site.sh ../../web/services/ggo-auth/install.sh
./install-site.sh
```

The installer:

- installs nginx, Python 3, CA certificates and curl as needed;
- deploys the portal/content into `/var/www/gungloryonline`;
- creates/uses the locked-down `ggo-auth` system user;
- installs the account service into `/opt/ggo-auth`;
- preserves account data under `/var/lib/ggo-auth`;
- binds auth only to localhost;
- proxies `/api/v1/` through nginx;
- applies login/registration rate limits;
- validates nginx and direct/proxied health before reporting success.

## 4. Verify account API

```bash
systemctl status ggo-auth --no-pager
systemctl status nginx --no-pager
curl http://127.0.0.1:8787/api/v1/health
curl -H 'Host: ggo.kvicloud.ru' http://127.0.0.1/api/v1/health
curl https://ggo.kvicloud.ru/api/v1/health
```

Current health response must contain at least:

```json
{"ok":true,"service":"ggo-auth","version":2,"game_tickets":true}
```

If the public HTTPS URL returns HTML instead of JSON, the deployed nginx/auth stack is stale or `/api/v1/` is falling through to the SPA root.

## 5. HTTPS

Production accounts require HTTPS. The account cookie is `Secure` for the production domain and launcher browser-login opens HTTPS.

If certificate setup is not already complete:

```bash
apt update
apt install -y certbot python3-certbot-nginx
certbot --nginx -d ggo.kvicloud.ru
```

Then verify:

```bash
curl -I https://ggo.kvicloud.ru/
curl https://ggo.kvicloud.ru/api/v1/health
```

## 6. Closed Beta content

The current repository beta manifest is:

`/content/manifests/beta.json`

Current GGO-owned client entries are:

- `mods/gungloryonline-core-runtime-v1-stage68.jar`
- `mods/gungloryonline-ui-runtime-v1-stage69.jar`
- `resourcepacks/GunGloryOnline-Official.zip`

The Stage72 payload already places those exact files under `web/site/content/files/v40/` and verifies byte size + SHA256 against the manifest before artifact upload.

After deployment:

```bash
curl https://ggo.kvicloud.ru/content/manifests/beta.json
```

Confirm it lists `GunGloryOnline-Official.zip` and does **not** list `GunGloryOnline-ResourcePack-1.20.1-v5-swittie-social.zip`.

The manifest is fail-closed: every listed file must have the exact public URL, size and SHA256.

## 7. Public launcher downloads

Keep these paths available:

- `/downloads/GunGloryOnline-Launcher-Windows.exe`
- `/downloads/GunGloryOnline-Launcher-Windows.msi`
- `/downloads/GunGloryOnline-Launcher-Windows-Portable.zip`
- `/downloads/GunGloryOnline-Launcher-Linux.AppImage`
- `/downloads/GunGloryOnline-Launcher-Ubuntu-Debian.deb`
- `/downloads/GunGloryOnline-Launcher-Fedora-RHEL.rpm`

Stage72 includes the verified Closed Beta launcher package set in `web/site/downloads/`.

## 8. Game server

The latest pre-deploy public probe found `2.26.100.125:24842` returning `Connection refused`. The server must be running and listening on TCP 24842 before the public route can be green.

Stage72 contains the current server Core:

`game-server/mods/gungloryonline-core-runtime-v1-stage68.jar`

Install it into the existing Forge 1.20.1 / 47.4.10 server while preserving the server's other required mods, configs and authored world.

Server environment must contain:

```text
GGO_AUTH_API_URL=https://ggo.kvicloud.ru/api/v1
GGO_SERVER_KEY=<private server key>
```

Never place `GGO_SERVER_KEY` in the client, website, artifact, public manifest or chat.

Verify listener/DNS from outside the VDS:

```bash
getent ahosts play.kvicloud.ru
nc -vz play.kvicloud.ru 24842
```

## 9. Account flow smoke

Open:

`https://ggo.kvicloud.ru/account/`

Use a temporary test account and test:

1. username/password login;
2. website PKCE device flow;
3. launcher one-shot game-ticket issuance;
4. game joins official route;
5. server consumes ticket and returns verification ACK;
6. client leaves verification overlay and gameplay unlocks.

Never paste passwords, access/refresh/game tickets or DB contents into logs/support chat.

## 10. Required post-deploy gate

Run GitHub Actions workflow `GGO Public Beta Reachability` after deployment.

Do not call the public beta route ready until a new immutable `.ci/public/<run>.txt` says all of these are `success`:

- website HTTPS
- auth health HTTPS
- beta manifest HTTPS
- official hostname DNS
- official hostname TCP
- origin server TCP
- overall result

Latest known pre-deploy probe is run `32567346737` and is intentionally red for auth/manifest/DNS/server because the live infrastructure had not yet received Stage72.

## Operational commands

```bash
journalctl -u ggo-auth -n 100 --no-pager
systemctl restart ggo-auth
nginx -t && systemctl reload nginx
ls -lh /var/lib/ggo-auth/auth.db
ss -ltnp | grep 24842
```

## Current security model

- scrypt + random per-user salt for password derivation;
- random access/refresh tokens; only SHA256 hashes stored server-side;
- one-shot 180-second pre-consume game tickets, replay/race protected;
- HttpOnly + SameSite=Lax website session cookie; Secure under production HTTPS;
- launcher browser login uses PKCE device authorization;
- auth service is localhost-only behind nginx;
- nginx rate-limits password login and account registration;
- server consumes tickets using private `GGO_SERVER_KEY`.

Microsoft/Minecraft linking is intentionally not advertised as complete until server-side ownership verification exists; the API fails closed rather than pretending the link succeeded.
