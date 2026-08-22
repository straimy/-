package arena.forge;

import arena.forge.player.ArenaPlayerState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Authoritative GGO player-presence policy.
 *
 * Social/safe spaces are visible-by-default. Invisibility is never inferred from being in the
 * lobby: a gameplay mode must explicitly opt a player into invisibility with MODE_INVISIBLE_TAG.
 */
public final class GgoPlayerPresencePolicy {
    public static final String MODE_INVISIBLE_TAG = "ggo_presence_invisible";

    private GgoPlayerPresencePolicy() {}

    public static void apply(ServerPlayer player, ArenaPlayerState state, ArenaRuntime runtime) {
        boolean social = state == ArenaPlayerState.LOBBY
            || state == ArenaPlayerState.QUEUED
            || runtime.safeRegions().isSafe(player);

        if (social) {
            player.setInvisible(false);
            player.clearFire();
            player.fallDistance = 0.0F;
            purgeLegacyCompassMenu(player);
            return;
        }

        player.setInvisible(player.getTags().contains(MODE_INVISIBLE_TAG));
    }

    private static void purgeLegacyCompassMenu(ServerPlayer player) {
        boolean changed = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack != null && !stack.isEmpty() && stack.is(Items.COMPASS)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                changed = true;
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand != null && !offhand.isEmpty() && offhand.is(Items.COMPASS)) {
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            changed = true;
        }
        if (changed) player.getInventory().setChanged();
    }

    public static void setModeInvisible(ServerPlayer player, boolean invisible) {
        if (invisible) {
            player.addTag(MODE_INVISIBLE_TAG);
        } else {
            player.removeTag(MODE_INVISIBLE_TAG);
            player.setInvisible(false);
        }
    }
}
