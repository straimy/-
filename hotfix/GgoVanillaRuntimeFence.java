package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Production UX fence for raw engine screens.
 *
 * The fence is deliberately active only for an official GGO launcher session. Developers who
 * start the client outside the launcher retain vanilla navigation for diagnostics, while normal
 * players can never fall through from GGO into Minecraft's title/options/world/server surfaces.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoVanillaRuntimeFence {
    private GgoVanillaRuntimeFence() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        var screen = event.getNewScreen();
        if (screen == null) return;

        if (screen instanceof GgoShellScreen
                || screen instanceof GgoFrontEndScreen
                || screen instanceof GgoRespawnScreen
                || screen instanceof GgoSettingsScreen
                || screen instanceof GgoEntryDisconnectedScreen
                || screen instanceof GgoTrainingScreen) {
            return;
        }

        if (!GgoLaunchTicketClient.isOfficialLaunch()) return;

        Minecraft mc = Minecraft.getInstance();

        if (screen instanceof DeathScreen) {
            event.setNewScreen(new GgoRespawnScreen());
            return;
        }

        if (screen instanceof PauseScreen) {
            event.setNewScreen(new GgoShellScreen(GgoShellScreen.Page.PAUSE));
            return;
        }

        if (screen instanceof AdvancementsScreen) {
            event.setNewScreen(new GgoShellScreen(GgoShellScreen.Page.ACTIVITIES));
            return;
        }

        if (screen instanceof OptionsScreen) {
            event.setNewScreen(new GgoSettingsScreen(
                mc.player != null ? new GgoShellScreen(GgoShellScreen.Page.PAUSE) : new GgoFrontEndScreen()
            ));
            return;
        }

        // Official GGO has no public vanilla title, local-world browser or arbitrary server list.
        // The Java/Forge client remains an internal engine implementation only.
        if (screen instanceof TitleScreen
                || screen instanceof SelectWorldScreen
                || screen instanceof JoinMultiplayerScreen) {
            event.setNewScreen(new GgoFrontEndScreen());
            return;
        }

        // InventoryScreen already has its own GGO redirect in the shell hooks. Other vanilla
        // container screens should not leak crafting/furnace/chest UX into normal GGO play.
        if (screen instanceof AbstractContainerScreen<?> && !(screen instanceof InventoryScreen)
                && !(screen instanceof CreativeModeInventoryScreen)
                && screen.getClass().getName().startsWith("net.minecraft.client.gui.screens.inventory.")) {
            if (mc.player != null && mc.gameMode != null && mc.gameMode.getPlayerMode().isCreative()) return;
            event.setNewScreen(new GgoShellScreen(GgoShellScreen.Page.INVENTORY));
        }
    }
}
