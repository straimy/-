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
public final class FriendNetwork {
    private static final String VERSION="1";
    public static final SimpleChannel CHANNEL= NetworkRegistry.ChannelBuilder.named(new ResourceLocation("gunnerarena","friends"))
        .networkProtocolVersion(()->VERSION).clientAcceptedVersions(VERSION::equals).serverAcceptedVersions(VERSION::equals).simpleChannel();
    private static Consumer<Snapshot> clientConsumer=s->{};
    private static int id;
    private FriendNetwork(){}

    @SubscribeEvent public static void setup(FMLCommonSetupEvent e){ e.enqueueWork(FriendNetwork::init); }
    private static void init(){
        CHANNEL.messageBuilder(Request.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{}).decoder(b->new Request()).consumerMainThread(FriendNetwork::handleRequest).add();
        CHANNEL.messageBuilder(Add.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->b.writeUtf(m.token,64)).decoder(b->new Add(b.readUtf(64))).consumerMainThread(FriendNetwork::handleAdd).add();
        CHANNEL.messageBuilder(Remove.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->b.writeUtf(m.token,64)).decoder(b->new Remove(b.readUtf(64))).consumerMainThread(FriendNetwork::handleRemove).add();
        CHANNEL.messageBuilder(Snapshot.class,id++,NetworkDirection.PLAY_TO_CLIENT).encoder(FriendNetwork::encodeSnapshot).decoder(FriendNetwork::decodeSnapshot).consumerMainThread((m,c)->{clientConsumer.accept(m);c.get().setPacketHandled(true);}).add();
    }
    public static void setClientConsumer(Consumer<Snapshot> c){clientConsumer=c==null?s->{}:c;}
    public static void request(){CHANNEL.sendToServer(new Request());}
    public static void add(String token){CHANNEL.sendToServer(new Add(token==null?"":token));}
    public static void remove(String token){CHANNEL.sendToServer(new Remove(token==null?"":token));}
    public static void send(ServerPlayer p,Snapshot s){CHANNEL.sendTo(s,p.connection.connection,NetworkDirection.PLAY_TO_CLIENT);}
    private static void handleRequest(Request m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)FriendService.sendSnapshot(p);c.get().setPacketHandled(true);}
    private static void handleAdd(Add m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)FriendService.addFriend(p,m.token);c.get().setPacketHandled(true);}
    private static void handleRemove(Remove m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)FriendService.removeFriend(p,m.token);c.get().setPacketHandled(true);}
    private static void encodeSnapshot(Snapshot m,FriendlyByteBuf b){b.writeUtf(m.selfId,32);b.writeVarInt(m.rows.size());for(Row r:m.rows){b.writeUtf(r.publicId,32);b.writeUtf(r.name,32);b.writeUtf(r.status,12);}}
    private static Snapshot decodeSnapshot(FriendlyByteBuf b){String self=b.readUtf(32);int n=Math.min(256,b.readVarInt());List<Row> rows=new ArrayList<>();for(int i=0;i<n;i++)rows.add(new Row(b.readUtf(32),b.readUtf(32),b.readUtf(12)));return new Snapshot(self,rows);}
    public record Request(){}
    public record Add(String token){}
    public record Remove(String token){}
    public record Row(String publicId,String name,String status){}
    public record Snapshot(String selfId,List<Row> rows){}
}
