package net.bumblebee.claysoldiers.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.bumblebee.claysoldiers.tuning.TuningKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * Tests for the tuning file ({@link Tuning}): defaults, bad values, reloading. Each asserts the CORRECT behavior.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class TuningGameTests {

    /**
     * A key the file doesn't set uses its default, and that is not a problem.
     */
    @GameTest(template = PLATFORM)
    public static void missingKeyUsesDefault(GameTestHelper helper) {
        JsonObject json = new JsonObject();
        json.addProperty(Tuning.EVENTS_WARNING_TIME.id(), 600);
        Tuning.Values values = Tuning.parse(json);
        helper.assertTrue(values.get(Tuning.EVENTS_WARNING_TIME) == 600, "The value in the file was not used");
        helper.assertTrue(values.get(Tuning.KINGDOM_MAX_LOADED).equals(Tuning.KINGDOM_MAX_LOADED.defaultValue()),
                "A missing key did not use its default, got " + values.get(Tuning.KINGDOM_MAX_LOADED));
        helper.assertTrue(values.get(Tuning.PERFORMANCE_SOLDIER_CAP).equals(Tuning.PERFORMANCE_SOLDIER_CAP.defaultValue()),
                "A missing per-tier key did not use its default");
        helper.assertTrue(values.problems().isEmpty(), "Missing keys should not be problems: " + values.problems());
        helper.succeed();
    }

    /**
     * Bad values (wrong type, out of range, wrong number of tiers) and unknown keys are reported by key name, and the
     * default is used for the bad ones.
     */
    @GameTest(template = PLATFORM)
    public static void badValueIsReportedAndDefaultUsed(GameTestHelper helper) {
        JsonObject json = new JsonObject();
        json.addProperty(Tuning.KINGDOM_MAX_LOADED.id(), "lots");
        json.addProperty(Tuning.EVENTS_WARNING_TIME.id(), -5);
        JsonArray tooShort = new JsonArray();
        tooShort.add(1);
        tooShort.add(2);
        json.add(Tuning.PERFORMANCE_SOLDIER_CAP.id(), tooShort);
        json.addProperty("kingdom.max_lodaed", 3);
        Tuning.Values values = Tuning.parse(json);

        assertDefault(helper, values, Tuning.KINGDOM_MAX_LOADED);
        assertDefault(helper, values, Tuning.EVENTS_WARNING_TIME);
        assertDefault(helper, values, Tuning.PERFORMANCE_SOLDIER_CAP);
        for (String id : List.of(Tuning.KINGDOM_MAX_LOADED.id(), Tuning.EVENTS_WARNING_TIME.id(), Tuning.PERFORMANCE_SOLDIER_CAP.id(), "kingdom.max_lodaed")) {
            helper.assertTrue(values.problems().stream().anyMatch(problem -> problem.contains(id)),
                    "No problem reported for " + id + ": " + values.problems());
        }
        helper.succeed();
    }

    /**
     * Reloading re-reads the file: a value changed on disk is picked up by the next reload.
     */
    @GameTest(template = PLATFORM, batch = "tuning_reload")
    public static void reloadPicksUpEditedFile(GameTestHelper helper) throws IOException {
        Path pack = Files.createTempDirectory("csr-tuning-test");
        try {
            Path file = pack.resolve("data").resolve(Tuning.FILE.getNamespace()).resolve(Tuning.FILE.getPath());
            Files.createDirectories(file.getParent());
            PathPackResources resources = new PathPackResources(
                    new PackLocationInfo("csr_tuning_test", Component.literal("tuning test"), PackSource.DEFAULT, Optional.empty()), pack);

            Files.writeString(file, "{\"" + Tuning.KINGDOM_MAX_LOADED.id() + "\": 7}");
            Tuning.reload(new MultiPackResourceManager(PackType.SERVER_DATA, List.of(resources)));
            helper.assertTrue(Tuning.KINGDOM_MAX_LOADED.get() == 7, "First load: expected 7, got " + Tuning.KINGDOM_MAX_LOADED.get());

            Files.writeString(file, "{\"" + Tuning.KINGDOM_MAX_LOADED.id() + "\": 9}");
            Tuning.reload(new MultiPackResourceManager(PackType.SERVER_DATA, List.of(resources)));
            helper.assertTrue(Tuning.KINGDOM_MAX_LOADED.get() == 9, "After editing the file: expected 9, got " + Tuning.KINGDOM_MAX_LOADED.get());
        } finally {
            Tuning.reload(helper.getLevel().getServer().getResourceManager());
            try (Stream<Path> files = Files.walk(pack)) {
                files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
        helper.succeed();
    }

    /**
     * The mod's own tuning file loads without problems and sets every key (so it documents all of them).
     */
    @GameTest(template = PLATFORM, batch = "tuning_reload")
    public static void shippedTuningFileIsComplete(GameTestHelper helper) {
        Tuning.Values values = Tuning.reload(helper.getLevel().getServer().getResourceManager());
        helper.assertTrue(values.problems().isEmpty(), "Problems in the shipped tuning file: " + values.problems());
        helper.assertTrue(Tuning.problems().isEmpty(), "Current problems: " + Tuning.problems());
        List<String> missing = Tuning.keys().stream().map(TuningKey::id).filter(id -> !values.setInFile().contains(id)).toList();
        helper.assertTrue(missing.isEmpty(), "Keys missing from the shipped tuning file: " + missing);
        helper.succeed();
    }

    private static void assertDefault(GameTestHelper helper, Tuning.Values values, TuningKey<?> key) {
        helper.assertTrue(values.get(key).equals(key.defaultValue()), "Bad value for " + key.id() + " did not fall back to the default, got " + values.get(key));
    }
}
