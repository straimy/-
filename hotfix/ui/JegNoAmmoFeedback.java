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

/** Hard client dry-fire guard for every JEG gun used on GunGloryOnline. */
@Mod.EventBusSubscriber(modid="gunnerarena", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class JegNoAmmoFeedback {
    private static boolean reloadWasDown;
    private static long messageUntil;
    private JegNoAmmoFeedback() {}

    @SubscribeEvent
    public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.screen!=null||!e.isAttack()) return;
        ItemStack gun=mc.player.getMainHandItem();
        if(!isJeg(gun)||loaded(gun)>0) return;
        e.setCanceled(true);
        mc.options.keyAttack.setDown(false);
        int reserve=reserve(mc,gun);
        show(mc,reserve<=0?"✦ НЕТ ПАТРОНОВ":"R — ПЕРЕЗАРЯДИТЬ",reserve<=0?ChatFormatting.RED:ChatFormatting.YELLOW);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.START)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.screen!=null){reloadWasDown=false;return;}
        ItemStack gun=mc.player.getMainHandItem();
        boolean reload=InputConstants.isKeyDown(mc.getWindow().getWindow(),GLFW.GLFW_KEY_R);
        if(!isJeg(gun)){reloadWasDown=reload;return;}
        int loaded=loaded(gun),reserve=reserve(mc,gun);
        // Clear attack every tick while empty: JEG must never reach its recoil/shot path.
        if(loaded<=0){
            if(mc.options.keyAttack.isDown()){
                mc.options.keyAttack.setDown(false);
                show(mc,reserve<=0?"✦ НЕТ ПАТРОНОВ":"R — ПЕРЕЗАРЯДИТЬ",reserve<=0?ChatFormatting.RED:ChatFormatting.YELLOW);
            }
        }
        if(reload&&!reloadWasDown&&reserve<=0&&loaded<=0)show(mc,"✦ НЕТ ПАТРОНОВ",ChatFormatting.RED);
        reloadWasDown=reload;
    }

    private static boolean isJeg(ItemStack gun){
        if(gun==null||gun.isEmpty())return false;
        ResourceLocation id=ForgeRegistries.ITEMS.getKey(gun.getItem());
        return id!=null&&"jeg".equals(id.getNamespace());
    }
    private static int loaded(ItemStack gun){return gun.hasTag()?Math.max(0,gun.getTag().getInt("AmmoCount")):0;}
    private static int reserve(Minecraft mc,ItemStack gun){ResourceLocation id=ForgeRegistries.ITEMS.getKey(gun.getItem());Item a=id==null?null:ammoItemFor(id);return a==null?0:count(mc,a);}
    private static void show(Minecraft mc,String text,ChatFormatting color){long now=System.currentTimeMillis();if(now<messageUntil-250)return;messageUntil=now+1100;mc.player.displayClientMessage(Component.literal(text).withStyle(color),true);}
    private static int count(Minecraft mc,Item item){int n=0;for(ItemStack s:mc.player.getInventory().items)if(s.is(item))n+=s.getCount();for(ItemStack s:mc.player.getInventory().offhand)if(s.is(item))n+=s.getCount();return n;}
    private static Item ammoItemFor(ResourceLocation gun){String p=gun.getPath();String ammo=p.contains("shotgun")?"shotgun_shell":((p.contains("pistol")||p.contains("revolver")||p.contains("smg"))?"pistol_ammo":"rifle_ammo");return ForgeRegistries.ITEMS.getValue(new ResourceLocation("jeg",ammo));}
}
