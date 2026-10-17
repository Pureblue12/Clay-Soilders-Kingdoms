package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.kingdom.layout.Cell;
import net.bumblebee.claysoldiers.kingdom.layout.Ground;
import net.bumblebee.claysoldiers.kingdom.layout.KingdomPlanner;
import net.bumblebee.claysoldiers.kingdom.layout.LayoutChecker;
import net.bumblebee.claysoldiers.kingdom.layout.MasterPlan;
import net.bumblebee.claysoldiers.kingdom.layout.Rect;
import net.bumblebee.claysoldiers.kingdom.layout.Side;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The kingdom planner's cases on made-up land (plain Java, no world): {@link KingdomPlannerGameTests} runs them. Each
 * returns what is wrong, or {@code null} when the plan is right. The sizes are the real ones: the keep's 31 by 33 (with
 * its easel rows), the lumber camp 16 by 11, the wheat field 13 by 9, the cottage 9 by 11, the fishing dock 7 by 9.
 */
final class KingdomPlannerCases {
    static final int SIZE = 240;
    /** Land runs from -OFFSET to SIZE - OFFSET - 1 on both axes. */
    static final int OFFSET = 120;
    /** Where the river starts (7 wide), east of town. */
    static final int RIVER = 50;
    static final KingdomPlanner.Rules RULES = new KingdomPlanner.Rules(1, 2, 3, 5, 7, 3, 3, 6, 24, 4, 4, 8);

    private KingdomPlannerCases() {
    }

    /** Made-up land: flat at 64 unless changed, all known. */
    static final class Land implements Ground {
        final int[][] height = new int[SIZE][SIZE];
        final boolean[][] wet = new boolean[SIZE][SIZE];
        final boolean[][] trees = new boolean[SIZE][SIZE];
        final int[][] hollow = new int[SIZE][SIZE];
        int knownRadius = Integer.MAX_VALUE;

        Land() {
            for (int[] row : height) {
                java.util.Arrays.fill(row, 64);
            }
        }

        private boolean in(int x, int z) {
            return x + OFFSET >= 0 && z + OFFSET >= 0 && x + OFFSET < SIZE && z + OFFSET < SIZE;
        }

        @Override
        public boolean known(int x, int z) {
            return in(x, z) && Math.abs(x) <= knownRadius && Math.abs(z) <= knownRadius;
        }

        @Override
        public int height(int x, int z) {
            return height[x + OFFSET][z + OFFSET];
        }

        @Override
        public boolean wet(int x, int z) {
            return wet[x + OFFSET][z + OFFSET];
        }

        @Override
        public boolean trees(int x, int z) {
            return trees[x + OFFSET][z + OFFSET];
        }

        @Override
        public int hollow(int x, int z) {
            return hollow[x + OFFSET][z + OFFSET];
        }

        void cave(Rect rect, int depth) {
            for (int x = rect.minX(); x <= rect.maxX(); x++) {
                for (int z = rect.minZ(); z <= rect.maxZ(); z++) {
                    if (in(x, z)) {
                        hollow[x + OFFSET][z + OFFSET] = depth;
                    }
                }
            }
        }
    }

    /**
     * A building's ground facing {@code facing}: {@code width} across, {@code depth} deep, its easel at 0, 0 with 3 rows
     * in front of it (like {@code KingdomKeep.area}).
     */
    static KingdomPlanner.Shape shape(Side facing, int width, int depth) {
        int back = -3;
        int front = depth + 1;
        int left = -(width / 2);
        int right = width - 1 - width / 2;
        if (facing.dx != 0) {
            int a = facing.dx * back;
            int b = facing.dx * front;
            return new KingdomPlanner.Shape(facing, Math.min(a, b), Math.max(a, b), left, right);
        }
        int a = facing.dz * back;
        int b = facing.dz * front;
        return new KingdomPlanner.Shape(facing, left, right, Math.min(a, b), Math.max(a, b));
    }

    static KingdomPlanner.Item item(String key, boolean outer, boolean likesTrees, int width, int depth) {
        List<KingdomPlanner.Shape> shapes = new ArrayList<>();
        for (Side facing : Side.values()) {
            shapes.add(shape(facing, width, depth));
        }
        return new KingdomPlanner.Item(key, outer, likesTrees, shapes);
    }

    static final KingdomPlanner.Item CAMP = item("csr:lumber_camp", true, true, 16, 11);
    static final KingdomPlanner.Item FIELD = item("csr:farm_field_wheat", true, false, 13, 9);
    static final KingdomPlanner.Item COTTAGE = item("csr:cottage", false, false, 9, 11);
    static final KingdomPlanner.Item DOCK = item("csr:fishing_dock", true, false, 7, 9);

    /** The keep's ground, its easel at 0, 0, its front facing {@code front}. */
    static Rect keep(Side front) {
        KingdomPlanner.Shape shape = shape(front.opposite(), 31, 33);
        return new Rect(shape.minX() - 1, shape.maxX() + 1, shape.minZ() - 1, shape.maxZ() + 1);
    }

    static KingdomPlanner.Result plan(Land land, Side front) {
        return KingdomPlanner.plan(land, new Cell(0, 0), keep(front), new Cell(0, 0), front, List.of(CAMP, FIELD, COTTAGE), COTTAGE, DOCK, RULES);
    }

    /** What every plan must be: valid, every building planned, a straight wall, houses inside, outer buildings outside, on known dry even ground. */
    static String sound(Land land, KingdomPlanner.Result result, Side front) {
        if (result.plan().isEmpty()) {
            return "no plan facing " + front + ": " + result.notes();
        }
        MasterPlan plan = result.plan().get();
        List<String> wrong = new ArrayList<>();
        List<LayoutChecker.Problem> problems = plan.check();
        if (!problems.isEmpty()) {
            wrong.add("layout problems " + problems);
        }
        for (KingdomPlanner.Item item : List.of(CAMP, FIELD, COTTAGE)) {
            if (plan.plotsFor(item.key()).size() != 1) {
                wrong.add("expected one plot for " + item.key() + ", got " + plan.plotsFor(item.key()).size());
            }
        }
        Rect wall = plan.wallRect();
        if (plan.wallCorners().size() != 4 || !plan.wallCorners().stream().allMatch(corner ->
                (corner.x() == wall.minX() || corner.x() == wall.maxX()) && (corner.z() == wall.minZ() || corner.z() == wall.maxZ()))) {
            wrong.add("the wall isn't a straight-sided rectangle: " + plan.wallCorners());
        }
        if (plan.gates().size() < 2) {
            wrong.add("fewer than 2 gates: " + plan.gates());
        }
        if (!wall.grow(-1).contains(new Cell(plan.keep().centerX(), plan.keep().centerZ())) || !inside(wall, plan.keep())) {
            wrong.add("the keep " + plan.keep() + " isn't inside the wall " + wall);
        }
        List<Cell> easels = new ArrayList<>(List.of(new Cell(0, 0)));
        for (MasterPlan.Plot plot : plan.plots()) {
            for (Cell other : easels) {
                if (Math.max(Math.abs(plot.easelX() - other.x()), Math.abs(plot.easelZ() - other.z())) <= RULES.easelSpacing()) {
                    wrong.add(plot.key() + "'s easel at " + plot.easelX() + ", " + plot.easelZ() + " is within " + RULES.easelSpacing() + " of the easel at " + other);
                }
            }
            easels.add(new Cell(plot.easelX(), plot.easelZ()));
            if (plot.outer() ? plot.rect().overlaps(wall, 0) : !inside(wall, plot.rect())) {
                wrong.add(plot.key() + " " + plot.kind().lowerName() + " at " + plot.rect() + (plot.outer() ? " is not outside" : " is not inside") + " the wall " + wall);
            }
            String ground = ground(land, plot.rect());
            if (ground != null) {
                wrong.add(plot.key() + " " + plot.kind().lowerName() + " at " + plot.rect() + ": " + ground);
            }
            if (plan.claimRadius() < Math.ceil(plot.rect().reach(0, 0))) {
                wrong.add("the claim " + plan.claimRadius() + " doesn't hold " + plot.key() + " at " + plot.rect());
            }
        }
        return wrong.isEmpty() ? null : "facing " + front + ": " + String.join("; ", wrong);
    }

    /** Whether {@code inner} lies strictly inside the wall {@code wall} (not on it). */
    static boolean inside(Rect wall, Rect inner) {
        return inner.minX() > wall.minX() && inner.maxX() < wall.maxX() && inner.minZ() > wall.minZ() && inner.maxZ() < wall.maxZ();
    }

    /** What is wrong with a plot's ground (unknown, wet, a cave under it, too uneven), or null. */
    static String ground(Land land, Rect rect) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int x = rect.minX(); x <= rect.maxX(); x++) {
            for (int z = rect.minZ(); z <= rect.maxZ(); z++) {
                if (!land.known(x, z)) {
                    return "unknown ground at " + x + ", " + z;
                }
                if (land.wet(x, z)) {
                    return "water at " + x + ", " + z;
                }
                if (land.hollow(x, z) > 0) {
                    return "a cave under " + x + ", " + z;
                }
                min = Math.min(min, land.height(x, z));
                max = Math.max(max, land.height(x, z));
            }
        }
        return max - min > RULES.maxRange() ? "ground from " + min + " to " + max + " (more than " + RULES.maxRange() + ")" : null;
    }

    // The cases

    /** Flat land, every front: a sound plan. */
    static String flatPlanIsSound() {
        Land land = new Land();
        List<String> wrong = new ArrayList<>();
        for (Side front : Side.values()) {
            String problem = sound(land, plan(land, front), front);
            if (problem != null) {
                wrong.add(problem);
            }
        }
        return wrong.isEmpty() ? null : String.join(" | ", wrong);
    }

    /** A cave right under where the cottage would go: the cottage moves off it, and no plot is planned over the cave. */
    static String caveIsAvoided() {
        Land land = new Land();
        KingdomPlanner.Result before = plan(land, Side.SOUTH);
        if (before.plan().isEmpty()) {
            return "no plan on flat land: " + before.notes();
        }
        Rect cottage = before.plan().get().plotsFor(COTTAGE.key()).get(0).rect();
        land.cave(cottage.grow(2), 3);
        KingdomPlanner.Result after = plan(land, Side.SOUTH);
        String problem = sound(land, after, Side.SOUTH);
        if (problem != null) {
            return "with a cave under " + cottage.grow(2) + ": " + problem;
        }
        Rect moved = after.plan().get().plotsFor(COTTAGE.key()).get(0).rect();
        return moved.overlaps(cottage.grow(2), 0) ? "the cottage stays over the cave: " + moved : null;
    }

    /**
     * A river just east of where the wall goes (within the outer band, {@code layout.outer_band}): no plot on the water, a plot by the water kept for a fishing village, and the outer buildings
     * away from the water when there is room (the owner's planner-page notes).
     */
    static String riverGetsAFishingPlot() {
        Land land = new Land();
        for (int x = RIVER; x <= RIVER + 6; x++) {
            for (int z = -OFFSET; z < SIZE - OFFSET; z++) {
                land.wet[x + OFFSET][z + OFFSET] = true;
            }
        }
        KingdomPlanner.Result result = plan(land, Side.SOUTH);
        String problem = sound(land, result, Side.SOUTH);
        if (problem != null) {
            return problem;
        }
        MasterPlan plan = result.plan().get();
        Optional<MasterPlan.Plot> dock = plan.plots().stream().filter(plot -> plot.kind() == MasterPlan.Kind.WATERSIDE).findFirst();
        if (dock.isEmpty()) {
            return "no plot by the river for a fishing village: " + result.notes();
        }
        if (dock.get().rect().maxX() < RIVER - 4) {
            return "the fishing plot " + dock.get().rect() + " isn't by the water (from x " + RIVER + ")";
        }
        for (MasterPlan.Plot plot : plan.plots()) {
            if (plot.kind() == MasterPlan.Kind.BUILDING && plot.outer() && plot.rect().maxX() >= RIVER - 2) {
                return plot.key() + " at " + plot.rect() + " is right by the water though there is room elsewhere";
            }
        }
        return null;
    }

    /** No river: nothing is kept for a fishing village (it isn't required). */
    static String noWaterNoFishingPlot() {
        Land land = new Land();
        KingdomPlanner.Result result = plan(land, Side.NORTH);
        if (result.plan().isEmpty()) {
            return "no plan: " + result.notes();
        }
        return result.plan().get().plots().stream().anyMatch(plot -> plot.kind() == MasterPlan.Kind.WATERSIDE)
                ? "a fishing plot with no water anywhere: " + result.plan().get().plots() : null;
    }

    /**
     * Rough land: rolling ground, a steep hill west of the square and a sharp drop north: every plot still sits on ground
     * within the height range (the planner works around what levelling can't fix cheaply).
     */
    static String roughLandIsRespected() {
        Land land = new Land();
        for (int x = -OFFSET; x < SIZE - OFFSET; x++) {
            for (int z = -OFFSET; z < SIZE - OFFSET; z++) {
                int h = 64 + (int) Math.round(2 * Math.sin(x / 11.0) + 2 * Math.cos(z / 13.0));
                if (Math.hypot(x + 30, z - 40) < 14) {
                    h += 9;
                }
                if (z < -60) {
                    h -= 6;
                }
                land.height[x + OFFSET][z + OFFSET] = h;
            }
        }
        List<String> wrong = new ArrayList<>();
        for (Side front : Side.values()) {
            String problem = sound(land, plan(land, front), front);
            if (problem != null) {
                wrong.add(problem);
            }
        }
        return wrong.isEmpty() ? null : String.join(" | ", wrong);
    }

    /** Only part of the land is known (chunks not loaded): plots only on known ground. */
    static String unknownLandIsLeftAlone() {
        Land land = new Land();
        land.knownRadius = 70;
        KingdomPlanner.Result result = plan(land, Side.EAST);
        return sound(land, result, Side.EAST);
    }

    /**
     * Water all around, close to the town: the town inside the wall still gets planned, the outer buildings that find no
     * dry ground are left out with a note (they are placed the stage 5 way later), and nothing is planned on the water.
     */
    static String crampedLandLeavesOutWhatDoesntFit() {
        Land land = new Land();
        for (int x = -OFFSET; x < SIZE - OFFSET; x++) {
            for (int z = -OFFSET; z < SIZE - OFFSET; z++) {
                if (Math.abs(x) > 40 || Math.abs(z) > 50) {
                    land.wet[x + OFFSET][z + OFFSET] = true;
                }
            }
        }
        KingdomPlanner.Result result = plan(land, Side.SOUTH);
        if (result.plan().isEmpty()) {
            return result.notes().isEmpty() ? "no plan and no reason" : null;
        }
        MasterPlan plan = result.plan().get();
        List<String> wrong = new ArrayList<>();
        if (!plan.check().isEmpty()) {
            wrong.add("layout problems " + plan.check());
        }
        for (MasterPlan.Plot plot : plan.plots()) {
            String ground = ground(land, plot.rect());
            if (ground != null) {
                wrong.add(plot.key() + " at " + plot.rect() + ": " + ground);
            }
        }
        for (KingdomPlanner.Item item : List.of(CAMP, FIELD)) {
            if (plan.plotsFor(item.key()).isEmpty() && result.notes().stream().noneMatch(note -> note.contains(item.key()))) {
                wrong.add(item.key() + " left out without a note: " + result.notes());
            }
        }
        if (plan.plotsFor(COTTAGE.key()).isEmpty()) {
            wrong.add("no cottage plot inside the wall: " + result.notes());
        }
        return wrong.isEmpty() ? null : String.join("; ", wrong);
    }

    /** Every case by name, for a quick run outside the game. */
    static List<String> all() {
        List<String> results = new ArrayList<>();
        results.add("flatPlanIsSound: " + flatPlanIsSound());
        results.add("caveIsAvoided: " + caveIsAvoided());
        results.add("riverGetsAFishingPlot: " + riverGetsAFishingPlot());
        results.add("noWaterNoFishingPlot: " + noWaterNoFishingPlot());
        results.add("roughLandIsRespected: " + roughLandIsRespected());
        results.add("unknownLandIsLeftAlone: " + unknownLandIsLeftAlone());
        results.add("crampedLandLeavesOutWhatDoesntFit: " + crampedLandLeavesOutWhatDoesntFit());
        return results;
    }
}
