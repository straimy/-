package arena.client.ui;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/** Public, non-secret identity handoff written by the GGO launcher before PLAY. */
public final class GgoIdentityBridge {
    private static final Gson GSON = new Gson();
    private static final String FILE_NAME = ".ggo-profile.json";

    private GgoIdentityBridge() {}

    public static Identity current() {
        Path path = Minecraft.getInstance().gameDirectory.toPath().resolve(FILE_NAME);
        if (!Files.isRegularFile(path)) return Identity.guest();
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            IdentityFile raw = GSON.fromJson(json, IdentityFile.class);
            if (raw == null || raw.schemaVersion != 1) return Identity.guest();
            String name = raw.displayName == null ? "Guest" : raw.displayName.trim();
            if (name.isEmpty() || name.length() > 32) return Identity.guest();
            String source = validSkinSource(raw.skinSource) ? raw.skinSource : "default";
            String provider = validProvider(raw.provider) ? raw.provider : "guest";
            UUID playerId = null;
            if (raw.ggoPlayerId != null && !raw.ggoPlayerId.isBlank()) {
                try { playerId = UUID.fromString(raw.ggoPlayerId); }
                catch (IllegalArgumentException ignored) { return Identity.guest(); }
            }
            if ("ggo".equals(provider) && playerId == null) return Identity.guest();
            return new Identity(playerId, name, source, provider);
        } catch (IOException | JsonSyntaxException ignored) {
            return Identity.guest();
        }
    }

    private static boolean validSkinSource(String value) {
        return "ggo".equals(value) || "microsoft".equals(value) || "default".equals(value);
    }

    private static boolean validProvider(String value) {
        return "ggo".equals(value) || "microsoft".equals(value) || "guest".equals(value);
    }

    private static final class IdentityFile {
        int schemaVersion;
        String ggoPlayerId;
        String displayName;
        String skinSource;
        String provider;
    }

    public record Identity(UUID ggoPlayerId, String displayName, String skinSource, String provider) {
        public static Identity guest() { return new Identity(null, "Guest", "default", "guest"); }
        public boolean wantsGgoSkin() { return ggoPlayerId != null && "ggo".equals(skinSource); }
    }
}
