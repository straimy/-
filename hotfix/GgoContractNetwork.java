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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class GgoContractNetwork {
    private static final String VERSION="1";
    private static int id;
    private static Consumer<Snapshot> clientConsumer=s->{};
    public static final SimpleChannel CHANNEL=NetworkRegistry.ChannelBuilder.named(new ResourceLocation("gunnerarena","ggo_contracts"))
            .networkProtocolVersion(()->VERSION).clientAcceptedVersions(VERSION::equals).serverAcceptedVersions(VERSION::equals).simpleChannel();
    private GgoContractNetwork(){}

    @SubscribeEvent public static void setup(FMLCommonSetupEvent e){e.enqueueWork(GgoContractNetwork::init);}
    private static void init(){
        CHANNEL.messageBuilder(Request.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{}).decoder(b->new Request()).consumerMainThread(GgoContractNetwork::request0).add();
        CHANNEL.messageBuilder(Track.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->b.writeUtf(m.id(),48)).decoder(b->new Track(b.readUtf(48))).consumerMainThread(GgoContractNetwork::track0).add();
        CHANNEL.messageBuilder(Snapshot.class,id++,NetworkDirection.PLAY_TO_CLIENT).encoder(GgoContractNetwork::encode).decoder(GgoContractNetwork::decode).consumerMainThread((m,c)->{clientConsumer.accept(m);c.get().setPacketHandled(true);}).add();
    }
    public static void setClientConsumer(Consumer<Snapshot> c){clientConsumer=c==null?s->{}:c;}
    public static void request(){CHANNEL.sendToServer(new Request());}
    public static void track(String id){CHANNEL.sendToServer(new Track(id==null?"":id));}
    private static void request0(Request m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)sendSnapshot(p);c.get().setPacketHandled(true);}
    private static void track0(Track m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null){GgoContractService.track(p,m.id());sendSnapshot(p);}c.get().setPacketHandled(true);}
    private static void sendSnapshot(ServerPlayer p){
        List<Entry> entries=new ArrayList<>();
        for(var c:GgoContractService.list(p))entries.add(new Entry(c.id(),c.title(),c.description(),c.activity(),c.current(),c.target(),c.rewardCredits(),c.completed()));
        CHANNEL.sendTo(new Snapshot(GgoContractService.trackedId(p),entries),p.connection.connection,NetworkDirection.PLAY_TO_CLIENT);
    }
    private static void encode(Snapshot s,FriendlyByteBuf b){
        b.writeUtf(s.trackedId(),48);int n=Math.min(8,s.entries().size());b.writeVarInt(n);
        for(int i=0;i<n;i++){Entry e=s.entries().get(i);b.writeUtf(e.id(),48);b.writeUtf(e.title(),64);b.writeUtf(e.description(),120);b.writeUtf(e.activity(),32);b.writeVarInt(e.current());b.writeVarInt(e.target());b.writeVarInt(e.rewardCredits());b.writeBoolean(e.completed());}
    }
    private static Snapshot decode(FriendlyByteBuf b){
        String tracked=b.readUtf(48);int n=Math.min(8,Math.max(0,b.readVarInt()));List<Entry> entries=new ArrayList<>(n);
        for(int i=0;i<n;i++)entries.add(new Entry(b.readUtf(48),b.readUtf(64),b.readUtf(120),b.readUtf(32),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean()));
        return new Snapshot(tracked,entries);
    }
    public record Request(){}
    public record Track(String id){}
    public record Entry(String id,String title,String description,String activity,int current,int target,int rewardCredits,boolean completed){}
    public record Snapshot(String trackedId,List<Entry> entries){}
}
