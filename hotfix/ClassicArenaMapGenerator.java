package arena.forge;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
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
 * content under world/generated/minecraft/structures, but selection, quotas, rotations, cleanup,
 * ammo-slot assignment and placement are owned by versioned GGO Java code.
 */
public final class ClassicArenaMapGenerator {
    public static final String VERSION = "GGO-CLASSIC-GEN-V3";
    public static final int GRID_SIZE = 8;
    public static final int TOTAL_CELLS = GRID_SIZE * GRID_SIZE;
    public static final int EMPTY_CELLS = 16;
    public static final int GUN_CELLS = 10;
    public static final int HEALTH_CELLS = 4;
    public static final int FLAT_CELLS = TOTAL_CELLS - EMPTY_CELLS - GUN_CELLS - HEALTH_CELLS;

    private static final int GRID_MIN_X = 48;
    private static final int GRID_MIN_Z = 48;
    private static final int STRUCTURE_BLOCK_X = 52;
    private static final int STRUCTURE_BLOCK_Z = 51;
    private static final int STRUCTURE_BLOCK_Y = 67;
    private static final int PLACE_Y = 70;
    private static final int CELL_SIZE = 8;
    private static final int CELL_HEIGHT = 32;
    private static final AABB ARENA_ENTITIES = new AABB(47.0D, 60.0D, 47.0D, 113.0D, 110.0D, 113.0D);

    private static final Logger LOG = LogUtils.getLogger();
    private static final ClassicArenaMapGenerator SHARED = new ClassicArenaMapGenerator();

    public enum State { IDLE, GENERATING, READY, ERROR }
    public enum CellKind { FLAT, EMPTY, GUN, HEALTH }

    public record GenerationSnapshot(State state, int placed, int empty, int guns, int health, String error) {}

    private State state = State.IDLE;
    private int placed;
    private final EnumMap<CellKind, Integer> counts = new EnumMap<>(CellKind.class);
    private String error = "";

    public ClassicArenaMapGenerator() {
        resetCounters();
    }

    /**
     * Single server-runtime generator state used by Classic match flow and the v40 compatibility
     * bridge. This prevents the old RoundManager snapshot path from observing a different generator
     * instance than /ggo classic and the actual Classic match.
     */
    public static ClassicArenaMapGenerator shared() {
        return SHARED;
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
            assignAmmoSlots(level);
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

    private List<CellKind> buildPlan(ServerLevel level) {
        List<CellKind> plan = new ArrayList<>(Collections.nCopies(TOTAL_CELLS, CellKind.FLAT));

        int rowParity = level.getRandom().nextInt(2);
        int colParity = level.getRandom().nextInt(2);
        List<Integer> safeItemCells = new ArrayList<>(16);
        for (int row = rowParity; row < GRID_SIZE; row += 2) {
            for (int col = colParity; col < GRID_SIZE; col += 2) {
                safeItemCells.add(row * GRID_SIZE + col);
            }
        }
        shuffle(level, safeItemCells);
        if (safeItemCells.size() < GUN_CELLS + HEALTH_CELLS) {
            throw new IllegalStateException("Classic item lattice is smaller than recovered quota");
        }

        int cursor = 0;
        for (int i = 0; i < GUN_CELLS; i++) plan.set(safeItemCells.get(cursor++), CellKind.GUN);
        for (int i = 0; i < HEALTH_CELLS; i++) plan.set(safeItemCells.get(cursor++), CellKind.HEALTH);

        List<Integer> remaining = new ArrayList<>();
        for (int i = 0; i < TOTAL_CELLS; i++) if (plan.get(i) == CellKind.FLAT) remaining.add(i);
        shuffle(level, remaining);
        for (int i = 0; i < EMPTY_CELLS; i++) plan.set(remaining.get(i), CellKind.EMPTY);
        return plan;
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

    private static void assignAmmoSlots(ServerLevel level) {
        List<Marker> markers = new ArrayList<>(level.getEntities(EntityType.MARKER, ARENA_ENTITIES,
            marker -> marker.getTags().contains("random_gun_ammo")));
        if (markers.size() != GUN_CELLS) {
            throw new IllegalStateException("expected " + GUN_CELLS + " random ammo markers, found " + markers.size());
        }
        shuffle(level, markers);
        for (int i = 0; i < markers.size(); i++) {
            Marker marker = markers.get(i);
            marker.removeTag("random_gun_ammo");
            marker.addTag(i < 4 ? "gun_1_ammo" : i < 7 ? "gun_2_ammo" : "gun_3_ammo");
            marker.addTag("item_spawner");
        }
    }

    private static void cleanupLegacyEntities(ServerLevel level) {
        List<Marker> entities = level.getEntities(EntityType.MARKER, ARENA_ENTITIES, entity -> {
            Set<String> tags = entity.getTags();
            return tags.contains("cg_random_chunk")
                || tags.contains("item_spawner")
                || tags.contains("has_items")
                || tags.contains("respawn_point")
                || tags.contains("small_health_orb")
                || tags.contains("health_orb")
                || tags.contains("jump_pad_marker")
                || tags.contains("random_gun_ammo")
                || tags.contains("gun_1_ammo")
                || tags.contains("gun_2_ammo")
                || tags.contains("gun_3_ammo");
        });
        for (Marker entity : entities) entity.discard();
    }

    private static <T> void shuffle(ServerLevel level, List<T> values) {
        for (int i = values.size() - 1; i > 0; i--) {
            int j = level.getRandom().nextInt(i + 1);
            T tmp = values.get(i);
            values.set(i, values.get(j));
            values.set(j, tmp);
        }
    }
}
