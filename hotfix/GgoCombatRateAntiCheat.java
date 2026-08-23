package arena.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REPORT ONLY combat-impact burst telemetry.
 *
 * This deliberately does NOT pretend LivingAttackEvent is a weapon fire event. Shotguns,
 * projectiles and custom weapons can legitimately create multiple impacts. We therefore only
 * record an evidence signal for extreme same-tick impact bursts, and never kick/ban from it.
 * Precise fire/reload timing must be wired to the authoritative weapon action path later.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoCombatRateAntiCheat {
    private static final int EXTREME_SAME_TICK_IMPACTS = 20;
    private static final long REPORT_COOLDOWN_TICKS = 20L * 10L;
    private static final Map<UUID, Sample> SAMPLES = new ConcurrentHashMap<>();

    private GgoCombatRateAntiCheat() {}

    @SubscribeEvent
    public static void attack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer player)) return;
        if (GgoOfficialAuthState.required() && !GgoOfficialAuthState.isAuthenticated(player)) return;
        if (player.isCreative() || player.isSpectator()) return;

        long now = player.serverLevel().getGameTime();
        Sample sample = SAMPLES.computeIfAbsent(player.getUUID(), ignored -> new Sample());
        if (sample.tick != now) {
            sample.tick = now;
            sample.impacts = 0;
        }
        sample.impacts++;

        if (sample.impacts < EXTREME_SAME_TICK_IMPACTS) return;
        if (now - sample.lastReportTick < REPORT_COOLDOWN_TICKS) return;
        sample.lastReportTick = now;

        GgoAntiCheatEvidence.record(
                player,
                GgoAntiCheatEvidence.Kind.COMBAT_RATE,
                1.0D,
                "extreme_same_tick_impacts=" + sample.impacts + ";tick=" + now
        );
    }

    @SubscribeEvent
    public static void leave(PlayerEvent.PlayerLoggedOutEvent event) {
        SAMPLES.remove(event.getEntity().getUUID());
    }

    private static final class Sample {
        long tick = Long.MIN_VALUE / 2;
        int impacts;
        long lastReportTick = Long.MIN_VALUE / 2;
    }
}
