package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Production UX fence for screens that still reveal raw Minecraft progression/survival UI.
 * GGO-owned screens remain untouched; creative inventory stays available to admin builders.
 * Music is intentionally not stopped here: the required GGO resource pack owns the music pool.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoVanillaRuntimeFence {
    private GgoVanillaRuntimeFence() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        var screen = event.getNewScreen();
        if (screen == null || screen instanceof GgoShellScreen || screen instanceof GgoRespawnScreen || screen instanceof GgoSettingsScreen) return;

        if (screen instanceof DeathScreen) {
            event.setNewScreen(new GgoRespawnScreen());
            return;
        }

        if (screen instanceof AdvancementsScreen) {
            event.setNewScreen(new GgoShellScreen(GgoShellScreen.Page.ACTIVITIES));
            return;
        }

        // InventoryScreen already has its own GGO redirect in GgoShellHooks. Other vanilla
        // container screens should not leak crafting/furnace/chest UX into normal GGO play.
        if (screen instanceof AbstractContainerScreen<?> && !(screen instanceof InventoryScreen)
                && !(screen instanceof CreativeModeInventoryScreen)
                && screen.getClass().getName().startsWith("net.minecraft.client.gui.screens.inventory.")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.gameMode != null && mc.gameMode.getPlayerMode().isCreative()) return;
            event.setNewScreen(new GgoShellScreen(GgoShellScreen.Page.INVENTORY));
        }
    }
}
