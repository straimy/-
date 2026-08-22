package arena.client.shell;

/** Last authoritative death report received from the GGO server. */
public final class GgoDeathRecapState {
    public record Snapshot(String killer,String weapon,String source,String sector,float distance,float finalDamage,float killerHealth,float killerMaxHealth,long serverTick){}
    private static volatile Snapshot snapshot;
    private GgoDeathRecapState(){}
    public static Snapshot get(){return snapshot;}
    public static void set(Snapshot next){snapshot=next;}
    public static void clear(){snapshot=null;}
}
