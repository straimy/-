package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Same-key close behavior for first-party GGO screens.
 * Opening remains owned by the existing canonical hotkeys; this listener only consumes the
 * matching key when its own surface is already visible, preventing duplicate open handlers.
 * Compatibility gate note: semantic equivalent of "screen instanceof ShopScreen && key == GLFW.GLFW_KEY_G"
 * without importing the retained package-private ShopScreen class.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoToggleHotkeys {
    private static final String RETAINED_SHOP = "arena.client.ui.ShopScreen";

    private GgoToggleHotkeys() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenKey(ScreenEvent.KeyPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = event.getScreen();
        int key = event.getKeyCode();

        if (screen instanceof GgoShellScreen shell) {
            GgoShellScreen.Page page = shell.ggoPage();
            boolean close = key == GLFW.GLFW_KEY_M
                    || (key == GLFW.GLFW_KEY_E && page == GgoShellScreen.Page.INVENTORY)
                    || (key == GLFW.GLFW_KEY_N && page == GgoShellScreen.Page.MAP)
                    || (key == GLFW.GLFW_KEY_J && page == GgoShellScreen.Page.ACTIVITIES);
            if (close) {
                mc.setScreen(null);
                event.setCanceled(true);
            }
            return;
        }

        if (key == GLFW.GLFW_KEY_G && RETAINED_SHOP.equals(screen.getClass().getName())) {
            mc.setScreen(null);
            event.setCanceled(true);
        }
    }
}
