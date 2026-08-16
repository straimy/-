package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import arena.round.RoundState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Keeps map regeneration and respawn from restoring legacy developer weapons.
 * Fresh respawn loadout is knife-only; guns are bought again with match credits.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RoundLoadoutGuard {
    public static final int STARTING_CREDITS = 700;
    private static RoundState previousState;
    private static int previousRound = Integer.MIN_VALUE;

    private RoundLoadoutGuard() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ArenaRuntime runtime = GunnerArenaMod.RUNTIME;
        if (runtime == null || !runtime.auth().isAuthenticated(player)) return;

        // Run after the normal respawn callbacks so old bow/AR/legacy starter grants cannot win the race.
        runtime.forgeLoadouts().clearCombatSlots(player);
        giveKnifeOnly(player);
        runtime.customWeaponCombat().forget(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ArenaRuntime runtime = GunnerArenaMod.RUNTIME;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (runtime == null || server == null) return;

        RoundState current = runtime.rounds().state();
        int round = runtime.rounds().roundNumber();
        boolean enteredRegeneration = current == RoundState.REGENERATING && previousState != RoundState.REGENERATING;
        boolean roundRolledWhileNotPlaying = round != previousRound && current != RoundState.PLAYING;

        if (enteredRegeneration || roundRolledWhileNotPlaying) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!runtime.auth().isAuthenticated(player)) continue;
                runtime.forgeLoadouts().clearCombatSlots(player);
                // Credits/loadout are per-match. Death does NOT reset credits; regeneration/new round does.
                runtime.players().roundSession(player).resetForNewRound(STARTING_CREDITS);
                runtime.customWeaponCombat().forget(player);
                runtime.players().session(player).state(ArenaPlayerState.LOBBY);
                player.setInvisible(true);
                player.setGameMode(GameType.ADVENTURE);
            }
        }

        previousState = current;
        previousRound = round;
    }

    private static void giveKnifeOnly(ServerPlayer player) {
        // Dedicated arena knife: no ammunition and no JEG firing path.
        ItemStack knife = new ItemStack(Items.IRON_SWORD);
        knife.getOrCreateTag().putBoolean("GunnerArenaKnife", true);
        knife.getOrCreateTag().putBoolean("GunnerArenaBound", true);
        knife.setHoverName(Component.literal("Нож"));
        knife.getOrCreateTag().putBoolean("Unbreakable", true);
        player.getInventory().setItem(0, knife);
        player.getInventory().selected = 0;
        player.getInventory().setChanged();
    }
}
