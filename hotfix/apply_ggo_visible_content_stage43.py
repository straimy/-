#!/usr/bin/env python3
from pathlib import Path
import shutil

ROOT=Path("ga-build/src/main/java/arena/forge")
for name in ["GgoVisibleItemPolicy.java","GgoVanillaLootFence.java"]:
    src=Path("hotfix")/name
    if not src.is_file(): raise SystemExit(f"missing {src}")
    shutil.copy2(src,ROOT/name)

policy=(ROOT/"GgoVisibleItemPolicy.java").read_text(encoding="utf-8")
loot=(ROOT/"GgoVanillaLootFence.java").read_text(encoding="utf-8")
for required in [
    'KEEP_VANILLA_TAG="ggo_keep_vanilla"',
    '"minecraft".equals(id.getNamespace())',
    'GgoSupplyExtractionService.isSupply(stack)',
    'markVanillaProxy',
    'p.getInventory().items.set(i,ItemStack.EMPTY)',
]:
    if required not in policy: raise SystemExit(f"stage43 policy missing: {required}")
if '!GgoVisibleItemPolicy.allowed(stack)' not in loot:
    raise SystemExit("stage43 loot fence is not using visible-item policy")
print("Applied GGO Stage 43 visible content allowlist")
