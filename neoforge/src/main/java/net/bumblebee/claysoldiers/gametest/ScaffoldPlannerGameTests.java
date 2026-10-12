package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.BlueprintTemplateSettings;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.bumblebee.claysoldiers.dev.DevTool;
import net.bumblebee.claysoldiers.dev.DevToolRegistry;
import net.bumblebee.claysoldiers.init.ModRegistries;
import net.bumblebee.claysoldiers.scaffold.ScaffoldApproach;
import net.bumblebee.claysoldiers.scaffold.ScaffoldInput;
import net.bumblebee.claysoldiers.scaffold.ScaffoldPlan;
import net.bumblebee.claysoldiers.scaffold.ScaffoldPlanner;
import net.bumblebee.claysoldiers.scaffold.TestStructures;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * The scaffold planner on generated structures on flat ground (pure data, no world). Each asserts the CORRECT behavior.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class ScaffoldPlannerGameTests {
    /** Budget for the 20-block tower (160 structure blocks): at most 40 scaffold blocks (a quarter). */
    static final int TOWER_BUDGET = 40;
    /** Budget for the 12-high, 16-long wall (192 blocks). */
    static final int WALL_BUDGET = 40;
    /** Budget for the corner tower (14 high) on two 10-high walls. */
    static final int WALL_TOWER_BUDGET = 60;

    static ScaffoldInput flat(TestStructures.Shape shape, int height) {
        return ScaffoldInput.flat(TestStructures.blocks(shape, height), -1);
    }

    static ScaffoldPlan inGame(ScaffoldInput input) {
        return ScaffoldPlanner.plan(input, ScaffoldApproach.STEP_PILLARS);
    }

    /** A 20-block tower: every block is reachable at some step, the scaffold comes down again, within budget. */
    @GameTest(template = PLATFORM)
    public static void plannerReachesEveryTowerBlock(GameTestHelper helper) {
        ScaffoldPlan plan = inGame(flat(TestStructures.Shape.TOWER, 20));
        helper.assertTrue(plan.complete() && plan.unreachable().isEmpty(), "Not every block is reachable: " + plan.describe());
        helper.assertTrue(plan.teardownComplete(), "The scaffold can't be taken down: " + plan.describe());
        helper.assertTrue(!plan.scaffold().isEmpty() && plan.scaffold().size() <= TOWER_BUDGET,
                "Scaffold blocks: " + plan.scaffold().size() + ", budget " + TOWER_BUDGET);
        helper.assertTrue(plan.scaffold().stream().noneMatch(Set.copyOf(TestStructures.blocks(TestStructures.Shape.TOWER, 20))::contains),
                "Scaffold inside the structure");
        helper.succeed();
    }

    /** Within reach (9 high: builders reach 8 above their feet) there is no scaffold. */
    @GameTest(template = PLATFORM)
    public static void plannerGivesNoScaffoldWithinReach(GameTestHelper helper) {
        ScaffoldPlan plan = inGame(flat(TestStructures.Shape.TOWER, 9));
        helper.assertTrue(plan.complete() && plan.scaffold().isEmpty(), "A 9-high tower needs no scaffold: " + plan.describe());
        helper.succeed();
    }

    /** One block taller than reach (10 high) needs scaffold. */
    @GameTest(template = PLATFORM)
    public static void plannerNeedsScaffoldAboveReach(GameTestHelper helper) {
        ScaffoldPlan plan = inGame(flat(TestStructures.Shape.TOWER, 10));
        helper.assertTrue(plan.complete() && !plan.scaffold().isEmpty(), "A 10-high tower needs scaffold: " + plan.describe());
        helper.succeed();
    }

    /**
     * All four approaches are planned for the 20-block tower: the ramp, step pillars and platforms reach every block
     * and come down; vanilla scaffolding is not usable by soldiers.
     */
    @GameTest(template = PLATFORM)
    public static void plannerComparesApproaches(GameTestHelper helper) {
        Map<ScaffoldApproach, ScaffoldPlan> plans = ScaffoldPlanner.compare(flat(TestStructures.Shape.TOWER, 20)).stream()
                .collect(Collectors.toMap(ScaffoldPlan::approach, Function.identity()));
        helper.assertTrue(plans.size() == ScaffoldApproach.values().length, "Missing approaches: " + plans.keySet());
        for (ScaffoldApproach approach : List.of(ScaffoldApproach.OUTSIDE_RAMP, ScaffoldApproach.STEP_PILLARS, ScaffoldApproach.PLATFORMS)) {
            ScaffoldPlan plan = plans.get(approach);
            helper.assertTrue(plan.usable() && !plan.scaffold().isEmpty(), approach + " should work: " + plan.describe());
        }
        ScaffoldPlan vanilla = plans.get(ScaffoldApproach.VANILLA_SCAFFOLDING);
        helper.assertTrue(!vanilla.usable() && !vanilla.scaffold().isEmpty(), "Vanilla scaffolding must be counted but not usable: " + vanilla.describe());
        ClaySoldiersCommon.LOGGER.info("CSR scaffold comparison, tower 20: {}", plans.values().stream().map(ScaffoldPlan::describe).toList());
        helper.succeed();
    }

    /** A long wall (16 long, 12 high) and a corner tower (14 high) on two walls (10 high): complete, within budget. */
    @GameTest(template = PLATFORM)
    public static void plannerHandlesWallAndCornerTower(GameTestHelper helper) {
        ScaffoldPlan wall = inGame(flat(TestStructures.Shape.WALL, 12));
        helper.assertTrue(wall.usable() && !wall.scaffold().isEmpty() && wall.scaffold().size() <= WALL_BUDGET, "Wall: " + wall.describe());
        ScaffoldPlan corner = inGame(flat(TestStructures.Shape.WALL_TOWER, 10));
        helper.assertTrue(corner.usable() && !corner.scaffold().isEmpty() && corner.scaffold().size() <= WALL_TOWER_BUDGET, "Corner tower: " + corner.describe());
        helper.succeed();
    }

    /**
     * Most time the planner may take for the biggest test structure (it runs on the server thread). Doubled on CI
     * (GitHub sets {@code CI=true}): its shared machines are slower (663 ms there); still far below the 2.6 s found in game.
     */
    static final long PLAN_MILLIS = "true".equals(System.getenv("CI")) ? 1000 : 500;

    /**
     * The planner is quick enough to run in game: climbed columns for a corner tower (24 high) on two 20-high walls
     * (592 blocks) take less than {@link #PLAN_MILLIS} ms and reach every block. Found in game: it took 2.6 s there.
     */
    @GameTest(template = PLATFORM)
    public static void plannerIsQuickForTallWallWithTower(GameTestHelper helper) {
        ScaffoldInput input = flat(TestStructures.Shape.WALL_TOWER, 20);
        ScaffoldPlanner.plan(flat(TestStructures.Shape.TOWER, 10), ScaffoldApproach.CLIMBED_COLUMNS); // warm up
        long start = System.nanoTime();
        ScaffoldPlan plan = ScaffoldPlanner.plan(input, ScaffoldApproach.CLIMBED_COLUMNS);
        long millis = (System.nanoTime() - start) / 1_000_000;
        ClaySoldiersCommon.LOGGER.info("CSR scaffold planner: wall_tower 20 ({} blocks) planned in {} ms: {}", input.structure().size(), millis, plan.describe());
        helper.assertTrue(plan.usable(), "Not every block reachable: " + plan.describe());
        helper.assertTrue(millis < PLAN_MILLIS, "Planning took " + millis + " ms (limit " + PLAN_MILLIS + ")");
        helper.succeed();
    }

    /**
     * A request search doesn't path-search blocks it just found out of reach again (found in game: with 500 blocks out
     * of reach every search took about 1 s, freezing the server): for {@code build.unreachable_recheck_ticks} they are
     * skipped (still counted as out of reach), until the build changes ({@code forgetUnreachable}) or the time is up.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void unreachableBlocksAreNotSearchedAgainRightAway(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerBlueprintPlan plan = TestStructures.blueprint(level, TestStructures.Shape.TOWER, 20).createServerPlan().orElseThrow();
        BlueprintTemplateSettings settings = new BlueprintTemplateSettings(plan.getSize(), Mirror.NONE, Rotation.NONE);
        BlockPos base = helper.absolutePos(new BlockPos(2, 2, 2));
        int[] checks = {0};
        java.util.function.Predicate<BlockPos> nothingReachable = pos -> {
            checks[0]++;
            return false;
        };
        int blocks = plan.targetPositions(base, settings).size();
        helper.assertTrue(plan.getRequest(level, base, settings, nothingReachable) == null && checks[0] == blocks,
                "first search should check all " + blocks + " blocks, checked " + checks[0]);
        helper.assertTrue(plan.lastSearchFoundUnreachable(), "blocks out of reach must still count as out of reach");
        checks[0] = 0;
        plan.getRequest(level, base, settings, nothingReachable);
        helper.assertTrue(checks[0] == 0, "second search right away path-searched " + checks[0] + " blocks again");
        helper.assertTrue(plan.lastSearchFoundUnreachable(), "skipped blocks must still count as out of reach");
        plan.forgetUnreachable();
        plan.getRequest(level, base, settings, nothingReachable);
        helper.assertTrue(checks[0] == blocks, "after the build changed every block is checked again, checked " + checks[0]);
        checks[0] = 0;
        helper.runAfterDelay(Tuning.BUILD_UNREACHABLE_RECHECK_TICKS.get() + 1, () -> {
            plan.getRequest(level, base, settings, nothingReachable);
            helper.assertTrue(checks[0] == blocks, "after the wait every block is checked again, checked " + checks[0]);
            helper.succeed();
        });
    }

    /**
     * A bell tower like the church's: a 5 by 5 tower with a door, a floor at y 10 builders can't walk to, and a spire
     * whose top is 18 up, right above the tower's middle. The highest column cell under it is 9 (below the floor), one
     * more than the usual reach: only stretching up from inside the column reaches it (D78).
     */
    @GameTest(template = PLATFORM)
    public static void spireTopIsReachedStretchingUpFromAColumn(GameTestHelper helper) {
        ScaffoldInput input = ScaffoldInput.flat(bellTower(), 0);
        BlockPos top = new BlockPos(2, 18, 2);
        ScaffoldPlan usual = ScaffoldPlanner.plan(input, ScaffoldApproach.CLIMBED_COLUMNS);
        helper.assertTrue(usual.unreachable().contains(top), "The spire top is in the usual reach, the test shows nothing: " + usual.describe());
        ScaffoldPlan stretched = ScaffoldPlanner.plan(input.withColumnReachBonus(2), ScaffoldApproach.CLIMBED_COLUMNS);
        helper.assertTrue(stretched.complete() && stretched.unreachable().isEmpty(), "Not every block is reachable stretching up: " + stretched.describe());
        helper.assertTrue(stretched.teardownComplete(), "The scaffold can't be taken down: " + stretched.describe());
        helper.succeed();
    }

    /** The bell tower of {@link #spireTopIsReachedStretchingUpFromAColumn}, on ground at y 0. */
    private static List<BlockPos> bellTower() {
        List<BlockPos> tower = new ArrayList<>();
        for (int y = 1; y <= 10; y++) {
            for (int x = 0; x <= 4; x++) {
                for (int z = 0; z <= 4; z++) {
                    boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
                    boolean door = x == 2 && z == 0 && y <= 2;
                    if (y == 10 || wall && !door) {
                        tower.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                tower.add(new BlockPos(x, 11, z));
            }
        }
        for (int y = 12; y <= 18; y++) {
            tower.add(new BlockPos(2, y, 2));
        }
        return tower;
    }

    /**
     * A hall 11 by 11 with walls 11 high, a door gap and a flat roof on top: columns outside the footprint reach only 2
     * blocks into the roof, so its middle needs columns standing on the floor inside (D69, like the keep's roof and the
     * chapel's steeple cap). Every block must be reachable and the scaffold must come down again.
     */
    @GameTest(template = PLATFORM)
    public static void plannerPutsColumnsInsideAWideRoof(GameTestHelper helper) {
        ScaffoldPlan plan = ScaffoldPlanner.plan(ScaffoldInput.flat(wideHall(), 0), ScaffoldApproach.CLIMBED_COLUMNS);
        helper.assertTrue(plan.complete() && plan.unreachable().isEmpty(), "Not every block is reachable: " + plan.describe());
        helper.assertTrue(plan.teardownComplete(), "The scaffold can't be taken down: " + plan.describe());
        helper.assertTrue(plan.scaffold().stream().anyMatch(pos -> pos.getX() > 0 && pos.getX() < 10 && pos.getZ() > 0 && pos.getZ() < 10),
                "No column inside the hall: " + plan.describe());
        helper.succeed();
    }

    /**
     * The same hall with every other floor cell inside a stair or carpet (like the keep L2 annex's pews): scaffolding put on
     * such a floor falls at once, so no column may stand there, and the roof must still be reachable from the others (D77).
     */
    @GameTest(template = PLATFORM)
    public static void columnsDoNotStandOnStairOrCarpetFloors(GameTestHelper helper) {
        Set<BlockPos> pews = new java.util.HashSet<>();
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) {
                if ((x + z) % 2 == 0) {
                    pews.add(new BlockPos(x, 0, z));
                }
            }
        }
        ScaffoldPlan plan = ScaffoldPlanner.plan(ScaffoldInput.flat(wideHall(), 0).withNoFooting(pews), ScaffoldApproach.CLIMBED_COLUMNS);
        Set<BlockPos> scaffold = Set.copyOf(plan.scaffold());
        List<BlockPos> onPews = scaffold.stream().filter(pos -> !scaffold.contains(pos.below()) && pews.contains(pos.below())).toList();
        helper.assertTrue(onPews.isEmpty(), "Columns stand on a stair or carpet floor at " + onPews + ": " + plan.describe());
        helper.assertTrue(plan.complete() && plan.unreachable().isEmpty(), "Not every block is reachable: " + plan.describe());
        helper.succeed();
    }

    /**
     * A hall 11 by 11 with walls 11 high, a door gap and a flat roof on top (y 12), on ground at y 0.
     */
    private static List<BlockPos> wideHall() {
        List<BlockPos> hall = new ArrayList<>();
        for (int y = 1; y <= 12; y++) {
            for (int x = 0; x <= 10; x++) {
                for (int z = 0; z <= 10; z++) {
                    boolean wall = x == 0 || x == 10 || z == 0 || z == 10;
                    boolean door = x == 5 && z == 0 && y <= 2;
                    if (y == 12 || wall && !door) {
                        hall.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return hall;
    }

    /** The Small House (151 blocks) is within reach everywhere: no scaffold. */
    @GameTest(template = PLATFORM)
    public static void plannerSmallHouseNeedsNoScaffold(GameTestHelper helper) {
        BlueprintData house = helper.getLevel().registryAccess().registryOrThrow(ModRegistries.BLUEPRINTS).get(DevScenarios.SMALL_HOUSE);
        helper.assertTrue(house != null && house.isValid(), "No Small House blueprint");
        ServerBlueprintPlan plan = house.createServerPlan().orElseThrow();
        List<BlockPos> blocks = plan.targetPositions(BlockPos.ZERO, new BlueprintTemplateSettings(plan.getSize(), Mirror.NONE, Rotation.NONE));
        ScaffoldPlan scaffold = inGame(ScaffoldInput.flat(new ArrayList<>(blocks), -1));
        helper.assertTrue(scaffold.scaffold().isEmpty(), "The Small House got scaffold: " + scaffold.describe());
        helper.succeed();
    }

    /** The dev book has a Scaffolding category with build, plan, status, cleanup and undo. */
    @GameTest(template = PLATFORM)
    public static void devBookHasScaffoldButtons(GameTestHelper helper) {
        Set<String> commands = DevToolRegistry.defaults().tools().stream().map(DevTool::command).collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        for (String command : List.of("/csr dev scaffold build", "/csr dev scaffold plan", "/csr dev scaffold status",
                "/csr dev scaffold cleanup", "/csr dev scaffold undo")) {
            if (!commands.contains(command)) {
                missing.add(command);
            }
        }
        helper.assertTrue(missing.isEmpty(), "No dev book entry for " + missing);
        helper.succeed();
    }
}
