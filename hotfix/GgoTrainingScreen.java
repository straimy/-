package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Honest placeholder for the future local GGO practice sandbox. */
public final class GgoTrainingScreen extends Screen {
    private final Screen parent;

    public GgoTrainingScreen(Screen parent) {
        super(Component.literal("GGO Practice"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(360, Math.max(260, width / 3));
        int x = (width - w) / 2;
        int y = Math.max(270, height / 2 + 62);
        addRenderableWidget(Button.builder(Component.literal("BACK"), b -> onClose())
            .bounds(x, y, w, 28).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF05070B);
        g.fill(0, 0, width, 3, 0xFFC83245);

        int center = width / 2;
        int top = Math.max(90, height / 6);
        g.drawCenteredString(font, Component.literal("GUN GLORY ONLINE"), center, top, 0xFFF2F4F7);
        g.drawCenteredString(font, Component.literal("PRACTICE"), center, top + 28, 0xFFC83245);
        g.drawCenteredString(font, Component.literal("OFFLINE SANDBOX · COMING SOON"), center, top + 58, 0xFFF2F4F7);
        g.drawCenteredString(font, Component.literal("Practice is not implemented yet."), center, top + 92, 0xFF8792A3);
        g.drawCenteredString(font, Component.literal("Aim, movement and loadout drills will be added here without server progression."), center, top + 112, 0xFF8792A3);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent == null ? new GgoFrontEndScreen() : parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
