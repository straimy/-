# GunGloryOnline portal + account API deployment (Ubuntu 22.04)

Production targets:

- website: `https://ggo.kvicloud.ru`
- game server: `2.26.100.125:24842`
- web root: `/var/www/gungloryonline`
- private account API: `127.0.0.1:8787`
- account DB: `/var/lib/ggo-auth/auth.db`

## 1. DNS

Create an A record:

```text
Type: A
Name: ggo
Content: 2.26.100.125
```

During initial certificate setup prefer DNS-only mode if your DNS provider has an HTTP proxy feature.

## 2. Install/update portal and account service

The WEB-VDS package contains `site/` and `services/ggo-auth/`. From the extracted package root run:

```bash
cd site
chmod +x install-site.sh ../services/ggo-auth/install.sh
./install-site.sh
```

The installer:

- installs nginx, Python 3, CA certificates and curl;
- deploys the portal into `/var/www/gungloryonline`;
- creates the locked-down `ggo-auth` system user;
- installs the SQLite account service into `/opt/ggo-auth`;
- keeps its database in `/var/lib/ggo-auth`;
- binds the account service only to localhost;
- proxies `/api/v1/` through nginx;
- applies login/registration rate limits;
- validates nginx and both direct/proxied API health before reporting success.

## 3. Verify HTTP before HTTPS

```bash
systemctl status ggo-auth --no-pager
systemctl status nginx --no-pager
curl http://127.0.0.1:8787/api/v1/health
curl -H 'Host: ggo.kvicloud.ru' http://127.0.0.1/api/v1/health
```

Both health calls must return an object containing:

```json
{"ok":true,"service":"ggo-auth","version":1}
```

## 4. Enable HTTPS — required for production accounts

Do not treat website login/device authorization as production-ready over raw HTTP. The account cookie is configured as `Secure` for the production domain and launcher browser-login opens the HTTPS URL.

After DNS resolves to this VDS:

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

## 5. Account flow smoke

Open:

```text
https://ggo.kvicloud.ru/account/
```

Create a temporary test user, sign out, then sign in again. In the launcher test both:

1. GGO username + password.
2. `Sign in through website` device flow: launcher opens `/account/device.html`, website session approves it, launcher receives the GGO profile through PKCE.

Never paste a password, access token, refresh token or database file into support chats/logs.

## 6. Operational commands

```bash
journalctl -u ggo-auth -n 100 --no-pager
systemctl restart ggo-auth
nginx -t && systemctl reload nginx
ls -lh /var/lib/ggo-auth/auth.db
```

Back up the account database while the service is stopped or by using SQLite's backup mechanism. Protect backups like credentials.

## 7. Launcher/content files

Keep these public paths available:

- `/downloads/GunGloryOnline-Launcher-Windows.exe`
- `/downloads/GunGloryOnline-Launcher-Windows-Portable.zip`
- `/downloads/GunGloryOnline-Launcher-Linux.AppImage`
- `/downloads/GunGloryOnline-Launcher-Ubuntu-Debian.deb`
- `/downloads/GunGloryOnline-Launcher-Fedora-RHEL.rpm`
- `/downloads/GunGloryOnline-ClientPack-1.20.1-Forge47.4.10.zip`
- `/content/manifests/beta.json`
- `/content/api/servers.json`
- `/content/api/news.json`
- `/content/files/v40/...`

The production game manifest remains fail-closed: every listed client file must have the correct URL, byte size and SHA256.

## Current security model

- password derivation: scrypt + random per-user salt;
- random access/refresh tokens; only SHA256 token hashes are stored server-side;
- HttpOnly + SameSite=Lax website session cookie; Secure under production HTTPS;
- launcher browser login uses PKCE device authorization;
- auth service is localhost-only behind nginx;
- nginx rate-limits password login and account registration.

Microsoft/Minecraft linking is intentionally not advertised as complete until server-side ownership verification is implemented; the API fails closed rather than pretending the link succeeded.
