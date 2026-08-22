#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build/client-ui/src/main/java/arena/client/shell")
SOURCES = [
    Path("hotfix/GgoEntryExperience.java"),
    Path("hotfix/GgoEntryDisconnectedScreen.java"),
]

if not ROOT.is_dir():
    raise SystemExit("client-ui source tree missing")
for source in SOURCES:
    if not source.is_file():
        raise SystemExit(f"missing {source}")
    shutil.copy2(source, ROOT / source.name)

experience = (ROOT / "GgoEntryExperience.java").read_text(encoding="utf-8")
disconnected = (ROOT / "GgoEntryDisconnectedScreen.java").read_text(encoding="utf-8")

for required in [
    "ConnectScreen",
    "ReceivingLevelScreen",
    "DisconnectedScreen.class.getDeclaredFields()",
    "OFFICIAL GGO NETWORK  •  VERIFIED ENTRY",
    "GgoEntryDisconnectedScreen",
]:
    if required not in experience:
        raise SystemExit(f"stage57 entry experience missing: {required}")

for required in [
    "RETURN TO LAUNCHER",
    "COPY ERROR",
    "SESSION EXPIRED",
    "No in-game login is required.",
]:
    if required not in disconnected:
        raise SystemExit(f"stage57 disconnect screen missing: {required}")

for forbidden in [
    "Forge-native",
    "server list",
    "Return to the launcher/server list",
    "Minecraft runtime is hidden",
]:
    if forbidden in experience or forbidden in disconnected:
        raise SystemExit(f"stage57 user-facing runtime leak: {forbidden}")

print("Applied GGO Stage 57 entry UX hardening")
print(" - secure connection screens are fully GGO-owned")
print(" - server disconnect reason is preserved when available")
print(" - expired sessions return through the launcher")
print(" - no server-list or runtime implementation text is shown")
