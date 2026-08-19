package arena.forge;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-owned GGO mode catalog and /play router. */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoGameModeRegistry {
    public static final String VERSION = "GGO-MODE-REGISTRY-V3";
    public enum Availability { ACTIVE, MIGRATING, PLANNED }
    public record Mode(String id, String title, String description, Availability defaultAvailability) {
        public Availability configuredAvailability() { return GgoModeConfig.availability(id, defaultAvailability); }
    }

    private static final Map<String, Mode> MODES = new LinkedHashMap<>();
    private static final Map<UUID, String> SELECTED = new ConcurrentHashMap<>();

    static {
        register(new Mode("arena", "Arena", "Fast always-on GunGloryOnline combat", Availability.ACTIVE));
        register(new Mode("classic", "Classic Arena", "Procedurally generated round combat", Availability.MIGRATING));
        register(new Mode("duels", "Duels", "1v1 / 2v2 round arenas", Availability.PLANNED));
        register(new Mode("br", "Battle Royale", "Last-player/team-standing operation", Availability.PLANNED));
        GgoModeConfig.reload();
    }

    private GgoGameModeRegistry() {}
    private static void register(Mode mode) { MODES.put(mode.id(), mode); }
    public static Map<String, Mode> modes() { return Map.copyOf(MODES); }
    public static String selectedMode(ServerPlayer player) { return SELECTED.getOrDefault(player.getUUID(), "arena"); }

    public static Availability effectiveAvailability(MinecraftServer server, Mode mode) {
        Availability configured = mode.configuredAvailability();
        if ("classic".equals(mode.id()) && configured == Availability.ACTIVE && !GgoClassicReadiness.ready(server)) {
            return Availability.MIGRATING;
        }
        return configured;
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("play")
                .executes(ctx -> showModes(ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("mode", StringArgumentType.word())
                    .suggests((ctx, builder) -> { for (String id : MODES.keySet()) builder.suggest(id); return builder.buildFuture(); })
                    .executes(ctx -> select(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "mode"))))
        );
        event.getDispatcher().register(
            Commands.literal("ggo").requires(s -> s.hasPermission(2))
                .then(Commands.literal("modes")
                    .then(Commands.literal("reload").executes(ctx -> {
                        GgoModeConfig.reload();
                        ctx.getSource().sendSuccess(() -> Component.literal("[GGO] Mode availability reloaded."), true);
                        return Command.SINGLE_SUCCESS;
                    }))
                    .then(Commands.literal("status").executes(ctx -> {
                        MinecraftServer server = ctx.getSource().getServer();
                        StringBuilder out = new StringBuilder("[GGO] Modes:");
                        for (Mode mode : MODES.values()) {
                            out.append(' ').append(mode.id()).append('=').append(effectiveAvailability(server, mode));
                        }
                        out.append(" classicReady=").append(GgoClassicReadiness.ready(server));
                        ctx.getSource().sendSuccess(() -> Component.literal(out.toString()).withStyle(ChatFormatting.AQUA), false);
                        return Command.SINGLE_SUCCESS;
                    })))
        );
    }

    private static int showModes(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("GunGloryOnline • PLAY").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        String selected = selectedMode(player);
        MinecraftServer server = player.getServer();
        for (Mode mode : MODES.values()) {
            Availability availability = effectiveAvailability(server, mode);
            ChatFormatting color = switch (availability) {
                case ACTIVE -> ChatFormatting.GREEN;
                case MIGRATING -> ChatFormatting.GOLD;
                case PLANNED -> ChatFormatting.DARK_GRAY;
            };
            String marker = mode.id().equals(selected) ? " ▶ " : "   ";
            player.sendSystemMessage(Component.literal(marker + mode.title() + " [" + availability.name() + "]")
                .withStyle(color).append(Component.literal(" — " + mode.description()).withStyle(ChatFormatting.GRAY)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int select(ServerPlayer player, String raw) {
        String id = raw.toLowerCase(Locale.ROOT);
        Mode mode = MODES.get(id);
        if (mode == null) {
            player.sendSystemMessage(Component.literal("[GGO] Unknown mode: " + raw).withStyle(ChatFormatting.RED));
            return 0;
        }
        Availability availability = effectiveAvailability(player.getServer(), mode);
        if (availability != Availability.ACTIVE) {
            String suffix = "classic".equals(id) && mode.configuredAvailability() == Availability.ACTIVE
                ? " (real-world smoke not approved for this world)" : "";
            player.sendSystemMessage(Component.literal("[GGO] " + mode.title() + " is " + availability.name().toLowerCase(Locale.ROOT) + suffix + ".")
                .withStyle(availability == Availability.MIGRATING ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            return 0;
        }
        SELECTED.put(player.getUUID(), mode.id());
        if ("classic".equals(id)) {
            if (!ClassicArenaQueueService.enqueue(player)) return 0;
            return Command.SINGLE_SUCCESS;
        }
        if ("duels".equals(id)) {
            if (!DuelMatchService.enqueue(player)) return 0;
            player.sendSystemMessage(Component.literal("GGO • Joined Duels queue").withStyle(ChatFormatting.AQUA));
            return Command.SINGLE_SUCCESS;
        }
        player.sendSystemMessage(Component.literal("[GGO] Selected: " + mode.title()).withStyle(ChatFormatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SELECTED.remove(event.getEntity().getUUID());
        ClassicArenaQueueService.remove(event.getEntity().getUUID());
    }
}
