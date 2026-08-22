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

/** Authoritative victim recap + compact GGO kill-feed channel. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class GgoDeathRecapNetwork {
    private static final String VERSION="2";
    private static int id;
    private static Consumer<Snapshot> clientConsumer=s->{};
    private static Consumer<KillFeed> clientKillFeedConsumer=s->{};
    public static final SimpleChannel CHANNEL=NetworkRegistry.ChannelBuilder.named(new ResourceLocation("gunnerarena","ggo_death_recap"))
            .networkProtocolVersion(()->VERSION).clientAcceptedVersions(VERSION::equals).serverAcceptedVersions(VERSION::equals).simpleChannel();
    private GgoDeathRecapNetwork(){}

    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){event.enqueueWork(()->{
        CHANNEL.messageBuilder(Snapshot.class,id++,NetworkDirection.PLAY_TO_CLIENT)
            .encoder(GgoDeathRecapNetwork::encode).decoder(GgoDeathRecapNetwork::decode)
            .consumerMainThread((m,c)->{clientConsumer.accept(m);c.get().setPacketHandled(true);}).add();
        CHANNEL.messageBuilder(KillFeed.class,id++,NetworkDirection.PLAY_TO_CLIENT)
            .encoder(GgoDeathRecapNetwork::encodeKillFeed).decoder(GgoDeathRecapNetwork::decodeKillFeed)
            .consumerMainThread((m,c)->{clientKillFeedConsumer.accept(m);c.get().setPacketHandled(true);}).add();
    });}

    public static void setClientConsumer(Consumer<Snapshot> consumer){clientConsumer=consumer==null?s->{}:consumer;}
    public static void setKillFeedConsumer(Consumer<KillFeed> consumer){clientKillFeedConsumer=consumer==null?s->{}:consumer;}
    public static void send(ServerPlayer player,Snapshot snapshot){if(player!=null&&snapshot!=null)CHANNEL.sendTo(snapshot,player.connection.connection,NetworkDirection.PLAY_TO_CLIENT);}
    public static void sendKillFeed(ServerPlayer player,KillFeed entry){if(player!=null&&entry!=null)CHANNEL.sendTo(entry,player.connection.connection,NetworkDirection.PLAY_TO_CLIENT);}

    private static void encode(Snapshot s,FriendlyByteBuf b){
        b.writeUtf(s.killer(),64);b.writeUtf(s.weapon(),96);b.writeUtf(s.source(),64);b.writeUtf(s.sector(),24);
        b.writeFloat(s.distance());b.writeFloat(s.finalDamage());b.writeFloat(s.killerHealth());b.writeFloat(s.killerMaxHealth());b.writeLong(s.serverTick());
    }
    private static Snapshot decode(FriendlyByteBuf b){return new Snapshot(b.readUtf(64),b.readUtf(96),b.readUtf(64),b.readUtf(24),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readLong());}
    private static void encodeKillFeed(KillFeed s,FriendlyByteBuf b){b.writeUtf(s.killer(),64);b.writeUtf(s.victim(),64);b.writeUtf(s.weapon(),96);b.writeFloat(s.distance());b.writeLong(s.serverTick());}
    private static KillFeed decodeKillFeed(FriendlyByteBuf b){return new KillFeed(b.readUtf(64),b.readUtf(64),b.readUtf(96),b.readFloat(),b.readLong());}

    public record Snapshot(String killer,String weapon,String source,String sector,float distance,float finalDamage,float killerHealth,float killerMaxHealth,long serverTick){}
    public record KillFeed(String killer,String victim,String weapon,float distance,long serverTick){}
}
