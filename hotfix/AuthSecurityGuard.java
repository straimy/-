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

/** Small server-side fence around the external SAuth login flow. */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AuthSecurityGuard {
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOGIN_TIMEOUT_TICKS = 20L * 120L;
    private static final Map<UUID, Long> DEADLINE = new HashMap<>();
    private static final Map<UUID, Integer> ATTEMPTS = new HashMap<>();
    private static final Map<UUID, Long> LAST_ATTEMPT = new HashMap<>();

    private AuthSecurityGuard() {}

    @SubscribeEvent
    public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        long now = GunnerArenaMod.RUNTIME == null ? 0L : GunnerArenaMod.RUNTIME.serverTick();
        DEADLINE.put(player.getUUID(), now + LOGIN_TIMEOUT_TICKS);
        ATTEMPTS.put(player.getUUID(), 0);
        LAST_ATTEMPT.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void leave(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        DEADLINE.remove(id); ATTEMPTS.remove(id); LAST_ATTEMPT.remove(id);
    }

    @SubscribeEvent
    public static void command(CommandEvent event) {
        ParseResults<CommandSourceStack> results = event.getParseResults();
        if (results == null) return;
        CommandSourceStack source = results.getContext().getSource();
        ServerPlayer player;
        try { player = source.getPlayer(); } catch (Exception ex) { return; }
        if (player == null || GunnerArenaMod.RUNTIME == null || GunnerArenaMod.RUNTIME.auth().isAuthenticated(player)) return;
        String raw = results.getReader().getString().trim();
        if (raw.startsWith("/")) raw = raw.substring(1);
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        if (!(lower.startsWith("login ") || lower.equals("login") || lower.startsWith("register ") || lower.equals("register"))) return;
        UUID id = player.getUUID();
        ATTEMPTS.put(id, ATTEMPTS.getOrDefault(id, 0) + 1);
        LAST_ATTEMPT.put(id, GunnerArenaMod.RUNTIME.serverTick());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || GunnerArenaMod.RUNTIME == null) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long now = GunnerArenaMod.RUNTIME.serverTick();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (GunnerArenaMod.RUNTIME.auth().isAuthenticated(player)) {
                DEADLINE.remove(id); ATTEMPTS.remove(id); LAST_ATTEMPT.remove(id);
                continue;
            }
            long deadline = DEADLINE.computeIfAbsent(id, ignored -> now + LOGIN_TIMEOUT_TICKS);
            if (now >= deadline) {
                player.connection.disconnect(Component.literal("GunGloryOnline: время на вход истекло. Зайди снова и используй /login или /register."));
                continue;
            }
            int attempts = ATTEMPTS.getOrDefault(id, 0);
            long last = LAST_ATTEMPT.getOrDefault(id, Long.MIN_VALUE / 4L);
            // Wait one second so a successful fifth attempt has time to propagate from SAuth.
            if (attempts >= MAX_ATTEMPTS && now - last >= 20L && !GunnerArenaMod.RUNTIME.auth().isAuthenticated(player)) {
                player.connection.disconnect(Component.literal("GunGloryOnline: слишком много неудачных попыток входа."));
            }
        }
    }
}
