package arena.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime-neutral server authority for the player's currently tracked GGO objective.
 * Gameplay systems (contracts, Training, events, story) publish into this service;
 * the HUD only reads the sanitized snapshot and never owns objective progress.
 */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoObjectiveService {
    public record Objective(
            String id,
            String activity,
            String title,
            String description,
            int current,
            int target,
            boolean completed
    ) {
        public String progressText() {
            if (target <= 0) return completed ? "COMPLETE" : "";
            return Math.max(0, Math.min(current, target)) + "/" + target;
        }
    }

    private static final Map<UUID, Objective> ACTIVE = new ConcurrentHashMap<>();

    private GgoObjectiveService() {}

    public static Objective current(ServerPlayer player) {
        return player == null ? null : ACTIVE.get(player.getUUID());
    }

    public static void set(ServerPlayer player, String id, String activity, String title, String description, int current, int target) {
        if (player == null) return;
        int safeTarget = Math.max(0, target);
        int safeCurrent = Math.max(0, safeTarget > 0 ? Math.min(current, safeTarget) : current);
        ACTIVE.put(player.getUUID(), new Objective(
                safe(id, 48), safe(activity, 32), safe(title, 64), safe(description, 120),
                safeCurrent, safeTarget, safeTarget > 0 && safeCurrent >= safeTarget
        ));
    }

    public static void updateProgress(ServerPlayer player, int current) {
        if (player == null) return;
        ACTIVE.computeIfPresent(player.getUUID(), (id, old) -> {
            int safeCurrent = Math.max(0, old.target() > 0 ? Math.min(current, old.target()) : current);
            return new Objective(old.id(), old.activity(), old.title(), old.description(), safeCurrent, old.target(),
                    old.target() > 0 && safeCurrent >= old.target());
        });
    }

    public static void addProgress(ServerPlayer player, int delta) {
        Objective old = current(player);
        if (old == null || delta == 0) return;
        updateProgress(player, old.current() + delta);
    }

    public static void complete(ServerPlayer player) {
        if (player == null) return;
        ACTIVE.computeIfPresent(player.getUUID(), (id, old) -> new Objective(
                old.id(), old.activity(), old.title(), old.description(),
                old.target() > 0 ? old.target() : old.current(), old.target(), true
        ));
    }

    public static void clear(ServerPlayer player) {
        if (player != null) ACTIVE.remove(player.getUUID());
    }

    public static void clear(UUID playerId) {
        if (playerId != null) ACTIVE.remove(playerId);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getEntity().getUUID());
    }

    private static String safe(String value, int max) {
        String out = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        return out.length() <= max ? out : out.substring(0, max);
    }
}
