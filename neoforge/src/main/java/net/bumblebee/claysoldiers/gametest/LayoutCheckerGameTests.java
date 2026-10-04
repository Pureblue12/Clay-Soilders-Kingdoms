package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.kingdom.layout.Cell;
import net.bumblebee.claysoldiers.kingdom.layout.Footprint;
import net.bumblebee.claysoldiers.kingdom.layout.LayoutChecker;
import net.bumblebee.claysoldiers.kingdom.layout.LayoutPlan;
import net.bumblebee.claysoldiers.kingdom.layout.RoadGraph;
import net.bumblebee.claysoldiers.kingdom.layout.WallLoop;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * Tests for the layout checker skeleton ({@link LayoutChecker}) on synthetic data (no world). Each asserts the CORRECT
 * behavior.
 * <p>
 * The valid layout: a 5x5 keep at the center, a road from each side of the keep out to the wall (radial roads), two
 * houses next to the roads, and a square wall 12 blocks out with gates where the roads cross it.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class LayoutCheckerGameTests {
    private static final Footprint KEEP = new Footprint("keep", "keep", -2, -2, 5, 5);
    private static final Footprint HOUSE_EAST = new Footprint("house_east", "house", 5, 1, 3, 3);
    private static final Footprint HOUSE_NORTH = new Footprint("house_north", "house", -3, -8, 3, 3);

    @GameTest(template = PLATFORM)
    public static void validLayoutPasses(GameTestHelper helper) {
        List<LayoutChecker.Problem> problems = LayoutChecker.check(validPlan(List.of(KEEP, HOUSE_EAST, HOUSE_NORTH), wall()));
        helper.assertTrue(problems.isEmpty(), "A valid layout should pass: " + problems);
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void overlappingFootprintsFail(GameTestHelper helper) {
        Footprint intruder = new Footprint("intruder", "house", 6, 2, 3, 3);
        List<LayoutChecker.Problem> problems = LayoutChecker.check(validPlan(List.of(KEEP, HOUSE_EAST, HOUSE_NORTH, intruder), wall()));
        assertProblem(helper, problems, LayoutChecker.Kind.OVERLAP, "house_east", "intruder");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void missingRoadAccessFails(GameTestHelper helper) {
        Footprint lonely = new Footprint("lonely", "house", 5, -9, 3, 3);
        List<LayoutChecker.Problem> problems = LayoutChecker.check(validPlan(List.of(KEEP, HOUSE_EAST, HOUSE_NORTH, lonely), wall()));
        assertProblem(helper, problems, LayoutChecker.Kind.NO_ROAD_ACCESS, "lonely");
        helper.assertTrue(problems.size() == 1, "Only the lonely house should fail: " + problems);

        // A road that doesn't connect to the keep's roads doesn't count.
        RoadGraph roads = radialRoads().road(new Cell(4, -10), new Cell(9, -10));
        problems = LayoutChecker.check(new LayoutPlan(List.of(KEEP, HOUSE_EAST, HOUSE_NORTH, lonely), roads, wall(), new Cell(0, 0)));
        assertProblem(helper, problems, LayoutChecker.Kind.NO_ROAD_ACCESS, "lonely");
        helper.succeed();
    }

    @GameTest(template = PLATFORM)
    public static void wallWithOneBlockGapFails(GameTestHelper helper) {
        Cell gap = new Cell(12, 7);
        List<LayoutChecker.Problem> problems = LayoutChecker.check(validPlan(List.of(KEEP, HOUSE_EAST, HOUSE_NORTH), wall().without(gap)));
        assertProblem(helper, problems, LayoutChecker.Kind.WALL_GAP, gap.toString());
        helper.succeed();
    }

    /**
     * Two wall pieces that only touch at a corner are closed: nothing can squeeze diagonally between them.
     */
    @GameTest(template = PLATFORM)
    public static void diagonalWallIsClosed(GameTestHelper helper) {
        List<Cell> diamond = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            diamond.add(new Cell(i, i - 10));
            diamond.add(new Cell(10 - i, i));
            diamond.add(new Cell(-i, 10 - i));
            diamond.add(new Cell(i - 10, -i));
        }
        List<LayoutChecker.Problem> problems = LayoutChecker.check(new LayoutPlan(List.of(), new RoadGraph(), WallLoop.ofCells(diamond), new Cell(0, 0)));
        helper.assertTrue(problems.isEmpty(), "A wall of corner-touching cells is closed: " + problems);
        helper.succeed();
    }

    private static LayoutPlan validPlan(List<Footprint> buildings, WallLoop wall) {
        return new LayoutPlan(buildings, radialRoads(), wall, new Cell(0, 0));
    }

    /** A road from the middle of each keep side out to the wall. */
    private static RoadGraph radialRoads() {
        return new RoadGraph()
                .road(new Cell(3, 0), new Cell(11, 0))
                .road(new Cell(-3, 0), new Cell(-11, 0))
                .road(new Cell(0, 3), new Cell(0, 11))
                .road(new Cell(0, -3), new Cell(0, -11));
    }

    /** A square wall 12 blocks out (gates where the roads meet it count as wall). */
    private static WallLoop wall() {
        return WallLoop.fromCorners(List.of(new Cell(-12, -12), new Cell(12, -12), new Cell(12, 12), new Cell(-12, 12)));
    }

    private static void assertProblem(GameTestHelper helper, List<LayoutChecker.Problem> problems, LayoutChecker.Kind kind, String... words) {
        helper.assertTrue(problems.stream().anyMatch(problem -> problem.kind() == kind && List.of(words).stream().allMatch(problem.message()::contains)),
                "No " + kind + " problem mentioning " + List.of(words) + " in " + problems);
    }
}
