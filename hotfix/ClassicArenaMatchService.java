package arena.forge;

import com.mojang.brigadier.Command;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Command-block-free state machine for the recovered Classic Arena mode.
 *
 * The old map stored players_count, kills_to_win, selected guns and countdown state in scoreboard
 * objectives and moved the flow with redstone blocks. This service owns those values directly.
 * Classic remains MIGRATING in /play; dev commands are OP-only until real-world gameplay smoke passes.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClassicArenaMatchService {
    public static final String VERSION = "GGO-CLASSIC-MATCH-V3";
    public enum State { WAITING, GENERATING, COUNTDOWN, RUNNING, FINISHED, ERROR }

    private static final int MIN_PLAYERS = 2;
    private static final int KILLS_PER_PLAYER = 10;
    private static final long COUNTDOWN_TICKS = 60L;
    private static final long FINISH_HOLD_TICKS = 100L;
    private static final AABB ARENA_MARKERS = new AABB(47.0D, 60.0D, 47.0D, 113.0D, 110.0D, 113.0D);
    private static final ClassicArenaMapGenerator GENERATOR = new ClassicArenaMapGenerator();
    private static final Set<UUID> PARTICIPANTS = new HashSet<>();
    private static final Map<UUID, Integer> KILLS = new HashMap<>();

    private static State state = State.WAITING;
    private static long stateSince;
    private static long deadline;
    private static int killTarget;
    private static UUID winner;
    private static String error = "";
    private static ServerLevel matchLevel;

    private ClassicArenaMatchService() {}

    public static boolean isParticipant(ServerPlayer player) {
        return PARTICIPANTS.contains(player.getUUID());
    }

    public static State state() { return state; }
    public static int killTarget() { return killTarget; }
    public static int kills(ServerPlayer player) { return KILLS.getOrDefault(player.getUUID(), 0); }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("ggo")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("classic")
                    .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                    .then(Commands.literal("dev")
                        .then(Commands.literal("generate").executes(ctx -> devGenerate(ctx.getSource())))
                        .then(Commands.literal("start").executes(ctx -> devStart(ctx.getSource())))
                        .then(Commands.literal("stop").executes(ctx -> devStop(ctx.getSource())))))
        );
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
            "[GGO] Classic state=" + state + " participants=" + PARTICIPANTS.size()
                + " target=" + killTarget
                + " guns=" + String.join(",", ClassicArenaLoadoutService.selectedWeapons())
                + (winner == null ? "" : " winner=" + winner)
                + (error.isBlank() ? "" : " error=" + error)
        ).withStyle(state == State.ERROR ? ChatFormatting.RED : ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    /** Player-free structure/marker integration smoke for the imported world. */
    private static int devGenerate(net.minecraft.commands.CommandSourceStack source) {
        if (state != State.WAITING) {
            source.sendFailure(Component.literal("[GGO] Refusing dev generation while Classic is " + state));
            return 0;
        }
        ServerLevel level = source.getLevel();
        if (!GENERATOR.generate(level)) {
            var snapshot = GENERATOR.snapshot();
            source.sendFailure(Component.literal("[GGO] Classic dev generation failed: " + snapshot.error()));
            return 0;
        }

        List<Marker> markers = level.getEntities(EntityType.MARKER, ARENA_MARKERS, marker -> true);
        int ammo1 = countTag(markers, "gun_1_ammo");
        int ammo2 = countTag(markers, "gun_2_ammo");
        int ammo3 = countTag(markers, "gun_3_ammo");
        int health = countTag(markers, "small_health_orb") + countTag(markers, "health_orb");
        int respawn = countTag(markers, "respawn_point");
        int jumpPads = countTag(markers, "jump_pad_marker");
        var snapshot = GENERATOR.snapshot();

        boolean quotasOk = snapshot.placed() == ClassicArenaMapGenerator.TOTAL_CELLS
            && ammo1 == 4 && ammo2 == 3 && ammo3 == 3
            && health == ClassicArenaMapGenerator.HEALTH_CELLS
            && respawn > 0;

        Component message = Component.literal(
            "[GGO] Classic dev generation: cells=" + snapshot.placed()
                + " ammo=" + ammo1 + "/" + ammo2 + "/" + ammo3
                + " health=" + health + " respawn=" + respawn + " jumpPads=" + jumpPads
                + " result=" + (quotasOk ? "PASS" : "CHECK")
        ).withStyle(quotasOk ? ChatFormatting.GREEN : ChatFormatting.GOLD);
        source.sendSuccess(() -> message, true);
        return quotasOk ? Command.SINGLE_SUCCESS : 0;
    }

    private static int countTag(List<Marker> markers, String tag) {
        int count = 0;
        for (Marker marker : markers) if (marker.getTags().contains(tag)) count++;
        return count;
    }

    private static int devStart(net.minecraft.commands.CommandSourceStack source) {
        if (state != State.WAITING) {
            source.sendFailure(Component.literal("[GGO] Classic is already " + state));
            return 0;
        }
        ServerLevel level = source.getLevel();
        List<ServerPlayer> candidates = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) candidates.add(player);
        }
        if (candidates.size() < MIN_PLAYERS) {
            source.sendFailure(Component.literal("[GGO] Classic dev start needs at least " + MIN_PLAYERS + " non-spectator players."));
            return 0;
        }
        if (!begin(level, candidates)) {
            source.sendFailure(Component.literal("[GGO] Classic start failed: " + error));
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int devStop(net.minecraft.commands.CommandSourceStack source) {
        reset(source.getServer(), true);
        source.sendSuccess(() -> Component.literal("[GGO] Classic stopped/reset.").withStyle(ChatFormatting.YELLOW), true);
        return Command.SINGLE_SUCCESS;
    }

    /** Future normal mode selector calls this with players queued for Classic. */
    public static synchronized boolean begin(ServerLevel level, List<ServerPlayer> players) {
        if (state != State.WAITING || players.size() < MIN_PLAYERS) return false;
        PARTICIPANTS.clear();
        KILLS.clear();
        for (ServerPlayer player : players) {
            PARTICIPANTS.add(player.getUUID());
            KILLS.put(player.getUUID(), 0);
        }
        winner = null;
        error = "";
        matchLevel = level;
        killTarget = players.size() * KILLS_PER_PLAYER;
        transition(level.getServer(), State.GENERATING, 0L);

        if (!GENERATOR.generate(level)) {
            return fail(level.getServer(), "generation: " + GENERATOR.snapshot().error());
        }
        if (!ClassicArenaLoadoutService.prepareRound(level, players)) {
            return fail(level.getServer(), "loadout: one or more recovered Classic weapon IDs are unavailable");
        }
        if (!ClassicArenaSpawnService.placeInitial(level, players)) {
            return fail(level.getServer(), "spawn: generated arena contains no usable respawn points");
        }

        transition(level.getServer(), State.COUNTDOWN, COUNTDOWN_TICKS);
        broadcast(level.getServer(), Component.literal("GGO • CLASSIC ARENA").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        broadcast(level.getServer(), Component.literal("First to " + killTarget + " kills").withStyle(ChatFormatting.GRAY));
        broadcast(level.getServer(), Component.literal(
            "Loadout: " + String.join(" • ", ClassicArenaLoadoutService.selectedWeapons())
        ).withStyle(ChatFormatting.DARK_GRAY));
        return true;
    }

    private static boolean fail(MinecraftServer server, String reason) {
        error = reason;
        transition(server, State.ERROR, 0L);
        return false;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();

        if (state == State.COUNTDOWN) {
            long left = deadline - now;
            if (left <= 0L) {
                transition(server, State.RUNNING, 0L);
                broadcastParticipants(server, Component.literal("FIGHT").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
            } else if ((left % 20L) == 0L) {
                long sec = Math.max(1L, (left + 19L) / 20L);
                broadcastParticipants(server, Component.literal(String.valueOf(sec)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
            }
        } else if (state == State.FINISHED && now >= deadline) {
            reset(server, false);
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent event) {
        if (state != State.RUNNING) return;
        if (!(event.getEntity() instanceof ServerPlayer victim) || !isParticipant(victim)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer == victim || !isParticipant(killer)) return;

        int value = KILLS.merge(killer.getUUID(), 1, Integer::sum);
        killer.heal(Math.min(4.0F, killer.getMaxHealth() - killer.getHealth()));
        killer.displayClientMessage(Component.literal("GGO • " + value + "/" + killTarget + " KILLS"), true);
        if (value >= killTarget) finish(killer);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        if (!PARTICIPANTS.contains(id)) return;
        PARTICIPANTS.remove(id);
        KILLS.remove(id);

        if (state == State.RUNNING || state == State.COUNTDOWN) {
            MinecraftServer server = event.getEntity().getServer();
            if (server != null && PARTICIPANTS.size() < MIN_PLAYERS) {
                ServerPlayer remaining = PARTICIPANTS.isEmpty() ? null : server.getPlayerList().getPlayer(PARTICIPANTS.iterator().next());
                if (remaining != null) finish(remaining); else reset(server, false);
            }
        }
    }

    private static void finish(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || state != State.RUNNING) return;
        winner = player.getUUID();
        transition(server, State.FINISHED, FINISH_HOLD_TICKS);
        broadcastParticipants(server, Component.literal(player.getGameProfile().getName() + " WINS").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    private static void reset(MinecraftServer server, boolean forced) {
        if (server != null && matchLevel != null) {
            for (UUID id : List.copyOf(PARTICIPANTS)) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null && player.level() == matchLevel) {
                    player.teleportTo(matchLevel, 36.5D, 76.0D, 4.5D, 0.0F, 0.0F);
                    if (!forced) player.displayClientMessage(Component.literal("GGO • Back to lobby"), true);
                }
            }
        }
        PARTICIPANTS.clear();
        KILLS.clear();
        killTarget = 0;
        winner = null;
        error = "";
        matchLevel = null;
        state = State.WAITING;
        stateSince = server == null ? 0L : server.getTickCount();
        deadline = 0L;
    }

    private static void transition(MinecraftServer server, State next, long durationTicks) {
        state = next;
        stateSince = server == null ? 0L : server.getTickCount();
        deadline = durationTicks <= 0L ? 0L : stateSince + durationTicks;
    }

    private static void broadcastParticipants(MinecraftServer server, Component message) {
        if (server == null) return;
        for (UUID id : PARTICIPANTS) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) player.displayClientMessage(message, true);
        }
    }

    private static void broadcast(MinecraftServer server, Component message) {
        if (server == null) return;
        for (UUID id : PARTICIPANTS) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) player.sendSystemMessage(message);
        }
    }
}
