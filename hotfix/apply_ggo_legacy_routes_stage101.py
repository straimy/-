from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
UI = ROOT / "client-ui/src/main/java/arena/client/ui"
SHELL = ROOT / "client-ui/src/main/java/arena/client/shell/GgoShellScreen.java"
HOOKS = ROOT / "client-ui/src/main/java/arena/client/shell/GgoShellHooks.java"
UI.mkdir(parents=True, exist_ok=True)

# Stage101 routing contract: retain the mature server-driven Shop/Profile/Skills screens
# while replacing only their legacy KVICloud/Gunner Arena navigation shell.
bridge = r'''package arena.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Public bridge from the Stage101 shell into retained server-driven GGO screens. */
public final class GgoLegacyUiBridge {
    private GgoLegacyUiBridge() {}

    public static Screen shop() { return new ShopScreen(); }
    public static Screen profile() { return new ProfileScreen(); }
    public static Screen skills() { return new SkillsScreen(); }
    public static Screen legacyMain() { return new MainArenaScreen(); }

    public static void openShop() { Minecraft.getInstance().setScreen(shop()); }
    public static void openProfile() { Minecraft.getInstance().setScreen(profile()); }
    public static void openSkills() { Minecraft.getInstance().setScreen(skills()); }
}
'''
(UI / "GgoLegacyUiBridge.java").write_text(bridge)

# Keep the server-driven route protocol, but make /menu (route MAIN/default) land in the new GGO Hub.
opener = r'''package arena.client.ui;

import arena.client.net.ArenaClientNetwork;
import arena.client.shell.GgoShellScreen;
import net.minecraft.client.Minecraft;

public final class ClientUiOpener {
    private ClientUiOpener() {}

    public static void open(int route) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ArenaClientNetwork.requestSnapshot();
        switch (route) {
            case 1 -> {
                ArenaClientNetwork.requestCatalog();
                mc.setScreen(new ShopScreen());
            }
            case 2 -> mc.setScreen(new ProfileScreen());
            case 3 -> {
                ArenaClientNetwork.requestSkillTree();
                mc.setScreen(new SkillsScreen());
            }
            default -> mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.HOME));
        }
    }
}
'''
(UI / "ClientUiOpener.java").write_text(opener)

s = SHELL.read_text()
s = s.replace(
    'import net.minecraft.client.Minecraft;\n',
    'import net.minecraft.client.Minecraft;\nimport arena.client.ui.GgoLegacyUiBridge;\n',
    1,
)
s = s.replace(
    'addRenderableWidget(Button.builder(Component.literal("OPEN MAIN STORE"), b -> runClientCommand("shop")).bounds(x, y, w, 28).build());',
    'addRenderableWidget(Button.builder(Component.literal("OPEN MAIN STORE"), b -> GgoLegacyUiBridge.openShop()).bounds(x, y, w, 28).build());',
)
s = s.replace(
    'addRenderableWidget(Button.builder(Component.literal("PROFILE DETAILS"), b -> runClientCommand("profile")).bounds(x, y, w, 26).build());',
    'addRenderableWidget(Button.builder(Component.literal("PROFILE DETAILS"), b -> GgoLegacyUiBridge.openProfile()).bounds(x, y, w, 26).build());',
)
s = s.replace(
    'addRenderableWidget(Button.builder(Component.literal("OPEN SKILLS / PROGRESSION"), b -> runClientCommand("skills")).bounds(x, y, w, 28).build());',
    'addRenderableWidget(Button.builder(Component.literal("OPEN SKILLS / PROGRESSION"), b -> GgoLegacyUiBridge.openSkills()).bounds(x, y, w, 28).build());',
)
SHELL.write_text(s)

# M is now literally the server /menu route. J keeps Activities as the separate local shortcut.
h = HOOKS.read_text()
h = h.replace(
    'if (event.getKey() == GLFW.GLFW_KEY_M) {\n            mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.HOME));',
    'if (event.getKey() == GLFW.GLFW_KEY_M) {\n            mc.player.connection.sendCommand("menu");',
)
HOOKS.write_text(h)

assert 'GgoLegacyUiBridge.openShop()' in s
assert 'GgoLegacyUiBridge.openProfile()' in s
assert 'GgoLegacyUiBridge.openSkills()' in s
assert 'sendCommand("menu")' in h
assert 'new GgoShellScreen(GgoShellScreen.Page.HOME)' in opener
print('Stage101 retained UI routes bridged')
print(' - M sends /menu')
print(' - /menu route MAIN opens canonical GGO Hub')
print(' - Shop/Profile/Skills remain real server-driven screens')
