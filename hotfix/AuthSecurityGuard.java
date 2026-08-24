package arena.forge;

import arena.GunnerArenaMod;
import com.mojang.brigadier.ParseResults;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Production auth fence: official GGO launcher ticket only. Legacy /login and /register are never accepted. */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AuthSecurityGuard {
    private static final long OFFICIAL_LOGIN_TIMEOUT_TICKS = 20L * 30L;
    private static final Map<UUID, Long> DEADLINE = new HashMap<>();

    private AuthSecurityGuard() {}

    @SubscribeEvent
    public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = GunnerArenaMod.RUNTIME == null ? 0L : GunnerArenaMod.RUNTIME.serverTick();
        DEADLINE.put(player.getUUID(), now + OFFICIAL_LOGIN_TIMEOUT_TICKS);
    }

    @SubscribeEvent
    public static void leave(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        DEADLINE.remove(id);
        if (event.getEntity() instanceof ServerPlayer player) GgoOfficialAuthState.clear(player);
    }

    @SubscribeEvent
    public static void command(CommandEvent event) {
        ParseResults<CommandSourceStack> results = event.getParseResults();
        if (results == null) return;
        CommandSourceStack source = results.getContext().getSource();
        ServerPlayer player;
        try { player = source.getPlayer(); } catch (Exception ex) { return; }
        if (player == null) return;

        String raw = results.getReader().getString().trim();
        if (raw.startsWith("/")) raw = raw.substring(1);
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        boolean legacyLogin = lower.startsWith("login ") || lower.equals("login")
                || lower.startsWith("register ") || lower.equals("register");
        if (!legacyLogin) return;

        event.setCanceled(true);
        player.sendSystemMessage(Component.literal(
                "GunGloryOnline: отдельная регистрация на сервере отключена. Вход выполняется только через GGO Launcher."));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || GunnerArenaMod.RUNTIME == null) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long now = GunnerArenaMod.RUNTIME.serverTick();

        if (!GgoOfficialAuthState.required()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.connection.disconnect(Component.literal(
                        "GunGloryOnline: official launcher authentication is not enabled on this server."));
            }
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (GgoOfficialAuthState.isAuthenticated(player)) {
                DEADLINE.remove(id);
                continue;
            }
            long deadline = DEADLINE.computeIfAbsent(id, ignored -> now + OFFICIAL_LOGIN_TIMEOUT_TICKS);
            if (now >= deadline) {
                player.connection.disconnect(Component.literal(
                        "GunGloryOnline: launcher authentication timed out. Return to the GGO Launcher and press PLAY again."));
            }
        }
    }
}
