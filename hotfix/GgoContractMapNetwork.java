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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Stage 27 server snapshot for contract-map UX.
 * Publishes only server-owned supply spawn points, extraction coordinates and economy state.
 */
@Mod.EventBusSubscriber(modid="gunnerarena", bus=Mod.EventBusSubscriber.Bus.MOD)
public final class GgoContractMapNetwork {
    private static final String VERSION="1";
    private static int id;
    private static Consumer<Snapshot> clientConsumer=s->{};
    public static final SimpleChannel CHANNEL=NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation("gunnerarena","ggo_contract_map"))
            .networkProtocolVersion(()->VERSION)
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .simpleChannel();

    private GgoContractMapNetwork(){}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent event){event.enqueueWork(GgoContractMapNetwork::init);}

    private static void init(){
        CHANNEL.messageBuilder(Request.class,id++,NetworkDirection.PLAY_TO_SERVER)
                .encoder((m,b)->{})
                .decoder(b->new Request())
                .consumerMainThread(GgoContractMapNetwork::request0)
                .add();
        CHANNEL.messageBuilder(Snapshot.class,id++,NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GgoContractMapNetwork::encode)
                .decoder(GgoContractMapNetwork::decode)
                .consumerMainThread((m,c)->{clientConsumer.accept(m);c.get().setPacketHandled(true);})
                .add();
    }

    public static void setClientConsumer(Consumer<Snapshot> consumer){clientConsumer=consumer==null?s->{}:consumer;}
    public static void request(){CHANNEL.sendToServer(new Request());}
    public static void sync(ServerPlayer player){if(player!=null)sendSnapshot(player);}

    private static void request0(Request message,Supplier<NetworkEvent.Context> context){
        ServerPlayer player=context.get().getSender();
        if(player!=null)sendSnapshot(player);
        context.get().setPacketHandled(true);
    }

    private static void sendSnapshot(ServerPlayer player){
        String dimension=player.level().dimension().location().toString();
        GgoSupplyExtractionService.ExtractionMarker extraction=GgoSupplyExtractionService.marker(player.level().dimension());
        List<Marker> markers=new ArrayList<>();
        for(GgoLootSpawnService.SupplyMarker marker:GgoLootSpawnService.supplyMarkers(player.level().dimension())){
            markers.add(new Marker(marker.id(),marker.x(),marker.y(),marker.z(),marker.available()));
        }
        boolean hasExtraction=extraction!=null;
        Snapshot snapshot=new Snapshot(
                dimension,
                hasExtraction,
                hasExtraction?extraction.x():0.0D,
                hasExtraction?extraction.y():0.0D,
                hasExtraction?extraction.z():0.0D,
                hasExtraction?extraction.radius():0.0D,
                Math.max(0L,balance(player)),
                markers
        );
        CHANNEL.sendTo(snapshot,player.connection.connection,NetworkDirection.PLAY_TO_CLIENT);
    }

    private static void encode(Snapshot snapshot,FriendlyByteBuf buffer){
        buffer.writeUtf(snapshot.dimension(),96);
        buffer.writeBoolean(snapshot.extractionAvailable());
        buffer.writeDouble(snapshot.extractionX());
        buffer.writeDouble(snapshot.extractionY());
        buffer.writeDouble(snapshot.extractionZ());
        buffer.writeDouble(snapshot.extractionRadius());
        buffer.writeLong(snapshot.creditBalance());
        int count=Math.min(64,snapshot.markers().size());
        buffer.writeVarInt(count);
        for(int i=0;i<count;i++){
            Marker marker=snapshot.markers().get(i);
            buffer.writeUtf(marker.id(),64);
            buffer.writeDouble(marker.x());
            buffer.writeDouble(marker.y());
            buffer.writeDouble(marker.z());
            buffer.writeBoolean(marker.available());
        }
    }

    private static Snapshot decode(FriendlyByteBuf buffer){
        String dimension=buffer.readUtf(96);
        boolean extractionAvailable=buffer.readBoolean();
        double extractionX=buffer.readDouble();
        double extractionY=buffer.readDouble();
        double extractionZ=buffer.readDouble();
        double extractionRadius=buffer.readDouble();
        long creditBalance=Math.max(0L,buffer.readLong());
        int count=Math.min(64,Math.max(0,buffer.readVarInt()));
        List<Marker> markers=new ArrayList<>(count);
        for(int i=0;i<count;i++){
            markers.add(new Marker(buffer.readUtf(64),buffer.readDouble(),buffer.readDouble(),buffer.readDouble(),buffer.readBoolean()));
        }
        return new Snapshot(dimension,extractionAvailable,extractionX,extractionY,extractionZ,extractionRadius,creditBalance,markers);
    }

    private static long balance(ServerPlayer player){
        try{
            var runtime=GunnerArenaMod.RUNTIME;
            if(runtime==null)return 0L;
            Object profile=runtime.players().profile(player);
            if(profile==null)return 0L;
            Field field=findField(profile.getClass(),"credits");
            if(field==null)field=findField(profile.getClass(),"crystals");
            if(field==null)return 0L;
            field.setAccessible(true);
            Object value=field.get(profile);
            return value instanceof Number number?number.longValue():0L;
        }catch(ReflectiveOperationException|RuntimeException ignored){return 0L;}
    }

    private static Field findField(Class<?> type,String name){
        Class<?> current=type;
        while(current!=null){
            try{return current.getDeclaredField(name);}
            catch(NoSuchFieldException ignored){current=current.getSuperclass();}
        }
        return null;
    }

    public record Request(){}
    public record Marker(String id,double x,double y,double z,boolean available){}
    public record Snapshot(String dimension,boolean extractionAvailable,double extractionX,double extractionY,double extractionZ,double extractionRadius,long creditBalance,List<Marker> markers){}
}
