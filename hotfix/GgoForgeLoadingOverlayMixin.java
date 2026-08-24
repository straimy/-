package arena.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.loading.ForgeLoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Covers Forge's early red loading presentation with the GGO startup surface.
 *
 * The injection deliberately runs at TAIL and never cancels Forge's render method:
 * Forge still owns reload completion, error propagation and fade timing. We only
 * replace the pixels presented to the player while ForgeLoadingOverlay is active.
 */
@Mixin(value = ForgeLoadingOverlay.class, remap = false)
public abstract class GgoForgeLoadingOverlayMixin {
    @Inject(method = "m_88315_", at = @At("TAIL"), remap = false, require = 0)
    private void ggo$renderStartupSurface(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        float partialTick,
        CallbackInfo ci
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.font == null) {
            return;
        }

        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        graphics.fill(0, 0, width, height, 0xFF08090C);

        int centerX = width / 2;
        int centerY = height / 2;
        graphics.drawCenteredString(minecraft.font, "GUNGLORYONLINE", centerX, centerY - 12, 0xFFF1F1F1);
        graphics.drawCenteredString(minecraft.font, "INITIALIZING GAME", centerX, centerY + 8, 0xFF9A9DA5);

        int barWidth = Math.min(220, Math.max(96, width / 4));
        int left = centerX - barWidth / 2;
        int top = centerY + 28;
        graphics.fill(left, top, left + barWidth, top + 2, 0xFF24262C);

        // A restrained moving pulse communicates activity without reading Forge's
        // private progress state or coupling GGO to its internal loading protocol.
        long now = System.currentTimeMillis();
        int pulseWidth = Math.max(24, barWidth / 5);
        int travel = Math.max(1, barWidth - pulseWidth);
        int pulse = (int) ((now / 8L) % (travel * 2L));
        if (pulse > travel) {
            pulse = travel * 2 - pulse;
        }
        graphics.fill(left + pulse, top, left + pulse + pulseWidth, top + 2, 0xFFD7D7DA);
    }
}
