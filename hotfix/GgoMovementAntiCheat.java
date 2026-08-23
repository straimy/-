package arena.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * First server-authoritative GGO movement detector.
 *
 * Stage 1 is intentionally REPORT ONLY. It accumulates evidence and never kicks/bans/teleports a
 * player. Thresholds are conservative because Forge movement can legitimately spike from combat,
 * server teleports, effects, scripted mechanics and lag. Real beta telemetry should be used before
 * enforcement is enabled.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoMovementAntiCheat {
    private static final Map<UUID, State> STATE = new HashMap<>();

    private static final double BASE_HORIZONTAL_LIMIT = 0.92D;
    private static final double BASE_VERTICAL_LIMIT = 1.35D;
    private static final double TELEPORT_LIKE_DISTANCE = 8.0D;
    private static final int REQUIRED_SUSTAINED_TICKS = 4;
    private static final int JOIN_GRACE_TICKS = 20 * 5;

    private GgoMovementAntiCheat() {}

    @SubscribeEvent
    public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        STATE.put(player.getUUID(), State.capture(player, JOIN_GRACE_TICKS));
    }

    @SubscribeEvent
    public static void leave(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        STATE.remove(id);
        GgoAntiCheatEvidence.clear(id);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        UUID id = player.getUUID();
        State state = STATE.computeIfAbsent(id, ignored -> State.capture(player, JOIN_GRACE_TICKS));

        if (state.dimensionChanged(player)) {
            STATE.put(id, State.capture(player, 40));
            return;
        }

        double dx = player.getX() - state.x;
        double dy = player.getY() - state.y;
        double dz = player.getZ() - state.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double total = Math.sqrt(dx * dx + dy * dy + dz * dz);

        state.x = player.getX();
        state.y = player.getY();
        state.z = player.getZ();
        state.dimension = player.level().dimension().location().toString();

        if (state.graceTicks > 0) {
            state.graceTicks--;
            state.sustainedHorizontal = 0;
            state.sustainedAir = 0;
            GgoAntiCheatEvidence.decay(id, 0.08D);
            return;
        }

        if (exempt(player)) {
            state.sustainedHorizontal = 0;
            state.sustainedAir = 0;
            state.graceTicks = Math.max(state.graceTicks, 4);
            GgoAntiCheatEvidence.decay(id, 0.05D);
            return;
        }

        // Very large one-tick displacement is logged separately. Because legitimate server-side
        // teleports can look identical, this signal alone must never punish a player.
        if (total >= TELEPORT_LIKE_DISTANCE) {
            GgoAntiCheatEvidence.record(
                    player,
                    GgoAntiCheatEvidence.Kind.TELEPORT_LIKE_MOVE,
                    1.0D,
                    String.format(java.util.Locale.ROOT, "distance=%.3f dx=%.3f dy=%.3f dz=%.3f", total, dx, dy, dz)
            );
            state.graceTicks = 20;
            state.sustainedHorizontal = 0;
            state.sustainedAir = 0;
            return;
        }

        double horizontalLimit = horizontalLimit(player);
        if (horizontal > horizontalLimit) {
            state.sustainedHorizontal++;
            if (state.sustainedHorizontal >= REQUIRED_SUSTAINED_TICKS) {
                double excess = horizontal / Math.max(0.01D, horizontalLimit);
                GgoAntiCheatEvidence.record(
                        player,
                        GgoAntiCheatEvidence.Kind.HORIZONTAL_SPEED,
                        Math.min(2.5D, 0.35D + (excess - 1.0D)),
                        String.format(java.util.Locale.ROOT, "h=%.3f limit=%.3f streak=%d", horizontal, horizontalLimit, state.sustainedHorizontal)
                );
            }
        } else {
            state.sustainedHorizontal = Math.max(0, state.sustainedHorizontal - 1);
        }

        if (Math.abs(dy) > BASE_VERTICAL_LIMIT && player.hurtTime <= 0) {
            GgoAntiCheatEvidence.record(
                    player,
                    GgoAntiCheatEvidence.Kind.VERTICAL_SPEED,
                    0.45D,
                    String.format(java.util.Locale.ROOT, "dy=%.3f limit=%.3f", dy, BASE_VERTICAL_LIMIT)
            );
        }

        boolean suspiciousAir = !player.onGround()
                && !player.isInWaterOrBubble()
                && !player.isFallFlying()
                && Math.abs(dy) < 0.035D
                && horizontal > 0.34D
                && player.hurtTime <= 0;
        if (suspiciousAir) {
            state.sustainedAir++;
            if (state.sustainedAir >= 14 && state.sustainedAir % 7 == 0) {
                GgoAntiCheatEvidence.record(
                        player,
                        GgoAntiCheatEvidence.Kind.IMPOSSIBLE_AIR_CHAIN,
                        0.8D,
                        String.format(java.util.Locale.ROOT, "airTicks=%d h=%.3f dy=%.3f", state.sustainedAir, horizontal, dy)
                );
            }
        } else {
            state.sustainedAir = 0;
        }

        if (state.sustainedHorizontal == 0 && state.sustainedAir == 0) {
            GgoAntiCheatEvidence.decay(id, 0.015D);
        }
    }

    private static boolean exempt(ServerPlayer player) {
        if (player.isSpectator() || player.isCreative()) return true;
        if (player.getAbilities().flying || player.isFallFlying() || player.isPassenger()) return true;
        if (player.isInWaterOrBubble() || player.isSwimming()) return true;
        if (player.hurtTime > 0) return true;
        return GgoOfficialAuthState.required() && !GgoOfficialAuthState.isAuthenticated(player);
    }

    private static double horizontalLimit(ServerPlayer player) {
        double limit = BASE_HORIZONTAL_LIMIT;
        var speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        if (speed != null) limit += 0.22D * (speed.getAmplifier() + 1);
        var slow = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (slow != null) limit += 0.08D; // avoid false positives during effect transition/desync
        if (!player.onGround()) limit += 0.22D;
        if (player.isSprinting()) limit += 0.12D;
        return limit;
    }

    private static final class State {
        double x;
        double y;
        double z;
        String dimension;
        int graceTicks;
        int sustainedHorizontal;
        int sustainedAir;

        static State capture(ServerPlayer player, int graceTicks) {
            State s = new State();
            s.x = player.getX();
            s.y = player.getY();
            s.z = player.getZ();
            s.dimension = player.level().dimension().location().toString();
            s.graceTicks = graceTicks;
            return s;
        }

        boolean dimensionChanged(ServerPlayer player) {
            return !dimension.equals(player.level().dimension().location().toString());
        }
    }
}
