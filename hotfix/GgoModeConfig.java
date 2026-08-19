package arena.forge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;
import net.minecraftforge.fml.loading.FMLPaths;

/** Runtime availability switches for GGO modes. No Core rebuild is required to promote a mode. */
public final class GgoModeConfig {
    public static final String VERSION = "GGO-MODE-CONFIG-V1";
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("gunnerarena/modes.properties");
    private static volatile Properties props = defaults();

    private GgoModeConfig() {}

    public static synchronized void reload() {
        Properties next = defaults();
        if (Files.isRegularFile(FILE)) {
            try (var in = Files.newInputStream(FILE)) { next.load(in); } catch (IOException ignored) {}
        }
        props = next;
    }

    public static GgoGameModeRegistry.Availability availability(String id, GgoGameModeRegistry.Availability fallback) {
        String raw = props.getProperty(id.toLowerCase(Locale.ROOT));
        if (raw == null || raw.isBlank()) return fallback;
        try { return GgoGameModeRegistry.Availability.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }

    private static Properties defaults() {
        Properties p = new Properties();
        p.setProperty("arena", "ACTIVE");
        p.setProperty("classic", "ACTIVE");
        p.setProperty("duels", "ACTIVE");
        p.setProperty("br", "ACTIVE");
        return p;
    }
}
