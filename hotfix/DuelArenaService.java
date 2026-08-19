package arena.forge;

import com.mojang.logging.LogUtils;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Server-owned Duels arena adapter.
 *
 * Existing maps may expose duel_spawn_a / duel_spawn_b markers. If they do not, Core creates a
 * small isolated arena and the two markers itself, so BO3 matchmaking does not depend on command
 * blocks or manual map edits.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DuelArenaService {
    public static final String VERSION = "GGO-DUEL-ARENA-V1";
    private static final Logger LOG = LogUtils.getLogger();
    static final AABB SCAN = new AABB(-4096, -64, -4096, 4096, 384, 4096);

    private static final int CENTER_X = 2048;
    private static final int FLOOR_Y = 80;
    private static final int CENTER_Z = 2048;
    private static final int HALF_X = 12;
    private static final int HALF_Z = 7;

    private DuelArenaService() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        boolean created = ensureArena(level);
        LOG.info("[GGO-DUELS-ARENA] ready={} created={} spawnA={} spawnB={}",
            ready(level), created, spawn(level, "duel_spawn_a") != null, spawn(level, "duel_spawn_b") != null);
    }

    public static boolean ready(ServerLevel level) {
        return spawn(level, "duel_spawn_a") != null && spawn(level, "duel_spawn_b") != null;
    }

    /** Creates the fallback arena only when the map has no complete Duels marker pair. */
    public static synchronized boolean ensureArena(ServerLevel level) {
        if (ready(level)) return false;

        removeSpawnMarkers(level);
        buildPlatform(level);
        createSpawn(level, "duel_spawn_a", CENTER_X - 8.0D, FLOOR_Y + 1.0D, CENTER_Z + 0.5D, -90.0F);
        createSpawn(level, "duel_spawn_b", CENTER_X + 8.0D, FLOOR_Y + 1.0D, CENTER_Z + 0.5D, 90.0F);
        return true;
    }

    public static boolean placePair(ServerPlayer a, ServerPlayer b) {
        if (!(a.level() instanceof ServerLevel level) || b.level() != level) return false;
        if (!ready(level)) ensureArena(level);
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

    private static void buildPlatform(ServerLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int z = CENTER_Z - HALF_Z; z <= CENTER_Z + HALF_Z; z++) {
            for (int x = CENTER_X - HALF_X; x <= CENTER_X + HALF_X; x++) {
                pos.set(x, FLOOR_Y, z);
                level.setBlock(pos, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 5; y++) {
                    pos.set(x, y, z);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }

        for (int x = CENTER_X - HALF_X; x <= CENTER_X + HALF_X; x++) {
            wall(level, x, CENTER_Z - HALF_Z);
            wall(level, x, CENTER_Z + HALF_Z);
        }
        for (int z = CENTER_Z - HALF_Z; z <= CENTER_Z + HALF_Z; z++) {
            wall(level, CENTER_X - HALF_X, z);
            wall(level, CENTER_X + HALF_X, z);
        }
    }

    private static void wall(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 3; y++) {
            pos.set(x, y, z);
            level.setBlock(pos, Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private static void createSpawn(ServerLevel level, String tag, double x, double y, double z, float yaw) {
        Marker marker = EntityType.MARKER.create(level);
        if (marker == null) throw new IllegalStateException("Could not create Duels marker " + tag);
        marker.setPos(x, y, z);
        marker.setYRot(yaw);
        marker.addTag(tag);
        marker.addTag("ggo_duel_arena");
        if (!level.addFreshEntity(marker)) throw new IllegalStateException("Could not add Duels marker " + tag);
    }

    private static void removeSpawnMarkers(ServerLevel level) {
        for (Marker marker : level.getEntities(EntityType.MARKER, SCAN, entity ->
            entity.getTags().contains("duel_spawn_a") || entity.getTags().contains("duel_spawn_b"))) {
            marker.discard();
        }
    }

    static Marker spawn(ServerLevel level, String tag) {
        List<Marker> markers = level.getEntities(EntityType.MARKER, SCAN, marker -> marker.getTags().contains(tag));
        return markers.stream().min(Comparator.comparingDouble(marker -> marker.distanceToSqr(0.0D, marker.getY(), 0.0D))).orElse(null);
    }
}

/** One-shot real-world Duels arena smoke. Disabled unless -Dggo.duels.smoke=true. */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
final class DuelArenaStartupSmoke {
    static final String VERSION = "GGO-DUELS-STARTUP-SMOKE-V1";
    static final String PROPERTY = "ggo.duels.smoke";
    private static final Logger LOG = LogUtils.getLogger();

    private DuelArenaStartupSmoke() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean(PROPERTY)) return;

        var server = event.getServer();
        ServerLevel level = server.overworld();
        boolean created = false;
        String error = "none";
        boolean pass;
        int spawnA = 0;
        int spawnB = 0;

        try {
            created = DuelArenaService.ensureArena(level);
            List<Marker> markers = level.getEntities(EntityType.MARKER, DuelArenaService.SCAN, marker -> true);
            spawnA = countTag(markers, "duel_spawn_a");
            spawnB = countTag(markers, "duel_spawn_b");
            pass = DuelArenaService.ready(level) && spawnA > 0 && spawnB > 0;
        } catch (Exception ex) {
            pass = false;
            error = ex.getClass().getSimpleName() + ":" + String.valueOf(ex.getMessage());
        }

        LOG.info("[GGO-DUELS-REALWORLD-SMOKE] result={} ready={} created={} spawnA={} spawnB={} error={}",
            pass ? "PASS" : "FAIL", DuelArenaService.ready(level), created, spawnA, spawnB, error);
        server.halt(false);
    }

    private static int countTag(List<Marker> markers, String tag) {
        int count = 0;
        for (Marker marker : markers) if (marker.getTags().contains(tag)) count++;
        return count;
    }
}
