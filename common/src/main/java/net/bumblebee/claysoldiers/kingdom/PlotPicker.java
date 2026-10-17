package net.bumblebee.claysoldiers.kingdom;

import net.bumblebee.claysoldiers.terrain.TerrainScan;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Where a kingdom puts a building after its keep (stage 5, the small planner: pure, no world access). Like {@link KeepSite}
 * it judges candidates on a grid around the kingdom's center in the four build directions, and the kingdom levels the
 * one it picks. On top of that (the owner's notes on the planner page, D97):
 * <ul>
 *     <li>a plot never overlaps a building already there, with {@link Rules#gap} blocks between them (a path's width);</li>
 *     <li>it lies between {@link Rules#minRadius} and {@link Rules#maxRadius} of the center: outer buildings (the lumber
 *     camp, fields) in a ring around the keep, just outside where the wall will be, inner ones (houses) near it;</li>
 *     <li>it looks planned: a building faces the keep (its easel and front toward the center), others cost points;</li>
 *     <li>it keeps away from water (points per water column near it) and from the claims of other kingdoms;</li>
 *     <li>a lumber camp likes trees around it (but not on its plot: those are cleared).</li>
 * </ul>
 * The best plot has the fewest points; on a tie the one nearer the center wins.
 */
public final class PlotPicker {
    /** Points per column with trees on the plot (they are cleared). */
    static final int TREE_COLUMN_COST = 3;
    /** Points per block of distance from the inner edge of the ring: plots stay close together. */
    static final int DISTANCE_COST = 4;
    /** Points for a building that doesn't face the keep. */
    static final int FACING_COST = 60;
    /** Points per water column within {@link #NEAR} of the plot. */
    static final int WATER_COST = 4;
    /** Points off per column with trees within {@link #NEAR} of the plot, for a building that wants trees. */
    static final int TREE_NEAR_BONUS = 2;
    /** How far around a plot water and trees count as near. */
    static final int NEAR = 8;

    private PlotPicker() {
    }

    /** A rectangle of columns (inclusive) that is taken: a building's levelled ground. */
    public record Taken(int minX, int maxX, int minZ, int maxZ) {
        boolean overlaps(int minX, int maxX, int minZ, int maxZ, int gap) {
            return minX <= this.maxX + gap && maxX >= this.minX - gap && minZ <= this.maxZ + gap && maxZ >= this.minZ - gap;
        }
    }

    /** Columns no plot may cover (another kingdom's claim). */
    public interface Forbidden {
        boolean test(int x, int z);
    }

    /**
     * @param minRadius  least distance of the plot's middle from the center
     * @param maxRadius  most distance of the plot's middle from the center
     * @param step       distance between candidates
     * @param maxRange   the most the ground may rise and fall across a plot
     * @param margin     columns around the area that are levelled with it
     * @param gap        free columns between a plot and every taken rectangle
     * @param likesTrees whether trees near the plot count for it (a lumber camp)
     */
    public record Rules(int minRadius, int maxRadius, int step, int maxRange, int margin, int gap, boolean likesTrees) {
        KeepSite.Rules site() {
            return new KeepSite.Rules(maxRadius, step, maxRange, margin);
        }
    }

    /**
     * A usable plot.
     *
     * @param site  where the easel goes and the ground it is levelled to
     * @param score its points (fewer is better)
     */
    public record Plot(KeepSite.Choice site, int score, int waterNear, int treesNear, boolean facesCenter) {
        public String describe() {
            return "%s; %d points, %d water and %d tree columns near, %s".formatted(site.describe(), score, waterNear, treesNear,
                    facesCenter ? "faces the keep" : "turned away from the keep");
        }
    }

    /**
     * The result: the best plot and why the other candidates weren't usable.
     *
     * @param taken  candidates overlapping a building, another claim, or outside the ring
     */
    public record Result(Optional<Plot> best, int judged, int taken, int unknown, int uneven, int wet) {
        public String summary() {
            return best.map(plot -> "plot: " + plot.describe())
                    .orElse("no plot: of %d candidates %d overlap a building or another claim, %d not loaded, %d too uneven, %d with water or lava"
                            .formatted(judged, taken, unknown, uneven, wet));
        }
    }

    /** How far around the center a scan must reach for {@link #search}. */
    public static int scanRadius(List<KeepSite.Area> areas, Rules rules) {
        return KeepSite.scanRadius(areas, rules.site()) + NEAR;
    }

    /** Finds the best plot around ({@code centerX}, {@code centerZ}) for any of {@code areas} (one per build direction). */
    public static Result search(TerrainScan scan, int centerX, int centerZ, List<KeepSite.Area> areas, List<Taken> taken,
                                Forbidden forbidden, Rules rules) {
        Plot best = null;
        int judged = 0;
        int takenCount = 0;
        int unknown = 0;
        int uneven = 0;
        int wet = 0;
        int step = Math.max(1, rules.step());
        int rings = rules.maxRadius() / step;
        List<int[]> offsets = new ArrayList<>();
        for (int i = -rings; i <= rings; i++) {
            for (int j = -rings; j <= rings; j++) {
                int dx = i * step;
                int dz = j * step;
                int d2 = dx * dx + dz * dz;
                if (d2 <= rules.maxRadius() * rules.maxRadius() && d2 >= rules.minRadius() * rules.minRadius()) {
                    offsets.add(new int[]{dx, dz});
                }
            }
        }
        offsets.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        KeepSite.Rules siteRules = rules.site();
        for (int[] offset : offsets) {
            int distance = (int) Math.round(Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1]));
            Direction outward = outward(offset[0], offset[1]);
            for (KeepSite.Area area : areas) {
                judged++;
                int easelX = centerX + offset[0] - area.midX();
                int easelZ = centerZ + offset[1] - area.midZ();
                int minX = easelX + area.minX() - rules.margin();
                int maxX = easelX + area.maxX() + rules.margin();
                int minZ = easelZ + area.minZ() - rules.margin();
                int maxZ = easelZ + area.maxZ() + rules.margin();
                if (blocked(minX, maxX, minZ, maxZ, taken, forbidden, rules.gap())) {
                    takenCount++;
                    continue;
                }
                KeepSite.Judgement judgement = KeepSite.judge(scan, area, easelX, easelZ, siteRules);
                switch (judgement.verdict()) {
                    case UNKNOWN -> unknown++;
                    case UNEVEN -> uneven++;
                    case WET -> wet++;
                    case USABLE -> {
                        int waterNear = 0;
                        int treesNear = 0;
                        for (int x = minX - NEAR; x <= maxX + NEAR; x++) {
                            for (int z = minZ - NEAR; z <= maxZ + NEAR; z++) {
                                boolean inside = x >= minX && x <= maxX && z >= minZ && z <= maxZ;
                                if (inside || !scan.contains(x, z) || !scan.known(x, z)) {
                                    continue;
                                }
                                if (scan.waterDepth(x, z) > 0) {
                                    waterNear++;
                                }
                                if (scan.trees(x, z)) {
                                    treesNear++;
                                }
                            }
                        }
                        boolean faces = area.facing() == outward;
                        int score = judgement.earth() + TREE_COLUMN_COST * judgement.trees() + DISTANCE_COST * (distance - rules.minRadius())
                                + WATER_COST * waterNear + (faces ? 0 : FACING_COST) - (rules.likesTrees() ? TREE_NEAR_BONUS * treesNear : 0);
                        KeepSite.Choice choice = new KeepSite.Choice(area, easelX, easelZ, judgement.groundY(), judgement.range(),
                                judgement.earth(), judgement.trees(), distance, judgement.hollow());
                        Plot plot = new Plot(choice, score, waterNear, treesNear, faces);
                        if (best == null || plot.score() < best.score()) {
                            best = plot;
                        }
                    }
                }
            }
        }
        return new Result(Optional.ofNullable(best), judged, takenCount, unknown, uneven, wet);
    }

    /**
     * The build direction that faces the keep from a plot at offset ({@code dx}, {@code dz}) of the center: the building
     * goes away from the center, so its easel and front are toward it.
     */
    static Direction outward(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static boolean blocked(int minX, int maxX, int minZ, int maxZ, List<Taken> taken, Forbidden forbidden, int gap) {
        for (Taken rect : taken) {
            if (rect.overlaps(minX, maxX, minZ, maxZ, gap)) {
                return true;
            }
        }
        return forbidden.test(minX, minZ) || forbidden.test(minX, maxZ) || forbidden.test(maxX, minZ) || forbidden.test(maxX, maxZ)
                || forbidden.test((minX + maxX) / 2, (minZ + maxZ) / 2);
    }
}
