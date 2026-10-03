package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.EASEL;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * Tests for how high and how reliably builders can place blueprint blocks.
 * Layout: easel at {@link ClaySoldierGameTests#EASEL} facing north, so a blueprint's local (x, y, z)
 * is built at EASEL + (x - sizeX / 2, y, 2 + z).
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuilderReachGameTests {
    /** A soldier carrying the same block longer than this is considered stuck. */
    private static final int STUCK_CARRY_TICKS = 600;

    /**
     * BUG TEST (builders freeze while "Carrying"): one block floating 2 above open ground. Before Fix A the
     * "reachable" check (path ends within 2 steps) accepted it while placing needed the feet within 2 blocks
     * straight-line, so the soldier carried it forever. (Since Fix B it is simply placed from below.) Correct: the
     * block is placed, or the soldier never takes it / gives up and returns it to the chest. Fails if the soldier
     * carries it for more than {@value #STUCK_CARRY_TICKS} ticks.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 800)
    public static void builderNeverStuckCarrying(GameTestHelper helper) {
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "floating_block", 1, 3, 1, new BlockPos(0, 2, 0));
        ClaySoldierGameTests.Builder builder = ClaySoldierGameTests.setUpBuilder(helper, blueprint,
                List.of(new ItemStack(Items.COBBLESTONE, 1)));
        BlockPos target = EASEL.offset(0, 2, 2);
        long[] carryingSince = {-1};

        helper.onEachTick(() -> {
            if (builder.soldier().getCarriedStack().isEmpty()) {
                carryingSince[0] = -1;
            } else if (carryingSince[0] < 0) {
                carryingSince[0] = helper.getTick();
            } else if (helper.getTick() - carryingSince[0] > STUCK_CARRY_TICKS) {
                helper.fail("BUG (stuck carrying): the soldier has carried the block for over %d ticks without placing it"
                        .formatted(STUCK_CARRY_TICKS), builder.soldier());
            }
        });
        helper.runAtTickTime(STUCK_CARRY_TICKS + 100, () -> {
            boolean placed = helper.getBlockState(target).is(Blocks.COBBLESTONE);
            boolean returned = builder.soldier().getCarriedStack().isEmpty() && builder.chest().countItem(Items.COBBLESTONE) == 1;
            helper.assertTrue(placed || returned, "The block was neither placed nor back in the chest");
            helper.succeed();
        });
    }

    /**
     * FIX B TEST: a block 5 above open ground, one worker, a chest with the block. Builders must be able to place blocks
     * well above their feet (planned reach: up to 8 above), so it must get placed.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void builderPlacesHighBlockFromTheGround(GameTestHelper helper) {
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "high_block", 1, 6, 1, new BlockPos(0, 5, 0));
        ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.COBBLESTONE, 1)));
        BlockPos target = EASEL.offset(0, 5, 2);
        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.COBBLESTONE, target));
    }

    /**
     * FIX B LIMIT TEST: a block 10 above open ground is beyond the planned reach (8 above the feet). It must not be
     * taken at all: nothing placed, the soldier never carries it, and the block stays in the chest.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 500)
    public static void blockAboveMaxReachIsNotTaken(GameTestHelper helper) {
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "too_high_block", 1, 11, 1, new BlockPos(0, 10, 0));
        ClaySoldierGameTests.Builder builder = ClaySoldierGameTests.setUpBuilder(helper, blueprint,
                List.of(new ItemStack(Items.COBBLESTONE, 1)));
        BlockPos target = EASEL.offset(0, 10, 2);
        helper.onEachTick(() -> {
            if (!builder.soldier().getCarriedStack().isEmpty()) {
                helper.fail("The soldier took a block that is out of reach", builder.soldier());
            }
        });
        helper.runAtTickTime(400, () -> {
            helper.assertBlockPresent(Blocks.AIR, target);
            helper.assertTrue(builder.chest().countItem(Items.COBBLESTONE) == 1, "The block left the chest");
            helper.succeed();
        });
    }

    /**
     * Fix B test (required since Fix B): a small tower, a 3x3 floor, two 3x3 wall rings (sealed hollow middle) and a
     * 3x3 roof, 34 blocks, one worker with a stocked chest. The blueprint must finish. Before Fix B builders could only
     * place about 1 block above their feet and stopped at 17 of 34; the roof's center over the sealed hollow needs the
     * 2-blocks-sideways reach.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 2000)
    public static void smallTowerFinishes(GameTestHelper helper) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 3; x++) {
                for (int z = 0; z < 3; z++) {
                    boolean hollowMiddle = (y == 1 || y == 2) && x == 1 && z == 1;
                    if (!hollowMiddle) {
                        blocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "small_tower", 3, 4, 3, blocks.toArray(BlockPos[]::new));
        ClaySoldierGameTests.Builder builder = ClaySoldierGameTests.setUpBuilder(helper, blueprint,
                List.of(new ItemStack(Items.COBBLESTONE, blocks.size())));

        helper.succeedWhen(() -> helper.assertTrue(builder.easel().isFinished(),
                "Tower not finished: %d of %d blocks still in the chest, soldier %s"
                        .formatted(builder.chest().countItem(Items.COBBLESTONE), blocks.size(),
                                builder.soldier().getCarriedStack().isEmpty() ? "empty-handed"
                                        : "carrying at " + builder.soldier().blockPosition().toShortString())));
    }
}
