package arena.client.ui;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Replaces vanilla entry/escape/error screens with GGO-owned screens and hosts lightweight runtime services. */
@Mod.EventBusSubscriber(modid=GunnerArenaUiMod.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoVanillaScreenGuard {
    private GgoVanillaScreenGuard() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof TitleScreen && !(event.getNewScreen() instanceof GgoTitleScreen)) {
            event.setNewScreen(new GgoTitleScreen());
            return;
        }
        if (event.getNewScreen() instanceof PauseScreen && !(event.getNewScreen() instanceof GgoPauseScreen)) {
            event.setNewScreen(new GgoPauseScreen());
            return;
        }
        if (event.getNewScreen() instanceof DisconnectedScreen) {
            event.setNewScreen(new GgoDisconnectedScreen(Component.literal("The server connection was closed")));
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) GgoSkinRuntime.tick();
    }
}
