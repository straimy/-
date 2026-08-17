package arena.forge;

import com.mojang.logging.LogUtils;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Conservative server-side anti-cheat telemetry for GGO.
 *
 * v1 intentionally does not kick or ban. It produces bounded suspicion signals that can be
 * correlated with authoritative GGO match state. This avoids false positives while the game still
 * contains teleports, custom weapons, NPCs and other mechanics that do not behave like vanilla.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoAntiCheatTelemetry {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Map<UUID, MovementSample> MOVEMENT = new ConcurrentHashMap<>();
    private static final Map<UUID, CombatSample> COMBAT = new ConcurrentHashMap<>();

    private static final int SAMPLE_EVERY_TICKS = 5;
    private static final double OBSERVE_DISTANCE = 8.0D;
    private static final double TELEPORT_RESET_DISTANCE = 24.0D;
    private static final int REPORT_SCORE = 5;
    private static final long REPORT_COOLDOWN_TICKS = 20L * 10L;

    private GgoAntiCheatTelemetry() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if ((player.tickCount % SAMPLE_EVERY_TICKS) != 0) return;

        long now = player.serverLevel().getGameTime();
        UUID id = player.getUUID();
        MovementSample previous = MOVEMENT.get(id);
        MovementSample current = new MovementSample(player.getX(), player.getY(), player.getZ(), now, previous == null ? 0 : previous.score, previous == null ? 0 : previous.lastReportTick);
        MOVEMENT.put(id, current);
        if (previous == null) return;

        if (isMovementExempt(player)) {
            current.score = Math.max(0, current.score - 1);
            return;
        }

        long elapsed = Math.max(1L, now - previous.tick);
        double dx = current.x - previous.x;
        double dy = current.y - previous.y;
        double dz = current.z - previous.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // Large jumps are normally server teleports/round transitions. Reset rather than accusing.
        if (distance >= TELEPORT_RESET_DISTANCE) {
            current.score = Math.max(0, current.score - 1);
            return;
        }

        double normalizedFiveTickDistance = distance * (SAMPLE_EVERY_TICKS / (double) elapsed);
        if (normalizedFiveTickDistance > OBSERVE_DISTANCE) {
            current.score += 2;
            maybeReport(player, "movement", current.score, now,
                String.format("distance=%.2f normalized5t=%.2f", distance, normalizedFiveTickDistance), current);
        } else {
            current.score = Math.max(0, current.score - 1);
        }
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) return;
        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof ServerPlayer player)) return;

        long now = player.serverLevel().getGameTime();
        CombatSample sample = COMBAT.computeIfAbsent(player.getUUID(), ignored -> new CombatSample());
        long delta = now - sample.lastAttackTick;
        sample.lastAttackTick = now;

        // One-tick attack bursts are only observed, never punished in v1. Custom automatic weapons
        // may legitimately produce unusual damage cadence, so enforcement belongs to weapon-specific
        // authoritative validation later.
        if (delta >= 0 && delta <= 1) sample.rapidAttackBurst++;
        else sample.rapidAttackBurst = Math.max(0, sample.rapidAttackBurst - 1);

        if (sample.rapidAttackBurst >= 8 && now - sample.lastReportTick >= REPORT_COOLDOWN_TICKS) {
            sample.lastReportTick = now;
            LOG.warn("[GGO-AC-V1] combat telemetry player={} uuid={} rapidAttackBurst={}",
                player.getGameProfile().getName(), player.getUUID(), sample.rapidAttackBurst);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        MOVEMENT.remove(id);
        COMBAT.remove(id);
    }

    private static boolean isMovementExempt(ServerPlayer player) {
        return player.isSpectator()
            || player.isCreative()
            || player.getAbilities().flying
            || player.isPassenger()
            || player.isFallFlying();
    }

    private static void maybeReport(ServerPlayer player, String category, int score, long now, String details, MovementSample sample) {
        if (score < REPORT_SCORE || now - sample.lastReportTick < REPORT_COOLDOWN_TICKS) return;
        sample.lastReportTick = now;
        LOG.warn("[GGO-AC-V1] {} telemetry player={} uuid={} score={} {}",
            category, player.getGameProfile().getName(), player.getUUID(), score, details);
    }

    private static final class MovementSample {
        final double x;
        final double y;
        final double z;
        final long tick;
        int score;
        long lastReportTick;

        MovementSample(double x, double y, double z, long tick, int score, long lastReportTick) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.tick = tick;
            this.score = score;
            this.lastReportTick = lastReportTick;
        }
    }

    private static final class CombatSample {
        long lastAttackTick = Long.MIN_VALUE / 2;
        int rapidAttackBurst;
        long lastReportTick;
    }
}
