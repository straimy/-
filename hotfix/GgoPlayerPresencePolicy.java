package arena.forge;

import arena.forge.player.ArenaPlayerState;
import net.minecraft.server.level.ServerPlayer;

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
            return;
        }

        player.setInvisible(player.getTags().contains(MODE_INVISIBLE_TAG));
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
