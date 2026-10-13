package net.bumblebee.claysoldiers.gametest;

import com.google.gson.JsonParser;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.BlueprintRequest;
import net.bumblebee.claysoldiers.blueprint.BlueprintTemplateSettings;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.plan.BuildPlan;
import net.bumblebee.claysoldiers.blueprint.templates.ImmutableTemplate;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.bumblebee.claysoldiers.structure.StructureBlueprints;
import net.bumblebee.claysoldiers.structure.StructureMeta;
import net.bumblebee.claysoldiers.structure.StructureRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-baked build plans (D82): every promoted structure level has a plan made from its current file, a blueprint made
 * from a structure follows its plan's order, the plan survives saving, and builders build from it.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class BuildPlanGameTests {

    private static ServerBlueprintPlan serverPlan(ServerLevel level, StructureMeta meta, int detailLevel) {
        BlueprintData data = StructureBlueprints.blueprint(level, meta, detailLevel);
        return data.getTemplate().createServer().orElseThrow(() -> new GameTestAssertException("no server plan for " + meta.id()));
    }

    @Nullable
    private static BuildPlan buildPlan(ServerLevel level, StructureMeta meta, int detailLevel) {
        return StructureBlueprints.blueprint(level, meta, detailLevel).getTemplate() instanceof ImmutableTemplate template ? template.buildPlan() : null;
    }

    private static List<BlockPos> order(ServerBlueprintPlan plan) {
        return plan.targetPositions(BlockPos.ZERO, new BlueprintTemplateSettings(plan.getSize(), Mirror.NONE, Rotation.NONE));
    }

    private static StructureMeta meta(ServerLevel level, String id) {
        return StructureRegistry.get(level.getServer()).byId(ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, id))
                .orElseThrow(() -> new GameTestAssertException("no registry entry " + id));
    }

    /** Every level of every structure promoted from the Studio has a plan that knows each of its blocks (no stale plan). */
    @GameTest(template = ClaySoldierGameTests.PLATFORM)
    public static void everyStudioLevelHasAMatchingPlan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        for (StructureBuildGameTests.Entry entry : StructureBuildGameTests.STRUCTURES) {
            StructureMeta meta = meta(level, entry.path());
            for (int lv = 1; lv <= meta.levelCount(); lv++) {
                BuildPlan plan = buildPlan(level, meta, lv);
                if (plan == null) {
                    problems.add(entry.path() + " L" + lv + ": no plan");
                    continue;
                }
                int unknown = 0;
                for (BlockPos pos : order(serverPlan(level, meta, lv))) {
                    if (!plan.knows(pos)) {
                        unknown++;
                    }
                }
                if (unknown > 0) {
                    problems.add(entry.path() + " L" + lv + ": " + unknown + " blocks the plan doesn't know");
                }
            }
        }
        helper.assertTrue(problems.isEmpty(), "build plans out of date (run python3 tools/build_plan.py): " + problems);
        helper.succeed();
    }

    /** A structure's blueprint hands out its blocks in the plan's order: waves never go down along the list. */
    @GameTest(template = ClaySoldierGameTests.PLATFORM)
    public static void blueprintFollowsThePlanOrder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        StructureMeta meta = meta(level, "well");
        BuildPlan plan = buildPlan(level, meta, 1);
        helper.assertTrue(plan != null && plan.plannedBlocks() > 0, "the well has no plan");
        List<BlockPos> order = order(serverPlan(level, meta, 1));
        int last = -1;
        for (BlockPos pos : order) {
            int wave = plan.wave(pos);
            helper.assertTrue(wave >= last, "wave " + wave + " at " + pos + " comes after wave " + last);
            last = wave;
        }
        helper.assertTrue(plan.waves() > 1, "one wave only: " + plan);
        helper.succeed();
    }

    /** An easel's build keeps its plan (order and waves) across saving and loading. */
    @GameTest(template = ClaySoldierGameTests.PLATFORM)
    public static void planSurvivesSaveAndLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerBlueprintPlan plan = serverPlan(level, meta(level, "well"), 1);
        ServerBlueprintPlan loaded = ServerBlueprintPlan.load(plan.save(new CompoundTag()), level.registryAccess());
        helper.assertTrue(loaded.buildPlan() != null, "the plan was lost on saving");
        helper.assertTrue(loaded.buildPlan().plannedBlocks() == plan.buildPlan().plannedBlocks()
                && loaded.buildPlan().waves() == plan.buildPlan().waves(), "saved " + plan.buildPlan() + ", loaded " + loaded.buildPlan());
        helper.assertTrue(order(loaded).equals(order(plan)), "the order changed on saving");
        BlockPos first = order(plan).get(0);
        helper.assertTrue(loaded.buildPlan().spots(first).equals(plan.buildPlan().spots(first)), "standing spots changed on saving");
        helper.succeed();
    }

    /**
     * A builder carrying a stack (D79) takes its next nearby block from a later wave only when that block isn't where a
     * block of an earlier wave still to place is placed from: here the cobblestone next to the one it placed is in a later
     * wave and on the wave-0 cobblestone's planned standing spot, so it gets the one further away in the current wave.
     */
    @GameTest(template = ClaySoldierGameTests.PLATFORM)
    public static void followUpKeepsOffAnEarlierBlocksSpot(GameTestHelper helper) {
        // Spot of the wave-0 block at (0, 0, 2), encoded (dx + 2) + 5 * (dz + 2) + 25 * (dy + 8): dz = 2 is the wave-1 block.
        BlockPos next = followUp(helper, "follow_up_spot", "{\"format\": 1, \"blocks\": [[0, 0, 0, 0, 222], [0, 0, 2, 1]]}");
        BlockPos waveZero = helper.absolutePos(ClaySoldierGameTests.EASEL.offset(0, 0, 2));
        helper.assertTrue(waveZero.equals(next), "The follow-up should be the wave-0 cobblestone at " + waveZero.toShortString() + ", got " + next);
        helper.succeed();
    }

    /**
     * A builder carrying a stack goes on into the next waves (build.plan_lookahead_waves) when the block is off every
     * earlier block's standing spot: here it takes the nearer, wave-1 cobblestone (with only the current wave, stacks were
     * cut short at every wave's end and builds made twice the chest trips).
     */
    @GameTest(template = ClaySoldierGameTests.PLATFORM)
    public static void followUpGoesOnIntoTheNextWave(GameTestHelper helper) {
        BlockPos next = followUp(helper, "follow_up_wave", "{\"format\": 1, \"blocks\": [[0, 0, 0, 0], [0, 0, 2, 1]]}");
        BlockPos waveOne = helper.absolutePos(ClaySoldierGameTests.EASEL.offset(0, 0, 4));
        helper.assertTrue(waveOne.equals(next), "The follow-up should be the wave-1 cobblestone at " + waveOne.toShortString() + ", got " + next);
        helper.succeed();
    }

    /** The follow-up after placing a cobblestone at local (0, 0, 3), with two more to place at (0, 0, 0) and (0, 0, 2). */
    private static BlockPos followUp(GameTestHelper helper, String name, String planJson) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        blocks.put(new BlockPos(0, 0, 0), Blocks.COBBLESTONE.defaultBlockState());
        blocks.put(new BlockPos(0, 0, 2), Blocks.COBBLESTONE.defaultBlockState());
        BlueprintData plain = BuildRequestGameTests.blueprint(helper, name, 1, 1, 5, blocks);
        BuildPlan plan = BuildPlan.fromJson(JsonParser.parseString(planJson).getAsJsonObject());
        BlueprintData blueprint = new BlueprintData(ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "gametest/" + name), "gametest." + name, 0);
        blueprint.bindStructure(((ImmutableTemplate) plain.getTemplate()).withPlan(plan));
        EaselBlockEntity easel = DevScenarios.placeEasel(helper.getLevel(), helper.absolutePos(ClaySoldierGameTests.EASEL), Direction.NORTH, blueprint);
        BlockPos placed = helper.absolutePos(ClaySoldierGameTests.EASEL.offset(0, 0, 5)); // local (0, 0, 3): next to the wave-1 block
        BlueprintRequest next = easel.getBlueprintRequestHandler().getFollowUpRequest(pos -> true, Items.COBBLESTONE, placed);
        return next == null ? null : next.getPos();
    }

    /**
     * A planned block builders gave up twice (keep L1: a chest the plan's spots said was reachable, handed out and given up
     * for 6,000 ticks) is left to the normal search: handed out without the plan's standing spots, so the builder looks for
     * a spot the normal way (and scaffolding can start when there is none).
     */
    @GameTest(template = ClaySoldierGameTests.PLATFORM)
    public static void blockGivenUpTwiceLeavesThePlan(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        blocks.put(new BlockPos(0, 0, 0), Blocks.COBBLESTONE.defaultBlockState());
        BlueprintData plain = BuildRequestGameTests.blueprint(helper, "given_up", 1, 1, 1, blocks);
        // One spot, beside the block: (dx + 2) + 5 * (dz + 2) + 25 * (dy + 8) with dx = 1.
        BuildPlan plan = BuildPlan.fromJson(JsonParser.parseString("{\"format\": 1, \"blocks\": [[0, 0, 0, 0, 213]]}").getAsJsonObject());
        BlueprintData blueprint = new BlueprintData(ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "gametest/given_up"), "gametest.given_up", 0);
        blueprint.bindStructure(((ImmutableTemplate) plain.getTemplate()).withPlan(plan));
        EaselBlockEntity easel = DevScenarios.placeEasel(helper.getLevel(), helper.absolutePos(ClaySoldierGameTests.EASEL), Direction.NORTH, blueprint);

        for (int giveUp = 0; giveUp < ServerBlueprintPlan.PLAN_GIVE_UPS; giveUp++) {
            BlueprintRequest request = easel.getBlueprintRequestHandler().getRequest(pos -> true);
            helper.assertTrue(request != null && !request.standingSpots().isEmpty(), "Give-up " + giveUp + ": the block should come with the plan's spot, got " + request);
            request.giveUp();
        }
        BlueprintRequest request = easel.getBlueprintRequestHandler().getRequest(pos -> true);
        helper.assertTrue(request != null && request.standingSpots().isEmpty(), "After " + ServerBlueprintPlan.PLAN_GIVE_UPS
                + " give-ups the block should be handed out without the plan's spots, got " + (request == null ? null : request.standingSpots()));
        helper.succeed();
    }

    /**
     * Builders place every block of the well's level 1 from its plan (with the normal order, the chain and logs above the
     * shaft ended up out of reach: 49 of 54 in the offline replay, unfinished in the structure builds). Only "every block
     * placed" is checked: the file's poppies stand on air, so they drop and the finished well never matches the file.
     */
    @GameTest(template = "gametest/structure_build", timeoutTicks = 12000, batch = "build_plan_well", skyAccess = true)
    public static void buildersBuildTheWellFromItsPlan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        DevScenarios.StructureBuildResult setup = DevScenarios.structureBuild(level, ClaySoldierGameTests.owner(level),
                helper.absolutePos(new BlockPos(24, 2, 3)), Direction.SOUTH, meta(level, "well"), 1);
        helper.assertTrue(setup.easel().getPlan() instanceof ServerBlueprintPlan plan && plan.buildPlan() != null, "the well's build has no plan");
        helper.onEachTick(() -> setup.supply().tick());
        helper.succeedWhen(() -> helper.assertTrue(setup.easel().isFinished(), "not every block placed yet"));
    }
}
