from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
CLIENT = ROOT / "client-ui"
JAVA = CLIENT / "src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

frontend = r'''package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class GgoFrontEndScreen extends Screen {
    public GgoFrontEndScreen() {
        super(Component.literal("GunGloryOnline"));
    }

    @Override
    protected void init() {
        int x = 44;
        int y = Math.max(150, this.height / 2 - 70);
        int w = 230;

        addRenderableWidget(Button.builder(Component.literal("ENTER GGO"), b -> reconnectOfficial())
                .bounds(x, y, w, 30).build());
        addRenderableWidget(Button.builder(Component.literal("ACTIVITIES"), b -> open(GgoShellScreen.Page.ACTIVITIES))
                .bounds(x, y + 38, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("TRAINING"), b -> open(GgoShellScreen.Page.ACTIVITIES))
                .bounds(x, y + 70, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("SETTINGS"), b -> Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.OptionsScreen(this, Minecraft.getInstance().options)))
                .bounds(x, y + 102, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("EXIT"), b -> Minecraft.getInstance().stop())
                .bounds(x, y + 134, w, 26).build());
    }

    private void open(GgoShellScreen.Page page) {
        Minecraft.getInstance().setScreen(new GgoShellScreen(page));
    }

    private void reconnectOfficial() {
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

        Minecraft mc = Minecraft.getInstance();
        String account = mc.getUser() == null ? "GGO ACCOUNT" : mc.getUser().getName();
        g.drawString(this.font, account, 44, 100, 0xFFE4E8ED, false);
        g.drawString(this.font, "Official network  •  play.kvicloud.ru", 44, 118, 0xFF7D8999, false);

        int rx = Math.max(360, this.width / 2);
        g.drawString(this.font, "WELCOME TO GGO", rx, Math.max(90, this.height / 3), 0xFFF1F3F6, false);
        g.drawString(this.font, "Persistent world • Battle Royale • Training • Events", rx, Math.max(112, this.height / 3 + 22), 0xFF7F8B9B, false);
        g.drawString(this.font, "Minecraft / Forge runtime details are hidden in Advanced diagnostics.", rx, Math.max(134, this.height / 3 + 44), 0xFF596574, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
'''

(JAVA / "GgoFrontEndScreen.java").write_text(frontend)

hooks = JAVA / "GgoShellHooks.java"
if hooks.exists():
    s = hooks.read_text()
    if "import net.minecraft.client.gui.screens.TitleScreen;" not in s:
        s = s.replace("import net.minecraft.client.gui.screens.PauseScreen;", "import net.minecraft.client.gui.screens.PauseScreen;\nimport net.minecraft.client.gui.screens.TitleScreen;")
    anchor = "if (event.getNewScreen() instanceof GgoShellScreen) return;"
    replacement = "if (event.getNewScreen() instanceof GgoShellScreen || event.getNewScreen() instanceof GgoFrontEndScreen) return;\n        if (event.getNewScreen() instanceof TitleScreen) {\n            event.setNewScreen(new GgoFrontEndScreen());\n            return;\n        }"
    if anchor in s and "instanceof TitleScreen" not in s:
        s = s.replace(anchor, replacement, 1)
    hooks.write_text(s)

print("GGO Frontend Stage 5 applied")
print(" - vanilla TitleScreen -> GGO Frontend")
print(" - official network reconnect button")
print(" - Activities / Training / Settings / Exit")
