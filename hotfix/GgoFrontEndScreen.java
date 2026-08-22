package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Player-facing GGO entry surface. Online credentials are owned by GGO Launcher. */
public final class GgoFrontEndScreen extends Screen {
    public GgoFrontEndScreen() {
        super(Component.literal("GunGloryOnline"));
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(320, Math.max(230, width / 3));
        int x = (width - buttonWidth) / 2;
        int y = Math.max(182, height / 2 + 8);
        boolean connected = minecraft != null && minecraft.getConnection() != null && minecraft.player != null;

        if (connected) {
            addRenderableWidget(Button.builder(Component.literal("CONTINUE GGO"), button -> minecraft.setScreen(null))
                .bounds(x, y, buttonWidth, 28).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("RETURN TO GGO LAUNCHER"), button -> minecraft.stop())
                .bounds(x, y, buttonWidth, 28).build());
        }

        addRenderableWidget(Button.builder(Component.literal("SETTINGS"), button -> minecraft.setScreen(new GgoSettingsScreen(this)))
            .bounds(x, y + 38, buttonWidth, 24).build());
        addRenderableWidget(Button.builder(Component.literal("EXIT"), button -> minecraft.stop())
            .bounds(x, y + 70, buttonWidth, 24).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF05070C);
        g.fill(0, 0, width, Math.max(3, height / 120), 0xFFD54855);

        int cardWidth = Math.min(620, width - 40);
        int cardHeight = 310;
        int x = (width - cardWidth) / 2;
        int y = Math.max(24, (height - cardHeight) / 2);
        g.fill(x, y, x + cardWidth, y + cardHeight, 0xF20A0E16);
        g.fill(x, y, x + 3, y + cardHeight, 0xFFD54855);
        g.fill(x + 3, y, x + cardWidth, y + 1, 0x665E6E86);

        Minecraft mc = Minecraft.getInstance();
        boolean connected = mc.getConnection() != null && mc.player != null;
        String account = mc.getUser() == null ? "GGO ACCOUNT" : mc.getUser().getName();
        String heading = connected ? "SESSION READY" : "GGO CLIENT READY";
        String message = connected
            ? "Verified session is active. Continue into GunGloryOnline."
            : "Online play starts from GGO Launcher with a fresh secure session.";
        String detail = connected
            ? "Gameplay stays locked until account verification finishes."
            : "No in-client login, server list, or manual address is required.";

        g.drawCenteredString(font, Component.literal("GUN GLORY ONLINE"), width / 2, y + 38, 0xFFF2F5F8);
        g.drawCenteredString(font, Component.literal("GGO CLIENT  •  BETA"), width / 2, y + 59, 0xFFD54855);
        g.drawCenteredString(font, Component.literal(account), width / 2, y + 86, 0xFFCBD2DC);
        g.drawCenteredString(font, Component.literal(heading), width / 2, y + 116, connected ? 0xFF9F6CFF : 0xFFD54855);
        g.drawCenteredString(font, Component.literal(message), width / 2, y + 143, 0xFF9BA6B7);
        g.drawCenteredString(font, Component.literal(detail), width / 2, y + 162, 0xFF687589);
        g.drawCenteredString(font, Component.literal("ONE ACCOUNT  •  SECURE ENTRY  •  GGO LAUNCHER"), width / 2, y + 188, 0xFF687589);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
