#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ -f .env ]]; then
  echo ".env already exists; refusing to overwrite it." >&2
  exit 1
fi

POSTGRES_PASSWORD="$(openssl rand -hex 32)"
GGO_JWT_SECRET="$(openssl rand -hex 48)"

cat > .env <<EOF
GGO_SITE_HOST=ggo.kvicloud.ru
GGO_UPDATES_HOST=updates.ggo.kvicloud.ru
GGO_PUBLIC_BASE_URL=https://updates.ggo.kvicloud.ru
POSTGRES_DB=ggo
POSTGRES_USER=ggo
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
GGO_JWT_SECRET=${GGO_JWT_SECRET}
EOF

chmod 600 .env
printf '%s\n' 'Created infra/vds/.env with random PostgreSQL and JWT secrets.'
printf '%s\n' 'Keep this file only on the VDS. Do not commit or send its contents.'
