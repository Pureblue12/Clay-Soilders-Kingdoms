package net.bumblebee.claysoldiers.gametest;

import com.mojang.authlib.GameProfile;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.templates.ImmutableTemplate;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.bumblebee.claysoldiers.entity.goal.workgoal.BuildBlueprintGoal;
import net.bumblebee.claysoldiers.entity.goal.workgoal.dig.DigHoleGoal;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Automated in-world tests, run with {@code ./gradlew :neoforge:runGameTestServer}.
 * <p>
 * NeoForge only registers GameTests outside of production (see {@code GameTestHooks.isGametestEnabled}),
 * so this class is never used in a normal player install.
 * <p>
 * All tests use the template {@code data/csr/structure/gametest/platform.nbt}:
 * a 16x8x16 area with a smooth-stone floor (regenerate with {@code tools/make_gametest_platform.py}).
 * <p>
 * Coordinates in tests are relative to the test's structure block, which vanilla places one block
 * <em>below</em> the template corner. So the floor is at relative {@code y = 1} and things stand on {@code y = 2}.
 * <p>
 * Bug tests assert the <b>correct</b> behavior: a failing bug test means the bug is confirmed.
 * Each one names the suspected bug from {@code docs/ANALYSIS.md} it represents.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class ClaySoldierGameTests {
    static final String PLATFORM = "gametest/platform";
    /** Relative y of the smooth-stone floor. */
    static final int FLOOR_Y = 1;
    /** Relative y of the first air layer, where soldiers and blocks are placed. */
    static final int GROUND_Y = 2;

    // Shared layout for the building tests (relative positions).
    static final BlockPos CHEST = new BlockPos(3, GROUND_Y, 3);
    static final BlockPos SOLDIER = new BlockPos(4, GROUND_Y, 3);
    /** The easel faces north, so a blueprint's local (0, 0, z) is built at EASEL + (0, 0, 2 + z). */
    static final BlockPos EASEL = new BlockPos(5, GROUND_Y, 5);

    /**
     * Smoke test: proves the GameTest setup works and the platform template loads.
     */
    @GameTest(template = PLATFORM)
    public static void platformFloorExists(GameTestHelper helper) {
        helper.assertBlockPresent(Blocks.SMOOTH_STONE, new BlockPos(0, FLOOR_Y, 0));
        helper.assertBlockPresent(Blocks.SMOOTH_STONE, new BlockPos(15, FLOOR_Y, 15));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(7, GROUND_Y, 7));
        helper.succeed();
    }

    /**
     * Tooling test: {@code /csr dev scenario build_test} sets up a stocked chest, an easel with a blueprint
     * and 3 working pacifist builders whose POI is the chest. Does not wait for the house to be built.
     */
    @GameTest(template = PLATFORM)
    public static void devScenarioBuildTestSetsUpWorkers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A FakePlayer is not added to the player list, so mods like Curios don't try to sync data to it.
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "csr-test-player"));
        // Stand at the north edge of the platform facing south (yaw 0), so the house fits on the platform.
        BlockPos standAt = helper.absolutePos(new BlockPos(8, GROUND_Y, 2));
        player.moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5, 0f, 0f);

        level.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4), "csr dev scenario build_test");

        // Same layout as DevScenarios.buildTest, facing south: right = west.
        BlockPos chestPos = standAt.relative(Direction.SOUTH, 2).relative(Direction.WEST, 2);
        BlockPos easelPos = standAt.relative(Direction.SOUTH, 3);
        assertBlockAt(helper, Blocks.CHEST, chestPos);
        assertBlockAt(helper, ModBlocks.EASEL_BLOCK.get(), easelPos);
        helper.assertTrue(level.getBlockEntity(easelPos) instanceof EaselBlockEntity easel && easel.hasBlueprintData(),
                "Easel has no blueprint at " + easelPos.toShortString());
        helper.assertTrue(level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest && !chest.isEmpty(),
                "Chest is not stocked at " + chestPos.toShortString());

        List<ClaySoldierEntity> soldiers = level.getEntitiesOfClass(ClaySoldierEntity.class, new AABB(standAt).inflate(6));
        helper.assertTrue(soldiers.size() == 3, "Expected 3 soldiers, found " + soldiers.size());
        for (ClaySoldierEntity soldier : soldiers) {
            helper.assertTrue(soldier.getAttackType().canWork(), "Soldier is not a pacifist: " + DevScenarios.describeWorker(soldier));
            helper.assertTrue(soldier.getWorkSelector().isWorking(), "Soldier has no job: " + DevScenarios.describeWorker(soldier));
            helper.assertTrue(chestPos.equals(soldier.getPoiPos()), "Soldier POI is not the chest: " + DevScenarios.describeWorker(soldier));
            helper.assertTrue(player.getUUID().equals(soldier.getClayTeamOwnerUUID()), "Team is not loyal to the player");
        }
        helper.succeed();
    }

    /**
     * BUG TEST, ANALYSIS.md §3 "Free blocks": with an EMPTY chest, a builder must not place any blueprint block.
     * Suspected cause: BuildBlueprintGoal.getRequestFromStorage marks the request as placing even when nothing
     * was extracted, and EaselBlockEntity.doBlueprintRequest places request.getItem() instead of the carried stack.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 500)
    public static void emptyChestBuildsNothing(GameTestHelper helper) {
        BlueprintData blueprint = tinyBlueprint(helper, "one_block", 1, 1, 1, BlockPos.ZERO);
        setUpBuilder(helper, blueprint, List.of());
        BlockPos target = EASEL.offset(0, 0, 2);

        helper.onEachTick(() -> {
            if (!helper.getBlockState(target).isAir()) {
                helper.fail("BUG (free blocks): a block was built although the chest was empty", target);
            }
        });
        helper.runAtTickTime(400, helper::succeed);
    }

    /**
     * CHARACTERIZATION TEST: documents a KNOWN LIMITATION, not a bug.
     * ANALYSIS.md §3 "Blocked sites never finish": builders don't clear obstructions. This test pins the current
     * behavior: every unblocked block is placed, the blocked spot keeps its obstruction, and the blueprint stays
     * unfinished. If clearing obstructions is ever added, update this test on purpose.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void blockedSiteBuildsAroundObstruction(GameTestHelper helper) {
        BlueprintData blueprint = tinyBlueprint(helper, "three_blocks", 1, 1, 3,
                new BlockPos(0, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 0, 2));
        BlockPos first = EASEL.offset(0, 0, 2);
        BlockPos blocked = EASEL.offset(0, 0, 3);
        BlockPos last = EASEL.offset(0, 0, 4);
        helper.setBlock(blocked, Blocks.DIRT);
        Builder builder = setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.COBBLESTONE, 3)));

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE, first);
            helper.assertBlockPresent(Blocks.COBBLESTONE, last);
            helper.assertBlockPresent(Blocks.DIRT, blocked);
            helper.assertTrue(!builder.easel().isFinished(), "Blueprint unexpectedly reports finished");
        });
    }

    /**
     * BUG TEST, ANALYSIS.md §1 "Saving resets the job": saving a soldier (as autosave does) must not cancel the
     * block it is carrying. Suspected cause: AbstractClaySoldierEntity.addAdditionalSaveData calls
     * workSelector.resetGoal(), which cancels the BuildBlueprintGoal request, so the item is returned to the chest.
     * Detection: the chest count must never go back up after the save.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void saveDoesNotCancelCarriedBlock(GameTestHelper helper) {
        // One block, far from the chest, so there is time to save while the soldier carries it.
        BlueprintData blueprint = tinyBlueprint(helper, "far_block", 1, 1, 8, new BlockPos(0, 0, 7));
        Builder builder = setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.COBBLESTONE, 1)));
        BlockPos target = EASEL.offset(0, 0, 9);
        Vec3 chestCenter = Vec3.atCenterOf(helper.absolutePos(CHEST));
        int[] countAtSave = {-1};

        helper.onEachTick(() -> {
            ClaySoldierEntity soldier = builder.soldier();
            int count = builder.chest().countItem(Items.COBBLESTONE);
            if (countAtSave[0] < 0) {
                if (!soldier.getCarriedStack().isEmpty() && soldier.position().distanceTo(chestCenter) > 3) {
                    soldier.saveWithoutId(new CompoundTag()); // the same save path as autosave / chunk unload
                    countAtSave[0] = count;
                }
            } else if (count > countAtSave[0]) {
                helper.fail("BUG (save resets job): after saving, the carried block was returned to the chest", soldier);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(countAtSave[0] >= 0, "The soldier never carried the block away from the chest");
            helper.assertBlockPresent(Blocks.COBBLESTONE, target);
        });
    }

    /**
     * BUG TEST, ANALYSIS.md §1/E "Dig never stops": a digger must stop at the first unbreakable layer (bedrock).
     * Setup: a 5x5 dirt layer over a bedrock layer, with dirt again below the bedrock.
     * Correct: the top layer gets dug out, and nothing below the bedrock is ever broken.
     * Suspected cause: DigHoleGoal treats unbreakable blocks as "already broken" and keeps going down.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 1500)
    public static void digStopsAtBedrock(GameTestHelper helper) {
        BlockPos center = new BlockPos(7, GROUND_Y, 7);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                helper.setBlock(center.offset(x, -1, z), Blocks.BEDROCK);
                helper.setBlock(center.offset(x, -2, z), Blocks.DIRT);
                if (Math.abs(x) <= 2 && Math.abs(z) <= 2) {
                    helper.setBlock(center.offset(x, 0, z), Blocks.DIRT);
                }
            }
        }
        ServerLevel level = helper.getLevel();
        DevScenarios.spawnWorker(level, helper.absolutePos(new BlockPos(3, GROUND_Y, 7)), DevScenarios.RED_TEAM,
                owner(level), DigHoleGoal.class, helper.absolutePos(center));

        long[] topLayerDugAt = {-1};
        helper.onEachTick(() -> {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos below = center.offset(x, -2, z);
                    if (!helper.getBlockState(below).is(Blocks.DIRT)) {
                        helper.fail("BUG (dig never stops): dug below the bedrock layer", below);
                    }
                }
            }
            if (topLayerDugAt[0] < 0 && countBlocks(helper, center, 0, Blocks.DIRT) == 0) {
                topLayerDugAt[0] = helper.getTick();
            }
        });
        // Correct behavior: the top layer is dug out, then nothing below bedrock is broken for 300 more ticks.
        helper.succeedWhen(() -> {
            helper.assertTrue(topLayerDugAt[0] >= 0, "Top dirt layer not dug out yet (%d dirt left)"
                    .formatted(countBlocks(helper, center, 0, Blocks.DIRT)));
            helper.assertTrue(helper.getTick() >= topLayerDugAt[0] + 300, "Waiting to see if the soldier digs below bedrock");
        });
    }

    // Helpers

    record Builder(ClaySoldierEntity soldier, ChestBlockEntity chest, EaselBlockEntity easel) {
    }

    /**
     * Chest at {@link #CHEST} with the given items, easel at {@link #EASEL} facing north with the blueprint,
     * and one builder soldier at {@link #SOLDIER} whose POI is the chest.
     */
    static Builder setUpBuilder(GameTestHelper helper, BlueprintData blueprint, List<ItemStack> chestItems) {
        ServerLevel level = helper.getLevel();
        BlockPos chestPos = helper.absolutePos(CHEST);
        ChestBlockEntity chest = DevScenarios.placeChest(level, chestPos, chestItems);
        EaselBlockEntity easel = DevScenarios.placeEasel(level, helper.absolutePos(EASEL), Direction.NORTH, blueprint);
        ClaySoldierEntity soldier = DevScenarios.spawnWorker(level, helper.absolutePos(SOLDIER), DevScenarios.RED_TEAM,
                owner(level), BuildBlueprintGoal.class, chestPos);
        return new Builder(soldier, chest, easel);
    }

    /**
     * Team owner for tests. Working requires the soldier's team to be loyal to some player.
     */
    static ServerPlayer owner(ServerLevel level) {
        return FakePlayerFactory.getMinecraft(level);
    }

    /**
     * Creates a blueprint in code (not registered, so it never shows up in game) made of cobblestone
     * at the given local positions, with the given template size.
     */
    static BlueprintData tinyBlueprint(GameTestHelper helper, String name, int sizeX, int sizeY, int sizeZ, BlockPos... cobblestoneAt) {
        CompoundTag tag = new CompoundTag();
        tag.put("size", intList(sizeX, sizeY, sizeZ));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.COBBLESTONE.defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        for (BlockPos pos : cobblestoneAt) {
            CompoundTag block = new CompoundTag();
            block.put("pos", intList(pos.getX(), pos.getY(), pos.getZ()));
            block.putInt("state", 0);
            blocks.add(block);
        }
        tag.put("blocks", blocks);

        var template = ImmutableTemplate.create(helper.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK), tag, List.of());
        BlueprintData data = new BlueprintData(ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "gametest/" + name), "gametest." + name, 0);
        data.bindStructure(template);
        return data;
    }

    static ListTag intList(int... values) {
        ListTag list = new ListTag();
        for (int value : values) {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }

    /**
     * Counts blocks of a type in the 5x5 area around {@code center}, at relative height {@code dy}.
     */
    static int countBlocks(GameTestHelper helper, BlockPos center, int dy, Block block) {
        int count = 0;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (helper.getBlockState(center.offset(x, dy, z)).is(block)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Asserts a block at an absolute position. Used instead of {@code helper.relativePos}, which in 1.21.1
     * converts absolute positions back with an extra 180° rotation.
     */
    static void assertBlockAt(GameTestHelper helper, Block block, BlockPos absolutePos) {
        var state = helper.getLevel().getBlockState(absolutePos);
        helper.assertTrue(state.is(block), "Expected %s at %s, got %s".formatted(
                block.getName().getString(), absolutePos.toShortString(), state.getBlock().getName().getString()));
    }
}
