package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ControlsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.SoundOptionsScreen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.Component;

/**
 * GGO settings hub. Categories open the real Minecraft option screens instead of decorative
 * buttons, so sliders/toggles apply immediately and persist through the vanilla Options system.
 */
public final class GgoSettingsScreen extends Screen {
    private final Screen parent;

    public GgoSettingsScreen(Screen parent) {
        super(Component.literal("GunGloryOnline Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        int buttonWidth = Math.min(360, Math.max(260, width / 3));
        int x = width / 2 - buttonWidth / 2;
        int y = Math.max(145, height / 2 - 95);
        int gap = 46;

        addRenderableWidget(Button.builder(Component.literal("AUDIO"), button ->
                mc.setScreen(new SoundOptionsScreen(this, mc.options)))
            .bounds(x, y, buttonWidth, 32).build());

        addRenderableWidget(Button.builder(Component.literal("VIDEO"), button ->
                mc.setScreen(new VideoSettingsScreen(this, mc.options)))
            .bounds(x, y + gap, buttonWidth, 32).build());

        addRenderableWidget(Button.builder(Component.literal("CONTROLS"), button ->
                mc.setScreen(new ControlsScreen(this, mc.options)))
            .bounds(x, y + gap * 2, buttonWidth, 32).build());

        addRenderableWidget(Button.builder(Component.literal("BACK"), button -> onClose())
            .bounds(x, y + gap * 3 + 14, buttonWidth, 32).build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().options.save();
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF050609);
        g.fill(0, 0, width, Math.max(3, height / 110), 0xFF9A2532);
        int margin = Math.max(28, width / 35);
        int top = Math.max(28, height / 18);
        g.drawString(font, "GUNGLORYONLINE", margin, top, 0xFFF3F5F7, false);
        g.drawString(font, "SETTINGS", margin, top + 24, 0xFFB74350, false);
        g.drawCenteredString(font, Component.literal("Choose a category"), width / 2, top + 72, 0xFF8C96A6);
        g.drawCenteredString(font, Component.literal("Audio includes Master, Music, Players, Blocks, Weather and more"), width / 2, top + 92, 0xFF697587);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
