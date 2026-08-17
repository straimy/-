package arena.forge;

import com.mojang.brigadier.Command;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Server-owned loot spawn authority replacing legacy command-block item spawners.
 *
 * Maps provide declarative points only. The server owns spawn cadence and item validation.
 * No arbitrary Minecraft commands are stored or executed by this service.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoLootSpawnService {
    public static final String VERSION = "GGO-LOOT-V1";
    private static final Logger LOG = LogUtils.getLogger();
    private static final Path CONFIG = FMLPaths.CONFIGDIR.get().resolve("gunnerarena/loot-spawns.properties");
    private static final Map<String, RuntimePoint> POINTS = new HashMap<>();
    private static long lastTick = Long.MIN_VALUE;
    private static boolean loaded;

    public enum Kind { AMMO, HEALTH, WEAPON, UTILITY }

    public record LootPoint(
        String id,
        Kind kind,
        ResourceKey<Level> dimension,
        double x,
        double y,
        double z,
        ResourceLocation itemId,
        int count,
        long respawnTicks
    ) {}

    private static final class RuntimePoint {
        final LootPoint definition;
        UUID activeEntity;
        long nextSpawnTick;

        RuntimePoint(LootPoint definition) {
            this.definition = definition;
        }
    }

    private GgoLootSpawnService() {}

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        if (now == lastTick) return;
        lastTick = now;
        if (!loaded) reload();
        for (RuntimePoint runtime : List.copyOf(POINTS.values())) tickPoint(server, runtime, now);
    }

    private static void tickPoint(MinecraftServer server, RuntimePoint runtime, long now) {
        LootPoint point = runtime.definition;
        ServerLevel level = server.getLevel(point.dimension());
        if (level == null) return;

        if (runtime.activeEntity != null) {
            Entity entity = level.getEntity(runtime.activeEntity);
            if (entity != null && entity.isAlive()) return;
            runtime.activeEntity = null;
            runtime.nextSpawnTick = now + point.respawnTicks();
        }

        if (now < runtime.nextSpawnTick) return;
        if (!BuiltInRegistries.ITEM.containsKey(point.itemId())) {
            runtime.nextSpawnTick = now + Math.max(200L, point.respawnTicks());
            LOG.warn("[{}] Unknown item {} at point {}", VERSION, point.itemId(), point.id());
            return;
        }

        Item item = BuiltInRegistries.ITEM.get(point.itemId());
        if (item == null) return;
        ItemStack stack = new ItemStack(item, point.count());
        ItemEntity drop = new ItemEntity(level, point.x(), point.y(), point.z(), stack);
        drop.setDeltaMovement(0.0D, 0.0D, 0.0D);
        drop.setPickUpDelay(10);
        drop.getPersistentData().putString("ggoLootPoint", point.id());
        drop.getPersistentData().putString("ggoLootKind", point.kind().name());
        level.addFreshEntity(drop);
        runtime.activeEntity = drop.getUUID();
    }

    public static synchronized int reload() {
        POINTS.clear();
        loaded = true;
        if (!Files.isRegularFile(CONFIG)) {
            LOG.info("[{}] No loot config yet: {}", VERSION, CONFIG);
            return 0;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(CONFIG)) {
            properties.load(reader);
        } catch (IOException e) {
            LOG.error("[{}] Failed reading {}", VERSION, CONFIG, e);
            return 0;
        }

        int count = parseInt(properties.getProperty("count", "0"), 0);
        for (int i = 0; i < count; i++) {
            String prefix = "point." + i + ".";
            try {
                LootPoint point = parsePoint(properties, prefix, i);
                POINTS.put(point.id(), new RuntimePoint(point));
            } catch (RuntimeException e) {
                LOG.warn("[{}] Ignoring invalid loot point {}: {}", VERSION, i, e.getMessage());
            }
        }
        LOG.info("[{}] Loaded {} typed loot points from {}", VERSION, POINTS.size(), CONFIG);
        return POINTS.size();
    }

    private static LootPoint parsePoint(Properties p, String prefix, int index) {
        String id = required(p, prefix + "id");
        Kind kind = Kind.valueOf(required(p, prefix + "kind").toUpperCase(Locale.ROOT));
        ResourceLocation dimensionId = ResourceLocation.tryParse(p.getProperty(prefix + "dimension", "minecraft:overworld"));
        if (dimensionId == null) throw new IllegalArgumentException("bad dimension");
        ResourceLocation itemId = ResourceLocation.tryParse(required(p, prefix + "item"));
        if (itemId == null) throw new IllegalArgumentException("bad item id");
        double x = Double.parseDouble(required(p, prefix + "x"));
        double y = Double.parseDouble(required(p, prefix + "y"));
        double z = Double.parseDouble(required(p, prefix + "z"));
        int itemCount = Math.max(1, Math.min(64, parseInt(p.getProperty(prefix + "count", "1"), 1)));
        long respawn = Math.max(20L, parseLong(p.getProperty(prefix + "respawnTicks", "200"), 200L));
        ResourceKey<Level> dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId);
        return new LootPoint(id, kind, dimension, x, y, z, itemId, itemCount, respawn);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + key);
        return value.trim();
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (Exception ignored) { return fallback; }
    }

    private static long parseLong(String value, long fallback) {
        try { return Long.parseLong(value.trim()); } catch (Exception ignored) { return fallback; }
    }

    public static List<LootPoint> points() {
        List<LootPoint> result = new ArrayList<>();
        for (RuntimePoint value : POINTS.values()) result.add(value.definition);
        return List.copyOf(result);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("ggo")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("loot")
                    .then(Commands.literal("reload").executes(ctx -> {
                        int count = reload();
                        ctx.getSource().sendSuccess(() -> Component.literal("[GGO] Loot points reloaded: " + count).withStyle(ChatFormatting.GREEN), false);
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(Commands.literal("status").executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal("[GGO] " + VERSION + " points=" + POINTS.size() + " config=" + CONFIG).withStyle(ChatFormatting.AQUA), false);
                        return Command.SINGLE_SUCCESS;
                    })))
        );
    }
}
