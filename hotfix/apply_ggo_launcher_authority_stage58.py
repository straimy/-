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
    "RETURN TO GGO LAUNCHER",
    "Online play starts from GGO Launcher with a fresh secure session.",
    "GGO CLIENT  •  BETA",
    "ONE ACCOUNT  •  SECURE ENTRY  •  GGO LAUNCHER",
    "new GgoSettingsScreen(this)",
]:
    if required not in text:
        raise SystemExit(f"stage58 frontend missing: {required}")
for forbidden in [
    "ConnectScreen.startConnecting",
    "ServerAddress.parseString",
    "play.kvicloud.ru:24842",
    "ENTER GGO",
    "OptionsScreen",
    "sendCommand(\"login",
    "sendCommand(\"register",
]:
    if forbidden in text:
        raise SystemExit(f"stage58 direct online/runtime entry leak: {forbidden}")

print("Applied GGO Stage 58 launcher-authoritative online entry")
print(" - in-client direct official reconnect removed")
print(" - fresh online sessions originate only from GGO Launcher")
print(" - fallback title surface is fully GGO-owned")
print(" - settings stay inside first-party GGO UI")
