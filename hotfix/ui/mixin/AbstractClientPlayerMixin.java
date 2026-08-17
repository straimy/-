package arena.client.ui.mixin;

import arena.client.ui.GgoNetworkSkinRuntime;
import arena.client.ui.GgoSkinRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies first-party GGO skins while preserving vanilla/Microsoft fallback behavior. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void ggo$skinTexture(CallbackInfoReturnable<ResourceLocation> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        ResourceLocation texture = player.getUUID().equals(mc.player.getUUID())
            ? GgoSkinRuntime.activeTexture()
            : GgoNetworkSkinRuntime.texture(player.getUUID());
        if (texture != null) cir.setReturnValue(texture);
    }
}
