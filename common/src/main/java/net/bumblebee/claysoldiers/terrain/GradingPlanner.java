package net.bumblebee.claysoldiers.terrain;

/**
 * Plans a {@link GradingMethod} for a site from a {@link TerrainScan} (pure data). Stage 2 skeleton: not implemented.
 */
public final class GradingPlanner {
    private GradingPlanner() {
    }

    public static GradingPlan plan(GradingMethod method, TerrainScan scan, int x, int z, SiteRules rules) {
        return GradingPlan.refused(method, x, z, "not implemented");
    }

    /** How builders would do this method with today's reach and door use. */
    public static String howSoldiers(GradingMethod method) {
        return "not implemented";
    }
}
