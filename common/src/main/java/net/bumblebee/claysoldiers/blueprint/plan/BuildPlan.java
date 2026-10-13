package net.bumblebee.claysoldiers.blueprint.plan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A pre-baked build plan for one structure level (D82), worked out offline by {@code tools/build_plan.py} from the
 * structure's block list: the order to build in, as waves, and for every block a few spots a builder can stand on to place
 * it. Inside a wave the order is free (every block of a wave can be placed whichever of the others are already there),
 * which lets builders pick at random and spread out without leaving the plan.
 * <p>
 * Positions are in the structure file's own coordinates (before rotation). Blocks the plan doesn't know (left out of it,
 * e.g. those needing scaffold, or a file changed since its plan was made) come after every planned block, in the
 * blueprint's normal order.
 */
public final class BuildPlan {
    /** The wave of a block the plan doesn't place. */
    public static final int UNPLANNED = Integer.MAX_VALUE;
    public static final int FORMAT = 1;

    /** One planned block: its place in the build order, its wave, and its standing spots (packed, see {@link #unpack}). */
    private record Step(int index, int wave, int[] spots) {
    }

    private final Map<BlockPos, Step> steps;
    private final int waves;
    /** Blocks the plan file lists as left out of the plan (not saved with a build: only for checking a file). */
    private final Set<BlockPos> unplanned;

    private BuildPlan(Map<BlockPos, Step> steps, int waves, Set<BlockPos> unplanned) {
        this.steps = steps;
        this.waves = waves;
        this.unplanned = unplanned;
    }

    /** Reads a plan file (format 1); {@code null} when it isn't one. */
    @Nullable
    public static BuildPlan fromJson(JsonObject json) {
        if (!json.has("format") || json.get("format").getAsInt() != FORMAT || !json.has("blocks")) {
            return null;
        }
        Map<BlockPos, Step> steps = new HashMap<>();
        int index = 0;
        int waves = 0;
        for (JsonElement element : json.getAsJsonArray("blocks")) {
            JsonArray row = element.getAsJsonArray();
            int[] spots = new int[row.size() - 4];
            for (int i = 0; i < spots.length; i++) {
                spots[i] = row.get(4 + i).getAsInt();
            }
            int wave = row.get(3).getAsInt();
            waves = Math.max(waves, wave + 1);
            steps.put(new BlockPos(row.get(0).getAsInt(), row.get(1).getAsInt(), row.get(2).getAsInt()), new Step(index++, wave, spots));
        }
        Set<BlockPos> unplanned = new HashSet<>();
        if (json.has("unplanned")) {
            for (JsonElement element : json.getAsJsonArray("unplanned")) {
                JsonArray row = element.getAsJsonArray();
                unplanned.add(new BlockPos(row.get(0).getAsInt(), row.get(1).getAsInt(), row.get(2).getAsInt()));
            }
        }
        return new BuildPlan(steps, waves, unplanned);
    }

    /** Saved with the easel's plan, so a build keeps its plan across a restart. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        steps.entrySet().stream().sorted(Comparator.comparingInt(e -> e.getValue().index())).forEach(e -> {
            BlockPos pos = e.getKey();
            int[] row = new int[4 + e.getValue().spots().length];
            row[0] = pos.getX();
            row[1] = pos.getY();
            row[2] = pos.getZ();
            row[3] = e.getValue().wave();
            System.arraycopy(e.getValue().spots(), 0, row, 4, e.getValue().spots().length);
            list.add(new IntArrayTag(row));
        });
        tag.put("blocks", list);
        tag.putInt("format", FORMAT);
        return tag;
    }

    @Nullable
    public static BuildPlan load(CompoundTag tag) {
        if (tag.getInt("format") != FORMAT) {
            return null;
        }
        Map<BlockPos, Step> steps = new HashMap<>();
        int waves = 0;
        ListTag list = tag.getList("blocks", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            int[] row = list.getIntArray(i);
            if (row.length < 4) {
                continue;
            }
            int[] spots = new int[row.length - 4];
            System.arraycopy(row, 4, spots, 0, spots.length);
            waves = Math.max(waves, row[3] + 1);
            steps.put(new BlockPos(row[0], row[1], row[2]), new Step(i, row[3], spots));
        }
        return new BuildPlan(steps, waves, Set.of());
    }

    /** {@code items} in the plan's order: planned blocks first, by their place in the plan; the rest after, in their order. */
    public <T> List<T> order(List<T> items, Function<T, BlockPos> position) {
        List<T> planned = new ArrayList<>();
        List<T> rest = new ArrayList<>();
        for (T item : items) {
            (steps.containsKey(position.apply(item)) ? planned : rest).add(item);
        }
        planned.sort(Comparator.comparingInt(item -> steps.get(position.apply(item)).index()));
        planned.addAll(rest);
        return planned;
    }

    /** The wave of the block at {@code pos} (structure coordinates), or {@link #UNPLANNED}. */
    public int wave(BlockPos pos) {
        Step step = steps.get(pos);
        return step == null ? UNPLANNED : step.wave();
    }

    /** Where a builder can stand to place the block at {@code pos}, in structure coordinates (empty when unplanned). */
    public List<BlockPos> spots(BlockPos pos) {
        Step step = steps.get(pos);
        if (step == null) {
            return List.of();
        }
        List<BlockPos> spots = new ArrayList<>(step.spots().length);
        for (int packed : step.spots()) {
            spots.add(unpack(pos, packed));
        }
        return spots;
    }

    /** A packed spot: (dx + 2) + 5 * (dz + 2) + 25 * (dy + 8), with (dx, dy, dz) = feet - block. */
    static BlockPos unpack(BlockPos block, int packed) {
        int dx = packed % 5 - 2;
        int dz = (packed / 5) % 5 - 2;
        int dy = packed / 25 - 8;
        return block.offset(dx, dy, dz);
    }

    public int plannedBlocks() {
        return steps.size();
    }

    public int waves() {
        return waves;
    }

    public boolean covers(BlockPos pos) {
        return steps.containsKey(pos);
    }

    /** Whether the plan file knows the block at {@code pos}: planned, or listed as left out of the plan. */
    public boolean knows(BlockPos pos) {
        return steps.containsKey(pos) || unplanned.contains(pos);
    }

    @Override
    public String toString() {
        return "BuildPlan{%s blocks in %s waves}".formatted(steps.size(), waves);
    }
}
