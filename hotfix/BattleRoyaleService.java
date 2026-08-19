package arena.forge;

import com.mojang.brigadier.Command;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-owned Battle Royale runtime.
 *
 * Match state, queueing, alive authority, safe-zone phases and typed loot are owned by GGO Core.
 * Maps are content only and may optionally provide typed BR loot markers.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BattleRoyaleService {
    public static final String VERSION = "GGO-BR-V3";

    public enum State { IDLE, COUNTDOWN, RUNNING, FINISHED }

    private static final int MIN_PLAYERS = 2;
    private static final long COUNTDOWN_TICKS = 100L;
    private static final long FINISH_TICKS = 100L;
    private static final long ZONE_PHASE_TICKS = 20L * 90L;
    private static final double[] RADII = { 300.0D, 220.0D, 140.0D, 70.0D, 30.0D };
    private static final float OUTSIDE_DAMAGE = 2.0F;

    private static final Deque<UUID> QUEUE = new ArrayDeque<>();
    private static final Set<UUID> PARTICIPANTS = new HashSet<>();
    private static final Set<UUID> ALIVE = new HashSet<>();
    private static State state = State.IDLE;
    private static ServerLevel level;
    private static double centerX;
    private static double centerZ;
    private static int phase;
    private static long deadline;
    private static UUID winner;
    private static int spawnedLoot;

    private BattleRoyaleService() {}

    public static boolean isParticipant(ServerPlayer player) { return PARTICIPANTS.contains(player.getUUID()); }
    public static synchronized boolean queued(UUID playerId) { return QUEUE.contains(playerId); }
    public static State state() { return state; }
    public static int phase() { return phase; }
    public static double radius() { return RADII[Math.min(phase, RADII.length - 1)]; }

    public static synchronized boolean enqueue(ServerPlayer player) {
        UUID id = player.getUUID();
        if (state != State.IDLE || PARTICIPANTS.contains(id) || QUEUE.contains(id)) return false;
        QUEUE.addLast(id);
        tryStart(player.getServer());
        return true;
    }

    public static synchronized void remove(UUID playerId) {
        QUEUE.remove(playerId);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("ggo")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("br")
                    .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                    .then(Commands.literal("dev")
                        .then(Commands.literal("start").executes(ctx -> devStart(ctx.getSource())))
                        .then(Commands.literal("stop").executes(ctx -> devStop(ctx.getSource())))))
        );
    }

    private static int status(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
            "[GGO] BR state=" + state + " queue=" + QUEUE.size() + " alive=" + ALIVE.size() + "/" + PARTICIPANTS.size()
                + " phase=" + phase + " radius=" + Math.round(radius()) + " loot=" + spawnedLoot
                + (winner == null ? "" : " winner=" + winner)
        ).withStyle(ChatFormatting.AQUA), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int devStart(net.minecraft.commands.CommandSourceStack source) {
        if (state != State.IDLE) return 0;
        ServerLevel target = source.getLevel();
        List<ServerPlayer> players = target.players().stream().filter(p -> !p.isSpectator()).toList();
        if (players.size() < MIN_PLAYERS) {
            source.sendFailure(Component.literal("[GGO] BR dev start needs at least " + MIN_PLAYERS + " players."));
            return 0;
        }
        begin(target, players, source.getPosition().x, source.getPosition().z);
        source.sendSuccess(() -> Component.literal("[GGO] BR dev session started. loot=" + spawnedLoot).withStyle(ChatFormatting.GREEN), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int devStop(net.minecraft.commands.CommandSourceStack source) {
        reset();
        source.sendSuccess(() -> Component.literal("[GGO] BR reset.").withStyle(ChatFormatting.YELLOW), true);
        return Command.SINGLE_SUCCESS;
    }

    public static synchronized boolean begin(ServerLevel target, List<ServerPlayer> players, double x, double z) {
        if (state != State.IDLE || players.size() < MIN_PLAYERS) return false;
        PARTICIPANTS.clear();
        ALIVE.clear();
        for (ServerPlayer player : players) {
            QUEUE.remove(player.getUUID());
            PARTICIPANTS.add(player.getUUID());
            ALIVE.add(player.getUUID());
        }
        level = target;
        centerX = x;
        centerZ = z;
        phase = 0;
        winner = null;
        BattleRoyaleLootService.cleanup(target);
        spawnedLoot = BattleRoyaleLootService.prepareRound(target, x, z);
        state = State.COUNTDOWN;
        deadline = target.getServer().getTickCount() + COUNTDOWN_TICKS;
        broadcast(target.getServer(), "BATTLE ROYALE • GET READY", ChatFormatting.GOLD);
        return true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null || state == State.IDLE) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();

        if (state == State.COUNTDOWN && now >= deadline) {
            state = State.RUNNING;
            deadline = now + ZONE_PHASE_TICKS;
            broadcast(server, "BATTLE ROYALE • GO", ChatFormatting.GREEN);
            return;
        }

        if (state == State.RUNNING) {
            if (now >= deadline && phase < RADII.length - 1) {
                phase++;
                deadline = now + ZONE_PHASE_TICKS;
                broadcast(server, "ZONE SHRINK • " + Math.round(radius()) + "m", ChatFormatting.RED);
            }
            if ((now % 20L) == 0L) applyZoneDamage(server);
            checkWinner(server);
        } else if (state == State.FINISHED && now >= deadline) {
            reset();
        }
    }

    private static void applyZoneDamage(MinecraftServer server) {
        if (level == null) return;
        double r = radius();
        double r2 = r * r;
        for (UUID id : Set.copyOf(ALIVE)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || player.level() != level || !player.isAlive()) continue;
            double dx = player.getX() - centerX;
            double dz = player.getZ() - centerZ;
            if ((dx * dx + dz * dz) > r2) {
                player.hurt(level.damageSources().magic(), OUTSIDE_DAMAGE);
                player.displayClientMessage(Component.literal("GGO • OUTSIDE SAFE ZONE"), true);
            }
        }
    }

    @SubscribeEvent
    public static synchronized void death(LivingDeathEvent event) {
        if (state != State.RUNNING || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ALIVE.remove(player.getUUID())) return;
        MinecraftServer server = player.getServer();
        if (server != null) checkWinner(server);
    }

    @SubscribeEvent
    public static synchronized void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        QUEUE.remove(id);
        PARTICIPANTS.remove(id);
        ALIVE.remove(id);
        MinecraftServer server = event.getEntity().getServer();
        if (server != null && state == State.RUNNING) checkWinner(server);
    }

    private static void tryStart(MinecraftServer server) {
        if (server == null || state != State.IDLE) return;
        QUEUE.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        if (QUEUE.size() < MIN_PLAYERS) return;

        ServerPlayer first = null;
        for (UUID id : QUEUE) {
            ServerPlayer candidate = server.getPlayerList().getPlayer(id);
            if (candidate != null && !candidate.isSpectator() && candidate.level() instanceof ServerLevel) {
                first = candidate;
                break;
            }
        }
        if (first == null || !(first.level() instanceof ServerLevel target)) return;

        List<ServerPlayer> players = QUEUE.stream()
            .map(id -> server.getPlayerList().getPlayer(id))
            .filter(player -> player != null && !player.isSpectator() && player.level() == target)
            .toList();
        if (players.size() < MIN_PLAYERS) return;

        double x = players.stream().mapToDouble(ServerPlayer::getX).average().orElse(first.getX());
        double z = players.stream().mapToDouble(ServerPlayer::getZ).average().orElse(first.getZ());
        begin(target, players, x, z);
    }

    private static void checkWinner(MinecraftServer server) {
        if (state != State.RUNNING || ALIVE.size() > 1) return;
        winner = ALIVE.isEmpty() ? null : ALIVE.iterator().next();
        state = State.FINISHED;
        deadline = server.getTickCount() + FINISH_TICKS;
        ServerPlayer player = winner == null ? null : server.getPlayerList().getPlayer(winner);
        broadcast(server, player == null ? "BATTLE ROYALE • NO WINNER" : player.getGameProfile().getName() + " WINS BR", ChatFormatting.GOLD);
    }

    private static void broadcast(MinecraftServer server, String text, ChatFormatting color) {
        for (UUID id : PARTICIPANTS) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) player.sendSystemMessage(Component.literal("GGO • " + text).withStyle(color));
        }
    }

    private static void reset() {
        if (level != null) BattleRoyaleLootService.cleanup(level);
        PARTICIPANTS.clear();
        ALIVE.clear();
        state = State.IDLE;
        level = null;
        phase = 0;
        deadline = 0L;
        winner = null;
        spawnedLoot = 0;
    }
}

/** One-shot real-world Battle Royale loot/runtime smoke. Disabled unless -Dggo.br.smoke=true. */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
final class BattleRoyaleStartupSmoke {
    static final String VERSION = "GGO-BR-STARTUP-SMOKE-V1";
    static final String PROPERTY = "ggo.br.smoke";
    private static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final net.minecraft.world.phys.AABB WORLD_SCAN =
        new net.minecraft.world.phys.AABB(-4096, -64, -4096, 4096, 384, 4096);

    private BattleRoyaleStartupSmoke() {}

    @SubscribeEvent
    public static void started(net.minecraftforge.event.server.ServerStartedEvent event) {
        if (!Boolean.getBoolean(PROPERTY)) return;

        var server = event.getServer();
        ServerLevel level = server.overworld();
        boolean createdLayout = false;
        int markers = 0;
        int spawned = 0;
        int taggedItems = 0;
        int remaining = 0;
        String error = "none";
        boolean pass;

        try {
            BattleRoyaleLootService.cleanup(level);
            createdLayout = BattleRoyaleLootService.ensureDefaultLayout(level, 0.0D, 0.0D);
            markers = BattleRoyaleLootService.markerCount(level);
            spawned = BattleRoyaleLootService.prepareRound(level, 0.0D, 0.0D);
            taggedItems = countLoot(level);
            BattleRoyaleLootService.cleanup(level);
            remaining = countLoot(level);
            pass = markers > 0 && spawned > 0 && taggedItems > 0 && remaining == 0 && (!createdLayout || markers == 16);
        } catch (Exception ex) {
            pass = false;
            error = ex.getClass().getSimpleName() + ":" + String.valueOf(ex.getMessage());
        }

        LOG.info("[GGO-BR-REALWORLD-SMOKE] result={} createdLayout={} markers={} spawned={} taggedItems={} remaining={} error={}",
            pass ? "PASS" : "FAIL", createdLayout, markers, spawned, taggedItems, remaining, error);
        server.halt(false);
    }

    private static int countLoot(ServerLevel level) {
        return level.getEntities(net.minecraft.world.entity.EntityType.ITEM, WORLD_SCAN,
            item -> item.getTags().contains("ggo_br_loot")).size();
    }
}
