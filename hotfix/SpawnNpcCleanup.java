package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Removes legacy lobby/menu NPC entities while explicitly preserving Swittie combat entities. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SpawnNpcCleanup {
    private static long nextSweep;
    private SpawnNpcCleanup(){}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;ArenaRuntime r=GunnerArenaMod.RUNTIME;MinecraftServer server=ServerLifecycleHooks.getCurrentServer();if(r==null||server==null)return;long now=r.serverTick();if(now<nextSweep)return;nextSweep=now+40;
        for(ServerLevel level:server.getAllLevels()){
            List<Entity> copy=new ArrayList<>();for(Entity entity:level.getAllEntities())copy.add(entity);
            for(Entity entity:copy){
                if(entity instanceof Player||entity.getTags().contains("gunglory_swittie_fox")||entity.getTags().contains("gunglory_swittie_companion"))continue;
                if(entity.getTags().contains("gunnerarena_drop_marker")||entity.getTags().contains("gunglory_ammo_box_marker"))continue;
                if(legacyMenuNpc(entity,r))entity.discard();
            }
        }
    }
    private static boolean legacyMenuNpc(Entity e,ArenaRuntime r){
        var key=ForgeRegistries.ENTITY_TYPES.getKey(e.getType());String ns=key==null?"":key.getNamespace().toLowerCase(Locale.ROOT),path=key==null?"":key.getPath().toLowerCase(Locale.ROOT);
        String name=e.hasCustomName()?e.getCustomName().getString().toLowerCase(Locale.ROOT):"";
        if(ns.contains("fancynpc")||ns.contains("fancynpcs")||path.contains("fancynpc"))return true;
        if(name.contains("проводник")||name.contains("начать игру")||name.contains("menu npc")||name.contains("menu_npc")||name.contains("лобби npc"))return true;
        if(e.getTags().contains("gunnerarena_menu_npc")||e.getTags().contains("spawn_menu_npc"))return true;
        // The remaining spawn mannequin in the legacy map is an equipped armor stand. Only scrub
        // these inside a GGO safe/lobby region so decorative/arena stands elsewhere are untouched.
        if(e instanceof ArmorStand stand&&r.safeRegions().isSafe(e.blockPosition(),e.level().dimension())){
            boolean equipped=!stand.getMainHandItem().isEmpty()||!stand.getOffhandItem().isEmpty()||!stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()||!stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).isEmpty()||!stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).isEmpty()||!stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).isEmpty();
            return equipped||stand.hasCustomName();
        }
        return false;
    }
}
