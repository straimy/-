package arena.mixin;

import arena.client.shell.GgoEntryDisconnectedScreen;
import arena.client.shell.GgoFrontEndScreen;
import arena.client.shell.GgoSettingsScreen;
import arena.client.shell.GgoShellScreen;
import arena.client.shell.GgoTrainingScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps vanilla Button input/narration semantics, but removes the vanilla grey-button presentation
 * from every first-party GunGloryOnline surface.
 */
@Mixin(Button.class)
public abstract class GgoButtonMixin {
    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void ggo$renderFirstPartyButton(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        float partialTick,
        CallbackInfo ci
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) return;

        Screen screen = minecraft.screen;
        if (!(screen instanceof GgoShellScreen)
            && !(screen instanceof GgoSettingsScreen)
            && !(screen instanceof GgoFrontEndScreen)
            && !(screen instanceof GgoTrainingScreen)
            && !(screen instanceof GgoEntryDisconnectedScreen)) {
            return;
        }

        Button button = (Button) (Object) this;
        int x = button.getX();
        int y = button.getY();
        int width = button.getWidth();
        int height = button.getHeight();
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;

        int background;
        int border;
        int text;
        if (!button.active) {
            background = 0xE80A0E14;
            border = 0xFF1C2430;
            text = 0xFF566171;
        } else if (hovered) {
            background = 0xF218202B;
            border = 0xFFD54855;
            text = 0xFFFFFFFF;
        } else {
            background = 0xF20D1219;
            border = 0xFF26303D;
            text = 0xFFE8EDF3;
        }

        graphics.fill(x, y, x + width, y + height, background);
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF090C11);
        graphics.fill(x, y, x + 2, y + height, button.active ? (hovered ? 0xFFFF6470 : 0xFFC83340) : 0xFF252D37);

        int textY = y + (height - minecraft.font.lineHeight) / 2;
        graphics.drawCenteredString(minecraft.font, button.getMessage(), x + width / 2, textY, text);
        ci.cancel();
    }
}
