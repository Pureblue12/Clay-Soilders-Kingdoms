package net.bumblebee.claysoldiers.event;

import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KingdomBook;
import net.bumblebee.claysoldiers.kingdom.KingdomKeep;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Connects kingdoms to the game (all installs, not only dev): a king's death ruins its kingdom, the census and the
 * keep (stage 4) run on server ticks, and a kingdom book is brought up to date when it is opened. (A king founds his
 * kingdom by himself: {@code KingFoundingGoal}.)
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
        KingdomKeep.tick(event.getServer());
    }

    /**
     * A kingdom book opened from the hand is rewritten first. The client opens the book it has in its hand, so the new
     * pages are sent before vanilla sends "open the book".
     */
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && KingdomBook.refresh(player.server, event.getItemStack())) {
            player.containerMenu.broadcastChanges();
        }
    }

    /**
     * A kingdom book on a lectern is rewritten before the lectern opens (only when its pages changed, because
     * replacing the book sends the lectern back to page 1).
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof LecternBlockEntity lectern)) {
            return;
        }
        ItemStack book = lectern.getBook().copy();
        if (KingdomBook.refresh(player.server, book)) {
            lectern.setBook(book);
        }
    }
}
