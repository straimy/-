package arena.forge;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Canonical GGO logical-world router.
 *
 * The production server keeps four logical roles:
 * training = current primary world, hub = imported world_3,
 * br_drop = imported "Точка Высадки", br_mini = imported Mini PUBG.
 * Missing imported dimensions fail closed instead of silently routing to the wrong map.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoWorldRouter {
    public static final String VERSION = "GGO-WORLD-ROUTER-V2";
    public static final String TRAINING = "training";
    public static final String HUB = "hub";
    public static final String BR_DROP = "br_drop";
    public static final String BR_MINI = "br_mini";

    private record Target(String id, String title, ResourceKey<Level> dimension, Integer anchorX, Integer anchorZ, int spawnRadius) {}
    private static final Map<String, Target> TARGETS = new LinkedHashMap<>();

    static {
        register(new Target(TRAINING, "Training", Level.OVERWORLD, null, null, 48));
        // Anchors come from the uploaded worlds' original level.dat SpawnX/SpawnZ.
        register(new Target(HUB, "GGO Hub", key("hub"), -126, -177, 28));
        register(new Target(BR_DROP, "Battle Royale • Drop Point", key("br_drop_point"), 28, -41, 160));
        register(new Target(BR_MINI, "Battle Royale • Mini PUBG", key("br_mini_pubg"), 999, 1019, 160));
    }

    private GgoWorldRouter() {}
    private static void register(Target target) { TARGETS.put(target.id(), target); }
    private static ResourceKey<Level> key(String path) {
        return ResourceKey.create(Registries.DIMENSION, new ResourceLocation("gungloryonline", path));
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("world").requires(s -> s.hasPermission(2))
                .executes(ctx -> status(ctx.getSource().getServer(), ctx.getSource().getPlayerOrException()))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests((ctx, builder) -> { TARGETS.keySet().forEach(builder::suggest); return builder.buildFuture(); })
                    .executes(ctx -> teleport(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id"))))
        );
        event.getDispatcher().register(
            Commands.literal("ggo").requires(s -> s.hasPermission(2))
                .then(Commands.literal("world")
                    .executes(ctx -> status(ctx.getSource().getServer(), ctx.getSource().getPlayerOrException()))
                    .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource().getServer(), ctx.getSource().getPlayerOrException())))
                    .then(Commands.literal("tp")
                        .then(Commands.argument("id", StringArgumentType.word())
                            .suggests((ctx, builder) -> { TARGETS.keySet().forEach(builder::suggest); return builder.buildFuture(); })
                            .executes(ctx -> teleport(ctx.getSource().getPlayerOrException(), StringArgumentType.getString(ctx, "id"))))))
        );
    }

    public static boolean teleportToRole(ServerPlayer player, String rawId) {
        if (player == null || rawId == null) return false;
        Target target = TARGETS.get(rawId.toLowerCase(Locale.ROOT));
        if (target == null) return false;
        ServerLevel level = player.getServer() == null ? null : player.getServer().getLevel(target.dimension());
        if (level == null) return false;
        BlockPos origin = target.anchorX() == null || target.anchorZ() == null
            ? level.getSharedSpawnPos()
            : new BlockPos(target.anchorX(), level.getMinBuildHeight() + 2, target.anchorZ());
        BlockPos pos = findSafeSurface(level, origin, target.spawnRadius(), player.getUUID().getLeastSignificantBits() ^ level.getGameTime());
        player.teleportTo(level, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), 0.0F);
        player.fallDistance = 0.0F;
        return true;
    }

    private static int teleport(ServerPlayer player, String rawId) {
        String id = rawId.toLowerCase(Locale.ROOT);
        Target target = TARGETS.get(id);
        if (target == null) {
            player.sendSystemMessage(Component.literal("[GGO] Unknown world: " + rawId).withStyle(ChatFormatting.RED));
            return 0;
        }
        if (!teleportToRole(player, id)) {
            player.sendSystemMessage(Component.literal("[GGO] " + target.title() + " is not mounted yet (MISSING DIMENSION: " + target.dimension().location() + ")")
                .withStyle(ChatFormatting.GOLD));
            return 0;
        }
        player.sendSystemMessage(Component.literal("[GGO] → " + target.title()).withStyle(ChatFormatting.AQUA));
        return Command.SINGLE_SUCCESS;
    }

    private static int status(MinecraftServer server, ServerPlayer player) {
        player.sendSystemMessage(Component.literal("GunGloryOnline • WORLDS").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        for (Target target : TARGETS.values()) {
            boolean loaded = server.getLevel(target.dimension()) != null;
            ChatFormatting color = loaded ? ChatFormatting.GREEN : ChatFormatting.GOLD;
            player.sendSystemMessage(Component.literal("  " + target.id() + " = " + (loaded ? "READY" : "MISSING") + " • " + target.title())
                .withStyle(color));
        }
        return Command.SINGLE_SUCCESS;
    }

    /** Finds a safe randomized surface position around the map's own anchor. */
    public static BlockPos findSafeSurface(ServerLevel level, BlockPos origin, int radius, long seed) {
        Random random = new Random(seed);
        int effectiveRadius = Math.max(0, radius);
        for (int attempt = 0; attempt < 96; attempt++) {
            int x = origin.getX() + (effectiveRadius == 0 ? 0 : random.nextInt(effectiveRadius * 2 + 1) - effectiveRadius);
            int z = origin.getZ() + (effectiveRadius == 0 ? 0 : random.nextInt(effectiveRadius * 2 + 1) - effectiveRadius);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 2) continue;
            if (!level.getWorldBorder().isWithinBounds(feet)) continue;
            BlockPos below = feet.below();
            if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
            if (!level.getBlockState(below).isSolidRender(level, below)) continue;
            if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(below).isEmpty()) continue;
            return feet;
        }
        int fallbackY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.getX(), origin.getZ());
        return new BlockPos(origin.getX(), Math.max(level.getMinBuildHeight() + 2, fallbackY), origin.getZ());
    }
}
