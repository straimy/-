package arena.forge;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Explicit migration-only cleaner for the imported Classic Arena command-block infrastructure.
 *
 * Nothing is deleted automatically. An OP must inspect status and then type the exact CONFIRM token.
 * The production deployment should be backed up before the strip command is ever used.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoLegacyCommandBlockCleaner {
    public static final String VERSION = "GGO-LEGACY-CLEAN-V1";

    // Bounds recovered from the supplied 2026-08-17 world audit. They cover all 992 command blocks.
    private static final int MIN_X = 32;
    private static final int MAX_X = 213;
    private static final int MIN_Y = 17;
    private static final int MAX_Y = 88;
    private static final int MIN_Z = -62;
    private static final int MAX_Z = 107;

    private GgoLegacyCommandBlockCleaner() {}

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("ggo")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("legacy")
                    .then(Commands.literal("commandblocks")
                        .then(Commands.literal("status").executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            List<BlockPos> found = find(level);
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
                                )))))
        );
    }

    private static int strip(ServerLevel level, net.minecraft.commands.CommandSourceStack source, String confirmation) {
        if (!"CONFIRM".equals(confirmation)) {
            source.sendFailure(Component.literal("[GGO] Refusing destructive migration. Use: /ggo legacy commandblocks strip CONFIRM"));
            return 0;
        }

        List<BlockPos> found = find(level);
        if (found.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[GGO] No legacy command blocks found.").withStyle(ChatFormatting.GREEN), false);
            return Command.SINGLE_SUCCESS;
        }

        int removed = 0;
        for (BlockPos pos : found) {
            if (!isCommandBlock(level, pos)) continue;
            if (level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())) removed++;
        }

        int finalRemoved = removed;
        source.sendSuccess(() -> Component.literal(
            "[GGO] Removed " + finalRemoved + " legacy command blocks. Restart/audit the clean world before production."
        ).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        return finalRemoved > 0 ? Command.SINGLE_SUCCESS : 0;
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
