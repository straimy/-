package arena.client.shell;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;

/**
 * Reads the short-lived launcher ticket from this game's child-process environment and forwards it once.
 * Reflection keeps the UI module compilable independently while Core owns the authenticated network channel.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoLaunchTicketClient {
    private static String ticket = readTicket();
    private static boolean sent;
    private static int retryTicks;

    private GgoLaunchTicketClient() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || sent || ticket == null) return;
        if (retryTicks > 0) { retryTicks--; return; }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) return;
        try {
            Class<?> network = Class.forName("arena.forge.GgoLaunchTicketNetwork");
            Method send = network.getMethod("sendTicket", String.class);
            send.invoke(null, ticket);
            sent = true;
            // Reduce the lifetime of the sensitive value inside the Java heap after the packet is encoded.
            ticket = null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            retryTicks = 20;
        }
    }

    private static String readTicket() {
        String value = System.getenv("GGO_GAME_TICKET");
        if (value == null) return null;
        value = value.trim();
        return value.isEmpty() || value.length() > 256 ? null : value;
    }
}
