package arena.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * GGO game-mode chooser. The client never changes match state locally: an enabled card only sends
 * the typed /play request and the server-owned GgoGameModeRegistry decides whether it is allowed.
 */
public final class GgoModeSelectScreen extends Screen {
    public static final String VERSION = "GGO-MODE-SELECT-V1";

    private final Screen parent;

    public GgoModeSelectScreen(Screen parent) {
        super(Component.literal("Choose Game Mode"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cardW = Math.min(230, Math.max(170, (width - 70) / 2));
        int gap = 14;
        int total = cardW * 2 + gap;
        int x = (width - total) / 2;
        int y = Math.max(108, height / 2 - 70);

        addModeButton("ARENA  •  ACTIVE", "arena", x, y, cardW, true);
        addModeButton("CLASSIC ARENA  •  MIGRATING", "classic", x + cardW + gap, y, cardW, false);
        addModeButton("DUELS  •  PLANNED", "duels", x, y + 42, cardW, false);
        addModeButton("BATTLE ROYALE  •  PLANNED", "br", x + cardW + gap, y + 42, cardW, false);

        addRenderableWidget(Button.builder(Component.literal("BACK"), button -> minecraft.setScreen(parent))
            .bounds((width - 180) / 2, y + 100, 180, 22).build());
    }

    private void addModeButton(String title, String command, int x, int y, int width, boolean enabled) {
        Button button = Button.builder(Component.literal(title), ignored -> requestMode(command))
            .bounds(x, y, width, 28).build();
        button.active = enabled;
        addRenderableWidget(button);
    }

    private void requestMode(String mode) {
        if (minecraft == null || minecraft.player == null || minecraft.player.connection == null) return;
        minecraft.player.connection.sendCommand("play " + mode);
        minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        long now = System.currentTimeMillis();
        int panelW = Math.min(540, width - 32);
        int panelH = 245;
        int x = (width - panelW) / 2;
        int y = Math.max(28, (height - panelH) / 2);

        UiEffects.verticalGradient(graphics, x, y, x + panelW, y + panelH, 0xEE101B2A, 0xEE080C14);
        UiEffects.animatedSheen(graphics, x, y, panelW, panelH, now, UiTheme.ACCENT);
        UiEffects.pulseBorder(graphics, x, y, panelW, panelH, now, UiTheme.ACCENT);
        graphics.drawCenteredString(font, Component.literal("PLAY"), width / 2, y + 28, UiTheme.TEXT);
        graphics.drawCenteredString(font, Component.literal("CHOOSE OPERATION"), width / 2, y + 47, UiTheme.ACCENT_2);
        graphics.drawCenteredString(font,
            Component.literal("Mode availability is controlled by the GGO server"),
            width / 2, y + 65, UiTheme.MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
