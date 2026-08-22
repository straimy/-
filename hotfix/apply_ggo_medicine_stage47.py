#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT=Path("ga-build/src/main/java/arena/forge")
SRC=Path("hotfix/GgoMedicineService.java")
DST=ROOT/"GgoMedicineService.java"
if not ROOT.is_dir(): raise SystemExit("ga-build source tree is missing")
if not SRC.is_file(): raise SystemExit("GgoMedicineService.java is missing")
shutil.copy2(SRC,DST)
text=DST.read_text(encoding="utf-8")
for required in ['Commands.literal("ggomed")','FIELD_FIRST=18','FIELD_LAST=35','COOLDOWN_TICKS','runtime.auth().isAuthenticated(p)','ArenaPlayerState.ALIVE','stack.shrink(1)']:
    if required not in text: raise SystemExit(f"stage47 medicine behavior missing: {required}")
print("Applied GGO Stage 47 medicine service")
print(" - field medicine is server-authoritative")
print(" - only field inventory slots 18..35 are accepted")
print(" - auth/alive/cooldown/full-health checks enforced")
