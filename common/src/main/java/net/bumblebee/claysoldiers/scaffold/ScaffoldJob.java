package net.bumblebee.claysoldiers.scaffold;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The temporary scaffold of one easel's build: the planned blocks (placing order) and the ones placed so far. Saved
 * in {@link ScaffoldSavedData}, so a save or restart never strands scaffold. Requests out with builders are not saved.
 */
public final class ScaffoldJob {
    private final BlockPos easel;
    private final ResourceLocation material;
    private final List<BlockPos> planned;
    private final Set<BlockPos> placed;
    /** Scaffolding columns builders climb inside (approach e), rather than steps they stand on. */
    private final boolean columns;
    /** Not saved: scaffold requests builders are working on, by position. */
    private final Map<BlockPos, ScaffoldRequest> active = new HashMap<>();
    /** Not saved: game time the teardown last made progress (-1 before it starts). */
    private long lastProgress = -1;

    public ScaffoldJob(BlockPos easel, ResourceLocation material, List<BlockPos> planned, Set<BlockPos> placed) {
        this(easel, material, planned, placed, false);
    }

    public ScaffoldJob(BlockPos easel, ResourceLocation material, List<BlockPos> planned, Set<BlockPos> placed, boolean columns) {
        this.columns = columns;
        this.easel = easel.immutable();
        this.material = material;
        this.planned = new ArrayList<>(planned);
        this.placed = new LinkedHashSet<>(placed);
    }

    public BlockPos easel() {
        return easel;
    }

    /** The scaffold block's id (from {@code scaffold.material} when the job was planned). */
    public ResourceLocation material() {
        return material;
    }

    /** Whether the scaffold is columns builders climb inside (taken down a whole column at a time from its base). */
    public boolean columns() {
        return columns;
    }

    /** The placed cells of the column at {@code cell}'s x and z, lowest first. */
    public List<BlockPos> column(BlockPos cell) {
        return placed.stream().filter(pos -> pos.getX() == cell.getX() && pos.getZ() == cell.getZ())
                .sorted(java.util.Comparator.comparingInt(BlockPos::getY)).toList();
    }

    public List<BlockPos> planned() {
        return Collections.unmodifiableList(planned);
    }

    public Set<BlockPos> placed() {
        return Collections.unmodifiableSet(placed);
    }

    /** Adds more planned scaffold (a re-plan when builders got stuck with all scaffold placed). */
    public void addPlanned(List<BlockPos> more) {
        for (BlockPos pos : more) {
            if (!planned.contains(pos)) {
                planned.add(pos.immutable());
            }
        }
    }

    public void markPlaced(BlockPos pos) {
        placed.add(pos.immutable());
    }

    public void markRemoved(BlockPos pos) {
        placed.remove(pos);
    }

    Map<BlockPos, ScaffoldRequest> active() {
        return active;
    }

    long lastProgress() {
        return lastProgress;
    }

    void setLastProgress(long gameTime) {
        lastProgress = gameTime;
    }

    @Override
    public String toString() {
        return "ScaffoldJob{easel " + easel.toShortString() + ", " + placed.size() + " of " + planned.size() + " placed, " + material + "}";
    }
}
