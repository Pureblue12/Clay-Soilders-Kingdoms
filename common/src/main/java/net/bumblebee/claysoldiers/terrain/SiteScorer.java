package net.bumblebee.claysoldiers.terrain;

import java.util.ArrayList;
import java.util.List;

/**
 * Judges a keep site from a {@link TerrainScan} (pure data, no world access). The site is the square of
 * {@code 2 * rules.half() + 1} columns around its center: the keep plus its margin. Heights are the walking surface
 * (ground, or the top of water).
 * <p>
 * Hard refusals (any one refuses the site): a cliff (a step of {@code terrain.refuse.cliff_step} or more between
 * neighbouring columns), lava on the site or within {@code terrain.refuse.lava_distance}, water
 * {@code terrain.refuse.water_depth} deep, and too steep (slope or height range at their limits). Soft penalties lower
 * the score from 100: height range, slope, shallow water, trees, steps of 2 or more, and a wet or mountain biome.
 * Weights and limits come from {@link SiteRules} (the tuning file).
 */
public final class SiteScorer {
    private SiteScorer() {
    }

    /**
     * Judges the site centered on column ({@code x}, {@code z}). Unknown when any site column isn't in the scan or not
     * loaded. Lava is looked for in the ring around the site where the scan knows the columns.
     */
    public static SiteScore score(TerrainScan scan, int x, int z, SiteRules rules) {
        int half = rules.half();
        int width = 2 * half + 1;
        int columns = width * width;
        int unknown = 0;
        for (int i = x - half; i <= x + half; i++) {
            for (int j = z - half; j <= z + half; j++) {
                if (!scan.contains(i, j) || !scan.known(i, j)) {
                    unknown++;
                }
            }
        }
        if (unknown > 0) {
            return new SiteScore(0, List.of(), List.of(),
                    new SiteScore.Metrics(columns, unknown, 0, 0, 0, 0, 0, -1, 0, 0, 0, scan.biome()), true);
        }

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int water = 0;
        int maxDepth = 0;
        int trees = 0;
        long sumX = 0;
        long sumZ = 0;
        int maxStep = 0;
        int stepColumns = 0;
        for (int i = x - half; i <= x + half; i++) {
            for (int j = z - half; j <= z + half; j++) {
                int height = scan.surface(i, j);
                min = Math.min(min, height);
                max = Math.max(max, height);
                water += scan.waterDepth(i, j) > 0 ? 1 : 0;
                maxDepth = Math.max(maxDepth, scan.waterDepth(i, j));
                trees += scan.trees(i, j) ? 1 : 0;
                int stepX = i < x + half ? Math.abs(scan.surface(i + 1, j) - height) : 0;
                int stepZ = j < z + half ? Math.abs(scan.surface(i, j + 1) - height) : 0;
                sumX += stepX;
                sumZ += stepZ;
                maxStep = Math.max(maxStep, Math.max(stepX, stepZ));
                if (largestStepAround(scan, i, j, x, z, half) >= 2) {
                    stepColumns++;
                }
            }
        }
        int pairs = (width - 1) * width;
        int slope = pairs == 0 ? 0 : (int) (Math.max(sumX, sumZ) * 100 / pairs);
        int nearestLava = nearestLava(scan, x, z, half, rules.refuseLavaDistance());
        SiteScore.Metrics metrics = new SiteScore.Metrics(columns, 0, min, max, slope, water * 100 / columns, maxDepth, nearestLava,
                trees * 100 / columns, maxStep, stepColumns * 100 / columns, scan.biome());

        List<SiteScore.Reason> refusals = new ArrayList<>();
        if (maxStep >= rules.refuseCliffStep()) {
            refusals.add(new SiteScore.Reason(Factor.CLIFF, 0, "a cliff: a " + maxStep + "-block step (soldiers hop 1 block; "
                    + rules.refuseCliffStep() + " or more is refused)"));
        }
        if (nearestLava >= 0) {
            refusals.add(new SiteScore.Reason(Factor.LAVA, 0, nearestLava == 0 ? "lava on the site" : "lava " + nearestLava + " blocks from the site"));
        }
        if (maxDepth >= rules.refuseWaterDepth()) {
            refusals.add(new SiteScore.Reason(Factor.DEEP_WATER, 0, "deep water: " + maxDepth + " blocks deep (" + rules.refuseWaterDepth()
                    + " or more is refused)"));
        }
        if (slope >= rules.refuseSlopePercent() || max - min >= rules.refuseHeightRange()) {
            refusals.add(new SiteScore.Reason(Factor.TOO_STEEP, 0, "too steep: slope " + slope + " %, " + (max - min)
                    + " blocks between highest and lowest (limits " + rules.refuseSlopePercent() + " % and " + rules.refuseHeightRange() + ")"));
        }

        List<SiteScore.Reason> penalties = new ArrayList<>();
        penalty(penalties, Factor.HEIGHT_RANGE, (max - min) * rules.penaltyHeightRange(), "uneven: " + (max - min) + " blocks between highest and lowest");
        penalty(penalties, Factor.SLOPE, slope * rules.penaltySlope() / 10, "slope " + slope + " %");
        penalty(penalties, Factor.WATER, metrics.waterPercent() * rules.penaltyWater() / 10, "water on " + metrics.waterPercent() + " % of the site");
        penalty(penalties, Factor.TREES, metrics.treePercent() * rules.penaltyTrees() / 10, "trees on " + metrics.treePercent() + " % of the site");
        if (maxStep < rules.refuseCliffStep()) {
            penalty(penalties, Factor.STEPS, metrics.stepPercent() * rules.penaltyStep() / 10,
                    "steps of 2 or more blocks next to " + metrics.stepPercent() + " % of the site");
        }
        if (scan.biome() != BiomeKind.NORMAL) {
            penalty(penalties, Factor.BIOME, rules.penaltyBiome(), scan.biome() == BiomeKind.WET ? "a wet biome" : "a mountain biome");
        }
        int points = penalties.stream().mapToInt(SiteScore.Reason::points).sum();
        return new SiteScore(Math.max(0, SiteScore.MAX_SCORE - points), refusals, penalties, metrics, false);
    }

    private static void penalty(List<SiteScore.Reason> penalties, Factor factor, int points, String text) {
        if (points > 0) {
            penalties.add(new SiteScore.Reason(factor, points, text));
        }
    }

    /** The largest height step from column (i, j) to a neighbour inside the site. */
    private static int largestStepAround(TerrainScan scan, int i, int j, int x, int z, int half) {
        int height = scan.surface(i, j);
        int largest = 0;
        if (i > x - half) largest = Math.max(largest, Math.abs(scan.surface(i - 1, j) - height));
        if (i < x + half) largest = Math.max(largest, Math.abs(scan.surface(i + 1, j) - height));
        if (j > z - half) largest = Math.max(largest, Math.abs(scan.surface(i, j - 1) - height));
        if (j < z + half) largest = Math.max(largest, Math.abs(scan.surface(i, j + 1) - height));
        return largest;
    }

    /**
     * Distance from the site's edge to the nearest known lava column within {@code ring} blocks of it (0 = on the
     * site), or -1.
     */
    private static int nearestLava(TerrainScan scan, int x, int z, int half, int ring) {
        int nearest = -1;
        int reach = half + ring;
        for (int i = x - reach; i <= x + reach; i++) {
            for (int j = z - reach; j <= z + reach; j++) {
                if (scan.contains(i, j) && scan.known(i, j) && scan.lava(i, j)) {
                    int distance = Math.max(0, Math.max(Math.abs(i - x), Math.abs(j - z)) - half);
                    if (nearest < 0 || distance < nearest) {
                        nearest = distance;
                    }
                }
            }
        }
        return nearest;
    }
}
