package net.bumblebee.claysoldiers.blueprint.templates;

import net.bumblebee.claysoldiers.blueprint.BlueprintRequest;
import net.bumblebee.claysoldiers.blueprint.BlueprintTemplateSettings;
import net.bumblebee.claysoldiers.blueprint.ReachOrigin;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.bumblebee.claysoldiers.entity.ClayMobEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ServerBlueprintPlan extends BlueprintPlan {
    private final List<BlueprintBlockInfo> blockInfoList;
    /** Whether the last request search that handed out nothing skipped blocks out of reach (for scaffolding). */
    private boolean lastSearchFoundUnreachable;
    /**
     * Per builder (keyed by its reach test, which each builder keeps): blocks it found out of reach recently, skipped
     * by its request searches until the time stored or until the build changes ({@link #forgetUnreachable}). Per
     * builder, because one standing somewhere awkward must not make the others skip blocks they can reach.
     */
    private final Map<Predicate<BlockPos>, BuilderMemory> unreachableByBuilder = new WeakHashMap<>();

    /** One builder's memory: where it stood when it found the blocks out of reach, and until when they count as such. */
    private static final class BuilderMemory {
        private BlockPos origin;
        private final Map<BlueprintBlockInfo, Long> blocks = new HashMap<>();
    }

    public ServerBlueprintPlan(Vec3i size, List<StructureTemplate.StructureBlockInfo> blockInfoList) {
        super(BlueprintUtil.getNeededItemsFromInfo(blockInfoList, StructureTemplate.StructureBlockInfo::state), size);
        this.blockInfoList = fromBlockInfoList(blockInfoList);
    }

    public static ServerBlueprintPlan load(CompoundTag tag, HolderLookup.Provider registries) {
        ServerBlueprintPlan serverBlueprintPlan = new ServerBlueprintPlan(BlueprintUtil.getSizeFromTag(tag), ImmutableTemplate.loadPallet(registries.lookupOrThrow(Registries.BLOCK), tag.getList("palette", 10), tag.getList(StructureTemplate.BLOCKS_TAG, 10), List.of()));
        serverBlueprintPlan.loadHasStarted(tag);
        return serverBlueprintPlan;
    }

    public CompoundTag save(CompoundTag pTag) {
        if (this.blockInfoList.isEmpty()) {
            pTag.put("blocks", new ListTag());
            pTag.put("palette", new ListTag());
        } else {
            BlueprintUtil.SimplePalette simplePalette = new BlueprintUtil.SimplePalette();
            ListTag blockInfoAsPallet = new ListTag();

            blockInfoList.forEach(blockInfo -> {
                CompoundTag compoundtag = new CompoundTag();
                compoundtag.put(
                        "pos",
                        this.newIntegerList(
                                blockInfo.getPos().getX(),
                                blockInfo.getPos().getY(),
                                blockInfo.getPos().getZ()
                        )
                );
                int stateId = simplePalette.idFor(blockInfo.getState());
                compoundtag.putInt("state", stateId);
                if (blockInfo.getNbt() != null) {
                    compoundtag.put("nbt", blockInfo.getNbt());
                }

                blockInfoAsPallet.add(compoundtag);
            });

            pTag.put("blocks", blockInfoAsPallet);
            ListTag palletTag = new ListTag();

            for (BlockState blockstate : simplePalette) {
                palletTag.add(NbtUtils.writeBlockState(blockstate));
            }

            pTag.put("palette", palletTag);
        }

        saveSize(pTag);
        saveHasStarted(pTag);
        return pTag;
    }

    @Override
    public PlaceResult tryPlacing(Level level, ItemStack itemStack, BlockPos base, BlueprintTemplateSettings settings) {
        if (!(itemStack.getItem() instanceof BlockItem)) {
            // Items that aren't blocks (e.g. a stick, or AIR for block states without an item) can never be placed.
            return PlaceResult.NOT_NEEDED;
        }
        var res = tryPlacingAll(itemStack.getItem(), itemStack, level, base, settings);
        if (res == PlaceResult.SUCCESS) {
            if (!hasItemAndShrink(itemStack.getItem())) {
                throw new IllegalStateException("Successfully placed a Block which was not needed");
            }
        }

        return res;
    }

    /**
     * Places the block of a builder's request exactly at the request's spot. Unlike {@link #tryPlacing}, never uses
     * another spot: if the request is no longer this plan's open request for its spot (cancelled, taken over, already
     * filled) or the item doesn't match, nothing is placed. When the open request's block fails to place, the block
     * gets a cooldown ({@link BlueprintBlockInfo#placementFailed}).
     */
    public PlaceResult tryPlacingRequest(Level level, BlueprintRequest request, ItemStack itemStack, BlockPos base, BlueprintTemplateSettings settings) {
        if (!(itemStack.getItem() instanceof BlockItem)) {
            return PlaceResult.NOT_NEEDED;
        }
        var iterator = blockInfoList.iterator();
        while (iterator.hasNext()) {
            var blockInfo = iterator.next();
            if (!blockInfo.isActiveRequest(request)) {
                continue;
            }
            BlockPos pos = targetPosition(base, settings, blockInfo);
            if (!pos.equals(request.getPos()) || !blockInfo.isFor(itemStack.getItem())) {
                blockInfo.placementFailed(level.getGameTime(), pos);
                return PlaceResult.NOT_NEEDED;
            }
            var updatedBlockInfo = processBlockInfos(settings, blockInfo);
            Placement placement = placeBlock(itemStack, level, pos, updatedBlockInfo.state().mirror(settings.mirror()).rotate(settings.rotation()));
            if (placement == Placement.SOLDIER_IN_THE_WAY) {
                blockInfo.soldierInTheWay(level.getGameTime(), pos);
                request.setSoldierInTheWay();
                return PlaceResult.CANT_PLACE;
            }
            if (placement != Placement.PLACED) {
                blockInfo.placementFailed(level.getGameTime(), pos);
                return PlaceResult.CANT_PLACE;
            }
            iterator.remove();
            blockInfo.markDone();
            if (!hasItemAndShrink(itemStack.getItem())) {
                throw new IllegalStateException("Successfully placed a Block which was not needed");
            }
            return PlaceResult.SUCCESS;
        }
        return PlaceResult.NOT_NEEDED;
    }

    private PlaceResult tryPlacingAll(Item item, ItemStack stack, Level level, BlockPos base, BlueprintTemplateSettings settings) {
        var iterator = blockInfoList.iterator();
        while (iterator.hasNext()) {
            var blockInfo = iterator.next();
            if (!blockInfo.isFor(item)) {
                continue;
            }
            var updatedBlockInfo = processBlockInfos(settings, blockInfo);

            if (placeBlock(stack, level, updatedBlockInfo.pos().offset(base), updatedBlockInfo.state().mirror(settings.mirror()).rotate(settings.rotation())) == Placement.PLACED) {
                iterator.remove();
                blockInfo.markDone();
                return PlaceResult.SUCCESS;
            }
        }
        return PlaceResult.CANT_PLACE;
    }

    /** What came of placing one block. */
    private enum Placement { PLACED, SPOT_TAKEN, SOLDIER_IN_THE_WAY }

    private Placement placeBlock(ItemStack stack, Level level, BlockPos pos, BlockState state) {
        if (!canPlaceBlock(level, pos)) {
            return Placement.SPOT_TAKEN;
        }
        if (creatureInside(level, pos, state)) {
            // A creature's body is where the block would be (church builders ended up inside cobblestone walls and
            // window panes, farm builders half inside a fence, stuck for good, D64). Vanilla's placement rule.
            DebugLog.event(DebugCategory.BUILD, null, "not placed at %s: a creature stands there", pos);
            return Placement.SOLDIER_IN_THE_WAY;
        }
        ClayMobEntity trapped = wouldTrap(level, pos, state);
        if (trapped != null) {
            // The block would close the last way out of the cell a soldier stands in (keep builders walled themselves
            // into a 1x1 shaft of the blueprint, D64). The block waits a moment (SOLDIER_IN_THE_WAY_TICKS); the soldier moves on; someone places it later.
            DebugLog.event(DebugCategory.BUILD, trapped, "not placed at %s: it would trap this soldier", pos);
            return Placement.SOLDIER_IN_THE_WAY;
        }
        if (level.setBlock(pos, state, 3)) {
            state.getBlock().setPlacedBy(level, pos, state, null, stack);
            return Placement.PLACED;
        }
        return Placement.SPOT_TAKEN;
    }

    private boolean canPlaceBlock(Level level, BlockPos pos) {
        return canPlaceAt(level, pos);
    }

    /**
     * Whether a living creature's body is where {@code state} placed at {@code pos} would be (vanilla's own placement
     * rule: the block's collision shape against every creature's bounding box, so a soldier half inside the cell counts),
     * or stands in the cell of a fence, wall or pane next to it (which would grow an arm toward the new block).
     */
    public static boolean creatureInside(Level level, BlockPos pos, BlockState state) {
        if (!level.isUnobstructed(state, pos, CollisionContext.empty())) {
            return true;
        }
        // A fence, wall or pane only fills the middle of its cell, so a tiny soldier can stand beside the post without
        // touching it; placed, the post pens it in (cottage L3 on CI: a builder inside a lantern post for 9,200 ticks).
        if ((state.getBlock() instanceof CrossCollisionBlock || state.getBlock() instanceof WallBlock)
                && !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive).isEmpty()) {
            return true;
        }
        // A fence, wall or pane next to the spot grows an arm toward the new block: a soldier standing beside that post
        // ends up inside the arm, stuck for good (farm and cottage builders, D64). Same answer: wait until it has moved.
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(side);
            BlockState neighbour = level.getBlockState(next);
            if ((neighbour.getBlock() instanceof CrossCollisionBlock || neighbour.getBlock() instanceof WallBlock)
                    && !level.getEntitiesOfClass(LivingEntity.class, new AABB(next), LivingEntity::isAlive).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The clay soldier next to {@code pos} that {@code state} placed there would trap: it stands in a cell it can
     * leave now (walk or drop out sideways, or hop up one block) and could not leave any more. Null when there is none.
     */
    @Nullable
    public static ClayMobEntity wouldTrap(Level level, BlockPos pos, BlockState state) {
        if (state.getCollisionShape(level, pos).isEmpty()) {
            return null;
        }
        for (ClayMobEntity soldier : level.getEntitiesOfClass(ClayMobEntity.class, new AABB(pos).inflate(1), LivingEntity::isAlive)) {
            BlockPos feet = soldier.blockPosition();
            if (feet.equals(pos) || feet.above().equals(pos)) {
                continue; // creatureInside
            }
            if (hasExit(level, feet, null) && !hasExit(level, feet, pos)) {
                return soldier;
            }
        }
        return null;
    }

    /** Whether a soldier standing at {@code feet} can leave its cell sideways, with {@code placed} counted as a block. */
    private static boolean hasExit(Level level, BlockPos feet, @Nullable BlockPos placed) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = feet.relative(side);
            boolean nextFeet = open(level, next, placed);
            boolean nextHead = open(level, next.above(), placed);
            if (nextFeet && nextHead) {
                return true; // walk (or drop)
            }
            if (!nextFeet && nextHead && open(level, next.above(2), placed) && open(level, feet.above(2), placed)) {
                return true; // hop up one block
            }
        }
        return false;
    }

    private static boolean open(Level level, BlockPos pos, @Nullable BlockPos placed) {
        return !pos.equals(placed) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Returns whether a blueprint block can be placed at the given position (it is air or replaceable).
     * Blocks that fail this are never requested, so a blueprint with such a spot never finishes.
     */
    public static boolean canPlaceAt(LevelReader level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    /**
     * Returns the world positions of the blocks this plan still has to place.
     *
     * @param base     the base position of the template, see {@code EaselBlockEntity.templateBase}
     * @param settings rotation and mirror of the template
     */
    public List<BlockPos> targetPositions(BlockPos base, BlueprintTemplateSettings settings) {
        return blockInfoList.stream().map(blockInfo -> targetPosition(base, settings, blockInfo)).toList();
    }

    /**
     * The block each block this plan still has to place will be once placed, by world position (the scaffold planner
     * checks which floors a column can stand on, D77). Changes nothing.
     */
    public Map<BlockPos, BlockState> targetStates(BlockPos base, BlueprintTemplateSettings settings) {
        Map<BlockPos, BlockState> states = new HashMap<>();
        for (BlueprintBlockInfo info : blockInfoList) {
            states.put(targetPosition(base, settings, info), info.getState().mirror(settings.mirror()).rotate(settings.rotation()));
        }
        return states;
    }

    /** Where the doors this plan still has to place go (handed out last; soldiers walk through their doorways). */
    public java.util.Set<BlockPos> doorPositions(BlockPos base, BlueprintTemplateSettings settings) {
        return blockInfoList.stream().filter(BlueprintBlockInfo::isDoor)
                .map(info -> targetPosition(base, settings, info)).collect(Collectors.toSet());
    }

    /**
     * The item of every block this plan still has to place, in hand-out order (the order {@link #getRequest} searches).
     * For the dev material supply and GameTests; changes nothing.
     */
    public List<Item> remainingItems() {
        return blockInfoList.stream().map(info -> info.getState().getBlock().asItem()).toList();
    }

    /**
     * One line per block this plan still has to place: its world position, block, whether its spot is free, whether it is
     * cooling down after a failed placement, and its last request (for GameTest failure reports; changes nothing).
     */
    public List<String> describeRemaining(Level level, BlockPos base, BlueprintTemplateSettings settings) {
        long time = level.getGameTime();
        return blockInfoList.stream().map(info -> {
            BlockPos pos = targetPosition(base, settings, info);
            return "%s %s | spot %s | %s | free to hand out: %s | %s".formatted(pos.toShortString(), info.getState(),
                    canPlaceAt(level, pos) ? "free" : "taken by " + level.getBlockState(pos),
                    info.isCoolingDown(time) ? "cooling down" : "no cooldown", info.hasAvailableRequest(time), info);
        }).toList();
    }

    private static BlockPos targetPosition(BlockPos base,BlueprintTemplateSettings settings, BlueprintBlockInfo blockInfo) {
        return base.offset(adjustForStructureSettings(settings, blockInfo.getPos()));
    }

    private static StructureTemplate.StructureBlockInfo processBlockInfos(BlueprintTemplateSettings pSettings, BlueprintBlockInfo pBlockInfo) {
        return new StructureTemplate.StructureBlockInfo(
                adjustForStructureSettings(pSettings, pBlockInfo.getPos()),
                pBlockInfo.getState(),
                pBlockInfo.getNbt() != null ? pBlockInfo.getNbt().copy() : null
        );
    }

    @Override
    public String toString() {
        return "ServerBlueprintTemplate{%s Blocks(%s) Items(%s): %s}".formatted(
                getSize(),
                blockInfoList.size(),
                getNumberOfItems(),
                getNeededItems());
    }

    /**
     * Returns the best suitable request for this template
     * @param base the base position from which this structure is build
     */
    public @Nullable BlueprintRequest getRequest(ServerLevel level, BlockPos base, BlueprintTemplateSettings settings, Predicate<BlockPos> canReach) {
        // Blocks another builder has are skipped (no taking over); they become free when cancelled, finished or stale.
        // Blocks that recently failed to place are skipped until their cooldown ends, and doors wait while any other
        // block can still be placed (both checked first: they're cheap and save a path search). Blocks found out of
        // reach recently by this builder count as out of reach without a new path search (build.unreachable_recheck_ticks).
        // Counters for the debug summary only.
        int checked = 0;
        int coolingDown = 0;
        int doorsWaiting = 0;
        int unreachable = 0;
        int notPlaceable = 0;
        int assigned = 0;
        Boolean doorsMustWait = null; // computed once, at the first door
        Map<BlueprintBlockInfo, Long> unreachableFor = unreachable(canReach);

        for (BlueprintBlockInfo blockInfo : blockInfoList) {
            checked++;
            if (blockInfo.isCoolingDown(level.getGameTime())) {
                coolingDown++;
                continue;
            }
            if (blockInfo.isDoor()) {
                if (doorsMustWait == null) {
                    doorsMustWait = nonDoorBlockStillPlaceable(level, base, settings);
                }
                if (doorsMustWait) {
                    doorsWaiting++;
                    continue;
                }
            }
            Long knownUnreachable = unreachableFor.get(blockInfo);
            if (knownUnreachable != null && level.getGameTime() < knownUnreachable) {
                unreachable++;
                continue;
            }
            var infoAbsolutePos = targetPosition(base, settings, blockInfo);
            if (!canReach.test(infoAbsolutePos)) {
                // Path searches are expensive: this builder doesn't search again for a while, unless the build changes.
                if (Tuning.BUILD_UNREACHABLE_RECHECK_TICKS.get() > 0) {
                    unreachableFor.put(blockInfo, level.getGameTime() + Tuning.BUILD_UNREACHABLE_RECHECK_TICKS.get());
                }
                unreachable++;
                continue;
            }
            if (!canPlaceBlock(level, infoAbsolutePos)) {
                notPlaceable++;
                continue;
            }
            if (blockInfo.hasAvailableRequest(level.getGameTime())) {
                lastSearchFoundUnreachable = false;
                logRequestSearch(checked, coolingDown, doorsWaiting, unreachable, notPlaceable, assigned, "handing out a free block");
                return blockInfo.getRequest(level.getGameTime(), infoAbsolutePos);
            }
            assigned++;
        }
        logRequestSearch(checked, coolingDown, doorsWaiting, unreachable, notPlaceable, assigned, "nothing to hand out");
        lastSearchFoundUnreachable = unreachable > 0;
        return null;
    }

    /**
     * Forgets which blocks were found out of reach, so the next request search checks them again (call when the world
     * around the build changed, e.g. a block of the build was placed or removed).
     */
    public void forgetUnreachable() {
        unreachableByBuilder.clear();
    }

    /**
     * Forgets which blocks within {@code radius} blocks sideways of {@code changed} were found out of reach: a block placed
     * or removed there can make them reachable (a new floor to stand on, a scaffolding column). Blocks farther away are
     * checked again when their wait ({@code build.unreachable_recheck_ticks}) is up.
     */
    public void forgetUnreachableNear(BlockPos changed, int radius, BlockPos base, BlueprintTemplateSettings settings) {
        for (BuilderMemory memory : unreachableByBuilder.values()) {
            memory.blocks.keySet().removeIf(info -> {
                BlockPos pos = targetPosition(base, settings, info);
                return Math.abs(pos.getX() - changed.getX()) <= radius && Math.abs(pos.getZ() - changed.getZ()) <= radius;
            });
        }
    }

    /** The blocks one builder (its reach test) found out of reach recently, and until when they count as such. */
    private Map<BlueprintBlockInfo, Long> unreachable(Predicate<BlockPos> canReach) {
        BuilderMemory memory = unreachableByBuilder.computeIfAbsent(canReach, builder -> new BuilderMemory());
        if (canReach instanceof ReachOrigin origin) {
            // Out of reach from where the builder stood then; from another spot it may not be.
            BlockPos at = origin.reachOrigin().immutable();
            if (!at.equals(memory.origin)) {
                memory.blocks.clear();
                memory.origin = at;
            }
        }
        return memory.blocks;
    }

    /**
     * Whether the last request search handed out nothing while some remaining blocks were out of reach. The easel then
     * asks for scaffolding ({@code Scaffolds}).
     */
    public boolean lastSearchFoundUnreachable() {
        return lastSearchFoundUnreachable;
    }

    /**
     * Returns whether a block that isn't a door still has to be placed and its spot is free (it may be carried by a
     * builder right now, unreachable at the moment, or cooling down). Doors wait until there is none: clay soldiers
     * can't open doors, so a door placed while others still work inside can lock them out. Doors don't wait for each
     * other, and blocks whose spot is taken don't hold them back.
     */
    private boolean nonDoorBlockStillPlaceable(LevelReader level, BlockPos base, BlueprintTemplateSettings settings) {
        return blockInfoList.stream().anyMatch(info -> !info.isDoor() && canPlaceAt(level, targetPosition(base, settings, info)));
    }

    private void logRequestSearch(int checked, int coolingDown, int doorsWaiting, int unreachable, int notPlaceable, int assigned, String result) {
        if (!DebugLog.isOn(DebugCategory.BUILD)) {
            return;
        }
        DebugLog.event(DebugCategory.BUILD, null, () -> "request search: checked %d of %d remaining blocks (%d cooling down after failed placing, %d doors waiting for other blocks, %d not reachable, %d not placeable, %d already assigned): %s"
                .formatted(checked, blockInfoList.size(), coolingDown, doorsWaiting, unreachable, notPlaceable, assigned, result));
    }

    @Override
    public boolean isFinished() {
        return blockInfoList.isEmpty();
    }

    private static BlockPos adjustForStructureSettings(BlueprintTemplateSettings settings, BlockPos pos) {
        return StructureTemplate.calculateRelativePosition(settings.convertTo(), pos.offset(BlockPos.ZERO));
    }

    /**
     * Maps the given {@code List<StructureTemplate.StructureBlockInfo>} to a deep copied {@code List<BlueprintBlockInfo>}.
     * @param infoList the list to copy.
     * @return deep copy {@code List<StructureTemplate.StructureBlockInfo>}
     */
    private static List<BlueprintBlockInfo> fromBlockInfoList(List<StructureTemplate.StructureBlockInfo> infoList) {
        return infoList.stream().map(BlueprintBlockInfo::fromInfo).collect(Collectors.toCollection(LinkedList::new));
    }
}
