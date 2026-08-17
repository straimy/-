#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

SITE_HOST="${GGO_SITE_HOST:-ggo.kvicloud.ru}"
UPDATES_HOST="${GGO_UPDATES_HOST:-updates.ggo.kvicloud.ru}"
GAME_HOST="${GGO_GAME_HOST:-play.ggo.kvicloud.ru}"
GAME_PORT="${GGO_GAME_PORT:-24842}"
ATTEMPTS="${GGO_HEALTH_ATTEMPTS:-18}"
DELAY="${GGO_HEALTH_DELAY_SECONDS:-5}"

retry_http() {
  local name="$1" url="$2"
  local i
  for ((i=1; i<=ATTEMPTS; i++)); do
    if curl -fsS --connect-timeout 4 --max-time 8 "$url" >/dev/null; then
      printf 'READY  %-16s %s\n' "$name" "$url"
      return 0
    fi
    sleep "$DELAY"
  done
  printf 'FAILED %-16s %s\n' "$name" "$url" >&2
  return 1
}

failed=0
retry_http "account-api" "https://${SITE_HOST}/api/v1/health" || failed=1
retry_http "website" "https://${SITE_HOST}/" || failed=1
retry_http "updates" "https://${UPDATES_HOST}/api/status.json" || failed=1

if docker compose --profile game ps --status running game-server --format '{{.Service}}' 2>/dev/null | grep -qx game-server; then
  if timeout 6 bash -c "</dev/tcp/${GAME_HOST}/${GAME_PORT}" 2>/dev/null; then
    printf 'READY  %-16s %s:%s\n' "game-server" "$GAME_HOST" "$GAME_PORT"
  else
    printf 'FAILED %-16s %s:%s\n' "game-server" "$GAME_HOST" "$GAME_PORT" >&2
    failed=1
  fi
else
  printf 'SKIP   %-16s no final world/Core installed yet\n' "game-server"
fi

if (( failed != 0 )); then
  echo "GGO readiness check failed." >&2
  exit 4
fi

echo "GunGloryOnline single-node READY."
