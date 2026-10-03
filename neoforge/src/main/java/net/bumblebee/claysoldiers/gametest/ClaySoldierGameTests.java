package net.bumblebee.claysoldiers.gametest;

import com.mojang.authlib.GameProfile;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class ClaySoldierGameTests {
    static final String PLATFORM = "gametest/platform";
    /** Relative y of the smooth-stone floor. */
    static final int FLOOR_Y = 1;
    /** Relative y of the first air layer, where soldiers and blocks are placed. */
    static final int GROUND_Y = 2;

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

    // Helpers

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
