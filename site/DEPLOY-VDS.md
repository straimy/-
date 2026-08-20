# GunGloryOnline website deployment (Ubuntu 22.04)

Target root: `/var/www/gungloryonline`

```bash
apt update
apt install -y nginx unzip
mkdir -p /var/www/gungloryonline/downloads /var/www/gungloryonline/content/api
```

Upload/extract the `GunGloryOnline-Website` artifact into `/var/www/gungloryonline`, then copy the built launcher files into `downloads/`:

- `GunGloryOnline-Launcher-Windows.exe`
- `GunGloryOnline-Launcher-Linux.AppImage`
- `GunGloryOnline-ClientPack-1.20.1-Forge47.4.10.zip`

The launcher content endpoint is `/content`, so publish the client manifest as:

- `/var/www/gungloryonline/content/manifests/beta.json`
- `/var/www/gungloryonline/content/api/servers.json`
- `/var/www/gungloryonline/content/api/news.json`

Install nginx config:

```bash
cp /var/www/gungloryonline/nginx/ggo.conf /etc/nginx/sites-available/gungloryonline
ln -sfn /etc/nginx/sites-available/gungloryonline /etc/nginx/sites-enabled/gungloryonline
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable --now nginx
systemctl reload nginx
```

After DNS for `ggo.kvicloud.ru` points to the VDS, enable HTTPS:

```bash
apt install -y certbot python3-certbot-nginx
certbot --nginx -d ggo.kvicloud.ru
```

Do not publish a production `beta.json` until every listed client file has a real URL, size and SHA256. The launcher updater is fail-closed by design.
