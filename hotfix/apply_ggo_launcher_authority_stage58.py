#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build/client-ui/src/main/java/arena/client/shell")
SRC = Path("hotfix/GgoFrontEndScreen.java")
DST = ROOT / "GgoFrontEndScreen.java"

if not ROOT.is_dir():
    raise SystemExit("client-ui source tree missing")
if not SRC.is_file():
    raise SystemExit("GgoFrontEndScreen.java missing")
shutil.copy2(SRC, DST)

text = DST.read_text(encoding="utf-8")
for required in [
    "PLAY ONLINE",
    "TRAINING",
    "GgoLaunchTicketClient.isOfficialLaunch()",
    "ConnectScreen.startConnecting",
    "ServerAddress.parseString(OFFICIAL_SERVER)",
    'OFFICIAL_SERVER = "play.kvicloud.ru:24842"',
    "GGO CLIENT  •  BETA",
    "ACTIVITIES",
    "new GgoSettingsScreen(this)",
]:
    if required not in text:
        raise SystemExit(f"stage58 frontend missing: {required}")
for forbidden in [
    "ServerListScreen",
    "DirectJoinServerScreen",
    "MultiplayerScreen",
    "ENTER GGO",
    "OptionsScreen",
    "sendCommand(\"login",
    "sendCommand(\"register",
]:
    if forbidden in text:
        raise SystemExit(f"stage58 unsafe/vanilla online entry leak: {forbidden}")

print("Applied GGO Stage 58 launcher-authenticated menu-first entry")
print(" - launcher owns authentication and short-lived session credential")
print(" - GGO client owns final Online / Training activity choice")
print(" - Online uses only the trusted official route; no server browser or manual address")
print(" - settings stay inside first-party GGO UI")
