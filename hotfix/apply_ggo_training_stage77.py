#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build/client-ui/src/main/java/arena/client/shell")
if not ROOT.is_dir():
    raise SystemExit("client-ui source tree missing")

for name in ["GgoFrontEndScreen.java", "GgoTrainingScreen.java"]:
    src = Path("hotfix") / name
    dst = ROOT / name
    if not src.is_file():
        raise SystemExit(f"missing canonical training source: {src}")
    shutil.copy2(src, dst)

front = (ROOT / "GgoFrontEndScreen.java").read_text(encoding="utf-8")
training = (ROOT / "GgoTrainingScreen.java").read_text(encoding="utf-8")

for required in [
    'Component.literal("TRAINING")',
    'new GgoTrainingScreen(this)',
    'PLAY ONLINE',
    'OFFICIAL_SERVER = "play.kvicloud.ru:24842"',
]:
    if required not in front:
        raise SystemExit(f"Stage 77 frontend missing: {required}")

for required in [
    'TRAINING  •  OFFLINE',
    'AIM RANGE',
    'MOVEMENT',
    'LOADOUT LAB',
    'No server connection · no online rewards · no ticket consumption',
]:
    if required not in training:
        raise SystemExit(f"Stage 77 training hub missing: {required}")

for forbidden in [
    'ConnectScreen',
    'ServerAddress',
    'ServerData',
    'GGO_GAME_TICKET',
    'GgoLaunchTicketNetwork',
    'play.kvicloud.ru',
]:
    if forbidden in training:
        raise SystemExit(f"Stage 77 training network leak: {forbidden}")

if 'GgoShellScreen(GgoShellScreen.Page.ACTIVITIES)' in front:
    raise SystemExit("Stage 77 TRAINING still routes to generic Activities")

print("Applied GGO Stage 77 dedicated offline training branch")
print(" - TRAINING no longer aliases Activities")
print(" - dedicated Aim / Movement / Loadout training hub")
print(" - no server route or ticket handling in training surface")
