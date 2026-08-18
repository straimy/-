package arena.forge;

import com.mojang.brigadier.Command;
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
 * Server-owned Battle Royale foundation.
 *
 * This intentionally does not depend on Minecraft world-border commands, scoreboards, command
 * blocks, vanilla chest loot or client-owned match state. The eventual BR map is content;
 * match state, safe-zone authority and typed loot points live in GGO Core.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BattleRoyaleService {
    public static final String VERSION = "GGO-BR-V2";

    public enum State { IDLE, COUNTDOWN, RUNNING, FINISHED }

    private static final int MIN_PLAYERS = 2;
    private static final long COUNTDOWN_TICKS = 100L;
    private static final long FINISH_TICKS = 100L;
    private static final long ZONE_PHASE_TICKS = 20L * 90L;
    private static final double[] RADII = { 300.0D, 220.0D, 140.0D, 70.0D, 30.0D };
    private static final float OUTSIDE_DAMAGE = 2.0F;

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
    public static State state() { return state; }
    public static int phase() { return phase; }
    public static double radius() { return RADII[Math.min(phase, RADII.length - 1)]; }

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
            "[GGO] BR state=" + state + " alive=" + ALIVE.size() + "/" + PARTICIPANTS.size()
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
            PARTICIPANTS.add(player.getUUID());
            ALIVE.add(player.getUUID());
        }
        level = target;
        centerX = x;
        centerZ = z;
        phase = 0;
        winner = null;
        BattleRoyaleLootService.cleanup(target);
        spawnedLoot = BattleRoyaleLootService.prepareRound(target);
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
    public static void death(LivingDeathEvent event) {
        if (state != State.RUNNING || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ALIVE.remove(player.getUUID())) return;
        MinecraftServer server = player.getServer();
        if (server != null) checkWinner(server);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        PARTICIPANTS.remove(id);
        ALIVE.remove(id);
        MinecraftServer server = event.getEntity().getServer();
        if (server != null && state == State.RUNNING) checkWinner(server);
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
