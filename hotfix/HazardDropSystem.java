package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Low-frequency, marker-based arena drops.
 * Idle markers stay visibly labelled; a 5..1 warning replaces the label before each fast drop.
 */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class HazardDropSystem {
    public static final String TNT_TAG="gunnerarena_tnt_spawn";
    public static final String FIREBALL_TAG="gunnerarena_fireball_spawn";
    public static final String HEAL_TAG="gunnerarena_heal_spawn";

    private static final Random RANDOM=new Random();
    private static final Map<UUID,Long> NEXT_TNT=new HashMap<>();
    private static final Map<UUID,Long> NEXT_FIREBALL=new HashMap<>();
    private static final Map<UUID,Long> NEXT_HEAL=new HashMap<>();
    private static final Map<UUID,Warning> WARNINGS=new HashMap<>();
    private static final Set<UUID> ACTIVE_MARKERS=new HashSet<>();
    private static final Map<UUID,ActiveTnt> ACTIVE_TNT=new HashMap<>();
    private static final List<FireDrop> FIRE_DROPS=new ArrayList<>();
    private static final List<HealDrop> HEAL_DROPS=new ArrayList<>();

    private HazardDropSystem(){}

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onRegisterCommands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("setspawntnt").requires(HazardDropSystem::admin).executes(c->createMarker(c.getSource(),DropType.TNT)));
        e.getDispatcher().register(Commands.literal("setspawnfireball").requires(HazardDropSystem::admin).executes(c->createMarker(c.getSource(),DropType.FIREBALL)));
        e.getDispatcher().register(Commands.literal("setspawnheal").requires(HazardDropSystem::admin).executes(c->createMarker(c.getSource(),DropType.HEAL)));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END) return;
        MinecraftServer server=ServerLifecycleHooks.getCurrentServer();
        ArenaRuntime runtime=GunnerArenaMod.RUNTIME;
        if(server==null||runtime==null) return;
        long now=runtime.serverTick();
        scanMarkers(server,now);
        tickWarnings(server,now);
        tickTnt(server,runtime,now);
        tickFire(runtime);
        tickHeal(runtime);
    }

    private static void scanMarkers(MinecraftServer server,long now){
        Set<UUID> liveTnt=new HashSet<>(),liveFire=new HashSet<>(),liveHeal=new HashSet<>();
        for(ServerLevel level:server.getAllLevels()){
            for(Entity entity:level.getAllEntities()){
                if(!(entity instanceof ArmorStand marker)) continue;
                DropType type=typeOf(marker);
                if(type==null) continue;
                switch(type){case TNT->liveTnt.add(marker.getUUID());case FIREBALL->liveFire.add(marker.getUUID());case HEAL->liveHeal.add(marker.getUUID());}
                styleMarker(marker,type,!ACTIVE_MARKERS.contains(marker.getUUID()));
                Map<UUID,Long> schedule=schedule(type);
                long due=schedule.computeIfAbsent(marker.getUUID(),u->now+randomInterval(type));
                if(now>=due&&!ACTIVE_MARKERS.contains(marker.getUUID())){
                    startWarning(marker,type);
                    schedule.put(marker.getUUID(),now+randomInterval(type));
                }
            }
        }
        NEXT_TNT.keySet().retainAll(liveTnt);
        NEXT_FIREBALL.keySet().retainAll(liveFire);
        NEXT_HEAL.keySet().retainAll(liveHeal);
        ACTIVE_MARKERS.removeIf(id->!liveTnt.contains(id)&&!liveFire.contains(id)&&!liveHeal.contains(id));
    }

    private static void startWarning(ArmorStand marker,DropType type){
        ACTIVE_MARKERS.add(marker.getUUID());
        marker.setCustomNameVisible(false);
        WARNINGS.put(marker.getUUID(),new Warning(marker.serverLevel(),marker.getUUID(),type,marker.getX(),marker.getY(),marker.getZ(),100,-1));
    }

    private static void tickWarnings(MinecraftServer server,long now){
        Iterator<Map.Entry<UUID,Warning>> it=WARNINGS.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry<UUID,Warning> en=it.next();
            Warning w=en.getValue();
            int sec=Math.max(1,(w.ticks+19)/20);
            if(sec!=w.lastSecond){
                w.lastSecond=sec;
                for(ServerPlayer p:w.level.players()) if(p.distanceToSqr(w.x,w.y,w.z)<=48*48) showCountdown(server,p,w.type,sec);
            }
            w.ticks--;
            if(w.ticks>0) continue;
            it.remove();
            switch(w.type){
                case TNT->spawnTnt(w,now);
                case FIREBALL->FIRE_DROPS.add(new FireDrop(w.level,w.marker,w.x,w.y,w.z,w.y+21.0,24));
                case HEAL->spawnHealVisual(w);
            }
        }
    }

    private static void spawnTnt(Warning w,long now){
        PrimedTnt t=EntityType.TNT.create(w.level);
        if(t==null){finishMarker(w.level,w.marker,w.type);return;}
        t.moveTo(w.x,w.y+3.3,w.z,0,0);
        t.setFuse(40);
        w.level.addFreshEntity(t);
        ACTIVE_TNT.put(t.getUUID(),new ActiveTnt(w.level,w.marker,now+30));
    }

    private static void tickTnt(MinecraftServer server,ArenaRuntime runtime,long now){
        Iterator<Map.Entry<UUID,ActiveTnt>> it=ACTIVE_TNT.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry<UUID,ActiveTnt> en=it.next();
            ActiveTnt active=en.getValue();
            if(now<active.explodeAt) continue;
            Entity entity=active.level.getEntity(en.getKey());
            it.remove();
            if(!(entity instanceof PrimedTnt t)){finishMarker(active.level,active.marker,DropType.TNT);continue;}
            double x=t.getX(),y=t.getY(),z=t.getZ();
            t.discard();
            active.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,x,y,z,1,0,0,0,0);
            active.level.playSound(null,x,y,z,SoundEvents.GENERIC_EXPLODE,SoundSource.BLOCKS,4,1);
            for(ServerPlayer p:active.level.players()){
                if(!canAffect(runtime,p)) continue;
                double d=p.distanceToSqr(x,y,z);
                if(d<=49) p.hurt(p.damageSources().generic(),8);
                if(d<=100) p.addEffect(new MobEffectInstance(MobEffects.POISON,60,0));
            }
            finishMarker(active.level,active.marker,DropType.TNT);
        }
    }

    private static void tickFire(ArenaRuntime runtime){
        Iterator<FireDrop> it=FIRE_DROPS.iterator();
        while(it.hasNext()){
            FireDrop f=it.next();
            double progress=1.0-(f.ticks/24.0);
            double y=f.startY+(f.groundY-f.startY)*progress;
            f.level.sendParticles(ParticleTypes.FLAME,f.x,y,f.z,10,.15,.28,.15,.015);
            f.level.sendParticles(ParticleTypes.SMOKE,f.x,y,f.z,4,.12,.2,.12,.01);
            f.ticks--;
            if(f.ticks>0) continue;
            it.remove();
            f.level.sendParticles(ParticleTypes.FLAME,f.x,f.groundY+.2,f.z,90,2.2,.5,2.2,.04);
            f.level.sendParticles(ParticleTypes.LAVA,f.x,f.groundY+.2,f.z,22,1.3,.2,1.3,.02);
            f.level.playSound(null,f.x,f.groundY,f.z,SoundEvents.FIRECHARGE_USE,SoundSource.BLOCKS,2.4f,.75f);
            for(ServerPlayer p:f.level.players()) if(canAffect(runtime,p)&&p.distanceToSqr(f.x,f.groundY,f.z)<=25){p.hurt(p.damageSources().onFire(),5);p.setSecondsOnFire(4);}
            finishMarker(f.level,f.marker,DropType.FIREBALL);
        }
    }

    private static void spawnHealVisual(Warning w){
        ArmorStand visual=EntityType.ARMOR_STAND.create(w.level);
        if(visual==null){finishMarker(w.level,w.marker,w.type);return;}
        visual.moveTo(w.x,w.y+10,w.z,0,0);
        visual.setInvisible(true);
        visual.setNoGravity(true);
        visual.setInvulnerable(true);
        visual.setSmall(true);
        visual.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.EMERALD_BLOCK));
        visual.addTag("gunnerarena_heal_drop_visual");
        w.level.addFreshEntity(visual);
        HEAL_DROPS.add(new HealDrop(w.level,w.marker,visual.getUUID(),w.x,w.y,w.z,w.y+10,24));
    }

    private static void tickHeal(ArenaRuntime runtime){
        Iterator<HealDrop> it=HEAL_DROPS.iterator();
        while(it.hasNext()){
            HealDrop h=it.next();
            Entity visual=h.level.getEntity(h.visual);
            double progress=1.0-(h.ticks/24.0);
            double y=h.startY+(h.groundY-h.startY)*progress;
            if(visual!=null) visual.setPos(h.x,y,h.z);
            h.level.sendParticles(ParticleTypes.HAPPY_VILLAGER,h.x,y,h.z,4,.25,.25,.25,.01);
            h.ticks--;
            if(h.ticks>0) continue;
            it.remove();
            if(visual!=null) visual.discard();
            h.level.sendParticles(ParticleTypes.HAPPY_VILLAGER,h.x,h.groundY+.3,h.z,100,2.4,.7,2.4,.05);
            h.level.sendParticles(ParticleTypes.COMPOSTER,h.x,h.groundY+.3,h.z,55,2.0,.5,2.0,.04);
            h.level.playSound(null,h.x,h.groundY,h.z,SoundEvents.EXPERIENCE_ORB_PICKUP,SoundSource.PLAYERS,1.5f,.8f);
            for(ServerPlayer p:h.level.players()){
                if(!canAffect(runtime,p)) continue;
                double d=Math.sqrt(p.distanceToSqr(h.x,h.groundY,h.z));
                if(d<=3.0) p.setHealth(p.getMaxHealth());
                else if(d<=5.0) p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+10.0f));
                else if(d<=10.0) p.setHealth(Math.min(p.getMaxHealth(),p.getHealth()+4.0f));
            }
            finishMarker(h.level,h.marker,DropType.HEAL);
        }
    }

    private static boolean canAffect(ArenaRuntime runtime,ServerPlayer p){
        return runtime.auth().isAuthenticated(p)&&runtime.players().session(p).state()==ArenaPlayerState.ALIVE&&!runtime.safeRegions().isSafe(p);
    }

    private static int createMarker(CommandSourceStack source,DropType type){
        ServerPlayer p;
        try{p=source.getPlayerOrException();}catch(Exception x){return 0;}
        ArmorStand marker=EntityType.ARMOR_STAND.create(p.serverLevel());
        if(marker==null) return 0;
        marker.moveTo(p.getX(),p.getY(),p.getZ(),0,0);
        marker.setInvisible(true);
        marker.setNoGravity(true);
        marker.setInvulnerable(true);
        marker.addTag(type.tag);
        styleMarker(marker,type,true);
        p.serverLevel().addFreshEntity(marker);
        long now=GunnerArenaMod.RUNTIME==null?0:GunnerArenaMod.RUNTIME.serverTick();
        schedule(type).put(marker.getUUID(),now+randomInterval(type));
        String msg=switch(type){case TNT->"[GGO] TNT spawn создан • примерно раз в 45–90 сек";case FIREBALL->"[GGO] Fireball spawn создан • примерно раз в 55–105 сек";case HEAL->"[GGO] Heal spawn создан • примерно раз в 65–120 сек";};
        source.sendSuccess(()->Component.literal(msg).withStyle(type.color),false);
        return 1;
    }

    private static void styleMarker(ArmorStand marker,DropType type,boolean visible){
        marker.setCustomName(Component.literal(type.idleLabel).withStyle(type.color,ChatFormatting.BOLD));
        marker.setCustomNameVisible(visible);
        marker.setGlowingTag(true);
    }

    private static void finishMarker(ServerLevel level,UUID markerId,DropType type){
        ACTIVE_MARKERS.remove(markerId);
        Entity entity=level.getEntity(markerId);
        if(entity instanceof ArmorStand marker) styleMarker(marker,type,true);
    }

    private static DropType typeOf(ArmorStand m){
        if(m.getTags().contains(TNT_TAG)) return DropType.TNT;
        if(m.getTags().contains(FIREBALL_TAG)) return DropType.FIREBALL;
        if(m.getTags().contains(HEAL_TAG)) return DropType.HEAL;
        return null;
    }

    private static Map<UUID,Long> schedule(DropType type){return switch(type){case TNT->NEXT_TNT;case FIREBALL->NEXT_FIREBALL;case HEAL->NEXT_HEAL;};}
    private static int randomInterval(DropType type){return switch(type){case TNT->randomTicks(45,90);case FIREBALL->randomTicks(55,105);case HEAL->randomTicks(65,120);};}
    private static int randomTicks(int minSec,int maxSec){return(minSec+RANDOM.nextInt(maxSec-minSec+1))*20;}

    private static void showCountdown(MinecraftServer server,ServerPlayer p,DropType type,int seconds){
        String color=seconds>=4?"green":seconds>=2?"yellow":"red";
        String name=p.getGameProfile().getName();
        run(server,"title "+name+" times 0 22 0");
        run(server,"title "+name+" title {\"text\":\""+type.warningLabel+"\",\"color\":\"white\",\"bold\":true}");
        run(server,"title "+name+" subtitle {\"text\":\""+seconds+"\",\"color\":\""+color+"\",\"bold\":true}");
    }

    private static void run(MinecraftServer server,String command){server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4),command);}
    private static boolean admin(CommandSourceStack s){
        if(s.hasPermission(2)) return true;
        try{ServerPlayer p=s.getPlayer();if(p==null)return false;String n=p.getGameProfile().getName();return"kvi_nella".equalsIgnoreCase(n)||"Twinida".equalsIgnoreCase(n);}catch(Exception x){return false;}
    }

    private enum DropType{
        TNT(TNT_TAG,"✦ TNT DROP ✦","ДИНАМИТ УПАДЁТ ЧЕРЕЗ",ChatFormatting.RED),
        FIREBALL(FIREBALL_TAG,"☄ FIREBALL DROP","ФАЕРБОЛ УПАДЁТ ЧЕРЕЗ",ChatFormatting.GOLD),
        HEAL(HEAL_TAG,"✚ HEAL DROP","ХИЛКА УПАДЁТ ЧЕРЕЗ",ChatFormatting.GREEN);
        final String tag,idleLabel,warningLabel;final ChatFormatting color;
        DropType(String tag,String idleLabel,String warningLabel,ChatFormatting color){this.tag=tag;this.idleLabel=idleLabel;this.warningLabel=warningLabel;this.color=color;}
    }

    private static final class Warning{
        final ServerLevel level;final UUID marker;final DropType type;final double x,y,z;int ticks,lastSecond;
        Warning(ServerLevel level,UUID marker,DropType type,double x,double y,double z,int ticks,int lastSecond){this.level=level;this.marker=marker;this.type=type;this.x=x;this.y=y;this.z=z;this.ticks=ticks;this.lastSecond=lastSecond;}
    }
    private record ActiveTnt(ServerLevel level,UUID marker,long explodeAt){}
    private static final class FireDrop{
        final ServerLevel level;final UUID marker;final double x,groundY,z,startY;int ticks;
        FireDrop(ServerLevel level,UUID marker,double x,double groundY,double z,double startY,int ticks){this.level=level;this.marker=marker;this.x=x;this.groundY=groundY;this.z=z;this.startY=startY;this.ticks=ticks;}
    }
    private static final class HealDrop{
        final ServerLevel level;final UUID marker,visual;final double x,groundY,z,startY;int ticks;
        HealDrop(ServerLevel level,UUID marker,UUID visual,double x,double groundY,double z,double startY,int ticks){this.level=level;this.marker=marker;this.visual=visual;this.x=x;this.groundY=groundY;this.z=z;this.startY=startY;this.ticks=ticks;}
    }
}
