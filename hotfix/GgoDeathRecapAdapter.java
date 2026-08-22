package arena.client.shell;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/** Reflection bridge keeps client-ui compilable while the core owns the packet channel. */
public final class GgoDeathRecapAdapter {
    private static boolean attempted,installed;
    private GgoDeathRecapAdapter(){}

    public static void install(){
        if(installed||attempted)return;attempted=true;
        try{
            Class<?> c=Class.forName("arena.forge.GgoDeathRecapNetwork");
            Method setter=c.getMethod("setClientConsumer",Consumer.class);
            setter.invoke(null,(Consumer<Object>)GgoDeathRecapAdapter::receive);
            installed=true;
        }catch(ReflectiveOperationException|LinkageError ignored){}
    }

    private static void receive(Object packet){
        try{
            GgoDeathRecapState.set(new GgoDeathRecapState.Snapshot(
                str(packet,"killer"),str(packet,"weapon"),str(packet,"source"),str(packet,"sector"),
                number(packet,"distance"),number(packet,"finalDamage"),number(packet,"killerHealth"),number(packet,"killerMaxHealth"),longNumber(packet,"serverTick")));
        }catch(ReflectiveOperationException ignored){}
    }
    private static Object value(Object o,String name)throws ReflectiveOperationException{return o.getClass().getMethod(name).invoke(o);}
    private static String str(Object o,String name)throws ReflectiveOperationException{return String.valueOf(value(o,name));}
    private static float number(Object o,String name)throws ReflectiveOperationException{return ((Number)value(o,name)).floatValue();}
    private static long longNumber(Object o,String name)throws ReflectiveOperationException{return ((Number)value(o,name)).longValue();}
}
