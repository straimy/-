#!/usr/bin/env python3
from pathlib import Path
import shutil

root = Path('ga-build/src/main/java/arena/forge')
root.mkdir(parents=True, exist_ok=True)
for name in ('GgoAntiCheatEvidence.java', 'GgoMovementAntiCheat.java', 'GgoWeaponStateAntiCheat.java'):
    src = Path('hotfix') / name
    if not src.exists():
        raise SystemExit(f'missing anti-cheat source: {src}')
    shutil.copy2(src, root / name)
print('Stage83 weapon anti-cheat patch applied')
