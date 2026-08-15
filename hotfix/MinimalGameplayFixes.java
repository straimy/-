package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.net.ArenaNetwork;
import arena.forge.player.ArenaPlayerState;
import arena.round.RoundState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MinimalGameplayFixes {
    private static final Map<UUID, Long> JOIN_AT = new HashMap<>();
    private static final Map<UUID, Integer> LAST_COUNT = new HashMap<>();
    private static long lastAmmoWave = Long.MIN_VALUE;

    private MinimalGameplayFixes() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && lobbyProtected(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && lobbyProtected(player)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (event.getSource().getEntity() instanceof ServerPlayer killer && killer != victim) {
            victim.sendSystemMessage(Component.literal("✖ Тебя убил ")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal(killer.getGameProfile().getName()).withStyle(ChatFormatting.GOLD)));
        } else {
            victim.sendSystemMessage(Component.literal("✖ Ты погиб.").withStyle(ChatFormatting.RED));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || GunnerArenaMod.RUNTIME == null) return;
        ArenaRuntime runtime = GunnerArenaMod.RUNTIME;
        long now = runtime.serverTick();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!runtime.auth().isAuthenticated(player)) {
                JOIN_AT.remove(player.getUUID());
                LAST_COUNT.remove(player.getUUID());
                continue;
            }

            ArenaPlayerState state = runtime.players().session(player).state();
            boolean lobby = state == ArenaPlayerState.LOBBY || state == ArenaPlayerState.QUEUED;
            if (lobby) {
                player.setInvisible(true);
                selectEmptyHotbarSlot(player);
                if (!JOIN_AT.containsKey(player.getUUID()) && !runtime.spawns().combatSpawns().isEmpty()) {
                    JOIN_AT.put(player.getUUID(), now + 60L);
                    LAST_COUNT.remove(player.getUUID());
                }
            } else if (state == ArenaPlayerState.ALIVE || state == ArenaPlayerState.SPAWNING) {
                player.setInvisible(false);
                JOIN_AT.remove(player.getUUID());
                LAST_COUNT.remove(player.getUUID());
            }

            Long at = JOIN_AT.get(player.getUUID());
            if (at == null) continue;
            long left = at - now;
            if (left > 0L) {
                int count = left > 40L ? 3 : left > 20L ? 2 : 1;
                if (!Integer.valueOf(count).equals(LAST_COUNT.put(player.getUUID(), count))) {
                    showTitle(server, player, Integer.toString(count));
                }
                continue;
            }

            JOIN_AT.remove(player.getUUID());
            LAST_COUNT.remove(player.getUUID());
            if (runtime.rounds().state() == RoundState.WAITING) runtime.forceStartRound();
            if (runtime.rounds().state() != RoundState.PLAYING) {
                player.sendSystemMessage(Component.literal("[GA] Карта ещё готовится, вход произойдёт сразу после подготовки.").withStyle(ChatFormatting.YELLOW));
                JOIN_AT.put(player.getUUID(), now + 40L);
                continue;
            }

            runtime.forgeLoadouts().clearCombatSlots(player);
            runtime.players().roundSession(player).resetForNewRound(500);
            boolean spawned = runtime.players().requestPlay(server, player, runtime.rounds().state(), runtime.rounds().roundNumber(), now);
            if (spawned) {
                player.setInvisible(false);
                ArenaNetwork.openUi(player, ArenaNetwork.UiTarget.SHOP);
                player.sendSystemMessage(Component.literal("[GA] Выбери оружие в магазине. Стартовый баланс: 500.").withStyle(ChatFormatting.AQUA));
            }
        }

        if (now - lastAmmoWave >= 200L) {
            lastAmmoWave = now;
            int cycle = (int)((now / 200L) % 3L);
            spawnLegacyAmmo(server, "gun_1_ammo", "jeg:pistol_ammo", 24);
            spawnLegacyAmmo(server, "gun_2_ammo", "jeg:rifle_ammo", 24);
            spawnLegacyAmmo(server, "gun_3_ammo", "jeg:shotgun_shell", 10);
            String random = cycle == 0 ? "jeg:pistol_ammo" : cycle == 1 ? "jeg:rifle_ammo" : "jeg:shotgun_shell";
            int amount = cycle == 2 ? 10 : 24;
            spawnLegacyAmmo(server, "random_gun_ammo", random, amount);
        }
    }

    private static boolean lobbyProtected(ServerPlayer player) {
        ArenaRuntime runtime = GunnerArenaMod.RUNTIME;
        if (runtime == null || !runtime.auth().isAuthenticated(player)) return true;
        ArenaPlayerState state = runtime.players().session(player).state();
        return state == ArenaPlayerState.LOBBY || state == ArenaPlayerState.QUEUED || runtime.safeRegions().isSafe(player);
    }

    private static void selectEmptyHotbarSlot(ServerPlayer player) {
        for (int i = 8; i >= 0; i--) {
            if (player.getInventory().getItem(i).isEmpty()) {
                player.getInventory().selected = i;
                return;
            }
        }
    }

    private static void showTitle(MinecraftServer server, ServerPlayer player, String number) {
        run(server, "title " + player.getGameProfile().getName() + " times 0 15 0");
        run(server, "title " + player.getGameProfile().getName() + " title {\"text\":\"" + number + "\",\"color\":\"gold\",\"bold\":true}");
    }

    private static void spawnLegacyAmmo(MinecraftServer server, String markerTag, String itemId, int count) {
        String command = "execute as @e[tag=item_spawner,tag=" + markerTag + "] at @s "
            + "unless entity @e[type=minecraft:item,tag=ga_legacy_ammo,distance=..1.5] "
            + "run summon minecraft:item ~ ~0.35 ~ {Item:{id:\"" + itemId + "\",Count:" + count + "b},Tags:[\"ga_legacy_ammo\"],Glowing:1b}";
        run(server, command);
    }

    private static void run(MinecraftServer server, String command) {
        CommandSourceStack source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }
}
