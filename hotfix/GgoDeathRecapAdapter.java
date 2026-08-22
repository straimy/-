package arena.client.shell;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/** Reflection bridge keeps client-ui compilable while the core owns combat-report channels. */
public final class GgoDeathRecapAdapter {
    private static boolean attempted,installed;
    private GgoDeathRecapAdapter(){}

    public static void install(){
        if(installed||attempted)return;attempted=true;
        try{
            Class<?> c=Class.forName("arena.forge.GgoDeathRecapNetwork");
            Method recapSetter=c.getMethod("setClientConsumer",Consumer.class);
            Method feedSetter=c.getMethod("setKillFeedConsumer",Consumer.class);
            recapSetter.invoke(null,(Consumer<Object>)GgoDeathRecapAdapter::receiveRecap);
            feedSetter.invoke(null,(Consumer<Object>)GgoDeathRecapAdapter::receiveKillFeed);
            installed=true;
        }catch(ReflectiveOperationException|LinkageError ignored){}
    }

    private static void receiveRecap(Object packet){
        try{
            GgoDeathRecapState.set(new GgoDeathRecapState.Snapshot(
                str(packet,"killer"),str(packet,"weapon"),str(packet,"source"),str(packet,"sector"),
                number(packet,"distance"),number(packet,"finalDamage"),number(packet,"killerHealth"),number(packet,"killerMaxHealth"),longNumber(packet,"serverTick")));
        }catch(ReflectiveOperationException ignored){}
    }

    private static void receiveKillFeed(Object packet){
        try{
            GgoKillFeedState.add(str(packet,"killer"),str(packet,"victim"),str(packet,"weapon"),number(packet,"distance"),longNumber(packet,"serverTick"));
        }catch(ReflectiveOperationException ignored){}
    }

    private static Object value(Object o,String name)throws ReflectiveOperationException{return o.getClass().getMethod(name).invoke(o);}
    private static String str(Object o,String name)throws ReflectiveOperationException{return String.valueOf(value(o,name));}
    private static float number(Object o,String name)throws ReflectiveOperationException{return ((Number)value(o,name)).floatValue();}
    private static long longNumber(Object o,String name)throws ReflectiveOperationException{return ((Number)value(o,name)).longValue();}
}
