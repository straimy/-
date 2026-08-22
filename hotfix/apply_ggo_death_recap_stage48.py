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
for required in ["ggo_death_recap",'VERSION="2"',"PLAY_TO_CLIENT","setClientConsumer","setKillFeedConsumer","KillFeed","sendKillFeed","killerHealth","finalDamage"]:
    if required not in network: raise SystemExit(f"stage48/55 death network missing: {required}")
for required in ["LivingHurtEvent","LivingDeathEvent","attacker.distanceTo(victim)","LAST_HIT","new GgoDeathRecapNetwork.KillFeed","runtime.auth().isAuthenticated(viewer)"]:
    if required not in hooks: raise SystemExit(f"stage48/55 death hooks missing: {required}")
print("Applied GGO Stage 48/55 authoritative death presentation")
print(" - victim receives detailed recap")
print(" - authenticated players in the same dimension receive compact kill feed")
