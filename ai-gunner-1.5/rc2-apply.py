from pathlib import Path

root = Path('/tmp/aigunner/ai-gunner-dev10')

# Aim at a stable torso point inside the actual entity hitbox.
# Horizontal lead is kept; vertical velocity is intentionally ignored so jumps do not pull aim above the hitbox.
p = root / 'src/main/java/ai/gunner/util/CombatGeometry.java'
s = p.read_text()
old = 'Vec3 to = target.getEyePosition().add(target.getDeltaMovement().scale(20.0 * leadSeconds));'
new = '''double aimY = target.getBoundingBox().minY + target.getBbHeight() * 0.58;\n        Vec3 vel = target.getDeltaMovement();\n        Vec3 to = new Vec3(target.getX(), aimY, target.getZ())\n                .add(new Vec3(vel.x, 0.0, vel.z).scale(20.0 * leadSeconds));'''
if old not in s:
    raise RuntimeError('rc2 CombatGeometry aim anchor not found')
s = s.replace(old, new)
p.write_text(s)

# Reduce dodge churn; do not dodge by jumping.
p = root / 'src/main/java/ai/gunner/brain/AIConfig.java'
s = p.read_text()
if 'public static final int DODGE_DIRECTION_TICKS = 10;' not in s:
    raise RuntimeError('rc2 dodge config anchor not found')
s = s.replace('public static final int DODGE_DIRECTION_TICKS = 10;', 'public static final int DODGE_DIRECTION_TICKS = 14;')
p.write_text(s)

p = root / 'src/main/java/ai/gunner/brain/CombatBrain.java'
s = p.read_text()
if 'mc.options.keyJump.setDown(shouldJump(self, t, threatened));' not in s:
    raise RuntimeError('rc2 engage jump anchor not found')
s = s.replace('mc.options.keyJump.setDown(shouldJump(self, t, threatened));', 'mc.options.keyJump.setDown(shouldJump(self, t, false));')
if 'return threatened && ticks % 34 == 0;' not in s:
    raise RuntimeError('rc2 periodic dodge jump anchor not found')
s = s.replace('return threatened && ticks % 34 == 0;', 'return false;')
p.write_text(s)

# AimMath must use exactly the same torso point as the clear-shot ray.
p = root / 'src/main/java/ai/gunner/util/AimMath.java'
s = p.read_text()
old = '''        Vec3 vel = target.getDeltaMovement();\n        Vec3 aim = target.getEyePosition().add(vel.scale(20.0 * leadSeconds));'''
new = '''        Vec3 vel = target.getDeltaMovement();\n        double aimY = target.getBoundingBox().minY + target.getBbHeight() * 0.58;\n        Vec3 aim = new Vec3(target.getX(), aimY, target.getZ())\n                .add(new Vec3(vel.x, 0.0, vel.z).scale(20.0 * leadSeconds));'''
if old not in s:
    raise RuntimeError('rc2 AimMath aim anchor not found')
s = s.replace(old, new)
p.write_text(s)

p = root / 'gradle.properties'
s = p.read_text()
if 'mod_version=1.5.0-rc1' not in s:
    raise RuntimeError('rc2 version anchor not found')
s = s.replace('mod_version=1.5.0-rc1', 'mod_version=1.5.0-rc2')
p.write_text(s)

(root / 'AI-GUNNER-1.5-RC2.md').write_text('''# AI Gunner 1.5 rc2\n\n- Aim moved from target eyes to 58% of actual hitbox height (torso/chest).\n- AimMath and clear-shot ray now use the same point.\n- Lead predicts horizontal movement only, so target jumps do not pull aim above the hitbox.\n- Periodic dodge-jumps removed completely.\n- Jump remains only for collision / nearby elevation navigation.\n- Dodge direction changes are slightly less frequent.\n''')
print('rc2 patch applied')
