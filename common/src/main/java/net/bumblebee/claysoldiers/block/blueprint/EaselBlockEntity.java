package net.bumblebee.claysoldiers.block.blueprint;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.PlannedReach;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.BlueprintManger;
import net.bumblebee.claysoldiers.blueprint.BlueprintRequest;
import net.bumblebee.claysoldiers.scaffold.ScaffoldRequest;
import net.bumblebee.claysoldiers.scaffold.Scaffolds;
import net.bumblebee.claysoldiers.blueprint.BlueprintTemplateSettings;
import net.bumblebee.claysoldiers.blueprint.templates.BlueprintPlan;
import net.bumblebee.claysoldiers.blueprint.templates.ClientBlueprintPlan;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.capability.BlueprintRequestHandler;
import net.bumblebee.claysoldiers.init.ModBlockEntities;
import net.bumblebee.claysoldiers.networking.BlueprintPlacePayload;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class EaselBlockEntity extends BlockEntity {
    /** How far sideways a placed block (or scaffold) can make blocks reachable: builder reach (2) plus a step. */
    private static final int REACH_CHANGE_RADIUS = 3;
    private static final String INDICATE_TAG_ON_CLIENT = "Client";
    private static final String MIRROR_TAG = "mirror";
    private static final String TEMPLATE_TAG = "template";
    @Nullable
    private BlueprintData data;
    @Nullable
    private BlueprintPlan template;
    private final BlueprintRequestHandler blueprintRequestHandler = new BlueprintRequestHandler() {
        @Override
        public @Nullable BlueprintRequest getRequest(Predicate<BlockPos> canReach) {
            return getBlueprintRequest(canReach);
        }

        @Override
        public @Nullable BlueprintRequest getFollowUpRequest(Predicate<BlockPos> canReach, Item item, BlockPos placed) {
            if (!(template instanceof ServerBlueprintPlan plan) || plan.isFinished() || !(level instanceof ServerLevel serverLevel)) {
                return null;
            }
            var settings = getTemplateSettings();
            return plan.getFollowUpRequest(serverLevel, getTemplateBase(settings), settings, canReach, item, placed);
        }

        @Override
        public boolean doRequest(@Nullable BlueprintRequest request) {
            return doBlueprintRequest(request);
        }
    };

    private Mirror mirror = Mirror.NONE;

    public EaselBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.EASEL_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    public List<String> getInfoState() {
        List<String> info = new ArrayList<>(3);
        info.add("Data: " + data);
        info.add("Template: " + template);
        info.add("Settings: " + getTemplateSettings());
        if (level instanceof ServerLevel serverLevel) {
            info.add("Blueprint Cap: " + ClaySoldiersCommon.CAPABILITY_MANGER.createPoiCache(serverLevel, worldPosition));
        }
        return info;
    }

    public void setBlueprintData(@NotNull BlueprintData data) {
        cleanUpScaffold("the blueprint was replaced");
        this.data = data;
        if (!level.isClientSide()) {
            this.template = data.createServerPlan().orElseThrow(IllegalArgumentException::new);
        } else {
            this.template = data.createClientPlan().orElseThrow(IllegalArgumentException::new);
        }
        setChanged();
    }

    public void clearBlueprintData() {
        cleanUpScaffold("the blueprint was taken off");
        data = null;
        template = null;
        setChanged();
    }

    /** An interrupted build: its scaffold is removed at once. */
    private void cleanUpScaffold(String why) {
        if (level instanceof ServerLevel serverLevel) {
            Scaffolds.cleanup(serverLevel, worldPosition, why);
        }
    }

    public ItemStack getBlueprintItem() {
        return BlueprintManger.createBlueprintItem(data, level.registryAccess());
    }


    public boolean hasBlueprintData() {
        return data != null;
    }

    @Nullable
    public BlueprintData getBlueprintData() {
        return data;
    }

    public List<ItemStack> getRequiredItems() {
        return template == null ? List.of() : template.getNeededItems();
    }

    @Override
    protected void saveAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.saveAdditional(pTag, pRegistries);
        if (data != null) {
            data.save(pTag, pRegistries);
        }
        if (pTag.contains(INDICATE_TAG_ON_CLIENT)) {
            if (template != null) {
                template.saveItems(pTag);
                template.saveSize(pTag);
                template.saveHasStarted(pTag);
            } else {
                pTag.remove(INDICATE_TAG_ON_CLIENT);
            }
        } else if (template instanceof ServerBlueprintPlan serverTemplate) {
            CompoundTag templateTag = new CompoundTag();
            pTag.put(TEMPLATE_TAG, serverTemplate.save(templateTag));
        }
        saveMirror(pTag);
    }


    @Override
    protected void loadAdditional(CompoundTag pTag, HolderLookup.Provider pRegistries) {
        super.loadAdditional(pTag, pRegistries);
        data = BlueprintData.load(pTag, pRegistries);
        if (pTag.contains(INDICATE_TAG_ON_CLIENT)) {
            template = new ClientBlueprintPlan(pTag);
        } else {
            if (pTag.contains(TEMPLATE_TAG)) {
                template = ServerBlueprintPlan.load(pTag.getCompound(TEMPLATE_TAG), pRegistries);
            }
        }
        loadMirror(pTag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider pRegistries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(INDICATE_TAG_ON_CLIENT, true);
        saveAdditional(tag, pRegistries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void saveMirror(CompoundTag tag) {
        tag.putString(MIRROR_TAG, this.mirror.toString());
    }

    private void loadMirror(CompoundTag tag) {
        try {
            this.mirror = Mirror.valueOf(tag.getString(MIRROR_TAG));
        } catch (IllegalArgumentException ignored) {
            this.mirror = Mirror.NONE;
        }
    }

    public boolean cycleMirror() {
        if (hasStarted()) {
            return false;
        }
        this.mirror = mirror == Mirror.NONE ? Mirror.FRONT_BACK : Mirror.NONE;
        return true;
    }

    public Mirror getMirror() {
        return mirror;
    }

    public boolean hasStarted() {
        return template != null && template.hasStarted();
    }

    public boolean isFinished() {
        return template != null && template.isFinished();
    }

    @Nullable
    public BlueprintTemplateSettings getTemplateSettings() {
        if (template == null) {
            return null;
        }
        return new BlueprintTemplateSettings(template.getSize(), mirror, rotationFor(getFacing()));
    }

    public Direction getFacing() {
        return getBlockState().getValue(EaselBlock.FACING);
    }

    /**
     * Returns the rotation of a blueprint on an easel with the given facing.
     */
    public static Rotation rotationFor(Direction direction) {
        return switch (direction) {
            case NORTH -> Rotation.NONE;
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalStateException("Easel Block Entity should never have a BlockState Facing of:" + direction);
        };
    }

    private @Nullable BlueprintRequest getBlueprintRequest(Predicate<BlockPos> canReachDestination) {
        if (!(template instanceof ServerBlueprintPlan plan) || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        if (plan.isFinished()) {
            // Scaffolding (if any) comes down after the structure is finished.
            return Scaffolds.removeRequest(serverLevel, worldPosition, canReachDestination);
        }
        var settings = getTemplateSettings();
        if (Tuning.SCAFFOLD_COLUMNS_FIRST.get() == 1 && Scaffolds.isGoingUp(serverLevel, worldPosition)) {
            // Scaffolding has started going up: put it all up first, so the work above reach doesn't stall at every level.
            var scaffold = Scaffolds.placeRequest(serverLevel, worldPosition, plan, getTemplateBase(settings), settings, canReachDestination);
            if (scaffold != null) {
                return scaffold;
            }
            if (Scaffolds.isScaffoldBeingCarried(serverLevel, worldPosition)) {
                return null; // the last scaffold blocks are on their way: wait for them, then work above reach
            }
        }
        var request = plan.getRequest(serverLevel, getTemplateBase(settings), settings, canReachDestination);
        if (request != null && !(canReachDestination instanceof PlannedReach planned && !request.standingSpots().isEmpty()
                ? planned.canReach(request.getPos(), request.standingSpots()) : canReachDestination.test(request.getPos()))) {
            request.cancel();
            return null;
        }
        if (request == null) {
            // Nothing reachable: scaffolding, if the rest is out of reach (never for structures within reach).
            return Scaffolds.placeRequest(serverLevel, worldPosition, plan, getTemplateBase(settings), settings, canReachDestination);
        }
        return request;
    }

    /** The server's plan of the blueprint on this easel (what is left to place), or null. */
    @Nullable
    public BlueprintPlan getPlan() {
        return template;
    }

    /** Whether this easel has a plan to build (the blueprint may have been taken off). */
    public boolean hasPlan() {
        return template != null;
    }

    private BlockPos getTemplateBase(BlueprintTemplateSettings settings) {
        return templateBase(worldPosition, getFacing(), settings);
    }

    /**
     * Returns the base position of the blueprint template on an easel: 2 blocks behind the easel
     * (opposite its facing), centered on it.
     *
     * @param easelPos the easel's position
     * @param facing   the easel's {@link EaselBlock#FACING}
     * @param settings the template settings, see {@link #getTemplateSettings()}
     */
    public static BlockPos templateBase(BlockPos easelPos, Direction facing, BlueprintTemplateSettings settings) {
        return easelPos.offset(
                BlockPos.ZERO.relative(facing.getOpposite(), 2).relative(facing.getClockWise(), settings.getDistanceToCenter())
        );
    }

    /**
     * A builder's request: places the block exactly at the request's spot, or nothing (see
     * {@link ServerBlueprintPlan#tryPlacingRequest}). Players feeding the easel by hand use {@link #tryPlacingSoldier}.
     */
    private boolean doBlueprintRequest(BlueprintRequest request) {
        if (request instanceof ScaffoldRequest scaffold) {
            boolean done = level instanceof ServerLevel serverLevel && Scaffolds.complete(serverLevel, worldPosition, scaffold);
            if (done && template instanceof ServerBlueprintPlan serverPlan) {
                var settings = getTemplateSettings(); // new scaffold: blocks near it may be reachable now
                serverPlan.forgetUnreachableNear(scaffold.getPos(), REACH_CHANGE_RADIUS, getTemplateBase(settings), settings);
            }
            return done;
        }
        if (request == null || !(template instanceof ServerBlueprintPlan serverPlan)) {
            return false;
        }
        ItemStack item = request.getItem().getDefaultInstance();
        var settings = getTemplateSettings();
        var result = serverPlan.tryPlacingRequest(level, request, item, getTemplateBase(settings), settings);
        if (result.isSuccess()) {
            // A new block can be a floor to stand on: blocks near it may be reachable now.
            serverPlan.forgetUnreachableNear(request.getPos(), REACH_CHANGE_RADIUS, getTemplateBase(settings), settings);
            if (level instanceof ServerLevel serverLevel) {
                Scaffolds.structurePlaced(serverLevel, worldPosition);
            }
            onPlaced(item);
        }
        return result.isSuccess();
    }

    public BlueprintPlan.PlaceResult tryPlacingSoldier(ItemStack item) {
        if (template == null) {
            return BlueprintPlan.PlaceResult.NOT_NEEDED;
        }
        var settings = getTemplateSettings();
        var result = template.tryPlacing(level, item,
                getTemplateBase(settings),
                settings
        );

        if (result.isSuccess()) {
            onPlaced(item);
        }
        return result;
    }

    private void onPlaced(ItemStack item) {
        if (!level.isClientSide()) {
            ClaySoldiersCommon.NETWORK_MANGER.sendToPlayersTrackingBlockEntity(this, new BlueprintPlacePayload(worldPosition, item.getItem()));
        }
        setChanged();
    }

    public BlueprintRequestHandler getBlueprintRequestHandler() {
        return blueprintRequestHandler;
    }

    @Override
    public String toString() {
        String levelName;
        if (level != null) {
            levelName = level.isClientSide() ? "Client" : "Server";
        } else {
            levelName = "Null";
        }
        return "EaselBlockEntity(%s, %s)".formatted(levelName, getBlockPos());
    }
}
