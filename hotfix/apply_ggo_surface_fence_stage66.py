#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
SOURCE = Path("hotfix/GgoProductionSurfaceFence.java")
TARGET = JAVA / SOURCE.name

if not JAVA.is_dir():
    raise SystemExit("client-ui source tree is missing")
if not SOURCE.is_file():
    raise SystemExit(f"missing {SOURCE}")
shutil.copy2(SOURCE, TARGET)

text = TARGET.read_text(encoding="utf-8")
for required in [
    "VanillaGuiOverlay.DEBUG_TEXT",
    "VanillaGuiOverlay.FPS_GRAPH",
    "VanillaGuiOverlay.ITEM_NAME",
    "VanillaGuiOverlay.POTION_ICONS",
    "VanillaGuiOverlay.SCOREBOARD",
    "VanillaGuiOverlay.BOSS_EVENT_PROGRESS",
    "VanillaGuiOverlay.RECORD_OVERLAY",
    "GenericDirtMessageScreen",
    "LevelLoadingScreen",
    "SYNCHRONIZING SESSION",
]:
    if required not in text:
        raise SystemExit(f"stage66 surface fence missing: {required}")

for forbidden in ["VanillaGuiOverlay.CHAT_TEXT", "VanillaGuiOverlay.SUBTITLES", "event.setNewScreen"]:
    if forbidden in text:
        raise SystemExit(f"stage66 must preserve functionality: {forbidden}")

print("Applied GGO Stage 66 production surface fence")
print(" - hides Minecraft debug/FPS/item/effect/score/boss/disc overlays")
print(" - covers non-interactive dirt/loading transitions with GGO presentation")
print(" - preserves chat, subtitles and underlying transition lifecycle")
