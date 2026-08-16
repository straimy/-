package arena.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** One-time cleanup for the old KVICloud/menu NPC experiments. */
@Mod.EventBusSubscriber(modid="gunnerarena", bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class LegacyNpcCleanupV04 {
    private LegacyNpcCleanupV04(){}
    @SubscribeEvent
    public static void started(ServerStartedEvent event){
        for(ServerLevel level:event.getServer().getAllLevels()){
            List<Entity> remove=new ArrayList<>();
            for(Entity e:level.getAllEntities()) if(isLegacy(e)) remove.add(e);
            for(Entity e:remove) e.discard();
        }
    }
    private static boolean isLegacy(Entity e){
        String name=e.getCustomName()==null?"":e.getCustomName().getString();
        if(name.contains("KVICloud")) return true;
        for(String tag:e.getTags()){
            String t=tag.toLowerCase();
            if(t.equals("gunner_arena_npc_hitbox")||t.equals("gunnerarena_menu_npc")||t.contains("legacy_npc")||t.contains("npc_hitbox")) return true;
        }
        return false;
    }
}
