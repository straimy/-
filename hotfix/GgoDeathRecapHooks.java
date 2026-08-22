package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Captures only facts the server actually knows; no guessed hit-location/headshot data. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoDeathRecapHooks {
    private static final Map<UUID,LastHit> LAST_HIT=new HashMap<>();
    private GgoDeathRecapHooks(){}

    @SubscribeEvent public static void hurt(LivingHurtEvent event){
        if(!(event.getEntity() instanceof ServerPlayer victim))return;
        Object attacker=event.getSource().getEntity();
        UUID attackerId=attacker instanceof net.minecraft.world.entity.Entity e?e.getUUID():null;
        LAST_HIT.put(victim.getUUID(),new LastHit(attackerId,event.getSource().getMsgId(),Math.max(0f,event.getAmount())));
    }

    @SubscribeEvent public static void death(LivingDeathEvent event){
        if(!(event.getEntity() instanceof ServerPlayer victim))return;
        ArenaRuntime runtime=GunnerArenaMod.RUNTIME;
        if(runtime==null||!runtime.auth().isAuthenticated(victim))return;
        var source=event.getSource();
        var attacker=source.getEntity();
        String killer="ENVIRONMENT",weapon="UNKNOWN";
        float distance=-1f,killerHealth=-1f,killerMax=-1f;
        UUID attackerId=null;
        if(attacker!=null){
            attackerId=attacker.getUUID();
            killer=attacker instanceof ServerPlayer p?p.getGameProfile().getName():attacker.getDisplayName().getString();
            distance=(float)attacker.distanceTo(victim);
            if(attacker instanceof LivingEntity living){
                ItemStack held=living.getMainHandItem();
                if(held!=null&&!held.isEmpty())weapon=held.getHoverName().getString();
                killerHealth=living.getHealth();killerMax=living.getMaxHealth();
            }
        }else if(source.getDirectEntity()!=null){
            killer=source.getDirectEntity().getDisplayName().getString();
            distance=(float)source.getDirectEntity().distanceTo(victim);
        }
        LastHit last=LAST_HIT.remove(victim.getUUID());
        float finalDamage=0f;
        if(last!=null&&(last.attackerId==null||last.attackerId.equals(attackerId))&&last.source.equals(source.getMsgId()))finalDamage=last.damage;
        String sector=sector(victim.getBlockX(),victim.getBlockZ());
        GgoDeathRecapNetwork.send(victim,new GgoDeathRecapNetwork.Snapshot(killer,weapon,source.getMsgId(),sector,distance,finalDamage,killerHealth,killerMax,runtime.serverTick()));
    }

    private static String sector(int x,int z){int sx=Math.floorDiv(x,256),sz=Math.floorDiv(z,256);char col=(char)('A'+Math.floorMod(sx,26));return col+"-"+Math.abs(sz);}
    private record LastHit(UUID attackerId,String source,float damage){}
}
