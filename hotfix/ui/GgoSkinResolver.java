package arena.client.ui;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;

/**
 * First-party GGO skin metadata/download cache.
 *
 * This class intentionally does not hook PlayerRenderer yet. It establishes a Minecraft-independent
 * profile -> immutable skin asset pipeline that a later render hook (or native client) can reuse.
 */
public final class GgoSkinResolver {
    public static final String DEFAULT_API_BASE = "https://ggo.kvicloud.ru/api/v1";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private GgoSkinResolver() {}

    public static CompletableFuture<ResolvedSkin> resolve(UUID ggoPlayerId) {
        return resolve(DEFAULT_API_BASE, ggoPlayerId);
    }

    static CompletableFuture<ResolvedSkin> resolve(String apiBase, UUID ggoPlayerId) {
        if (ggoPlayerId == null) {
            return CompletableFuture.completedFuture(ResolvedSkin.defaultSkin());
        }

        String endpoint = apiBase.replaceAll("/+$", "") + "/players/" + ggoPlayerId + "/skin";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(6))
            .header("Accept", "application/json")
            .GET()
            .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenCompose(response -> {
                if (response.statusCode() != 200) {
                    return CompletableFuture.completedFuture(ResolvedSkin.defaultSkin());
                }
                final SkinMetadata metadata;
                try {
                    metadata = GSON.fromJson(response.body(), SkinMetadata.class);
                } catch (JsonSyntaxException ex) {
                    return CompletableFuture.completedFuture(ResolvedSkin.defaultSkin());
                }
                if (metadata == null || !"ggo".equals(metadata.source)
                    || !isSha256(metadata.skin_hash) || metadata.skin_url == null || metadata.skin_url.isBlank()) {
                    return CompletableFuture.completedFuture(new ResolvedSkin(
                        metadata == null || metadata.source == null ? "default" : metadata.source,
                        null,
                        null
                    ));
                }
                return downloadAndVerify(metadata);
            })
            .exceptionally(error -> ResolvedSkin.defaultSkin());
    }

    private static CompletableFuture<ResolvedSkin> downloadAndVerify(SkinMetadata metadata) {
        Path cacheDir = Minecraft.getInstance().gameDirectory.toPath().resolve("ggo-cache").resolve("skins");
        Path target = cacheDir.resolve(metadata.skin_hash + ".png");

        try {
            Files.createDirectories(cacheDir);
            if (Files.isRegularFile(target) && metadata.skin_hash.equalsIgnoreCase(sha256(target))) {
                return CompletableFuture.completedFuture(new ResolvedSkin("ggo", metadata.skin_hash, target));
            }
        } catch (IOException ignored) {
            return CompletableFuture.completedFuture(ResolvedSkin.defaultSkin());
        }

        HttpRequest request;
        try {
            URI uri = URI.create(metadata.skin_url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return CompletableFuture.completedFuture(ResolvedSkin.defaultSkin());
            }
            request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "image/png")
                .GET()
                .build();
        } catch (IllegalArgumentException ex) {
            return CompletableFuture.completedFuture(ResolvedSkin.defaultSkin());
        }

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
            .thenApply(response -> {
                if (response.statusCode() != 200 || response.body().length == 0 || response.body().length > 512 * 1024) {
                    return ResolvedSkin.defaultSkin();
                }
                String digest = sha256(response.body());
                if (!metadata.skin_hash.equalsIgnoreCase(digest)) {
                    return ResolvedSkin.defaultSkin();
                }
                try {
                    Path temp = Files.createTempFile(cacheDir, metadata.skin_hash, ".tmp");
                    Files.write(temp, response.body());
                    try {
                        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException atomicMoveUnsupported) {
                        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    return new ResolvedSkin("ggo", metadata.skin_hash, target);
                } catch (IOException error) {
                    return ResolvedSkin.defaultSkin();
                }
            })
            .exceptionally(error -> ResolvedSkin.defaultSkin());
    }

    private static boolean isSha256(String value) {
        return value != null && value.matches("(?i)^[0-9a-f]{64}$");
    }

    private static String sha256(Path path) throws IOException {
        return sha256(Files.readAllBytes(path));
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static final class SkinMetadata {
        String source;
        String skin_hash;
        String skin_url;
    }

    public record ResolvedSkin(String source, String hash, Path file) {
        public static ResolvedSkin defaultSkin() {
            return new ResolvedSkin("default", null, null);
        }

        public boolean hasGgoTexture() {
            return "ggo".equals(source) && hash != null && file != null;
        }
    }
}
