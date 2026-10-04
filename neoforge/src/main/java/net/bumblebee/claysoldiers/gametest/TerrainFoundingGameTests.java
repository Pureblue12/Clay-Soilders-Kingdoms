package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.bumblebee.claysoldiers.terrain.TestTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static net.bumblebee.claysoldiers.gametest.KingdomGameTests.RED;
import static net.bumblebee.claysoldiers.gametest.TerrainScanGameTests.TERRAIN;

/**
 * Kingdom founding checks the ground around the king: refused with a clear reason on a cliff, near lava and on deep
 * water; founded on flat ground; founded with warnings on soft penalties; operators can force it. Each asserts the
 * CORRECT behavior. Kingdom data is world-wide, so each test has its own batch and cleans up.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class TerrainFoundingGameTests {

    /** The king stands on the low side of the cliff; the site includes the 6-block step. */
    @GameTest(template = TERRAIN, batch = "terrain_found_cliff")
    public static void foundRefusedOnCliff(GameTestHelper helper) {
        refused(helper, TestTerrain.Shape.CLIFF, new BlockPos(15, 9, 16), "cliff");
    }

    @GameTest(template = TERRAIN, batch = "terrain_found_lava")
    public static void foundRefusedNearLava(GameTestHelper helper) {
        refused(helper, TestTerrain.Shape.LAVA_POOL, new BlockPos(19, 9, 16), "lava");
    }

    @GameTest(template = TERRAIN, batch = "terrain_found_water")
    public static void foundRefusedOnDeepWater(GameTestHelper helper) {
        refused(helper, TestTerrain.Shape.LAKE_EDGE, new BlockPos(15, 9, 16), "deep water");
    }

    private static void refused(GameTestHelper helper, TestTerrain.Shape shape, BlockPos king, String reason) {
        KingdomGameTests.clear(helper);
        TerrainScanGameTests.build(helper, shape);
        FakePlayer player = KingdomGameTests.player(helper);
        try {
            ClaySoldierEntity soldier = KingdomGameTests.spawnKing(helper, king, RED, player);
            Kingdoms.Result result = Kingdoms.found(helper.getLevel(), soldier, player);
            helper.assertTrue(!result.ok() && Kingdoms.byTeam(server(helper), RED).isEmpty(), "Founding on the " + shape.getSerializedName() + " should be refused");
            helper.assertTrue(result.refusal().contains(reason), "The refusal should say '" + reason + "': " + result.refusal());
        } finally {
            KingdomGameTests.clear(helper);
        }
        helper.succeed();
    }

    /** The plateau's flat top: founded, without warnings. */
    @GameTest(template = TERRAIN, batch = "terrain_found_flat")
    public static void foundWorksOnFlatGround(GameTestHelper helper) {
        KingdomGameTests.clear(helper);
        TerrainScanGameTests.build(helper, TestTerrain.Shape.PLATEAU);
        FakePlayer player = KingdomGameTests.player(helper);
        try {
            ClaySoldierEntity king = KingdomGameTests.spawnKing(helper, new BlockPos(16, 13, 16), RED, player);
            Kingdoms.Result result = Kingdoms.found(helper.getLevel(), king, player);
            helper.assertTrue(result.ok() && Kingdoms.byTeam(server(helper), RED).isPresent(), "Founding on flat ground failed: " + result.refusal());
            helper.assertTrue(result.warnings().isEmpty(), "Flat ground needs no warnings: " + result.warnings());
        } finally {
            KingdomGameTests.clear(helper);
        }
        helper.succeed();
    }

    /** The gentle slope: founded, with a warning naming the slope. */
    @GameTest(template = TERRAIN, batch = "terrain_found_slope")
    public static void foundWarnsOnSoftPenalties(GameTestHelper helper) {
        KingdomGameTests.clear(helper);
        TerrainScanGameTests.build(helper, TestTerrain.Shape.SLOPE);
        FakePlayer player = KingdomGameTests.player(helper);
        try {
            ClaySoldierEntity king = KingdomGameTests.spawnKing(helper, new BlockPos(16, 9, 16), RED, player);
            Kingdoms.Result result = Kingdoms.found(helper.getLevel(), king, player);
            helper.assertTrue(result.ok(), "A gentle slope must not refuse founding: " + result.refusal());
            helper.assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("slope")), "Expected a slope warning: " + result.warnings());
        } finally {
            KingdomGameTests.clear(helper);
        }
        helper.succeed();
    }

    /**
     * {@code /csr kingdom found force} skips the terrain check, for operators (permission 2) only: a normal player can't
     * use it, an operator founds on the cliff.
     */
    @GameTest(template = TERRAIN, batch = "terrain_found_force")
    public static void forceFoundsForOperatorsOnly(GameTestHelper helper) {
        KingdomGameTests.clear(helper);
        BlockPos center = TerrainScanGameTests.build(helper, TestTerrain.Shape.CLIFF);
        FakePlayer player = KingdomGameTests.player(helper);
        MinecraftServer server = server(helper);
        try {
            player.moveTo(center.getX() - 0.5, center.getY() + 1, center.getZ() + 0.5);
            KingdomGameTests.spawnKing(helper, new BlockPos(15, 9, 16), RED, player);
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(0), "csr kingdom found force");
            helper.assertTrue(Kingdoms.byTeam(server, RED).isEmpty(), "A normal player forced a kingdom onto the cliff");
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(0), "csr kingdom found");
            helper.assertTrue(Kingdoms.byTeam(server, RED).isEmpty(), "Founding on the cliff without force should be refused");
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "csr kingdom found force");
            helper.assertTrue(Kingdoms.byTeam(server, RED).isPresent(), "An operator's forced founding failed");
        } finally {
            KingdomGameTests.clear(helper);
        }
        helper.succeed();
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }
}
