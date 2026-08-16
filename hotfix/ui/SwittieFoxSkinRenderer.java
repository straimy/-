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

@Mod.EventBusSubscriber(modid="gunnerarena", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class SwittieFoxSkinRenderer {
    private static final String HOSTILE_TAG = "gunglory_swittie_fox";
    private static final String COMPANION_TAG = "gunglory_swittie_companion";
    private static final ResourceLocation SKIN = new ResourceLocation("gunnerarena", "textures/entity/swittie_fox.png");
    private static final Component NAME = Component.literal("Свитти Фокс");
    private static PlayerModel<Pillager> model;
    private SwittieFoxSkinRenderer() {}

    @SubscribeEvent public static void pre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Pillager bot) || (!bot.getTags().contains(HOSTILE_TAG)&&!bot.getTags().contains(COMPANION_TAG))) return;
        event.setCanceled(true);Minecraft mc=Minecraft.getInstance();if(model==null)model=new PlayerModel<>(mc.getEntityModels().bakeLayer(ModelLayers.PLAYER),false);
        float partial=event.getPartialTick(),bodyYaw=Mth.rotLerp(partial,bot.yBodyRotO,bot.yBodyRot),headYaw=Mth.rotLerp(partial,bot.yHeadRotO,bot.yHeadRot)-bodyYaw,headPitch=Mth.lerp(partial,bot.xRotO,bot.getXRot()),walkSpeed=bot.walkAnimation.speed(partial),walkPos=bot.walkAnimation.position(partial),age=bot.tickCount+partial;
        model.attackTime=0;model.riding=bot.isPassenger();model.young=false;model.crouching=bot.isCrouching();model.prepareMobModel(bot,walkPos,walkSpeed,partial);model.setupAnim(bot,walkPos,walkSpeed,age,headYaw,headPitch);
        PoseStack pose=event.getPoseStack();pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(180-bodyYaw));pose.scale(-1,-1,1);pose.translate(0,-1.501,0);
        VertexConsumer skinBuffer=event.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(SKIN));model.renderToBuffer(pose,skinBuffer,event.getPackedLight(),OverlayTexture.NO_OVERLAY,1,1,1,1);
        ItemStack held=bot.getMainHandItem();if(!held.isEmpty()){pose.pushPose();model.rightArm.translateAndRotate(pose);pose.mulPose(Axis.XP.rotationDegrees(-90));pose.mulPose(Axis.YP.rotationDegrees(180));pose.translate(0,.125,-.625);ItemRenderer items=mc.getItemRenderer();items.renderStatic(held,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,event.getPackedLight(),OverlayTexture.NO_OVERLAY,pose,event.getMultiBufferSource(),bot.level(),bot.getId());pose.popPose();}pose.popPose();
        pose.pushPose();pose.translate(0,bot.getBbHeight()+.55,0);pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());pose.scale(-.025f,-.025f,.025f);var font=mc.font;float x=-font.width(NAME)/2f;font.drawInBatch(NAME,x,0,0xFFFF8AD8,false,pose.last().pose(),event.getMultiBufferSource(),net.minecraft.client.gui.Font.DisplayMode.NORMAL,0x50000000,event.getPackedLight());pose.popPose();
    }
}
