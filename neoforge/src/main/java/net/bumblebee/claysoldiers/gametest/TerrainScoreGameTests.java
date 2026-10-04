package net.bumblebee.claysoldiers.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.terrain.BiomeKind;
import net.bumblebee.claysoldiers.terrain.Factor;
import net.bumblebee.claysoldiers.terrain.SiteRules;
import net.bumblebee.claysoldiers.terrain.SiteScore;
import net.bumblebee.claysoldiers.terrain.SiteScorer;
import net.bumblebee.claysoldiers.terrain.SiteSearch;
import net.bumblebee.claysoldiers.terrain.TerrainScan;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * The site scorer and the site search on hand-made height data (no world). Each asserts the CORRECT behavior. The
 * rules come from parsed tuning values, never the global tuning, so these tests share the default batch.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class TerrainScoreGameTests {
    static final int H = 64;

    static SiteRules defaults() {
        return SiteRules.from(Tuning.parse(new JsonObject()));
    }

    /** Flat, dry, treeless ground in a normal biome: usable, full score, no penalties. */
    @GameTest(template = PLATFORM)
    public static void scorerPassesFlatGround(GameTestHelper helper) {
        SiteScore score = SiteScorer.score(TerrainScan.flat(0, 0, 20, H), 0, 0, defaults());
        helper.assertTrue(score.ok() && score.score() == SiteScore.MAX_SCORE && score.penalties().isEmpty(),
                "Flat ground should pass with " + SiteScore.MAX_SCORE + ": " + score.describe());
        helper.assertTrue(score.metrics().heightRange() == 0 && score.metrics().slopePercent() == 0, "Flat metrics: " + score.metrics());
        helper.succeed();
    }

    /** A step of terrain.refuse.cliff_step (4) blocks on the site is a cliff: refused with that reason. */
    @GameTest(template = PLATFORM)
    public static void scorerRefusesCliff(GameTestHelper helper) {
        SiteRules rules = defaults();
        TerrainScan scan = TerrainScan.flat(0, 0, 20, H);
        for (int x = 2; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                scan.setGround(x, z, H + rules.refuseCliffStep());
            }
        }
        SiteScore score = SiteScorer.score(scan, 0, 0, rules);
        helper.assertTrue(score.refusedFor(Factor.CLIFF) && !score.ok(), "A " + rules.refuseCliffStep() + "-block step should be refused as a cliff: " + score.describe());
        helper.assertTrue(score.metrics().maxStep() == rules.refuseCliffStep(), "Max step: " + score.metrics());
        helper.assertTrue(score.describe().contains("cliff"), "The reason should say cliff: " + score.describe());

        TerrainScan lower = TerrainScan.flat(0, 0, 20, H);
        for (int x = 2; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                lower.setGround(x, z, H + rules.refuseCliffStep() - 1);
            }
        }
        SiteScore step = SiteScorer.score(lower, 0, 0, rules);
        helper.assertTrue(step.ok() && step.penalizedFor(Factor.STEPS), "A " + (rules.refuseCliffStep() - 1) + "-block step is a soft penalty, not a cliff: " + step.describe());
        helper.succeed();
    }

    /** Lava on the site or within terrain.refuse.lava_distance of it is refused; lava farther away is not. */
    @GameTest(template = PLATFORM)
    public static void scorerRefusesLava(GameTestHelper helper) {
        SiteRules rules = defaults();
        int edge = rules.half();
        TerrainScan near = TerrainScan.flat(0, 0, 25, H);
        near.setLava(edge + rules.refuseLavaDistance(), 0, true);
        SiteScore score = SiteScorer.score(near, 0, 0, rules);
        helper.assertTrue(score.refusedFor(Factor.LAVA), "Lava " + rules.refuseLavaDistance() + " blocks from the site should be refused: " + score.describe());
        helper.assertTrue(score.metrics().nearestLava() == rules.refuseLavaDistance(), "Nearest lava: " + score.metrics());
        helper.assertTrue(score.describe().contains("lava"), "The reason should say lava: " + score.describe());

        TerrainScan far = TerrainScan.flat(0, 0, 25, H);
        far.setLava(edge + rules.refuseLavaDistance() + 1, 0, true);
        helper.assertTrue(SiteScorer.score(far, 0, 0, rules).ok(), "Lava farther than the lava distance must not refuse the site");
        helper.succeed();
    }

    /** Water terrain.refuse.water_depth (3) deep anywhere on the site is refused; shallower water is a penalty. */
    @GameTest(template = PLATFORM)
    public static void scorerRefusesDeepWater(GameTestHelper helper) {
        SiteRules rules = defaults();
        TerrainScan deep = TerrainScan.flat(0, 0, 20, H);
        deep.setGround(3, 3, H - rules.refuseWaterDepth());
        deep.setWater(3, 3, rules.refuseWaterDepth());
        SiteScore score = SiteScorer.score(deep, 0, 0, rules);
        helper.assertTrue(score.refusedFor(Factor.DEEP_WATER), "Water " + rules.refuseWaterDepth() + " deep should be refused: " + score.describe());
        helper.assertTrue(score.metrics().maxWaterDepth() == rules.refuseWaterDepth(), "Max water depth: " + score.metrics());
        helper.assertTrue(score.describe().contains("deep water"), "The reason should say deep water: " + score.describe());
        helper.succeed();
    }

    /**
     * Too steep: a slope of 1 up per block (100 %, over terrain.refuse.slope_percent) or a height range of
     * terrain.refuse.height_range is refused, even though no single step is a cliff.
     */
    @GameTest(template = PLATFORM)
    public static void scorerRefusesTooSteep(GameTestHelper helper) {
        SiteRules rules = defaults();
        TerrainScan ramp = TerrainScan.flat(0, 0, 20, H);
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                ramp.setGround(x, z, H + x);
            }
        }
        SiteScore score = SiteScorer.score(ramp, 0, 0, rules);
        helper.assertTrue(score.refusedFor(Factor.TOO_STEEP) && !score.refusedFor(Factor.CLIFF),
                "A 100 % ramp should be refused as too steep (not as a cliff): " + score.describe());
        helper.assertTrue(score.metrics().slopePercent() == 100 && score.metrics().maxStep() == 1, "Ramp metrics: " + score.metrics());
        helper.assertTrue(score.describe().contains("too steep"), "The reason should say too steep: " + score.describe());
        helper.succeed();
    }

    /** Each soft factor alone keeps the site usable but lowers its score below the full score. */
    @GameTest(template = PLATFORM)
    public static void scorerSoftPenaltiesLowerScore(GameTestHelper helper) {
        SiteRules rules = defaults();
        TerrainScan slope = TerrainScan.flat(0, 0, 20, H);
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                slope.setGround(x, z, H + Math.floorDiv(x, 2));
            }
        }
        check(helper, "a gentle slope", SiteScorer.score(slope, 0, 0, rules), Factor.SLOPE);

        TerrainScan pond = TerrainScan.flat(0, 0, 20, H);
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 3; z++) {
                pond.setGround(x, z, H - 1);
                pond.setWater(x, z, 1);
            }
        }
        check(helper, "a shallow pond", SiteScorer.score(pond, 0, 0, rules), Factor.WATER);

        TerrainScan trees = TerrainScan.flat(0, 0, 20, H);
        for (int x = -3; x <= 3; x++) {
            trees.setTrees(x, 0, 5);
        }
        check(helper, "trees", SiteScorer.score(trees, 0, 0, rules), Factor.TREES);

        TerrainScan wet = TerrainScan.flat(0, 0, 20, H);
        wet.setBiome(BiomeKind.WET);
        check(helper, "a wet biome", SiteScorer.score(wet, 0, 0, rules), Factor.BIOME);

        TerrainScan step = TerrainScan.flat(0, 0, 20, H);
        for (int x = 2; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                step.setGround(x, z, H + 2);
            }
        }
        check(helper, "a 2-block step", SiteScorer.score(step, 0, 0, rules), Factor.STEPS);
        helper.succeed();
    }

    private static void check(GameTestHelper helper, String what, SiteScore score, Factor factor) {
        helper.assertTrue(score.ok(), what + " must not refuse the site: " + score.describe());
        helper.assertTrue(score.penalizedFor(factor), what + " should cost a " + factor + " penalty: " + score.describe());
        helper.assertTrue(score.score() < SiteScore.MAX_SCORE, what + " should lower the score: " + score.describe());
    }

    /** Weights and thresholds come from the tuning values: changing them changes the result. */
    @GameTest(template = PLATFORM)
    public static void scorerWeightsComeFromTuning(GameTestHelper helper) {
        TerrainScan trees = TerrainScan.flat(0, 0, 20, H);
        for (int x = -5; x <= 5; x++) {
            trees.setTrees(x, 0, 5);
            trees.setTrees(x, 1, 5);
        }
        int free = SiteScorer.score(trees, 0, 0, rules("{\"terrain.penalty.trees\": 0}")).score();
        int costly = SiteScorer.score(trees, 0, 0, rules("{\"terrain.penalty.trees\": 20}")).score();
        helper.assertTrue(free == SiteScore.MAX_SCORE && costly < free, "terrain.penalty.trees should change the score: 0 -> " + free + ", 20 -> " + costly);

        TerrainScan cliff = TerrainScan.flat(0, 0, 20, H);
        for (int x = 2; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                cliff.setGround(x, z, H + 5);
            }
        }
        helper.assertTrue(!SiteScorer.score(cliff, 0, 0, rules("{}")).ok(), "A 5-block step is a cliff by default");
        helper.assertTrue(SiteScorer.score(cliff, 0, 0, rules("{\"terrain.refuse.cliff_step\": 6}")).ok(),
                "With terrain.refuse.cliff_step 6, a 5-block step must not be refused");
        helper.succeed();
    }

    static SiteRules rules(String json) {
        return SiteRules.from(Tuning.parse(JsonParser.parseString(json).getAsJsonObject()));
    }

    /** Columns that aren't loaded make the site unknown: not usable, but not refused either. */
    @GameTest(template = PLATFORM)
    public static void scorerReportsUnknownColumns(GameTestHelper helper) {
        TerrainScan scan = TerrainScan.flat(0, 0, 20, H);
        scan.setUnknown(1, 1);
        SiteScore score = SiteScorer.score(scan, 0, 0, defaults());
        helper.assertTrue(score.unknown() && !score.ok() && score.refusals().isEmpty(), "Unknown columns: " + score.describe());
        helper.assertTrue(score.metrics().unknownColumns() == 1, "Unknown count: " + score.metrics());
        helper.succeed();
    }

    /**
     * Steep ground everywhere except a flat pocket 8 blocks east of the wanted point: the search finds the pocket.
     */
    @GameTest(template = PLATFORM)
    public static void searchPicksFlatSpotWithinRange(GameTestHelper helper) {
        SiteRules rules = defaults();
        TerrainScan scan = steep(rules.searchScanRadius());
        for (int x = 2; x <= 14; x++) {
            for (int z = -6; z <= 6; z++) {
                scan.setGround(x, z, H + 40);
            }
        }
        SiteSearch.Result result = SiteSearch.search(scan, 0, 0, rules);
        helper.assertTrue(result.best().isPresent(), "No site found: " + result.summary());
        SiteSearch.Candidate best = result.best().get();
        helper.assertTrue(best.x() == 8 && best.z() == 0 && best.score().ok(), "Expected the pocket at 8, 0, got " + best);
        helper.succeed();
    }

    /** Nothing usable in range: no site, and the reasons are counted and reported. */
    @GameTest(template = PLATFORM)
    public static void searchReportsReasonsWhenNoneFit(GameTestHelper helper) {
        SiteRules rules = defaults();
        SiteSearch.Result result = SiteSearch.search(steep(rules.searchScanRadius()), 0, 0, rules);
        helper.assertTrue(result.best().isEmpty(), "Nothing should fit: " + result.best());
        helper.assertTrue(!result.candidates().isEmpty() && result.refusals().getOrDefault(Factor.TOO_STEEP, 0) == result.candidates().size(),
                "Every candidate should be refused as too steep: " + result.refusals() + " of " + result.candidates().size());
        helper.assertTrue(result.summary().contains("too steep"), "The summary should name the reason: " + result.summary());
        helper.succeed();
    }

    /** The search judges at most terrain.search.max_candidates sites, nearest first. */
    @GameTest(template = PLATFORM)
    public static void searchRespectsCandidateCap(GameTestHelper helper) {
        SiteRules rules = defaults().withSearch(16, 1, 10);
        SiteSearch.Result result = SiteSearch.search(steep(rules.searchScanRadius()), 0, 0, rules);
        helper.assertTrue(result.candidates().size() == 10, "Expected 10 candidates, got " + result.candidates().size());
        helper.assertTrue(result.candidates().getFirst().distance() == 0 && result.candidates().getLast().distance() <= 2,
                "Candidates should be nearest first: " + result.candidates().stream().map(SiteSearch.Candidate::distance).toList());
        helper.succeed();
    }

    /** On equally good ground the wanted point itself wins (nearest on a tie). */
    @GameTest(template = PLATFORM)
    public static void searchPrefersNearestOnTie(GameTestHelper helper) {
        SiteRules rules = defaults();
        SiteSearch.Result result = SiteSearch.search(TerrainScan.flat(0, 0, rules.searchScanRadius(), H), 0, 0, rules);
        helper.assertTrue(result.best().map(best -> best.x() == 0 && best.z() == 0).orElse(false), "Expected the wanted point: " + result.best());
        helper.succeed();
    }

    /** A diamond-shaped mountain 1 up per block in every direction: too steep everywhere. */
    static TerrainScan steep(int radius) {
        TerrainScan scan = TerrainScan.flat(0, 0, radius, H);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                scan.setGround(x, z, H + 2 * radius - Math.abs(x) - Math.abs(z));
            }
        }
        return scan;
    }
}
