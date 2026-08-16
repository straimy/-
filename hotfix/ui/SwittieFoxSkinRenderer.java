package arena.client.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only visual replacement for the server-side Swittie Fox pillager body.
 * Gameplay/AI stays server-authoritative, while clients see a normal player-shaped model
 * using the bundled 64x64 skin instead of an illager wearing a player head.
 */
@Mod.EventBusSubscriber(modid="gunnerarena", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SwittieFoxSkinRenderer {
    private static final String TAG = "gunglory_swittie_fox";
    private static final ResourceLocation SKIN = new ResourceLocation("gunnerarena", "textures/entity/swittie_fox.png");
    private static final Component NAME = Component.literal("Свитти Фокс");
    private static PlayerModel<Pillager> model;

    private SwittieFoxSkinRenderer() {}

    @SubscribeEvent
    public static void pre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Pillager bot) || !bot.getTags().contains(TAG)) return;

        event.setCanceled(true);
        Minecraft mc = Minecraft.getInstance();
        if (model == null) model = new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER), false);

        float partial = event.getPartialTick();
        float bodyYaw = Mth.rotLerp(partial, bot.yBodyRotO, bot.yBodyRot);
        float headYaw = Mth.rotLerp(partial, bot.yHeadRotO, bot.yHeadRot) - bodyYaw;
        float headPitch = Mth.lerp(partial, bot.xRotO, bot.getXRot());
        float walkSpeed = bot.walkAnimation.speed(partial);
        float walkPos = bot.walkAnimation.position(partial);
        float age = bot.tickCount + partial;

        model.attackTime = 0.0F;
        model.riding = bot.isPassenger();
        model.young = false;
        model.crouching = bot.isCrouching();
        model.prepareMobModel(bot, walkPos, walkSpeed, partial);
        model.setupAnim(bot, walkPos, walkSpeed, age, headYaw, headPitch);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0F, -1.501F, 0.0F);

        VertexConsumer skinBuffer = event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(SKIN));
        model.renderToBuffer(pose, skinBuffer, event.getPackedLight(), OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);

        ItemStack held = bot.getMainHandItem();
        if (!held.isEmpty()) {
            pose.pushPose();
            model.rightArm.translateAndRotate(pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate(0.0F, 0.125F, -0.625F);
            ItemRenderer items = mc.getItemRenderer();
            items.renderStatic(held, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, event.getPackedLight(), OverlayTexture.NO_OVERLAY,
                pose, event.getMultiBufferSource(), bot.level(), bot.getId());
            pose.popPose();
        }
        pose.popPose();

        // Keep the bot's identity visible even though the vanilla pillager renderer is cancelled.
        pose.pushPose();
        pose.translate(0.0D, bot.getBbHeight() + 0.55D, 0.0D);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025F, -0.025F, 0.025F);
        var font = mc.font;
        float x = -font.width(NAME) / 2.0F;
        font.drawInBatch(NAME, x, 0.0F, 0xFFFF55FF, false, pose.last().pose(), event.getMultiBufferSource(),
            net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0x50000000, event.getPackedLight());
        pose.popPose();
    }
}
