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

/**
 * Client-side hard guard for arena-owned JEG guns.
 * Empty magazine means the fire input is cancelled before JEG can play a fake shot/recoil animation.
 * The actual ammo/damage authority still stays on the server/JEG side.
 */
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
        if(!isArenaJeg(gun)) return;
        int loaded=loaded(gun);
        if(loaded>0) return;

        e.setCanceled(true);
        mc.options.keyAttack.setDown(false);
        ResourceLocation id=ForgeRegistries.ITEMS.getKey(gun.getItem());
        Item ammoItem=id==null?null:ammoItemFor(id);
        int reserve=ammoItem==null?0:count(mc,ammoItem);
        if(reserve<=0) show(mc,"✦ НЕТ ПАТРОНОВ",ChatFormatting.RED);
        else show(mc,"R — ПЕРЕЗАРЯДИТЬ",ChatFormatting.YELLOW);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) { reloadWasDown=false; return; }
        ItemStack gun = mc.player.getMainHandItem();
        boolean reload = InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_R);
        if (!isArenaJeg(gun)) { reloadWasDown=reload; return; }

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(gun.getItem());
        int loaded = loaded(gun);
        Item ammoItem = id == null ? null : ammoItemFor(id);
        int reserve = ammoItem == null ? 0 : count(mc, ammoItem);

        // Do this every client tick, not only on the first click. This closes the shotgun
        // dry-fire/recoil loop where a held attack key could be re-polled by JEG next tick.
        if (loaded <= 0 && mc.options.keyAttack.isDown()) {
            mc.options.keyAttack.setDown(false);
            if (reserve <= 0) show(mc, "✦ НЕТ ПАТРОНОВ", ChatFormatting.RED);
            else show(mc, "R — ПЕРЕЗАРЯДИТЬ", ChatFormatting.YELLOW);
        }

        if (reload && !reloadWasDown && reserve <= 0 && loaded < magazineCapHint(gun)) {
            show(mc, "✦ НЕТ ПАТРОНОВ ДЛЯ ПЕРЕЗАРЯДКИ", ChatFormatting.RED);
        }
        reloadWasDown=reload;
    }

    private static boolean isArenaJeg(ItemStack gun){
        if(gun==null||gun.isEmpty()||!gun.hasTag()) return false;
        ResourceLocation id=ForgeRegistries.ITEMS.getKey(gun.getItem());
        return id!=null&&"jeg".equals(id.getNamespace())&&gun.getTag().getBoolean("GunnerArenaBound");
    }

    private static int loaded(ItemStack gun){
        if(gun==null||gun.isEmpty()||!gun.hasTag()) return 0;
        return Math.max(0,gun.getTag().getInt("AmmoCount"));
    }

    private static void show(Minecraft mc,String text,ChatFormatting color){
        long now=System.currentTimeMillis();
        if(now<messageUntil-250)return;
        messageUntil=now+1300;
        // Action-bar placement is intentionally just above the hotbar/hearts.
        mc.player.displayClientMessage(Component.literal(text).withStyle(color),true);
    }
    private static int count(Minecraft mc,Item item){int n=0;for(ItemStack s:mc.player.getInventory().items)if(s.is(item))n+=s.getCount();for(ItemStack s:mc.player.getInventory().offhand)if(s.is(item))n+=s.getCount();return n;}
    private static Item ammoItemFor(ResourceLocation gun){
        String p=gun.getPath(); String ammo;
        if(p.contains("shotgun")) ammo="shotgun_shell";
        else if(p.contains("pistol")||p.contains("revolver")||p.contains("smg")) ammo="pistol_ammo";
        else ammo="rifle_ammo";
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation("jeg",ammo));
    }
    private static int magazineCapHint(ItemStack gun){
        int loaded=loaded(gun);return Math.max(loaded+1,1);
    }
}
