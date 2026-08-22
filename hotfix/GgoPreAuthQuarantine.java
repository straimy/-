package arena.forge;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps an official-online connection outside gameplay until its launcher ticket is verified.
 * The quarantine is deliberately derived from GgoOfficialAuthState, so there is no second auth truth.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoPreAuthQuarantine {
    private static final int MAX_QUARANTINE_TICKS = 20 * 15;
    private static final Map<UUID, Anchor> ANCHORS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> QUARANTINE_TICKS = new ConcurrentHashMap<>();

    private GgoPreAuthQuarantine() {}

    @SubscribeEvent
    public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!quarantined(player)) return;
        UUID id = player.getUUID();
        ANCHORS.put(id, Anchor.capture(player));
        QUARANTINE_TICKS.put(id, 0);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0.0F;
    }

    @SubscribeEvent
    public static void leave(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        ANCHORS.remove(id);
        QUARANTINE_TICKS.remove(id);
        if (event.getEntity() instanceof ServerPlayer player) {
            GgoOfficialAuthState.clear(player);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !GgoOfficialAuthState.required()) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            if (!quarantined(player)) {
                ANCHORS.remove(id);
                QUARANTINE_TICKS.remove(id);
                continue;
            }

            int ticks = QUARANTINE_TICKS.merge(id, 1, Integer::sum);
            if (ticks >= MAX_QUARANTINE_TICKS) {
                ANCHORS.remove(id);
                QUARANTINE_TICKS.remove(id);
                GgoOfficialAuthState.verificationFailed(player);
                player.connection.disconnect(Component.literal(
                        "GunGloryOnline: secure session verification timed out. Return to the GGO launcher and press Play again."
                ));
                continue;
            }

            Anchor anchor = ANCHORS.computeIfAbsent(id, ignored -> Anchor.capture(player));
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
            if (player.position().distanceToSqr(anchor.position()) > 0.0025D) {
                player.teleportTo(anchor.x(), anchor.y(), anchor.z());
                player.setYRot(anchor.yRot());
                player.setXRot(anchor.xRot());
            }
        }
    }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && quarantined(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && quarantined(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void placeBlock(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && quarantined(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void pickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && quarantined(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void attack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && quarantined(victim)) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && quarantined(attacker)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void command(CommandEvent event) {
        if (!GgoOfficialAuthState.required()) return;
        try {
            ServerPlayer player = event.getParseResults().getContext().getSource().getPlayer();
            if (quarantined(player)) event.setCanceled(true);
        } catch (Exception ignored) {
            // Console, command blocks and non-player sources are not part of player quarantine.
        }
    }

    private static boolean quarantined(ServerPlayer player) {
        return player != null
                && GgoOfficialAuthState.required()
                && !GgoOfficialAuthState.isAuthenticated(player);
    }

    private record Anchor(double x, double y, double z, float yRot, float xRot) {
        static Anchor capture(ServerPlayer player) {
            return new Anchor(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        }

        Vec3 position() {
            return new Vec3(x, y, z);
        }
    }
}
