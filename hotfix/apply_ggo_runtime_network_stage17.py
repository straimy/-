from pathlib import Path

ROOT = Path("ga-build") if Path("ga-build").exists() else Path(".")
JAVA = ROOT / "client-ui/src/main/java/arena/client/shell"
JAVA.mkdir(parents=True, exist_ok=True)

adapter = r'''package arena.client.shell;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Reflection keeps client-ui runtime-neutral: it compiles without a hard
 * dependency on the Core module, then binds to arena.forge.GgoSquadNetwork
 * when the official GGO Core is present at runtime. */
public final class GgoRuntimeV1NetworkAdapter {
    private static volatile boolean installed;
    private static volatile boolean attempted;
    private static Class<?> networkClass;
    private static Method requestSnapshot;
    private static Method sendPing;
    private static Method clearPing;

    private GgoRuntimeV1NetworkAdapter() {}

    public static void install() {
        if (installed || attempted) return;
        attempted = true;
        try {
            networkClass = Class.forName("arena.forge.GgoSquadNetwork");
            requestSnapshot = networkClass.getMethod("requestSnapshot");
            sendPing = networkClass.getMethod("sendPing", int.class, int.class, int.class, String.class);
            clearPing = networkClass.getMethod("clearPing");
            Method consumers = networkClass.getMethod("setClientConsumers", Consumer.class, Consumer.class, Consumer.class);
            consumers.invoke(null,
                    (Consumer<Object>) GgoRuntimeV1NetworkAdapter::receiveSnapshot,
                    (Consumer<Object>) GgoRuntimeV1NetworkAdapter::receivePing,
                    (Consumer<Object>) GgoRuntimeV1NetworkAdapter::receiveClear);

            GgoSquadSyncBridge.installTransport(() -> invoke(requestSnapshot));
            GgoSquadPingBridge.installTransport(new GgoSquadPingBridge.Transport() {
                @Override public void send(GgoSquadPingBridge.Ping ping) {
                    invoke(sendPing, ping.x(), ping.y(), ping.z(), ping.type().name());
                }
                @Override public void clear() { invoke(clearPing); }
            });
            installed = true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Training/offline and partial dev builds keep local-only behavior.
        }
    }

    public static boolean installed() { return installed; }

    private static void receiveSnapshot(Object snapshot) {
        try {
            Object raw = snapshot.getClass().getMethod("members").invoke(snapshot);
            if (!(raw instanceof List<?> list)) return;
            List<GgoSquadSyncBridge.MemberSnapshot> mapped = new ArrayList<>();
            for (Object m : list) {
                mapped.add(new GgoSquadSyncBridge.MemberSnapshot(
                        str(m, "name"), flt(m, "health"), flt(m, "maxHealth"), bool(m, "downed"),
                        integer(m, "pingMs"), bool(m, "voiceActive"), bool(m, "leader"), str(m, "sector"), str(m, "activity")
                ));
            }
            GgoSquadSyncBridge.receive(mapped);
        } catch (ReflectiveOperationException ignored) {}
    }

    private static void receivePing(Object packet) {
        try {
            GgoPingType type;
            try { type = GgoPingType.valueOf(str(packet, "type")); }
            catch (IllegalArgumentException ex) { type = GgoPingType.MOVE; }
            GgoSquadPingBridge.receiveSquadPing(new GgoSquadPingBridge.Ping(
                    integer(packet, "x"), integer(packet, "y"), integer(packet, "z"),
                    type, type.label(), str(packet, "sender"), lng(packet, "createdAtMillis")
            ));
        } catch (ReflectiveOperationException ignored) {}
    }

    private static void receiveClear(Object packet) {
        GgoNavigationState.clearWaypoint();
    }

    private static void invoke(Method method, Object... args) {
        if (method == null) return;
        try { method.invoke(null, args); } catch (ReflectiveOperationException ignored) {}
    }

    private static Object value(Object target, String accessor) throws ReflectiveOperationException {
        return target.getClass().getMethod(accessor).invoke(target);
    }
    private static String str(Object o, String n) throws ReflectiveOperationException { Object v=value(o,n); return v==null?"":String.valueOf(v); }
    private static int integer(Object o, String n) throws ReflectiveOperationException { return ((Number)value(o,n)).intValue(); }
    private static long lng(Object o, String n) throws ReflectiveOperationException { return ((Number)value(o,n)).longValue(); }
    private static float flt(Object o, String n) throws ReflectiveOperationException { return ((Number)value(o,n)).floatValue(); }
    private static boolean bool(Object o, String n) throws ReflectiveOperationException { return Boolean.TRUE.equals(value(o,n)); }
}
'''

(JAVA / "GgoRuntimeV1NetworkAdapter.java").write_text(adapter)

controller = JAVA / "GgoPingWheelController.java"
if controller.exists():
    s = controller.read_text()
    old = '''    public static void onClientTick(TickEvent.ClientTickEvent event) {\n        if (event.phase != TickEvent.Phase.END || !holding || target == null) return;'''
    new = '''    public static void onClientTick(TickEvent.ClientTickEvent event) {\n        if (event.phase != TickEvent.Phase.END) return;\n        GgoRuntimeV1NetworkAdapter.install();\n        if (!holding || target == null) return;'''
    if old in s:
        s = s.replace(old, new, 1)
    controller.write_text(s)

print("GGO Runtime Network Stage 17 applied")
print(" - reflection-safe client-ui -> Core network binding")
print(" - MMB tactical ping sends through GgoSquadNetwork")
print(" - server ping broadcasts feed Navigation/HUD")
print(" - TAB snapshot requests feed squad overlay")
print(" - Training/offline remains local-only if Core transport is absent")
