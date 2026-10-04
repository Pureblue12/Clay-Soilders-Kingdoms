package net.bumblebee.claysoldiers.terrain;

import net.bumblebee.claysoldiers.tuning.Tuning;

/**
 * Every number the terrain scorer, site search and grading use, read from the tuning file ({@code terrain.*}, see
 * {@code docs/TUNING.md}). A plain record, so tests can judge sites with other values without touching the global
 * tuning.
 */
public record SiteRules(int keepSize, int margin,
                        int refuseHeightRange, int refuseSlopePercent, int refuseCliffStep, int refuseWaterDepth, int refuseLavaDistance,
                        int penaltyHeightRange, int penaltySlope, int penaltyWater, int penaltyTrees, int penaltyStep, int penaltyBiome,
                        int searchRadius, int searchStep, int searchMaxCandidates,
                        int cutFillBudget, int foundationBudget, int foundationMaxHeight, int pillarSpacing, int clearTreesBudget) {

    public static SiteRules from(Tuning.Values values) {
        return new SiteRules(values.get(Tuning.TERRAIN_KEEP_SIZE), values.get(Tuning.TERRAIN_KEEP_MARGIN),
                values.get(Tuning.TERRAIN_REFUSE_HEIGHT_RANGE), values.get(Tuning.TERRAIN_REFUSE_SLOPE),
                values.get(Tuning.TERRAIN_REFUSE_CLIFF_STEP), values.get(Tuning.TERRAIN_REFUSE_WATER_DEPTH),
                values.get(Tuning.TERRAIN_REFUSE_LAVA_DISTANCE),
                values.get(Tuning.TERRAIN_PENALTY_HEIGHT_RANGE), values.get(Tuning.TERRAIN_PENALTY_SLOPE),
                values.get(Tuning.TERRAIN_PENALTY_WATER), values.get(Tuning.TERRAIN_PENALTY_TREES),
                values.get(Tuning.TERRAIN_PENALTY_STEP), values.get(Tuning.TERRAIN_PENALTY_BIOME),
                values.get(Tuning.TERRAIN_SEARCH_RADIUS), values.get(Tuning.TERRAIN_SEARCH_STEP),
                values.get(Tuning.TERRAIN_SEARCH_MAX_CANDIDATES),
                values.get(Tuning.TERRAIN_GRADE_CUT_FILL_BUDGET), values.get(Tuning.TERRAIN_GRADE_FOUNDATION_BUDGET),
                values.get(Tuning.TERRAIN_GRADE_FOUNDATION_MAX_HEIGHT), values.get(Tuning.TERRAIN_GRADE_PILLAR_SPACING),
                values.get(Tuning.TERRAIN_GRADE_CLEAR_TREES_BUDGET));
    }

    /** The rules from the current tuning values. */
    public static SiteRules current() {
        return from(Tuning.current());
    }

    /**
     * Columns from the site's center to its edge: half the keep plus the margin. A site is a square of
     * {@code 2 * half() + 1} columns.
     */
    public int half() {
        return keepSize / 2 + margin;
    }

    /** How far around a site's center a scan must reach to judge it (the site plus the lava ring). */
    public int siteScanRadius() {
        return half() + refuseLavaDistance;
    }

    /** How far around a wanted point a scan must reach for a site search. */
    public int searchScanRadius() {
        return searchRadius + siteScanRadius();
    }

    public SiteRules withSearch(int radius, int step, int maxCandidates) {
        return new SiteRules(keepSize, margin, refuseHeightRange, refuseSlopePercent, refuseCliffStep, refuseWaterDepth, refuseLavaDistance,
                penaltyHeightRange, penaltySlope, penaltyWater, penaltyTrees, penaltyStep, penaltyBiome, radius, step, maxCandidates,
                cutFillBudget, foundationBudget, foundationMaxHeight, pillarSpacing, clearTreesBudget);
    }
}
