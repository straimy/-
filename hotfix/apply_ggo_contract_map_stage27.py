from pathlib import Path
import shutil

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
TARGET = ROOT / "src/main/java/arena/forge"
TARGET.mkdir(parents=True, exist_ok=True)

# Start from the known server-owned Runtime v1 implementations.
shutil.copy2(Path("hotfix/GgoLootSpawnService.java"), TARGET / "GgoLootSpawnService.java")
shutil.copy2(Path("hotfix/GgoContractMapNetwork.java"), TARGET / "GgoContractMapNetwork.java")

loot_path = TARGET / "GgoLootSpawnService.java"
loot = loot_path.read_text(encoding="utf-8")

old_enum = "public enum Kind { AMMO, HEALTH, WEAPON, UTILITY }"
new_enum = "public enum Kind { AMMO, HEALTH, WEAPON, UTILITY, SUPPLY }"
if old_enum in loot:
    loot = loot.replace(old_enum, new_enum, 1)
elif new_enum not in loot:
    raise SystemExit("Stage 27: GgoLootSpawnService Kind anchor missing")

old_stack = "        ItemStack stack = new ItemStack(item, point.count());\n"
new_stack = '''        ItemStack stack = new ItemStack(item, point.count());
        if (point.kind() == Kind.SUPPLY) {
            GgoSupplyExtractionService.markSupply(stack);
            stack.getOrCreateTag().putString("ggo_supply_point", point.id());
        }
'''
if old_stack in loot and "ggo_supply_point" not in loot:
    loot = loot.replace(old_stack, new_stack, 1)
elif "ggo_supply_point" not in loot:
    raise SystemExit("Stage 27: loot stack anchor missing")

commands_anchor = "    @SubscribeEvent\n    public static void commands(RegisterCommandsEvent event) {\n"
marker_api = '''    public record SupplyMarker(String id, ResourceKey<Level> dimension, double x, double y, double z, boolean available) {}

    /** Current, server-owned supply marker snapshot for one dimension. */
    public static List<SupplyMarker> supplyMarkers(ResourceKey<Level> dimension) {
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
if commands_anchor in loot and "record SupplyMarker" not in loot:
    loot = loot.replace(commands_anchor, marker_api + commands_anchor, 1)
elif "record SupplyMarker" not in loot:
    raise SystemExit("Stage 27: loot commands anchor missing")

loot_path.write_text(loot, encoding="utf-8")

extraction_path = TARGET / "GgoSupplyExtractionService.java"
extraction = extraction_path.read_text(encoding="utf-8")
point_anchor = "    private static int consumeSupplies(ServerPlayer p,int limit){\n"
point_api = '''    /** Read-only extraction marker used by the Stage 27 map snapshot. */
    public static ExtractionMarker marker(ResourceKey<Level> dimension){
        if(dimension==null)return null;
        Point point=point(dimension);
        return point==null?null:new ExtractionMarker(dimension.location().toString(),point.x(),point.y(),point.z(),EXTRACTION_RADIUS);
    }

    public record ExtractionMarker(String dimension,double x,double y,double z,double radius){}

'''
if point_anchor in extraction and "record ExtractionMarker" not in extraction:
    extraction = extraction.replace(point_anchor, point_api + point_anchor, 1)
elif "record ExtractionMarker" not in extraction:
    raise SystemExit("Stage 27: extraction consume anchor missing")
extraction_path.write_text(extraction, encoding="utf-8")

print("GGO Contracts Stage 27 server applied")
print(" - real SUPPLY kind in server loot points")
print(" - only spawned tagged supplies are map-visible")
print(" - authoritative extraction marker")
print(" - live Runtime v1 credit balance snapshot")
