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
# Stage58 owns launcher authority and trusted online routing. Presentation may evolve,
# but Online must remain gated by the launcher ticket and locked to the official server.
for required in [
    "PLAY ONLINE",
    "PRACTICE · COMING SOON",
    "GgoLaunchTicketClient.isOfficialLaunch()",
    "GgoLaunchTicketClient.canStartOnline()",
    "ConnectScreen.startConnecting",
    "ServerAddress.parseString(OFFICIAL_SERVER)",
    'OFFICIAL_SERVER = "play.kvicloud.ru:24842"',
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
    "LAUNCHER REQUIRED",
    "RETURN TO GGO LAUNCHER",
    "sendCommand(\"login",
    "sendCommand(\"register",
]:
    if forbidden in text:
        raise SystemExit(f"stage58 unsafe/legacy online entry leak: {forbidden}")

print("Applied GGO Stage 58 launcher-authenticated clean entry")
print(" - launcher owns authentication and short-lived session credential")
print(" - client exposes Online plus a clearly disabled future Practice surface")
print(" - Online uses only the trusted official route; no server browser or manual address")
print(" - settings stay inside first-party GGO UI")
