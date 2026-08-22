package arena.forge;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps authored GGO maps immutable while preserving combat/entity damage. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoMapIntegrityGuard {
    private GgoMapIntegrityGuard(){}

    /** Explosions still affect entities, but never carve Minecraft blocks out of the map. */
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event){
        if(event.getLevel().isClientSide())return;
        event.getAffectedBlocks().clear();
    }

    /** Disable vanilla systems that mutate maps/populate them or leak Minecraft death chat. */
    @SubscribeEvent public static void levelLoad(LevelEvent.Load event){
        if(!(event.getLevel() instanceof ServerLevel level))return;
        var server=level.getServer();
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_DOFIRETICK).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        level.getGameRules().getRule(GameRules.RULE_SHOWDEATHMESSAGES).set(false,server);
    }
}
