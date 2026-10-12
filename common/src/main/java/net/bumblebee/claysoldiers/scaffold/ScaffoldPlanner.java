package net.bumblebee.claysoldiers.scaffold;

import net.bumblebee.claysoldiers.entity.goal.workgoal.BuilderReach;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plans temporary scaffolding so builders can reach every block of a structure taller than their reach (pure data,
 * from a {@link ScaffoldInput}).
 * <p>
 * The plan is checked by simulating the build the way builders work: they place the first block in build order they
 * can reach; only when no structure block is reachable do they put up the next scaffold block (lowest first); when the
 * structure is done they take the scaffold down from the top. Structures with nothing above reach (from the ground,
 * {@link #aboveReach}) get no scaffold at all, so their build order is unchanged.
 * <p>
 * Step pillars are 2 by 2 spiral staircases: each step is one block higher than the last and next to it, so soldiers
 * climb them by hopping 1 block at a time (they can't climb ladders or vanilla scaffolding).
 */
public final class ScaffoldPlanner {
    /** The 4 cells of a step pillar, in climbing order. */
    private static final int[][] SPIRAL = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
    /** How many times the planner adds pillars for blocks the simulation still couldn't reach. */
    private static final int ROUNDS = 4;
    /** The same for the inner columns, where a round can also only drop columns no one can walk to (D75). */
    private static final int INNER_ROUNDS = 8;

    private ScaffoldPlanner() {
    }

    /**
     * The result of simulating a build.
     *
     * @param stuck              structure blocks never placed
     * @param scaffoldPlaced     scaffold blocks put up, in order
     * @param teardownComplete   every scaffold block could be removed afterwards (top-down)
     * @param scaffoldEntered    scaffold cells a builder could walk or climb into at some point of the build: a block
     *                           placed from below but never entered (a column base on a roof) is no use (D75)
     */
    public record Simulation(List<BlockPos> stuck, List<BlockPos> scaffoldPlaced, boolean teardownComplete, Set<BlockPos> scaffoldEntered) {
    }

    public static ScaffoldPlan plan(ScaffoldInput input, ScaffoldApproach approach) {
        Set<BlockPos> above = aboveReach(input);
        if (above.isEmpty()) {
            Simulation simulation = simulate(input, List.of());
            return new ScaffoldPlan(approach, List.of(), simulation.stuck().isEmpty(), simulation.stuck(), input.structure().size(),
                    true, "nothing above reach");
        }
        return switch (approach) {
            case OUTSIDE_RAMP -> ramp(input);
            case STEP_PILLARS, CLIMBED_COLUMNS -> pillars(input, approach, above);
            case PLATFORMS -> {
                Set<BlockPos> stuck = new LinkedHashSet<>(simulate(input, List.of()).stuck());
                stuck.retainAll(above);
                yield pillars(input, approach, stuck);
            }
            case VANILLA_SCAFFOLDING -> vanilla(input, above);
        };
    }

    /** Every approach for the same input, for comparison. */
    public static List<ScaffoldPlan> compare(ScaffoldInput input) {
        return Arrays.stream(ScaffoldApproach.values()).map(approach -> plan(input, approach)).toList();
    }

    /**
     * Structure blocks no builder could reach from the ground of the empty site: the ones above reach. A structure
     * without any never gets scaffold.
     */
    public static Set<BlockPos> aboveReach(ScaffoldInput input) {
        ScaffoldGrid grid = emptySite(input);
        BitSet reachable = reachable(grid, input);
        Set<BlockPos> above = new LinkedHashSet<>();
        for (BlockPos block : input.structure()) {
            if (!grid.canWorkOn(reachable, block)) {
                above.add(block);
            }
        }
        return above;
    }

    private static BitSet reachable(ScaffoldGrid grid, ScaffoldInput input) {
        return grid.reachable(input.home(), input.maxPath(), input.columnPath(), null);
    }

    /** The standing spots a column's base could be walked to: the longer limit (D75). */
    private static BitSet walkable(ScaffoldGrid grid, ScaffoldInput input) {
        BitSet walk = new BitSet();
        grid.reachable(input.home(), input.maxPath(), input.columnPath(), walk);
        return walk;
    }

    private static ScaffoldGrid emptySite(ScaffoldInput input) {
        ScaffoldGrid grid = new ScaffoldGrid(input.region());
        grid.setAll(input.solid(), true);
        grid.setOverhead(input.overhead());
        grid.setColumnReachBonus(input.columnReachBonus());
        return grid;
    }

    /**
     * Simulates building {@code input} with {@code scaffold} available (placed in its order when needed), then the
     * teardown.
     */
    public static Simulation simulate(ScaffoldInput input, List<BlockPos> scaffold) {
        return simulate(input, scaffold, false);
    }

    /**
     * Like {@link #simulate(ScaffoldInput, List)}; with {@code columns} the scaffold is climbable columns soldiers are
     * inside, not steps they stand on.
     */
    public static Simulation simulate(ScaffoldInput input, List<BlockPos> scaffold, boolean columns) {
        return simulate(input, scaffold, columns, List.of());
    }

    /**
     * Like {@link #simulate(ScaffoldInput, List, boolean)}; the cells of {@code watch} a builder could walk into at some
     * point of the build are added to {@link Simulation#scaffoldEntered}.
     */
    private static Simulation simulate(ScaffoldInput input, List<BlockPos> scaffold, boolean columns, Collection<BlockPos> watch) {
        ScaffoldGrid grid = emptySite(input);
        List<BlockPos> watching = new ArrayList<>(watch);
        List<BlockPos> structure = new ArrayList<>(input.structure());
        List<BlockPos> pending = new ArrayList<>(scaffold);
        List<BlockPos> placedScaffold = new ArrayList<>();
        Set<BlockPos> entered = new HashSet<>();
        while (!structure.isEmpty()) {
            BitSet walk = new BitSet();
            BitSet reachable = grid.reachable(input.home(), input.maxPath(), input.columnPath(), walk);
            for (BlockPos cell : placedScaffold) {
                if (grid.isReachable(walk, cell.getX(), cell.getY(), cell.getZ())) {
                    entered.add(cell);
                }
            }
            watching.removeIf(cell -> grid.isReachable(walk, cell.getX(), cell.getY(), cell.getZ()) && entered.add(cell));
            BlockPos next = firstWorkable(grid, reachable, structure);
            if (next != null) {
                structure.remove(next);
                grid.set(next, true);
                continue;
            }
            BlockPos step = firstWorkable(grid, reachable, pending);
            if (step == null) {
                break;
            }
            pending.remove(step);
            if (columns) {
                grid.setClimbable(step);
            } else {
                grid.set(step, true);
            }
            placedScaffold.add(step);
        }
        boolean teardown = teardown(grid, input, new ArrayList<>(placedScaffold));
        return new Simulation(List.copyOf(structure), placedScaffold, teardown, entered);
    }

    private static BlockPos firstWorkable(ScaffoldGrid grid, BitSet reachable, List<BlockPos> blocks) {
        for (BlockPos block : blocks) {
            if (!grid.occupied(block) && grid.canWorkOn(reachable, block)) {
                return block;
            }
        }
        return null;
    }

    /** Removes scaffold from the top down; false when the highest remaining block can't be reached. */
    private static boolean teardown(ScaffoldGrid grid, ScaffoldInput input, List<BlockPos> scaffold) {
        while (!scaffold.isEmpty()) {
            int top = scaffold.stream().mapToInt(BlockPos::getY).max().orElseThrow();
            BitSet reachable = reachable(grid, input);
            BlockPos removable = scaffold.stream().filter(pos -> pos.getY() == top && grid.canWorkOn(reachable, pos)).findFirst().orElse(null);
            if (removable == null) {
                return false;
            }
            scaffold.remove(removable);
            grid.clear(removable);
        }
        return true;
    }

    // (b) and (c): step pillars

    /** A place a step pillar could stand: its corner, the y of its first step, and how many steps fit. */
    /**
     * A step pillar (2 by 2 spiral; soldiers stand on the steps) or, with {@code column}, a 1 by 1 scaffolding column
     * (soldiers are inside its cells).
     */
    private record Spot(int x, int z, int base, int maxSteps, boolean column) {
        BlockPos step(int h) {
            if (column) {
                return new BlockPos(x, base + h, z);
            }
            int[] cell = SPIRAL[h % SPIRAL.length];
            return new BlockPos(x + cell[0], base + h, z + cell[1]);
        }

        /** The feet of a soldier on step {@code h} (standing on it, or inside the column cell). */
        BlockPos feet(int h) {
            return column ? step(h) : step(h).above();
        }

        /** Pillars can't share cells; columns also keep a gap, so each base stays easy to walk into. */
        boolean overlaps(Spot other) {
            return Math.abs(x - other.x) <= 1 && Math.abs(z - other.z) <= 1;
        }
    }

    private static ScaffoldPlan pillars(ScaffoldInput input, ScaffoldApproach approach, Set<BlockPos> toCover) {
        boolean columns = approach.columns();
        Map<Spot, Integer> chosen = choosePillars(input, toCover, columns);
        List<BlockPos> scaffold = steps(chosen);
        Simulation simulation = simulate(input, scaffold, columns);
        return new ScaffoldPlan(approach, scaffold, simulation.stuck().isEmpty(), simulation.stuck(), input.structure().size(),
                simulation.teardownComplete(), chosen.size() + (columns ? " column(s)" : " step pillar(s)"));
    }

    /** The pillars (and their top step) that let builders reach {@code toCover}, checked by simulating the build. */
    private static Map<Spot, Integer> choosePillars(ScaffoldInput input, Set<BlockPos> toCover, boolean columns) {
        // Pillars are needed late in the build, so their first step must be reachable with the whole structure standing.
        ScaffoldGrid site = emptySite(input);
        input.structure().forEach(block -> site.set(block, false));
        BitSet ground = columns ? walkable(site, input) : reachable(site, input);
        List<Spot> spots = spots(input, site, ground, columns);
        Map<Spot, Integer> chosen = new HashMap<>(); // spot -> highest step
        Set<BlockPos> uncovered = new LinkedHashSet<>(toCover);
        Set<BlockPos> stuckMatters = aboveReachOrAll(input, toCover);
        uncovered = coverRounds(input, spots, chosen, uncovered, stuckMatters, columns);
        if (columns && !uncovered.isEmpty()) {
            // Columns outside the footprint can't reach everything (a roof more than 2 blocks inside, the chapel's
            // steeple cap): add columns standing on the structure's own floors (D69). Only then, so every structure
            // the outside columns already cover keeps its plan.
            List<Spot> inner = innerSpots(input);
            // Only floors builders get to at some point of the build (without the inner columns): the rest of the roofs
            // and upper floors would only cost rounds (D75).
            Set<BlockPos> walkedTo = simulate(input, steps(chosen), true, inner.stream().map(spot -> spot.step(0)).toList()).scaffoldEntered();
            inner.removeIf(spot -> !walkedTo.contains(spot.step(0)));
            if (!inner.isEmpty()) {
                List<Spot> all = new ArrayList<>(spots);
                all.addAll(inner);
                Set<Spot> before = Set.copyOf(chosen.keySet());
                coverInner(input, all, chosen, before, uncovered, stuckMatters);
                // An inner column the simulated build never puts up or never enters (its base can't be walked to) is dropped.
                Set<BlockPos> used = simulate(input, steps(chosen), true).scaffoldEntered();
                chosen.keySet().removeIf(spot -> !before.contains(spot) && !used.contains(spot.step(0)));
            }
        }
        return chosen;
    }

    /** Adds pillars for the blocks still uncovered, checked by simulating; returns the blocks still stuck. */
    private static Set<BlockPos> coverRounds(ScaffoldInput input, List<Spot> spots, Map<Spot, Integer> chosen, Set<BlockPos> uncovered,
                                             Set<BlockPos> stuckMatters, boolean columns) {
        for (int round = 0; round < ROUNDS && !uncovered.isEmpty(); round++) {
            cover(spots, chosen, uncovered, input.columnReachBonus());
            Set<BlockPos> stuck = new LinkedHashSet<>(simulate(input, steps(chosen), columns).stuck());
            stuck.retainAll(stuckMatters);
            uncovered = stuck;
        }
        return uncovered;
    }

    /**
     * Like {@link #coverRounds} for the inner columns, but a column the simulated build never gets into (its base can't be
     * walked to: a roof beside a chimney, a floor with no stairs) is dropped and never picked again, so the next round
     * tries the next best place (D75). The greedy cover picks the cheapest column first, and that is often such a roof
     * spot right beside the block; without this every round picked it again.
     */
    private static void coverInner(ScaffoldInput input, List<Spot> spots, Map<Spot, Integer> chosen, Set<Spot> outer,
                                   Set<BlockPos> uncovered, Set<BlockPos> stuckMatters) {
        List<Spot> candidates = new ArrayList<>(spots);
        for (int round = 0; round < INNER_ROUNDS && !uncovered.isEmpty(); round++) {
            Set<BlockPos> before = Set.copyOf(uncovered);
            cover(candidates, chosen, uncovered, input.columnReachBonus());
            Simulation simulation = simulate(input, steps(chosen), true);
            Set<BlockPos> used = simulation.scaffoldEntered();
            List<Spot> unused = chosen.keySet().stream().filter(spot -> !outer.contains(spot) && !used.contains(spot.step(0))).toList();
            Set<BlockPos> stuck = new LinkedHashSet<>(simulation.stuck());
            stuck.retainAll(stuckMatters);
            if (stuck.isEmpty() || unused.isEmpty() && stuck.equals(before)) {
                return;
            }
            unused.forEach(chosen::remove);
            candidates.removeAll(unused);
            uncovered = stuck;
        }
    }

    /**
     * Every place a column could stand on one of the structure's own floors or roofs, or on the ground inside or beside
     * it: each free cell with a floor below, rising until the next solid cell. Every floor of every column counts, not
     * only the lowest under a block still to place (D75): a re-plan late in the build lists only the few blocks left, so
     * the cells around a chimney cap or a spire top are the attic floor or a lower roof beside it. Whether builders can
     * walk to a base when they need it is left to the simulation.
     */
    private static List<Spot> innerSpots(ScaffoldInput input) {
        ScaffoldGrid built = emptySite(input);
        input.structure().forEach(block -> built.set(block, true));
        BoundingBox region = input.region();
        int top = input.structure().stream().mapToInt(BlockPos::getY).max().orElse(region.maxY());
        List<Spot> spots = new ArrayList<>();
        for (int x = region.minX() + 1; x < region.maxX() - 1; x++) {
            for (int z = region.minZ() + 1; z < region.maxZ() - 1; z++) {
                for (int base = region.minY() + 1; base <= top; base++) {
                    if (!built.standable(x, base, z) || input.blocked().contains(new BlockPos(x, base, z))
                            || input.noFooting().contains(new BlockPos(x, base - 1, z))) {
                        continue;
                    }
                    Spot probe = new Spot(x, z, base, 0, true);
                    int steps = 0;
                    while (base + steps <= top && free(input, built, probe.step(steps))) {
                        steps++;
                    }
                    if (steps > 1) {
                        spots.add(new Spot(x, z, base, steps, true));
                    }
                    base += steps; // the next floor up starts above this column's top
                }
            }
        }
        return spots;
    }

    private static Set<BlockPos> aboveReachOrAll(ScaffoldInput input, Set<BlockPos> toCover) {
        Set<BlockPos> above = aboveReach(input);
        above.addAll(toCover);
        return above;
    }

    /** Every place a step pillar could stand, with its first step on reachable ground. */
    private static List<Spot> spots(ScaffoldInput input, ScaffoldGrid site, BitSet ground, boolean columns) {
        BoundingBox region = input.region();
        Set<Long> structureColumns = new HashSet<>();
        input.structure().forEach(pos -> structureColumns.add(column(pos.getX(), pos.getZ())));
        int top = input.structure().stream().mapToInt(BlockPos::getY).max().orElse(region.maxY());
        List<Spot> spots = new ArrayList<>();
        for (int x = region.minX() + 1; x < region.maxX() - 1; x++) {
            for (int z = region.minZ() + 1; z < region.maxZ() - 1; z++) {
                if (structureColumns.contains(column(x, z)) || !columns && (structureColumns.contains(column(x + 1, z))
                        || structureColumns.contains(column(x, z + 1)) || structureColumns.contains(column(x + 1, z + 1)))) {
                    continue;
                }
                int base = site.lowestReachable(ground, x, z);
                if (base == Integer.MIN_VALUE || columns && (openSides(site, ground, x, base, z) < 2
                        || input.noFooting().contains(new BlockPos(x, base - 1, z)))) {
                    continue;
                }
                Spot probe = new Spot(x, z, base, 0, columns);
                int steps = 0;
                while (base + steps <= top && free(input, site, probe.step(steps)) && free(input, site, probe.feet(steps))
                        && (columns || free(input, site, probe.feet(steps).above()))) {
                    steps++;
                }
                if (steps > 0) {
                    spots.add(new Spot(x, z, base, steps, columns));
                }
            }
        }
        return spots;
    }

    /** How many sides of a column's base cell have reachable ground to walk in from. */
    private static int openSides(ScaffoldGrid site, BitSet ground, int x, int y, int z) {
        int open = 0;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (site.isReachable(ground, x + d[0], y, z + d[1])) {
                open++;
            }
        }
        return open;
    }

    private static boolean free(ScaffoldInput input, ScaffoldGrid site, BlockPos pos) {
        return site.inside(pos) && !site.solid(pos) && !input.blocked().contains(pos);
    }

    private static long column(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    /**
     * Greedy cover: repeatedly adds the pillar (or raises a chosen one) that reaches the most uncovered blocks per
     * step block, until everything is covered or nothing helps.
     */
    private static void cover(List<Spot> spots, Map<Spot, Integer> chosen, Set<BlockPos> uncovered, int columnReachBonus) {
        while (!uncovered.isEmpty()) {
            Spot best = null;
            int bestHeight = -1;
            double bestRatio = 0;
            List<BlockPos> bestCovered = List.of();
            for (Spot spot : spots) {
                Integer current = chosen.get(spot);
                if (current == null && chosen.keySet().stream().anyMatch(spot::overlaps)) {
                    continue;
                }
                Map<BlockPos, Integer> need = new HashMap<>();
                for (BlockPos block : uncovered) {
                    int h = firstStepReaching(spot, block, columnReachBonus);
                    if (h >= 0) {
                        need.put(block, h);
                    }
                }
                if (need.isEmpty()) {
                    continue;
                }
                int[] heights = need.values().stream().mapToInt(Integer::intValue).distinct().sorted().toArray();
                for (int height : heights) {
                    int cost = current == null ? height + 1 : Math.max(0, height - current);
                    long count = need.values().stream().filter(h -> h <= height).count();
                    double ratio = cost == 0 ? Double.MAX_VALUE : (double) count / cost;
                    if (ratio > bestRatio || ratio == bestRatio && best != null && height < bestHeight) {
                        best = spot;
                        bestHeight = current == null ? height : Math.max(current, height);
                        bestRatio = ratio;
                        bestCovered = need.entrySet().stream().filter(e -> e.getValue() <= height).map(Map.Entry::getKey).toList();
                    }
                }
            }
            if (best == null) {
                return;
            }
            chosen.merge(best, bestHeight, Math::max);
            bestCovered.forEach(uncovered::remove);
        }
    }

    /**
     * The lowest step of {@code spot} a builder standing on it could place {@code block} from, or -1. Inside a column it
     * reaches {@code columnReachBonus} more up (D78).
     */
    private static int firstStepReaching(Spot spot, BlockPos block, int columnReachBonus) {
        int bonus = spot.column() ? columnReachBonus : 0;
        for (int h = 0; h < spot.maxSteps(); h++) {
            BlockPos feet = spot.feet(h);
            if (!feet.equals(block) && BuilderReach.canPlaceFrom(new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5), block, bonus)) {
                return h;
            }
        }
        return -1;
    }

    /** All steps of the chosen pillars, lowest first. */
    private static List<BlockPos> steps(Map<Spot, Integer> chosen) {
        List<BlockPos> steps = new ArrayList<>();
        chosen.forEach((spot, height) -> {
            for (int h = 0; h <= height; h++) {
                steps.add(spot.step(h));
            }
        });
        steps.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return steps;
    }

    // (a): a ramp up the outside

    private static ScaffoldPlan ramp(ScaffoldInput input) {
        BoundingBox box = BoundingBox.encapsulatingPositions(input.structure()).orElseThrow();
        List<int[]> ring = ring(box);
        ScaffoldGrid site = emptySite(input);
        BitSet ground = reachable(site, input);
        int top = box.maxY();
        ScaffoldPlan best = null;
        for (int corner = 0; corner < 4; corner++) {
            int start = corner * ring.size() / 4;
            int[] first = ring.get(start);
            int base = site.lowestReachable(ground, first[0], first[1]);
            if (base == Integer.MIN_VALUE) {
                continue;
            }
            List<BlockPos> steps = new ArrayList<>();
            for (int h = 0; base + h <= top + 1; h++) {
                int[] cell = ring.get((start + h) % ring.size());
                BlockPos step = new BlockPos(cell[0], base + h, cell[1]);
                if (!free(input, site, step) || !free(input, site, step.above()) || !free(input, site, step.above(2))) {
                    break;
                }
                steps.add(step);
            }
            // The fewest steps that let every block be placed (more steps never hurt).
            int low = 0;
            int high = steps.size();
            if (!simulate(input, steps).stuck().isEmpty()) {
                low = high;
            } else {
                while (low < high) {
                    int mid = (low + high) / 2;
                    if (simulate(input, steps.subList(0, mid)).stuck().isEmpty()) {
                        high = mid;
                    } else {
                        low = mid + 1;
                    }
                }
            }
            List<BlockPos> chosen = List.copyOf(steps.subList(0, Math.min(low, steps.size())));
            Simulation simulation = simulate(input, chosen);
            ScaffoldPlan plan = new ScaffoldPlan(ScaffoldApproach.OUTSIDE_RAMP, chosen, simulation.stuck().isEmpty(), simulation.stuck(),
                    input.structure().size(), simulation.teardownComplete(), "ramp from corner " + (corner + 1));
            if (best == null || plan.complete() && (!best.complete() || plan.scaffold().size() < best.scaffold().size())) {
                best = plan;
            }
        }
        return best != null ? best : new ScaffoldPlan(ScaffoldApproach.OUTSIDE_RAMP, List.of(), false, input.structure(),
                input.structure().size(), true, "no ground for a ramp");
    }

    /** The cells 1 block outside a box's footprint, going around it. */
    private static List<int[]> ring(BoundingBox box) {
        List<int[]> ring = new ArrayList<>();
        int minX = box.minX() - 1;
        int maxX = box.maxX() + 1;
        int minZ = box.minZ() - 1;
        int maxZ = box.maxZ() + 1;
        for (int x = minX; x < maxX; x++) ring.add(new int[]{x, minZ});
        for (int z = minZ; z < maxZ; z++) ring.add(new int[]{maxX, z});
        for (int x = maxX; x > minX; x--) ring.add(new int[]{x, maxZ});
        for (int z = maxZ; z > minZ; z--) ring.add(new int[]{minX, z});
        return ring;
    }

    // (d): vanilla scaffolding

    /**
     * Vanilla scaffolding columns where the step pillars would stand, as high as their top step: counted for the
     * comparison only. Soldiers' walking navigation never plans climbing, so they can't use it.
     */
    private static ScaffoldPlan vanilla(ScaffoldInput input, Set<BlockPos> above) {
        List<BlockPos> scaffolding = steps(choosePillars(input, above, true));
        return new ScaffoldPlan(ScaffoldApproach.VANILLA_SCAFFOLDING, scaffolding, false, input.structure(), input.structure().size(),
                false, "the same columns as (e), but soldiers' vanilla navigation can't plan climbing them");
    }
}
