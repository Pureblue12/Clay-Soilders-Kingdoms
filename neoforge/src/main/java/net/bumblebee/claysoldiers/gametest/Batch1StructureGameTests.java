package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.bumblebee.claysoldiers.entity.goal.workgoal.BuildBlueprintGoal;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.structure.StructureBlueprints;
import net.bumblebee.claysoldiers.structure.StructureFidelity;
import net.bumblebee.claysoldiers.structure.StructureMeta;
import net.bumblebee.claysoldiers.structure.StructurePaster;
import net.bumblebee.claysoldiers.structure.StructureRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Structures batch 1 (look A, docs/STRUCTURES.md): for every structure and detail level, the registry entry is valid,
 * a paste is undone exactly, and 3 builders build it through the blueprint pipeline from a stocked chest: every block
 * placed, the result matching the file block by block, in a time at most {@link #TIME_TOLERANCE} over the doc's estimate.
 * Plus the file rules (levels only add blocks; shell and fittings only KNOWN-OK blocks; nothing BROKEN placed) and an
 * optional test of building level 2 on a finished level 1 (the upgrade-in-place gap, not fixed here).
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
public class Batch1StructureGameTests {
    static final String TEMPLATE = ClaySoldiersCommon.MOD_ID + ":gametest/terrain";
    /** Build time may differ from the doc's estimate by this much (fraction of the estimate). */
    static final double TIME_TOLERANCE = 0.5;
    /**
     * Ticks every build may take on top of the estimate and its tolerance: fixed waits that don't grow with the build (a
     * builder's break, 86 ticks on CI; a refused block's retry). Was a floor of 200 (the larger of this and the tolerance);
     * since builds follow a plan (D82) the order within a wave is random, and small builds spread by about this much from
     * run to run on CI: road L3 202 to 474 ticks (estimate 253), hovel L3 310 to 653 (estimate 429), in CI runs 38473736321,
     * 38477495214 and 38482122076. Added to the tolerance, so a big build is still checked at 50% over the model.
     */
    static final long FIXED_WAIT_SLACK = 300;
    /**
     * The doc's build-time model (docs/STRUCTURES.md, rule 4, D63): with 3 builders, a block within reach takes this many
     * ticks per block of the structure's width plus depth (builders walk farther on bigger footprints). Was 1.4 (the
     * Small House's measured 22.5 ticks per block); since builders carry a stack and place nearby blocks in a row (D79)
     * the batch-1 builds take 0.4 to 1.15 of that (CI runs 38220105839 and 38220816998: cottage L1 about 1,500 ticks against
     * 3,405, hovel L2 539 against 490). How much faster depends on how many blocks of one item a layer has, so the time
     * is only checked from above: a build may be faster than the model, not slower than it plus the tolerance.
     */
    static final double TICKS_PER_BLOCK_PER_SIDE = 1.1;

    /**
     * One batch-1 structure: id path, width plus depth, and its block count per level as the doc lists it (level 1 =
     * shell + fittings, then + flourish 2, then + flourish 3; air excluded, doors and beds count both halves). The
     * number of counts is the number of levels.
     */
    record Entry(String path, int sides, int... docBlocks) {
        /**
         * Registry id of the batch-1 files. Every batch-1 structure except the road was replaced by its approved Studio
         * version under its own id, so the batch-1 files stay in the registry as test copies ({@code csr:batch1_<path>}).
         */
        ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, idPath());
        }

        String idPath() {
            return path.equals("road") ? path : "batch1_" + path;
        }

        int levels() {
            return docBlocks.length;
        }
    }

    static final List<Entry> BATCH1 = List.of(
            new Entry("keep", 18, 378, 392, 413),
            new Entry("hovel", 10, 30, 35, 39),
            new Entry("cottage", 16, 152, 176, 191),
            new Entry("visitors_camp", 18, 60, 73, 85),
            new Entry("farm", 16, 43, 55, 80),
            new Entry("lumber_camp", 14, 47, 56, 66),
            new Entry("well", 6, 23, 26, 28),
            new Entry("bench", 5, 2, 4),
            new Entry("fire_pit", 10, 9, 11),
            new Entry("signpost", 4, 2, 4),
            new Entry("flower_bed", 4, 6),
            new Entry("road", 10, 15, 19, 23));

    /** Blocks builders placed in a GameTest (docs/STRUCTURES.md buildability checklist, incl. the probe, D61). */
    static final Set<String> KNOWN_OK = Set.of("cobblestone", "cobblestone_slab", "oak_planks", "oak_slab", "oak_stairs",
            "oak_log", "oak_wood", "stripped_oak_log", "stripped_oak_wood", "spruce_log", "oak_door", "spruce_door",
            "spruce_trapdoor", "blue_wool", "cyan_wool", "green_wool", "light_blue_wool", "glowstone", "torch", "wall_torch",
            "oak_wall_sign", "scaffolding", "chest", "barrel", "red_bed", "oak_fence", "spruce_fence", "red_carpet",
            "white_carpet", "brown_carpet", "furnace", "smoker", "crafting_table", "composter", "grindstone", "glass_pane",
            "hay_block", "oak_pressure_plate", "oak_trapdoor", "cobblestone_wall", "cobblestone_stairs", "mossy_cobblestone",
            "stone_bricks", "bricks", "white_terracotta", "dark_oak_planks", "dark_oak_log", "dark_oak_stairs", "spruce_planks",
            "spruce_stairs", "spruce_slab", "stripped_spruce_log", "coarse_dirt", "dirt", "gravel", "flower_pot", "campfire",
            "lantern", "oak_leaves", "chain", "stone_brick_stairs", "oak_button", "poppy");
    /** Never placed by builders: the decoration step adds these (D54, D56). */
    static final Set<String> BROKEN = Set.of("water", "lava", "fire", "farmland", "dirt_path", "water_cauldron");

    @GameTestGenerator
    public static List<TestFunction> batch1Tests() {
        List<TestFunction> tests = new ArrayList<>();
        tests.add(test("batch1_registry", "batch1registryentriesarevalid", 100, true, Batch1StructureGameTests::registryEntriesAreValid));
        tests.add(test("batch1_rules", "batch1filesfollowtheblockrules", 100, true, Batch1StructureGameTests::filesFollowTheBlockRules));
        for (Entry entry : BATCH1) {
            for (int level = 1; level <= entry.levels(); level++) {
                int detail = level;
                String name = entry.path().replace("_", "") + "l" + level;
                tests.add(test("batch1_paste", "batch1pasteundo" + name, 100, true, helper -> pasteThenUndoRestores(helper, entry, detail)));
                long estimate = estimate(entry, detail);
                int timeout = (int) Math.max(2000, estimate * 3 + 1000);
                // Each build in its own batch: tests in one batch run at the same time side by side, and builders take
                // the nearest easel, which can be the next test's.
                tests.add(test("batch1_build_" + name, "batch1build" + name, timeout, true, helper -> buildersBuildIt(helper, entry, detail, timeout)));
            }
        }
        tests.add(test("batch1_upgrade", "batch1cottageupgradel1tol2inplace", 12_000, false, Batch1StructureGameTests::upgradeInPlace));
        return tests;
    }

    private static TestFunction test(String batch, String name, int maxTicks, boolean required, java.util.function.Consumer<GameTestHelper> body) {
        return new TestFunction(batch, name, TEMPLATE, Rotation.NONE, maxTicks, 0, required, false, 1, 1, false, body);
    }

    /** The doc's estimated build time for a level, in ticks. */
    static long estimate(Entry entry, int level) {
        return Math.round(TICKS_PER_BLOCK_PER_SIDE * entry.sides() * entry.docBlocks()[level - 1]);
    }

    static StructureMeta meta(GameTestHelper helper, Entry entry) {
        Optional<StructureMeta> meta = StructureRegistry.get(helper.getLevel().getServer()).byId(entry.id());
        if (meta.isEmpty()) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("no registry entry " + entry.id());
        }
        return meta.get();
    }

    /** Every batch-1 entry is ready, has the expected levels and placement fields, and the registry has no problems. */
    static void registryEntriesAreValid(GameTestHelper helper) {
        StructureRegistry.Snapshot snapshot = StructureRegistry.reload(helper.getLevel().getServer());
        helper.assertTrue(snapshot.problems().isEmpty(), "registry problems: " + snapshot.problems());
        for (Entry entry : BATCH1) {
            StructureMeta meta = meta(helper, entry);
            helper.assertTrue(meta.isReady(), entry.id() + " is not ready");
            helper.assertTrue(meta.levelCount() == entry.levels(), entry.id() + " has " + meta.levelCount() + " levels, expected " + entry.levels());
            helper.assertTrue(meta.batch() != null && meta.batch() == 1, entry.id() + " is not batch 1");
            helper.assertTrue(meta.groundLevel() != null && meta.terrain() != null, entry.id() + " lacks ground_level or terrain");
            helper.assertTrue(meta.footprint() != null && meta.footprint().getY() <= 9, entry.id() + " is taller than 9");
        }
        helper.succeed();
    }

    /**
     * File rules: each level only adds blocks to the one before (same box); level 1 (shell + fittings) uses only
     * KNOWN-OK blocks; no level contains a BROKEN block; every level has the doc's block count and size; every structure
     * has a decoration sidecar.
     */
    static void filesFollowTheBlockRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (Entry entry : BATCH1) {
            StructureMeta meta = meta(helper, entry);
            Map<BlockPos, BlockState> previous = Map.of();
            for (int detail = 1; detail <= entry.levels(); detail++) {
                StructureTemplate template = StructurePaster.template(level, meta, detail);
                Map<BlockPos, BlockState> blocks = blocks(template);
                helper.assertTrue(blocks.size() == entry.docBlocks()[detail - 1], entry.id() + " level " + detail + " has "
                        + blocks.size() + " blocks, the doc lists " + entry.docBlocks()[detail - 1]);
                helper.assertTrue(template.getSize().getX() + template.getSize().getZ() == entry.sides(),
                        entry.id() + " is " + template.getSize() + ", the doc's width + depth is " + entry.sides());
                for (var block : previous.entrySet()) {
                    helper.assertTrue(block.getValue().equals(blocks.get(block.getKey())),
                            entry.id() + " level " + detail + " changes or removes " + block.getValue() + " at " + block.getKey());
                }
                for (BlockState state : blocks.values()) {
                    String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    helper.assertTrue(!BROKEN.contains(name), entry.id() + " level " + detail + " places BROKEN " + name);
                    if (detail == 1) {
                        helper.assertTrue(KNOWN_OK.contains(name), entry.id() + " shell or fittings use " + name + ", which isn't KNOWN-OK");
                    }
                }
                previous = blocks;
            }
            ResourceLocation sidecar = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "csr/decoration/" + entry.idPath() + ".json");
            helper.assertTrue(level.getServer().getResourceManager().getResource(sidecar).isPresent(), "no decoration sidecar " + sidecar);
        }
        helper.succeed();
    }

    /** The non-air blocks of a structure file, by local position. */
    static Map<BlockPos, BlockState> blocks(StructureTemplate template) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (var info : allBlocks(template)) {
            if (!info.state().isAir()) {
                blocks.put(info.pos(), info.state());
            }
        }
        return blocks;
    }

    /** Every block of a structure file (air included), in local positions. */
    static List<StructureTemplate.StructureBlockInfo> allBlocks(StructureTemplate template) {
        net.minecraft.nbt.CompoundTag tag = template.save(new net.minecraft.nbt.CompoundTag());
        List<BlockState> palette = new ArrayList<>();
        var lookup = net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup();
        for (var entry : tag.getList("palette", 10)) {
            palette.add(net.minecraft.nbt.NbtUtils.readBlockState(lookup, (net.minecraft.nbt.CompoundTag) entry));
        }
        List<StructureTemplate.StructureBlockInfo> infos = new ArrayList<>();
        for (var entry : tag.getList("blocks", 10)) {
            var block = (net.minecraft.nbt.CompoundTag) entry;
            var pos = block.getList("pos", 3);
            infos.add(new StructureTemplate.StructureBlockInfo(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)),
                    palette.get(block.getInt("state")), null));
        }
        return infos;
    }

    /** Pasting a level and then undoing it puts every block of the area back exactly. */
    static void pasteThenUndoRestores(GameTestHelper helper, Entry entry, int detail) {
        ServerLevel level = helper.getLevel();
        StructureMeta meta = meta(helper, entry);
        BlockPos at = helper.absolutePos(new BlockPos(16, 2, 4));
        BoundingBox box = StructurePaster.box(StructurePaster.template(level, meta, detail), at, Direction.SOUTH);
        Map<BlockPos, BlockState> before = new HashMap<>();
        BlockPos.betweenClosedStream(box).forEach(pos -> before.put(pos.immutable(), level.getBlockState(pos)));
        StructurePaster.paste(level, meta, detail, at, Direction.SOUTH);
        long changed = before.keySet().stream().filter(pos -> !level.getBlockState(pos).equals(before.get(pos))).count();
        helper.assertTrue(changed > 0, "the paste changed nothing");
        StructurePaster.undo(level).orElseThrow();
        List<BlockPos> wrong = before.keySet().stream().filter(pos -> !level.getBlockState(pos).equals(before.get(pos))).toList();
        helper.assertTrue(wrong.isEmpty(), wrong.size() + " blocks not restored, first at " + (wrong.isEmpty() ? "" : wrong.getFirst()));
        helper.succeed();
    }

    /**
     * Builders build one level from scratch: an easel facing north at (16, 2, 6) (the structure goes south), a chest
     * with exactly the blueprint's materials and 3 builders ({@link #setUpBuilders}). Every block is placed, the result matches the file block
     * by block (air included, neighbour connections aside), and the time is within the tolerance of the doc's estimate.
     */
    static void buildersBuildIt(GameTestHelper helper, Entry entry, int detail, int timeoutTicks) {
        ServerLevel level = helper.getLevel();
        StructureMeta meta = meta(helper, entry);
        BlueprintData blueprint = StructureBlueprints.blueprint(level, meta, detail);
        BlockPos easelPos = helper.absolutePos(new BlockPos(16, 2, 6));
        EaselBlockEntity easel = DevScenarios.placeEasel(level, easelPos, Direction.NORTH, blueprint);
        List<ItemStack> items = blueprint.createServerPlan().orElseThrow().getNeededItems();
        Builders builders = setUpBuilders(helper, items);
        BuildStallReport.watch(helper, entry.path() + " L" + detail, timeoutTicks, easel, builders.chest(), builders.soldiers());
        long start = helper.getTick();
        long estimate = estimate(entry, detail);
        long[] finished = {-1};
        helper.succeedWhen(() -> {
            helper.assertTrue(easel.isFinished(), "not finished: " + remaining(easel));
            boolean first = finished[0] < 0;
            if (first) {
                finished[0] = helper.getTick();
            }
            long ticks = finished[0] - start;
            Fidelity fidelity = fidelity(level, easel, StructurePaster.template(level, meta, detail));
            if (first) ClaySoldiersCommon.LOGGER.info("CSR batch1 build: {} L{}: {} blocks, {} ticks (doc estimate {}), fidelity {}/{} ({}%){}",
                    entry.path(), detail, fidelity.blocks(), ticks, estimate, fidelity.matching(), fidelity.checked(),
                    fidelity.percent(), fidelity.firstWrong() == null ? "" : ", first wrong: " + fidelity.firstWrong());
            helper.assertTrue(fidelity.matching() == fidelity.checked(), "built result differs from the file at "
                    + (fidelity.checked() - fidelity.matching()) + " positions, first: " + fidelity.firstWrong());
            // The well's 1x1 shaft is a standing spot builders like; since D64 the blocks that would wall a builder in
            // there wait until it leaves (396 ticks for L3 on CI, estimate 235), so the well gets twice the tolerance.
            // The farm is fences: since D64/D66 a fence beside a soldier waits until it has moved, and a builder beside
            // a post is put out first (farm L3 2,705 ticks on CI, estimate 1,792; L2 2,018 against 1,232). A tiny build
            // (the bench: 14 ticks) is mostly fixed waits, so every build also gets FIXED_WAIT_SLACK ticks on top.
            double tolerance = entry.path().equals("well") || entry.path().equals("farm") ? 2 * TIME_TOLERANCE : TIME_TOLERANCE;
            long allowed = Math.round(estimate * tolerance) + FIXED_WAIT_SLACK;
            helper.assertTrue(ticks - estimate <= allowed,
                    "build took " + ticks + " ticks, the doc estimates " + estimate + " (allowed ±" + allowed + " ticks)");
        });
    }

    /** The blocks the easel's plan still lists (at most 5), with their world positions. */
    static String remaining(EaselBlockEntity easel) {
        if (!(easel.getPlan() instanceof net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan plan)) {
            return "no plan";
        }
        var settings = easel.getTemplateSettings();
        BlockPos base = EaselBlockEntity.templateBase(easel.getBlockPos(), easel.getFacing(), settings);
        List<BlockPos> left = plan.targetPositions(base, settings);
        return left.size() + " blocks left, e.g. " + left.stream().limit(8).map(pos -> pos.subtract(base).toShortString()).toList()
                + " (offsets from the structure's corner; the easel faces north, so they are the file's own positions)";
    }

    /**
     * The chest and the 3 builders in the Small House scenario's layout (DevScenarios.buildTest), whose measured rate
     * the doc's estimates use: the player would stand at (16, 2, 3) facing south, the easel is 3 ahead at (16, 2, 6),
     * the chest 2 ahead and 2 to the right, the builders 1 ahead.
     */
    static Builders setUpBuilders(GameTestHelper helper, List<ItemStack> items) {
        ServerLevel level = helper.getLevel();
        BlockPos chestPos = helper.absolutePos(new BlockPos(14, 2, 5));
        ChestBlockEntity chest = DevScenarios.placeChest(level, chestPos, items);
        List<ClaySoldierEntity> soldiers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            soldiers.add(DevScenarios.spawnWorker(level, helper.absolutePos(new BlockPos(16 - i, 2, 4)), DevScenarios.RED_TEAM,
                    ClaySoldierGameTests.owner(level), BuildBlueprintGoal.class, chestPos));
        }
        return new Builders(chest, soldiers);
    }

    /** The chest and the builders {@link #setUpBuilders} placed. */
    record Builders(ChestBlockEntity chest, List<ClaySoldierEntity> soldiers) {
    }

    /** How closely the world matches a structure file placed by an easel. */
    record Fidelity(int blocks, int checked, int matching, String firstWrong) {
        long percent() {
            return checked == 0 ? 100 : Math.round(100.0 * matching / checked);
        }
    }

    static Fidelity fidelity(ServerLevel level, EaselBlockEntity easel, StructureTemplate template) {
        var settings = easel.getTemplateSettings();
        BlockPos base = EaselBlockEntity.templateBase(easel.getBlockPos(), easel.getFacing(), settings);
        StructurePlaceSettings place = settings.convertTo();
        int blocks = 0;
        int checked = 0;
        int matching = 0;
        String firstWrong = null;
        for (var info : allBlocks(template)) {
            BlockPos world = StructureTemplate.calculateRelativePosition(place, info.pos()).offset(base);
            BlockState expected = info.state().mirror(settings.mirror()).rotate(settings.rotation());
            BlockState actual = level.getBlockState(world);
            if (!expected.isAir()) {
                blocks++;
            }
            checked++;
            if (expected.isAir() ? actual.isAir() : StructureFidelity.matches(expected, actual)) {
                matching++;
            } else if (firstWrong == null) {
                firstWrong = info.pos() + " expected " + expected + ", found " + actual;
            }
        }
        return new Fidelity(blocks, checked, matching, firstWrong);
    }

    /**
     * Upgrade in place (expected to fail, not required): builders finish cottage level 1, then the easel gets level 2.
     * Records whether the planner refuses the blocks already there (it lists every level-2 block, including the ones
     * level 1 already placed, and an occupied spot is never handed out, so the build can't finish).
     */
    static void upgradeInPlace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Entry cottage = BATCH1.get(2);
        StructureMeta meta = meta(helper, cottage);
        BlockPos easelPos = helper.absolutePos(new BlockPos(16, 2, 6));
        EaselBlockEntity easel = DevScenarios.placeEasel(level, easelPos, Direction.NORTH, StructureBlueprints.blueprint(level, meta, 1));
        BlueprintData level2 = StructureBlueprints.blueprint(level, meta, 2);
        List<ItemStack> items = new ArrayList<>(StructureBlueprints.blueprint(level, meta, 1).createServerPlan().orElseThrow().getNeededItems());
        items.addAll(level2.createServerPlan().orElseThrow().getNeededItems());
        setUpBuilders(helper, items);
        boolean[] upgraded = {false};
        long[] upgradeStart = {0};
        helper.onEachTick(() -> {
            if (!upgraded[0] && easel.isFinished()) {
                upgraded[0] = true;
                upgradeStart[0] = helper.getTick();
                easel.setBlueprintData(level2);
                ClaySoldiersCommon.LOGGER.info("CSR batch1 upgrade: cottage L1 finished at tick {}, L2 put on the easel", helper.getTick());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(upgraded[0], "level 1 not finished yet");
            int left = easel.getPlan() instanceof net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan plan
                    ? plan.targetPositions(EaselBlockEntity.templateBase(easel.getBlockPos(), easel.getFacing(), easel.getTemplateSettings()),
                    easel.getTemplateSettings()).size() : -1;
            helper.assertTrue(easel.isFinished(), "level 2 on top of level 1 not finished: " + left + " blocks still listed, "
                    + (helper.getTick() - upgradeStart[0]) + " ticks after the upgrade started");
            ClaySoldiersCommon.LOGGER.info("CSR batch1 upgrade: L2 finished {} ticks after the upgrade started", helper.getTick() - upgradeStart[0]);
        });
    }
}
