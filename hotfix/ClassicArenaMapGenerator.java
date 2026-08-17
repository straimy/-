package arena.forge;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

/**
 * Direct server-side generator for the recovered 8x8 Classic Arena.
 *
 * This replaces the legacy redstone/command-block/scoreboard generator. Templates remain map
 * content under world/generated/minecraft/structures, but selection, quotas, rotations, cleanup and
 * placement are owned by versioned GGO Java code.
 */
public final class ClassicArenaMapGenerator {
    public static final String VERSION = "GGO-CLASSIC-GEN-V1";
    public static final int GRID_SIZE = 8;
    public static final int TOTAL_CELLS = GRID_SIZE * GRID_SIZE;
    public static final int EMPTY_CELLS = 16;
    public static final int GUN_CELLS = 10;
    public static final int HEALTH_CELLS = 4;
    public static final int FLAT_CELLS = TOTAL_CELLS - EMPTY_CELLS - GUN_CELLS - HEALTH_CELLS;

    // Recovered from the original 64 structure-block grid.
    private static final int GRID_MIN_X = 48;
    private static final int GRID_MIN_Z = 48;
    private static final int STRUCTURE_BLOCK_X = 52;
    private static final int STRUCTURE_BLOCK_Z = 51;
    private static final int STRUCTURE_BLOCK_Y = 67;
    private static final int PLACE_Y = 70;
    private static final int CELL_SIZE = 8;
    private static final int CELL_HEIGHT = 32;
    private static final AABB ARENA_ENTITIES = new AABB(47.0D, 68.0D, 47.0D, 113.0D, 105.0D, 113.0D);

    private static final Logger LOG = LogUtils.getLogger();

    public enum State { IDLE, GENERATING, READY, ERROR }
    public enum CellKind { FLAT, EMPTY, GUN, HEALTH }

    public record GenerationSnapshot(
        State state,
        int placed,
        int empty,
        int guns,
        int health,
        String error
    ) {}

    private State state = State.IDLE;
    private int placed;
    private final EnumMap<CellKind, Integer> counts = new EnumMap<>(CellKind.class);
    private String error = "";

    public ClassicArenaMapGenerator() {
        resetCounters();
    }

    public synchronized boolean generate(ServerLevel level) {
        state = State.GENERATING;
        placed = 0;
        error = "";
        resetCounters();

        try {
            cleanupLegacyEntities(level);
            List<CellKind> plan = buildPlan(level);
            for (int index = 0; index < TOTAL_CELLS; index++) {
                int row = index / GRID_SIZE;
                int col = index % GRID_SIZE;
                CellKind kind = plan.get(index);
                Rotation rotation = randomRotation(level);
                ResourceLocation templateId = chooseTemplate(level, kind);

                clearCell(level, col, row);
                placeTemplate(level, templateId, col, row, rotation);
                counts.merge(kind, 1, Integer::sum);
                placed++;
            }
            state = State.READY;
            LOG.info("[{}] Generated Classic Arena cells={} flat={} empty={} gun={} health={}",
                VERSION, placed, count(CellKind.FLAT), count(CellKind.EMPTY), count(CellKind.GUN), count(CellKind.HEALTH));
            return true;
        } catch (Exception ex) {
            state = State.ERROR;
            error = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
            LOG.error("[{}] Classic Arena generation failed after {} cells", VERSION, placed, ex);
            return false;
        }
    }

    public synchronized GenerationSnapshot snapshot() {
        return new GenerationSnapshot(state, placed, count(CellKind.EMPTY), count(CellKind.GUN), count(CellKind.HEALTH), error);
    }

    private void resetCounters() {
        counts.clear();
        for (CellKind kind : CellKind.values()) counts.put(kind, 0);
    }

    private int count(CellKind kind) {
        return counts.getOrDefault(kind, 0);
    }

    /**
     * Places item-bearing cells first with one-cell separation, then distributes empty/flat cells.
     * The old command system rejected nearby has_items markers within roughly 8-12 blocks; on an
     * 8-block grid this is equivalent to keeping GUN/HEALTH cells out of adjacent grid cells.
     */
    private List<CellKind> buildPlan(ServerLevel level) {
        List<CellKind> plan = new ArrayList<>(Collections.nCopies(TOTAL_CELLS, CellKind.FLAT));
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < TOTAL_CELLS; i++) candidates.add(i);
        shuffle(level, candidates);

        Set<Integer> itemCells = new HashSet<>();
        selectSeparated(plan, candidates, itemCells, CellKind.GUN, GUN_CELLS);
        selectSeparated(plan, candidates, itemCells, CellKind.HEALTH, HEALTH_CELLS);

        List<Integer> remaining = new ArrayList<>();
        for (int i = 0; i < TOTAL_CELLS; i++) if (plan.get(i) == CellKind.FLAT) remaining.add(i);
        shuffle(level, remaining);
        for (int i = 0; i < Math.min(EMPTY_CELLS, remaining.size()); i++) plan.set(remaining.get(i), CellKind.EMPTY);
        return plan;
    }

    private static void selectSeparated(
        List<CellKind> plan,
        List<Integer> shuffled,
        Set<Integer> itemCells,
        CellKind kind,
        int target
    ) {
        int selected = 0;
        for (int index : shuffled) {
            if (selected >= target) break;
            if (plan.get(index) != CellKind.FLAT || touchesItemCell(index, itemCells)) continue;
            plan.set(index, kind);
            itemCells.add(index);
            selected++;
        }
        // The 8x8 grid has enough separated cells for the recovered 10+4 quota. Fail closed if a
        // future quota/config makes that impossible instead of silently changing map balance.
        if (selected != target) throw new IllegalStateException("cannot place " + target + " separated " + kind + " cells; placed " + selected);
    }

    private static boolean touchesItemCell(int index, Set<Integer> itemCells) {
        int row = index / GRID_SIZE;
        int col = index % GRID_SIZE;
        for (int other : itemCells) {
            int otherRow = other / GRID_SIZE;
            int otherCol = other % GRID_SIZE;
            if (Math.abs(row - otherRow) <= 1 && Math.abs(col - otherCol) <= 1) return true;
        }
        return false;
    }

    private static ResourceLocation chooseTemplate(ServerLevel level, CellKind kind) {
        return switch (kind) {
            case FLAT -> id("cg.pregen.flat_1");
            case EMPTY -> id("cg.pregen.empty_" + (1 + level.getRandom().nextInt(20)));
            case GUN -> id("cg.pregen.random_gun_" + (1 + level.getRandom().nextInt(15)));
            case HEALTH -> id("cg.pregen.s_health_" + (1 + level.getRandom().nextInt(15)));
        };
    }

    private static ResourceLocation id(String path) {
        ResourceLocation id = ResourceLocation.tryParse("minecraft:" + path);
        if (id == null) throw new IllegalArgumentException("invalid structure id " + path);
        return id;
    }

    private static Rotation randomRotation(ServerLevel level) {
        return switch (level.getRandom().nextInt(4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static void clearCell(ServerLevel level, int col, int row) {
        int minX = GRID_MIN_X + col * CELL_SIZE;
        int minZ = GRID_MIN_Z + row * CELL_SIZE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = PLACE_Y; y < PLACE_Y + CELL_HEIGHT; y++) {
            for (int z = minZ; z < minZ + CELL_SIZE; z++) {
                for (int x = minX; x < minX + CELL_SIZE; x++) {
                    cursor.set(x, y, z);
                    level.setBlock(cursor, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static void placeTemplate(ServerLevel level, ResourceLocation templateId, int col, int row, Rotation rotation) {
        StructureTemplate template = level.getStructureManager().get(templateId)
            .orElseThrow(() -> new IllegalStateException("missing Classic Arena template " + templateId));

        // These four offsets are recovered directly from the legacy command-block generator.
        int offsetX;
        int offsetZ;
        switch (rotation) {
            case CLOCKWISE_90 -> { offsetX = 3; offsetZ = -3; }
            case CLOCKWISE_180 -> { offsetX = 3; offsetZ = 4; }
            case COUNTERCLOCKWISE_90 -> { offsetX = -4; offsetZ = 4; }
            default -> { offsetX = -4; offsetZ = -3; }
        }

        int structureX = STRUCTURE_BLOCK_X + col * CELL_SIZE;
        int structureZ = STRUCTURE_BLOCK_Z + row * CELL_SIZE;
        BlockPos origin = new BlockPos(structureX + offsetX, STRUCTURE_BLOCK_Y + 3, structureZ + offsetZ);
        StructurePlaceSettings settings = new StructurePlaceSettings()
            .setMirror(Mirror.NONE)
            .setRotation(rotation)
            .setIgnoreEntities(false);

        boolean placed = template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        if (!placed) throw new IllegalStateException("template placement returned false for " + templateId + " at " + origin);
    }

    private static void cleanupLegacyEntities(ServerLevel level) {
        List<Entity> entities = level.getEntities(EntityType.MARKER, ARENA_ENTITIES, entity -> {
            Set<String> tags = entity.getTags();
            return tags.contains("cg_random_chunk")
                || tags.contains("item_spawner")
                || tags.contains("has_items")
                || tags.contains("small_health_orb")
                || tags.contains("random_gun_ammo")
                || tags.contains("gun_1_ammo")
                || tags.contains("gun_2_ammo")
                || tags.contains("gun_3_ammo");
        });
        for (Entity entity : entities) entity.discard();
    }

    private static <T> void shuffle(ServerLevel level, List<T> values) {
        // Fisher-Yates using the world RNG keeps generation reproducible from server RNG without
        // introducing java.util.Random state outside the game server.
        for (int i = values.size() - 1; i > 0; i--) {
            int j = level.getRandom().nextInt(i + 1);
            T tmp = values.get(i);
            values.set(i, values.get(j));
            values.set(j, tmp);
        }
    }
}
