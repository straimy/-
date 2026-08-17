package arena.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** GGO-owned connection error shell. */
public final class GgoDisconnectedScreen extends Screen {
    private final Component reason;

    public GgoDisconnectedScreen(Component reason) {
        super(Component.literal("GunGloryOnline"));
        this.reason = reason == null ? Component.literal("Connection lost") : reason;
    }

    @Override
    protected void init() {
        int bw = Math.min(300, Math.max(220, width / 3));
        int x = (width - bw) / 2;
        int y = Math.max(175, height / 2 + 28);

        addRenderableWidget(Button.builder(Component.literal("BACK TO GGO"), b -> minecraft.setScreen(new GgoTitleScreen()))
            .bounds(x, y, bw, 22).build());
        addRenderableWidget(Button.builder(Component.literal("EXIT GAME"), b -> minecraft.stop())
            .bounds(x, y + 30, bw, 22).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        UiEffects.verticalGradient(g, 0, 0, width, height, 0xFF07111D, 0xFF18070E);
        int cw = Math.min(470, width - 40);
        int ch = 260;
        int x = (width - cw) / 2;
        int y = (height - ch) / 2;
        UiEffects.verticalGradient(g, x, y, x + cw, y + ch, 0xE5162234, 0xE50A0E18);
        UiEffects.animatedSheen(g, x, y, cw, ch, now, UiTheme.PINK);
        UiEffects.pulseBorder(g, x, y, cw, ch, now, UiTheme.PINK);
        g.drawCenteredString(font, Component.literal("GUN GLORY ONLINE"), width / 2, y + 34, UiTheme.TEXT);
        g.drawCenteredString(font, Component.literal("CONNECTION LOST"), width / 2, y + 58, UiTheme.PINK);
        g.drawCenteredString(font, reason, width / 2, y + 92, UiTheme.MUTED);
        g.drawCenteredString(font, Component.literal("Return to the launcher/server list and try again"), width / 2, y + 116, UiTheme.DIM);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
