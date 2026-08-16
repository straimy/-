package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Authoritative arena knife melee. The tactical knife never uses ammo and must hurt/knock players. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaKnifeDamageFix {
    private ArenaKnifeDamageFix(){}

    /**
     * Handle the actual player swing ourselves. This bypasses legacy map protection that used to
     * swallow the vanilla melee path after the click, while still respecting GGO state + safe zones.
     */
    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void playerAttack(AttackEntityEvent e){
        if(!(e.getEntity() instanceof ServerPlayer attacker)||!(e.getTarget() instanceof ServerPlayer victim)||!allowed(attacker,victim))return;
        if(attacker.getAttackStrengthScale(.5f)<.72f){e.setCanceled(true);return;}
        e.setCanceled(true);
        victim.invulnerableTime=0;
        victim.setLastHurtByPlayer(attacker);
        float before=victim.getHealth();
        boolean hurt=victim.hurt(attacker.damageSources().playerAttack(attacker),5.0f);
        // Some legacy arena protection cancels even a normal DamageSource. GGO owns combat now,
        // so apply a controlled fallback rather than leaving the knife cosmetic-only.
        if(!hurt&&victim.isAlive()&&victim.getHealth()>=before-.01f){
            float after=Math.max(0f,before-5.0f);victim.setHealth(after);victim.hurtMarked=true;
            if(after<=0f)victim.kill();
        }
        double dx=victim.getX()-attacker.getX(),dz=victim.getZ()-attacker.getZ();
        if(dx*dx+dz*dz>.0001)victim.knockback(.42, -dx, -dz);
        attacker.resetAttackStrengthTicker();
    }

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void attack(LivingAttackEvent e){if(allowed(e.getSource().getEntity(),e.getEntity()))e.setCanceled(false);}

    @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
    public static void hurt(LivingHurtEvent e){if(allowed(e.getSource().getEntity(),e.getEntity()))e.setCanceled(false);}

    private static boolean allowed(net.minecraft.world.entity.Entity attacker,LivingEntity victim){
        if(!(attacker instanceof ServerPlayer a)||!(victim instanceof ServerPlayer v))return false;
        return allowed(a,v);
    }
    private static boolean allowed(ServerPlayer a,ServerPlayer v){
        ItemStack held=a.getMainHandItem();if(!isArenaKnife(held))return false;
        ArenaRuntime r=GunnerArenaMod.RUNTIME;
        if(r==null||!r.auth().isAuthenticated(a)||!r.auth().isAuthenticated(v))return false;
        if(r.players().session(a).state()!=ArenaPlayerState.ALIVE||r.players().session(v).state()!=ArenaPlayerState.ALIVE)return false;
        return !r.safeRegions().isSafe(a)&&!r.safeRegions().isSafe(v);
    }
    private static boolean isArenaKnife(ItemStack s){return s!=null&&!s.isEmpty()&&s.hasTag()&&s.getTag().getBoolean("GunnerArenaKnife");}
}
