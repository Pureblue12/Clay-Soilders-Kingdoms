package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.door.DoorPathing;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KingdomEconomy;
import net.bumblebee.claysoldiers.kingdom.KingdomWorkshop;
import net.bumblebee.claysoldiers.kingdom.QuarryPit;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Stage 6b: a quarryman of a kingdom's quarry ({@code KingdomEconomy}). It digs the quarry's pit ({@link QuarryPit}) layer
 * by layer from the top, the nearest block of the highest layer not yet dug first, so the pit's stepped walls are never
 * more than one block high, and carries what it digs to the quarry's chest (the stockpile). Dirt is left behind.
 * <p>
 * It never digs a block next to water or lava (no flooding, no lava running into the pit), a block entity, or anything
 * that isn't mined with a pickaxe or shovel, and never a column in or beside the kingdom's buildings, on a planned plot,
 * road or the wall line, or in another kingdom's claim. A block it can't reach in {@code economy.lumber.walk_ticks} is left
 * alone for a while. Each search also tells the workshop whether the pit has any stone left ({@link KingdomWorkshop#setDry}).
 */
public class QuarryGoal extends AbstractWorkGoal {
    public static final String QUARRYING_LANG = JOB_LANG_KEY.formatted(ClaySoldiersCommon.MOD_ID, "quarrying");
    /** The gap between the quarry building and its pit. */
    public static final int PIT_GAP = 2;
    private static final int SKIP_TICKS = 6000;
    private static final int REPATH_TICKS = 20;
    private static final int SEARCH_PAUSE_TICKS = 100;
    private static final int WORKPLACE_TICKS = 100;
    private static final double REACH = 2.5;
    /** The blocks quarrymen are digging, so two never dig the same one. */
    private static final Set<BlockPos> CLAIMED = new HashSet<>();
    @Nullable
    private static Integer pitSizeForTests;

    private enum Step { FIND, WALK, DIG, DELIVER }

    private Step step = Step.FIND;
    @Nullable
    private BlockPos target;
    private int walked;
    private int digTimer;
    private int nextSearch;
    private boolean saidNothing;
    private final Map<BlockPos, Long> skipped = new HashMap<>();
    @Nullable
    private KingdomEconomy.Workplace workplace;
    private int workplaceChecked = -WORKPLACE_TICKS;

    public QuarryGoal(AbstractClaySoldierEntity soldier, Supplier<WorkSelectorGoal> workSelector) {
        super(soldier, workSelector);
    }

    /** GameTests only: the size of every quarry's pit ({@code null}: {@code economy.quarry.pit_size} again). */
    public static void setPitSizeForTests(@Nullable Integer size) {
        pitSizeForTests = size;
    }

    /** The pit of the quarry a quarryman works at. */
    public static QuarryPit pit(KingdomEconomy.Workplace place) {
        int size = pitSizeForTests != null ? pitSizeForTests : Tuning.ECONOMY_QUARRY_PIT_SIZE.get();
        return QuarryPit.behind(place.bounds(), place.building().facing(), place.building().pos().getY() - 1, size, PIT_GAP,
                Tuning.ECONOMY_QUARRY_MAX_DEPTH.get());
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
        step = soldier.getCarriedStack().isEmpty() ? Step.FIND : Step.DELIVER;
        walked = 0;
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
        release();
        step = Step.FIND;
    }

    private void release() {
        if (target != null) {
            CLAIMED.remove(target);
            target = null;
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
        switch (step) {
            case FIND -> find(level, place);
            case WALK -> walk(level);
            case DIG -> dig(level, place);
            case DELIVER -> deliver(place);
        }
    }

    private void find(ServerLevel level, KingdomEconomy.Workplace place) {
        setStatus(SEARCHING_ID);
        if (soldier.tickCount < nextSearch) {
            return;
        }
        release();
        target = findBlock(level, place);
        if (target == null) {
            nextSearch = soldier.tickCount + SEARCH_PAUSE_TICKS;
            if (!saidNothing) {
                saidNothing = true;
                DebugLog.event(DebugCategory.ECONOMY, soldier, "quarryman: nothing left it may dig in the pit");
            }
            if (!soldier.getCarriedStack().isEmpty()) {
                step = Step.DELIVER;
            } else {
                takeAShortBreak();
            }
            return;
        }
        saidNothing = false;
        CLAIMED.add(target);
        step = Step.WALK;
        walked = 0;
    }

    private boolean inReach(BlockPos pos) {
        double dx = soldier.getX() - (pos.getX() + 0.5);
        double dz = soldier.getZ() - (pos.getZ() + 0.5);
        int feet = soldier.getBlockY();
        return dx * dx + dz * dz <= REACH * REACH && pos.getY() >= feet - 3 && pos.getY() <= feet + 1;
    }

    private void walk(ServerLevel level) {
        if (target == null || !diggable(level, target)) {
            step = Step.FIND;
            return;
        }
        if (inReach(target)) {
            soldier.getNavigation().stop();
            step = Step.DIG;
            digTimer = 0;
            return;
        }
        if (walked++ > Tuning.ECONOMY_LUMBER_WALK_TICKS.get()) {
            skipped.put(target, level.getGameTime() + SKIP_TICKS);
            BlockPos lost = target;
            DebugLog.event(DebugCategory.ECONOMY, soldier, "quarryman: gave up the block at %s, no way there", lost.toShortString());
            release();
            step = Step.FIND;
            return;
        }
        if (walked % REPATH_TICKS == 1) {
            soldier.getNavigation().moveTo(target.getX() + 0.5, target.getY() + 1, target.getZ() + 0.5, 1.0);
        }
    }

    private void dig(ServerLevel level, KingdomEconomy.Workplace place) {
        if (target == null || !diggable(level, target)) {
            step = Step.FIND;
            return;
        }
        soldier.getLookControl().setLookAt(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        if (++digTimer < Tuning.ECONOMY_QUARRY_DIG_TICKS.get()) {
            if (digTimer % 6 == 0) {
                soldier.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }
        digTimer = 0;
        BlockState state = level.getBlockState(target);
        List<ItemStack> drops = Block.getDrops(state, level, target, null, soldier, new ItemStack(Items.IRON_PICKAXE));
        ItemStack drop = drops.stream().filter(stack -> !stack.isEmpty() && !stack.is(ItemTags.DIRT)).findFirst().orElse(ItemStack.EMPTY);
        ItemStack carried = soldier.getCarriedStack();
        if (!drop.isEmpty() && !carried.isEmpty() && (!ItemStack.isSameItemSameComponents(carried, drop)
                || carried.getCount() + drop.getCount() > Math.min(carried.getMaxStackSize(), Tuning.ECONOMY_QUARRY_CARRY.get()))) {
            // A different kind, or a full load: bring this lot first, the block waits.
            step = Step.DELIVER;
            return;
        }
        BlockState above = level.getBlockState(target.above());
        if (!above.isAir() && above.canBeReplaced() && above.getFluidState().isEmpty()) {
            level.destroyBlock(target.above(), false, soldier);
        }
        level.destroyBlock(target, false, soldier);
        if (!drop.isEmpty()) {
            soldier.setCarriedStack(carried.isEmpty() ? drop.copy() : carried.copyWithCount(carried.getCount() + drop.getCount()));
        }
        release();
        ItemStack load = soldier.getCarriedStack();
        step = load.getCount() >= Math.min(load.getMaxStackSize(), Tuning.ECONOMY_QUARRY_CARRY.get()) ? Step.DELIVER : Step.FIND;
        walked = 0;
        nextSearch = 0;
    }

    private void deliver(KingdomEconomy.Workplace place) {
        if (soldier.getCarriedStack().isEmpty()) {
            step = Step.FIND;
            return;
        }
        setStatus(CARRYING_ID);
        BlockPos chest = place.chest();
        if (chest.closerToCenterThan(soldier.position(), 2.5)) {
            soldier.getNavigation().stop();
            KingdomEconomy.deliver(soldier, place.kingdom(), soldier.getCarriedStack());
            soldier.setCarriedStack(ItemStack.EMPTY);
            step = Step.FIND;
            takeAShortBreak();
            return;
        }
        if (walked++ % REPATH_TICKS == 0) {
            soldier.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1.0);
        }
    }

    /**
     * The block to dig next: the nearest diggable block of the highest layer that still has one. Also tells the workshop
     * whether the pit has stone left.
     */
    @Nullable
    private BlockPos findBlock(ServerLevel level, KingdomEconomy.Workplace place) {
        QuarryPit pit = pit(place);
        long now = level.getGameTime();
        skipped.values().removeIf(until -> until <= now);
        BlockPos found = null;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int layer = 1; layer <= pit.depth() && found == null; layer++) {
            int y = pit.y(layer);
            double best = Double.MAX_VALUE;
            for (int x = pit.minX(); x <= pit.maxX(); x++) {
                for (int z = pit.minZ(); z <= pit.maxZ(); z++) {
                    if (!pit.inLayer(x, z, layer) || !level.isLoaded(pos.set(x, y, z))) {
                        continue;
                    }
                    BlockPos candidate = pos.immutable();
                    double distance = soldier.distanceToSqr(x + 0.5, y, z + 0.5);
                    if (distance < best && !skipped.containsKey(candidate) && !CLAIMED.contains(candidate) && diggable(level, candidate)
                            && KingdomWorkshop.pitFree(level, place.kingdom(), x, z, y)) {
                        best = distance;
                        found = candidate;
                    }
                }
            }
        }
        KingdomWorkshop.setDry(place.kingdom(), !hasStone(level, place, pit));
        return found;
    }

    /** Whether any block of the pit still to dig is stone or cobblestone (what gives cobblestone). */
    private static boolean hasStone(ServerLevel level, KingdomEconomy.Workplace place, QuarryPit pit) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int layer = 1; layer <= pit.depth(); layer++) {
            int y = pit.y(layer);
            for (int x = pit.minX(); x <= pit.maxX(); x++) {
                for (int z = pit.minZ(); z <= pit.maxZ(); z++) {
                    if (!pit.inLayer(x, z, layer) || !level.isLoaded(pos.set(x, y, z))) {
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if ((state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE)) && diggable(level, pos.immutable())
                            && KingdomWorkshop.pitFree(level, place.kingdom(), x, z, y)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Whether a quarryman may dig the block at {@code pos}: mined with a pickaxe or shovel, not air or a fluid, not a block
     * entity, not unbreakable, and with no water or lava beside or above it.
     */
    static boolean diggable(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0
                || !(state.is(BlockTags.MINEABLE_WITH_PICKAXE) || state.is(BlockTags.MINEABLE_WITH_SHOVEL))) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (direction != Direction.DOWN && !level.getFluidState(pos.relative(direction)).isEmpty()) {
                return false;
            }
        }
        return level.getFluidState(pos.below()).isEmpty();
    }

    @Override
    public boolean workRequiresItemCarrying() {
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(QUARRYING_LANG);
    }
}
