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
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Stable server-side registry for GunGloryOnline game modes.
 *
 * Maps are content. Modes are named GGO services. /play is a fallback/admin-friendly entry point;
 * the normal player flow will use the GGO client mode selector backed by the same registry.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoGameModeRegistry {
    public enum Availability { ACTIVE, MIGRATING, PLANNED }

    public record Mode(String id, String title, String description, Availability availability) {}

    private static final Map<String, Mode> MODES = new LinkedHashMap<>();
    private static final Map<UUID, String> SELECTED = new ConcurrentHashMap<>();

    static {
        register(new Mode("arena", "Arena", "Fast always-on GunGloryOnline combat", Availability.ACTIVE));
        register(new Mode("classic", "Classic Arena", "Round-based legacy mode recovered from the original map", Availability.MIGRATING));
        register(new Mode("duels", "Duels", "1v1 / 2v2 round arenas", Availability.PLANNED));
        register(new Mode("br", "Battle Royale", "Last-player/team-standing operation", Availability.PLANNED));
    }

    private GgoGameModeRegistry() {}

    private static void register(Mode mode) {
        MODES.put(mode.id(), mode);
    }

    public static Map<String, Mode> modes() {
        return Map.copyOf(MODES);
    }

    public static String selectedMode(ServerPlayer player) {
        return SELECTED.getOrDefault(player.getUUID(), "arena");
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("play")
                .executes(ctx -> showModes(ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("mode", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        for (String id : MODES.keySet()) builder.suggest(id);
                        return builder.buildFuture();
                    })
                    .executes(ctx -> select(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "mode"))))
        );
    }

    private static int showModes(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("GunGloryOnline • PLAY").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        String selected = selectedMode(player);
        for (Mode mode : MODES.values()) {
            ChatFormatting color = switch (mode.availability()) {
                case ACTIVE -> ChatFormatting.GREEN;
                case MIGRATING -> ChatFormatting.GOLD;
                case PLANNED -> ChatFormatting.DARK_GRAY;
            };
            String marker = mode.id().equals(selected) ? " ▶ " : "   ";
            player.sendSystemMessage(Component.literal(marker + mode.title() + " [" + mode.availability().name() + "]")
                .withStyle(color)
                .append(Component.literal(" — " + mode.description()).withStyle(ChatFormatting.GRAY)));
        }
        player.sendSystemMessage(Component.literal("/play arena  •  /play classic  •  /play duels  •  /play br").withStyle(ChatFormatting.DARK_GRAY));
        return Command.SINGLE_SUCCESS;
    }

    private static int select(ServerPlayer player, String raw) {
        String id = raw.toLowerCase(Locale.ROOT);
        Mode mode = MODES.get(id);
        if (mode == null) {
            player.sendSystemMessage(Component.literal("[GGO] Unknown mode: " + raw).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (mode.availability() != Availability.ACTIVE) {
            player.sendSystemMessage(Component.literal("[GGO] " + mode.title() + " is " + mode.availability().name().toLowerCase(Locale.ROOT) + ".")
                .withStyle(mode.availability() == Availability.MIGRATING ? ChatFormatting.GOLD : ChatFormatting.GRAY));
            return 0;
        }
        SELECTED.put(player.getUUID(), mode.id());
        player.sendSystemMessage(Component.literal("[GGO] Selected: " + mode.title()).withStyle(ChatFormatting.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SELECTED.remove(event.getEntity().getUUID());
    }
}
