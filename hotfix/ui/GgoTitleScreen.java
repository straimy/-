package arena.client.ui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Standalone-facing GGO shell. Minecraft/Forge remains an internal Runtime v1 implementation detail. */
public final class GgoTitleScreen extends Screen {
    public static final String VERSION = "GGO-TITLE-V2";

    public GgoTitleScreen() {
        super(Component.literal("GunGloryOnline"));
    }

    @Override
    protected void init() {
        int width = Math.min(310, Math.max(230, this.width / 3));
        int x = (this.width - width) / 2;
        int y = Math.max(142, this.height / 2 - 28);

        Button play = Button.builder(Component.literal("PLAY"), button -> openPlay())
            .bounds(x, y, width, 28).build();
        play.active = minecraft != null && minecraft.player != null && minecraft.player.connection != null;
        addRenderableWidget(play);

        addRenderableWidget(Button.builder(Component.literal("GAME FILES"), button -> openGameFiles())
            .bounds(x, y + 36, width, 22).build());
        addRenderableWidget(Button.builder(Component.literal("SETTINGS"), button -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)))
            .bounds(x, y + 64, width, 22).build());
        addRenderableWidget(Button.builder(Component.literal("EXIT GAME"), button -> minecraft.stop())
            .bounds(x, y + 92, width, 22).build());
    }

    private void openPlay() {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) return;
        minecraft.setScreen(new GgoModeSelectScreen(this));
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
        int cardY = Math.max(42, height / 2 - 158);
        UiEffects.verticalGradient(graphics, cardX, cardY, cardX + cardW, cardY + 292, 0xD9152235, 0xD90B101A);
        UiEffects.animatedSheen(graphics, cardX, cardY, cardW, 292, now, UiTheme.ACCENT);
        UiEffects.pulseBorder(graphics, cardX, cardY, cardW, 292, now, UiTheme.ACCENT);

        graphics.drawCenteredString(font, Component.literal("GUN GLORY ONLINE"), width / 2, cardY + 34, UiTheme.TEXT);
        graphics.drawCenteredString(font, Component.literal("ONLINE OPERATIONS"), width / 2, cardY + 54, UiTheme.ACCENT_2);
        graphics.drawCenteredString(font, Component.literal("Choose a GGO game mode and enter the operation"), width / 2, cardY + 78, UiTheme.MUTED);
        graphics.drawCenteredString(font, Component.literal("Runtime v1"), width / 2, cardY + 94, UiTheme.DIM);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
