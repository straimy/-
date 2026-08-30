#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
BACKUP_ROOT="${GGO_BACKUP_DIR:-/opt/ggo/backups}"
KEEP_DAYS="${GGO_BACKUP_KEEP_DAYS:-7}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
WORK="${BACKUP_ROOT}/.${STAMP}.tmp"
FINAL="${BACKUP_ROOT}/ggo-${STAMP}.tar.gz"

mkdir -p "$BACKUP_ROOT"
rm -rf "$WORK"
mkdir -p "$WORK"

if [[ ! -f .env ]]; then
  echo "Missing .env; deploy GGO first." >&2
  exit 2
fi
set -a
# shellcheck disable=SC1091
source .env
set +a

# Database dump is consistent and does not require exposing PostgreSQL publicly.
if docker compose --profile backend ps --status running postgres --format '{{.Service}}' | grep -qx postgres; then
  docker compose --profile backend exec -T postgres \
    pg_dump -U "${POSTGRES_USER:-ggo}" -d "${POSTGRES_DB:-ggo}" --clean --if-exists \
    > "${WORK}/postgres.sql"
fi

# GGO skin assets live in the account-api volume; stream them out without exposing the volume.
if docker compose --profile backend ps --status running account-api --format '{{.Service}}' | grep -qx account-api; then
  docker compose --profile backend exec -T account-api tar -C /data -czf - skins \
    > "${WORK}/skins.tar.gz"
fi

# Game world/config are bind-mounted, so back them up directly.
if [[ -f game-server/world/level.dat ]]; then
  tar -C game-server -czf "${WORK}/world.tar.gz" world
fi
if [[ -d game-server/config ]]; then
  tar -C game-server -czf "${WORK}/game-config.tar.gz" config
fi

cp .env "${WORK}/production.env"
chmod 600 "${WORK}/production.env"

cat > "${WORK}/README.txt" <<EOF
GunGloryOnline single-node backup
UTC timestamp: ${STAMP}
Contains server-owned state only: database, GGO skins, game world/config, and production environment.
Treat this archive as SECRET because production.env contains credentials.
EOF

if ! find "$WORK" -mindepth 1 -maxdepth 1 -type f | grep -q .; then
  echo "Nothing to back up." >&2
  rm -rf "$WORK"
  exit 3
fi

tar -C "$WORK" -czf "${FINAL}.tmp" .
mv "${FINAL}.tmp" "$FINAL"
chmod 600 "$FINAL"
rm -rf "$WORK"

find "$BACKUP_ROOT" -maxdepth 1 -type f -name 'ggo-*.tar.gz' -mtime "+${KEEP_DAYS}" -delete

echo "$FINAL"
