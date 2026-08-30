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
GAME_ENV="/etc/ggo-game.env"
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

# The auth API must keep the server key because /auth/game-ticket/consume validates
# X-GGO-Server-Key against this exact environment value. Do not print the secret.
SERVER_KEY="$(sed -n 's/^GGO_SERVER_KEY=//p' "$AUTH_ENV" | head -n1)"
if [ -z "$SERVER_KEY" ]; then
  echo "GGO_SERVER_KEY resolved empty from $AUTH_ENV" >&2
  exit 3
fi

# Give the game process only the two variables it needs instead of exposing the whole
# auth service environment. The same server key is intentionally shared by the auth
# API and the official Forge server, but remains root-readable only.
umask 077
{
  printf 'GGO_SERVER_KEY=%s\n' "$SERVER_KEY"
  printf 'GGO_AUTH_API_URL=%s\n' "$AUTH_URL"
} > "$GAME_ENV"
chmod 600 "$GAME_ENV"
unset SERVER_KEY

# Locate the already-installed Forge server by its exact runtime args file. Do not create
# a second server tree or touch its world data.
ARGS="$(find /root /opt /srv /home -type f -path '*/libraries/net/minecraftforge/forge/1.20.1-47.4.10/unix_args.txt' -print -quit 2>/dev/null || true)"
if [ -z "$ARGS" ]; then
  echo "Forge 1.20.1-47.4.10 unix_args.txt not found" >&2
  exit 4
fi
SERVER_DIR="${ARGS%%/libraries/*}"
RUN_SH="$SERVER_DIR/run.sh"
PROPS="$SERVER_DIR/server.properties"
MODS="$SERVER_DIR/mods"

if [ ! -f "$RUN_SH" ] || [ ! -f "$PROPS" ] || [ ! -d "$MODS" ]; then
  echo "Detected server directory is incomplete: $SERVER_DIR" >&2
  exit 5
fi

echo "Using existing Forge server: $SERVER_DIR"

# Prepare a transactional backup before touching the game runtime. If any later game
# deployment or verification step fails, EXIT rollback restores run.sh, server.properties,
# the previous Core jar set, and any prior systemd unit automatically.
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
SERVER_BACKUP="$SERVER_DIR/ggo-hotfix-backup-$STAMP"
mkdir -p "$SERVER_BACKUP"
cp -a "$RUN_SH" "$SERVER_BACKUP/run.sh.previous"
cp -a "$PROPS" "$SERVER_BACKUP/server.properties.previous"
SERVICE_EXISTED=0
if [ -f "$SERVICE_FILE" ]; then
  cp -a "$SERVICE_FILE" "$SERVER_BACKUP/ggo-game.service.previous"
  SERVICE_EXISTED=1
fi

shopt -s nullglob
existing_core=("$MODS"/gungloryonline-core-*.jar "$MODS"/gunnerarena-*.jar)
for file in "${existing_core[@]}"; do
  cp -a "$file" "$SERVER_BACKUP/"
done
shopt -u nullglob

DEPLOY_COMMITTED=0
rollback_game_runtime() {
  status=$?
  if [ "$DEPLOY_COMMITTED" -eq 1 ]; then
    return "$status"
  fi

  echo "Stage73 game deployment did not complete; restoring previous server runtime" >&2
  systemctl stop ggo-game.service >/dev/null 2>&1 || true
  cp -a "$SERVER_BACKUP/run.sh.previous" "$RUN_SH" || true
  cp -a "$SERVER_BACKUP/server.properties.previous" "$PROPS" || true

  shopt -s nullglob
  current_core=("$MODS"/gungloryonline-core-*.jar "$MODS"/gunnerarena-*.jar)
  for file in "${current_core[@]}"; do rm -f "$file"; done
  backed_core=("$SERVER_BACKUP"/gungloryonline-core-*.jar "$SERVER_BACKUP"/gunnerarena-*.jar)
  for file in "${backed_core[@]}"; do cp -a "$file" "$MODS/"; done
  shopt -u nullglob

  if [ "$SERVICE_EXISTED" -eq 1 ]; then
    cp -a "$SERVER_BACKUP/ggo-game.service.previous" "$SERVICE_FILE" || true
  else
    rm -f "$SERVICE_FILE"
  fi
  systemctl daemon-reload >/dev/null 2>&1 || true
  echo "Rollback complete. Backup retained at: $SERVER_BACKUP" >&2
  return "$status"
}
trap rollback_game_runtime EXIT

# In the Stage73 bundle the hardened official run script and Stage68 Core sit next to
# the web payload. Install them into the detected existing server; originals are already
# preserved above for automatic rollback and manual recovery.
BUNDLED_RUN=""
for candidate in "$HERE/../../game-server/infra/run.sh" "$HERE/../infra/vds/game-server/run.sh"; do
  if [ -f "$candidate" ]; then BUNDLED_RUN="$candidate"; break; fi
done
if [ -n "$BUNDLED_RUN" ]; then
  install -m 0755 "$BUNDLED_RUN" "$RUN_SH"
  echo "Installed hardened GGO run.sh"
fi

BUNDLED_CORE=""
for candidate in \
  "$HERE/../../game-server/mods/gungloryonline-core-runtime-v1-stage68.jar" \
  "$HERE/content/files/v40/gungloryonline-core-runtime-v1-stage68.jar"; do
  if [ -f "$candidate" ]; then BUNDLED_CORE="$candidate"; break; fi
done
if [ -n "$BUNDLED_CORE" ]; then
  shopt -s nullglob
  old_core=("$MODS"/gungloryonline-core-*.jar "$MODS"/gunnerarena-*.jar)
  for file in "${old_core[@]}"; do rm -f "$file"; done
  shopt -u nullglob
  install -m 0644 "$BUNDLED_CORE" "$MODS/gungloryonline-core-runtime-v1-stage68.jar"
  echo "Installed Stage68 server Core"
fi

# GGO Account is the sole official online identity. The launcher intentionally starts
# Minecraft with the GGO profile rather than a Microsoft session, so vanilla Mojang
# online-mode would reject the player before the one-shot GGO ticket handshake can run.
# The Core refuses official startup/auth unlock without GGO_SERVER_KEY.
set_property() {
  local key="$1" value="$2"
  if grep -q "^${key}=" "$PROPS"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$PROPS"
  else
    printf '%s=%s\n' "$key" "$value" >> "$PROPS"
  fi
}
set_property server-port 24842
set_property server-ip ""
set_property online-mode false
set_property enforce-secure-profile false

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
EnvironmentFile=$GAME_ENV
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
  echo "Server hotfix backup: $SERVER_BACKUP" >&2
  exit 6
fi

echo "ggo-game.service: ACTIVE"
echo "TCP 24842: LISTENING"
echo "Server hotfix backup: $SERVER_BACKUP"

chmod +x "$HERE/verify-production.sh"
"$HERE/verify-production.sh"

# Public verification passed; keep the new runtime and disable automatic rollback.
DEPLOY_COMMITTED=1
trap - EXIT
echo "Stage73 runtime deployment committed"
