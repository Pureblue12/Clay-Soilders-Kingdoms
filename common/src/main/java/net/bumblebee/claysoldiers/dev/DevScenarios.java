package net.bumblebee.claysoldiers.dev;

import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.site.BuildSitePlanner;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.entity.goal.workgoal.BuildBlueprintGoal;
import net.bumblebee.claysoldiers.entity.goal.workgoal.BuilderReach;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.init.ModEntityTypes;
import net.bumblebee.claysoldiers.init.ModRegistries;
import net.bumblebee.claysoldiers.scaffold.TestStructures;
import net.bumblebee.claysoldiers.structure.StructureBlueprints;
import net.bumblebee.claysoldiers.structure.StructureMeta;
import net.bumblebee.claysoldiers.structure.StructurePaster;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.bumblebee.claysoldiers.team.TeamLoyaltyManger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds test setups in code, so manual tests and GameTests don't need Clay Brush clicks.
 * Development only: used by {@link DevCommands} and the NeoForge GameTests.
 */
public final class DevScenarios {
    public static final ResourceLocation RED_TEAM = ResourceLocation.fromNamespaceAndPath("csr", "red");
    public static final ResourceLocation SMALL_HOUSE = ResourceLocation.fromNamespaceAndPath("csr", "small_house");

    private DevScenarios() {
    }

    /**
     * Result of {@link #buildTest}, describing what was placed.
     */
    public record BuildTestResult(BlockPos chest, BlockPos easel, List<ClaySoldierEntity> soldiers, List<ItemStack> chestItems) {
    }

    /**
     * Places a chest (stocked with the Small House materials, or empty), an easel with the Small House blueprint
     * and 3 red pacifist soldiers working as builders with the chest as their POI.
     * The house is built on the far side of the easel, in the direction the player is facing.
     *
     * @param stocked whether the chest gets the blueprint's materials
     * @throws IllegalStateException when the blueprint is missing (e.g. the blueprint experiment is disabled)
     */
    public static BuildTestResult buildTest(ServerLevel level, Player player, BlockPos origin, Direction forward, boolean stocked) {
        BlueprintData blueprint = level.registryAccess().registryOrThrow(ModRegistries.BLUEPRINTS).get(SMALL_HOUSE);
        if (blueprint == null || !blueprint.isValid()) {
            throw new IllegalStateException("Blueprint " + SMALL_HOUSE + " is missing or invalid. Is the blueprint experiment enabled?");
        }
        Direction right = forward.getClockWise();

        BlockPos chestPos = origin.relative(forward, 2).relative(right, 2);
        List<ItemStack> items = stocked ? blueprint.createServerPlan().orElseThrow().getNeededItems() : List.of();
        placeChest(level, chestPos, items);

        // The easel faces the player; the blueprint is built 2 blocks behind it.
        BlockPos easelPos = origin.relative(forward, 3);
        placeEasel(level, easelPos, forward.getOpposite(), blueprint);

        List<ClaySoldierEntity> soldiers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BlockPos spawnPos = origin.relative(forward, 1).relative(right, i);
            soldiers.add(spawnWorker(level, spawnPos, RED_TEAM, player, BuildBlueprintGoal.class, chestPos));
        }
        return new BuildTestResult(chestPos, easelPos, soldiers, items);
    }

    /**
     * The "Build test" setup of the Scaffolding dev book category (also used by GameTests, so they test the same
     * layout): a chest 3 blocks to the right with the structure's cobblestone and {@code scaffoldStock} scaffold
     * material, an easel 3 blocks ahead facing back with a test structure (built behind it), and {@code builders} red
     * builders between them.
     */
    public static BuildTestResult scaffoldTest(ServerLevel level, Player player, BlockPos origin, Direction forward,
                                               TestStructures.Shape shape, int height, int builders, int scaffoldStock) {
        Direction right = forward.getClockWise();
        BlockPos easelPos = origin.relative(forward, 3);
        BlockPos chestPos = origin.relative(right, 3);
        ItemStack scaffold = new ItemStack(BuiltInRegistries.BLOCK.get(Tuning.SCAFFOLD_MATERIAL.get()).asItem(), scaffoldStock);
        List<ItemStack> items = List.of(new ItemStack(Items.COBBLESTONE, TestStructures.blocks(shape, height).size()), scaffold);
        placeChest(level, chestPos, items);
        placeEasel(level, easelPos, forward.getOpposite(), TestStructures.blueprint(level, shape, height));
        List<ClaySoldierEntity> soldiers = new ArrayList<>();
        for (int i = 0; i < builders; i++) {
            soldiers.add(spawnWorker(level, origin.relative(right, 1 + i).relative(forward), RED_TEAM, player, BuildBlueprintGoal.class, chestPos));
        }
        return new BuildTestResult(chestPos, easelPos, soldiers, items);
    }

    /** Scaffold material put in the supply of a structure build that is taller than builders reach (9). */
    public static final int STRUCTURE_SCAFFOLD_STOCK = 256;

    /**
     * Result of {@link #structureBuild}.
     *
     * @param supply keeps the chest stocked; tick it (GameTests) or {@link MaterialSupply#start start} it (dev commands)
     */
    public record StructureBuildResult(EaselBlockEntity easel, ChestBlockEntity chest, List<ClaySoldierEntity> soldiers,
                                       MaterialSupply supply, List<ItemStack> materials, boolean scaffold) {
    }

    /**
     * Sets up builders for one detail level of a kingdom structure, in the Small House scenario's layout (the same as
     * the batch-1 builder GameTests): an easel 3 ahead facing back (the structure goes on its far side, centered), a
     * chest 2 ahead and 2 to the right, 3 red builders 1 ahead. The chest is kept stocked with the level's materials
     * by a {@link MaterialSupply} (most structures need more than a chest holds), plus
     * {@link #STRUCTURE_SCAFFOLD_STOCK} scaffold material when the structure is taller than builders reach.
     */
    public static StructureBuildResult structureBuild(ServerLevel level, Player owner, BlockPos origin, Direction forward,
                                                      StructureMeta meta, int detailLevel) {
        Direction right = forward.getClockWise();
        BlueprintData blueprint = StructureBlueprints.blueprint(level, meta, detailLevel);
        BlockPos easelPos = origin.relative(forward, 3);
        BlockPos chestPos = origin.relative(forward, 2).relative(right, 2);
        List<ItemStack> materials = new ArrayList<>(blueprint.createServerPlan().orElseThrow().getNeededItems());
        int height = StructurePaster.template(level, meta, detailLevel).getSize().getY();
        boolean scaffold = height > BuilderReach.MAX_PLACE_HEIGHT + 1;
        if (scaffold) {
            materials.add(new ItemStack(BuiltInRegistries.BLOCK.get(Tuning.SCAFFOLD_MATERIAL.get()).asItem(), STRUCTURE_SCAFFOLD_STOCK));
        }
        ChestBlockEntity chest = placeChest(level, chestPos, List.of());
        EaselBlockEntity easel = placeEasel(level, easelPos, forward.getOpposite(), blueprint);
        MaterialSupply supply = new MaterialSupply(level, chestPos, materials,
                () -> easel.getPlan() instanceof ServerBlueprintPlan plan ? plan.remainingItems() : List.of(),
                () -> easel.isRemoved() || easel.isFinished());
        supply.restock();
        List<ClaySoldierEntity> soldiers = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            soldiers.add(spawnWorker(level, origin.relative(forward).relative(right, i), RED_TEAM, owner, BuildBlueprintGoal.class, chestPos));
        }
        return new StructureBuildResult(easel, chest, soldiers, supply, materials, scaffold);
    }

    /**
     * Places a chest at the given position and fills it with the given stacks, split into max-size stacks.
     */
    public static ChestBlockEntity placeChest(ServerLevel level, BlockPos pos, List<ItemStack> items) {
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(pos);
        int slot = 0;
        for (ItemStack stack : items) {
            int remaining = stack.getCount();
            while (remaining > 0 && slot < chest.getContainerSize()) {
                int count = Math.min(remaining, stack.getMaxStackSize());
                chest.setItem(slot++, stack.copyWithCount(count));
                remaining -= count;
            }
        }
        chest.setChanged();
        return chest;
    }

    /**
     * Places an easel facing the given direction and puts the blueprint on it.
     */
    public static EaselBlockEntity placeEasel(ServerLevel level, BlockPos pos, Direction facing, BlueprintData blueprint) {
        return BuildSitePlanner.placeEasel(level, pos, facing, blueprint);
    }

    /**
     * Spawns a clay soldier and makes it a worker, the same way a player would with wheat and the Clay Brush:
     * team, team loyalty to {@code owner}, wheat (makes it a pacifist), job, then POI.
     * The job is set before the POI, otherwise a non-working soldier treats the POI as a one-time gear pickup.
     *
     * @param workType the work goal to select, e.g. {@code BuildBlueprintGoal.class}
     * @param poi      the soldier's POI, or {@code null}
     */
    public static ClaySoldierEntity spawnWorker(ServerLevel level, BlockPos pos, ResourceLocation team, Player owner,
                                                Class<? extends Goal> workType, @Nullable BlockPos poi) {
        ClaySoldierEntity soldier = ModEntityTypes.CLAY_SOLDIER_ENTITY.get().create(level);
        soldier.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        level.addFreshEntity(soldier);
        soldier.setClayTeamType(team);
        TeamLoyaltyManger.setTeamPlayer(level, team, owner);
        soldier.equipItemIfPossible(new ItemStack(Items.WHEAT));
        soldier.getWorkSelector().selectWork(workType);
        soldier.setPoiPos(poi);
        return soldier;
    }

    /**
     * Describes a soldier's worker state, e.g. for command output.
     */
    public static String describeWorker(AbstractClaySoldierEntity soldier) {
        return "%s: canWork=%s, job=%s, poi=%s".formatted(
                soldier.getUUID().toString().substring(0, 8),
                soldier.getAttackType().canWork(),
                soldier.getWorkSelector().getWorkDisplayName().getString(),
                soldier.getPoiPos() == null ? "none" : soldier.getPoiPos().toShortString());
    }
}
