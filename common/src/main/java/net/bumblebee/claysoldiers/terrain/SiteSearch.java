package net.bumblebee.claysoldiers.terrain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Looks for the best keep site near a wanted point (pure data). Stage 2 skeleton: not implemented yet.
 */
public final class SiteSearch {
    private SiteSearch() {
    }

    /**
     * One judged candidate site.
     *
     * @param distance blocks from the wanted point (the larger of the x and z distances)
     */
    public record Candidate(int x, int z, int distance, SiteScore score) {
    }

    /**
     * @param best       the highest-scoring usable candidate (nearest on a tie), if any
     * @param candidates every judged candidate, nearest first
     * @param refusals   how many candidates were refused for each reason
     * @param unknown    candidates that couldn't be judged (not loaded or outside the scan)
     */
    public record Result(Optional<Candidate> best, List<Candidate> candidates, Map<Factor, Integer> refusals, int unknown) {
        public String summary() {
            return "not implemented";
        }
    }

    public static Result search(TerrainScan scan, int wantedX, int wantedZ, SiteRules rules) {
        return new Result(Optional.empty(), List.of(), Map.of(), 0);
    }
}
