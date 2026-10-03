package net.bumblebee.claysoldiers.debug;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * One recorded event. Repeats of the same message from the same soldier are collapsed into it ({@link #count()}).
 */
public final class DebugEvent {
    private final long sequence;
    private final long tick;
    private final DebugCategory category;
    @Nullable
    private final UUID soldierUuid;
    private final String soldierName;
    private final String position;
    private final String message;
    private long lastTick;
    private int count = 1;

    DebugEvent(long sequence, long tick, DebugCategory category, @Nullable UUID soldierUuid, String soldierName, String position, String message) {
        this.sequence = sequence;
        this.tick = tick;
        this.lastTick = tick;
        this.category = category;
        this.soldierUuid = soldierUuid;
        this.soldierName = soldierName;
        this.position = position;
        this.message = message;
    }

    void repeat(long tick) {
        count++;
        lastTick = tick;
    }

    boolean sameAs(DebugCategory category, String message) {
        return this.category == category && this.message.equals(message);
    }

    long sequence() {
        return sequence;
    }

    public long tick() {
        return tick;
    }

    public DebugCategory category() {
        return category;
    }

    @Nullable
    public UUID soldierUuid() {
        return soldierUuid;
    }

    public String message() {
        return message;
    }

    public int count() {
        return count;
    }

    /**
     * One line, e.g. {@code [t=1234] BUILD red#42 @(10, -60, 3) placed Cobblestone at (11, -60, 5) (x3 until t=1290)}.
     */
    public String toLine() {
        String repeats = count > 1 ? " (x%d until t=%d)".formatted(count, lastTick) : "";
        return "[t=%d] %s %s @(%s) %s%s".formatted(tick, category.id(), soldierName, position, message, repeats);
    }

    @Override
    public String toString() {
        return toLine();
    }
}
