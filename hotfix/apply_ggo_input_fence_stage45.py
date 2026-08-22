#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT=Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA=ROOT/"client-ui/src/main/java/arena/client/shell"
SOURCE=Path("hotfix/GgoCombatInputFence.java")
TARGET=JAVA/"GgoCombatInputFence.java"
if not JAVA.is_dir(): raise SystemExit("client-ui source tree missing")
if not SOURCE.is_file(): raise SystemExit("GgoCombatInputFence.java missing")
shutil.copy2(SOURCE,TARGET)
text=TARGET.read_text(encoding="utf-8")
for required in [
    "keyDrop",
    "keySwapOffhand",
    "keyPickItem",
    "keyHotbarSlots",
    "selected<3",
    "drain(mc.options.keyDrop)",
    "MouseScrollingEvent",
    "Math.floorMod(current+direction,3)",
    "event.setCanceled(true)",
]:
    if required not in text: raise SystemExit(f"stage45 input behavior missing: {required}")
print("Applied GGO Stage 45 three-slot combat input fence")
print(" - mouse wheel cycles only the 3 GGO combat slots")
