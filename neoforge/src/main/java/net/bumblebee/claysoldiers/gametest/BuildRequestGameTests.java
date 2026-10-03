package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.BlueprintRequest;
import net.bumblebee.claysoldiers.blueprint.BlueprintTemplateSettings;
import net.bumblebee.claysoldiers.blueprint.templates.BlueprintBlockInfo;
import net.bumblebee.claysoldiers.blueprint.templates.ImmutableTemplate;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.capability.BlueprintRequestHandler;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugEvent;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.bumblebee.claysoldiers.entity.goal.workgoal.BuildBlueprintGoal;
import net.bumblebee.claysoldiers.init.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.EASEL;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.GROUND_Y;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * Bug tests for the four problems found in the 2026-10-03 recording (stair ping-pong):
 * placing at the wrong spot, builders taking blocks from each other, jigsaw blocks sorted last,
 * and a door locking builders out. Each asserts the CORRECT behavior.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildRequestGameTests {

    /**
     * BUG TEST (placed at the wrong spot): two spots want cobblestone; the request for the SECOND spot must fill
     * the second spot and leave the first empty. Suspected cause: placing ignores the request's position and fills
     * the first free spot for that block type.
     */
    @GameTest(template = PLATFORM)
    public static void placedAtRequestedSpot(GameTestHelper helper) {
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "two_spots", 1, 1, 3,
                new BlockPos(0, 0, 0), new BlockPos(0, 0, 2));
        EaselBlockEntity easel = DevScenarios.placeEasel(helper.getLevel(), helper.absolutePos(EASEL), Direction.NORTH, blueprint);
        BlockPos firstSpot = helper.absolutePos(EASEL.offset(0, 0, 2));
        BlockPos secondSpot = helper.absolutePos(EASEL.offset(0, 0, 4));

        BlueprintRequestHandler handler = easel.getBlueprintRequestHandler();
        BlueprintRequest first = handler.getRequest(pos -> true);
        BlueprintRequest second = handler.getRequest(pos -> true);
        helper.assertTrue(first != null && first.getPos().equals(firstSpot), "Setup: first request should be the first spot, got " + first);
        helper.assertTrue(second != null && second.getPos().equals(secondSpot), "Setup: second request should be the second spot, got " + second);

        second.setPlacing();
        handler.doRequest(second);
        helper.assertTrue(helper.getLevel().getBlockState(secondSpot).is(Blocks.COBBLESTONE),
                "BUG (wrong spot): the requested second spot is still empty");
        helper.assertTrue(helper.getLevel().getBlockState(firstSpot).isAir(),
                "BUG (wrong spot): the block went to the first spot instead of the requested one");
        helper.succeed();
    }

    /**
     * BUG TEST (ping-pong): one reachable ground block 9 blocks from the easel (like the doorstep in the recording),
     * 2 workers, a chest with one matching block. The block is placed within ~300 ticks and builders take a block
     * from each other at most once.
     */
    @GameTest(template = PLATFORM, batch = "build_request_ping_pong", timeoutTicks = 300)
    public static void noBlockPingPong(GameTestHelper helper) {
        DebugLog.reset();
        DebugLog.setEnabled(DebugCategory.BUILD, true, helper.getLevel().getServer());
        BlueprintData blueprint = ClaySoldierGameTests.tinyBlueprint(helper, "far_block", 1, 1, 8, new BlockPos(0, 0, 7));
        ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.COBBLESTONE, 1)));
        DevScenarios.spawnWorker(helper.getLevel(), helper.absolutePos(new BlockPos(4, GROUND_Y, 4)), DevScenarios.RED_TEAM,
                ClaySoldierGameTests.owner(helper.getLevel()), BuildBlueprintGoal.class, helper.absolutePos(ClaySoldierGameTests.CHEST));
        BlockPos target = EASEL.offset(0, 0, 9);

        helper.onEachTick(() -> {
            int takeovers = takeovers();
            if (takeovers > 1) {
                DebugLog.reset();
                helper.fail("BUG (ping-pong): builders took the block from each other " + takeovers + " times");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.COBBLESTONE, target);
            DebugLog.reset();
        });
    }

    /**
     * A builder must always give up (and release) its request before the plan treats the request as stale,
     * otherwise a block could be handed out twice while its builder is still working on it.
     */
    @GameTest(template = PLATFORM)
    public static void staleTimeoutLongerThanGiveUpTimer(GameTestHelper helper) {
        helper.assertTrue(BlueprintBlockInfo.STALE_REQUEST_TICKS > BuildBlueprintGoal.MAX_CARRY_TICKS,
                "STALE_REQUEST_TICKS (%d) must be longer than MAX_CARRY_TICKS (%d)"
                        .formatted(BlueprintBlockInfo.STALE_REQUEST_TICKS, BuildBlueprintGoal.MAX_CARRY_TICKS));
        helper.succeed();
    }

    private static int takeovers() {
        return DebugLog.events().stream()
                .filter(event -> event.category() == DebugCategory.BUILD && event.message().contains("taking over"))
                .mapToInt(DebugEvent::count).sum();
    }

    /**
     * BUG TEST (jigsaw ordering): in csr:small_house the doorstep stair and a floor plank are jigsaw connectors in the
     * vanilla structure file. Their leftover jigsaw data must not push them behind the roof: both must be handed out
     * before every roof stair.
     */
    @GameTest(template = PLATFORM)
    public static void jigsawBlocksSortWithTheirGroup(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlueprintData house = level.registryAccess().registryOrThrow(ModRegistries.BLUEPRINTS)
                .get(ResourceLocation.fromNamespaceAndPath("csr", "small_house"));
        helper.assertTrue(house != null && house.isValid(), "csr:small_house is not loaded");
        ServerBlueprintPlan plan = house.createServerPlan().orElseThrow();
        List<BlockPos> order = plan.targetPositions(BlockPos.ZERO, new BlueprintTemplateSettings(plan.getSize(), Mirror.NONE, Rotation.NONE));

        StructureTemplate template = level.getStructureManager().get(house.location()).orElseThrow();
        StructurePlaceSettings raw = new StructurePlaceSettings();
        List<BlockPos> jigsawBlocks = template.filterBlocks(BlockPos.ZERO, raw, Blocks.JIGSAW).stream()
                .filter(info -> info.nbt() != null && !info.nbt().getString("final_state").contains("structure_void"))
                .map(StructureTemplate.StructureBlockInfo::pos).toList();
        List<BlockPos> roofStairs = template.filterBlocks(BlockPos.ZERO, raw, Blocks.OAK_STAIRS).stream()
                .map(StructureTemplate.StructureBlockInfo::pos).filter(pos -> pos.getY() >= 4).toList();
        helper.assertTrue(jigsawBlocks.size() == 2 && !roofStairs.isEmpty(),
                "Setup: expected 2 jigsaw blocks and roof stairs, got " + jigsawBlocks + " / " + roofStairs.size());

        int firstRoofStair = roofStairs.stream().mapToInt(order::indexOf).min().orElseThrow();
        for (BlockPos jigsaw : jigsawBlocks) {
            int index = order.indexOf(jigsaw);
            helper.assertTrue(index >= 0, "Jigsaw block " + jigsaw.toShortString() + " is missing from the plan");
            helper.assertTrue(index < firstRoofStair, "BUG (jigsaw ordering): block at %s is handed out as #%d, after the first roof stair (#%d)"
                    .formatted(jigsaw.toShortString(), index + 1, firstRoofStair + 1));
        }
        helper.succeed();
    }

    /**
     * BUG TEST (door locks builders out): a 5x5 hut whose 2-high walls already stand, and a blueprint with only the
     * door in the front wall and a slab inside, one worker. Clay soldiers can't open doors, so the inside slab must be
     * handed out before the door; it must get placed.
     * (The walls are pre-built so the test only exercises the door/slab order and finishes quickly.)
     */
    @GameTest(template = PLATFORM, timeoutTicks = 600)
    public static void doorDoesNotLockOutBuilders(GameTestHelper helper) {
        buildHutWalls(helper);
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        blocks.put(new BlockPos(2, 0, 0), Blocks.OAK_DOOR.defaultBlockState());
        BlockPos insideLocal = new BlockPos(2, 0, 2);
        blocks.put(insideLocal, Blocks.COBBLESTONE_SLAB.defaultBlockState());
        BlueprintData blueprint = blueprint(helper, "door_hut", 5, 2, 5, blocks);
        ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.COBBLESTONE_SLAB), new ItemStack(Items.OAK_DOOR)));
        BlockPos inside = EASEL.offset(insideLocal.getX() - 2, 0, 2 + insideLocal.getZ());

        helper.succeedWhen(() -> helper.assertBlockPresent(Blocks.COBBLESTONE_SLAB, inside));
    }

    /**
     * Builds the walls of a 5x5 hut (2 high, doorway at the front middle) where a 5x2x5 blueprint on the test easel
     * goes: local (x, y, z) is EASEL + (x - 2, y, 2 + z).
     */
    private static void buildHutWalls(GameTestHelper helper) {
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 5; x++) {
                for (int z = 0; z < 5; z++) {
                    boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
                    if (wall && !(x == 2 && z == 0)) {
                        helper.setBlock(EASEL.offset(x - 2, y, 2 + z), Blocks.COBBLESTONE);
                    }
                }
            }
        }
    }

    /**
     * Recorder check for the door theory: a hut with a closed door is already standing and the blueprint only wants a
     * slab inside. With BUILD recording on, the builder's search must name the closed door as the reason.
     */
    @GameTest(template = PLATFORM, batch = "build_request_door_reason", timeoutTicks = 300)
    public static void unreachableReasonNamesClosedDoor(GameTestHelper helper) {
        DebugLog.reset();
        DebugLog.setEnabled(DebugCategory.BUILD, true, helper.getLevel().getServer());
        buildHutWalls(helper);
        BlockPos door = EASEL.offset(0, 0, 2);
        helper.setBlock(door, Blocks.OAK_DOOR.defaultBlockState());
        helper.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));

        BlueprintData blueprint = blueprint(helper, "slab_inside", 5, 2, 5, Map.of(new BlockPos(2, 0, 2), Blocks.COBBLESTONE_SLAB.defaultBlockState()));
        ClaySoldierGameTests.setUpBuilder(helper, blueprint, List.of(new ItemStack(Items.COBBLESTONE_SLAB)));

        helper.succeedWhen(() -> {
            helper.assertTrue(DebugLog.contains(DebugCategory.BUILD, "closed door in the way"),
                    "No 'closed door in the way' reason recorded yet (" + DebugLog.size() + " events)");
            DebugLog.reset();
        });
    }

    /**
     * Builds an unregistered blueprint from a map of local positions to block states.
     */
    static BlueprintData blueprint(GameTestHelper helper, String name, int sizeX, int sizeY, int sizeZ, Map<BlockPos, BlockState> blocks) {
        CompoundTag tag = new CompoundTag();
        tag.put("size", ClaySoldierGameTests.intList(sizeX, sizeY, sizeZ));
        List<BlockState> palette = new ArrayList<>();
        ListTag paletteTag = new ListTag();
        ListTag blocksTag = new ListTag();
        blocks.forEach((pos, state) -> {
            if (!palette.contains(state)) {
                palette.add(state);
                paletteTag.add(NbtUtils.writeBlockState(state));
            }
            CompoundTag block = new CompoundTag();
            block.put("pos", ClaySoldierGameTests.intList(pos.getX(), pos.getY(), pos.getZ()));
            block.putInt("state", palette.indexOf(state));
            blocksTag.add(block);
        });
        tag.put("palette", paletteTag);
        tag.put("blocks", blocksTag);
        var template = ImmutableTemplate.create(helper.getLevel().registryAccess().lookupOrThrow(Registries.BLOCK), tag, List.of());
        BlueprintData data = new BlueprintData(ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "gametest/" + name), "gametest." + name, 0);
        data.bindStructure(template);
        return data;
    }
}
