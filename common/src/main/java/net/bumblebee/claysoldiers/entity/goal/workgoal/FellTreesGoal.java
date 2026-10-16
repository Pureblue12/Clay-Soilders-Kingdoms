package net.bumblebee.claysoldiers.entity.goal.workgoal;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.door.DoorPathing;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KingdomEconomy;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Stage 5: a woodcutter of a kingdom's lumber camp ({@code KingdomEconomy}). It walks to the nearest tree within
 * {@code economy.lumber.radius} of the camp, chops it log by log from the top (no floating trunks), plants a sapling of the
 * same kind where the trunk stood, and carries the logs to the camp's chest, where they go into the kingdom's stockpile.
 * <p>
 * A tree is a single column of natural logs (at most {@code economy.lumber.max_trunk} tall, so every log is within reach)
 * standing on dirt, with natural leaves at its top and no other logs around it: big trees (2 by 2 trunks, branches) are
 * left alone, and so are logs in any of the kingdom's buildings or in another kingdom's claim. A sapling of the tree's kind
 * is planted at once, standing for the ones its leaves drop (D96). A tree it can't reach in {@code economy.lumber.walk_ticks}
 * is left alone for a while.
 */
public class FellTreesGoal extends AbstractWorkGoal {
    public static final String FELLING_LANG = JOB_LANG_KEY.formatted(ClaySoldiersCommon.MOD_ID, "felling_trees");
    /** How long a tree it couldn't reach is left alone, in ticks. */
    private static final int SKIP_TICKS = 6000;
    private static final int REPATH_TICKS = 20;
    private static final int SEARCH_PAUSE_TICKS = 100;
    private static final int WORKPLACE_TICKS = 100;

    private enum Step { FIND, WALK, CHOP, DELIVER }

    private Step step = Step.FIND;
    @Nullable
    private BlockPos tree;
    private int walked;
    private int chopTimer;
    private int nextSearch;
    private boolean saidNoTree;
    private final Map<BlockPos, Long> skipped = new HashMap<>();
    @Nullable
    private KingdomEconomy.Workplace workplace;
    private int workplaceChecked = Integer.MIN_VALUE;

    public FellTreesGoal(AbstractClaySoldierEntity soldier, Supplier<WorkSelectorGoal> workSelector) {
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
        tree = null;
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
            case CHOP -> chop(level);
            case DELIVER -> deliver(place);
        }
    }

    private void find(ServerLevel level, KingdomEconomy.Workplace place) {
        setStatus(SEARCHING_ID);
        if (soldier.tickCount < nextSearch) {
            return;
        }
        tree = findTree(level, place);
        if (tree == null) {
            nextSearch = soldier.tickCount + SEARCH_PAUSE_TICKS;
            if (!saidNoTree) {
                saidNoTree = true;
                DebugLog.event(DebugCategory.ECONOMY, soldier, "woodcutter: no tree it may fell within %s of the camp", Tuning.ECONOMY_LUMBER_RADIUS.get());
            }
            if (!soldier.getCarriedStack().isEmpty()) {
                step = Step.DELIVER;
            } else {
                takeAShortBreak();
            }
            return;
        }
        saidNoTree = false;
        BlockPos found = tree;
        DebugLog.event(DebugCategory.ECONOMY, soldier, "woodcutter: going to the tree at %s", found.toShortString());
        step = Step.WALK;
        walked = 0;
    }

    private void walk(ServerLevel level) {
        if (tree == null || trunkHeight(level, tree) <= 0) {
            step = Step.FIND;
            return;
        }
        double dx = soldier.getX() - (tree.getX() + 0.5);
        double dz = soldier.getZ() - (tree.getZ() + 0.5);
        if (dx * dx + dz * dz <= 2.0 * 2.0 && Math.abs(soldier.getY() - tree.getY()) <= 1.5) {
            soldier.getNavigation().stop();
            step = Step.CHOP;
            chopTimer = 0;
            return;
        }
        if (walked++ > Tuning.ECONOMY_LUMBER_WALK_TICKS.get()) {
            skipped.put(tree, level.getGameTime() + SKIP_TICKS);
            BlockPos lost = tree;
            DebugLog.event(DebugCategory.ECONOMY, soldier, "woodcutter: gave up the tree at %s, no way there", lost.toShortString());
            tree = null;
            step = Step.FIND;
            return;
        }
        if (walked % REPATH_TICKS == 1) {
            soldier.getNavigation().moveTo(tree.getX() + 0.5, tree.getY(), tree.getZ() + 0.5, 1.0);
        }
    }

    private void chop(ServerLevel level) {
        if (tree == null) {
            step = Step.FIND;
            return;
        }
        soldier.getLookControl().setLookAt(tree.getX() + 0.5, tree.getY() + 1, tree.getZ() + 0.5);
        if (++chopTimer < Tuning.ECONOMY_LUMBER_CHOP_TICKS.get()) {
            if (chopTimer % 6 == 0) {
                soldier.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }
        chopTimer = 0;
        int height = trunkHeight(level, tree);
        if (height <= 0) {
            step = Step.DELIVER;
            return;
        }
        BlockPos top = tree.above(height - 1);
        BlockState log = level.getBlockState(top);
        Item item = log.getBlock().asItem();
        ItemStack carried = soldier.getCarriedStack();
        if (!carried.isEmpty() && (!carried.is(item) || carried.getCount() >= carried.getMaxStackSize())) {
            // A different kind, or a full stack: bring this lot first, the rest of the tree waits.
            step = Step.DELIVER;
            return;
        }
        level.destroyBlock(top, false, soldier);
        soldier.setCarriedStack(carried.isEmpty() ? new ItemStack(item) : carried.copyWithCount(carried.getCount() + 1));
        if (height == 1) {
            replant(level, tree, item);
            BlockPos felled = tree;
            DebugLog.event(DebugCategory.ECONOMY, soldier, "woodcutter: felled the tree at %s", felled.toShortString());
            tree = null;
            step = Step.DELIVER;
            walked = 0;
        }
    }

    private void replant(ServerLevel level, BlockPos base, Item log) {
        Block sapling = saplingFor(log);
        if (sapling == null || !level.getBlockState(base).isAir()) {
            return;
        }
        BlockState state = sapling.defaultBlockState();
        if (state.canSurvive(level, base)) {
            level.setBlockAndUpdate(base, state);
            level.gameEvent(GameEvent.BLOCK_PLACE, base, GameEvent.Context.of(soldier, state));
            DebugLog.event(DebugCategory.ECONOMY, soldier, "woodcutter: planted %s at %s", BuiltInRegistries.BLOCK.getKey(sapling), base.toShortString());
        }
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

    /** The nearest tree this woodcutter may fell, or null. */
    @Nullable
    private BlockPos findTree(ServerLevel level, KingdomEconomy.Workplace place) {
        int radius = Tuning.ECONOMY_LUMBER_RADIUS.get();
        BlockPos center = place.building().pos();
        long now = level.getGameTime();
        skipped.values().removeIf(until -> until <= now);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                if (!level.isLoaded(pos.set(x, center.getY(), z))) {
                    continue;
                }
                for (int y = center.getY() - 6; y <= center.getY() + 6; y++) {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).is(BlockTags.LOGS) || !level.getBlockState(pos.below()).is(BlockTags.DIRT)) {
                        continue;
                    }
                    BlockPos base = pos.immutable();
                    double distance = soldier.distanceToSqr(x + 0.5, y, z + 0.5);
                    if (distance < bestDistance && !skipped.containsKey(base) && trunkHeight(level, base) > 0
                            && !KingdomEconomy.inBuilding(level, place.kingdom(), base, 2) && !otherClaim(level, place, base)) {
                        best = base;
                        bestDistance = distance;
                    }
                    break;
                }
            }
        }
        return best;
    }

    private boolean otherClaim(ServerLevel level, KingdomEconomy.Workplace place, BlockPos pos) {
        return Kingdoms.at(level.getServer(), level.dimension(), pos).filter(other -> !other.id().equals(place.kingdom().id())).isPresent();
    }

    /**
     * How many logs tall the tree standing at {@code base} is, or 0 when it isn't one this woodcutter may fell: a single
     * column of logs, at most {@code economy.lumber.max_trunk} tall, no other logs within 2 blocks, natural leaves at its top.
     */
    static int trunkHeight(ServerLevel level, BlockPos base) {
        if (!level.getBlockState(base).is(BlockTags.LOGS) || !level.getBlockState(base.below()).is(BlockTags.DIRT)) {
            return 0;
        }
        int max = Tuning.ECONOMY_LUMBER_MAX_TRUNK.get();
        int height = 0;
        while (height <= max && level.getBlockState(base.above(height)).is(BlockTags.LOGS)) {
            height++;
        }
        if (height > max) {
            return 0;
        }
        boolean leaves = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy <= height + 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0) {
                        if (dy == height) {
                            leaves |= naturalLeaves(level.getBlockState(pos.setWithOffset(base, 0, dy, 0)));
                        }
                        continue;
                    }
                    BlockState state = level.getBlockState(pos.setWithOffset(base, dx, dy, dz));
                    if (state.is(BlockTags.LOGS)) {
                        return 0;
                    }
                    if (dy >= height - 2) {
                        leaves |= naturalLeaves(state);
                    }
                }
            }
        }
        return leaves ? height : 0;
    }

    private static boolean naturalLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
    }

    /** The sapling of a log's kind (oak log: oak sapling; mangrove: its propagule), or null. */
    @Nullable
    static Block saplingFor(Item log) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(log);
        String path = id.getPath();
        if (!path.endsWith("_log")) {
            return null;
        }
        String kind = path.substring(0, path.length() - "_log".length());
        String sapling = kind.equals("mangrove") ? "mangrove_propagule" : kind + "_sapling";
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), sapling));
        return block.filter(found -> found instanceof SaplingBlock).orElse(null);
    }

    @Override
    public boolean workRequiresItemCarrying() {
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(FELLING_LANG);
    }
}
