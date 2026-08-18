package arena.forge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClassicArenaSpawnService {
    public static final String VERSION = "GGO-CLASSIC-SPAWN-V3";
    private static final long RESPAWN_DELAY_TICKS = 60L;
    private static final double SAFE_RADIUS = 32.0D;
    private static final double SAFE_RADIUS_SQR = SAFE_RADIUS * SAFE_RADIUS;
    private static final AABB ARENA = new AABB(47.0D, 68.0D, 47.0D, 113.0D, 105.0D, 113.0D);
    private static final double WAIT_X = 34.5D, WAIT_Y = 51.0D, WAIT_Z = 6.5D;
    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    private ClassicArenaSpawnService() {}

    public static boolean placeInitial(ServerLevel level, List<ServerPlayer> players) {
        List<Marker> markers = respawnMarkers(level);
        if (markers.isEmpty()) return false;
        List<ServerPlayer> placed = new ArrayList<>();
        for (ServerPlayer player : players) {
            Marker marker = chooseAgainst(level, markers, placed);
            if (marker == null) return false;
            GgoMovementAuthority.authorize(player, 10L);
            player.teleportTo(level, marker.getX(), marker.getY() + 0.15D, marker.getZ(), player.getYRot(), player.getXRot());
            player.fallDistance = 0.0F;
            placed.add(player);
        }
        return true;
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ClassicArenaMatchService.isParticipant(player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        READY_AT.put(player.getUUID(), server.getTickCount() + RESPAWN_DELAY_TICKS);
        ServerLevel level = player.serverLevel();
        GgoMovementAuthority.authorize(player, 10L);
        player.teleportTo(level, WAIT_X, WAIT_Y, WAIT_Z, player.getYRot(), player.getXRot());
        player.displayClientMessage(Component.literal("GGO • Respawn in 3"), true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null || READY_AT.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        List<UUID> done = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : List.copyOf(READY_AT.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !ClassicArenaMatchService.isParticipant(player)) { done.add(entry.getKey()); continue; }
            long left = entry.getValue() - now;
            if (left > 0L) {
                if ((left % 20L) == 0L) player.displayClientMessage(Component.literal("GGO • Respawn in " + Math.max(1L, (left + 19L) / 20L)), true);
                continue;
            }
            Marker marker = chooseSpawn(player.serverLevel(), player);
            if (marker != null) {
                GgoMovementAuthority.authorize(player, 10L);
                player.teleportTo(player.serverLevel(), marker.getX(), marker.getY() + 0.15D, marker.getZ(), player.getYRot(), player.getXRot());
                player.fallDistance = 0.0F;
            }
            player.displayClientMessage(Component.literal("GGO • FIGHT"), true);
            done.add(entry.getKey());
        }
        for (UUID id : done) READY_AT.remove(id);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { READY_AT.remove(event.getEntity().getUUID()); }

    private static Marker chooseSpawn(ServerLevel level, ServerPlayer respawning) {
        List<Marker> markers = respawnMarkers(level);
        if (markers.isEmpty()) return null;
        List<ServerPlayer> active = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player == respawning || !player.isAlive() || !ClassicArenaMatchService.isParticipant(player)) continue;
            active.add(player);
        }
        return chooseAgainst(level, markers, active);
    }

    private static List<Marker> respawnMarkers(ServerLevel level) {
        return level.getEntities(EntityType.MARKER, ARENA, marker -> marker.getTags().contains("respawn_point"));
    }

    private static Marker chooseAgainst(ServerLevel level, List<Marker> markers, List<ServerPlayer> active) {
        if (markers.isEmpty()) return null;
        if (active.isEmpty()) return markers.get(level.getRandom().nextInt(markers.size()));
        List<Marker> safe = new ArrayList<>();
        for (Marker marker : markers) if (nearestPlayerDistanceSqr(marker, active) >= SAFE_RADIUS_SQR) safe.add(marker);
        if (!safe.isEmpty()) return safe.get(level.getRandom().nextInt(safe.size()));
        Marker best = null; double bestDistance = -1.0D;
        for (Marker marker : markers) {
            double distance = nearestPlayerDistanceSqr(marker, active);
            if (distance > bestDistance) { best = marker; bestDistance = distance; }
        }
        return best;
    }

    private static double nearestPlayerDistanceSqr(Marker marker, List<ServerPlayer> players) {
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer player : players) nearest = Math.min(nearest, marker.distanceToSqr(player));
        return nearest;
    }
}
