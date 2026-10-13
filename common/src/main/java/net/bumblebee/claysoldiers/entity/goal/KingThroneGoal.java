package net.bumblebee.claysoldiers.entity.goal;

import net.bumblebee.claysoldiers.entity.door.DoorPathing;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KingdomKeep;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Stage 4: the king of a kingdom whose keep is finished walks to his throne, the gold block in the keep
 * ({@code KeepThrone}), and stands on it. He steps down to fight when he is hurt (like {@link ClayMobSitGoal}) and goes
 * back afterwards. When he hasn't reached it after {@code kingdom.throne_walk_ticks} (vanilla paths are short, the throne
 * may be up a winding stair), he is carried there and it is recorded (KINGDOM).
 */
public class KingThroneGoal extends Goal {
    /** Ticks between looks for the throne while not on it. */
    private static final int CHECK_TICKS = 40;
    /** Ticks after being hurt before he goes back (as {@link ClayMobSitGoal}). */
    private static final int FIGHT_TICKS = 60;
    /** Ticks between new paths while walking. */
    private static final int REPATH_TICKS = 20;
    /** How far (squared, horizontally) from the middle of the throne's top he steps back to it while standing on it. */
    private static final double MIDDLE_SQR = 0.2 * 0.2;

    private final AbstractClaySoldierEntity king;
    @Nullable
    private BlockPos throne;
    private int nextCheck;
    private int walked;

    public KingThroneGoal(AbstractClaySoldierEntity king) {
        this.king = king;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
    }

    private boolean fighting() {
        return king.getLastHurtByMobTimestamp() + FIGHT_TICKS > king.tickCount && king.getLastHurtByMobTimestamp() > 0;
    }

    @Override
    public boolean canUse() {
        if (king.level().isClientSide() || king.tickCount < nextCheck || !Kingdoms.isKing(king)) {
            return false;
        }
        nextCheck = king.tickCount + CHECK_TICKS;
        if (fighting() || king.isPassenger() || king.isInWaterOrBubble()) {
            return false;
        }
        throne = KingdomKeep.throneFor(king).orElse(null);
        return throne != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (throne == null || fighting() || king.isPassenger() || !Kingdoms.isKing(king)) {
            return false;
        }
        if (king.tickCount >= nextCheck) {
            nextCheck = king.tickCount + CHECK_TICKS;
            BlockPos now = KingdomKeep.throneFor(king).orElse(null);
            if (!throne.equals(now)) {
                return false;
            }
        }
        return true;
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
        if (throne == null) {
            return;
        }
        // The middle of the throne's top (a gold block: a full cube; a player may have changed it, so ask its shape).
        double top = king.level().getBlockState(throne).getCollisionShape(king.level(), throne).max(Direction.Axis.Y);
        Vec3 spot = new Vec3(throne.getX() + 0.5, throne.getY() + (Double.isFinite(top) ? Math.max(top, 0) : 0), throne.getZ() + 0.5);
        double dx = king.getX() - spot.x;
        double dz = king.getZ() - spot.z;
        if (Math.abs(dx) < 0.5 && Math.abs(dz) < 0.5 && Math.abs(king.getY() - spot.y) < 0.5) {
            // On it (a path ends up to 0.675 from its last node's middle for a soldier this small): step to the middle.
            walked = 0;
            if (!king.getNavigation().isDone()) {
                king.getNavigation().stop();
            }
            if (dx * dx + dz * dz > MIDDLE_SQR) {
                king.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 0.5);
            }
            return;
        }
        int limit = Tuning.KINGDOM_THRONE_WALK_TICKS.get();
        if (limit > 0 && walked >= limit) {
            KingdomKeep.carried(king, throne);
            king.getNavigation().stop();
            king.moveTo(spot.x, spot.y, spot.z, king.getYRot(), 0);
            walked = 0;
            return;
        }
        if (walked++ % REPATH_TICKS == 0 || king.getNavigation().isDone()) {
            king.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0);
        }
    }
}
