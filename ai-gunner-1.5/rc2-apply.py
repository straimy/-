from pathlib import Path

root = Path('/tmp/aigunner/ai-gunner-dev10')

# Use center/upper-torso hitbox aim instead of eye position.
p = root / 'src/main/java/ai/gunner/util/CombatGeometry.java'
s = p.read_text()
s = s.replace('Vec3 to = target.getEyePosition().add(target.getDeltaMovement().scale(20.0 * leadSeconds));', '''double aimY = target.getBoundingBox().minY + target.getBbHeight() * 0.62;\n        Vec3 to = new Vec3(target.getX(), aimY, target.getZ())\n                .add(target.getDeltaMovement().scale(20.0 * leadSeconds));''')
p.write_text(s)

# Reduce jump frequency and only jump for actual navigation needs.
p = root / 'src/main/java/ai/gunner/brain/AIConfig.java'
s = p.read_text()
s = s.replace('public static final int DODGE_DIRECTION_TICKS = 10;', 'public static final int DODGE_DIRECTION_TICKS = 14;')
p.write_text(s)

p = root / 'src/main/java/ai/gunner/brain/CombatBrain.java'
s = p.read_text()
s = s.replace('mc.options.keyJump.setDown(shouldJump(self, t, threatened));', 'mc.options.keyJump.setDown(shouldJump(self, t, false));')
s = s.replace('return threatened && ticks % 34 == 0;', 'return false;')
p.write_text(s)

# AimMath should also point at the same combat hitbox center, not target eyes.
p = root / 'src/main/java/ai/gunner/util/AimMath.java'
s = p.read_text()
s = s.replace('Vec3 aim = target.getEyePosition().add(target.getDeltaMovement().scale(20.0 * leadSeconds));', '''double aimY = target.getBoundingBox().minY + target.getBbHeight() * 0.62;\n        Vec3 aim = new Vec3(target.getX(), aimY, target.getZ())\n                .add(target.getDeltaMovement().scale(20.0 * leadSeconds));''')
p.write_text(s)

p = root / 'gradle.properties'
s = p.read_text().replace('mod_version=1.5.0-rc1', 'mod_version=1.5.0-rc2')
p.write_text(s)

(root / 'AI-GUNNER-1.5-RC2.md').write_text('''# AI Gunner 1.5 rc2\n\n- Aim point moved from target eyes to ~62% of hitbox height (upper torso/chest).\n- Clear-shot ray uses the same hitbox aim point as AimMath.\n- Removed periodic dodge-jumps; jumping is now for collision / actual elevation needs only.\n- Slightly reduced dodge direction churn.\n''')
print('rc2 patch applied')
