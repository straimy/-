#!/usr/bin/env bash
set -euo pipefail

FORGE_VERSION="1.20.1-47.4.10"
MC_VERSION="1.20.1"
PORT="${GGO_GAME_PORT:-24842}"
XMS="${GGO_GAME_XMS:-1G}"
XMX="${GGO_GAME_XMX:-4G}"

mkdir -p mods logs config

core_count=$(find mods -maxdepth 1 -type f \( -name 'gungloryonline-core-*.jar' -o -name 'gunnerarena-*.jar' \) | wc -l | tr -d ' ')
if [[ "$core_count" != "1" ]]; then
  echo "GGO server requires exactly one Core jar in /server/mods; found $core_count" >&2
  find mods -maxdepth 1 -type f -name '*.jar' -print >&2 || true
  exit 2
fi

if [[ ! -f libraries/net/minecraftforge/forge/${FORGE_VERSION}/unix_args.txt ]]; then
  echo "Installing Forge ${FORGE_VERSION} runtime..."
  installer="forge-${FORGE_VERSION}-installer.jar"
  curl -fL --retry 5 --retry-all-errors \
    -o "$installer" \
    "https://maven.minecraftforge.net/net/minecraftforge/forge/${FORGE_VERSION}/forge-${FORGE_VERSION}-installer.jar"
  java -jar "$installer" --installServer .
  rm -f "$installer" "forge-${FORGE_VERSION}-installer.jar.log"
fi

echo 'eula=true' > eula.txt

touch server.properties
set_property() {
  local key="$1" value="$2"
  if grep -q "^${key}=" server.properties; then
    sed -i "s|^${key}=.*|${key}=${value}|" server.properties
  else
    printf '%s=%s\n' "$key" "$value" >> server.properties
  fi
}

set_property server-port "$PORT"
set_property online-mode true
set_property enable-rcon false
set_property enable-query false
set_property enforce-secure-profile true
set_property allow-flight false
set_property view-distance 10
set_property simulation-distance 8
set_property sync-chunk-writes true

# Command blocks remain disabled in the new single-node production target.
# If an imported legacy map still needs them, migrate that logic before enabling this profile.
set_property enable-command-block false

echo "Starting GunGloryOnline server on :${PORT} (${XMS}..${XMX})"
exec java "-Xms${XMS}" "-Xmx${XMX}" \
  -XX:+UseG1GC \
  -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=120 \
  @"libraries/net/minecraftforge/forge/${FORGE_VERSION}/unix_args.txt" nogui
