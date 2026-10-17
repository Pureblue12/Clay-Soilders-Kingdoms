package net.bumblebee.claysoldiers.kingdom.layout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A kingdom's master plan (stage 6, plain data): where every building goes, the roads, the wall and its gates, worked out
 * once by {@link KingdomPlanner} when the keep is finished and saved with the kingdom. Buildings are added on their plots
 * in the order of the kingdom's building list; plots marked {@link Kind#LATER} and {@link Kind#WATERSIDE} are kept free
 * for later buildings.
 *
 * @param center      the middle of the town (in front of the keep), where the wall check starts
 * @param keep        the keep's ground (with its margin)
 * @param front       the side the keep's front (easel and door) faces; the main street runs that way
 * @param plots       every plot, in the order they were planned
 * @param roads       the roads (streets, the square, paths to the outer buildings)
 * @param wallCorners the wall's four corners: the wall runs in straight lines between them
 * @param gates       where roads go through the wall
 * @param claimRadius how far from the kingdom's center the plan reaches (its claim must be at least this big)
 */
public record MasterPlan(Cell center, Rect keep, Side front, List<Plot> plots, List<Road> roads, List<Cell> wallCorners,
                         List<Gate> gates, int claimRadius) {
    public MasterPlan {
        plots = List.copyOf(plots);
        roads = List.copyOf(roads);
        wallCorners = List.copyOf(wallCorners);
        gates = List.copyOf(gates);
    }

    public enum Kind {
        /** A building of the kingdom's list of buildings. */
        BUILDING,
        /** Kept free for a building that comes later (a house). */
        LATER,
        /** Kept free by the water for a fishing village (the owner's planner notes). */
        WATERSIDE;

        public String lowerName() {
            return name().toLowerCase();
        }
    }

    /**
     * One plot.
     *
     * @param key    the structure id for a building, or a name for a kept-free plot
     * @param outer  whether it lies outside the wall
     * @param facing the build direction (the building's front, with its easel, faces the other way: toward its road)
     * @param easelX where the easel goes
     * @param easelZ where the easel goes
     * @param rect   the ground the plot takes (levelled when it is built), with its margin
     */
    public record Plot(String key, Kind kind, boolean outer, Side facing, int easelX, int easelZ, Rect rect) {
    }

    /**
     * A straight road: a line of cells from {@code from} to {@code to} (same x or same z) and {@code width} wide around
     * it (an odd number).
     */
    public record Road(Cell from, Cell to, int width) {
        public Road {
            if (from.x() != to.x() && from.z() != to.z()) {
                throw new IllegalArgumentException("A road runs straight along x or z: " + from + " to " + to);
            }
            if (width < 1 || width % 2 == 0) {
                throw new IllegalArgumentException("A road's width is an odd number: " + width);
            }
        }

        /** The road's cells. */
        public Set<Cell> cells() {
            Set<Cell> cells = new LinkedHashSet<>();
            int half = width / 2;
            boolean alongX = from.z() == to.z();
            for (Cell cell : RoadGraph.line(from, to)) {
                for (int offset = -half; offset <= half; offset++) {
                    cells.add(alongX ? new Cell(cell.x(), cell.z() + offset) : new Cell(cell.x() + offset, cell.z()));
                }
            }
            return cells;
        }

        public int length() {
            return Math.abs(to.x() - from.x()) + Math.abs(to.z() - from.z()) + 1;
        }
    }

    /** A gate: where a road crosses the wall, on the wall's {@code side}. */
    public record Gate(Cell at, Side side) {
    }

    /** The plots of {@code key} in plan order (the n-th building of a structure goes on the n-th plot). */
    public List<Plot> plotsFor(String key) {
        return plots.stream().filter(plot -> plot.kind() == Kind.BUILDING && plot.key().equals(key)).toList();
    }

    /** The n-th plot of {@code key} (from 0), if the plan has one. */
    public Optional<Plot> plotFor(String key, int n) {
        List<Plot> list = plotsFor(key);
        return n >= 0 && n < list.size() ? Optional.of(list.get(n)) : Optional.empty();
    }

    /** Every road cell. */
    public Set<Cell> roadCells() {
        Set<Cell> cells = new LinkedHashSet<>();
        roads.forEach(road -> cells.addAll(road.cells()));
        return cells;
    }

    public RoadGraph roadGraph() {
        RoadGraph graph = new RoadGraph();
        for (Road road : roads) {
            int half = road.width() / 2;
            boolean alongX = road.from().z() == road.to().z();
            for (int offset = -half; offset <= half; offset++) {
                graph.road(alongX ? new Cell(road.from().x(), road.from().z() + offset) : new Cell(road.from().x() + offset, road.from().z()),
                        alongX ? new Cell(road.to().x(), road.to().z() + offset) : new Cell(road.to().x() + offset, road.to().z()));
            }
        }
        return graph;
    }

    public WallLoop wall() {
        return WallLoop.fromCorners(wallCorners);
    }

    /** The rectangle the wall runs around. */
    public Rect wallRect() {
        int minX = wallCorners.stream().mapToInt(Cell::x).min().orElse(center.x());
        int maxX = wallCorners.stream().mapToInt(Cell::x).max().orElse(center.x());
        int minZ = wallCorners.stream().mapToInt(Cell::z).min().orElse(center.z());
        int maxZ = wallCorners.stream().mapToInt(Cell::z).max().orElse(center.z());
        return new Rect(minX, maxX, minZ, maxZ);
    }

    /** The plan as the layout checker sees it: the keep and every plot as buildings, the roads, the wall. */
    public LayoutPlan layout() {
        List<Footprint> buildings = new ArrayList<>();
        buildings.add(keep.footprint("keep", "keep", true));
        for (int i = 0; i < plots.size(); i++) {
            Plot plot = plots.get(i);
            buildings.add(plot.rect().footprint(plot.key() + " #" + (i + 1), plot.kind().lowerName(), true));
        }
        return new LayoutPlan(buildings, roadGraph(), wall(), center);
    }

    public List<LayoutChecker.Problem> check() {
        return LayoutChecker.check(layout());
    }

    /** Wall cells (gates included) and its length in blocks. */
    public int wallLength() {
        return wall().cells().size();
    }

    /** One line about the plan. */
    public String summary() {
        long buildings = plots.stream().filter(plot -> plot.kind() == Kind.BUILDING).count();
        long later = plots.stream().filter(plot -> plot.kind() != Kind.BUILDING).count();
        Rect wall = wallRect();
        return "%d building plots and %d kept for later, %d blocks of road, a wall of %d blocks (%d by %d) with %d gates, claim %d"
                .formatted(buildings, later, roadCells().size(), wallLength(), wall.sizeX(), wall.sizeZ(), gates.size(), claimRadius);
    }
}
