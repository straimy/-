package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.net.ArenaNetwork;
import arena.forge.player.ArenaPlayerState;
import arena.round.RoundState;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class MinimalGameplayFixes {
    private static final Map<UUID,Long> JOIN_AT=new HashMap<>();
    private static final Map<UUID,Integer> LAST_COUNT=new HashMap<>();
    private static final Map<UUID,Long> LAST_NO_SPAWN_WARN=new HashMap<>();
    private static final Map<UUID,Long> ACTIVE_SAFE_TNT=new HashMap<>();
    private static final Map<UUID,Long> NEXT_TNT_BY_MARKER=new HashMap<>();
    private static final List<FireStrike> FIRE_STRIKES=new ArrayList<>();
    private static long lastAmmoWave=Long.MIN_VALUE;
    private static long nextFireStrike=Long.MIN_VALUE;
    private static boolean legacyNpcCleanupDone;
    private static final Random RANDOM=new Random();
    private static final String MENU_COMPASS_TAG="gunnerarena_menu_compass";
    private static final String TNT_SPAWN_TAG="gunnerarena_tnt_spawn";
    private MinimalGameplayFixes(){}

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onRegisterCommands(RegisterCommandsEvent event){
        event.getDispatcher().register(Commands.literal("setnpc").requires(s->s.hasPermission(2)||isNamedAdmin(s)).executes(c->createMenuNpc(c.getSource(),"GunGloryOnline")).then(Commands.argument("name",StringArgumentType.greedyString()).executes(c->createMenuNpc(c.getSource(),StringArgumentType.getString(c,"name")))));
        event.getDispatcher().register(Commands.literal("setspawntnt").requires(s->s.hasPermission(2)||isNamedAdmin(s)).executes(c->createTntSpawn(c.getSource())));
        event.getDispatcher().register(Commands.literal("clearnpcs").requires(s->s.hasPermission(2)||isNamedAdmin(s)).executes(c->clearLegacyNpcs(c.getSource())));
        event.getDispatcher().register(Commands.literal("play").executes(c->queueForPlay(c.getSource())));
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!isMenuCompassFor(p,event.getItemStack()))return;
        event.setCanceled(true);openMain(p);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer p)||!isMenuCompassFor(p,event.getItemStack()))return;
        event.setCanceled(true);openMain(p);
    }
    private static void openMain(ServerPlayer p){ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null)return;if(!r.auth().isAuthenticated(p)){r.auth().deny(p);return;}ArenaNetwork.openUi(p,ArenaNetwork.UiTarget.MAIN);}

    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        if(!event.getTarget().getTags().contains("gunnerarena_menu_npc")&&!event.getTarget().getTags().contains("gunner_arena_npc_hitbox"))return;
        event.setCanceled(true);openMain(p);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onAttack(LivingAttackEvent e){if(e.getEntity() instanceof ServerPlayer p&&lobbyProtected(p))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void onHurt(LivingHurtEvent e){if(e.getEntity() instanceof ServerPlayer p&&lobbyProtected(p))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void onDeath(LivingDeathEvent e){
        if(!(e.getEntity() instanceof ServerPlayer v))return;
        if(e.getSource().getEntity() instanceof ServerPlayer k&&k!=v)v.sendSystemMessage(Component.literal("✖ Тебя убил ").withStyle(ChatFormatting.RED).append(Component.literal(k.getGameProfile().getName()).withStyle(ChatFormatting.GOLD)));
        else v.sendSystemMessage(Component.literal("✖ Ты погиб.").withStyle(ChatFormatting.RED));
    }

    @SubscribeEvent public static void onServerTick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;MinecraftServer server=ServerLifecycleHooks.getCurrentServer();ArenaRuntime r=GunnerArenaMod.RUNTIME;if(server==null||r==null)return;long now=r.serverTick();
        if(!legacyNpcCleanupDone){legacyNpcCleanupDone=true;cleanupLegacyNpcArtifacts(server);}
        for(ServerPlayer p:server.getPlayerList().getPlayers()){
            if(!r.auth().isAuthenticated(p)){clearQueueState(p);continue;}ArenaPlayerState state=r.players().session(p).state();
            if(state==ArenaPlayerState.LOBBY||state==ArenaPlayerState.QUEUED){p.setInvisible(true);p.setGameMode(GameType.ADVENTURE);selectEmptyHotbarSlot(p);ensureMenuCompass(p);if(state==ArenaPlayerState.LOBBY){JOIN_AT.remove(p.getUUID());LAST_COUNT.remove(p.getUUID());}else JOIN_AT.putIfAbsent(p.getUUID(),now+20L);}
            else if(state==ArenaPlayerState.ALIVE||state==ArenaPlayerState.SPAWNING){p.setInvisible(false);p.setGameMode(GameType.ADVENTURE);clearQueueState(p);}
            Long at=JOIN_AT.get(p.getUUID());if(at==null||state!=ArenaPlayerState.QUEUED)continue;long left=at-now;
            if(left>0){if(!Integer.valueOf(1).equals(LAST_COUNT.put(p.getUUID(),1)))showTitle(server,p,"1");continue;}
            JOIN_AT.remove(p.getUUID());LAST_COUNT.remove(p.getUUID());
            if(r.spawns().combatSpawns().isEmpty()){long prev=LAST_NO_SPAWN_WARN.getOrDefault(p.getUUID(),Long.MIN_VALUE);if(now-prev>100){LAST_NO_SPAWN_WARN.put(p.getUUID(),now);p.sendSystemMessage(Component.literal("[GGO] Нет боевых spawn-точек • /addspawn").withStyle(ChatFormatting.RED));}JOIN_AT.put(p.getUUID(),now+40);continue;}
            if(r.rounds().state()!=RoundState.PLAYING)r.forceStartRound();if(r.rounds().state()!=RoundState.PLAYING){JOIN_AT.put(p.getUUID(),now+40);continue;}
            boolean spawned=r.players().requestPlay(server,p,r.rounds().state(),r.rounds().roundNumber(),now);
            if(spawned){p.setInvisible(false);p.setGameMode(GameType.ADVENTURE);LAST_NO_SPAWN_WARN.remove(p.getUUID());p.sendSystemMessage(Component.literal("✦ 5 секунд защиты • G — магазин оружия").withStyle(ChatFormatting.AQUA));}else JOIN_AT.put(p.getUUID(),now+40);
        }
        if(now-lastAmmoWave>=200){lastAmmoWave=now;int cycle=(int)((now/200)%3);spawnLegacyAmmo(server,"gun_1_ammo","jeg:pistol_ammo",24);spawnLegacyAmmo(server,"gun_2_ammo","jeg:rifle_ammo",24);spawnLegacyAmmo(server,"gun_3_ammo","jeg:shotgun_shell",10);String a=cycle==0?"jeg:pistol_ammo":cycle==1?"jeg:rifle_ammo":"jeg:shotgun_shell";spawnLegacyAmmo(server,"random_gun_ammo",a,cycle==2?10:24);}
        tickRandomTnt(server,now);tickSafeTnt(server,now);tickFireStrikes(server,r,now);
    }

    private static void tickRandomTnt(MinecraftServer server,long now){
        Set<UUID> live=new HashSet<>();
        for(ServerLevel level:server.getAllLevels())for(Entity e:level.getAllEntities())if(e instanceof ArmorStand m&&m.getTags().contains(TNT_SPAWN_TAG)){
            live.add(m.getUUID());long due=NEXT_TNT_BY_MARKER.computeIfAbsent(m.getUUID(),u->now+randomTicks(6,30));
            if(now>=due){spawnSafeTnt(level,m,now);NEXT_TNT_BY_MARKER.put(m.getUUID(),now+randomTicks(6,30));}
        }
        NEXT_TNT_BY_MARKER.keySet().retainAll(live);
    }
    private static int randomTicks(int minSec,int maxSec){return(minSec+RANDOM.nextInt(maxSec-minSec+1))*20;}
    private static void spawnSafeTnt(ServerLevel level,ArmorStand marker,long now){PrimedTnt t=EntityType.TNT.create(level);if(t==null)return;t.moveTo(marker.getX(),marker.getY()+.3,marker.getZ(),0,0);t.setFuse(60);level.addFreshEntity(t);ACTIVE_SAFE_TNT.put(t.getUUID(),now+40);}
    private static void tickSafeTnt(MinecraftServer server,long now){
        Iterator<Map.Entry<UUID,Long>>it=ACTIVE_SAFE_TNT.entrySet().iterator();while(it.hasNext()){
            var en=it.next();if(now<en.getValue())continue;Entity entity=null;ServerLevel level=null;
            for(ServerLevel l:server.getAllLevels()){Entity f=l.getEntity(en.getKey());if(f!=null){entity=f;level=l;break;}}
            it.remove();if(!(entity instanceof PrimedTnt t)||level==null)continue;double x=t.getX(),y=t.getY(),z=t.getZ();t.discard();
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,x,y,z,1,0,0,0,0);level.playSound(null,x,y,z,SoundEvents.GENERIC_EXPLODE,SoundSource.BLOCKS,4,1);
            for(ServerPlayer p:level.players()){double d=p.distanceToSqr(x,y,z);if(d<=49)p.hurt(p.damageSources().generic(),8);if(d<=100)p.addEffect(new MobEffectInstance(MobEffects.POISON,60,0));}
        }
    }

    private static void tickFireStrikes(MinecraftServer server,ArenaRuntime r,long now){
        if(nextFireStrike==Long.MIN_VALUE)nextFireStrike=now+randomTicks(8,22);
        if(now>=nextFireStrike){List<ServerPlayer>targets=new ArrayList<>();for(ServerPlayer p:server.getPlayerList().getPlayers())if(r.auth().isAuthenticated(p)&&r.players().session(p).state()==ArenaPlayerState.ALIVE)targets.add(p);if(!targets.isEmpty()){ServerPlayer p=targets.get(RANDOM.nextInt(targets.size()));FIRE_STRIKES.add(new FireStrike(p.serverLevel(),p.getX()+RANDOM.nextDouble()*4-2,p.getY(),p.getZ()+RANDOM.nextDouble()*4-2,p.getY()+18,24));}nextFireStrike=now+randomTicks(8,22);}
        Iterator<FireStrike>it=FIRE_STRIKES.iterator();while(it.hasNext()){FireStrike f=it.next();double y=f.groundY+(f.ticks/24.0)*(f.startY-f.groundY);f.level.sendParticles(ParticleTypes.FLAME,f.x,y,f.z,8,.12,.35,.12,.01);f.level.sendParticles(ParticleTypes.SMOKE,f.x,y,f.z,3,.1,.2,.1,.01);f.ticks--;if(f.ticks>0)continue;it.remove();f.level.sendParticles(ParticleTypes.FLAME,f.x,f.groundY+.2,f.z,80,2.2,.5,2.2,.04);f.level.sendParticles(ParticleTypes.LAVA,f.x,f.groundY+.2,f.z,20,1.3,.2,1.3,.02);f.level.playSound(null,f.x,f.groundY,f.z,SoundEvents.FIRECHARGE_USE,SoundSource.BLOCKS,2.4f,.75f);for(ServerPlayer p:f.level.players())if(p.distanceToSqr(f.x,f.groundY,f.z)<=25){p.hurt(p.damageSources().onFire(),5);p.setSecondsOnFire(4);}}
    }

    private static int queueForPlay(CommandSourceStack source){ServerPlayer p;try{p=source.getPlayerOrException();}catch(Exception x){return 0;}ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null)return 0;if(!r.auth().isAuthenticated(p)){r.auth().deny(p);return 0;}r.players().session(p).state(ArenaPlayerState.QUEUED);JOIN_AT.put(p.getUUID(),r.serverTick()+1);LAST_COUNT.remove(p.getUUID());p.sendSystemMessage(Component.literal("[GGO] Вход в арену…").withStyle(ChatFormatting.GREEN));return 1;}
    private static int createMenuNpc(CommandSourceStack source,String requested){ServerPlayer p;try{p=source.getPlayerOrException();}catch(Exception x){return 0;}String name=requested==null?"GunGloryOnline":requested.trim();Villager n=EntityType.VILLAGER.create(p.serverLevel());if(n==null)return 0;n.moveTo(p.getX(),p.getY(),p.getZ(),p.getYRot(),0);n.setCustomName(Component.literal("✦ "+name+" • МЕНЮ ✦"));n.setCustomNameVisible(true);n.setNoAi(true);n.setInvulnerable(true);n.setPersistenceRequired();n.addTag("gunnerarena_menu_npc");p.serverLevel().addFreshEntity(n);source.sendSuccess(()->Component.literal("[GGO] Житель меню создан"),false);return 1;}
    private static int createTntSpawn(CommandSourceStack source){ServerPlayer p;try{p=source.getPlayerOrException();}catch(Exception x){return 0;}ArmorStand m=EntityType.ARMOR_STAND.create(p.serverLevel());if(m==null)return 0;m.moveTo(p.getX(),p.getY(),p.getZ(),0,0);m.setInvisible(true);m.setNoGravity(true);m.setInvulnerable(true);m.addTag(TNT_SPAWN_TAG);m.setCustomName(Component.literal("GGO TNT SPAWN"));p.serverLevel().addFreshEntity(m);NEXT_TNT_BY_MARKER.put(m.getUUID(),GunnerArenaMod.RUNTIME.serverTick()+randomTicks(6,30));source.sendSuccess(()->Component.literal("[GGO] TNT-точка • случайно каждые 6–30 сек • без разрушения карты"),false);return 1;}
    private static int clearLegacyNpcs(CommandSourceStack source){int count=0;for(ServerLevel l:source.getServer().getAllLevels())for(Entity e:allEntities(l))if(isLegacyNpcArtifact(e)){e.discard();count++;}final int c=count;source.sendSuccess(()->Component.literal("[GGO] Удалено старых NPC/следов: "+c),false);return count;}
    private static void cleanupLegacyNpcArtifacts(MinecraftServer server){for(ServerLevel l:server.getAllLevels())for(Entity e:allEntities(l))if(e instanceof ArmorStand&&isLegacyNpcArtifact(e))e.discard();}
    private static List<Entity> allEntities(ServerLevel l){List<Entity>out=new ArrayList<>();for(Entity e:l.getAllEntities())out.add(e);return out;}
    private static boolean isLegacyNpcArtifact(Entity e){String n=e.getCustomName()==null?"":e.getCustomName().getString();return e.getTags().contains("gunner_arena_npc_hitbox")||e.getTags().contains("gunnerarena_menu_npc")||((e instanceof ArmorStand)&&(n.contains("KVICloud")||n.contains("GunGloryOnline")));}
    private static boolean isNamedAdmin(CommandSourceStack s){try{ServerPlayer p=s.getPlayer();if(p==null)return false;String n=p.getGameProfile().getName();return"kvi_nella".equalsIgnoreCase(n)||"Twinida".equalsIgnoreCase(n);}catch(Exception x){return false;}}
    private static void clearQueueState(ServerPlayer p){JOIN_AT.remove(p.getUUID());LAST_COUNT.remove(p.getUUID());LAST_NO_SPAWN_WARN.remove(p.getUUID());}
    private static boolean lobbyProtected(ServerPlayer p){ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null||!r.auth().isAuthenticated(p))return true;ArenaPlayerState s=r.players().session(p).state();return s==ArenaPlayerState.LOBBY||s==ArenaPlayerState.QUEUED||r.safeRegions().isSafe(p);}
    private static boolean isMenuCompassFor(ServerPlayer p,ItemStack s){if(s==null||!s.is(Items.COMPASS))return false;if(s.hasTag()&&s.getTag().getBoolean(MENU_COMPASS_TAG))return true;ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null)return false;ArenaPlayerState st=r.players().session(p).state();return st==ArenaPlayerState.LOBBY||st==ArenaPlayerState.QUEUED;}
    private static void ensureMenuCompass(ServerPlayer p){for(int i=0;i<p.getInventory().getContainerSize();i++){ItemStack s=p.getInventory().getItem(i);if(s.is(Items.COMPASS)){s.getOrCreateTag().putBoolean(MENU_COMPASS_TAG,true);s.setHoverName(Component.literal("✦ Меню GunGloryOnline ✦").withStyle(ChatFormatting.LIGHT_PURPLE));return;}}ItemStack c=new ItemStack(Items.COMPASS);c.getOrCreateTag().putBoolean(MENU_COMPASS_TAG,true);c.setHoverName(Component.literal("✦ Меню GunGloryOnline ✦").withStyle(ChatFormatting.LIGHT_PURPLE));if(p.getInventory().getItem(8).isEmpty())p.getInventory().setItem(8,c);else p.getInventory().add(c);}
    private static void selectEmptyHotbarSlot(ServerPlayer p){for(int i=8;i>=0;i--)if(p.getInventory().getItem(i).isEmpty()){p.getInventory().selected=i;return;}}
    private static void spawnLegacyAmmo(MinecraftServer server,String tag,String itemId,int count){ResourceLocation key=ResourceLocation.tryParse(itemId);if(key==null)return;Item item=ForgeRegistries.ITEMS.getValue(key);if(item==null||item==Items.AIR)return;for(ServerLevel l:server.getAllLevels())for(Entity e:l.getAllEntities())if(e instanceof ArmorStand m&&m.getTags().contains(tag)){boolean nearby=false;for(ItemEntity d:l.getEntitiesOfClass(ItemEntity.class,m.getBoundingBox().inflate(1.5)))if(d.getItem().is(item)){nearby=true;break;}if(!nearby){ItemEntity d=new ItemEntity(l,m.getX(),m.getY()+.35,m.getZ(),new ItemStack(item,count));d.setDefaultPickUpDelay();l.addFreshEntity(d);}}}
    private static void showTitle(MinecraftServer server,ServerPlayer p,String s){run(server,"title "+p.getGameProfile().getName()+" times 0 15 0");run(server,"title "+p.getGameProfile().getName()+" title {\"text\":\""+s+"\",\"color\":\"aqua\",\"bold\":true}");}
    private static void run(MinecraftServer server,String command){server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4),command);}
    private static final class FireStrike{final ServerLevel level;final double x,groundY,z,startY;int ticks;FireStrike(ServerLevel l,double x,double y,double z,double sy,int t){level=l;this.x=x;groundY=y;this.z=z;startY=sy;ticks=t;}}
}
