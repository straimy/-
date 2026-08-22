#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is missing. Run: sudo bash bootstrap_ubuntu.sh" >&2
  exit 2
fi

if [[ ! -f .env ]]; then
  cp .env.example .env
  postgres_password="$(openssl rand -hex 32)"
  jwt_secret="$(openssl rand -hex 48)"
  sed -i "s|CHANGE_ME_TO_A_LONG_RANDOM_PASSWORD|${postgres_password}|" .env
  sed -i "s|CHANGE_ME_TO_A_LONG_RANDOM_SECRET_AT_LEAST_32_CHARS|${jwt_secret}|" .env
  chmod 600 .env
  echo "Generated production .env secrets."
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

chmod +x bootstrap_ubuntu.sh deploy_all.sh backup_now.sh health_check.sh game-server/run.sh 2>/dev/null || true
mkdir -p public site game-server/mods game-server/config/gunnerarena

# Validate every configured profile before touching running containers.
docker compose --profile backend --profile game config >/dev/null

echo "Starting GGO edge + account backend..."
docker compose --profile backend up -d --build

core_count=$(find game-server/mods -maxdepth 1 -type f \( -name 'gungloryonline-core-*.jar' -o -name 'gunnerarena-*.jar' \) | wc -l | tr -d ' ')
world_ready=false
if [[ -f game-server/world/level.dat ]]; then world_ready=true; fi
migration_ready=false
if [[ -f game-server/.ggo-world-ready ]]; then migration_ready=true; fi
classic_ready=false
if [[ -f game-server/world/.ggo-classic-ready ]] && grep -Eq '^result[=:]PASS$' game-server/world/.ggo-classic-ready; then classic_ready=true; fi

if [[ "$core_count" == "1" && "$world_ready" == "true" && "$migration_ready" == "true" ]]; then
  echo "Final Core + audited clean world detected; starting game server on this VDS..."
  docker compose --profile game up -d --build game-server
else
  echo "Game server not started yet. Production requires:"
  echo "  - exactly one Core jar"
  echo "  - game-server/world/level.dat"
  echo "  - game-server/.ggo-world-ready created only after command-block audit reports 0"
fi

echo
echo "GunGloryOnline single-node status:"
docker compose --profile backend --profile game ps || true

echo
echo "World readiness:"
echo "  clean-world: ${migration_ready}"
echo "  classic-smoke: ${classic_ready}"
if [[ "$migration_ready" == "true" && "$classic_ready" != "true" ]]; then
  echo "  Classic stays MIGRATING even if modes.properties requests ACTIVE."
fi

echo
echo "Public endpoints:"
echo "  https://${GGO_SITE_HOST:-ggo.kvicloud.ru}"
echo "  https://${GGO_UPDATES_HOST:-updates.ggo.kvicloud.ru}"
echo "  ${GGO_GAME_HOST:-play.ggo.kvicloud.ru}:${GGO_GAME_PORT:-24842} (when audited game world is installed)"

echo
echo "Waiting for public readiness..."
./health_check.sh
