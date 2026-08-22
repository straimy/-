package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps raw Minecraft loot/progression out of normal GGO gameplay while preserving GGO/JEG loot.
 * The visible-item policy is shared with inventory cleanup so intentionally tagged resource-pack
 * proxy items remain valid while accidental vanilla content is removed.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoVanillaLootFence {
    private GgoVanillaLootFence() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (GunnerArenaMod.RUNTIME == null || event.getEntity() instanceof Player) return;
        event.getDrops().removeIf(drop -> isUnmarkedVanilla(drop, drop.getItem()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (GunnerArenaMod.RUNTIME == null) return;
        event.setDroppedExperience(0);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !governed(player) || maintenance(player)) return;
        ItemEntity entity = event.getItem();
        ItemStack stack = entity.getItem();
        if (!isUnmarkedVanilla(entity, stack)) return;
        event.setCanceled(true);
        entity.discard();
    }

    private static boolean isUnmarkedVanilla(ItemEntity entity, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (entity.getPersistentData().contains("ggoLootPoint") || entity.getPersistentData().contains("ggoLootKind")) return false;
        return !GgoVisibleItemPolicy.allowed(stack);
    }

    private static boolean governed(ServerPlayer player) {
        ArenaRuntime runtime = GunnerArenaMod.RUNTIME;
        return runtime != null && runtime.auth().isAuthenticated(player);
    }

    private static boolean maintenance(ServerPlayer player) {
        return player.hasPermissions(2) && player.gameMode.getGameModeForPlayer().isCreative();
    }
}
