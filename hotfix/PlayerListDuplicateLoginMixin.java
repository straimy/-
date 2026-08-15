package arena.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Allows the same Minecraft UUID to stay connected from more than one client.
 * Only the vanilla duplicate-login disconnect is suppressed; every other kick remains untouched.
 *
 * NOTE: Gunner Arena intentionally treats those clients as the same arena account/session.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListDuplicateLoginMixin {
    @Redirect(
        method = "placeNewPlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;disconnect(Lnet/minecraft/network/chat/Component;)V"
        ),
        require = 0
    )
    private void gunnerarena$allowDuplicateLogin(ServerGamePacketListenerImpl connection, Component reason) {
        if (reason != null && reason.getContents() instanceof TranslatableContents contents
            && "multiplayer.disconnect.duplicate_login".equals(contents.getKey())) {
            return;
        }
        connection.disconnect(reason);
    }
}
