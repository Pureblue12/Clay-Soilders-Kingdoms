package net.bumblebee.claysoldiers.blueprint.site;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlock;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.BlueprintTemplateSettings;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.init.ModBlocks;
import net.bumblebee.claysoldiers.init.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Checks whether a blueprint can be built at a place and starts the build by placing an easel with it.
 * Used by the {@code /csr build} command; meant to be reused by soldier AI (M3).
 * <p>
 * The building goes in {@code buildDirection} from the easel, exactly like an easel placed by a player
 * looking that way: the easel faces back (towards the player) and the blueprint starts 2 blocks behind it.
 * Positions come from {@link EaselBlockEntity#templateBase} and {@link ServerBlueprintPlan#targetPositions},
 * the same code the easel uses while building.
 */
public final class BuildSitePlanner {
    /**
     * Minimum distance to another easel: builders use the nearest easel within 8 blocks.
     */
    public static final int EASEL_SPACING = 8;
    /**
     * How far above and below the easel other easels are searched (builders search a few blocks vertically).
     */
    private static final int EASEL_SEARCH_HEIGHT = 3;

    private BuildSitePlanner() {
    }

    /**
     * Checks a site for the registered blueprint with the given id.
     */
    public static SiteCheck check(ServerLevel level, ResourceLocation blueprintId, BlockPos easelPos, Direction buildDirection) {
        BlueprintData blueprint = level.registryAccess().registryOrThrow(ModRegistries.BLUEPRINTS).get(blueprintId);
        if (blueprint == null) {
            return SiteCheck.refused(SiteCheck.Reason.UNKNOWN_BLUEPRINT, "Unknown blueprint " + blueprintId, null);
        }
        return check(level, blueprint, blueprintId, easelPos, buildDirection);
    }

    /**
     * Checks whether the blueprint can be built with its easel at {@code easelPos}, building towards {@code buildDirection}.
     * Rules are checked cheapest first; the first one that fails is returned.
     *
     * @param blueprintId the registry id, only used in messages and the result; may be {@code null}
     */
    public static SiteCheck check(ServerLevel level, BlueprintData blueprint, @Nullable ResourceLocation blueprintId,
                                  BlockPos easelPos, Direction buildDirection) {
        return check(level, blueprint, blueprintId, easelPos, buildDirection, false);
    }

    /**
     * Like {@link #check(ServerLevel, BlueprintData, ResourceLocation, BlockPos, Direction)}; with {@code overWater} a
     * column with water where the ground would be counts as ground (a fishing dock's jetty stands over a lake).
     */
    public static SiteCheck check(ServerLevel level, BlueprintData blueprint, @Nullable ResourceLocation blueprintId,
                                  BlockPos easelPos, Direction buildDirection, boolean overWater) {
        if (!buildDirection.getAxis().isHorizontal()) {
            throw new IllegalArgumentException("Build direction must be horizontal, got " + buildDirection);
        }
        String name = blueprintId != null ? blueprintId.toString() : blueprint.name();
        Optional<ServerBlueprintPlan> plan = blueprint.createServerPlan();
        if (!blueprint.isValid() || plan.isEmpty()) {
            return SiteCheck.refused(SiteCheck.Reason.UNKNOWN_BLUEPRINT, "Blueprint " + name + " is not loaded", null);
        }
        if (!ClaySoldiersCommon.COMMON_HOOKS.isBlueprintEnabled(level.enabledFeatures())) {
            return SiteCheck.refused(SiteCheck.Reason.BLUEPRINTS_DISABLED, "The blueprint experiment is off in this world", null);
        }

        Direction easelFacing = buildDirection.getOpposite();
        BlueprintTemplateSettings settings = new BlueprintTemplateSettings(plan.get().getSize(), Mirror.NONE,
                EaselBlockEntity.rotationFor(easelFacing));
        BlockPos base = EaselBlockEntity.templateBase(easelPos, easelFacing, settings);
        List<BlockPos> targets = plan.get().targetPositions(base, settings);
        Optional<BoundingBox> maybeBounds = BoundingBox.encapsulatingPositions(targets);
        if (maybeBounds.isEmpty()) {
            return SiteCheck.refused(SiteCheck.Reason.UNKNOWN_BLUEPRINT, "Blueprint " + name + " has no blocks", null);
        }
        BoundingBox bounds = maybeBounds.get();
        int groundY = easelPos.getY() - 1;

        if (level.isOutsideBuildHeight(groundY) || level.isOutsideBuildHeight(bounds.maxY()) || level.isOutsideBuildHeight(easelPos)) {
            return SiteCheck.refused(SiteCheck.Reason.OUTSIDE_BUILD_HEIGHT, "The site is outside the world's build height", easelPos);
        }

        BoundingBox searchArea = easelSearchArea(easelPos, bounds);
        if (!level.hasChunksAt(searchArea.minX(), searchArea.minZ(), searchArea.maxX(), searchArea.maxZ())) {
            return SiteCheck.refused(SiteCheck.Reason.NOT_LOADED, "Part of the site is not loaded", easelPos);
        }

        if (!ServerBlueprintPlan.canPlaceAt(level, easelPos)) {
            return SiteCheck.refused(SiteCheck.Reason.EASEL_SPOT_TAKEN,
                    "The easel spot %s is taken by %s".formatted(easelPos.toShortString(), blockName(level.getBlockState(easelPos))), easelPos);
        }
        BlockPos belowEasel = easelPos.below();
        if (!isSolidGround(level, belowEasel)) {
            return SiteCheck.refused(SiteCheck.Reason.NO_EASEL_SUPPORT,
                    "Nothing solid under the easel at " + belowEasel.toShortString(), belowEasel);
        }

        List<BlockPos> blocked = targets.stream().filter(pos -> !ServerBlueprintPlan.canPlaceAt(level, pos)).toList();
        if (!blocked.isEmpty()) {
            BlockPos first = blocked.getFirst();
            return SiteCheck.refused(SiteCheck.Reason.OBSTRUCTED, "%d block(s) in the way, first: %s at %s"
                    .formatted(blocked.size(), blockName(level.getBlockState(first)), first.toShortString()), first);
        }

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                BlockPos ground = new BlockPos(x, groundY, z);
                if (!isSolidGround(level, ground) && !(overWater && level.getFluidState(ground).is(FluidTags.WATER))) {
                    return SiteCheck.refused(SiteCheck.Reason.NO_GROUND, "No solid ground under the building at " + ground.toShortString(), ground);
                }
            }
        }

        for (BlockPos pos : BlockPos.betweenClosed(searchArea.minX(), searchArea.minY(), searchArea.minZ(),
                searchArea.maxX(), searchArea.maxY(), searchArea.maxZ())) {
            if (level.getBlockState(pos).is(ModBlocks.EASEL_BLOCK.get())) {
                return SiteCheck.refused(SiteCheck.Reason.EASEL_NEARBY, "Another easel at %s is too close (within %d blocks)"
                        .formatted(pos.toShortString(), EASEL_SPACING), pos.immutable());
            }
        }

        return SiteCheck.valid(new BuildSite(blueprintId, blueprint, easelPos.immutable(), easelFacing, base, bounds, targets));
    }

    /**
     * Checks the site again and, if it is still valid, places the easel with the blueprint.
     *
     * @return the check; when valid, the easel has been placed at {@code site.easelPos()}
     */
    public static SiteCheck place(ServerLevel level, BuildSite site) {
        SiteCheck check = check(level, site.blueprint(), site.blueprintId(), site.easelPos(), site.buildDirection());
        if (check.isValid()) {
            placeEasel(level, site.easelPos(), site.easelFacing(), site.blueprint());
        }
        return check;
    }

    /**
     * Checks the site and places the easel if it is valid.
     */
    public static SiteCheck checkAndPlace(ServerLevel level, ResourceLocation blueprintId, BlockPos easelPos, Direction buildDirection) {
        SiteCheck check = check(level, blueprintId, easelPos, buildDirection);
        return check.isValid() ? place(level, check.site()) : check;
    }

    /**
     * Places an easel with the blueprint, WITHOUT checking the site.
     */
    public static EaselBlockEntity placeEasel(ServerLevel level, BlockPos pos, Direction facing, BlueprintData blueprint) {
        level.setBlockAndUpdate(pos, ModBlocks.EASEL_BLOCK.get().defaultBlockState().setValue(EaselBlock.FACING, facing));
        EaselBlockEntity easel = (EaselBlockEntity) level.getBlockEntity(pos);
        easel.setBlueprintData(blueprint);
        return easel;
    }

    /**
     * The area searched for other easels: the building plus {@link #EASEL_SPACING} blocks around the easel.
     */
    private static BoundingBox easelSearchArea(BlockPos easelPos, BoundingBox bounds) {
        BoundingBox around = new BoundingBox(
                easelPos.getX() - EASEL_SPACING, easelPos.getY() - EASEL_SEARCH_HEIGHT, easelPos.getZ() - EASEL_SPACING,
                easelPos.getX() + EASEL_SPACING, easelPos.getY() + EASEL_SEARCH_HEIGHT, easelPos.getZ() + EASEL_SPACING);
        return around.encapsulate(bounds);
    }

    private static boolean isSolidGround(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isFaceSturdy(level, pos, Direction.UP);
    }

    private static String blockName(BlockState state) {
        return state.getBlock().getName().getString();
    }
}
