package arena.forge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Command-block-free Classic Arena jump pads.
 *
 * The supplied legacy world created permanent area-effect-clouds with Levitation 125/2t,
 * 111/3t and 105/4t for power_1/2/3. This service removes that Minecraft potion/AEC layer and
 * applies a direct server-authoritative movement impulse while keeping the same three relative tiers.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClassicArenaJumpPadService {
    public static final String VERSION = "GGO-CLASSIC-JUMPPAD-V1";

    private static final AABB ARENA = new AABB(47.0D, 60.0D, 47.0D, 113.0D, 110.0D, 113.0D);
    private static final double TRIGGER_RADIUS = 1.75D;
    private static final long PLAYER_COOLDOWN_TICKS = 8L;

    // Approximate final vertical velocities produced by the recovered short Levitation pulses.
    // We keep horizontal momentum untouched so bunnyhop/dash movement remains compatible.
    private static final double POWER_1_Y = 2.25D;
    private static final double POWER_2_Y = 2.75D;
    private static final double POWER_3_Y = 3.15D;

    private static final Map<UUID, Long> READY_AT = new HashMap<>();

    private ClassicArenaJumpPadService() {}

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) return;
        long now = event.getServer().getTickCount();
        // 10 Hz is plenty for a 1.75 block trigger volume and avoids scanning markers every tick.
        if ((now & 1L) != 0L) return;

        for (ServerLevel level : event.getServer().getAllLevels()) {
            List<Marker> pads = level.getEntities(EntityType.MARKER, ARENA,
                marker -> marker.getTags().contains("jump_pad_marker"));
            for (Marker pad : pads) trigger(level, pad, now);
        }

        if ((now % 200L) == 0L) READY_AT.entrySet().removeIf(e -> e.getValue() + 200L < now);
    }

    private static void trigger(ServerLevel level, Marker pad, long now) {
        double impulse = impulseFor(pad.getTags());
        if (impulse <= 0.0D) return;

        AABB area = pad.getBoundingBox().inflate(TRIGGER_RADIUS, 1.0D, TRIGGER_RADIUS);
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, area,
            player -> player.isAlive()
                && ClassicArenaMatchService.isParticipant(player)
                && ClassicArenaMatchService.state() == ClassicArenaMatchService.State.RUNNING
                && READY_AT.getOrDefault(player.getUUID(), 0L) <= now);

        for (ServerPlayer player : players) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, Math.max(motion.y, impulse), motion.z);
            player.hurtMarked = true;
            player.hasImpulse = true;
            READY_AT.put(player.getUUID(), now + PLAYER_COOLDOWN_TICKS);
            playSound(level, player);
        }
    }

    private static double impulseFor(Set<String> tags) {
        if (tags.contains("power_3")) return POWER_3_Y;
        if (tags.contains("power_2")) return POWER_2_Y;
        if (tags.contains("power_1")) return POWER_1_Y;
        return 0.0D;
    }

    private static void playSound(ServerLevel level, ServerPlayer player) {
        ResourceLocation id = ResourceLocation.tryParse("jeg:s1queence.custom.jumppad_jump");
        SoundEvent sound = id == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(id);
        if (sound != null) {
            level.playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 1.0F, 1.5F);
        }
    }
}
