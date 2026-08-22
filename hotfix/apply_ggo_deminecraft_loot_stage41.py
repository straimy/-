#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build/src/main/java/arena/forge")
SOURCE = Path("hotfix/GgoVanillaLootFence.java")
TARGET = ROOT / "GgoVanillaLootFence.java"

if not ROOT.is_dir():
    raise SystemExit("ga-build source tree is missing")
if not SOURCE.is_file():
    raise SystemExit("GgoVanillaLootFence.java is missing")

shutil.copy2(SOURCE, TARGET)
text = TARGET.read_text(encoding="utf-8")
for required in [
    "LivingDropsEvent",
    "LivingExperienceDropEvent",
    "EntityItemPickupEvent",
    '"minecraft".equals(id.getNamespace())',
    "GgoSupplyExtractionService.isSupply(stack)",
    'contains("ggoLootPoint")',
]:
    if required not in text:
        raise SystemExit(f"stage41 required behavior missing: {required}")

print("Applied GGO Stage 41 de-Minecraft loot fence")
print(" - vanilla mob drops are stripped")
print(" - vanilla XP drops are zeroed")
print(" - stray vanilla pickup items are removed for normal GGO players")
print(" - tagged GGO supply/loot and modded JEG/GGO items remain allowed")
