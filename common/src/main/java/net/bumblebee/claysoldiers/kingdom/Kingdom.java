package net.bumblebee.claysoldiers.kingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One kingdom: a team's claimed land around where its king stood when it was founded. Saved in the world
 * ({@link KingdomSavedData}). No building or economy behavior yet (stage 1): this is data that exists, persists and
 * can be observed. Change it through {@code Kingdoms}, which marks the data for saving and records events.
 */
public final class Kingdom {
    private final UUID id;
    private final ResourceLocation team;
    private final ResourceKey<Level> dimension;
    private final BlockPos center;
    private final int claimRadius;
    private King king;
    private KingdomStatus status;
    private int tier;
    private final TreeMap<ResourceLocation, Integer> stockpile;
    private final List<KingdomBuilding> buildings;
    private Census census;
    private int renown;
    private final long foundedTick;
    private final List<ChronicleEntry> chronicle;

    /**
     * The king.
     *
     * @param id       the king soldier's UUID
     * @param name     its custom name, if it has one
     * @param lastPos  where it was last seen
     * @param seenTick game time it was last seen
     */
    public record King(UUID id, Optional<String> name, BlockPos lastPos, long seenTick) {
    }

    /**
     * The last population count. Soldiers in unloaded chunks can't be counted, so the count is only updated when the
     * whole claim is loaded; otherwise the last count stays (see {@code Kingdoms.census}).
     *
     * @param population clay soldiers of the team inside the claim
     * @param tick       game time of the count, or -1 if it was never counted
     */
    public record Census(int population, long tick) {
        public static final Census NEVER = new Census(0, -1);
    }

    public Kingdom(UUID id, ResourceLocation team, ResourceKey<Level> dimension, BlockPos center, int claimRadius,
                   King king, KingdomStatus status, int tier, Map<ResourceLocation, Integer> stockpile,
                   List<KingdomBuilding> buildings, Census census, int renown, long foundedTick, List<ChronicleEntry> chronicle) {
        this.id = id;
        this.team = team;
        this.dimension = dimension;
        this.center = center.immutable();
        this.claimRadius = claimRadius;
        this.king = king;
        this.status = status;
        this.tier = tier;
        this.stockpile = new TreeMap<>(stockpile);
        this.buildings = new ArrayList<>(buildings);
        this.census = census;
        this.renown = renown;
        this.foundedTick = foundedTick;
        this.chronicle = new ArrayList<>(chronicle);
    }

    /**
     * A newly founded kingdom: tier 1, active, empty stockpile, no buildings, never counted.
     */
    public static Kingdom found(ResourceLocation team, ResourceKey<Level> dimension, BlockPos center, int claimRadius,
                                UUID kingId, @Nullable String kingName, long gameTime) {
        return new Kingdom(UUID.randomUUID(), team, dimension, center, claimRadius,
                new King(kingId, Optional.ofNullable(kingName), center, gameTime), KingdomStatus.ACTIVE, 1, Map.of(),
                List.of(), Census.NEVER, 0, gameTime, List.of());
    }

    // Stockpile

    /**
     * How much of {@code resource} the stockpile holds (0 if none).
     */
    public int resource(ResourceLocation resource) {
        return stockpile.getOrDefault(resource, 0);
    }

    /**
     * Adds {@code amount} (0 or more) of {@code resource}.
     */
    public void addResource(ResourceLocation resource, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Can't add a negative amount (" + amount + ") of " + resource + "; use removeResource");
        }
        if (amount > 0) {
            stockpile.merge(resource, amount, (a, b) -> (int) Math.min(Integer.MAX_VALUE, (long) a + b));
        }
    }

    /**
     * Takes {@code amount} of {@code resource}, all or nothing: if the stockpile has less, nothing is taken and the
     * shortfall is reported. The stockpile never goes negative.
     */
    public Withdrawal removeResource(ResourceLocation resource, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Can't remove a negative amount (" + amount + ") of " + resource);
        }
        int have = resource(resource);
        if (have < amount) {
            return new Withdrawal(false, amount - have);
        }
        if (have == amount) {
            stockpile.remove(resource);
        } else {
            stockpile.put(resource, have - amount);
        }
        return new Withdrawal(true, 0);
    }

    /**
     * The result of {@link #removeResource}.
     *
     * @param done      whether it was taken
     * @param shortfall how much was missing (0 when done)
     */
    public record Withdrawal(boolean done, int shortfall) {
    }

    // Chronicle

    /**
     * Adds a line to the chronicle, dropping the oldest lines beyond {@code max}.
     */
    public void addChronicle(ChronicleEntry entry, int max) {
        chronicle.add(entry);
        trimChronicle(max);
    }

    /**
     * Drops the oldest lines beyond {@code max} (e.g. after the tuning value was lowered).
     */
    public void trimChronicle(int max) {
        while (chronicle.size() > Math.max(0, max)) {
            chronicle.removeFirst();
        }
    }

    // Getters and setters

    public UUID id() {
        return id;
    }

    public ResourceLocation team() {
        return team;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public BlockPos center() {
        return center;
    }

    public int claimRadius() {
        return claimRadius;
    }

    public King king() {
        return king;
    }

    void setKing(King king) {
        this.king = king;
    }

    public KingdomStatus status() {
        return status;
    }

    void setStatus(KingdomStatus status) {
        this.status = status;
    }

    public int tier() {
        return tier;
    }

    public Map<ResourceLocation, Integer> stockpile() {
        return Collections.unmodifiableMap(stockpile);
    }

    public List<KingdomBuilding> buildings() {
        return Collections.unmodifiableList(buildings);
    }

    public void addBuilding(KingdomBuilding building) {
        buildings.add(building);
    }

    public Census census() {
        return census;
    }

    void setCensus(Census census) {
        this.census = census;
    }

    public int renown() {
        return renown;
    }

    public long foundedTick() {
        return foundedTick;
    }

    public List<ChronicleEntry> chronicle() {
        return Collections.unmodifiableList(chronicle);
    }

    /**
     * Whether a block column is inside the claim (a circle around the center; height doesn't matter).
     */
    public boolean claims(ResourceKey<Level> dimension, BlockPos pos) {
        if (!this.dimension.equals(dimension)) {
            return false;
        }
        long dx = pos.getX() - center.getX();
        long dz = pos.getZ() - center.getZ();
        return dx * dx + dz * dz <= (long) claimRadius * claimRadius;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Kingdom other)) return false;
        return claimRadius == other.claimRadius && tier == other.tier && renown == other.renown && foundedTick == other.foundedTick
                && id.equals(other.id) && team.equals(other.team) && dimension.equals(other.dimension) && center.equals(other.center)
                && king.equals(other.king) && status == other.status && stockpile.equals(other.stockpile)
                && buildings.equals(other.buildings) && census.equals(other.census) && chronicle.equals(other.chronicle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, team, dimension, center, claimRadius, king, status, tier, stockpile, buildings, census, renown,
                foundedTick, chronicle);
    }

    @Override
    public String toString() {
        return "Kingdom{" + team + " " + status + " tier " + tier + " at " + center.toShortString() + " r" + claimRadius
                + " in " + dimension.location() + ", king " + king.id() + "}";
    }
}
