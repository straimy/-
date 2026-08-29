#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
SHELL_DIR = ROOT / "client-ui/src/main/java/arena/client/shell"
UI_DIR = ROOT / "client-ui/src/main/java/arena/client/ui"
FRONTEND = SHELL_DIR / "GgoFrontEndScreen.java"
HOOKS = SHELL_DIR / "GgoShellHooks.java"
SHELL = SHELL_DIR / "GgoShellScreen.java"
OPENER = UI_DIR / "ClientUiOpener.java"

for required in [FRONTEND, HOOKS, SHELL, OPENER, UI_DIR / "GgoLegacyUiBridge.java"]:
    if not required.is_file():
        raise SystemExit(f"Stage112 missing generated source: {required}")

# Stage111 accidentally turned launcher PLAY into an immediate server connection. The launcher
# launches the GGO engine only; the player explicitly chooses PLAY ONLINE on this first-party page.
frontend = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

/** Canonical GunGloryOnline entry surface. Launcher PLAY starts GGO; online entry is explicit here. */
public final class GgoFrontEndScreen extends Screen {
    private static final String OFFICIAL_SERVER = "play.kvicloud.ru:24842";
    private static final int BG = 0xFF05070B;
    private static final int PANEL = 0xE80B0E14;
    private static final int ACCENT = 0xFFC83245;
    private static final int TEXT = 0xFFF2F4F7;
    private static final int MUTED = 0xFF8792A3;
    private static final int READY = 0xFF72C391;
    private static final int WARN = 0xFFE26A73;

    public GgoFrontEndScreen() {
        super(Component.literal("GunGloryOnline"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        boolean connected = mc.getConnection() != null && mc.player != null;
        boolean officialLaunch = GgoLaunchTicketClient.isOfficialLaunch();
        boolean canStartOnline = GgoLaunchTicketClient.canStartOnline();

        int w = Math.min(370, Math.max(290, width / 3));
        int x = (width - w) / 2;
        int y = Math.max(220, height / 2 - 34);

        if (connected) {
            addRenderableWidget(Button.builder(Component.literal("CONTINUE"), b -> mc.setScreen(null))
                .bounds(x, y, w, 34).build());
        } else if (officialLaunch && canStartOnline) {
            addRenderableWidget(Button.builder(Component.literal("PLAY ONLINE"), b -> connectOfficial())
                .bounds(x, y, w, 34).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("RETURN TO LAUNCHER"), b -> mc.stop())
                .bounds(x, y, w, 34).build());
        }

        Button practice = Button.builder(Component.literal("PRACTICE · COMING SOON"), b -> {})
            .bounds(x, y + 46, w, 28).build();
        practice.active = false;
        addRenderableWidget(practice);

        addRenderableWidget(Button.builder(Component.literal("SETTINGS"), b -> mc.setScreen(new GgoSettingsScreen(this)))
            .bounds(x, y + 86, w, 28).build());
        addRenderableWidget(Button.builder(Component.literal("EXIT"), b -> mc.stop())
            .bounds(x, y + 126, w, 28).build());
    }

    private void connectOfficial() {
        Minecraft mc = Minecraft.getInstance();
        if (!GgoLaunchTicketClient.isOfficialLaunch() || !GgoLaunchTicketClient.canStartOnline()) {
            mc.setScreen(new GgoFrontEndScreen());
            return;
        }
        ServerData server = new ServerData("GunGloryOnline", OFFICIAL_SERVER, false);
        ConnectScreen.startConnecting(this, mc, ServerAddress.parseString(OFFICIAL_SERVER), server, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        boolean connected = mc.getConnection() != null && mc.player != null;
        boolean officialLaunch = GgoLaunchTicketClient.isOfficialLaunch();
        boolean canStartOnline = GgoLaunchTicketClient.canStartOnline();
        String account = mc.getUser() == null ? "GGO PLAYER" : mc.getUser().getName();

        g.fill(0, 0, width, height, BG);
        g.fill(0, 0, width, 3, ACCENT);

        int center = width / 2;
        int top = Math.max(66, height / 8);
        int cardW = Math.min(520, width - 70);
        int cardX = center - cardW / 2;
        int cardTop = top + 70;
        int cardBottom = Math.min(height - 46, cardTop + 390);
        g.fill(cardX, cardTop, cardX + cardW, cardBottom, PANEL);
        g.fill(cardX, cardTop, cardX + 3, cardBottom, ACCENT);

        g.drawCenteredString(font, Component.literal("GUN GLORY ONLINE"), center, top, TEXT);
        g.drawCenteredString(font, Component.literal("CLOSED BETA"), center, top + 22, ACCENT);
        g.drawCenteredString(font, Component.literal(account.toUpperCase()), center, cardTop + 34, TEXT);

        String status;
        int statusColor;
        if (connected) {
            status = "ONLINE SESSION ACTIVE";
            statusColor = READY;
        } else if (officialLaunch && canStartOnline) {
            status = "READY FOR OFFICIAL ONLINE";
            statusColor = READY;
        } else {
            status = "LAUNCHER SESSION REQUIRED";
            statusColor = WARN;
        }
        g.drawCenteredString(font, Component.literal(status), center, cardTop + 58, statusColor);

        if (!connected && (!officialLaunch || !canStartOnline)) {
            g.drawCenteredString(font, Component.literal("Return to the GGO Launcher and press PLAY."), center, cardTop + 80, MUTED);
        }

        g.drawCenteredString(font, Component.literal("Official server · " + OFFICIAL_SERVER), center, cardBottom - 50, MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
'''
FRONTEND.write_text(frontend, encoding="utf-8")

# M remains a manual local GGO Hub action. G is restored as the direct combat-store shortcut.
hooks = HOOKS.read_text(encoding="utf-8")
if "import arena.client.ui.GgoLegacyUiBridge;" not in hooks:
    hooks = hooks.replace(
        "package arena.client.shell;\n",
        "package arena.client.shell;\n\nimport arena.client.ui.GgoLegacyUiBridge;\n",
        1,
    )
if "GLFW.GLFW_KEY_G" not in hooks:
    marker = '''        if (event.getKey() == GLFW.GLFW_KEY_M) {\n            mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.HOME));\n'''
    replacement = marker + '''        } else if (event.getKey() == GLFW.GLFW_KEY_G) {\n            GgoLegacyUiBridge.openShop();\n'''
    if marker not in hooks:
        raise SystemExit("Stage112 could not locate manual M hub handler")
    hooks = hooks.replace(marker, replacement, 1)
HOOKS.write_text(hooks, encoding="utf-8")

# Server MAIN is a synchronization route, not permission to pop a menu over gameplay.
opener = OPENER.read_text(encoding="utf-8")
opener = opener.replace(
    "default -> mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.HOME));",
    "default -> { /* MAIN snapshot only; UI opens exclusively by explicit player action. */ }",
)
OPENER.write_text(opener, encoding="utf-8")

# Make the direct G shortcut discoverable without changing TAB/J/N behavior.
shell = SHELL.read_text(encoding="utf-8")
shell = shell.replace(
    'M  GGO HUB     N  NAVIGATION     J  ACTIVITIES     ESC  BACK',
    'M  GGO HUB     G  STORE     N  NAVIGATION     J  ACTIVITIES     ESC  BACK',
)
SHELL.write_text(shell, encoding="utf-8")

# Final contract: no auto-connect, no unsolicited MAIN popup, manual M, direct real Shop on G.
frontend = FRONTEND.read_text(encoding="utf-8")
hooks = HOOKS.read_text(encoding="utf-8")
opener = OPENER.read_text(encoding="utf-8")
shell = SHELL.read_text(encoding="utf-8")

checks = {
    "explicit play online": 'Component.literal("PLAY ONLINE")' in frontend,
    "no frontend auto connect": "mc.execute(this::connectOfficial)" not in frontend and "autoConnectScheduled" not in frontend,
    "manual M hub": "GLFW.GLFW_KEY_M" in hooks and "new GgoShellScreen(GgoShellScreen.Page.HOME)" in hooks,
    "G direct weapon store": "GLFW.GLFW_KEY_G" in hooks and "GgoLegacyUiBridge.openShop();" in hooks,
    "MAIN route passive": "default -> { /* MAIN snapshot only; UI opens exclusively by explicit player action. */ }" in opener,
    "no MAIN forced hub": "default -> mc.setScreen(new GgoShellScreen(GgoShellScreen.Page.HOME))" not in opener,
    "store hint": "G  STORE" in shell,
}
for label, ok in checks.items():
    if not ok:
        raise SystemExit(f"Stage112 contract failed: {label}")

print("Applied GGO Stage112 menu contract")
print(" - launcher PLAY opens GGO frontend; PLAY ONLINE is explicit")
print(" - server MAIN route never auto-opens Hub")
print(" - M opens Hub manually")
print(" - G opens retained real weapon ShopScreen directly")
print(" - TAB/J/N behavior is preserved from Stage111")
