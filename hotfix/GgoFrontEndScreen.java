package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Safe fallback frontend. Official online sessions are minted only by GGO Launcher,
 * so this screen deliberately contains no direct server-connect path.
 */
public final class GgoFrontEndScreen extends Screen {
    public GgoFrontEndScreen() {
        super(Component.literal("GunGloryOnline"));
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(300, Math.max(220, width / 3));
        int x = (width - buttonWidth) / 2;
        int y = Math.max(176, height / 2 + 10);

        if (minecraft != null && minecraft.getConnection() != null && minecraft.player != null) {
            addRenderableWidget(
                Button.builder(Component.literal("CONTINUE GGO"), button -> minecraft.setScreen(null))
                    .bounds(x, y, buttonWidth, 26)
                    .build()
            );
        } else {
            addRenderableWidget(
                Button.builder(Component.literal("RETURN TO LAUNCHER"), button -> minecraft.stop())
                    .bounds(x, y, buttonWidth, 26)
                    .build()
            );
        }

        addRenderableWidget(
            Button.builder(
                    Component.literal("SETTINGS"),
                    button -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))
                )
                .bounds(x, y + 36, buttonWidth, 22)
                .build()
        );
        addRenderableWidget(
            Button.builder(Component.literal("EXIT GAME"), button -> minecraft.stop())
                .bounds(x, y + 66, buttonWidth, 22)
                .build()
        );
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF05070C);
        g.fill(0, 0, width, Math.max(3, height / 120), 0xFFD54855);

        int cardWidth = Math.min(580, width - 40);
        int cardHeight = 300;
        int x = (width - cardWidth) / 2;
        int y = Math.max(28, (height - cardHeight) / 2);
        g.fill(x, y, x + cardWidth, y + cardHeight, 0xF20A0E16);
        g.fill(x, y, x + 3, y + cardHeight, 0xFFD54855);

        Minecraft mc = Minecraft.getInstance();
        boolean connected = mc.getConnection() != null && mc.player != null;
        String heading = connected ? "SESSION ACTIVE" : "ONLINE ENTRY LOCKED";
        String message = connected
            ? "Your verified GGO session is active."
            : "Official online sessions are created by GGO Launcher.";
        String detail = connected
            ? "Continue directly into GunGloryOnline."
            : "Return to the launcher and press PLAY ONLINE for a fresh secure session.";

        g.drawCenteredString(font, Component.literal("GUN GLORY ONLINE"), width / 2, y + 42, 0xFFF2F5F8);
        g.drawCenteredString(font, Component.literal(heading), width / 2, y + 70, connected ? 0xFF9F6CFF : 0xFFD54855);
        g.drawCenteredString(font, Component.literal(message), width / 2, y + 104, 0xFF9BA6B7);
        g.drawCenteredString(font, Component.literal(detail), width / 2, y + 124, 0xFF687589);
        g.drawCenteredString(
            font,
            Component.literal("ONE ACCOUNT  •  ONE SECURE ENTRY  •  GGO LAUNCHER"),
            width / 2,
            y + 150,
            0xFF687589
        );

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
