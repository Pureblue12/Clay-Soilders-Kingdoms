package net.bumblebee.claysoldiers.debug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A recorder category. Categories are data: add one with {@link #register} (e.g. below) and it works in the
 * recorder, the {@code /csr dev log on|off} commands and the dev book automatically.
 * <p>
 * Each category holds its own on/off flag, so {@link DebugLog#event} can return after reading one field.
 */
public final class DebugCategory {
    private static final List<DebugCategory> ALL = new ArrayList<>();

    public static final DebugCategory BUILD = register("BUILD", "Blueprint requests, placing, giving up");
    public static final DebugCategory WORK = register("WORK", "Job changes");
    public static final DebugCategory DAMAGE = register("DAMAGE", "Damage to clay soldiers");
    public static final DebugCategory PATHING = register("PATHING", "Why a block counts as unreachable");
    public static final DebugCategory TEAM = register("TEAM", "Team and loyalty changes");
    public static final DebugCategory STRUCTURE = register("STRUCTURE", "Structure registry: paste, undo, reload, validation problems");
    public static final DebugCategory KINGDOM = register("KINGDOM", "Kingdoms: founding, disbanding, king death, stockpile changes, refusals");

    /** The categories turned on by "Start recording". */
    public static final List<DebugCategory> PRESET = List.of(BUILD, WORK, DAMAGE);

    private final String id;
    private final String description;
    volatile boolean enabled;

    private DebugCategory(String id, String description) {
        this.id = id;
        this.description = description;
    }

    /**
     * Registers a new category. Call from a static field like the ones above.
     */
    public static synchronized DebugCategory register(String id, String description) {
        if (byId(id).isPresent()) {
            throw new IllegalArgumentException("Duplicate debug category " + id);
        }
        DebugCategory category = new DebugCategory(id, description);
        ALL.add(category);
        return category;
    }

    public static List<DebugCategory> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Optional<DebugCategory> byId(String id) {
        return ALL.stream().filter(category -> category.id.equalsIgnoreCase(id)).findFirst();
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return id;
    }
}
