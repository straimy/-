#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build/src/main/java/arena/forge")
LOOT_SOURCE = Path("hotfix/GgoVanillaLootFence.java")
POLICY_SOURCE = Path("hotfix/GgoVisibleItemPolicy.java")
LOOT_TARGET = ROOT / "GgoVanillaLootFence.java"
POLICY_TARGET = ROOT / "GgoVisibleItemPolicy.java"

if not ROOT.is_dir():
    raise SystemExit("ga-build source tree is missing")
if not LOOT_SOURCE.is_file():
    raise SystemExit("GgoVanillaLootFence.java is missing")

shutil.copy2(LOOT_SOURCE, LOOT_TARGET)
# Newer de-Minecraft stages share the visible-item allowlist with the Stage 41 loot fence.
# Carry it here as well so old cumulative compile chains stay valid.
if POLICY_SOURCE.is_file():
    shutil.copy2(POLICY_SOURCE, POLICY_TARGET)

text = LOOT_TARGET.read_text(encoding="utf-8")
for required in [
    "LivingDropsEvent",
    "LivingExperienceDropEvent",
    "EntityItemPickupEvent",
    'contains("ggoLootPoint")',
    "GgoVisibleItemPolicy.allowed(stack)",
]:
    if required not in text:
        raise SystemExit(f"stage41 required behavior missing: {required}")

if POLICY_TARGET.is_file():
    policy=POLICY_TARGET.read_text(encoding="utf-8")
    for required in ['"minecraft".equals(id.getNamespace())',"GgoSupplyExtractionService.isSupply(stack)"]:
        if required not in policy:
            raise SystemExit(f"stage41 visible policy missing: {required}")

print("Applied GGO Stage 41 de-Minecraft loot fence")
print(" - vanilla mob drops are stripped")
print(" - vanilla XP drops are zeroed")
print(" - stray vanilla pickup items are removed for normal GGO players")
print(" - tagged GGO supply/loot and modded JEG/GGO items remain allowed")
