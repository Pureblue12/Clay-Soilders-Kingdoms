package net.bumblebee.claysoldiers.terrain;

import java.util.List;

/**
 * Judges a keep site from a {@link TerrainScan} (pure data, no world access). Stage 2 skeleton: not implemented yet.
 */
public final class SiteScorer {
    private SiteScorer() {
    }

    /**
     * Judges the site centered on column ({@code x}, {@code z}).
     */
    public static SiteScore score(TerrainScan scan, int x, int z, SiteRules rules) {
        return new SiteScore(0, List.of(), List.of(), SiteScore.Metrics.NONE, true);
    }
}
