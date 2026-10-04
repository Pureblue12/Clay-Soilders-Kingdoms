package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.structure.StructureMeta;
import net.bumblebee.claysoldiers.structure.StructureRegistry;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * Tests for the structure registry ({@link StructureRegistry}): validation of metadata against the structure files,
 * and the shipped registry. Each asserts the CORRECT behavior.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class StructureRegistryGameTests {
    /** The test hut's structure file: data/csr/structure/kingdom/test_hut.nbt, 5 x 3 x 5. */
    static final ResourceLocation HUT_FILE = csr("kingdom/test_hut");
    static final ResourceLocation HUT = csr("test_hut");
    static final ResourceLocation WELL = csr("test_well");
    static final ResourceLocation WALL = csr("test_wall");

    /**
     * A ready entry whose structure file doesn't exist is reported, naming the entry and the file.
     */
    @GameTest(template = PLATFORM)
    public static void missingStructureFileIsReported(GameTestHelper helper) {
        StructureMeta missing = meta(csr("ghost_house"), "inner", "house", new Vec3i(5, 3, 5), csr("kingdom/does_not_exist"));
        List<String> problems = StructureRegistry.validate(List.of(missing), StructureRegistry.sizesFrom(helper.getLevel().getServer()));
        assertProblem(helper, problems, "csr:ghost_house", "csr:kingdom/does_not_exist");
        helper.succeed();
    }

    /**
     * An illegal zone or role is reported, naming the entry and the bad value.
     */
    @GameTest(template = PLATFORM)
    public static void badTagIsReported(GameTestHelper helper) {
        StructureMeta badZone = meta(csr("moat_hut"), "moat", "house", new Vec3i(5, 3, 5), HUT_FILE);
        StructureMeta badRole = meta(csr("odd_hut"), "inner", "castle_thing", new Vec3i(5, 3, 5), HUT_FILE);
        List<String> problems = StructureRegistry.validate(List.of(badZone, badRole), StructureRegistry.sizesFrom(helper.getLevel().getServer()));
        assertProblem(helper, problems, "csr:moat_hut", "moat");
        assertProblem(helper, problems, "csr:odd_hut", "castle_thing");
        helper.succeed();
    }

    /**
     * Two entries with the same id are reported.
     */
    @GameTest(template = PLATFORM)
    public static void duplicateIdIsReported(GameTestHelper helper) {
        StructureMeta first = meta(csr("twin_hut"), "inner", "house", new Vec3i(5, 3, 5), HUT_FILE);
        StructureMeta second = meta(csr("twin_hut"), "outer", "house", new Vec3i(5, 3, 5), HUT_FILE);
        List<String> problems = StructureRegistry.validate(List.of(first, second), StructureRegistry.sizesFrom(helper.getLevel().getServer()));
        assertProblem(helper, problems, "csr:twin_hut", "duplicate");
        helper.succeed();
    }

    /**
     * A footprint that doesn't match the structure file's size is reported with both sizes.
     */
    @GameTest(template = PLATFORM)
    public static void footprintSizeMismatchIsReported(GameTestHelper helper) {
        StructureMeta wrongSize = meta(csr("squashed_hut"), "inner", "house", new Vec3i(4, 3, 5), HUT_FILE);
        List<String> problems = StructureRegistry.validate(List.of(wrongSize), StructureRegistry.sizesFrom(helper.getLevel().getServer()));
        assertProblem(helper, problems, "csr:squashed_hut", "5");
        helper.succeed();
    }

    /**
     * The mod's own registry is valid: no problems, the three test structures are ready, and the placeholders from
     * docs/STRUCTURES.md are there without geometry.
     */
    @GameTest(template = PLATFORM)
    public static void shippedRegistryIsValid(GameTestHelper helper) {
        StructureRegistry.Snapshot snapshot = StructureRegistry.get(helper.getLevel().getServer());
        helper.assertTrue(snapshot.problems().isEmpty(), "Problems in the shipped structure registry: " + snapshot.problems());
        for (ResourceLocation id : List.of(HUT, WELL, WALL)) {
            helper.assertTrue(snapshot.byId(id).map(StructureMeta::isReady).orElse(false), id + " is missing or not ready");
        }
        List<StructureMeta> placeholders = snapshot.structures().stream().filter(meta -> !meta.isReady()).toList();
        helper.assertTrue(!placeholders.isEmpty(), "No placeholder (design TBD) entries");
        helper.assertTrue(placeholders.stream().allMatch(meta -> meta.structure() == null && meta.footprint() == null),
                "Placeholders must not have geometry yet");
        helper.succeed();
    }

    /**
     * Recorder: validation problems are logged in the STRUCTURE category.
     */
    @GameTest(template = PLATFORM, batch = "structure_validation_log")
    public static void validationProblemIsLogged(GameTestHelper helper) {
        DebugLog.reset();
        DebugLog.setEnabled(DebugCategory.byId("STRUCTURE").orElseThrow(), true, helper.getLevel().getServer());
        StructureMeta first = meta(csr("logged_twin"), "inner", "house", new Vec3i(5, 3, 5), HUT_FILE);
        StructureRegistry.validate(List.of(first, first), StructureRegistry.sizesFrom(helper.getLevel().getServer()));
        boolean logged = DebugLog.contains(DebugCategory.byId("STRUCTURE").orElseThrow(), "csr:logged_twin");
        DebugLog.reset();
        helper.assertTrue(logged, "The validation problem was not logged in the STRUCTURE category");
        helper.succeed();
    }

    static StructureMeta meta(ResourceLocation id, String zone, String role, @Nullable Vec3i footprint, @Nullable ResourceLocation structure) {
        return new StructureMeta(id, id.getPath(), StructureMeta.READY, 1, zone, role, footprint, null, null, null, null,
                structure, csr("gametest/" + id.getPath()));
    }

    static ResourceLocation csr(String path) {
        return ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, path);
    }

    private static void assertProblem(GameTestHelper helper, List<String> problems, String... words) {
        helper.assertTrue(problems.stream().anyMatch(problem -> List.of(words).stream().allMatch(problem::contains)),
                "No problem mentioning " + List.of(words) + " in " + problems);
    }
}
