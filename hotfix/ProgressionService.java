package arena.forge;

import arena.GunnerArenaMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Simple, persistent GGO progression: four useful combat skills + PvP rank XP. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class ProgressionService {
    private static final Path FILE=FMLPaths.CONFIGDIR.get().resolve("gunnerarena").resolve("progression.properties");
    private static final Properties DATA=new Properties();
    private static final Object LOCK=new Object();
    private static boolean loaded;
    private static final UUID SPEED_MOD=UUID.fromString("76df6a47-5485-4b78-9d25-502ad2439e91");
    private static final UUID HEALTH_MOD=UUID.fromString("bdb63ff7-40d5-49d7-8424-7f79d39ed87f");
    public static final int MAX_LEVEL=5;
    public static final int XP_PER_KILL=100;

    private static final String[] RANKS={"НОВОБРАНЕЦ","БОЕЦ","ШТУРМОВИК","ВЕТЕРАН","ЭЛИТА","АС","ЛЕГЕНДА"};
    private static final int[] THRESHOLDS={0,300,800,1500,2500,4000,6500};
    private ProgressionService(){}

    public static ProgressionNetwork.Snapshot snapshot(ServerPlayer p){
        synchronized(LOCK){load();UUID id=GgoIdentityBridge.idFor(p);int xp=get(id,"xp"),kills=get(id,"kills");int ri=rankIndex(xp);int next=ri+1<RANKS.length?THRESHOLDS[ri+1]:THRESHOLDS[ri];String nextName=ri+1<RANKS.length?RANKS[ri+1]:"MAX";
            return new ProgressionNetwork.Snapshot(level(id,"speed"),level(id,"health"),level(id,"damage"),level(id,"armor"),xp,kills,RANKS[ri],nextName,next,MAX_LEVEL,XP_PER_KILL);
        }
    }

    public static void upgrade(ServerPlayer p,String raw){
        String skill=raw==null?"":raw.trim().toLowerCase(Locale.ROOT);if(!Set.of("speed","health","damage","armor").contains(skill))return;
        synchronized(LOCK){load();UUID id=GgoIdentityBridge.idFor(p);int lv=level(id,skill);if(lv>=MAX_LEVEL){msg(p,"✓ Навык уже максимального уровня.",ChatFormatting.GREEN);ProgressionNetwork.send(p,snapshot(p));return;}
            int cost=upgradeCost(lv);var runtime=GunnerArenaMod.RUNTIME;if(runtime==null)return;var profile=runtime.players().profile(p);if(profile==null||profile.crystals<cost){msg(p,"✦ Нужно ◆ "+cost+" кристаллов.",ChatFormatting.RED);ProgressionNetwork.send(p,snapshot(p));return;}
            profile.crystals-=cost;runtime.profiles().markDirty(p.getUUID());set(id,skill,lv+1);save();applyAttributes(p);msg(p,"✓ Навык улучшен до "+(lv+1)+" уровня.",ChatFormatting.AQUA);
        }
        ProgressionNetwork.send(p,snapshot(p));
    }
    public static int upgradeCost(int currentLevel){return 20+currentLevel*15;}

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase!=TickEvent.Phase.END||e.player.level().isClientSide||!(e.player instanceof ServerPlayer p))return;if((p.tickCount%40)==0)applyAttributes(p);}
    private static void applyAttributes(ServerPlayer p){
        ProgressionNetwork.Snapshot s=snapshot(p);
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null){speed.removeModifier(SPEED_MOD);if(s.speed()>0)speed.addTransientModifier(new AttributeModifier(SPEED_MOD,"ggo_speed",s.speed()*0.03,AttributeModifier.Operation.MULTIPLY_TOTAL));}
        var health=p.getAttribute(Attributes.MAX_HEALTH);if(health!=null){health.removeModifier(HEALTH_MOD);if(s.health()>0)health.addTransientModifier(new AttributeModifier(HEALTH_MOD,"ggo_health",s.health()*2.0,AttributeModifier.Operation.ADDITION));if(p.getHealth()>p.getMaxHealth())p.setHealth(p.getMaxHealth());}
    }

    @SubscribeEvent public static void hurt(LivingHurtEvent e){
        if(e.getEntity().level().isClientSide)return;float amount=e.getAmount();
        if(e.getSource().getEntity() instanceof ServerPlayer attacker){int lv=snapshot(attacker).damage();if(lv>0)amount*=1.0f+0.025f*lv;}
        if(e.getEntity() instanceof ServerPlayer victim){int lv=snapshot(victim).armor();if(lv>0)amount*=1.0f-0.025f*lv;}
        e.setAmount(Math.max(0.0f,amount));
    }

    @SubscribeEvent public static void death(LivingDeathEvent e){
        if(e.getEntity().level().isClientSide||!(e.getEntity() instanceof ServerPlayer victim)||!(e.getSource().getEntity() instanceof ServerPlayer killer)||killer==victim)return;
        synchronized(LOCK){load();UUID id=GgoIdentityBridge.idFor(killer);int oldXp=get(id,"xp"),oldRank=rankIndex(oldXp);set(id,"xp",oldXp+XP_PER_KILL);set(id,"kills",get(id,"kills")+1);save();int newRank=rankIndex(oldXp+XP_PER_KILL);killer.sendSystemMessage(Component.literal("✦ +"+XP_PER_KILL+" XP за убийство").withStyle(ChatFormatting.AQUA));if(newRank>oldRank)killer.sendSystemMessage(Component.literal("★ НОВОЕ ЗВАНИЕ: "+RANKS[newRank]).withStyle(ChatFormatting.GOLD));}
        ProgressionNetwork.send(killer,snapshot(killer));
    }

    private static int rankIndex(int xp){int r=0;for(int i=1;i<THRESHOLDS.length;i++)if(xp>=THRESHOLDS[i])r=i;else break;return r;}
    private static int level(UUID id,String skill){return Math.max(0,Math.min(MAX_LEVEL,get(id,"skill."+skill)));}
    private static int get(UUID id,String key){try{return Integer.parseInt(DATA.getProperty("p."+id+"."+key,"0"));}catch(Exception e){return 0;}}
    private static void set(UUID id,String key,int v){DATA.setProperty("p."+id+"."+key,Integer.toString(Math.max(0,v)));}
    private static void msg(ServerPlayer p,String s,ChatFormatting c){p.sendSystemMessage(Component.literal(s).withStyle(c));}
    private static void load(){if(loaded)return;loaded=true;try{Files.createDirectories(FILE.getParent());if(Files.exists(FILE))try(InputStream in=Files.newInputStream(FILE)){DATA.load(in);}}catch(Exception ignored){}}
    private static void save(){try{Files.createDirectories(FILE.getParent());try(OutputStream out=Files.newOutputStream(FILE)){DATA.store(out,"GunGloryOnline progression");}}catch(Exception ignored){}}
}