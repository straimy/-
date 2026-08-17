from pathlib import Path

root = Path('/tmp/aigunner/ai-gunner-dev10')

p = root / 'src/main/java/ai/gunner/integration/ReturnToGameAdapter.java'
p.write_text('''package ai.gunner.integration;\n\nimport net.minecraft.client.Minecraft;\nimport net.minecraft.client.gui.screens.DeathScreen;\n\npublic final class ReturnToGameAdapter {\n    private ReturnToGameAdapter() {}\n\n    public static boolean tryInstantRespawn(Minecraft mc) {\n        try {\n            if (mc == null || mc.player == null || mc.player.isAlive()) return false;\n            if (!(mc.screen instanceof DeathScreen)) return false;\n            mc.player.respawn();\n            return true;\n        } catch (Throwable ignored) {\n            return false;\n        }\n    }\n\n    public static boolean sendPlay(Minecraft mc) {\n        try {\n            if (mc.player == null || mc.player.connection == null || mc.player.isAlive()) return false;\n            mc.player.connection.sendCommand("play");\n            return true;\n        } catch (Throwable ignored) {\n            return false;\n        }\n    }\n}\n''')

p = root / 'src/main/java/ai/gunner/brain/CombatBrain.java'
s = p.read_text()
s = s.replace('    private int returnAttempts;\n', '    private int returnAttempts;\n    private boolean instantRespawnAttempted;\n')
s = s.replace('        previousSlot = -1;\n', '        instantRespawnAttempted = false;\n        previousSlot = -1;\n', 1)
old = '''            deadTicks++;\n            release(mc);\n            target = null;\n            coverPos = null;\n            if (deadTicks >= AIConfig.RETURN_COMMAND_DELAY_TICKS\n'''
new = '''            deadTicks++;\n            release(mc);\n            target = null;\n            coverPos = null;\n            if (!instantRespawnAttempted && deadTicks >= 1) {\n                instantRespawnAttempted = ReturnToGameAdapter.tryInstantRespawn(mc);\n                if (instantRespawnAttempted) return;\n            }\n            if (deadTicks >= AIConfig.RETURN_COMMAND_DELAY_TICKS\n'''
if old not in s:
    raise RuntimeError('rc1 dead branch anchor not found')
s = s.replace(old, new)
s = s.replace('            returnAttempts = 0;\n            state = BotState.SEARCH;\n', '            returnAttempts = 0;\n            instantRespawnAttempted = false;\n            state = BotState.SEARCH;\n')
p.write_text(s)

p = root / 'gradle.properties'
s = p.read_text().replace('mod_version=1.5.0-dev13', 'mod_version=1.5.0-rc1')
p.write_text(s)

(root / 'AI-GUNNER-1.5-RC1.md').write_text('''# AI Gunner 1.5 rc1\n\n- Built-in one-shot instant-respawn fallback on DeathScreen via LocalPlayer.respawn().\n- Keeps the existing delayed /play fallback only if the player remains dead.\n- Removes the need to depend on the broken Instant-Respawn-1.20.1.d.jar for AI Gunner testing.\n- No new combat behavior beyond dev13.\n''')
print('rc1 patch applied')
