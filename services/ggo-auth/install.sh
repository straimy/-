#!/usr/bin/env bash
set -euo pipefail

if [ "${EUID}" -ne 0 ]; then
  echo "Run as root: sudo ./install.sh" >&2
  exit 1
fi

SRC_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
install -d -m 0755 /opt/ggo-auth
install -d -m 0750 /var/lib/ggo-auth

if ! id -u ggo-auth >/dev/null 2>&1; then
  useradd --system --home /var/lib/ggo-auth --shell /usr/sbin/nologin ggo-auth
fi

install -m 0755 "$SRC_DIR/server.py" /opt/ggo-auth/server.py
install -m 0755 "$SRC_DIR/secure_server.py" /opt/ggo-auth/secure_server.py
install -m 0644 "$SRC_DIR/news_seed.json" /opt/ggo-auth/news_seed.json
install -m 0644 "$SRC_DIR/ggo-auth.service" /etc/systemd/system/ggo-auth.service
chown -R ggo-auth:ggo-auth /var/lib/ggo-auth
chmod 0750 /var/lib/ggo-auth

systemctl daemon-reload
systemctl enable ggo-auth.service
# Always restart after replacing API code. `enable --now` alone leaves an already
# running process on the previous version and would skip DB migrations/features.
systemctl restart ggo-auth.service
sleep 1
systemctl --no-pager --full status ggo-auth.service || true

python3 - <<'PY'
import json, urllib.request
with urllib.request.urlopen('http://127.0.0.1:8787/api/v1/health', timeout=3) as r:
    data=json.load(r)
    assert data.get('ok') is True, data
    assert data.get('support_tickets') is True, data
    assert data.get('staff_roles') is True, data
    print('GGO auth health: PASS', data)
with urllib.request.urlopen('http://127.0.0.1:8787/api/v1/news', timeout=3) as r:
    news=json.load(r)
    assert news.get('schemaVersion') == 1, news
    assert len(news.get('items', [])) >= 8, news
    print('GGO news feed: PASS', len(news['items']))
PY
