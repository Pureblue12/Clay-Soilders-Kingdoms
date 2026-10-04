package net.bumblebee.claysoldiers.event;

import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Connects kingdoms to the game (all installs, not only dev): a king's death ruins its kingdom, the census runs on
 * server ticks, and sneak + right-clicking your own king with a gold block founds a kingdom.
 */
public final class KingdomEvents {
    private KingdomEvents() {
    }

    /**
     * Registered with the lowest priority, and only for deaths no other mod cancelled.
     */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof AbstractClaySoldierEntity soldier) {
            Kingdoms.onSoldierDeath(soldier, event.getSource());
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        Kingdoms.tick(event.getServer());
    }

    /**
     * The in-game founding trigger: sneak + right-click a king of a team loyal to you while holding a gold block. The
     * block is not used up. Anything else (not sneaking, another item, not a king) is left to the normal interaction.
     */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof AbstractClaySoldierEntity soldier) || !event.getEntity().isShiftKeyDown()
                || !event.getItemStack().is(Items.GOLD_BLOCK) || !Kingdoms.isKing(soldier)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Kingdoms.Result result = Kingdoms.found(level, soldier, player);
        if (result.ok()) {
            player.sendSystemMessage(Component.literal("You founded the kingdom of " + Kingdoms.teamName(level.getServer(), soldier.getClayTeamType())
                    + ". See it with /csr kingdom book; undo with /csr kingdom disband.").withStyle(ChatFormatting.GOLD));
        } else {
            player.sendSystemMessage(Component.literal("No kingdom founded: " + result.refusal()).withStyle(ChatFormatting.RED));
        }
    }
}
