package arena.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = GunnerArenaUiMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PlayerStatusOverlay {
    private PlayerStatusOverlay() {}

    @SubscribeEvent
    public static void render(RenderPlayerEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.getEntity() == mc.player) return;
        var player = event.getEntity();
        if (mc.getEntityRenderDispatcher().distanceToSqr(player) > 4096.0D) return;

        float hp = Math.max(0f, player.getHealth());
        float max = Math.max(1f, player.getMaxHealth());
        var resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
        boolean protectedSpawn = resistance != null && resistance.getAmplifier() >= 4;
        int seconds = protectedSpawn ? Math.max(1, (resistance.getDuration()+19)/20) : 0;
        String text = protectedSpawn
            ? "🛡 " + seconds + "с   ❤ " + Math.round(hp) + "/" + Math.round(max)
            : "❤ " + Math.round(hp) + "/" + Math.round(max);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0.0D, player.getBbHeight() + 0.62D, 0.0D);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f matrix = pose.last().pose();
        Font font = mc.font;
        float x = -font.width(text) / 2.0F;
        int color = protectedSpawn ? 0x66E6FF : 0xFFFFFF;
        font.drawInBatch(Component.literal(text), x, 0.0F, color, false, matrix,
            event.getMultiBufferSource(), Font.DisplayMode.NORMAL, 0x55000000, event.getPackedLight());
        pose.popPose();
    }
}
