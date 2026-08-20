package arena.forge;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Explicit, OP-only migration cleaner for the imported Classic Arena command-block layer. */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoLegacyCommandBlockCleaner {
    public static final String VERSION = "GGO-LEGACY-CLEAN-V1";
    public static final String STARTUP_PROPERTY = "ggo.legacy.cleanup";
    public static final String EXPECTED_PROPERTY = "ggo.legacy.cleanup.expected";
    private static final Logger LOG = LogUtils.getLogger();

    // Exact bounds recovered from the supplied world audit; all 992 legacy blocks are inside.
    private static final int MIN_X = 32, MAX_X = 213;
    private static final int MIN_Y = 17, MAX_Y = 88;
    private static final int MIN_Z = -62, MAX_Z = 107;

    private GgoLegacyCommandBlockCleaner() {}

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        var commandBlocks = Commands.literal("commandblocks")
            .then(Commands.literal("status").executes(ctx -> {
                List<BlockPos> found = find(ctx.getSource().getLevel());
                ctx.getSource().sendSuccess(() -> Component.literal(
                    "[GGO] Legacy command blocks in audited bounds: " + found.size()
                ).withStyle(found.isEmpty() ? ChatFormatting.GREEN : ChatFormatting.GOLD), false);
                return Command.SINGLE_SUCCESS;
            }))
            .then(Commands.literal("strip")
                .then(Commands.argument("confirmation", StringArgumentType.word())
                    .executes(ctx -> strip(
                        ctx.getSource().getLevel(),
                        ctx.getSource(),
                        StringArgumentType.getString(ctx, "confirmation")
                    ))));
        event.getDispatcher().register(
            Commands.literal("ggo")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("legacy").then(commandBlocks))
        );
    }

    /** One-shot destructive cleanup for a disposable/final candidate copy only. */
    @SubscribeEvent
    public static void startupCleanup(ServerStartedEvent event) {
        if (!Boolean.getBoolean(STARTUP_PROPERTY)) return;
        var server = event.getServer();
        ServerLevel level = server.overworld();
        int expected = Integer.getInteger(EXPECTED_PROPERTY, 992);
        int before = -1;
        int removed = 0;
        int after = -1;
        String error = "none";
        boolean pass;
        try {
            before = find(level).size();
            if (before == expected) removed = stripLevel(level);
            after = find(level).size();
            pass = before == expected && removed == expected && after == 0;
        } catch (Exception ex) {
            pass = false;
            error = ex.getClass().getSimpleName() + ":" + String.valueOf(ex.getMessage());
        }
        LOG.info("[GGO-LEGACY-CLEAN-STARTUP] result={} expected={} before={} removed={} after={} error={}",
            pass ? "PASS" : "FAIL", expected, before, removed, after, error);
        server.halt(false);
    }

    private static int strip(ServerLevel level, net.minecraft.commands.CommandSourceStack source, String confirmation) {
        if (!"CONFIRM".equals(confirmation)) {
            source.sendFailure(Component.literal(
                "[GGO] Refusing destructive migration. Use: /ggo legacy commandblocks strip CONFIRM"
            ));
            return 0;
        }
        List<BlockPos> found = find(level);
        if (found.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[GGO] No legacy command blocks found.").withStyle(ChatFormatting.GREEN), false);
            return Command.SINGLE_SUCCESS;
        }
        int removed = stripLevel(level);
        source.sendSuccess(() -> Component.literal(
            "[GGO] Removed " + removed + " legacy command blocks. Restart/audit the clean world before production."
        ).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        return removed > 0 ? Command.SINGLE_SUCCESS : 0;
    }

    private static int stripLevel(ServerLevel level) {
        int removed = 0;
        for (BlockPos pos : find(level)) {
            if (!isCommandBlock(level, pos)) continue;
            if (level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())) removed++;
        }
        return removed;
    }

    private static List<BlockPos> find(ServerLevel level) {
        List<BlockPos> result = new ArrayList<>();
        int minChunkX = Math.floorDiv(MIN_X, 16);
        int maxChunkX = Math.floorDiv(MAX_X, 16);
        int minChunkZ = Math.floorDiv(MIN_Z, 16);
        int maxChunkZ = Math.floorDiv(MAX_Z, 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (BlockPos pos : List.copyOf(chunk.getBlockEntities().keySet())) {
                    if (!inside(pos)) continue;
                    BlockEntity ignored = chunk.getBlockEntity(pos);
                    if (ignored != null && isCommandBlock(level, pos)) result.add(pos.immutable());
                }
            }
        }
        return result;
    }

    private static boolean inside(BlockPos pos) {
        return pos.getX() >= MIN_X && pos.getX() <= MAX_X
            && pos.getY() >= MIN_Y && pos.getY() <= MAX_Y
            && pos.getZ() >= MIN_Z && pos.getZ() <= MAX_Z;
    }

    private static boolean isCommandBlock(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(Blocks.COMMAND_BLOCK)
            || state.is(Blocks.CHAIN_COMMAND_BLOCK)
            || state.is(Blocks.REPEATING_COMMAND_BLOCK);
    }
}
