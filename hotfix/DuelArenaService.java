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

/** Server-owned Duels arena adapter with marker-optional deterministic fallback spawns. */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DuelArenaService {
    public static final String VERSION = "GGO-DUEL-ARENA-V1";
    private static final Logger LOG = LogUtils.getLogger();
    static final AABB SCAN = new AABB(-4096, -64, -4096, 4096, 384, 4096);

    private static final int CENTER_X = 2040;
    private static final int FLOOR_Y = 80;
    private static final int CENTER_Z = 2048;
    private static final int HALF_X = 12;
    private static final int HALF_Z = 7;
    private static final SpawnPoint FALLBACK_A = new SpawnPoint(CENTER_X - 5.0D, FLOOR_Y + 1.0D, CENTER_Z + 0.5D, -90.0F);
    private static final SpawnPoint FALLBACK_B = new SpawnPoint(CENTER_X + 5.0D, FLOOR_Y + 1.0D, CENTER_Z + 0.5D, 90.0F);

    record SpawnPoint(double x, double y, double z, float yaw) {}

    private DuelArenaService() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        boolean created = ensureArena(level);
        LOG.info("[GGO-DUELS-ARENA] ready={} created={} authoredA={} authoredB={} fallback={}",
            ready(level), created, authoredSpawn(level, "duel_spawn_a") != null,
            authoredSpawn(level, "duel_spawn_b") != null, fallbackReady(level));
    }

    public static boolean ready(ServerLevel level) {
        boolean authored = authoredSpawn(level, "duel_spawn_a") != null && authoredSpawn(level, "duel_spawn_b") != null;
        return authored || fallbackReady(level);
    }

    /** Creates only world geometry for fallback. Fallback spawns are coordinates, not entities. */
    public static synchronized boolean ensureArena(ServerLevel level) {
        if (ready(level)) return false;
        removeOwnedFallbackMarkers(level);
        buildPlatform(level);
        return true;
    }

    public static boolean placePair(ServerPlayer a, ServerPlayer b) {
        if (!(a.level() instanceof ServerLevel level) || b.level() != level) return false;
        if (!ready(level)) ensureArena(level);
        SpawnPoint spawnA = resolvedSpawn(level, true);
        SpawnPoint spawnB = resolvedSpawn(level, false);
        if (spawnA == null || spawnB == null) return false;

        GgoMovementAuthority.authorize(a, 30L);
        GgoMovementAuthority.authorize(b, 30L);
        a.teleportTo(level, spawnA.x(), spawnA.y(), spawnA.z(), spawnA.yaw(), 0.0F);
        b.teleportTo(level, spawnB.x(), spawnB.y(), spawnB.z(), spawnB.yaw(), 0.0F);
        a.setHealth(a.getMaxHealth());
        b.setHealth(b.getMaxHealth());
        return true;
    }

    static SpawnPoint resolvedSpawn(ServerLevel level, boolean first) {
        Marker a = authoredSpawn(level, "duel_spawn_a");
        Marker b = authoredSpawn(level, "duel_spawn_b");
        if (a != null && b != null) {
            Marker marker = first ? a : b;
            return new SpawnPoint(marker.getX(), marker.getY(), marker.getZ(), marker.getYRot());
        }
        if (!fallbackReady(level)) return null;
        return first ? FALLBACK_A : FALLBACK_B;
    }

    private static boolean fallbackReady(ServerLevel level) {
        return level.getBlockState(new BlockPos(CENTER_X, FLOOR_Y, CENTER_Z)).is(Blocks.SMOOTH_STONE)
            && level.getBlockState(BlockPos.containing(FALLBACK_A.x(), FALLBACK_A.y(), FALLBACK_A.z())).isAir()
            && level.getBlockState(BlockPos.containing(FALLBACK_B.x(), FALLBACK_B.y(), FALLBACK_B.z())).isAir();
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

    private static void removeOwnedFallbackMarkers(ServerLevel level) {
        for (Marker marker : level.getEntities(EntityType.MARKER, SCAN,
            entity -> entity.getTags().contains("ggo_duel_arena"))) marker.discard();
    }

    static Marker authoredSpawn(ServerLevel level, String tag) {
        List<Marker> markers = level.getEntities(EntityType.MARKER, SCAN, marker -> marker.getTags().contains(tag));
        return markers.stream().min(Comparator.comparingDouble(marker -> marker.distanceToSqr(0.0D, marker.getY(), 0.0D))).orElse(null);
    }
}

/** One-shot real-world Duels arena smoke. Disabled unless -Dggo.duels.smoke=true. */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
final class DuelArenaStartupSmoke {
    static final String VERSION = "GGO-DUELS-STARTUP-SMOKE-V2";
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
        boolean spawnA = false;
        boolean spawnB = false;
        try {
            created = DuelArenaService.ensureArena(level);
            spawnA = DuelArenaService.resolvedSpawn(level, true) != null;
            spawnB = DuelArenaService.resolvedSpawn(level, false) != null;
            pass = DuelArenaService.ready(level) && spawnA && spawnB;
        } catch (Exception ex) {
            pass = false;
            error = ex.getClass().getSimpleName() + ":" + String.valueOf(ex.getMessage());
        }
        LOG.info("[GGO-DUELS-REALWORLD-SMOKE] result={} ready={} created={} spawnA={} spawnB={} error={}",
            pass ? "PASS" : "FAIL", DuelArenaService.ready(level), created, spawnA, spawnB, error);
        server.halt(false);
    }
}
