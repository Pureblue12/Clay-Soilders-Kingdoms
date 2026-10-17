package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugEvent;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KingFounding;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomBuilding;
import net.bumblebee.claysoldiers.kingdom.KingdomEconomy;
import net.bumblebee.claysoldiers.kingdom.KingdomKeep;
import net.bumblebee.claysoldiers.kingdom.KingdomLayout;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.bumblebee.claysoldiers.kingdom.layout.Cell;
import net.bumblebee.claysoldiers.kingdom.layout.LayoutChecker;
import net.bumblebee.claysoldiers.kingdom.layout.MasterPlan;
import net.bumblebee.claysoldiers.kingdom.layout.Rect;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Stage 6 the way a player plays it, with no dev shortcuts, on uneven land with a cave: soldiers stand scattered, one
 * picks up a dropped gold ingot and founds a kingdom, the kingdom builds its keep, then draws up its master plan (streets,
 * plots, a straight wall with gates), lays its roads as dirt paths and builds the lumber camp, the wheat field and the
 * cottage on their planned plots.
 * <p>
 * The land (84 by 84): earth 5 deep everywhere (so the cave check never looks under the test floor), rising in steps in
 * the west, and a raised plateau in the south-east with a cave under it. The buildings are the stage 5 story's small
 * stand-ins and the town is drawn small (narrow roads, a small square) so it fits; the real sizes are planned by
 * {@link KingdomPlannerGameTests}.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = ClaySoldiersCommon.MOD_ID)
public class KingdomTownGameTests {
    private static final String BATCH = "kingdom_town";
    private static final int TIMEOUT = 40000;
    private static final int SIZE = 84;
    private static final ResourceLocation TEAM = csr("cyan");
    private static final ResourceLocation KEEP = KingdomKeepGameTests.HUT;
    private static final ResourceLocation CAMP = csr("test_camp");
    private static final ResourceLocation FIELD = csr("test_field");
    private static final ResourceLocation COTTAGE = csr("test_cottage");
    private static final String TUNING = """
            {"kingdom.claim_radius": 14, "economy.plot.outer_reach": 2, "terrain.search.radius": 4,
             "economy.lumber.radius": 60, "economy.farm.tend_ticks": 1, "economy.food_per_building": 3,
             "layout.scan_radius": 32, "layout.road_width": 1, "layout.square_depth": 3, "layout.square_width": 3,
             "layout.plot_gap": 1, "layout.wall_setback": 2, "layout.wall_shift": 2, "layout.outer_band": 16, "layout.later_plots": 1}""";
    /** The earth's top (relative y) away from the slope and the plateau. */
    private static final int GROUND = 6;
    /** The plateau (3 higher) and the cave under it: 2 blocks of air, 2 under the plateau's top. */
    private static final int PLATEAU_MIN = 56;
    private static final int PLATEAU_MAX = 76;
    private static final int CAVE_MIN = 60;
    private static final int CAVE_MAX = 72;
    private static final BlockPos CENTER = new BlockPos(SIZE / 2, GROUND + 1, SIZE / 2);
    private static final List<BlockPos> TREES = List.of(new BlockPos(5, 0, 5), new BlockPos(78, 0, 5), new BlockPos(5, 0, 78), new BlockPos(78, 0, 78));

    @Nullable
    private static volatile Runnable perTick;

    /** The earth's top at a relative column. */
    static int height(int x, int z) {
        if (x >= PLATEAU_MIN && x <= PLATEAU_MAX && z >= PLATEAU_MIN && z <= PLATEAU_MAX) {
            return GROUND + 3;
        }
        return x < 14 ? GROUND + Math.min(3, (14 - x) / 4 + 1) : GROUND;
    }

    static boolean cave(int x, int y, int z) {
        return x >= CAVE_MIN && x <= CAVE_MAX && z >= CAVE_MIN && z <= CAVE_MAX && (y == GROUND || y == GROUND + 1);
    }

    @GameTest(template = "gametest/kingdom_town", batch = BATCH, timeoutTicks = TIMEOUT, skyAccess = true)
    public static void kingdomPlansItsTownOnRoughLand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        int width = (int) Math.round(helper.getBounds().getXsize());
        helper.assertTrue(width >= SIZE, "The test area is " + width + " wide, not " + SIZE + " (was the template's size cut?)");
        disband(server);
        GameTestTuning.load(TUNING);
        KingdomKeep.useKeepForTests(KEEP);
        KingdomEconomy.usePlanForTests(List.of(
                new KingdomEconomy.Slot(CAMP, 1, true, true, KingdomEconomy.Job.WOODCUTTER),
                new KingdomEconomy.Slot(FIELD, 1, true, true, KingdomEconomy.Job.FARMER),
                new KingdomEconomy.Slot(COTTAGE, 1, false, false, null)));
        KingFounding.enableInTestsForTests(true);
        KingdomLayout.enableInTestsForTests(true);
        DebugLog.reset();
        DebugLog.setCapacity(50000);
        DebugLog.setEnabled(DebugCategory.ECONOMY, true, server);
        DebugLog.setEnabled(DebugCategory.KINGDOM, true, server);
        DebugLog.setEnabled(DebugCategory.TERRAIN, true, server);

        buildLand(helper);
        for (BlockPos tree : TREES) {
            plantTree(helper, tree.atY(height(tree.getX(), tree.getZ()) + 1));
        }
        ClaySoldierEntity first = KingdomGameTests.DevSoldier.spawnPlain(helper, CENTER, TEAM);
        for (int[] offset : new int[][]{{9, 0}, {-9, 0}, {0, 9}, {0, -9}, {7, 7}, {-7, -7}, {7, -7}}) {
            BlockPos at = CENTER.offset(offset[0], 0, offset[1]);
            KingdomGameTests.DevSoldier.spawnPlain(helper, at.atY(height(at.getX(), at.getZ()) + 1), TEAM);
        }
        level.addFreshEntity(new ItemEntity(level, first.getX(), first.getY() + 0.2, first.getZ(), new ItemStack(Items.GOLD_INGOT)));
        server.tickRateManager().requestGameToSprint(TIMEOUT + 200);

        int[] ticks = {0};
        boolean[] done = {false};
        List<String> planProblems = new ArrayList<>();
        boolean[] planChecked = {false};
        perTick = () -> {
            if (done[0] || ++ticks[0] % 20 != 0) {
                return;
            }
            Optional<Kingdom> found = Kingdoms.byTeam(server, TEAM);
            if (found.isEmpty()) {
                return;
            }
            KingdomKeep.update(level, found.get());
            KingdomEconomy.update(level, found.get());
            if (!planChecked[0] && found.get().plan().isPresent()) {
                planChecked[0] = true;
                planProblems.addAll(checkPlan(helper, found.get().plan().get()));
            }
            List<KingdomBuilding> buildings = found.get().buildings();
            if (buildings.size() == 4 && buildings.stream().allMatch(b -> b.state() == KingdomBuilding.State.BUILT)) {
                done[0] = true;
                perTick = null;
            }
        };
        for (int tick = 100; tick < TIMEOUT - 20; tick += 100) {
            helper.runAtTickTime(tick, () -> {
                if (done[0]) {
                    finish(helper, planProblems);
                }
            });
        }
        helper.runAtTickTime(TIMEOUT - 20, () -> {
            if (!done[0]) {
                perTick = null;
                helper.fail(story(level, planProblems));
            }
        });
    }

    /** Everything that must hold once the town is built: on the plan's plots, nothing over a hole. */
    private static void finish(GameTestHelper helper, List<String> planProblems) {
        ServerLevel level = helper.getLevel();
        Kingdom kingdom = Kingdoms.byTeam(level.getServer(), TEAM).orElseThrow();
        List<String> wrong = new ArrayList<>(planProblems);
        if (kingdom.plan().isEmpty()) {
            wrong.add("the kingdom has no plan: " + KingdomLayout.describe(kingdom));
        } else {
            MasterPlan plan = kingdom.plan().get();
            for (KingdomBuilding building : kingdom.buildings().subList(1, kingdom.buildings().size())) {
                Optional<MasterPlan.Plot> plot = plan.plotFor(building.structure().toString(), 0);
                if (plot.isEmpty()) {
                    wrong.add("the plan has no plot for " + building.structure());
                    continue;
                }
                if (plot.get().easelX() != building.pos().getX() || plot.get().easelZ() != building.pos().getZ()) {
                    wrong.add(building.structure() + " was built at " + building.pos().toShortString() + ", not on its plot (easel at "
                            + plot.get().easelX() + ", " + plot.get().easelZ() + ")");
                }
                // Under the levelled plot: no open space down to the test floor.
                Rect rect = plot.get().rect();
                int groundY = building.pos().getY() - 1;
                int floor = helper.absolutePos(new BlockPos(0, 1, 0)).getY();
                for (int x = rect.minX(); x <= rect.maxX(); x++) {
                    for (int z = rect.minZ(); z <= rect.maxZ(); z++) {
                        for (int y = groundY - 1; y > floor && y >= groundY - 4; y--) {
                            BlockState state = level.getBlockState(new BlockPos(x, y, z));
                            if (!state.blocksMotion()) {
                                wrong.add("open space (" + state + ") under the " + building.structure().getPath() + " at " + x + ", " + y + ", " + z);
                                x = rect.maxX();
                                z = rect.maxZ();
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (wrong.isEmpty()) {
            helper.succeed();
        } else {
            helper.fail(String.join("; ", wrong) + "; " + story(level, List.of()));
        }
    }

    /** Checks the plan as soon as it is drawn (before anything is built on it). */
    private static List<String> checkPlan(GameTestHelper helper, MasterPlan plan) {
        ServerLevel level = helper.getLevel();
        List<String> wrong = new ArrayList<>();
        List<LayoutChecker.Problem> problems = plan.check();
        if (!problems.isEmpty()) {
            wrong.add("layout problems: " + problems);
        }
        for (ResourceLocation structure : List.of(CAMP, FIELD, COTTAGE)) {
            if (plan.plotsFor(structure.toString()).size() != 1) {
                wrong.add("the plan has " + plan.plotsFor(structure.toString()).size() + " plots for " + structure + ": " + plan.summary());
            }
        }
        Rect wall = plan.wallRect();
        if (plan.wallCorners().size() != 4 || !plan.wallCorners().stream().allMatch(corner ->
                (corner.x() == wall.minX() || corner.x() == wall.maxX()) && (corner.z() == wall.minZ() || corner.z() == wall.maxZ()))) {
            wrong.add("the wall isn't straight-sided: " + plan.wallCorners());
        }
        BlockPos caveA = helper.absolutePos(new BlockPos(CAVE_MIN, 0, CAVE_MIN));
        BlockPos caveB = helper.absolutePos(new BlockPos(CAVE_MAX, 0, CAVE_MAX));
        Rect cave = new Rect(Math.min(caveA.getX(), caveB.getX()), Math.max(caveA.getX(), caveB.getX()),
                Math.min(caveA.getZ(), caveB.getZ()), Math.max(caveA.getZ(), caveB.getZ()));
        for (MasterPlan.Plot plot : plan.plots()) {
            if (plot.rect().overlaps(cave, 0)) {
                wrong.add("the " + plot.kind().lowerName() + " plot for " + plot.key() + " at " + plot.rect() + " is over the cave " + cave);
            }
            if (plot.outer() ? plot.rect().overlaps(wall, 0) : !KingdomPlannerCases.inside(wall, plot.rect())) {
                wrong.add("the plot for " + plot.key() + " at " + plot.rect() + (plot.outer() ? " should be outside" : " should be inside") + " the wall " + wall);
            }
        }
        // The roads were laid at once: every road cell on the test's earth is a dirt path.
        BlockPos min = helper.absolutePos(new BlockPos(1, 0, 1));
        BlockPos max = helper.absolutePos(new BlockPos(SIZE - 2, 0, SIZE - 2));
        Rect inside = new Rect(Math.min(min.getX(), max.getX()), Math.max(min.getX(), max.getX()), Math.min(min.getZ(), max.getZ()), Math.max(min.getZ(), max.getZ()));
        int cells = 0;
        int paths = 0;
        for (Cell cell : plan.roadCells()) {
            if (!inside.contains(cell)) {
                continue;
            }
            cells++;
            BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(cell.x(), 0, cell.z())).below();
            if (level.getBlockState(top).is(Blocks.DIRT_PATH)) {
                paths++;
            }
        }
        if (cells == 0 || paths < cells * 9 / 10) {
            wrong.add(paths + " of " + cells + " road cells are dirt paths");
        }
        return wrong;
    }

    /** Earth 5 deep with grass on top, the stepped west, the plateau and the cave under it. */
    private static void buildLand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int top = height(x, z);
                for (int y = 1; y <= top; y++) {
                    BlockState state = cave(x, y, z) ? air : y == top ? grass : dirt;
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static BlockPos plantTree(GameTestHelper helper, BlockPos at) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(at);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dy = 2; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlockAndUpdate(base.offset(dx, dy, dz), leaves);
                }
            }
        }
        level.setBlockAndUpdate(base.above(4), leaves);
        for (int dy = 0; dy < 4; dy++) {
            level.setBlockAndUpdate(base.above(dy), Blocks.OAK_LOG.defaultBlockState());
        }
        return base;
    }

    /** Where the story stands, for the failure message. */
    private static String story(ServerLevel level, List<String> planProblems) {
        MinecraftServer server = level.getServer();
        Optional<Kingdom> kingdom = Kingdoms.byTeam(server, TEAM);
        if (kingdom.isEmpty()) {
            return "No kingdom was founded (did a soldier pick up the gold ingot?)";
        }
        List<String> lines = new ArrayList<>(planProblems);
        lines.add("Buildings: " + kingdom.get().buildings().stream().map(b -> b.structure().getPath() + " " + b.state() + " at " + b.pos().toShortString()).toList());
        lines.add("Plan: " + kingdom.get().plan().map(MasterPlan::summary).orElse("none"));
        lines.addAll(KingdomKeep.describe(server, kingdom.get()).stream().map(line -> "Keep: " + line).toList());
        lines.addAll(KingdomEconomy.describe(server, kingdom.get()).stream().map(line -> "Economy: " + line).toList());
        lines.add("Stockpile: " + kingdom.get().stockpile());
        List<String> planner = DebugLog.events().stream().map(DebugEvent::toLine)
                .filter(line -> line.contains("plan") || line.contains("levelled") || line.contains("laid the roads")).toList();
        lines.add("Planner events: " + planner.subList(Math.max(0, planner.size() - 20), planner.size()));
        return String.join("; ", lines);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Runnable run = perTick;
        if (run != null) {
            run.run();
        }
    }

    @AfterBatch(batch = BATCH)
    public static void restore(ServerLevel level) {
        perTick = null;
        MinecraftServer server = level.getServer();
        server.tickRateManager().stopSprinting();
        KingFounding.enableInTestsForTests(false);
        KingdomLayout.enableInTestsForTests(false);
        DebugLog.reset();
        KingdomKeep.useKeepForTests(null);
        KingdomEconomy.usePlanForTests(null);
        GameTestTuning.restore(server);
        disband(server);
    }

    private static void disband(MinecraftServer server) {
        Kingdoms.byTeam(server, TEAM).ifPresent(kingdom -> Kingdoms.disband(server, TEAM, null));
    }

    private static ResourceLocation csr(String path) {
        return ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, path);
    }
}
