package arena.forge;

import arena.GunnerArenaMod;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Small crystal shop for simple combat boosts. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class BoostShopService {
    private static final Path FILE=FMLPaths.CONFIGDIR.get().resolve("gunnerarena").resolve("boosts.properties");
    private static final Properties DATA=new Properties();private static boolean loaded;
    private static final UUID BOOTS_MOD=UUID.fromString("2cd55429-0758-438f-b69e-f72155ddb748");
    private static final UUID KEVLAR_MOD=UUID.fromString("c0c6cc16-a9d8-4d46-8d44-80c49e37d3b8");
    private static final UUID STIM_MOD=UUID.fromString("87d76a35-7182-43c0-9978-dfc28beb1526");
    private BoostShopService(){}

    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("ggoboostbuy").then(Commands.argument("id",StringArgumentType.word()).executes(c->{
            ServerPlayer p=c.getSource().getPlayerOrException();buy(p,StringArgumentType.getString(c,"id"));return 1;
        })));
    }

    public static void buy(ServerPlayer p,String raw){String id=raw==null?"":raw.toLowerCase(Locale.ROOT);int cost=switch(id){case"boots"->60;case"kevlar"->75;case"stim"->20;case"gloves"->55;default->-1;};if(cost<0)return;
        synchronized(DATA){load();UUID u=GgoIdentityBridge.idFor(p);long now=System.currentTimeMillis();
            if((id.equals("boots")||id.equals("kevlar")||id.equals("gloves"))&&bool(u,id)){msg(p,"✓ Уже куплено навсегда.",ChatFormatting.GREEN);return;}
            var r=GunnerArenaMod.RUNTIME;if(r==null)return;var profile=r.players().profile(p);if(profile==null||profile.crystals<cost){msg(p,"✦ Нужно ◆ "+cost+".",ChatFormatting.RED);return;}
            profile.crystals-=cost;r.profiles().markDirty(p.getUUID());
            if(id.equals("stim"))DATA.setProperty(key(u,"stimUntil"),Long.toString(now+20L*60L*1000L));else DATA.setProperty(key(u,id),"true");save();apply(p);
            String name=switch(id){case"boots"->"Ботинки скороходы";case"kevlar"->"Кевларовая вставка";case"stim"->"Адреналин на 20 минут";default->"Боевые перчатки";};msg(p,"✓ Куплено: "+name,ChatFormatting.AQUA);
        }
    }

    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase!=TickEvent.Phase.END||e.player.level().isClientSide||!(e.player instanceof ServerPlayer p)||p.tickCount%40!=0)return;apply(p);}
    private static void apply(ServerPlayer p){synchronized(DATA){load();UUID u=GgoIdentityBridge.idFor(p);long now=System.currentTimeMillis();
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null){speed.removeModifier(BOOTS_MOD);speed.removeModifier(STIM_MOD);if(bool(u,"boots"))speed.addTransientModifier(new AttributeModifier(BOOTS_MOD,"ggo_boots",.04,AttributeModifier.Operation.MULTIPLY_TOTAL));if(longVal(u,"stimUntil")>now)speed.addTransientModifier(new AttributeModifier(STIM_MOD,"ggo_stim",.08,AttributeModifier.Operation.MULTIPLY_TOTAL));}
        var hp=p.getAttribute(Attributes.MAX_HEALTH);if(hp!=null){hp.removeModifier(KEVLAR_MOD);if(bool(u,"kevlar"))hp.addTransientModifier(new AttributeModifier(KEVLAR_MOD,"ggo_kevlar",2.0,AttributeModifier.Operation.ADDITION));}
    }}

    @SubscribeEvent public static void hurt(LivingHurtEvent e){if(e.getEntity().level().isClientSide)return;if(e.getSource().getEntity() instanceof ServerPlayer p){synchronized(DATA){load();if(bool(GgoIdentityBridge.idFor(p),"gloves")&&ArenaKnifeDamageFix.isArenaKnife(p.getMainHandItem()))e.setAmount(e.getAmount()*1.10f);}}}

    private static boolean bool(UUID u,String k){return Boolean.parseBoolean(DATA.getProperty(key(u,k),"false"));}private static long longVal(UUID u,String k){try{return Long.parseLong(DATA.getProperty(key(u,k),"0"));}catch(Exception x){return 0L;}}
    private static String key(UUID u,String k){return"p."+u+"."+k;}private static void msg(ServerPlayer p,String s,ChatFormatting c){p.sendSystemMessage(Component.literal(s).withStyle(c));}
    private static void load(){if(loaded)return;loaded=true;try{Files.createDirectories(FILE.getParent());if(Files.exists(FILE))try(InputStream in=Files.newInputStream(FILE)){DATA.load(in);}}catch(Exception ignored){}}
    private static void save(){try{Files.createDirectories(FILE.getParent());try(OutputStream out=Files.newOutputStream(FILE)){DATA.store(out,"GunGloryOnline boost shop");}}catch(Exception ignored){}}
}
