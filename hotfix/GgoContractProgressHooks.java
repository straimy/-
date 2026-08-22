package arena.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Authoritative gameplay hooks for contract progression. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoContractProgressHooks {
    private static final ResourceLocation JEG_BULLET=new ResourceLocation("jeg","bullet");
    private GgoContractProgressHooks(){}

    @SubscribeEvent
    public static void death(LivingDeathEvent event){
        LivingEntity victim=event.getEntity();
        DamageSource source=event.getSource();
        if(!(source.getEntity() instanceof ServerPlayer player)||victim==player)return;

        // Both contracts require an authoritative JEG bullet kill against a hostile
        // combat target or another player. Passive mobs, melee, fire and environment
        // damage therefore never produce progress.
        if(!isCombatTarget(victim)||!isJegBullet(source))return;

        GgoContractService.addProgress(player,"field_test",1);
        if(player.distanceTo(victim)>=GgoContractBalance.distanceDrillMeters()){
            GgoContractService.addProgress(player,"distance_drill",1);
        }
    }

    static boolean isCombatTarget(LivingEntity victim){
        return victim instanceof Enemy||victim instanceof ServerPlayer;
    }

    static boolean isJegBullet(DamageSource source){
        if(source==null)return false;
        return source.typeHolder().unwrapKey()
                .map(key->JEG_BULLET.equals(key.location()))
                .orElse(false);
    }
}
