from pathlib import Path

p = Path('ga-build/src/main/java/arena/forge/MinimalGameplayFixes.java')
s = p.read_text()
changed = False

old = '        if (now - lastTntWave >= 1200L) { lastTntWave = now; spawnSafeTntWave(server, now); }\n'
new = '''        if (lastTntWave == Long.MIN_VALUE) {\n            lastTntWave = now;\n        } else if (now - lastTntWave >= 1200L) {\n            lastTntWave = now;\n            spawnSafeTntWave(server, now);\n        }\n'''
if old in s:
    s = s.replace(old, new, 1)
    changed = True

old2 = '        source.sendSuccess(() -> Component.literal("[GA] TNT spawn добавлен • цикл 60 сек • карта не ломается"), false); return 1;\n'
new2 = '''        ArenaRuntime runtime = GunnerArenaMod.RUNTIME;\n        if (runtime != null) lastTntWave = runtime.serverTick();\n        source.sendSuccess(() -> Component.literal("[GA] TNT spawn добавлен • цикл 60 сек • карта не ломается"), false); return 1;\n'''
if old2 in s:
    s = s.replace(old2, new2, 1)
    changed = True

# Newer MinimalGameplayFixes revisions already replaced this legacy 60s system with
# per-marker hazard scheduling. In that case the old anchors are intentionally absent,
# so this compatibility patch must be a no-op rather than fail the whole release build.
p.write_text(s)
print('apply_v6_runtime_patch:', 'patched legacy anchors' if changed else 'newer gameplay source; nothing to patch')
