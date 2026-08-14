from pathlib import Path

root = Path('/tmp/aigunner/ai-gunner-dev10')

p = root / 'src/main/java/ai/gunner/brain/CombatBrain.java'
s = p.read_text()
old = '''                double currentD2 = self.distanceToSqr(target);\n                double candidateD2 = self.distanceToSqr(candidate);\n                if (candidateD2 < currentD2 * AIConfig.TARGET_SWITCH_ADVANTAGE) target = candidate;'''
new = '''                double currentD = self.distanceTo(target);\n                double candidateD = self.distanceTo(candidate);\n                if (candidateD < currentD * AIConfig.TARGET_SWITCH_ADVANTAGE) target = candidate;'''
if old not in s:
    raise RuntimeError('dev13 target hysteresis block not found')
s = s.replace(old, new)
old = '''    private void updateStuck(LocalPlayer self) {\n        double dx = self.getX() - lastX, dz = self.getZ() - lastZ;\n        double moved = dx * dx + dz * dz;\n        if (ticks > 5 && moved < 0.0004) {\n            stuckTicks++;'''
new = '''    private void updateStuck(LocalPlayer self) {\n        double dx = self.getX() - lastX, dz = self.getZ() - lastZ;\n        double moved = dx * dx + dz * dz;\n        Minecraft mc = Minecraft.getInstance();\n        boolean movementExpected = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()\n                || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();\n        if (!movementExpected) {\n            // Standing still while aiming/reloading/healing is intentional, not a pathing failure.\n            stuckTicks = Math.max(0, stuckTicks - 1);\n        } else if (ticks > 5 && moved < 0.0004) {\n            stuckTicks++;'''
if old not in s:
    raise RuntimeError('dev13 stuck block not found')
s = s.replace(old, new)
p.write_text(s)

p = root / 'gradle.properties'
s = p.read_text().replace('mod_version=1.5.0-dev12', 'mod_version=1.5.0-dev13')
p.write_text(s)

(root / 'AI-GUNNER-1.5-DEV13.md').write_text('''# AI Gunner 1.5 dev13\n\n- Target hysteresis compares real distance, so 0.72 means a truly 28% closer candidate.\n- Stuck recovery only accumulates when local movement was actually requested.\n- Standing still while aiming/reloading/healing no longer creates false PATHFIND pressure.\n''')
print('dev13 patch applied')
