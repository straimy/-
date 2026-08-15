from pathlib import Path
root = Path('/tmp/aigunner/ai-gunner-dev10')

# Fix accidental literal backslash-n in AIConfig.
p = root / 'src/main/java/ai/gunner/brain/AIConfig.java'
s = p.read_text()
s = s.replace('public static final int RETURN_COMMAND_MAX_ATTEMPTS = 2;\\n    public static final int POST_RESPAWN_PLAY_DELAY_TICKS = 50;',
              'public static final int RETURN_COMMAND_MAX_ATTEMPTS = 2;\n    public static final int POST_RESPAWN_PLAY_DELAY_TICKS = 50;')
p.write_text(s)

# Guarantee required rc3 imports/fields/hooks were inserted even if an escaped anchor no-op'd.
p = root / 'src/main/java/ai/gunner/brain/CombatBrain.java'
s = p.read_text()
if 'import ai.gunner.integration.GunnerArenaAdapter;' not in s:
    s = s.replace('import ai.gunner.integration.HealingAdapter;\n',
                  'import ai.gunner.integration.HealingAdapter;\nimport ai.gunner.integration.GunnerArenaAdapter;\n')
if 'import net.minecraft.world.entity.Entity;' not in s:
    s = s.replace('import net.minecraft.world.entity.item.ItemEntity;\n',
                  'import net.minecraft.world.entity.Entity;\nimport net.minecraft.world.entity.item.ItemEntity;\n')
if 'private Entity ammoTarget;' not in s:
    s = s.replace('    private ItemEntity ammoPickup;\n',
                  '    private Entity ammoTarget;\n    private int postRespawnNoTargetTicks;\n    private boolean needsPlayAfterRespawn;\n')
if 'GunnerArenaAdapter.tick();' not in s:
    s = s.replace('        if (ammoRefresh > 0) ammoRefresh--;\n',
                  '        if (ammoRefresh > 0) ammoRefresh--;\n        GunnerArenaAdapter.tick();\n')
# Normalize any old field references left by silent replacements.
s = s.replace('ammoPickup', 'ammoTarget')
p.write_text(s)

# Hard assertions so CI fails before Gradle if rc3 is incomplete.
checks = {
    'AIConfig.java': ['POST_RESPAWN_PLAY_DELAY_TICKS'],
    'CombatBrain.java': ['GunnerArenaAdapter', 'private Entity ammoTarget', 'findAmmoDestination', 'item_spawner', 'needsPlayAfterRespawn'],
    '../integration/GunnerArenaAdapter.java': ['ArenaNetwork$BuyRequest', 'jeg:semi_auto_pistol', 'jeg:custom_smg'],
}
for rel, needles in checks.items():
    path = (root / 'src/main/java/ai/gunner/brain' / rel) if not rel.startswith('../') else (root / 'src/main/java/ai/gunner/integration/GunnerArenaAdapter.java')
    text = path.read_text()
    for needle in needles:
        if needle not in text:
            raise RuntimeError(f'missing rc3 marker {needle} in {path}')
print('rc3 fix applied and verified')
