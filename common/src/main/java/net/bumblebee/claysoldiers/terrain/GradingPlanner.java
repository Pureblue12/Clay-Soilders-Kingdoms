package net.bumblebee.claysoldiers.terrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Plans a {@link GradingMethod} for the keep site centered on a column, from a {@link TerrainScan} (pure data, no
 * world access). The site is the keep plus its margin ({@link SiteRules#half()}). Budgets and limits come from the
 * tuning file. The methods that search ({@link GradingMethod#FLATTEST}, {@link GradingMethod#AVOID_TREES}) need a
 * scan covering {@link SiteRules#searchScanRadius()}; the others {@link SiteRules#siteScanRadius()}.
 */
public final class GradingPlanner {
    private GradingPlanner() {
    }

    public static GradingPlan plan(GradingMethod method, TerrainScan scan, int x, int z, SiteRules rules) {
        if (method.searches()) {
            return planSearch(method, scan, x, z, rules);
        }
        int half = rules.half();
        for (int i = x - half; i <= x + half; i++) {
            for (int j = z - half; j <= z + half; j++) {
                if (!scan.contains(i, j) || !scan.known(i, j)) {
                    return GradingPlan.refused(method, x, z, "part of the site isn't loaded");
                }
            }
        }
        return switch (method) {
            case CUT_FILL -> planCutFill(scan, x, z, rules);
            case FOUNDATION -> planFoundation(scan, x, z, rules);
            case CLEAR_TREES -> planClearTrees(scan, x, z, rules);
            case FLATTEST, AVOID_TREES -> throw new IllegalStateException("searching method " + method);
        };
    }

    /**
     * (b) Every column of the site to the median ground height: higher ground is cut (with anything on it), lower
     * ground filled with dirt (replacing water and plants). The median moves the fewest blocks.
     */
    private static GradingPlan planCutFill(TerrainScan scan, int x, int z, SiteRules rules) {
        int half = rules.half();
        int[] heights = new int[(2 * half + 1) * (2 * half + 1)];
        int n = 0;
        for (int i = x - half; i <= x + half; i++) {
            for (int j = z - half; j <= z + half; j++) {
                heights[n++] = scan.ground(i, j);
            }
        }
        Arrays.sort(heights);
        int level = heights[heights.length / 2];
        List<GradingPlan.Work> work = new ArrayList<>();
        int placed = 0;
        int cut = 0;
        int vegetation = 0;
        for (int i = x - half; i <= x + half; i++) {
            for (int j = z - half; j <= z + half; j++) {
                int ground = scan.ground(i, j);
                placed += Math.max(0, level - ground);
                cut += Math.max(0, ground - level);
                vegetation += scan.vegetation(i, j);
                work.add(new GradingPlan.Work(i, j, level + 1, scan.top(i, j), false, ground + 1, level, GradingPlan.Fill.DIRT, GradingPlan.NONE));
            }
        }
        int budget = rules.cutFillBudget();
        if (placed + cut > budget) {
            return new GradingPlan(GradingMethod.CUT_FILL, x, z, level, false, "needs " + (placed + cut) + " blocks of earth moved (" + cut
                    + " cut, " + placed + " filled), over the budget of " + budget, placed, cut, vegetation, budget, List.of());
        }
        return new GradingPlan(GradingMethod.CUT_FILL, x, z, level, true, "cut and fill to the median ground height",
                placed, cut, vegetation, budget, work);
    }

    /**
     * (c) A stone floor at the highest ground of the site, on pillars every {@code terrain.grade.pillar_spacing} blocks
     * (and at every edge and corner) down to the ground. Nothing is cut; trees and plants above the floor are removed.
     */
    private static GradingPlan planFoundation(TerrainScan scan, int x, int z, SiteRules rules) {
        int half = rules.half();
        int width = 2 * half + 1;
        int level = Integer.MIN_VALUE;
        int lowest = Integer.MAX_VALUE;
        for (int i = x - half; i <= x + half; i++) {
            for (int j = z - half; j <= z + half; j++) {
                level = Math.max(level, scan.ground(i, j));
                lowest = Math.min(lowest, scan.ground(i, j));
            }
        }
        int spacing = Math.max(1, rules.pillarSpacing());
        List<GradingPlan.Work> work = new ArrayList<>();
        int placed = 0;
        int removed = 0;
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < width; j++) {
                int cx = x - half + i;
                int cz = z - half + j;
                int ground = scan.ground(cx, cz);
                boolean pillar = (i % spacing == 0 || i == width - 1) && (j % spacing == 0 || j == width - 1);
                int fillFrom = ground >= level ? level + 1 : pillar ? ground + 1 : level;
                placed += Math.max(0, level - fillFrom + 1);
                removed += Math.min(scan.vegetation(cx, cz), Math.max(0, scan.top(cx, cz) - level));
                work.add(new GradingPlan.Work(cx, cz, level + 1, scan.top(cx, cz), false, fillFrom, level, GradingPlan.Fill.STONE, GradingPlan.NONE));
            }
        }
        int budget = rules.foundationBudget();
        if (level - lowest > rules.foundationMaxHeight()) {
            return new GradingPlan(GradingMethod.FOUNDATION, x, z, level, false, "the platform would stand " + (level - lowest)
                    + " blocks above the lowest ground, too high (limit " + rules.foundationMaxHeight() + ")", placed, 0, removed, budget, List.of());
        }
        if (placed > budget) {
            return new GradingPlan(GradingMethod.FOUNDATION, x, z, level, false, "needs " + placed + " blocks for floor and pillars, over the budget of "
                    + budget, placed, 0, removed, budget, List.of());
        }
        return new GradingPlan(GradingMethod.FOUNDATION, x, z, level, true, "a stone floor at the highest ground, on pillars every " + spacing
                + " blocks", placed, 0, removed, budget, work);
    }

    /** (d) Remove every log and leaf above the ground of the site. The ground isn't changed. */
    private static GradingPlan planClearTrees(TerrainScan scan, int x, int z, SiteRules rules) {
        int half = rules.half();
        List<GradingPlan.Work> work = new ArrayList<>();
        int removed = 0;
        int level = scan.ground(x, z);
        for (int i = x - half; i <= x + half; i++) {
            for (int j = z - half; j <= z + half; j++) {
                if (scan.trees(i, j)) {
                    removed += scan.treeBlocks(i, j);
                    work.add(new GradingPlan.Work(i, j, scan.ground(i, j) + 1, scan.top(i, j), true, 1, 0, GradingPlan.Fill.DIRT, GradingPlan.NONE));
                }
            }
        }
        int budget = rules.clearTreesBudget();
        if (removed > budget) {
            return new GradingPlan(GradingMethod.CLEAR_TREES, x, z, level, false, "needs " + removed + " tree blocks removed, over the budget of "
                    + budget, 0, 0, removed, budget, List.of());
        }
        return new GradingPlan(GradingMethod.CLEAR_TREES, x, z, level, true, work.isEmpty() ? "no trees on the site" : "clear the trees on "
                + work.size() + " columns", 0, 0, removed, budget, work);
    }

    /**
     * (a) and (d, working around): no terrain changes; pick a site from the search. FLATTEST takes the usable
     * candidate with the smallest height range (then the best score, then the nearest); AVOID_TREES the best usable
     * candidate without trees (then the nearest). Marks the keep's 4 corners.
     */
    private static GradingPlan planSearch(GradingMethod method, TerrainScan scan, int x, int z, SiteRules rules) {
        SiteSearch.Result result = SiteSearch.search(scan, x, z, rules);
        Comparator<SiteSearch.Candidate> order = method == GradingMethod.FLATTEST
                ? Comparator.<SiteSearch.Candidate>comparingInt(c -> c.score().metrics().heightRange())
                .thenComparing(c -> -c.score().score()).thenComparingInt(SiteSearch.Candidate::distance)
                : Comparator.<SiteSearch.Candidate>comparingInt(c -> -c.score().score()).thenComparingInt(SiteSearch.Candidate::distance);
        Optional<SiteSearch.Candidate> chosen = result.candidates().stream()
                .filter(c -> c.score().ok())
                .filter(c -> method != GradingMethod.AVOID_TREES || c.score().metrics().treePercent() == 0)
                .min(order);
        if (chosen.isEmpty()) {
            return GradingPlan.refused(method, x, z, (method == GradingMethod.AVOID_TREES ? "no usable site without trees: " : "no usable site: ")
                    + result.summary());
        }
        SiteSearch.Candidate site = chosen.get();
        int keepHalf = rules.keepSize() / 2;
        List<GradingPlan.Work> markers = new ArrayList<>();
        for (int dx : new int[]{-keepHalf, keepHalf}) {
            for (int dz : new int[]{-keepHalf, keepHalf}) {
                int mx = site.x() + dx;
                int mz = site.z() + dz;
                markers.add(new GradingPlan.Work(mx, mz, 1, 0, false, 1, 0, GradingPlan.Fill.DIRT, scan.surface(mx, mz) + 1));
            }
        }
        return new GradingPlan(method, site.x(), site.z(), scan.surface(site.x(), site.z()), true,
                (method == GradingMethod.FLATTEST ? "the flattest site" : "the best site without trees") + " (score " + site.score().score()
                        + ", height range " + site.score().metrics().heightRange() + ", " + site.distance() + " blocks away), corners marked",
                0, 0, 0, 0, markers);
    }

    /** How builders would do this method with today's reach (2 sideways, 8 up, 2 down) and door use. */
    public static String howSoldiers(GradingMethod method) {
        return switch (method) {
            case FLATTEST -> "Nothing to build: the planner just picks the spot. Free, but the kingdom may sit away from where the king stood.";
            case CUT_FILL -> "Diggers cut the high side top-down (like dig-hole work), builders fill the low side bottom-up as a "
                    + "blueprint of dirt layers. Every block is within 2 of the ground they stand on, so reach is no problem; "
                    + "earth needs carrying from the cut to the fill (or from storage).";
            case FOUNDATION -> "Builders place pillars first (a blueprint, bottom-up), then the floor from the high side outward, "
                    + "standing on the floor they have built. The floor is at most terrain.grade.foundation_max_height above the "
                    + "ground, inside the 8-block reach; no digging, and doors don't matter (it's all outdoors).";
            case CLEAR_TREES -> "Woodcutters (stage 5) fell the trees; leaves above 8 blocks are out of builders' reach, so they "
                    + "would need to stand on the trunk or let leaves decay once the logs are gone.";
            case AVOID_TREES -> "Nothing to build: the planner picks a spot without trees. Keeps the forest, but may move the "
                    + "keep away from the king.";
        };
    }
}
