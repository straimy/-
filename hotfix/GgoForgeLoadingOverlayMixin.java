package arena.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.loading.ForgeLoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Covers Forge's early loading presentation with the GGO startup surface.
 *
 * Forge still owns reload completion, error propagation and fade timing. The TAIL overlay only
 * replaces presentation pixels, but it resets the pose first: Forge leaves transforms active while
 * rendering its logo and those transforms previously made the GGO cover occupy only part of the
 * framebuffer on scaled Linux desktops.
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
        if (minecraft == null || minecraft.font == null) return;

        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (width <= 0 || height <= 0) return;

        graphics.pose().pushPose();
        try {
            // Do not inherit Forge/Mojang logo transforms. Draw in canonical GUI coordinates.
            graphics.pose().last().pose().identity();
            graphics.pose().translate(0.0F, 0.0F, 1000.0F);

            graphics.fill(0, 0, width, height, 0xFF08090C);
            graphics.fill(0, 0, width, Math.max(3, height / 120), 0xFFC72F3C);

            int centerX = width / 2;
            int centerY = height / 2;
            graphics.drawCenteredString(minecraft.font, "GUNGLORYONLINE", centerX, centerY - 18, 0xFFF1F1F1);
            graphics.drawCenteredString(minecraft.font, "INITIALIZING GGO", centerX, centerY + 4, 0xFFD24A57);

            int barWidth = Math.min(260, Math.max(120, width / 4));
            int left = centerX - barWidth / 2;
            int top = centerY + 32;
            graphics.fill(left, top, left + barWidth, top + 3, 0xFF24262C);

            long now = System.currentTimeMillis();
            int pulseWidth = Math.max(28, barWidth / 5);
            int travel = Math.max(1, barWidth - pulseWidth);
            int pulse = (int) ((now / 8L) % (travel * 2L));
            if (pulse > travel) pulse = travel * 2 - pulse;
            graphics.fill(left + pulse, top, left + pulse + pulseWidth, top + 3, 0xFFE4E5E8);
        } finally {
            graphics.pose().popPose();
        }
    }
}
