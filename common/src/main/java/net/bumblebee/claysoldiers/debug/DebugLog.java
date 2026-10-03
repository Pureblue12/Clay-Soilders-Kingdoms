package net.bumblebee.claysoldiers.debug;

import net.bumblebee.claysoldiers.entity.ClayMobEntity;
import net.bumblebee.claysoldiers.entity.goal.workgoal.IWorkGoal;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Toggleable recorder of what soldiers and build plans do, to find out WHY something goes wrong.
 * <p>
 * Instrumenting gameplay code is one line, e.g. {@code DebugLog.event(DebugCategory.BUILD, soldier, "placed %s", stack);}.
 * Cost when the category is off: one field read, then return. Pass raw objects as arguments (they are only turned into
 * text when recording), or a {@code Supplier<String>} for anything expensive. Avoid building strings at the call site.
 * <p>
 * Events go into a ring buffer (last {@link #DEFAULT_CAPACITY} by default); repeats of the same message from the same
 * soldier collapse into one line with a count. {@link #save} writes the buffer to {@code <run folder>/debuglogs/}.
 * Controlled with {@code /csr dev log ...} and the dev book (development only). All state is in memory only: it is
 * reset when the game restarts (but not when you only leave and re-join a world in the same game session).
 */
public final class DebugLog {
    public static final int DEFAULT_CAPACITY = 5000;
    /** Saved files kept in {@code debuglogs/}; older ones are deleted. */
    public static final int MAX_FILES = 20;
    public static final String FOLDER = "debuglogs";
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter HEADER_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final UUID NO_SOLDIER = new UUID(0, 0);

    private static final ArrayDeque<DebugEvent> BUFFER = new ArrayDeque<>();
    private static final Map<UUID, DebugEvent> LAST_BY_SOLDIER = new HashMap<>();
    private static int capacity = DEFAULT_CAPACITY;
    private static long sequence = 0;
    @Nullable
    private static UUID focus;
    private static String focusName = "";
    @Nullable
    private static MinecraftServer server;

    private DebugLog() {
    }

    // Recording (the one-line entry points)

    public static boolean isOn(DebugCategory category) {
        return category.enabled;
    }

    public static void event(DebugCategory category, @Nullable Entity soldier, String message) {
        if (!category.enabled) {
            return;
        }
        record(category, soldier, message);
    }

    public static void event(DebugCategory category, @Nullable Entity soldier, String format, @Nullable Object arg) {
        if (!category.enabled) {
            return;
        }
        record(category, soldier, format.formatted(describe(arg)));
    }

    public static void event(DebugCategory category, @Nullable Entity soldier, String format, @Nullable Object arg1, @Nullable Object arg2) {
        if (!category.enabled) {
            return;
        }
        record(category, soldier, format.formatted(describe(arg1), describe(arg2)));
    }

    public static void event(DebugCategory category, @Nullable Entity soldier, String format,
                             @Nullable Object arg1, @Nullable Object arg2, @Nullable Object arg3) {
        if (!category.enabled) {
            return;
        }
        record(category, soldier, format.formatted(describe(arg1), describe(arg2), describe(arg3)));
    }

    public static void event(DebugCategory category, @Nullable Entity soldier, Supplier<String> message) {
        if (!category.enabled) {
            return;
        }
        record(category, soldier, message.get());
    }

    private static synchronized void record(DebugCategory category, @Nullable Entity soldier, String message) {
        UUID uuid = soldier == null ? null : soldier.getUUID();
        if (focus != null && uuid != null && !focus.equals(uuid)) {
            return;
        }
        long tick = currentTick(soldier);
        UUID key = uuid == null ? NO_SOLDIER : uuid;
        DebugEvent last = LAST_BY_SOLDIER.get(key);
        if (last != null && isInBuffer(last) && last.sameAs(category, message)) {
            last.repeat(tick);
            return;
        }
        DebugEvent event = new DebugEvent(sequence++, tick, category, uuid, shortName(soldier),
                soldier == null ? "-" : soldier.blockPosition().toShortString(), message);
        BUFFER.addLast(event);
        LAST_BY_SOLDIER.put(key, event);
        while (BUFFER.size() > capacity) {
            BUFFER.removeFirst();
        }
    }

    private static boolean isInBuffer(DebugEvent event) {
        DebugEvent oldest = BUFFER.peekFirst();
        return oldest != null && event.sequence() >= oldest.sequence();
    }

    private static long currentTick(@Nullable Entity soldier) {
        if (soldier != null) {
            return soldier.level().getGameTime();
        }
        return server != null ? server.overworld().getGameTime() : -1;
    }

    /**
     * Turns an argument into short text: positions, items, blocks, entities and work goals get compact forms.
     */
    static String describe(@Nullable Object value) {
        return switch (value) {
            case null -> "null";
            case BlockPos pos -> "(" + pos.toShortString() + ")";
            case ItemStack stack -> stack.isEmpty() ? "nothing" : stack.getCount() + " " + stack.getHoverName().getString();
            case BlockState state -> BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            case Component component -> component.getString();
            case Entity entity -> shortName(entity);
            case IWorkGoal goal -> goal.asString();
            case Float number -> "%.1f".formatted(number);
            case Double number -> "%.1f".formatted(number);
            default -> String.valueOf(value);
        };
    }

    /**
     * Short name of a soldier, e.g. {@code red#42} (team and entity id), or {@code -} for none.
     */
    public static String shortName(@Nullable Entity entity) {
        if (entity == null) {
            return "-";
        }
        String kind = entity instanceof ClayMobEntity clayMob ? clayMob.getClayTeamType().getPath()
                : BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        return kind + "#" + entity.getId();
    }

    // Control

    /**
     * Turns on the {@link DebugCategory#PRESET} categories (others off), clears the buffer and removes the focus.
     */
    public static synchronized void start(MinecraftServer server) {
        attach(server);
        DebugCategory.all().forEach(category -> category.enabled = DebugCategory.PRESET.contains(category));
        focus = null;
        focusName = "";
        clear();
    }

    /**
     * Turns every category off and saves the buffer.
     *
     * @return the saved file
     */
    public static synchronized Path stop(MinecraftServer server) throws IOException {
        attach(server);
        DebugCategory.all().forEach(category -> category.enabled = false);
        return save(server);
    }

    public static synchronized void setEnabled(DebugCategory category, boolean enabled, @Nullable MinecraftServer server) {
        attach(server);
        category.enabled = enabled;
    }

    public static List<DebugCategory> enabledCategories() {
        return DebugCategory.all().stream().filter(DebugCategory::isEnabled).toList();
    }

    public static boolean isRecording() {
        return !enabledCategories().isEmpty();
    }

    public static synchronized void focus(@Nullable Entity soldier) {
        focus = soldier == null ? null : soldier.getUUID();
        focusName = soldier == null ? "" : shortName(soldier);
    }

    /**
     * Short name of the focused soldier, or empty when recording all soldiers.
     */
    public static synchronized String focusName() {
        return focusName;
    }

    public static synchronized void clear() {
        BUFFER.clear();
        LAST_BY_SOLDIER.clear();
    }

    public static synchronized void setCapacity(int newCapacity) {
        capacity = Math.max(1, newCapacity);
        while (BUFFER.size() > capacity) {
            BUFFER.removeFirst();
        }
    }

    public static synchronized int capacity() {
        return capacity;
    }

    public static synchronized int size() {
        return BUFFER.size();
    }

    /**
     * All buffered events, oldest first.
     */
    public static synchronized List<DebugEvent> events() {
        return List.copyOf(BUFFER);
    }

    /**
     * The last {@code count} events, oldest first.
     */
    public static synchronized List<DebugEvent> lastEvents(int count) {
        List<DebugEvent> all = new ArrayList<>(BUFFER);
        return all.subList(Math.max(0, all.size() - count), all.size());
    }

    /**
     * Turns everything off, clears the buffer and focus and restores the default capacity (used by tests).
     */
    public static synchronized void reset() {
        DebugCategory.all().forEach(category -> category.enabled = false);
        focus = null;
        focusName = "";
        capacity = DEFAULT_CAPACITY;
        clear();
    }

    private static void attach(@Nullable MinecraftServer newServer) {
        if (newServer != null) {
            server = newServer;
        }
    }

    // Output

    /**
     * The whole log as plain text: a short header, then one line per event, oldest first.
     */
    public static synchronized String toText() {
        List<String> lines = new ArrayList<>(header("Clay Soldiers Remake debug log"));
        BUFFER.forEach(event -> lines.add(event.toLine()));
        return String.join("\n", lines) + "\n";
    }

    private static List<String> header(String title) {
        Set<UUID> soldiers = new HashSet<>();
        BUFFER.forEach(event -> {
            if (event.soldierUuid() != null) {
                soldiers.add(event.soldierUuid());
            }
        });
        List<String> enabled = enabledCategories().stream().map(DebugCategory::id).toList();
        return List.of(
                "# " + title,
                "# saved: " + LocalDateTime.now().format(HEADER_TIME),
                "# categories on: " + (enabled.isEmpty() ? "none" : String.join(", ", enabled)),
                "# focus: " + (focusName.isEmpty() ? "all soldiers" : focusName),
                "# events: %d (oldest first, capacity %d), soldiers: %d".formatted(BUFFER.size(), capacity, soldiers.size()),
                "# line format: [t=game tick] CATEGORY team#entityId @(position) message (xN = repeated N times)");
    }

    /**
     * Saves the buffer to {@code <run folder>/debuglogs/csr-debug-<time>.txt} without stopping.
     */
    public static synchronized Path save(MinecraftServer server) throws IOException {
        attach(server);
        return writeFile(server, "csr-debug", toText());
    }

    /**
     * Writes text to a new timestamped file in {@code debuglogs/} and deletes the oldest files beyond {@link #MAX_FILES}.
     */
    public static Path writeFile(MinecraftServer server, String prefix, String text) throws IOException {
        Path folder = server.getServerDirectory().resolve(FOLDER);
        Files.createDirectories(folder);
        String time = LocalDateTime.now().format(FILE_TIME);
        Path file = folder.resolve(prefix + "-" + time + ".txt");
        for (int i = 2; Files.exists(file); i++) {
            file = folder.resolve(prefix + "-" + time + "-" + i + ".txt");
        }
        Files.writeString(file, text);
        pruneOldFiles(folder);
        return file;
    }

    private static void pruneOldFiles(Path folder) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(folder)) {
            files = stream.filter(path -> path.getFileName().toString().startsWith("csr-") && path.toString().endsWith(".txt"))
                    .sorted((a, b) -> Long.compare(lastModified(a), lastModified(b)))
                    .toList();
        }
        for (int i = 0; i < files.size() - MAX_FILES; i++) {
            Files.deleteIfExists(files.get(i));
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * One line per soldier: job, status, carried item, position, health, current work goal, attack target and team.
     */
    public static List<String> snapshot(List<? extends AbstractClaySoldierEntity> soldiers) {
        List<String> lines = new ArrayList<>();
        for (AbstractClaySoldierEntity soldier : soldiers) {
            Component status = soldier.getWorkStatus();
            lines.add("%s @(%s) hp %.1f/%.1f | job %s | status %s | carrying %s | goal %s | target %s | team %s".formatted(
                    shortName(soldier), soldier.blockPosition().toShortString(), soldier.getHealth(), soldier.getMaxHealth(),
                    soldier.getWorkSelector().getWorkDisplayName().getString(),
                    status == null ? "-" : status.getString(),
                    describe(soldier.getCarriedStack()),
                    soldier.getWorkSelector(),
                    soldier.getTarget() == null ? "none" : shortName(soldier.getTarget()),
                    soldier.getClayTeamType()));
        }
        return lines;
    }

    /**
     * Snapshot text with a header, for saving.
     */
    public static synchronized String snapshotText(List<? extends AbstractClaySoldierEntity> soldiers) {
        List<String> lines = new ArrayList<>(List.of(
                "# Clay Soldiers Remake soldier snapshot",
                "# saved: " + LocalDateTime.now().format(HEADER_TIME),
                "# soldiers: " + soldiers.size()));
        lines.addAll(snapshot(soldiers));
        return String.join("\n", lines) + "\n";
    }

    /**
     * Whether an event was recorded whose message contains the text (for tests).
     */
    public static synchronized boolean contains(DebugCategory category, String text) {
        return BUFFER.stream().anyMatch(event -> event.category() == category && Objects.requireNonNull(event.message()).contains(text));
    }
}
