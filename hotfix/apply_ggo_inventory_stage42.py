#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build/src/main/java/arena/forge")
SOURCES = [Path("hotfix/ArenaBeltGuard.java"), Path("hotfix/InventoryUtilityCommands.java")]

if not ROOT.is_dir():
    raise SystemExit("ga-build source tree is missing")
for source in SOURCES:
    if not source.is_file():
        raise SystemExit(f"missing {source}")
    shutil.copy2(source, ROOT/source.name)

target = ROOT / "InventoryUtilityCommands.java"
text = target.read_text(encoding="utf-8")
for required in [
    'Commands.literal("ggoinv")',
    'Commands.literal("select")',
    'Commands.literal("swap")',
    'Commands.literal("drop")',
    'GgoSupplyExtractionService.isSupply(s)',
    'sameStorageCompartment(from,to)',
    'runtime.auth().isAuthenticated(p)',
]:
    if required not in text:
        raise SystemExit(f"stage42 server inventory behavior missing: {required}")

belt=(ROOT/"ArenaBeltGuard.java").read_text(encoding="utf-8")
for required in ["COMBAT_SLOTS=3","AMMO_FIRST=9,AMMO_LAST=17","normalizeAmmoSlots(p)","removePhysicalMenuCompasses(p)"]:
    if required not in belt:
        raise SystemExit(f"stage42 belt behavior missing: {required}")

print("Applied GGO Stage 42 authoritative inventory actions + belt policy")
