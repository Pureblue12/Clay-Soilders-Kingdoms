package net.bumblebee.claysoldiers.scaffold;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every scaffold job of one dimension, saved in {@code <world>/<dimension>/data/csr_scaffold.dat} with
 * {@link #DATA_VERSION}, so scaffold placed before a save or restart is still known and gets removed. Change jobs
 * through {@link Scaffolds} (it marks this dirty).
 */
public class ScaffoldSavedData extends SavedData {
    public static final String NAME = "csr_scaffold";
    /** Version of the saved format. Raise it (and write a migration note in DECISIONS.md) when the format changes. */
    public static final int DATA_VERSION = 1;
    static final String VERSION_TAG = "csr_data_version";
    static final String JOBS_TAG = "jobs";

    private final Map<BlockPos, ScaffoldJob> jobs = new LinkedHashMap<>();
    /** The whole file when it was written by a newer format version: kept unchanged. */
    @Nullable
    private CompoundTag newerFormat;

    public static ScaffoldSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(ScaffoldSavedData::new, ScaffoldSavedData::load, null), NAME);
    }

    public Optional<ScaffoldJob> job(BlockPos easel) {
        return Optional.ofNullable(jobs.get(easel));
    }

    public Collection<ScaffoldJob> jobs() {
        return Collections.unmodifiableCollection(jobs.values());
    }

    public void put(ScaffoldJob job) {
        jobs.put(job.easel(), job);
        setDirty();
    }

    public void remove(BlockPos easel) {
        if (jobs.remove(easel) != null) {
            setDirty();
        }
    }

    /** Whether a placed scaffold block of any job is at {@code pos}. */
    public boolean isScaffold(BlockPos pos) {
        for (ScaffoldJob job : jobs.values()) {
            if (job.placed().contains(pos)) {
                return true;
            }
        }
        return false;
    }

    public boolean isNewerFormat() {
        return newerFormat != null;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (newerFormat != null) {
            return newerFormat.copy();
        }
        tag.putInt(VERSION_TAG, DATA_VERSION);
        ListTag list = new ListTag();
        for (ScaffoldJob job : jobs.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("easel", job.easel().asLong());
            entry.putString("material", job.material().toString());
            entry.put("planned", positions(job.planned()));
            entry.put("placed", positions(job.placed()));
            entry.putBoolean("columns", job.columns());
            list.add(entry);
        }
        tag.put(JOBS_TAG, list);
        return tag;
    }

    public static ScaffoldSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ScaffoldSavedData data = new ScaffoldSavedData();
        int version = tag.contains(VERSION_TAG) ? tag.getInt(VERSION_TAG) : DATA_VERSION;
        if (version > DATA_VERSION) {
            ClaySoldiersCommon.LOGGER.error("CSR scaffold: saved with data version {} by a newer version of the mod (this one reads {}); "
                    + "kept unchanged, scaffold from it won't be removed until you update the mod.", version, DATA_VERSION);
            data.newerFormat = tag.copy();
            return data;
        }
        for (Tag raw : tag.getList(JOBS_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            ResourceLocation material = ResourceLocation.tryParse(entry.getString("material"));
            if (material == null) {
                ClaySoldiersCommon.LOGGER.error("CSR scaffold: a saved job has a bad material {}; skipped", entry.getString("material"));
                continue;
            }
            BlockPos easel = BlockPos.of(entry.getLong("easel"));
            List<BlockPos> planned = new ArrayList<>(readPositions(entry.getLongArray("planned")));
            Set<BlockPos> placed = new LinkedHashSet<>(readPositions(entry.getLongArray("placed")));
            data.jobs.put(easel, new ScaffoldJob(easel, material, planned, placed, entry.getBoolean("columns")));
        }
        return data;
    }

    private static LongArrayTag positions(Collection<BlockPos> positions) {
        return new LongArrayTag(positions.stream().mapToLong(BlockPos::asLong).toArray());
    }

    private static List<BlockPos> readPositions(long[] values) {
        List<BlockPos> positions = new ArrayList<>(values.length);
        for (long value : values) {
            positions.add(BlockPos.of(value));
        }
        return positions;
    }
}
