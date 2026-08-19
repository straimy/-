package arena.forge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** World-specific readiness gate for activating Classic Arena in production. */
public final class GgoClassicReadiness {
    public static final String VERSION = "GGO-CLASSIC-READINESS-V1";
    public static final String FILE_NAME = ".ggo-classic-ready";

    private GgoClassicReadiness() {}

    public static Path marker(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }

    public static boolean ready(MinecraftServer server) {
        Path marker = marker(server);
        if (!Files.isRegularFile(marker)) return false;
        Properties props = new Properties();
        try (var in = Files.newInputStream(marker)) {
            props.load(in);
            return "PASS".equalsIgnoreCase(props.getProperty("result", ""))
                && "64".equals(props.getProperty("cells", ""))
                && "4/3/3".equals(props.getProperty("ammo", ""))
                && "4".equals(props.getProperty("health", ""))
                && Integer.parseInt(props.getProperty("respawn", "0")) > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void writePass(MinecraftServer server, int cells, int ammo1, int ammo2, int ammo3, int health, int respawn, int jumpPads) throws IOException {
        Path marker = marker(server);
        Files.createDirectories(marker.getParent());
        Properties props = new Properties();
        props.setProperty("schemaVersion", "1");
        props.setProperty("result", "PASS");
        props.setProperty("generatedAt", Instant.now().toString());
        props.setProperty("generator", ClassicArenaMapGenerator.VERSION);
        props.setProperty("cells", Integer.toString(cells));
        props.setProperty("ammo", ammo1 + "/" + ammo2 + "/" + ammo3);
        props.setProperty("health", Integer.toString(health));
        props.setProperty("respawn", Integer.toString(respawn));
        props.setProperty("jumpPads", Integer.toString(jumpPads));
        try (var out = Files.newOutputStream(marker)) {
            props.store(out, "GunGloryOnline Classic real-world readiness");
        }
    }

    public static void clear(MinecraftServer server) {
        try { Files.deleteIfExists(marker(server)); } catch (IOException ignored) {}
    }
}
