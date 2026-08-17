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

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ClanNetwork {
    private static final String VERSION="1";
    public static final SimpleChannel CHANNEL=NetworkRegistry.ChannelBuilder.named(new ResourceLocation("gunnerarena","clans"))
        .networkProtocolVersion(()->VERSION).clientAcceptedVersions(VERSION::equals).serverAcceptedVersions(VERSION::equals).simpleChannel();
    private static Consumer<Snapshot> clientConsumer=s->{};
    private static int id;
    private ClanNetwork(){}

    @SubscribeEvent public static void setup(FMLCommonSetupEvent e){e.enqueueWork(ClanNetwork::init);}
    private static void init(){
        CHANNEL.messageBuilder(Request.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{b.writeUtf(m.query,40);b.writeUtf(m.sort,16);}).decoder(b->new Request(b.readUtf(40),b.readUtf(16))).consumerMainThread(ClanNetwork::request0).add();
        CHANNEL.messageBuilder(Create.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{b.writeUtf(m.name,32);b.writeUtf(m.description,120);b.writeVarInt(m.entryPrice);}).decoder(b->new Create(b.readUtf(32),b.readUtf(120),b.readVarInt())).consumerMainThread(ClanNetwork::create0).add();
        CHANNEL.messageBuilder(Join.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->b.writeUtf(m.clanId,24)).decoder(b->new Join(b.readUtf(24))).consumerMainThread(ClanNetwork::join0).add();
        CHANNEL.messageBuilder(Leave.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{}).decoder(b->new Leave()).consumerMainThread(ClanNetwork::leave0).add();
        CHANNEL.messageBuilder(Settings.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{b.writeUtf(m.field,20);b.writeUtf(m.value,140);}).decoder(b->new Settings(b.readUtf(20),b.readUtf(140))).consumerMainThread(ClanNetwork::settings0).add();
        CHANNEL.messageBuilder(MemberAction.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{b.writeUtf(m.memberId,24);b.writeUtf(m.action,16);}).decoder(b->new MemberAction(b.readUtf(24),b.readUtf(16))).consumerMainThread(ClanNetwork::member0).add();
        CHANNEL.messageBuilder(Snapshot.class,id++,NetworkDirection.PLAY_TO_CLIENT).encoder(ClanNetwork::encode).decoder(ClanNetwork::decode).consumerMainThread((m,c)->{clientConsumer.accept(m);c.get().setPacketHandled(true);}).add();
    }
    public static void setClientConsumer(Consumer<Snapshot> c){clientConsumer=c==null?s->{}:c;}
    public static void request(String q,String sort){CHANNEL.sendToServer(new Request(q==null?"":q,sort==null?"WEALTH":sort));}
    public static void create(String name,String description,int entryPrice){CHANNEL.sendToServer(new Create(name==null?"":name,description==null?"":description,entryPrice));}
    public static void join(String id){CHANNEL.sendToServer(new Join(id==null?"":id));}
    public static void leave(){CHANNEL.sendToServer(new Leave());}
    public static void settings(String field,String value){CHANNEL.sendToServer(new Settings(field==null?"":field,value==null?"":value));}
    public static void member(String id,String action){CHANNEL.sendToServer(new MemberAction(id==null?"":id,action==null?"":action));}
    public static void send(ServerPlayer p,Snapshot s){CHANNEL.sendTo(s,p.connection.connection,NetworkDirection.PLAY_TO_CLIENT);}

    private static void request0(Request m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)ClanService.sendSnapshot(p,m.query,m.sort);c.get().setPacketHandled(true);}
    private static void create0(Create m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)ClanService.create(p,m.name,m.description,m.entryPrice);c.get().setPacketHandled(true);}
    private static void join0(Join m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)ClanService.join(p,m.clanId);c.get().setPacketHandled(true);}
    private static void leave0(Leave m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)ClanService.leave(p);c.get().setPacketHandled(true);}
    private static void settings0(Settings m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)ClanService.updateSetting(p,m.field,m.value);c.get().setPacketHandled(true);}
    private static void member0(MemberAction m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)ClanService.memberAction(p,m.memberId,m.action);c.get().setPacketHandled(true);}

    private static void encode(Snapshot s,FriendlyByteBuf b){
        b.writeBoolean(s.inClan);b.writeUtf(s.selfRole,12);b.writeUtf(s.clanId,24);b.writeUtf(s.clanName,32);b.writeUtf(s.description,120);b.writeVarInt(s.entryPrice);b.writeLong(s.treasury);b.writeVarInt(s.createCost);b.writeVarInt(s.renameCost);
        b.writeVarInt(s.members.size());for(Member m:s.members){b.writeUtf(m.publicId,24);b.writeUtf(m.name,32);b.writeUtf(m.role,12);b.writeUtf(m.status,12);}
        b.writeVarInt(s.results.size());for(ClanCard c:s.results){b.writeUtf(c.id,24);b.writeUtf(c.name,32);b.writeUtf(c.description,120);b.writeVarInt(c.members);b.writeLong(c.treasury);b.writeVarInt(c.entryPrice);}
    }
    private static Snapshot decode(FriendlyByteBuf b){
        boolean in=b.readBoolean();String role=b.readUtf(12),id=b.readUtf(24),name=b.readUtf(32),desc=b.readUtf(120);int price=b.readVarInt();long treasury=b.readLong();int create=b.readVarInt(),rename=b.readVarInt();
        int n=Math.min(128,b.readVarInt());List<Member> members=new ArrayList<>();for(int i=0;i<n;i++)members.add(new Member(b.readUtf(24),b.readUtf(32),b.readUtf(12),b.readUtf(12)));
        int k=Math.min(128,b.readVarInt());List<ClanCard> results=new ArrayList<>();for(int i=0;i<k;i++)results.add(new ClanCard(b.readUtf(24),b.readUtf(32),b.readUtf(120),b.readVarInt(),b.readLong(),b.readVarInt()));
        return new Snapshot(in,role,id,name,desc,price,treasury,create,rename,members,results);
    }

    public record Request(String query,String sort){}
    public record Create(String name,String description,int entryPrice){}
    public record Join(String clanId){}
    public record Leave(){}
    public record Settings(String field,String value){}
    public record MemberAction(String memberId,String action){}
    public record Member(String publicId,String name,String role,String status){}
    public record ClanCard(String id,String name,String description,int members,long treasury,int entryPrice){}
    public record Snapshot(boolean inClan,String selfRole,String clanId,String clanName,String description,int entryPrice,long treasury,int createCost,int renameCost,List<Member> members,List<ClanCard> results){}
}
