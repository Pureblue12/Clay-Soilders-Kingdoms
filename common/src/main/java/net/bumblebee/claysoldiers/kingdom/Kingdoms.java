package net.bumblebee.claysoldiers.kingdom;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.soldierproperties.customproperties.AttackTypeProperty;
import net.bumblebee.claysoldiers.team.ClayMobTeamManger;
import net.bumblebee.claysoldiers.terrain.SiteScore;
import net.bumblebee.claysoldiers.terrain.SiteSearch;
import net.bumblebee.claysoldiers.terrain.Terrain;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Everything that changes kingdoms goes through here: it keeps the saved data dirty, writes the chronicle and records
 * KINGDOM events. A kingdom is founded by its king: a soldier who picks up a gold ingot founds one by himself where
 * he likes ({@link KingFounding}, D93), or a command founds it at once. Always reversible ({@link #disband}).
 */
public final class Kingdoms {
    /** Dimensions a kingdom can be founded in. */
    public static final Set<ResourceKey<Level>> SUPPORTED_DIMENSIONS = Set.of(Level.OVERWORLD);

    private Kingdoms() {
    }

    /**
     * The result of founding or disbanding: the kingdom, or why it was refused.
     */
    public record Result(@Nullable Kingdom kingdom, @Nullable String refusal, List<String> warnings) {
        public Result(@Nullable Kingdom kingdom, @Nullable String refusal) {
            this(kingdom, refusal, List.of());
        }

        public boolean ok() {
            return refusal == null;
        }

        static Result refused(String why) {
            return new Result(null, why);
        }
    }

    public static List<Kingdom> all(MinecraftServer server) {
        return List.copyOf(KingdomSavedData.get(server).all());
    }

    public static Optional<Kingdom> byTeam(MinecraftServer server, ResourceLocation team) {
        return KingdomSavedData.get(server).byTeam(team);
    }

    /** Whether the soldier is a king (wears a gold ingot: attack type KING). */
    public static boolean isKing(AbstractClaySoldierEntity soldier) {
        return soldier.getAttackType() == AttackTypeProperty.KING;
    }

    /** Whether {@code soldier}'s team has an active kingdom (its soldiers work for it, D93). */
    public static boolean hasActiveKingdom(AbstractClaySoldierEntity soldier) {
        MinecraftServer server = soldier.getServer();
        return server != null && byTeam(server, soldier.getClayTeamType()).filter(kingdom -> kingdom.status() == KingdomStatus.ACTIVE).isPresent();
    }

    /** The nearest living king without a kingdom, within {@code kingdom.found_search_radius} of {@code pos}. */
    public static Optional<AbstractClaySoldierEntity> findNearestKing(ServerLevel level, Vec3 pos) {
        double radius = Tuning.KINGDOM_FOUND_SEARCH_RADIUS.get();
        return level.getEntitiesOfClass(AbstractClaySoldierEntity.class, new AABB(pos, pos).inflate(radius),
                        soldier -> soldier.isAlive() && isKing(soldier) && soldier.distanceToSqr(pos) <= radius * radius
                                && byTeam(level.getServer(), soldier.getClayTeamType()).isEmpty())
                .stream().min(Comparator.comparingDouble(soldier -> soldier.distanceToSqr(pos)));
    }

    /** Founds a kingdom around the nearest king without one (see {@link #findNearestKing}). */
    public static Result foundNear(ServerPlayer player) {
        return foundNear(player, false);
    }

    /**
     * {@link #foundNear(ServerPlayer)}; with {@code force} the terrain check is skipped (operators only: the command
     * checks the permission).
     */
    public static Result foundNear(ServerPlayer player, boolean force) {
        Optional<AbstractClaySoldierEntity> king = findNearestKing(player.serverLevel(), player.position());
        if (king.isEmpty()) {
            return refuse(null, null, "There is no king without a kingdom within " + Tuning.KINGDOM_FOUND_SEARCH_RADIUS.get()
                    + " blocks. (A king is a clay soldier wearing a gold ingot: drop one by a soldier.)");
        }
        return found(player.serverLevel(), king.get(), player, force);
    }

    /**
     * Founds a kingdom for {@code king}'s team around the king. Refused when: the dimension isn't supported, it isn't a
     * living king, the team already has a kingdom, the claim overlaps
     * another kingdom's, or the ground around the king can't hold a keep (terrain check: cliff, lava, deep water, too
     * steep; soft problems become warnings). When part of the site isn't loaded the terrain check is skipped.
     */
    public static Result found(ServerLevel level, AbstractClaySoldierEntity king, @Nullable Player founder) {
        return found(level, king, founder, false);
    }

    /**
     * Founds a kingdom; with {@code force} the terrain check is skipped (operators' testing tool, logged).
     */
    public static Result found(ServerLevel level, AbstractClaySoldierEntity king, @Nullable Player founder, boolean force) {
        MinecraftServer server = level.getServer();
        KingdomSavedData data = KingdomSavedData.get(server);
        ResourceLocation team = king.getClayTeamType();
        if (data.isNewerFormat()) {
            return refuse(king, team, "This world's kingdoms were saved by a newer version of the mod; update it to change them.");
        }
        if (!SUPPORTED_DIMENSIONS.contains(level.dimension())) {
            return refuse(king, team, "Kingdoms can only be founded in the Overworld for now, not in " + level.dimension().location() + ".");
        }
        if (!king.isAlive() || !isKing(king)) {
            return refuse(king, team, "That soldier isn't a living king (a king is a clay soldier wearing a gold ingot).");
        }
        Optional<Kingdom> existing = data.byTeam(team);
        if (existing.isPresent()) {
            return refuse(king, team, teamName(server, team) + " already has a kingdom (around " + existing.get().center().toShortString()
                    + ", " + existing.get().status().getSerializedName() + "). Disband it first.");
        }
        BlockPos center = king.blockPosition();
        int radius = Tuning.KINGDOM_CLAIM_RADIUS.get();
        for (Kingdom other : data.all()) {
            if (other.dimension().equals(level.dimension()) && overlaps(center, radius, other)) {
                return refuse(king, team, "The claim (radius " + radius + " around " + center.toShortString() + ") overlaps the kingdom of "
                        + teamName(server, other.team()) + " (radius " + other.claimRadius() + " around " + other.center().toShortString() + ").");
            }
        }
        List<String> warnings = List.of();
        if (force) {
            String by = founder == null ? "" : " by " + founder.getName().getString();
            DebugLog.event(DebugCategory.KINGDOM, king, "terrain check skipped for %s: forced%s", team, by);
            DebugLog.event(DebugCategory.TERRAIN, king, "founding at %s: terrain check skipped (forced%s)", center.toShortString(), by);
        } else {
            Terrain.SiteCheck check = Terrain.checkSite(level, center, true);
            SiteScore score = check.score();
            if (score.unknown()) {
                // Not all of the site is loaded: keep the old behavior (found without a terrain check), but record it.
                DebugLog.event(DebugCategory.TERRAIN, king, "founding at %s: terrain check skipped, %s", center.toShortString(), score.describe());
            } else if (!score.ok()) {
                String nearby = check.search().flatMap(SiteSearch.Result::best)
                        .map(best -> " The best site nearby is at " + best.x() + ", " + best.z() + " (score " + best.score().score()
                                + ", " + best.distance() + " blocks away): move the king there.")
                        .orElse(" There is no usable site within " + Tuning.TERRAIN_SEARCH_RADIUS.get() + " blocks either.");
                DebugLog.event(DebugCategory.TERRAIN, king, "founding refused at %s: %s", center.toShortString(), score.describe());
                return refuse(king, team, "The ground around the king can't hold a keep: "
                        + score.refusals().stream().map(SiteScore.Reason::text).collect(Collectors.joining("; ")) + "." + nearby);
            } else {
                warnings = score.penalties().stream().map(reason -> reason.text() + " (-" + reason.points() + ")").toList();
                if (!warnings.isEmpty()) {
                    DebugLog.event(DebugCategory.TERRAIN, king, "founding at %s with warnings: %s", center.toShortString(), score.describe());
                }
            }
        }
        Kingdom kingdom = Kingdom.found(team, level.dimension(), center, radius, king.getUUID(),
                king.hasCustomName() ? king.getCustomName().getString() : null, level.getGameTime());
        data.add(kingdom);
        chronicle(server, kingdom, "The kingdom of " + teamName(server, team) + " was founded" + (founder == null ? "" : " by " + founder.getName().getString())
                + " around " + kingTitle(server, kingdom, false) + " at " + center.toShortString() + ".");
        DebugLog.event(DebugCategory.KINGDOM, king, () -> "founded the kingdom of %s at %s, radius %d%s".formatted(team, center.toShortString(), radius,
                founder == null ? "" : " (by " + founder.getName().getString() + ")"));
        return new Result(kingdom, null, warnings);
    }

    private static boolean overlaps(BlockPos center, int radius, Kingdom other) {
        long dx = center.getX() - other.center().getX();
        long dz = center.getZ() - other.center().getZ();
        long reach = (long) radius + other.claimRadius();
        return dx * dx + dz * dz < reach * reach;
    }

    private static Result refuse(@Nullable AbstractClaySoldierEntity king, @Nullable ResourceLocation team, String why) {
        DebugLog.event(DebugCategory.KINGDOM, king, "found refused%s: %s", team == null ? "" : " for " + team, why);
        return Result.refused(why);
    }

    /** Removes {@code team}'s kingdom. The world is not touched. */
    public static Result disband(MinecraftServer server, ResourceLocation team, @Nullable Player by) {
        KingdomSavedData data = KingdomSavedData.get(server);
        if (data.isNewerFormat()) {
            return Result.refused("This world's kingdoms were saved by a newer version of the mod; update it to change them.");
        }
        Optional<Kingdom> removed = data.remove(team);
        if (removed.isEmpty()) {
            DebugLog.event(DebugCategory.KINGDOM, null, "disband refused for %s: no kingdom", team);
            return Result.refused(teamName(server, team) + " has no kingdom.");
        }
        DebugLog.event(DebugCategory.KINGDOM, null, "disbanded the kingdom of %s%s", team, by == null ? "" : " (by " + by.getName().getString() + ")");
        KingdomKeep.uncrown(server, removed.get());
        return new Result(removed.get(), null);
    }

    /** Called when a clay soldier dies: a kingdom whose king it was becomes RUINED. */
    public static void onSoldierDeath(AbstractClaySoldierEntity soldier, DamageSource source) {
        MinecraftServer server = soldier.getServer();
        if (server == null) {
            return;
        }
        for (Kingdom kingdom : KingdomSavedData.get(server).all()) {
            if (kingdom.status() == KingdomStatus.ACTIVE && kingdom.king().id().equals(soldier.getUUID())) {
                kingdom.setKing(new Kingdom.King(kingdom.king().id(), kingdom.king().name(), soldier.blockPosition(), soldier.level().getGameTime()));
                kingdom.setStatus(KingdomStatus.RUINED);
                String killer = source.getEntity() == null || source.getEntity() == soldier ? "" : " (killed by " + source.getEntity().getName().getString() + ")";
                chronicle(server, kingdom, kingTitle(server, kingdom, true) + " has died" + killer + ". The kingdom of "
                        + teamName(server, kingdom.team()) + " lies in ruins.");
                DebugLog.event(DebugCategory.KINGDOM, soldier, "king of %s died at %s: kingdom RUINED", kingdom.team(), soldier.blockPosition());
            }
        }
    }

    public static void addResource(MinecraftServer server, Kingdom kingdom, ResourceLocation resource, int amount) {
        kingdom.addResource(resource, amount);
        KingdomSavedData.get(server).setDirty();
        DebugLog.event(DebugCategory.KINGDOM, null, () -> "stockpile of %s: +%d %s (now %d)".formatted(kingdom.team(), amount, resource, kingdom.resource(resource)));
    }

    public static Kingdom.Withdrawal removeResource(MinecraftServer server, Kingdom kingdom, ResourceLocation resource, int amount) {
        Kingdom.Withdrawal withdrawal = kingdom.removeResource(resource, amount);
        if (withdrawal.done()) {
            KingdomSavedData.get(server).setDirty();
            DebugLog.event(DebugCategory.KINGDOM, null, () -> "stockpile of %s: -%d %s (now %d)".formatted(kingdom.team(), amount, resource, kingdom.resource(resource)));
        } else {
            DebugLog.event(DebugCategory.KINGDOM, null, () -> "stockpile of %s: shortfall of %d %s (wanted %d, has %d), nothing taken"
                    .formatted(kingdom.team(), withdrawal.shortfall(), resource, amount, kingdom.resource(resource)));
        }
        return withdrawal;
    }

    /** Adds a chronicle line (game time now), keeping at most {@code kingdom.chronicle_max} lines. */
    public static void chronicle(MinecraftServer server, Kingdom kingdom, String text) {
        kingdom.addChronicle(new ChronicleEntry(server.overworld().getGameTime(), text), Tuning.KINGDOM_CHRONICLE_MAX.get());
        KingdomSavedData.get(server).setDirty();
    }

    /** Runs the census for active kingdoms every {@code kingdom.census_interval_ticks}. Called every server tick. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % Tuning.KINGDOM_CENSUS_INTERVAL.get() != 0) {
            return;
        }
        for (Kingdom kingdom : KingdomSavedData.get(server).all()) {
            if (kingdom.status() == KingdomStatus.ACTIVE) {
                census(server, kingdom);
            }
        }
    }

    /**
     * Counts the population and finds the king, if the whole claim is loaded (otherwise keeps the last values: soldiers
     * in unloaded chunks can't be seen, so a partial count would be wrong).
     */
    public static void census(MinecraftServer server, Kingdom kingdom) {
        ServerLevel level = server.getLevel(kingdom.dimension());
        if (level == null) {
            return;
        }
        KingdomSavedData data = KingdomSavedData.get(server);
        Entity king = level.getEntity(kingdom.king().id());
        if (king instanceof AbstractClaySoldierEntity soldier && soldier.isAlive()) {
            kingdom.setKing(new Kingdom.King(kingdom.king().id(), kingdom.king().name(), soldier.blockPosition(), level.getGameTime()));
            data.setDirty();
        }
        BlockPos center = kingdom.center();
        int radius = kingdom.claimRadius();
        int minChunkX = SectionPos.blockToSectionCoord(center.getX() - radius);
        int maxChunkX = SectionPos.blockToSectionCoord(center.getX() + radius);
        int minChunkZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maxChunkZ = SectionPos.blockToSectionCoord(center.getZ() + radius);
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(x, z))) {
                    return; // part of the claim is unloaded: keep the last count
                }
            }
        }
        AABB box = new AABB(center.getX() - radius, level.getMinBuildHeight(), center.getZ() - radius,
                center.getX() + radius + 1, level.getMaxBuildHeight(), center.getZ() + radius + 1);
        int population = level.getEntitiesOfClass(AbstractClaySoldierEntity.class, box,
                soldier -> soldier.isAlive() && kingdom.team().equals(soldier.getClayTeamType())
                        && kingdom.claims(level.dimension(), soldier.blockPosition())).size();
        kingdom.setCensus(new Kingdom.Census(population, level.getGameTime()));
        data.setDirty();
    }

    /** The kingdom whose claim contains {@code pos}, if any. */
    public static Optional<Kingdom> at(MinecraftServer server, ResourceKey<Level> dimension, BlockPos pos) {
        return KingdomSavedData.get(server).all().stream().filter(kingdom -> kingdom.claims(dimension, pos)).findFirst();
    }

    /** The team's display name, e.g. "Red" (the id if the team doesn't exist any more). */
    public static String teamName(MinecraftServer server, ResourceLocation team) {
        try {
            return ClayMobTeamManger.getFromKeyOrError(team, server.registryAccess()).getDisplayName().getString();
        } catch (RuntimeException e) {
            return team.toString();
        }
    }

    /**
     * "King Arthur" for a named king, otherwise "its king" (or "The king of Red" at the start of a sentence).
     */
    public static String kingTitle(MinecraftServer server, Kingdom kingdom, boolean sentenceStart) {
        return kingdom.king().name().map(name -> "King " + name)
                .orElse(sentenceStart ? "The king of " + teamName(server, kingdom.team()) : "its king");
    }
}
