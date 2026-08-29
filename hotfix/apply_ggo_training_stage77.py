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
        raise SystemExit(f"missing canonical practice source: {src}")
    shutil.copy2(src, dst)

front = (ROOT / "GgoFrontEndScreen.java").read_text(encoding="utf-8")
practice = (ROOT / "GgoTrainingScreen.java").read_text(encoding="utf-8")

for required in [
    'PRACTICE · COMING SOON',
    'SETTINGS',
    'OFFICIAL_SERVER = "play.kvicloud.ru:24842"',
    'GgoLaunchTicketClient.canStartOnline()',
]:
    if required not in front:
        raise SystemExit(f"Stage 77 frontend missing: {required}")
if 'PLAY ONLINE' not in front and 'mc.execute(this::connectOfficial);' not in front:
    raise SystemExit("Stage 77 frontend missing trusted online action")

for required in [
    'Component.literal("PRACTICE")',
    'OFFLINE SANDBOX · COMING SOON',
    'Practice is not implemented yet.',
]:
    if required not in practice:
        raise SystemExit(f"Stage 77 practice screen missing: {required}")

for forbidden in [
    'ConnectScreen',
    'ServerAddress',
    'ServerData',
    'GGO_GAME_TICKET',
    'GgoLaunchTicketNetwork',
    'play.kvicloud.ru',
    'AIM RANGE',
    'MOVEMENT',
    'LOADOUT LAB',
]:
    if forbidden in practice:
        raise SystemExit(f"Stage 77 practice leak/legacy control: {forbidden}")

if 'GgoShellScreen(GgoShellScreen.Page.ACTIVITIES)' in front:
    raise SystemExit("Stage 77 practice still routes to generic Activities")

print("Applied GGO Stage 77 practice branch")
print(" - old decorative Aim / Movement / Loadout buttons removed")
print(" - Practice is explicitly marked coming soon")
print(" - official entry supports legacy button and Stage111 auto-connect UX")
print(" - no server route or ticket handling in practice surface")
