package arena.forge;

import net.minecraft.server.level.ServerPlayer;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Small in-memory evidence ledger for the first GGO anti-cheat pass.
 *
 * Important: Stage 1 is REPORT ONLY. Evidence is never an automatic ban/kick decision.
 * The server remains authoritative and later enforcement can consume this ledger after thresholds
 * have been calibrated against real beta telemetry.
 */
public final class GgoAntiCheatEvidence {
    public enum Kind {
        HORIZONTAL_SPEED,
        VERTICAL_SPEED,
        TELEPORT_LIKE_MOVE,
        IMPOSSIBLE_AIR_CHAIN,
        WEAPON_STATE,
        COMBAT_RATE,
        INVENTORY_DESYNC,
        CLIENT_INTEGRITY
    }

    public record Evidence(
            long epochMillis,
            Kind kind,
            double weight,
            String detail
    ) {}

    private static final int MAX_EVIDENCE_PER_PLAYER = 64;
    private static final double MAX_SCORE = 100.0D;
    private static final Map<UUID, Deque<Evidence>> EVIDENCE = new HashMap<>();
    private static final Map<UUID, Double> SCORE = new HashMap<>();
    private static final Map<UUID, Long> LAST_NOTICE = new HashMap<>();

    private GgoAntiCheatEvidence() {}

    public static synchronized void record(ServerPlayer player, Kind kind, double weight, String detail) {
        if (player == null || kind == null) return;
        UUID id = player.getUUID();
        Deque<Evidence> list = EVIDENCE.computeIfAbsent(id, ignored -> new ArrayDeque<>());
        list.addLast(new Evidence(Instant.now().toEpochMilli(), kind, Math.max(0.0D, weight), sanitize(detail)));
        while (list.size() > MAX_EVIDENCE_PER_PLAYER) list.removeFirst();
        SCORE.put(id, Math.min(MAX_SCORE, SCORE.getOrDefault(id, 0.0D) + Math.max(0.0D, weight)));

        // Deliberately sparse report-only logging. Never print tokens, IPs, credentials or full packets.
        double score = SCORE.getOrDefault(id, 0.0D);
        long now = System.currentTimeMillis();
        long last = LAST_NOTICE.getOrDefault(id, 0L);
        if (score >= 10.0D && now - last >= 30_000L) {
            LAST_NOTICE.put(id, now);
            System.out.printf(
                    "[GGO-AC][REPORT] player=%s uuid=%s score=%.2f latest=%s detail=%s%n",
                    player.getGameProfile().getName(), id, score, kind.name(), sanitize(detail)
            );
        }
    }

    public static synchronized double score(UUID playerId) {
        return SCORE.getOrDefault(playerId, 0.0D);
    }

    public static synchronized List<Evidence> evidence(UUID playerId) {
        Deque<Evidence> list = EVIDENCE.get(playerId);
        return list == null ? List.of() : new ArrayList<>(list);
    }

    public static synchronized void decay(UUID playerId, double amount) {
        if (playerId == null || amount <= 0.0D) return;
        double next = Math.max(0.0D, SCORE.getOrDefault(playerId, 0.0D) - amount);
        if (next == 0.0D) SCORE.remove(playerId); else SCORE.put(playerId, next);
    }

    public static synchronized void clear(UUID playerId) {
        if (playerId == null) return;
        EVIDENCE.remove(playerId);
        SCORE.remove(playerId);
        LAST_NOTICE.remove(playerId);
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return clean.length() <= 180 ? clean : clean.substring(0, 180);
    }
}
