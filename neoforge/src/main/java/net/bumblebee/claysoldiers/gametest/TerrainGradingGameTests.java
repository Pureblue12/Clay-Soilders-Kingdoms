package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.dev.DevTool;
import net.bumblebee.claysoldiers.dev.DevToolRegistry;
import net.bumblebee.claysoldiers.structure.StructurePaster;
import net.bumblebee.claysoldiers.terrain.GradingMethod;
import net.bumblebee.claysoldiers.terrain.GradingPlan;
import net.bumblebee.claysoldiers.terrain.GradingPlanner;
import net.bumblebee.claysoldiers.terrain.SiteRules;
import net.bumblebee.claysoldiers.terrain.SiteScore;
import net.bumblebee.claysoldiers.terrain.SiteScorer;
import net.bumblebee.claysoldiers.terrain.Terrain;
import net.bumblebee.claysoldiers.terrain.TerrainScan;
import net.bumblebee.claysoldiers.terrain.TerrainScanner;
import net.bumblebee.claysoldiers.terrain.TestTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;
import static net.bumblebee.claysoldiers.gametest.TerrainScanGameTests.RADIUS;
import static net.bumblebee.claysoldiers.gametest.TerrainScanGameTests.TERRAIN;

/**
 * The grading methods on built test terrains: each leaves the site level (or tree-free) within its budget, and undo
 * restores the terrain exactly. Each asserts the CORRECT behavior. The undo history is global, so every test that
 * grades has its own batch.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class TerrainGradingGameTests {

    /** (b) Cut and fill on the slope: level, within terrain.grade.cut_fill_budget; undo restores it. */
    @GameTest(template = TERRAIN, batch = "terrain_grade_cut_fill")
    public static void gradeCutFillLevelsSlope(GameTestHelper helper) {
        BlockPos c = TerrainScanGameTests.build(helper, TestTerrain.Shape.SLOPE);
        Map<BlockPos, BlockState> before = blocks(helper, c);
        Terrain.Graded graded = Terrain.grade(helper.getLevel(), c, GradingMethod.CUT_FILL);
        helper.assertTrue(graded.applied(), "Cut and fill wasn't applied: " + graded.plan().describe());
        SiteRules rules = SiteRules.current();
        SiteScore after = siteScore(helper, graded.plan());
        helper.assertTrue(after.metrics().heightRange() == 0, "The site isn't level after cut and fill: " + after.metrics());
        helper.assertTrue(graded.placed() > 0 && graded.removed() > 0, "Expected both cut and fill: " + graded);
        helper.assertTrue(graded.placed() + graded.removed() <= rules.cutFillBudget(), "Over budget: " + graded + ", budget " + rules.cutFillBudget());
        undoRestores(helper, before, c);
        helper.succeed();
    }

    /** (c) A foundation platform on the slope: level, within terrain.grade.foundation_budget; undo restores it. */
    @GameTest(template = TERRAIN, batch = "terrain_grade_foundation")
    public static void gradeFoundationLevelsSlope(GameTestHelper helper) {
        BlockPos c = TerrainScanGameTests.build(helper, TestTerrain.Shape.SLOPE);
        Map<BlockPos, BlockState> before = blocks(helper, c);
        Terrain.Graded graded = Terrain.grade(helper.getLevel(), c, GradingMethod.FOUNDATION);
        helper.assertTrue(graded.applied(), "The foundation wasn't applied: " + graded.plan().describe());
        SiteRules rules = SiteRules.current();
        SiteScore after = siteScore(helper, graded.plan());
        helper.assertTrue(after.metrics().heightRange() == 0, "The site isn't level on the foundation: " + after.metrics());
        helper.assertTrue(graded.plan().level() == c.getY() + 2, "The platform should be at the highest ground (" + (c.getY() + 2) + "): " + graded.plan().level());
        helper.assertTrue(graded.placed() > 0 && graded.placed() <= rules.foundationBudget(), "Placed " + graded.placed() + ", budget " + rules.foundationBudget());
        undoRestores(helper, before, c);
        helper.succeed();
    }

    /** (a) The flattest site: from near the plateau's edge it picks the level top; no terrain changes; undo restores the markers. */
    @GameTest(template = TERRAIN, batch = "terrain_grade_flattest")
    public static void gradeFlattestPicksPlateauTop(GameTestHelper helper) {
        BlockPos c = TerrainScanGameTests.build(helper, TestTerrain.Shape.PLATEAU);
        Map<BlockPos, BlockState> before = blocks(helper, c);
        Terrain.Graded graded = Terrain.grade(helper.getLevel(), c.offset(4, 0, 4), GradingMethod.FLATTEST);
        GradingPlan plan = graded.plan();
        helper.assertTrue(graded.applied() && plan.centerX() == c.getX() && plan.centerZ() == c.getZ(),
                "Expected the plateau's center " + c.getX() + ", " + c.getZ() + ": " + plan.describe());
        helper.assertTrue(plan.earthPlaced() == 0 && plan.earthRemoved() == 0 && graded.placed() == 0 && graded.removed() == 0,
                "The flattest site changes no terrain: " + graded);
        helper.assertTrue(siteScore(helper, plan).metrics().heightRange() == 0, "The chosen site isn't level");
        undoRestores(helper, before, c);
        helper.succeed();
    }

    /** (d) Clearing trees: no tree is left on the site, within terrain.grade.clear_trees_budget; undo restores the forest. */
    @GameTest(template = TERRAIN, batch = "terrain_grade_clear_trees")
    public static void gradeClearTreesClearsForest(GameTestHelper helper) {
        BlockPos c = TerrainScanGameTests.build(helper, TestTerrain.Shape.FOREST);
        Map<BlockPos, BlockState> before = blocks(helper, c);
        Terrain.Graded graded = Terrain.grade(helper.getLevel(), c, GradingMethod.CLEAR_TREES);
        helper.assertTrue(graded.applied(), "Clearing wasn't applied: " + graded.plan().describe());
        SiteRules rules = SiteRules.current();
        SiteScore after = siteScore(helper, graded.plan());
        helper.assertTrue(after.metrics().treePercent() == 0 && after.metrics().heightRange() == 0, "Trees left on the site: " + after.metrics());
        helper.assertTrue(graded.removed() > 0 && graded.removed() <= rules.clearTreesBudget() && graded.placed() == 0,
                "Removed " + graded.removed() + ", budget " + rules.clearTreesBudget());
        undoRestores(helper, before, c);
        helper.succeed();
    }

    /** (d) Working around trees: the chosen site is level and has no trees; no terrain changes. */
    @GameTest(template = TERRAIN, batch = "terrain_grade_avoid_trees")
    public static void gradeAvoidTreesFindsClearing(GameTestHelper helper) {
        BlockPos c = TerrainScanGameTests.build(helper, TestTerrain.Shape.FOREST);
        Map<BlockPos, BlockState> before = blocks(helper, c);
        Terrain.Graded graded = Terrain.grade(helper.getLevel(), c, GradingMethod.AVOID_TREES);
        helper.assertTrue(graded.applied(), "Avoiding trees found nothing: " + graded.plan().describe());
        SiteScore site = siteScore(helper, graded.plan());
        helper.assertTrue(site.ok() && site.metrics().treePercent() == 0 && site.metrics().heightRange() == 0,
                "The chosen site has trees or isn't level: " + graded.plan().describe() + " " + site.metrics());
        helper.assertTrue(graded.placed() == 0 && graded.removed() == 0, "Avoiding trees changes no terrain: " + graded);
        undoRestores(helper, before, c);
        helper.succeed();
    }

    /** Over budget or too high: the plan says it can't, and why (no world change). */
    @GameTest(template = PLATFORM)
    public static void gradingRefusesOverBudget(GameTestHelper helper) {
        SiteRules rules = TerrainScoreGameTests.defaults();
        TerrainScan steep = mountain(rules.siteScanRadius());
        GradingPlan cut = GradingPlanner.plan(GradingMethod.CUT_FILL, steep, 0, 0, rules);
        helper.assertTrue(!cut.feasible() && cut.reason().contains("budget"), "Cut and fill on a mountain should be over budget: " + cut.describe());
        GradingPlan foundation = GradingPlanner.plan(GradingMethod.FOUNDATION, steep, 0, 0, rules);
        helper.assertTrue(!foundation.feasible() && foundation.reason().contains("high"), "A foundation on a mountain is too high: " + foundation.describe());
        GradingPlan cheap = GradingPlanner.plan(GradingMethod.CUT_FILL, steep, 0, 0, TerrainScoreGameTests.rules("{\"terrain.grade.cut_fill_budget\": 100000}"));
        helper.assertTrue(cheap.feasible(), "With a big budget it should be possible: " + cheap.describe());
        for (GradingMethod method : GradingMethod.values()) {
            helper.assertTrue(!GradingPlanner.howSoldiers(method).isBlank() && !GradingPlanner.howSoldiers(method).equals("not implemented"),
                    "No description of how soldiers would do " + method);
        }
        helper.succeed();
    }

    /** The dev book has a Terrain category with scan, site, make, grade and undo. */
    @GameTest(template = PLATFORM)
    public static void devBookHasTerrainButtons(GameTestHelper helper) {
        Set<String> commands = DevToolRegistry.defaults().tools().stream().map(DevTool::command).collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        for (String command : List.of("/csr dev terrain scan", "/csr dev terrain site", "/csr dev terrain make", "/csr dev terrain grade",
                "/csr dev terrain undo")) {
            if (!commands.contains(command)) {
                missing.add(command);
            }
        }
        helper.assertTrue(missing.isEmpty(), "No dev book entry for " + missing);
        helper.succeed();
    }

    /** The TERRAIN recorder category gets scans, site scores and grading. */
    @GameTest(template = TERRAIN, batch = "terrain_log")
    public static void terrainEventsAreLogged(GameTestHelper helper) {
        BlockPos c = TerrainScanGameTests.build(helper, TestTerrain.Shape.SLOPE);
        DebugLog.reset();
        DebugCategory terrain = DebugCategory.byId("TERRAIN").orElseThrow();
        DebugLog.setEnabled(terrain, true, helper.getLevel().getServer());
        Terrain.checkSite(helper.getLevel(), c, true);
        Terrain.grade(helper.getLevel(), c, GradingMethod.CUT_FILL);
        Terrain.grade(helper.getLevel(), c, GradingMethod.FLATTEST);
        StructurePaster.undo(helper.getLevel());
        Map<String, Boolean> seen = new HashMap<>();
        for (String text : List.of("scanned", "site", "graded")) {
            seen.put(text, DebugLog.contains(terrain, text));
        }
        DebugLog.reset();
        helper.assertTrue(!seen.containsValue(false), "Missing TERRAIN events: " + seen);
        helper.succeed();
    }

    /** A diamond-shaped mountain 2 up per block in every direction (20 blocks high across a keep site). */
    static TerrainScan mountain(int radius) {
        TerrainScan scan = TerrainScan.flat(0, 0, radius, TerrainScoreGameTests.H);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                scan.setGround(x, z, TerrainScoreGameTests.H + 2 * (2 * radius - Math.abs(x) - Math.abs(z)));
            }
        }
        return scan;
    }

    static SiteScore siteScore(GameTestHelper helper, GradingPlan plan) {
        SiteRules rules = SiteRules.current();
        BlockPos center = new BlockPos(plan.centerX(), 0, plan.centerZ());
        return SiteScorer.score(TerrainScanner.scan(helper.getLevel(), center, rules.siteScanRadius()), plan.centerX(), plan.centerZ(), rules);
    }

    static Map<BlockPos, BlockState> blocks(GameTestHelper helper, BlockPos center) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BoundingBox box = TestTerrain.box(center, RADIUS);
        BlockPos.betweenClosedStream(box).forEach(pos -> blocks.put(pos.immutable(), helper.getLevel().getBlockState(pos)));
        return blocks;
    }

    static void undoRestores(GameTestHelper helper, Map<BlockPos, BlockState> before, BlockPos center) {
        helper.assertTrue(StructurePaster.undo(helper.getLevel()).isPresent(), "Nothing to undo");
        Map<BlockPos, BlockState> after = blocks(helper, center);
        List<BlockPos> changed = before.keySet().stream().filter(pos -> !before.get(pos).equals(after.get(pos))).limit(5).toList();
        helper.assertTrue(changed.isEmpty(), "Undo didn't restore the terrain exactly, e.g. at " + changed.stream()
                .map(pos -> pos.toShortString() + " " + before.get(pos) + " -> " + after.get(pos)).toList());
    }
}
