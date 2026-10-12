package net.bumblebee.claysoldiers.entity.door;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A path search that goes on from where the previous one ran out of steps (D76).
 * <p>
 * Vanilla's search looks at a fixed number of steps and, when they run out, returns the path to the cell closest to the
 * target. Around a tall building most steps go to the open ground: the keep's upper floor is straight above its
 * middle floor, but the only way up winds through two staircases, so every search from the chest ended on the middle
 * floor, 3 blocks below the scaffold columns there (structure build run 38178408665). A second search started from that
 * cell is short and finds the way. Here, while a search ends short and closer to the target than the one before, the
 * next one starts where it ended, up to {@link #MAX_SEARCHES} searches; their paths are joined into one.
 */
public final class ChainedPath {
    /** Most searches one path may take: a target nobody can reach costs at most this many full searches. */
    public static final int MAX_SEARCHES = 3;

    private ChainedPath() {
    }

    /**
     * A path from where {@code mob} stands to one of {@code targets}, with the navigation's own rules (its node
     * evaluator). Without a {@link DoorPathing.StartAt} evaluator it is a single search.
     *
     * @param range    the longest walked length of each search
     * @param nodes    the most steps each search may look at
     * @param accuracy how close (Manhattan) the path's end must come to a target to reach it
     */
    @Nullable
    public static Path find(Mob mob, Set<BlockPos> targets, float range, int nodes, int accuracy) {
        NodeEvaluator evaluator = mob.getNavigation().getNodeEvaluator();
        Path path = search(mob, evaluator, null, targets, range, nodes, accuracy);
        if (path == null || path.canReach() || !(evaluator instanceof DoorPathing.StartAt)) {
            return path;
        }
        List<Node> joined = nodesOf(path);
        for (int searches = 1; searches < MAX_SEARCHES && !path.canReach(); searches++) {
            Node end = path.getEndNode();
            if (end == null || path.getNodeCount() < 2) {
                break;
            }
            Path next = search(mob, evaluator, end.asBlockPos(), targets, range, nodes, accuracy);
            if (next == null || next.getNodeCount() < 2 || !(next.getDistToTarget() < path.getDistToTarget())) {
                break;
            }
            List<Node> more = nodesOf(next);
            joined.addAll(more.subList(1, more.size()));
            path = new Path(new ArrayList<>(joined), next.getTarget(), next.canReach());
        }
        return path;
    }

    @Nullable
    private static Path search(Mob mob, NodeEvaluator evaluator, @Nullable BlockPos from, Set<BlockPos> targets, float range,
                               int nodes, int accuracy) {
        BlockPos center = from != null ? from : mob.blockPosition();
        int region = (int) range + 8;
        PathNavigationRegion area = new PathNavigationRegion(mob.level(), center.offset(-region, -region, -region), center.offset(region, region, region));
        if (from != null) {
            ((DoorPathing.StartAt) evaluator).startAt(from);
        }
        try {
            return new PathFinder(evaluator, nodes).findPath(area, mob, targets, range, accuracy, 1.0F);
        } finally {
            if (from != null) {
                ((DoorPathing.StartAt) evaluator).startAt(null);
            }
        }
    }

    private static List<Node> nodesOf(Path path) {
        List<Node> nodes = new ArrayList<>(path.getNodeCount());
        for (int i = 0; i < path.getNodeCount(); i++) {
            nodes.add(path.getNode(i));
        }
        return nodes;
    }
}
