package arena.forge;

import com.mojang.brigadier.Command;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Server-authoritative supply/extraction foundation for Runtime v1.
 * Supply loot is explicitly tagged; arbitrary inventory items never count.
 */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class GgoSupplyExtractionService {
    public static final String SUPPLY_TAG="ggo_supply";
    private static final double EXTRACTION_RADIUS=4.0D;
    private static final Path FILE=FMLPaths.CONFIGDIR.get().resolve("gunnerarena").resolve("ggo-extraction.properties");
    private static final Properties DATA=new Properties();
    private static boolean loaded;

    private GgoSupplyExtractionService(){}

    public static ItemStack markSupply(ItemStack stack){
        if(stack==null||stack.isEmpty())return stack;
        stack.getOrCreateTag().putBoolean(SUPPLY_TAG,true);
        return stack;
    }

    public static boolean isSupply(ItemStack stack){
        CompoundTag tag=stack==null?null:stack.getTag();
        return stack!=null&&!stack.isEmpty()&&tag!=null&&tag.getBoolean(SUPPLY_TAG);
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("ggoextraction")
                .requires(s->s.hasPermission(2))
                .then(Commands.literal("set").executes(c->{
                    ServerPlayer p=c.getSource().getPlayerOrException();
                    setPoint(p.level().dimension(),p.getX(),p.getY(),p.getZ());
                    c.getSource().sendSuccess(()->Component.literal("GGO extraction point set here.").withStyle(ChatFormatting.GREEN),true);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("clear").executes(c->{
                    ServerPlayer p=c.getSource().getPlayerOrException();
                    clearPoint(p.level().dimension());
                    c.getSource().sendSuccess(()->Component.literal("GGO extraction point cleared.").withStyle(ChatFormatting.YELLOW),true);
                    return Command.SINGLE_SUCCESS;
                })));

        event.getDispatcher().register(Commands.literal("ggosupplytest")
                .requires(s->s.hasPermission(2))
                .executes(c->{
                    ServerPlayer p=c.getSource().getPlayerOrException();
                    ItemStack stack=markSupply(new ItemStack(Items.PAPER));
                    stack.setHoverName(Component.literal("GGO Marked Supplies"));
                    if(!p.getInventory().add(stack))p.drop(stack,false);
                    return Command.SINGLE_SUCCESS;
                }));
    }

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||event.player.level().isClientSide||!(event.player instanceof ServerPlayer p)||p.tickCount%10!=0)return;
        Point point=point(p.level().dimension());
        if(point==null)return;
        double dx=p.getX()-point.x,dy=p.getY()-point.y,dz=p.getZ()-point.z;
        if(dx*dx+dy*dy+dz*dz>EXTRACTION_RADIUS*EXTRACTION_RADIUS)return;

        GgoContractService.Contract contract=GgoContractService.list(p).stream()
                .filter(c->c.id().equals("supply_run"))
                .findFirst().orElse(null);
        if(contract==null||contract.completed())return;
        int needed=Math.max(0,contract.target()-contract.current());
        int extracted=consumeSupplies(p,needed);
        if(extracted<=0)return;

        GgoContractService.addProgress(p,"supply_run",extracted);
        p.displayClientMessage(Component.literal("SUPPLIES EXTRACTED  •  +"+extracted).withStyle(ChatFormatting.AQUA),true);
    }

    private static int consumeSupplies(ServerPlayer p,int limit){
        int remaining=Math.max(0,limit),removed=0;
        for(int i=0;i<p.getInventory().getContainerSize()&&remaining>0;i++){
            ItemStack stack=p.getInventory().getItem(i);
            if(!isSupply(stack))continue;
            int take=Math.min(remaining,stack.getCount());
            stack.shrink(take);
            remaining-=take;
            removed+=take;
        }
        return removed;
    }

    private static synchronized void setPoint(ResourceKey<Level> dimension,double x,double y,double z){
        load();String k=dimension.location().toString();
        DATA.setProperty(k+".x",Double.toString(x));DATA.setProperty(k+".y",Double.toString(y));DATA.setProperty(k+".z",Double.toString(z));save();
    }
    private static synchronized void clearPoint(ResourceKey<Level> dimension){
        load();String k=dimension.location().toString();
        DATA.remove(k+".x");DATA.remove(k+".y");DATA.remove(k+".z");save();
    }
    private static synchronized Point point(ResourceKey<Level> dimension){
        load();String k=dimension.location().toString();
        try{
            if(!DATA.containsKey(k+".x"))return null;
            return new Point(Double.parseDouble(DATA.getProperty(k+".x")),Double.parseDouble(DATA.getProperty(k+".y")),Double.parseDouble(DATA.getProperty(k+".z")));
        }catch(Exception ignored){return null;}
    }
    private static void load(){if(loaded)return;loaded=true;try{Files.createDirectories(FILE.getParent());if(Files.exists(FILE))try(InputStream in=Files.newInputStream(FILE)){DATA.load(in);}}catch(Exception ignored){}}
    private static void save(){try{Files.createDirectories(FILE.getParent());try(OutputStream out=Files.newOutputStream(FILE)){DATA.store(out,"GunGloryOnline extraction points");}}catch(Exception ignored){}}
    private record Point(double x,double y,double z){}
}
