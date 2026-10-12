package net.bumblebee.claysoldiers.entity.goal.workgoal;

import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * One map per build of every spot its builders can walk to from the easel, with the walked distance and the step that
 * leads there (a Dijkstra flood with the soldiers' own step rules: their navigation's node evaluator, so doors, pits,
 * fence tops and trapdoors count exactly as when they walk). Is a blueprint block reachable? One look at the spots
 * around it, instead of up to three vanilla path searches per block per request (D80).
 * <p>
 * Vanilla's searches stop after a fixed number of steps and then say "no path", also for a block that is reachable
 * (keep L1/L2: the way up is a staircase on the far side), and a failed search always costs its whole budget. The
 * flood has no step budget, only the same walked-length limit as {@link BuilderReach}'s long search, so it never says
 * "unreachable" for a reachable block. It is made again when a block of the build is placed or removed, or when it is
 * older than {@link #MAX_AGE_TICKS} (players and other changes nearby).
 * <p>
 * Off when {@code build.reach_map} is 0: builders then search as before.
 */
public final class ReachMap {
    /** A map this old is made again even when the build didn't change (players, falling blocks, doors). */
    static final int MAX_AGE_TICKS = 100;
    /** Maps not used for this long are dropped (the easel is gone or the build is finished). */
    private static final int FORGET_TICKS = 2400;
    /** Safety cap on the spots of one map (an open field within the walked limit has about 8,000). */
    private static final int MAX_NODES = 40_000;
    /**
     * How long a blueprint block counts as out of reach after builders gave it up {@link #FAILED_WALKS} times while walking
     * to the map's spot for it: the map's step rules can accept a way soldiers don't manage (keep L2's roof slabs, D80).
     * Out of reach, the easel hands out scaffold for it, as without the map.
     */
    static final int DISTRUST_TICKS = 2400;
    /**
     * Give-ups on the map's way to one block before it counts as out of reach. One isn't enough: a builder stranded up
     * high gives its block up once, is brought back to its chest (D72) and then gets there.
     */
    static final int FAILED_WALKS = 2;

    private record Key(ResourceKey<Level> dimension, BlockPos easel) {
    }

    private static final Map<Key, ReachMap> MAPS = new HashMap<>();

    private final Long2FloatOpenHashMap distance = new Long2FloatOpenHashMap();
    private final Long2LongOpenHashMap step = new Long2LongOpenHashMap();
    private final Map<Long, Node> nodes = new HashMap<>();
    /** Blueprint blocks builders couldn't walk to though the map has a spot for them, until the game time stored. */
    private final Long2LongOpenHashMap distrusted = new Long2LongOpenHashMap();
    /** Give-ups on the map's way to a blueprint block since it last counted as out of reach. */
    private final Long2IntOpenHashMap failedWalks = new Long2IntOpenHashMap();
    private long madeAt;
    private long usedAt;
    private boolean stale = true;

    private ReachMap() {
        distance.defaultReturnValue(Float.NaN);
    }

    public static boolean enabled() {
        return Tuning.BUILD_REACH_MAP.get() != 0;
    }

    /**
     * The build at {@code easel} changed near {@code changed} (a block placed or removed, scaffold put up or taken
     * down): its map is made again before it is used next.
     */
    public static void changed(Level level, BlockPos easel) {
        ReachMap map = MAPS.get(new Key(level.dimension(), easel.immutable()));
        if (map != null) {
            map.stale = true;
        }
    }

    /**
     * The map of the build at {@code easel}, made (again) from {@code builder}'s position when needed. Only made from
     * a builder at the easel, where builders ask for blocks: a map made from an upper floor would count spots reachable
     * only from there (a drop is one-way).
     */
    static ReachMap of(PathfinderMob builder, BlockPos easel) {
        Level level = builder.level();
        long now = level.getGameTime();
        MAPS.values().removeIf(map -> now - map.usedAt > FORGET_TICKS);
        ReachMap map = MAPS.computeIfAbsent(new Key(level.dimension(), easel.immutable()), key -> new ReachMap());
        map.usedAt = now;
        if (map.stale || now - map.madeAt > MAX_AGE_TICKS) {
            map.make(builder);
        }
        return map;
    }

    /**
     * Whether {@code builder} can walk to a spot it can place {@code target} from: one of the spots around the target
     * that {@link BuilderReach#canPlaceFrom} accepts is on the map.
     */
    public static boolean canReachToPlace(PathfinderMob builder, BlockPos easel, BlockPos target) {
        ReachMap map = of(builder, easel);
        if (builder.level().getGameTime() < map.distrusted.getOrDefault(target.asLong(), Long.MIN_VALUE)) {
            return false;
        }
        return map.bestSpot(builder.level(), target) != null;
    }

    /**
     * {@code builder} gave up {@code target} while walking to the map's spot for it. After {@link #FAILED_WALKS} such
     * give-ups the block counts as out of reach for {@link #DISTRUST_TICKS}, so the easel can hand out scaffold for it
     * instead of handing the block out again.
     */
    public static void walkFailed(PathfinderMob builder, BlockPos easel, BlockPos target) {
        ReachMap map = MAPS.get(new Key(builder.level().dimension(), easel.immutable()));
        if (map == null) {
            return;
        }
        int failed = map.failedWalks.addTo(target.asLong(), 1) + 1;
        if (failed >= FAILED_WALKS) {
            map.failedWalks.remove(target.asLong());
            map.distrusted.put(target.asLong(), builder.level().getGameTime() + DISTRUST_TICKS);
            DebugLog.event(DebugCategory.BUILD, builder, "couldn't walk to the reach map's spot for %s %s times: out of reach for %s ticks", target, failed, DISTRUST_TICKS);
        } else {
            DebugLog.event(DebugCategory.BUILD, builder, "couldn't walk to the reach map's spot for %s (%s of %s)", target, failed, FAILED_WALKS);
        }
    }

    /**
     * Vanilla's path to the map's nearest spot for {@code target}, for when the old search ({@link
     * BuilderReach#pathToPlace}) found no way (its step budget ran out); cheap because that spot is known to be
     * reachable. {@code null} when it doesn't get there: the builder may then climb a scaffolding column, or walk
     * {@link #routeToPlace}.
     */
    @Nullable
    public static Path pathToMapSpot(PathfinderMob builder, BlockPos easel, BlockPos target) {
        ReachMap map = MAPS.get(new Key(builder.level().dimension(), easel.immutable()));
        BlockPos spot = map == null ? null : map.bestSpot(builder.level(), target);
        if (spot == null) {
            return null;
        }
        Path path = BuilderReach.pathToSpot(builder, spot);
        if (path != null && path.canReach() && path.getEndNode() != null && BuilderReach.canPlaceFrom(Vec3.atBottomCenterOf(path.getEndNode().asBlockPos()), target)) {
            DebugLog.event(DebugCategory.PATHING, builder, "path search for %s found no way, walking to the reach map's spot %s", target, spot);
            return path;
        }
        return null;
    }

    /**
     * The map's own route to its nearest spot for {@code target} (from where the map was made, beside the easel), for a
     * builder no path search got a way for. {@code null} when the map has none.
     */
    @Nullable
    public static Path routeToPlace(PathfinderMob builder, BlockPos easel, BlockPos target) {
        ReachMap map = MAPS.get(new Key(builder.level().dimension(), easel.immutable()));
        BlockPos spot = map == null ? null : map.bestSpot(builder.level(), target);
        Path route = spot == null ? null : map.route(spot);
        if (route != null) {
            DebugLog.event(DebugCategory.PATHING, builder, "no path search found a way to %s, walking the reach map's route to %s (%s steps)", target, spot, route.getNodeCount());
        }
        return route;
    }

    /**
     * The spot on the map nearest (walked) to the easel that {@code target} can be placed from. Never a doorway: a
     * builder standing there keeps the door from being closed.
     */
    @Nullable
    private BlockPos bestSpot(Level level, BlockPos target) {
        BlockPos best = null;
        float bestDistance = Float.MAX_VALUE;
        int range = Mth.floor(BuilderReach.HORIZONTAL_REACH);
        BlockPos.MutableBlockPos feet = new BlockPos.MutableBlockPos();
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                for (int below = -BuilderReach.MAX_PLACE_DEPTH; below <= BuilderReach.MAX_PLACE_HEIGHT; below++) {
                    feet.set(target.getX() + dx, target.getY() - below, target.getZ() + dz);
                    float walked = distance.get(feet.asLong());
                    if (!Float.isNaN(walked) && walked < bestDistance
                            && BuilderReach.canPlaceFrom(new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5), target)
                            && !(level.getBlockState(feet).getBlock() instanceof DoorBlock)) {
                        best = feet.immutable();
                        bestDistance = walked;
                    }
                }
            }
        }
        return best;
    }

    /** The map's route from where it was made to {@code spot}, as a vanilla path. */
    @Nullable
    private Path route(BlockPos spot) {
        List<Node> route = new ArrayList<>();
        long at = spot.asLong();
        while (nodes.containsKey(at) && route.size() <= MAX_NODES) {
            Node known = nodes.get(at);
            Node copy = new Node(known.x, known.y, known.z); // a fresh node: paths change their nodes as they are walked
            copy.type = known.type;
            copy.costMalus = known.costMalus;
            route.add(copy);
            if (!step.containsKey(at)) {
                break;
            }
            at = step.get(at);
        }
        if (route.isEmpty()) {
            return null;
        }
        Collections.reverse(route);
        return new Path(route, spot, true);
    }

    private void make(PathfinderMob builder) {
        long start = System.nanoTime();
        distance.clear();
        step.clear();
        nodes.clear();
        stale = false;
        madeAt = builder.level().getGameTime();
        float range = (float) (builder.getAttributeBaseValue(Attributes.FOLLOW_RANGE) + Tuning.BUILD_PATH_RANGE_BONUS.get());
        BlockPos from = builder.blockPosition();
        int region = (int) range + 8;
        PathNavigationRegion area = new PathNavigationRegion(builder.level(), from.offset(-region, -region, -region), from.offset(region, region, region));
        NodeEvaluator evaluator = builder.getNavigation().getNodeEvaluator();
        evaluator.prepare(area, builder);
        boolean capped = false;
        try {
            Node origin = evaluator.getStart();
            if (origin == null) {
                return;
            }
            Node[] neighbours = new Node[32];
            PriorityQueue<Entry> open = new PriorityQueue<>();
            add(origin, 0.0F, Long.MIN_VALUE, open);
            while (!open.isEmpty()) {
                Entry entry = open.poll();
                if (entry.walked > distance.get(entry.node.asBlockPos().asLong())) {
                    continue; // a shorter way was found after this one was queued
                }
                if (nodes.size() >= MAX_NODES) {
                    capped = true;
                    break;
                }
                int count = evaluator.getNeighbors(neighbours, entry.node);
                for (int i = 0; i < count; i++) {
                    Node next = neighbours[i];
                    float walked = entry.walked + entry.node.distanceTo(next) + Math.max(0.0F, next.costMalus);
                    if (walked >= range) {
                        continue;
                    }
                    float known = distance.get(next.asBlockPos().asLong());
                    if (Float.isNaN(known) || walked < known) {
                        add(next, walked, entry.node.asBlockPos().asLong(), open);
                    }
                }
            }
        } finally {
            evaluator.done();
        }
        boolean wasCapped = capped;
        DebugLog.event(DebugCategory.PATHING, builder, () -> "reach map made from %s: %s spots in %s us%s"
                .formatted(from.toShortString(), distance.size(), (System.nanoTime() - start) / 1000, wasCapped ? " (capped)" : ""));
    }

    private void add(Node node, float walked, long from, PriorityQueue<Entry> open) {
        long key = node.asBlockPos().asLong();
        distance.put(key, walked);
        nodes.put(key, node);
        if (from != Long.MIN_VALUE) {
            step.put(key, from);
        } else {
            step.remove(key);
        }
        open.add(new Entry(node, walked));
    }

    private record Entry(Node node, float walked) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry other) {
            return Float.compare(walked, other.walked);
        }
    }
}
