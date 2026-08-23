package arena.forge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * One-shot launcher -> game -> official server authentication bridge.
 * The launcher ticket is intentionally never logged or persisted by the game server.
 */
@Mod.EventBusSubscriber(modid = "gunnerarena", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GgoLaunchTicketNetwork {
    // v3 adds bounded launcher/runtime build metadata to the authenticated entry handshake.
    private static final String PROTOCOL = "3";
    private static final int MAX_TICKET_LENGTH = 256;
    private static final int MAX_BUILD_ID_LENGTH = 96;
    private static final int MAX_HASH_LENGTH = 64;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("gunnerarena", "launch_ticket"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static volatile boolean clientVerificationExpected;
    private static volatile boolean clientVerificationComplete;
    private static boolean registered;

    private GgoLaunchTicketNetwork() {}

    @SubscribeEvent
    public static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(GgoLaunchTicketNetwork::register);
    }

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(
                0,
                LaunchTicket.class,
                GgoLaunchTicketNetwork::encode,
                GgoLaunchTicketNetwork::decode,
                GgoLaunchTicketNetwork::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
        CHANNEL.registerMessage(
                1,
                VerificationAck.class,
                GgoLaunchTicketNetwork::encodeAck,
                GgoLaunchTicketNetwork::decodeAck,
                GgoLaunchTicketNetwork::handleAck,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    /** Client-only call. The ticket should be read from the launcher-provided child-process environment once. */
    public static void sendTicket(String ticket) {
        if (ticket == null) return;
        String value = ticket.trim();
        if (value.isEmpty() || value.length() > MAX_TICKET_LENGTH) return;
        clientVerificationExpected = true;
        clientVerificationComplete = false;
        CHANNEL.sendToServer(new LaunchTicket(
                value,
                environment("GGO_CLIENT_BUILD_ID", MAX_BUILD_ID_LENGTH),
                environment("GGO_CORE_SHA256", MAX_HASH_LENGTH),
                environment("GGO_UI_SHA256", MAX_HASH_LENGTH)
        ));
    }

    /** Read by the UI module through reflection; no credential or account data is exposed. */
    public static boolean isClientVerificationExpected() {
        return clientVerificationExpected;
    }

    /** Read by the UI module through reflection; true only after the server has bound the verified account. */
    public static boolean isClientVerificationComplete() {
        return clientVerificationComplete;
    }

    private static void encode(LaunchTicket packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.ticket(), MAX_TICKET_LENGTH);
        buf.writeUtf(packet.buildId(), MAX_BUILD_ID_LENGTH);
        buf.writeUtf(packet.coreSha256(), MAX_HASH_LENGTH);
        buf.writeUtf(packet.uiSha256(), MAX_HASH_LENGTH);
    }

    private static LaunchTicket decode(FriendlyByteBuf buf) {
        return new LaunchTicket(
                buf.readUtf(MAX_TICKET_LENGTH),
                buf.readUtf(MAX_BUILD_ID_LENGTH),
                buf.readUtf(MAX_HASH_LENGTH),
                buf.readUtf(MAX_HASH_LENGTH)
        );
    }

    private static void encodeAck(VerificationAck packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.verified());
    }

    private static VerificationAck decodeAck(FriendlyByteBuf buf) {
        return new VerificationAck(buf.readBoolean());
    }

    private static void handleAck(VerificationAck packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> clientVerificationComplete = packet.verified());
        context.setPacketHandled(true);
    }

    private static void handle(LaunchTicket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer sender = context.getSender();
        if (sender == null) {
            context.setPacketHandled(true);
            return;
        }
        String ticket = packet.ticket() == null ? "" : packet.ticket().trim();
        if (!GgoOfficialAuthState.required()) {
            context.setPacketHandled(true);
            return;
        }
        if (ticket.isEmpty() || ticket.length() > MAX_TICKET_LENGTH) {
            context.enqueueWork(() -> sender.connection.disconnect(Component.literal("GunGloryOnline: invalid launcher session.")));
            context.setPacketHandled(true);
            return;
        }
        if (!GgoOfficialAuthState.beginVerification(sender)) {
            context.setPacketHandled(true);
            return;
        }

        UUID connectionId = sender.getUUID();
        MinecraftServer server = sender.getServer();
        consume(ticket).whenComplete((profile, error) -> {
            if (server == null) return;
            server.execute(() -> {
                ServerPlayer live = server.getPlayerList().getPlayer(connectionId);
                if (live == null) return;
                if (error != null || profile == null) {
                    GgoOfficialAuthState.verificationFailed(live);
                    live.connection.disconnect(Component.literal("GunGloryOnline: launcher session expired or was rejected. Return to the GGO launcher and press Play again."));
                    return;
                }
                try {
                    // Build metadata is only an integrity signal; the one-shot account ticket remains the authentication root.
                    GgoClientBuildPolicy.Result build = GgoClientBuildPolicy.evaluate(
                            live, packet.buildId(), packet.coreSha256(), packet.uiSha256());
                    if (build.enforced() && !build.accepted()) {
                        GgoOfficialAuthState.verificationFailed(live);
                        live.connection.disconnect(Component.literal("GunGloryOnline: this client build is not approved. Update or repair the game in the GGO launcher."));
                        return;
                    }
                    GgoOfficialAuthState.bind(live, profile.id(), profile.displayName(), profile.skinSource());
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> live), new VerificationAck(true));
                } catch (RuntimeException ex) {
                    GgoOfficialAuthState.verificationFailed(live);
                    live.connection.disconnect(Component.literal("GunGloryOnline: account identity could not be verified."));
                }
            });
        });
        context.setPacketHandled(true);
    }

    private static CompletableFuture<VerifiedTicketProfile> consume(String ticket) {
        JsonObject payload = new JsonObject();
        payload.addProperty("ticket", ticket);
        payload.addProperty("audience", "official-online");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GgoOfficialAuthState.authApiUrl() + "/auth/game-ticket/consume"))
                .timeout(Duration.ofSeconds(6))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-GGO-Server-Key", GgoOfficialAuthState.serverKey())
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) throw new IllegalStateException("ticket rejected");
                    JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (!root.has("valid") || !root.get("valid").getAsBoolean()) throw new IllegalStateException("ticket invalid");
                    JsonObject player = root.getAsJsonObject("player");
                    if (player == null) throw new IllegalStateException("ticket profile missing");
                    String id = string(player, "id");
                    String displayName = string(player, "display_name");
                    String skinSource = player.has("skin_source") ? string(player, "skin_source") : "default";
                    if (id.isBlank() || displayName.isBlank()) throw new IllegalStateException("ticket profile incomplete");
                    return new VerifiedTicketProfile(id, displayName, skinSource);
                });
    }

    private static String environment(String key, int maxLength) {
        String value = System.getenv(key);
        if (value == null) return "";
        value = value.trim();
        return value.length() <= maxLength ? value : "";
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private record LaunchTicket(String ticket, String buildId, String coreSha256, String uiSha256) {}
    private record VerificationAck(boolean verified) {}
    private record VerifiedTicketProfile(String id, String displayName, String skinSource) {}
}
