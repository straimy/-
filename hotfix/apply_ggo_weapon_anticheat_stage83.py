#!/usr/bin/env python3
from pathlib import Path
import shutil

root = Path('ga-build/src/main/java/arena/forge')
root.mkdir(parents=True, exist_ok=True)

# Stage83 is intentionally a thin layer on top of Stage82. Stage82 already installs movement,
# weapon-state, inventory, staff diagnostics and the evidence ledger. Here we add only the
# conservative combat-impact burst signal so the build cannot accidentally replace Stage82 files.
src = Path('hotfix/GgoCombatRateAntiCheat.java')
if not src.exists():
    raise SystemExit(f'missing Stage83 anti-cheat source: {src}')
shutil.copy2(src, root / src.name)
print('Stage83 combat-rate evidence patch applied')
