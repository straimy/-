package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import com.mojang.brigadier.Command;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * GGO ammo economy: a small set of fixed universal ammo boxes rather than giant per-ammo pickups.
 * Active boxes refill the reserve for the weapon currently held. 10% of respawns are rare purple boxes.
 */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class UniversalAmmoBoxService {
    private static final Path FILE=FMLPaths.CONFIGDIR.get().resolve("gunnerarena").resolve("ammo-boxes.properties");
    private static final List<Point> POINTS=new ArrayList<>();
    private static final Random RNG=new Random();
    private static boolean loaded;
    private UniversalAmmoBoxService(){}

    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("ammobox").requires(s->s.hasPermission(2))
            .then(Commands.literal("add").executes(c->add(c.getSource().getPlayerOrException())))
            .then(Commands.literal("clear").executes(c->clear(c.getSource().getServer())))
            .then(Commands.literal("list").executes(c->list(c.getSource().getPlayerOrException()))));
    }

    private static int add(ServerPlayer p){load();Point q=new Point(p.level().dimension(),p.getX(),p.getY()+.25,p.getZ(),0L,RNG.nextInt(10)==0);POINTS.add(q);save();p.sendSystemMessage(Component.literal("✦ AMMO BOX #"+POINTS.size()+" добавлен • поставь всего 4–6 точек").withStyle(ChatFormatting.AQUA));return Command.SINGLE_SUCCESS;}
    private static int clear(MinecraftServer server){load();POINTS.clear();save();return Command.SINGLE_SUCCESS;}
    private static int list(ServerPlayer p){load();p.sendSystemMessage(Component.literal("✦ Универсальные ammo-точки: "+POINTS.size()).withStyle(ChatFormatting.LIGHT_PURPLE));for(int i=0;i<POINTS.size();i++){Point q=POINTS.get(i);p.sendSystemMessage(Component.literal("#"+(i+1)+"  "+q.dimension.location()+"  "+Math.round(q.x)+" "+Math.round(q.y)+" "+Math.round(q.z)).withStyle(ChatFormatting.GRAY));}return Command.SINGLE_SUCCESS;}

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;ArenaRuntime r=GunnerArenaMod.RUNTIME;MinecraftServer server=ServerLifecycleHooks.getCurrentServer();if(r==null||server==null)return;load();long now=r.serverTick();if((now%5)!=0)return;
        for(Point q:POINTS){ServerLevel level=server.getLevel(q.dimension);if(level==null||now<q.readyAt)continue;
            if(q.rare){level.sendParticles(ParticleTypes.WITCH,q.x,q.y+.55,q.z,3,.18,.25,.18,.01);level.sendParticles(ParticleTypes.END_ROD,q.x,q.y+.35,q.z,1,.08,.12,.08,0);}
            else level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,q.x,q.y+.4,q.z,2,.16,.18,.16,.005);
            for(ServerPlayer p:level.players()){
                if(!p.isAlive()||!r.auth().isAuthenticated(p)||r.players().session(p).state()!=ArenaPlayerState.ALIVE)continue;
                double dx=p.getX()-q.x,dy=p.getY()+.5-q.y,dz=p.getZ()-q.z;if(dx*dx+dy*dy+dz*dz>3.24)continue;
                if(refill(p,q.rare,r)){q.readyAt=now+20L*(20+RNG.nextInt(21));q.rare=RNG.nextInt(10)==0;break;}
            }
        }
    }

    private static boolean refill(ServerPlayer p,boolean rare,ArenaRuntime r){
        ItemStack gun=p.getMainHandItem();ResourceLocation gunId=ForgeRegistries.ITEMS.getKey(gun.getItem());
        if(gun.isEmpty()||gunId==null||!"jeg".equals(gunId.getNamespace())||isKnife(gun,gunId)){
            p.displayClientMessage(Component.literal("✦ Возьми огнестрельное оружие в руку").withStyle(ChatFormatting.GRAY),true);return false;
        }
        Item ammo=ammoFor(gunId);if(ammo==null)return false;
        int mag=20;try{var def=r.weapons().get(gunId.toString());if(def!=null)mag=Math.max(1,def.magazineSize());}catch(Exception ignored){}
        int amount=rare?Math.max(8,mag*3):Math.max(4,(int)Math.ceil(mag*(1.2+RNG.nextDouble()*.35)));
        ItemStack supply=new ItemStack(ammo,Math.min(amount,supplyMax(ammo)));p.getInventory().add(supply);p.getInventory().setChanged();
        p.displayClientMessage(Component.literal(rare?"◆ РЕДКИЙ ЯЩИК • +"+amount+" патронов":"◇ БОЕПРИПАСЫ • +"+amount).withStyle(rare?ChatFormatting.LIGHT_PURPLE:ChatFormatting.AQUA),true);return true;
    }

    /** Small ammo sustain for winning a duel: about 10–20% of one magazine. */
    @SubscribeEvent public static void kill(LivingDeathEvent e){
        if(!(e.getSource().getEntity() instanceof ServerPlayer killer)||!(e.getEntity() instanceof ServerPlayer victim)||killer==victim)return;ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null||r.players().session(killer).state()!=ArenaPlayerState.ALIVE)return;
        ItemStack gun=killer.getMainHandItem();ResourceLocation gunId=ForgeRegistries.ITEMS.getKey(gun.getItem());if(gunId==null||!"jeg".equals(gunId.getNamespace())||isKnife(gun,gunId))return;Item ammo=ammoFor(gunId);if(ammo==null)return;
        int mag=20;try{var def=r.weapons().get(gunId.toString());if(def!=null)mag=Math.max(1,def.magazineSize());}catch(Exception ignored){}
        int n=Math.max(1,(int)Math.ceil(mag*(.10+RNG.nextDouble()*.10)));killer.getInventory().add(new ItemStack(ammo,n));killer.displayClientMessage(Component.literal("✦ +"+n+" патронов за убийство").withStyle(ChatFormatting.DARK_AQUA),true);
    }

    private static boolean isKnife(ItemStack s,ResourceLocation id){return s.hasTag()&&s.getTag().getBoolean("GunnerArenaKnife")||id.getPath().contains("knife")||id.getPath().contains("melee");}
    private static Item ammoFor(ResourceLocation gun){String p=gun.getPath();String aid=p.contains("shotgun")?"shotgun_shell":((p.contains("pistol")||p.contains("revolver")||p.contains("smg"))?"pistol_ammo":"rifle_ammo");Item item=ForgeRegistries.ITEMS.getValue(new ResourceLocation("jeg",aid));return item;}
    private static int supplyMax(Item item){return Math.max(1,item.getMaxStackSize());}

    private static synchronized void load(){if(loaded)return;loaded=true;if(!Files.isRegularFile(FILE))return;Properties p=new Properties();try(InputStream in=Files.newInputStream(FILE)){p.load(in);}catch(IOException ex){return;}int n=Integer.parseInt(p.getProperty("count","0"));for(int i=0;i<n;i++){try{ResourceLocation dim=new ResourceLocation(p.getProperty(i+".dim"));ResourceKey<Level> key=ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,dim);double x=Double.parseDouble(p.getProperty(i+".x")),y=Double.parseDouble(p.getProperty(i+".y")),z=Double.parseDouble(p.getProperty(i+".z"));POINTS.add(new Point(key,x,y,z,0L,RNG.nextInt(10)==0));}catch(Exception ignored){}}}
    private static synchronized void save(){Properties p=new Properties();p.setProperty("count",Integer.toString(POINTS.size()));for(int i=0;i<POINTS.size();i++){Point q=POINTS.get(i);p.setProperty(i+".dim",q.dimension.location().toString());p.setProperty(i+".x",Double.toString(q.x));p.setProperty(i+".y",Double.toString(q.y));p.setProperty(i+".z",Double.toString(q.z));}try{Files.createDirectories(FILE.getParent());Path tmp=FILE.resolveSibling(FILE.getFileName()+".tmp");try(OutputStream out=Files.newOutputStream(tmp)){p.store(out,"GunGloryOnline universal ammo boxes");}Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);}catch(IOException ignored){}}
    private static final class Point{final ResourceKey<Level> dimension;final double x,y,z;long readyAt;boolean rare;Point(ResourceKey<Level>d,double x,double y,double z,long readyAt,boolean rare){this.dimension=d;this.x=x;this.y=y;this.z=z;this.readyAt=readyAt;this.rare=rare;}}
}
