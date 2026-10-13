package net.bumblebee.claysoldiers.event;

import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KingStone;
import net.bumblebee.claysoldiers.kingdom.KingdomBook;
import net.bumblebee.claysoldiers.kingdom.KingdomKeep;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Connects kingdoms to the game (all installs, not only dev): a king's death ruins its kingdom, the census and the
 * keep (stage 4) run on server ticks, sneak + right-clicking your own king with a gold block sends him to found a kingdom, and a kingdom book is brought
 * up to date when it is opened.
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
     * The in-game founding trigger (D86): sneak + right-click a king of a team loyal to you while holding a gold block.
     * He takes one block, picks a spot himself, walks there and sets it down, which founds the kingdom
     * ({@link KingStone}, {@code KingStoneGoal}). Anything else (not sneaking, another item, not a soldier) is left to
     * the normal interaction.
     */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof AbstractClaySoldierEntity soldier) || !event.getEntity().isShiftKeyDown()
                || !event.getItemStack().is(Items.GOLD_BLOCK)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        KingStone.Given given = KingStone.give(level, soldier, player, event.getItemStack());
        player.sendSystemMessage(Component.literal(given.message()).withStyle(given.ok() ? ChatFormatting.GOLD : ChatFormatting.RED));
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
