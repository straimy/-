package arena.client.ui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Minimal pause shell that keeps the player inside the GGO experience. */
public final class GgoPauseScreen extends Screen {
    public GgoPauseScreen() {
        super(Component.literal("GunGloryOnline"));
    }

    @Override
    protected void init() {
        int width = Math.min(300, Math.max(220, this.width / 3));
        int x = (this.width - width) / 2;
        int y = Math.max(95, this.height / 2 - 58);

        addRenderableWidget(Button.builder(Component.literal("BACK TO GAME"), button -> minecraft.setScreen(null))
            .bounds(x, y, width, 22).build());
        addRenderableWidget(Button.builder(Component.literal("GAME FILES"), button -> Util.getPlatform().openFile(Minecraft.getInstance().gameDirectory))
            .bounds(x, y + 28, width, 22).build());
        addRenderableWidget(Button.builder(Component.literal("SETTINGS"), button -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)))
            .bounds(x, y + 56, width, 22).build());
        addRenderableWidget(Button.builder(Component.literal("EXIT GAME"), button -> minecraft.stop())
            .bounds(x, y + 84, width, 22).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        long now = System.currentTimeMillis();
        int cardW = Math.min(390, width - 40);
        int cardH = 215;
        int x = (width - cardW) / 2;
        int y = (height - cardH) / 2;
        UiEffects.verticalGradient(graphics, x, y, x + cardW, y + cardH, 0xE1142030, 0xE10A0E18);
        UiEffects.animatedSheen(graphics, x, y, cardW, cardH, now, UiTheme.ACCENT);
        UiEffects.pulseBorder(graphics, x, y, cardW, cardH, now, UiTheme.ACCENT);
        graphics.drawCenteredString(font, Component.literal("GUN GLORY ONLINE"), width / 2, y + 24, UiTheme.TEXT);
        graphics.drawCenteredString(font, Component.literal("GAME MENU"), width / 2, y + 41, UiTheme.ACCENT_2);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
