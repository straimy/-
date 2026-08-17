package arena.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
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

/** Conservative JEG dry-fire feedback. Never touches the GGO tactical knife. */
@Mod.EventBusSubscriber(modid="gunnerarena", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class JegNoAmmoFeedback {
    private static boolean reloadWasDown;
    private static long messageUntil;
    private JegNoAmmoFeedback() {}

    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.screen!=null||!e.isAttack())return;
        ItemStack gun=mc.player.getMainHandItem();
        if(KnifeClientGuard.isKnife(gun)||mc.player.getInventory().selected==0)return;
        if(!isJegGun(gun))return;
        int loaded=loaded(gun),reserve=reserve(mc,gun);
        if(loaded>0||reserve>0||loaded<0)return;
        e.setCanceled(true);mc.options.keyAttack.setDown(false);
        show(mc,"✦ НЕТ ПАТРОНОВ",ChatFormatting.RED);
    }

    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.START)return;Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.screen!=null){reloadWasDown=false;return;}
        ItemStack gun=mc.player.getMainHandItem();boolean reload=InputConstants.isKeyDown(mc.getWindow().getWindow(),GLFW.GLFW_KEY_R);
        if(KnifeClientGuard.isKnife(gun)||mc.player.getInventory().selected==0){reloadWasDown=reload;return;}
        if(!isJegGun(gun)){reloadWasDown=reload;return;}
        int loaded=loaded(gun),reserve=reserve(mc,gun);
        if(loaded==0&&reserve==0&&mc.options.keyAttack.isDown()){mc.options.keyAttack.setDown(false);show(mc,"✦ НЕТ ПАТРОНОВ",ChatFormatting.RED);}
        if(reload&&!reloadWasDown){if(loaded>0){}else if(reserve>0)show(mc,"R — ПЕРЕЗАРЯДИТЬ",ChatFormatting.YELLOW);else if(loaded==0)show(mc,"✦ НЕТ ПАТРОНОВ",ChatFormatting.RED);}
        reloadWasDown=reload;
    }

    private static boolean isJegGun(ItemStack stack){
        if(stack==null||stack.isEmpty()||KnifeClientGuard.isKnife(stack))return false;
        ResourceLocation id=ForgeRegistries.ITEMS.getKey(stack.getItem());
        if(id==null||!"jeg".equals(id.getNamespace()))return false;
        String p=id.getPath();return !p.contains("knife")&&!p.contains("melee");
    }
    private static int loaded(ItemStack gun){if(!gun.hasTag())return -1;CompoundTag t=gun.getTag();String[] keys={"AmmoCount","Ammo","CurrentAmmo","Magazine","MagazineAmmo","Bullets","BulletsInMagazine"};for(String k:keys)if(t.contains(k,99))return Math.max(0,t.getInt(k));if(t.contains("Gun",10)){CompoundTag g=t.getCompound("Gun");for(String k:keys)if(g.contains(k,99))return Math.max(0,g.getInt(k));}return -1;}
    private static int reserve(Minecraft mc,ItemStack gun){ResourceLocation id=ForgeRegistries.ITEMS.getKey(gun.getItem());Item a=id==null?null:ammoItemFor(id);return a==null?0:count(mc,a);}
    private static void show(Minecraft mc,String text,ChatFormatting color){long now=System.currentTimeMillis();if(now<messageUntil-250)return;messageUntil=now+1100;mc.player.displayClientMessage(Component.literal(text).withStyle(color),true);}
    private static int count(Minecraft mc,Item item){int n=0;for(ItemStack s:mc.player.getInventory().items)if(s.is(item))n+=s.getCount();for(ItemStack s:mc.player.getInventory().offhand)if(s.is(item))n+=s.getCount();return n;}
    private static Item ammoItemFor(ResourceLocation gun){String p=gun.getPath();String ammo=p.contains("shotgun")?"shotgun_shell":((p.contains("pistol")||p.contains("revolver")||p.contains("smg"))?"pistol_ammo":"rifle_ammo");return ForgeRegistries.ITEMS.getValue(new ResourceLocation("jeg",ammo));}
}
