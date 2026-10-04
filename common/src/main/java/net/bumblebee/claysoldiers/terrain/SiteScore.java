package net.bumblebee.claysoldiers.terrain;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The result of judging a keep site ({@link SiteScorer}): a score from 0 to 100, the hard refusals, the soft penalties
 * and the measured numbers. A site is usable when it is known and has no refusals.
 *
 * @param unknown part of the site isn't loaded, so it wasn't judged (score 0, no refusals)
 */
public record SiteScore(int score, List<Reason> refusals, List<Reason> penalties, Metrics metrics, boolean unknown) {
    public static final int MAX_SCORE = 100;

    public SiteScore {
        refusals = List.copyOf(refusals);
        penalties = List.copyOf(penalties);
    }

    public boolean ok() {
        return !unknown && refusals.isEmpty();
    }

    public boolean refusedFor(Factor factor) {
        return refusals.stream().anyMatch(reason -> reason.factor() == factor);
    }

    public boolean penalizedFor(Factor factor) {
        return penalties.stream().anyMatch(reason -> reason.factor() == factor);
    }

    /**
     * One line for chat and logs: "refused: a cliff (...)" or "score 82: slope 40 % (-8), ...".
     */
    public String describe() {
        if (unknown) {
            return "unknown: " + metrics.unknownColumns() + " of " + metrics.columns() + " columns aren't loaded";
        }
        if (!refusals.isEmpty()) {
            return "refused: " + refusals.stream().map(Reason::text).collect(Collectors.joining("; "));
        }
        return "score " + score + (penalties.isEmpty() ? ", no penalties"
                : ": " + penalties.stream().map(reason -> reason.text() + " (-" + reason.points() + ")").collect(Collectors.joining(", ")));
    }

    /**
     * Why a site was refused or lost points.
     *
     * @param points penalty points (0 for refusals)
     * @param text   for people, e.g. "a cliff (a 6-block step; soldiers hop 1)"
     */
    public record Reason(Factor factor, int points, String text) {
    }

    /**
     * What was measured over the site's columns (the keep plus its margin; lava over a wider ring).
     *
     * @param slopePercent average height change per block along the steeper axis, in percent (100 = 1 up per block)
     * @param nearestLava  distance in blocks from the site to the nearest lava within the lava ring, or -1
     * @param maxStep      largest height step between neighbouring columns
     * @param stepPercent  share of columns next to a step of 2 or more that isn't a cliff yet
     */
    public record Metrics(int columns, int unknownColumns, int minHeight, int maxHeight, int slopePercent, int waterPercent,
                          int maxWaterDepth, int nearestLava, int treePercent, int maxStep, int stepPercent, BiomeKind biome) {
        public static final Metrics NONE = new Metrics(0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, BiomeKind.NORMAL);

        public int heightRange() {
            return maxHeight - minHeight;
        }
    }
}
