#!/usr/bin/env bash
set -euo pipefail

GGO_HOST="${GGO_HOST:-ggo.kvicloud.ru}"
PLAY_HOST="${PLAY_HOST:-play.kvicloud.ru}"
PLAY_PORT="${PLAY_PORT:-24842}"
BASE="https://${GGO_HOST}"

fail(){ echo "FAIL: $*" >&2; exit 1; }
pass(){ echo "PASS: $*"; }

command -v curl >/dev/null || fail "curl is required"
command -v python3 >/dev/null || fail "python3 is required"

health_tmp="$(mktemp)"
register_tmp="$(mktemp)"
login_tmp="$(mktemp)"
manifest_tmp="$(mktemp)"
support_tmp="$(mktemp)"
admin_tmp="$(mktemp)"
trap 'rm -f "$health_tmp" "$register_tmp" "$login_tmp" "$manifest_tmp" "$support_tmp" "$admin_tmp"' EXIT

health_code="$(curl --silent --show-error --output "$health_tmp" --write-out '%{http_code}' "$BASE/api/v1/health")"
[ "$health_code" = 200 ] || { cat "$health_tmp" >&2; fail "GET /api/v1/health returned HTTP $health_code"; }
python3 - "$health_tmp" <<'PY'
import json,sys
p=json.load(open(sys.argv[1],encoding='utf-8'))
assert p.get('ok') is True, p
assert p.get('service') == 'ggo-auth', p
assert p.get('game_tickets') is True, p
# New beta portal requires these capabilities. Failing here prevents a visually new
# support/admin frontend from being deployed against an old auth process.
assert p.get('support_tickets') is True, p
assert p.get('staff_roles') is True, p
PY
pass "public auth health + staff capabilities"

register_code="$(curl --silent --show-error --output "$register_tmp" --write-out '%{http_code}' -H 'Content-Type: application/json' --data '{}' "$BASE/api/v1/auth/register")"
[ "$register_code" = 400 ] || { cat "$register_tmp" >&2; fail "POST /api/v1/auth/register returned HTTP $register_code (expected 400 route probe, never 405)"; }
pass "register POST reaches auth service"

login_code="$(curl --silent --show-error --output "$login_tmp" --write-out '%{http_code}' -H 'Content-Type: application/json' --data '{}' "$BASE/api/v1/auth/login")"
[ "$login_code" = 401 ] || { cat "$login_tmp" >&2; fail "POST /api/v1/auth/login returned HTTP $login_code (expected 401 route probe, never 405)"; }
pass "login POST reaches auth service"

support_code="$(curl --silent --show-error --output "$support_tmp" --write-out '%{http_code}' "$BASE/support/")"
[ "$support_code" = 200 ] || fail "support page returned HTTP $support_code"
grep -q 'НАПИСАТЬ В ПОДДЕРЖКУ' "$support_tmp" || fail "support page is missing ticket CTA"
pass "support ticket UI"

admin_code="$(curl --silent --show-error --output "$admin_tmp" --write-out '%{http_code}' "$BASE/admin/")"
[ "$admin_code" = 200 ] || fail "staff console returned HTTP $admin_code"
grep -q 'STAFF CONSOLE' "$admin_tmp" || fail "staff console marker missing"
pass "staff console UI"

unauth_ticket_code="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' "$BASE/api/v1/support/tickets")"
[ "$unauth_ticket_code" = 401 ] || fail "support API auth wall returned HTTP $unauth_ticket_code (expected 401)"
pass "support API authentication wall"

curl --fail --silent --show-error "$BASE/content/manifests/beta.json" >"$manifest_tmp"
python3 - "$manifest_tmp" <<'PY'
import json,sys
p=json.load(open(sys.argv[1],encoding='utf-8'))
paths={f.get('path') for f in p.get('files',[])}
legacy={
 'mods/gungloryonline-core-runtime-v1-stage68.jar',
 'mods/gungloryonline-ui-runtime-v1-stage69.jar',
}
beta77={
 'mods/gungloryonline-core-runtime-v1-stage77.jar',
 'mods/gungloryonline-ui-runtime-v1-stage77.jar',
}
assert 'resourcepacks/GunGloryOnline-Official.zip' in paths, paths
assert 'resourcepacks/GunGloryOnline-ResourcePack-1.20.1-v5-swittie-social.zip' not in paths
if beta77 <= paths:
    assert not (legacy & paths), f'old and new GGO runtimes mixed: {sorted(legacy & paths)}'
    assert p.get('betaRuntimeStage') == 77, p.get('betaRuntimeStage')
    print('manifest runtime: stage77')
elif legacy <= paths:
    # Accepted only while production is intentionally still on the pre-rollout runtime.
    assert not (beta77 & paths), f'partial stage77 mixed into legacy manifest: {sorted(beta77 & paths)}'
    print('manifest runtime: legacy stage68/69')
else:
    raise AssertionError(f'incomplete/unknown GGO runtime pair: {sorted(paths)}')
PY
pass "production beta manifest is internally consistent"

python3 - "$PLAY_HOST" "$PLAY_PORT" <<'PY'
import socket,sys
host=sys.argv[1]; port=int(sys.argv[2])
infos=socket.getaddrinfo(host,port,type=socket.SOCK_STREAM)
if not infos:
    raise SystemExit('no DNS result')
last=None
for family,socktype,proto,_,addr in infos:
    s=socket.socket(family,socktype,proto); s.settimeout(4)
    try:
        s.connect(addr); print(f'connected {addr}'); s.close(); break
    except OSError as e:
        last=e; s.close()
else:
    raise SystemExit(f'cannot connect to {host}:{port}: {last}')
PY
pass "$PLAY_HOST:$PLAY_PORT TCP"

echo "PRODUCTION PORTAL + AUTH + MANIFEST + GAME PORT: GREEN"
