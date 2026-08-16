package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Restores normal melee damage for the tagged arena knife; safe zones remain protected. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaKnifeDamageFix {
    private ArenaKnifeDamageFix(){}

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void attack(LivingAttackEvent e){
        if(allowed(e.getSource().getEntity(),e.getEntity()))e.setCanceled(false);
    }

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void hurt(LivingHurtEvent e){
        if(allowed(e.getSource().getEntity(),e.getEntity()))e.setCanceled(false);
    }

    private static boolean allowed(net.minecraft.world.entity.Entity attacker,net.minecraft.world.entity.LivingEntity victim){
        if(!(attacker instanceof ServerPlayer a)||!(victim instanceof ServerPlayer v))return false;
        ItemStack held=a.getMainHandItem();
        if(held.isEmpty()||!held.hasTag()||!held.getTag().getBoolean("GunnerArenaKnife"))return false;
        ArenaRuntime r=GunnerArenaMod.RUNTIME;
        if(r==null||!r.auth().isAuthenticated(a)||!r.auth().isAuthenticated(v))return false;
        if(r.players().session(a).state()!=ArenaPlayerState.ALIVE||r.players().session(v).state()!=ArenaPlayerState.ALIVE)return false;
        return !r.safeRegions().isSafe(a)&&!r.safeRegions().isSafe(v);
    }
}
