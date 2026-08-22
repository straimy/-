package arena.forge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Admin-only runtime palette collector. It scans an authored map incrementally while an admin
 * walks/flys through it, so the resource-pack slimmer can later keep only actually visible blocks.
 */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoMapPaletteAuditService {
    private static final Path OUTPUT=FMLPaths.CONFIGDIR.get().resolve("gunnerarena").resolve("ggo-map-palette.txt");
    private static final Set<String> BLOCKS=new HashSet<>();
    private static final int BUDGET_PER_TICK=4096;
    private static Scan scan;
    private GgoMapPaletteAuditService(){}

    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("ggopalette").requires(s->s.hasPermission(2))
            .then(Commands.literal("start")
                .executes(c->start(c.getSource().getPlayerOrException(),32))
                .then(Commands.argument("radius",IntegerArgumentType.integer(8,48))
                    .executes(c->start(c.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(c,"radius")))))
            .then(Commands.literal("stop").executes(c->stop(c.getSource().getPlayerOrException())))
            .then(Commands.literal("status").executes(c->status(c.getSource().getPlayerOrException())))
            .then(Commands.literal("export").executes(c->export(c.getSource().getPlayerOrException())))
            .then(Commands.literal("clear").executes(c->clear(c.getSource().getPlayerOrException()))));
    }

    private static int start(ServerPlayer p,int radius){
        int vertical=Math.min(32,Math.max(12,radius/2));
        scan=new Scan(p.getUUID(),p.level().dimension().location().toString(),p.blockPosition(),radius,vertical,0);
        p.sendSystemMessage(Component.literal("GGO PALETTE AUDIT ON // radius "+radius+" // fly through the authored map").withStyle(ChatFormatting.AQUA));
        return 1;
    }
    private static int stop(ServerPlayer p){scan=null;write();p.sendSystemMessage(Component.literal("GGO PALETTE AUDIT OFF // "+BLOCKS.size()+" unique blocks saved").withStyle(ChatFormatting.YELLOW));return 1;}
    private static int status(ServerPlayer p){
        String active=scan==null?"OFF":"ON // radius "+scan.radius+" // scanner "+scan.player;
        p.sendSystemMessage(Component.literal("GGO PALETTE // "+active+" // unique "+BLOCKS.size()+" // "+OUTPUT).withStyle(ChatFormatting.GRAY));return 1;
    }
    private static int export(ServerPlayer p){write();p.sendSystemMessage(Component.literal("GGO PALETTE EXPORTED // "+BLOCKS.size()+" blocks // "+OUTPUT).withStyle(ChatFormatting.GREEN));return 1;}
    private static int clear(ServerPlayer p){BLOCKS.clear();scan=null;write();p.sendSystemMessage(Component.literal("GGO PALETTE CLEARED").withStyle(ChatFormatting.RED));return 1;}

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||scan==null)return;
        var server=ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        ServerPlayer p=server.getPlayerList().getPlayer(scan.player);if(p==null)return;
        String dim=p.level().dimension().location().toString();
        BlockPos pos=p.blockPosition();
        if(!dim.equals(scan.dimension)||horizontalDistanceSq(pos,scan.center)>scan.recenterDistanceSq()){
            scan=new Scan(p.getUUID(),dim,pos,scan.radius,scan.vertical,0);
        }
        ServerLevel level=p.serverLevel();int volume=scan.volume();int processed=0;
        BlockPos.MutableBlockPos cursor=new BlockPos.MutableBlockPos();
        while(processed<BUDGET_PER_TICK&&scan.index<volume){
            int index=scan.index++;int side=scan.radius*2+1;int height=scan.vertical*2+1;
            int xIndex=index%side;int yz=index/side;int zIndex=yz%side;int yIndex=(yz/side)%height;
            int x=scan.center.getX()+xIndex-scan.radius;
            int z=scan.center.getZ()+zIndex-scan.radius;
            int y=scan.center.getY()+yIndex-scan.vertical;
            cursor.set(x,y,z);processed++;
            if(!level.hasChunkAt(cursor)||y<level.getMinBuildHeight()||y>=level.getMaxBuildHeight())continue;
            BlockState state=level.getBlockState(cursor);if(state.isAir())continue;
            ResourceLocation id=ForgeRegistries.BLOCKS.getKey(state.getBlock());if(id!=null)BLOCKS.add(id.toString());
        }
        if(scan.index>=volume){write();scan=new Scan(scan.player,scan.dimension,scan.center,scan.radius,scan.vertical,0);}
    }

    private static long horizontalDistanceSq(BlockPos a,BlockPos b){long dx=a.getX()-b.getX(),dz=a.getZ()-b.getZ();return dx*dx+dz*dz;}

    private static synchronized void write(){
        List<String> sorted=new ArrayList<>(BLOCKS);Collections.sort(sorted);
        int vanilla=0,modded=0;for(String id:sorted){if(id.startsWith("minecraft:"))vanilla++;else modded++;}
        List<String> lines=new ArrayList<>();
        lines.add("# GunGloryOnline authored-map palette audit");
        lines.add("# unique="+sorted.size()+" minecraft="+vanilla+" modded="+modded);
        lines.add("# Generated by /ggopalette; use this as INPUT for resource-pack slimming, not as a deletion command.");
        lines.addAll(sorted);
        try{
            Files.createDirectories(OUTPUT.getParent());Path tmp=OUTPUT.resolveSibling(OUTPUT.getFileName()+".tmp");
            Files.write(tmp,lines,StandardCharsets.UTF_8);Files.move(tmp,OUTPUT,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        }catch(IOException atomic){
            try{Path tmp=OUTPUT.resolveSibling(OUTPUT.getFileName()+".tmp");Files.write(tmp,lines,StandardCharsets.UTF_8);Files.move(tmp,OUTPUT,StandardCopyOption.REPLACE_EXISTING);}catch(IOException ignored){}
        }
    }

    private static final class Scan{
        final UUID player;final String dimension;final BlockPos center;final int radius,vertical;int index;
        Scan(UUID player,String dimension,BlockPos center,int radius,int vertical,int index){this.player=player;this.dimension=dimension;this.center=center.immutable();this.radius=radius;this.vertical=vertical;this.index=index;}
        int volume(){return (radius*2+1)*(radius*2+1)*(vertical*2+1);}
        long recenterDistanceSq(){int d=Math.max(6,radius/2);return (long)d*d;}
    }
}
