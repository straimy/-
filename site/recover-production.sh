#!/usr/bin/env bash
set -euo pipefail

# Stage 73 production recovery helper.
# Run on the live VDS as root from the current launcher/bootstrap-v0.1 checkout/payload.
# It preserves the existing auth DB, repairs the canonical nginx/auth route, then brings
# the existing Forge 1.20.1 server up on the official GGO port under systemd.

if [ "${EUID}" -ne 0 ]; then
  echo "Run as root" >&2
  exit 1
fi

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AUTH_ENV="/etc/ggo-auth.env"
AUTH_URL="https://ggo.kvicloud.ru/api/v1"
SERVICE_FILE="/etc/systemd/system/ggo-game.service"

if [ ! -x "$HERE/install-site.sh" ]; then
  chmod +x "$HERE/install-site.sh"
fi

# install-site.sh makes its own timestamped backup of /var/lib/ggo-auth/auth.db and
# disables duplicate enabled nginx vhosts before reloading the canonical TLS vhost.
"$HERE/install-site.sh"

if [ ! -f "$AUTH_ENV" ]; then
  echo "$AUTH_ENV is missing; refusing to start official server without GGO auth configuration" >&2
  exit 2
fi
if ! grep -q '^GGO_SERVER_KEY=.' "$AUTH_ENV"; then
  echo "GGO_SERVER_KEY is missing from $AUTH_ENV; keep the existing secret and add it before continuing" >&2
  exit 3
fi
chmod 600 "$AUTH_ENV"

if grep -q '^GGO_AUTH_API_URL=' "$AUTH_ENV"; then
  sed -i "s#^GGO_AUTH_API_URL=.*#GGO_AUTH_API_URL=$AUTH_URL#" "$AUTH_ENV"
else
  printf '\nGGO_AUTH_API_URL=%s\n' "$AUTH_URL" >> "$AUTH_ENV"
fi

# Locate the already-installed Forge server by its exact runtime args file. The previous
# Stage72 deployment log shows this installation exists; do not create a second server tree.
ARGS="$(find /root /opt /srv /home -type f -path '*/libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt' -print -quit 2>/dev/null || true)"
if [ -z "$ARGS" ]; then
  echo "Forge 1.20.1-47.4.10 unix_args.txt not found" >&2
  exit 4
fi
SERVER_DIR="${ARGS%%/libraries/*}"
RUN_SH="$SERVER_DIR/run.sh"
PROPS="$SERVER_DIR/server.properties"

if [ ! -f "$RUN_SH" ] || [ ! -f "$PROPS" ]; then
  echo "Detected server directory is incomplete: $SERVER_DIR" >&2
  exit 5
fi

echo "Using existing Forge server: $SERVER_DIR"

# Keep the world and all server state. Only force the official listener settings.
if grep -q '^server-port=' "$PROPS"; then
  sed -i 's/^server-port=.*/server-port=24842/' "$PROPS"
else
  echo 'server-port=24842' >> "$PROPS"
fi
if grep -q '^server-ip=' "$PROPS"; then
  sed -i 's/^server-ip=.*/server-ip=/' "$PROPS"
else
  echo 'server-ip=' >> "$PROPS"
fi

chmod +x "$RUN_SH"
cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=GunGloryOnline Official Forge Runtime
After=network-online.target ggo-auth.service
Wants=network-online.target
Requires=ggo-auth.service

[Service]
Type=simple
WorkingDirectory=$SERVER_DIR
EnvironmentFile=$AUTH_ENV
ExecStart=/bin/bash $RUN_SH nogui
Restart=on-failure
RestartSec=8
TimeoutStopSec=90
KillSignal=SIGINT

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable ggo-game.service
systemctl restart ggo-game.service

if command -v ufw >/dev/null 2>&1; then
  ufw allow 24842/tcp >/dev/null || true
fi

# Wait for Forge to bind. A slow modded server can need time, so give it up to 120s.
ready=0
for _ in $(seq 1 60); do
  if ss -ltn 2>/dev/null | awk '{print $4}' | grep -Eq '(^|:)24842$'; then
    ready=1
    break
  fi
  if ! systemctl is-active --quiet ggo-game.service; then
    break
  fi
  sleep 2
done

if [ "$ready" -ne 1 ]; then
  systemctl status ggo-game.service --no-pager || true
  journalctl -u ggo-game.service -n 120 --no-pager || true
  echo "GGO game server did not bind TCP 24842" >&2
  exit 6
fi

echo "ggo-game.service: ACTIVE"
echo "TCP 24842: LISTENING"

chmod +x "$HERE/verify-production.sh"
"$HERE/verify-production.sh"
