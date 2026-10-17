package net.bumblebee.claysoldiers.kingdom;

import net.bumblebee.claysoldiers.terrain.TerrainScan;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Where a kingdom puts its keep (stage 4, pure: no world access). The site is the ground the keep, its easel and the
 * builders' chest stand on, plus a margin of 1. Candidates are the keep's middle on a grid around where the kingdom was
 * founded ({@code kingdom.keep.search_radius}, {@code terrain.search.step}), in each of the four build directions.
 * <p>
 * A candidate is usable when every column is known (loaded), dry, without lava, and its ground rises and falls at most
 * {@code kingdom.keep.max_height_range}. The kingdom levels a usable site at the median ground height before it starts
 * ({@code KingdomKeep}). The best usable site moves the least earth, with trees counting extra (they are cleared too)
 * and every block of distance from the founding point counting more: on plains the keep stands where the king stood.
 */
public final class KeepSite {
    /** Score points per column with trees on it (the trees are cleared). */
    static final int TREE_COLUMN_COST = 3;
    /** Score points per block of distance from the founding point. */
    static final int DISTANCE_COST = 10;

    private KeepSite() {
    }

    /**
     * The site for one build direction, relative to the easel (x and z offsets, inclusive): everything that must stand on
     * level ground (the structure, the easel, the chest, where the builders gather), without the margin.
     *
     * @param facing  the build direction
     * @param midX    the keep's middle, relative to the easel
     * @param midZ    the keep's middle, relative to the easel
     */
    public record Area(Direction facing, int minX, int maxX, int minZ, int maxZ, int midX, int midZ) {
    }

    /**
     * @param searchRadius how far the keep's middle may be from the founding point
     * @param step         distance between candidates
     * @param maxRange     the most the ground may rise and fall across a site
     * @param margin       columns around the area that are levelled with it
     */
    public record Rules(int searchRadius, int step, int maxRange, int margin) {
    }

    /**
     * A usable site.
     *
     * @param easelX   the easel's column
     * @param easelZ   the easel's column
     * @param groundY  the height the site is levelled to (the easel stands one above)
     * @param range    how much the ground rises and falls across it
     * @param earth    blocks to cut plus blocks to fill
     * @param trees    columns with trees
     * @param distance blocks from the founding point to the keep's middle
     */
    public record Choice(Area area, int easelX, int easelZ, int groundY, int range, int earth, int trees, int distance, int hollow) {
        public int score() {
            // Caves (hollow) are reported, not weighed: only the stage 6 planner avoids them (D106).
            return earth + TREE_COLUMN_COST * trees + DISTANCE_COST * distance;
        }

        /** The columns to level: the area plus the margin, in world x and z. */
        public int minX(int margin) {
            return easelX + area.minX() - margin;
        }

        public int maxX(int margin) {
            return easelX + area.maxX() + margin;
        }

        public int minZ(int margin) {
            return easelZ + area.minZ() - margin;
        }

        public int maxZ(int margin) {
            return easelZ + area.maxZ() + margin;
        }

        public String describe() {
            return "easel at %d, %d going %s, ground %d (rises and falls %d, %d blocks of earth to move, %d columns with trees, %d over a cave, %d from the founding point)"
                    .formatted(easelX, easelZ, area.facing().getName(), groundY, range, earth, trees, hollow, distance);
        }
    }

    /**
     * The search's result: the best site, and why the others weren't usable.
     *
     * @param judged  candidates judged
     * @param unknown candidates with ground that isn't loaded
     * @param uneven  candidates whose ground rises and falls too much
     * @param wet     candidates with water or lava
     */
    public record Result(Optional<Choice> best, int judged, int unknown, int uneven, int wet) {
        public String summary() {
            return best.map(choice -> "site: " + choice.describe())
                    .orElse("no site: of %d candidates %d not loaded, %d too uneven, %d with water or lava".formatted(judged, unknown, uneven, wet));
        }
    }

    /** How far around the founding point a scan must reach for {@link #search}. */
    public static int scanRadius(List<Area> areas, Rules rules) {
        int reach = 0;
        for (Area area : areas) {
            reach = Math.max(reach, Math.max(Math.max(Math.abs(area.minX() - area.midX()), Math.abs(area.maxX() - area.midX())),
                    Math.max(Math.abs(area.minZ() - area.midZ()), Math.abs(area.maxZ() - area.midZ()))));
        }
        return rules.searchRadius() + reach + rules.margin() + 1;
    }

    /**
     * Finds the best keep site around ({@code centerX}, {@code centerZ}) for any of {@code areas} (one per build
     * direction); on a tie the nearer candidate, then the earlier area, wins.
     */
    public static Result search(TerrainScan scan, int centerX, int centerZ, List<Area> areas, Rules rules) {
        Choice best = null;
        int judged = 0;
        int unknown = 0;
        int uneven = 0;
        int wet = 0;
        int step = Math.max(1, rules.step());
        int rings = rules.searchRadius() / step;
        List<int[]> offsets = new ArrayList<>();
        for (int i = -rings; i <= rings; i++) {
            for (int j = -rings; j <= rings; j++) {
                int dx = i * step;
                int dz = j * step;
                if (dx * dx + dz * dz <= rules.searchRadius() * rules.searchRadius()) {
                    offsets.add(new int[]{dx, dz});
                }
            }
        }
        offsets.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
        for (int[] offset : offsets) {
            int distance = (int) Math.round(Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1]));
            for (Area area : areas) {
                judged++;
                int easelX = centerX + offset[0] - area.midX();
                int easelZ = centerZ + offset[1] - area.midZ();
                Judgement judgement = judge(scan, area, easelX, easelZ, rules);
                switch (judgement.verdict) {
                    case UNKNOWN -> unknown++;
                    case UNEVEN -> uneven++;
                    case WET -> wet++;
                    case USABLE -> {
                        Choice choice = new Choice(area, easelX, easelZ, judgement.groundY, judgement.range, judgement.earth, judgement.trees, distance, judgement.hollow);
                        if (best == null || choice.score() < best.score()) {
                            best = choice;
                        }
                    }
                }
            }
        }
        return new Result(Optional.ofNullable(best), judged, unknown, uneven, wet);
    }

    enum Verdict { USABLE, UNKNOWN, UNEVEN, WET }

    record Judgement(Verdict verdict, int groundY, int range, int earth, int trees, int hollow) {
        static Judgement of(Verdict verdict) {
            return new Judgement(verdict, 0, 0, 0, 0, 0);
        }
    }

    /** Judges one candidate: the area plus the margin around the easel at ({@code easelX}, {@code easelZ}). */
    static Judgement judge(TerrainScan scan, Area area, int easelX, int easelZ, Rules rules) {
        int minX = easelX + area.minX() - rules.margin();
        int maxX = easelX + area.maxX() + rules.margin();
        int minZ = easelZ + area.minZ() - rules.margin();
        int maxZ = easelZ + area.maxZ() + rules.margin();
        int[] heights = new int[(maxX - minX + 1) * (maxZ - minZ + 1)];
        int n = 0;
        int trees = 0;
        int hollow = 0;
        boolean wet = false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!scan.contains(x, z) || !scan.known(x, z)) {
                    return Judgement.of(Verdict.UNKNOWN);
                }
                if (scan.waterDepth(x, z) > 0 || scan.lava(x, z)) {
                    wet = true;
                }
                if (scan.trees(x, z)) {
                    trees++;
                }
                if (scan.hollow(x, z) > 0) {
                    hollow++;
                }
                heights[n++] = scan.ground(x, z);
            }
        }
        if (wet) {
            return Judgement.of(Verdict.WET);
        }
        Arrays.sort(heights);
        int range = heights[n - 1] - heights[0];
        if (range > rules.maxRange()) {
            return Judgement.of(Verdict.UNEVEN);
        }
        int groundY = heights[n / 2];
        int earth = 0;
        for (int height : heights) {
            earth += Math.abs(height - groundY);
        }
        return new Judgement(Verdict.USABLE, groundY, range, earth, trees, hollow);
    }
}
