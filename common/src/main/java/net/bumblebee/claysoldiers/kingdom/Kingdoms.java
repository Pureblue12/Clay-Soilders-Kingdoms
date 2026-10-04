package net.bumblebee.claysoldiers.kingdom;

import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.minecraft.core.BlockPos;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Everything that changes kingdoms goes through here: it keeps the saved data dirty, writes the chronicle and records
 * KINGDOM events. Founding is explicit (a command, the book, or sneak + right-click your king with a gold block),
 * never automatic, and always reversible ({@link #disband}).
 */
public final class Kingdoms {
    /** Dimensions a kingdom can be founded in. */
    public static final Set<ResourceKey<Level>> SUPPORTED_DIMENSIONS = Set.of(Level.OVERWORLD);

    private Kingdoms() {
    }

    /**
     * The result of founding or disbanding: the kingdom, or why it was refused.
     */
    public record Result(@Nullable Kingdom kingdom, @Nullable String refusal) {
        public boolean ok() {
            return refusal == null;
        }

        static Result refused(String why) {
            return new Result(null, why);
        }
    }

    public static List<Kingdom> all(MinecraftServer server) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    public static Optional<Kingdom> byTeam(MinecraftServer server, ResourceLocation team) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    /** Whether the soldier is a king (wears a gold ingot: attack type KING). */
    public static boolean isKing(AbstractClaySoldierEntity soldier) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    /** The nearest living king of a team loyal to {@code player}, within {@code kingdom.found_search_radius}. */
    public static Optional<AbstractClaySoldierEntity> findNearestKing(ServerLevel level, Vec3 pos, Player player) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    /** Founds a kingdom around the nearest king loyal to {@code player} (see {@link #findNearestKing}). */
    public static Result foundNear(ServerPlayer player) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    /**
     * Founds a kingdom for {@code king}'s team around the king. Refused when: the dimension isn't supported, it isn't a
     * living king, {@code founder} isn't loyal to its team, the team already has a kingdom, or the claim overlaps
     * another kingdom's.
     */
    public static Result found(ServerLevel level, AbstractClaySoldierEntity king, @Nullable Player founder) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    /** Removes {@code team}'s kingdom. The world is not touched. */
    public static Result disband(MinecraftServer server, ResourceLocation team, @Nullable Player by) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    /** Called when a clay soldier dies: a kingdom whose king it was becomes RUINED. */
    public static void onSoldierDeath(AbstractClaySoldierEntity soldier, DamageSource source) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    public static void addResource(MinecraftServer server, Kingdom kingdom, ResourceLocation resource, int amount) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    public static Kingdom.Withdrawal removeResource(MinecraftServer server, Kingdom kingdom, ResourceLocation resource, int amount) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    /** Adds a chronicle line (game time now), keeping at most {@code kingdom.chronicle_max} lines. */
    public static void chronicle(MinecraftServer server, Kingdom kingdom, String text) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    /** Counts the population and finds the king, if the whole claim is loaded (otherwise keeps the last values). */
    public static void census(MinecraftServer server, Kingdom kingdom) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }

    /** The kingdom whose claim contains {@code pos}, if any. */
    public static Optional<Kingdom> at(MinecraftServer server, ResourceKey<Level> dimension, BlockPos pos) {
        throw new UnsupportedOperationException("Stage 1: not built yet");
    }
}
