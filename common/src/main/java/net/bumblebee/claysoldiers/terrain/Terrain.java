package net.bumblebee.claysoldiers.terrain;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.structure.StructurePaster;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Terrain in the world: checks keep sites (scan, score and, if asked, search) and applies grading methods instantly
 * with undo (the dev tool's review; soldiers don't grade yet). Everything is recorded in the TERRAIN category.
 */
public final class Terrain {
    /** Marks the keep's corners for the searching methods (removed by undo). A torch, so it isn't ground. */
    private static final BlockState MARKER = Blocks.TORCH.defaultBlockState();

    private Terrain() {
    }

    /**
     * A site check: the site's score and, when asked and the site isn't usable, the search for a better one nearby.
     */
    public record SiteCheck(SiteScore score, Optional<SiteSearch.Result> search) {
    }

    /**
     * Judges the keep site centered on {@code center}'s column. With {@code searchIfRefused}, a refused site also gets
     * a search for the best site nearby (a bigger scan).
     */
    public static SiteCheck checkSite(ServerLevel level, BlockPos center, boolean searchIfRefused) {
        SiteRules rules = SiteRules.current();
        TerrainScan scan = TerrainScanner.scan(level, center, rules.siteScanRadius());
        SiteScore score = SiteScorer.score(scan, center.getX(), center.getZ(), rules);
        DebugLog.event(DebugCategory.TERRAIN, null, () -> "site at %d, %d: %s".formatted(center.getX(), center.getZ(), score.describe()));
        Optional<SiteSearch.Result> search = Optional.empty();
        if (searchIfRefused && !score.unknown() && !score.ok()) {
            TerrainScan wide = TerrainScanner.scan(level, center, rules.searchScanRadius());
            SiteSearch.Result result = SiteSearch.search(wide, center.getX(), center.getZ(), rules);
            DebugLog.event(DebugCategory.TERRAIN, null, () -> "site search around %d, %d: %s".formatted(center.getX(), center.getZ(), result.summary()));
            search = Optional.of(result);
        }
        return new SiteCheck(score, search);
    }

    /**
     * A grading that was applied: the plan, what was actually changed, and the box the undo snapshot covers.
     *
     * @param placed  blocks set to dirt or stone
     * @param removed blocks (not air, not fluid) removed or replaced
     * @param box     the undo snapshot's box, or null when nothing was applied
     */
    public record Graded(GradingPlan plan, int placed, int removed, int markers, @Nullable BoundingBox box) {
        public boolean applied() {
            return box != null;
        }
    }

    /**
     * Plans {@code method} for the keep site at {@code at} and applies it at once, keeping an undo snapshot
     * ({@code /csr dev terrain undo}, shared with the structure paste undo). Not applied when the plan isn't feasible.
     */
    public static Graded grade(ServerLevel level, BlockPos at, GradingMethod method) {
        SiteRules rules = SiteRules.current();
        TerrainScan scan = TerrainScanner.scan(level, at, method.searches() ? rules.searchScanRadius() : rules.siteScanRadius());
        GradingPlan plan = GradingPlanner.plan(method, scan, at.getX(), at.getZ(), rules);
        if (!plan.feasible()) {
            DebugLog.event(DebugCategory.TERRAIN, null, () -> "grading refused at %d, %d: %s".formatted(at.getX(), at.getZ(), plan.describe()));
            return new Graded(plan, 0, 0, 0, null);
        }
        BoundingBox box = box(plan, at);
        StructurePaster.remember(level, ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "terrain/" + method.getSerializedName()), at, box);
        int placed = 0;
        int removed = 0;
        int markers = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState fill;
        for (GradingPlan.Work work : plan.work()) {
            for (int y = work.clearFrom(); y <= work.clearTo(); y++) {
                BlockState state = level.getBlockState(pos.set(work.x(), y, work.z()));
                if (state.isAir() || work.treesOnly() && !(state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES))) {
                    continue;
                }
                removed += state.getFluidState().isEmpty() ? 1 : 0;
                set(level, pos, Blocks.AIR.defaultBlockState());
            }
            fill = work.fill() == GradingPlan.Fill.STONE ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
            for (int y = work.fillFrom(); y <= work.fillTo(); y++) {
                BlockState state = level.getBlockState(pos.set(work.x(), y, work.z()));
                if (state.is(fill.getBlock())) {
                    continue;
                }
                removed += !state.isAir() && state.getFluidState().isEmpty() ? 1 : 0;
                set(level, pos, fill);
                placed++;
            }
            if (work.markerY() != GradingPlan.NONE && level.getBlockState(pos.set(work.x(), work.markerY(), work.z())).isAir()) {
                set(level, pos, MARKER);
                markers++;
            }
        }
        Graded graded = new Graded(plan, placed, removed, markers, box);
        DebugLog.event(DebugCategory.TERRAIN, null, () -> "graded %s: placed %d, removed %d, markers %d (planned %d placed, %d cut, %d trees/plants)"
                .formatted(plan.describe(), graded.placed(), graded.removed(), graded.markers(), plan.earthPlaced(), plan.earthRemoved(), plan.vegetationRemoved()));
        return graded;
    }

    /** The box covering every block a plan may change. */
    private static BoundingBox box(GradingPlan plan, BlockPos at) {
        int minX = at.getX();
        int maxX = at.getX();
        int minZ = at.getZ();
        int maxZ = at.getZ();
        int minY = at.getY();
        int maxY = at.getY();
        for (GradingPlan.Work work : plan.work()) {
            minX = Math.min(minX, work.x());
            maxX = Math.max(maxX, work.x());
            minZ = Math.min(minZ, work.z());
            maxZ = Math.max(maxZ, work.z());
            if (work.clearFrom() <= work.clearTo()) {
                minY = Math.min(minY, work.clearFrom());
                maxY = Math.max(maxY, work.clearTo());
            }
            if (work.fillFrom() <= work.fillTo()) {
                minY = Math.min(minY, work.fillFrom());
                maxY = Math.max(maxY, work.fillTo());
            }
            if (work.markerY() != GradingPlan.NONE) {
                minY = Math.min(minY, work.markerY());
                maxY = Math.max(maxY, work.markerY());
            }
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    /**
     * Builds a test terrain centered on {@code center} (the base's top block) with an undo snapshot.
     */
    public static BoundingBox makeTestTerrain(ServerLevel level, BlockPos center, TestTerrain.Shape shape) {
        BoundingBox box = TestTerrain.box(center, TestTerrain.RADIUS);
        StructurePaster.remember(level, ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "terrain/test_" + shape.getSerializedName()), center, box);
        TestTerrain.build(level, center, shape, TestTerrain.RADIUS);
        DebugLog.event(DebugCategory.TERRAIN, null, "built test terrain %s at %s", shape.getSerializedName(), center.toShortString());
        return box;
    }
}
