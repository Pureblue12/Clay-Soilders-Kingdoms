package net.bumblebee.claysoldiers.kingdom;

import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.BlueprintTemplateSettings;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.kingdom.layout.Cell;
import net.bumblebee.claysoldiers.kingdom.layout.DockSite;
import net.bumblebee.claysoldiers.kingdom.layout.Lake;
import net.bumblebee.claysoldiers.kingdom.layout.MasterPlan;
import net.bumblebee.claysoldiers.structure.StructureBlueprints;
import net.bumblebee.claysoldiers.structure.StructureMeta;
import net.bumblebee.claysoldiers.structure.StructureRegistry;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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
 * The fishing dock (the owner's ask, 2026-10-17): a house on the bank and a jetty out over a real lake, from which fishers
 * fish ({@code FishGoal}). The world side of {@link DockSite}: finds the site when the kingdom's plan reaches the dock,
 * levels the bank under the house, drives the jetty's pilings down to the lake bed (at once, like levelling: soldiers don't
 * dive) and shows the lake and the dock in particles. No lake nearby: the kingdom builds the rest of its plan and looks
 * again after {@code fishing.dock_retry_ticks}. Memory only: retry timers, problems, views.
 */
public final class KingdomDock {
    /** The dock's structure; the master plan's waterside plot (stage 6) is sized for it too. */
    public static final ResourceLocation FISHING_DOCK = KingdomLayout.FISHING_DOCK;
    /** Rows of the dock (counted from its front, the side facing the easel) that are its house on the bank. */
    static final int LAND_ROWS = 7;
    /** Rows after the house where the jetty leaves the bank: water or bank. */
    static final int SHORE_ROWS = 2;
    /** No lava this close to the dock. */
    static final int LAVA_REACH = 4;
    /** Columns of a lake counted at most (the plan view shows its edge that far). */
    static final int LAKE_LIMIT = 4000;
    /** The most lake edge columns the plan view shows (particles sent every 10 ticks). */
    static final int MAX_EDGE_SHOWN = 600;
    /** Pilings go at most this far below the water's surface (a deeper hole under a jetty is a cave). */
    static final int MAX_PILING = 16;

    private static final Map<UUID, Long> NEXT_TRY = new HashMap<>();
    private static final Map<UUID, String> PROBLEMS = new HashMap<>();
    private static final Map<UUID, Show> SHOWS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, Integer> LAND_ROWS_FOR_TESTS = new HashMap<>();

    private KingdomDock() {
    }

    public static boolean isDock(ResourceLocation structure) {
        return structure.equals(FISHING_DOCK) || LAND_ROWS_FOR_TESTS.containsKey(structure);
    }

    /** GameTests only: {@code structure} is a dock whose house is its first {@code landRows} rows ({@code null}: forget it). */
    public static void useDockForTests(ResourceLocation structure, @Nullable Integer landRows) {
        if (landRows == null) {
            LAND_ROWS_FOR_TESTS.remove(structure);
        } else {
            LAND_ROWS_FOR_TESTS.put(structure, landRows);
        }
    }

    private static int landRows(ResourceLocation structure) {
        return LAND_ROWS_FOR_TESTS.getOrDefault(structure, LAND_ROWS);
    }

    public static DockSite.Rules rules(int radius) {
        int minLake = Tuning.FISHING_LAKE_MIN_SIZE.get();
        return new DockSite.Rules(minLake, Math.max(minLake, LAKE_LIMIT), Tuning.FISHING_DOCK_MIN_DEPTH.get(),
                Math.max(Tuning.FISHING_DOCK_MIN_DEPTH.get(), Tuning.FISHING_DOCK_MAX_DEPTH.get()), Tuning.FISHING_DOCK_BANK_RANGE.get(),
                LAVA_REACH, radius, 2);
    }

    // The dock's shape

    /**
     * The dock's footprint for each build direction in {@code directions} (the {@code index} of each is its place in that
     * list): its blocks' columns sorted into house, shore and jetty by their row from the front, the easel, the chest and
     * the row where builders gather (bank), and {@code fishing.dock_open_water} rows of water past the jetty's end.
     */
    public static List<DockSite.Footprint> footprints(ResourceLocation structure, ServerBlueprintPlan plan, List<Direction> directions) {
        int landRows = landRows(structure);
        int open = Tuning.FISHING_DOCK_OPEN_WATER.get();
        List<DockSite.Footprint> footprints = new ArrayList<>();
        for (int index = 0; index < directions.size(); index++) {
            Direction facing = directions.get(index);
            Direction easelFacing = facing.getOpposite();
            BlueprintTemplateSettings settings = new BlueprintTemplateSettings(plan.getSize(), Mirror.NONE, EaselBlockEntity.rotationFor(easelFacing));
            BlockPos base = EaselBlockEntity.templateBase(BlockPos.ZERO, easelFacing, settings);
            Map<Cell, Integer> rows = new LinkedHashMap<>();
            int front = Integer.MAX_VALUE;
            List<BlockPos> targets = plan.targetPositions(base, settings);
            for (BlockPos pos : targets) {
                front = Math.min(front, along(pos, facing));
            }
            int last = Integer.MIN_VALUE;
            for (BlockPos pos : targets) {
                int row = along(pos, facing) - front;
                rows.merge(new Cell(pos.getX(), pos.getZ()), row, Math::max);
                last = Math.max(last, row);
            }
            Set<Cell> land = new HashSet<>();
            List<Cell> shore = new ArrayList<>();
            List<Cell> water = new ArrayList<>();
            Set<Cell> lastRow = new HashSet<>();
            for (Map.Entry<Cell, Integer> entry : rows.entrySet()) {
                int row = entry.getValue();
                if (row < landRows) {
                    land.add(entry.getKey());
                } else if (row < landRows + SHORE_ROWS) {
                    shore.add(entry.getKey());
                } else {
                    water.add(entry.getKey());
                }
                if (row == last) {
                    lastRow.add(entry.getKey());
                }
            }
            for (BlockPos pos : new BlockPos[]{BlockPos.ZERO, KingdomKeep.chestPos(BlockPos.ZERO, facing), BlockPos.ZERO.relative(facing.getOpposite(), 3)}) {
                land.add(new Cell(pos.getX(), pos.getZ()));
            }
            List<Cell> ahead = new ArrayList<>();
            for (Cell cell : lastRow) {
                for (int i = 1; i <= open; i++) {
                    ahead.add(new Cell(cell.x() + facing.getStepX() * i, cell.z() + facing.getStepZ() * i));
                }
            }
            water.sort((a, b) -> Integer.compare(along(a, facing), along(b, facing)));
            footprints.add(new DockSite.Footprint(index, List.copyOf(land), List.copyOf(shore), List.copyOf(water), List.copyOf(ahead)));
        }
        return footprints;
    }

    private static int along(BlockPos pos, Direction facing) {
        return pos.getX() * facing.getStepX() + pos.getZ() * facing.getStepZ();
    }

    private static int along(Cell cell, Direction facing) {
        return cell.x() * facing.getStepX() + cell.z() * facing.getStepZ();
    }

    // The land and water

    /** The world as {@link DockSite} sees it: loaded columns only, read once each. */
    public static DockSite.Land land(ServerLevel level) {
        return new DockSite.Land() {
            private final Map<Cell, int[]> columns = new HashMap<>();

            /** {surface or NONE, depth, still 0/1, ground, lava 0/1}; null when not loaded. */
            @Nullable
            private int[] column(int x, int z) {
                Cell cell = new Cell(x, z);
                int[] column = columns.get(cell);
                if (column == null && !columns.containsKey(cell)) {
                    column = read(level, x, z);
                    columns.put(cell, column);
                }
                return column;
            }

            @Override
            public boolean known(int x, int z) {
                return column(x, z) != null;
            }

            @Override
            public int surface(int x, int z) {
                int[] column = column(x, z);
                return column == null ? Lake.NONE : column[0];
            }

            @Override
            public int depth(int x, int z) {
                int[] column = column(x, z);
                return column == null ? 0 : column[1];
            }

            @Override
            public boolean still(int x, int z) {
                int[] column = column(x, z);
                return column != null && column[2] == 1;
            }

            @Override
            public int ground(int x, int z) {
                int[] column = column(x, z);
                return column == null ? level.getMinBuildHeight() : column[3];
            }

            @Override
            public boolean lava(int x, int z) {
                int[] column = column(x, z);
                return column != null && column[4] == 1;
            }
        };
    }

    @Nullable
    private static int[] read(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) {
            return null;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        BlockState state = level.getBlockState(pos);
        // Lily pads, logs and plants over the top don't count.
        while (pos.getY() > level.getMinBuildHeight() && state.getFluidState().isEmpty()
                && (state.is(Blocks.LILY_PAD) || state.is(BlockTags.LOGS) || !state.blocksMotion())) {
            pos.move(Direction.DOWN);
            state = level.getBlockState(pos);
        }
        FluidState fluid = state.getFluidState();
        if (fluid.is(FluidTags.LAVA)) {
            return new int[]{Lake.NONE, 0, 0, pos.getY(), 1};
        }
        if (!fluid.is(FluidTags.WATER)) {
            return new int[]{Lake.NONE, 0, 0, pos.getY(), 0};
        }
        int surface = pos.getY();
        int still = fluid.isSource() ? 1 : 0;
        int depth = 0;
        while (pos.getY() > level.getMinBuildHeight() && level.getFluidState(pos).is(FluidTags.WATER)) {
            depth++;
            pos.move(Direction.DOWN);
        }
        return new int[]{surface, depth, still, pos.getY(), 0};
    }

    // Finding the site

    /** The columns the dock may not cover: other kingdoms' claims, this kingdom's buildings, plots, roads and wall. */
    private static java.util.function.Predicate<Cell> blocked(ServerLevel level, Kingdom kingdom) {
        MinecraftServer server = level.getServer();
        List<Kingdom> others = Kingdoms.all(server).stream().filter(other -> !other.id().equals(kingdom.id())).toList();
        List<PlotPicker.Taken> taken = new ArrayList<>();
        for (KingdomBuilding building : kingdom.buildings()) {
            taken.add(KingdomEconomy.taken(level, building));
        }
        Set<Cell> lines = new HashSet<>();
        kingdom.plan().ifPresent(plan -> {
            // The waterside plot stage 6 keeps by the water is the dock's own; other plots are taken.
            plan.plots().stream().filter(plot -> plot.kind() != MasterPlan.Kind.WATERSIDE)
                    .forEach(plot -> taken.add(new PlotPicker.Taken(plot.rect().minX(), plot.rect().maxX(), plot.rect().minZ(), plot.rect().maxZ())));
            lines.addAll(plan.roadCells());
            lines.addAll(plan.wall().cells());
        });
        return cell -> lines.contains(cell)
                || taken.stream().anyMatch(t -> t.overlaps(cell.x(), cell.x(), cell.z(), cell.z(), 0))
                || others.stream().anyMatch(other -> other.claims(level.dimension(), new BlockPos(cell.x(), 0, cell.z())));
    }

    /** One search for the dock's site: the result, and the footprints and rules it used. */
    public record Search(DockSite.Result result, List<DockSite.Footprint> footprints, DockSite.Rules rules) {
        public String summary() {
            return result.summary(rules);
        }
    }

    /** Searches the dock's site within the claim plus {@code economy.plot.outer_reach} of the kingdom's center. */
    public static Search search(ServerLevel level, Kingdom kingdom, ResourceLocation structure, ServerBlueprintPlan plan, List<Direction> directions) {
        List<DockSite.Footprint> footprints = footprints(structure, plan, directions);
        DockSite.Rules rules = rules(kingdom.claimRadius() + Tuning.ECONOMY_PLOT_OUTER_REACH.get());
        BlockPos center = kingdom.center();
        long start = System.nanoTime();
        DockSite.Result result = DockSite.search(land(level), center.getX(), center.getZ(), footprints, blocked(level, kingdom), rules);
        double millis = (System.nanoTime() - start) / 1_000_000.0;
        Search search = new Search(result, footprints, rules);
        DebugLog.event(DebugCategory.KINGDOM, null, () -> "dock site of %s (%.1f ms): %s".formatted(kingdom.team(), millis, search.summary()));
        return search;
    }

    /**
     * Looks for the dock's site ({@link #search}); empty (with the reason recorded, and no new look for
     * {@code fishing.dock_retry_ticks}) when there is no lake. Each of {@code areas} is the dock's ground for one build
     * direction, as the economy uses them. A site found is shown to players nearby.
     */
    public static Optional<KeepSite.Choice> site(ServerLevel level, Kingdom kingdom, ResourceLocation structure, ServerBlueprintPlan plan,
                                                 List<KeepSite.Area> areas) {
        Search search = search(level, kingdom, structure, plan, areas.stream().map(KeepSite.Area::facing).toList());
        if (search.result().best().isEmpty()) {
            NEXT_TRY.put(kingdom.id(), level.getGameTime() + Tuning.FISHING_DOCK_RETRY_TICKS.get());
            PROBLEMS.put(kingdom.id(), search.summary() + "; looking again later, building the rest meanwhile");
            return Optional.empty();
        }
        PROBLEMS.remove(kingdom.id());
        NEXT_TRY.remove(kingdom.id());
        DockSite.Choice choice = search.result().best().get();
        KeepSite.Area area = areas.get(choice.index());
        showNearby(level, kingdom, choice, search.footprints().get(choice.index()));
        return Optional.of(new KeepSite.Choice(area, choice.easelX(), choice.easelZ(), choice.surface(), choice.earth(), choice.earth(), 0,
                choice.distance(), 0));
    }

    /**
     * Dev tool: where the kingdom's fishing dock would go now, shown in particles to players nearby; builds nothing.
     */
    public static String preview(ServerLevel level, Kingdom kingdom) {
        Optional<StructureMeta> meta = StructureRegistry.get(level.getServer()).byId(FISHING_DOCK).filter(StructureMeta::isReady);
        Optional<ServerBlueprintPlan> plan = meta.flatMap(m -> StructureBlueprints.blueprint(level, m, 1).createServerPlan());
        if (plan.isEmpty()) {
            return "The fishing dock's structure isn't loaded, or blueprints are off.";
        }
        Search search = search(level, kingdom, FISHING_DOCK, plan.get(), List.of(Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST));
        search.result().best().ifPresent(choice -> showNearby(level, kingdom, choice, search.footprints().get(choice.index())));
        return "Fishing dock: " + search.summary() + ".";
    }

    /** Whether the kingdom found no lake for its dock lately and doesn't look again yet (the economy goes on without it). */
    public static boolean resting(Kingdom kingdom, long gameTime) {
        Long next = NEXT_TRY.get(kingdom.id());
        return next != null && gameTime < next;
    }

    @Nullable
    public static String problemFor(Kingdom kingdom) {
        return PROBLEMS.get(kingdom.id());
    }

    public static void forget(Kingdom kingdom) {
        NEXT_TRY.remove(kingdom.id());
        PROBLEMS.remove(kingdom.id());
    }

    // Getting the site ready

    /**
     * What getting the site ready did.
     *
     * @param pilings piling blocks driven into the lake below the jetty
     */
    public record Prepared(int filled, int cleared, int pilings) {
    }

    /**
     * Gets the dock's site ready at once: the bank under the house, easel and chest is levelled to the water's surface
     * ({@code choice.groundY()}), whatever stands over the water (lily pads, reeds) is cleared, and every log at the
     * jetty's deck that stands over water gets a piling of the same log down to the lake bed.
     */
    public static Prepared prepare(ServerLevel level, Kingdom kingdom, ResourceLocation structure, KeepSite.Choice choice, ServerBlueprintPlan plan) {
        Direction facing = choice.area().facing();
        DockSite.Footprint footprint = footprints(structure, plan, List.of(facing)).getFirst();
        int surface = choice.groundY();
        int clearTo = surface + plan.getSize().getY() + KingdomKeep.CLEAR_ABOVE;
        int filled = 0;
        int cleared = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        List<Cell> dry = new ArrayList<>(footprint.land());
        List<Cell> wet = new ArrayList<>(footprint.water());
        wet.addAll(footprint.ahead());
        for (Cell rel : footprint.shore()) {
            Cell cell = new Cell(choice.easelX() + rel.x(), choice.easelZ() + rel.z());
            (level.getFluidState(pos.set(cell.x(), surface, cell.z())).is(FluidTags.WATER) ? wet : dry).add(rel);
        }
        for (Cell rel : dry) {
            int x = choice.easelX() + rel.x();
            int z = choice.easelZ() + rel.z();
            KingdomKeep.Levelled levelled = KingdomKeep.levelGround(level, x, x, z, z, surface, clearTo);
            levelled.logs().forEach((item, count) -> Kingdoms.addResource(level.getServer(), kingdom, KingdomStore.id(item), count));
            filled += levelled.filled();
            cleared += levelled.cleared();
        }
        for (Cell rel : wet) {
            int x = choice.easelX() + rel.x();
            int z = choice.easelZ() + rel.z();
            for (int y = clearTo; y > surface; y--) {
                if (!level.getBlockState(pos.set(x, y, z)).isAir()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    cleared++;
                }
            }
        }
        int pilings = 0;
        Direction easelFacing = facing.getOpposite();
        BlueprintTemplateSettings settings = new BlueprintTemplateSettings(plan.getSize(), Mirror.NONE, EaselBlockEntity.rotationFor(easelFacing));
        BlockPos easel = new BlockPos(choice.easelX(), surface + 1, choice.easelZ());
        BlockPos base = EaselBlockEntity.templateBase(easel, easelFacing, settings);
        for (Map.Entry<BlockPos, BlockState> target : plan.targetStates(base, settings).entrySet()) {
            BlockPos top = target.getKey();
            if (top.getY() != surface + 1 || !target.getValue().is(BlockTags.LOGS)) {
                continue;
            }
            pos.set(top.getX(), surface, top.getZ());
            while (pos.getY() > Math.max(level.getMinBuildHeight(), surface - MAX_PILING)
                    && (level.getFluidState(pos).is(FluidTags.WATER) || level.getBlockState(pos).canBeReplaced())) {
                level.setBlock(pos, target.getValue(), Block.UPDATE_ALL);
                pilings++;
                pos.move(Direction.DOWN);
            }
        }
        Prepared prepared = new Prepared(filled, cleared, pilings);
        DebugLog.event(DebugCategory.TERRAIN, null, () -> "got the dock site of %s ready at %d, %d: water surface %d, bank filled %d, cleared %d, %d piling blocks"
                .formatted(kingdom.team(), choice.easelX(), choice.easelZ(), surface, prepared.filled(), prepared.cleared(), prepared.pilings()));
        return prepared;
    }

    // Seeing the dock site

    /** A dock site shown to a player: the lake's edge, the jetty, the house. */
    private record Show(ServerPlayer player, List<Cell> lakeEdge, List<Cell> jetty, List<Cell> house, int surface, long until) {
    }

    /**
     * Shows a chosen dock site to players within {@link KingdomLayout#SHOW_RANGE} for {@code layout.show_seconds}: blue
     * flames along the lake's edge it measured, flames along the jetty, green sparks around the house (the owner likes
     * seeing plans in game).
     */
    static void showNearby(ServerLevel level, Kingdom kingdom, DockSite.Choice choice, DockSite.Footprint footprint) {
        int seconds = Tuning.LAYOUT_SHOW_SECONDS.get();
        if (seconds <= 0) {
            return;
        }
        List<Cell> jetty = footprint.water().stream().map(rel -> new Cell(choice.easelX() + rel.x(), choice.easelZ() + rel.z())).toList();
        List<Cell> house = footprint.land().stream().map(rel -> new Cell(choice.easelX() + rel.x(), choice.easelZ() + rel.z())).toList();
        List<Cell> edge = choice.lake().edge().subList(0, Math.min(choice.lake().edge().size(), MAX_EDGE_SHOWN));
        int shown = 0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(choice.easelX() + 0.5, player.getY(), choice.easelZ() + 0.5) <= (double) KingdomLayout.SHOW_RANGE * KingdomLayout.SHOW_RANGE) {
                SHOWS.put(player.getUUID(), new Show(player, edge, jetty, house, choice.surface(), level.getGameTime() + seconds * 20L));
                shown++;
            }
        }
        int players = shown;
        DebugLog.event(DebugCategory.KINGDOM, null, () -> "showed the dock site of %s to %d player(s)".formatted(kingdom.team(), players));
    }

    /** Called every server tick (with the plan view): draws the dock sites being shown every 10 ticks. */
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
            for (Cell cell : show.lakeEdge()) {
                particle(level, show.player(), ParticleTypes.SOUL_FIRE_FLAME, cell, show.surface() + 1.2);
            }
            for (Cell cell : show.jetty()) {
                particle(level, show.player(), ParticleTypes.FLAME, cell, show.surface() + 1.5);
            }
            for (Cell cell : show.house()) {
                particle(level, show.player(), ParticleTypes.HAPPY_VILLAGER, cell, show.surface() + 1.6);
            }
        }
    }

    private static void particle(ServerLevel level, ServerPlayer player, ParticleOptions type, Cell cell, double y) {
        double dx = cell.x() + 0.5 - player.getX();
        double dz = cell.z() + 0.5 - player.getZ();
        if (dx * dx + dz * dz > (double) KingdomLayout.SHOW_RANGE * KingdomLayout.SHOW_RANGE || !level.hasChunk(cell.x() >> 4, cell.z() >> 4)) {
            return;
        }
        level.sendParticles(player, type, true, cell.x() + 0.5, y, cell.z() + 0.5, 1, 0, 0, 0, 0);
    }
}
