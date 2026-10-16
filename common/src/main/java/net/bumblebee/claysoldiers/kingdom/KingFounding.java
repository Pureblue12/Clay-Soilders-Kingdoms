package net.bumblebee.claysoldiers.kingdom;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.terrain.SiteRules;
import net.bumblebee.claysoldiers.terrain.SiteSearch;
import net.bumblebee.claysoldiers.terrain.TerrainScan;
import net.bumblebee.claysoldiers.terrain.TerrainScanner;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Stage 4, how a kingdom is founded in game (D93): a soldier who picks up a gold ingot is a king, and a king whose team
 * has no kingdom founds one by himself. He picks a spot, at random among the good keep sites near him
 * ({@code kingdom.found_pick_tolerance}), walks there ({@code KingFoundingGoal}) and founds the kingdom around himself.
 * No player has to claim the team or do anything else; the players nearby are told. {@code /csr kingdom found} still
 * founds at once where the king stands.
 */
public final class KingFounding {
    /** Kings in a GameTest server found only while a test turns this on (other tests' kings must stay put). */
    private static boolean inTests;

    private KingFounding() {
    }

    /** For GameTests only: lets kings found by themselves in the test server (reset it afterwards). */
    public static void enableInTestsForTests(boolean enabled) {
        inTests = enabled;
    }

    /**
     * Whether {@code king} should go and found his kingdom: a living king in the Overworld whose team has none yet, or only
     * a RUINED one (D95).
     */
    public static boolean founding(AbstractClaySoldierEntity king) {
        return king.isAlive() && Kingdoms.isKing(king) && king.level() instanceof ServerLevel level
                && (inTests || !(level.getServer() instanceof GameTestServer))
                && Kingdoms.SUPPORTED_DIMENSIONS.contains(level.dimension())
                && Kingdoms.canFound(level.getServer(), king.getClayTeamType());
    }

    // Picking a spot

    /**
     * The spot {@code king} picks: at random among the usable keep sites near him (the terrain search around him) whose
     * score is at most {@code kingdom.found_pick_tolerance} below the best, leaving out the columns in {@code tried}.
     * The spot is the cell above the ground in the site's middle.
     */
    public static Optional<BlockPos> pick(ServerLevel level, AbstractClaySoldierEntity king, Set<Long> tried, RandomSource random) {
        SiteRules rules = SiteRules.current();
        BlockPos at = king.blockPosition();
        TerrainScan scan = TerrainScanner.scan(level, at, rules.searchScanRadius());
        SiteSearch.Result result = SiteSearch.search(scan, at.getX(), at.getZ(), rules);
        List<SiteSearch.Candidate> usable = result.candidates().stream()
                .filter(candidate -> !candidate.score().unknown() && candidate.score().ok())
                .filter(candidate -> !tried.contains(column(candidate.x(), candidate.z())))
                .toList();
        if (usable.isEmpty()) {
            DebugLog.event(DebugCategory.KINGDOM, king, "no spot for the kingdom near %s: %s", at.toShortString(), result.summary());
            return Optional.empty();
        }
        int best = usable.stream().mapToInt(candidate -> candidate.score().score()).max().orElse(0);
        int lowest = best - Tuning.KINGDOM_FOUND_PICK_TOLERANCE.get();
        List<SiteSearch.Candidate> liked = usable.stream().filter(candidate -> candidate.score().score() >= lowest)
                .sorted(Comparator.comparingInt(SiteSearch.Candidate::distance)).toList();
        SiteSearch.Candidate pick = liked.get(random.nextInt(liked.size()));
        BlockPos spot = new BlockPos(pick.x(), scan.ground(pick.x(), pick.z()) + 1, pick.z());
        DebugLog.event(DebugCategory.KINGDOM, king, () -> "picked %s for the kingdom (score %d, %d blocks away; %d of %d usable sites liked)"
                .formatted(spot.toShortString(), pick.score().score(), pick.distance(), liked.size(), usable.size()));
        return Optional.of(spot);
    }

    /** A column as a number, for the set of tried spots. */
    public static long column(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }

    // Founding

    /** {@code king} founds the kingdom around himself where he stands; the players nearby are told. */
    public static Kingdoms.Result found(ServerLevel level, AbstractClaySoldierEntity king) {
        Kingdoms.Result result = Kingdoms.found(level, king, null);
        if (result.ok()) {
            BlockPos at = king.blockPosition();
            DebugLog.event(DebugCategory.KINGDOM, king, "founded the kingdom by himself at %s", at.toShortString());
            tell(level, king, Component.literal(kingName(king) + " founded the kingdom of " + Kingdoms.teamName(level.getServer(), king.getClayTeamType())
                    + " at " + at.getX() + ", " + at.getZ() + ". See it with /csr kingdom book.").withStyle(ChatFormatting.GOLD));
        }
        return result;
    }

    /** {@code king} sets off for {@code spot}; the players nearby are told, so they know he's on his way. */
    public static void setOff(ServerLevel level, AbstractClaySoldierEntity king, BlockPos spot) {
        int distance = (int) Math.round(Math.sqrt(king.blockPosition().distSqr(spot)));
        tell(level, king, Component.literal(kingName(king) + " walks to " + spot.getX() + ", " + spot.getZ() + " (" + distance
                + " blocks) to found his kingdom.").withStyle(ChatFormatting.YELLOW));
    }

    /** {@code king} rests: the spots he tried didn't work out. The players nearby are told why. */
    public static void rest(ServerLevel level, AbstractClaySoldierEntity king, String why) {
        DebugLog.event(DebugCategory.KINGDOM, king, "rests before looking for a kingdom spot again: %s", why);
        tell(level, king, Component.literal(kingName(king) + " found no place for his kingdom yet (" + why + "); he'll look again soon.")
                .withStyle(ChatFormatting.RED));
    }

    // Talking

    /** Tells the players within the kingdom claim radius of {@code king} (twice over: they watch from a distance). */
    private static void tell(ServerLevel level, AbstractClaySoldierEntity king, Component message) {
        double range = 2.0 * Tuning.KINGDOM_CLAIM_RADIUS.get();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(king) <= range * range) {
                player.sendSystemMessage(message);
            }
        }
    }

    private static String kingName(AbstractClaySoldierEntity king) {
        return king.hasCustomName() ? king.getCustomName().getString() : "The king of " + Kingdoms.teamName(king.getServer(), king.getClayTeamType());
    }
}
