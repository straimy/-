package arena.client.ui;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.IForgeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.List;

/**
 * JEG 0.13.2 streams ForgeRegistries.ITEMS.getValues() directly during a resource reload.
 * Disconnecting from a server unloads the server resource pack and can overlap registry sync,
 * causing ConcurrentModificationException. Return a stable snapshot instead of the live view.
 */
@Pseudo
@Mixin(targets = "ttv.migami.jeg.client.MetaLoader", remap = false)
public abstract class JegMetaLoaderMixin {
    @Redirect(
        method = "getResourceSuppliers",
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/registries/IForgeRegistry;getValues()Ljava/util/Collection;"),
        remap = false,
        require = 0
    )
    private Collection<Item> ggo$snapshotRegistry(IForgeRegistry<Item> registry) {
        for (int attempt = 0; attempt < 8; attempt++) {
            try {
                return List.copyOf(registry.getValues());
            } catch (ConcurrentModificationException ignored) {
                Thread.yield();
            }
        }
        // A temporary empty metadata pass is safer than crashing the whole client on disconnect.
        try { return new ArrayList<>(registry.getValues()); }
        catch (ConcurrentModificationException ignored) { return List.of(); }
    }
}
