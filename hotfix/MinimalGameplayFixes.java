package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.net.ArenaNetwork;
import arena.forge.player.ArenaPlayerState;
import arena.round.RoundState;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
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
    private static final Map<UUID, Long> LAST_NO_SPAWN_WARN = new HashMap<>();
    private static long lastAmmoWave = Long.MIN_VALUE;

    private MinimalGameplayFixes() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("setnpc")
            .requires(src -> src.hasPermission(2) || isNamedAdmin(src))
            .executes(ctx -> createMenuNpc(ctx.getSource(), "KVICloud"))
            .then(Commands.argument("name", StringArgumentType.greedyString())
                .executes(ctx -> createMenuNpc(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));
        event.getDispatcher().register(Commands.literal("play")
            .executes(ctx -> queueForPlay(ctx.getSource())));
    }

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
                clearQueueState(player);
                continue;
            }

            ArenaPlayerState state = runtime.players().session(player).state();
            if (state == ArenaPlayerState.LOBBY) {
                player.setInvisible(true);
                selectEmptyHotbarSlot(player);
                JOIN_AT.remove(player.getUUID());
                LAST_COUNT.remove(player.getUUID());
            } else if (state == ArenaPlayerState.QUEUED) {
                player.setInvisible(true);
                selectEmptyHotbarSlot(player);
                JOIN_AT.putIfAbsent(player.getUUID(), now + 20L);
            } else if (state == ArenaPlayerState.ALIVE || state == ArenaPlayerState.SPAWNING) {
                player.setInvisible(false);
                clearQueueState(player);
            }

            Long at = JOIN_AT.get(player.getUUID());
            if (at == null || state != ArenaPlayerState.QUEUED) continue;
            long left = at - now;
            if (left > 0L) {
                if (!Integer.valueOf(1).equals(LAST_COUNT.put(player.getUUID(), 1))) showTitle(server, player, "1");
                continue;
            }

            JOIN_AT.remove(player.getUUID());
            LAST_COUNT.remove(player.getUUID());

            if (runtime.spawns().combatSpawns().isEmpty()) {
                long prev = LAST_NO_SPAWN_WARN.getOrDefault(player.getUUID(), Long.MIN_VALUE);
                if (now - prev > 100L) {
                    LAST_NO_SPAWN_WARN.put(player.getUUID(), now);
                    player.sendSystemMessage(Component.literal("[GA] Нет боевых spawn-точек. Админу: встань на арену и выполни /addspawn (лучше несколько точек).")
                        .withStyle(ChatFormatting.RED));
                }
                JOIN_AT.put(player.getUUID(), now + 40L);
                continue;
            }

            if (runtime.rounds().state() != RoundState.PLAYING) runtime.forceStartRound();
            if (runtime.rounds().state() != RoundState.PLAYING) {
                player.sendSystemMessage(Component.literal("[GA] Карта ещё готовится, повторяю вход автоматически.").withStyle(ChatFormatting.YELLOW));
                JOIN_AT.put(player.getUUID(), now + 40L);
                continue;
            }

            runtime.forgeLoadouts().clearCombatSlots(player);
            runtime.players().roundSession(player).resetForNewRound(500);
            boolean spawned = runtime.players().requestPlay(server, player, runtime.rounds().state(), runtime.rounds().roundNumber(), now);
            if (spawned) {
                player.setInvisible(false);
                LAST_NO_SPAWN_WARN.remove(player.getUUID());
                ArenaNetwork.openUi(player, ArenaNetwork.UiTarget.SHOP);
                player.sendSystemMessage(Component.literal("[GA] Ты в игре. Выбери оружие в магазине. Стартовый баланс: 500.").withStyle(ChatFormatting.AQUA));
            } else {
                JOIN_AT.put(player.getUUID(), now + 40L);
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

    private static int queueForPlay(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception ex) { source.sendFailure(Component.literal("[GA] /play доступна только игроку.")); return 0; }
        ArenaRuntime runtime = GunnerArenaMod.RUNTIME;
        if (runtime == null) return 0;
        if (!runtime.auth().isAuthenticated(player)) { runtime.auth().deny(player); return 0; }
        runtime.players().session(player).state(ArenaPlayerState.QUEUED);
        JOIN_AT.put(player.getUUID(), runtime.serverTick() + 1L);
        LAST_COUNT.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("[GA] Вхожу в арену…").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int createMenuNpc(CommandSourceStack source, String requestedName) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception ex) { source.sendFailure(Component.literal("[GA] /setnpc нужно выполнять игроком.")); return 0; }
        String name = requestedName == null ? "KVICloud" : requestedName.trim();
        if (name.isEmpty()) name = "KVICloud";
        ArmorStand npc = EntityType.ARMOR_STAND.create(player.serverLevel());
        if (npc == null) { source.sendFailure(Component.literal("[GA] Не удалось создать NPC.")); return 0; }
        npc.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        npc.setCustomName(Component.literal(name));
        npc.setCustomNameVisible(true);
        npc.setInvisible(true);
        npc.setNoGravity(true);
        npc.setInvulnerable(true);
        npc.addTag("gunnerarena_menu_npc");
        npc.addTag("gunner_arena_npc_hitbox");
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.getOrCreateTag().putString("SkullOwner", name);
        npc.setItemSlot(EquipmentSlot.HEAD, head);
        player.serverLevel().addFreshEntity(npc);
        source.sendSuccess(() -> Component.literal("[GA] NPC создан (кликабельный)."), false);
        return 1;
    }

    private static boolean isNamedAdmin(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayer();
            if (player == null) return false;
            String n = player.getGameProfile().getName();
            return "kvi_nella".equalsIgnoreCase(n) || "Twinida".equalsIgnoreCase(n);
        } catch (Exception ignored) { return false; }
    }

    private static void clearQueueState(ServerPlayer player) {
        JOIN_AT.remove(player.getUUID());
        LAST_COUNT.remove(player.getUUID());
        LAST_NO_SPAWN_WARN.remove(player.getUUID());
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
