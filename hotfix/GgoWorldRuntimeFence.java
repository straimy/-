package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * Removes vanilla-survival affordances from normal GGO sessions without touching Minecraft's
 * registries. Maps stay immutable, the GGO inventory replaces crafting, and hunger/XP are inert.
 * Operators keep an explicit maintenance escape hatch through permission level 2.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoWorldRuntimeFence {
    private static final Set<String> VANILLA_MENU_BLOCKS = Set.of(
        "chest", "trapped_chest", "barrel", "ender_chest",
        "crafting_table", "furnace", "blast_furnace", "smoker",
        "brewing_stand", "enchanting_table", "anvil", "chipped_anvil", "damaged_anvil",
        "grindstone", "smithing_table", "stonecutter", "cartography_table", "loom",
        "beacon", "hopper", "dispenser", "dropper"
    );

    private GgoWorldRuntimeFence() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && governed(player) && !maintenance(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && governed(player) && !maintenance(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onVanillaBlockUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !governed(player) || maintenance(player)) return;
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(event.getLevel().getBlockState(event.getPos()).getBlock());
        if (id == null || !"minecraft".equals(id.getNamespace())) return;
        String path = id.getPath();
        if (!VANILLA_MENU_BLOCKS.contains(path) && !path.endsWith("_bed")) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide || !(event.player instanceof ServerPlayer player)) return;
        if (!governed(player) || maintenance(player) || player.tickCount % 10 != 0) return;

        // Hunger and vanilla XP no longer form part of the GGO progression/runtime loop.
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0F);
        player.getFoodData().setExhaustion(0.0F);
        player.experienceProgress = 0.0F;
        player.experienceLevel = 0;
        player.totalExperience = 0;
    }

    private static boolean governed(ServerPlayer player) {
        ArenaRuntime runtime = GunnerArenaMod.RUNTIME;
        return runtime != null && runtime.auth().isAuthenticated(player);
    }

    private static boolean maintenance(ServerPlayer player) {
        if (!player.hasPermissions(2)) return false;
        GameType mode = player.gameMode.getGameModeForPlayer();
        return mode == GameType.CREATIVE || mode == GameType.SPECTATOR;
    }
}
