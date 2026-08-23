package arena.forge;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Staff-only diagnostics for the report-only GGO anti-cheat.
 *
 * These commands are intentionally read-mostly. They do not ban, kick, teleport,
 * mutate inventory, or change gameplay. Permission level 2 is required.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoAntiCheatCommands {
    private GgoAntiCheatCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("ggoac")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> status(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> clear(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
        );
    }

    private static int status(CommandSourceStack source, ServerPlayer player) {
        double score = GgoAntiCheatEvidence.score(player.getUUID());
        List<GgoAntiCheatEvidence.Evidence> evidence = GgoAntiCheatEvidence.evidence(player.getUUID());
        source.sendSuccess(() -> Component.literal(String.format(
                java.util.Locale.ROOT,
                "[GGO-AC] %s score=%.2f evidence=%d mode=REPORT_ONLY",
                player.getGameProfile().getName(), score, evidence.size()
        )), false);

        int from = Math.max(0, evidence.size() - 8);
        for (int i = from; i < evidence.size(); i++) {
            GgoAntiCheatEvidence.Evidence item = evidence.get(i);
            source.sendSuccess(() -> Component.literal(
                    "[GGO-AC] " + item.kind().name()
                            + " weight=" + String.format(java.util.Locale.ROOT, "%.2f", item.weight())
                            + " detail=" + item.detail()
            ), false);
        }
        return 1;
    }

    private static int clear(CommandSourceStack source, ServerPlayer player) {
        GgoAntiCheatEvidence.clear(player.getUUID());
        source.sendSuccess(() -> Component.literal(
                "[GGO-AC] cleared report-only evidence for " + player.getGameProfile().getName()
        ), true);
        return 1;
    }
}
