package arena.client.ui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Offline-only shell. Actual range bootstrap is intentionally separate from online progression. */
public final class GgoTrainingScreen extends Screen {
    public GgoTrainingScreen() {
        super(Component.literal("GunGloryOnline Training"));
    }

    @Override
    protected void init() {
        int buttonW = Math.min(310, Math.max(230, width / 3));
        int x = (width - buttonW) / 2;
        int y = Math.max(176, height / 2 + 18);

        addRenderableWidget(Button.builder(Component.literal("TRAINING FILES"), button -> openGameFiles())
            .bounds(x, y, buttonW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("SETTINGS"), button -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)))
            .bounds(x, y + 28, buttonW, 22).build());
        addRenderableWidget(Button.builder(Component.literal("EXIT TRAINING"), button -> minecraft.stop())
            .bounds(x, y + 56, buttonW, 22).build());
    }

    private void openGameFiles() {
        Minecraft mc = Minecraft.getInstance();
        Util.getPlatform().openFile(mc.gameDirectory);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        graphics.fill(0, 0, width, height, 0xFF05080E);
        UiEffects.verticalGradient(graphics, 0, 0, width, height, 0xFF08101A, 0xFF161008);
        int cardW = Math.min(480, width - 40);
        int cardX = (width - cardW) / 2;
        int cardY = Math.max(40, height / 2 - 180);
        UiEffects.verticalGradient(graphics, cardX, cardY, cardX + cardW, cardY + 320, 0xE0152131, 0xE00B1018);
        UiEffects.animatedSheen(graphics, cardX, cardY, cardW, 320, now, UiTheme.ACCENT);
        UiEffects.pulseBorder(graphics, cardX, cardY, cardW, 320, now, UiTheme.ACCENT);

        graphics.drawCenteredString(font, Component.literal("GUN GLORY ONLINE"), width / 2, cardY + 34, UiTheme.TEXT);
        graphics.drawCenteredString(font, Component.literal("OFFLINE TRAINING"), width / 2, cardY + 56, UiTheme.ACCENT_2);
        graphics.drawCenteredString(font, Component.literal("LOCAL RUNTIME · NO ONLINE REWARDS"), width / 2, cardY + 78, UiTheme.MUTED);
        graphics.drawCenteredString(font, Component.literal("Training range bootstrap is isolated from ranked XP, currency and season progress"), width / 2, cardY + 108, UiTheme.DIM);
        graphics.drawCenteredString(font, Component.literal("Minecraft singleplayer menus remain hidden"), width / 2, cardY + 122, UiTheme.DIM);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
