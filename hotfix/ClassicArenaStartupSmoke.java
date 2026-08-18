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

/**
 * One-shot real-world integration smoke for Classic Arena.
 *
 * Disabled in normal runtime. Enable only with -Dggo.classic.smoke=true on a disposable copy of
 * the production world. It runs after the server is fully started, invokes the direct Java
 * generator, validates recovered marker quotas, emits one machine-readable result line, and then
 * stops the smoke server without saving the generated test state.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClassicArenaStartupSmoke {
    public static final String VERSION = "GGO-CLASSIC-STARTUP-SMOKE-V1";
    public static final String PROPERTY = "ggo.classic.smoke";

    private static final Logger LOG = LogUtils.getLogger();
    private static final AABB ARENA_MARKERS = new AABB(47.0D, 60.0D, 47.0D, 113.0D, 110.0D, 113.0D);

    private ClassicArenaStartupSmoke() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean(PROPERTY)) return;

        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        ClassicArenaMapGenerator generator = ClassicArenaMapGenerator.shared();
        boolean generated = generator.generate(level);
        var snapshot = generator.snapshot();

        List<Marker> markers = level.getEntities(EntityType.MARKER, ARENA_MARKERS, marker -> true);
        int ammo1 = countTag(markers, "gun_1_ammo");
        int ammo2 = countTag(markers, "gun_2_ammo");
        int ammo3 = countTag(markers, "gun_3_ammo");
        int health = countTag(markers, "small_health_orb") + countTag(markers, "health_orb");
        int respawn = countTag(markers, "respawn_point");
        int jumpPads = countTag(markers, "jump_pad_marker");

        boolean pass = generated
            && snapshot.placed() == ClassicArenaMapGenerator.TOTAL_CELLS
            && ammo1 == 4 && ammo2 == 3 && ammo3 == 3
            && health == ClassicArenaMapGenerator.HEALTH_CELLS
            && respawn > 0;

        LOG.info("[GGO-CLASSIC-REALWORLD-SMOKE] result={} generated={} cells={} ammo={}/{}/{} health={} respawn={} jumpPads={} error={}",
            pass ? "PASS" : "FAIL",
            generated,
            snapshot.placed(),
            ammo1, ammo2, ammo3,
            health,
            respawn,
            jumpPads,
            snapshot.error().isBlank() ? "none" : snapshot.error());

        // This mode is only for a disposable copied world. Do not persist smoke generation changes.
        server.halt(false);
    }

    private static int countTag(List<Marker> markers, String tag) {
        int count = 0;
        for (Marker marker : markers) if (marker.getTags().contains(tag)) count++;
        return count;
    }
}
