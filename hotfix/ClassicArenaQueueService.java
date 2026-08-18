package arena.forge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Minimal server-owned Classic queue. Starts a session when two queued players are available. */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClassicArenaQueueService {
    public static final String VERSION = "GGO-CLASSIC-QUEUE-V1";
    private static final int MIN_PLAYERS = 2;
    private static final Deque<UUID> QUEUE = new ArrayDeque<>();

    private ClassicArenaQueueService() {}

    public static synchronized boolean enqueue(ServerPlayer player) {
        UUID id = player.getUUID();
        if (QUEUE.contains(id) || ClassicArenaMatchService.isParticipant(player)) return false;
        QUEUE.addLast(id);
        player.sendSystemMessage(Component.literal("GGO • CLASSIC QUEUE • " + QUEUE.size()).withStyle(ChatFormatting.AQUA));
        return true;
    }

    public static synchronized void remove(UUID id) { QUEUE.remove(id); }
    public static synchronized int size() { return QUEUE.size(); }

    @SubscribeEvent
    public static synchronized void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) return;
        if (ClassicArenaMatchService.state() != ClassicArenaMatchService.State.WAITING || QUEUE.size() < MIN_PLAYERS) return;
        MinecraftServer server = event.getServer();
        List<ServerPlayer> players = new ArrayList<>();
        while (!QUEUE.isEmpty() && players.size() < MIN_PLAYERS) {
            UUID id = QUEUE.removeFirst();
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null && !player.isSpectator()) players.add(player);
        }
        if (players.size() < MIN_PLAYERS) {
            for (ServerPlayer player : players) QUEUE.addFirst(player.getUUID());
            return;
        }
        if (!ClassicArenaMatchService.begin(players.get(0).serverLevel(), players)) {
            for (ServerPlayer player : players) QUEUE.addLast(player.getUUID());
            return;
        }
        for (ServerPlayer player : players) player.sendSystemMessage(Component.literal("GGO • CLASSIC MATCH FOUND").withStyle(ChatFormatting.GOLD));
    }

    @SubscribeEvent
    public static synchronized void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        QUEUE.remove(event.getEntity().getUUID());
    }
}
