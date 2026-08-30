package arena.forge;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps explicit /admin 1 and /admin 2 maintenance modes authoritative.
 * OP permission alone is never enough: the explicit GGO admin-mode tag is required.
 */
@Mod.EventBusSubscriber(modid="gunnerarena",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class AdminModeGuard {
    private AdminModeGuard(){}

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||e.player.level().isClientSide||!(e.player instanceof ServerPlayer p)||!p.hasPermissions(2))return;

        boolean build=p.getTags().contains(AdminToolsCommands.ADMIN_BUILD_TAG);
        boolean spectator=p.getTags().contains(AdminToolsCommands.ADMIN_SPECTATOR_TAG);
        if(!build&&!spectator)return;

        if(build&&p.gameMode.getGameModeForPlayer()!=GameType.CREATIVE)p.setGameMode(GameType.CREATIVE);
        if(spectator&&p.gameMode.getGameModeForPlayer()!=GameType.SPECTATOR)p.setGameMode(GameType.SPECTATOR);

        // Explicit admin maintenance modes are intentionally immortal. This also protects
        // against modded/environmental damage sources that do not respect creative mode.
        p.setHealth(p.getMaxHealth());
        p.clearFire();
        p.fallDistance=0.0F;
        p.setAirSupply(p.getMaxAirSupply());

        if(build){
            Abilities a=p.getAbilities();
            boolean changed=!a.invulnerable||!a.mayfly||!a.instabuild;
            a.invulnerable=true;
            a.mayfly=true;
            a.instabuild=true;
            if(changed)p.onUpdateAbilities();
        }
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent e){
        if(isImmortalAdmin(e.getEntity()))e.setCanceled(true);
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void hurt(LivingHurtEvent e){
        if(isImmortalAdmin(e.getEntity()))e.setCanceled(true);
    }

    private static boolean isImmortalAdmin(Object entity){
        if(!(entity instanceof ServerPlayer p)||!p.hasPermissions(2))return false;
        return p.getTags().contains(AdminToolsCommands.ADMIN_BUILD_TAG)
            ||p.getTags().contains(AdminToolsCommands.ADMIN_SPECTATOR_TAG);
    }
}
