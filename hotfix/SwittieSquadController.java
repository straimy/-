package arena.forge;

import arena.GunnerArenaMod;
import arena.forge.player.ArenaPlayerState;
import net.minecraft.ChatFormatting;
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
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;

/** Extra arena combat NPCs spawned by /switiie copy|copypast|bea|clear. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SwittieSquadController {
    private static final String EXTRA="ggo_swittie_extra", BEE="ggo_swittie_bee";
    private static final String[] NAMES={"Свитти Фокс","Mia Melano","Kendra Lust","Gabbie Carter","Riley Reid","Maitland Ward","Sasha Grey","Gina Valentina","Emily Willis","Alina Lopez","Cory Chase","Amouranth","Kira Queen","Eva Elfie"};
    private static final String[] GUNS={"jeg:pump_shotgun","jeg:semi_auto_pistol","jeg:combat_pistol","jeg:revolver","jeg:custom_smg","jeg:assault_rifle","jeg:burst_rifle","jeg:bolt_action_rifle","jeg:light_machine_gun","jeg:assault_rifle","jeg:custom_smg","jeg:combat_pistol","jeg:burst_rifle","jeg:bolt_action_rifle"};
    private static final Random R=new Random();
    private SwittieSquadController(){}

    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("switiie").requires(s->s.hasPermission(2))
            .then(Commands.literal("copy").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();spawnFighter(p.serverLevel(),p.position(),0,2);c.getSource().sendSuccess(()->Component.literal("[GGO] Создан клон Свитти Фокс."),false);return 1;}))
            .then(Commands.literal("copypast").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();for(int i=0;i<NAMES.length;i++)spawnFighter(p.serverLevel(),p.position(),i,1+(i/4));c.getSource().sendSuccess(()->Component.literal("[GGO] Отряд Свитти создан: "+NAMES.length+" NPC."),false);return NAMES.length;}))
            .then(Commands.literal("bea").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();spawnBee(p.serverLevel(),p.position());c.getSource().sendSuccess(()->Component.literal("[GGO] Ядовитая боевая пчела создана."),false);return 1;}))
            .then(Commands.literal("clear").executes(c->{int n=clear(c.getSource().getServer());c.getSource().sendSuccess(()->Component.literal("[GGO] Удалено дополнительных NPC: "+n),false);return n;})));
    }

    private static void spawnFighter(ServerLevel level,net.minecraft.world.phys.Vec3 at,int index,int tier){
        Pillager mob=EntityType.PILLAGER.create(level);if(mob==null)return;double a=R.nextDouble()*Math.PI*2,rad=3+R.nextDouble()*5;mob.moveTo(at.x+Math.cos(a)*rad,at.y,at.z+Math.sin(a)*rad,R.nextFloat()*360f,0);mob.addTag(EXTRA);mob.addTag("ggo_npc_tier_"+tier);mob.setCustomName(Component.literal(NAMES[index]).withStyle(index==0?ChatFormatting.LIGHT_PURPLE:ChatFormatting.AQUA));mob.setCustomNameVisible(true);mob.setPersistenceRequired();mob.setCanPickUpLoot(false);
        double hp=28+Math.min(4,tier)*10;var max=mob.getAttribute(Attributes.MAX_HEALTH);if(max!=null)max.setBaseValue(hp);mob.setHealth((float)hp);var speed=mob.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.setBaseValue(.30+.018*Math.min(4,tier));
        Item gun=ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(GUNS[index]));if(gun!=null){ItemStack s=new ItemStack(gun);s.getOrCreateTag().putBoolean("IgnoreAmmo",true);s.getOrCreateTag().putInt("AmmoCount",100);s.getOrCreateTag().putBoolean("GunGloryBotWeapon",true);mob.setItemSlot(EquipmentSlot.MAINHAND,s);mob.setDropChance(EquipmentSlot.MAINHAND,0f);}
        ItemStack head=new ItemStack(Items.PLAYER_HEAD);head.getOrCreateTag().putString("SkullOwner",NAMES[index].replace(" ",""));mob.setItemSlot(EquipmentSlot.HEAD,head);mob.setDropChance(EquipmentSlot.HEAD,0f);
        level.addFreshEntity(mob);
    }

    private static void spawnBee(ServerLevel level,net.minecraft.world.phys.Vec3 at){Bee bee=EntityType.BEE.create(level);if(bee==null)return;bee.moveTo(at.x+2,at.y+2,at.z+2,R.nextFloat()*360,0);bee.addTag(EXTRA);bee.addTag(BEE);bee.addTag("ggo_npc_tier_5");bee.setCustomName(Component.literal("Свитти Би").withStyle(ChatFormatting.GOLD));bee.setCustomNameVisible(true);bee.setPersistenceRequired();var max=bee.getAttribute(Attributes.MAX_HEALTH);if(max!=null)max.setBaseValue(55);bee.setHealth(55);var speed=bee.getAttribute(Attributes.FLYING_SPEED);if(speed!=null)speed.setBaseValue(.7);level.addFreshEntity(bee);}

    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent ev){
        if(ev.phase!=TickEvent.Phase.END)return;MinecraftServer server=ServerLifecycleHooks.getCurrentServer();ArenaRuntime rt=GunnerArenaMod.RUNTIME;if(server==null||rt==null||rt.serverTick()%4!=0)return;
        for(ServerLevel level:server.getAllLevels())for(Entity entity:level.getAllEntities()){
            if(!entity.getTags().contains(EXTRA)||!entity.isAlive())continue;
            ServerPlayer target=nearest(level,entity,rt);if(target==null)continue;
            if(entity instanceof Pillager p){p.setTarget(target);p.getLookControl().setLookAt(target,35,35);double d=p.distanceToSqr(target);if(d>36)p.getNavigation().moveTo(target,1.15);else p.getNavigation().stop();if(p.horizontalCollision&&p.onGround()){double dy=target.getY()-p.getY();double jump=dy>1.6?.92:dy>.5?.72:.58;p.setDeltaMovement(p.getDeltaMovement().x,jump,p.getDeltaMovement().z);p.hurtMarked=true;}long cd=28-Math.min(12,tier(p)*2);if(d<26*26&&rt.serverTick()%Math.max(8,cd)==0&&p.getSensing().hasLineOfSight(target))shoot(level,p,target,tier(p));}
            else if(entity instanceof Bee b){b.getNavigation().moveTo(target,1.35);double d=b.distanceToSqr(target);if(d<3.2){target.hurt(level.damageSources().mobAttack(b),3.5f);target.addEffect(new MobEffectInstance(MobEffects.POISON,100,1));}if(d<24*24&&rt.serverTick()%32==0)beeArrow(level,b,target);}
        }
    }

    private static ServerPlayer nearest(ServerLevel level,Entity mob,ArenaRuntime rt){ServerPlayer best=null;double bd=48*48;for(ServerPlayer p:level.players()){if(!p.isAlive()||!rt.auth().isAuthenticated(p)||rt.players().session(p).state()!=ArenaPlayerState.ALIVE||rt.safeRegions().isSafe(p))continue;double d=mob.distanceToSqr(p);if(d<bd){bd=d;best=p;}}return best;}

    private static void shoot(ServerLevel level,Pillager bot,ServerPlayer target,int tier){float damage=3.2f+tier*.85f;level.playSound(null,bot.blockPosition(),SoundEvents.FIREWORK_ROCKET_BLAST,SoundSource.HOSTILE,.3f,1.45f);target.hurt(level.damageSources().mobAttack(bot),damage);level.sendParticles(ParticleTypes.SMOKE,target.getX(),target.getEyeY(),target.getZ(),2,.1,.1,.1,0);}
    private static void beeArrow(ServerLevel level,Bee bee,ServerPlayer target){Arrow a=new Arrow(level,bee);double dx=target.getX()-bee.getX(),dy=target.getEyeY()-bee.getEyeY(),dz=target.getZ()-bee.getZ();a.shoot(dx,dy,dz,1.45f,3f);a.setBaseDamage(2.5);a.setSecondsOnFire(4);a.addEffect(new MobEffectInstance(MobEffects.POISON,100,1));level.addFreshEntity(a);}

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hurt(LivingHurtEvent e){if(!e.getEntity().getTags().contains(EXTRA))return;Entity src=e.getSource().getEntity();if(!(src instanceof ServerPlayer p))return;ItemStack held=p.getMainHandItem();float mult=.22f;if(held.hasTag()&&held.getTag().getBoolean("GunnerArenaKnife"))mult=.72f;else{ResourceLocation id=ForgeRegistries.ITEMS.getKey(held.getItem());if(id!=null&&(id.getNamespace().equals("jeg")||id.getNamespace().equals("gunnerarena")))mult=1.45f;}e.setAmount(Math.max(.25f,e.getAmount()*mult));}

    @SubscribeEvent public static void death(LivingDeathEvent e){if(!e.getEntity().getTags().contains(EXTRA))return;ServerPlayer killer=e.getSource().getEntity() instanceof ServerPlayer p?p:null;if(killer==null)return;int t=tier(e.getEntity());int credits=100+t*100,crystals=Math.max(1,(t+1)/2);boolean c=AdminToolsCommands.grantCredits(killer,credits),r=AdminToolsCommands.grantCrystals(killer,crystals);killer.sendSystemMessage(Component.literal("✦ NPC повержен: +$"+credits+"  +"+crystals+"◆").withStyle(c||r?ChatFormatting.GOLD:ChatFormatting.GRAY));}

    private static int tier(Entity e){for(String s:e.getTags())if(s.startsWith("ggo_npc_tier_"))try{return Math.max(1,Math.min(5,Integer.parseInt(s.substring(13))));}catch(Exception ignored){}return 1;}
    private static int clear(MinecraftServer server){int n=0;for(ServerLevel l:server.getAllLevels()){List<Entity> rm=new ArrayList<>();for(Entity e:l.getAllEntities())if(e.getTags().contains(EXTRA))rm.add(e);for(Entity e:rm){e.discard();n++;}}return n;}
}
