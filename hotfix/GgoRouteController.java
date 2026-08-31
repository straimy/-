package arena.client.shell;

import arena.client.ui.GgoLegacyUiBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Canonical first-party navigation controller.
 *
 * Every visible GGO button, gameplay hotkey and intercepted engine screen enters through this
 * class so the interaction paths cannot drift into different surfaces. No method here opens a
 * vanilla Minecraft/Forge navigation surface.
 */
public final class GgoRouteController {
    private GgoRouteController() {}

    /** Factory used by ScreenEvent.Opening replacements (E inventory / ESC pause). */
    public static GgoShellScreen screen(GgoShellScreen.Page page) {
        return new GgoShellScreen(page);
    }

    public static void open(GgoShellScreen.Page page) {
        Minecraft.getInstance().setScreen(screen(page));
    }

    public static void hub() { open(GgoShellScreen.Page.HOME); }
    public static void activities() { open(GgoShellScreen.Page.ACTIVITIES); }
    public static void loadout() { open(GgoShellScreen.Page.INVENTORY); }
    public static void contracts() { open(GgoShellScreen.Page.CONTRACTS); }
    public static void social() { open(GgoShellScreen.Page.SOCIAL); }
    public static void profileHub() { open(GgoShellScreen.Page.PROFILE); }
    public static void season() { open(GgoShellScreen.Page.SEASON); }
    public static void navigation() { open(GgoShellScreen.Page.MAP); }
    public static void pause() { open(GgoShellScreen.Page.PAUSE); }

    public static void store() { GgoLegacyUiBridge.openShop(); }
    public static void profile() { GgoLegacyUiBridge.openProfile(); }
    public static void skills() { GgoLegacyUiBridge.openSkills(); }

    public static void settings(Screen parent) {
        Minecraft.getInstance().setScreen(new GgoSettingsScreen(parent));
    }

    public static void training() {
        runServerAction("play");
    }

    /**
     * Battle Royale is intentionally not launched before its map/runtime stage is authoritative.
     * The button is still interactive and lands on the first-party Activities surface instead of
     * being a dead Minecraft-style disabled widget.
     */
    public static void battleRoyale() {
        activities();
    }

    /** Events are browseable now even while the first seasonal operation remains coming soon. */
    public static void events() {
        season();
    }

    public static void friends() {
        runServerAction("friends");
    }

    public static void clans() {
        runServerAction("clan");
    }

    private static void runServerAction(String command) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;
        mc.setScreen(null);
        mc.player.connection.sendCommand(command);
    }
}
