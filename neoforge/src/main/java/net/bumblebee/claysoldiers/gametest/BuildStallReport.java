package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugEvent;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.scaffold.ScaffoldClimber;
import net.bumblebee.claysoldiers.scaffold.ScaffoldSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Explains a builder test that is about to time out: the CI builds sometimes end 1 to 5 blocks short, and the suite log
 * is all there is to go on. Records BUILD, PATHING and SCAFFOLD for the test unless it already records (so it must have its own
 * batch, the recorder is world-wide), and shortly before the timeout, if the easel isn't finished, writes "CSR stall report" lines to the log:
 * every block still listed (spot, cooldown, request), the chest's contents, every builder (alive, where, carrying,
 * goal, whether it stands or floats, its route, the blocks around it), items lying on the ground, and the last recorded events. The CI workflow prints these lines when the suite
 * fails. Changes nothing in the test's outcome.
 */
final class BuildStallReport {
    /** Recorded events written into a report (the newest ones that aren't PATHING). */
    static final int EVENTS = 150;
    /** PATHING events written into a report (the newest ones). */
    static final int PATHING_EVENTS = 30;
    /** Ticks before the timeout at which the report is written. */
    static final int BEFORE_TIMEOUT = 20;

    private BuildStallReport() {
    }

    /**
     * Starts recording and arranges the report. Call at the start of the test; {@code timeoutTicks} is the test's own.
     */
    static void watch(GameTestHelper helper, String what, int timeoutTicks, EaselBlockEntity easel, ChestBlockEntity chest, List<ClaySoldierEntity> builders) {
        watch(helper, what, timeoutTicks, easel, chest, builders, () -> {
        });
    }

    /**
     * As above; {@code whenFinished} runs on the tick the easel is found finished, before the recorder is cleared (tick
     * handlers run in no fixed order, so another handler can't read the recorder then).
     */
    static void watch(GameTestHelper helper, String what, int timeoutTicks, EaselBlockEntity easel, ChestBlockEntity chest, List<ClaySoldierEntity> builders,
                      Runnable whenFinished) {
        // A test that records for its own checks keeps its recorder as it set it up; the report then shows its events.
        boolean ownRecorder = !DebugLog.isRecording();
        if (ownRecorder) {
            DebugLog.reset();
            for (String category : List.of("BUILD", "PATHING", "SCAFFOLD", "DAMAGE")) {
                DebugCategory.byId(category).ifPresent(c -> DebugLog.setEnabled(c, true, helper.getLevel().getServer()));
            }
        }
        boolean[] done = {false};
        EveryTick.run(helper, () -> {
            if (done[0]) {
                return;
            }
            if (easel.isFinished()) {
                done[0] = true;
                whenFinished.run();
                if (ownRecorder) {
                    DebugLog.reset();
                }
            } else if (helper.getTick() >= timeoutTicks - BEFORE_TIMEOUT) {
                done[0] = true;
                report(helper, what, easel, chest, builders).forEach(line -> ClaySoldiersCommon.LOGGER.info("CSR stall report {}: {}", what, line));
                if (ownRecorder) {
                    DebugLog.reset();
                }
            }
        });
    }

    static List<String> report(GameTestHelper helper, String what, EaselBlockEntity easel, ChestBlockEntity chest, List<ClaySoldierEntity> builders) {
        List<String> lines = new ArrayList<>();
        lines.add("not finished at test tick " + helper.getTick() + " (game time " + helper.getLevel().getGameTime() + ")");
        if (easel.getPlan() instanceof ServerBlueprintPlan plan) {
            BlockPos base = EaselBlockEntity.templateBase(easel.getBlockPos(), easel.getFacing(), easel.getTemplateSettings());
            List<String> remaining = plan.describeRemaining(helper.getLevel(), base, easel.getTemplateSettings());
            lines.add(remaining.size() + " block(s) still listed, still needed: " + plan.getNeededItems());
            remaining.stream().limit(20).forEach(line -> lines.add("  block " + line));
        } else {
            lines.add("easel has no server plan: " + easel.getPlan());
        }
        Map<String, Integer> stock = new TreeMap<>();
        int used = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty()) {
                used++;
                stock.merge(stack.getItem().toString(), stack.getCount(), Integer::sum);
            }
        }
        lines.add("chest " + chest.getBlockPos().toShortString() + (chest.isRemoved() ? " (removed)" : "") + ", " + used + " of "
                + chest.getContainerSize() + " slots used: " + stock);
        ScaffoldSavedData.get(helper.getLevel()).job(easel.getBlockPos()).ifPresent(job -> {
            lines.add("scaffold job: " + job.placed().size() + " of " + job.planned().size() + " planned blocks up, columns " + job.columns());
            if (job.columns()) {
                ClaySoldierEntity walker = builders.stream().filter(ClaySoldierEntity::isAlive).findFirst().orElse(null);
                job.placed().stream().filter(pos -> job.column(pos).getFirst().equals(pos)).limit(12)
                        .forEach(base -> lines.add("  column from " + base.toShortString() + ", " + job.column(base).size() + " up" + missing(helper, job.column(base))
                                + ": " + pathTo(walker, base)));
            }
        });
        for (ClaySoldierEntity builder : builders) {
            if (builder.isAlive()) {
                lines.add("  builder " + DebugLog.snapshot(List.of(builder)).getFirst());
                lines.add("    " + physics(builder));
                lines.add("    " + surroundings(builder));
            } else {
                lines.add("  builder " + DebugLog.shortName(builder) + " is gone (" + builder.getRemovalReason() + ") at "
                        + builder.blockPosition().toShortString() + ", last carried " + builder.getCarriedStack());
            }
        }
        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(easel.getBlockPos()).inflate(24));
        lines.add(items.size() + " item(s) on the ground within 24 blocks of the easel");
        items.stream().limit(10).forEach(item -> lines.add("  item " + item.getItem() + " at " + item.blockPosition().toShortString()));
        // PATHING is recorded every tick a builder searches, so it would fill the whole tail: it gets its own, shorter one.
        List<DebugEvent> all = DebugLog.events();
        List<DebugEvent> events = tail(all, event -> event.category() != DebugCategory.PATHING, EVENTS);
        lines.add("last " + events.size() + " recorded events (PATHING left out), oldest first:");
        events.forEach(event -> lines.add("  " + event.toLine()));
        List<DebugEvent> pathing = tail(all, event -> event.category() == DebugCategory.PATHING, PATHING_EVENTS);
        lines.add("last " + pathing.size() + " PATHING events, oldest first:");
        pathing.forEach(event -> lines.add("  " + event.toLine()));
        return lines;
    }

    /** Cells of a column the job counts as placed that aren't scaffolding in the world (it fell, D77). */
    private static String missing(GameTestHelper helper, List<BlockPos> column) {
        long gone = column.stream().filter(cell -> !(helper.getLevel().getBlockState(cell).getBlock() instanceof ScaffoldingBlock)).count();
        return gone == 0 ? "" : " (" + gone + " not scaffolding in the world)";
    }

    /** Whether {@code builder} finds a path to a scaffold column's base from where it stands (what the climber needs). */
    private static String pathTo(ClaySoldierEntity builder, BlockPos base) {
        if (builder == null) {
            return "no builder alive";
        }
        if (ScaffoldClimber.pathToBase(builder, base) != null) {
            return "path reaches it from " + builder.blockPosition().toShortString();
        }
        Path path = builder.getNavigation().createPath(base, 1);
        if (path == null) {
            return "no path from " + builder.blockPosition().toShortString();
        }
        return (path.canReach() ? "path reaches it" : "path ends at " + (path.getEndNode() == null ? "-" : path.getEndNode().asBlockPos().toShortString()))
                + " from " + builder.blockPosition().toShortString();
    }

    private static List<DebugEvent> tail(List<DebugEvent> events, Predicate<DebugEvent> filter, int count) {
        List<DebugEvent> matching = events.stream().filter(filter).toList();
        return matching.subList(Math.max(0, matching.size() - count), matching.size());
    }

    /** Whether the builder stands, floats or falls, and where its navigation is going. */
    private static String physics(ClaySoldierEntity builder) {
        PathNavigation navigation = builder.getNavigation();
        Path path = navigation.getPath();
        String route = path == null ? "no path" : "path of " + path.getNodeCount() + " nodes to " + path.getTarget().toShortString()
                + (path.canReach() ? "" : " (incomplete)") + ", at node " + path.getNextNodeIndex() + (navigation.isDone() ? ", done" : "")
                + (navigation.isStuck() ? ", stuck" : "");
        return "onGround %s | noGravity %s | fallDistance %.1f | motion %s | %s".formatted(builder.onGround(), builder.isNoGravity(),
                builder.fallDistance, builder.getDeltaMovement(), route);
    }

    /** The blocks around a builder: what it stands on and in, and what is beside it (what boxes it in). */
    private static String surroundings(ClaySoldierEntity builder) {
        Level level = builder.level();
        BlockPos feet = builder.blockPosition();
        StringBuilder text = new StringBuilder("stands on ").append(name(level, feet.below())).append(", in ").append(name(level, feet))
                .append(", head ").append(name(level, feet.above())).append(" | beside:");
        for (Direction side : Direction.Plane.HORIZONTAL) {
            text.append(' ').append(side.getName().charAt(0)).append('=').append(name(level, feet.relative(side)))
                    .append('/').append(name(level, feet.relative(side).above()));
        }
        return text.toString();
    }

    private static String name(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() ? "air" : BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }
}
