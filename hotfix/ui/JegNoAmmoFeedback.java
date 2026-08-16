package arena.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

/** Hard client dry-fire guard for JEG firearms. Arena melee/knife items are never treated as guns. */
@Mod.EventBusSubscriber(modid="gunnerarena", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class JegNoAmmoFeedback {
    private static boolean reloadWasDown;
    private static long messageUntil;
    private JegNoAmmoFeedback() {}

    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.screen!=null||!e.isAttack())return;
        ItemStack gun=mc.player.getMainHandItem();
        if(!isJegGun(gun)||loaded(gun)>0)return;
        e.setCanceled(true);mc.options.keyAttack.setDown(false);
        int reserve=reserve(mc,gun);show(mc,reserve<=0?"✦ НЕТ ПАТРОНОВ":"R — ПЕРЕЗАРЯДИТЬ",reserve<=0?ChatFormatting.RED:ChatFormatting.YELLOW);
    }

    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.START)return;Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.screen!=null){reloadWasDown=false;return;}
        ItemStack gun=mc.player.getMainHandItem();boolean reload=InputConstants.isKeyDown(mc.getWindow().getWindow(),GLFW.GLFW_KEY_R);
        if(!isJegGun(gun)){reloadWasDown=reload;return;}
        int loaded=loaded(gun),reserve=reserve(mc,gun);
        if(loaded<=0&&mc.options.keyAttack.isDown()){mc.options.keyAttack.setDown(false);show(mc,reserve<=0?"✦ НЕТ ПАТРОНОВ":"R — ПЕРЕЗАРЯДИТЬ",reserve<=0?ChatFormatting.RED:ChatFormatting.YELLOW);}
        if(reload&&!reloadWasDown&&reserve<=0&&loaded<=0)show(mc,"✦ НЕТ ПАТРОНОВ",ChatFormatting.RED);
        reloadWasDown=reload;
    }

    private static boolean isJegGun(ItemStack stack){
        if(stack==null||stack.isEmpty()||isArenaKnife(stack))return false;
        ResourceLocation id=ForgeRegistries.ITEMS.getKey(stack.getItem());
        if(id==null||!"jeg".equals(id.getNamespace()))return false;
        String p=id.getPath();
        return !p.contains("knife")&&!p.contains("melee");
    }
    private static boolean isArenaKnife(ItemStack s){return s.hasTag()&&s.getTag().getBoolean("GunnerArenaKnife");}
    private static int loaded(ItemStack gun){return gun.hasTag()?Math.max(0,gun.getTag().getInt("AmmoCount")):0;}
    private static int reserve(Minecraft mc,ItemStack gun){ResourceLocation id=ForgeRegistries.ITEMS.getKey(gun.getItem());Item a=id==null?null:ammoItemFor(id);return a==null?0:count(mc,a);}
    private static void show(Minecraft mc,String text,ChatFormatting color){long now=System.currentTimeMillis();if(now<messageUntil-250)return;messageUntil=now+1100;mc.player.displayClientMessage(Component.literal(text).withStyle(color),true);}
    private static int count(Minecraft mc,Item item){int n=0;for(ItemStack s:mc.player.getInventory().items)if(s.is(item))n+=s.getCount();for(ItemStack s:mc.player.getInventory().offhand)if(s.is(item))n+=s.getCount();return n;}
    private static Item ammoItemFor(ResourceLocation gun){String p=gun.getPath();String ammo=p.contains("shotgun")?"shotgun_shell":((p.contains("pistol")||p.contains("revolver")||p.contains("smg"))?"pistol_ammo":"rifle_ammo");return ForgeRegistries.ITEMS.getValue(new ResourceLocation("jeg",ammo));}
}
