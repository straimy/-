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
 * Production UX firewall for raw engine screens.
 *
 * It is deliberately active only for an official launcher-owned GGO process. Development launches
 * keep the normal engine navigation for diagnostics. In production, Minecraft/Forge remains an
 * implementation detail: title/world/server browsers, Realms, Forge mod lists, resource-pack
 * selectors and the whole vanilla options family cannot become player-facing surfaces.
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

        String screenClass = screen.getClass().getName();

        // OptionsScreen is only the top-level door. Vanilla can also open video/audio/controls,
        // accessibility, language, telemetry and resource-pack children directly. Keep the whole
        // family behind first-party GGO Settings instead of whack-a-mole redirects per button.
        if (screen instanceof OptionsScreen || isEngineSettingsSurface(screenClass)) {
            event.setNewScreen(new GgoSettingsScreen(
                mc.player != null ? new GgoShellScreen(GgoShellScreen.Page.PAUSE) : new GgoFrontEndScreen()
            ));
            return;
        }

        // Official GGO has no public Minecraft title, local-world browser, arbitrary server list,
        // Realms browser or Forge Mods screen. These classes may still exist in the engine, but
        // an official launcher session can never navigate to them.
        if (screen instanceof TitleScreen
                || screen instanceof SelectWorldScreen
                || screen instanceof JoinMultiplayerScreen
                || isForbiddenNavigationSurface(screenClass)) {
            event.setNewScreen(new GgoFrontEndScreen());
            return;
        }

        // InventoryScreen already has its own GGO redirect in the shell hooks. Other vanilla
        // container screens should not leak crafting/furnace/chest UX into normal GGO play.
        // Creative remains an explicit exception for OP-only /admin 1 builders.
        if (screen instanceof AbstractContainerScreen<?> && !(screen instanceof InventoryScreen)
                && !(screen instanceof CreativeModeInventoryScreen)
                && screenClass.startsWith("net.minecraft.client.gui.screens.inventory.")) {
            if (mc.player != null && mc.gameMode != null && mc.gameMode.getPlayerMode().isCreative()) return;
            event.setNewScreen(new GgoShellScreen(GgoShellScreen.Page.INVENTORY));
        }
    }

    private static boolean isEngineSettingsSurface(String className) {
        return className.startsWith("net.minecraft.client.gui.screens.options.")
                || className.equals("net.minecraft.client.gui.screens.LanguageSelectScreen")
                || className.equals("net.minecraft.client.gui.screens.PackSelectionScreen")
                || className.startsWith("net.minecraft.client.gui.screens.telemetry.")
                || className.equals("net.minecraft.client.gui.screens.CreditsAndAttributionScreen");
    }

    private static boolean isForbiddenNavigationSurface(String className) {
        return className.startsWith("net.minecraft.client.gui.screens.worldselection.")
                || className.startsWith("net.minecraft.client.gui.screens.multiplayer.")
                || className.startsWith("net.minecraft.client.gui.screens.realms.")
                || className.equals("net.minecraftforge.client.gui.ModListScreen")
                || className.startsWith("net.minecraftforge.client.gui.ModListScreen$");
    }
}
