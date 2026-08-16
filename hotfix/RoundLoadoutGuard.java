package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import arena.round.RoundState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Keeps a map regeneration from restoring/carrying a previous-round JEG loadout.
 * The normal ArenaPlayerManager remains the only owner of fresh-round weapon grants.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RoundLoadoutGuard {
    public static final int STARTING_CREDITS = 700;
    private static RoundState previousState;
    private static int previousRound = Integer.MIN_VALUE;

    private RoundLoadoutGuard() {}

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
                // Credits/loadout are per-match. Death does NOT call this; regeneration/new round does.
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
}
