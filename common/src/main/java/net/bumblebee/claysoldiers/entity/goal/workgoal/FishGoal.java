package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.door.DoorPathing;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KingdomEconomy;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * A fisher of a kingdom's fishing dock ({@code KingdomDock}): walks out onto the jetty to a free spot with open water
 * beside it, casts (a splash where the line lands), waits {@code fishing.wait_min_ticks} to {@code fishing.wait_max_ticks}
 * for a bite and catches a cod or a salmon ({@code fishing.salmon_percent}); after {@code fishing.carry} fish (or a catch of
 * the other kind) it brings them to the dock's chest, into the stockpile, where they count as food
 * ({@code fishing.fish_is_food}).
 */
public class FishGoal extends AbstractWorkGoal {
    public static final String FISHING_LANG = JOB_LANG_KEY.formatted(ClaySoldiersCommon.MOD_ID, "fishing");
    private static final int REPATH_TICKS = 20;
    private static final int SKIP_TICKS = 2400;
    private static final int WORKPLACE_TICKS = 100;
    /** How far out from the spot the line lands. */
    private static final int CAST_REACH = 2;
    /** Spots taken by fishers, so two don't stand on one (memory only). */
    private static final Map<BlockPos, UUID> TAKEN = new HashMap<>();

    private enum Step { FIND, WALK, FISH, DELIVER }

    private Step step = Step.FIND;
    /** The deck block the fisher stands on. */
    @Nullable
    private BlockPos spot;
    /** The water the line lands in. */
    @Nullable
    private BlockPos line;
    private int walked;
    private int waited;
    private int bite;
    private ItemStack pending = ItemStack.EMPTY;
    private final Map<BlockPos, Long> skipped = new HashMap<>();
    @Nullable
    private KingdomEconomy.Workplace workplace;
    private int workplaceChecked = -WORKPLACE_TICKS;

    public FishGoal(AbstractClaySoldierEntity soldier, Supplier<WorkSelectorGoal> workSelector) {
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
        step = Step.FIND;
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
            case FISH -> fish(level);
            case DELIVER -> deliver(place);
        }
    }

    private void release() {
        if (spot != null) {
            TAKEN.remove(spot, soldier.getUUID());
        }
        spot = null;
        line = null;
    }

    /**
     * Where a fisher can stand on the dock: a deck block with room above it and open water beside it, whose line lands in
     * water {@link #CAST_REACH} out; the water side, or empty.
     */
    @Nullable
    public static Direction waterSide(ServerLevel level, BlockPos deck) {
        if (!level.getBlockState(deck).isFaceSturdy(level, deck, Direction.UP) || !level.getBlockState(deck.above()).getCollisionShape(level, deck.above()).isEmpty()
                || !level.getBlockState(deck.above(2)).getCollisionShape(level, deck.above(2)).isEmpty()) {
            return null;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos beside = deck.relative(side);
            if (level.getBlockState(beside).isAir() && level.getFluidState(beside.below()).is(FluidTags.WATER)
                    && level.getFluidState(deck.relative(side, CAST_REACH).below()).is(FluidTags.WATER)) {
                return side;
            }
        }
        return null;
    }

    private void find(ServerLevel level, KingdomEconomy.Workplace place) {
        setStatus(SEARCHING_ID);
        release();
        if (soldier.getCarriedStack().getCount() >= Tuning.FISHING_CARRY.get() || !pending.isEmpty()) {
            step = Step.DELIVER;
            walked = 0;
            return;
        }
        long now = level.getGameTime();
        skipped.values().removeIf(until -> until <= now);
        TAKEN.entrySet().removeIf(entry -> !(level.getEntity(entry.getValue()) instanceof AbstractClaySoldierEntity other) || !other.isAlive());
        List<BlockPos> spots = new ArrayList<>();
        BoundingBox bounds = place.bounds();
        // The deck is the dock's lowest layer (one above the water).
        int y = bounds.minY();
        for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), y, bounds.minZ(), bounds.maxX(), y, bounds.maxZ())) {
            if (!skipped.containsKey(pos) && !TAKEN.containsKey(pos) && waterSide(level, pos) != null) {
                spots.add(pos.immutable());
            }
        }
        if (spots.isEmpty()) {
            if (!soldier.getCarriedStack().isEmpty()) {
                step = Step.DELIVER;
                walked = 0;
            } else {
                DebugLog.event(DebugCategory.ECONOMY, soldier, "fisher: no free spot on the dock, taking a break");
                takeAShortBreak();
            }
            return;
        }
        spot = spots.get(soldier.getRandom().nextInt(spots.size()));
        TAKEN.put(spot, soldier.getUUID());
        step = Step.WALK;
        walked = 0;
    }

    private void walk(ServerLevel level) {
        if (spot == null) {
            step = Step.FIND;
            return;
        }
        double dx = soldier.getX() - (spot.getX() + 0.5);
        double dz = soldier.getZ() - (spot.getZ() + 0.5);
        if (dx * dx + dz * dz <= 0.8 * 0.8 && Math.abs(soldier.getY() - (spot.getY() + 1)) <= 0.6) {
            soldier.getNavigation().stop();
            Direction side = waterSide(level, spot);
            if (side == null) {
                step = Step.FIND;
                return;
            }
            line = spot.relative(side, CAST_REACH).below();
            cast(level);
            return;
        }
        if (walked++ > Tuning.ECONOMY_LUMBER_WALK_TICKS.get()) {
            skipped.put(spot, level.getGameTime() + SKIP_TICKS);
            BlockPos lost = spot;
            DebugLog.event(DebugCategory.ECONOMY, soldier, "fisher: gave up the spot at %s, no way there", lost.toShortString());
            step = Step.FIND;
            return;
        }
        if (walked % REPATH_TICKS == 1) {
            soldier.getNavigation().moveTo(spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5, 1.0);
        }
    }

    private void cast(ServerLevel level) {
        int min = Tuning.FISHING_WAIT_MIN_TICKS.get();
        int max = Math.max(min, Tuning.FISHING_WAIT_MAX_TICKS.get());
        bite = min + soldier.getRandom().nextInt(max - min + 1);
        waited = 0;
        step = Step.FISH;
        soldier.swing(InteractionHand.MAIN_HAND);
        if (line != null) {
            level.playSound(null, line, SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 1.0F);
            level.sendParticles(ParticleTypes.SPLASH, line.getX() + 0.5, line.getY() + 1.0, line.getZ() + 0.5, 6, 0.15, 0, 0.15, 0);
        }
        BlockPos at = spot;
        DebugLog.event(DebugCategory.ECONOMY, soldier, () -> "fisher: cast from %s, a bite in %d ticks".formatted(at == null ? "?" : at.toShortString(), bite));
    }

    private void fish(ServerLevel level) {
        setStatus(SEARCHING_ID);
        if (spot == null || line == null || !level.getFluidState(line).is(FluidTags.WATER)) {
            step = Step.FIND;
            return;
        }
        // Pushed off the spot: walk back first.
        if (soldier.distanceToSqr(spot.getX() + 0.5, spot.getY() + 1, spot.getZ() + 0.5) > 1.5 * 1.5) {
            step = Step.WALK;
            walked = 0;
            return;
        }
        soldier.getLookControl().setLookAt(line.getX() + 0.5, line.getY() + 1, line.getZ() + 0.5);
        if (++waited % 40 == 0) {
            level.sendParticles(ParticleTypes.FISHING, line.getX() + 0.5, line.getY() + 1.0, line.getZ() + 0.5, 1, 0.05, 0, 0.05, 0);
        }
        if (waited < bite) {
            return;
        }
        Item fish = soldier.getRandom().nextInt(100) < Tuning.FISHING_SALMON_PERCENT.get() ? Items.SALMON : Items.COD;
        level.playSound(null, line, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.5F, 1.0F);
        level.sendParticles(ParticleTypes.SPLASH, line.getX() + 0.5, line.getY() + 1.0, line.getZ() + 0.5, 12, 0.2, 0, 0.2, 0);
        soldier.swing(InteractionHand.MAIN_HAND);
        ItemStack carried = soldier.getCarriedStack();
        if (carried.isEmpty()) {
            soldier.setCarriedStack(new ItemStack(fish));
        } else if (carried.is(fish) && carried.getCount() < carried.getMaxStackSize()) {
            carried.grow(1);
            soldier.setCarriedStack(carried);
        } else {
            pending = new ItemStack(fish);
        }
        DebugLog.event(DebugCategory.ECONOMY, soldier, "fisher: caught a %s", fish);
        if (soldier.getCarriedStack().getCount() >= Tuning.FISHING_CARRY.get() || !pending.isEmpty()) {
            release();
            step = Step.DELIVER;
            walked = 0;
        } else {
            cast(level);
        }
    }

    private void deliver(KingdomEconomy.Workplace place) {
        if (soldier.getCarriedStack().isEmpty()) {
            if (pending.isEmpty()) {
                step = Step.FIND;
                return;
            }
            soldier.setCarriedStack(pending);
            pending = ItemStack.EMPTY;
        }
        setStatus(CARRYING_ID);
        BlockPos chest = place.chest();
        if (chest.closerToCenterThan(soldier.position(), 2.5)) {
            soldier.getNavigation().stop();
            KingdomEconomy.deliver(soldier, place.kingdom(), soldier.getCarriedStack());
            soldier.setCarriedStack(ItemStack.EMPTY);
            if (!pending.isEmpty()) {
                KingdomEconomy.deliver(soldier, place.kingdom(), pending);
                pending = ItemStack.EMPTY;
            }
            step = Step.FIND;
            takeAShortBreak();
            return;
        }
        if (walked++ % REPATH_TICKS == 0) {
            soldier.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1.0);
        }
    }

    @Override
    public boolean workRequiresItemCarrying() {
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(FISHING_LANG);
    }
}
