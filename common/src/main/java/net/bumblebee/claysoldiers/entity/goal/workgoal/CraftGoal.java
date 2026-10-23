package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.door.DoorPathing;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KingdomEconomy;
import net.bumblebee.claysoldiers.kingdom.KingdomWorkshop;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Stage 6b: a crafter of a kingdom's workshop ({@link KingdomWorkshop}). While the workshop's order board has an order the
 * stockpile has the raw materials for, it walks to the workshop's chest and works there for
 * {@code economy.workshop.craft_ticks} (shorter at higher workshop levels), then makes one batch: the raw materials leave
 * the stockpile and what it made goes in. With nothing to make it takes short breaks.
 */
public class CraftGoal extends AbstractWorkGoal {
    public static final String CRAFTING_LANG = JOB_LANG_KEY.formatted(ClaySoldiersCommon.MOD_ID, "crafting");
    private static final int REPATH_TICKS = 20;
    private static final int WORKPLACE_TICKS = 100;

    @Nullable
    private KingdomEconomy.Workplace workplace;
    private int workplaceChecked = -WORKPLACE_TICKS;
    private int walked;
    private int workTimer;
    @Nullable
    private String saidWaiting;

    public CraftGoal(AbstractClaySoldierEntity soldier, Supplier<WorkSelectorGoal> workSelector) {
        super(soldier, workSelector);
    }

    @Nullable
    private KingdomEconomy.Workplace workplace() {
        if (soldier.tickCount - workplaceChecked >= WORKPLACE_TICKS || workplace != null && !workplace.chest().equals(soldier.getPoiPos())) {
            workplaceChecked = soldier.tickCount;
            workplace = KingdomEconomy.workplace(soldier).orElse(null);
        }
        return workplace;
    }

    @Override
    public boolean canUse() {
        if (isOnBreak()) {
            return false;
        }
        return workplace() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        walked = 0;
        workTimer = 0;
        if (soldier.getNavigation() instanceof DoorPathing.Ground ground) {
            ground.setRangeBonus(Tuning.BUILD_PATH_RANGE_BONUS.get());
        }
    }

    @Override
    public void stop() {
        soldier.getNavigation().stop();
        if (soldier.getNavigation() instanceof DoorPathing.Ground ground) {
            ground.setRangeBonus(0);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        KingdomEconomy.Workplace place = workplace();
        if (place == null || !(soldier.level() instanceof ServerLevel level)) {
            return;
        }
        if (KingdomWorkshop.orders(place.kingdom()).isEmpty()) {
            setStatus(SEARCHING_ID);
            takeAShortBreak();
            return;
        }
        BlockPos chest = place.chest();
        if (!chest.closerToCenterThan(soldier.position(), 2.5)) {
            setStatus(RETURNING_ID);
            if (walked++ % REPATH_TICKS == 0) {
                soldier.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1.0);
            }
            return;
        }
        soldier.getNavigation().stop();
        soldier.getLookControl().setLookAt(chest.getX() + 0.5, chest.getY() + 0.5, chest.getZ() + 0.5);
        int level1 = Math.max(1, KingdomWorkshop.level(place.kingdom()));
        if (++workTimer < Math.max(1, Tuning.ECONOMY_WORKSHOP_CRAFT_TICKS.get() / level1)) {
            if (workTimer % 8 == 0) {
                soldier.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }
        workTimer = 0;
        walked = 0;
        Optional<KingdomWorkshop.Made> made = KingdomWorkshop.craft(level.getServer(), place.kingdom());
        if (made.isPresent()) {
            saidWaiting = null;
            KingdomWorkshop.Made what = made.get();
            DebugLog.event(DebugCategory.ECONOMY, soldier, "crafter: made %d %s", what.count(), what.item());
            return;
        }
        String why = KingdomWorkshop.shortOf(level.getServer(), place.kingdom()).orElse("nothing it can make now");
        if (!why.equals(saidWaiting)) {
            saidWaiting = why;
            DebugLog.event(DebugCategory.ECONOMY, soldier, "crafter: waiting for %s", why);
        }
        takeAShortBreak();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(CRAFTING_LANG);
    }
}
