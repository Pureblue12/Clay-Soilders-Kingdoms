package net.bumblebee.claysoldiers.entity.door;

import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

/**
 * The ONE place that decides whether a clay soldier may open a door. Physics come first and are not decided here:
 * only wooden doors can be opened at all (iron doors need redstone and stay closed to everyone).
 * <p>
 * Today every clay soldier may open every wooden door: doors have no owner and territory doesn't exist yet.
 * FUTURE (war milestone): restrict this by team or territory, e.g. only doors of the soldier's own kingdom. When it
 * starts saying no, also let the pathfinder treat refused doors as walls (today pathfinding plans through every
 * wooden door, and {@link UseDoorGoal} gives up at a refused door).
 */
public final class DoorAccess {
    private static final BiPredicate<AbstractClaySoldierEntity, BlockPos> EVERYONE = (soldier, doorPos) -> true;
    private static BiPredicate<AbstractClaySoldierEntity, BlockPos> rule = EVERYONE;

    private DoorAccess() {
    }

    /**
     * Returns whether {@code soldier} may open the (wooden) door at {@code doorPos} (its lower half).
     */
    public static boolean canUse(AbstractClaySoldierEntity soldier, BlockPos doorPos) {
        return rule.test(soldier, doorPos);
    }

    /**
     * Why {@link #canUse} said no, for the debug recorder.
     */
    public static String whyNot(AbstractClaySoldierEntity soldier, BlockPos doorPos) {
        return rule == EVERYONE ? "allowed" : "not allowed for this soldier (DoorAccess rule)";
    }

    /**
     * GameTests only: replaces the rule, {@code null} restores the default (everyone may open every wooden door).
     */
    public static void overrideRuleForTests(@Nullable BiPredicate<AbstractClaySoldierEntity, BlockPos> testRule) {
        rule = testRule == null ? EVERYONE : testRule;
    }
}
