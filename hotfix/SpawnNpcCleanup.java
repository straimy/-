package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Removes legacy menu/lobby NPC entities while explicitly preserving Swittie combat entities. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SpawnNpcCleanup {
    private static long nextSweep;
    private SpawnNpcCleanup(){}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;ArenaRuntime r=GunnerArenaMod.RUNTIME;MinecraftServer server= ServerLifecycleHooks.getCurrentServer();if(r==null||server==null)return;long now=r.serverTick();if(now<nextSweep)return;nextSweep=now+100;
        for(ServerLevel level:server.getAllLevels()){
            List<Entity> copy=new ArrayList<>();for(Entity entity:level.getAllEntities())copy.add(entity);
            for(Entity entity:copy){if(entity instanceof Player||entity.getTags().contains("gunglory_swittie_fox")||entity.getTags().contains("gunglory_swittie_companion"))continue;if(legacyMenuNpc(entity))entity.discard();}
        }
    }
    private static boolean legacyMenuNpc(Entity e){
        var key=ForgeRegistries.ENTITY_TYPES.getKey(e.getType());String ns=key==null?"":key.getNamespace().toLowerCase(Locale.ROOT),path=key==null?"":key.getPath().toLowerCase(Locale.ROOT);
        if(ns.contains("fancynpc")||ns.contains("fancynpcs")||path.contains("fancynpc"))return true;
        String name=e.hasCustomName()?e.getCustomName().getString().toLowerCase(Locale.ROOT):"";
        return name.contains("проводник")||name.contains("menu npc")||name.contains("menu_npc")||name.contains("лобби npc")||e.getTags().contains("gunnerarena_menu_npc")||e.getTags().contains("spawn_menu_npc");
    }
}
