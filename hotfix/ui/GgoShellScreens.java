package arena.client.ui;

import java.awt.Desktop;
import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "gunnerarena_ui", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoShellScreens {
    private GgoShellScreens() {}

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen next = event.getNewScreen();
        if (next instanceof GgoTitleScreen || next instanceof GgoPauseScreen) return;
        if (next instanceof TitleScreen) event.setNewScreen(new GgoTitleScreen());
        else if (next instanceof PauseScreen) event.setNewScreen(new GgoPauseScreen());
    }

    private abstract static class GgoBaseScreen extends Screen {
        private static final int BG = 0xFF07090D;
        private static final int PANEL = 0xE8141820;
        private static final int LINE = 0xFF303746;
        private static final int TEXT = 0xFFF5F7FA;
        private static final int MUTED = 0xFF8993A2;
        private static final int RED = 0xFFE83B48;
        private static final int RED_DARK = 0xFF7A1620;

        protected GgoBaseScreen(String title) { super(Component.literal(title)); }

        protected int cardWidth() { return Math.min(420, Math.max(300, this.width - 64)); }
        protected int left() { return (this.width - cardWidth()) / 2; }

        protected Button action(String label, int y, Button.OnPress press) {
            return Button.builder(Component.literal(label), press)
                    .bounds(left() + 30, y, cardWidth() - 60, 28)
                    .build();
        }

        protected void paintShell(GuiGraphics graphics, String eyebrow, String subtitle) {
            graphics.fill(0, 0, width, height, BG);
            graphics.fillGradient(0, 0, width, height, 0xFF10141C, BG);
            int glowW = Math.max(180, width / 3);
            int glowX = width - glowW;
            graphics.fillGradient(glowX, 0, width, height, 0x227A1620, 0x00101820);

            int cardX = left();
            int cardY = Math.max(44, (height - 330) / 2);
            graphics.fill(cardX - 1, cardY - 1, cardX + cardWidth() + 1, cardY + 330, LINE);
            graphics.fill(cardX, cardY, cardX + cardWidth(), cardY + 329, PANEL);
            graphics.fill(cardX, cardY, cardX + 4, cardY + 329, RED_DARK);

            graphics.drawString(font, Component.literal(eyebrow), cardX + 30, cardY + 26, RED, false);
            graphics.drawString(font, Component.literal("GUNGLORYONLINE"), cardX + 30, cardY + 48, TEXT, false);
            graphics.drawString(font, Component.literal(subtitle), cardX + 30, cardY + 66, MUTED, false);

            long now = System.currentTimeMillis();
            int pulse = (int) ((Math.sin(now / 350.0) + 1.0) * 28.0);
            int alpha = 0x30 + pulse;
            int pulseColor = (alpha << 24) | 0x00E83B48;
            graphics.fill(cardX + 30, cardY + 88, cardX + cardWidth() - 30, cardY + 89, 0xFF2A303A);
            graphics.fill(cardX + 30, cardY + 88, cardX + 96 + pulse, cardY + 89, pulseColor);
        }

        protected void paintFooter(GuiGraphics graphics) {
            String runtime = "GunGlory Runtime v1  ·  Minecraft 1.20.1 / Forge 47.4.10";
            int x = Math.max(12, (width - font.width(runtime)) / 2);
            graphics.drawString(font, runtime, x, height - 22, 0xFF5F6877, false);
        }

        protected void openGameFiles() {
            Minecraft mc = Minecraft.getInstance();
            File dir = mc.gameDirectory;
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(dir);
                    return;
                }
            } catch (Throwable ignored) {}
            try { new ProcessBuilder("xdg-open", dir.getAbsolutePath()).start(); }
            catch (Throwable ignored) {}
        }

        protected void openSettings() {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new OptionsScreen(this, mc.options));
        }

        protected void exitGame() { Minecraft.getInstance().stop(); }
    }

    public static final class GgoTitleScreen extends GgoBaseScreen {
        public GgoTitleScreen() { super("GunGloryOnline"); }

        @Override
        protected void init() {
            int y = Math.max(160, (height - 330) / 2 + 112);
            addRenderableWidget(action("GAME FILES", y, button -> openGameFiles()));
            addRenderableWidget(action("SETTINGS", y + 40, button -> openSettings()));
            addRenderableWidget(action("EXIT GAME", y + 80, button -> exitGame()));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            paintShell(graphics, "GGO CLIENT", "Server selection is handled by the launcher");
            super.render(graphics, mouseX, mouseY, partialTick);
            paintFooter(graphics);
        }

        @Override
        public boolean isPauseScreen() { return false; }
    }

    public static final class GgoPauseScreen extends GgoBaseScreen {
        public GgoPauseScreen() { super("GunGloryOnline"); }

        @Override
        protected void init() {
            int y = Math.max(144, (height - 330) / 2 + 104);
            addRenderableWidget(action("BACK TO GAME", y, button -> Minecraft.getInstance().setScreen(null)));
            addRenderableWidget(action("GAME FILES", y + 38, button -> openGameFiles()));
            addRenderableWidget(action("SETTINGS", y + 76, button -> openSettings()));
            addRenderableWidget(action("EXIT GAME", y + 114, button -> exitGame()));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            paintShell(graphics, "GAME MENU", "GunGloryOnline session");
            super.render(graphics, mouseX, mouseY, partialTick);
            paintFooter(graphics);
        }

        @Override
        public void onClose() { Minecraft.getInstance().setScreen(null); }

        @Override
        public boolean isPauseScreen() { return true; }
    }
}
