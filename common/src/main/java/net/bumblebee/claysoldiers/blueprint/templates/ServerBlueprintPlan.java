package net.bumblebee.claysoldiers.blueprint.templates;

import net.bumblebee.claysoldiers.blueprint.BlueprintRequest;
import net.bumblebee.claysoldiers.blueprint.PlannedReach;
import net.bumblebee.claysoldiers.blueprint.plan.BuildPlan;
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
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WitherRoseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ServerBlueprintPlan extends BlueprintPlan {
    private final List<BlueprintBlockInfo> blockInfoList;
    private static final String BUILD_PLAN_TAG = "build_plan";
    /** The structure's pre-baked build plan (D82), or {@code null} (blocks are handed out in list order, as before). */
    @Nullable
    private final BuildPlan buildPlan;
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
        this(size, blockInfoList, null);
    }

    /**
     * @param buildPlan the structure's pre-baked build plan (D82), or {@code null}; {@code blockInfoList} is already in its
     *                  order ({@link ImmutableTemplate#withPlan})
     */
    public ServerBlueprintPlan(Vec3i size, List<StructureTemplate.StructureBlockInfo> blockInfoList, @Nullable BuildPlan buildPlan) {
        super(BlueprintUtil.getNeededItemsFromInfo(blockInfoList, StructureTemplate.StructureBlockInfo::state), size);
        this.blockInfoList = fromBlockInfoList(blockInfoList);
        this.buildPlan = buildPlan;
    }

    public static ServerBlueprintPlan load(CompoundTag tag, HolderLookup.Provider registries) {
        // Loading sorts the blocks into the normal order again; with a plan, they go back into the plan's.
        BuildPlan buildPlan = tag.contains(BUILD_PLAN_TAG, Tag.TAG_COMPOUND) ? BuildPlan.load(tag.getCompound(BUILD_PLAN_TAG)) : null;
        List<StructureTemplate.StructureBlockInfo> blocks = ImmutableTemplate.loadPallet(registries.lookupOrThrow(Registries.BLOCK), tag.getList("palette", 10), tag.getList(StructureTemplate.BLOCKS_TAG, 10), List.of());
        if (buildPlan != null) {
            blocks = buildPlan.order(blocks, StructureTemplate.StructureBlockInfo::pos);
        }
        ServerBlueprintPlan serverBlueprintPlan = new ServerBlueprintPlan(BlueprintUtil.getSizeFromTag(tag), blocks, buildPlan);
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

        if (buildPlan != null) {
            pTag.put(BUILD_PLAN_TAG, buildPlan.save());
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
     * or stands in the cell of a fence, wall or pane next to it (which would grow an arm toward the new block), or is
     * partly in the cell of a block that hurts whoever is inside it ({@link #hurtsInside}: a sweet berry bush).
     */
    public static boolean creatureInside(Level level, BlockPos pos, BlockState state) {
        if (!level.isUnobstructed(state, pos, CollisionContext.empty())) {
            return true;
        }
        // A block that hurts whoever is inside it has no collision box, so the rule above lets it through: hospital L3
        // builders stood where a sweet berry bush was placed and were hurt until they died (D81). Any part of a body
        // in the cell counts.
        if (hurtsInside(state) && !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive).isEmpty()) {
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

    /** Blocks without a collision box that hurt or trap a creature standing in them (vanilla's {@code entityInside}). */
    static boolean hurtsInside(BlockState state) {
        Block block = state.getBlock();
        return block instanceof SweetBerryBushBlock || block instanceof WitherRoseBlock || block instanceof BaseFireBlock
                || block instanceof PowderSnowBlock;
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
        if (buildPlan == null || Tuning.BUILD_FOLLOW_PLAN.get() == 0) {
            return search(blockInfoList, level, base, settings, canReach, "").request();
        }
        // With a pre-baked plan (D82): only the blocks of the lowest wave not yet built, in random order, those away from
        // the blocks other builders carry first, so builders pick different blocks and spread out. Every order inside a
        // wave was checked offline. When none of them can be handed out and no other builder has one of them (out of
        // reach here, cooling down after failing: the world differs from the plan), the whole list is searched as before.
        int wave = currentWave();
        if (wave == BuildPlan.UNPLANNED) {
            return search(blockInfoList, level, base, settings, canReach, "").request();
        }
        List<BlueprintBlockInfo> current = new ArrayList<>();
        List<BlockPos> carried = new ArrayList<>();
        for (BlueprintBlockInfo info : blockInfoList) {
            int infoWave = buildPlan.wave(info.getPos());
            if (infoWave == wave) {
                current.add(info);
            } else if (infoWave < wave && info.timesGivenUp() >= PLAN_GIVE_UPS) {
                // A block builders keep giving up on can't be reached from the plan's spots here (keep L1: a chest 10 up,
                // handed out and given up for 6,000 ticks): it leaves the plan, so it no longer holds its wave back (chapel
                // L3: one such block kept the plan on its wave for the rest of the build, every block came from the
                // whole-list search and stacks could only follow up inside that wave: twice the time). It stays on offer
                // with the normal standing-spot search, and the whole-list search after the plan can start scaffold.
                DebugLog.event(DebugCategory.BUILD, null, "plan: %s at %s given up %s times, left to the normal search",
                        info.getState().getBlock(), targetPosition(base, settings, info), info.timesGivenUp());
                current.add(info);
            }
            if (!info.hasAvailableRequest(level.getGameTime())) {
                carried.add(targetPosition(base, settings, info));
            }
        }
        // Random, but among the wave's blocks nearest the builder first (in steps of NEAR_STEP blocks): a fully random pick
        // sent builders across the whole site and doubled build times (town hall L1 51,300 ticks against 28,000).
        Collections.shuffle(current, new java.util.Random(level.getRandom().nextLong()));
        Comparator<BlueprintBlockInfo> order = Comparator.comparing(info -> nearAny(targetPosition(base, settings, info), carried));
        if (canReach instanceof ReachOrigin origin) {
            BlockPos from = origin.reachOrigin();
            order = order.thenComparingInt(info -> (int) Math.sqrt(targetPosition(base, settings, info).distSqr(from)) / NEAR_STEP);
        }
        current.sort(order);
        Search inWave = search(current, level, base, settings, canReach, "plan wave " + wave + ": ");
        if (inWave.request() != null) {
            return inWave.request();
        }
        if (inWave.assigned() > 0) {
            // Other builders are on this wave's last blocks. Rather than wait for them (a crew stood idle at every wave's
            // end: chapel L3 took twice as long as without the plan), take a block of the next waves well away from them.
            List<BlueprintBlockInfo> next = lookahead(base, settings, wave, Tuning.BUILD_PLAN_LOOKAHEAD_CLEARANCE.get(), false);
            if (!next.isEmpty()) {
                next.sort(order);
                Search ahead = search(next, level, base, settings, canReach, "plan waves after " + wave + " ahead: ");
                if (ahead.request() != null) {
                    return ahead.request();
                }
            }
            // Nothing far enough from them: wait (no scaffolding: nothing is stuck).
            lastSearchFoundUnreachable = false;
            return null;
        }
        // Nothing of this wave can be handed out and nobody carries any of it: its last blocks are stuck here (out of reach
        // from where the world differs from the plan, cooling down, or never placeable). Go on with the next wave that has
        // something, rather than with the whole list in the normal order (chapel L3: one stuck block sent 967 of 1,077
        // hand-outs there, without the plan's spots and with stacks that could only follow up in the stuck wave).
        java.util.TreeMap<Integer, List<BlueprintBlockInfo>> later = new java.util.TreeMap<>();
        for (BlueprintBlockInfo info : blockInfoList) {
            int infoWave = buildPlan.wave(info.getPos());
            if (infoWave > wave && infoWave != BuildPlan.UNPLANNED && info.timesGivenUp() < PLAN_GIVE_UPS) {
                later.computeIfAbsent(infoWave, w -> new ArrayList<>()).add(info);
            }
        }
        for (Map.Entry<Integer, List<BlueprintBlockInfo>> entry : later.entrySet()) {
            List<BlueprintBlockInfo> blocks = entry.getValue();
            Collections.shuffle(blocks, new java.util.Random(level.getRandom().nextLong()));
            blocks.sort(order);
            Search past = search(blocks, level, base, settings, canReach, "plan wave " + entry.getKey() + " past stuck wave " + wave + ": ");
            if (past.request() != null) {
                if (entry.getKey() > frontier) {
                    DebugLog.event(DebugCategory.BUILD, null, "plan: nothing of wave %s can be handed out (%s blocks left in it), going on with wave %s",
                            wave, current.size(), entry.getKey());
                    frontier = entry.getKey();
                }
                return past.request();
            }
            if (past.assigned() > 0) {
                lastSearchFoundUnreachable = false;
                return null; // others are on that wave's last blocks: wait, as above
            }
        }
        DebugLog.event(DebugCategory.BUILD, null, "plan: nothing of wave %s or later can be handed out (%s blocks left in it), searching every block", wave, current.size());
        return search(blockInfoList, level, base, settings, canReach, "outside the plan: ").request();
    }

    /**
     * The latest wave handed out past a stuck one (see {@link #getRequest}): stacks may follow up in any wave up to it, since
     * an earlier wave's block never closes the way to a later one. Memory only.
     */
    private int frontier;

    /**
     * Blocks of the next {@code build.plan_lookahead_waves} waves after {@code wave} that may be handed out while builders
     * finish wave {@code wave} (see {@link #getRequest}), and that stacks may follow up into: each more than
     * {@code build.plan_lookahead_clearance} blocks (on every axis) from every block of an earlier wave still to place and
     * from its planned standing spots. The plan only checked a wave with the earlier ones finished, so a block placed early
     * stays clear of where their last blocks are placed from; one that blocks a way anyway is caught by the give-ups (the
     * blocked block leaves the plan). Shuffled, nearer waves first. With {@code spotsOnly} (a stack's follow-ups, which are
     * next to the block just placed and so always near the wave's other blocks) only the earlier blocks' standing spots and
     * the cells above them are kept clear, {@code clearance} blocks around (0: the cells themselves); otherwise the earlier
     * blocks too, and a clearance of 0 turns the lookahead off.
     */
    private List<BlueprintBlockInfo> lookahead(BlockPos base, BlueprintTemplateSettings settings, int wave, int clearance, boolean spotsOnly) {
        int waves = Tuning.BUILD_PLAN_LOOKAHEAD_WAVES.get();
        if (clearance < 0 || (clearance == 0 && !spotsOnly) || waves <= 0) {
            return List.of();
        }
        // Every position to keep clear, with the lowest wave that needs it (a block of a later wave than that keeps clear).
        Map<BlockPos, Integer> keepClear = new java.util.HashMap<>();
        List<BlueprintBlockInfo> next = new ArrayList<>();
        for (BlueprintBlockInfo info : blockInfoList) {
            int infoWave = buildPlan.wave(info.getPos());
            if (infoWave == BuildPlan.UNPLANNED || infoWave > wave + waves) {
                continue;
            }
            if (infoWave < wave + waves) {
                if (!spotsOnly) {
                    keepClear.merge(targetPosition(base, settings, info), infoWave, Math::min);
                }
                for (BlockPos spot : plannedSpots(base, settings, info)) {
                    keepClear.merge(spot, infoWave, Math::min);
                    if (spotsOnly) {
                        keepClear.merge(spot.above(), infoWave, Math::min);
                    }
                }
            }
            if (infoWave > wave && info.timesGivenUp() < PLAN_GIVE_UPS) {
                next.add(info);
            }
        }
        next.removeIf(info -> clearedBefore(targetPosition(base, settings, info), keepClear, clearance, buildPlan.wave(info.getPos())));
        Collections.shuffle(next);
        next.sort(Comparator.comparingInt(info -> buildPlan.wave(info.getPos())));
        return next;
    }

    /** Whether a position within {@code distance} blocks (on every axis) of {@code pos} is kept clear for a wave before {@code wave}. */
    private static boolean clearedBefore(BlockPos pos, Map<BlockPos, Integer> keepClear, int distance, int wave) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -distance; dx <= distance; dx++) {
            for (int dy = -distance; dy <= distance; dy++) {
                for (int dz = -distance; dz <= distance; dz++) {
                    Integer needed = keepClear.get(at.setWithOffset(pos, dx, dy, dz));
                    if (needed != null && needed < wave) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Distance steps (blocks) inside which a wave's blocks count as equally near the builder (see {@link #getRequest}). */
    private static final int NEAR_STEP = 4;

    /** Times builders may give up a planned block before the plan stops handing it out (see {@link #getRequest}). */
    public static final int PLAN_GIVE_UPS = 2;

    /** Whether {@code pos} is within 2 blocks of any of {@code others} (a block another builder works on). */
    private static boolean nearAny(BlockPos pos, List<BlockPos> others) {
        for (BlockPos other : others) {
            if (Math.abs(pos.getX() - other.getX()) <= 2 && Math.abs(pos.getY() - other.getY()) <= 2 && Math.abs(pos.getZ() - other.getZ()) <= 2) {
                return true;
            }
        }
        return false;
    }

    /** What a request search found: the request handed out (or {@code null}), and how many candidates other builders have. */
    private record Search(@Nullable BlueprintRequest request, int assigned) {
    }

    /**
     * Where builders can stand to place {@code info}, from the plan (world positions; empty without a plan, and for a block
     * builders gave up {@link #PLAN_GIVE_UPS} times: they then look for a spot the normal way).
     */
    private List<BlockPos> plannedSpots(BlockPos base, BlueprintTemplateSettings settings, BlueprintBlockInfo info) {
        if (buildPlan == null || Tuning.BUILD_FOLLOW_PLAN.get() == 0 || info.timesGivenUp() >= PLAN_GIVE_UPS) {
            return List.of();
        }
        return buildPlan.spots(info.getPos()).stream().map(spot -> base.offset(adjustForStructureSettings(settings, spot))).toList();
    }

    private boolean reachable(Predicate<BlockPos> canReach, BlockPos pos, List<BlockPos> spots) {
        return !spots.isEmpty() && canReach instanceof PlannedReach planned ? planned.canReach(pos, spots) : canReach.test(pos);
    }

    private Search search(List<BlueprintBlockInfo> candidates, ServerLevel level, BlockPos base, BlueprintTemplateSettings settings, Predicate<BlockPos> canReach, String what) {
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

        for (BlueprintBlockInfo blockInfo : candidates) {
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
            List<BlockPos> spots = plannedSpots(base, settings, blockInfo);
            if (!reachable(canReach, infoAbsolutePos, spots)) {
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
                logRequestSearch(checked, candidates.size(), coolingDown, doorsWaiting, unreachable, notPlaceable, assigned, what + "handing out a free block");
                BlueprintRequest request = blockInfo.getRequest(level.getGameTime(), infoAbsolutePos);
                request.setStandingSpots(spots);
                return new Search(request, assigned);
            }
            assigned++;
        }
        logRequestSearch(checked, candidates.size(), coolingDown, doorsWaiting, unreachable, notPlaceable, assigned, what + "nothing to hand out");
        lastSearchFoundUnreachable = unreachable > 0;
        return new Search(null, assigned);
    }

    /** The pre-baked build plan this build follows, or {@code null} (for GameTests and the recorder; changes nothing). */
    @Nullable
    public BuildPlan buildPlan() {
        return buildPlan;
    }

    /**
     * The lowest wave of the build plan with blocks still to place (D82), or {@link BuildPlan#UNPLANNED} when the build
     * doesn't follow a plan or only blocks the plan leaves out (or that left it, given up) are left.
     */
    private int currentWave() {
        int wave = BuildPlan.UNPLANNED;
        if (buildPlan != null && Tuning.BUILD_FOLLOW_PLAN.get() != 0) {
            for (BlueprintBlockInfo info : blockInfoList) {
                if (info.timesGivenUp() < PLAN_GIVE_UPS) { // a block builders gave up on has left the plan (see getRequest)
                    wave = Math.min(wave, buildPlan.wave(info.getPos()));
                }
            }
        }
        return wave;
    }

    /**
     * A builder that has just placed a block at {@code placed} and still carries more of {@code item} (D79) asks for its
     * next block here instead of walking back to the easel and its chest: the nearest free block of that item in the same
     * layer (same height) whose spot is reachable from where it stands, or null. Never a door, never a block whose spot
     * below is still an unplaced block of this plan (so nothing is put on air the layer order would have filled first),
     * and at most {@link #FOLLOW_UP_REACH_TESTS} path searches. A build that follows a plan (D82) takes only blocks of the
     * current wave (or up to the wave handed out past a stuck one, {@code frontier}), or of the next ones well clear of the
     * blocks before them (see {@code lookahead}), so a builder with a stack never closes a way in that a later wave's block would.
     */
    public @Nullable BlueprintRequest getFollowUpRequest(ServerLevel level, BlockPos base, BlueprintTemplateSettings settings,
                                                         Predicate<BlockPos> canReach, Item item, BlockPos placed) {
        long now = level.getGameTime();
        int wave = currentWave();
        // The next waves' blocks clear of the earlier ones' (see lookahead): a builder that took one keeps going with its stack.
        java.util.Set<BlueprintBlockInfo> ahead = wave == BuildPlan.UNPLANNED ? java.util.Set.of()
                : java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        if (wave != BuildPlan.UNPLANNED) {
            ahead.addAll(lookahead(base, settings, wave, Tuning.BUILD_PLAN_FOLLOW_UP_CLEARANCE.get(), true));
        }
        java.util.Set<BlockPos> unplaced = new java.util.HashSet<>();
        List<Map.Entry<BlueprintBlockInfo, BlockPos>> candidates = new java.util.ArrayList<>();
        for (BlueprintBlockInfo blockInfo : blockInfoList) {
            BlockPos pos = targetPosition(base, settings, blockInfo);
            unplaced.add(pos);
            if (wave != BuildPlan.UNPLANNED && buildPlan.wave(blockInfo.getPos()) > Math.max(wave, frontier) && !ahead.contains(blockInfo)) {
                continue;
            }
            if (blockInfo.isDoor() || pos.getY() != placed.getY() || !blockInfo.isFor(item) || blockInfo.isCoolingDown(now)
                    || !blockInfo.hasAvailableRequest(now) || !canPlaceAt(level, pos)) {
                continue;
            }
            candidates.add(Map.entry(blockInfo, pos));
        }
        candidates.sort(java.util.Comparator.comparingDouble(entry -> entry.getValue().distSqr(placed)));
        int tests = 0;
        for (Map.Entry<BlueprintBlockInfo, BlockPos> candidate : candidates) {
            if (unplaced.contains(candidate.getValue().below())) {
                continue;
            }
            if (tests++ >= FOLLOW_UP_REACH_TESTS) {
                break;
            }
            if (canReach.test(candidate.getValue())) {
                return candidate.getKey().getRequest(now, candidate.getValue());
            }
        }
        return null;
    }

    /** Path searches a follow-up request search may make (see {@link #getFollowUpRequest}). */
    public static final int FOLLOW_UP_REACH_TESTS = 6;

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

    private void logRequestSearch(int checked, int of, int coolingDown, int doorsWaiting, int unreachable, int notPlaceable, int assigned, String result) {
        if (!DebugLog.isOn(DebugCategory.BUILD)) {
            return;
        }
        DebugLog.event(DebugCategory.BUILD, null, () -> "request search: checked %d of %d blocks (%d remaining; %d cooling down after failed placing, %d doors waiting for other blocks, %d not reachable, %d not placeable, %d already assigned): %s"
                .formatted(checked, of, blockInfoList.size(), coolingDown, doorsWaiting, unreachable, notPlaceable, assigned, result));
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
