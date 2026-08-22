#!/usr/bin/env python3
from pathlib import Path
import shutil
ROOT=Path("ga-build/src/main/java/arena/forge")
for name in ["GgoDeathRecapNetwork.java","GgoDeathRecapHooks.java"]:
    src=Path("hotfix")/name
    if not src.is_file(): raise SystemExit(f"missing {src}")
    shutil.copy2(src,ROOT/name)
network=(ROOT/"GgoDeathRecapNetwork.java").read_text(encoding="utf-8")
hooks=(ROOT/"GgoDeathRecapHooks.java").read_text(encoding="utf-8")
for required in ["ggo_death_recap","PLAY_TO_CLIENT","setClientConsumer","killerHealth","finalDamage"]:
    if required not in network: raise SystemExit(f"stage48 death network missing: {required}")
for required in ["LivingHurtEvent","LivingDeathEvent","attacker.distanceTo(victim)","No guessed" if False else "LAST_HIT"]:
    if required not in hooks: raise SystemExit(f"stage48 death hooks missing: {required}")
print("Applied GGO Stage 48 authoritative death recap")
