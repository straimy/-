package arena.client.ui.mixin;

import arena.client.ui.GgoSkinRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the first-party GGO skin only to the local launcher identity. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void ggo$skinTexture(CallbackInfoReturnable<ResourceLocation> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        if (!self.getUUID().equals(mc.player.getUUID())) return;

        ResourceLocation texture = GgoSkinRuntime.activeTexture();
        if (texture != null) cir.setReturnValue(texture);
    }
}
