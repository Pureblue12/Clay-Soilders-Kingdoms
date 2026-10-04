package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.terrain.Factor;
import net.bumblebee.claysoldiers.terrain.SiteRules;
import net.bumblebee.claysoldiers.terrain.SiteScore;
import net.bumblebee.claysoldiers.terrain.SiteScorer;
import net.bumblebee.claysoldiers.terrain.SiteSearch;
import net.bumblebee.claysoldiers.terrain.TerrainScan;
import net.bumblebee.claysoldiers.terrain.TerrainScanner;
import net.bumblebee.claysoldiers.terrain.TestTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The terrain scan on built test terrains ({@link TestTerrain}) in the 32 by 32 "terrain" template, and the site search
 * on real terrain. Each asserts the CORRECT behavior.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class TerrainScanGameTests {
    static final String TERRAIN = "gametest/terrain";
    /** The terrain's center column (relative), at the height of its base. */
    static final BlockPos CENTER = new BlockPos(16, 8, 16);
    /** Test terrains reach 13 blocks from the center (the template is 32 wide). */
    static final int RADIUS = 13;
    /** Most a scan of radius {@link #COST_RADIUS} (16,641 columns) may take on average, in milliseconds. */
    static final double COST_BUDGET_MS = 50;
    static final int COST_RADIUS = 64;

    static BlockPos build(GameTestHelper helper, TestTerrain.Shape shape) {
        BlockPos center = helper.absolutePos(CENTER);
        TestTerrain.build(helper.getLevel(), center, shape, RADIUS);
        return center;
    }

    static SiteScore scoreAt(GameTestHelper helper, BlockPos center, int dx, int dz) {
        SiteRules rules = SiteRules.current();
        TerrainScan scan = TerrainScanner.scan(helper.getLevel(), center.offset(dx, 0, dz), rules.siteScanRadius());
        return SiteScorer.score(scan, center.getX() + dx, center.getZ() + dz, rules);
    }

    static TerrainScan scan(GameTestHelper helper, BlockPos center) {
        return TerrainScanner.scan(helper.getLevel(), center, RADIUS);
    }

    /** Slope: 1 up every 2 blocks going east. Usable, with slope and height-range penalties. */
    @GameTest(template = TERRAIN)
    public static void scanSlope(GameTestHelper helper) {
        BlockPos c = build(helper, TestTerrain.Shape.SLOPE);
        TerrainScan scan = scan(helper, c);
        int y = c.getY();
        helper.assertTrue(scan.ground(c.getX() + 4, c.getZ()) == y + 2 && scan.ground(c.getX() - 5, c.getZ()) == y - 3,
                "Slope heights: " + scan.ground(c.getX() + 4, c.getZ()) + ", " + scan.ground(c.getX() - 5, c.getZ()) + " (base " + y + ")");
        SiteScore score = scoreAt(helper, c, 0, 0);
        SiteScore.Metrics m = score.metrics();
        helper.assertTrue(m.heightRange() == 5 && m.slopePercent() == 50 && m.maxStep() == 1, "Slope metrics: " + m);
        helper.assertTrue(score.ok() && score.penalizedFor(Factor.SLOPE) && score.penalizedFor(Factor.HEIGHT_RANGE), "Slope score: " + score.describe());
        helper.succeed();
    }

    /** Hill: 9 high, 1 up per block: too steep, but no cliff. */
    @GameTest(template = TERRAIN)
    public static void scanHill(GameTestHelper helper) {
        BlockPos c = build(helper, TestTerrain.Shape.HILL);
        TerrainScan scan = scan(helper, c);
        helper.assertTrue(scan.ground(c.getX(), c.getZ()) == c.getY() + 9 && scan.ground(c.getX() + 3, c.getZ() + 2) == c.getY() + 4,
                "Hill heights: " + scan.ground(c.getX(), c.getZ()) + ", " + scan.ground(c.getX() + 3, c.getZ() + 2));
        SiteScore score = scoreAt(helper, c, 0, 0);
        helper.assertTrue(score.refusedFor(Factor.TOO_STEEP) && !score.refusedFor(Factor.CLIFF) && score.metrics().maxStep() == 1,
                "Hill: " + score.describe() + " " + score.metrics());
        helper.succeed();
    }

    /** One-sided cliff: a 6-block step at the center. */
    @GameTest(template = TERRAIN)
    public static void scanCliff(GameTestHelper helper) {
        BlockPos c = build(helper, TestTerrain.Shape.CLIFF);
        TerrainScan scan = scan(helper, c);
        helper.assertTrue(scan.ground(c.getX() - 1, c.getZ()) == c.getY() && scan.ground(c.getX(), c.getZ()) == c.getY() + 6,
                "Cliff heights: " + scan.ground(c.getX() - 1, c.getZ()) + ", " + scan.ground(c.getX(), c.getZ()));
        SiteScore score = scoreAt(helper, c, 0, 0);
        helper.assertTrue(score.refusedFor(Factor.CLIFF) && score.metrics().maxStep() == 6, "Cliff: " + score.describe() + " " + score.metrics());
        helper.succeed();
    }

    /** Lake edge: shallow then 4-deep water east of the center. Deep water is refused. */
    @GameTest(template = TERRAIN)
    public static void scanLakeEdge(GameTestHelper helper) {
        BlockPos c = build(helper, TestTerrain.Shape.LAKE_EDGE);
        TerrainScan scan = scan(helper, c);
        int x = c.getX();
        int z = c.getZ();
        int y = c.getY();
        helper.assertTrue(scan.ground(x + 4, z) == y - 4 && scan.waterDepth(x + 4, z) == 4 && scan.surface(x + 4, z) == y,
                "Deep part: ground " + scan.ground(x + 4, z) + ", depth " + scan.waterDepth(x + 4, z));
        helper.assertTrue(scan.ground(x + 1, z) == y - 1 && scan.waterDepth(x + 1, z) == 1, "Shallow part: depth " + scan.waterDepth(x + 1, z));
        helper.assertTrue(scan.ground(x - 2, z) == y && scan.waterDepth(x - 2, z) == 0, "Land: depth " + scan.waterDepth(x - 2, z));
        SiteScore score = scoreAt(helper, c, 0, 0);
        helper.assertTrue(score.refusedFor(Factor.DEEP_WATER) && score.metrics().maxWaterDepth() == 4 && score.metrics().waterPercent() == 45,
                "Lake edge: " + score.describe() + " " + score.metrics());
        helper.succeed();
    }

    /** Forest: logs and leaves are tree blocks, not ground. Usable, with a tree penalty. */
    @GameTest(template = TERRAIN)
    public static void scanForest(GameTestHelper helper) {
        BlockPos c = build(helper, TestTerrain.Shape.FOREST);
        TerrainScan scan = scan(helper, c);
        int x = c.getX();
        int z = c.getZ();
        helper.assertTrue(scan.ground(x, z) == c.getY() && scan.trees(x, z) && scan.treeBlocks(x, z) == 6,
                "Trunk column: ground " + scan.ground(x, z) + ", tree blocks " + scan.treeBlocks(x, z));
        helper.assertTrue(scan.treeBlocks(x + 1, z + 1) == 2 && scan.top(x, z) == c.getY() + 6, "Leaf column: " + scan.treeBlocks(x + 1, z + 1));
        helper.assertTrue(!scan.trees(x - 5, z), "The clearing has no trees");
        SiteScore score = scoreAt(helper, c, 0, 0);
        helper.assertTrue(score.ok() && score.penalizedFor(Factor.TREES) && score.metrics().treePercent() > 0, "Forest: " + score.describe());
        helper.succeed();
    }

    /** Lava pool: lava on the site is refused. */
    @GameTest(template = TERRAIN)
    public static void scanLavaPool(GameTestHelper helper) {
        BlockPos c = build(helper, TestTerrain.Shape.LAVA_POOL);
        TerrainScan scan = scan(helper, c);
        helper.assertTrue(scan.lava(c.getX(), c.getZ()) && !scan.lava(c.getX() + 2, c.getZ()), "Lava columns wrong");
        SiteScore score = scoreAt(helper, c, 0, 0);
        helper.assertTrue(score.refusedFor(Factor.LAVA) && score.metrics().nearestLava() == 0, "Lava: " + score.describe() + " " + score.metrics());
        helper.succeed();
    }

    /** Plateau: the flat top is a perfect site; its edge is a cliff. */
    @GameTest(template = TERRAIN)
    public static void scanPlateau(GameTestHelper helper) {
        BlockPos c = build(helper, TestTerrain.Shape.PLATEAU);
        SiteScore top = scoreAt(helper, c, 0, 0);
        helper.assertTrue(top.ok() && top.score() == SiteScore.MAX_SCORE && top.metrics().heightRange() == 0, "Plateau top: " + top.describe());
        SiteScore edge = scoreAt(helper, c, 6, 0);
        helper.assertTrue(edge.refusedFor(Factor.CLIFF), "Plateau edge: " + edge.describe());
        helper.succeed();
    }

    /** Searching from near the plateau's edge finds its center, the only perfect site in range. */
    @GameTest(template = TERRAIN)
    public static void searchFindsPlateauTop(GameTestHelper helper) {
        BlockPos c = build(helper, TestTerrain.Shape.PLATEAU);
        SiteRules rules = SiteRules.current().withSearch(8, 4, 81);
        BlockPos wanted = c.offset(4, 0, 4);
        TerrainScan scan = TerrainScanner.scan(helper.getLevel(), wanted, rules.searchScanRadius());
        SiteSearch.Result result = SiteSearch.search(scan, wanted.getX(), wanted.getZ(), rules);
        helper.assertTrue(result.best().map(best -> best.x() == c.getX() && best.z() == c.getZ()).orElse(false),
                "Expected the plateau's center " + c.getX() + ", " + c.getZ() + ", got " + result.best() + " (" + result.summary() + ")");
        helper.succeed();
    }

    /** Columns in chunks that aren't loaded are reported as unknown, and scanning doesn't load them. */
    @GameTest(template = TERRAIN)
    public static void scanReportsUnloadedAsUnknown(GameTestHelper helper) {
        BlockPos far = helper.absolutePos(CENTER).offset(200_000, 0, 200_000);
        TerrainScan scan = TerrainScanner.scan(helper.getLevel(), far, 8);
        helper.assertTrue(scan.knownColumns() == 0, "Unloaded columns should be unknown, " + scan.knownColumns() + " known");
        helper.assertTrue(!helper.getLevel().hasChunk(far.getX() >> 4, far.getZ() >> 4), "The scan loaded a chunk");
        SiteScore score = SiteScorer.score(scan, far.getX(), far.getZ(), SiteRules.current());
        helper.assertTrue(score.unknown() && score.refusals().isEmpty(), "An unloaded site is unknown, not refused: " + score.describe());
        helper.succeed();
    }

    /**
     * Cost: a scan of radius 64 (16,641 columns) takes at most {@link #COST_BUDGET_MS} ms on average (3 runs after a
     * warm-up), and a forest of radius 13 is scanned too. The numbers are written to the log ("CSR terrain scan cost").
     */
    @GameTest(template = TERRAIN, timeoutTicks = 400)
    public static void scanCostStaysWithinBudget(GameTestHelper helper) {
        BlockPos c = build(helper, TestTerrain.Shape.FOREST);
        TerrainScanner.scan(helper.getLevel(), c, COST_RADIUS); // warm-up
        long total = 0;
        int known = 0;
        for (int i = 0; i < 3; i++) {
            TerrainScan scan = TerrainScanner.scan(helper.getLevel(), c, COST_RADIUS);
            total += scan.costNanos();
            known = scan.knownColumns();
        }
        double averageMs = total / 3 / 1_000_000.0;
        TerrainScan forest = TerrainScanner.scan(helper.getLevel(), c, RADIUS);
        int columns = (2 * COST_RADIUS + 1) * (2 * COST_RADIUS + 1);
        ClaySoldiersCommon.LOGGER.info("CSR terrain scan cost: radius {} = {} columns ({} loaded): {} ms average; forest radius {} = {} columns: {} ms",
                COST_RADIUS, columns, known, "%.2f".formatted(averageMs), RADIUS, forest.knownColumns(), "%.3f".formatted(forest.costNanos() / 1_000_000.0));
        helper.assertTrue(known > 0, "Nothing was scanned");
        helper.assertTrue(averageMs <= COST_BUDGET_MS, "Scan of radius " + COST_RADIUS + " took " + averageMs + " ms, budget " + COST_BUDGET_MS);
        helper.succeed();
    }
}
