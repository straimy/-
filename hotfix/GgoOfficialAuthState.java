package arena.forge;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative bridge between the short-lived launcher ticket and the existing GGO AuthGate.
 * Raw tickets are never stored here. Only the verified account profile remains for the life of the connection.
 */
public final class GgoOfficialAuthState {
    public static final String AUTH_TAG = "sauth_authenticated";
    private static final Map<UUID, VerifiedProfile> VERIFIED = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> PENDING = new ConcurrentHashMap<>();

    private GgoOfficialAuthState() {}

    public static boolean required() {
        String key = System.getenv("GGO_SERVER_KEY");
        return key != null && !key.isBlank();
    }

    static String serverKey() {
        String key = System.getenv("GGO_SERVER_KEY");
        return key == null ? "" : key.trim();
    }

    static String authApiUrl() {
        String configured = System.getenv("GGO_AUTH_API_URL");
        String value = configured == null || configured.isBlank()
                ? "https://ggo.kvicloud.ru/api/v1"
                : configured.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    public static boolean beginVerification(ServerPlayer player) {
        if (player == null || isAuthenticated(player)) return false;
        return PENDING.putIfAbsent(player.getUUID(), Boolean.TRUE) == null;
    }

    public static void verificationFailed(ServerPlayer player) {
        if (player != null) PENDING.remove(player.getUUID());
    }

    public static void bind(ServerPlayer player, String accountId, String displayName, String skinSource) {
        if (player == null) throw new IllegalArgumentException("player is required");
        UUID accountUuid = GgoIdentityBridge.bindAuthenticated(player, accountId, displayName);
        String safeName = displayName == null || displayName.isBlank()
                ? player.getGameProfile().getName()
                : displayName.trim();
        String safeSkin = skinSource == null || skinSource.isBlank() ? "default" : skinSource.trim();
        VERIFIED.put(player.getUUID(), new VerifiedProfile(accountUuid, accountId, safeName, safeSkin));
        PENDING.remove(player.getUUID());
        player.addTag(AUTH_TAG);
        if (player.server != null) player.server.getCommands().sendCommands(player);
    }

    public static boolean isAuthenticated(ServerPlayer player) {
        return player != null && VERIFIED.containsKey(player.getUUID());
    }

    public static VerifiedProfile profile(ServerPlayer player) {
        return player == null ? null : VERIFIED.get(player.getUUID());
    }

    public static void clear(ServerPlayer player) {
        if (player == null) return;
        UUID id = player.getUUID();
        VERIFIED.remove(id);
        PENDING.remove(id);
        GgoIdentityBridge.clearAuthenticated(player);
        player.removeTag(AUTH_TAG);
    }

    public record VerifiedProfile(UUID accountUuid, String accountId, String displayName, String skinSource) {}
}
