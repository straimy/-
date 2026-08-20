#!/usr/bin/env bash
set -euo pipefail

if [ "${EUID}" -ne 0 ]; then
  echo "Run as root: sudo ./install-site.sh" >&2
  exit 1
fi

SITE_SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AUTH_SRC="${GGO_AUTH_SOURCE:-$SITE_SRC/../services/ggo-auth}"
WEB_ROOT="/var/www/gungloryonline"

if [ ! -f "$SITE_SRC/index.html" ] || [ ! -f "$SITE_SRC/nginx/ggo.conf" ]; then
  echo "Invalid GGO website package" >&2
  exit 2
fi
if [ ! -f "$AUTH_SRC/server.py" ] || [ ! -f "$AUTH_SRC/install.sh" ]; then
  echo "GGO auth service is missing: $AUTH_SRC" >&2
  exit 3
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y nginx python3 ca-certificates curl

install -d -m 0755 "$WEB_ROOT"
# Keep downloads/content already present on VDS while replacing portal code and configs.
for item in index.html styles.css portal.css account regions legal support content; do
  if [ -e "$SITE_SRC/$item" ]; then
    rm -rf "$WEB_ROOT/$item"
    cp -a "$SITE_SRC/$item" "$WEB_ROOT/$item"
  fi
done
if [ -d "$SITE_SRC/downloads" ]; then
  install -d -m 0755 "$WEB_ROOT/downloads"
  cp -a "$SITE_SRC/downloads/." "$WEB_ROOT/downloads/"
fi
chown -R www-data:www-data "$WEB_ROOT"
find "$WEB_ROOT" -type d -exec chmod 0755 {} +
find "$WEB_ROOT" -type f -exec chmod 0644 {} +

chmod +x "$AUTH_SRC/install.sh"
"$AUTH_SRC/install.sh"

install -m 0644 "$SITE_SRC/nginx/ggo.conf" /etc/nginx/sites-available/ggo.conf
ln -sfn /etc/nginx/sites-available/ggo.conf /etc/nginx/sites-enabled/ggo.conf
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable nginx
systemctl reload nginx

curl --fail --silent --show-error http://127.0.0.1:8787/api/v1/health >/tmp/ggo-auth-health.json
cat /tmp/ggo-auth-health.json
echo
curl --fail --silent --show-error -H 'Host: ggo.kvicloud.ru' http://127.0.0.1/api/v1/health >/tmp/ggo-nginx-health.json
cat /tmp/ggo-nginx-health.json
echo

echo "GunGloryOnline portal + auth installed"
echo "Web root: $WEB_ROOT"
echo "Account API: http://127.0.0.1:8787 (nginx: /api/v1/)"
echo "Production URL after DNS/Certbot: https://ggo.kvicloud.ru/"
