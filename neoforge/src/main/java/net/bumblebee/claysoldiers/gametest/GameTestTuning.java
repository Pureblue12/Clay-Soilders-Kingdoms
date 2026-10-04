package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * GameTest helper: loads tuning values from a temporary data pack (a {@code tuning.json} with the given content) and
 * restores the real file afterwards. Tests using it change global state, so they need their own batch.
 */
final class GameTestTuning {
    private GameTestTuning() {
    }

    /**
     * Makes {@code json} (a whole tuning file) the current tuning values.
     */
    static Tuning.Values load(String json) {
        try {
            Path pack = Files.createTempDirectory("csr-tuning-test");
            try {
                Path file = pack.resolve("data").resolve(Tuning.FILE.getNamespace()).resolve(Tuning.FILE.getPath());
                Files.createDirectories(file.getParent());
                Files.writeString(file, json);
                PathPackResources resources = new PathPackResources(
                        new PackLocationInfo("csr_tuning_test", Component.literal("tuning test"), PackSource.DEFAULT, Optional.empty()), pack);
                return Tuning.reload(new MultiPackResourceManager(PackType.SERVER_DATA, List.of(resources)));
            } finally {
                try (Stream<Path> files = Files.walk(pack)) {
                    files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Restores the tuning values from the server's real data packs.
     */
    static void restore(MinecraftServer server) {
        Tuning.reload(server.getResourceManager());
    }
}
