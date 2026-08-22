#!/usr/bin/env python3
from pathlib import Path
import shutil
ROOT=Path("ga-build/src/main/java/arena/forge")
SRC=Path("hotfix/GgoMapIntegrityGuard.java")
DST=ROOT/"GgoMapIntegrityGuard.java"
if not ROOT.is_dir(): raise SystemExit("ga-build source tree missing")
if not SRC.is_file(): raise SystemExit("GgoMapIntegrityGuard.java missing")
shutil.copy2(SRC,DST)
text=DST.read_text(encoding="utf-8")
for required in ["ExplosionEvent.Detonate","getAffectedBlocks().clear()","RULE_MOBGRIEFING","RULE_DOFIRETICK","RULE_DOMOBSPAWNING","RULE_SHOWDEATHMESSAGES"]:
    if required not in text: raise SystemExit(f"stage51 map integrity missing: {required}")
print("Applied GGO Stage 51 immutable map integrity")
print(" - vanilla Minecraft death chat is disabled for the GGO kill feed")
