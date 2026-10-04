package net.bumblebee.claysoldiers.tuning;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

/**
 * One number (or per-tier list of numbers) in the tuning file, with its default and allowed range.
 * Keys are namespaced, e.g. {@code kingdom.max_loaded}; define them in {@link Tuning} and document them in
 * {@code docs/TUNING.md}.
 *
 * @param <T> the value type
 */
public abstract sealed class TuningKey<T> permits TuningKey.IntKey, TuningKey.IntListKey {
    private final String id;
    private final String description;
    private final T defaultValue;

    private TuningKey(String id, String description, T defaultValue) {
        this.id = id;
        this.description = description;
        this.defaultValue = defaultValue;
    }

    public String id() {
        return id;
    }

    public String description() {
        return description;
    }

    public T defaultValue() {
        return defaultValue;
    }

    /**
     * The current value (from the tuning file, or the default when the file doesn't set it or sets a bad value).
     */
    public T get() {
        return Tuning.get(this);
    }

    /**
     * Reads a value from JSON, or throws {@link IllegalArgumentException} with a readable reason.
     */
    abstract T parse(JsonElement json);

    /**
     * The value as shown in commands and docs.
     */
    public abstract String format(T value);

    /**
     * The allowed values, as shown in commands and docs.
     */
    public abstract String range();

    static int parseInt(JsonElement json, int min, int max) {
        if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("expected a whole number, got " + json);
        }
        double number = json.getAsDouble();
        if (number != Math.rint(number)) {
            throw new IllegalArgumentException("expected a whole number, got " + json);
        }
        if (number < min || number > max) {
            throw new IllegalArgumentException(json + " is outside " + min + " to " + max);
        }
        return (int) number;
    }

    /**
     * A whole number between {@code min} and {@code max} (inclusive).
     */
    public static final class IntKey extends TuningKey<Integer> {
        private final int min;
        private final int max;

        IntKey(String id, String description, int defaultValue, int min, int max) {
            super(id, description, defaultValue);
            this.min = min;
            this.max = max;
        }

        @Override
        Integer parse(JsonElement json) {
            return parseInt(json, min, max);
        }

        @Override
        public String range() {
            return min + " to " + max;
        }

        @Override
        public String format(Integer value) {
            return String.valueOf(value);
        }
    }

    /**
     * One whole number per tier (exactly {@link Tuning#TIERS} of them), each between {@code min} and {@code max}.
     */
    public static final class IntListKey extends TuningKey<List<Integer>> {
        private final int min;
        private final int max;

        IntListKey(String id, String description, List<Integer> defaultValue, int min, int max) {
            super(id, description, List.copyOf(defaultValue));
            this.min = min;
            this.max = max;
        }

        @Override
        List<Integer> parse(JsonElement json) {
            if (!json.isJsonArray()) {
                throw new IllegalArgumentException("expected a list of " + Tuning.TIERS + " whole numbers (one per tier), got " + json);
            }
            JsonArray array = json.getAsJsonArray();
            if (array.size() != Tuning.TIERS) {
                throw new IllegalArgumentException("expected " + Tuning.TIERS + " numbers (one per tier), got " + array.size());
            }
            List<Integer> values = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                try {
                    values.add(parseInt(array.get(i), min, max));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("tier " + (i + 1) + ": " + e.getMessage());
                }
            }
            return List.copyOf(values);
        }

        @Override
        public String range() {
            return Tuning.TIERS + " numbers (tier 1 to " + Tuning.TIERS + "), each " + min + " to " + max;
        }

        @Override
        public String format(List<Integer> value) {
            return value.toString();
        }
    }
}
