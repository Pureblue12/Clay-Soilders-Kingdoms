package net.bumblebee.claysoldiers.kingdom.layout;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The kingdom planner (stage 6, plain data, no world access): reads the land around a finished keep and lays out the whole
 * town in advance, for function and looks (docs/ROADMAP.md stage 6, the owner's planner page and notes, D105–D110).
 * <ol>
 *     <li><b>Square and streets:</b> an open square in front of the keep's door, a main street from the square straight
 *     out, and a cross street through the far side of the square.</li>
 *     <li><b>Houses:</b> inner buildings (and the plots kept free for the buildings a kingdom adds later) line the streets
 *     with their fronts on the road, nearest the square first; the two sides of a street fill in turn, so they face each
 *     other. A plot needs known, dry ground that rises and falls at most {@link Rules#maxRange}; levelling, trees and caves
 *     under it cost points.</li>
 *     <li><b>The wall</b> is sized from what it holds (the keep and every house plot, {@link Rules#wallSetback} away), so
 *     it fits the real buildings; it runs in four straight lines (the wall pieces are straight) and each side moves out
 *     up to {@link Rules#wallShift} blocks where that keeps it off water, cliffs and caves. A gate stands wherever a street
 *     crosses it; the streets run on out of the gates.</li>
 *     <li><b>Outer buildings</b> (fields, the lumber camp) go in a band just outside the wall, fronts toward the town, each
 *     with a path to the nearest gate road (outer buildings for later get theirs too); they keep away from water and a
 *     lumber camp likes trees around it. One plot by
 *     the water is kept free for a fishing village when there is water nearby.</li>
 * </ol>
 * Everything is placed in a frame turned with the keep's front, so a town looks the same whichever way its keep faces.
 * A building that finds no plot is left out of the plan (the kingdom then looks for a plot the stage 5 way).
 */
public final class KingdomPlanner {
    /** Points per column with trees on a plot (they are cleared). */
    static final int TREE_COST = 3;
    /** Points per column with a cave under it (the kingdom fills it, but solid ground is better). */
    static final int HOLLOW_COST = 20;
    /** Points per block a house plot lies from the square. */
    static final int DISTANCE_COST = 2;
    /** Points per block of an outer building's path to its gate road. */
    static final int PATH_COST = 2;
    /** Points per water column near an outer plot. */
    static final int WATER_NEAR_COST = 4;
    /** Points off per column with trees near an outer plot that wants them. */
    static final int TREE_NEAR_BONUS = 2;
    /** How far around an outer plot water and trees count as near. */
    static final int NEAR = 8;
    /** How far along a street a house plot is looked for past the street's last plot. */
    static final int STREET_SEARCH = 48;
    /** Wall cost of one column: unknown, water, a cave under it, trees, and per block of a step along the wall. */
    static final int WALL_UNKNOWN = 3;
    static final int WALL_WET = 12;
    static final int WALL_HOLLOW = 2;
    static final int WALL_TREES = 1;
    static final int WALL_STEP = 2;
    /** How far the streets run on outside the gates, past the free ground around the wall. */
    static final int ROAD_PAST_GATE = 2;
    /** How near (blocks) water must be to a plot kept for a fishing village, and how many water columns it needs there. */
    static final int WATERSIDE_REACH = 3;
    static final int WATERSIDE_MIN_WATER = 3;

    private KingdomPlanner() {
    }

    /**
     * A building's ground for one build direction, relative to its easel (inclusive, without the margin).
     *
     * @param facing the build direction: the building stands that way from its easel, its front faces the other way
     */
    public record Shape(Side facing, int minX, int maxX, int minZ, int maxZ) {
        int midX() {
            return Math.floorDiv(minX + maxX, 2);
        }

        int midZ() {
            return Math.floorDiv(minZ + maxZ, 2);
        }
    }

    /**
     * A building to plan.
     *
     * @param key        its structure id
     * @param outer      whether it goes outside the wall
     * @param likesTrees whether trees near it are good (a lumber camp)
     * @param shapes     its ground for each build direction
     */
    public record Item(String key, boolean outer, boolean likesTrees, List<Shape> shapes) {
        public Item {
            shapes = List.copyOf(shapes);
        }

        Shape shape(Side facing) {
            return shapes.stream().filter(shape -> shape.facing() == facing).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(key + " has no shape for " + facing));
        }
    }

    /**
     * The planner's numbers (tuning keys {@code layout.*}).
     *
     * @param margin       columns around a building that are levelled with it
     * @param gap          free columns between plots
     * @param roadWidth    street width (odd)
     * @param squareDepth  how deep the square in front of the keep is
     * @param squareWidth  how wide the square is (odd)
     * @param maxRange     the most a plot's ground may rise and fall
     * @param wallSetback  free columns between the wall and the plots inside it
     * @param wallShift    the most a wall side moves out to stay on good ground
     * @param outerBand    how far outside the wall outer buildings may stand
     * @param outerStep    distance between candidate outer plots
     * @param easelSpacing how far apart easels must be (an easel isn't placed with another within this many blocks either
     *                     way, {@code BuildSitePlanner.EASEL_SPACING})
     */
    public record Rules(int margin, int gap, int roadWidth, int squareDepth, int squareWidth, int maxRange, int wallSetback,
                        int wallShift, int outerBand, int outerStep, int easelSpacing) {
    }

    /**
     * What the planner made.
     *
     * @param plan  the plan, or empty when even the streets and wall couldn't be laid out
     * @param notes what it decided and what it couldn't place, for the recorder and the book
     */
    public record Result(Optional<MasterPlan> plan, List<String> notes) {
    }

    /**
     * Plans a town.
     *
     * @param ground      the land
     * @param claimCenter the kingdom's center (the claim is measured from it)
     * @param keep        the keep's ground (with margin)
     * @param keepEasel   the keep's easel (no plot's easel may stand near it)
     * @param front       the side the keep's front faces
     * @param items       the buildings to plan, in the kingdom's order
     * @param later       the buildings plots are kept free for, in order (inner ones along the streets after the kingdom's
     *                    own, outer ones outside the wall after its own); one that doesn't fit is left out with a note
     * @param waterside   the plot kept free by the water (none when null)
     */
    public static Result plan(Ground ground, Cell claimCenter, Rect keep, Cell keepEasel, Side front, List<Item> items, List<Item> later,
                              @Nullable Item waterside, Rules rules) {
        return new Planner(ground, keep, keepEasel, front, rules).run(claimCenter, items, later, waterside);
    }

    /** Ground under a rectangle: whether it can be built on, and what it costs. */
    record Judgement(boolean usable, String why, int earth, int trees, int hollow, int groundY) {
        int cost() {
            return earth + TREE_COST * trees + HOLLOW_COST * hollow;
        }
    }

    /** Judges the ground of {@code rect}: known, dry, rises and falls at most {@code maxRange}; the cost of levelling it. */
    static Judgement judge(Ground ground, Rect rect, int maxRange) {
        int[] heights = new int[rect.sizeX() * rect.sizeZ()];
        int n = 0;
        int trees = 0;
        int hollow = 0;
        for (int x = rect.minX(); x <= rect.maxX(); x++) {
            for (int z = rect.minZ(); z <= rect.maxZ(); z++) {
                if (!ground.known(x, z)) {
                    return new Judgement(false, "unknown", 0, 0, 0, 0);
                }
                if (ground.wet(x, z)) {
                    return new Judgement(false, "wet", 0, 0, 0, 0);
                }
                trees += ground.trees(x, z) ? 1 : 0;
                hollow += ground.hollow(x, z) > 0 ? 1 : 0;
                heights[n++] = ground.height(x, z);
            }
        }
        Arrays.sort(heights);
        if (heights[n - 1] - heights[0] > maxRange) {
            return new Judgement(false, "uneven", 0, 0, 0, 0);
        }
        int groundY = heights[n / 2];
        int earth = 0;
        for (int height : heights) {
            earth += Math.abs(height - groundY);
        }
        return new Judgement(true, "", earth, trees, hollow, groundY);
    }

    /**
     * One planning run. Local frame: v runs along the keep's front (away from the keep), u to its right; (0, 0) is the
     * cell in front of the middle of the keep's front edge.
     */
    private static final class Planner {
        private final Ground ground;
        private final Rect keep;
        private final Cell keepEasel;
        private final Side front;
        private final Side right;
        private final Rules rules;
        private final int originX;
        private final int originZ;
        private final int half;
        private final int squareHalf;
        /** Local v of the cross street's near edge. */
        private final int crossV;
        private final List<MasterPlan.Plot> plots = new ArrayList<>();
        private final List<MasterPlan.Road> roads = new ArrayList<>();
        private final List<String> notes = new ArrayList<>();
        /** Columns no plot may cover: streets and the square (local strips run far out until the wall is known). */
        private final List<Rect> blocked = new ArrayList<>();

        Planner(Ground ground, Rect keep, Cell keepEasel, Side front, Rules rules) {
            this.ground = ground;
            this.keep = keep;
            this.keepEasel = keepEasel;
            this.front = front;
            this.right = front.clockwise();
            this.rules = rules;
            int edge = keep.edge(front);
            int mid = front.dx != 0 ? keep.centerZ() : keep.centerX();
            this.originX = front.dx != 0 ? edge + front.dx : mid;
            this.originZ = front.dz != 0 ? edge + front.dz : mid;
            this.half = Math.max(0, rules.roadWidth() / 2);
            this.squareHalf = Math.max(half, rules.squareWidth() / 2);
            this.crossV = Math.max(1, rules.squareDepth());
        }

        // Frame

        Cell world(int u, int v) {
            return new Cell(originX + v * front.dx + u * right.dx, originZ + v * front.dz + u * right.dz);
        }

        Rect world(int u0, int v0, int u1, int v1) {
            Cell a = world(u0, v0);
            Cell b = world(u1, v1);
            return Rect.of(a.x(), a.z(), b.x(), b.z());
        }

        int u(int x, int z) {
            return (x - originX) * right.dx + (z - originZ) * right.dz;
        }

        int v(int x, int z) {
            return (x - originX) * front.dx + (z - originZ) * front.dz;
        }

        /** A world rectangle in the local frame: {u0, u1, v0, v1}. */
        int[] local(Rect rect) {
            int ua = u(rect.minX(), rect.minZ());
            int ub = u(rect.maxX(), rect.maxZ());
            int va = v(rect.minX(), rect.minZ());
            int vb = v(rect.maxX(), rect.maxZ());
            return new int[]{Math.min(ua, ub), Math.max(ua, ub), Math.min(va, vb), Math.max(va, vb)};
        }

        /** The world side of a local direction: 0 = +u, 1 = -u, 2 = +v, 3 = -v. */
        Side side(int local) {
            return switch (local) {
                case 0 -> right;
                case 1 -> right.opposite();
                case 2 -> front;
                default -> front.opposite();
            };
        }

        Result run(Cell claimCenter, List<Item> items, List<Item> later, @Nullable Item waterside) {
            int far = 1000;
            // The square and the streets (the cross street's ends and the main street's length come with the wall).
            blocked.add(world(-squareHalf, 0, squareHalf, crossV - 1));
            blocked.add(world(-half, 0, half, far));
            blocked.add(world(-far, crossV, far, crossV + 2 * half));
            List<Street> streets = streets();
            for (Item item : items) {
                if (!item.outer()) {
                    placeHouse(streets, item, MasterPlan.Kind.BUILDING);
                }
            }
            for (Item item : later) {
                if (!item.outer()) {
                    placeHouse(streets, item, MasterPlan.Kind.LATER);
                }
            }
            int[] wall = wall();
            int wallU0 = wall[0];
            int wallU1 = wall[1];
            int wallV0 = wall[2];
            int wallV1 = wall[3];
            int crossMid = crossV + half;
            Cell mainGate = world(0, wallV1);
            Cell leftGate = world(wallU0, crossMid);
            Cell rightGate = world(wallU1, crossMid);
            blocked.clear();
            int past = clearance() + ROAD_PAST_GATE;
            roads.add(new MasterPlan.Road(world(0, 0), world(0, crossV - 1), 2 * squareHalf + 1));
            roads.add(new MasterPlan.Road(world(0, crossV), world(0, wallV1 + past), 2 * half + 1));
            roads.add(new MasterPlan.Road(world(wallU0 - past, crossMid), world(wallU1 + past, crossMid), 2 * half + 1));
            Rect wallRect = world(wallU0, wallV0, wallU1, wallV1);
            List<Cell> corners = List.of(new Cell(wallRect.minX(), wallRect.minZ()), new Cell(wallRect.maxX(), wallRect.minZ()),
                    new Cell(wallRect.maxX(), wallRect.maxZ()), new Cell(wallRect.minX(), wallRect.maxZ()));
            List<MasterPlan.Gate> gates = List.of(new MasterPlan.Gate(mainGate, side(2)), new MasterPlan.Gate(leftGate, side(1)),
                    new MasterPlan.Gate(rightGate, side(0)));
            List<Cell> roadEnds = List.of(world(0, wallV1 + past), world(wallU0 - past, crossMid),
                    world(wallU1 + past, crossMid));
            // Nothing outside stands in front of a gate: the streets look out of the town and can go on later.
            int lane = half + rules.gap();
            int out = past + rules.outerBand() + 64;
            blocked.add(world(-lane, wallV1, lane, wallV1 + out));
            blocked.add(world(wallU0 - out, crossMid - lane, wallU0, crossMid + lane));
            blocked.add(world(wallU1, crossMid - lane, wallU1 + out, crossMid + lane));
            for (Item item : items) {
                if (item.outer()) {
                    placeOuter(item, MasterPlan.Kind.BUILDING, wallRect, roadEnds);
                }
            }
            for (Item item : later) {
                if (item.outer()) {
                    placeOuter(item, MasterPlan.Kind.LATER, wallRect, roadEnds);
                }
            }
            if (waterside != null) {
                placeOuter(waterside, MasterPlan.Kind.WATERSIDE, wallRect, roadEnds);
            }
            int claim = (int) Math.ceil(wallRect.reach(claimCenter.x(), claimCenter.z()));
            for (MasterPlan.Plot plot : plots) {
                claim = Math.max(claim, (int) Math.ceil(plot.rect().reach(claimCenter.x(), claimCenter.z())));
            }
            MasterPlan plan = new MasterPlan(world(0, Math.max(0, crossV / 2)), keep, front, plots, roads, corners, gates, claim + 1);
            List<LayoutChecker.Problem> problems = plan.check();
            if (!problems.isEmpty()) {
                notes.add("the layout checker refused the plan: " + problems.stream().map(LayoutChecker.Problem::message).toList());
                return new Result(Optional.empty(), List.copyOf(notes));
            }
            notes.add(0, "planned " + plan.summary());
            return new Result(Optional.of(plan), List.copyOf(notes));
        }

        // Houses along the streets

        /**
         * A side of a street houses line: plots go {@code along} (a local direction, 0 = +u, 1 = -u, 2 = +v) from
         * {@code cursor}, and stand toward {@code toward} with their front on the road edge {@code roadEdge}.
         */
        private final class Street {
            final int along;
            final int toward;
            final int roadEdge;
            final int start;
            int cursor;

            Street(int along, int toward, int roadEdge, int start) {
                this.along = along;
                this.toward = toward;
                this.roadEdge = roadEdge;
                this.start = start;
                this.cursor = start;
            }
        }

        private List<Street> streets() {
            int beyondCross = crossV + 2 * half + 1;
            int gap = rules.gap();
            return List.of(
                    new Street(2, 0, half + 1, beyondCross + gap),
                    new Street(2, 1, -(half + 1), beyondCross + gap),
                    new Street(0, 3, crossV - 1, squareHalf + 1 + gap),
                    new Street(1, 3, crossV - 1, squareHalf + 1 + gap),
                    new Street(0, 2, beyondCross, half + 1 + gap),
                    new Street(1, 2, beyondCross, half + 1 + gap));
        }

        /** A candidate plot along a street. */
        private record Candidate(Street street, Side facing, int easelX, int easelZ, Rect rect, int next, int score) {
        }

        private void placeHouse(List<Street> streets, Item item, MasterPlan.Kind kind) {
            Candidate best = null;
            for (Street street : streets) {
                Candidate candidate = alongStreet(street, item);
                if (candidate != null && (best == null || candidate.score() < best.score())) {
                    best = candidate;
                }
            }
            if (best == null) {
                notes.add("no plot along the streets for " + item.key() + (kind == MasterPlan.Kind.LATER ? " (kept for later)" : ""));
                return;
            }
            plots.add(new MasterPlan.Plot(item.key(), kind, false, best.facing(), best.easelX(), best.easelZ(), best.rect()));
            best.street().cursor = best.next();
        }

        /** The first usable plot along {@code street} from its cursor, or null. */
        @Nullable
        private Candidate alongStreet(Street street, Item item) {
            Side facing = side(street.toward);
            Shape shape = item.shape(facing);
            int m = rules.margin();
            Rect relative = new Rect(shape.minX() - m, shape.maxX() + m, shape.minZ() - m, shape.maxZ() + m);
            // The plot's extent in the local frame, relative to its easel.
            int[] rel = localDelta(relative);
            int a0 = rel[0];
            int a1 = rel[1];
            int b0 = rel[2];
            int b1 = rel[3];
            for (int p = street.cursor; p <= street.cursor + STREET_SEARCH; p++) {
                int eu;
                int ev;
                switch (street.toward) {
                    case 0 -> eu = street.roadEdge - a0;
                    case 1 -> eu = street.roadEdge - a1;
                    default -> eu = 0;
                }
                switch (street.toward) {
                    case 2 -> ev = street.roadEdge - b0;
                    case 3 -> ev = street.roadEdge - b1;
                    default -> ev = 0;
                }
                int alongEnd;
                switch (street.along) {
                    case 0 -> {
                        eu = p - a0;
                        alongEnd = eu + a1;
                    }
                    case 1 -> {
                        eu = -p - a1;
                        alongEnd = -(eu + a0);
                    }
                    default -> {
                        ev = p - b0;
                        alongEnd = ev + b1;
                    }
                }
                Cell easel = world(eu, ev);
                Rect rect = new Rect(easel.x() + relative.minX(), easel.x() + relative.maxX(), easel.z() + relative.minZ(), easel.z() + relative.maxZ());
                if (taken(rect) || easelTooClose(easel.x(), easel.z())) {
                    continue;
                }
                Judgement judgement = judge(ground, rect, rules.maxRange());
                if (!judgement.usable()) {
                    continue;
                }
                int[] local = local(rect);
                int du = (local[0] + local[1]) / 2;
                int dv = (local[2] + local[3]) / 2;
                int distance = (int) Math.round(Math.sqrt((double) du * du + (double) dv * dv));
                return new Candidate(street, facing, easel.x(), easel.z(), rect, alongEnd + 1 + rules.gap(),
                        judgement.cost() + DISTANCE_COST * distance);
            }
            return null;
        }

        /** A rectangle relative to an easel (world offsets), as local offsets {u0, u1, v0, v1}. */
        int[] localDelta(Rect relative) {
            int ua = relative.minX() * right.dx + relative.minZ() * right.dz;
            int ub = relative.maxX() * right.dx + relative.maxZ() * right.dz;
            int va = relative.minX() * front.dx + relative.minZ() * front.dz;
            int vb = relative.maxX() * front.dx + relative.maxZ() * front.dz;
            return new int[]{Math.min(ua, ub), Math.max(ua, ub), Math.min(va, vb), Math.max(va, vb)};
        }

        private boolean taken(Rect rect) {
            if (rect.overlaps(keep, rules.gap())) {
                return true;
            }
            for (Rect strip : blocked) {
                if (rect.overlaps(strip, 0)) {
                    return true;
                }
            }
            for (MasterPlan.Plot plot : plots) {
                if (rect.overlaps(plot.rect(), rules.gap())) {
                    return true;
                }
            }
            return false;
        }

        /** Whether an easel at x, z would stand too near the keep's easel or another plot's (it couldn't be placed). */
        private boolean easelTooClose(int x, int z) {
            int spacing = rules.easelSpacing();
            if (Math.max(Math.abs(x - keepEasel.x()), Math.abs(z - keepEasel.z())) <= spacing) {
                return true;
            }
            for (MasterPlan.Plot plot : plots) {
                if (Math.max(Math.abs(x - plot.easelX()), Math.abs(z - plot.easelZ())) <= spacing) {
                    return true;
                }
            }
            return false;
        }

        // The wall

        /** The wall's local rectangle {u0, u1, v0, v1}: around the keep, the square and every house, moved onto good ground. */
        private int[] wall() {
            int[] box = local(keep);
            int u0 = Math.min(box[0], -squareHalf);
            int u1 = Math.max(box[1], squareHalf);
            int v0 = box[2];
            int v1 = Math.max(box[3], crossV + 2 * half);
            for (MasterPlan.Plot plot : plots) {
                int[] p = local(plot.rect());
                u0 = Math.min(u0, p[0]);
                u1 = Math.max(u1, p[1]);
                v0 = Math.min(v0, p[2]);
                v1 = Math.max(v1, p[3]);
            }
            int s = Math.max(1, rules.wallSetback()) + 1;
            int[] wall = {u0 - s, u1 + s, v0 - s, v1 + s};
            // Each side in turn moves out to its cheapest line (0: -u, 1: +u, 2: -v, 3: +v).
            for (int side = 0; side < 4; side++) {
                int bestShift = 0;
                int bestCost = Integer.MAX_VALUE;
                for (int shift = 0; shift <= rules.wallShift(); shift++) {
                    int[] moved = wall.clone();
                    switch (side) {
                        case 0 -> moved[0] -= shift;
                        case 1 -> moved[1] += shift;
                        case 2 -> moved[2] -= shift;
                        default -> moved[3] += shift;
                    }
                    int cost = sideCost(moved, side) + shift;
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestShift = shift;
                    }
                }
                switch (side) {
                    case 0 -> wall[0] -= bestShift;
                    case 1 -> wall[1] += bestShift;
                    case 2 -> wall[2] -= bestShift;
                    default -> wall[3] += bestShift;
                }
                if (bestShift > 0) {
                    notes.add("moved the wall's " + side(new int[]{1, 0, 3, 2}[side]).lowerName() + " side out " + bestShift
                            + " blocks onto better ground");
                }
            }
            return wall;
        }

        /** What one side of the wall rectangle {@code wall} costs to build on. */
        private int sideCost(int[] wall, int side) {
            List<Cell> line = new ArrayList<>();
            if (side < 2) {
                int u = wall[side];
                for (int v = wall[2]; v <= wall[3]; v++) {
                    line.add(world(u, v));
                }
            } else {
                int v = wall[side];
                for (int u = wall[0]; u <= wall[1]; u++) {
                    line.add(world(u, v));
                }
            }
            int cost = 0;
            Integer last = null;
            for (Cell cell : line) {
                if (!ground.known(cell.x(), cell.z())) {
                    cost += WALL_UNKNOWN;
                    last = null;
                    continue;
                }
                cost += ground.wet(cell.x(), cell.z()) ? WALL_WET : 0;
                cost += ground.hollow(cell.x(), cell.z()) > 0 ? WALL_HOLLOW : 0;
                cost += ground.trees(cell.x(), cell.z()) ? WALL_TREES : 0;
                int height = ground.height(cell.x(), cell.z());
                if (last != null) {
                    int step = Math.abs(height - last);
                    cost += step >= 2 ? WALL_STEP * step : 0;
                }
                last = height;
            }
            return cost;
        }

        // Outside the wall

        /** Free ground kept around the outside of the wall. */
        private int clearance() {
            return Math.max(2, rules.gap());
        }

        private void placeOuter(Item item, MasterPlan.Kind kind, Rect wallRect, List<Cell> roadEnds) {
            Rect inner = wallRect.grow(clearance());
            Set<Cell> roadCells = new HashSet<>();
            roads.forEach(road -> roadCells.addAll(road.cells()));
            int step = Math.max(1, rules.outerStep());
            int townX = wallRect.centerX();
            int townZ = wallRect.centerZ();
            OuterCandidate best = null;
            // The band just outside the wall first; when it is full, a band twice as wide (a big town's later buildings).
            for (int reach = rules.outerBand(); best == null && reach <= 2 * rules.outerBand(); reach += rules.outerBand()) {
                Rect band = wallRect.grow(Math.max(clearance() + 1, reach));
                for (int x = band.minX(); x <= band.maxX(); x += step) {
                    for (int z = band.minZ(); z <= band.maxZ(); z += step) {
                        if (inner.contains(x, z)) {
                            continue;
                        }
                        OuterCandidate candidate = outerAt(item, kind, x, z, townX, townZ, inner, band, roadCells, roadEnds);
                        if (candidate != null && (best == null || candidate.score() < best.score())) {
                            best = candidate;
                        }
                    }
                }
            }
            if (best == null) {
                notes.add(kind == MasterPlan.Kind.WATERSIDE ? "no plot by the water to keep for a fishing village"
                        : "no plot outside the wall for " + item.key() + (kind == MasterPlan.Kind.LATER ? " (kept for later)" : ""));
                return;
            }
            plots.add(new MasterPlan.Plot(item.key(), kind, true, best.facing(), best.easelX(), best.easelZ(), best.rect()));
            roads.addAll(best.path());
        }

        private record OuterCandidate(Side facing, int easelX, int easelZ, Rect rect, List<MasterPlan.Road> path, int score) {
        }

        @Nullable
        private OuterCandidate outerAt(Item item, MasterPlan.Kind kind, int x, int z, int townX, int townZ, Rect inner, Rect band,
                                       Set<Cell> roadCells, List<Cell> roadEnds) {
            // The building stands away from the town: its front faces the town.
            Side facing = Side.toward(x - townX, z - townZ);
            Shape shape = item.shape(facing);
            int m = rules.margin();
            int easelX = x - shape.midX();
            int easelZ = z - shape.midZ();
            Rect rect = new Rect(easelX + shape.minX() - m, easelX + shape.maxX() + m, easelZ + shape.minZ() - m, easelZ + shape.maxZ() + m);
            if (rect.overlaps(inner, 0) || !band.contains(rect.minX(), rect.minZ()) || !band.contains(rect.maxX(), rect.maxZ())
                    || easelTooClose(easelX, easelZ)) {
                return null;
            }
            for (MasterPlan.Plot plot : plots) {
                if (rect.overlaps(plot.rect(), rules.gap())) {
                    return null;
                }
            }
            for (Rect lane : blocked) {
                if (rect.overlaps(lane, 0)) {
                    return null;
                }
            }
            for (int cx = rect.minX(); cx <= rect.maxX(); cx++) {
                for (int cz = rect.minZ(); cz <= rect.maxZ(); cz++) {
                    if (roadCells.contains(new Cell(cx, cz))) {
                        return null;
                    }
                }
            }
            Judgement judgement = judge(ground, rect, rules.maxRange());
            if (!judgement.usable()) {
                return null;
            }
            int waterNear = 0;
            int treesNear = 0;
            int waterClose = 0;
            for (int cx = rect.minX() - NEAR; cx <= rect.maxX() + NEAR; cx++) {
                for (int cz = rect.minZ() - NEAR; cz <= rect.maxZ() + NEAR; cz++) {
                    if (rect.contains(cx, cz) || !ground.known(cx, cz)) {
                        continue;
                    }
                    if (ground.wet(cx, cz)) {
                        waterNear++;
                        if (rect.grow(WATERSIDE_REACH).contains(cx, cz)) {
                            waterClose++;
                        }
                    }
                    if (ground.trees(cx, cz)) {
                        treesNear++;
                    }
                }
            }
            if (kind == MasterPlan.Kind.WATERSIDE && waterClose < WATERSIDE_MIN_WATER) {
                return null;
            }
            List<MasterPlan.Road> path = path(rect, facing.opposite(), roadEnds, inner, roadCells);
            if (path == null) {
                return null;
            }
            int length = path.stream().mapToInt(MasterPlan.Road::length).sum();
            int score = judgement.cost() + PATH_COST * length;
            if (kind == MasterPlan.Kind.WATERSIDE) {
                score -= 4 * waterClose;
            } else {
                score += WATER_NEAR_COST * waterNear - (item.likesTrees() ? TREE_NEAR_BONUS * treesNear : 0);
            }
            return new OuterCandidate(facing, easelX, easelZ, rect, path, score);
        }

        /**
         * A path (1 wide, one or two straight pieces) from the middle of the plot's front to the nearest street end outside
         * the wall that it can reach without crossing the wall, a plot or water; null when there is none.
         */
        @Nullable
        private List<MasterPlan.Road> path(Rect rect, Side frontSide, List<Cell> roadEnds, Rect inner, Set<Cell> roadCells) {
            Cell start = switch (frontSide) {
                case NORTH -> new Cell(rect.centerX(), rect.minZ() - 1);
                case SOUTH -> new Cell(rect.centerX(), rect.maxZ() + 1);
                case WEST -> new Cell(rect.minX() - 1, rect.centerZ());
                case EAST -> new Cell(rect.maxX() + 1, rect.centerZ());
            };
            List<Cell> ends = new ArrayList<>(roadEnds);
            ends.sort((a, b) -> Integer.compare(manhattan(start, a), manhattan(start, b)));
            for (Cell end : ends) {
                // Leave the front straight first, then turn; or the other way round.
                Cell bendA = frontSide.dx != 0 ? new Cell(end.x(), start.z()) : new Cell(start.x(), end.z());
                Cell bendB = frontSide.dx != 0 ? new Cell(start.x(), end.z()) : new Cell(end.x(), start.z());
                for (Cell bend : List.of(bendA, bendB)) {
                    List<MasterPlan.Road> path = new ArrayList<>();
                    if (!bend.equals(start)) {
                        path.add(new MasterPlan.Road(start, bend, 1));
                    }
                    if (!bend.equals(end)) {
                        path.add(new MasterPlan.Road(bend, end, 1));
                    }
                    if (path.isEmpty()) {
                        path.add(new MasterPlan.Road(start, start, 1));
                    }
                    if (clear(path, rect, inner, roadCells)) {
                        return path;
                    }
                }
            }
            return null;
        }

        private boolean clear(List<MasterPlan.Road> path, Rect rect, Rect inner, Set<Cell> roadCells) {
            for (MasterPlan.Road road : path) {
                for (Cell cell : road.cells()) {
                    if (roadCells.contains(cell)) {
                        continue;
                    }
                    if (rect.contains(cell) || inner.contains(cell) || !ground.known(cell.x(), cell.z()) || ground.wet(cell.x(), cell.z())) {
                        return false;
                    }
                    for (MasterPlan.Plot plot : plots) {
                        if (plot.rect().contains(cell)) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private static int manhattan(Cell a, Cell b) {
            return Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z());
        }
    }
}
