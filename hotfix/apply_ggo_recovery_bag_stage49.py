#!/usr/bin/env python3
from pathlib import Path
import shutil
ROOT=Path("ga-build/src/main/java/arena/forge")
SRC=Path("hotfix/GgoRecoveryBagService.java")
DST=ROOT/"GgoRecoveryBagService.java"
if not ROOT.is_dir(): raise SystemExit("ga-build source tree is missing")
if not SRC.is_file(): raise SystemExit("GgoRecoveryBagService.java is missing")
shutil.copy2(SRC,DST)
text=DST.read_text(encoding="utf-8")
for required in ["FIELD ITEMS 18..35","PlayerDropsEvent","PlayerEvent.Clone","GgoRecoveryContents","ggo_keep_vanilla","event.getDrops().clear()","RightClickItem"]:
    if required not in text: raise SystemExit(f"stage49 recovery behavior missing: {required}")
print("Applied GGO Stage 49 recovery bag")
print(" - combat/ammo/armor retained")
print(" - field slots become one sealed owner-bound bag")
print(" - loose Minecraft death piles are suppressed")
