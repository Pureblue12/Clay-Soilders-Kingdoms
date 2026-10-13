package net.bumblebee.claysoldiers.entity.goal;

import net.bumblebee.claysoldiers.entity.door.DoorPathing;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KingdomKeep;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Stage 4: the king of a kingdom whose keep is finished walks to his throne ({@code KeepThrone}) and sits on it. He
 * stands up to fight when he is hurt (like {@link ClayMobSitGoal}) and goes back afterwards. When he hasn't reached it
 * after {@code kingdom.throne_walk_ticks} (vanilla paths are short, the seat may be up a winding stair), he is carried
 * there and it is recorded (KINGDOM). Sitting on a stair, he sits on its low half and faces away from its back.
 */
public class KingThroneGoal extends Goal {
    /** Ticks between looks for the throne while not on it. */
    private static final int CHECK_TICKS = 40;
    /** Ticks after being hurt before he goes back (as {@link ClayMobSitGoal}). */
    private static final int FIGHT_TICKS = 60;
    /** Ticks between new paths while walking. */
    private static final int REPATH_TICKS = 20;
    /** How close (squared, horizontally) the king must be to his seat to sit down. */
    private static final double ARRIVED_SQR = 0.8 * 0.8;

    private final AbstractClaySoldierEntity king;
    @Nullable
    private BlockPos throne;
    private int nextCheck;
    private int walked;
    private boolean seated;

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
        seated = false;
        if (king.getNavigation() instanceof DoorPathing.Ground ground) {
            ground.setRangeBonus(Tuning.BUILD_PATH_RANGE_BONUS.get());
        }
    }

    @Override
    public void stop() {
        if (seated) {
            king.setInSittingPose(false);
            king.setPose(Pose.STANDING);
        }
        seated = false;
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
        Vec3 seat = seat(throne);
        double dx = king.getX() - seat.x;
        double dz = king.getZ() - seat.z;
        boolean arrived = dx * dx + dz * dz <= ARRIVED_SQR && Math.abs(king.getY() - seat.y) < 1.0;
        int limit = Tuning.KINGDOM_THRONE_WALK_TICKS.get();
        if (!arrived && limit > 0 && walked >= limit) {
            KingdomKeep.carried(king, throne);
            arrived = true;
        }
        if (arrived) {
            if (!seated || king.distanceToSqr(seat) > 0.01) {
                king.getNavigation().stop();
                float yaw = facing(throne).toYRot();
                king.moveTo(seat.x, seat.y, seat.z, yaw, 0);
                king.setYHeadRot(yaw);
                king.setYBodyRot(yaw);
                king.setInSittingPose(true);
                king.setPose(Pose.SITTING);
                seated = true;
            }
            return;
        }
        if (seated) {
            king.setInSittingPose(false);
            king.setPose(Pose.STANDING);
            seated = false;
        }
        if (walked++ % REPATH_TICKS == 0 || king.getNavigation().isDone()) {
            king.getNavigation().moveTo(seat.x, throne.getY(), seat.z, 1.0);
        }
    }

    /** Where he sits: on a stair's low half, else the middle of the block (its top when it has a full shape). */
    private Vec3 seat(BlockPos pos) {
        BlockState state = king.level().getBlockState(pos);
        if (state.getBlock() instanceof StairBlock) {
            Direction back = state.getValue(StairBlock.FACING);
            return new Vec3(pos.getX() + 0.5 - back.getStepX() * 0.25, pos.getY() + 0.5, pos.getZ() + 0.5 - back.getStepZ() * 0.25);
        }
        double top = state.getCollisionShape(king.level(), pos).isEmpty() ? 0 : state.getCollisionShape(king.level(), pos).max(Direction.Axis.Y);
        return new Vec3(pos.getX() + 0.5, pos.getY() + top, pos.getZ() + 0.5);
    }

    /** The way he faces: away from a stair's back, else the way he faces now. */
    private Direction facing(BlockPos pos) {
        BlockState state = king.level().getBlockState(pos);
        if (state.getBlock() instanceof StairBlock) {
            return state.getValue(StairBlock.FACING).getOpposite();
        }
        return king.getDirection();
    }
}
