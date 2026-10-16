package net.bumblebee.claysoldiers.kingdom;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.BlueprintTemplateSettings;
import net.bumblebee.claysoldiers.blueprint.site.BuildSitePlanner;
import net.bumblebee.claysoldiers.blueprint.site.SiteCheck;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.dev.MaterialSupply;
import net.bumblebee.claysoldiers.entity.goal.workgoal.BuildBlueprintGoal;
import net.bumblebee.claysoldiers.entity.goal.workgoal.BuilderReach;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.structure.StructureBlueprints;
import net.bumblebee.claysoldiers.structure.StructureMeta;
import net.bumblebee.claysoldiers.structure.StructurePaster;
import net.bumblebee.claysoldiers.structure.StructureRegistry;
import net.bumblebee.claysoldiers.terrain.TerrainScan;
import net.bumblebee.claysoldiers.terrain.TerrainScanner;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Stage 4: a kingdom builds its own keep. Master plan v0 is one slot, the keep ({@code csr:keep}, detail level
 * {@code kingdom.keep.level}); this is the kingdom-level planner that places it and looks after it.
 * <ol>
 *     <li><b>Plan:</b> an active kingdom without a keep picks the site ({@link KeepSite}: the nearest flat ground to where
 *     it was founded), levels it at once (cuts and fills earth, clears plants and trees; logs go to the stockpile), and
 *     places the easel with the keep's blueprint and the builders' chest (the {@code /csr dev structure build} layout).
 *     Until soldiers gather materials, everything the keep needs goes into the stockpile then
 *     ({@code kingdom.keep.free_materials}, D93). When there is no site (ground not loaded, too uneven, water) it tries
 *     again after {@code kingdom.keep.plan_retry_ticks}.</li>
 *     <li><b>Build:</b> the kingdom picks {@code kingdom.keep.builders} of its own soldiers with no job (wheat holders
 *     first; the others are handed wheat so they can work) and makes them builders of the keep. The chest is stocked
 *     from the stockpile ({@link KingdomStore}); a player can also put materials in the chest.</li>
 *     <li><b>Built:</b> the keep is finished when its easel is; the kingdom puts a gold block, the throne, in its spot
 *     ({@link KeepThrone}), the king gets {@code kingdom.king_bonus_health} more health and goes to stand on the throne
 *     ({@code KingThroneGoal}).</li>
 * </ol>
 * Everything is driven by the server tick ({@link #tick}); the material supply and the last planning problem are in
 * memory only (rebuilt from the saved building and the easel after a restart). In a GameTest server the tick does
 * nothing: tests call {@link #update} themselves, so the kingdoms other tests found are left alone.
 */
public final class KingdomKeep {
    /** The keep's structure id. */
    public static final ResourceLocation KEEP = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "keep");
    /** The king's extra health, as an attribute modifier saved with him. */
    public static final ResourceLocation KING_HEALTH = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "king_of_a_kingdom");
    /** Margin of levelled ground around the keep, its easel and chest. */
    static final int MARGIN = 1;
    /** Blocks cleared above the keep's top when levelling (tree crowns). */
    static final int CLEAR_ABOVE = 8;
    /** Deepest a hole is filled when levelling. */
    static final int MAX_FILL = 16;

    private static final Map<UUID, MaterialSupply> SUPPLIES = new HashMap<>();
    private static final Map<UUID, Long> NEXT_PLAN = new HashMap<>();
    private static final Map<UUID, String> PROBLEMS = new HashMap<>();
    private static final Map<UUID, Integer> CREWS = new HashMap<>();

    private KingdomKeep() {
    }

    /**
     * Called every server tick: every {@code kingdom.keep.check_ticks}, each active kingdom looks after its keep, then its
     * economy (stage 5, {@link KingdomEconomy}).
     */
    public static void tick(MinecraftServer server) {
        if (server instanceof GameTestServer || server.getTickCount() % Tuning.KINGDOM_KEEP_CHECK_TICKS.get() != 0) {
            return;
        }
        KingdomSavedData data = KingdomSavedData.get(server);
        if (data.isNewerFormat()) {
            return;
        }
        for (Kingdom kingdom : List.copyOf(data.all())) {
            if (kingdom.status() == KingdomStatus.ACTIVE) {
                ServerLevel level = server.getLevel(kingdom.dimension());
                if (level != null) {
                    update(level, kingdom);
                    KingdomEconomy.update(level, kingdom);
                }
            }
        }
        SUPPLIES.keySet().removeIf(id -> data.all().stream().noneMatch(kingdom -> kingdom.id().equals(id)));
    }

    /** One look after the kingdom's keep: plan it, build it, or crown the king. */
    public static void update(ServerLevel level, Kingdom kingdom) {
        Optional<KingdomBuilding> keep = keep(kingdom);
        if (keep.isEmpty()) {
            Long next = NEXT_PLAN.get(kingdom.id());
            if (next == null || level.getGameTime() >= next) {
                NEXT_PLAN.put(kingdom.id(), level.getGameTime() + Tuning.KINGDOM_KEEP_PLAN_RETRY_TICKS.get());
                plan(level, kingdom);
            }
            return;
        }
        switch (keep.get().state()) {
            case PLANNED, BUILDING -> build(level, kingdom, keep.get());
            case BUILT -> crown(level, kingdom);
        }
    }

    /** The kingdom's keep, if it has one (built or not): master plan v0 has one slot, so it is the first building. */
    public static Optional<KingdomBuilding> keep(Kingdom kingdom) {
        return kingdom.buildings().stream().findFirst();
    }

    // Planning

    /** Plans the keep with the tuning's level: picks and levels the site, places the easel and chest. */
    public static boolean plan(ServerLevel level, Kingdom kingdom) {
        Optional<StructureMeta> meta = StructureRegistry.get(level.getServer()).byId(KEEP).filter(StructureMeta::isReady);
        if (meta.isEmpty()) {
            return problem(kingdom, "the keep structure " + KEEP + " isn't loaded");
        }
        if (!ClaySoldiersCommon.COMMON_HOOKS.isBlueprintEnabled(level.enabledFeatures())) {
            return problem(kingdom, "blueprints are off in this world (the blueprint experiment), so soldiers can't build");
        }
        int detail = Math.min(Tuning.KINGDOM_KEEP_LEVEL.get(), meta.get().levelCount());
        BlueprintData blueprint = StructureBlueprints.blueprint(level, meta.get(), detail);
        Optional<ServerBlueprintPlan> plan = blueprint.createServerPlan();
        if (plan.isEmpty()) {
            return problem(kingdom, "the keep's blueprint has no plan");
        }
        List<KeepSite.Area> areas = new ArrayList<>();
        for (Direction facing : new Direction[]{Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST}) {
            areas.add(area(plan.get(), facing));
        }
        KeepSite.Rules rules = new KeepSite.Rules(Tuning.KINGDOM_KEEP_SEARCH_RADIUS.get(), Tuning.TERRAIN_SEARCH_STEP.get(),
                Tuning.KINGDOM_KEEP_MAX_HEIGHT_RANGE.get(), MARGIN);
        BlockPos center = kingdom.center();
        TerrainScan scan = TerrainScanner.scan(level, center, KeepSite.scanRadius(areas, rules));
        KeepSite.Result result = KeepSite.search(scan, center.getX(), center.getZ(), areas, rules);
        DebugLog.event(DebugCategory.KINGDOM, null, () -> "keep of %s: %s".formatted(kingdom.team(), result.summary()));
        if (result.best().isEmpty()) {
            return problem(kingdom, "no flat ground for the keep within " + rules.searchRadius() + " blocks of " + center.toShortString()
                    + " (" + result.summary().substring("no site: ".length()) + "); trying again later");
        }
        KeepSite.Choice choice = result.best().get();
        int height = plan.get().getSize().getY();
        Levelled levelled = levelGround(level, choice.minX(MARGIN), choice.maxX(MARGIN), choice.minZ(MARGIN), choice.maxZ(MARGIN),
                choice.groundY(), choice.groundY() + height + CLEAR_ABOVE);
        levelled.logs().forEach((item, count) -> Kingdoms.addResource(level.getServer(), kingdom, KingdomStore.id(item), count));
        DebugLog.event(DebugCategory.TERRAIN, null, () -> "levelled the keep site of %s: %s; filled %d, cleared %d, logs to the stockpile %s"
                .formatted(kingdom.team(), choice.describe(), levelled.filled(), levelled.cleared(), levelled.logs()));
        BlockPos easel = new BlockPos(choice.easelX(), choice.groundY() + 1, choice.easelZ());
        if (!start(level, kingdom, KEEP, detail, blueprint, easel, choice.area().facing())) {
            return false;
        }
        grantMaterials(level, kingdom, detail);
        return true;
    }

    /**
     * Until soldiers gather materials (stage 5), a kingdom that starts its keep gets everything the keep needs in its
     * stockpile ({@code kingdom.keep.free_materials}, D93): nothing else supplies it in a normal game.
     */
    private static void grantMaterials(ServerLevel level, Kingdom kingdom, int detail) {
        if (Tuning.KINGDOM_KEEP_FREE_MATERIALS.get() == 0) {
            return;
        }
        MinecraftServer server = level.getServer();
        int count = 0;
        for (ItemStack stack : materials(level, detail)) {
            Kingdoms.addResource(server, kingdom, KingdomStore.id(stack.getItem()), stack.getCount());
            count += stack.getCount();
        }
        int total = count;
        Kingdoms.chronicle(server, kingdom, "The kingdom gathered what its keep needs (" + total + " items).");
        DebugLog.event(DebugCategory.KINGDOM, null, () -> "granted %s the keep's %d items (kingdom.keep.free_materials)".formatted(kingdom.team(), total));
    }

    /**
     * Starts building {@code structure} at {@code easel}, going {@code facing}: checks the site like {@code /csr build},
     * places the easel and the builders' chest, and adds the building to the kingdom. Doesn't level anything.
     */
    public static boolean start(ServerLevel level, Kingdom kingdom, ResourceLocation structure, int detail, BlueprintData blueprint,
                                BlockPos easel, Direction facing) {
        SiteCheck check = BuildSitePlanner.check(level, blueprint, structure, easel, facing);
        if (!check.isValid()) {
            DebugLog.event(DebugCategory.KINGDOM, null, () -> "keep of %s not started at %s: %s".formatted(kingdom.team(), easel.toShortString(), check.message()));
            return problem(kingdom, "the keep site at " + easel.toShortString() + " is refused: " + check.message() + "; trying again later");
        }
        BuildSitePlanner.placeEasel(level, easel, facing.getOpposite(), blueprint);
        BlockPos chest = chestPos(easel, facing);
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        KingdomBuilding building = new KingdomBuilding(structure, easel.immutable(), facing, KingdomBuilding.State.BUILDING, detail);
        kingdom.addBuilding(building);
        PROBLEMS.remove(kingdom.id());
        MinecraftServer server = level.getServer();
        Kingdoms.chronicle(server, kingdom, "The kingdom of " + Kingdoms.teamName(server, kingdom.team()) + " began its keep at "
                + easel.getX() + ", " + easel.getZ() + ".");
        DebugLog.event(DebugCategory.KINGDOM, null, () -> "keep of %s started: %s level %d, easel at %s going %s, chest at %s"
                .formatted(kingdom.team(), structure, detail, easel.toShortString(), facing.getName(), chest.toShortString()));
        return true;
    }

    /** The builders' chest of a building whose easel is at {@code easel}: 1 back toward the builders, 2 to the right. */
    public static BlockPos chestPos(BlockPos easel, Direction facing) {
        return easel.relative(facing.getOpposite()).relative(facing.getClockWise(), 2);
    }

    /**
     * The ground a building needs for one build direction, relative to its easel: the structure (from the plan's block
     * positions), the easel, the chest and the row in front where builders gather (the dev scenario's origin).
     */
    static KeepSite.Area area(ServerBlueprintPlan plan, Direction facing) {
        Direction easelFacing = facing.getOpposite();
        BlueprintTemplateSettings settings = new BlueprintTemplateSettings(plan.getSize(), Mirror.NONE, EaselBlockEntity.rotationFor(easelFacing));
        BlockPos base = EaselBlockEntity.templateBase(BlockPos.ZERO, easelFacing, settings);
        List<BlockPos> points = new ArrayList<>(plan.targetPositions(base, settings));
        points.add(BlockPos.ZERO);
        points.add(chestPos(BlockPos.ZERO, facing));
        points.add(BlockPos.ZERO.relative(facing.getOpposite(), 3));
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : points) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        BlockPos middle = base.offset(StructureTemplate.calculateRelativePosition(settings.convertTo(),
                new BlockPos(plan.getSize().getX() / 2, 0, plan.getSize().getZ() / 2)));
        return new KeepSite.Area(facing, minX, maxX, minZ, maxZ, middle.getX(), middle.getZ());
    }

    /**
     * What levelling changed.
     *
     * @param logs logs cut down (they go to the stockpile)
     */
    public record Levelled(int filled, int cleared, Map<Item, Integer> logs) {
    }

    /**
     * Levels the columns from ({@code minX}, {@code minZ}) to ({@code maxX}, {@code maxZ}) at once: everything from
     * {@code groundY + 1} to {@code clearTo} is removed (earth, plants, trees), and the ground is filled up to
     * {@code groundY} with dirt (grass on top), at most {@link #MAX_FILL} deep. Like the grading dev tool, but for a keep
     * site of any size; soldiers don't grade yet (docs/TERRAIN.md).
     */
    public static Levelled levelGround(ServerLevel level, int minX, int maxX, int minZ, int maxZ, int groundY, int clearTo) {
        int filled = 0;
        int cleared = 0;
        Map<Item, Integer> logs = new LinkedHashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = clearTo; y > groundY; y--) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir()) {
                        continue;
                    }
                    if (state.is(BlockTags.LOGS)) {
                        logs.merge(state.getBlock().asItem(), 1, Integer::sum);
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
                    cleared++;
                }
                for (int y = groundY; y > groundY - MAX_FILL; y--) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (isGround(state)) {
                        if (y == groundY && state.is(Blocks.DIRT)) {
                            level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), flags);
                        }
                        break;
                    }
                    if (state.is(BlockTags.LOGS)) {
                        logs.merge(state.getBlock().asItem(), 1, Integer::sum);
                    }
                    level.setBlock(pos, (y == groundY ? Blocks.GRASS_BLOCK : Blocks.DIRT).defaultBlockState(), flags);
                    filled++;
                }
            }
        }
        // Whoever stood in a hole that was filled (soldiers take 100 times vanilla's suffocation damage) stands on top now.
        AABB below = new AABB(minX, groundY - MAX_FILL, minZ, maxX + 1, groundY + 1, maxZ + 1);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, below, e -> e.getY() < groundY + 1)) {
            entity.teleportTo(entity.getX(), groundY + 1, entity.getZ());
        }
        return new Levelled(filled, cleared, logs);
    }

    /** Ground to build on: blocks movement, isn't part of a tree and holds no fluid (like {@code TerrainScanner}). */
    private static boolean isGround(BlockState state) {
        return state.blocksMotion() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && state.getFluidState().isEmpty();
    }

    // Building

    private static void build(ServerLevel level, Kingdom kingdom, KingdomBuilding keep) {
        if (!level.isLoaded(keep.pos())) {
            return;
        }
        if (!(level.getBlockEntity(keep.pos()) instanceof EaselBlockEntity easel) || !(easel.getPlan() instanceof ServerBlueprintPlan plan)) {
            problem(kingdom, "the keep's easel at " + keep.pos().toShortString() + " is gone or has no blueprint");
            return;
        }
        MinecraftServer server = level.getServer();
        if (easel.isFinished()) {
            kingdom.replaceBuilding(keep, keep.withState(KingdomBuilding.State.BUILT));
            KingdomSavedData.get(server).setDirty();
            SUPPLIES.remove(kingdom.id());
            CREWS.remove(kingdom.id());
            Kingdoms.chronicle(server, kingdom, "The keep of " + Kingdoms.teamName(server, kingdom.team()) + " is finished. "
                    + Kingdoms.kingTitle(server, kingdom, true) + " takes his throne.");
            DebugLog.event(DebugCategory.KINGDOM, null, () -> "keep of %s finished at %s".formatted(kingdom.team(), keep.pos().toShortString()));
            placeThrone(level, kingdom, keep);
            crown(level, kingdom);
            return;
        }
        PROBLEMS.remove(kingdom.id());
        BlockPos chest = chestPos(keep.pos(), keep.facing());
        MaterialSupply supply = SUPPLIES.get(kingdom.id());
        if (supply == null || supply.isFinished() || !supply.chestPos().equals(chest)) {
            Item scaffold = BuiltInRegistries.BLOCK.get(Tuning.SCAFFOLD_MATERIAL.get()).asItem();
            supply = new MaterialSupply(level, chest, new KingdomStore(server, kingdom, scaffold == Items.AIR ? null : scaffold),
                    plan::remainingItems, () -> easel.isRemoved() || easel.isFinished());
            SUPPLIES.put(kingdom.id(), supply);
        }
        supply.restock();
        CREWS.put(kingdom.id(), assignBuilders(level, kingdom, chest));
    }

    /**
     * Makes soldiers of the kingdom's team inside its claim builders of the keep until it has
     * {@code kingdom.keep.builders}: soldiers with no job and no POI that aren't the king, the ones that can already work
     * (holding wheat) first, then the nearest to the chest. A soldier that can't work yet is handed wheat (D93: nobody
     * has to give the builders anything). Returns the crew's size.
     */
    static int assignBuilders(ServerLevel level, Kingdom kingdom, BlockPos chest) {
        int radius = kingdom.claimRadius();
        BlockPos center = kingdom.center();
        AABB claim = new AABB(center.getX() - radius, level.getMinBuildHeight(), center.getZ() - radius,
                center.getX() + radius + 1, level.getMaxBuildHeight(), center.getZ() + radius + 1);
        List<AbstractClaySoldierEntity> soldiers = level.getEntitiesOfClass(AbstractClaySoldierEntity.class, claim,
                soldier -> soldier.isAlive() && kingdom.team().equals(soldier.getClayTeamType()) && !soldier.getUUID().equals(kingdom.king().id())
                        && kingdom.claims(level.dimension(), soldier.blockPosition()));
        int crew = (int) soldiers.stream().filter(soldier -> chest.equals(soldier.getPoiPos()) && soldier.getWorkSelector().isWorking()).count();
        int wanted = Tuning.KINGDOM_KEEP_BUILDERS.get();
        if (crew >= wanted) {
            return crew;
        }
        List<AbstractClaySoldierEntity> idle = new ArrayList<>(soldiers.stream()
                .filter(soldier -> !soldier.getWorkSelector().isWorking() && soldier.getPoiPos() == null && !soldier.isPassenger()
                        && !Kingdoms.isKing(soldier))
                .toList());
        idle.sort(Comparator.<AbstractClaySoldierEntity>comparingInt(soldier -> soldier.getAttackType().canWork() ? 0 : 1)
                .thenComparingDouble(soldier -> soldier.distanceToSqr(chest.getX() + 0.5, chest.getY(), chest.getZ() + 0.5)));
        for (AbstractClaySoldierEntity soldier : idle) {
            if (crew >= wanted) {
                break;
            }
            if (!soldier.getAttackType().canWork()) {
                soldier.equipItemIfPossible(new ItemStack(Items.WHEAT));
                if (!soldier.getAttackType().canWork()) {
                    DebugLog.event(DebugCategory.KINGDOM, soldier, "couldn't hand wheat to a soldier of %s (no free slot)", kingdom.team());
                    continue;
                }
                DebugLog.event(DebugCategory.KINGDOM, soldier, "%s handed a soldier wheat to make him a builder", kingdom.team());
            }
            // The job before the POI: a soldier without a job treats a POI as a one-time gear pickup (DevScenarios.spawnWorker).
            if (soldier.getWorkSelector().selectWork(BuildBlueprintGoal.class)) {
                soldier.setPoiPos(chest);
                crew++;
                int count = crew;
                DebugLog.event(DebugCategory.KINGDOM, soldier, () -> "%s made a builder of the keep (%d of %d)".formatted(kingdom.team(), count, wanted));
            }
        }
        return crew;
    }

    // The king

    /**
     * Puts the throne, a gold block, in its spot in the finished keep (replacing what the structure has there: a carpet,
     * the wooden throne). The kingdom adds it; it doesn't come from the stockpile.
     */
    static void placeThrone(ServerLevel level, Kingdom kingdom, KingdomBuilding keep) {
        throne(level, keep).ifPresent(pos -> {
            if (!level.getBlockState(pos).is(Blocks.GOLD_BLOCK)) {
                level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            }
            DebugLog.event(DebugCategory.KINGDOM, null, () -> "throne of %s placed at %s".formatted(kingdom.team(), pos.toShortString()));
        });
    }

    /** Gives the king of a kingdom with a finished keep his extra health (once; it is saved with him). */
    static void crown(ServerLevel level, Kingdom kingdom) {
        if (level.getEntity(kingdom.king().id()) instanceof AbstractClaySoldierEntity king && king.isAlive()) {
            AttributeInstance health = king.getAttribute(Attributes.MAX_HEALTH);
            int bonus = Tuning.KINGDOM_KING_BONUS_HEALTH.get();
            if (health != null && bonus > 0 && !health.hasModifier(KING_HEALTH)) {
                health.addPermanentModifier(new AttributeModifier(KING_HEALTH, bonus, AttributeModifier.Operation.ADD_VALUE));
                king.setHealth(king.getMaxHealth());
                DebugLog.event(DebugCategory.KINGDOM, king, () -> "king of %s crowned: health %.0f".formatted(kingdom.team(), king.getMaxHealth()));
            }
        }
    }

    /** Takes the extra health back (the kingdom was disbanded). */
    static void uncrown(MinecraftServer server, Kingdom kingdom) {
        SUPPLIES.remove(kingdom.id());
        NEXT_PLAN.remove(kingdom.id());
        PROBLEMS.remove(kingdom.id());
        CREWS.remove(kingdom.id());
        ServerLevel level = server.getLevel(kingdom.dimension());
        if (level != null && level.getEntity(kingdom.king().id()) instanceof AbstractClaySoldierEntity king) {
            AttributeInstance health = king.getAttribute(Attributes.MAX_HEALTH);
            if (health != null && health.hasModifier(KING_HEALTH)) {
                health.removeModifier(KING_HEALTH);
                king.setHealth(Math.min(king.getHealth(), king.getMaxHealth()));
            }
        }
    }

    /**
     * The throne {@code soldier} stands on, if it is the king of an active kingdom whose keep is finished: the gold block's
     * position ({@link KeepThrone}) in the world.
     */
    public static Optional<BlockPos> throneFor(AbstractClaySoldierEntity soldier) {
        MinecraftServer server = soldier.getServer();
        if (server == null || !(soldier.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        for (Kingdom kingdom : KingdomSavedData.get(server).all()) {
            if (kingdom.status() == KingdomStatus.ACTIVE && kingdom.king().id().equals(soldier.getUUID())
                    && kingdom.dimension().equals(level.dimension())) {
                return keep(kingdom).filter(keep -> keep.state() == KingdomBuilding.State.BUILT).flatMap(keep -> throne(level, keep));
            }
        }
        return Optional.empty();
    }

    /** The world position of a building's throne, if its structure has one. */
    public static Optional<BlockPos> throne(ServerLevel level, KingdomBuilding building) {
        Optional<BlockPos> throne = KeepThrone.throne(building.structure(), building.level());
        Optional<StructureMeta> meta = StructureRegistry.get(level.getServer()).byId(building.structure());
        if (throne.isEmpty() || meta.isEmpty()) {
            return Optional.empty();
        }
        Vec3i size;
        try {
            size = StructurePaster.template(level, meta.get(), building.level()).getSize();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        Direction easelFacing = building.facing().getOpposite();
        BlueprintTemplateSettings settings = new BlueprintTemplateSettings(size, Mirror.NONE, EaselBlockEntity.rotationFor(easelFacing));
        BlockPos base = EaselBlockEntity.templateBase(building.pos(), easelFacing, settings);
        return Optional.of(base.offset(StructureTemplate.calculateRelativePosition(settings.convertTo(), throne.get())));
    }

    /** Called by {@code KingThroneGoal} when the king is carried to his throne (he couldn't walk there). */
    public static void carried(AbstractClaySoldierEntity king, BlockPos throne) {
        DebugLog.event(DebugCategory.KINGDOM, king, "king carried to his throne at %s: no way there within the walk time", throne.toShortString());
    }

    // Status

    /** How much of each item the keep still needs that neither its chest nor the stockpile holds (most first). */
    public static Map<Item, Integer> missing(ServerLevel level, Kingdom kingdom) {
        Optional<KingdomBuilding> keep = keep(kingdom).filter(building -> building.state() != KingdomBuilding.State.BUILT);
        if (keep.isEmpty() || !level.isLoaded(keep.get().pos())
                || !(level.getBlockEntity(keep.get().pos()) instanceof EaselBlockEntity easel) || !(easel.getPlan() instanceof ServerBlueprintPlan plan)) {
            return Map.of();
        }
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (Item item : plan.remainingItems()) {
            if (item != Items.AIR) {
                needed.merge(item, 1, Integer::sum);
            }
        }
        BlockPos chestPos = chestPos(keep.get().pos(), keep.get().facing());
        ChestBlockEntity chest = level.isLoaded(chestPos) && level.getBlockEntity(chestPos) instanceof ChestBlockEntity found ? found : null;
        Map<Item, Integer> missing = new LinkedHashMap<>();
        needed.forEach((item, count) -> {
            int have = kingdom.resource(KingdomStore.id(item)) + (chest == null ? 0 : chest.countItem(item));
            if (count > have) {
                missing.put(item, count - have);
            }
        });
        List<Map.Entry<Item, Integer>> sorted = new ArrayList<>(missing.entrySet());
        sorted.sort(Map.Entry.<Item, Integer>comparingByValue().reversed());
        Map<Item, Integer> result = new LinkedHashMap<>();
        sorted.forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    /** Everything the keep needs at its detail level, with the scaffold stock when it is taller than builders reach. */
    public static List<ItemStack> materials(ServerLevel level, int detail) {
        Optional<StructureMeta> meta = StructureRegistry.get(level.getServer()).byId(KEEP).filter(StructureMeta::isReady);
        if (meta.isEmpty()) {
            return List.of();
        }
        int lvl = Math.min(detail, meta.get().levelCount());
        List<ItemStack> materials = new ArrayList<>(StructureBlueprints.blueprint(level, meta.get(), lvl).createServerPlan()
                .map(ServerBlueprintPlan::getNeededItems).orElse(List.of()));
        if (StructurePaster.template(level, meta.get(), lvl).getSize().getY() > BuilderReach.MAX_PLACE_HEIGHT + 1) {
            materials.add(new ItemStack(BuiltInRegistries.BLOCK.get(Tuning.SCAFFOLD_MATERIAL.get()).asItem(), Tuning.KINGDOM_KEEP_SCAFFOLD_STOCK.get()));
        }
        return materials;
    }

    /** Lines about the keep for the book and {@code /csr kingdom status}. */
    public static List<String> describe(MinecraftServer server, Kingdom kingdom) {
        List<String> lines = new ArrayList<>();
        Optional<KingdomBuilding> keep = keep(kingdom);
        String problem = PROBLEMS.get(kingdom.id());
        if (keep.isEmpty()) {
            lines.add(kingdom.status() == KingdomStatus.ACTIVE ? "Not started yet." : "Never built.");
            if (problem != null) {
                lines.add("Waiting: " + problem + ".");
            }
            return lines;
        }
        KingdomBuilding building = keep.get();
        BlockPos pos = building.pos();
        if (building.state() == KingdomBuilding.State.BUILT) {
            lines.add("Finished (level " + building.level() + ") at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ".");
            return lines;
        }
        lines.add("Being built (level " + building.level() + "), easel at " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ".");
        Integer crew = CREWS.get(kingdom.id());
        if (crew != null) {
            int wanted = Tuning.KINGDOM_KEEP_BUILDERS.get();
            lines.add("Builders: " + crew + " of " + wanted + (crew < wanted ? " (more soldiers of the team without a job are needed in the claim)" : "") + ".");
        }
        if (problem != null) {
            lines.add("Problem: " + problem + ".");
        }
        ServerLevel level = server.getLevel(kingdom.dimension());
        if (level != null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof EaselBlockEntity easel
                && easel.getPlan() instanceof ServerBlueprintPlan plan) {
            lines.add("Blocks left: " + plan.remainingItems().size() + ".");
            Map<Item, Integer> missing = missing(level, kingdom);
            if (missing.isEmpty()) {
                lines.add("Materials: all supplied.");
            } else {
                lines.add("Put in its chest (still missing):");
                missing.entrySet().stream().limit(12).forEach(entry -> lines.add("  " + entry.getValue() + " " + entry.getKey().getDescription().getString()));
                if (missing.size() > 12) {
                    lines.add("  and " + (missing.size() - 12) + " more kinds");
                }
            }
        }
        return lines;
    }

    /** Records why the keep can't go on, for the book; returns false. */
    private static boolean problem(Kingdom kingdom, String why) {
        if (!why.equals(PROBLEMS.put(kingdom.id(), why))) {
            DebugLog.event(DebugCategory.KINGDOM, null, () -> "keep of %s waiting: %s".formatted(kingdom.team(), why));
        }
        return false;
    }

    /** The keep's material supply (GameTests). */
    @Nullable
    public static MaterialSupply supply(Kingdom kingdom) {
        return SUPPLIES.get(kingdom.id());
    }

    /** Forgets everything kept in memory about the kingdom (GameTests). */
    public static void forget(Kingdom kingdom) {
        SUPPLIES.remove(kingdom.id());
        NEXT_PLAN.remove(kingdom.id());
        PROBLEMS.remove(kingdom.id());
        CREWS.remove(kingdom.id());
    }
}
