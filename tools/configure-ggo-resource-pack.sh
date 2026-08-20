#!/usr/bin/env bash
set -euo pipefail

PROPERTIES="${1:-server.properties}"
if [[ ! -f "$PROPERTIES" ]]; then
  echo "server.properties not found: $PROPERTIES" >&2
  exit 1
fi

python3 - "$PROPERTIES" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")

values = {
    "require-resource-pack": "false",
    "resource-pack": r"https\://ggo.kvicloud.ru/content/files/v40/GunGloryOnline-ResourcePack-1.20.1-v5-swittie-social.zip",
    "resource-pack-sha1": "76ce370b59c7efde462ecbf35bee1cace86b8722",
    "resource-pack-prompt": r'{"text"\:"GunGloryOnline resource pack is recommended for the intended visuals and weapon models. Download it now?","color"\:"red"}',
}

lines = text.splitlines()
seen = set()
out = []
for line in lines:
    if "=" in line and not line.lstrip().startswith("#"):
        key = line.split("=", 1)[0].strip()
        if key in values:
            out.append(f"{key}={values[key]}")
            seen.add(key)
            continue
    out.append(line)

for key, value in values.items():
    if key not in seen:
        out.append(f"{key}={value}")

path.write_text("\n".join(out) + "\n", encoding="utf-8")
PY

echo "Configured optional GunGloryOnline server resource pack:"
grep -E '^(require-resource-pack|resource-pack=|resource-pack-sha1=|resource-pack-prompt=)' "$PROPERTIES"
