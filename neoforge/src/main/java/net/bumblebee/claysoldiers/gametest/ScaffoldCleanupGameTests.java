package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.scaffold.ScaffoldJob;
import net.bumblebee.claysoldiers.scaffold.ScaffoldSavedData;
import net.bumblebee.claysoldiers.scaffold.Scaffolds;
import net.bumblebee.claysoldiers.scaffold.TestStructures;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;
import static net.bumblebee.claysoldiers.gametest.TerrainScanGameTests.TERRAIN;

/**
 * Scaffold is never stranded: an interrupted build (easel broken, plan cleared, save and reload, easel gone) leaves no
 * scaffold after cleanup; blocks a player put on or in place of scaffold are kept and reported; refunds follow the
 * tuning file; doors still work while scaffold stands. Each asserts the CORRECT behavior; each has its own batch.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class ScaffoldCleanupGameTests {

    static Block material() {
        return BuiltInRegistries.BLOCK.get(Tuning.SCAFFOLD_MATERIAL.get());
    }

    /**
     * A 14-high tower on an easel (no builders), its scaffold planned, and the first {@code count} scaffold blocks
     * placed as a builder would.
     */
    static EaselBlockEntity towerWithScaffold(GameTestHelper helper, int count) {
        ServerLevel level = helper.getLevel();
        EaselBlockEntity easel = DevScenarios.placeEasel(level, helper.absolutePos(new BlockPos(16, 2, 8)), Direction.NORTH,
                TestStructures.blueprint(level, TestStructures.Shape.TOWER, 14));
        helper.assertTrue(Scaffolds.ensureJob(level, easel).isPresent(), "Setup: no scaffold job planned for a 14-high tower");
        for (int i = 0; i < count; i++) {
            helper.assertTrue(Scaffolds.placeNext(level, easel.getBlockPos()), "Setup: scaffold block " + (i + 1) + " not placed");
        }
        return easel;
    }

    static ScaffoldJob job(GameTestHelper helper, EaselBlockEntity easel) {
        return ScaffoldSavedData.get(helper.getLevel()).job(easel.getBlockPos()).orElseThrow();
    }

    static void assertNoScaffold(GameTestHelper helper, List<BlockPos> positions, BlockPos easel, String what) {
        for (BlockPos pos : positions) {
            helper.assertTrue(!helper.getLevel().getBlockState(pos).is(material()), what + ": scaffold left at " + pos.toShortString());
        }
        helper.assertTrue(ScaffoldSavedData.get(helper.getLevel()).job(easel).isEmpty(), what + ": the scaffold job is still known");
    }

    /** Breaking the easel in the middle of a build removes its scaffold at once. */
    @GameTest(template = TERRAIN, batch = "scaffold_easel_broken")
    public static void brokenEaselLeavesNoScaffold(GameTestHelper helper) {
        EaselBlockEntity easel = towerWithScaffold(helper, 3);
        BlockPos easelPos = easel.getBlockPos();
        List<BlockPos> placed = new ArrayList<>(job(helper, easel).placed());
        helper.getLevel().destroyBlock(easelPos, false);
        assertNoScaffold(helper, placed, easelPos, "Easel broken");
        helper.succeed();
    }

    /** Taking the blueprint off the easel (the plan is gone) removes its scaffold too. */
    @GameTest(template = TERRAIN, batch = "scaffold_plan_cleared")
    public static void clearedPlanLeavesNoScaffold(GameTestHelper helper) {
        EaselBlockEntity easel = towerWithScaffold(helper, 3);
        List<BlockPos> placed = new ArrayList<>(job(helper, easel).placed());
        easel.clearBlueprintData();
        Scaffolds.sweep(helper.getLevel().getServer());
        assertNoScaffold(helper, placed, easel.getBlockPos(), "Plan cleared");
        helper.succeed();
    }

    /**
     * Save and reload in the middle of a build: the saved data still knows every placed scaffold block, and after
     * reloading, cleanup removes them all.
     */
    @GameTest(template = TERRAIN, batch = "scaffold_save_reload")
    public static void scaffoldSurvivesSaveAndReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EaselBlockEntity easel = towerWithScaffold(helper, 3);
        ScaffoldSavedData data = ScaffoldSavedData.get(level);
        Set<BlockPos> placed = Set.copyOf(job(helper, easel).placed());
        CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
        ScaffoldSavedData reloaded = ScaffoldSavedData.load(saved, level.registryAccess());
        helper.assertTrue(reloaded.job(easel.getBlockPos()).map(job -> job.placed().equals(placed)).orElse(false),
                "The reloaded data lost scaffold: " + reloaded.job(easel.getBlockPos()) + " vs " + placed);
        level.getDataStorage().set(ScaffoldSavedData.NAME, reloaded);
        Scaffolds.cleanup(level, easel.getBlockPos(), "test: after reload");
        assertNoScaffold(helper, List.copyOf(placed), easel.getBlockPos(), "After reload");
        helper.succeed();
    }

    /** A job whose easel isn't there any more (e.g. gone while the world was closed) is cleaned up by the sweep. */
    @GameTest(template = TERRAIN, batch = "scaffold_sweep")
    public static void sweepRemovesScaffoldOfMissingEasel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos noEasel = helper.absolutePos(new BlockPos(16, 2, 16));
        BlockPos step = helper.absolutePos(new BlockPos(20, 2, 20));
        level.setBlockAndUpdate(step, material().defaultBlockState());
        ScaffoldJob job = new ScaffoldJob(noEasel, Tuning.SCAFFOLD_MATERIAL.get(), List.of(step), Set.of(step));
        ScaffoldSavedData.get(level).put(job);
        Scaffolds.sweep(level.getServer());
        assertNoScaffold(helper, List.of(step), noEasel, "Easel missing");
        helper.succeed();
    }

    /**
     * A block a player put in place of a scaffold block, and one put on top of scaffold, are not deleted by cleanup;
     * the kept block is recorded (SCAFFOLD "kept").
     */
    @GameTest(template = TERRAIN, batch = "scaffold_player_blocks")
    public static void playerBlocksOnScaffoldAreKept(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DebugLog.reset();
        DebugCategory scaffold = DebugCategory.byId("SCAFFOLD").orElseThrow();
        DebugLog.setEnabled(scaffold, true, level.getServer());
        EaselBlockEntity easel = towerWithScaffold(helper, 3);
        List<BlockPos> placed = new ArrayList<>(job(helper, easel).placed());
        BlockPos replaced = placed.get(0);
        BlockPos onTop = placed.get(1).above();
        level.setBlockAndUpdate(replaced, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(onTop, Blocks.GLASS.defaultBlockState());
        Scaffolds.cleanup(level, easel.getBlockPos(), "test");
        boolean kept = DebugLog.contains(scaffold, "kept");
        DebugLog.reset();
        helper.assertTrue(level.getBlockState(replaced).is(Blocks.STONE), "The player's block in place of scaffold was deleted");
        helper.assertTrue(level.getBlockState(onTop).is(Blocks.GLASS), "The player's block on top of scaffold was deleted");
        helper.assertTrue(!level.getBlockState(placed.get(1)).is(material()) && !level.getBlockState(placed.get(2)).is(material()),
                "The rest of the scaffold should be gone");
        helper.assertTrue(kept, "Keeping the player's block wasn't recorded");
        helper.succeed();
    }

    /**
     * Refund is a tuning option: with scaffold.refund 1 cleanup drops the scaffold blocks as items, with 0 it doesn't.
     */
    @GameTest(template = TERRAIN, batch = "scaffold_refund")
    public static void refundFollowsTuning(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(40);
        try {
            EaselBlockEntity easel = towerWithScaffold(helper, 3);
            Scaffolds.cleanup(level, easel.getBlockPos(), "test: refund on");
            int dropped = level.getEntitiesOfClass(ItemEntity.class, area, item -> item.getItem().is(material().asItem()))
                    .stream().mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertTrue(dropped == 3, "With refund on, 3 scaffold blocks should drop, got " + dropped);
            level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);

            GameTestTuning.load("{\"scaffold.refund\": 0}");
            EaselBlockEntity again = towerWithScaffold(helper, 3);
            Scaffolds.cleanup(level, again.getBlockPos(), "test: refund off");
            int none = level.getEntitiesOfClass(ItemEntity.class, area, item -> item.getItem().is(material().asItem())).size();
            helper.assertTrue(none == 0, "With refund off, nothing should drop, got " + none);
        } finally {
            GameTestTuning.restore(level.getServer());
        }
        helper.succeed();
    }

    /**
     * Doors still work while scaffold stands: a builder goes through the closed door to place the slab inside (the door
     * is opened and closed again), then takes down the easel's scaffold outside.
     */
    @GameTest(template = PLATFORM, batch = "scaffold_door", timeoutTicks = 1600)
    public static void doorStillWorksWithScaffold(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DoorGameTests.hutWithDoor(helper, Blocks.OAK_DOOR, false);
        List<ClaySoldierEntity> builders = DoorGameTests.slabInsideWithBuilders(helper, 1, DoorGameTests.INSIDE);
        List<BlockPos> steps = List.of(helper.absolutePos(new BlockPos(11, 2, 2)), helper.absolutePos(new BlockPos(12, 3, 2)),
                helper.absolutePos(new BlockPos(12, 4, 3)));
        steps.forEach(step -> level.setBlockAndUpdate(step, material().defaultBlockState()));
        BlockPos easel = helper.absolutePos(ClaySoldierGameTests.EASEL);
        ScaffoldSavedData.get(level).put(new ScaffoldJob(easel, Tuning.SCAFFOLD_MATERIAL.get(), steps, Set.copyOf(steps)));
        boolean[] wasOpened = {false};
        helper.onEachTick(() -> wasOpened[0] |= DoorGameTests.isOpen(helper));
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE_SLAB, DoorGameTests.INSIDE);
            helper.assertTrue(wasOpened[0], "The door was never opened");
            helper.assertTrue(DoorGameTests.noneInDoorway(helper, builders) && !DoorGameTests.isOpen(helper), "The door wasn't closed again");
            assertNoScaffold(helper, steps, easel, "After the build");
        });
    }
}
