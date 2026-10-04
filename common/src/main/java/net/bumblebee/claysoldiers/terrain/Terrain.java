package net.bumblebee.claysoldiers.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Terrain in the world: checks keep sites (scan, score, search) and applies grading methods with undo. Stage 2
 * skeleton: not implemented.
 */
public final class Terrain {
    private Terrain() {
    }

    /**
     * A site check: the site's score and, when asked and the site isn't usable, the search for a better one nearby.
     */
    public record SiteCheck(SiteScore score, Optional<SiteSearch.Result> search) {
    }

    public static SiteCheck checkSite(ServerLevel level, BlockPos center, boolean searchIfRefused) {
        return new SiteCheck(new SiteScore(0, java.util.List.of(), java.util.List.of(), SiteScore.Metrics.NONE, true), Optional.empty());
    }

    /**
     * A grading that was applied: the plan, what was actually changed, and the box the undo snapshot covers.
     */
    public record Graded(GradingPlan plan, int placed, int removed, int markers, @Nullable BoundingBox box) {
        public boolean applied() {
            return box != null;
        }
    }

    public static Graded grade(ServerLevel level, BlockPos at, GradingMethod method) {
        return new Graded(GradingPlan.refused(method, at.getX(), at.getZ(), "not implemented"), 0, 0, 0, null);
    }
}
