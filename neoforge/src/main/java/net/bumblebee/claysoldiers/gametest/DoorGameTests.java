package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.bumblebee.claysoldiers.entity.goal.workgoal.BuildBlueprintGoal;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.init.ModEntityTypes;
import net.bumblebee.claysoldiers.init.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.CHEST;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.EASEL;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.GROUND_Y;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * Tests for clay soldiers using doors: opening a closed wooden door in their path, walking through, closing it
 * behind them. Iron doors stay closed. Each asserts the CORRECT behavior.
 * <p>
 * Layout: a square room with 2-high walls and an open top, its doorway at {@link #DOORWAY}. Builders can place blocks
 * up to 2 blocks sideways over a wall (Fix B), so only spots at least 3 blocks from every outside spot need the
 * door: the middle of a 5x5 room, or the middle 3x3 of a 7x7 room.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class DoorGameTests {
    /** The room's doorway (front wall middle). */
    static final BlockPos DOORWAY = EASEL.offset(0, 0, 2);
    /** The middle of the 5x5 room's floor. */
    static final BlockPos INSIDE = EASEL.offset(0, 0, 4);
    private static final ResourceLocation BLUE_TEAM = ResourceLocation.fromNamespaceAndPath("csr", "blue");

    /**
     * A builder must place a slab inside a closed room: the only way in is a closed wooden door.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 800)
    public static void builderEntersClosedRoomThroughWoodenDoor(GameTestHelper helper) {
        hutWithDoor(helper, Blocks.OAK_DOOR, false);
        slabInsideWithBuilders(helper, 1, INSIDE);
        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.COBBLESTONE_SLAB, INSIDE));
    }

    /**
     * After a builder has gone through a closed wooden door, the door is closed again: it was opened at some point,
     * the slab inside got placed, and once the builder has left the doorway the door is closed.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 1000)
    public static void doorIsClosedAgainAfterBuilderPasses(GameTestHelper helper) {
        hutWithDoor(helper, Blocks.OAK_DOOR, false);
        List<ClaySoldierEntity> builders = slabInsideWithBuilders(helper, 1, INSIDE);
        boolean[] wasOpened = {false};
        helper.onEachTick(() -> wasOpened[0] |= isOpen(helper));
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE_SLAB, INSIDE);
            helper.assertTrue(wasOpened[0], "The door was never opened");
            helper.assertTrue(noneInDoorway(helper, builders), "A builder is still in the doorway");
            helper.assertTrue(!isOpen(helper), "The door was left open");
        });
    }

    /**
     * Iron doors stay closed to everyone: the slab inside a room closed by an iron door is never placed and the door
     * never opens.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void ironDoorStaysClosed(GameTestHelper helper) {
        hutWithDoor(helper, Blocks.IRON_DOOR, false);
        slabInsideWithBuilders(helper, 1, INSIDE);
        helper.onEachTick(() -> {
            if (isOpen(helper)) {
                helper.fail("The iron door was opened");
            }
        });
        helper.runAtTickTime(500, () -> {
            helper.assertBlockPresent(Blocks.AIR, INSIDE);
            helper.succeed();
        });
    }

    /**
     * A door that is already open still works: the builder walks through and places the slab. Soldiers only close
     * doors they opened themselves, so a door a player left open stays open.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 800)
    public static void alreadyOpenDoorStillWorks(GameTestHelper helper) {
        hutWithDoor(helper, Blocks.OAK_DOOR, true);
        slabInsideWithBuilders(helper, 1, INSIDE);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE_SLAB, INSIDE);
            helper.assertTrue(isOpen(helper), "A soldier closed a door it did not open");
        });
    }

    /**
     * Two builders use the same closed door at about the same time (a 7x7 room with two slabs that can only be placed
     * from inside, both builders start together). Neither may get stuck: both slabs get placed and the door ends up
     * closed.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 1600)
    public static void twoBuildersShareADoorWithoutDeadlock(GameTestHelper helper) {
        roomWithDoor(helper, 7, Blocks.OAK_DOOR, false);
        BlockPos left = EASEL.offset(-1, 0, 5);
        BlockPos right = EASEL.offset(1, 0, 5);
        List<ClaySoldierEntity> builders = slabsInRoomWithBuilders(helper, 7, 2, left, right);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE_SLAB, left);
            helper.assertBlockPresent(Blocks.COBBLESTONE_SLAB, right);
            helper.assertTrue(noneInDoorway(helper, builders), "A builder is still in the doorway");
            helper.assertTrue(!isOpen(helper), "The door was left open");
        });
    }

    /**
     * The old locked-out case from the 2026-10-03 evening recording: a Small House that is finished except for the
     * center plank of the roof, its door already closed. That plank can only be placed from inside the house (every
     * spot within reach is inside). One builder with the plank must finish the house.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 1500)
    public static void lockedOutHouseGetsItsRoofFinished(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlueprintData house = level.registryAccess().registryOrThrow(ModRegistries.BLUEPRINTS).get(DevScenarios.SMALL_HOUSE);
        helper.assertTrue(house != null && house.isValid(), "csr:small_house is not loaded");
        EaselBlockEntity easel = DevScenarios.placeEasel(level, helper.absolutePos(EASEL), Direction.NORTH, house);

        // Build everything by hand (the easel fills the first free spot for each item, in build order), keeping back
        // one oak plank: the last plank in the order is the roof's center.
        List<ItemStack> needed = easel.getRequiredItems();
        boolean keptOnePlank = false;
        for (ItemStack stack : needed) {
            for (int i = 0; i < stack.getCount(); i++) {
                if (stack.is(Items.OAK_PLANKS) && !keptOnePlank) {
                    keptOnePlank = true;
                    continue;
                }
                helper.assertTrue(easel.tryPlacingSoldier(new ItemStack(stack.getItem())).isSuccess(),
                        "Setup: could not hand-place " + stack.getItem());
            }
        }
        // Local (3, 6, 3) is the roof's center; with the easel facing north it's at EASEL + (3 - 3, 6, 2 + 3).
        BlockPos roofCenter = EASEL.offset(0, 6, 5);
        BlockPos door = EASEL.offset(-2, 1, 5);
        helper.assertTrue(easel.getRequiredItems().size() == 1 && easel.getRequiredItems().getFirst().is(Items.OAK_PLANKS)
                && easel.getRequiredItems().getFirst().getCount() == 1, "Setup: expected 1 plank left, got " + easel.getRequiredItems());
        helper.assertBlockPresent(Blocks.AIR, roofCenter);
        helper.assertBlockState(door, state -> state.getBlock() instanceof DoorBlock && !state.getValue(DoorBlock.OPEN),
                () -> "Setup: expected a closed door at the house's entrance");

        DevScenarios.placeChest(level, helper.absolutePos(CHEST), List.of(new ItemStack(Items.OAK_PLANKS)));
        DevScenarios.spawnWorker(level, helper.absolutePos(ClaySoldierGameTests.SOLDIER), DevScenarios.RED_TEAM,
                ClaySoldierGameTests.owner(level), BuildBlueprintGoal.class, helper.absolutePos(CHEST));

        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.OAK_PLANKS, roofCenter);
            helper.assertTrue(easel.isFinished(), "The house is not finished");
        });
    }

    /**
     * A combat soldier chases its target through a closed wooden door: the target (an enemy soldier that doesn't move)
     * stands inside the closed hut; the attacker outside must get in and hurt it.
     */
    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void combatSoldierChasesTargetThroughClosedDoor(GameTestHelper helper) {
        hutWithDoor(helper, Blocks.OAK_DOOR, false);
        ClaySoldierEntity target = spawnSoldier(helper, INSIDE, BLUE_TEAM);
        target.setNoAi(true);
        ClaySoldierEntity attacker = spawnSoldier(helper, new BlockPos(4, GROUND_Y, 3), DevScenarios.RED_TEAM);
        attacker.setTarget(target);
        float startHealth = target.getHealth();
        helper.onEachTick(() -> {
            if (attacker.getTarget() == null && target.isAlive()) {
                attacker.setTarget(target); // keep the order, the test is about the path, not target selection
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(!target.isAlive() || target.getHealth() < startHealth,
                "The target inside the closed hut was not hurt (attacker at %s)".formatted(attacker.blockPosition().toShortString())));
    }

    /**
     * Builds the 5x5 hut walls with a door of the given type in the doorway, closed or open.
     */
    static void hutWithDoor(GameTestHelper helper, Block doorType, boolean open) {
        roomWithDoor(helper, 5, doorType, open);
    }

    /**
     * Builds a square room ({@code size} wide, odd, 2-high walls, open top) where a {@code size}-wide blueprint on the
     * test easel goes (local (x, y, z) is EASEL + (x - size / 2, y, 2 + z)), with a door of the given type in the
     * front wall's middle ({@link #DOORWAY}), closed or open.
     */
    static void roomWithDoor(GameTestHelper helper, int size, Block doorType, boolean open) {
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    boolean wall = x == 0 || x == size - 1 || z == 0 || z == size - 1;
                    if (wall && !(x == size / 2 && z == 0)) {
                        helper.setBlock(EASEL.offset(x - size / 2, y, 2 + z), Blocks.COBBLESTONE);
                    }
                }
            }
        }
        BlockState lower = doorType.defaultBlockState().setValue(DoorBlock.OPEN, open);
        helper.setBlock(DOORWAY, lower);
        helper.setBlock(DOORWAY.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /**
     * A blueprint with a cobblestone slab at each given spot inside the 5x5 hut, a chest with the slabs and
     * {@code builderCount} builders at the chest. Returns the builders.
     */
    static List<ClaySoldierEntity> slabInsideWithBuilders(GameTestHelper helper, int builderCount, BlockPos... spots) {
        return slabsInRoomWithBuilders(helper, 5, builderCount, spots);
    }

    /**
     * Like {@link #slabInsideWithBuilders} for a room of the given size.
     */
    static List<ClaySoldierEntity> slabsInRoomWithBuilders(GameTestHelper helper, int size, int builderCount, BlockPos... spots) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (BlockPos spot : spots) {
            // relative position -> local position of the room blueprint: EASEL + (x - size / 2, y, 2 + z)
            BlockPos offset = spot.subtract(EASEL);
            blocks.put(new BlockPos(offset.getX() + size / 2, offset.getY(), offset.getZ() - 2), Blocks.COBBLESTONE_SLAB.defaultBlockState());
        }
        BlueprintData blueprint = BuildRequestGameTests.blueprint(helper, "slabs_in_room", size, 2, size, blocks);
        ClaySoldierGameTests.Builder first = ClaySoldierGameTests.setUpBuilder(helper, blueprint,
                List.of(new ItemStack(Items.COBBLESTONE_SLAB, spots.length)));
        List<ClaySoldierEntity> builders = new ArrayList<>(List.of(first.soldier()));
        for (int i = 1; i < builderCount; i++) {
            builders.add(DevScenarios.spawnWorker(helper.getLevel(), helper.absolutePos(ClaySoldierGameTests.SOLDIER), DevScenarios.RED_TEAM,
                    ClaySoldierGameTests.owner(helper.getLevel()), BuildBlueprintGoal.class, helper.absolutePos(CHEST)));
        }
        return builders;
    }

    static boolean isOpen(GameTestHelper helper) {
        BlockState state = helper.getBlockState(DOORWAY);
        return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN);
    }

    static boolean noneInDoorway(GameTestHelper helper, List<ClaySoldierEntity> soldiers) {
        BlockPos doorway = helper.absolutePos(DOORWAY);
        return soldiers.stream().noneMatch(soldier -> soldier.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(doorway)));
    }

    /**
     * Spawns a fighting clay soldier (no wheat, no job) of the given team.
     */
    static ClaySoldierEntity spawnSoldier(GameTestHelper helper, BlockPos pos, ResourceLocation team) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(pos);
        ClaySoldierEntity soldier = ModEntityTypes.CLAY_SOLDIER_ENTITY.get().create(level);
        soldier.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
        level.addFreshEntity(soldier);
        soldier.setClayTeamType(team);
        return soldier;
    }
}
