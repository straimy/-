#!/usr/bin/env python3
from pathlib import Path
import shutil
ROOT=Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA=ROOT/"client-ui/src/main/java/arena/client/shell"
for name in ["GgoDeathRecapState.java","GgoDeathRecapAdapter.java","GgoRespawnScreen.java"]:
    src=Path("hotfix")/name
    if not src.is_file(): raise SystemExit(f"missing {src}")
    shutil.copy2(src,JAVA/name)
respawn=(JAVA/"GgoRespawnScreen.java").read_text(encoding="utf-8")
adapter=(JAVA/"GgoDeathRecapAdapter.java").read_text(encoding="utf-8")
fence=(JAVA/"GgoVanillaRuntimeFence.java").read_text(encoding="utf-8")
for required in ["COMBAT REPORT","KILLED BY","FINAL HIT","ATTACKER STATUS","GgoDeathRecapState.get()"]:
    if required not in respawn: raise SystemExit(f"stage48 recap UI missing: {required}")
for required in ["GgoDeathRecapNetwork","setClientConsumer","Consumer<Object>"]:
    if required not in adapter: raise SystemExit(f"stage48 recap adapter missing: {required}")
if "new GgoRespawnScreen()" not in fence: raise SystemExit("stage48 death fence route missing")
print("Applied GGO Stage 48 client death recap")
