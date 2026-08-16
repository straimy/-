package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import arena.profile.PlayerProfile;
import arena.round.RoundState;
import com.mojang.brigadier.Command;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SwittieCompanionService {
    public static final int PRICE=6;
    private static final String TAG="gunglory_swittie_companion";
    private static UUID reservedOwnerGgo;
    private static UUID activeOwnerMinecraft;
    private static UUID bodyId;
    private static int activeRound=Integer.MIN_VALUE;
    private static long nextShot;
    private static final Random RNG=new Random();
    private SwittieCompanionService(){}

    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("companion").then(Commands.literal("swittie").executes(c->buy(c.getSource().getPlayerOrException()))));
    }

    private static int buy(ServerPlayer p){
        ArenaRuntime r=GunnerArenaMod.RUNTIME;if(r==null)return 0;PlayerProfile profile=r.players().profile(p);if(profile==null)return 0;
        UUID ggo=GgoIdentityBridge.idFor(p);
        if(reservedOwnerGgo!=null&&!reservedOwnerGgo.equals(ggo)){p.sendSystemMessage(Component.literal("✦ Свитти уже занята на следующий раунд.").withStyle(ChatFormatting.RED));return 0;}
        if(reservedOwnerGgo!=null){p.sendSystemMessage(Component.literal("✦ Свитти уже забронирована тобой.").withStyle(ChatFormatting.AQUA));return Command.SINGLE_SUCCESS;}
        if(profile.crystals<PRICE){p.sendSystemMessage(Component.literal("✦ Нужно "+PRICE+"◆. Сейчас: "+profile.crystals+"◆").withStyle(ChatFormatting.RED));return 0;}
        profile.crystals-=PRICE;r.profiles().markDirty(p.getUUID());reservedOwnerGgo=ggo;
        p.sendSystemMessage(Component.literal("✦ Свитти с тобой в следующем раунде • -"+PRICE+"◆").withStyle(ChatFormatting.LIGHT_PURPLE));return Command.SINGLE_SUCCESS;
    }

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;ArenaRuntime r=GunnerArenaMod.RUNTIME;MinecraftServer server=ServerLifecycleHooks.getCurrentServer();if(r==null||server==null)return;long now=r.serverTick();
        int round=r.rounds().roundNumber();RoundState state=r.rounds().state();
        if(state==RoundState.PLAYING&&round!=activeRound){activeRound=round;removeBody(server);activeOwnerMinecraft=null;
            if(reservedOwnerGgo!=null){ServerPlayer owner=findByGgo(server,reservedOwnerGgo);if(owner!=null){activeOwnerMinecraft=owner.getUUID();spawn(owner,now);}reservedOwnerGgo=null;}
        }
        if(state!=RoundState.PLAYING){removeBody(server);activeOwnerMinecraft=null;return;}
        if(activeOwnerMinecraft==null)return;ServerPlayer owner=server.getPlayerList().getPlayer(activeOwnerMinecraft);if(owner==null)return;
        Pillager bot=findBody(server);if(bot==null||!bot.isAlive()){spawn(owner,now);bot=findBody(server);if(bot==null)return;}
        if((now%5)!=0)return;
        if(bot.level()!=owner.level()||bot.distanceToSqr(owner)>900){bot.teleportTo(owner.getX()+1,owner.getY(),owner.getZ()+1);}
        ServerPlayer target=findTarget(server,r,owner,bot);
        if(target==null){bot.setTarget(null);double d=bot.distanceToSqr(owner);if(d>16)bot.getNavigation().moveTo(owner,1.1);else bot.getNavigation().stop();return;}
        bot.setTarget(target);bot.getLookControl().setLookAt(target,30,30);double d2=bot.distanceToSqr(target);if(d2>49)bot.getNavigation().moveTo(target,1.12);else bot.getNavigation().stop();
        if(d2<=324&&now>=nextShot&&bot.getSensing().hasLineOfSight(target)){nextShot=now+24+RNG.nextInt(12);shoot(bot,target,r);}
    }

    private static void spawn(ServerPlayer owner,long now){
        if(!(owner.level() instanceof ServerLevel level))return;Pillager bot=EntityType.PILLAGER.create(level);if(bot==null)return;
        bot.moveTo(owner.getX()+1.2,owner.getY(),owner.getZ()+1.2,owner.getYRot(),0);bot.addTag(TAG);bot.setCustomName(Component.literal("Свитти Фокс ✦ "+owner.getGameProfile().getName()).withStyle(ChatFormatting.LIGHT_PURPLE));bot.setCustomNameVisible(true);bot.setPersistenceRequired();bot.setCanPickUpLoot(false);
        var hp=bot.getAttribute(Attributes.MAX_HEALTH);if(hp!=null)hp.setBaseValue(26);bot.setHealth(26);var speed=bot.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.setBaseValue(.32);
        Item gun=ForgeRegistries.ITEMS.getValue(new ResourceLocation("jeg","pump_shotgun"));if(gun!=null){ItemStack s=new ItemStack(gun);s.getOrCreateTag().putBoolean("IgnoreAmmo",true);s.getOrCreateTag().putBoolean("GunGloryBotWeapon",true);bot.setItemSlot(EquipmentSlot.MAINHAND,s);bot.setDropChance(EquipmentSlot.MAINHAND,0);}
        level.addFreshEntity(bot);bodyId=bot.getUUID();nextShot=now+20;
    }
    private static ServerPlayer findTarget(MinecraftServer server,ArenaRuntime r,ServerPlayer owner,Pillager bot){ServerPlayer best=null;double bd=32*32;for(ServerPlayer p:server.getPlayerList().getPlayers()){if(p==owner||!p.isAlive()||p.level()!=bot.level()||!r.auth().isAuthenticated(p)||r.players().session(p).state()!=ArenaPlayerState.ALIVE||r.safeRegions().isSafe(p)||FriendService.areFriends(owner,p))continue;double d=bot.distanceToSqr(p);if(d<bd){bd=d;best=p;}}return best;}
    private static void shoot(Pillager bot,ServerPlayer target,ArenaRuntime r){if(!target.isAlive()||r.safeRegions().isSafe(target))return;ServerLevel level=(ServerLevel)bot.level();level.playSound(null,bot.blockPosition(),SoundEvents.GENERIC_EXPLODE,SoundSource.HOSTILE,.35f,1.8f);double dx=target.getX()-bot.getX(),dy=target.getEyeY()-bot.getEyeY(),dz=target.getZ()-bot.getZ();for(int i=1;i<=6;i++){double t=i/6d;level.sendParticles(ParticleTypes.SMOKE,bot.getX()+dx*t,bot.getEyeY()+dy*t,bot.getZ()+dz*t,1,0,0,0,0);}target.hurt(level.damageSources().mobAttack(bot),3.5f);}
    private static ServerPlayer findByGgo(MinecraftServer s,UUID ggo){for(ServerPlayer p:s.getPlayerList().getPlayers())if(GgoIdentityBridge.idFor(p).equals(ggo))return p;return null;}
    private static Pillager findBody(MinecraftServer server){if(bodyId==null)return null;for(ServerLevel l:server.getAllLevels()){Entity e=l.getEntity(bodyId);if(e instanceof Pillager p)return p;}return null;}
    private static void removeBody(MinecraftServer server){for(ServerLevel l:server.getAllLevels()){List<Entity> copy=new ArrayList<>();for(Entity e:l.getAllEntities())copy.add(e);for(Entity e:copy)if(e.getTags().contains(TAG))e.discard();}bodyId=null;}
}
