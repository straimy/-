package arena.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.UUID;

/**
 * Compatibility bridge between today's Minecraft identity and GunGloryOnline's own account identity.
 *
 * Minecraft UUID remains the key for existing profile files in this release so no progress is migrated
 * or lost. New systems may use idFor(player) as the stable GGO identity. A future standalone login
 * service can bind directly to the same GGO UUID without changing gameplay APIs again.
 */
public final class GgoIdentityBridge {
    private static final Object LOCK = new Object();
    private static final Properties IDS = new Properties();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("gunnerarena").resolve("ggo-identities.properties");
    private static boolean loaded;

    private GgoIdentityBridge() {}

    public static UUID idFor(ServerPlayer player) {
        if (player == null) return new UUID(0L, 0L);
        return idForMinecraft(player.getUUID());
    }

    public static UUID idForMinecraft(UUID minecraftId) {
        if (minecraftId == null) return new UUID(0L, 0L);
        synchronized (LOCK) {
            load();
            String key = "minecraft." + minecraftId;
            String value = IDS.getProperty(key);
            if (value != null) {
                try { return UUID.fromString(value); }
                catch (IllegalArgumentException ignored) { /* regenerate corrupt mapping */ }
            }
            UUID ggo = UUID.randomUUID();
            IDS.setProperty(key, ggo.toString());
            save();
            return ggo;
        }
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(FILE)) return;
        try (InputStream in = Files.newInputStream(FILE)) { IDS.load(in); }
        catch (IOException ex) { System.err.println("[GGO] identity map load failed: " + ex.getMessage()); }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) { IDS.store(out, "GunGloryOnline stable account identities"); }
            try { Files.move(tmp, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ex) {
            System.err.println("[GGO] identity map save failed: " + ex.getMessage());
        }
    }
}
