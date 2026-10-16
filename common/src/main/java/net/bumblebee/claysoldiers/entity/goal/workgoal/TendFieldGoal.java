package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.door.DoorPathing;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KingdomEconomy;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Stage 5: a farmer of a kingdom's field ({@code KingdomEconomy}). The field's structure is fences and props around bare
 * dirt (D55: builders can't place farmland); the farmer works it, in this order of need:
 * <ol>
 *     <li>harvests a ripe crop and replants it at once with a seed from the harvest;</li>
 *     <li>tills dirt or grass in the field and sows it (the seed comes with the ground, like vanilla grass drops seeds);</li>
 *     <li>brings the harvest to the field's chest (into the stockpile) once it carries {@code economy.farm.carry}, or has
 *     nothing else to do;</li>
 *     <li>tends a growing crop: standing at it for {@code economy.farm.tend_ticks} makes it grow one stage (the field has no
 *     water; without tending a field takes most of an hour).</li>
 * </ol>
 * Clay soldiers are too light to trample farmland.
 */
public class TendFieldGoal extends AbstractWorkGoal {
    public static final String FARMING_LANG = JOB_LANG_KEY.formatted(ClaySoldiersCommon.MOD_ID, "farming");
    private static final int REPATH_TICKS = 20;
    private static final int WORK_TICKS = 20;
    private static final int SKIP_TICKS = 2400;
    private static final int WORKPLACE_TICKS = 100;

    private enum Step { FIND, WALK, WORK, DELIVER }

    private enum Task { HARVEST, TILL, TEND }

    private Step step = Step.FIND;
    @Nullable
    private BlockPos cell;
    @Nullable
    private Task task;
    private int walked;
    private int workTimer;
    private final Map<BlockPos, Long> skipped = new HashMap<>();
    @Nullable
    private KingdomEconomy.Workplace workplace;
    private int workplaceChecked = Integer.MIN_VALUE;

    public TendFieldGoal(AbstractClaySoldierEntity soldier, Supplier<WorkSelectorGoal> workSelector) {
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
        cell = null;
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
            case WORK -> work(level, place);
            case DELIVER -> deliver(place);
        }
    }

    /** The crop a field grows: by its structure (carrot and potato fields), wheat otherwise. */
    public static Block cropFor(ResourceLocation structure) {
        String path = structure.getPath();
        if (path.contains("carrot")) {
            return Blocks.CARROTS;
        }
        if (path.contains("potato")) {
            return Blocks.POTATOES;
        }
        return Blocks.WHEAT;
    }

    private void find(ServerLevel level, KingdomEconomy.Workplace place) {
        setStatus(SEARCHING_ID);
        ItemStack carried = soldier.getCarriedStack();
        if (carried.getCount() >= Tuning.ECONOMY_FARM_CARRY.get()) {
            step = Step.DELIVER;
            walked = 0;
            return;
        }
        long now = level.getGameTime();
        skipped.values().removeIf(until -> until <= now);
        List<BlockPos> ripe = new ArrayList<>();
        List<BlockPos> bare = new ArrayList<>();
        List<BlockPos> growing = new ArrayList<>();
        BoundingBox bounds = place.bounds();
        for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            if (skipped.containsKey(pos)) {
                continue;
            }
            BlockState ground = level.getBlockState(pos);
            BlockState above = level.getBlockState(pos.above());
            if (ground.is(Blocks.FARMLAND) && above.getBlock() instanceof CropBlock crop) {
                (crop.isMaxAge(above) ? ripe : growing).add(pos.immutable());
            } else if ((ground.is(Blocks.DIRT) || ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.FARMLAND)) && above.isAir()) {
                bare.add(pos.immutable());
            }
        }
        if (!ripe.isEmpty() && (carried.isEmpty() || carried.getCount() < carried.getMaxStackSize())) {
            target(nearest(ripe), Task.HARVEST);
        } else if (!bare.isEmpty()) {
            target(nearest(bare), Task.TILL);
        } else if (!carried.isEmpty()) {
            step = Step.DELIVER;
            walked = 0;
        } else if (!growing.isEmpty()) {
            target(growing.get(soldier.getRandom().nextInt(growing.size())), Task.TEND);
        } else {
            takeAShortBreak();
        }
    }

    private BlockPos nearest(List<BlockPos> cells) {
        BlockPos best = cells.getFirst();
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : cells) {
            double distance = soldier.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
            if (distance < bestDistance) {
                best = pos;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void target(BlockPos pos, Task what) {
        cell = pos;
        task = what;
        step = Step.WALK;
        walked = 0;
    }

    private void walk(ServerLevel level) {
        if (cell == null) {
            step = Step.FIND;
            return;
        }
        double dx = soldier.getX() - (cell.getX() + 0.5);
        double dz = soldier.getZ() - (cell.getZ() + 0.5);
        if (dx * dx + dz * dz <= 2.0 * 2.0 && Math.abs(soldier.getY() - (cell.getY() + 1)) <= 2) {
            soldier.getNavigation().stop();
            step = Step.WORK;
            workTimer = 0;
            return;
        }
        if (walked++ > Tuning.ECONOMY_LUMBER_WALK_TICKS.get()) {
            skipped.put(cell, level.getGameTime() + SKIP_TICKS);
            BlockPos lost = cell;
            DebugLog.event(DebugCategory.ECONOMY, soldier, "farmer: gave up the cell at %s, no way there", lost.toShortString());
            cell = null;
            step = Step.FIND;
            return;
        }
        if (walked % REPATH_TICKS == 1) {
            soldier.getNavigation().moveTo(cell.getX() + 0.5, cell.getY() + 1, cell.getZ() + 0.5, 1.0);
        }
    }

    private void work(ServerLevel level, KingdomEconomy.Workplace place) {
        if (cell == null || task == null) {
            step = Step.FIND;
            return;
        }
        soldier.getLookControl().setLookAt(cell.getX() + 0.5, cell.getY() + 1, cell.getZ() + 0.5);
        int needed = task == Task.TEND ? Tuning.ECONOMY_FARM_TEND_TICKS.get() : WORK_TICKS;
        if (++workTimer < needed) {
            if (workTimer % 8 == 0) {
                soldier.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }
        BlockPos above = cell.above();
        BlockState ground = level.getBlockState(cell);
        BlockState plant = level.getBlockState(above);
        switch (task) {
            case HARVEST -> {
                if (plant.getBlock() instanceof CropBlock crop && crop.isMaxAge(plant)) {
                    harvest(level, place, above, plant, crop);
                }
            }
            case TILL -> {
                if ((ground.is(Blocks.DIRT) || ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.FARMLAND)) && plant.isAir()) {
                    level.setBlockAndUpdate(cell, Blocks.FARMLAND.defaultBlockState());
                    level.playSound(null, cell, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    Block crop = cropFor(place.building().structure());
                    BlockState sown = crop instanceof CropBlock cropBlock ? cropBlock.getStateForAge(0) : crop.defaultBlockState();
                    if (sown.canSurvive(level, above)) {
                        level.setBlockAndUpdate(above, sown);
                    }
                    BlockPos tilled = cell;
                    DebugLog.event(DebugCategory.ECONOMY, soldier, "farmer: tilled and sowed %s", tilled.toShortString());
                }
            }
            case TEND -> {
                if (plant.getBlock() instanceof CropBlock crop && !crop.isMaxAge(plant)) {
                    level.setBlockAndUpdate(above, crop.getStateForAge(crop.getAge(plant) + 1));
                    level.levelEvent(1505, above, 0);
                }
            }
        }
        cell = null;
        task = null;
        step = Step.FIND;
    }

    private void harvest(ServerLevel level, KingdomEconomy.Workplace place, BlockPos pos, BlockState plant, CropBlock crop) {
        Item seed = crop.asItem();
        List<ItemStack> drops = Block.getDrops(plant, level, pos, null);
        Item product = null;
        int count = 0;
        int seeds = 0;
        for (ItemStack drop : drops) {
            if (drop.is(seed)) {
                seeds += drop.getCount();
            } else if (product == null || drop.is(product)) {
                product = drop.getItem();
                count += drop.getCount();
            }
        }
        if (product == null) {
            // Carrots and potatoes: the crop is its own seed; one is kept to replant.
            product = seed;
            count = Math.max(0, seeds - 1);
        }
        ItemStack carried = soldier.getCarriedStack();
        if (!carried.isEmpty() && !carried.is(product)) {
            step = Step.DELIVER;
            walked = 0;
            return;
        }
        level.levelEvent(2001, pos, Block.getId(plant));
        level.setBlockAndUpdate(pos, crop.getStateForAge(0));
        if (count > 0) {
            int total = Math.min(carried.isEmpty() ? count : carried.getCount() + count, new ItemStack(product).getMaxStackSize());
            soldier.setCarriedStack(new ItemStack(product, total));
        }
        int got = count;
        Item what = product;
        DebugLog.event(DebugCategory.ECONOMY, soldier, () -> "farmer: harvested %d %s at %s and replanted".formatted(got, what, pos.toShortString()));
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

    @Override
    public boolean workRequiresItemCarrying() {
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(FARMING_LANG);
    }
}
