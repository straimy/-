#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build/src/main/java/arena/forge")
SOURCE = Path("hotfix/GgoWorldRuntimeFence.java")
TARGET = ROOT / "GgoWorldRuntimeFence.java"

if not ROOT.is_dir():
    raise SystemExit("ga-build source tree is missing")
if not SOURCE.is_file():
    raise SystemExit("GgoWorldRuntimeFence.java is missing")

shutil.copy2(SOURCE, TARGET)
text = TARGET.read_text(encoding="utf-8")
for required in [
    "BlockEvent.BreakEvent",
    "BlockEvent.EntityPlaceEvent",
    "VANILLA_MENU_BLOCKS",
    "setFoodLevel(20)",
    "experienceLevel = 0",
    "maintenance(ServerPlayer player)",
]:
    if required not in text:
        raise SystemExit(f"stage39 required behavior missing: {required}")

print("Applied GGO Stage 39 de-Minecraft world runtime fence")
print(" - normal GGO players cannot edit maps with vanilla block break/place")
print(" - vanilla crafting/container/bed interactions are fenced")
print(" - hunger and XP progression are inert")
print(" - permission-2 creative/spectator maintenance remains available")
