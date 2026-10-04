package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.bumblebee.claysoldiers.entity.goal.workgoal.BuildBlueprintGoal;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.scaffold.ScaffoldJob;
import net.bumblebee.claysoldiers.scaffold.ScaffoldSavedData;
import net.bumblebee.claysoldiers.scaffold.TestStructures;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static net.bumblebee.claysoldiers.gametest.TerrainScanGameTests.TERRAIN;

/**
 * Builders finishing structures taller than their reach with temporary scaffolding, which is gone afterwards. Each
 * asserts the CORRECT behavior. Each test has its own batch (scaffold jobs and the recorder are world-wide state).
 * The tests have sky access: otherwise the framework puts a barrier roof at the top of the 16-high test area. Chests
 * stand within 16 blocks of every block (builders' paths are limited to their follow range, 16).
 * The times are written to the log ("CSR scaffold build").
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class ScaffoldBuildGameTests {
    static final int BUILDERS = 3;
    /** Scaffold material put in the chest (more than any plan within budget needs). */
    static final int SCAFFOLD_STOCK = 128;
    static final int LONG_TIMEOUT = 24_000;

    /**
     * A build scenario in the terrain template: an easel facing north at {@code easelRel}, the structure going south
     * from 2 blocks behind it, a chest with the cobblestone and the scaffold material, and builders.
     */
    record Scenario(EaselBlockEntity easel, ChestBlockEntity chest, List<ClaySoldierEntity> builders, List<BlockPos> blocks, Item scaffold) {
    }

    static Scenario scenario(GameTestHelper helper, TestStructures.Shape shape, int height, BlockPos easelRel, BlockPos chestRel) {
        ServerLevel level = helper.getLevel();
        BlueprintData blueprint = TestStructures.blueprint(level, shape, height);
        int count = TestStructures.blocks(shape, height).size();
        Item scaffold = BuiltInRegistries.BLOCK.get(Tuning.SCAFFOLD_MATERIAL.get()).asItem();
        BlockPos chestPos = helper.absolutePos(chestRel);
        ChestBlockEntity chest = DevScenarios.placeChest(level, chestPos, List.of(new ItemStack(Items.COBBLESTONE, count), new ItemStack(scaffold, SCAFFOLD_STOCK)));
        EaselBlockEntity easel = DevScenarios.placeEasel(level, helper.absolutePos(easelRel), Direction.NORTH, blueprint);
        List<ClaySoldierEntity> builders = new ArrayList<>();
        for (int i = 0; i < BUILDERS; i++) {
            builders.add(DevScenarios.spawnWorker(level, chestPos.east(1 + i), DevScenarios.RED_TEAM, ClaySoldierGameTests.owner(level),
                    BuildBlueprintGoal.class, chestPos));
        }
        var settings = easel.getTemplateSettings();
        BlockPos base = EaselBlockEntity.templateBase(easel.getBlockPos(), easel.getFacing(), settings);
        List<BlockPos> blocks = new ArrayList<>();
        var plan = blueprint.createServerPlan().orElseThrow();
        blocks.addAll(plan.targetPositions(base, settings));
        return new Scenario(easel, chest, builders, blocks, scaffold);
    }

    static long countAt(GameTestHelper helper, List<BlockPos> positions, Block block) {
        return positions.stream().filter(pos -> helper.getLevel().getBlockState(pos).is(block)).count();
    }

    /** Scaffold blocks anywhere within 6 blocks of the structure. */
    static long scaffoldLeft(GameTestHelper helper, Scenario scenario) {
        Block material = BuiltInRegistries.BLOCK.get(Tuning.SCAFFOLD_MATERIAL.get());
        var box = net.minecraft.world.level.levelgen.structure.BoundingBox.encapsulatingPositions(scenario.blocks()).orElseThrow().inflatedBy(6);
        return BlockPos.betweenClosedStream(box).filter(pos -> helper.getLevel().getBlockState(pos).is(material)).count();
    }

    /**
     * Waits until the structure is finished and its scaffold is gone (job forgotten, no scaffold block left, every
     * structure block in place, all scaffold material back in the chest when refunded).
     */
    static void finishesAndCleansUp(GameTestHelper helper, Scenario scenario, String what) {
        finishesAndCleansUp(helper, scenario, what, () -> {
        });
    }

    /**
     * Like {@link #finishesAndCleansUp(GameTestHelper, Scenario, String)}, with {@code alsoCheck} run (and allowed to
     * fail) once everything else holds.
     */
    static void finishesAndCleansUp(GameTestHelper helper, Scenario scenario, String what, Runnable alsoCheck) {
        long[] structureDone = {-1};
        helper.onEachTick(() -> {
            if (structureDone[0] < 0 && scenario.easel().isFinished()) {
                structureDone[0] = helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            long placed = countAt(helper, scenario.blocks(), Blocks.COBBLESTONE);
            helper.assertTrue(scenario.easel().isFinished(), what + " not finished: " + placed + " of " + scenario.blocks().size() + " blocks");
            helper.assertTrue(ScaffoldSavedData.get(helper.getLevel()).job(scenario.easel().getBlockPos()).isEmpty(), what + ": scaffold job still open");
            long left = scaffoldLeft(helper, scenario);
            helper.assertTrue(left == 0, what + ": " + left + " scaffold blocks left");
            helper.assertTrue(placed == scenario.blocks().size(), what + ": only " + placed + " of " + scenario.blocks().size() + " blocks in place");
            if (Tuning.SCAFFOLD_REFUND.get() == 1) {
                int back = scenario.chest().countItem(scenario.scaffold());
                helper.assertTrue(back == SCAFFOLD_STOCK, what + ": " + back + " of " + SCAFFOLD_STOCK + " scaffold blocks back in the chest");
            }
            alsoCheck.run();
            ClaySoldiersCommon.LOGGER.info("CSR scaffold build: {}: {} blocks, structure finished at tick {}, scaffold gone at tick {}",
                    what, scenario.blocks().size(), structureDone[0], helper.getTick());
        });
    }

    /**
     * Acceptance test: builders finish a 20-block tower (3 by 3, hollow) with scaffolding, then the scaffold is gone.
     * While recording BUILD and SCAFFOLD: no builder gives up carrying, and the scaffold is planned, placed and removed.
     */
    @GameTest(template = TERRAIN, batch = "scaffold_tower", timeoutTicks = LONG_TIMEOUT, skyAccess = true)
    public static void builderFinishesTwentyBlockTower(GameTestHelper helper) {
        DebugLog.reset();
        DebugCategory build = DebugCategory.byId("BUILD").orElseThrow();
        DebugCategory scaffold = DebugCategory.byId("SCAFFOLD").orElseThrow();
        DebugLog.setEnabled(build, true, helper.getLevel().getServer());
        DebugLog.setEnabled(scaffold, true, helper.getLevel().getServer());
        Scenario scenario = scenario(helper, TestStructures.Shape.TOWER, 20, new BlockPos(16, 2, 8), new BlockPos(9, 2, 6));
        helper.onEachTick(() -> {
            if (DebugLog.contains(build, "gave up")) {
                DebugLog.reset();
                helper.fail("A builder gave up carrying while building the tower");
            }
        });
        finishesAndCleansUp(helper, scenario, "tower 20", () -> {
            boolean logged = DebugLog.contains(scaffold, "planned") && DebugLog.contains(scaffold, "placed") && DebugLog.contains(scaffold, "removed");
            DebugLog.reset();
            helper.assertTrue(logged, "SCAFFOLD events missing (planned, placed, removed)");
        });
    }

    /** A long wall (16 long, 12 high) finishes, and its scaffold is gone. */
    @GameTest(template = TERRAIN, batch = "scaffold_wall", timeoutTicks = LONG_TIMEOUT, skyAccess = true)
    public static void builderFinishesLongWall(GameTestHelper helper) {
        Scenario scenario = scenario(helper, TestStructures.Shape.WALL, 12, new BlockPos(16, 2, 6), new BlockPos(12, 2, 4));
        finishesAndCleansUp(helper, scenario, "wall 12");
    }

    /** Two 10-high walls meeting at a 14-high corner tower finish, and the scaffold is gone. */
    @GameTest(template = TERRAIN, batch = "scaffold_corner_tower", timeoutTicks = LONG_TIMEOUT, skyAccess = true)
    public static void builderFinishesCornerTowerOnWall(GameTestHelper helper) {
        Scenario scenario = scenario(helper, TestStructures.Shape.WALL_TOWER, 10, new BlockPos(16, 2, 4), new BlockPos(8, 2, 4));
        finishesAndCleansUp(helper, scenario, "corner tower 14 on walls 10");
    }

    /**
     * The full Small House (151 blocks) still finishes with 3 builders, and gets no scaffold: nothing in it is above
     * reach, so its build is unchanged.
     */
    @GameTest(template = TERRAIN, batch = "scaffold_small_house", timeoutTicks = 36_000, skyAccess = true)
    public static void smallHouseStillReachesAllBlocksWithoutScaffold(GameTestHelper helper) {
        DebugLog.reset();
        DebugCategory scaffold = DebugCategory.byId("SCAFFOLD").orElseThrow();
        DebugLog.setEnabled(scaffold, true, helper.getLevel().getServer());
        ServerLevel level = helper.getLevel();
        var result = DevScenarios.buildTest(level, ClaySoldierGameTests.owner(level), helper.absolutePos(new BlockPos(16, 2, 3)), Direction.SOUTH, true);
        EaselBlockEntity easel = (EaselBlockEntity) level.getBlockEntity(result.easel());
        long[] start = {helper.getTick()};
        helper.onEachTick(() -> {
            if (ScaffoldSavedData.get(level).job(result.easel()).isPresent() || DebugLog.contains(scaffold, "planned")) {
                DebugLog.reset();
                helper.fail("The Small House got scaffolding");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(easel != null && easel.isFinished(), "Small House not finished: " + (easel == null ? "no easel" : easel.getRequiredItems()));
            ClaySoldiersCommon.LOGGER.info("CSR scaffold build: Small House 151 blocks, no scaffold, finished at tick {}", helper.getTick() - start[0]);
            DebugLog.reset();
        });
    }

    /**
     * The dev book's "Build test → tower" layout (chest beside the player, easel 3 blocks ahead, builders between):
     * builders finish a 20-block tower and the scaffold is gone. Found in game: builders who had climbed down a column
     * were carried back up it by vanilla climbing and got stuck on top.
     */
    @GameTest(template = TERRAIN, batch = "scaffold_dev_tower", timeoutTicks = LONG_TIMEOUT, skyAccess = true)
    public static void devBookTowerLayoutFinishes(GameTestHelper helper) {
        finishesAndCleansUp(helper, devTower(helper), "dev book tower 20");
    }

    /**
     * A builder that ends up standing inside a scaffolding column, on a level too high to drop down from (vanilla
     * climbing carried it up; it isn't climbing the column itself), comes back down to the ground instead of standing
     * there for good. The column (6 high, beside the tower) is set up directly, so this doesn't depend on the build.
     */
    @GameTest(template = TERRAIN, batch = "scaffold_stranded", timeoutTicks = 1200, skyAccess = true)
    public static void builderStrandedOnColumnClimbsDown(GameTestHelper helper) {
        Scenario scenario = devTower(helper);
        ServerLevel level = helper.getLevel();
        var box = net.minecraft.world.level.levelgen.structure.BoundingBox.encapsulatingPositions(scenario.blocks()).orElseThrow();
        Block material = BuiltInRegistries.BLOCK.get(Tuning.SCAFFOLD_MATERIAL.get());
        List<BlockPos> column = new ArrayList<>();
        for (int h = 0; h < 6; h++) {
            BlockPos cell = new BlockPos(box.maxX() + 2, box.minY() + h, box.minZ());
            level.setBlockAndUpdate(cell, material.defaultBlockState().trySetValue(net.minecraft.world.level.block.ScaffoldingBlock.DISTANCE, 0));
            column.add(cell);
        }
        ScaffoldSavedData.get(level).put(new ScaffoldJob(scenario.easel().getBlockPos(), Tuning.SCAFFOLD_MATERIAL.get(),
                column, new java.util.HashSet<>(column), true));
        // A builder with no task, standing on the column 4 levels up (as found in game).
        ClaySoldierEntity builder = DevScenarios.spawnWorker(level, column.get(4), DevScenarios.RED_TEAM, ClaySoldierGameTests.owner(level),
                BuildBlueprintGoal.class, scenario.chest().getBlockPos());
        int groundY = column.getFirst().getY();
        helper.succeedWhen(() -> helper.assertTrue(builder.blockPosition().getY() == groundY && builder.onGround(),
                "builder still up at y=" + builder.getY() + " (ground " + groundY + ")"));
    }

    /** The dev book's tower 20 build test, with the player standing at (16, 2, 5) facing south. */
    static Scenario devTower(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var result = DevScenarios.scaffoldTest(level, ClaySoldierGameTests.owner(level), helper.absolutePos(new BlockPos(16, 2, 5)), Direction.SOUTH,
                TestStructures.Shape.TOWER, 20, BUILDERS, SCAFFOLD_STOCK);
        EaselBlockEntity easel = (EaselBlockEntity) level.getBlockEntity(result.easel());
        var settings = easel.getTemplateSettings();
        BlockPos base = EaselBlockEntity.templateBase(easel.getBlockPos(), easel.getFacing(), settings);
        List<BlockPos> blocks = new ArrayList<>(TestStructures.blueprint(level, TestStructures.Shape.TOWER, 20).createServerPlan().orElseThrow().targetPositions(base, settings));
        Item scaffold = BuiltInRegistries.BLOCK.get(Tuning.SCAFFOLD_MATERIAL.get()).asItem();
        return new Scenario(easel, (ChestBlockEntity) level.getBlockEntity(result.chest()), result.soldiers(), blocks, scaffold);
    }

    /**
     * Once scaffold is planned, builders put the columns up before working above reach, instead of raising each
     * column one level whenever they run out of reachable blocks (found in game: slow, with pauses). Checked when the
     * first structure block above reach (9 or more above the ground) is placed: every planned scaffold block stands.
     */
    @GameTest(template = TERRAIN, batch = "scaffold_columns_first", timeoutTicks = 12_000, skyAccess = true)
    public static void columnsGoUpBeforeWorkAboveReach(GameTestHelper helper) {
        Scenario scenario = scenario(helper, TestStructures.Shape.WALL, 12, new BlockPos(16, 2, 6), new BlockPos(12, 2, 4));
        ServerLevel level = helper.getLevel();
        int groundY = scenario.blocks().stream().mapToInt(BlockPos::getY).min().orElseThrow();
        List<BlockPos> aboveReach = scenario.blocks().stream().filter(pos -> pos.getY() >= groundY + 9).toList();
        String[] verdict = {null};
        helper.onEachTick(() -> {
            if (verdict[0] == null && countAt(helper, aboveReach, Blocks.COBBLESTONE) > 0) {
                var job = ScaffoldSavedData.get(level).job(scenario.easel().getBlockPos());
                int planned = job.map(j -> j.planned().size()).orElse(0);
                int placed = job.map(j -> j.placed().size()).orElse(0);
                verdict[0] = planned > 0 && placed == planned ? "" : placed + " of " + planned + " scaffold blocks up when work above reach started";
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(verdict[0] != null, "no block above reach placed yet");
            helper.assertTrue(verdict[0].isEmpty(), verdict[0]);
        });
    }
}
