#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build/src/main/java/arena/forge")
FILES = [
    "GgoOfficialAuthState.java",
    "GgoLaunchTicketNetwork.java",
    "GgoIdentityBridge.java",
    "AuthSecurityGuard.java",
    "LegacyAuthSyncGuard.java",
]

if not ROOT.is_dir():
    raise SystemExit("ga-build source tree missing")

for name in FILES:
    src = Path("hotfix") / name
    dst = ROOT / name
    if not src.is_file():
        raise SystemExit(f"Stage55 source missing: {src}")
    shutil.copy2(src, dst)

checks = {
    "GgoOfficialAuthState.java": ["GGO_SERVER_KEY", "sauth_authenticated", "bind("],
    "GgoLaunchTicketNetwork.java": ["launch_ticket", "X-GGO-Server-Key", "/auth/game-ticket/consume", "official-online"],
    "GgoIdentityBridge.java": ["bindAuthenticated", "AUTHENTICATED_BINDINGS", "invalid GGO account id"],
    "AuthSecurityGuard.java": ["OFFICIAL_LOGIN_TIMEOUT_TICKS", "PLAY ONLINE", "event.setCanceled(true)"],
    "LegacyAuthSyncGuard.java": ["GgoOfficialAuthState.required()", "Fail closed"],
}
for name, needles in checks.items():
    text = (ROOT / name).read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            raise SystemExit(f"Stage55 {name} missing: {needle}")

print("Applied GGO Stage 55 official launcher authentication")
print(" - one-shot launcher ticket is consumed server-side")
print(" - production login/register commands are fenced")
print(" - verified GGO account id becomes the runtime identity")
print(" - development servers retain the legacy auth fallback")
