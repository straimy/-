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

import java.util.function.Consumer;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class GgoHudNetwork {
    private static final String VERSION="2";
    private static int id;
    private static Consumer<Snapshot> clientConsumer=s->{};
    public static final SimpleChannel CHANNEL=NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation("gunnerarena","ggo_hud"))
            .networkProtocolVersion(()->VERSION).clientAcceptedVersions(VERSION::equals).serverAcceptedVersions(VERSION::equals).simpleChannel();
    private GgoHudNetwork(){}

    @SubscribeEvent public static void setup(FMLCommonSetupEvent e){e.enqueueWork(GgoHudNetwork::init);}
    private static void init(){
        CHANNEL.messageBuilder(Request.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{}).decoder(b->new Request()).consumerMainThread(GgoHudNetwork::request0).add();
        CHANNEL.messageBuilder(Snapshot.class,id++,NetworkDirection.PLAY_TO_CLIENT).encoder(GgoHudNetwork::encode).decoder(GgoHudNetwork::decode).consumerMainThread((m,c)->{clientConsumer.accept(m);c.get().setPacketHandled(true);}).add();
    }
    public static void setClientConsumer(Consumer<Snapshot> c){clientConsumer=c==null?s->{}:c;}
    public static void request(){CHANNEL.sendToServer(new Request());}
    public static void send(ServerPlayer p,Snapshot s){if(p!=null&&s!=null)CHANNEL.sendTo(s,p.connection.connection,NetworkDirection.PLAY_TO_CLIENT);}
    private static void request0(Request m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)send(p,GgoHudStateService.snapshot(p));c.get().setPacketHandled(true);}
    private static void encode(Snapshot s,FriendlyByteBuf b){
        b.writeUtf(s.activity(),32);b.writeUtf(s.title(),64);b.writeUtf(s.description(),120);b.writeUtf(s.progress(),64);b.writeBoolean(s.available());
        b.writeVarInt(Math.max(0,s.alive()));b.writeVarInt(Math.max(0,s.total()));b.writeVarInt(Math.max(0,s.placement()));
        b.writeVarInt(Math.max(0,s.zonePhase()));b.writeVarInt(Math.max(0,s.secondsRemaining()));b.writeBoolean(s.playerAlive());
    }
    private static Snapshot decode(FriendlyByteBuf b){
        return new Snapshot(b.readUtf(32),b.readUtf(64),b.readUtf(120),b.readUtf(64),b.readBoolean(),
                b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean());
    }
    public record Request(){}
    public record Snapshot(String activity,String title,String description,String progress,boolean available,
                           int alive,int total,int placement,int zonePhase,int secondsRemaining,boolean playerAlive){}
}
