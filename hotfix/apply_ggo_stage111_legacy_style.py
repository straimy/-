#!/usr/bin/env python3
from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
UI = ROOT / "client-ui/src/main/java/arena/client/ui"
if not UI.is_dir():
    raise SystemExit("Stage111 legacy UI directory missing")

(UI / "UiTheme.java").write_text(r'''package arena.client.ui;

/** Retained server-data screens styled as part of the first-party GunGloryOnline shell. */
public final class UiTheme {
    private UiTheme() {}
    public static final int BACKDROP = 0xF205070B;
    public static final int PANEL = 0xF20B0F15;
    public static final int PANEL_SOFT = 0xE00F141C;
    public static final int PANEL_BORDER = 0xA33A4657;
    public static final int TEXT = 0xFFF2F4F7;
    public static final int MUTED = 0xFF9AA5B5;
    public static final int DIM = 0xFF687486;
    public static final int ACCENT = 0xFFD54855;
    public static final int ACCENT_2 = 0xFFE46A76;
    public static final int PINK = 0xFFE46A76;
    public static final int BLUE = 0xFF8998AA;
    public static final int GREEN = 0xFF72C391;
    public static final int GOLD = 0xFFE2B96A;
    public static final int BUTTON = 0xEC111720;
    public static final int BUTTON_HOVER = 0xFF1B2430;
    public static final int HAIRLINE = 0x7A3A4657;
    public static final int GLOW = 0x42D54855;
}
''', encoding="utf-8")

(UI / "ArenaNavigation.java").write_text(r'''package arena.client.ui;

import arena.client.shell.GgoShellScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Minimal bridge navigation for retained Shop/Profile/Skills data screens. */
final class ArenaNavigation {
    private ArenaNavigation() {}

    static void install(AbstractArenaScreen screen, UiRoute current) {
        UiLayout layout = UiLayout.of(screen.width, screen.height);
        int total = Math.min(620, Math.max(420, layout.panelWidth() - 60));
        int gap = 7;
        int w = (total - gap * 3) / 4;
        int x = layout.centerX() - total / 2;
        int y = 14;
        addDirect(screen, new UiLayout.Rect(x, y, w, 19), "GGO HUB",
            () -> new GgoShellScreen(GgoShellScreen.Page.HOME));
        x += w + gap;
        add(screen, current, UiRoute.SHOP, new UiLayout.Rect(x, y, w, 19), "STORE");
        x += w + gap;
        add(screen, current, UiRoute.PROFILE, new UiLayout.Rect(x, y, w, 19), "PROFILE");
        x += w + gap;
        add(screen, current, UiRoute.SKILLS, new UiLayout.Rect(x, y, w, 19), "SKILLS");
    }

    private static void add(AbstractArenaScreen screen, UiRoute current, UiRoute target,
                            UiLayout.Rect rect, String text) {
        ArenaButton button = new ArenaButton(rect, Component.literal(text), ignored -> navigate(target));
        button.active = current != target;
        screen.addArenaButton(button);
    }

    private static void addDirect(AbstractArenaScreen screen, UiLayout.Rect rect, String text,
                                  java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> factory) {
        ArenaButton button = new ArenaButton(rect, Component.literal(text),
            ignored -> Minecraft.getInstance().setScreen(factory.get()));
        screen.addArenaButton(button);
    }

    static void navigate(UiRoute target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || target == null) return;
        switch (target) {
            case MAIN -> mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.HOME));
            case SHOP -> mc.setScreen(new ShopScreen());
            case PROFILE -> mc.setScreen(new ProfileScreen());
            case SKILLS -> mc.setScreen(new SkillsScreen());
        }
    }
}
''', encoding="utf-8")

print("Applied GGO Stage111 retained-screen merge")
print(" - old cyan/purple palette -> graphite/red GGO palette")
print(" - six legacy tabs -> GGO HUB / STORE / PROFILE / SKILLS")
print(" - real server-backed Shop/Profile/Skills logic retained")
