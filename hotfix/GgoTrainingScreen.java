package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Dedicated offline training branch for GGO Client.
 *
 * This screen deliberately owns no networking code. Training stays local to the client shell and
 * cannot consume, forward, or refresh the one-shot official online ticket. The actual drill/runtime
 * implementation can evolve behind this entry point without re-introducing a vanilla world/server UI.
 */
public final class GgoTrainingScreen extends Screen {
    public enum Drill {
        AIM_RANGE("AIM RANGE", "Weapons · recoil · target transitions"),
        MOVEMENT("MOVEMENT", "Sprint · cover · traversal drills"),
        LOADOUT_LAB("LOADOUT LAB", "Inspect GGO weapons and equipment offline");

        private final String title;
        private final String subtitle;

        Drill(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private final Screen parent;
    private Drill selected = Drill.AIM_RANGE;

    public GgoTrainingScreen(Screen parent) {
        super(Component.literal("GGO Training"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(330, Math.max(240, width / 3));
        int x = (width - w) / 2;
        int y = Math.max(170, height / 2 - 28);

        addRenderableWidget(Button.builder(Component.literal("AIM RANGE"), b -> select(Drill.AIM_RANGE))
            .bounds(x, y, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("MOVEMENT"), b -> select(Drill.MOVEMENT))
            .bounds(x, y + 34, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("LOADOUT LAB"), b -> select(Drill.LOADOUT_LAB))
            .bounds(x, y + 68, w, 26).build());
        addRenderableWidget(Button.builder(Component.literal("BACK"), b -> onClose())
            .bounds(x, y + 112, w, 24).build());
    }

    private void select(Drill drill) {
        selected = drill;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF05070C);
        g.fill(0, 0, width, Math.max(3, height / 120), 0xFFD54855);

        int cardWidth = Math.min(700, width - 40);
        int cardHeight = 370;
        int x = (width - cardWidth) / 2;
        int y = Math.max(20, (height - cardHeight) / 2);
        g.fill(x, y, x + cardWidth, y + cardHeight, 0xF20A0E16);
        g.fill(x, y, x + 3, y + cardHeight, 0xFFD54855);

        g.drawCenteredString(font, Component.literal("GUN GLORY ONLINE"), width / 2, y + 34, 0xFFF2F5F8);
        g.drawCenteredString(font, Component.literal("TRAINING  •  OFFLINE"), width / 2, y + 56, 0xFFD54855);
        g.drawCenteredString(font, Component.literal("No server connection · no online rewards · no ticket consumption"), width / 2, y + 84, 0xFF8A96A8);
        g.drawCenteredString(font, Component.literal(selected.title), width / 2, y + 112, 0xFFF0F3F7);
        g.drawCenteredString(font, Component.literal(selected.subtitle), width / 2, y + 132, 0xFF697588);
        g.drawCenteredString(font, Component.literal("Training runtime is isolated from the official GGO network."), width / 2, y + 154, 0xFF697588);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent == null ? new GgoFrontEndScreen() : parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
