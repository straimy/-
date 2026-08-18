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
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Typed loot points for Battle Royale.
 *
 * Maps expose marker tags only (br_loot_common / military / medical / rare). Concrete item IDs and
 * weights live in config/gunnerarena/br-loot.properties, so balance changes never require map edits.
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

    private BattleRoyaleLootService() {}

    public static synchronized int prepareRound(ServerLevel level) {
        load();
        int spawned = 0;
        List<Marker> markers = level.getEntities(EntityType.MARKER, WORLD_SCAN, marker -> marker.getTags().stream().anyMatch(BattleRoyaleLootService::isLootTag));
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
        for (Tier tier : Tier.values()) TABLES.put(tier, parse(props.getProperty(tier.name().toLowerCase(Locale.ROOT), "")));
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
