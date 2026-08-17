package arena.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;

/**
 * Bridges the legacy /login mod (serverreg) into GGO's AuthGate tag.
 * The external auth mod owns credentials; GGO only mirrors its current authenticated state.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyAuthSyncGuard {
    private static final String AUTH_TAG = "sauth_authenticated";
    private static volatile boolean resolved;
    private static volatile Object legacyAuth;
    private static volatile Field notLoggedPlayers;
    private static int ticks;

    private LegacyAuthSyncGuard() {}

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) sync(player, true);
    }

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++ticks < 5) return;
        ticks = 0;
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) sync(player, false);
    }

    private static void sync(ServerPlayer player, boolean forceCommandRefresh) {
        resolve();
        Object auth = legacyAuth;
        Field field = notLoggedPlayers;
        if (auth == null || field == null) return;
        try {
            Object value = field.get(auth);
            if (!(value instanceof Set<?> set)) return;
            UUID id = player.getUUID();
            boolean authenticated = !set.contains(id);
            boolean tagged = player.getTags().contains(AUTH_TAG);
            if (authenticated && !tagged) {
                player.addTag(AUTH_TAG);
                player.server.getCommands().sendCommands(player);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[GGO] Авторизация подтверждена — меню и игровые команды доступны.").withStyle(net.minecraft.ChatFormatting.GREEN));
            } else if (!authenticated && tagged) {
                player.removeTag(AUTH_TAG);
                player.server.getCommands().sendCommands(player);
            } else if (forceCommandRefresh && authenticated) {
                player.server.getCommands().sendCommands(player);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Fail closed: never authenticate if the external auth state cannot be read.
        }
    }

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            var container = ModList.get().getModContainerById("serverreg");
            if (container.isEmpty()) return;
            Object mod = container.get().getMod();
            Field field = mod.getClass().getDeclaredField("notLoggedPlayers");
            field.setAccessible(true);
            legacyAuth = mod;
            notLoggedPlayers = field;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            legacyAuth = null;
            notLoggedPlayers = null;
        }
    }
}
