package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.init.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.EASEL;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.SOLDIER;

/**
 * Builders must survive their own building site (D63, D64), found in the structure build run 38120112603: builders
 * fell off upper floors and died (fall damage is multiplied by 100 upstream), got cobblestone walls and window panes
 * placed into them, and walled themselves into a 1x1 shaft of the keep. Each test asserts the CORRECT behavior.
 * D65 (run 38124453564): the town hall's builders walked out of an unglazed window onto its open shutters and fell to
 * their deaths, because vanilla's pathfinder treats an open trapdoor as a floor.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuilderSafetyGameTests {
    /** Where a 1-wide test blueprint's local (0, 0, 0) is built (easel facing north). */
    private static final BlockPos SPOT = EASEL.offset(0, 0, 2);

    /**
     * BUG TEST (fall deaths): a working builder dropped 6 blocks lands hurt but alive. Upstream multiplies fall damage
     * by 100 (3 becomes 300), which killed the builders of the keep and the town hall when they walked off the floors
     * they had built.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void workingBuilderSurvivesAFall(GameTestHelper helper) {
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "fall_survival", 1, 1, 1, BlockPos.ZERO);
        ClaySoldierGameTests.Builder builder = ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.COBBLESTONE)));
        ClaySoldierEntity soldier = builder.soldier();
        BlockPos top = helper.absolutePos(SOLDIER).above(6);
        soldier.teleportTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(soldier.isAlive(), "BUG (fall death): the working builder died from a 6-block fall");
            helper.assertTrue(soldier.onGround(), "The builder has not landed yet");
            helper.assertTrue(soldier.getHealth() < soldier.getMaxHealth(), "The fall did not hurt the builder at all");
            helper.succeed();
        });
    }

    /**
     * BUG TEST (block placed into a soldier): a soldier stands on the blueprint's only spot. The block must not be
     * placed while it stands there (church builders ended up inside cobblestone walls and glass panes); once the soldier
     * is gone, it must be placed.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 1200)
    public static void blockIsNotPlacedIntoASoldier(GameTestHelper helper) {
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "soldier_in_the_spot", 1, 1, 1, BlockPos.ZERO);
        ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.COBBLESTONE)));
        ClaySoldierEntity bystander = bystander(helper, SPOT);
        helper.runAfterDelay(400, () -> {
            helper.assertBlockPresent(Blocks.AIR, SPOT);
            helper.assertTrue(bystander.isAlive(), "The bystander died");
            bystander.discard();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 400, "Too early");
            helper.assertBlockPresent(Blocks.COBBLESTONE, SPOT);
        });
    }

    /**
     * BUG TEST (walled in): a soldier stands in a 1x1 pit whose fourth side is two blueprint blocks (feet and head
     * level). The lower one may be placed (the soldier can still hop out), the upper one must wait until the soldier is
     * gone, because it would close the last way out (keep builders walled themselves into a shaft and stood there for
     * 10,000 ticks).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 1200)
    public static void blockDoesNotTrapASoldier(GameTestHelper helper) {
        BlockPos pit = SPOT.east();
        for (BlockPos wall : List.of(pit.north(), pit.south(), pit.east())) {
            helper.setBlock(wall, Blocks.COBBLESTONE);
            helper.setBlock(wall.above(), Blocks.COBBLESTONE);
        }
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "pit_wall", 1, 2, 1, BlockPos.ZERO, new BlockPos(0, 1, 0));
        ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.COBBLESTONE, 2)));
        ClaySoldierEntity bystander = bystander(helper, pit);
        helper.runAfterDelay(500, () -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE, SPOT);
            helper.assertBlockPresent(Blocks.AIR, SPOT.above());
            helper.assertTrue(bystander.isAlive(), "The bystander died");
            bystander.discard();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 500, "Too early");
            helper.assertBlockPresent(Blocks.COBBLESTONE, SPOT.above());
        });
    }

    /**
     * BUG TEST (the trapdoor trap): a soldier on top of a 4-high pillar, an open trapdoor (a shutter) in the air beside
     * the top. Vanilla plans a path into the trapdoor's cell, and the soldier falls. The soldiers' pathfinder must not
     * use an open trapdoor as a floor: no path step is in the shutter's cell, and the soldier stays on the pillar.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void soldierDoesNotWalkOntoAnOpenTrapdoor(GameTestHelper helper) {
        BlockPos top = pillar(helper, 2, 4, 6, 8);
        BlockPos shutter = top.east();
        BlockState open = Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.OPEN, true)
                .setValue(TrapDoorBlock.FACING, Direction.EAST);
        helper.setBlock(shutter, open);
        BlockPos target = new BlockPos(shutter.getX() + 3, ClaySoldierGameTests.GROUND_Y, shutter.getZ());
        ClaySoldierEntity soldier = DoorGameTests.spawnSoldier(helper, top, DevScenarios.RED_TEAM);
        BlockPos shutterAbsolute = helper.absolutePos(shutter);
        BlockPos targetAbsolute = helper.absolutePos(target);
        helper.runAfterDelay(5, () -> {
            Path path = soldier.getNavigation().createPath(targetAbsolute, 0);
            helper.assertTrue(path != null, "No path at all (not even a partial one)");
            for (int i = 0; i < path.getNodeCount(); i++) {
                Node node = path.getNode(i);
                helper.assertFalse(node.asBlockPos().equals(shutterAbsolute),
                        "BUG (trapdoor trap): the path steps into the open trapdoor at " + shutterAbsolute.toShortString());
            }
        });
        helper.onEachTick(() -> {
            if (helper.getTick() % 10 == 0) {
                soldier.getNavigation().moveTo(targetAbsolute.getX() + 0.5, targetAbsolute.getY(), targetAbsolute.getZ() + 0.5, 1.0);
            }
        });
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(soldier.isAlive(), "The soldier died");
            helper.assertTrue(soldier.getY() >= helper.absolutePos(top).getY() - 0.1,
                    "BUG (trapdoor trap): the soldier fell off the pillar, it is at y " + soldier.getY());
            helper.succeed();
        });
    }

    /** Closed trapdoors are still a floor: a bridge of them between two pillars is walked. */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void soldierWalksOverClosedTrapdoors(GameTestHelper helper) {
        BlockPos top = pillar(helper, 2, 3, 6, 8);
        BlockPos other = pillar(helper, 7, 8, 6, 8);
        BlockState closed = Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.BOTTOM);
        for (int x = 4; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, top.getY(), top.getZ()), closed);
        }
        ClaySoldierEntity soldier = DoorGameTests.spawnSoldier(helper, top, DevScenarios.RED_TEAM);
        BlockPos otherAbsolute = helper.absolutePos(other);
        helper.runAfterDelay(5, () -> {
            Path path = soldier.getNavigation().createPath(otherAbsolute, 0);
            helper.assertTrue(path != null && path.canReach(), "No path over the closed trapdoors to the other pillar");
            helper.succeed();
        });
    }

    /** A cobblestone pillar from the ground up to y 5 (relative) over x0..x1, z0..z1; returns the standing spot on top (its west end). */
    private static BlockPos pillar(GameTestHelper helper, int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = ClaySoldierGameTests.GROUND_Y; y <= 5; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.COBBLESTONE);
                }
            }
        }
        return new BlockPos(x1, 6, (z0 + z1) / 2);
    }

    /** A clay soldier of the builders' team standing still (no AI) at {@code pos}. */
    private static ClaySoldierEntity bystander(GameTestHelper helper, BlockPos pos) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(pos);
        ClaySoldierEntity soldier = ModEntityTypes.CLAY_SOLDIER_ENTITY.get().create(level);
        soldier.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        soldier.setNoAi(true);
        level.addFreshEntity(soldier);
        soldier.setClayTeamType(DevScenarios.RED_TEAM);
        return soldier;
    }
}
