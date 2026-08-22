from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
LOOT = ROOT / "src/main/java/arena/forge/GgoLootSpawnService.java"
if not LOOT.exists():
    raise SystemExit("Stage 33: GgoLootSpawnService.java missing; apply Stage 27 first")

text = LOOT.read_text(encoding="utf-8")

imports_anchor = "import net.minecraftforge.eventbus.api.SubscribeEvent;\n"
imports = "import net.minecraftforge.event.server.ServerStoppedEvent;\nimport net.minecraftforge.server.ServerLifecycleHooks;\n"
if imports_anchor in text and "ServerStoppedEvent" not in text:
    text = text.replace(imports_anchor, imports_anchor + imports, 1)

old_active = '''        if (runtime.activeEntity != null) {
            Entity entity = level.getEntity(runtime.activeEntity);
            if (entity != null && entity.isAlive()) return;
            runtime.activeEntity = null;
            runtime.nextSpawnTick = now + point.respawnTicks();
        }

        if (now < runtime.nextSpawnTick) return;
'''
new_active = '''        if (resolveActive(server, runtime, now)) return;
        if (now < runtime.nextSpawnTick) return;
'''
if old_active in text:
    text = text.replace(old_active, new_active, 1)
elif "resolveActive(server, runtime, now)" not in text:
    raise SystemExit("Stage 33: active drop tick anchor missing")

reload_anchor = "    public static synchronized int reload() {\n"
helpers = '''    private static boolean resolveActive(MinecraftServer server, RuntimePoint runtime, long now) {
        LootPoint point = runtime.definition;
        ServerLevel level = server.getLevel(point.dimension());
        if (level == null) return false;
        if (runtime.activeEntity != null) {
            Entity entity = level.getEntity(runtime.activeEntity);
            if (matchesDrop(entity, point)) return true;
            runtime.activeEntity = null;
            runtime.nextSpawnTick = Math.max(runtime.nextSpawnTick, now + point.respawnTicks());
            return false;
        }
        if (runtime.nextSpawnTick != 0L) return false;
        ItemEntity existing = adoptExisting(level, point);
        if (existing == null) return false;
        runtime.activeEntity = existing.getUUID();
        return true;
    }

    private static ItemEntity adoptExisting(ServerLevel level, LootPoint point) {
        ItemEntity selected = null;
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof ItemEntity item)) continue;
            if (!point.id().equals(item.getPersistentData().getString("ggoLootPoint"))) continue;
            if (!matchesDrop(item, point)) {
                item.discard();
                continue;
            }
            if (selected == null) selected = item;
            else item.discard();
        }
        return selected;
    }

    private static boolean matchesDrop(Entity entity, LootPoint point) {
        if (!(entity instanceof ItemEntity item) || !item.isAlive()) return false;
        if (!point.id().equals(item.getPersistentData().getString("ggoLootPoint"))) return false;
        if (!point.kind().name().equals(item.getPersistentData().getString("ggoLootKind"))) return false;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item.getItem().getItem());
        return point.itemId().equals(itemId);
    }

    private static void reconcileExistingDrops(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ItemEntity item)) continue;
                String pointId = item.getPersistentData().getString("ggoLootPoint");
                if (pointId.isBlank()) continue;
                RuntimePoint runtime = POINTS.get(pointId);
                if (runtime == null || !runtime.definition.dimension().equals(level.dimension()) || !matchesDrop(item, runtime.definition)) {
                    item.discard();
                    continue;
                }
                if (runtime.activeEntity == null) runtime.activeEntity = item.getUUID();
                else if (!runtime.activeEntity.equals(item.getUUID())) item.discard();
            }
        }
    }

'''
if reload_anchor in text and "private static boolean resolveActive" not in text:
    text = text.replace(reload_anchor, helpers + reload_anchor, 1)
elif "private static boolean resolveActive" not in text:
    raise SystemExit("Stage 33: reload helper anchor missing")

old_reload_end = '''        LOG.info("[{}] Loaded {} typed loot points from {}", VERSION, POINTS.size(), CONFIG);
        return POINTS.size();
'''
new_reload_end = '''        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) reconcileExistingDrops(server);
        LOG.info("[{}] Loaded {} typed loot points from {}", VERSION, POINTS.size(), CONFIG);
        return POINTS.size();
'''
if old_reload_end in text and "reconcileExistingDrops(server)" not in text[text.index(reload_anchor):]:
    text = text.replace(old_reload_end, new_reload_end, 1)
elif "reconcileExistingDrops(server)" not in text:
    raise SystemExit("Stage 33: reload reconciliation anchor missing")

old_markers = '''    public static List<SupplyMarker> supplyMarkers(ResourceKey<Level> dimension) {
        if (dimension == null) return List.of();
        List<SupplyMarker> result = new ArrayList<>();
        for (RuntimePoint runtime : POINTS.values()) {
            LootPoint point = runtime.definition;
            if (point.kind() != Kind.SUPPLY || !point.dimension().equals(dimension)) continue;
            result.add(new SupplyMarker(point.id(), point.dimension(), point.x(), point.y(), point.z(), runtime.activeEntity != null));
        }
        return List.copyOf(result);
    }
'''
new_markers = '''    public static List<SupplyMarker> supplyMarkers(ResourceKey<Level> dimension) {
        if (dimension == null) return List.of();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        long now = server == null ? 0L : server.overworld().getGameTime();
        List<SupplyMarker> result = new ArrayList<>();
        for (RuntimePoint runtime : POINTS.values()) {
            LootPoint point = runtime.definition;
            if (point.kind() != Kind.SUPPLY || !point.dimension().equals(dimension)) continue;
            boolean available = server != null && resolveActive(server, runtime, now);
            result.add(new SupplyMarker(point.id(), point.dimension(), point.x(), point.y(), point.z(), available));
        }
        return List.copyOf(result);
    }
'''
if old_markers in text:
    text = text.replace(old_markers, new_markers, 1)
elif "boolean available = server != null && resolveActive" not in text:
    raise SystemExit("Stage 33: supply marker snapshot anchor missing")

commands_anchor = "    @SubscribeEvent\n    public static void commands(RegisterCommandsEvent event) {\n"
stop_hook = '''    @SubscribeEvent
    public static synchronized void serverStopped(ServerStoppedEvent event) {
        POINTS.clear();
        loaded = false;
        lastTick = Long.MIN_VALUE;
    }

'''
if commands_anchor in text and "serverStopped(ServerStoppedEvent" not in text:
    text = text.replace(commands_anchor, stop_hook + commands_anchor, 1)

LOOT.write_text(text, encoding="utf-8")

required = (
    "resolveActive(server, runtime, now)",
    "adoptExisting(level, point)",
    "reconcileExistingDrops(server)",
    "boolean available = server != null && resolveActive",
    "serverStopped(ServerStoppedEvent",
)
for marker in required:
    if marker not in text:
        raise SystemExit(f"Stage 33: marker missing: {marker}")

print("GGO Supply Lifecycle Stage 33 applied")
print(" - persisted drops are adopted after restart")
print(" - duplicate and orphan GGO loot entities are removed")
print(" - supply availability is revalidated for every map snapshot")
print(" - static loot runtime state resets when the server stops")
