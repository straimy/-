package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import arena.forge.spawn.ArenaPoint;
import com.mojang.authlib.GameProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

/**
 * Server adaptation of the user's client-only AI Gunner rc4 concept.
 * The combat body is a stable server-side humanoid mob so the arena never depends on a second Minecraft client.
 */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SwittieFoxBot {
    private static final String TAG="gunglory_swittie_fox";
    private static final UUID TAB_UUID=UUID.nameUUIDFromBytes("GunGloryOnline:SwittieFox".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final String INTERNAL_NAME="SwittieFox";
    private static final String DISPLAY_NAME="Свитти Фокс";
    private static final String[] KILL_LINES={"xD","езз","))","туда","победа","ема","туда тебя","лол","круть"};
    private static final Random RANDOM=new Random();
    private static boolean enabled;
    private static UUID bodyId;
    private static long respawnAt=Long.MAX_VALUE;
    private static long nextShot;
    private static UUID lastTarget;
    private SwittieFoxBot(){}

    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("switiie").requires(s->s.hasPermission(2))
            .then(Commands.literal("start").executes(c->start(c.getSource().getServer())))
            .then(Commands.literal("stop").executes(c->stop(c.getSource().getServer())))
            .then(Commands.literal("status").executes(c->{c.getSource().sendSuccess(()->Component.literal("[GGO] SwittieFox: "+(enabled?"ON":"OFF")),false);return 1;})));
    }

    private static int start(MinecraftServer server){
        enabled=true;removeBodies(server);bodyId=null;respawnAt=0;nextShot=0;broadcast(server,Component.literal("[+] "+DISPLAY_NAME+" зашел на сервер").withStyle(ChatFormatting.YELLOW));syncTab(server,true);return 1;
    }
    private static int stop(MinecraftServer server){
        if(enabled)broadcast(server,Component.literal("[-] "+DISPLAY_NAME+" вышел с сервера").withStyle(ChatFormatting.GRAY));enabled=false;removeBodies(server);bodyId=null;respawnAt=Long.MAX_VALUE;syncTab(server,false);return 1;
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!enabled)return;MinecraftServer server=ServerLifecycleHooks.getCurrentServer();ArenaRuntime r=GunnerArenaMod.RUNTIME;if(server==null||r==null)return;long now=r.serverTick();
        Pillager bot=findBody(server);if(bot==null||!bot.isAlive()){bodyId=null;if(now>=respawnAt)spawn(server,r,now);return;}
        if((now%5)!=0)return;
        ServerPlayer target=findTarget(server,r,bot);if(target==null){bot.setTarget(null);return;}lastTarget=target.getUUID();bot.setTarget(target);bot.getLookControl().setLookAt(target,30f,30f);
        double d2=bot.distanceToSqr(target);if(d2>49)bot.getNavigation().moveTo(target,1.05);else bot.getNavigation().stop();
        if(d2<=324&&now>=nextShot&&bot.getSensing().hasLineOfSight(target)){nextShot=now+22+RANDOM.nextInt(13);shoot(bot,target);}
    }

    private static void spawn(MinecraftServer server,ArenaRuntime r,long now){
        List<ArenaPoint> points=r.spawns().combatSpawns();if(points.isEmpty()){respawnAt=now+100;return;}ArenaPoint p=points.get(RANDOM.nextInt(points.size()));ServerLevel level=server.getLevel(p.dimension());if(level==null){respawnAt=now+60;return;}
        Pillager bot=EntityType.PILLAGER.create(level);if(bot==null){respawnAt=now+100;return;}bot.moveTo(p.x(),p.y(),p.z(),p.yaw(),p.pitch());bot.addTag(TAG);bot.setCustomName(Component.literal(DISPLAY_NAME).withStyle(ChatFormatting.LIGHT_PURPLE));bot.setCustomNameVisible(true);bot.setPersistenceRequired();bot.setCanPickUpLoot(false);bot.setHealth(20f);
        var max=bot.getAttribute(Attributes.MAX_HEALTH);if(max!=null)max.setBaseValue(20.0);var speed=bot.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.setBaseValue(.31);
        Item gun=ForgeRegistries.ITEMS.getValue(new ResourceLocation("jeg","pump_shotgun"));if(gun!=null){ItemStack s=new ItemStack(gun);s.getOrCreateTag().putBoolean("IgnoreAmmo",true);s.getOrCreateTag().putInt("AmmoCount",6);s.getOrCreateTag().putBoolean("GunGloryBotWeapon",true);bot.setItemSlot(EquipmentSlot.MAINHAND,s);bot.setDropChance(EquipmentSlot.MAINHAND,0f);}
        level.addFreshEntity(bot);bodyId=bot.getUUID();respawnAt=Long.MAX_VALUE;nextShot=now+20;
    }

    private static ServerPlayer findTarget(MinecraftServer server,ArenaRuntime r,Pillager bot){ServerPlayer best=null;double bd=45*45;for(ServerPlayer p:server.getPlayerList().getPlayers()){if(!p.isAlive()||p.level()!=bot.level()||!r.auth().isAuthenticated(p)||r.players().session(p).state()!=ArenaPlayerState.ALIVE)continue;double d=bot.distanceToSqr(p);if(d<bd){bd=d;best=p;}}return best;}
    private static void shoot(Pillager bot,ServerPlayer target){
        ServerLevel level=(ServerLevel)bot.level();level.playSound(null,bot.getX(),bot.getY(),bot.getZ(),SoundEvents.GENERIC_EXPLODE,SoundSource.HOSTILE,.45f,1.7f);double dx=target.getX()-bot.getX(),dy=target.getEyeY()-bot.getEyeY(),dz=target.getZ()-bot.getZ();double len=Math.max(.001,Math.sqrt(dx*dx+dy*dy+dz*dz));for(int i=1;i<=8;i++){double t=i/8.0;level.sendParticles(ParticleTypes.SMOKE,bot.getX()+dx*t,bot.getEyeY()+dy*t,bot.getZ()+dz*t,1,.02,.02,.02,0);}float damage=4.5f;target.hurt(level.damageSources().mobAttack(bot),damage);
    }

    @SubscribeEvent public static void death(LivingDeathEvent e){
        if(e.getEntity().getTags().contains(TAG)){MinecraftServer server=e.getEntity().getServer();if(server!=null&&enabled){bodyId=null;ArenaRuntime r=GunnerArenaMod.RUNTIME;respawnAt=(r==null?0:r.serverTick()+60);ServerPlayer killer=e.getSource().getEntity() instanceof ServerPlayer p?p:null;if(killer!=null)broadcast(server,Component.literal("✦ "+killer.getGameProfile().getName()+" убил "+DISPLAY_NAME).withStyle(ChatFormatting.GRAY));}return;}
        if(!(e.getEntity() instanceof ServerPlayer victim)||!(e.getSource().getEntity() instanceof LivingEntity src)||!src.getTags().contains(TAG))return;MinecraftServer server=victim.getServer();if(server!=null)broadcast(server,Component.literal("<"+DISPLAY_NAME+"> "+KILL_LINES[RANDOM.nextInt(KILL_LINES.length)]).withStyle(ChatFormatting.WHITE));
    }

    private static Pillager findBody(MinecraftServer server){if(bodyId==null)return null;for(ServerLevel l:server.getAllLevels()){Entity e=l.getEntity(bodyId);if(e instanceof Pillager p)return p;}return null;}
    private static void removeBodies(MinecraftServer server){for(ServerLevel l:server.getAllLevels()){List<Entity> all=new ArrayList<>();for(Entity e:l.getAllEntities())all.add(e);for(Entity e:all)if(e.getTags().contains(TAG))e.discard();}}
    private static void broadcast(MinecraftServer server,Component c){server.getPlayerList().broadcastSystemMessage(c,false);}

    /** Optional real tab-row. Forge FakePlayer is used only as packet data and is never inserted into the world/player list. Failure is non-fatal. */
    private static void syncTab(MinecraftServer server,boolean add){
        try{ServerLevel level=server.overworld();FakePlayer fp=FakePlayerFactory.get(level,new GameProfile(TAB_UUID,INTERNAL_NAME));if(add){ClientboundPlayerInfoUpdatePacket packet=ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fp));for(ServerPlayer p:server.getPlayerList().getPlayers())p.connection.send(packet);}else{ClientboundPlayerInfoRemovePacket packet=new ClientboundPlayerInfoRemovePacket(List.of(TAB_UUID));for(ServerPlayer p:server.getPlayerList().getPlayers())p.connection.send(packet);}}catch(Throwable ignored){}
    }
}
