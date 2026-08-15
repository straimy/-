from pathlib import Path
root = Path('/tmp/aigunner/ai-gunner-dev10')
p = root / 'src/main/java/ai/gunner/brain/CombatBrain.java'
s = p.read_text()

anchor = '    private Entity ammoTarget;\n'
if anchor not in s:
    raise RuntimeError('ammoTarget field missing')
s = s.replace(anchor, anchor + '    private BlockPos rememberedPistolAmmo;\n    private BlockPos rememberedRifleAmmo;\n    private BlockPos rememberedShotgunAmmo;\n', 1)

# Clear remembered points only on full brain reset/world change.
anchor = '        ammoTarget = null;\n        release(mc);\n'
if anchor not in s:
    raise RuntimeError('reset ammoTarget anchor missing')
s = s.replace(anchor, '        ammoTarget = null;\n        rememberedPistolAmmo = null;\n        rememberedRifleAmmo = null;\n        rememberedShotgunAmmo = null;\n        release(mc);\n', 1)

# Remember live pickup/spawner positions.
s = s.replace('''        if (pickup != null) {\n            ammoTarget = pickup;\n            return ammoTarget;\n        }\n''', '''        if (pickup != null) {\n            ammoTarget = pickup;\n            rememberAmmoPoint(gun, pickup.blockPosition());\n            return ammoTarget;\n        }\n''')
s = s.replace('''        if (exact != null) {\n            ammoTarget = exact;\n            return ammoTarget;\n        }\n''', '''        if (exact != null) {\n            ammoTarget = exact;\n            rememberAmmoPoint(gun, exact.blockPosition());\n            return ammoTarget;\n        }\n''')
s = s.replace('''        ammoTarget = mc.level.getEntities(self, self.getBoundingBox().inflate(AIConfig.AMMO_PICKUP_SEARCH_RANGE),\n                        e -> e.isAlive() && e.getTags().contains("item_spawner")\n                                && e.getTags().contains("random_gun_ammo"))\n                .stream().min(Comparator.comparingDouble(self::distanceToSqr)).orElse(null);\n        return ammoTarget;\n''', '''        ammoTarget = mc.level.getEntities(self, self.getBoundingBox().inflate(AIConfig.AMMO_PICKUP_SEARCH_RANGE),\n                        e -> e.isAlive() && e.getTags().contains("item_spawner")\n                                && e.getTags().contains("random_gun_ammo"))\n                .stream().min(Comparator.comparingDouble(self::distanceToSqr)).orElse(null);\n        if (ammoTarget != null) rememberAmmoPoint(gun, ammoTarget.blockPosition());\n        return ammoTarget;\n''')

insert_before = '    private void resupply(Minecraft mc, LocalPlayer self, ItemStack gun) {'
idx = s.index(insert_before)
helpers = '''    private void rememberAmmoPoint(ItemStack gun, BlockPos pos) {\n        if (pos == null) return;\n        var id = JegAdapter.ammoItemId(gun);\n        if (id == null) return;\n        switch (id.getPath()) {\n            case "pistol_ammo" -> rememberedPistolAmmo = pos.immutable();\n            case "rifle_ammo" -> rememberedRifleAmmo = pos.immutable();\n            case "shotgun_shell", "handmade_shell" -> rememberedShotgunAmmo = pos.immutable();\n            default -> { }\n        }\n    }\n\n    private BlockPos rememberedAmmoPoint(ItemStack gun) {\n        var id = JegAdapter.ammoItemId(gun);\n        if (id == null) return null;\n        return switch (id.getPath()) {\n            case "pistol_ammo" -> rememberedPistolAmmo;\n            case "rifle_ammo" -> rememberedRifleAmmo;\n            case "shotgun_shell", "handmade_shell" -> rememberedShotgunAmmo;\n            default -> null;\n        };\n    }\n\n'''
s = s[:idx] + helpers + s[idx:]

old = '''        Entity destination = findAmmoDestination(mc, self, gun);\n        if (destination == null || !destination.isAlive()) {\n            BaritoneAdapter.cancel();\n            ammoTarget = null;\n            GunnerArenaAdapter.ensureBaselineLoadout(self);\n            state = BotState.SEARCH;\n            return;\n        }\n'''
new = '''        Entity destination = findAmmoDestination(mc, self, gun);\n        if (destination == null || !destination.isAlive()) {\n            ammoTarget = null;\n            BlockPos remembered = rememberedAmmoPoint(gun);\n            if (remembered != null) {\n                double dx = self.getX() - (remembered.getX() + 0.5);\n                double dy = self.getY() - remembered.getY();\n                double dz = self.getZ() - (remembered.getZ() + 0.5);\n                double d2 = dx * dx + dy * dy + dz * dz;\n                if (d2 <= 2.25) {\n                    BaritoneAdapter.cancel();\n                    releaseCombat(mc);\n                    return;\n                }\n                if (!BaritoneAdapter.pathTo(remembered, ticks % AIConfig.AMMO_PICKUP_REFRESH_TICKS == 0)) {\n                    moveToward(mc, self, remembered, true);\n                }\n                return;\n            }\n            BaritoneAdapter.cancel();\n            GunnerArenaAdapter.ensureBaselineLoadout(self);\n            state = BotState.SEARCH;\n            return;\n        }\n'''
if old not in s:
    raise RuntimeError('rc4 resupply fallback anchor not found')
s = s.replace(old, new, 1)
p.write_text(s)
print('rc4 ammo memory cache applied')
