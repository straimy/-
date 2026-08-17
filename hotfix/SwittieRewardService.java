package arena.forge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Rewards the player for killing the original persistent Swittie Fox bot. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SwittieRewardService {
    private static final String TAG="gunglory_swittie_fox";
    private SwittieRewardService(){}

    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent e){
        if(!e.getEntity().getTags().contains(TAG)||e.getEntity().getTags().contains("ggo_swittie_extra"))return;
        if(!(e.getSource().getEntity() instanceof ServerPlayer killer))return;
        int credits=300,crystals=2;
        boolean c=AdminToolsCommands.grantCredits(killer,credits),r=AdminToolsCommands.grantCrystals(killer,crystals);
        killer.sendSystemMessage(Component.literal("✦ Свитти Фокс повержена: +$"+credits+"  +"+crystals+"◆").withStyle(c||r?ChatFormatting.GOLD:ChatFormatting.GRAY));
    }
}
