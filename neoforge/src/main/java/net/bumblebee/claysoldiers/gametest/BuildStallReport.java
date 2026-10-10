package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugEvent;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Explains a builder test that is about to time out: the CI builds sometimes end 1 to 5 blocks short, and the suite log
 * is all there is to go on. Records BUILD and PATHING for the test (so it must have its own batch, the recorder is
 * world-wide), and shortly before the timeout, if the easel isn't finished, writes "CSR stall report" lines to the log:
 * every block still listed (spot, cooldown, request), the chest's contents, every builder (alive, where, carrying,
 * goal), items lying on the ground, and the last recorded events. The CI workflow prints these lines when the suite
 * fails. Changes nothing in the test's outcome.
 */
final class BuildStallReport {
    /** Recorded events written into a report (the newest ones). */
    static final int EVENTS = 150;
    /** Ticks before the timeout at which the report is written. */
    static final int BEFORE_TIMEOUT = 20;

    private BuildStallReport() {
    }

    /**
     * Starts recording and arranges the report. Call at the start of the test; {@code timeoutTicks} is the test's own.
     */
    static void watch(GameTestHelper helper, String what, int timeoutTicks, EaselBlockEntity easel, ChestBlockEntity chest, List<ClaySoldierEntity> builders) {
        DebugLog.reset();
        for (String category : List.of("BUILD", "PATHING")) {
            DebugCategory.byId(category).ifPresent(c -> DebugLog.setEnabled(c, true, helper.getLevel().getServer()));
        }
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) {
                return;
            }
            if (easel.isFinished()) {
                done[0] = true;
                DebugLog.reset();
            } else if (helper.getTick() >= timeoutTicks - BEFORE_TIMEOUT) {
                done[0] = true;
                report(helper, what, easel, chest, builders).forEach(line -> ClaySoldiersCommon.LOGGER.info("CSR stall report {}: {}", what, line));
                DebugLog.reset();
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
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (!stack.isEmpty()) {
                stock.merge(stack.getItem().toString(), stack.getCount(), Integer::sum);
            }
        }
        lines.add("chest " + chest.getBlockPos().toShortString() + (chest.isRemoved() ? " (removed)" : "") + ": " + stock);
        for (ClaySoldierEntity builder : builders) {
            if (builder.isAlive()) {
                lines.add("  builder " + DebugLog.snapshot(List.of(builder)).getFirst());
            } else {
                lines.add("  builder " + DebugLog.shortName(builder) + " is gone (" + builder.getRemovalReason() + ") at "
                        + builder.blockPosition().toShortString() + ", last carried " + builder.getCarriedStack());
            }
        }
        List<ItemEntity> items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(easel.getBlockPos()).inflate(24));
        lines.add(items.size() + " item(s) on the ground within 24 blocks of the easel");
        items.stream().limit(10).forEach(item -> lines.add("  item " + item.getItem() + " at " + item.blockPosition().toShortString()));
        List<DebugEvent> events = DebugLog.lastEvents(EVENTS);
        lines.add("last " + events.size() + " recorded events (BUILD, PATHING), oldest first:");
        events.forEach(event -> lines.add("  " + event.toLine()));
        return lines;
    }
}
