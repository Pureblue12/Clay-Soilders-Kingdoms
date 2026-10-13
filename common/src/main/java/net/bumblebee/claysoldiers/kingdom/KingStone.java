package net.bumblebee.claysoldiers.kingdom;

import net.bumblebee.claysoldiers.datamap.SoldierEquipmentSlot;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.team.TeamLoyaltyManger;
import net.bumblebee.claysoldiers.team.TeamPlayerData;
import net.bumblebee.claysoldiers.terrain.SiteRules;
import net.bumblebee.claysoldiers.terrain.SiteSearch;
import net.bumblebee.claysoldiers.terrain.TerrainScan;
import net.bumblebee.claysoldiers.terrain.TerrainScanner;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Stage 4, the owner's way of founding a kingdom (D86): crown a soldier with a gold ingot (he is a king), then
 * sneak + right-click him with a gold block. He takes the block (he carries it in his off hand, so it is saved with him),
 * picks a spot himself, at random among the good keep sites near him ({@code kingdom.stone_pick_tolerance}), walks
 * there ({@code KingStoneGoal}) and sets the block down: that founds the kingdom around him. When the kingdom starts its
 * keep, the block is taken up again ({@link #takeUp}); it comes back as the throne when the keep is finished.
 * {@code /csr kingdom found} still founds at once where the king stands.
 */
public final class KingStone {
    /** What the king is handed, carries and sets down. */
    public static final Block STONE = Blocks.GOLD_BLOCK;
    /** Where he carries it: saved with him, shown in his hand. */
    public static final SoldierEquipmentSlot SLOT = SoldierEquipmentSlot.OFFHAND;

    private KingStone() {
    }

    /** The outcome of handing a king the gold block, with the line the player is told. */
    public record Given(boolean ok, String message) {
    }

    /**
     * {@code player} hands {@code king} one of the gold blocks in {@code held}. Refused when he isn't a king, his team
     * isn't loyal to the player or already has a kingdom, or he already carries one. Whatever he had in his off hand goes
     * to the player. One block is used up (none in creative).
     */
    public static Given give(ServerLevel level, AbstractClaySoldierEntity king, Player player, ItemStack held) {
        ResourceLocation team = king.getClayTeamType();
        String why = null;
        if (!held.is(STONE.asItem())) {
            why = "that isn't a gold block";
        } else if (!Kingdoms.SUPPORTED_DIMENSIONS.contains(level.dimension())) {
            why = "kingdoms can only be founded in the Overworld for now";
        } else if (!Kingdoms.isKing(king)) {
            why = "only a king takes the gold block (crown a soldier with a gold ingot first)";
        } else if (!Kingdoms.isLoyalTo(level, team, player)) {
            why = "the team " + Kingdoms.teamName(level.getServer(), team) + " isn't loyal to you";
        } else if (Kingdoms.byTeam(level.getServer(), team).isPresent()) {
            why = Kingdoms.teamName(level.getServer(), team) + " already has a kingdom";
        } else if (carries(king)) {
            why = "he already carries a gold block and is looking for his spot";
        }
        if (why != null) {
            String refusal = why;
            DebugLog.event(DebugCategory.KINGDOM, king, "gold block not taken from %s: %s", player.getName().getString(), refusal);
            return new Given(false, "The king didn't take the gold block: " + refusal + ".");
        }
        ItemStack old = king.getItemBySlot(SLOT).stack();
        if (!old.isEmpty() && !player.getInventory().add(old.copy())) {
            player.drop(old.copy(), false);
        }
        king.setItemSlot(SLOT, new ItemStack(STONE));
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        DebugLog.event(DebugCategory.KINGDOM, king, "took the gold block from %s for the kingdom of %s", player.getName().getString(), team);
        return new Given(true, kingName(king) + " takes the gold block and looks for a place for his kingdom.");
    }

    /** Whether {@code soldier} carries a gold block. */
    public static boolean carries(AbstractClaySoldierEntity soldier) {
        return soldier.getItemBySlot(SLOT).stack().is(STONE.asItem());
    }

    /** Whether {@code king} carries a gold block to found his kingdom: a living king whose team has none yet. */
    public static boolean founding(AbstractClaySoldierEntity king) {
        return king.isAlive() && carries(king) && Kingdoms.isKing(king) && king.level() instanceof ServerLevel level
                && Kingdoms.byTeam(level.getServer(), king.getClayTeamType()).isEmpty();
    }

    // Picking a spot

    /**
     * The spot {@code king} picks: at random among the usable keep sites near him (the terrain search around him) whose
     * score is at most {@code kingdom.stone_pick_tolerance} below the best, leaving out the columns in {@code tried}.
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
        int lowest = best - Tuning.KINGDOM_STONE_PICK_TOLERANCE.get();
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

    // Setting it down

    /**
     * {@code king} sets his gold block down beside him and founds the kingdom around himself. When founding is refused
     * (or there is no room beside him) nothing is placed and he keeps the block; the refusal is returned.
     */
    public static Kingdoms.Result setDown(ServerLevel level, AbstractClaySoldierEntity king) {
        Optional<BlockPos> spot = spotBeside(level, king);
        if (spot.isEmpty()) {
            DebugLog.event(DebugCategory.KINGDOM, king, "no room to set the gold block down at %s", king.blockPosition().toShortString());
            return Kingdoms.Result.refused("there was no room beside him to set the gold block down");
        }
        ServerPlayer founder = founder(level, king.getClayTeamType());
        Kingdoms.Result result = Kingdoms.found(level, king, founder);
        if (!result.ok()) {
            return result;
        }
        BlockPos pos = spot.get();
        level.setBlock(pos, STONE.defaultBlockState(), Block.UPDATE_ALL);
        king.setItemSlot(SLOT, ItemStack.EMPTY);
        Kingdoms.chronicle(level.getServer(), result.kingdom(), kingName(king) + " set down his gold block at " + pos.toShortString() + ".");
        DebugLog.event(DebugCategory.KINGDOM, king, "set the gold block down at %s", pos.toShortString());
        tell(level, king.getClayTeamType(), Component.literal(kingName(king) + " set down his gold block at " + pos.toShortString()
                + ": the kingdom of " + Kingdoms.teamName(level.getServer(), king.getClayTeamType()) + " is founded. See it with /csr kingdom book.")
                .withStyle(ChatFormatting.GOLD));
        return result;
    }

    /**
     * The cell beside {@code king} (north, east, south, west, at his feet) where the block can go: replaceable, with a
     * floor under it and nobody in it.
     */
    static Optional<BlockPos> spotBeside(ServerLevel level, AbstractClaySoldierEntity king) {
        BlockPos feet = king.blockPosition();
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos pos = feet.relative(direction);
            BlockState state = level.getBlockState(pos);
            BlockState below = level.getBlockState(pos.below());
            if (state.canBeReplaced() && state.getFluidState().isEmpty() && below.isFaceSturdy(level, pos.below(), Direction.UP)
                    && level.getEntitiesOfClass(Entity.class, new AABB(pos), Entity::isAlive).isEmpty()) {
                return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    /** {@code king} gives up: he drops the gold block at his feet, and the player his team is loyal to is told why. */
    public static void giveUp(ServerLevel level, AbstractClaySoldierEntity king, String why) {
        ItemStack stack = king.getItemBySlot(SLOT).stack().copy();
        king.setItemSlot(SLOT, ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            king.spawnAtLocation(stack);
        }
        DebugLog.event(DebugCategory.KINGDOM, king, "gave up founding and dropped the gold block: %s", why);
        tell(level, king.getClayTeamType(), Component.literal(kingName(king) + " dropped the gold block: " + why + ".")
                .withStyle(ChatFormatting.RED));
    }

    // Taken into the keep

    /**
     * Takes the king's gold block up again when the kingdom starts its keep (it comes back as the throne): a gold block
     * beside the kingdom's center, where the king stood when he set it down. Returns where it was, if there was one.
     */
    public static Optional<BlockPos> takeUp(ServerLevel level, Kingdom kingdom) {
        BlockPos center = kingdom.center();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 0, 1))) {
            if (!pos.equals(center) && level.isLoaded(pos) && level.getBlockState(pos).is(STONE)) {
                BlockPos stone = pos.immutable();
                level.setBlock(stone, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                Kingdoms.chronicle(level.getServer(), kingdom, "The king's gold block was taken into the keep, to be its throne.");
                DebugLog.event(DebugCategory.KINGDOM, null, () -> "took up the gold block of %s at %s for the throne"
                        .formatted(kingdom.team(), stone.toShortString()));
                return Optional.of(stone);
            }
        }
        return Optional.empty();
    }

    // Talking

    /** The player {@code team} is loyal to, if online. */
    @Nullable
    static ServerPlayer founder(ServerLevel level, ResourceLocation team) {
        TeamPlayerData.PlayerData owner = TeamLoyaltyManger.getTeamPlayerData(level).getPlayerForTeam(team);
        return owner == null ? null : level.getServer().getPlayerList().getPlayer(owner.getUUID());
    }

    private static void tell(ServerLevel level, ResourceLocation team, Component message) {
        ServerPlayer player = founder(level, team);
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    private static String kingName(AbstractClaySoldierEntity king) {
        return king.hasCustomName() ? king.getCustomName().getString() : "The king of " + Kingdoms.teamName(king.getServer(), king.getClayTeamType());
    }
}
