package net.bumblebee.claysoldiers.kingdom.layout;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Where a fishing dock goes (pure): its house on dry bank, its jetty out over a real lake ({@link Lake}). The kingdom
 * levels the bank under the house and drives the jetty's pilings down to the lake bed ({@code KingdomDock}); the water
 * stays.
 * <p>
 * A candidate is an easel column and one of the dock's {@link Footprint}s (one per build direction). It is usable when
 * every rule holds:
 * <ul>
 *     <li>the house, easel and chest columns are loaded dry land, no more than {@link Rules#maxRange} above or below the
 *     water's surface (they are levelled to it);</li>
 *     <li>every jetty column is water with the same surface, at least {@link Rules#minDepth} and at most
 *     {@link Rules#maxDepth} deep (the pilings reach the bed);</li>
 *     <li>the columns ahead of the jetty's end are open water with that surface;</li>
 *     <li>no lava within {@link Rules#lavaReach} of the dock;</li>
 *     <li>the jetty's water is a lake of at least {@link Rules#minLake} still water columns;</li>
 *     <li>nothing else (plots, other kingdoms) is on its columns (the caller's {@code blocked}).</li>
 * </ul>
 * The best is the one with the least earth to move plus {@link #DISTANCE_COST} per block from the search's center.
 */
public final class DockSite {
    static final int DISTANCE_COST = 2;

    private DockSite() {
    }

    /** The land and water as the search sees it. */
    public interface Land extends Lake.Water {
        /** The height of the top block that isn't water, a log or leaves (the lake's bed under water). */
        int ground(int x, int z);

        boolean lava(int x, int z);
    }

    /**
     * The dock for one build direction, relative to its easel.
     *
     * @param index  which of the caller's build directions this is
     * @param land   the columns that must be dry bank: the house, the easel, the chest, where builders gather
     * @param shore  columns that may be either (where the jetty leaves the bank)
     * @param water  the jetty's columns over the lake
     * @param ahead  open water wanted past the jetty's end
     */
    public record Footprint(int index, List<Cell> land, List<Cell> shore, List<Cell> water, List<Cell> ahead) {
    }

    /**
     * @param minLake   still water columns a lake needs at least
     * @param lakeLimit how many columns of a lake are counted at most (a bigger lake isn't counted to its end)
     * @param minDepth  water under the jetty is at least this deep
     * @param maxDepth  and at most this deep (the pilings reach the bed)
     * @param maxRange  how far the bank under the house may be above or below the water's surface
     * @param lavaReach no lava this close to the dock
     * @param radius    how far from the center the easel may be
     * @param step      distance between candidate easel columns
     */
    public record Rules(int minLake, int lakeLimit, int minDepth, int maxDepth, int maxRange, int lavaReach, int radius, int step) {
    }

    /**
     * A usable site.
     *
     * @param surface the height of the water's top: the bank is levelled to it, the easel and the deck stand one above
     * @param earth   blocks of bank to cut or fill
     */
    public record Choice(int index, int easelX, int easelZ, int surface, int earth, int distance, Lake.Measure lake) {
        public int score() {
            return earth + DISTANCE_COST * distance;
        }
    }

    /**
     * The search's result: the best site, and counts of why the others weren't usable.
     *
     * @param dry     candidates whose jetty wasn't over water deep enough (or too deep), or no open water ahead
     * @param bank    candidates whose house wasn't on dry bank near the water's height
     * @param puddles candidates whose water was too small a lake
     */
    public record Result(Optional<Choice> best, int judged, int unknown, int dry, int bank, int puddles, int blocked,
                         @Nullable Lake.Measure biggestPuddle) {
        public String summary(Rules rules) {
            if (best.isPresent()) {
                Choice choice = best.get();
                return "site: easel at %d, %d (build direction %d), water surface %d, %d blocks of bank to move, %s"
                        .formatted(choice.easelX(), choice.easelZ(), choice.index(), choice.surface(), choice.earth(),
                                choice.lake().describe(rules.minLake()));
            }
            return "no lake for a dock: %d candidates judged; %d not loaded, %d without deep enough open water for the jetty, %d without dry bank for the house, %d on a puddle or pond%s, %d taken"
                    .formatted(judged, unknown, dry, bank, puddles,
                            biggestPuddle == null ? "" : " (the biggest: " + biggestPuddle.size() + " still water columns, needs " + rules.minLake() + ")",
                            blocked);
        }
    }

    /** Searches easel columns in a square of {@link Rules#radius} around ({@code centerX}, {@code centerZ}). */
    public static Result search(Land land, int centerX, int centerZ, List<Footprint> footprints, Predicate<Cell> blocked, Rules rules) {
        Map<Cell, Lake.Measure> lakes = new HashMap<>();
        Choice best = null;
        int judged = 0;
        int unknown = 0;
        int dry = 0;
        int bank = 0;
        int puddles = 0;
        int taken = 0;
        Lake.Measure biggestPuddle = null;
        int step = Math.max(1, rules.step());
        for (int ex = centerX - rules.radius(); ex <= centerX + rules.radius(); ex += step) {
            for (int ez = centerZ - rules.radius(); ez <= centerZ + rules.radius(); ez += step) {
                for (Footprint footprint : footprints) {
                    judged++;
                    Verdict verdict = judge(land, ex, ez, footprint, blocked, rules, lakes);
                    switch (verdict.kind) {
                        case UNKNOWN -> unknown++;
                        case DRY -> dry++;
                        case BANK -> bank++;
                        case BLOCKED -> taken++;
                        case PUDDLE -> {
                            puddles++;
                            if (biggestPuddle == null || verdict.lake.size() > biggestPuddle.size()) {
                                biggestPuddle = verdict.lake;
                            }
                        }
                        case OK -> {
                            int distance = (int) Math.round(Math.sqrt((double) (ex - centerX) * (ex - centerX) + (double) (ez - centerZ) * (ez - centerZ)));
                            Choice choice = new Choice(footprint.index(), ex, ez, verdict.surface, verdict.earth, distance, verdict.lake);
                            if (best == null || choice.score() < best.score()) {
                                best = choice;
                            }
                        }
                    }
                }
            }
        }
        return new Result(Optional.ofNullable(best), judged, unknown, dry, bank, puddles, taken, biggestPuddle);
    }

    /** Judges one candidate (for the dev tool: why the dock can't go exactly here). */
    public static String explain(Land land, int easelX, int easelZ, Footprint footprint, Predicate<Cell> blocked, Rules rules) {
        Verdict verdict = judge(land, easelX, easelZ, footprint, blocked, rules, new HashMap<>());
        return switch (verdict.kind) {
            case OK -> "usable: " + verdict.lake.describe(rules.minLake()) + ", " + verdict.earth + " blocks of bank to move";
            case UNKNOWN -> "part of it isn't loaded";
            case DRY -> "the jetty isn't over open water " + rules.minDepth() + " to " + rules.maxDepth() + " deep";
            case BANK -> "the house isn't on dry bank within " + rules.maxRange() + " of the water's height";
            case PUDDLE -> "the water is " + verdict.lake.describe(rules.minLake());
            case BLOCKED -> "something else is planned or built there";
        };
    }

    private enum Kind { OK, UNKNOWN, DRY, BANK, PUDDLE, BLOCKED }

    private record Verdict(Kind kind, int surface, int earth, @Nullable Lake.Measure lake) {
        static Verdict of(Kind kind) {
            return new Verdict(kind, Lake.NONE, 0, null);
        }
    }

    private static Verdict judge(Land land, int ex, int ez, Footprint footprint, Predicate<Cell> blocked, Rules rules, Map<Cell, Lake.Measure> lakes) {
        // The jetty first: most candidates are on dry land and fail here cheaply.
        int surface = Lake.NONE;
        for (Cell rel : footprint.water()) {
            int x = ex + rel.x();
            int z = ez + rel.z();
            if (!land.known(x, z)) {
                return Verdict.of(Kind.UNKNOWN);
            }
            int top = land.surface(x, z);
            if (top == Lake.NONE || surface != Lake.NONE && top != surface) {
                return Verdict.of(Kind.DRY);
            }
            surface = top;
            int depth = land.depth(x, z);
            if (depth < rules.minDepth() || depth > rules.maxDepth() || land.lava(x, z)) {
                return Verdict.of(Kind.DRY);
            }
        }
        if (surface == Lake.NONE) {
            return Verdict.of(Kind.DRY);
        }
        for (Cell rel : footprint.ahead()) {
            int x = ex + rel.x();
            int z = ez + rel.z();
            if (!land.known(x, z)) {
                return Verdict.of(Kind.UNKNOWN);
            }
            if (land.surface(x, z) != surface) {
                return Verdict.of(Kind.DRY);
            }
        }
        int earth = 0;
        for (Cell rel : footprint.land()) {
            int x = ex + rel.x();
            int z = ez + rel.z();
            if (!land.known(x, z)) {
                return Verdict.of(Kind.UNKNOWN);
            }
            if (land.surface(x, z) != Lake.NONE || land.lava(x, z)) {
                return Verdict.of(Kind.BANK);
            }
            int step = land.ground(x, z) - surface;
            if (Math.abs(step) > rules.maxRange()) {
                return Verdict.of(Kind.BANK);
            }
            earth += Math.abs(step);
        }
        for (Cell rel : footprint.shore()) {
            int x = ex + rel.x();
            int z = ez + rel.z();
            if (!land.known(x, z)) {
                return Verdict.of(Kind.UNKNOWN);
            }
            int top = land.surface(x, z);
            if (top == Lake.NONE ? Math.abs(land.ground(x, z) - surface) > rules.maxRange() : top != surface || land.depth(x, z) > rules.maxDepth()) {
                return Verdict.of(Kind.BANK);
            }
        }
        for (List<Cell> cells : List.of(footprint.land(), footprint.shore(), footprint.water())) {
            for (Cell rel : cells) {
                if (blocked.test(new Cell(ex + rel.x(), ez + rel.z()))) {
                    return Verdict.of(Kind.BLOCKED);
                }
            }
        }
        if (lavaNear(land, ex, ez, footprint, rules.lavaReach())) {
            return Verdict.of(Kind.BANK);
        }
        Cell start = footprint.water().getFirst();
        Cell at = new Cell(ex + start.x(), ez + start.z());
        Lake.Measure lake = lakes.get(at);
        if (lake == null) {
            lake = Lake.measure(land, at.x(), at.z(), rules.lakeLimit());
            // Every column of a lake measured to its end, or past the limit, has the same answer.
            if (!lake.unknown() || lake.isLake(rules.minLake())) {
                lakes.put(at, lake);
            }
        }
        if (!lake.isLake(rules.minLake())) {
            return lake.unknown() ? Verdict.of(Kind.UNKNOWN) : new Verdict(Kind.PUDDLE, surface, earth, lake);
        }
        return new Verdict(Kind.OK, surface, earth, lake);
    }

    private static boolean lavaNear(Land land, int ex, int ez, Footprint footprint, int reach) {
        if (reach <= 0) {
            return false;
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (List<Cell> cells : List.of(footprint.land(), footprint.shore(), footprint.water())) {
            for (Cell rel : cells) {
                minX = Math.min(minX, rel.x());
                maxX = Math.max(maxX, rel.x());
                minZ = Math.min(minZ, rel.z());
                maxZ = Math.max(maxZ, rel.z());
            }
        }
        for (int x = ex + minX - reach; x <= ex + maxX + reach; x++) {
            for (int z = ez + minZ - reach; z <= ez + maxZ + reach; z++) {
                if (land.known(x, z) && land.lava(x, z)) {
                    return true;
                }
            }
        }
        return false;
    }
}
