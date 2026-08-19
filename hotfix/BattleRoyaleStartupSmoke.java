package arena.forge;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** One-shot real-world Battle Royale loot/runtime smoke. Disabled unless -Dggo.br.smoke=true. */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BattleRoyaleStartupSmoke {
    public static final String VERSION = "GGO-BR-STARTUP-SMOKE-V1";
    public static final String PROPERTY = "ggo.br.smoke";

    private static final Logger LOG = LogUtils.getLogger();
    private static final AABB WORLD_SCAN = new AABB(-4096, -64, -4096, 4096, 384, 4096);

    private BattleRoyaleStartupSmoke() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
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

            pass = markers > 0
                && spawned > 0
                && taggedItems > 0
                && remaining == 0
                && (!createdLayout || markers == 16);
        } catch (Exception ex) {
            pass = false;
            error = ex.getClass().getSimpleName() + ":" + String.valueOf(ex.getMessage());
        }

        LOG.info("[GGO-BR-REALWORLD-SMOKE] result={} createdLayout={} markers={} spawned={} taggedItems={} remaining={} error={}",
            pass ? "PASS" : "FAIL", createdLayout, markers, spawned, taggedItems, remaining, error);
        server.halt(false);
    }

    private static int countLoot(ServerLevel level) {
        return level.getEntities(EntityType.ITEM, WORLD_SCAN, item -> item.getTags().contains("ggo_br_loot")).size();
    }
}
