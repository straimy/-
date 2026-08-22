#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build/src/main/java/arena/forge")
SOURCE = Path("hotfix/InventoryUtilityCommands.java")
TARGET = ROOT / "InventoryUtilityCommands.java"

if not ROOT.is_dir():
    raise SystemExit("ga-build source tree is missing")
if not SOURCE.is_file():
    raise SystemExit("InventoryUtilityCommands.java is missing")

shutil.copy2(SOURCE, TARGET)
text = TARGET.read_text(encoding="utf-8")
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

print("Applied GGO Stage 42 authoritative inventory actions")
