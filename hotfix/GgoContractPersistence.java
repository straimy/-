package arena.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** World-scoped durable storage for mutable contract state. */
public final class GgoContractPersistence {
    public record PlayerState(
            String trackedId,
            Map<String, Integer> progress,
            Set<String> completed,
            Set<String> rewarded
    ) {
        public static PlayerState empty() {
            return new PlayerState("", Map.of(), Set.of(), Set.of());
        }
    }

    private static final String FILE_NAME = "gungloryonline-contracts.properties";

    private GgoContractPersistence() {}

    public static synchronized PlayerState load(ServerPlayer player, Collection<GgoContractService.Contract> catalog) {
        if (player == null) return PlayerState.empty();
        Path file = file(player);
        Properties data = read(file);
        String prefix = player.getUUID() + ".";
        String tracked = cleanId(data.getProperty(prefix + "tracked", ""));
        Map<String, Integer> progress = new LinkedHashMap<>();
        Set<String> completed = new LinkedHashSet<>();
        Set<String> rewarded = new LinkedHashSet<>();

        for (GgoContractService.Contract contract : catalog) {
            String id = cleanId(contract.id());
            int current = parseInt(data.getProperty(prefix + "contract." + id + ".current"), 0);
            current = Math.max(0, Math.min(contract.target(), current));
            boolean done = Boolean.parseBoolean(data.getProperty(prefix + "contract." + id + ".completed", "false"))
                    || current >= contract.target();
            boolean paid = Boolean.parseBoolean(data.getProperty(prefix + "contract." + id + ".rewarded", "false"));
            progress.put(id, current);
            if (done) completed.add(id);
            if (paid) rewarded.add(id);
        }
        return new PlayerState(tracked, Map.copyOf(progress), Set.copyOf(completed), Set.copyOf(rewarded));
    }

    public static synchronized void save(
            ServerPlayer player,
            String trackedId,
            Collection<GgoContractService.Contract> contracts,
            Set<String> rewarded
    ) {
        if (player == null) return;
        Path file = file(player);
        Properties data = read(file);
        String prefix = player.getUUID() + ".";

        List<String> oldKeys = new ArrayList<>();
        for (String key : data.stringPropertyNames()) {
            if (key.startsWith(prefix)) oldKeys.add(key);
        }
        for (String key : oldKeys) data.remove(key);

        String tracked = cleanId(trackedId);
        if (!tracked.isBlank()) data.setProperty(prefix + "tracked", tracked);
        for (GgoContractService.Contract contract : contracts) {
            String id = cleanId(contract.id());
            data.setProperty(prefix + "contract." + id + ".current", Integer.toString(Math.max(0, contract.current())));
            data.setProperty(prefix + "contract." + id + ".completed", Boolean.toString(contract.completed()));
            data.setProperty(prefix + "contract." + id + ".rewarded", Boolean.toString(rewarded != null && rewarded.contains(id)));
        }
        writeAtomic(file, data);
    }

    private static Path file(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) throw new IllegalStateException("Server player has no MinecraftServer");
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME);
    }

    private static Properties read(Path file) {
        Properties data = new Properties();
        try {
            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    data.load(in);
                }
            }
        } catch (Exception ignored) {
            // Keep the runtime usable. A later successful save writes valid state atomically.
        }
        return data;
    }

    private static void writeAtomic(Path file, Properties data) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                data.store(out, "GunGloryOnline contract state v1");
            }
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
            // Runtime state remains authoritative for this process; the next mutation retries persistence.
        }
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (RuntimeException ignored) { return fallback; }
    }

    private static String cleanId(String value) {
        if (value == null) return "";
        String clean = value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return clean.length() > 48 ? clean.substring(0, 48) : clean;
    }
}
