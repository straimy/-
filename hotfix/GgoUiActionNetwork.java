package arena.forge;

import arena.GunnerArenaMod;
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

import java.util.function.Supplier;

/**
 * Narrow client -> server action channel for the first-party GGO combat UI.
 *
 * No free-form command text crosses this channel. Every action is a small opcode plus bounded
 * integer slots, is re-authorized server-side, and is rate-limited before reaching inventory or
 * medicine services.
 */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class GgoUiActionNetwork {
    private static final String VERSION="1";
    private static final String NEXT_TICK_TAG="GgoUiActionNextTick";
    public static final int MEDICINE_USE=0;
    public static final int INVENTORY_AMMO=1;
    public static final int INVENTORY_SELECT=2;
    public static final int INVENTORY_DROP=3;
    public static final int INVENTORY_SWAP=4;
    public static final int INVENTORY_CLEAR=5;
    public static final int INVENTORY_DROP_AMMO=6;

    private static final SimpleChannel CHANNEL=NetworkRegistry.ChannelBuilder
        .named(new ResourceLocation("gunnerarena","ggo_ui_action"))
        .networkProtocolVersion(()->VERSION)
        .clientAcceptedVersions(VERSION::equals)
        .serverAcceptedVersions(VERSION::equals)
        .simpleChannel();
    private static int id;
    private GgoUiActionNetwork(){}

    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){event.enqueueWork(GgoUiActionNetwork::init);}
    private static void init(){
        CHANNEL.messageBuilder(Action.class,id++,NetworkDirection.PLAY_TO_SERVER)
            .encoder((m,b)->{b.writeVarInt(m.opcode());b.writeVarInt(m.a());b.writeVarInt(m.b());})
            .decoder(b->new Action(b.readVarInt(),b.readVarInt(),b.readVarInt()))
            .consumerMainThread(GgoUiActionNetwork::handle)
            .add();
    }

    public static void useMedicine(int slot){send(MEDICINE_USE,slot,0);}
    public static void sortAmmo(){send(INVENTORY_AMMO,0,0);}
    public static void selectSlot(int slot){send(INVENTORY_SELECT,slot,0);}
    public static void dropSlot(int slot){send(INVENTORY_DROP,slot,0);}
    public static void swapSlots(int from,int to){send(INVENTORY_SWAP,from,to);}
    public static void clearField(){send(INVENTORY_CLEAR,0,0);}
    public static void dropAmmo(){send(INVENTORY_DROP_AMMO,0,0);}
    private static void send(int opcode,int a,int b){CHANNEL.sendToServer(new Action(opcode,a,b));}

    private static void handle(Action action,Supplier<NetworkEvent.Context> context){
        NetworkEvent.Context ctx=context.get();
        ServerPlayer p=ctx.getSender();
        if(p!=null&&authorizedAndRateLimited(p)){
            switch(action.opcode()){
                case MEDICINE_USE -> GgoMedicineService.useFromUi(p,action.a());
                case INVENTORY_AMMO -> InventoryUtilityCommands.uiAmmo(p);
                case INVENTORY_SELECT -> InventoryUtilityCommands.uiSelect(p,action.a());
                case INVENTORY_DROP -> InventoryUtilityCommands.uiDrop(p,action.a());
                case INVENTORY_SWAP -> InventoryUtilityCommands.uiSwap(p,action.a(),action.b());
                case INVENTORY_CLEAR -> InventoryUtilityCommands.uiClear(p);
                case INVENTORY_DROP_AMMO -> InventoryUtilityCommands.uiDropAmmo(p);
                default -> { /* Unknown opcodes are ignored fail-closed. */ }
            }
        }
        ctx.setPacketHandled(true);
    }

    private static boolean authorizedAndRateLimited(ServerPlayer p){
        ArenaRuntime runtime=GunnerArenaMod.RUNTIME;
        if(runtime==null||!runtime.auth().isAuthenticated(p))return false;
        long now=runtime.serverTick();
        long next=p.getPersistentData().getLong(NEXT_TICK_TAG);
        if(now<next)return false;
        p.getPersistentData().putLong(NEXT_TICK_TAG,now+1L);
        return true;
    }

    public record Action(int opcode,int a,int b){}
}
