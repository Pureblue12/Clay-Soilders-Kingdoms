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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.EASEL;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * Bug tests for suspected bugs from {@code docs/ANALYSIS.md} that were not confirmed yet.
 * Each asserts the CORRECT behavior: a failing test here means the bug is confirmed.
 * Don't change gameplay code just to make one pass.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class SuspectedBugGameTests {

    /**
     * BUG TEST, ANALYSIS.md §E / TESTING.md C7: right-clicking an easel that has a blueprint while holding a
     * non-block item (a stick) should do nothing and must not throw.
     * Suspected cause: ServerBlueprintPlan.tryPlacing casts the held item to BlockItem.
     */
    @GameTest(template = PLATFORM)
    public static void easelIgnoresNonBlockItems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "one_block", 1, 1, 1, BlockPos.ZERO);
        BlockPos easelPos = helper.absolutePos(EASEL);
        DevScenarios.placeEasel(level, easelPos, Direction.NORTH, blueprint);

        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "csr-easel-tester"));
        ItemStack stick = new ItemStack(Items.STICK);
        player.setItemInHand(InteractionHand.MAIN_HAND, stick);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(easelPos), Direction.UP, easelPos, false);
        try {
            // The same server-side path as a real right-click on the easel.
            player.gameMode.useItemOn(player, level, stick, InteractionHand.MAIN_HAND, hit);
        } catch (RuntimeException e) {
            helper.fail("BUG (easel + non-block item): right-clicking the easel with a stick threw " + e);
        }
        helper.assertTrue(player.getMainHandItem().is(Items.STICK) && player.getMainHandItem().getCount() == 1,
                "The stick was used up");
        helper.assertBlockPresent(Blocks.AIR, EASEL.offset(0, 0, 2));
        helper.succeed();
    }
}
