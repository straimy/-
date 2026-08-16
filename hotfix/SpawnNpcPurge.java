package arena.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Removes obsolete spawn/menu NPCs while explicitly preserving the combat Swittie bot. */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpawnNpcPurge {
    private static final String SWITTIE_TAG = "gunglory_swittie_fox";
    private static long nextSweep;

    private SpawnNpcPurge() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        purge(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        long tick = server.getTickCount();
        if (tick < nextSweep) return;
        nextSweep = tick + 100L;
        purge(server);
    }

    private static void purge(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> snapshot = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) snapshot.add(entity);
            for (Entity entity : snapshot) {
                if (entity.getTags().contains(SWITTIE_TAG)) continue;
                if (isLegacySpawnNpc(entity)) entity.discard();
            }
        }
    }

    private static boolean isLegacySpawnNpc(Entity entity) {
        String name = entity.getCustomName() == null ? "" : entity.getCustomName().getString().toLowerCase(Locale.ROOT);
        if (name.contains("kvicloud")) return true;
        for (String raw : entity.getTags()) {
            String tag = raw.toLowerCase(Locale.ROOT);
            if (tag.equals("gunner_arena_npc_hitbox")
                    || tag.equals("gunnerarena_menu_npc")
                    || tag.contains("legacy_npc")
                    || tag.contains("npc_hitbox")
                    || tag.contains("spawn_npc")
                    || tag.contains("menu_npc")) {
                return true;
            }
        }
        return false;
    }
}
