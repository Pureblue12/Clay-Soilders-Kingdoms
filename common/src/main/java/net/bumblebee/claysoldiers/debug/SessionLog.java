package net.bumblebee.claysoldiers.debug;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomEconomy;
import net.bumblebee.claysoldiers.kingdom.KingdomKeep;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Always-on logging for development runs (the owner's call 2026-10-17: "record anytime I'm in a game", no recorder to
 * start or stop). From the moment a world opens until it closes, every recorder category is on and every event goes
 * straight into {@code debuglogs/csr-session-<time>.txt}, so nothing falls out of the recorder's ring buffer. Every
 * {@link #SNAPSHOT_MILLIS} of real time it also writes a snapshot: each kingdom (keep, economy, buildings, stockpile) and
 * every loaded clay soldier (job, status, carried item, position, health). The file is flushed every second, so it is
 * readable while the game runs and survives a crash.
 * <p>
 * Started by the NeoForge server-start hook in development runs only, never in a GameTest server (its tests switch the
 * recorder themselves).
 */
public final class SessionLog {
    /** Real time between snapshots. */
    public static final long SNAPSHOT_MILLIS = 30_000;
    /** The file stops taking events past this size (a note says so); snapshots go on. */
    public static final long MAX_EVENT_BYTES = 512L * 1024 * 1024;
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Nullable
    private static BufferedWriter writer;
    @Nullable
    private static Path file;
    private static long eventBytes;
    private static boolean full;
    private static long lastSnapshot;
    private static long lastFlush;

    private SessionLog() {
    }

    /** Opens a new session file, turns every category on and sends every event to the file. */
    public static synchronized Path start(MinecraftServer server) throws IOException {
        stop(server);
        Path folder = server.getServerDirectory().resolve(DebugLog.FOLDER);
        Files.createDirectories(folder);
        file = folder.resolve("csr-session-" + LocalDateTime.now().format(FILE_TIME) + ".txt");
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        eventBytes = 0;
        full = false;
        lastSnapshot = 0;
        lastFlush = System.currentTimeMillis();
        write("# Clay Soldiers Remake session log, started " + LocalDateTime.now() + " (every category, every event)");
        write("# line format: [t=game tick] CATEGORY team#entityId @(position) message (xN = repeated N times)");
        DebugLog.start(server);
        DebugCategory.all().forEach(category -> DebugLog.setEnabled(category, true, server));
        DebugLog.setSink(SessionLog::onEvent);
        ClaySoldiersCommon.LOGGER.info("CSR session log: {}", file);
        return file;
    }

    /** Writes a last snapshot and closes the file. */
    public static synchronized void stop(@Nullable MinecraftServer server) {
        if (writer == null) {
            return;
        }
        DebugLog.setSink(null);
        if (server != null) {
            snapshot(server);
        }
        write("# session ended " + LocalDateTime.now());
        try {
            writer.close();
        } catch (IOException e) {
            ClaySoldiersCommon.LOGGER.error("CSR session log: could not close {}", file, e);
        }
        writer = null;
    }

    @Nullable
    public static synchronized Path file() {
        return file;
    }

    public static synchronized boolean isOn() {
        return writer != null;
    }

    /** Called every server tick: a snapshot every {@link #SNAPSHOT_MILLIS}, a flush every second. */
    public static synchronized void tick(MinecraftServer server) {
        if (writer == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastSnapshot >= SNAPSHOT_MILLIS) {
            lastSnapshot = now;
            snapshot(server);
        }
        if (now - lastFlush >= 1000) {
            lastFlush = now;
            // The old recorder buttons (stop, start) turn categories off: the session log turns them back on.
            DebugCategory.all().stream().filter(category -> !category.isEnabled())
                    .forEach(category -> DebugLog.setEnabled(category, true, server));
            DebugLog.setSink(SessionLog::onEvent);
            try {
                writer.flush();
            } catch (IOException e) {
                ClaySoldiersCommon.LOGGER.error("CSR session log: could not write {}", file, e);
            }
        }
    }

    private static synchronized void onEvent(DebugEvent event) {
        if (full) {
            return;
        }
        String line = event.toLine();
        eventBytes += line.length() + 1;
        if (eventBytes > MAX_EVENT_BYTES) {
            full = true;
            write("# events stop here: the file passed " + MAX_EVENT_BYTES / (1024 * 1024) + " MB; snapshots go on");
            return;
        }
        write(line);
    }

    /** Every kingdom and every loaded clay soldier, as a block of lines starting with "##". */
    public static synchronized void snapshot(MinecraftServer server) {
        if (writer == null) {
            return;
        }
        ServerLevel overworld = server.overworld();
        List<String> lines = new ArrayList<>();
        lines.add("## snapshot " + LocalDateTime.now().format(LINE_TIME) + " t=" + overworld.getGameTime());
        for (Kingdom kingdom : Kingdoms.all(server)) {
            lines.add("## kingdom " + kingdom.team() + " " + kingdom.status() + " center " + kingdom.center().toShortString()
                    + " claim " + kingdom.claimRadius());
            lines.add("##   buildings " + kingdom.buildings().stream()
                    .map(b -> b.structure().getPath() + " " + b.state() + " at " + b.pos().toShortString()).toList());
            KingdomKeep.describe(server, kingdom).forEach(line -> lines.add("##   keep: " + line));
            KingdomEconomy.describe(server, kingdom).forEach(line -> lines.add("##   economy: " + line));
            lines.add("##   stockpile " + kingdom.stockpile());
        }
        List<AbstractClaySoldierEntity> soldiers = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            soldiers.addAll(level.getEntities(EntityTypeTest.forClass(AbstractClaySoldierEntity.class), AbstractClaySoldierEntity::isAlive));
        }
        lines.add("## soldiers loaded: " + soldiers.size());
        DebugLog.snapshot(soldiers).forEach(line -> lines.add("##   " + line));
        lines.forEach(SessionLog::write);
    }

    private static void write(String line) {
        if (writer == null) {
            return;
        }
        try {
            writer.write(line);
            writer.newLine();
        } catch (IOException e) {
            ClaySoldiersCommon.LOGGER.error("CSR session log: could not write {}", file, e);
        }
    }
}
