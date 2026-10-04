package net.bumblebee.claysoldiers.kingdom.layout;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks a {@link LayoutPlan}: overlapping footprints, buildings without access to the keep's road network, gaps in
 * the wall. Pure data, no world access; the kingdom planner (stage 4+) will run it before building.
 */
public final class LayoutChecker {
    private LayoutChecker() {
    }

    /**
     * One problem with a layout.
     *
     * @param kind    what is wrong
     * @param message readable description naming the buildings or cells
     * @param where   the cells involved
     */
    public record Problem(Kind kind, String message, List<Cell> where) {
    }

    public enum Kind {
        OVERLAP, NO_ROAD_ACCESS, WALL_GAP
    }

    public static List<Problem> check(LayoutPlan plan) {
        List<Problem> problems = new ArrayList<>();
        checkOverlaps(plan, problems);
        checkRoadAccess(plan, problems);
        if (plan.wall() != null) {
            checkWall(plan, plan.wall(), problems);
        }
        return List.copyOf(problems);
    }

    private static void checkOverlaps(LayoutPlan plan, List<Problem> problems) {
        List<Footprint> buildings = plan.buildings();
        for (int i = 0; i < buildings.size(); i++) {
            for (int j = i + 1; j < buildings.size(); j++) {
                Footprint a = buildings.get(i);
                Footprint b = buildings.get(j);
                if (a.overlaps(b)) {
                    Cell from = new Cell(Math.max(a.minX(), b.minX()), Math.max(a.minZ(), b.minZ()));
                    Cell to = new Cell(Math.min(a.maxX(), b.maxX()), Math.min(a.maxZ(), b.maxZ()));
                    problems.add(new Problem(Kind.OVERLAP, a.id() + " and " + b.id() + " overlap from " + from + " to " + to, List.of(from, to)));
                }
            }
        }
    }

    /**
     * Every building that needs a road must touch a road cell that is connected to the keep's roads (any road when
     * there is no keep). The keep must touch a road too.
     */
    private static void checkRoadAccess(LayoutPlan plan, List<Problem> problems) {
        Set<Cell> roads = plan.roads().cells();
        Optional<Footprint> keep = plan.buildings().stream().filter(building -> "keep".equals(building.role())).findFirst();
        Set<Cell> network = keep.map(k -> connected(roads, k.edgeNeighbors())).orElse(roads);
        for (Footprint building : plan.buildings()) {
            if (!building.needsRoad()) {
                continue;
            }
            boolean access = building.edgeNeighbors().stream().anyMatch(network::contains);
            if (!access) {
                boolean anyRoad = building.edgeNeighbors().stream().anyMatch(roads::contains);
                problems.add(new Problem(Kind.NO_ROAD_ACCESS, building.id() + (anyRoad
                        ? " touches only roads that don't connect to the keep"
                        : " has no road next to it") + " (footprint " + new Cell(building.minX(), building.minZ()) + " to "
                        + new Cell(building.maxX(), building.maxZ()) + ")", List.of(new Cell(building.minX(), building.minZ()))));
            }
        }
    }

    /**
     * The road cells connected (north/south/east/west) to any of {@code starts}.
     */
    private static Set<Cell> connected(Set<Cell> roads, Set<Cell> starts) {
        Set<Cell> reached = new HashSet<>();
        Deque<Cell> queue = new ArrayDeque<>();
        for (Cell start : starts) {
            if (roads.contains(start) && reached.add(start)) {
                queue.add(start);
            }
        }
        while (!queue.isEmpty()) {
            for (Cell next : queue.poll().neighbors()) {
                if (roads.contains(next) && reached.add(next)) {
                    queue.add(next);
                }
            }
        }
        return reached;
    }

    /**
     * Walks (north/south/east/west) from the center over every non-wall cell. Reaching beyond the wall's outer edge
     * means the wall has a gap; the gap is the cell on that walk with wall on both sides.
     */
    private static void checkWall(LayoutPlan plan, WallLoop wall, List<Problem> problems) {
        Set<Cell> cells = wall.cells();
        if (cells.isEmpty()) {
            problems.add(new Problem(Kind.WALL_GAP, "the wall has no cells", List.of()));
            return;
        }
        if (cells.contains(plan.center())) {
            problems.add(new Problem(Kind.WALL_GAP, "the center " + plan.center() + " is on the wall", List.of(plan.center())));
            return;
        }
        int minX = cells.stream().mapToInt(Cell::x).min().orElseThrow();
        int maxX = cells.stream().mapToInt(Cell::x).max().orElseThrow();
        int minZ = cells.stream().mapToInt(Cell::z).min().orElseThrow();
        int maxZ = cells.stream().mapToInt(Cell::z).max().orElseThrow();
        Cell center = plan.center();
        if (center.x() <= minX || center.x() >= maxX || center.z() <= minZ || center.z() >= maxZ) {
            problems.add(new Problem(Kind.WALL_GAP, "the center " + center + " is not inside the wall", List.of(center)));
            return;
        }
        Map<Cell, Cell> cameFrom = new HashMap<>();
        Deque<Cell> queue = new ArrayDeque<>();
        cameFrom.put(center, center);
        queue.add(center);
        Cell escaped = null;
        while (!queue.isEmpty() && escaped == null) {
            Cell cell = queue.poll();
            for (Cell next : cell.neighbors()) {
                if (cells.contains(next) || cameFrom.containsKey(next)) {
                    continue;
                }
                cameFrom.put(next, cell);
                if (next.x() < minX || next.x() > maxX || next.z() < minZ || next.z() > maxZ) {
                    escaped = next;
                    break;
                }
                queue.add(next);
            }
        }
        if (escaped == null) {
            return;
        }
        Cell gap = escaped;
        for (Cell step = escaped; !step.equals(center); step = cameFrom.get(step)) {
            boolean betweenWallsX = cells.contains(new Cell(step.x() - 1, step.z())) && cells.contains(new Cell(step.x() + 1, step.z()));
            boolean betweenWallsZ = cells.contains(new Cell(step.x(), step.z() - 1)) && cells.contains(new Cell(step.x(), step.z() + 1));
            if (betweenWallsX || betweenWallsZ) {
                gap = step;
                break;
            }
        }
        problems.add(new Problem(Kind.WALL_GAP, "the wall is not closed: there is a way out at " + gap, List.of(gap)));
    }
}
