package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Slow, server-authoritative earn path for premium currency. */
@Mod.EventBusSubscriber(modid="gunnerarena", bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ProgressionRewards {
    private static final int KILLS_PER_CRYSTAL = 5;
    private static final long CRYSTALS_PER_WIN = 2L;
    private static final Map<UUID,Long> LAST_KILLS = new HashMap<>();
    private static final Map<UUID,Long> LAST_WINS = new HashMap<>();
    private static long nextAuditTick;

    private ProgressionRewards() {}

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var runtime = GunnerArenaMod.RUNTIME;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (runtime == null || server == null) return;
        long tick = runtime.serverTick();
        if (tick < nextAuditTick) return;
        nextAuditTick = tick + 20L;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!runtime.auth().isAuthenticated(player) || !runtime.auth().isInitialized(player)) continue;
            var profile = runtime.players().profile(player);
            if (profile == null) continue;

            // Ensure every active Minecraft account already has a stable future GGO account id.
            GgoIdentityBridge.idFor(player);

            UUID id = player.getUUID();
            Long oldKills = LAST_KILLS.putIfAbsent(id, profile.kills);
            Long oldWins = LAST_WINS.putIfAbsent(id, profile.roundsWon);
            if (oldKills == null || oldWins == null) continue; // no historical back-pay on reconnect/restart

            long crystalGain = 0L;
            long killMilestonesBefore = Math.max(0L, oldKills) / KILLS_PER_CRYSTAL;
            long killMilestonesNow = Math.max(0L, profile.kills) / KILLS_PER_CRYSTAL;
            if (killMilestonesNow > killMilestonesBefore) {
                long gained = killMilestonesNow - killMilestonesBefore;
                crystalGain += gained;
                player.sendSystemMessage(Component.literal("◆ +"+gained+" кристалл"+(gained==1?"":"а")+" • за убийства").withStyle(ChatFormatting.AQUA));
            }

            long newWins = Math.max(0L, profile.roundsWon - oldWins);
            if (newWins > 0L) {
                long gained = newWins * CRYSTALS_PER_WIN;
                crystalGain += gained;
                player.sendSystemMessage(Component.literal("◆ +"+gained+" кристалла • победа").withStyle(ChatFormatting.LIGHT_PURPLE));
            }

            LAST_KILLS.put(id, profile.kills);
            LAST_WINS.put(id, profile.roundsWon);
            if (crystalGain > 0L) {
                if (Long.MAX_VALUE - profile.crystals < crystalGain) profile.crystals = Long.MAX_VALUE;
                else profile.crystals += crystalGain;
                runtime.profiles().markDirty(id);
            }
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        LAST_KILLS.remove(id);
        LAST_WINS.remove(id);
    }
}
