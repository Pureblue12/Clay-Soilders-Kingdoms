package net.bumblebee.claysoldiers.entity.goal;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.door.DoorPathing;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KingStone;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Stage 4 (D92): a king carrying a gold block for a kingdom his team doesn't have yet ({@link KingStone#founding}) picks
 * a spot himself, walks there and sets the block down, which founds the kingdom. A spot he can't reach within
 * {@code kingdom.stone_walk_ticks}, or where founding is refused, is left out and he picks another; after
 * {@code kingdom.stone_tries} of them he drops the block and gives up. Fighting interrupts him (as {@link KingThroneGoal}).
 */
public class KingStoneGoal extends Goal {
    /** Ticks between looks while he isn't walking to a spot. */
    private static final int CHECK_TICKS = 40;
    /** Ticks after being hurt before he goes on. */
    private static final int FIGHT_TICKS = 60;
    /** Ticks between new paths while walking. */
    private static final int REPATH_TICKS = 20;
    /** How close (horizontally, squared) to the middle of his spot counts as there. */
    private static final double THERE_SQR = 0.75 * 0.75;

    private final AbstractClaySoldierEntity king;
    private final Set<Long> tried = new HashSet<>();
    @Nullable
    private BlockPos spot;
    private int tries;
    private int nextCheck;
    private int walked;

    public KingStoneGoal(AbstractClaySoldierEntity king) {
        this.king = king;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    private boolean fighting() {
        return king.getLastHurtByMobTimestamp() + FIGHT_TICKS > king.tickCount && king.getLastHurtByMobTimestamp() > 0;
    }

    @Override
    public boolean canUse() {
        if (!(king.level() instanceof ServerLevel level) || king.tickCount < nextCheck || !Kingdoms.isKing(king) || !KingStone.carries(king)) {
            return false;
        }
        nextCheck = king.tickCount + CHECK_TICKS;
        if (!KingStone.founding(king) || fighting() || king.isPassenger() || king.isInWaterOrBubble()) {
            return false;
        }
        if (spot == null) {
            spot = KingStone.pick(level, king, tried, king.getRandom()).orElse(null);
            if (spot == null) {
                failed(level, "found no good ground for a keep near him");
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return spot != null && KingStone.founding(king) && !fighting() && !king.isPassenger();
    }

    @Override
    public void start() {
        walked = 0;
        if (king.getNavigation() instanceof DoorPathing.Ground ground) {
            ground.setRangeBonus(Tuning.BUILD_PATH_RANGE_BONUS.get());
        }
    }

    @Override
    public void stop() {
        king.getNavigation().stop();
        if (king.getNavigation() instanceof DoorPathing.Ground ground) {
            ground.setRangeBonus(0);
        }
        nextCheck = king.tickCount + CHECK_TICKS;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (spot == null || !(king.level() instanceof ServerLevel level)) {
            return;
        }
        double dx = king.getX() - (spot.getX() + 0.5);
        double dz = king.getZ() - (spot.getZ() + 0.5);
        if (dx * dx + dz * dz < THERE_SQR && king.onGround()) {
            king.getNavigation().stop();
            Kingdoms.Result result = KingStone.setDown(level, king);
            if (!result.ok()) {
                failed(level, "couldn't found the kingdom at " + spot.toShortString() + ": " + result.refusal());
            } else {
                spot = null;
            }
            return;
        }
        if (walked >= Tuning.KINGDOM_STONE_WALK_TICKS.get()) {
            failed(level, "couldn't get to " + spot.toShortString());
            return;
        }
        if (walked++ % REPATH_TICKS == 0 || king.getNavigation().isDone()) {
            king.getNavigation().moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.0);
        }
    }

    /** One spot didn't work out: leave it out and pick again, or give up after {@code kingdom.stone_tries}. */
    private void failed(ServerLevel level, String why) {
        if (spot != null) {
            tried.add(KingStone.column(spot.getX(), spot.getZ()));
        }
        spot = null;
        tries++;
        DebugLog.event(DebugCategory.KINGDOM, king, "spot %d for the kingdom didn't work out: %s", tries, why);
        if (tries >= Tuning.KINGDOM_STONE_TRIES.get()) {
            KingStone.giveUp(level, king, why);
            tries = 0;
            tried.clear();
        }
    }
}
