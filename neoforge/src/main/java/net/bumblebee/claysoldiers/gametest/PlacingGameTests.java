package net.bumblebee.claysoldiers.gametest;

import com.mojang.authlib.GameProfile;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.EASEL;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * Tests for placing blueprint blocks whose item places a different block (a torch item places a WALL torch,
 * an oak sign item places an oak WALL sign), by builders and by hand. Each asserts the CORRECT behavior.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class PlacingGameTests {
    /** Where a 1-wide test blueprint's local (0, 0, 0) is built (easel facing north). */
    private static final BlockPos SPOT = EASEL.offset(0, 0, 2);
    private static final BlockState WALL_TORCH = Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.NORTH);
    private static final BlockState WALL_SIGN = Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.NORTH);

    /**
     * BUG TEST (wall-mounted blocks never placed): the blueprint wants a wall torch on a wall that already stands, the
     * chest has one torch (the item that places wall torches), one worker. The torch must be placed.
     * Found while measuring Fix B: placing compared the torch item's own block (standing torch) with the spot's block
     * (wall torch), so the builder returned the torch and got it handed out again forever.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void builderPlacesWallTorch(GameTestHelper helper) {
        builderPlacesWallBlock(helper, "wall_torch", WALL_TORCH, Items.TORCH);
    }

    /**
     * Same as {@link #builderPlacesWallTorch} with a different item/block pair (oak sign item, oak wall sign block), so
     * the fix must be general, not torch-only.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void builderPlacesWallSign(GameTestHelper helper) {
        builderPlacesWallBlock(helper, "wall_sign", WALL_SIGN, Items.OAK_SIGN);
    }

    private static void builderPlacesWallBlock(GameTestHelper helper, String name, BlockState wallBlock, Item item) {
        helper.setBlock(SPOT.south(), Blocks.COBBLESTONE); // the wall it hangs on
        BlueprintData blueprint = BuildRequestGameTests.blueprint(helper, name, 1, 1, 1, Map.of(BlockPos.ZERO, wallBlock));
        ClaySoldierGameTests.Builder builder = ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(item)));

        helper.succeedWhen(() -> {
            helper.assertBlockState(SPOT, wallBlock::equals, () -> "BUG (wall-mounted block): expected " + wallBlock
                    + " at the spot, got " + helper.getBlockState(SPOT));
            helper.assertTrue(builder.easel().isFinished(), "The blueprint is not finished");
        });
    }

    /**
     * BUG TEST (wall-mounted blocks, hand placing): a player right-clicks the easel with a torch and then with an oak
     * sign; the blueprint wants a wall torch and a wall sign above it, on a wall that already stands. Both must be placed
     * and each right-click must use up one item.
     */
    @GameTest(template = PLATFORM)
    public static void handPlacesWallTorchAndSign(GameTestHelper helper) {
        helper.setBlock(SPOT.south(), Blocks.COBBLESTONE);
        helper.setBlock(SPOT.south().above(), Blocks.COBBLESTONE);
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        blocks.put(BlockPos.ZERO, WALL_TORCH);
        blocks.put(new BlockPos(0, 1, 0), WALL_SIGN);
        BlueprintData blueprint = BuildRequestGameTests.blueprint(helper, "wall_torch_and_sign", 1, 2, 1, blocks);
        ServerLevel level = helper.getLevel();
        BlockPos easelPos = helper.absolutePos(EASEL);
        DevScenarios.placeEasel(level, easelPos, Direction.NORTH, blueprint);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "csr-easel-tester"));

        for (Item item : List.of(Items.TORCH, Items.OAK_SIGN)) {
            ItemStack held = new ItemStack(item, 2);
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            // The same server-side path as a real right-click on the easel.
            player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(easelPos), Direction.UP, easelPos, false));
            helper.assertTrue(held.getCount() == 1, "BUG (wall-mounted block, by hand): right-clicking with "
                    + item + " did not use one up (" + held.getCount() + " left)");
        }
        helper.assertBlockState(SPOT, WALL_TORCH::equals, () -> "Expected the wall torch, got " + helper.getBlockState(SPOT));
        helper.assertBlockState(SPOT.above(), WALL_SIGN::equals, () -> "Expected the wall sign, got " + helper.getBlockState(SPOT.above()));
        helper.succeed();
    }
}
