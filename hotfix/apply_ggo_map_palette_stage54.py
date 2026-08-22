#!/usr/bin/env python3
from pathlib import Path
import shutil
ROOT=Path("ga-build/src/main/java/arena/forge")
SRC=Path("hotfix/GgoMapPaletteAuditService.java")
DST=ROOT/"GgoMapPaletteAuditService.java"
if not ROOT.is_dir(): raise SystemExit("ga-build source tree missing")
if not SRC.is_file(): raise SystemExit("GgoMapPaletteAuditService.java missing")
shutil.copy2(SRC,DST)
text=DST.read_text(encoding="utf-8")
for required in [
    'Commands.literal("ggopalette")','Commands.literal("start")','Commands.literal("export")',
    'BUDGET_PER_TICK=4096','level.hasChunkAt(cursor)','ForgeRegistries.BLOCKS.getKey',
    'ggo-map-palette.txt','use this as INPUT for resource-pack slimming'
]:
    if required not in text: raise SystemExit(f"stage54 palette audit missing: {required}")
print("Applied GGO Stage 54 map palette audit")
print(" - admin-only incremental scan with bounded per-tick work")
print(" - emits exact block registry IDs used by traversed authored map areas")
