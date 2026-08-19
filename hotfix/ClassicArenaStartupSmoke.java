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

/** One-shot real-world Classic integration smoke. Disabled unless -Dggo.classic.smoke=true. */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClassicArenaStartupSmoke {
    public static final String VERSION = "GGO-CLASSIC-STARTUP-SMOKE-V2";
    public static final String PROPERTY = "ggo.classic.smoke";

    private static final Logger LOG = LogUtils.getLogger();
    private static final AABB ARENA_MARKERS = new AABB(47.0D, 60.0D, 47.0D, 113.0D, 110.0D, 113.0D);

    private ClassicArenaStartupSmoke() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean(PROPERTY)) return;

        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        GgoClassicReadiness.clear(server);

        ClassicArenaMapGenerator generator = ClassicArenaMapGenerator.shared();
        boolean generated = generator.generate(level);
        var snapshot = generator.snapshot();

        List<Marker> markers = level.getEntities(EntityType.MARKER, ARENA_MARKERS, marker -> true);
        int ammo1 = countTag(markers, "gun_1_ammo");
        int ammo2 = countTag(markers, "gun_2_ammo");
        int ammo3 = countTag(markers, "gun_3_ammo");
        // HEALTH_CELLS is a generator cell quota. Recovered s_health_* templates may legitimately
        // contain one or two small_health_orb markers, so marker count is not the cell count.
        int healthCells = snapshot.health();
        int healthMarkers = countTag(markers, "small_health_orb");
        int respawn = countTag(markers, "respawn_point");
        int jumpPads = countTag(markers, "jump_pad_marker");

        boolean pass = generated
            && snapshot.placed() == ClassicArenaMapGenerator.TOTAL_CELLS
            && ammo1 == 4 && ammo2 == 3 && ammo3 == 3
            && healthCells == ClassicArenaMapGenerator.HEALTH_CELLS
            && healthMarkers >= healthCells
            && respawn > 0;

        String markerResult = "not-written";
        if (pass) {
            try {
                // Readiness stores the stable generator quota, not the template-internal marker count.
                GgoClassicReadiness.writePass(server, snapshot.placed(), ammo1, ammo2, ammo3, healthCells, respawn, jumpPads);
                markerResult = "written";
            } catch (Exception ex) {
                pass = false;
                markerResult = "write-failed:" + ex.getClass().getSimpleName();
            }
        }

        LOG.info("[GGO-CLASSIC-REALWORLD-SMOKE] result={} generated={} cells={} ammo={}/{}/{} health={} healthMarkers={} respawn={} jumpPads={} marker={} error={}",
            pass ? "PASS" : "FAIL",
            generated,
            snapshot.placed(),
            ammo1, ammo2, ammo3,
            healthCells,
            healthMarkers,
            respawn,
            jumpPads,
            markerResult,
            snapshot.error().isBlank() ? "none" : snapshot.error());

        server.halt(false);
    }

    private static int countTag(List<Marker> markers, String tag) {
        int count = 0;
        for (Marker marker : markers) if (marker.getTags().contains(tag)) count++;
        return count;
    }
}
