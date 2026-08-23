package arena.forge;

import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Server-side allow-list policy for official client build metadata.
 *
 * Phase 1 is observation/report-only unless GGO_ENFORCE_CLIENT_BUILD=1 is explicitly configured.
 * Client-provided metadata is never treated as authentication by itself; launcher ticket auth remains required.
 */
public final class GgoClientBuildPolicy {
    public static final String VERSION = "GGO-BUILD-POLICY-V1";
    private static final int MAX_BUILD_ID = 96;
    private static final int SHA256_HEX_LENGTH = 64;
    private static final Set<String> ALLOWED_BUILD_IDS = parseCsv(System.getenv("GGO_ALLOWED_CLIENT_BUILDS"));
    private static final Set<String> ALLOWED_CORE_SHA256 = parseHashes(System.getenv("GGO_ALLOWED_CORE_SHA256"));
    private static final Set<String> ALLOWED_UI_SHA256 = parseHashes(System.getenv("GGO_ALLOWED_UI_SHA256"));
    private static final boolean ENFORCE = "1".equals(System.getenv("GGO_ENFORCE_CLIENT_BUILD"));

    private GgoClientBuildPolicy() {}

    public static Result evaluate(ServerPlayer player, String buildId, String coreSha256, String uiSha256) {
        String build = sanitizeBuild(buildId);
        String core = sanitizeHash(coreSha256);
        String ui = sanitizeHash(uiSha256);

        boolean buildKnown = ALLOWED_BUILD_IDS.isEmpty() || ALLOWED_BUILD_IDS.contains(build);
        boolean coreKnown = ALLOWED_CORE_SHA256.isEmpty() || ALLOWED_CORE_SHA256.contains(core);
        boolean uiKnown = ALLOWED_UI_SHA256.isEmpty() || ALLOWED_UI_SHA256.contains(ui);
        boolean metadataPresent = !build.isEmpty() && !core.isEmpty() && !ui.isEmpty();
        boolean accepted = metadataPresent && buildKnown && coreKnown && uiKnown;

        if (!accepted && player != null) {
            String detail = "build=" + printable(build)
                    + " core=" + shortHash(core)
                    + " ui=" + shortHash(ui)
                    + " metadata=" + metadataPresent
                    + " allowBuild=" + buildKnown
                    + " allowCore=" + coreKnown
                    + " allowUi=" + uiKnown
                    + " enforce=" + ENFORCE;
            GgoAntiCheatEvidence.record(player, GgoAntiCheatEvidence.Kind.CLIENT_INTEGRITY, ENFORCE ? 12.0D : 2.0D, detail);
        }
        return new Result(accepted, ENFORCE, build, core, ui);
    }

    public static boolean enforcementEnabled() {
        return ENFORCE;
    }

    private static Set<String> parseCsv(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) return out;
        Arrays.stream(raw.split(","))
                .map(GgoClientBuildPolicy::sanitizeBuild)
                .filter(v -> !v.isEmpty())
                .forEach(out::add);
        return out;
    }

    private static Set<String> parseHashes(String raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) return out;
        Arrays.stream(raw.split(","))
                .map(GgoClientBuildPolicy::sanitizeHash)
                .filter(v -> !v.isEmpty())
                .forEach(out::add);
        return out;
    }

    private static String sanitizeBuild(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.length() > MAX_BUILD_ID) return "";
        return value.matches("[A-Za-z0-9._:-]+") ? value : "";
    }

    private static String sanitizeHash(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return value.length() == SHA256_HEX_LENGTH && value.matches("[0-9a-f]{64}") ? value : "";
    }

    private static String printable(String value) {
        return value == null || value.isEmpty() ? "missing" : value;
    }

    private static String shortHash(String value) {
        return value == null || value.length() < 12 ? "missing" : value.substring(0, 12);
    }

    public record Result(boolean accepted, boolean enforced, String buildId, String coreSha256, String uiSha256) {}
}
