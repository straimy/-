package arena.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

/** Client UX guard for arena-owned JEG guns. JEG remains authoritative for actual fire/reload. */
@Mod.EventBusSubscriber(modid="gunnerarena", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class JegNoAmmoFeedback {
    private static boolean attackWasDown, reloadWasDown;
    private static long messageUntil;
    private JegNoAmmoFeedback() {}

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) { attackWasDown=false; reloadWasDown=false; return; }
        ItemStack gun = mc.player.getMainHandItem();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(gun.getItem());
        boolean arenaJeg = id != null && "jeg".equals(id.getNamespace()) && gun.hasTag() && gun.getTag().getBoolean("GunnerArenaBound");
        boolean attack = mc.options.keyAttack.isDown();
        boolean reload = InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_R);
        if (!arenaJeg) { attackWasDown=attack; reloadWasDown=reload; return; }

        int loaded = Math.max(0, gun.getOrCreateTag().getInt("AmmoCount"));
        Item ammoItem = ammoItemFor(id);
        int reserve = ammoItem == null ? 0 : count(mc, ammoItem);

        if (attack && !attackWasDown && loaded <= 0) {
            if (reserve <= 0) {
                show(mc, "✦ НЕТ ПАТРОНОВ", ChatFormatting.RED);
                // Prevent the arena client from presenting a normal attack when the server/JEG cannot fire.
                mc.options.keyAttack.setDown(false);
                attack = false;
            } else {
                show(mc, "R — ПЕРЕЗАРЯДИТЬ", ChatFormatting.YELLOW);
                mc.options.keyAttack.setDown(false);
                attack = false;
            }
        }
        if (reload && !reloadWasDown && reserve <= 0 && loaded < magazineCapHint(gun)) {
            show(mc, "✦ НЕТ ПАТРОНОВ ДЛЯ ПЕРЕЗАРЯДКИ", ChatFormatting.RED);
        }
        attackWasDown=attack; reloadWasDown=reload;
    }

    private static void show(Minecraft mc,String text,ChatFormatting color){
        long now=System.currentTimeMillis();
        if(now<messageUntil-250)return;
        messageUntil=now+1800;
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
        // Guard only needs to know whether a reload could make sense. The exact cap remains native JEG data.
        int loaded=gun.getOrCreateTag().getInt("AmmoCount");return Math.max(loaded+1,1);
    }
}
