package arena.forge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Command-block-free pickups for the recovered Classic Arena.
 *
 * V1 intentionally migrates only the legacy small-health marker. Ammo slot pickups are kept out of
 * this class until their three-slot legacy loadout is mapped to the modern GGO weapon catalog.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClassicArenaPickupService {
    public static final String VERSION = "GGO-CLASSIC-PICKUPS-V1";

    // Recovered from the old command graph: distance=..1.3 and 16 second visual/cooldown cycle.
    private static final double PICKUP_RADIUS = 1.3D;
    private static final long HEALTH_COOLDOWN_TICKS = 16L * 20L;
    private static final AABB ARENA = new AABB(47.0D, 68.0D, 47.0D, 113.0D, 105.0D, 113.0D);
    private static final Map<UUID, Long> HEALTH_READY_AT = new HashMap<>();

    private ClassicArenaPickupService() {}

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) return;
        long now = event.getServer().getTickCount();
        // 5 Hz is enough for a 1.3 block pickup radius and avoids rescanning markers every tick.
        if ((now & 3L) != 0L) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            tickHealth(level, now);
        }
    }

    private static void tickHealth(ServerLevel level, long now) {
        List<Marker> markers = level.getEntities(EntityType.MARKER, ARENA, ClassicArenaPickupService::isHealthMarker);
        for (Marker marker : markers) {
            UUID markerId = marker.getUUID();
            if (HEALTH_READY_AT.getOrDefault(markerId, 0L) > now) continue;

            AABB pickup = marker.getBoundingBox().inflate(PICKUP_RADIUS);
            List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, pickup, player ->
                player.isAlive()
                    && "classic".equals(GgoGameModeRegistry.selectedMode(player))
                    && player.getHealth() < player.getMaxHealth()
            );
            if (players.isEmpty()) continue;

            ServerPlayer player = players.get(0);
            // Instant Health I on a normal living player corresponds to 4 health points (2 hearts).
            player.heal(Math.min(4.0F, player.getMaxHealth() - player.getHealth()));
            playPickupSound(level, marker);
            HEALTH_READY_AT.put(markerId, now + HEALTH_COOLDOWN_TICKS);
        }

        // Generated arenas replace marker entities each round; discard stale cooldown entries.
        if ((now % 200L) == 0L && !HEALTH_READY_AT.isEmpty()) {
            Set<UUID> live = new java.util.HashSet<>();
            for (Marker marker : markers) live.add(marker.getUUID());
            HEALTH_READY_AT.keySet().removeIf(id -> !live.contains(id));
        }
    }

    private static boolean isHealthMarker(Marker marker) {
        Set<String> tags = marker.getTags();
        return tags.contains("small_health_orb") || tags.contains("health_orb");
    }

    private static void playPickupSound(ServerLevel level, Marker marker) {
        ResourceLocation id = ResourceLocation.tryParse("jeg:s1queence.custom.health_pickup");
        SoundEvent sound = id == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(id);
        if (sound != null) {
            level.playSound(null, marker.blockPosition(), sound, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }
}
