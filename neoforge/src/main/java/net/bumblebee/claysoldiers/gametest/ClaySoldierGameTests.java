package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
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
}
