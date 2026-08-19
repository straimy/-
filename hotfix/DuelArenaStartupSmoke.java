package arena.forge;

import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** One-shot real-world Duels arena smoke. Disabled unless -Dggo.duels.smoke=true. */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DuelArenaStartupSmoke {
    public static final String VERSION = "GGO-DUELS-STARTUP-SMOKE-V1";
    public static final String PROPERTY = "ggo.duels.smoke";

    private static final Logger LOG = LogUtils.getLogger();
    private static final AABB SCAN = new AABB(-4096, -64, -4096, 4096, 384, 4096);

    private DuelArenaStartupSmoke() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean(PROPERTY)) return;

        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        boolean created = false;
        String error = "none";
        boolean pass;
        int spawnA = 0;
        int spawnB = 0;

        try {
            created = DuelArenaService.ensureArena(level);
            List<Marker> markers = level.getEntities(EntityType.MARKER, SCAN, marker -> true);
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
