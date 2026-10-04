package net.bumblebee.claysoldiers.kingdom;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import org.jetbrains.annotations.Nullable;
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
    /** Entries that couldn't be read; kept as they were so saving doesn't lose them. */
    private final List<Tag> unreadable = new ArrayList<>();
    /** The whole file when it was written by a newer format version: kept unchanged, nothing can be added. */
    @Nullable
    private CompoundTag newerFormat;

    private static final Codec<Kingdom.King> KING_CODEC = RecordCodecBuilder.create(in -> in.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Kingdom.King::id),
            Codec.STRING.optionalFieldOf("name").forGetter(Kingdom.King::name),
            BlockPos.CODEC.fieldOf("last_pos").forGetter(Kingdom.King::lastPos),
            Codec.LONG.fieldOf("seen_tick").forGetter(Kingdom.King::seenTick)
    ).apply(in, Kingdom.King::new));
    private static final Codec<Kingdom.Census> CENSUS_CODEC = RecordCodecBuilder.create(in -> in.group(
            Codec.INT.fieldOf("population").forGetter(Kingdom.Census::population),
            Codec.LONG.fieldOf("tick").forGetter(Kingdom.Census::tick)
    ).apply(in, Kingdom.Census::new));
    /** The saved format of one kingdom (version {@link #DATA_VERSION}). */
    public static final Codec<Kingdom> KINGDOM_CODEC = RecordCodecBuilder.create(in -> in.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Kingdom::id),
            ResourceLocation.CODEC.fieldOf("team").forGetter(Kingdom::team),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Kingdom::dimension),
            BlockPos.CODEC.fieldOf("center").forGetter(Kingdom::center),
            ExtraCodecs.POSITIVE_INT.fieldOf("claim_radius").forGetter(Kingdom::claimRadius),
            KING_CODEC.fieldOf("king").forGetter(Kingdom::king),
            KingdomStatus.CODEC.fieldOf("status").forGetter(Kingdom::status),
            ExtraCodecs.POSITIVE_INT.fieldOf("tier").forGetter(Kingdom::tier),
            Codec.unboundedMap(ResourceLocation.CODEC, ExtraCodecs.NON_NEGATIVE_INT).fieldOf("stockpile").forGetter(Kingdom::stockpile),
            KingdomBuilding.CODEC.listOf().fieldOf("buildings").forGetter(Kingdom::buildings),
            CENSUS_CODEC.fieldOf("census").forGetter(Kingdom::census),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("renown").forGetter(Kingdom::renown),
            Codec.LONG.fieldOf("founded_tick").forGetter(Kingdom::foundedTick),
            ChronicleEntry.CODEC.listOf().fieldOf("chronicle").forGetter(Kingdom::chronicle)
    ).apply(in, Kingdom::new));

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
     * Whether this world's kingdoms were saved by a newer version of the mod: they are kept unchanged and nothing can
     * be founded or changed.
     */
    public boolean isNewerFormat() {
        return newerFormat != null;
    }

    /**
     * Adds a kingdom; refuses a second one for the same team.
     */
    public void add(Kingdom kingdom) {
        if (newerFormat != null) {
            throw new IllegalStateException("Kingdoms were saved by a newer version of the mod; they can't be changed");
        }
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
        return KINGDOM_CODEC.encodeStart(NbtOps.INSTANCE, kingdom).getOrThrow(IllegalStateException::new);
    }

    /**
     * Decodes one kingdom, or throws with a readable reason.
     */
    public static Kingdom decode(Tag tag) {
        return KINGDOM_CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(IllegalArgumentException::new);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (newerFormat != null) {
            return newerFormat.copy();
        }
        tag.putInt(VERSION_TAG, DATA_VERSION);
        ListTag kingdoms = new ListTag();
        byTeam.values().forEach(kingdom -> kingdoms.add(encode(kingdom)));
        unreadable.forEach(raw -> kingdoms.add(raw.copy()));
        tag.put(KINGDOMS_TAG, kingdoms);
        return tag;
    }

    public static KingdomSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        KingdomSavedData data = new KingdomSavedData();
        int version = tag.contains(VERSION_TAG) ? tag.getInt(VERSION_TAG) : DATA_VERSION;
        if (version > DATA_VERSION) {
            ClaySoldiersCommon.LOGGER.error("CSR kingdoms: saved with data version {} by a newer version of the mod (this one reads {}). "
                    + "They are kept unchanged and can't be changed until you update the mod.", version, DATA_VERSION);
            data.newerFormat = tag.copy();
            return data;
        }
        for (Tag entry : tag.getList(KINGDOMS_TAG, Tag.TAG_COMPOUND)) {
            try {
                Kingdom kingdom = decode(entry);
                if (data.byTeam.putIfAbsent(kingdom.team(), kingdom) != null) {
                    throw new IllegalArgumentException("a second kingdom for team " + kingdom.team());
                }
            } catch (RuntimeException e) {
                ClaySoldiersCommon.LOGGER.error("CSR kingdoms: can't read a saved kingdom ({}); it is kept unchanged in the file", e.getMessage());
                data.unreadable.add(entry.copy());
            }
        }
        return data;
    }

    /**
     * The kingdoms as a list (save order).
     */
    List<Kingdom> list() {
        return new ArrayList<>(byTeam.values());
    }
}
