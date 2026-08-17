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
    private static final String VERSION="2";
    public static final SimpleChannel CHANNEL=NetworkRegistry.ChannelBuilder.named(new ResourceLocation("gunnerarena","friends"))
        .networkProtocolVersion(()->VERSION).clientAcceptedVersions(VERSION::equals).serverAcceptedVersions(VERSION::equals).simpleChannel();
    private static Consumer<Snapshot> clientConsumer=s->{};
    private static Consumer<ChatSnapshot> chatConsumer=s->{};
    private static int id;
    private FriendNetwork(){}

    @SubscribeEvent public static void setup(FMLCommonSetupEvent e){e.enqueueWork(FriendNetwork::init);}
    private static void init(){
        CHANNEL.messageBuilder(Request.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{}).decoder(b->new Request()).consumerMainThread(FriendNetwork::handleRequest).add();
        CHANNEL.messageBuilder(Add.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->b.writeUtf(m.token,64)).decoder(b->new Add(b.readUtf(64))).consumerMainThread(FriendNetwork::handleAdd).add();
        CHANNEL.messageBuilder(Accept.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->b.writeUtf(m.token,64)).decoder(b->new Accept(b.readUtf(64))).consumerMainThread(FriendNetwork::handleAccept).add();
        CHANNEL.messageBuilder(Decline.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->b.writeUtf(m.token,64)).decoder(b->new Decline(b.readUtf(64))).consumerMainThread(FriendNetwork::handleDecline).add();
        CHANNEL.messageBuilder(Remove.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->b.writeUtf(m.token,64)).decoder(b->new Remove(b.readUtf(64))).consumerMainThread(FriendNetwork::handleRemove).add();
        CHANNEL.messageBuilder(ToggleRequests.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->b.writeBoolean(m.allow)).decoder(b->new ToggleRequests(b.readBoolean())).consumerMainThread(FriendNetwork::handleToggle).add();
        CHANNEL.messageBuilder(ChatRequest.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->b.writeUtf(m.token,64)).decoder(b->new ChatRequest(b.readUtf(64))).consumerMainThread(FriendNetwork::handleChatRequest).add();
        CHANNEL.messageBuilder(SendMessage.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{b.writeUtf(m.token,64);b.writeUtf(m.text,160);}).decoder(b->new SendMessage(b.readUtf(64),b.readUtf(160))).consumerMainThread(FriendNetwork::handleSend).add();
        CHANNEL.messageBuilder(Snapshot.class,id++,NetworkDirection.PLAY_TO_CLIENT).encoder(FriendNetwork::encodeSnapshot).decoder(FriendNetwork::decodeSnapshot).consumerMainThread((m,c)->{clientConsumer.accept(m);c.get().setPacketHandled(true);}).add();
        CHANNEL.messageBuilder(ChatSnapshot.class,id++,NetworkDirection.PLAY_TO_CLIENT).encoder(FriendNetwork::encodeChat).decoder(FriendNetwork::decodeChat).consumerMainThread((m,c)->{chatConsumer.accept(m);c.get().setPacketHandled(true);}).add();
    }

    public static void setClientConsumer(Consumer<Snapshot> c){clientConsumer=c==null?s->{}:c;}
    public static void setChatConsumer(Consumer<ChatSnapshot> c){chatConsumer=c==null?s->{}:c;}
    public static void request(){CHANNEL.sendToServer(new Request());}
    public static void add(String token){CHANNEL.sendToServer(new Add(token==null?"":token));}
    public static void accept(String token){CHANNEL.sendToServer(new Accept(token==null?"":token));}
    public static void decline(String token){CHANNEL.sendToServer(new Decline(token==null?"":token));}
    public static void remove(String token){CHANNEL.sendToServer(new Remove(token==null?"":token));}
    public static void toggleRequests(boolean allow){CHANNEL.sendToServer(new ToggleRequests(allow));}
    public static void requestChat(String token){CHANNEL.sendToServer(new ChatRequest(token==null?"":token));}
    public static void sendMessage(String token,String text){CHANNEL.sendToServer(new SendMessage(token==null?"":token,text==null?"":text));}
    public static void send(ServerPlayer p,Snapshot s){CHANNEL.sendTo(s,p.connection.connection,NetworkDirection.PLAY_TO_CLIENT);}
    public static void sendChat(ServerPlayer p,ChatSnapshot s){CHANNEL.sendTo(s,p.connection.connection,NetworkDirection.PLAY_TO_CLIENT);}

    private static void handleRequest(Request m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)FriendService.sendSnapshot(p);c.get().setPacketHandled(true);}
    private static void handleAdd(Add m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)FriendService.sendRequest(p,m.token);c.get().setPacketHandled(true);}
    private static void handleAccept(Accept m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)FriendService.accept(p,m.token);c.get().setPacketHandled(true);}
    private static void handleDecline(Decline m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)FriendService.decline(p,m.token);c.get().setPacketHandled(true);}
    private static void handleRemove(Remove m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)FriendService.removeFriend(p,m.token);c.get().setPacketHandled(true);}
    private static void handleToggle(ToggleRequests m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)FriendService.setAllowRequests(p,m.allow);c.get().setPacketHandled(true);}
    private static void handleChatRequest(ChatRequest m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)FriendService.sendChat(p,m.token);c.get().setPacketHandled(true);}
    private static void handleSend(SendMessage m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)FriendService.sendMessage(p,m.token,m.text);c.get().setPacketHandled(true);}

    private static void encodeRow(FriendlyByteBuf b,Row r){b.writeUtf(r.publicId,32);b.writeUtf(r.name,32);b.writeUtf(r.status,12);b.writeUtf(r.avatarKey,64);}
    private static Row decodeRow(FriendlyByteBuf b){return new Row(b.readUtf(32),b.readUtf(32),b.readUtf(12),b.readUtf(64));}
    private static void encodeSnapshot(Snapshot m,FriendlyByteBuf b){b.writeUtf(m.selfId,32);b.writeBoolean(m.allowRequests);b.writeVarInt(m.rows.size());for(Row r:m.rows)encodeRow(b,r);b.writeVarInt(m.pending.size());for(Row r:m.pending)encodeRow(b,r);}
    private static Snapshot decodeSnapshot(FriendlyByteBuf b){String self=b.readUtf(32);boolean allow=b.readBoolean();int n=Math.min(256,b.readVarInt());List<Row> rows=new ArrayList<>();for(int i=0;i<n;i++)rows.add(decodeRow(b));int pn=Math.min(256,b.readVarInt());List<Row> pending=new ArrayList<>();for(int i=0;i<pn;i++)pending.add(decodeRow(b));return new Snapshot(self,allow,rows,pending);}
    private static void encodeChat(ChatSnapshot m,FriendlyByteBuf b){b.writeUtf(m.friendId,32);b.writeVarInt(m.lines.size());for(ChatLine l:m.lines){b.writeUtf(l.senderId,32);b.writeUtf(l.senderName,32);b.writeUtf(l.text,160);b.writeLong(l.time);}}
    private static ChatSnapshot decodeChat(FriendlyByteBuf b){String id=b.readUtf(32);int n=Math.min(40,b.readVarInt());List<ChatLine> lines=new ArrayList<>();for(int i=0;i<n;i++)lines.add(new ChatLine(b.readUtf(32),b.readUtf(32),b.readUtf(160),b.readLong()));return new ChatSnapshot(id,lines);}

    public record Request(){}
    public record Add(String token){}
    public record Accept(String token){}
    public record Decline(String token){}
    public record Remove(String token){}
    public record ToggleRequests(boolean allow){}
    public record ChatRequest(String token){}
    public record SendMessage(String token,String text){}
    public record Row(String publicId,String name,String status,String avatarKey){}
    public record Snapshot(String selfId,boolean allowRequests,List<Row> rows,List<Row> pending){}
    public record ChatLine(String senderId,String senderName,String text,long time){}
    public record ChatSnapshot(String friendId,List<ChatLine> lines){}
}
