package net.bumblebee.claysoldiers.gametest;

import com.mojang.authlib.GameProfile;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlock;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.site.BuildSite;
import net.bumblebee.claysoldiers.blueprint.site.BuildSitePlanner;
import net.bumblebee.claysoldiers.blueprint.site.SiteCheck;
import net.bumblebee.claysoldiers.init.ModBlocks;
import net.bumblebee.claysoldiers.init.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.GROUND_Y;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * Tests for M2: {@link BuildSitePlanner} and {@code /csr build}.
 * <p>
 * Most tests use a tiny unregistered 3x2x4 blueprint with cobblestone only at two opposite corners,
 * local (0,0,0) and (2,1,3). Expected world positions are worked out by hand from the footprint table
 * (not by calling the same code), so the tests check the placement math independently.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildSiteGameTests {
    private static final BlockPos CORNER_A = new BlockPos(0, 0, 0);
    private static final BlockPos CORNER_B = new BlockPos(2, 1, 3);

    private static BlueprintData cornersBlueprint(GameTestHelper helper) {
        return ClaySoldierGameTests.tinyBlueprint(helper, "corners", 3, 2, 4, CORNER_A, CORNER_B);
    }

    /**
     * Valid site, building south (easel faces north): the blueprint starts 2 blocks south of the easel, centered on it.
     * Expected: (0,0,0) at easel + (-1, 0, 2); (2,1,3) at easel + (1, 1, 5).
     */
    @GameTest(template = PLATFORM)
    public static void validSiteSouthPlacesEasel(GameTestHelper helper) {
        BlockPos easel = helper.absolutePos(new BlockPos(8, GROUND_Y, 3));
        assertValidSiteAndPlace(helper, easel, Direction.SOUTH, Direction.NORTH,
                easel.offset(-1, 0, 2), easel.offset(1, 1, 5));
    }

    /**
     * Valid site, building west (easel faces east, template rotated 90° clockwise).
     * Expected: (0,0,0) at easel + (-2, 0, -1); (2,1,3) at easel + (-5, 1, 1).
     */
    @GameTest(template = PLATFORM)
    public static void validSiteWestPlacesEasel(GameTestHelper helper) {
        BlockPos easel = helper.absolutePos(new BlockPos(11, GROUND_Y, 8));
        assertValidSiteAndPlace(helper, easel, Direction.WEST, Direction.EAST,
                easel.offset(-2, 0, -1), easel.offset(-5, 1, 1));
    }

    /**
     * Obstructed site: refused with the obstruction as reason, and placing the (earlier valid) site places nothing.
     */
    @GameTest(template = PLATFORM)
    public static void obstructedSiteIsRefused(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlueprintData blueprint = cornersBlueprint(helper);
        BlockPos easel = helper.absolutePos(new BlockPos(8, GROUND_Y, 3));
        SiteCheck before = BuildSitePlanner.check(level, blueprint, null, easel, Direction.SOUTH);
        helper.assertTrue(before.isValid(), "Site should be valid before the obstruction: " + before.message());

        BlockPos obstruction = easel.offset(1, 1, 5);
        level.setBlockAndUpdate(obstruction, Blocks.DIRT.defaultBlockState());
        SiteCheck check = BuildSitePlanner.check(level, blueprint, null, easel, Direction.SOUTH);
        assertRefused(helper, check, SiteCheck.Reason.OBSTRUCTED, obstruction);
        helper.assertTrue(check.message().contains("Dirt"), "Reason should name the obstruction: " + check.message());

        SiteCheck placed = BuildSitePlanner.place(level, before.site());
        assertRefused(helper, placed, SiteCheck.Reason.OBSTRUCTED, obstruction);
        helper.assertTrue(level.getBlockState(easel).isAir(), "An easel was placed on an obstructed site");
        helper.succeed();
    }

    /**
     * A hole in the ground under the building: refused.
     */
    @GameTest(template = PLATFORM)
    public static void siteWithoutGroundIsRefused(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos easel = helper.absolutePos(new BlockPos(8, GROUND_Y, 3));
        BlockPos hole = easel.offset(0, -1, 4);
        level.setBlockAndUpdate(hole, Blocks.AIR.defaultBlockState());

        SiteCheck check = BuildSitePlanner.check(level, cornersBlueprint(helper), null, easel, Direction.SOUTH);
        assertRefused(helper, check, SiteCheck.Reason.NO_GROUND, hole);
        helper.succeed();
    }

    /**
     * Another easel within 8 blocks: refused, because builders use the nearest easel.
     */
    @GameTest(template = PLATFORM)
    public static void siteNearAnotherEaselIsRefused(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos easel = helper.absolutePos(new BlockPos(8, GROUND_Y, 3));
        BlockPos otherEasel = easel.offset(5, 0, 0);
        level.setBlockAndUpdate(otherEasel, ModBlocks.EASEL_BLOCK.get().defaultBlockState());

        SiteCheck check = BuildSitePlanner.check(level, cornersBlueprint(helper), null, easel, Direction.SOUTH);
        assertRefused(helper, check, SiteCheck.Reason.EASEL_NEARBY, otherEasel);
        helper.succeed();
    }

    /**
     * {@code /csr build csr:small_house}, run by a player standing on the platform facing south, places an easel
     * at the player's block facing north with the blueprint. Running it again on the same spot is refused.
     */
    @GameTest(template = PLATFORM)
    public static void buildCommandPlacesEaselAtPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "csr-build-tester"));
        BlockPos standAt = helper.absolutePos(new BlockPos(8, GROUND_Y, 2));
        player.moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5, 0f, 0f); // yaw 0 = facing south

        String command = "csr build csr:small_house";
        level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(2), command);

        var state = level.getBlockState(standAt);
        helper.assertTrue(state.is(ModBlocks.EASEL_BLOCK.get()), "No easel at the player's position, found " + state);
        helper.assertTrue(state.getValue(EaselBlock.FACING) == Direction.NORTH, "Easel should face north (building south)");
        BlueprintData smallHouse = level.registryAccess().registryOrThrow(ModRegistries.BLUEPRINTS)
                .get(ResourceLocation.fromNamespaceAndPath("csr", "small_house"));
        helper.assertTrue(level.getBlockEntity(standAt) instanceof EaselBlockEntity easel && easel.getBlueprintData() == smallHouse,
                "Easel does not hold the small house blueprint");

        // Same spot again: the easel spot is taken, so nothing changes.
        level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(2), command);
        int easels = 0;
        for (BlockPos pos : BlockPos.betweenClosed(standAt.offset(-8, -2, -8), standAt.offset(8, 2, 8))) {
            if (level.getBlockState(pos).is(ModBlocks.EASEL_BLOCK.get())) {
                easels++;
            }
        }
        helper.assertTrue(easels == 1, "Expected exactly 1 easel after running the command twice, found " + easels);
        helper.succeed();
    }

    // Helpers

    private static void assertValidSiteAndPlace(GameTestHelper helper, BlockPos easel, Direction buildDirection, Direction expectedFacing,
                                                BlockPos expectedA, BlockPos expectedB) {
        ServerLevel level = helper.getLevel();
        BlueprintData blueprint = cornersBlueprint(helper);
        SiteCheck check = BuildSitePlanner.check(level, blueprint, null, easel, buildDirection);
        helper.assertTrue(check.isValid(), "Site should be valid: " + check.message());
        BuildSite site = check.site();

        helper.assertTrue(site.easelFacing() == expectedFacing, "Easel facing " + site.easelFacing() + ", expected " + expectedFacing);
        Set<BlockPos> targets = new HashSet<>(site.targets());
        helper.assertTrue(targets.equals(Set.of(expectedA, expectedB)),
                "Footprint %s, expected [%s, %s]".formatted(site.targets(), expectedA.toShortString(), expectedB.toShortString()));
        helper.assertTrue(site.bounds().minX() == Math.min(expectedA.getX(), expectedB.getX())
                        && site.bounds().maxX() == Math.max(expectedA.getX(), expectedB.getX())
                        && site.bounds().minZ() == Math.min(expectedA.getZ(), expectedB.getZ())
                        && site.bounds().maxZ() == Math.max(expectedA.getZ(), expectedB.getZ()),
                "Bounds " + site.bounds() + " don't match the expected footprint");

        SiteCheck placed = BuildSitePlanner.place(level, site);
        helper.assertTrue(placed.isValid(), "Placing failed: " + placed.message());
        var state = level.getBlockState(easel);
        helper.assertTrue(state.is(ModBlocks.EASEL_BLOCK.get()) && state.getValue(EaselBlock.FACING) == expectedFacing,
                "Easel not placed with facing " + expectedFacing + ", found " + state);
        helper.assertTrue(level.getBlockEntity(easel) instanceof EaselBlockEntity easelEntity && easelEntity.getBlueprintData() == blueprint,
                "Easel has no blueprint");
        helper.assertTrue(level.getBlockState(expectedA).isAir() && level.getBlockState(expectedB).isAir(),
                "The planner must only place the easel, not build");
        helper.succeed();
    }

    private static void assertRefused(GameTestHelper helper, SiteCheck check, SiteCheck.Reason reason, BlockPos problemPos) {
        helper.assertTrue(!check.isValid(), "Site should be refused (" + reason + ")");
        helper.assertTrue(check.reason() == reason, "Refused for " + check.reason() + " (" + check.message() + "), expected " + reason);
        helper.assertTrue(problemPos.equals(check.problemPos()),
                "Problem at " + check.problemPos() + ", expected " + problemPos.toShortString());
        helper.assertTrue(!check.message().isEmpty(), "Refusal has no message");
    }
}
