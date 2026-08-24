package arena.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keeps the native OS window title first-party even though Runtime v1 is Minecraft/Forge based. */
@Mixin(Window.class)
public abstract class GgoWindowTitleMixin {
    @ModifyVariable(method = "setTitle", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String ggo$brandWindowTitle(String original) {
        return "GunGloryOnline";
    }
}
