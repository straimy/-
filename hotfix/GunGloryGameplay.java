package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.net.ArenaNetwork;
import arena.forge.player.ArenaPlayerState;
import arena.round.RoundState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** GunGloryOnline live-match QoL/progression layer. */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GunGloryGameplay {
    private static final Set<UUID> OUR_GLOW = new HashSet<>();
    private static long lastSecondTick = Long.MIN_VALUE;

    private GunGloryGameplay() {}

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gunshop").executes(ctx -> {
            ServerPlayer player;
            try { player = ctx.getSource().getPlayerOrException(); }
            catch (Exception ex) { return 0; }
            var runtime = GunnerArenaMod.RUNTIME;
            if (runtime == null || !runtime.auth().isAuthenticated(player) || !runtime.auth().isInitialized(player)) {
                player.sendSystemMessage(Component.literal("[GGO] Сначала войди в аккаунт.").withStyle(ChatFormatting.RED));
                return 0;
            }
            if (runtime.rounds().state() != RoundState.PLAYING || runtime.players().session(player).state() != ArenaPlayerState.ALIVE) {
                player.sendSystemMessage(Component.literal("[GGO] Оружейный магазин доступен только во время игры.").withStyle(ChatFormatting.YELLOW));
                return 0;
            }
            ArenaNetwork.openUi(player, ArenaNetwork.UiTarget.SHOP);
            return 1;
        }));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        var runtime = GunnerArenaMod.RUNTIME;
        if (server == null || runtime == null) return;
        long now = runtime.serverTick();
        boolean oneSecond = now - lastSecondTick >= 20L;
        if (oneSecond) lastSecondTick = now;

        Set<UUID> currentlyProtected = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!runtime.auth().isAuthenticated(player)) continue;
            ArenaPlayerState state = runtime.players().session(player).state();
            if (state != ArenaPlayerState.ALIVE) continue;

            long until = runtime.players().session(player).protectionUntilTick();
            long remainTicks = Math.max(0L, until - now);
            if (remainTicks > 0L) {
                currentlyProtected.add(player.getUUID());
                OUR_GLOW.add(player.getUUID());
                player.setGlowingTag(true);
                // Hard safety net: even if another damage path misses ArenaPlayerManager protection,
                // the first five seconds stay effectively invulnerable.
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 25, 4, false, false, false));
                if (oneSecond) {
                    long sec = (remainTicks + 19L) / 20L;
                    player.displayClientMessage(Component.literal("🛡 Бессмертие: " + sec + "с   •   G — магазин оружия").withStyle(ChatFormatting.AQUA), true);
                }
            } else if (OUR_GLOW.remove(player.getUUID())) {
                player.setGlowingTag(false);
            }

            if (oneSecond) {
                var profile = runtime.players().profile(player);
                if (profile != null) {
                    profile.playTimeSeconds++;
                    // Slow free progression: one training point per 20 active match minutes.
                    if (profile.playTimeSeconds % 1200L == 0L) {
                        profile.skillPoints = Math.min(Integer.MAX_VALUE, profile.skillPoints + 1);
                        player.sendSystemMessage(Component.literal("✦ Тренировка: +1 очко навыка").withStyle(ChatFormatting.LIGHT_PURPLE));
                    }
                    // Premium/special currency is also earnable, but deliberately slower.
                    if (profile.playTimeSeconds % 1800L == 0L) {
                        profile.crystals = Math.min(Long.MAX_VALUE, profile.crystals + 1L);
                        player.sendSystemMessage(Component.literal("◆ +1 кристалл за активную игру").withStyle(ChatFormatting.AQUA));
                    }
                    runtime.profiles().markDirty(player.getUUID());
                }
            }
        }
        for (UUID uuid : Set.copyOf(OUR_GLOW)) {
            if (currentlyProtected.contains(uuid)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) p.setGlowingTag(false);
            OUR_GLOW.remove(uuid);
        }
    }
}
