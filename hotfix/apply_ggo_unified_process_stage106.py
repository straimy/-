#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
SOURCE = Path("hotfix/GgoUnifiedSurfaceBridge.java")
TARGET = JAVA / "GgoUnifiedSurfaceBridge.java"

if not JAVA.is_dir():
    raise SystemExit("Stage106 client-ui source tree missing")
if not SOURCE.is_file():
    raise SystemExit("Stage106 readiness bridge source missing")
shutil.copy2(SOURCE, TARGET)

bridge = TARGET.read_text(encoding="utf-8")
for required in [
    'System.getenv("GGO_READY_FILE")',
    "GgoLaunchTicketClient.isOfficialLaunch()",
    "mc.screen instanceof GgoFrontEndScreen",
    "Files.writeString",
    '"ready\\n"',
    "StandardOpenOption.TRUNCATE_EXISTING",
]:
    if required not in bridge:
        raise SystemExit(f"Stage106 readiness bridge missing: {required}")

for forbidden in [
    "GGO_GAME_TICKET",
    "access_token",
    "player_id",
    "displayName",
]:
    if forbidden in bridge:
        raise SystemExit(f"Stage106 readiness bridge must not persist identity/credential data: {forbidden}")

print("Applied GGO Stage106 unified-process readiness bridge")
print(" - launcher receives a local READY marker only after first-party GGO UI is actually renderable")
print(" - early Mojang/Forge implementation remains behind the launcher-owned startup surface")
print(" - readiness marker contains no credential or account data")
