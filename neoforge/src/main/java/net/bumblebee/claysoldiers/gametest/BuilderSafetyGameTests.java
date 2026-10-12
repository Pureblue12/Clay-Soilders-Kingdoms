package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.init.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
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
 * their deaths, because vanilla's pathfinder treats an open trapdoor as a floor. D68 (run 38140495889): they also fell
 * through the tops of fences and panes, which vanilla counts as floors too. D74 (run 38165377544): they hopped off a
 * stair's low step at the top of a planned drop and lost health on drops onto half-height blocks until they died.
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
     * BUG TEST (hurt by a planned drop): a working builder drops 3 blocks onto the top of a slab, 3.5 in all. The
     * pathfinder counts that as a 3-block drop and plans it, and the edge guard lets it through, so it must not hurt (town
     * hall builders lost a point of health on every such drop until they died, run 38165377544; D74).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void workingBuilderIsNotHurtByAPlannedDrop(GameTestHelper helper) {
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "planned_drop", 1, 1, 1, BlockPos.ZERO);
        ClaySoldierEntity soldier = ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.COBBLESTONE))).soldier();
        BlockPos slab = new BlockPos(12, ClaySoldierGameTests.GROUND_Y, 12);
        helper.setBlock(slab, Blocks.COBBLESTONE_SLAB);
        BlockPos slabAbsolute = helper.absolutePos(slab);
        helper.runAfterDelay(20, () -> soldier.teleportTo(slabAbsolute.getX() + 0.5, slabAbsolute.getY() + 4.0, slabAbsolute.getZ() + 0.5));
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(soldier.isAlive(), "The builder died");
            helper.assertTrue(soldier.onGround(), "The builder has not landed yet");
            helper.assertTrue(soldier.getHealth() >= soldier.getMaxHealth(),
                    "BUG (hurt by a planned drop): a 3.5-block drop onto a slab cost the working builder " + (soldier.getMaxHealth() - soldier.getHealth()));
            helper.succeed();
        });
    }

    /**
     * BUG TEST (hop off a stair): a soldier stands on the low step of a stair at the edge of a 3-high ledge and walks to
     * the ground below. Vanilla's move control hops whenever the feet are below the top of the block they stand in, so the
     * soldier hopped 1.25 up and fell 3.75 instead of 2.5 (town hall builders, every fall trace of run 38165377544; D74).
     * The low step is walked off without a hop: the soldier never rises above the step and lands unhurt.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 300)
    public static void soldierDoesNotHopOffAStairAtADrop(GameTestHelper helper) {
        for (int x = 4; x <= 6; x++) {
            for (int y = ClaySoldierGameTests.GROUND_Y; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 8), Blocks.COBBLESTONE);
            }
        }
        BlockPos stair = new BlockPos(4, 4, 8);
        helper.setBlock(stair, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        ClaySoldierEntity soldier = DoorGameTests.spawnSoldier(helper, stair.above(), DevScenarios.RED_TEAM);
        BlockPos stairAbsolute = helper.absolutePos(stair);
        double stepTop = stairAbsolute.getY() + 0.5;
        soldier.moveTo(stairAbsolute.getX() + 0.25, stepTop, stairAbsolute.getZ() + 0.5, 0, 0);
        BlockPos target = helper.absolutePos(new BlockPos(1, ClaySoldierGameTests.GROUND_Y, 8));
        double[] highest = {Double.NEGATIVE_INFINITY};
        helper.onEachTick(() -> {
            if (helper.getTick() < 5) {
                return;
            }
            highest[0] = Math.max(highest[0], soldier.getY());
            if (helper.getTick() % 10 == 0) {
                soldier.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 1.0);
            }
        });
        helper.runAfterDelay(250, () -> {
            helper.assertTrue(soldier.isAlive(), "BUG (hop off a stair): the soldier died from the drop");
            helper.assertTrue(highest[0] < stepTop + 0.3,
                    "BUG (hop off a stair): the soldier hopped up to y " + highest[0] + " from the step at " + stepTop);
            helper.assertTrue(soldier.getY() < target.getY() + 0.1, "The soldier did not get down, it is at " + soldier.position());
            helper.assertTrue(soldier.getHealth() >= soldier.getMaxHealth(), "The drop hurt the soldier");
            helper.succeed();
        });
    }

    /**
     * While DAMAGE is recorded, a working builder's fall writes a "fall trace" event: where it was each tick before the
     * fall and the route it followed (town hall builders fell at one spot until they died, and no event said from
     * where).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200, batch = "fall_trace")
    public static void fallOfAWorkingBuilderIsTraced(GameTestHelper helper) {
        DebugLog.reset();
        DebugLog.setEnabled(DebugCategory.DAMAGE, true, helper.getLevel().getServer());
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "fall_trace", 1, 1, 1, BlockPos.ZERO);
        ClaySoldierGameTests.Builder builder = ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.COBBLESTONE)));
        ClaySoldierEntity soldier = builder.soldier();
        BlockPos top = helper.absolutePos(SOLDIER).above(6);
        helper.runAfterDelay(20, () -> soldier.teleportTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5));
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(soldier.isAlive(), "The builder died");
            helper.assertTrue(DebugLog.contains(DebugCategory.DAMAGE, "fall trace (last "), "No 'fall trace' DAMAGE event after the fall");
            DebugLog.reset();
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
     * BUG TEST (berry bushes): a soldier stands where the blueprint wants a sweet berry bush. A bush has no collision
     * box, so the "block would overlap a creature" rule let it through, and the soldier ended up inside it, hurt until it
     * died (hospital L3: all three builders, D81). The bush must wait until the soldier is gone.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 1000)
    public static void berryBushIsNotPlacedOnASoldier(GameTestHelper helper) {
        helper.setBlock(SPOT.below(), Blocks.GRASS_BLOCK);
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "bush_on_the_spot", 1, 1, 1, Blocks.SWEET_BERRY_BUSH, BlockPos.ZERO);
        ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.SWEET_BERRIES)));
        ClaySoldierEntity bystander = bystander(helper, SPOT);
        helper.runAfterDelay(400, () -> {
            helper.assertBlockPresent(Blocks.AIR, SPOT);
            helper.assertTrue(bystander.isAlive(), "The bystander died");
            bystander.discard();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 400, "Too early");
            helper.assertBlockPresent(Blocks.SWEET_BERRY_BUSH, SPOT);
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

    /**
     * BUG TEST (falling through a fence top): a soldier on a 4-high pillar, a row of fences and a glass pane on the
     * ground beside it. Vanilla counts the cell above a fence or pane as a floor (a 3-block drop) and the soldier, too
     * tiny to touch the post, falls through to the ground (town hall builders, fall traces in run 38140495889; D68). No
     * path step may stand on top of the row, and the soldier stays on the pillar (the ground is a 4-block drop).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void soldierDoesNotStandOnAFenceTop(GameTestHelper helper) {
        BlockPos top = pillar(helper, 2, 4, 6, 8);
        int rowX = top.getX() + 1;
        helper.setBlock(new BlockPos(rowX, ClaySoldierGameTests.GROUND_Y, 6), Blocks.OAK_FENCE);
        helper.setBlock(new BlockPos(rowX, ClaySoldierGameTests.GROUND_Y, 7), Blocks.COBBLESTONE_WALL);
        helper.setBlock(new BlockPos(rowX, ClaySoldierGameTests.GROUND_Y, 8), Blocks.GLASS_PANE);
        BlockPos target = new BlockPos(rowX + 3, ClaySoldierGameTests.GROUND_Y, top.getZ());
        ClaySoldierEntity soldier = DoorGameTests.spawnSoldier(helper, top, DevScenarios.RED_TEAM);
        int rowAbsoluteX = helper.absolutePos(new BlockPos(rowX, 0, 0)).getX();
        int rowTopAbsoluteY = helper.absolutePos(new BlockPos(rowX, ClaySoldierGameTests.GROUND_Y + 1, 0)).getY();
        BlockPos targetAbsolute = helper.absolutePos(target);
        helper.runAfterDelay(5, () -> {
            Path path = soldier.getNavigation().createPath(targetAbsolute, 0);
            for (int i = 0; path != null && i < path.getNodeCount(); i++) {
                BlockPos node = path.getNode(i).asBlockPos();
                helper.assertFalse(node.getX() == rowAbsoluteX && node.getY() == rowTopAbsoluteY,
                        "BUG (fence top): the path stands on top of the fence row at " + node.toShortString());
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
                    "BUG (fence top): the soldier left the pillar over the fence row, it is at y " + soldier.getY());
            helper.succeed();
        });
    }

    /**
     * BUG TEST (slipping off a ledge): a working builder on top of a 4-high pillar is pushed toward its edge, the way a
     * hop or a cut corner carries a tiny soldier past its path (the gatehouse's builders fell 9 blocks down the
     * portcullis slot beside their walkway; D70). It must stop at the edge, not fall.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 200)
    public static void workingBuilderDoesNotStepOffALedge(GameTestHelper helper) {
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "ledge", 1, 1, 1, BlockPos.ZERO);
        ClaySoldierEntity soldier = ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.COBBLESTONE))).soldier();
        BlockPos top = helper.absolutePos(pillar(helper, 9, 11, 9, 11));
        double edge = top.getX() + 1;
        soldier.teleportTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
        helper.onEachTick(() -> {
            if (helper.getTick() > 20 && helper.getTick() < 100) {
                soldier.move(MoverType.SELF, new Vec3(0.2, 0, 0));
            }
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(soldier.isAlive(), "The builder died");
            helper.assertTrue(soldier.getY() >= top.getY() - 0.1 && soldier.getX() < edge + 0.1,
                    "BUG (slipping off a ledge): the builder left the pillar, it is at " + soldier.position());
            helper.succeed();
        });
    }

    /**
     * BUG TEST (stranded up high): a builder on top of a 4-high pillar has no way down (the drop is deadly, so it may not
     * take it) and its block to place is on the ground. It must be brought back beside its chest and finish the build
     * (keep and town hall builders stood on their upper floors with one-node paths for 30,000 ticks; D72).
     */
    @GameTest(template = PLATFORM, timeoutTicks = 2400)
    public static void strandedBuilderIsBroughtBack(GameTestHelper helper) {
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "stranded", 1, 1, 1, BlockPos.ZERO);
        ClaySoldierEntity soldier = ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.COBBLESTONE))).soldier();
        BlockPos top = helper.absolutePos(pillar(helper, 9, 11, 9, 11));
        boolean[] stranded = {false};
        // Strand it once it carries the block: it must have found its easel first (it looks for one only 3 blocks down).
        helper.onEachTick(() -> {
            if (!stranded[0] && soldier.getCarriedStack().is(Items.COBBLESTONE)) {
                stranded[0] = true;
                soldier.teleportTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(soldier.isAlive(), "The builder died");
            helper.assertTrue(stranded[0], "The builder never carried the block");
            helper.assertBlockPresent(Blocks.COBBLESTONE, SPOT);
        });
    }

    /**
     * BUG TEST (held at an edge): a builder standing on the edge of a 1x1 pillar, its center over the 4-block drop
     * beside it. Its path starts with that drop, which the edge guard won't let it take, so it stood still with a
     * complete path forever (noble house L1, farm L1, hospital L1). It must be brought back and finish the build.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 2400)
    public static void builderHeldAtAnEdgeIsBroughtBack(GameTestHelper helper) {
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "held_at_edge", 1, 1, 1, BlockPos.ZERO);
        ClaySoldierEntity soldier = ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.COBBLESTONE))).soldier();
        BlockPos top = helper.absolutePos(pillar(helper, 9, 9, 10, 10));
        double half = soldier.getBbWidth() / 2;
        boolean[] stranded = {false};
        helper.onEachTick(() -> {
            if (!stranded[0] && soldier.getCarriedStack().is(Items.COBBLESTONE)) {
                stranded[0] = true;
                // East edge of the pillar: the center over air, a sliver of the box still on the pillar.
                soldier.teleportTo(top.getX() + 1 + half * 0.5, top.getY(), top.getZ() + 0.5);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(soldier.isAlive(), "The builder died");
            helper.assertTrue(stranded[0], "The builder never carried the block");
            helper.assertBlockPresent(Blocks.COBBLESTONE, SPOT);
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

    /**
     * BUG TEST (penned in by a post): a soldier stands in the blueprint's only spot, beside where a fence's post would
     * be, not touching it (a tiny soldier fits). The fence must wait: placed, its post pens the soldier in (a cottage
     * builder stood inside a lantern post for 9,200 ticks on CI). Once the soldier is gone, it must be placed.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 1200)
    public static void fenceIsNotPlacedAroundASoldier(GameTestHelper helper) {
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "fence_around_soldier", 1, 1, 1, Blocks.OAK_FENCE, BlockPos.ZERO);
        ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.OAK_FENCE)));
        ClaySoldierEntity bystander = bystander(helper, SPOT, 0.12, 0.5);
        helper.runAfterDelay(400, () -> {
            helper.assertBlockPresent(Blocks.AIR, SPOT);
            helper.assertTrue(bystander.isAlive(), "The bystander died");
            bystander.discard();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 400, "Too early");
            helper.assertBlockPresent(Blocks.OAK_FENCE, SPOT);
        });
    }

    /**
     * BUG TEST (stuck inside a post): a soldier inside a fence's cell, beside the post. Vanilla starts its path in the
     * fence cell, which it may not enter, so every path is one step long. The soldier must get a real path to a spot
     * 4 blocks away and walk out of the cell.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 400)
    public static void soldierInsideAFenceCellWalksOut(GameTestHelper helper) {
        BlockPos cell = new BlockPos(4, ClaySoldierGameTests.GROUND_Y, 7);
        helper.setBlock(cell, Blocks.OAK_FENCE);
        BlockPos target = cell.east(4);
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(cell);
        ClaySoldierEntity soldier = ModEntityTypes.CLAY_SOLDIER_ENTITY.get().create(level);
        soldier.moveTo(at.getX() + 0.12, at.getY(), at.getZ() + 0.5, 0, 0);
        level.addFreshEntity(soldier);
        soldier.setClayTeamType(DevScenarios.RED_TEAM);
        BlockPos targetAbsolute = helper.absolutePos(target);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(soldier.blockPosition().equals(at), "The soldier is not inside the fence cell but at " + soldier.blockPosition().toShortString());
            Path path = soldier.getNavigation().createPath(targetAbsolute, 0);
            helper.assertTrue(path != null && path.getNodeCount() > 1 && path.canReach(),
                    "BUG (stuck inside a post): no path out of the fence cell, " + (path == null ? "null" : path.getNodeCount() + " node(s)"));
        });
        helper.onEachTick(() -> {
            if (helper.getTick() % 10 == 0 && soldier.blockPosition().equals(at)) {
                soldier.getNavigation().moveTo(targetAbsolute.getX() + 0.5, targetAbsolute.getY(), targetAbsolute.getZ() + 0.5, 1.0);
            }
        });
        helper.succeedWhen(() -> helper.assertFalse(soldier.blockPosition().equals(at), "BUG (stuck inside a post): the soldier is still in the fence cell"));
    }

    /**
     * BUG TEST (penned in a pocket): a builder inside a fence cell's corner pocket, between the post and the arms to the
     * north, south and east (farm L2/L3 on CI: three builders stood in one for 4,000 ticks, each with a path to the
     * chest it could never start walking, since the way out crossed the post). It must be standing outside the cell
     * soon after, and finish its build.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 1200)
    public static void builderInAFencePocketGetsOut(GameTestHelper helper) {
        BlockPos cell = new BlockPos(10, ClaySoldierGameTests.GROUND_Y, 10);
        helper.setBlock(cell, Blocks.OAK_FENCE);
        helper.setBlock(cell.north(), Blocks.OAK_FENCE);
        helper.setBlock(cell.south(), Blocks.OAK_FENCE);
        helper.setBlock(cell.east(), Blocks.OAK_FENCE);
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "fence_pocket", 1, 1, 1, BlockPos.ZERO);
        ClaySoldierGameTests.Builder builder = ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.COBBLESTONE, 4)));
        BlockPos at = helper.absolutePos(cell);
        ClaySoldierEntity soldier = builder.soldier();
        soldier.getNavigation().stop();
        soldier.setPos(at.getX() + 0.8, at.getY(), at.getZ() + 0.2); // the north-east pocket
        helper.runAfterDelay(2, () -> helper.assertTrue(soldier.blockPosition().equals(at),
                "The builder is not inside the fence cell but at " + soldier.blockPosition().toShortString()));
        helper.succeedWhen(() -> {
            helper.assertTrue(soldier.isAlive(), "The builder died");
            helper.assertFalse(soldier.blockPosition().equals(at), "BUG (penned in a pocket): the builder is still in the fence cell");
            helper.assertBlockPresent(Blocks.COBBLESTONE, SPOT);
        });
    }

    /**
     * BUG TEST (dropped into a pit): vanilla lets a mob drop up to 3 blocks into any cell, also into a 1x1 pit with
     * 2-high walls on every side, where no path leads out (three keep builders, 15,000 ticks in a shaft of the keep's
     * ground floor; D67). A soldier on the pit's rim planning a path to the pit's floor must not get one that ends in
     * the pit. Its sibling: a pit one block deep is fine (the soldier hops out), so that path must reach.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 100)
    public static void soldierDoesNotDropIntoAPit(GameTestHelper helper) {
        BlockPos pit = new BlockPos(8, ClaySoldierGameTests.GROUND_Y, 8);
        BlockPos shallow = new BlockPos(3, ClaySoldierGameTests.GROUND_Y, 12);
        ring(helper, pit, 2);
        ring(helper, shallow, 1);
        ServerLevel level = helper.getLevel();
        // with AI: a soldier without AI never lands (no gravity), and vanilla plans no path for a mob in the air
        ClaySoldierEntity onRim = walker(helper, pit.west().above(2));
        ClaySoldierEntity onLowRim = walker(helper, shallow.west().above(1));
        BlockPos pitAbsolute = helper.absolutePos(pit);
        BlockPos shallowAbsolute = helper.absolutePos(shallow);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(onRim.onGround() && onLowRim.onGround(), "The soldiers have not landed on the rims");
            Path deep = onRim.getNavigation().createPath(pitAbsolute, 0);
            helper.assertFalse(deep != null && deep.canReach() && deep.getEndNode() != null && deep.getEndNode().asBlockPos().equals(pitAbsolute),
                    "BUG (dropped into a pit): the path ends in the 2-deep pit");
            Path low = onLowRim.getNavigation().createPath(shallowAbsolute, 0);
            helper.assertTrue(low != null && low.canReach() && low.getEndNode() != null && low.getEndNode().asBlockPos().equals(shallowAbsolute),
                    "No path into the 1-deep pit, which the soldier can hop out of");
            helper.succeed();
        });
    }

    /** Cobblestone {@code height} high on the 8 cells around {@code center}, so {@code center} becomes a pit. */
    private static void ring(GameTestHelper helper, BlockPos center, int height) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    for (int dy = 0; dy < height; dy++) {
                        helper.setBlock(center.offset(dx, dy, dz), Blocks.COBBLESTONE);
                    }
                }
            }
        }
    }

    @AfterBatch(batch = "fall_trace")
    public static void stopRecording(ServerLevel level) {
        DebugLog.reset();
    }

    /** A clay soldier of the builders' team with its AI, standing at {@code pos} (it lands, and can plan paths). */
    private static ClaySoldierEntity walker(GameTestHelper helper, BlockPos pos) {
        ClaySoldierEntity soldier = bystander(helper, pos);
        soldier.setNoAi(false);
        return soldier;
    }

    /** A clay soldier of the builders' team standing still (no AI) at {@code pos}. */
    private static ClaySoldierEntity bystander(GameTestHelper helper, BlockPos pos) {
        return bystander(helper, pos, 0.5, 0.5);
    }

    /** The same, standing at ({@code dx}, {@code dz}) within the cell. */
    private static ClaySoldierEntity bystander(GameTestHelper helper, BlockPos pos, double dx, double dz) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(pos);
        ClaySoldierEntity soldier = ModEntityTypes.CLAY_SOLDIER_ENTITY.get().create(level);
        soldier.moveTo(at.getX() + dx, at.getY(), at.getZ() + dz, 0, 0);
        soldier.setNoAi(true);
        level.addFreshEntity(soldier);
        soldier.setClayTeamType(DevScenarios.RED_TEAM);
        return soldier;
    }
}
