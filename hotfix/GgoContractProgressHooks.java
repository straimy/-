package arena.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Authoritative gameplay hooks for contract progression. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoContractProgressHooks {
    private static final double DISTANCE_DRILL_METERS=24.0D;
    private GgoContractProgressHooks(){}

    @SubscribeEvent
    public static void death(LivingDeathEvent event){
        LivingEntity victim=event.getEntity();
        if(!(event.getSource().getEntity() instanceof ServerPlayer player))return;
        if(victim==player)return;

        // FIELD TEST: real combat eliminations only; passive animals do not count.
        if(victim instanceof Mob || victim instanceof ServerPlayer){
            GgoContractService.addProgress(player,"field_test",1);
        }

        // DISTANCE DRILL: same authoritative kill, but only at meaningful range.
        if(player.distanceTo(victim)>=DISTANCE_DRILL_METERS){
            GgoContractService.addProgress(player,"distance_drill",1);
        }
    }
}
