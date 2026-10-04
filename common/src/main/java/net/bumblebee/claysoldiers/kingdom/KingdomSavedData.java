package net.bumblebee.claysoldiers.kingdom;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * All kingdoms of a world, saved in {@code <world>/data/csr_kingdoms.dat} (the overworld's data folder, for every
 * dimension). The file carries {@link #DATA_VERSION}; see the migration notes in {@code docs/DECISIONS.md}.
 * At most one kingdom per team.
 */
public class KingdomSavedData extends SavedData {
    public static final String NAME = "csr_kingdoms";
    /** Version of the saved format. Raise it (and write a migration note) whenever the format changes. */
    public static final int DATA_VERSION = 1;
    static final String VERSION_TAG = "csr_data_version";
    static final String KINGDOMS_TAG = "kingdoms";

    private final Map<ResourceLocation, Kingdom> byTeam = new LinkedHashMap<>();

    public static KingdomSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(KingdomSavedData::new, KingdomSavedData::load, null), NAME);
    }

    public Collection<Kingdom> all() {
        return Collections.unmodifiableCollection(byTeam.values());
    }

    public Optional<Kingdom> byTeam(ResourceLocation team) {
        return Optional.ofNullable(byTeam.get(team));
    }

    /**
     * Adds a kingdom; refuses a second one for the same team.
     */
    public void add(Kingdom kingdom) {
        if (byTeam.putIfAbsent(kingdom.team(), kingdom) != null) {
            throw new IllegalStateException("Team " + kingdom.team() + " already has a kingdom");
        }
        setDirty();
    }

    public Optional<Kingdom> remove(ResourceLocation team) {
        Optional<Kingdom> removed = Optional.ofNullable(byTeam.remove(team));
        removed.ifPresent(kingdom -> setDirty());
        return removed;
    }

    /**
     * Encodes one kingdom.
     */
    public static Tag encode(Kingdom kingdom) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    /**
     * Decodes one kingdom, or throws with a readable reason.
     */
    public static Kingdom decode(Tag tag) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    public static KingdomSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    /**
     * The kingdoms as a list (save order).
     */
    List<Kingdom> list() {
        return new ArrayList<>(byTeam.values());
    }
}
