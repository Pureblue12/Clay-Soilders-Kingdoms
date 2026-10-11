package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.bumblebee.claysoldiers.entity.door.DoorPathing;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.structure.StructureMeta;
import net.bumblebee.claysoldiers.structure.StructurePaster;
import net.bumblebee.claysoldiers.structure.StructureRegistry;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * RECORDING (not a bug test yet): keep L2 builders take 5-block falls into the forecourt, at the same spot below the
 * upper front window, every time they walk back from the upper floor to the chest in front of the keep, until they
 * die (structure build runs 38120112603 and 38124453564). Vanilla's drop limit is 3, so the fall should not be on
 * any path. This test rebuilds the situation (the keep pasted, a builder on the upper floor with a build's path
 * range, the chest spot in front) and writes what the pathfinder plans and what the soldier does as
 * "CSR stall report" lines, which CI prints. It succeeds whatever happens; the cause is read from the lines.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class KeepFallGameTests {
    private static final ResourceLocation KEEP = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "keep");
    /** The easel spot the structure build test uses (player at (24, 2, 3) facing south). */
    private static final BlockPos EASEL = new BlockPos(24, 2, 6);
    private static final String WHAT = "keep_fall";

    @GameTest(template = "gametest/structure_build", timeoutTicks = 600, batch = "keep_fall")
    public static void keepUpperFloorReturnIsRecorded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureMeta meta = StructureRegistry.get(level.getServer()).byId(KEEP).orElseThrow();
        StructureTemplate template = StructurePaster.template(level, meta, 2);
        BlockPos at = helper.absolutePos(EASEL);
        StructurePaster.Placement placement = StructurePaster.placement(at, Direction.SOUTH, template.getSize());
        StructurePaster.paste(level, meta, 2, at, Direction.SOUTH);
        BlockPos base = placement.base();
        List<String> lines = new ArrayList<>();
        lines.add("base " + base.toShortString() + " size " + template.getSize());
        // the keep's front window column (local x 15, z 9) and what is outside it
        for (int y = 0; y <= 7; y++) {
            BlockPos inWall = base.offset(15, y, 9);
            BlockPos outside = base.offset(15, y, 8);
            BlockPos court = base.offset(15, y, 7);
            lines.add("local y " + y + ": wall " + level.getBlockState(inWall).getBlock() + " | outside " + level.getBlockState(outside).getBlock()
                    + " | court " + level.getBlockState(court).getBlock());
        }
        DebugLog.reset();
        DebugLog.setEnabled(DebugCategory.PATHING, true, level.getServer());
        DebugLog.setEnabled(DebugCategory.DAMAGE, true, level.getServer());
        // a builder on the upper floor (local y 6, feet), inside; the chest spot in front of the keep
        BlockPos start = base.offset(12, 6, 12);
        BlockPos chest = base.offset(13, 1, -3);
        ClaySoldierEntity soldier = ModEntityTypesHolder.spawn(level, start, DevScenarios.RED_TEAM);
        if (soldier.getNavigation() instanceof DoorPathing.Ground ground) {
            ground.setRangeBonus(Tuning.BUILD_PATH_RANGE_BONUS.get());
        }
        soldier.getNavigation().setMaxVisitedNodesMultiplier(Tuning.BUILD_PATH_NODES_MULTIPLIER.get());
        lines.add("soldier at " + start.toShortString() + " (" + level.getBlockState(start.below()).getBlock() + " below), target " + chest.toShortString()
                + " (" + level.getBlockState(chest.below()).getBlock() + " below)");
        helper.runAfterDelay(5, () -> {
            Path path = soldier.getNavigation().createPath(chest, 0);
            if (path == null) {
                lines.add("path: null");
            } else {
                StringBuilder nodes = new StringBuilder();
                for (int i = 0; i < path.getNodeCount(); i++) {
                    Node node = path.getNode(i);
                    BlockPos local = node.asBlockPos().subtract(base);
                    nodes.append(local.toShortString()).append(' ').append(node.type).append("; ");
                }
                lines.add("path: " + path.getNodeCount() + " nodes, canReach " + path.canReach() + ": " + nodes);
            }
        });
        long[] lastMove = {0};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick >= 10 && tick % 20 == 10 && soldier.isAlive()) {
                soldier.getNavigation().moveTo(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5, 1.0);
                Path path = soldier.getNavigation().getPath();
                lines.add("t=" + tick + " at " + soldier.position() + " local " + soldier.blockPosition().subtract(base).toShortString()
                        + " onGround " + soldier.onGround() + " fall " + soldier.fallDistance + " path " + (path == null || path.getEndNode() == null ? "none"
                        : path.getNodeCount() + " nodes to " + path.getEndNode().asBlockPos().subtract(base).toShortString()
                        + (path.isDone() ? " (done)" : " next " + path.getNextNodePos().subtract(base).toShortString())));
                lastMove[0] = tick;
            }
        });
        helper.runAfterDelay(400, () -> {
            lines.add("end: alive " + soldier.isAlive() + " hp " + soldier.getHealth() + " at " + soldier.blockPosition().subtract(base).toShortString());
            for (String line : DebugLog.toText().split("\n")) {
                if (line.contains("DAMAGE") || line.contains("PATHING")) {
                    lines.add(line);
                }
            }
            lines.forEach(line -> ClaySoldiersCommon.LOGGER.info("CSR stall report {}: {}", WHAT, line));
            helper.succeed();
        });
    }

    @AfterBatch(batch = "keep_fall")
    public static void stopRecording(ServerLevel level) {
        DebugLog.reset();
        StructurePaster.clearUndo();
    }

    /** Spawning without the DoorGameTests helper's relative positions. */
    private static final class ModEntityTypesHolder {
        static ClaySoldierEntity spawn(ServerLevel level, BlockPos pos, ResourceLocation team) {
            ClaySoldierEntity soldier = net.bumblebee.claysoldiers.init.ModEntityTypes.CLAY_SOLDIER_ENTITY.get().create(level);
            soldier.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            level.addFreshEntity(soldier);
            soldier.setClayTeamType(team);
            return soldier;
        }
    }
}
