package arena.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Resolves public GGO cosmetics for other players without touching auth or gameplay state. */
public final class GgoNetworkSkinRuntime {
    private static final Map<UUID, ResourceLocation> TEXTURES = new HashMap<>();
    private static final Map<UUID, CompletableFuture<GgoSkinResolver.ResolvedSkin>> PENDING = new HashMap<>();
    private static final Set<UUID> RESOLVED = new HashSet<>();
    private static int tickCounter;

    private GgoNetworkSkinRuntime() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clear();
            return;
        }
        if (++tickCounter % 20 != 0) return;

        Set<UUID> present = new HashSet<>();
        for (AbstractClientPlayer player : mc.level.players()) {
            UUID id = player.getUUID();
            if (id.equals(mc.player.getUUID())) continue;
            present.add(id);
            if (!RESOLVED.contains(id) && !PENDING.containsKey(id)) {
                PENDING.put(id, GgoSkinResolver.resolveMinecraft(id));
            }
        }

        TEXTURES.keySet().retainAll(present);
        RESOLVED.retainAll(present);
        PENDING.keySet().retainAll(present);

        for (UUID id : new HashSet<>(PENDING.keySet())) {
            CompletableFuture<GgoSkinResolver.ResolvedSkin> future = PENDING.get(id);
            if (future == null || !future.isDone()) continue;
            PENDING.remove(id);
            RESOLVED.add(id);
            GgoSkinResolver.ResolvedSkin resolved;
            try {
                resolved = future.getNow(GgoSkinResolver.ResolvedSkin.defaultSkin());
            } catch (RuntimeException ignored) {
                continue;
            }
            if (!resolved.hasGgoTexture()) continue;

            try (InputStream input = Files.newInputStream(resolved.file())) {
                NativeImage image = NativeImage.read(input);
                if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                    image.close();
                    continue;
                }
                ResourceLocation location = new ResourceLocation("gunnerarena_ui", "ggo_skin/remote_" + resolved.hash());
                mc.getTextureManager().register(location, new DynamicTexture(image));
                TEXTURES.put(id, location);
            } catch (IOException | RuntimeException ignored) {
                // Keep the normal Minecraft skin on any failure.
            }
        }
    }

    public static ResourceLocation texture(UUID minecraftPlayerId) {
        return minecraftPlayerId == null ? null : TEXTURES.get(minecraftPlayerId);
    }

    public static void clear() {
        TEXTURES.clear();
        PENDING.clear();
        RESOLVED.clear();
        tickCounter = 0;
    }
}
