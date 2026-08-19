package arena.forge;

import com.mojang.brigadier.Command;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-owned 1v1 BO3 matchmaking/session foundation. Duel-map logic is marker-only. */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DuelMatchService {
    public static final String VERSION = "GGO-DUELS-V2";
    public static final int ROUNDS_TO_WIN = 2;

    public enum State { WAITING, RUNNING, FINISHED }
    public record Snapshot(UUID playerA, UUID playerB, int winsA, int winsB, State state) {}

    private static final Deque<UUID> QUEUE = new ArrayDeque<>();
    private static final Map<UUID, DuelSession> BY_PLAYER = new HashMap<>();

    private DuelMatchService() {}

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("ggo")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("duels")
                    .then(Commands.literal("status").executes(ctx -> status(ctx.getSource().getServer(), ctx.getSource())))
                    .then(Commands.literal("dev")
                        .then(Commands.literal("queue").executes(ctx -> join(ctx.getSource().getPlayerOrException())))
                        .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource().getPlayerOrException())))))
        );
    }

    public static synchronized boolean queued(UUID playerId) { return QUEUE.contains(playerId); }

    public static synchronized Snapshot snapshot(UUID playerId) {
        DuelSession session = BY_PLAYER.get(playerId);
        return session == null ? null : session.snapshot();
    }

    public static synchronized boolean enqueue(ServerPlayer player) {
        UUID id = player.getUUID();
        if (BY_PLAYER.containsKey(id) || QUEUE.contains(id)) return false;
        QUEUE.addLast(id);
        tryPair(player.getServer());
        return true;
    }

    public static synchronized void remove(UUID playerId) {
        QUEUE.remove(playerId);
        DuelSession session = BY_PLAYER.remove(playerId);
        if (session == null) return;
        UUID other = session.other(playerId);
        BY_PLAYER.remove(other);
    }

    /** Records a server-authoritative duel round result. No client score is trusted. */
    public static synchronized boolean recordRoundWinner(MinecraftServer server, UUID winnerId) {
        DuelSession session = BY_PLAYER.get(winnerId);
        if (session == null || session.state != State.RUNNING) return false;
        if (!session.playerA.equals(winnerId) && !session.playerB.equals(winnerId)) return false;

        if (session.playerA.equals(winnerId)) session.winsA++;
        else session.winsB++;

        if (session.winsA >= ROUNDS_TO_WIN || session.winsB >= ROUNDS_TO_WIN) {
            session.state = State.FINISHED;
            announce(server, session);
            BY_PLAYER.remove(session.playerA);
            BY_PLAYER.remove(session.playerB);
            tryPair(server);
        }
        return true;
    }

    @SubscribeEvent
    public static synchronized void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        QUEUE.remove(id);
        DuelSession session = BY_PLAYER.remove(id);
        if (session != null) {
            UUID otherId = session.other(id);
            BY_PLAYER.remove(otherId);
            MinecraftServer server = event.getEntity().getServer();
            if (server != null) {
                ServerPlayer other = server.getPlayerList().getPlayer(otherId);
                if (other != null) other.sendSystemMessage(Component.literal("GGO • Duel opponent disconnected").withStyle(ChatFormatting.YELLOW));
                tryPair(server);
            }
        }
    }

    private static int join(ServerPlayer player) {
        if (!enqueue(player)) {
            player.sendSystemMessage(Component.literal("[GGO] Already queued/in a duel.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        player.sendSystemMessage(Component.literal("[GGO] Joined Duels dev queue.").withStyle(ChatFormatting.AQUA));
        return Command.SINGLE_SUCCESS;
    }

    private static int leave(ServerPlayer player) {
        boolean existed = queued(player.getUUID()) || BY_PLAYER.containsKey(player.getUUID());
        remove(player.getUUID());
        player.sendSystemMessage(Component.literal(existed ? "[GGO] Left Duels dev queue/session." : "[GGO] Not queued.")
            .withStyle(existed ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        return existed ? Command.SINGLE_SUCCESS : 0;
    }

    private static int status(MinecraftServer server, net.minecraft.commands.CommandSourceStack source) {
        long sessions = BY_PLAYER.values().stream().distinct().count();
        ServerLevel level = source.getLevel();
        source.sendSuccess(() -> Component.literal(
            "[GGO] Duels queue=" + QUEUE.size() + " activeSessions=" + sessions + " arenaReady=" + DuelArenaService.ready(level)
        ).withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void tryPair(MinecraftServer server) {
        if (server == null) return;
        while (QUEUE.size() >= 2) {
            UUID a = pollOnline(server);
            if (a == null) return;
            UUID b = pollOnline(server);
            if (b == null) {
                QUEUE.addFirst(a);
                return;
            }

            ServerPlayer pa = server.getPlayerList().getPlayer(a);
            ServerPlayer pb = server.getPlayerList().getPlayer(b);
            if (pa == null || pb == null) continue;

            if (!DuelArenaService.placePair(pa, pb)) {
                QUEUE.addFirst(b);
                QUEUE.addFirst(a);
                pa.sendSystemMessage(Component.literal("GGO • Duel arena is not ready yet").withStyle(ChatFormatting.YELLOW));
                pb.sendSystemMessage(Component.literal("GGO • Duel arena is not ready yet").withStyle(ChatFormatting.YELLOW));
                return;
            }

            DuelSession session = new DuelSession(a, b);
            BY_PLAYER.put(a, session);
            BY_PLAYER.put(b, session);
            pa.sendSystemMessage(Component.literal("GGO • DUEL FOUND • BO3 vs " + name(pb)).withStyle(ChatFormatting.GOLD));
            pb.sendSystemMessage(Component.literal("GGO • DUEL FOUND • BO3 vs " + name(pa)).withStyle(ChatFormatting.GOLD));
        }
    }

    private static UUID pollOnline(MinecraftServer server) {
        while (!QUEUE.isEmpty()) {
            UUID id = QUEUE.removeFirst();
            if (server.getPlayerList().getPlayer(id) != null && !BY_PLAYER.containsKey(id)) return id;
        }
        return null;
    }

    private static void announce(MinecraftServer server, DuelSession session) {
        UUID winner = session.winsA > session.winsB ? session.playerA : session.playerB;
        UUID loser = session.other(winner);
        ServerPlayer pw = server == null ? null : server.getPlayerList().getPlayer(winner);
        ServerPlayer pl = server == null ? null : server.getPlayerList().getPlayer(loser);
        String score = session.winsA + "-" + session.winsB;
        if (pw != null) pw.sendSystemMessage(Component.literal("GGO • DUEL WON " + score).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        if (pl != null) pl.sendSystemMessage(Component.literal("GGO • DUEL LOST " + score).withStyle(ChatFormatting.RED));
    }

    private static String name(ServerPlayer player) { return player == null ? "opponent" : player.getGameProfile().getName(); }

    private static final class DuelSession {
        final UUID playerA;
        final UUID playerB;
        int winsA;
        int winsB;
        State state = State.RUNNING;

        DuelSession(UUID playerA, UUID playerB) {
            this.playerA = playerA;
            this.playerB = playerB;
        }

        UUID other(UUID id) { return playerA.equals(id) ? playerB : playerA; }
        Snapshot snapshot() { return new Snapshot(playerA, playerB, winsA, winsB, state); }
    }
}
