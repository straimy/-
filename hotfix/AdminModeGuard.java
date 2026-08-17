package arena.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps explicit admin /gm 1 and /gm 3 modes from being overwritten by arena/session guards. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class AdminModeGuard {
    private AdminModeGuard(){}

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||e.player.level().isClientSide||!(e.player instanceof ServerPlayer p)||!p.hasPermissions(2))return;
        if(p.getTags().contains(AdminToolsCommands.ADMIN_BUILD_TAG)){
            if(p.gameMode.getGameModeForPlayer()!=GameType.CREATIVE)p.setGameMode(GameType.CREATIVE);
            return;
        }
        if(p.getTags().contains(AdminToolsCommands.ADMIN_SPECTATOR_TAG)&&p.gameMode.getGameModeForPlayer()!=GameType.SPECTATOR)p.setGameMode(GameType.SPECTATOR);
    }
}
