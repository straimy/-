package arena.forge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GgoSquadNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation("gunnerarena", "ggo_squad"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .simpleChannel();

    private static int id;
    private static Consumer<Snapshot> snapshotConsumer = s -> {};
    private static Consumer<PingBroadcast> pingConsumer = p -> {};
    private static Consumer<ClearPing> clearConsumer = p -> {};

    private GgoSquadNetwork() {}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(GgoSquadNetwork::init);
    }

    private static void init() {
        CHANNEL.messageBuilder(SnapshotRequest.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> {})
                .decoder(b -> new SnapshotRequest())
                .consumerMainThread(GgoSquadNetwork::snapshotRequest0)
                .add();

        CHANNEL.messageBuilder(PingRequest.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> { b.writeInt(m.x()); b.writeInt(m.y()); b.writeInt(m.z()); b.writeUtf(m.type(), 16); })
                .decoder(b -> new PingRequest(b.readInt(), b.readInt(), b.readInt(), b.readUtf(16)))
                .consumerMainThread(GgoSquadNetwork::pingRequest0)
                .add();

        CHANNEL.messageBuilder(ClearRequest.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> {})
                .decoder(b -> new ClearRequest())
                .consumerMainThread(GgoSquadNetwork::clearRequest0)
                .add();

        CHANNEL.messageBuilder(Snapshot.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GgoSquadNetwork::encodeSnapshot)
                .decoder(GgoSquadNetwork::decodeSnapshot)
                .consumerMainThread((m, c) -> { snapshotConsumer.accept(m); c.get().setPacketHandled(true); })
                .add();

        CHANNEL.messageBuilder(PingBroadcast.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, b) -> { b.writeInt(m.x()); b.writeInt(m.y()); b.writeInt(m.z()); b.writeUtf(m.type(), 16); b.writeUtf(m.sender(), 32); b.writeLong(m.createdAtMillis()); })
                .decoder(b -> new PingBroadcast(b.readInt(), b.readInt(), b.readInt(), b.readUtf(16), b.readUtf(32), b.readLong()))
                .consumerMainThread((m, c) -> { pingConsumer.accept(m); c.get().setPacketHandled(true); })
                .add();

        CHANNEL.messageBuilder(ClearPing.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, b) -> b.writeUUID(m.senderId()))
                .decoder(b -> new ClearPing(b.readUUID()))
                .consumerMainThread((m, c) -> { clearConsumer.accept(m); c.get().setPacketHandled(true); })
                .add();
    }

    public static void setClientConsumers(Consumer<Snapshot> snapshots, Consumer<PingBroadcast> pings, Consumer<ClearPing> clears) {
        snapshotConsumer = snapshots == null ? s -> {} : snapshots;
        pingConsumer = pings == null ? p -> {} : pings;
        clearConsumer = clears == null ? p -> {} : clears;
    }

    public static void requestSnapshot() { CHANNEL.sendToServer(new SnapshotRequest()); }
    public static void sendPing(int x, int y, int z, String type) { CHANNEL.sendToServer(new PingRequest(x, y, z, type == null ? "MOVE" : type)); }
    public static void clearPing() { CHANNEL.sendToServer(new ClearRequest()); }
    public static void send(ServerPlayer player, Object message) {
        if (player != null && message != null) CHANNEL.sendTo(message, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    private static void snapshotRequest0(SnapshotRequest m, Supplier<NetworkEvent.Context> c) {
        ServerPlayer p = c.get().getSender();
        if (p != null) GgoSquadService.sendSnapshot(p);
        c.get().setPacketHandled(true);
    }

    private static void pingRequest0(PingRequest m, Supplier<NetworkEvent.Context> c) {
        ServerPlayer p = c.get().getSender();
        if (p != null) GgoSquadService.handlePing(p, m);
        c.get().setPacketHandled(true);
    }

    private static void clearRequest0(ClearRequest m, Supplier<NetworkEvent.Context> c) {
        ServerPlayer p = c.get().getSender();
        if (p != null) GgoSquadService.clearPing(p);
        c.get().setPacketHandled(true);
    }

    private static void encodeSnapshot(Snapshot s, FriendlyByteBuf b) {
        int count = Math.min(8, s.members().size());
        b.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Member m = s.members().get(i);
            b.writeUUID(m.id()); b.writeUtf(m.name(), 32); b.writeFloat(m.health()); b.writeFloat(m.maxHealth());
            b.writeVarInt(Math.max(0, m.pingMs())); b.writeBoolean(m.downed()); b.writeBoolean(m.voiceActive()); b.writeBoolean(m.leader());
            b.writeUtf(m.sector(), 24); b.writeUtf(m.activity(), 32);
        }
    }

    private static Snapshot decodeSnapshot(FriendlyByteBuf b) {
        int count = Math.min(8, Math.max(0, b.readVarInt()));
        List<Member> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            members.add(new Member(b.readUUID(), b.readUtf(32), b.readFloat(), b.readFloat(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readUtf(24), b.readUtf(32)));
        }
        return new Snapshot(members);
    }

    public record SnapshotRequest() {}
    public record PingRequest(int x, int y, int z, String type) {}
    public record ClearRequest() {}
    public record ClearPing(UUID senderId) {}
    public record PingBroadcast(int x, int y, int z, String type, String sender, long createdAtMillis) {}
    public record Member(UUID id, String name, float health, float maxHealth, int pingMs, boolean downed, boolean voiceActive, boolean leader, String sector, String activity) {}
    public record Snapshot(List<Member> members) {}
}
