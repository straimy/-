package arena.forge;

import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.AABB;

/**
 * Map-facing Duels adapter. The map owns only marker positions; round/session logic stays in Core.
 * Initial contract: one active duel arena exposes exactly one duel_spawn_a and one duel_spawn_b.
 */
public final class DuelArenaService {
    public static final String VERSION = "GGO-DUEL-ARENA-V1";
    private static final AABB SCAN = new AABB(-4096, -64, -4096, 4096, 384, 4096);

    private DuelArenaService() {}

    public static boolean ready(ServerLevel level) {
        return spawn(level, "duel_spawn_a") != null && spawn(level, "duel_spawn_b") != null;
    }

    public static boolean placePair(ServerPlayer a, ServerPlayer b) {
        if (!(a.level() instanceof ServerLevel level) || b.level() != level) return false;
        Marker spawnA = spawn(level, "duel_spawn_a");
        Marker spawnB = spawn(level, "duel_spawn_b");
        if (spawnA == null || spawnB == null) return false;

        GgoMovementAuthority.authorize(a, 30L);
        GgoMovementAuthority.authorize(b, 30L);
        a.teleportTo(level, spawnA.getX(), spawnA.getY(), spawnA.getZ(), spawnA.getYRot(), spawnA.getXRot());
        b.teleportTo(level, spawnB.getX(), spawnB.getY(), spawnB.getZ(), spawnB.getYRot(), spawnB.getXRot());
        a.setHealth(a.getMaxHealth());
        b.setHealth(b.getMaxHealth());
        return true;
    }

    private static Marker spawn(ServerLevel level, String tag) {
        List<Marker> markers = level.getEntities(EntityType.MARKER, SCAN, marker -> marker.getTags().contains(tag));
        return markers.stream().min(Comparator.comparingDouble(marker -> marker.distanceToSqr(0.0D, marker.getY(), 0.0D))).orElse(null);
    }
}
