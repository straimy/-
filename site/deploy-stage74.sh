#!/usr/bin/env bash
set -euo pipefail

if [ "${EUID}" -ne 0 ]; then
  echo "Run as root: sudo ./deploy-stage74.sh <package-dir>" >&2
  exit 1
fi

SITE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PACKAGE_DIR="${1:-}"
NOTES="GGO launcher stability, account persistence and client runtime fixes"
VERIFY_DIR="$(mktemp -d /tmp/ggo-stage74-verify.XXXXXX)"
trap 'rm -rf "$VERIFY_DIR"' EXIT

if [ -z "$PACKAGE_DIR" ]; then
  echo "Usage: sudo $0 <package-dir>" >&2
  exit 2
fi
PACKAGE_DIR="$(cd "$PACKAGE_DIR" && pwd)"

for file in \
  GunGloryOnline-Launcher-Windows.exe \
  GunGloryOnline-Launcher-Ubuntu-Debian.deb \
  GunGloryOnline-Launcher-Linux.AppImage; do
  test -s "$PACKAGE_DIR/$file" || {
    echo "Required package missing: $PACKAGE_DIR/$file" >&2
    exit 3
  }
done

VERSION="$(dpkg-deb -f "$PACKAGE_DIR/GunGloryOnline-Launcher-Ubuntu-Debian.deb" Version)"
if ! [[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Invalid Ubuntu package version: $VERSION" >&2
  exit 4
fi

echo "Publishing GunGloryOnline Launcher $VERSION"
chmod +x "$SITE_DIR/install-site.sh" "$SITE_DIR/publish-launcher-update.sh"
"$SITE_DIR/install-site.sh"
"$SITE_DIR/publish-launcher-update.sh" "$PACKAGE_DIR" "$VERSION" "$NOTES"

curl --fail --silent --show-error "https://ggo.kvicloud.ru/install-launcher.sh" >"$VERIFY_DIR/install-launcher.sh"
head -n 1 "$VERIFY_DIR/install-launcher.sh" | grep -q '^#!/usr/bin/env bash$'
bash -n "$VERIFY_DIR/install-launcher.sh"

curl --fail --silent --show-error "https://ggo.kvicloud.ru/content/launcher/latest-beta.json" >"$VERIFY_DIR/latest-beta.json"
VERSION="$VERSION" VERIFY_DIR="$VERIFY_DIR" python3 - <<'PY'
import json, os
p=json.load(open(os.path.join(os.environ['VERIFY_DIR'],'latest-beta.json'),encoding='utf-8'))
assert p.get('version') == os.environ['VERSION'], p
for key in ('windowsExe','linuxDeb','linuxAppImage'):
    item=p.get(key) or {}
    assert item.get('url','').startswith('https://ggo.kvicloud.ru/downloads/'), (key,item)
    sha=item.get('sha256','')
    assert len(sha)==64 and all(c in '0123456789abcdef' for c in sha.lower()), (key,sha)
print('Stage 74 public update manifest: PASS', p['version'])
PY

# Prove the bytes served publicly are exactly the bytes described by the manifest.
VERIFY_DIR="$VERIFY_DIR" python3 - <<'PY'
import json, os
p=json.load(open(os.path.join(os.environ['VERIFY_DIR'],'latest-beta.json'),encoding='utf-8'))
with open(os.path.join(os.environ['VERIFY_DIR'],'packages.tsv'),'w',encoding='utf-8') as h:
    for key in ('windowsExe','linuxDeb','linuxAppImage'):
        item=p[key]
        h.write(f"{key}\t{item['url']}\t{item['sha256'].lower()}\n")
PY

while IFS=$'\t' read -r key url expected_sha; do
  target="$VERIFY_DIR/$key.bin"
  echo "Verifying public package: $key"
  curl --fail --location --silent --show-error "$url" -o "$target"
  test -s "$target"
  actual_sha="$(sha256sum "$target" | awk '{print $1}')"
  if [ "$actual_sha" != "$expected_sha" ]; then
    echo "Public package hash mismatch for $key" >&2
    echo "Expected: $expected_sha" >&2
    echo "Actual:   $actual_sha" >&2
    exit 5
  fi
done <"$VERIFY_DIR/packages.tsv"

# A launcher update must never publish a manifest whose required game payload was
# removed by the website refresh. Validate every required v40 URL before GREEN.
curl --fail --silent --show-error "https://ggo.kvicloud.ru/content/manifests/beta.json" >"$VERIFY_DIR/game-manifest.json"
VERIFY_DIR="$VERIFY_DIR" python3 - <<'PY'
import json, os
p=json.load(open(os.path.join(os.environ['VERIFY_DIR'],'game-manifest.json'),encoding='utf-8'))
with open(os.path.join(os.environ['VERIFY_DIR'],'game-urls.txt'),'w',encoding='utf-8') as h:
    for item in p.get('files', []):
        if item.get('required', False):
            h.write(item['url'] + '\n')
PY
while IFS= read -r url; do
  [ -n "$url" ] || continue
  curl --fail --location --silent --show-error --head "$url" >/dev/null || {
    echo "Required GGO client payload is unavailable: $url" >&2
    exit 6
  }
done <"$VERIFY_DIR/game-urls.txt"
echo "Required GGO client payload: PASS"

curl --fail --silent --show-error "https://ggo.kvicloud.ru/api/v1/health" >"$VERIFY_DIR/auth-health.json"
python3 - "$VERIFY_DIR/auth-health.json" <<'PY'
import json, sys
p=json.load(open(sys.argv[1],encoding='utf-8'))
assert p.get('ok') is True, p
print('GGO auth health: PASS')
PY

python3 - <<'PY'
import socket
host='play.kvicloud.ru'
port=24842
with socket.create_connection((host, port), timeout=8):
    pass
print(f'Official game TCP: PASS {host}:{port}')
PY

echo "Stage 74 launcher update deploy: GREEN ($VERSION)"
echo "Ubuntu update command: curl -fsSL https://ggo.kvicloud.ru/install-launcher.sh | bash"
