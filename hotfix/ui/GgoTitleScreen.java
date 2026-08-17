package arena.client.ui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Standalone-facing title shell. Normal players should reach gameplay from the GGO launcher, not vanilla menus. */
public final class GgoTitleScreen extends Screen {
    public GgoTitleScreen() {
        super(Component.literal("GunGloryOnline"));
    }

    @Override
    protected void init() {
        int width = Math.min(310, Math.max(230, this.width / 3));
        int x = (this.width - width) / 2;
        int y = Math.max(150, this.height / 2 - 18);

        addRenderableWidget(Button.builder(Component.literal("GAME FILES"), button -> openGameFiles())
            .bounds(x, y, width, 22).build());
        addRenderableWidget(Button.builder(Component.literal("SETTINGS"), button -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)))
            .bounds(x, y + 28, width, 22).build());
        addRenderableWidget(Button.builder(Component.literal("EXIT GAME"), button -> minecraft.stop())
            .bounds(x, y + 56, width, 22).build());
    }

    private void openGameFiles() {
        Minecraft mc = Minecraft.getInstance();
        Util.getPlatform().openFile(mc.gameDirectory);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        graphics.fill(0, 0, width, height, 0xFF060910);
        UiEffects.verticalGradient(graphics, 0, 0, width, height, 0xFF07111D, 0xFF18070E);
        int cardW = Math.min(420, width - 40);
        int cardX = (width - cardW) / 2;
        int cardY = Math.max(48, height / 2 - 150);
        UiEffects.verticalGradient(graphics, cardX, cardY, cardX + cardW, cardY + 270, 0xD9152235, 0xD90B101A);
        UiEffects.animatedSheen(graphics, cardX, cardY, cardW, 270, now, UiTheme.ACCENT);
        UiEffects.pulseBorder(graphics, cardX, cardY, cardW, 270, now, UiTheme.ACCENT);

        graphics.drawCenteredString(font, Component.literal("GUN GLORY ONLINE"), width / 2, cardY + 38, UiTheme.TEXT);
        graphics.drawCenteredString(font, Component.literal("GUNGLORY RUNTIME v1"), width / 2, cardY + 58, UiTheme.ACCENT_2);
        graphics.drawCenteredString(font, Component.literal("Launch and choose servers from the GunGloryOnline launcher"), width / 2, cardY + 82, UiTheme.MUTED);
        graphics.drawCenteredString(font, Component.literal("Minecraft 1.20.1 + Forge is an internal runtime"), width / 2, cardY + 96, UiTheme.DIM);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
