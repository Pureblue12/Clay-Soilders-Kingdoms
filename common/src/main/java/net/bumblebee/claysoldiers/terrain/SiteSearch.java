package net.bumblebee.claysoldiers.terrain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Looks for the best keep site near a wanted point (pure data, from a {@link TerrainScan}). Candidates lie on square
 * rings around the wanted point, {@code terrain.search.step} blocks apart, out to {@code terrain.search.radius}, nearest
 * ring first, at most {@code terrain.search.max_candidates}. The best is the usable candidate with the highest score;
 * on a tie, the nearest.
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
            String refused = refusals.isEmpty() ? "" : "; refused: " + refusals.entrySet().stream()
                    .map(entry -> entry.getKey().label() + " " + entry.getValue()).collect(Collectors.joining(", "));
            String notLoaded = unknown == 0 ? "" : "; not loaded " + unknown;
            if (best.isPresent()) {
                Candidate site = best.get();
                return "best site " + site.x() + ", " + site.z() + " (score " + site.score().score() + ", " + site.distance()
                        + " blocks away) of " + candidates.size() + " candidates" + refused + notLoaded;
            }
            return "no usable site among " + candidates.size() + " candidates" + refused + notLoaded;
        }
    }

    public static Result search(TerrainScan scan, int wantedX, int wantedZ, SiteRules rules) {
        List<Candidate> candidates = new ArrayList<>();
        int step = Math.max(1, rules.searchStep());
        for (int ring = 0; ring * step <= rules.searchRadius() && candidates.size() < rules.searchMaxCandidates(); ring++) {
            for (int i = -ring; i <= ring && candidates.size() < rules.searchMaxCandidates(); i++) {
                for (int j = -ring; j <= ring && candidates.size() < rules.searchMaxCandidates(); j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
                        continue;
                    }
                    int x = wantedX + i * step;
                    int z = wantedZ + j * step;
                    candidates.add(new Candidate(x, z, ring * step, SiteScorer.score(scan, x, z, rules)));
                }
            }
        }
        Optional<Candidate> best = Optional.empty();
        Map<Factor, Integer> refusals = new EnumMap<>(Factor.class);
        int unknown = 0;
        for (Candidate candidate : candidates) {
            SiteScore score = candidate.score();
            if (score.unknown()) {
                unknown++;
            } else if (!score.ok()) {
                Set<Factor> factors = new LinkedHashSet<>();
                score.refusals().forEach(reason -> factors.add(reason.factor()));
                factors.forEach(factor -> refusals.merge(factor, 1, Integer::sum));
            } else if (best.isEmpty() || score.score() > best.get().score().score()) {
                best = Optional.of(candidate);
            }
        }
        return new Result(best, List.copyOf(candidates), refusals, unknown);
    }
}
