package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-authoritative tactical knife. No ammo, no JEG dependency, reliable arena melee. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ArenaKnifeDamageFix {
    private ArenaKnifeDamageFix(){}

    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)
    public static void playerAttack(AttackEntityEvent e){
        if(!(e.getEntity() instanceof ServerPlayer attacker)||!(e.getTarget() instanceof ServerPlayer victim))return;
        if(!isArenaKnife(attacker.getMainHandItem())||!allowed(attacker,victim))return;

        // GGO owns knife hits completely. This prevents other gun/protection handlers from swallowing the hit.
        e.setCanceled(true);
        if(attacker.distanceToSqr(victim)>12.25D)return; // ~3.5 blocks, close melee only.
        if(attacker.getAttackStrengthScale(.25f)<.55f)return;

        victim.invulnerableTime=0;
        victim.setLastHurtByPlayer(attacker);
        float damage=5.5f;
        float before=victim.getHealth();
        boolean hurt=victim.hurt(attacker.damageSources().playerAttack(attacker),damage);
        if(!hurt&&victim.isAlive()&&victim.getHealth()>=before-.01f){
            float after=Math.max(0f,before-damage);
            victim.setHealth(after);victim.hurtMarked=true;
            if(after<=0f)victim.kill();
        }
        double dx=victim.getX()-attacker.getX(),dz=victim.getZ()-attacker.getZ();
        if(dx*dx+dz*dz>.0001D)victim.knockback(.34D,-dx,-dz);
        attacker.resetAttackStrengthTicker();
    }

    private static boolean allowed(ServerPlayer a,ServerPlayer v){
        ArenaRuntime r=GunnerArenaMod.RUNTIME;
        if(r==null||a==v||!a.isAlive()||!v.isAlive())return false;
        if(!r.auth().isAuthenticated(a)||!r.auth().isAuthenticated(v))return false;
        if(r.players().session(a).state()!=ArenaPlayerState.ALIVE||r.players().session(v).state()!=ArenaPlayerState.ALIVE)return false;
        return !r.safeRegions().isSafe(a)&&!r.safeRegions().isSafe(v);
    }
    public static boolean isArenaKnife(ItemStack s){return s!=null&&!s.isEmpty()&&s.hasTag()&&s.getTag().getBoolean("GunnerArenaKnife");}
}
