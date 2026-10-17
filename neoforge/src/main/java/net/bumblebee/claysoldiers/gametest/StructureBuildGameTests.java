package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.dev.DevScenarios;
import net.bumblebee.claysoldiers.scaffold.ScaffoldJob;
import net.bumblebee.claysoldiers.scaffold.ScaffoldSavedData;
import net.bumblebee.claysoldiers.structure.StructureFidelity;
import net.bumblebee.claysoldiers.structure.StructureMeta;
import net.bumblebee.claysoldiers.structure.StructurePaster;
import net.bumblebee.claysoldiers.structure.StructureRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Builders build every structure promoted from the Structure Studio, every detail level from scratch, on flat ground:
 * 3 builders, a chest kept stocked with the level's materials (plus scaffolding when it is taller than builders
 * reach), the Small House layout ({@link DevScenarios#structureBuild}, the same setup as {@code /csr dev structure build}).
 * A build passes when the easel is finished, its scaffold is gone and the world matches the file block by block (air
 * included, structure void and neighbour connections aside). A build that makes no progress for {@link #STALL_TICKS}
 * fails at once, with a stall report in the log. So does a build still running after {@link #wallClockMinutes()} minutes of
 * real time ("too slow"), so one huge build can't hold a whole run hostage (keep L3 placed a block every ~114 ticks and
 * would have needed about 5 hours).
 * <p>
 * About 156 builds, far too slow for the normal suite: the tests only exist when the environment variable
 * {@code CSR_STRUCTURE_BUILDS} is set ({@code all}, or a shard {@code k/n}; {@code CSR_STRUCTURE_BUILDS_ONLY} limits
 * them to a comma-separated list of structure ids, {@code CSR_STRUCTURE_BUILDS_SKIP} leaves some out). They are optional, so they never decide the suite. Run them with
 * {@code tools/structure_builds.sh} or the "Structure builds" GitHub workflow, which prints a table per structure.
 * Each build logs "CSR structure build result: PASS|FAIL ..." lines for that table.
 * <p>
 * The one required test, {@link #buildListMatchesRegistry}, runs in the normal suite and keeps {@link #STRUCTURES} in
 * step with the registry: a newly promoted structure must be added here.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class StructureBuildGameTests {
    /** 48 by 24 by 48 with a grass floor (flowers and plants on the bottom layer need it): room for the keep (31 by 33) behind the easel. */
    static final String TEMPLATE = ClaySoldiersCommon.MOD_ID + ":gametest/structure_build";
    /** A build whose remaining blocks and scaffold don't change for this long fails (the rest would only time out). */
    static final int STALL_TICKS = 6000;
    /** Real-time budget per build in minutes unless {@code CSR_STRUCTURE_BUILD_MINUTES} says otherwise (0 = no limit). */
    static final int DEFAULT_WALL_CLOCK_MINUTES = 45;
    /** How often progress is checked, in ticks. */
    static final int CHECK_TICKS = 100;
    /** Same build-time model as batch 1 ({@link Batch1StructureGameTests#TICKS_PER_BLOCK_PER_SIDE}); used for timeouts and shards. */
    static final double TICKS_PER_BLOCK_PER_SIDE = Batch1StructureGameTests.TICKS_PER_BLOCK_PER_SIDE;

    /**
     * One promoted structure: its registry id path, width plus depth, height, and the non-air block count of each level
     * (level 1 first). The counts only size the timeouts and shards; the tests read the real files.
     */
    record Entry(String path, int sides, int height, int... blocks) {
        int levels() {
            return blocks.length;
        }

        ResourceLocation id() {
            return ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, path);
        }

        long estimate(int level) {
            return Math.round(TICKS_PER_BLOCK_PER_SIDE * sides * blocks[level - 1]);
        }

        String testName(int level) {
            return "structurebuild_" + path + "_l" + level;
        }
    }

    static final List<Entry> STRUCTURES = List.of(
            new Entry("armory", 26, 9, 376, 447, 486),
            new Entry("bakery", 21, 9, 214, 240, 269),
            new Entry("banner_pole", 6, 9, 19, 22, 25),
            new Entry("bench", 14, 4, 36, 65),
            new Entry("butcher", 20, 8, 200, 217, 245),
            new Entry("carpenter", 18, 7, 156, 173, 210),
            new Entry("chapel", 31, 13, 371, 506, 600),
            new Entry("church", 41, 20, 1094, 1183, 1248),
            new Entry("commons", 30, 9, 247, 301, 380),
            new Entry("cottage", 20, 9, 253, 297, 329),
            new Entry("farm", 34, 15, 475, 547, 872),
            new Entry("farm_field_carrots", 23, 3, 139),
            new Entry("farm_field_potatoes", 25, 4, 198),
            new Entry("farm_field_wheat", 22, 5, 158),
            new Entry("fire_pit", 18, 8, 47, 76, 137),
            new Entry("fishing_dock", 16, 5, 93, 105, 110),
            new Entry("flower_bed", 4, 2, 6),
            new Entry("food_hall", 28, 9, 418, 475, 568),
            new Entry("fountain", 18, 8, 89, 149, 160),
            new Entry("garden", 34, 9, 207, 615, 760),
            new Entry("gatehouse", 25, 20, 572, 619, 841),
            new Entry("graveyard", 21, 7, 115, 193, 227),
            new Entry("hitching_post", 8, 3, 12, 18),
            new Entry("hospital", 36, 13, 1446, 1597, 1652),
            new Entry("hovel", 10, 3, 30, 35, 39),
            new Entry("infirmary", 24, 9, 389, 431, 463),
            new Entry("inn", 27, 9, 433, 488, 521),
            new Entry("keep", 64, 20, 1229, 2116, 4926),
            new Entry("lamp_post", 6, 6, 11, 17, 20),
            new Entry("library", 22, 9, 394, 443, 469),
            new Entry("lumber_camp", 27, 8, 219, 294, 346),
            new Entry("market", 30, 8, 497, 616, 702),
            new Entry("market_stall", 13, 5, 62, 73, 85),
            new Entry("mine", 22, 7, 222, 262, 275),
            new Entry("noble_house", 29, 9, 442, 521, 553),
            new Entry("notice_board", 6, 4, 21, 27),
            new Entry("quarry", 35, 9, 237, 303, 644),
            new Entry("signpost", 10, 7, 31, 45),
            new Entry("smithy", 20, 9, 233, 250, 296),
            new Entry("stables", 20, 9, 197, 231, 262),
            new Entry("statue", 18, 9, 110, 170, 182),
            new Entry("stone_wall", 8, 7, 66, 73, 83),
            new Entry("stone_wall_corner", 6, 8, 42, 46, 52),
            new Entry("stone_wall_gate", 12, 9, 151, 164, 179),
            new Entry("stone_wall_stairs", 10, 7, 81, 88, 100),
            new Entry("stone_wall_stepped", 8, 8, 75, 82, 91),
            new Entry("stonemason", 21, 9, 277, 310, 331),
            new Entry("tannery", 22, 8, 191, 226, 252),
            new Entry("town_hall", 31, 18, 1115, 1197, 1291),
            new Entry("townhouse", 21, 9, 267, 303, 355),
            new Entry("training_yard", 22, 5, 183, 196, 227),
            new Entry("tree_farm", 33, 8, 268, 305, 349),
            new Entry("visitors_camp", 35, 5, 241, 272, 311),
            new Entry("wall_tower", 14, 13, 185, 211, 294),
            new Entry("watchtower", 12, 9, 98, 124, 148),
            new Entry("well", 16, 6, 54, 94, 106));

    /** One build: a structure and a level. */
    record Build(Entry entry, int level) {
        long estimate() {
            return entry.estimate(level);
        }
    }

    @GameTestGenerator
    public static List<TestFunction> structureBuilds() {
        String setting = System.getenv("CSR_STRUCTURE_BUILDS");
        if (setting == null || setting.isBlank()) {
            return List.of();
        }
        List<Build> builds = selected(setting.trim(), System.getenv("CSR_STRUCTURE_BUILDS_ONLY"), System.getenv("CSR_STRUCTURE_BUILDS_SKIP"));
        List<TestFunction> tests = new ArrayList<>();
        for (Build build : builds) {
            Entry entry = build.entry();
            int level = build.level();
            int timeout = (int) Math.min(Integer.MAX_VALUE / 2, Math.max(STALL_TICKS * 2L, build.estimate() * 3 + 2000));
            ClaySoldiersCommon.LOGGER.info("CSR structure build planned: {} | {} | L{} | {} blocks | height {}",
                    entry.testName(level), entry.path(), level, entry.blocks()[level - 1], entry.height());
            // Each build in its own batch: builders take the nearest easel, which could be the next test's.
            tests.add(new TestFunction("structure_build_" + entry.path() + "_l" + level, entry.testName(level), TEMPLATE,
                    Rotation.NONE, timeout, 0, false, false, 1, 1, true, helper -> buildersBuildIt(helper, entry, level, timeout)));
        }
        return tests;
    }

    /**
     * The builds of a shard ({@code k/n}, or {@code all}), optionally only for the given structure ids ({@code keep}: every
     * level, {@code keep:2}: only level 2), leaving out the ones in {@code skip} (same form; the workflow skips keep L3 in
     * automatic runs that change nothing it depends on). Shards are
     * balanced by the estimated build time (largest first, each to the least loaded shard).
     */
    static List<Build> selected(String setting, String only) {
        return selected(setting, only, null);
    }

    static List<Build> selected(String setting, String only, String skip) {
        Set<String> ids = idSet(only);
        Set<String> skipped = idSet(skip);
        List<Build> all = new ArrayList<>();
        for (Entry entry : STRUCTURES) {
            for (int level = 1; level <= entry.levels(); level++) {
                boolean picked = ids.isEmpty() || ids.contains(entry.path()) || ids.contains(entry.path() + ":" + level);
                if (picked && !skipped.contains(entry.path()) && !skipped.contains(entry.path() + ":" + level)) {
                    all.add(new Build(entry, level));
                }
            }
        }
        if (setting.equalsIgnoreCase("all")) {
            return all;
        }
        String[] parts = setting.split("/");
        int shard = Integer.parseInt(parts[0].trim());
        int shards = Integer.parseInt(parts[1].trim());
        long[] load = new long[shards];
        List<Build> mine = new ArrayList<>();
        all.sort(Comparator.comparingLong(Build::estimate).reversed().thenComparing(build -> build.entry().testName(build.level())));
        for (Build build : all) {
            int least = 0;
            for (int i = 1; i < shards; i++) {
                if (load[i] < load[least]) {
                    least = i;
                }
            }
            load[least] += build.estimate();
            if (least == shard - 1) {
                mine.add(build);
            }
        }
        mine.sort(Comparator.comparing(build -> build.entry().testName(build.level())));
        return mine;
    }

    /** Comma-separated structure ids ({@code keep}, {@code csr:keep}, {@code keep:2}) as a set without the mod id. */
    static Set<String> idSet(String list) {
        return list == null || list.isBlank() ? Set.of()
                : Arrays.stream(list.split(",")).map(String::trim).filter(id -> !id.isEmpty())
                .map(id -> id.startsWith(ClaySoldiersCommon.MOD_ID + ":") ? id.substring(ClaySoldiersCommon.MOD_ID.length() + 1) : id)
                .collect(Collectors.toSet());
    }

    /**
     * Builders build one level ({@link DevScenarios#structureBuild}, player spot at (24, 2, 3) facing south, so the
     * easel is at (24, 2, 6) and the structure has the file's own orientation from z = 8).
     */
    static void buildersBuildIt(GameTestHelper helper, Entry entry, int level, int timeoutTicks) {
        ServerLevel world = helper.getLevel();
        String tuning = System.getenv("CSR_STRUCTURE_BUILD_TUNING");
        if (tuning != null && !tuning.isBlank()) {
            // An experiment's tuning values (workflow input "tuning"), loaded again for each build: other tests in the same
            // server restore the real file. The required suite running alongside sees them too; its results don't count here.
            GameTestTuning.load(tuning).problems().forEach(problem -> ClaySoldiersCommon.LOGGER.warn("CSR structure build tuning: {}", problem));
        }
        String name = entry.testName(level);
        StructureMeta meta = StructureRegistry.get(world.getServer()).byId(entry.id())
                .orElseThrow(() -> new GameTestAssertException("no registry entry " + entry.id()));
        DevScenarios.StructureBuildResult setup = DevScenarios.structureBuild(world, ClaySoldierGameTests.owner(world),
                helper.absolutePos(new BlockPos(24, 2, 3)), Direction.SOUTH, meta, level);
        EaselBlockEntity easel = setup.easel();
        StructureTemplate template = StructurePaster.template(world, meta, level);
        boolean profile = profiling();
        long profileStart = helper.getTick();
        // The profile is counted from the recorder just before the stall report's watcher clears it, when the easel finishes.
        BuildStallReport.watch(helper, name, timeoutTicks, easel, setup.chest(), setup.soldiers(),
                profile ? () -> BuildProfile.write(name, helper.getTick() - profileStart) : () -> {
                });
        if (profile) {
            DebugLog.setCapacity(BuildProfile.CAPACITY);
        }
        long start = helper.getTick();
        long startNanos = System.nanoTime();
        long budgetNanos = wallClockMinutes() * 60_000_000_000L;
        long[] lastChange = {start};
        String[] lastProgress = {""};
        boolean[] passed = {false};
        // EveryTick, not helper.onEachTick / succeedWhen: with a timeout this long those cost most of the server's time.
        EveryTick.run(helper, () -> {
            setup.supply().tick();
            if (passed[0]) {
                return;
            }
            if (helper.getTick() >= timeoutTicks - 2) {
                fail(helper, name, entry, level, "not built yet: " + describe(easel, ScaffoldSavedData.get(world).job(easel.getBlockPos())));
            }
            if ((helper.getTick() - start) % CHECK_TICKS != 0) {
                return;
            }
            Optional<ScaffoldJob> job = ScaffoldSavedData.get(world).job(easel.getBlockPos());
            if (easel.isFinished() && job.isEmpty()) {
                Batch1StructureGameTests.Fidelity fidelity = fidelity(world, easel, template);
                if (fidelity.matching() == fidelity.checked()) {
                    passed[0] = true;
                    ClaySoldiersCommon.LOGGER.info("CSR structure build result: PASS {} | {} | L{} | {} blocks | {} ticks",
                            name, entry.path(), level, fidelity.blocks(), helper.getTick() - start);
                    helper.runAfterDelay(1, helper::succeed);
                    return;
                }
                fail(helper, name, entry, level, "finished, but differs from the file at " + (fidelity.checked() - fidelity.matching())
                        + " of " + fidelity.checked() + " positions, first: " + fidelity.firstWrong());
            }
            String progress = remainingCount(easel) + "/" + job.map(j -> j.placed().size()).orElse(-1);
            if (!progress.equals(lastProgress[0])) {
                lastProgress[0] = progress;
                lastChange[0] = helper.getTick();
            } else if (helper.getTick() - lastChange[0] >= STALL_TICKS) {
                BuildStallReport.report(helper, name, easel, setup.chest(), setup.soldiers())
                        .forEach(line -> ClaySoldiersCommon.LOGGER.info("CSR stall report {}: {}", name, line));
                DebugLog.reset();
                fail(helper, name, entry, level, "stalled: no progress for " + STALL_TICKS + " ticks, " + describe(easel, job)
                        + ", " + (helper.getTick() - start) + " ticks in" + (setup.scaffold() ? " (needs scaffold)" : ""));
            }
            if (budgetNanos > 0 && System.nanoTime() - startNanos > budgetNanos) {
                BuildStallReport.report(helper, name, easel, setup.chest(), setup.soldiers())
                        .forEach(line -> ClaySoldiersCommon.LOGGER.info("CSR stall report {}: {}", name, line));
                DebugLog.reset();
                fail(helper, name, entry, level, "too slow: still building after " + wallClockMinutes() + " minutes of real time, "
                        + describe(easel, job) + ", " + (helper.getTick() - start) + " ticks in" + (setup.scaffold() ? " (needs scaffold)" : ""));
            }
        });
    }

    /** Whether {@code CSR_STRUCTURE_BUILD_PROFILE} is set (workflow input "profile"): each finished build writes a profile line. */
    static boolean profiling() {
        String setting = System.getenv("CSR_STRUCTURE_BUILD_PROFILE");
        return setting != null && !setting.isBlank() && !setting.equals("0") && !setting.equalsIgnoreCase("false");
    }

    /** {@code CSR_STRUCTURE_BUILD_MINUTES}, or {@link #DEFAULT_WALL_CLOCK_MINUTES} when unset or not a number. */
    static int wallClockMinutes() {
        String setting = System.getenv("CSR_STRUCTURE_BUILD_MINUTES");
        try {
            return setting == null || setting.isBlank() ? DEFAULT_WALL_CLOCK_MINUTES : Math.max(0, Integer.parseInt(setting.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_WALL_CLOCK_MINUTES;
        }
    }

    private static void fail(GameTestHelper helper, String name, Entry entry, int level, String reason) {
        ClaySoldiersCommon.LOGGER.info("CSR structure build result: FAIL {} | {} | L{} | {}", name, entry.path(), level, reason);
        helper.fail(reason);
    }

    private static int remainingCount(EaselBlockEntity easel) {
        if (!(easel.getPlan() instanceof ServerBlueprintPlan plan)) {
            return -1;
        }
        var settings = easel.getTemplateSettings();
        return plan.targetPositions(EaselBlockEntity.templateBase(easel.getBlockPos(), easel.getFacing(), settings), settings).size();
    }

    private static String describe(EaselBlockEntity easel, Optional<ScaffoldJob> job) {
        String blocks = easel.isFinished() ? "structure finished" : remainingCount(easel) + " blocks left";
        return blocks + job.map(j -> ", scaffold job open (" + j.placed().size() + " scaffold blocks up)").orElse("");
    }

    /** Like {@link Batch1StructureGameTests#fidelity}, skipping structure void (Studio files mark sunk ground layers with it). */
    static Batch1StructureGameTests.Fidelity fidelity(ServerLevel level, EaselBlockEntity easel, StructureTemplate template) {
        var settings = easel.getTemplateSettings();
        BlockPos base = EaselBlockEntity.templateBase(easel.getBlockPos(), easel.getFacing(), settings);
        StructurePlaceSettings place = settings.convertTo();
        int blocks = 0;
        int checked = 0;
        int matching = 0;
        String firstWrong = null;
        for (var info : Batch1StructureGameTests.allBlocks(template)) {
            if (info.state().is(Blocks.STRUCTURE_VOID)) {
                continue;
            }
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
        return new Batch1StructureGameTests.Fidelity(blocks, checked, matching, firstWrong);
    }

    /**
     * Required, in the normal suite: {@link #STRUCTURES} lists exactly the ready structures promoted from the Studio
     * (structure files in {@code kingdom/studio/}), with their level counts and heights, and the shards cover every
     * build once.
     */
    @GameTest(template = ClaySoldierGameTests.PLATFORM)
    public static void buildListMatchesRegistry(GameTestHelper helper) {
        Map<String, StructureMeta> studio = new TreeMap<>();
        for (StructureMeta meta : StructureRegistry.get(helper.getLevel().getServer()).ready()) {
            if (meta.structure() != null && meta.structure().getPath().startsWith("kingdom/studio/")) {
                studio.put(meta.id().getPath(), meta);
            }
        }
        Map<String, Entry> listed = new TreeMap<>();
        STRUCTURES.forEach(entry -> listed.put(entry.path(), entry));
        helper.assertTrue(studio.keySet().equals(listed.keySet()), "StructureBuildGameTests.STRUCTURES is out of step with the registry."
                + " Missing: " + studio.keySet().stream().filter(id -> !listed.containsKey(id)).toList()
                + ", no longer promoted: " + listed.keySet().stream().filter(id -> !studio.containsKey(id)).toList());
        for (Entry entry : STRUCTURES) {
            StructureMeta meta = studio.get(entry.path());
            helper.assertTrue(meta.levelCount() == entry.levels(), entry.path() + " has " + meta.levelCount() + " levels, the list says " + entry.levels());
            int height = StructurePaster.template(helper.getLevel(), meta, 1).getSize().getY();
            helper.assertTrue(height == entry.height(), entry.path() + " is " + height + " tall, the list says " + entry.height());
        }
        int all = selected("all", null).size();
        // The workflow runs up to 20 shards (tools/structure_results.py shards); every shard must hold at least one build.
        for (int shards : new int[]{4, 20}) {
            int sharded = 0;
            for (int shard = 1; shard <= shards; shard++) {
                int size = selected(shard + "/" + shards, null).size();
                helper.assertTrue(size > 0, "shard " + shard + "/" + shards + " holds no build");
                sharded += size;
            }
            helper.assertTrue(sharded == all, shards + " shards hold " + sharded + " builds, all = " + all);
        }
        helper.assertTrue(selected("all", "csr:keep, hovel").size() == STRUCTURES.stream()
                .filter(entry -> Set.of("keep", "hovel").contains(entry.path())).mapToInt(Entry::levels).sum(), "the id filter is wrong");
        // The workflow's automatic runs skip keep L3 (CSR_STRUCTURE_BUILDS_SKIP=keep:3): exactly that one build goes.
        List<Build> skipped = selected("all", null, "keep:3");
        helper.assertTrue(skipped.size() == all - 1 && skipped.stream().noneMatch(build -> build.entry().path().equals("keep") && build.level() == 3),
                "the skip filter is wrong");
        helper.succeed();
    }
}
