package arena.client.shell;

import java.util.ArrayList;
import java.util.List;

/** Short-lived GGO kill-feed entries; vanilla death chat is disabled server-side. */
public final class GgoKillFeedState {
    private static final long TTL_MS=8000L;
    private static final List<Entry> ENTRIES=new ArrayList<>();
    private GgoKillFeedState(){}

    public static synchronized void add(String killer,String victim,String weapon,float distance,long serverTick){
        prune();
        ENTRIES.add(0,new Entry(killer,victim,weapon,distance,serverTick,System.currentTimeMillis()));
        while(ENTRIES.size()>5)ENTRIES.remove(ENTRIES.size()-1);
    }
    public static synchronized List<Entry> visible(){prune();return List.copyOf(ENTRIES);}
    public static synchronized void clear(){ENTRIES.clear();}
    private static void prune(){long now=System.currentTimeMillis();ENTRIES.removeIf(e->now-e.receivedAtMillis>TTL_MS);}
    public record Entry(String killer,String victim,String weapon,float distance,long serverTick,long receivedAtMillis){}
}
