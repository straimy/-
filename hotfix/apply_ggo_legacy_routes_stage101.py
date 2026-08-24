from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
UI = ROOT / "client-ui/src/main/java/arena/client/ui"
SHELL = ROOT / "client-ui/src/main/java/arena/client/shell/GgoShellScreen.java"
UI.mkdir(parents=True, exist_ok=True)

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
# The HOME buttons enter the new hub pages first; the retained screens are then opened from those pages.
# This keeps server-driven snapshots, purchases and progression intact while removing KVICloud/GUNNER ARENA
# from the primary navigation surface.
SHELL.write_text(s)

assert 'GgoLegacyUiBridge.openShop()' in s
assert 'GgoLegacyUiBridge.openProfile()' in s
assert 'GgoLegacyUiBridge.openSkills()' in s
print('Stage101 retained UI routes bridged')
