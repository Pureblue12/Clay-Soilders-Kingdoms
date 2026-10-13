package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KingStone;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.bumblebee.claysoldiers.terrain.TestTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.GROUND_Y;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;
import static net.bumblebee.claysoldiers.gametest.KingdomGameTests.RED;
import static net.bumblebee.claysoldiers.gametest.TerrainScanGameTests.TERRAIN;

/**
 * Stage 4, founding by gold block (D92): a king handed a gold block picks a spot himself, walks there and sets it down,
 * which founds the kingdom; only a king of a team loyal to you without a kingdom takes it; the block is taken up when the
 * keep starts. Kingdom data is world-wide, so the tests that found get their own batch and clean up.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class KingStoneGameTests {

    /** On the plateau: the king takes one block, walks to a spot he picks, sets it down beside him; the kingdom is founded there. */
    @GameTest(template = TERRAIN, batch = "king_stone_found", timeoutTicks = 2400)
    public static void kingSetsDownHisGoldBlockAndFoundsTheKingdom(GameTestHelper helper) {
        KingdomGameTests.clear(helper);
        TerrainScanGameTests.build(helper, TestTerrain.Shape.PLATEAU);
        FakePlayer player = KingdomGameTests.player(helper);
        ClaySoldierEntity king = KingdomGameTests.spawnKing(helper, new BlockPos(16, 13, 16), RED, player);
        ItemStack held = new ItemStack(Items.GOLD_BLOCK, 2);
        KingStone.Given given = KingStone.give(helper.getLevel(), king, player, held);
        helper.assertTrue(given.ok(), "The king should take the gold block: " + given.message());
        helper.assertTrue(held.getCount() == 1, "One gold block should be used up, " + held.getCount() + " left");
        helper.assertTrue(KingStone.carries(king), "The king should carry the gold block");
        helper.assertTrue(Kingdoms.byTeam(server(helper), RED).isEmpty(), "Handing over the block must not found the kingdom yet");
        helper.succeedWhen(() -> {
            Optional<Kingdom> kingdom = Kingdoms.byTeam(server(helper), RED);
            helper.assertTrue(kingdom.isPresent(), "No kingdom yet; the king is at " + king.blockPosition().toShortString());
            helper.assertFalse(KingStone.carries(king), "The king still carries the gold block");
            BlockPos center = kingdom.get().center();
            boolean beside = Direction.Plane.HORIZONTAL.stream()
                    .anyMatch(direction -> helper.getLevel().getBlockState(center.relative(direction)).is(Blocks.GOLD_BLOCK));
            helper.assertTrue(beside, "No gold block beside the kingdom's center " + center.toShortString());
            KingdomGameTests.clear(helper);
        });
    }

    /** A soldier without a gold ingot isn't a king: he doesn't take the block, nothing is used up. */
    @GameTest(template = PLATFORM)
    public static void soldierWhoIsNotAKingDoesNotTakeTheGoldBlock(GameTestHelper helper) {
        FakePlayer player = KingdomGameTests.player(helper);
        ClaySoldierEntity soldier = KingdomGameTests.DevSoldier.spawnPlain(helper, new BlockPos(8, GROUND_Y, 8), RED);
        ItemStack held = new ItemStack(Items.GOLD_BLOCK);
        KingStone.Given given = KingStone.give(helper.getLevel(), soldier, player, held);
        helper.assertFalse(given.ok(), "A soldier who isn't a king took the gold block");
        helper.assertTrue(given.message().contains("gold ingot"), "The refusal should say how to crown him: " + given.message());
        helper.assertTrue(held.getCount() == 1 && !KingStone.carries(soldier), "Nothing should change hands");
        helper.succeed();
    }

    /** A king whose team already has a kingdom doesn't take another block. */
    @GameTest(template = PLATFORM, batch = "king_stone_has_kingdom")
    public static void kingWithAKingdomDoesNotTakeTheGoldBlock(GameTestHelper helper) {
        KingdomGameTests.clear(helper);
        FakePlayer player = KingdomGameTests.player(helper);
        try {
            ClaySoldierEntity king = KingdomGameTests.spawnKing(helper, new BlockPos(8, GROUND_Y, 8), RED, player);
            helper.assertTrue(Kingdoms.found(helper.getLevel(), king, player, true).ok(), "Setup: founding failed");
            ItemStack held = new ItemStack(Items.GOLD_BLOCK);
            KingStone.Given given = KingStone.give(helper.getLevel(), king, player, held);
            helper.assertFalse(given.ok(), "A king with a kingdom took the gold block");
            helper.assertTrue(given.message().contains("already has a kingdom"), "The refusal should say why: " + given.message());
            helper.assertTrue(held.getCount() == 1 && !KingStone.carries(king), "Nothing should change hands");
        } finally {
            KingdomGameTests.clear(helper);
        }
        helper.succeed();
    }

    /** When the keep starts, the gold block beside the kingdom's center is taken up (it comes back as the throne). */
    @GameTest(template = PLATFORM, batch = "king_stone_take_up")
    public static void goldBlockIsTakenUpWhenTheKeepStarts(GameTestHelper helper) {
        KingdomGameTests.clear(helper);
        FakePlayer player = KingdomGameTests.player(helper);
        try {
            ClaySoldierEntity king = KingdomGameTests.spawnKing(helper, new BlockPos(8, GROUND_Y, 8), RED, player);
            helper.assertTrue(Kingdoms.found(helper.getLevel(), king, player, true).ok(), "Setup: founding failed");
            Kingdom kingdom = Kingdoms.byTeam(server(helper), RED).orElseThrow();
            BlockPos stone = kingdom.center().east();
            helper.getLevel().setBlockAndUpdate(stone, Blocks.GOLD_BLOCK.defaultBlockState());
            Optional<BlockPos> taken = KingStone.takeUp(helper.getLevel(), kingdom);
            helper.assertTrue(taken.isPresent() && taken.get().equals(stone), "The gold block at " + stone.toShortString() + " wasn't taken up: " + taken);
            helper.assertTrue(helper.getLevel().getBlockState(stone).isAir(), "The gold block is still there");
        } finally {
            KingdomGameTests.clear(helper);
        }
        helper.succeed();
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }
}
