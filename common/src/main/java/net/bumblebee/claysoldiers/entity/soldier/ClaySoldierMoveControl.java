package net.bumblebee.claysoldiers.entity.soldier;

import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.BooleanSupplier;

public class ClaySoldierMoveControl extends MoveControl {
    private final BooleanSupplier canSwim;
    private final int maxTurnX;
    private final int maxTurnY;
    private final float inWaterSpeedModifier;
    private final float outsideWaterSpeedModifier;
    private final boolean applyGravity;
    /** The cell the soldier last stepped out to from under a ceiling (only so the event is written once). */
    private BlockPos steppingOutTo;

    public ClaySoldierMoveControl(AbstractClaySoldierEntity mob, BooleanSupplier canSwim, int maxTurnX, int maxTurnY, boolean applyGravity) {
        super(mob);
        this.canSwim = canSwim;
        this.maxTurnX = maxTurnX;
        this.maxTurnY = maxTurnY;
        this.inWaterSpeedModifier = 4f;
        this.outsideWaterSpeedModifier = 1;
        this.applyGravity = applyGravity;
    }

    private boolean shouldUseSwimControl() {
        return mob.isInWater() && canSwim.getAsBoolean();
    }

    @Override
    public void tick() {
        if (shouldUseSwimControl()) {
            waterTick();
        } else if (this.operation == MoveControl.Operation.MOVE_TO && this.mob.onGround() && underACeiling()) {
            BlockPos out = this.wantedY - this.mob.getY() > this.mob.maxUpStep() ? spotToClimbFrom() : null;
            if (out != null) {
                stepOutTo(out);
            } else {
                moveWithoutHop();
            }
        } else if (this.operation == MoveControl.Operation.MOVE_TO && wouldHopNeedlessly()) {
            moveWithoutHop();
        } else {
            super.tick();
        }
    }

    /**
     * Vanilla's move control also hops whenever the soldier's feet are below the top of the block it stands in, such as
     * the low step of a stair, wherever the path goes next. A step that low is walked up without a hop (step height), so
     * the hop does nothing but throw the soldier 1.25 blocks up: town hall builders hopped on the stair at the top of a
     * planned 3-block drop and fell 3.75, hurt every time, until they died (structure build run 38165377544, fall
     * traces; D74). True when vanilla would hop for that reason only: the next path point is not above step height, and
     * the block's top is within step height of the feet. A hop the soldier needs (a higher next point, a taller block)
     * stays.
     */
    private boolean wouldHopNeedlessly() {
        double dx = this.wantedX - this.mob.getX();
        double dz = this.wantedZ - this.mob.getZ();
        double dy = this.wantedY - this.mob.getY();
        float step = this.mob.maxUpStep();
        if (dy > step && dx * dx + dz * dz < Math.max(1.0F, this.mob.getBbWidth())) {
            return false;
        }
        BlockPos feet = this.mob.blockPosition();
        BlockState state = this.mob.level().getBlockState(feet);
        VoxelShape shape = state.getCollisionShape(this.mob.level(), feet);
        if (shape.isEmpty() || state.is(BlockTags.DOORS) || state.is(BlockTags.FENCES)) {
            return false;
        }
        double top = shape.max(Direction.Axis.Y) + feet.getY();
        return this.mob.getY() < top && top - this.mob.getY() <= step;
    }

    /**
     * A tiny soldier fits under a top slab (a keep bench's seat), and its head then touches the slab. Vanilla hops it
     * toward a path point on top of that slab from right underneath it, so the hop hits the slab, the soldier walks back
     * under, and it goes back and forth for good (the owner's game 2026-10-17: keep builders stuck under the benches for
     * 400 ticks at a time, again and again). Under a ceiling a hop does nothing: the soldier walks without hopping, and
     * when it has to climb it first steps out to a free cell beside it with room to hop
     * ({@code soldierClimbsOutFromUnderASlab}).
     */
    private boolean underACeiling() {
        // A soldier inside a block (the king standing where his gold block throne was placed) hops out of it as before.
        return this.mob.level().noCollision(this.mob, this.mob.getBoundingBox())
                && !this.mob.level().noCollision(this.mob, this.mob.getBoundingBox().expandTowards(0, CEILING_GAP, 0));
    }

    /** How close above a soldier's head a block counts as a ceiling. */
    private static final double CEILING_GAP = 0.25;

    /**
     * The middle of the free cell beside the soldier's own with room above to hop up to the wanted height, nearest to the
     * soldier; null when there is none (vanilla's move then goes on).
     */
    private BlockPos spotToClimbFrom() {
        BlockPos feet = this.mob.blockPosition();
        double rise = this.wantedY - this.mob.getY() + 0.1;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos cell : new BlockPos[]{feet, feet.north(), feet.south(), feet.east(), feet.west()}) {
            double dx = cell.getX() + 0.5 - this.mob.getX();
            double dz = cell.getZ() + 0.5 - this.mob.getZ();
            var box = this.mob.getBoundingBox().move(dx, 0, dz);
            if (!this.mob.level().noCollision(this.mob, box) || !this.mob.level().noCollision(this.mob, box.expandTowards(0, rise, 0))) {
                continue;
            }
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = cell;
            }
        }
        return best;
    }

    private void stepOutTo(BlockPos cell) {
        if (!cell.equals(this.steppingOutTo)) {
            DebugLog.event(DebugCategory.PATHING, this.mob, "under a ceiling at %s, stepping out to %s to climb", this.mob.blockPosition().toShortString(),
                    cell.toShortString());
        }
        this.steppingOutTo = cell;
        this.operation = MoveControl.Operation.WAIT;
        double dx = cell.getX() + 0.5 - this.mob.getX();
        double dz = cell.getZ() + 0.5 - this.mob.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
        this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yaw, 90.0F));
        this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
    }

    /** Vanilla's walk toward the wanted position ({@link MoveControl#tick}, MOVE_TO), without the hop. */
    private void moveWithoutHop() {
        this.operation = MoveControl.Operation.WAIT;
        double dx = this.wantedX - this.mob.getX();
        double dz = this.wantedZ - this.mob.getZ();
        double dy = this.wantedY - this.mob.getY();
        if (dx * dx + dy * dy + dz * dz < 2.5000003E-7F) {
            this.mob.setZza(0.0F);
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0F / (float) Math.PI) - 90.0F;
        this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yaw, 90.0F));
        this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
    }

    private void waterTick() {
        if (this.applyGravity && this.mob.isInWater()) {
            this.mob.setDeltaMovement(this.mob.getDeltaMovement().add(0.0, 0.005, 0.0));
        }

        if (this.operation == MoveControl.Operation.MOVE_TO && !this.mob.getNavigation().isDone()) {
            double difWantedX = this.wantedX - this.mob.getX();
            double difWantedY = this.wantedY - this.mob.getY();
            double difWantedZ = this.wantedZ - this.mob.getZ();
            double d3 = difWantedX * difWantedX + difWantedY * difWantedY + difWantedZ * difWantedZ;
            if (d3 < 2.5000003E-7F) {
                this.mob.setZza(0.0F);
            } else {
                float f = (float) (Mth.atan2(difWantedZ, difWantedX) * 180.0F / (float) Math.PI) - 90.0F;
                this.mob.setYRot(this.rotlerp(this.mob.getYRot(), f, (float) this.maxTurnY));
                this.mob.yBodyRot = this.mob.getYRot();
                this.mob.yHeadRot = this.mob.getYRot();
                float mobSpeed = (float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED));
                if (this.mob.isInWater()) {
                    this.mob.setSpeed(mobSpeed * this.inWaterSpeedModifier);
                    double flatDistance = Math.sqrt(difWantedX * difWantedX + difWantedZ * difWantedZ);
                    if (Math.abs(difWantedY) > 1.0E-5F || Math.abs(flatDistance) > 1.0E-5F) {
                        float f3 = -((float) (Mth.atan2(difWantedY, flatDistance) * 180.0F / (float) Math.PI));
                        f3 = Mth.clamp(Mth.wrapDegrees(f3), (float) (-this.maxTurnX), (float) this.maxTurnX);
                        this.mob.setXRot(this.rotlerp(this.mob.getXRot(), f3, 5.0F));
                    }

                    float f6 = Mth.cos(this.mob.getXRot() * (float) (Math.PI / 180.0));
                    float f4 = Mth.sin(this.mob.getXRot() * (float) (Math.PI / 180.0));
                    this.mob.zza = f6 * mobSpeed;
                    this.mob.yya = -f4 * mobSpeed;
                } else {
                    float f5 = Math.abs(Mth.wrapDegrees(this.mob.getYRot() - f));
                    float f2 = getTurningSpeedFactor(f5);
                    this.mob.setSpeed(mobSpeed * this.outsideWaterSpeedModifier * f2);
                }
            }
        } else {
            this.mob.setSpeed(0.0F);
            this.mob.setXxa(0.0F);
            this.mob.setYya(0.0F);
            this.mob.setZza(0.0F);
        }
    }


    private static float getTurningSpeedFactor(float p_249853_) {
        return 1.0F - Mth.clamp((p_249853_ - 10.0F) / 50.0F, 0.0F, 1.0F);
    }
}
