#!/usr/bin/env bash
set -euo pipefail

if [ "${EUID}" -ne 0 ]; then
  echo "Run as root: sudo ./install-site.sh" >&2
  exit 1
fi

SITE_SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AUTH_SRC="${GGO_AUTH_SOURCE:-$SITE_SRC/../services/ggo-auth}"
WEB_ROOT="/var/www/gungloryonline"
GGO_HOST="ggo.kvicloud.ru"
GGO_SITE_AVAILABLE="/etc/nginx/sites-available/ggo.conf"
GGO_SITE_ENABLED="/etc/nginx/sites-enabled/ggo.conf"
TLS_CERT="/etc/letsencrypt/live/${GGO_HOST}/fullchain.pem"
TLS_KEY="/etc/letsencrypt/live/${GGO_HOST}/privkey.pem"
BACKUP_ROOT="/root/ggo-site-backups"
BACKUP_DIR="$BACKUP_ROOT/$(date -u +%Y%m%dT%H%M%SZ)"

if [ ! -f "$SITE_SRC/index.html" ] || [ ! -f "$SITE_SRC/nginx/ggo.conf" ]; then
  echo "Invalid GGO website package" >&2
  exit 2
fi
if [ ! -f "$AUTH_SRC/server.py" ] || [ ! -f "$AUTH_SRC/install.sh" ]; then
  echo "GGO auth service is missing: $AUTH_SRC" >&2
  exit 3
fi
if [ ! -f "$TLS_CERT" ] || [ ! -f "$TLS_KEY" ]; then
  echo "TLS certificate for ${GGO_HOST} is missing. Install/renew it before production deploy." >&2
  exit 4
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y nginx python3 ca-certificates curl

install -d -m 0700 "$BACKUP_DIR"
if [ -f /var/lib/ggo-auth/auth.db ]; then
  cp -a /var/lib/ggo-auth/auth.db "$BACKUP_DIR/auth.db"
fi
if [ -f "$GGO_SITE_AVAILABLE" ]; then
  cp -a "$GGO_SITE_AVAILABLE" "$BACKUP_DIR/ggo.conf.previous"
fi
# Preserve generated launcher update metadata across portal refreshes.
if [ -d "$WEB_ROOT/content/launcher" ]; then
  cp -a "$WEB_ROOT/content/launcher" "$BACKUP_DIR/launcher-channel"
fi
# Runtime payloads are published separately from the website source package.
# Never delete them during a portal/launcher-channel update.
if [ -d "$WEB_ROOT/content/files" ]; then
  cp -a "$WEB_ROOT/content/files" "$BACKUP_DIR/runtime-files"
fi

install -d -m 0755 "$WEB_ROOT"
for item in index.html styles.css portal.css install-launcher.sh publish-launcher-update.sh account regions legal support content; do
  if [ -e "$SITE_SRC/$item" ]; then
    rm -rf "$WEB_ROOT/$item"
    cp -a "$SITE_SRC/$item" "$WEB_ROOT/$item"
  fi
done
if [ -d "$BACKUP_DIR/launcher-channel" ]; then
  install -d -m 0755 "$WEB_ROOT/content"
  rm -rf "$WEB_ROOT/content/launcher"
  cp -a "$BACKUP_DIR/launcher-channel" "$WEB_ROOT/content/launcher"
fi
if [ -d "$BACKUP_DIR/runtime-files" ]; then
  install -d -m 0755 "$WEB_ROOT/content"
  rm -rf "$WEB_ROOT/content/files"
  cp -a "$BACKUP_DIR/runtime-files" "$WEB_ROOT/content/files"
fi
if [ -d "$SITE_SRC/downloads" ]; then
  install -d -m 0755 "$WEB_ROOT/downloads"
  cp -a "$SITE_SRC/downloads/." "$WEB_ROOT/downloads/"
fi
chown -R www-data:www-data "$WEB_ROOT"
find "$WEB_ROOT" -type d -exec chmod 0755 {} +
find "$WEB_ROOT" -type f -exec chmod 0644 {} +
if [ -f "$WEB_ROOT/install-launcher.sh" ]; then
  chmod 0755 "$WEB_ROOT/install-launcher.sh"
fi
if [ -f "$WEB_ROOT/publish-launcher-update.sh" ]; then
  chmod 0755 "$WEB_ROOT/publish-launcher-update.sh"
fi

chmod +x "$AUTH_SRC/install.sh"
"$AUTH_SRC/install.sh"

install -m 0644 "$SITE_SRC/nginx/ggo.conf" "$GGO_SITE_AVAILABLE"
ln -sfn "$GGO_SITE_AVAILABLE" "$GGO_SITE_ENABLED"
rm -f /etc/nginx/sites-enabled/default

# Certbot previously left another enabled vhost named `gungloryonline`, which made
# nginx ignore the new GGO server block. Keep old files for recovery, but disable
# every enabled config that still claims ggo.kvicloud.ru except the canonical one.
shopt -s nullglob
for enabled in /etc/nginx/sites-enabled/*; do
  [ -e "$enabled" ] || continue
  [ "$enabled" = "$GGO_SITE_ENABLED" ] && continue
  if grep -Eq "^[[:space:]]*server_name[[:space:]][^;]*${GGO_HOST//./\.}([^[:alnum:]_.-]|;|$)" "$enabled" 2>/dev/null; then
    echo "Disabling duplicate nginx vhost: $enabled"
    rm -f "$enabled"
  fi
done
shopt -u nullglob

mapfile -t ggo_enabled < <(grep -El "^[[:space:]]*server_name[[:space:]][^;]*${GGO_HOST//./\.}([^[:alnum:]_.-]|;|$)" /etc/nginx/sites-enabled/* 2>/dev/null || true)
if [ "${#ggo_enabled[@]}" -ne 1 ] || [ "${ggo_enabled[0]}" != "$GGO_SITE_ENABLED" ]; then
  printf 'Expected one canonical GGO vhost, got: %s\n' "${ggo_enabled[*]:-none}" >&2
  exit 5
fi

nginx -t
systemctl enable nginx
systemctl reload nginx

curl --fail --silent --show-error http://127.0.0.1:8787/api/v1/health >/tmp/ggo-auth-health.json
python3 - <<'PY'
import json
p=json.load(open('/tmp/ggo-auth-health.json',encoding='utf-8'))
assert p.get('ok') is True and p.get('service') == 'ggo-auth', p
print('direct auth health: PASS', p)
PY

curl_local=(--silent --show-error --resolve "${GGO_HOST}:443:127.0.0.1")
health_code="$(curl "${curl_local[@]}" -o /tmp/ggo-nginx-health.json -w '%{http_code}' "https://${GGO_HOST}/api/v1/health")"
[ "$health_code" = 200 ] || { cat /tmp/ggo-nginx-health.json >&2; echo "HTTPS health returned $health_code" >&2; exit 6; }
python3 - <<'PY'
import json
p=json.load(open('/tmp/ggo-nginx-health.json',encoding='utf-8'))
assert p.get('ok') is True and p.get('service') == 'ggo-auth', p
print('nginx HTTPS auth health: PASS', p)
PY

register_code="$(curl "${curl_local[@]}" -o /tmp/ggo-register-probe.json -w '%{http_code}' -H 'Content-Type: application/json' --data '{}' "https://${GGO_HOST}/api/v1/auth/register")"
[ "$register_code" = 400 ] || { cat /tmp/ggo-register-probe.json >&2; echo "register route returned $register_code (expected 400, never 405)" >&2; exit 7; }
login_code="$(curl "${curl_local[@]}" -o /tmp/ggo-login-probe.json -w '%{http_code}' -H 'Content-Type: application/json' --data '{}' "https://${GGO_HOST}/api/v1/auth/login")"
[ "$login_code" = 401 ] || { cat /tmp/ggo-login-probe.json >&2; echo "login route returned $login_code (expected 401, never 405)" >&2; exit 8; }

echo "register POST route: PASS ($register_code)"
echo "login POST route: PASS ($login_code)"
echo "GunGloryOnline portal + auth installed"
echo "Backup: $BACKUP_DIR"
echo "Web root: $WEB_ROOT"
echo "Account API: https://${GGO_HOST}/api/v1/"
echo "Canonical nginx vhost: $GGO_SITE_ENABLED"
