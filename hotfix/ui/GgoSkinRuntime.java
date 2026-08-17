package arena.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Resolves the active GGO identity and registers its verified skin in Minecraft's texture manager. */
public final class GgoSkinRuntime {
    private static String requestedPlayerId;
    private static String registeredHash;
    private static ResourceLocation activeTexture;
    private static CompletableFuture<GgoSkinResolver.ResolvedSkin> pending;

    private GgoSkinRuntime() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GgoIdentityBridge.Identity identity = GgoIdentityBridge.current();
        if (!identity.wantsGgoSkin()) {
            requestedPlayerId = null;
            registeredHash = null;
            activeTexture = null;
            pending = null;
            return;
        }

        String playerId = identity.ggoPlayerId().toString();
        if (!Objects.equals(requestedPlayerId, playerId)) {
            requestedPlayerId = playerId;
            registeredHash = null;
            activeTexture = null;
            pending = GgoSkinResolver.resolve(identity.ggoPlayerId());
        }

        if (pending == null || !pending.isDone()) return;
        GgoSkinResolver.ResolvedSkin resolved;
        try { resolved = pending.getNow(GgoSkinResolver.ResolvedSkin.defaultSkin()); }
        catch (RuntimeException ignored) { pending = null; return; }
        pending = null;
        if (!resolved.hasGgoTexture() || Objects.equals(registeredHash, resolved.hash())) return;

        try (InputStream input = Files.newInputStream(resolved.file())) {
            NativeImage image = NativeImage.read(input);
            if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                image.close();
                return;
            }
            ResourceLocation location = new ResourceLocation("gunnerarena_ui", "ggo_skin/" + resolved.hash());
            mc.getTextureManager().register(location, new DynamicTexture(image));
            registeredHash = resolved.hash();
            activeTexture = location;
        } catch (IOException | RuntimeException ignored) {
            // Vanilla/Microsoft skin remains active on any failure.
        }
    }

    public static ResourceLocation activeTexture() {
        return activeTexture;
    }
}
