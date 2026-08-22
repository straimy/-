package arena.client.shell;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** GGO-owned disconnect recovery screen. Fresh online tickets are always issued by the launcher. */
public final class GgoEntryDisconnectedScreen extends Screen {
    private static final int ACCENT = 0xFFD54855;
    private static final int TEXT = 0xFFF2F5F8;
    private static final int MUTED = 0xFF8B96A7;
    private final Component reason;

    public GgoEntryDisconnectedScreen(Component reason) {
        super(Component.literal("GunGloryOnline"));
        this.reason = reason == null
            ? Component.literal("Connection to GunGloryOnline was closed")
            : reason;
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(320, Math.max(220, width / 3));
        int x = (width - buttonWidth) / 2;
        int y = Math.max(220, height / 2 + 62);

        addRenderableWidget(
            Button.builder(Component.literal("RETURN TO LAUNCHER"), button -> minecraft.stop())
                .bounds(x, y, buttonWidth, 24)
                .build()
        );
        addRenderableWidget(
            Button.builder(Component.literal("COPY ERROR"), button -> {
                if (minecraft != null) minecraft.keyboardHandler.setClipboard(reason.getString());
            })
                .bounds(x, y + 32, buttonWidth, 22)
                .build()
        );
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF05070C);
        g.fill(0, 0, width, Math.max(3, height / 120), ACCENT);

        int cardWidth = Math.min(560, width - 36);
        int cardHeight = Math.min(330, height - 44);
        int x = (width - cardWidth) / 2;
        int y = (height - cardHeight) / 2;
        g.fill(x, y, x + cardWidth, y + cardHeight, 0xF20A0E16);
        g.fill(x, y, x + 3, y + cardHeight, ACCENT);

        boolean sessionFailure = GgoEntryExperience.looksLikeSessionFailure(reason);
        String heading = sessionFailure ? "SESSION EXPIRED" : "CONNECTION LOST";
        g.drawCenteredString(font, Component.literal("GUN GLORY ONLINE"), width / 2, y + 30, TEXT);
        g.drawCenteredString(font, Component.literal(heading), width / 2, y + 55, ACCENT);

        List<FormattedCharSequence> lines = font.split(reason, cardWidth - 52);
        int lineY = y + 88;
        int maxLines = Math.min(4, lines.size());
        for (int i = 0; i < maxLines; i++) {
            FormattedCharSequence line = lines.get(i);
            int lineWidth = font.width(line);
            g.drawString(font, line, width / 2 - lineWidth / 2, lineY + i * 13, MUTED, false);
        }

        int helpY = y + 154;
        g.drawCenteredString(
            font,
            Component.literal(
                sessionFailure
                    ? "The launcher will create a fresh secure session when you press PLAY ONLINE again."
                    : "Return to the launcher and retry the official connection."
            ),
            width / 2,
            helpY,
            0xFF657287
        );
        g.drawCenteredString(
            font,
            Component.literal("No in-game login is required."),
            width / 2,
            helpY + 18,
            0xFF657287
        );

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
