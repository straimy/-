package arena.client.shell;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * First-party GGO chat input presentation.
 *
 * The vanilla ChatScreen implementation remains the transport/command-suggestion/history engine,
 * so signed chat and server chat semantics are preserved. Only the player-facing input chrome is
 * replaced with GGO presentation.
 */
public final class GgoChatScreen extends ChatScreen {
    public GgoChatScreen(String initial) {
        super(initial == null ? "" : initial);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Preserve ChatScreen's mature history, signing, hover and command-suggestion behavior.
        super.render(g, mouseX, mouseY, partialTick);

        int top = this.height - 36;
        g.fill(0, top, this.width, this.height, 0xF00A0E14);
        g.fill(0, top, this.width, top + 2, 0xFFC73A47);
        g.drawString(this.font, "GGO COMMS", 9, top + 7, 0xFFF0F2F5, false);
        g.drawString(this.font, "ENTER SEND   ESC CLOSE", this.width - 142, top + 7, 0xFF687586, false);

        // The first render was covered by our chrome; redraw only the real chat EditBox on top.
        if (this.input != null) this.input.render(g, mouseX, mouseY, partialTick);
    }
}
