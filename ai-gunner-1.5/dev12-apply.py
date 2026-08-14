from pathlib import Path

root = Path('/tmp/aigunner/ai-gunner-dev10')

p = root / 'src/main/java/ai/gunner/brain/AIConfig.java'
s = p.read_text()
s = s.replace('public static final double AMMO_PICKUP_ARRIVAL_DISTANCE_SQR = 2.25;', 'public static final double AMMO_PICKUP_ARRIVAL_DISTANCE_SQR = 0.49;')
p.write_text(s)

p = root / 'src/main/java/ai/gunner/util/CombatGeometry.java'
s = p.read_text()
s = s.replace('''        if (enemy == null || self == null) return false;\n        Vec3 delta = self.getEyePosition().subtract(enemy.getEyePosition());''','''        if (enemy == null || self == null) return false;\n        // Ignore aim threats through walls; only react when the enemy can actually see us.\n        if (!enemy.hasLineOfSight(self)) return false;\n        Vec3 delta = self.getEyePosition().subtract(enemy.getEyePosition());''')
p.write_text(s)

p = root / 'src/main/java/ai/gunner/brain/CombatBrain.java'
s = p.read_text()
old = '''        if (self.distanceToSqr(pickup) <= AIConfig.AMMO_PICKUP_ARRIVAL_DISTANCE_SQR) {\n            BaritoneAdapter.cancel();\n            // Vanilla pickup happens automatically when close enough. Re-evaluate next tick.\n            ammoRefresh = 0;\n            return;\n        }\n        BlockPos goal = pickup.blockPosition();\n        if (!BaritoneAdapter.pathTo(goal, ticks % AIConfig.AMMO_PICKUP_REFRESH_TICKS == 0)) {\n            moveToward(mc, self, goal, true);\n        }'''
new = '''        double pickupD2 = self.distanceToSqr(pickup);\n        BlockPos goal = pickup.blockPosition();\n        if (pickupD2 <= AIConfig.AMMO_PICKUP_ARRIVAL_DISTANCE_SQR) {\n            BaritoneAdapter.cancel();\n            releaseCombat(mc);\n            // We are genuinely inside pickup distance now; let vanilla collect it.\n            ammoRefresh = 0;\n            return;\n        }\n        // Baritone can stop a little short of an ItemEntity. Finish the last couple blocks locally\n        // so RESUPPLY cannot stall just outside vanilla pickup range.\n        if (pickupD2 <= 4.0) {\n            BaritoneAdapter.cancel();\n            moveToward(mc, self, goal, true);\n            return;\n        }\n        if (!BaritoneAdapter.pathTo(goal, ticks % AIConfig.AMMO_PICKUP_REFRESH_TICKS == 0)) {\n            moveToward(mc, self, goal, true);\n        }'''
if old not in s:
    raise RuntimeError('dev12 resupply block not found')
s = s.replace(old, new)
p.write_text(s)

p = root / 'gradle.properties'
s = p.read_text().replace('mod_version=1.5.0-dev11', 'mod_version=1.5.0-dev12')
p.write_text(s)

(root / 'AI-GUNNER-1.5-DEV12.md').write_text('''# AI Gunner 1.5 dev12\n\n- RESUPPLY closes the final gap to dropped ammo locally.\n- Pickup arrival distance reduced to avoid stopping outside vanilla pickup range.\n- Aim-dodge ignores enemies aiming through walls.\n- No expensive new prediction loops.\n''')
print('dev12 patch applied')
