package arena.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Central server-side rules that make GGO behave like a game rather than a vanilla survival world.
 *
 * Normal players cannot break/place map blocks. Explicit OP admin build mode remains exempt.
 * Command blocks are additionally protected even from accidental interactions while production
 * worlds are being migrated away from command-driven gameplay.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoServerRulesGuard {
    public static final String RULESET_VERSION = "GGO-RULES-V1";

    private GgoServerRulesGuard() {}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (canBuild(player)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (canBuild(player)) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (canBuild(player)) return;
        event.setCanceled(true);
    }

    private static boolean canBuild(ServerPlayer player) {
        if (!player.hasPermissions(2)) return false;
        if (!player.getTags().contains(AdminToolsCommands.ADMIN_BUILD_TAG)) return false;
        return player.isCreative();
    }

    /** Utility used by future interaction guards while command blocks are phased out. */
    public static boolean isCommandBlock(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(Blocks.COMMAND_BLOCK)
            || state.is(Blocks.CHAIN_COMMAND_BLOCK)
            || state.is(Blocks.REPEATING_COMMAND_BLOCK);
    }
}
