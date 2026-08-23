package arena.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REPORT ONLY inventory-policy detector for GGO beta.
 *
 * ArenaBeltGuard remains the authority that normalizes the inventory. This detector only records
 * sustained impossible belt layouts after normalization had time to run. It never mutates inventory,
 * kicks, bans, or exposes secrets.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoInventoryAntiCheat {
    private static final int SAMPLE_EVERY_TICKS = 10;
    private static final int JOIN_GRACE_TICKS = 20 * 8;
    private static final int REQUIRED_BAD_SAMPLES = 3;
    private static final long REPORT_COOLDOWN_MS = 15_000L;

    private static final Map<UUID, Integer> JOIN_TICK = new ConcurrentHashMap<>();
    private static final Map<String, Integer> BAD_STREAK = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_REPORT = new ConcurrentHashMap<>();

    private GgoInventoryAntiCheat() {}

    @SubscribeEvent
    public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            JOIN_TICK.put(player.getUUID(), player.tickCount);
        }
    }

    @SubscribeEvent
    public static void leave(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        JOIN_TICK.remove(id);
        BAD_STREAK.keySet().removeIf(k -> k.startsWith(id + ":"));
        LAST_REPORT.keySet().removeIf(k -> k.startsWith(id + ":"));
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.player.level().isClientSide()
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % SAMPLE_EVERY_TICKS != 0) return;
        if (GgoOfficialAuthState.required() && !GgoOfficialAuthState.isAuthenticated(player)) return;
        if (player.isCreative() || player.isSpectator()) return;

        int joinedAt = JOIN_TICK.getOrDefault(player.getUUID(), player.tickCount);
        if (player.tickCount - joinedAt < JOIN_GRACE_TICKS) return;

        // GGO UX reserves slots 3..8: ArenaBeltGuard moves their contents to field storage.
        for (int slot = 3; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            observe(player, "hidden:" + slot, stack != null && !stack.isEmpty(),
                    "reserved hidden slot remains occupied slot=" + slot);
        }

        // Slots 9..17 are ammo-only. A sustained non-ammo item here is a server-policy violation.
        for (int slot = ArenaBeltGuard.AMMO_FIRST; slot <= ArenaBeltGuard.AMMO_LAST; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            boolean bad = stack != null && !stack.isEmpty() && !ArenaBeltGuard.isAmmo(stack);
            observe(player, "ammo:" + slot, bad,
                    "non-ammo item persisted in reserved ammo slot=" + slot);
        }
    }

    private static void observe(ServerPlayer player, String keySuffix, boolean bad, String detail) {
        String key = player.getUUID() + ":" + keySuffix;
        if (!bad) {
            BAD_STREAK.remove(key);
            return;
        }

        int streak = BAD_STREAK.merge(key, 1, Integer::sum);
        if (streak < REQUIRED_BAD_SAMPLES) return;

        long now = System.currentTimeMillis();
        long last = LAST_REPORT.getOrDefault(key, 0L);
        if (now - last < REPORT_COOLDOWN_MS) return;
        LAST_REPORT.put(key, now);

        GgoAntiCheatEvidence.record(
                player,
                GgoAntiCheatEvidence.Kind.INVENTORY_DESYNC,
                1.25D,
                detail + ";samples=" + streak
        );
    }
}
