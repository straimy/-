from pathlib import Path

ROOT = Path('/tmp/aigunner/ai-gunner-dev10')

def edit(rel, replacements):
    p = ROOT / rel
    s = p.read_text()
    for old, new in replacements:
        if old not in s:
            raise SystemExit(f'missing patch anchor in {rel}: {old[:80]!r}')
        s = s.replace(old, new)
    p.write_text(s)

edit('gradle.properties', [('mod_version=1.5.0-dev10', 'mod_version=1.5.0-dev11')])
edit('src/main/java/ai/gunner/brain/BotState.java', [('RELOAD, PATHFIND, RETURN_TO_GAME', 'RELOAD, RESUPPLY, PATHFIND, RETURN_TO_GAME')])
edit('src/main/java/ai/gunner/brain/AIConfig.java', [('    public static final int RELOAD_COOLDOWN_TICKS = 28;\n', '    public static final int RELOAD_COOLDOWN_TICKS = 28;\n    public static final double AMMO_PICKUP_SEARCH_RANGE = 48.0;\n    public static final int AMMO_PICKUP_REFRESH_TICKS = 20;\n    public static final double AMMO_PICKUP_ARRIVAL_DISTANCE_SQR = 2.25;\n    public static final int DODGE_DIRECTION_TICKS = 10;\n')])

edit('src/main/java/ai/gunner/integration/JegAdapter.java', [('    public static int reserveAmmo(LocalPlayer player, ItemStack stack) {\n', '''    public static ResourceLocation ammoItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isGun(stack)) return null;
        try {
            Object item = stack.getItem();
            Method getModifiedGun = item.getClass().getMethod("getModifiedGun", ItemStack.class);
            Object gun = getModifiedGun.invoke(item, stack);
            if (gun == null) return null;
            Object ammoKey = null;
            Object projectile = gun.getClass().getMethod("getProjectile").invoke(gun);
            if (projectile != null) ammoKey = projectile.getClass().getMethod("getItem").invoke(projectile);
            if (ammoKey == null || ammoKey.toString().isBlank()) {
                Object reloads = gun.getClass().getMethod("getReloads").invoke(gun);
                if (reloads != null) ammoKey = reloads.getClass().getMethod("getReloadItem").invoke(reloads);
            }
            if (ammoKey instanceof ResourceLocation id) return id;
            if (ammoKey != null) return ResourceLocation.tryParse(ammoKey.toString());
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isAmmoForGun(ItemStack gun, ItemStack candidate) {
        if (candidate == null || candidate.isEmpty()) return false;
        ResourceLocation wanted = ammoItemId(gun);
        ResourceLocation actual = BuiltInRegistries.ITEM.getKey(candidate.getItem());
        return wanted != null && wanted.equals(actual);
    }

    public static int reserveAmmo(LocalPlayer player, ItemStack stack) {
''')])

geometry = ROOT / 'src/main/java/ai/gunner/util/CombatGeometry.java'
geometry.write_text('''package ai.gunner.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class CombatGeometry {
    private CombatGeometry() {}

    public static boolean clearShot(LocalPlayer self, Player target, double leadSeconds) {
        if (self == null || target == null || self.level() == null) return false;
        Vec3 from = self.getEyePosition();
        Vec3 to = target.getEyePosition().add(target.getDeltaMovement().scale(20.0 * leadSeconds));
        HitResult hit = self.level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self));
        return hit.getType() == HitResult.Type.MISS;
    }

    public static boolean enemyAimingAt(Player enemy, LocalPlayer self) {
        if (enemy == null || self == null) return false;
        Vec3 delta = self.getEyePosition().subtract(enemy.getEyePosition());
        double distance = delta.length();
        if (distance < 0.001 || distance > 48.0) return false;
        Vec3 towardSelf = delta.scale(1.0 / distance);
        Vec3 look = enemy.getViewVector(1.0f).normalize();
        double dot = look.dot(towardSelf);
        double threshold = distance < 14.0 ? 0.90 : 0.955;
        return dot >= threshold;
    }
}
''')

edit('src/main/java/ai/gunner/brain/CombatBrain.java', [
('import ai.gunner.util.AimMath;\n', 'import ai.gunner.util.AimMath;\nimport ai.gunner.util.CombatGeometry;\n'),
('import net.minecraft.network.chat.Component;\n', 'import net.minecraft.network.chat.Component;\nimport net.minecraft.world.entity.item.ItemEntity;\n'),
('    private int coverLocalTicks;\n', '    private int coverLocalTicks;\n    private int ammoRefresh;\n'),
('    private BlockPos lastPathGoal;\n', '    private BlockPos lastPathGoal;\n    private ItemEntity ammoPickup;\n'),
('        healCooldown = coverRefresh = weaponSwitchCooldown = deadTicks = returnRetry = returnAttempts = coverLocalTicks = 0;\n', '        healCooldown = coverRefresh = weaponSwitchCooldown = deadTicks = returnRetry = returnAttempts = coverLocalTicks = ammoRefresh = 0;\n'),
('        lastPathGoal = null;\n        release(mc);', '        lastPathGoal = null;\n        ammoPickup = null;\n        release(mc);'),
('        if (returnRetry > 0) returnRetry--;\n', '        if (returnRetry > 0) returnRetry--;\n        if (ammoRefresh > 0) ammoRefresh--;\n'),
('''        else if (JegAdapter.shouldTacticalReload(self, gun)
                && (!los || d > profile.maxRange() * 1.15)) state = BotState.RELOAD;
        else if (JegAdapter.isGun(gun) && !JegAdapter.hasAmmo(gun)) state = BotState.TAKE_COVER;
''', '''        else if (JegAdapter.shouldTacticalReload(self, gun)
                && (!los || d > profile.maxRange() * 1.15)) state = BotState.RELOAD;
        else if (JegAdapter.isGun(gun) && !JegAdapter.hasAmmo(gun) && JegAdapter.reserveAmmo(self, gun) <= 0
                && findAmmoPickup(mc, self, gun) != null) state = BotState.RESUPPLY;
        else if (JegAdapter.isGun(gun) && !JegAdapter.hasAmmo(gun)) state = BotState.TAKE_COVER;
'''),
('            case RELOAD -> reload(mc, self, gun);\n            case PATHFIND -> pathfind(mc, target);', '            case RELOAD -> reload(mc, self, gun);\n            case RESUPPLY -> resupply(mc, self, gun);\n            case PATHFIND -> pathfind(mc, target);'),
('''        boolean strafe = d >= profile.minRange() && d <= profile.maxRange();
        if (ticks % 24 == 0) strafeRight = !strafeRight;
        mc.options.keyLeft.setDown(strafe && !strafeRight);
        mc.options.keyRight.setDown(strafe && strafeRight);
        mc.options.keySprint.setDown(d > profile.maxRange());
        mc.options.keyJump.setDown(false);
        if (JegAdapter.isReadyToFire(self, gun) && self.hasLineOfSight(t) && fireCooldown <= 0) {
''', '''        boolean threatened = CombatGeometry.enemyAimingAt(t, self);
        boolean strafe = threatened || (d >= profile.minRange() && d <= profile.maxRange());
        int switchTicks = threatened ? AIConfig.DODGE_DIRECTION_TICKS : 24;
        if (ticks % switchTicks == 0) strafeRight = !strafeRight;
        mc.options.keyLeft.setDown(strafe && !strafeRight);
        mc.options.keyRight.setDown(strafe && strafeRight);
        mc.options.keySprint.setDown(threatened || d > profile.maxRange());
        mc.options.keyJump.setDown(shouldJump(self, t, threatened));
        if (JegAdapter.isReadyToFire(self, gun)
                && CombatGeometry.clearShot(self, t, profile.leadSeconds())
                && fireCooldown <= 0) {
'''),
('        mc.options.keyJump.setDown(nudge);\n', '        mc.options.keyJump.setDown(nudge || shouldJump(self, t, false));\n'),
('        mc.options.keyJump.setDown(stuckTicks >= AIConfig.STUCK_NUDGE_TICKS);\n', '        mc.options.keyJump.setDown(stuckTicks >= AIConfig.STUCK_NUDGE_TICKS || shouldJump(self, null, false));\n'),
('''    private Player acquire(Minecraft mc, LocalPlayer self) {
''', '''    private ItemEntity findAmmoPickup(Minecraft mc, LocalPlayer self, ItemStack gun) {
        if (mc.level == null || self == null || !JegAdapter.isGun(gun)) return null;
        if (ammoRefresh > 0 && ammoPickup != null && ammoPickup.isAlive()
                && self.distanceTo(ammoPickup) <= AIConfig.AMMO_PICKUP_SEARCH_RANGE
                && JegAdapter.isAmmoForGun(gun, ammoPickup.getItem())) return ammoPickup;
        ammoRefresh = AIConfig.AMMO_PICKUP_REFRESH_TICKS;
        ammoPickup = mc.level.getEntitiesOfClass(ItemEntity.class, self.getBoundingBox().inflate(AIConfig.AMMO_PICKUP_SEARCH_RANGE),
                        e -> e.isAlive() && !e.getItem().isEmpty() && JegAdapter.isAmmoForGun(gun, e.getItem()))
                .stream().min(Comparator.comparingDouble(self::distanceToSqr)).orElse(null);
        return ammoPickup;
    }

    private void resupply(Minecraft mc, LocalPlayer self, ItemStack gun) {
        HealingAdapter.stopUse(mc);
        release(mc);
        ItemEntity pickup = findAmmoPickup(mc, self, gun);
        if (pickup == null || !pickup.isAlive()) {
            BaritoneAdapter.cancel();
            ammoPickup = null;
            state = BotState.TAKE_COVER;
            return;
        }
        if (self.distanceToSqr(pickup) <= AIConfig.AMMO_PICKUP_ARRIVAL_DISTANCE_SQR) {
            BaritoneAdapter.cancel();
            ammoRefresh = 0;
            return;
        }
        BlockPos goal = pickup.blockPosition();
        if (!BaritoneAdapter.pathTo(goal, ticks % AIConfig.AMMO_PICKUP_REFRESH_TICKS == 0)) moveToward(mc, self, goal, true);
    }

    private boolean shouldJump(LocalPlayer self, Player t, boolean threatened) {
        if (self == null || !self.onGround()) return false;
        if (self.horizontalCollision) return true;
        if (t != null && t.getY() > self.getY() + 0.9 && self.distanceTo(t) < 7.0) return true;
        return threatened && ticks % 34 == 0;
    }

    private Player acquire(Minecraft mc, LocalPlayer self) {
''')
])

(ROOT / 'AI-GUNNER-1.5-DEV11.md').write_text('''# AI Gunner 1.5 dev11\n\n- clear-shot block ray before firing\n- simple dodge when enemy aims at bot\n- contextual jump movement\n- nearest compatible dropped JEG ammo resupply\n- no /give command\n''')
print('dev11 patch applied')
