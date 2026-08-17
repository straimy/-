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

chmod +x bootstrap_ubuntu.sh deploy_all.sh game-server/run.sh 2>/dev/null || true
mkdir -p public site game-server/mods game-server/config

# Validate core services before touching running containers.
docker compose --profile backend config >/dev/null

echo "Starting GGO edge + account backend..."
docker compose --profile backend up -d --build

core_count=$(find game-server/mods -maxdepth 1 -type f \( -name 'gungloryonline-core-*.jar' -o -name 'gunnerarena-*.jar' \) | wc -l | tr -d ' ')
world_ready=false
if [[ -f game-server/world/level.dat ]]; then world_ready=true; fi

if [[ "$core_count" == "1" && "$world_ready" == "true" ]]; then
  echo "Final Core + world detected; starting game server on this VDS..."
  docker compose --profile game up -d game-server
else
  echo "Game server not started yet: requires exactly one Core jar and game-server/world/level.dat."
fi

echo
echo "GunGloryOnline single-node status:"
docker compose --profile backend --profile game ps || true

echo
echo "Public endpoints:"
echo "  https://ggo.kvicloud.ru"
echo "  https://updates.ggo.kvicloud.ru"
echo "  play.ggo.kvicloud.ru:${GGO_GAME_PORT:-24842} (when game world is installed)"
