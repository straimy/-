package arena.forge;

import arena.round.CGBetaSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Compatibility bridge for the existing RoundManager/CGBetaRegenerator API.
 *
 * Runtime v40 used this adapter to toggle a redstone block at (44,63,3) and read generator progress
 * from scoreboard objectives. The public adapter shape is intentionally preserved, but generation
 * is now owned by ClassicArenaMapGenerator and snapshot data comes from Java state.
 */
public final class CGBetaForgeAdapter {
    /** Legacy coordinate kept only for binary/source compatibility. It is no longer written to. */
    @Deprecated
    public static final BlockPos GENERATOR_TRIGGER = new BlockPos(44, 63, 3);

    private final ClassicArenaMapGenerator generator = ClassicArenaMapGenerator.shared();

    public void trigger(ServerLevel level) {
        generator.generate(level);
    }

    public CGBetaSnapshot snapshot(MinecraftServer server, ServerLevel level) {
        ClassicArenaMapGenerator.GenerationSnapshot state = generator.snapshot();
        if (state.state() == ClassicArenaMapGenerator.State.READY) {
            return new CGBetaSnapshot(
                ClassicArenaMapGenerator.TOTAL_CELLS,
                state.empty(), ClassicArenaMapGenerator.EMPTY_CELLS,
                state.guns(), ClassicArenaMapGenerator.GUN_CELLS,
                state.health(), ClassicArenaMapGenerator.HEALTH_CELLS,
                0,
                true
            );
        }

        return new CGBetaSnapshot(
            Math.max(0, state.placed()),
            state.empty(), ClassicArenaMapGenerator.EMPTY_CELLS,
            state.guns(), ClassicArenaMapGenerator.GUN_CELLS,
            state.health(), ClassicArenaMapGenerator.HEALTH_CELLS,
            Math.max(0, ClassicArenaMapGenerator.TOTAL_CELLS - state.placed()),
            state.placed() > 0
        );
    }
}
