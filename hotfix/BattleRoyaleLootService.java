package arena.forge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Typed loot points for Battle Royale.
 *
 * Maps may expose marker tags (br_loot_common / military / medical / rare). When they do not,
 * GGO Core creates a small deterministic marker layout around the match center. Concrete item IDs
 * and weights live in config/gunnerarena/br-loot.properties, with built-in defaults for first boot.
 */
public final class BattleRoyaleLootService {
    public static final String VERSION = "GGO-BR-LOOT-V1";

    public enum Tier {
        COMMON("br_loot_common"),
        MILITARY("br_loot_military"),
        MEDICAL("br_loot_medical"),
        RARE("br_loot_rare");

        final String markerTag;
        Tier(String markerTag) { this.markerTag = markerTag; }
    }

    private static final AABB WORLD_SCAN = new AABB(-4096, -64, -4096, 4096, 384, 4096);
    private static final Path CONFIG = FMLPaths.CONFIGDIR.get().resolve("gunnerarena/br-loot.properties");
    private static final Map<Tier, List<Entry>> TABLES = new HashMap<>();
    private static final Map<Tier, String> DEFAULT_TABLES = Map.of(
        Tier.COMMON, "minecraft:bread|6|1|3,minecraft:arrow|5|4|12,jeg:pistol_ammo|5|10|30",
        Tier.MILITARY, "jeg:semi_auto_pistol|5|1|1,jeg:custom_smg|3|1|1,jeg:pump_shotgun|2|1|1,jeg:rifle_ammo|4|10|30",
        Tier.MEDICAL, "minecraft:golden_apple|2|1|1,minecraft:cooked_beef|5|2|5",
        Tier.RARE, "jeg:bolt_action_rifle|4|1|1,jeg:double_barrel_shotgun|3|1|1,jeg:shotgun_shell|5|8|20"
    );

    private BattleRoyaleLootService() {}

    public static synchronized int prepareRound(ServerLevel level) {
        return prepareRound(level, 0.0D, 0.0D);
    }

    public static synchronized int prepareRound(ServerLevel level, double centerX, double centerZ) {
        load();
        ensureDefaultLayout(level, centerX, centerZ);
        int spawned = 0;
        List<Marker> markers = lootMarkers(level);
        for (Marker marker : markers) {
            Tier tier = tierOf(marker);
            if (tier == null) continue;
            Entry entry = choose(level, TABLES.getOrDefault(tier, List.of()));
            if (entry == null) continue;
            Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(entry.itemId));
            if (item == null) continue;
            int count = entry.minCount == entry.maxCount ? entry.minCount : level.getRandom().nextInt(entry.minCount, entry.maxCount + 1);
            ItemEntity entity = new ItemEntity(level, marker.getX(), marker.getY() + 0.35D, marker.getZ(), new ItemStack(item, count));
            entity.setDefaultPickUpDelay();
            entity.addTag("ggo_br_loot");
            level.addFreshEntity(entity);
            spawned++;
        }
        return spawned;
    }

    public static void cleanup(ServerLevel level) {
        for (ItemEntity item : level.getEntities(EntityType.ITEM, WORLD_SCAN, e -> e.getTags().contains("ggo_br_loot"))) item.discard();
    }

    /** Creates 16 typed fallback loot points only when the map has no BR loot markers at all. */
    public static synchronized boolean ensureDefaultLayout(ServerLevel level, double centerX, double centerZ) {
        if (!lootMarkers(level).isEmpty()) return false;

        createRing(level, Tier.COMMON, centerX, centerZ, 35.0D, 6);
        createRing(level, Tier.MEDICAL, centerX, centerZ, 60.0D, 4);
        createRing(level, Tier.MILITARY, centerX, centerZ, 95.0D, 4);
        createRing(level, Tier.RARE, centerX, centerZ, 130.0D, 2);
        return true;
    }

    public static int markerCount(ServerLevel level) {
        return lootMarkers(level).size();
    }

    private static List<Marker> lootMarkers(ServerLevel level) {
        return level.getEntities(EntityType.MARKER, WORLD_SCAN, marker -> marker.getTags().stream().anyMatch(BattleRoyaleLootService::isLootTag));
    }

    private static void createRing(ServerLevel level, Tier tier, double centerX, double centerZ, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double angle = (Math.PI * 2.0D * i / count) + (tier.ordinal() * 0.31D);
            double x = centerX + Math.cos(angle) * radius;
            double z = centerZ + Math.sin(angle) * radius;
            int blockX = (int)Math.floor(x);
            int blockZ = (int)Math.floor(z);
            double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ) + 1.0D;
            createMarker(level, tier.markerTag, x, y, z);
        }
    }

    private static void createMarker(ServerLevel level, String tag, double x, double y, double z) {
        Marker marker = EntityType.MARKER.create(level);
        if (marker == null) throw new IllegalStateException("Could not create BR loot marker " + tag);
        marker.setPos(x, y, z);
        marker.addTag(tag);
        marker.addTag("ggo_br_layout");
        if (!level.addFreshEntity(marker)) throw new IllegalStateException("Could not add BR loot marker " + tag);
    }

    private static boolean isLootTag(String tag) {
        for (Tier tier : Tier.values()) if (tier.markerTag.equals(tag)) return true;
        return false;
    }

    private static Tier tierOf(Marker marker) {
        for (Tier tier : Tier.values()) if (marker.getTags().contains(tier.markerTag)) return tier;
        return null;
    }

    private static Entry choose(ServerLevel level, List<Entry> entries) {
        int total = entries.stream().mapToInt(e -> e.weight).sum();
        if (total <= 0) return null;
        int roll = level.getRandom().nextInt(total);
        for (Entry entry : entries) {
            roll -= entry.weight;
            if (roll < 0) return entry;
        }
        return null;
    }

    private static synchronized void load() {
        TABLES.clear();
        Properties props = new Properties();
        if (Files.isRegularFile(CONFIG)) {
            try (var in = Files.newInputStream(CONFIG)) { props.load(in); } catch (IOException ignored) {}
        }
        for (Tier tier : Tier.values()) {
            String key = tier.name().toLowerCase(Locale.ROOT);
            TABLES.put(tier, parse(props.getProperty(key, DEFAULT_TABLES.get(tier))));
        }
    }

    private static List<Entry> parse(String raw) {
        List<Entry> out = new ArrayList<>();
        for (String token : raw.split(",")) {
            String value = token.trim();
            if (value.isEmpty()) continue;
            String[] p = value.split("\\|");
            if (p.length < 2 || ResourceLocation.tryParse(p[0]) == null) continue;
            try {
                int weight = Math.max(1, Integer.parseInt(p[1]));
                int min = p.length >= 3 ? Math.max(1, Integer.parseInt(p[2])) : 1;
                int max = p.length >= 4 ? Math.max(min, Integer.parseInt(p[3])) : min;
                out.add(new Entry(p[0], weight, min, max));
            } catch (NumberFormatException ignored) {}
        }
        return List.copyOf(out);
    }

    private record Entry(String itemId, int weight, int minCount, int maxCount) {}
}
