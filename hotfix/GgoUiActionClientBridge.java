package arena.client.shell;

import java.lang.reflect.Method;

/**
 * Reflection-safe client-ui bridge to the official Core C2S action channel.
 * Training and partial dev builds simply no-op when Core is absent.
 */
public final class GgoUiActionClientBridge {
    private static volatile boolean attempted;
    private static Method useMedicine,sortAmmo,selectSlot,dropSlot,swapSlots,clearField,dropAmmo;
    private GgoUiActionClientBridge(){}

    private static void install(){
        if(attempted)return;
        attempted=true;
        try{
            Class<?> type=Class.forName("arena.forge.GgoUiActionNetwork");
            useMedicine=type.getMethod("useMedicine",int.class);
            sortAmmo=type.getMethod("sortAmmo");
            selectSlot=type.getMethod("selectSlot",int.class);
            dropSlot=type.getMethod("dropSlot",int.class);
            swapSlots=type.getMethod("swapSlots",int.class,int.class);
            clearField=type.getMethod("clearField");
            dropAmmo=type.getMethod("dropAmmo");
        }catch(ReflectiveOperationException|LinkageError ignored){
            useMedicine=sortAmmo=selectSlot=dropSlot=swapSlots=clearField=dropAmmo=null;
        }
    }

    public static void useMedicine(int slot){install();invoke(useMedicine,slot);}
    public static void sortAmmo(){install();invoke(sortAmmo);}
    public static void selectSlot(int slot){install();invoke(selectSlot,slot);}
    public static void dropSlot(int slot){install();invoke(dropSlot,slot);}
    public static void swapSlots(int from,int to){install();invoke(swapSlots,from,to);}
    public static void clearField(){install();invoke(clearField);}
    public static void dropAmmo(){install();invoke(dropAmmo);}

    private static void invoke(Method method,Object... args){
        if(method==null)return;
        try{method.invoke(null,args);}catch(ReflectiveOperationException ignored){}
    }
}
