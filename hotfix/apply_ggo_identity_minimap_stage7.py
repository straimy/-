from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
CLIENT = ROOT / "client-ui"
JAVA = CLIENT / "src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

identity = r'''package arena.client.shell;

public final class GgoAccountContext {
    private static final String NAME = sanitize(System.getProperty("ggo.account.name", ""));
    private static final String ACCOUNT_ID = sanitize(System.getProperty("ggo.account.id", ""));
    private static final boolean AUTHENTICATED = Boolean.parseBoolean(System.getProperty("ggo.account.authenticated", "false"));

    private GgoAccountContext() {}

    public static boolean onlineReady() {
        return AUTHENTICATED && !NAME.isBlank() && !ACCOUNT_ID.isBlank();
    }

    public static String displayName() {
        return NAME.isBlank() ? "GGO ACCOUNT REQUIRED" : NAME;
    }

    public static String accountIdMasked() {
        if (ACCOUNT_ID.isBlank()) return "—";
        if (ACCOUNT_ID.length() <= 8) return ACCOUNT_ID;
        return ACCOUNT_ID.substring(0, 4) + "…" + ACCOUNT_ID.substring(ACCOUNT_ID.length() - 4);
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        String clean = value.replaceAll("[\\r\\n\\t]", "").trim();
        return clean.length() > 64 ? clean.substring(0, 64) : clean;
    }
}
'''

frontend = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class GgoFrontEndScreen extends Screen {
    private String statusMessage = "";

    public GgoFrontEndScreen() {
        super(Component.literal("GunGloryOnline"));
    }

    @Override
    protected void init() {
        int x = 44;
        int y = Math.max(150, this.height / 2 - 70);
        int w = 230;

        Button enter = Button.builder(Component.literal("ENTER GGO"), b -> reconnectOfficial())
                .bounds(x, y, w, 30).build();
        enter.active = GgoAccountContext.onlineReady();
        addRenderableWidget(enter);

        addRenderableWidget(Button.builder(Component.literal("ACTIVITIES"), b -> open(GgoShellScreen.Page.ACTIVITIES))
                .bounds(x, y + 38, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("TRAINING"), b -> open(GgoShellScreen.Page.ACTIVITIES))
                .bounds(x, y + 70, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("SETTINGS"), b -> Minecraft.getInstance().setScreen(new GgoSettingsScreen(this)))
                .bounds(x, y + 102, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("EXIT"), b -> Minecraft.getInstance().stop())
                .bounds(x, y + 134, w, 26).build());
    }

    private void open(GgoShellScreen.Page page) {
        Minecraft.getInstance().setScreen(new GgoShellScreen(page));
    }

    private void reconnectOfficial() {
        if (!GgoAccountContext.onlineReady()) {
            statusMessage = "SIGN IN THROUGH GGO LAUNCHER";
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        var server = new net.minecraft.client.multiplayer.ServerData(
                "GunGloryOnline",
                "play.kvicloud.ru:24842",
                false
        );
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                this,
                mc,
                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("play.kvicloud.ru:24842"),
                server,
                false
        );
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xFF07090D);
        g.fill(0, 0, this.width, 4, 0xFFC83240);
        g.fill(0, 0, Math.max(330, this.width / 3), this.height, 0xE50A0D12);

        g.drawString(this.font, "GUNGLORYONLINE", 44, 46, 0xFFF5F6F8, false);
        g.drawString(this.font, "GGO CLIENT", 44, 66, 0xFFD04855, false);

        g.drawString(this.font, GgoAccountContext.displayName(), 44, 100, 0xFFE4E8ED, false);
        String accountState = GgoAccountContext.onlineReady()
                ? "GGO SESSION READY  •  " + GgoAccountContext.accountIdMasked()
                : "SIGN IN THROUGH GGO LAUNCHER TO ENTER ONLINE";
        g.drawString(this.font, accountState, 44, 118, GgoAccountContext.onlineReady() ? 0xFF7FA890 : 0xFFC96B72, false);
        g.drawString(this.font, "Official network  •  play.kvicloud.ru", 44, 134, 0xFF6D7989, false);
        if (!statusMessage.isBlank()) g.drawString(this.font, statusMessage, 44, 152, 0xFFD34B57, false);

        int rx = Math.max(360, this.width / 2);
        int ry = Math.max(90, this.height / 3);
        g.drawString(this.font, "WELCOME TO GGO", rx, ry, 0xFFF1F3F6, false);
        g.drawString(this.font, "Persistent world • Battle Royale • Training • Events", rx, ry + 22, 0xFF7F8B9B, false);
        g.drawString(this.font, "Online identity is owned by GGO Account, not the Minecraft session.", rx, ry + 44, 0xFF697586, false);
        g.drawString(this.font, "Minecraft / Forge runtime details stay hidden in Advanced diagnostics.", rx, ry + 62, 0xFF596574, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
'''

(JAVA / "GgoAccountContext.java").write_text(identity)
(JAVA / "GgoFrontEndScreen.java").write_text(frontend)

hud = JAVA / "GgoCombatHud.java"
if hud.exists():
    s = hud.read_text()
    call_anchor = '                renderWorldStatus(g, mc, width);\n'
    if call_anchor in s and 'renderMinimap(g, mc, width, height);' not in s:
        s = s.replace(call_anchor, call_anchor + '                if (GgoClientPreferences.minimapEnabled()) renderMinimap(g, mc, width, height);\n', 1)

    method_anchor = '    private static void renderWorldStatus(GuiGraphics g, Minecraft mc, int width) {'
    minimap = '''    private static void renderMinimap(GuiGraphics g, Minecraft mc, int width, int height) {\n        int size = 118;\n        int x = width - size - 18;\n        int y = 34;\n        g.fill(x, y, x + size, y + size, 0xD90A0E14);\n        g.fill(x, y, x + size, y + 1, 0xFF384454);\n        g.fill(x, y, x + 1, y + size, 0xFF384454);\n        for (int i = 24; i < size; i += 24) {\n            g.fill(x + i, y + 1, x + i + 1, y + size - 1, 0xFF18212B);\n            g.fill(x + 1, y + i, x + size - 1, y + i + 1, 0xFF18212B);\n        }\n        int cx = x + size / 2;\n        int cy = y + size / 2;\n        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFD34855);\n        String sector = sectorFor(mc.player.getBlockX(), mc.player.getBlockZ());\n        String facing = mc.player.getDirection().getName().toUpperCase();\n        g.drawString(mc.font, sector, x + 7, y + 7, 0xFFB8C1CE, false);\n        g.drawString(mc.font, facing, x + size - mc.font.width(facing) - 7, y + 7, 0xFF727F91, false);\n        g.drawString(mc.font, "YOU", cx + 7, cy - 4, 0xFFEBEEF2, false);\n    }\n\n'''
    if method_anchor in s and 'private static void renderMinimap' not in s:
        s = s.replace(method_anchor, minimap + method_anchor, 1)
    hud.write_text(s)

print("GGO Identity / Minimap Stage 7 applied")
print(" - frontend identity now comes from launcher-supplied GGO context")
print(" - ENTER GGO requires authenticated GGO account context")
print(" - no tokens or secrets displayed")
print(" - optional minimap renders only when enabled in GGO Settings")
