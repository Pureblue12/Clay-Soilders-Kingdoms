package net.bumblebee.claysoldiers.scaffold;

import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.BlueprintRequest;
import net.bumblebee.claysoldiers.blueprint.BlueprintTemplateSettings;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.door.ChainedPath;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Scaffolding in the world (stage 3). Builders get scaffold work through the easel, like blueprint blocks:
 * <ul>
 *     <li>When no structure block is reachable and some are out of reach, the easel's scaffold is planned
 *     ({@link ScaffoldPlanner}, approach {@code scaffold.approach}) and its blocks are handed out lowest first.
 *     Structures with nothing above reach never get a job, so their build is unchanged.</li>
 *     <li>When the structure is finished, builders take the scaffold down from the top (and carry it back when
 *     {@code scaffold.refund} is 1). If they can't for {@code scaffold.teardown_stuck_ticks}, it is removed at once.</li>
 *     <li>Interrupted builds (easel broken, blueprint changed, easel gone while the world was closed) are cleaned up
 *     at once; jobs are saved ({@link ScaffoldSavedData}).</li>
 *     <li>A scaffold position that holds something else now (a player changed it) is never removed; that is
 *     recorded and nearby players are told.</li>
 * </ul>
 * Everything is recorded in the SCAFFOLD category.
 */
public final class Scaffolds {
    /** Next game time each easel may (re)plan, so a stuck build doesn't plan every tick. Memory only. */
    private static final Map<String, Long> NEXT_PLAN = new HashMap<>();
    /** Per easel (same key): since when no structure block was placed while builders found nothing in reach. */
    private static final Map<String, Long> STUCK_SINCE = new HashMap<>();
    private static long lastSweep = Long.MIN_VALUE;
    /** Clay soldiers' follow range (their attribute), which limits how long a path they follow. */
    private static final int BUILDER_FOLLOW_RANGE = 16;
    /** How much shorter than the builders' real path limit the planner's paths must be. */
    private static final int PATH_MARGIN = 8;
    /** Players this close to a kept block are told about it. */
    private static final int TELL_RADIUS = 32;

    private Scaffolds() {
    }

    // Requests for builders (through the easel)

    /**
     * The next scaffold block to put up for the easel's build, when no structure block was reachable. Plans (or adds
     * to) the job when the plan's last request search found blocks out of reach.
     */
    @Nullable
    public static BlueprintRequest placeRequest(ServerLevel level, BlockPos easel, ServerBlueprintPlan plan, BlockPos base,
                                                BlueprintTemplateSettings settings, Predicate<BlockPos> canReach) {
        ScaffoldSavedData data = ScaffoldSavedData.get(level);
        Optional<ScaffoldJob> job = data.job(easel);
        if ((job.isEmpty() || nothingLeftToPlace(level, job.get())) && plan.lastSearchFoundUnreachable()) {
            String key = key(level, easel);
            if (level.getGameTime() >= NEXT_PLAN.getOrDefault(key, Long.MIN_VALUE)) {
                NEXT_PLAN.put(key, level.getGameTime() + Tuning.SCAFFOLD_REPLAN_TICKS.get());
                BlockPos home = canReach instanceof ScaffoldHome builder && builder.scaffoldHome() != null ? builder.scaffoldHome() : easel;
                job = plan(level, easel, home, plan, base, settings, job.orElse(null));
            }
        }
        if (job.isEmpty()) {
            return null;
        }
        ScaffoldJob current = job.get();
        if (current.placed().isEmpty() && !stuckLongEnough(level, easel)) {
            return null; // maybe only one builder is boxed in for a moment: start the scaffold once the whole build is stuck
        }
        Block material = material(current);
        for (BlockPos pos : current.planned()) {
            if (current.placed().contains(pos)) {
                continue;
            }
            // columns: only the lowest missing cell of a column can go up next (a column on an upper floor above
            // another one at the same x and z is a separate column, D75)
            if (current.columns() && current.planned().contains(pos.below()) && !current.placed().contains(pos.below())) {
                continue;
            }
            if (busy(level, current, pos) || !ServerBlueprintPlan.canPlaceAt(level, pos)) {
                continue;
            }
            if (canReach.test(pos)) {
                ScaffoldRequest request = new ScaffoldRequest(material.asItem(), pos, level.getGameTime(), false, refund());
                current.active().put(pos, request);
                return request;
            }
        }
        return null;
    }

    /**
     * Whether builders have found nothing in reach for {@code scaffold.start_after_stuck_ticks} without any structure
     * block being placed in between ({@link #structurePlaced} resets it).
     */
    private static boolean stuckLongEnough(ServerLevel level, BlockPos easel) {
        long since = STUCK_SINCE.computeIfAbsent(key(level, easel), k -> level.getGameTime());
        return level.getGameTime() - since >= Tuning.SCAFFOLD_START_AFTER_STUCK_TICKS.get();
    }

    /** A structure block of the easel's build was placed: builders aren't stuck. */
    public static void structurePlaced(ServerLevel level, BlockPos easel) {
        STUCK_SINCE.remove(key(level, easel));
    }

    private static String key(ServerLevel level, BlockPos easel) {
        return level.dimension().location() + "|" + easel.asLong();
    }

    /**
     * The next scaffold block to take down once the structure is finished: only blocks at the highest level, so the
     * builders always have the steps below to stand on.
     */
    @Nullable
    public static BlueprintRequest removeRequest(ServerLevel level, BlockPos easel, Predicate<BlockPos> canReach) {
        ScaffoldSavedData data = ScaffoldSavedData.get(level);
        Optional<ScaffoldJob> found = data.job(easel);
        if (found.isEmpty()) {
            return null;
        }
        ScaffoldJob job = found.get();
        Block material = material(job);
        forgetChanged(level, job);
        if (job.placed().isEmpty()) {
            finish(level, data, job);
            return null;
        }
        long now = level.getGameTime();
        if (job.lastProgress() < 0) {
            job.setLastProgress(now);
            DebugLog.event(DebugCategory.SCAFFOLD, null, "structure at easel %s finished: taking down %s scaffold blocks", easel, job.placed().size());
        } else if (now - job.lastProgress() > Tuning.SCAFFOLD_TEARDOWN_STUCK_TICKS.get()) {
            DebugLog.event(DebugCategory.SCAFFOLD, null, "stuck taking down scaffold at easel %s for %s ticks: removing it at once", easel, now - job.lastProgress());
            cleanup(level, easel, "teardown stuck");
            return null;
        }
        List<BlockPos> highest;
        if (job.columns()) {
            // A column comes down whole from its base (its cells are removed top-down).
            highest = job.placed().stream().filter(pos -> job.column(pos).getFirst().equals(pos)).sorted().toList();
        } else {
            int top = job.placed().stream().mapToInt(BlockPos::getY).max().orElseThrow();
            highest = job.placed().stream().filter(pos -> pos.getY() == top).sorted().toList();
        }
        for (BlockPos pos : highest) {
            if (!busy(level, job, pos) && canReach.test(pos)) {
                ScaffoldRequest request = new ScaffoldRequest(material.asItem(), pos, now, true, refund());
                job.active().put(pos, request);
                return request;
            }
        }
        return null;
    }

    /**
     * A builder carries out a scaffold request at the easel: places the scaffold block, or removes it. A position that
     * holds something else now is left alone (and reported).
     *
     * @return whether it was done
     */
    public static boolean complete(ServerLevel level, BlockPos easel, ScaffoldRequest request) {
        ScaffoldSavedData data = ScaffoldSavedData.get(level);
        Optional<ScaffoldJob> found = data.job(easel);
        if (found.isEmpty()) {
            return false;
        }
        ScaffoldJob job = found.get();
        BlockPos pos = request.getPos();
        job.active().remove(pos);
        Block material = material(job);
        if (request.isRemoval()) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(material)) {
                job.markRemoved(pos);
                data.setDirty();
                kept(level, pos, state);
                return false;
            }
            // Climbed columns come down whole, top-down, from their base; steps one at a time.
            List<BlockPos> remove = new ArrayList<>(job.columns() ? job.column(pos) : List.of(pos));
            remove.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed());
            int removed = 0;
            for (BlockPos cell : remove) {
                BlockState there = level.getBlockState(cell);
                if (there.is(material)) {
                    level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    removed++;
                } else if (!there.isAir()) {
                    kept(level, cell, there);
                }
                job.markRemoved(cell);
            }
            request.setRemovedCount(removed);
            job.setLastProgress(level.getGameTime());
            data.setDirty();
            int count = removed;
            DebugLog.event(DebugCategory.SCAFFOLD, null, () -> "removed %d scaffold block(s) at %s (%d left)".formatted(count, pos.toShortString(), job.placed().size()));
            if (job.placed().isEmpty()) {
                finish(level, data, job);
            }
            return true;
        }
        if (!job.planned().contains(pos) || !ServerBlueprintPlan.canPlaceAt(level, pos)) {
            return false;
        }
        level.setBlock(pos, placedState(level, pos, material), Block.UPDATE_ALL);
        job.markPlaced(pos);
        data.setDirty();
        DebugLog.event(DebugCategory.SCAFFOLD, null, "placed scaffold at %s (%s of %s)", pos, job.placed().size(), job.planned().size());
        return true;
    }

    // Planning

    /**
     * Plans the easel's scaffold now (if its structure needs one) and returns the job. Used by the easel when builders
     * get stuck, and by dev tools and tests.
     */
    public static Optional<ScaffoldJob> ensureJob(ServerLevel level, EaselBlockEntity easel) {
        if (!(easel.getPlan() instanceof ServerBlueprintPlan plan) || easel.getTemplateSettings() == null) {
            return Optional.empty();
        }
        BlueprintTemplateSettings settings = easel.getTemplateSettings();
        BlockPos base = EaselBlockEntity.templateBase(easel.getBlockPos(), easel.getFacing(), settings);
        return plan(level, easel.getBlockPos(), easel.getBlockPos(), plan, base, settings, ScaffoldSavedData.get(level).job(easel.getBlockPos()).orElse(null));
    }

    private static Optional<ScaffoldJob> plan(ServerLevel level, BlockPos easel, BlockPos home, ServerBlueprintPlan plan, BlockPos base,
                                              BlueprintTemplateSettings settings, @Nullable ScaffoldJob existing) {
        long start = System.nanoTime();
        ScaffoldInput input = input(level, easel, home, plan.targetPositions(base, settings), plan.doorPositions(base, settings),
                existing == null ? Set.of() : existing.placed(), plan.targetStates(base, settings));
        if (input.structure().isEmpty() || ScaffoldPlanner.aboveReach(input).isEmpty()) {
            return Optional.ofNullable(existing);
        }
        ScaffoldApproach approach = ScaffoldApproach.inGame(Tuning.SCAFFOLD_APPROACH.get());
        if (approach.columns() && !(BuiltInRegistries.BLOCK.get(Tuning.SCAFFOLD_MATERIAL.get()) instanceof ScaffoldingBlock)) {
            DebugLog.event(DebugCategory.SCAFFOLD, null, "scaffold.material %s can't be climbed inside: using step pillars", Tuning.SCAFFOLD_MATERIAL.get());
            approach = ScaffoldApproach.STEP_PILLARS;
        }
        if (existing != null && existing.columns() != approach.columns()) {
            approach = existing.columns() ? ScaffoldApproach.CLIMBED_COLUMNS : ScaffoldApproach.STEP_PILLARS;
        }
        // Builders inside a column stretch up for a block no column reaches the usual way (D78), so the planned
        // scaffold counts as enough when it reaches every block that way.
        ScaffoldInput stretched = input.withColumnReachBonus(Tuning.SCAFFOLD_COLUMN_REACH_BONUS.get());
        if (existing != null && !existing.planned().isEmpty()
                && ScaffoldPlanner.simulate(stretched, existing.planned(), existing.columns()).stuck().isEmpty()) {
            // The scaffold already planned still reaches every block left: builders were only briefly stuck. Planning
            // from scratch would pick other spots and add columns needlessly.
            DebugLog.event(DebugCategory.SCAFFOLD, null, "easel %s: the planned scaffold still reaches every block left, nothing added", easel);
            return Optional.of(existing);
        }
        ScaffoldPlan usual = ScaffoldPlanner.plan(input, approach);
        ScaffoldPlan scaffold = usual;
        if (!usual.complete() && approach.columns() && stretched.columnReachBonus() > 0) {
            // Some block no column reaches the usual way (the church's spire top, 9 above the highest column cell under
            // it): plan again letting builders inside a column stretch up, and keep that plan if it leaves fewer stuck (D78).
            ScaffoldPlan longer = ScaffoldPlanner.plan(stretched, approach);
            if (longer.unreachable().size() < usual.unreachable().size()) {
                DebugLog.event(DebugCategory.SCAFFOLD, null, () -> "easel %s: %d block(s) only in reach stretching up from a column (scaffold.column_reach_bonus %d); usual reach: %s"
                        .formatted(easel.toShortString(), usual.unreachable().size() - longer.unreachable().size(), stretched.columnReachBonus(), usual.describe()));
                scaffold = longer;
            }
        }
        List<BlockPos> more = added(scaffold, existing);
        if (more.isEmpty() && !scaffold.complete() && approach.columns()) {
            // Nothing more to put up, and blocks are still stuck: the only floor within reach of them may be farther than
            // the usual limits. Keep L3's back corner towers: the banner on top is in reach only from a column in the
            // tower's top room, about 60 walked blocks from the chest up through the tower, and builders walk that far
            // (ChainedPath, D76). Plan again with the walks a builder makes in up to ChainedPath.MAX_SEARCHES searches,
            // and keep that plan if it adds scaffold and leaves fewer stuck (D80). Only this late, so every plan the usual
            // limits make stays as it is.
            ScaffoldInput far = stretched.withPathLimit(ChainedPath.MAX_SEARCHES * (BUILDER_FOLLOW_RANGE
                    + Math.max(Tuning.BUILD_PATH_RANGE_BONUS.get(), Tuning.SCAFFOLD_PATH_RANGE_BONUS.get())) - PATH_MARGIN);
            ScaffoldPlan farther = ScaffoldPlanner.plan(far, approach);
            List<BlockPos> farMore = added(farther, existing);
            if (!farMore.isEmpty() && farther.unreachable().size() < scaffold.unreachable().size()) {
                ScaffoldPlan before = scaffold;
                DebugLog.event(DebugCategory.SCAFFOLD, null, () -> "easel %s: %d block(s) only in reach walking more than %d blocks (up to %d); before: %s"
                        .formatted(easel.toShortString(), before.unreachable().size() - farther.unreachable().size(), stretched.columnPath(), far.columnPath(), before.describe()));
                scaffold = farther;
                more = farMore;
            }
        }
        ScaffoldPlan chosen = scaffold;
        List<BlockPos> extra = more;
        if (extra.isEmpty()) {
            DebugLog.event(DebugCategory.SCAFFOLD, null, "stuck at easel %s: %s", easel, chosen.describe());
            return Optional.ofNullable(existing);
        }
        int total = extra.size() + (existing == null ? 0 : existing.planned().size());
        if (total > Tuning.SCAFFOLD_MAX_BLOCKS.get()) {
            DebugLog.event(DebugCategory.SCAFFOLD, null, "stuck at easel %s: needs %s scaffold blocks, more than scaffold.max_blocks (%s)",
                    easel, total, Tuning.SCAFFOLD_MAX_BLOCKS.get());
            return Optional.ofNullable(existing);
        }
        ScaffoldJob job = existing != null ? existing : new ScaffoldJob(easel, Tuning.SCAFFOLD_MATERIAL.get(), List.of(), Set.of(), approach.columns());
        job.addPlanned(extra);
        ScaffoldSavedData.get(level).put(job);
        long millis = (System.nanoTime() - start) / 1_000_000;
        DebugLog.event(DebugCategory.SCAFFOLD, null, () -> "planned %d scaffold blocks at easel %s in %d ms (%s)".formatted(extra.size(), easel.toShortString(),
                millis, chosen.describe()));
        return Optional.of(job);
    }

    /** The scaffold cells of {@code plan} the job hasn't planned yet. */
    private static List<BlockPos> added(ScaffoldPlan plan, @Nullable ScaffoldJob existing) {
        List<BlockPos> more = new ArrayList<>(plan.scaffold());
        if (existing != null) {
            more.removeAll(existing.planned());
        }
        return more;
    }

    /**
     * What the planner needs from the world: the region around the structure, its solid cells, the cells scaffold can't
     * use, and the structure blocks still to place. The build's own placed {@code scaffold} counts as free (a re-plan
     * simulates it as scaffold again). Doors are left out of the blocks to place and wooden doors already standing
     * count as open: doors are handed out last and soldiers open wooden doors, so a doorway is a way in (D71; the
     * planner placed the chapel's door first and found the nave sealed, so no column inside it).
     */
    static ScaffoldInput input(ServerLevel level, BlockPos easel, BlockPos home, List<BlockPos> targets, Set<BlockPos> doors, Set<BlockPos> scaffold,
                               Map<BlockPos, BlockState> future) {
        BoundingBox box = BoundingBox.encapsulatingPositions(targets).orElseThrow();
        // The plan only lists blocks still to place, so the region reaches down to the easel's ground (the site's base),
        // and it includes where the builders come from.
        int bottom = Math.min(Math.min(box.minY(), easel.getY()), home.getY()) - 2;
        BoundingBox region = new BoundingBox(Math.min(box.minX() - ScaffoldInput.MARGIN, home.getX() - 3), bottom,
                Math.min(box.minZ() - ScaffoldInput.MARGIN, home.getZ() - 3), Math.max(box.maxX() + ScaffoldInput.MARGIN, home.getX() + 3),
                box.maxY() + 3, Math.max(box.maxZ() + ScaffoldInput.MARGIN, home.getZ() + 3));
        Set<BlockPos> solid = new HashSet<>();
        Set<BlockPos> blocked = new HashSet<>(targets);
        Set<BlockPos> noFooting = new HashSet<>();
        Set<BlockPos> overhead = new HashSet<>();
        BlockPos.betweenClosedStream(region).forEach(pos -> {
            if (scaffold.contains(pos)) {
                return; // the build's own scaffold: the planner puts it in again (climbable), it isn't in the way
            }
            BlockState state = level.getBlockState(pos);
            // Scaffolding needs a full top face under it (or scaffolding beside it): on a stair, slab or carpet floor it
            // falls at once, and the column is never there to climb (keep L2's annex pews, D77). Blocks still to place count
            // as what they will be.
            BlockState floor = future.getOrDefault(pos, state);
            VoxelShape shape = floor.getCollisionShape(level, pos);
            if (!shape.isEmpty() && !floor.isFaceSturdy(level, pos, Direction.UP)) {
                noFooting.add(pos.immutable());
            }
            // A block that doesn't reach down to its cell's bottom (a hanging lantern, a top slab) leaves room under it for a
            // soldier, under half a block tall: the church's side porch, the only way into its nave, has a hanging lantern
            // at head height, so the planner found the nave sealed and no column inside it (D80).
            if (!shape.isEmpty() && shape.min(Direction.Axis.Y) > 0) {
                overhead.add(pos.immutable());
            }
            boolean woodenDoor = state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand();
            if (!woodenDoor && !state.getCollisionShape(level, pos).isEmpty()) {
                solid.add(pos.immutable());
            }
            if (!ServerBlueprintPlan.canPlaceAt(level, pos)) {
                blocked.add(pos.immutable());
            }
        });
        blocked.add(easel);
        blocked.add(easel.above());
        blocked.add(easel.above(2));
        List<BlockPos> remaining = targets.stream().filter(pos -> !doors.contains(pos) && ServerBlueprintPlan.canPlaceAt(level, pos)).toList();
        // Builders come from their storage (home), and vanilla only follows paths shorter than their follow range (16, plus the
        // bonus while scaffolding). The planner keeps a margin (the way from the storage, and vanilla's search, which
        // gives up on long winding routes before the limit).
        int maxPath = BUILDER_FOLLOW_RANGE + Tuning.SCAFFOLD_PATH_RANGE_BONUS.get() - PATH_MARGIN;
        // A column's base may be as far as builders walk while they work for a build (the larger bonus, as
        // BuildBlueprintGoal sets it): the keep's far roof corners need columns more than maxPath away (D75).
        int columnPath = BUILDER_FOLLOW_RANGE + Math.max(Tuning.BUILD_PATH_RANGE_BONUS.get(), Tuning.SCAFFOLD_PATH_RANGE_BONUS.get()) - PATH_MARGIN;
        return new ScaffoldInput(region, solid, blocked, remaining, home, maxPath, Math.max(maxPath, columnPath), noFooting, overhead, 0);
    }

    /**
     * Whether the easel's scaffolding has started going up (builders ran out of reachable blocks) and isn't all up yet.
     * Planning can happen earlier; the work within reach comes first.
     */
    public static boolean isGoingUp(ServerLevel level, BlockPos easel) {
        return ScaffoldSavedData.get(level).job(easel)
                .filter(job -> !job.placed().isEmpty() && job.placed().size() < job.planned().size()).isPresent();
    }

    /** Whether a builder is carrying one of the easel's scaffold blocks up right now (not yet placed). */
    public static boolean isScaffoldBeingCarried(ServerLevel level, BlockPos easel) {
        return ScaffoldSavedData.get(level).job(easel)
                .filter(job -> List.copyOf(job.active().keySet()).stream().anyMatch(pos -> !job.placed().contains(pos) && busy(level, job, pos)))
                .isPresent();
    }

    /** Whether the easel's build has scaffolding (builders working for it get a bigger path search). */
    public static boolean hasJob(ServerLevel level, BlockPos easel) {
        return ScaffoldSavedData.get(level).job(easel).isPresent();
    }

    // Placing directly (dev tools and tests)

    /** Places the next planned scaffold block at once, as a builder would (dev tools and tests). */
    public static boolean placeNext(ServerLevel level, BlockPos easel) {
        Optional<ScaffoldJob> job = ScaffoldSavedData.get(level).job(easel);
        if (job.isEmpty()) {
            return false;
        }
        for (BlockPos pos : job.get().planned()) {
            if (!job.get().placed().contains(pos) && ServerBlueprintPlan.canPlaceAt(level, pos)) {
                return complete(level, easel, new ScaffoldRequest(material(job.get()).asItem(), pos, level.getGameTime(), false, false));
            }
        }
        return false;
    }

    // Cleanup

    /**
     * Removes the easel's scaffold at once and forgets the job. Positions that hold something else now are kept and
     * reported. With {@code scaffold.refund} 1 the removed blocks drop as items.
     *
     * @return how many scaffold blocks were removed
     */
    public static int cleanup(ServerLevel level, BlockPos easel, String why) {
        ScaffoldSavedData data = ScaffoldSavedData.get(level);
        Optional<ScaffoldJob> found = data.job(easel);
        if (found.isEmpty()) {
            return 0;
        }
        ScaffoldJob job = found.get();
        Block material = material(job);
        boolean refund = refund();
        int removed = 0;
        // (Cleanup of scaffolding columns removes them top-down, so nothing is left unsupported to fall.)
        int kept = 0;
        List<BlockPos> topDown = new ArrayList<>(job.placed());
        topDown.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).reversed());
        for (BlockPos pos : topDown) {
            BlockState state = level.getBlockState(pos);
            if (state.is(material)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                if (refund) {
                    Block.popResource(level, pos, new ItemStack(material.asItem()));
                }
                removed++;
            } else if (!state.isAir()) {
                kept(level, pos, state);
                kept++;
            }
        }
        data.remove(easel);
        int keptBlocks = kept;
        int removedBlocks = removed;
        DebugLog.event(DebugCategory.SCAFFOLD, null, () -> "cleanup at easel %s (%s): removed %d scaffold blocks%s".formatted(easel.toShortString(), why,
                removedBlocks, keptBlocks > 0 ? ", kept " + keptBlocks + " changed by a player" : ""));
        return removed;
    }

    /**
     * Called every server tick: sweeps every {@code scaffold.sweep_ticks}.
     */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        if (lastSweep != Long.MIN_VALUE && now >= lastSweep && now - lastSweep < Tuning.SCAFFOLD_SWEEP_TICKS.get()) {
            return;
        }
        lastSweep = now;
        sweep(server);
    }

    /**
     * Cleans up jobs whose easel is gone or has no plan any more. Jobs in unloaded chunks wait until they are loaded.
     */
    public static void sweep(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            ScaffoldSavedData data = ScaffoldSavedData.get(level);
            for (ScaffoldJob job : List.copyOf(data.jobs())) {
                if (!level.isLoaded(job.easel())) {
                    continue;
                }
                if (!(level.getBlockEntity(job.easel()) instanceof EaselBlockEntity easel)) {
                    cleanup(level, job.easel(), "easel gone");
                } else if (!easel.hasPlan()) {
                    cleanup(level, job.easel(), "the easel has no plan");
                }
            }
        }
    }

    // Helpers

    /** The state a scaffold block is placed in: scaffolding gets its support distance (a column stands on its base). */
    private static BlockState placedState(ServerLevel level, BlockPos pos, Block material) {
        BlockState state = material.defaultBlockState();
        if (material instanceof ScaffoldingBlock) {
            state = state.setValue(ScaffoldingBlock.DISTANCE, ScaffoldingBlock.getDistance(level, pos)).setValue(ScaffoldingBlock.BOTTOM, false);
        }
        return state;
    }

    /** The material block of a job (for the climber and tools). */
    static Block materialOf(ScaffoldJob job) {
        return material(job);
    }

    private static Block material(ScaffoldJob job) {
        Block block = BuiltInRegistries.BLOCK.get(job.material());
        return block == Blocks.AIR ? Blocks.SCAFFOLDING : block;
    }

    private static boolean refund() {
        return Tuning.SCAFFOLD_REFUND.get() == 1;
    }

    /** Whether a builder is working on this position right now (a request that isn't done, cancelled or stale). */
    private static boolean busy(ServerLevel level, ScaffoldJob job, BlockPos pos) {
        ScaffoldRequest request = job.active().get(pos);
        if (request == null) {
            return false;
        }
        boolean fresh = !request.isCancelled() && !request.isFinished()
                && level.getGameTime() - request.getStart() < Tuning.SCAFFOLD_STALE_TICKS.get();
        if (!fresh) {
            job.active().remove(pos);
        }
        return fresh;
    }

    private static boolean nothingLeftToPlace(ServerLevel level, ScaffoldJob job) {
        return job.planned().stream().noneMatch(pos -> !job.placed().contains(pos) && ServerBlueprintPlan.canPlaceAt(level, pos));
    }

    /** Placed positions that hold something else now: forgotten (never removed) and reported. */
    private static void forgetChanged(ServerLevel level, ScaffoldJob job) {
        Block material = material(job);
        for (BlockPos pos : List.copyOf(job.placed())) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(material)) {
                job.markRemoved(pos);
                ScaffoldSavedData.get(level).setDirty();
                if (!state.isAir()) {
                    kept(level, pos, state);
                }
            }
        }
    }

    private static void finish(ServerLevel level, ScaffoldSavedData data, ScaffoldJob job) {
        data.remove(job.easel());
        DebugLog.event(DebugCategory.SCAFFOLD, null, "all scaffold of easel %s removed", job.easel());
    }

    /** A scaffold position a player changed: it is left alone, recorded, and nearby players are told. */
    private static void kept(ServerLevel level, BlockPos pos, BlockState state) {
        DebugLog.event(DebugCategory.SCAFFOLD, null, "kept %s at %s: it isn't scaffold any more (a player changed it)", state.getBlock().getName().getString(), pos);
        Component message = Component.literal("Scaffold cleanup left " + state.getBlock().getName().getString() + " at " + pos.toShortString()
                + " (it replaced a scaffold block).").withStyle(ChatFormatting.YELLOW);
        for (ServerPlayer player : level.players()) {
            if (player.blockPosition().closerThan(pos, TELL_RADIUS)) {
                player.sendSystemMessage(message);
            }
        }
    }
}
