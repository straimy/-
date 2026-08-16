package arena.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Prevents custom GunGloryOnline screens/key handlers from surviving a server kick/disconnect. */
@Mod.EventBusSubscriber(modid="gunnerarena_ui",bus=Mod.EventBusSubscriber.Bus.FORGE,value=Dist.CLIENT)
public final class ClientDisconnectGuard {
    private ClientDisconnectGuard(){}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut e){
        Minecraft mc=Minecraft.getInstance();
        mc.execute(() -> {
            try {
                if(mc.options!=null){
                    mc.options.keyAttack.setDown(false);
                    mc.options.keyUse.setDown(false);
                }
                Screen s=mc.screen;
                if(isArenaScreen(s))mc.setScreen(null);
            }catch(Throwable ignored){
                // Disconnect/kick must never crash the client because an arena UI was still active.
            }
        });
    }

    private static boolean isArenaScreen(Screen s){
        return s instanceof MainArenaScreen
            || s instanceof ShopScreen
            || s instanceof MatchShopScreen
            || s instanceof LobbyShopScreen
            || s instanceof SkillsScreen;
    }
}
