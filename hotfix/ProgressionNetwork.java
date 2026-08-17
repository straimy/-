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
public final class ProgressionNetwork {
    private static final String VERSION="1";
    public static final SimpleChannel CHANNEL=NetworkRegistry.ChannelBuilder.named(new ResourceLocation("gunnerarena","progression"))
        .networkProtocolVersion(()->VERSION).clientAcceptedVersions(VERSION::equals).serverAcceptedVersions(VERSION::equals).simpleChannel();
    private static int id;
    private static Consumer<Snapshot> clientConsumer=s->{};
    private ProgressionNetwork(){}

    @SubscribeEvent public static void setup(FMLCommonSetupEvent e){e.enqueueWork(ProgressionNetwork::init);}
    private static void init(){
        CHANNEL.messageBuilder(Request.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->{}).decoder(b->new Request()).consumerMainThread(ProgressionNetwork::request0).add();
        CHANNEL.messageBuilder(Upgrade.class,id++,NetworkDirection.PLAY_TO_SERVER).encoder((m,b)->b.writeUtf(m.skill,16)).decoder(b->new Upgrade(b.readUtf(16))).consumerMainThread(ProgressionNetwork::upgrade0).add();
        CHANNEL.messageBuilder(Snapshot.class,id++,NetworkDirection.PLAY_TO_CLIENT).encoder(ProgressionNetwork::encode).decoder(ProgressionNetwork::decode).consumerMainThread((m,c)->{clientConsumer.accept(m);c.get().setPacketHandled(true);}).add();
    }
    public static void setClientConsumer(Consumer<Snapshot> c){clientConsumer=c==null?s->{}:c;}
    public static void request(){CHANNEL.sendToServer(new Request());}
    public static void upgrade(String skill){CHANNEL.sendToServer(new Upgrade(skill==null?"":skill));}
    public static void send(ServerPlayer p,Snapshot s){CHANNEL.sendTo(s,p.connection.connection,NetworkDirection.PLAY_TO_CLIENT);}
    private static void request0(Request m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)send(p,ProgressionService.snapshot(p));c.get().setPacketHandled(true);}
    private static void upgrade0(Upgrade m,Supplier<NetworkEvent.Context> c){ServerPlayer p=c.get().getSender();if(p!=null)ProgressionService.upgrade(p,m.skill);c.get().setPacketHandled(true);}
    private static void encode(Snapshot s,FriendlyByteBuf b){b.writeVarInt(s.speed);b.writeVarInt(s.health);b.writeVarInt(s.damage);b.writeVarInt(s.armor);b.writeVarInt(s.xp);b.writeVarInt(s.kills);b.writeUtf(s.rank,32);b.writeUtf(s.nextRank,32);b.writeVarInt(s.nextThreshold);b.writeVarInt(s.maxLevel);b.writeVarInt(s.xpPerKill);}
    private static Snapshot decode(FriendlyByteBuf b){return new Snapshot(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(32),b.readUtf(32),b.readVarInt(),b.readVarInt(),b.readVarInt());}
    public record Request(){}
    public record Upgrade(String skill){}
    public record Snapshot(int speed,int health,int damage,int armor,int xp,int kills,String rank,String nextRank,int nextThreshold,int maxLevel,int xpPerKill){}
}