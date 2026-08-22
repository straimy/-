package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** GGO-owned death/respawn flow; the vanilla Minecraft death screen never reaches players. */
public final class GgoRespawnScreen extends Screen {
    public GgoRespawnScreen() {
        super(Component.literal("KIA"));
    }

    @Override
    protected void init() {
        int x = width / 2 - 105;
        int y = height / 2 + 30;
        addRenderableWidget(Button.builder(Component.literal("RESPAWN"), b -> respawn())
            .bounds(x, y, 210, 24).build());
        addRenderableWidget(Button.builder(Component.literal("ACTIVITIES"), b -> Minecraft.getInstance().setScreen(new GgoShellScreen(GgoShellScreen.Page.ACTIVITIES)))
            .bounds(x, y + 32, 210, 22).build());
    }

    private void respawn() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.respawn();
        mc.setScreen(null);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xF20A0C11);
        g.fill(0, 0, width, 3, 0xFFD13B48);
        int cx = width / 2;
        int y = height / 2 - 58;
        g.drawCenteredString(font, Component.literal("GUNGLORYONLINE"), cx, y, 0xFF8B96A8);
        g.drawCenteredString(font, Component.literal("KIA"), cx, y + 24, 0xFFE14B59);
        g.drawCenteredString(font, Component.literal("Combat session interrupted"), cx, y + 44, 0xFFC2CBD8);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
