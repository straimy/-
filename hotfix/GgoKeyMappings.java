package arena.client.shell;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** First-party GGO controls that should not depend on hard-coded GLFW polling. */
@Mod.EventBusSubscriber(value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class GgoKeyMappings {
    public static final String CATEGORY="key.categories.gungloryonline";
    public static final KeyMapping MEDICAL_WHEEL=new KeyMapping(
            "key.gungloryonline.medical_wheel", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);
    private GgoKeyMappings(){}

    @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(MEDICAL_WHEEL);}
}
