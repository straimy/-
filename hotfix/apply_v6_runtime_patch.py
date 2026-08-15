from pathlib import Path

p = Path('ga-build/src/main/java/arena/forge/MinimalGameplayFixes.java')
s = p.read_text()
old = '        if (now - lastTntWave >= 1200L) { lastTntWave = now; spawnSafeTntWave(server, now); }\n'
new = '''        if (lastTntWave == Long.MIN_VALUE) {\n            lastTntWave = now;\n        } else if (now - lastTntWave >= 1200L) {\n            lastTntWave = now;\n            spawnSafeTntWave(server, now);\n        }\n'''
if old not in s:
    raise SystemExit('TNT timer anchor not found')
s = s.replace(old, new, 1)
# When a new point is placed, restart the minute interval from that moment.
old2 = '        source.sendSuccess(() -> Component.literal("[GA] TNT spawn добавлен • цикл 60 сек • карта не ломается"), false); return 1;\n'
new2 = '''        ArenaRuntime runtime = GunnerArenaMod.RUNTIME;\n        if (runtime != null) lastTntWave = runtime.serverTick();\n        source.sendSuccess(() -> Component.literal("[GA] TNT spawn добавлен • цикл 60 сек • карта не ломается"), false); return 1;\n'''
if old2 not in s:
    raise SystemExit('TNT placement anchor not found')
p.write_text(s.replace(old2, new2, 1))
