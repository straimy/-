package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Flat first-party GunGloryOnline control. No vanilla button sprite is rendered. */
public final class GgoButton extends AbstractButton {
    @FunctionalInterface
    public interface OnPress {
        void onPress(GgoButton button);
    }

    private final OnPress onPress;

    private GgoButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public static Builder builder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
    public void onPress() {
        if (active) onPress.onPress(this);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        boolean hot = active && isHoveredOrFocused();
        int left = getX();
        int top = getY();
        int right = left + getWidth();
        int bottom = top + getHeight();

        int background = !active ? 0xD90A0E15 : hot ? 0xF21B1017 : 0xE60C1119;
        int border = !active ? 0xFF242A34 : hot ? 0xFFD24452 : 0xFF303844;
        int text = !active ? 0xFF596270 : hot ? 0xFFFFFFFF : 0xFFE7EBF0;

        g.fill(left, top, right, bottom, background);
        g.fill(left, top, right, top + 1, border);
        g.fill(left, bottom - 1, right, bottom, border);
        g.fill(left, top, left + 1, bottom, border);
        g.fill(right - 1, top, right, bottom, border);
        if (hot) g.fill(left, top, left + 3, bottom, 0xFFD24452);

        g.drawCenteredString(mc.font, getMessage(), left + getWidth() / 2, top + (getHeight() - 8) / 2, text);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, createNarrationMessage());
    }

    public static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int width = 150;
        private int height = 20;

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public GgoButton build() {
            return new GgoButton(x, y, width, height, message, onPress);
        }
    }
}
