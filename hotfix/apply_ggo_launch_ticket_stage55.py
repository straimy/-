#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build/client-ui/src/main/java/arena/client/shell")
SRC = Path("hotfix/GgoLaunchTicketClient.java")
DST = ROOT / "GgoLaunchTicketClient.java"
if not ROOT.is_dir(): raise SystemExit("client-ui source tree missing")
if not SRC.is_file(): raise SystemExit("GgoLaunchTicketClient.java missing")
shutil.copy2(SRC, DST)
text = DST.read_text(encoding="utf-8")
for required in [
    'System.getenv("GGO_GAME_TICKET")',
    'Class.forName("arena.forge.GgoLaunchTicketNetwork")',
    'getMethod("sendTicket", String.class)',
    'ticket = null',
]:
    if required not in text: raise SystemExit(f"stage55 launch ticket client missing: {required}")
if 'System.out' in text or 'println' in text:
    raise SystemExit("stage55 launch ticket client must not log ticket/auth state")
print("Applied GGO Stage 55 launcher ticket client")
print(" - reads child-process-only ticket environment")
print(" - forwards once through Core network after connection")
print(" - does not expose ticket through UI or logs")
