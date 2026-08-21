package arena.forge;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GgoSquadService {
    private static final int MAX_MEMBERS = 8;
    private static final double MAX_PING_DISTANCE = 512.0;
    private static final long PING_COOLDOWN_MS = 300L;
    private static final Map<UUID, Long> LAST_PING = new ConcurrentHashMap<>();

    private GgoSquadService() {}

    public static void sendSnapshot(ServerPlayer requester) {
        if (requester == null) return;
        List<GgoSquadNetwork.Member> members = new ArrayList<>();
        for (ServerPlayer p : membersFor(requester)) {
            if (members.size() >= MAX_MEMBERS) break;
            float max = Math.max(1.0F, p.getMaxHealth());
            float hp = Math.max(0.0F, p.getHealth());
            members.add(new GgoSquadNetwork.Member(
                    p.getUUID(), safe(p.getGameProfile().getName(), 32), hp, max,
                    Math.max(0, p.latency), hp <= 0.01F, false, false,
                    sectorFor(p.getBlockX(), p.getBlockZ()), "GGO"
            ));
        }
        GgoSquadNetwork.send(requester, new GgoSquadNetwork.Snapshot(members));
    }

    public static void handlePing(ServerPlayer sender, GgoSquadNetwork.PingRequest req) {
        if (sender == null || req == null) return;
        long now = System.currentTimeMillis();
        if (now - LAST_PING.getOrDefault(sender.getUUID(), 0L) < PING_COOLDOWN_MS) return;

        double dx = req.x() + 0.5D - sender.getX();
        double dy = req.y() + 0.5D - sender.getY();
        double dz = req.z() + 0.5D - sender.getZ();
        if (dx * dx + dy * dy + dz * dz > MAX_PING_DISTANCE * MAX_PING_DISTANCE) return;

        String type = normalizeType(req.type());
        LAST_PING.put(sender.getUUID(), now);
        GgoSquadNetwork.PingBroadcast msg = new GgoSquadNetwork.PingBroadcast(
                req.x(), req.y(), req.z(), type, safe(sender.getGameProfile().getName(), 32), now
        );
        for (ServerPlayer member : membersFor(sender)) GgoSquadNetwork.send(member, msg);
    }

    public static void clearPing(ServerPlayer sender) {
        if (sender == null) return;
        for (ServerPlayer member : membersFor(sender)) {
            GgoSquadNetwork.send(member, new GgoSquadNetwork.ClearPing(sender.getUUID()));
        }
    }

    private static List<ServerPlayer> membersFor(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return List.of(player);
        Team team = player.getTeam();
        if (team == null) return List.of(player);
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
            if (candidate.getTeam() == team) out.add(candidate);
        }
        out.sort(Comparator.comparing(p -> p.getGameProfile().getName(), String.CASE_INSENSITIVE_ORDER));
        return out.isEmpty() ? List.of(player) : out;
    }

    private static String normalizeType(String raw) {
        String value = raw == null ? "MOVE" : raw.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "ENEMY", "MOVE", "DANGER", "LOOT", "DEFEND", "REGROUP" -> value;
            default -> "MOVE";
        };
    }

    private static String sectorFor(int x, int z) {
        return "S" + Math.floorDiv(x, 512) + ":" + Math.floorDiv(z, 512);
    }

    private static String safe(String value, int max) {
        String v = value == null ? "" : value.replace("\r", "").replace("\n", "").replace("\t", "").trim();
        return v.length() <= max ? v : v.substring(0, max);
    }
}
