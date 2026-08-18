package arena.forge;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Small server-owned ledger for legitimate movement impulses/teleports.
 *
 * Anti-cheat must not guess whether an unusual movement came from a GGO mechanic. The mechanic
 * explicitly authorizes a short window before applying the movement, and telemetry/enforcement can
 * consult this ledger. No client packet can grant an exemption.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoMovementAuthority {
    public static final String VERSION = "GGO-MOVE-AUTH-V1";

    private static final Map<UUID, Long> EXEMPT_UNTIL = new ConcurrentHashMap<>();

    private GgoMovementAuthority() {}

    public static void authorize(ServerPlayer player, long ticks) {
        if (player == null || ticks <= 0L) return;
        long until = player.serverLevel().getGameTime() + ticks;
        EXEMPT_UNTIL.merge(player.getUUID(), until, Math::max);
    }

    public static boolean isAuthorized(ServerPlayer player) {
        if (player == null) return false;
        long now = player.serverLevel().getGameTime();
        Long until = EXEMPT_UNTIL.get(player.getUUID());
        if (until == null) return false;
        if (until < now) {
            EXEMPT_UNTIL.remove(player.getUUID(), until);
            return false;
        }
        return true;
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        EXEMPT_UNTIL.remove(event.getEntity().getUUID());
    }
}
