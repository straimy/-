package arena.forge;

import arena.GunnerArenaMod;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** OP-only GGO admin mode + shared safe economy mutators. */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class AdminToolsCommands {
    public static final String ADMIN_BUILD_TAG="ggo_admin_build_mode";
    public static final String ADMIN_SPECTATOR_TAG="ggo_admin_spectator_mode";
    private AdminToolsCommands(){}

    @SubscribeEvent public static void commands(RegisterCommandsEvent e){
        // Canonical GGO administration mode.  The old /gm alias is intentionally
        // not registered anymore, so there is one obvious entry point for staff.
        e.getDispatcher().register(Commands.literal("admin").requires(s->s.hasPermission(2))
            .executes(ctx->showAdminMode(ctx.getSource().getPlayerOrException()))
            .then(Commands.argument("mode",IntegerArgumentType.integer(0,2)).executes(ctx->{
                ServerPlayer p=ctx.getSource().getPlayerOrException();
                int mode=IntegerArgumentType.getInteger(ctx,"mode");
                return setAdminMode(p,mode);
            })));

        e.getDispatcher().register(Commands.literal("crystals").requires(s->s.hasPermission(2)).then(Commands.literal("give").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("amount",LongArgumentType.longArg(1,1_000_000L)).executes(ctx->{ServerPlayer target=EntityArgument.getPlayer(ctx,"player");long amount=LongArgumentType.getLong(ctx,"amount");if(!grantCrystals(target,amount))return 0;ctx.getSource().sendSuccess(()->Component.literal("[GGO] +"+amount+" ◆ → "+target.getGameProfile().getName()).withStyle(ChatFormatting.AQUA),true);return 1;})))));
        registerCredits(e,"credits");registerCredits(e,"credit");
    }

    private static int setAdminMode(ServerPlayer p,int mode){
        p.removeTag(ADMIN_BUILD_TAG);
        p.removeTag(ADMIN_SPECTATOR_TAG);
        switch(mode){
            case 1 -> {
                p.addTag(ADMIN_BUILD_TAG);
                p.setGameMode(GameType.CREATIVE);
                p.sendSystemMessage(Component.literal("[GGO] ADMIN 1 • BUILD MODE").withStyle(ChatFormatting.AQUA));
                p.sendSystemMessage(Component.literal("Creative inventory, flight, block placement/breaking enabled. Use /admin 0 to return to normal play.").withStyle(ChatFormatting.GRAY));
            }
            case 2 -> {
                p.addTag(ADMIN_SPECTATOR_TAG);
                p.setGameMode(GameType.SPECTATOR);
                p.sendSystemMessage(Component.literal("[GGO] ADMIN 2 • SPECTATOR").withStyle(ChatFormatting.LIGHT_PURPLE));
                p.sendSystemMessage(Component.literal("Free spectator camera enabled. Use /admin 0 to return to normal play.").withStyle(ChatFormatting.GRAY));
            }
            default -> {
                // GGO's ordinary protected player state. OP permission itself is
                // deliberately retained; only the explicit admin gameplay mode ends.
                p.setGameMode(GameType.ADVENTURE);
                p.sendSystemMessage(Component.literal("[GGO] ADMIN 0 • PLAYER MODE").withStyle(ChatFormatting.GREEN));
                p.sendSystemMessage(Component.literal("Returned to normal GGO gameplay; operator permissions are unchanged.").withStyle(ChatFormatting.GRAY));
            }
        }
        return 1;
    }

    private static int showAdminMode(ServerPlayer p){
        String mode=p.getTags().contains(ADMIN_BUILD_TAG)?"1 • BUILD":p.getTags().contains(ADMIN_SPECTATOR_TAG)?"2 • SPECTATOR":"0 • PLAYER";
        p.sendSystemMessage(Component.literal("[GGO] Current admin mode: "+mode).withStyle(ChatFormatting.AQUA));
        p.sendSystemMessage(Component.literal("/admin 0 = player   /admin 1 = build   /admin 2 = spectator").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    public static boolean grantCrystals(ServerPlayer target,long amount){if(target==null||amount<=0||GunnerArenaMod.RUNTIME==null)return false;var r=GunnerArenaMod.RUNTIME;var profile=r.players().profile(target);if(profile==null)return false;profile.crystals=profile.crystals>Long.MAX_VALUE-amount?Long.MAX_VALUE:profile.crystals+amount;r.profiles().markDirty(target.getUUID());return true;}
    public static boolean grantCredits(ServerPlayer target,int amount){if(target==null||amount<=0||GunnerArenaMod.RUNTIME==null)return false;Object manager=GunnerArenaMod.RUNTIME.players(),session=GunnerArenaMod.RUNTIME.players().roundSession(target);boolean ok=addCreditsReflective(session,amount);if(!ok)ok=managerCreditsReflective(manager,target,amount,false);return ok;}

    private static void registerCredits(RegisterCommandsEvent e,String root){
        e.getDispatcher().register(Commands.literal(root).requires(s->s.hasPermission(2))
            .then(Commands.literal("give").then(Commands.argument("amount",IntegerArgumentType.integer(1,1_000_000)).executes(ctx->change(ctx.getSource(),ctx.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(ctx,"amount"),false))).then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("amount",IntegerArgumentType.integer(1,1_000_000)).executes(ctx->change(ctx.getSource(),EntityArgument.getPlayer(ctx,"player"),IntegerArgumentType.getInteger(ctx,"amount"),false)))))
            .then(Commands.literal("set").then(Commands.argument("amount",IntegerArgumentType.integer(0,1_000_000)).executes(ctx->change(ctx.getSource(),ctx.getSource().getPlayerOrException(),IntegerArgumentType.getInteger(ctx,"amount"),true))).then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("amount",IntegerArgumentType.integer(0,1_000_000)).executes(ctx->change(ctx.getSource(),EntityArgument.getPlayer(ctx,"player"),IntegerArgumentType.getInteger(ctx,"amount"),true)))))
            .then(Commands.literal("get").executes(ctx->show(ctx.getSource(),ctx.getSource().getPlayerOrException())).then(Commands.argument("player",EntityArgument.player()).executes(ctx->show(ctx.getSource(),EntityArgument.getPlayer(ctx,"player"))))));
    }

    private static int change(CommandSourceStack src,ServerPlayer target,int amount,boolean set){var r=GunnerArenaMod.RUNTIME;if(r==null){src.sendFailure(Component.literal("[GGO] Runtime ещё не готов."));return 0;}Object manager=r.players(),session=r.players().roundSession(target);boolean ok=set?setCreditsReflective(session,amount):grantCredits(target,amount);if(!ok&&set)ok=managerCreditsReflective(manager,target,amount,true);Integer now=readCredits(session);if(!ok){src.sendFailure(Component.literal("[GGO] Не удалось изменить кредиты. Используй /credits get — runtime несовместим."));return 0;}String value=now==null?"?":Integer.toString(now);src.sendSuccess(()->Component.literal("[GGO] $"+value+" → "+target.getGameProfile().getName()).withStyle(ChatFormatting.GOLD),true);target.sendSystemMessage(Component.literal("$ Матчевые кредиты: "+value).withStyle(ChatFormatting.GOLD));return 1;}
    private static int show(CommandSourceStack src,ServerPlayer target){Object s=GunnerArenaMod.RUNTIME==null?null:GunnerArenaMod.RUNTIME.players().roundSession(target);Integer v=readCredits(s);if(v==null){src.sendFailure(Component.literal("[GGO] Не удалось прочитать кредиты."));return 0;}src.sendSuccess(()->Component.literal("[GGO] "+target.getGameProfile().getName()+": $"+v),false);return 1;}
    private static boolean managerCreditsReflective(Object manager,ServerPlayer p,int amount,boolean set){if(manager==null)return false;String[] names=set?new String[]{"setCredits","setRoundCredits"}:new String[]{"addCredits","addRoundCredits","giveCredits"};for(String n:names){for(Method m:manager.getClass().getMethods()){if(!m.getName().equals(n)||m.getParameterCount()!=2)continue;try{Class<?>[] t=m.getParameterTypes();Object a0=t[0].isAssignableFrom(ServerPlayer.class)?p:p.getUUID();Object a1=t[1]==long.class||t[1]==Long.class?(long)amount:amount;m.invoke(manager,a0,a1);return true;}catch(Exception ignored){}}}return false;}
    private static boolean addCreditsReflective(Object s,int a){if(s==null)return false;for(String n:new String[]{"addCredits","addRoundCredits","credit","grantCredits"})if(invokeNumber(s,n,a))return true;Integer v=readCredits(s);return v!=null&&setCreditsReflective(s,saturatingIntAdd(v,a));}
    private static boolean setCreditsReflective(Object s,int v){if(s==null)return false;for(String n:new String[]{"setCredits","setRoundCredits","credits","roundCredits"})if(invokeNumber(s,n,v))return true;for(Field f:allFields(s.getClass())){if(!f.getName().toLowerCase(Locale.ROOT).contains("credit"))continue;try{f.setAccessible(true);Class<?> t=f.getType();if(t==int.class){f.setInt(s,v);return true;}if(t==long.class){f.setLong(s,v);return true;}Object x=f.get(s);if(x instanceof AtomicInteger ai){ai.set(v);return true;}if(x instanceof AtomicLong al){al.set(v);return true;}}catch(Exception ignored){}}return false;}
    private static Integer readCredits(Object s){if(s==null)return null;for(String n:new String[]{"credits","roundCredits","getCredits","getRoundCredits"})try{Method m=s.getClass().getMethod(n);Object v=m.invoke(s);if(v instanceof Number x)return x.intValue();}catch(Exception ignored){}for(Field f:allFields(s.getClass())){if(!f.getName().toLowerCase(Locale.ROOT).contains("credit"))continue;try{f.setAccessible(true);Object v=f.get(s);if(v instanceof Number x)return x.intValue();if(v instanceof AtomicInteger ai)return ai.get();if(v instanceof AtomicLong al)return (int)Math.min(Integer.MAX_VALUE,al.get());}catch(Exception ignored){}}return null;}
    private static boolean invokeNumber(Object s,String n,int v){for(Class<?> t:new Class<?>[]{int.class,long.class,Integer.class,Long.class})try{s.getClass().getMethod(n,t).invoke(s,(t==long.class||t==Long.class)?(long)v:v);return true;}catch(Exception ignored){}return false;}
    private static Field[] allFields(Class<?> c){java.util.ArrayList<Field> out=new java.util.ArrayList<>();for(Class<?> x=c;x!=null;x=x.getSuperclass())for(Field f:x.getDeclaredFields())out.add(f);return out.toArray(Field[]::new);}
    private static int saturatingIntAdd(int v,int a){long s=(long)v+a;return (int)Math.min(Integer.MAX_VALUE,Math.max(0,s));}
}
