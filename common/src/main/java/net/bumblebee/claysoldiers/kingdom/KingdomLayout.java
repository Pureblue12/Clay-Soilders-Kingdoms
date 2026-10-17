package net.bumblebee.claysoldiers.kingdom;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.site.BuildSitePlanner;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.kingdom.layout.Cell;
import net.bumblebee.claysoldiers.kingdom.layout.Ground;
import net.bumblebee.claysoldiers.kingdom.layout.KingdomPlanner;
import net.bumblebee.claysoldiers.kingdom.layout.MasterPlan;
import net.bumblebee.claysoldiers.kingdom.layout.Rect;
import net.bumblebee.claysoldiers.kingdom.layout.Side;
import net.bumblebee.claysoldiers.structure.StructureBlueprints;
import net.bumblebee.claysoldiers.structure.StructureMeta;
import net.bumblebee.claysoldiers.structure.StructureRegistry;
import net.bumblebee.claysoldiers.terrain.TerrainScan;
import net.bumblebee.claysoldiers.terrain.TerrainScanner;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stage 6, the world side of the kingdom planner ({@link KingdomPlanner}): once its keep is finished, a kingdom looks at
 * the land around it and draws up its master plan, saves it ({@link Kingdom#plan()}), grows its claim to hold it and lays
 * its roads as dirt paths at once (soldiers don't lay roads yet). From then on {@link KingdomEconomy} builds each
 * building on its planned plot; when that plot can't be used any more (a player built on it, the ground changed) it looks
 * for a plot the stage 5 way.
 * <p>
 * A kingdom that already started buildings after its keep before stage 6 keeps building the stage 5 way (the planner
 * would have to fit a wall around buildings it didn't place). Memory only: retry timers, problems, the plan views.
 */
public final class KingdomLayout {
    /** The plot kept free for a fishing village (the owner's planner-page note) is sized for this structure. */
    public static final ResourceLocation FISHING_DOCK = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "fishing_dock");
    /** How long the dev view of a plan shows, in ticks. */
    static final int SHOW_TICKS = 600;
    /** Players this close (blocks) to a plan being drawn up or a plot being started see it; particles farther away aren't sent. */
    static final int SHOW_RANGE = 128;

    private static final Map<UUID, Long> NEXT_TRY = new HashMap<>();
    private static final Map<UUID, String> PROBLEMS = new HashMap<>();
    private static final Map<UUID, Show> SHOWS = new LinkedHashMap<>();
    private static boolean inTests;

    private KingdomLayout() {
    }

    /** The land as the planner sees it. */
    public static Ground ground(TerrainScan scan) {
        return new Ground() {
            @Override
            public boolean known(int x, int z) {
                return scan.contains(x, z) && scan.known(x, z);
            }

            @Override
            public int height(int x, int z) {
                return scan.ground(x, z);
            }

            @Override
            public boolean wet(int x, int z) {
                return scan.waterDepth(x, z) > 0 || scan.lava(x, z);
            }

            @Override
            public boolean trees(int x, int z) {
                return scan.trees(x, z);
            }

            @Override
            public int hollow(int x, int z) {
                return scan.hollow(x, z);
            }
        };
    }

    public static KingdomPlanner.Rules rules() {
        return new KingdomPlanner.Rules(KingdomKeep.MARGIN, Tuning.LAYOUT_PLOT_GAP.get(), Tuning.LAYOUT_ROAD_WIDTH.get() | 1,
                Tuning.LAYOUT_SQUARE_DEPTH.get(), Tuning.LAYOUT_SQUARE_WIDTH.get() | 1, Tuning.ECONOMY_PLOT_MAX_HEIGHT_RANGE.get(),
                Tuning.LAYOUT_WALL_SETBACK.get(), Tuning.LAYOUT_WALL_SHIFT.get(), Tuning.LAYOUT_OUTER_BAND.get(),
                Tuning.TERRAIN_SEARCH_STEP.get(), BuildSitePlanner.EASEL_SPACING);
    }

    public static Side side(Direction direction) {
        return switch (direction) {
            case NORTH -> Side.NORTH;
            case SOUTH -> Side.SOUTH;
            case WEST -> Side.WEST;
            default -> Side.EAST;
        };
    }

    public static Direction direction(Side side) {
        return switch (side) {
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
        };
    }

    /** A building's ground for every build direction, from its blueprint; empty when the structure isn't loaded. */
    static Optional<KingdomPlanner.Item> item(ServerLevel level, ResourceLocation structure, int detail, boolean outer, boolean likesTrees) {
        Optional<ServerBlueprintPlan> plan = blueprintPlan(level, structure, detail);
        if (plan.isEmpty()) {
            return Optional.empty();
        }
        List<KingdomPlanner.Shape> shapes = new ArrayList<>();
        for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            KeepSite.Area area = KingdomKeep.area(plan.get(), facing);
            shapes.add(new KingdomPlanner.Shape(side(facing), area.minX(), area.maxX(), area.minZ(), area.maxZ()));
        }
        return Optional.of(new KingdomPlanner.Item(structure.toString(), outer, likesTrees, shapes));
    }

    private static Optional<ServerBlueprintPlan> blueprintPlan(ServerLevel level, ResourceLocation structure, int detail) {
        Optional<StructureMeta> meta = StructureRegistry.get(level.getServer()).byId(structure).filter(StructureMeta::isReady);
        if (meta.isEmpty() || !ClaySoldiersCommon.COMMON_HOOKS.isBlueprintEnabled(level.enabledFeatures())) {
            return Optional.empty();
        }
        BlueprintData blueprint = StructureBlueprints.blueprint(level, meta.get(), Math.max(1, Math.min(detail, meta.get().levelCount())));
        return blueprint.createServerPlan();
    }

    /** Roles of small things that stand in the streets and squares, not on plots of their own. */
    private static final Set<String> STREET_ROLES = Set.of("decoration", "utility");

    /**
     * A plot kept for every ready building of the inner town and outside the wall that the kingdom doesn't build yet (not on
     * its list, not the keep or the fishing dock, not street decoration, not a test structure), lowest tier first, at its
     * top level's size so it can grow in place: the town is sized for everything still to come (the owner's note on part
     * 1, 2026-10-17: "a little small for the amount of buildings we're going to add").
     */
    public static List<KingdomPlanner.Item> roster(ServerLevel level, ResourceLocation keep) {
        Set<ResourceLocation> own = new HashSet<>(Set.of(keep, FISHING_DOCK));
        KingdomEconomy.plan().forEach(slot -> own.add(slot.structure()));
        List<StructureMeta> metas = new ArrayList<>(StructureRegistry.get(level.getServer()).ready().stream()
                .filter(meta -> meta.zone().equals("inner") || meta.zone().equals("outer"))
                .filter(meta -> !STREET_ROLES.contains(meta.role()) && !own.contains(meta.id()))
                .filter(meta -> !meta.id().getPath().startsWith("batch1_") && !meta.id().getPath().startsWith("test_"))
                .toList());
        metas.sort(Comparator.comparingInt(StructureMeta::tier).thenComparing(meta -> meta.id().toString()));
        List<KingdomPlanner.Item> items = new ArrayList<>();
        for (StructureMeta meta : metas) {
            item(level, meta.id(), meta.levelCount(), meta.zone().equals("outer"), false).ifPresent(items::add);
        }
        return items;
    }

    /**
     * Called with the economy while the keep is finished: draws up the master plan if the kingdom has none yet (retrying
     * every {@code kingdom.keep.plan_retry_ticks} while it can't).
     */
    public static void update(ServerLevel level, Kingdom kingdom) {
        if (Tuning.LAYOUT_ENABLED.get() == 0 || kingdom.plan().isPresent() || (level.getServer() instanceof GameTestServer && !inTests)) {
            return;
        }
        Long next = NEXT_TRY.get(kingdom.id());
        if (next != null && level.getGameTime() < next) {
            return;
        }
        NEXT_TRY.put(kingdom.id(), level.getGameTime() + Tuning.KINGDOM_KEEP_PLAN_RETRY_TICKS.get());
        draw(level, kingdom);
    }

    /**
     * GameTests only: whether kingdoms draw up plans in a GameTest server (off by default, so the stage 5 tests keep
     * building the stage 5 way and no plan reaches past their test area).
     */
    public static void enableInTestsForTests(boolean enabled) {
        inTests = enabled;
    }

    /** Draws up and saves the kingdom's master plan and lays its roads; returns it, or empty with a recorded reason. */
    public static Optional<MasterPlan> draw(ServerLevel level, Kingdom kingdom) {
        MinecraftServer server = level.getServer();
        Optional<KingdomBuilding> keep = KingdomKeep.keep(kingdom);
        if (keep.isEmpty() || keep.get().state() != KingdomBuilding.State.BUILT) {
            return problem(kingdom, "the keep isn't finished yet");
        }
        if (kingdom.buildings().size() > 1) {
            return problem(kingdom, "the kingdom started buildings after its keep before it had a plan, so it goes on without one");
        }
        Optional<KingdomPlanner.Item> keepItem = item(level, keep.get().structure(), keep.get().level(), false, false);
        if (keepItem.isEmpty()) {
            return problem(kingdom, "the keep's structure " + keep.get().structure() + " isn't loaded");
        }
        Side keepFacing = side(keep.get().facing());
        KingdomPlanner.Shape keepShape = keepItem.get().shapes().stream().filter(shape -> shape.facing() == keepFacing).findFirst().orElseThrow();
        BlockPos easel = keep.get().pos();
        int margin = KingdomKeep.MARGIN;
        Rect keepRect = new Rect(easel.getX() + keepShape.minX() - margin, easel.getX() + keepShape.maxX() + margin,
                easel.getZ() + keepShape.minZ() - margin, easel.getZ() + keepShape.maxZ() + margin);
        List<KingdomPlanner.Item> items = new ArrayList<>();
        KingdomPlanner.Item house = null;
        for (KingdomEconomy.Slot slot : KingdomEconomy.plan()) {
            if (KingdomDock.isDock(slot.structure())) {
                continue; // it goes out over a lake, found when its turn comes (KingdomDock); the waterside plot keeps room for it
            }
            Optional<KingdomPlanner.Item> item = item(level, slot.structure(), slot.level(), slot.outer(), slot.job() == KingdomEconomy.Job.WOODCUTTER);
            if (item.isEmpty()) {
                return problem(kingdom, "the structure " + slot.structure() + " isn't loaded");
            }
            items.add(item.get());
            if (house == null && !slot.outer()) {
                house = item.get();
            }
        }
        List<KingdomPlanner.Item> later = new ArrayList<>(Tuning.LAYOUT_ROSTER.get() == 1 ? roster(level, keep.get().structure()) : List.of());
        for (int i = 0; house != null && i < Tuning.LAYOUT_LATER_PLOTS.get(); i++) {
            later.add(house);
        }
        KingdomPlanner.Item waterside = item(level, FISHING_DOCK, 1, true, false).orElse(null);
        BlockPos middle = new BlockPos(keepRect.centerX(), easel.getY(), keepRect.centerZ());
        TerrainScan scan = TerrainScanner.scan(level, middle, Tuning.LAYOUT_SCAN_RADIUS.get());
        long start = System.nanoTime();
        KingdomPlanner.Result result = KingdomPlanner.plan(ground(scan), new Cell(kingdom.center().getX(), kingdom.center().getZ()), keepRect,
                new Cell(easel.getX(), easel.getZ()), keepFacing.opposite(), items, later, waterside, rules());
        double millis = (System.nanoTime() - start) / 1_000_000.0;
        result.notes().forEach(note -> DebugLog.event(DebugCategory.KINGDOM, null, () -> "planner of %s: %s".formatted(kingdom.team(), note)));
        DebugLog.event(DebugCategory.KINGDOM, null, () -> "planner of %s took %.1f ms (scan %.1f ms, %d of %d columns loaded)".formatted(kingdom.team(),
                millis, scan.costNanos() / 1_000_000.0, scan.knownColumns(), scan.size() * scan.size()));
        if (result.plan().isEmpty()) {
            return problem(kingdom, "no layout fits here (" + String.join("; ", result.notes()) + "); trying again later");
        }
        MasterPlan plan = result.plan().get();
        Kingdoms.setPlan(server, kingdom, plan);
        PROBLEMS.remove(kingdom.id());
        showNearby(level, kingdom, plan, null, middle);
        long houses = plan.plots().stream().filter(plot -> !plot.outer()).count();
        Kingdoms.chronicle(server, kingdom, "The kingdom of " + Kingdoms.teamName(server, kingdom.team()) + " drew up its plan: a square, "
                + houses + " house plots along its streets and a wall of " + plan.wallLength() + " blocks with " + plan.gates().size() + " gates.");
        if (Tuning.LAYOUT_LAY_ROADS.get() == 1) {
            Roads roads = layRoads(level, plan);
            roads.logs().forEach((item, count) -> Kingdoms.addResource(server, kingdom, KingdomStore.id(item), count));
            DebugLog.event(DebugCategory.TERRAIN, null, () -> "laid the roads of %s: %d dirt path blocks, %d plants cleared, %d logs to the stockpile, %d cells skipped (not loaded, water or not earth)"
                    .formatted(kingdom.team(), roads.laid(), roads.cleared(), roads.logs().values().stream().mapToInt(Integer::intValue).sum(), roads.skipped()));
            List<Bridge> bridges = bridgeRoads(level, plan, Tuning.LAYOUT_BRIDGE_MAX_LENGTH.get());
            for (Bridge bridge : bridges) {
                DebugLog.event(DebugCategory.TERRAIN, null, () -> "bridged the road of %s over water: %d long from %s to %s, %d planks"
                        .formatted(kingdom.team(), bridge.length(), bridge.from(), bridge.to(), bridge.planks()));
            }
        }
        return Optional.of(plan);
    }

    /**
     * What laying roads did.
     *
     * @param laid    dirt path blocks placed
     * @param cleared plants and logs removed above the road
     * @param skipped road cells left as they were (not loaded, water, or not earth: stone, sand)
     */
    public record Roads(int laid, int cleared, int skipped, Map<Item, Integer> logs) {
    }

    /** Lays the plan's roads at once: earth at the top of each road cell becomes a dirt path; plants and tree trunks on it go. */
    public static Roads layRoads(ServerLevel level, MasterPlan plan) {
        int laid = 0;
        int cleared = 0;
        int skipped = 0;
        Map<Item, Integer> logs = new LinkedHashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_ALL;
        for (Cell cell : plan.roadCells()) {
            if (!level.hasChunk(cell.x() >> 4, cell.z() >> 4)) {
                skipped++;
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cell.x(), cell.z()) - 1;
            BlockState state = level.getBlockState(pos.set(cell.x(), y, cell.z()));
            while (state.is(BlockTags.LOGS) && y > level.getMinBuildHeight()) {
                logs.merge(state.getBlock().asItem(), 1, Integer::sum);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
                cleared++;
                y--;
                state = level.getBlockState(pos.set(cell.x(), y, cell.z()));
            }
            if (!state.getFluidState().isEmpty() || !(state.is(BlockTags.DIRT) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND))) {
                skipped++;
                continue;
            }
            BlockPos above = pos.above();
            BlockState over = level.getBlockState(above);
            if (!over.isAir()) {
                boolean plant = over.canBeReplaced() || over.is(BlockTags.FLOWERS) || over.is(BlockTags.SAPLINGS);
                if (!plant || !over.getFluidState().isEmpty()) {
                    skipped++;
                    continue;
                }
                level.setBlock(above, Blocks.AIR.defaultBlockState(), flags);
                cleared++;
            }
            if (!state.is(Blocks.DIRT_PATH)) {
                level.setBlock(pos, Blocks.DIRT_PATH.defaultBlockState(), flags);
                laid++;
            }
        }
        return new Roads(laid, cleared, skipped, logs);
    }

    /**
     * A bridge laid over water on a road.
     *
     * @param from   the first water cell on the road's middle line
     * @param to     the last one
     * @param length water cells crossed
     * @param planks plank blocks placed (across the road's width)
     */
    public record Bridge(Cell from, Cell to, int length, int planks) {
    }

    /**
     * Bridges each stretch of water a road crosses, up to {@code maxLength} long and with land at both ends (a stream, a
     * pond; not a road running into the sea): the water's top block becomes spruce planks across the road's width
     * (D114, the owner's island game: "a gap in the road ... a small bridge should go there").
     */
    public static List<Bridge> bridgeRoads(ServerLevel level, MasterPlan plan, int maxLength) {
        List<Bridge> bridges = new ArrayList<>();
        if (maxLength <= 0) {
            return bridges;
        }
        for (MasterPlan.Road road : plan.roads()) {
            boolean alongX = road.from().z() == road.to().z();
            int line = alongX ? road.from().z() : road.from().x();
            int start = alongX ? Math.min(road.from().x(), road.to().x()) : Math.min(road.from().z(), road.to().z());
            int end = alongX ? Math.max(road.from().x(), road.to().x()) : Math.max(road.from().z(), road.to().z());
            int runStart = Integer.MIN_VALUE; // first water cell of the stretch, MIN_VALUE when not after land
            boolean afterLand = false;
            for (int t = start; t <= end; t++) {
                Cell cell = alongX ? new Cell(t, line) : new Cell(line, t);
                Boolean wet = wetTop(level, cell);
                if (wet == null) { // not loaded: unknown, no bridge across it
                    afterLand = false;
                    runStart = Integer.MIN_VALUE;
                } else if (wet) {
                    if (runStart == Integer.MIN_VALUE && afterLand) {
                        runStart = t;
                    }
                    afterLand = false;
                } else {
                    if (runStart != Integer.MIN_VALUE && t - runStart <= maxLength) {
                        bridges.add(bridge(level, road, alongX, line, runStart, t - 1));
                    }
                    runStart = Integer.MIN_VALUE;
                    afterLand = true;
                }
            }
        }
        return bridges;
    }

    private static Bridge bridge(ServerLevel level, MasterPlan.Road road, boolean alongX, int line, int first, int last) {
        int half = road.width() / 2;
        int planks = 0;
        for (int t = first; t <= last; t++) {
            for (int offset = -half; offset <= half; offset++) {
                Cell cell = alongX ? new Cell(t, line + offset) : new Cell(line + offset, t);
                if (Boolean.TRUE.equals(wetTop(level, cell))) {
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, cell.x(), cell.z()) - 1;
                    level.setBlock(new BlockPos(cell.x(), y, cell.z()), Blocks.SPRUCE_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
                    planks++;
                }
            }
        }
        Cell from = alongX ? new Cell(first, line) : new Cell(line, first);
        Cell to = alongX ? new Cell(last, line) : new Cell(line, last);
        return new Bridge(from, to, last - first + 1, planks);
    }

    /** Whether the top block at {@code cell} is water; null when it isn't loaded. */
    @Nullable
    private static Boolean wetTop(ServerLevel level, Cell cell) {
        if (!level.hasChunk(cell.x() >> 4, cell.z() >> 4)) {
            return null;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, cell.x(), cell.z()) - 1;
        return level.getFluidState(new BlockPos(cell.x(), y, cell.z())).is(FluidTags.WATER);
    }

    /**
     * Fills every open space (a cave, a hole) under a levelled plot down to {@code depth} below its ground, so nothing
     * stands over a hollow and no soldier falls into one; returns the blocks filled.
     */
    public static int sealHollows(ServerLevel level, Rect rect, int groundY, int depth) {
        int filled = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = rect.minX(); x <= rect.maxX(); x++) {
            for (int z = rect.minZ(); z <= rect.maxZ(); z++) {
                for (int y = groundY - 1; y >= groundY - depth && y >= level.getMinBuildHeight(); y--) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (!state.blocksMotion()) {
                        level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                        filled++;
                    }
                }
            }
        }
        return filled;
    }

    /** The plan's plot for the next building of {@code structure} (the n-th building of a structure takes its n-th plot). */
    public static Optional<MasterPlan.Plot> plotFor(Kingdom kingdom, ResourceLocation structure) {
        if (kingdom.plan().isEmpty()) {
            return Optional.empty();
        }
        int built = (int) kingdom.buildings().stream().filter(building -> building.structure().equals(structure)).count();
        return kingdom.plan().get().plotFor(structure.toString(), built);
    }

    /**
     * The site of a planned plot as it is now, judged like any plot (known, dry, even enough); empty when it can't be used
     * any more, with the reason recorded.
     */
    static Optional<KeepSite.Choice> site(ServerLevel level, Kingdom kingdom, MasterPlan.Plot plot, List<KeepSite.Area> areas) {
        Optional<KeepSite.Area> area = areas.stream().filter(candidate -> side(candidate.facing()) == plot.facing()).findFirst();
        if (area.isEmpty()) {
            return Optional.empty();
        }
        int reach = (int) Math.ceil(plot.rect().reach(plot.easelX(), plot.easelZ())) + 1;
        TerrainScan scan = TerrainScanner.scan(level, new BlockPos(plot.easelX(), 0, plot.easelZ()), reach);
        KeepSite.Judgement judgement = KeepSite.judge(scan, area.get(), plot.easelX(), plot.easelZ(),
                new KeepSite.Rules(0, 1, Tuning.ECONOMY_PLOT_MAX_HEIGHT_RANGE.get(), KingdomKeep.MARGIN));
        if (judgement.verdict() != KeepSite.Verdict.USABLE) {
            DebugLog.event(DebugCategory.ECONOMY, null, () -> "the planned plot of %s for %s at %d, %d can't be used now (%s); looking for another"
                    .formatted(kingdom.team(), plot.key(), plot.easelX(), plot.easelZ(), judgement.verdict().name().toLowerCase()));
            return Optional.empty();
        }
        return Optional.of(new KeepSite.Choice(area.get(), plot.easelX(), plot.easelZ(), judgement.groundY(), judgement.range(),
                judgement.earth(), judgement.trees(), 0, judgement.hollow()));
    }

    /** The plan's plots as ground a fallback plot must keep off. */
    static List<PlotPicker.Taken> reserved(Kingdom kingdom) {
        List<PlotPicker.Taken> taken = new ArrayList<>();
        kingdom.plan().ifPresent(plan -> plan.plots().forEach(plot -> taken.add(new PlotPicker.Taken(plot.rect().minX(), plot.rect().maxX(),
                plot.rect().minZ(), plot.rect().maxZ()))));
        return taken;
    }

    // The book and status lines

    /** Lines about the master plan for the book and {@code /csr kingdom status}. */
    public static List<String> describe(Kingdom kingdom) {
        List<String> lines = new ArrayList<>();
        if (kingdom.plan().isPresent()) {
            MasterPlan plan = kingdom.plan().get();
            Rect wall = plan.wallRect();
            long houses = plan.plots().stream().filter(plot -> !plot.outer() && plot.kind() == MasterPlan.Kind.BUILDING).count();
            long later = plan.plots().stream().filter(plot -> plot.kind() == MasterPlan.Kind.LATER).count();
            boolean fishing = plan.plots().stream().anyMatch(plot -> plot.kind() == MasterPlan.Kind.WATERSIDE);
            lines.add("Plan: a wall " + wall.sizeX() + " by " + wall.sizeZ() + " with " + plan.gates().size() + " gates, the main street going "
                    + plan.front().lowerName() + ", " + houses + " house plot(s) and " + later + " kept for later"
                    + (fishing ? ", a plot by the water for a fishing village" : "") + ".");
        } else if (Tuning.LAYOUT_ENABLED.get() == 1) {
            String problem = PROBLEMS.get(kingdom.id());
            lines.add(problem == null ? "Plan: drawn up when the keep is finished." : "Plan: none yet, " + problem + ".");
        }
        return lines;
    }

    private static Optional<MasterPlan> problem(Kingdom kingdom, String why) {
        if (!why.equals(PROBLEMS.put(kingdom.id(), why))) {
            DebugLog.event(DebugCategory.KINGDOM, null, () -> "plan of %s: %s".formatted(kingdom.team(), why));
        }
        return Optional.empty();
    }

    /** Forgets everything kept in memory about the kingdom (disbanding, GameTests). */
    public static void forget(Kingdom kingdom) {
        NEXT_TRY.remove(kingdom.id());
        PROBLEMS.remove(kingdom.id());
    }

    // Seeing the plan

    /** A plan shown to a player: the whole plan, or only the plot {@code only}. */
    private record Show(ServerPlayer player, MasterPlan plan, @Nullable Rect only, long until) {
    }

    /** Shows {@code plan} to {@code player} for {@link #SHOW_TICKS} with particles: the wall, the plots, the gates. */
    public static void show(ServerPlayer player, MasterPlan plan) {
        SHOWS.put(player.getUUID(), new Show(player, plan, null, player.serverLevel().getGameTime() + SHOW_TICKS));
    }

    /**
     * Shows the plan (or only the plot {@code only}, a building being started) to every player within {@link #SHOW_RANGE}
     * of {@code at} for {@code layout.show_seconds}: the owner liked seeing the plan (2026-10-17, "use that more often").
     */
    public static void showNearby(ServerLevel level, Kingdom kingdom, MasterPlan plan, @Nullable Rect only, BlockPos at) {
        int seconds = Tuning.LAYOUT_SHOW_SECONDS.get();
        if (seconds <= 0) {
            return;
        }
        int shown = 0;
        for (ServerPlayer player : level.players()) {
            Show showing = SHOWS.get(player.getUUID());
            if (only != null && showing != null && showing.only() == null && level.getGameTime() <= showing.until()) {
                continue; // the whole plan is still showing, this plot included
            }
            if (player.distanceToSqr(at.getX() + 0.5, player.getY(), at.getZ() + 0.5) <= (double) SHOW_RANGE * SHOW_RANGE) {
                SHOWS.put(player.getUUID(), new Show(player, plan, only, level.getGameTime() + seconds * 20L));
                shown++;
            }
        }
        int players = shown;
        DebugLog.event(DebugCategory.KINGDOM, null, () -> "showed %s of %s to %d player(s) near %s".formatted(only == null ? "the plan" : "the plot " + only,
                kingdom.team(), players, at.toShortString()));
    }

    /** Called every server tick: draws the plans being shown every 10 ticks. */
    public static void tick(MinecraftServer server) {
        if (SHOWS.isEmpty() || server.getTickCount() % 10 != 0) {
            return;
        }
        for (Iterator<Show> it = SHOWS.values().iterator(); it.hasNext(); ) {
            Show show = it.next();
            ServerLevel level = show.player().serverLevel();
            if (show.player().isRemoved() || level.getGameTime() > show.until()) {
                it.remove();
                continue;
            }
            draw(level, show.player(), show.plan(), show.only());
        }
    }

    private static void draw(ServerLevel level, ServerPlayer player, MasterPlan plan, @Nullable Rect only) {
        if (only != null) {
            outline(level, player, ParticleTypes.HAPPY_VILLAGER, only);
            return;
        }
        for (Cell cell : plan.wall().cells()) {
            if (!seaGuarded(level, cell)) {
                particle(level, player, ParticleTypes.END_ROD, cell, 1.2);
            }
        }
        for (MasterPlan.Gate gate : plan.gates()) {
            for (int dy = 0; dy < 4; dy++) {
                particle(level, player, ParticleTypes.FLAME, gate.at(), 1.5 + dy);
            }
        }
        for (MasterPlan.Plot plot : plan.plots()) {
            outline(level, player, plot.kind() == MasterPlan.Kind.BUILDING ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.WAX_ON, plot.rect());
        }
    }

    private static void outline(ServerLevel level, ServerPlayer player, ParticleOptions type, Rect rect) {
        for (int x = rect.minX(); x <= rect.maxX(); x++) {
            particle(level, player, type, new Cell(x, rect.minZ()), 0.6);
            particle(level, player, type, new Cell(x, rect.maxZ()), 0.6);
        }
        for (int z = rect.minZ() + 1; z < rect.maxZ(); z++) {
            particle(level, player, type, new Cell(rect.minX(), z), 0.6);
            particle(level, player, type, new Cell(rect.maxX(), z), 0.6);
        }
    }

    private static void particle(ServerLevel level, ServerPlayer player, ParticleOptions type, Cell cell, double up) {
        double dx = cell.x() + 0.5 - player.getX();
        double dz = cell.z() + 0.5 - player.getZ();
        if (dx * dx + dz * dz > (double) SHOW_RANGE * SHOW_RANGE || !level.hasChunk(cell.x() >> 4, cell.z() >> 4)) {
            return;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, cell.x(), cell.z());
        level.sendParticles(player, type, true, cell.x() + 0.5, y + up, cell.z() + 0.5, 1, 0, 0, 0, 0);
    }

    /**
     * Whether the wall line at {@code cell} runs over water: the sea (or a lake) guards that stretch, so no wall goes there
     * and the plan view leaves it out (D113). The wall is a rectangle around every plot, so on a coast it runs out over
     * the water.
     */
    public static boolean seaGuarded(ServerLevel level, Cell cell) {
        return Boolean.TRUE.equals(wetTop(level, cell));
    }

    /** The cells of a plan's roads (for tests). */
    public static Set<Cell> roadCells(MasterPlan plan) {
        return plan.roadCells();
    }

    @Nullable
    static String problemFor(Kingdom kingdom) {
        return PROBLEMS.get(kingdom.id());
    }
}
