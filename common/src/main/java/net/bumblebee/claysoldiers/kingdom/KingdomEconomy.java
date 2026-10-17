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
import net.bumblebee.claysoldiers.entity.goal.workgoal.FellTreesGoal;
import net.bumblebee.claysoldiers.entity.goal.workgoal.FishGoal;
import net.bumblebee.claysoldiers.entity.goal.workgoal.TendFieldGoal;
import net.bumblebee.claysoldiers.entity.soldier.AbstractClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.layout.MasterPlan;
import net.bumblebee.claysoldiers.kingdom.layout.Rect;
import net.bumblebee.claysoldiers.scaffold.Scaffolds;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Stage 5, the first economy: what a kingdom does once its keep is built. Master plan v1 is a short list of slots after
 * the keep ({@link #PLAN}): a lumber camp, a wheat field, then a cottage the kingdom pays for with its own wood and food.
 * <ol>
 *     <li><b>Plots:</b> the small planner ({@link PlotPicker}) puts each building on free ground around the keep, facing it,
 *     away from water and other kingdoms; the kingdom levels the plot at once (like the keep's site) and places the easel and
 *     the builders' chest.</li>
 *     <li><b>Building:</b> like the keep: idle soldiers become builders ({@code kingdom.keep.builders}), the chest is stocked
 *     from the stockpile ({@link KingdomStore}, which makes wooden blocks from logs). The lumber camp and the field are the
 *     kingdom's start: with {@code economy.free_starter_buildings} their materials are given, like the keep's. The cottage
 *     is paid for: it starts only when the stockpile has the logs for all its wooden blocks and {@code economy.food_per_building}
 *     wheat (the builders' meals, taken when it starts); its other materials (stone, bricks, glass) are given with
 *     {@code economy.free_other_materials}, as no job makes them before stage 9.</li>
 *     <li><b>Finished:</b> the builders go back to being idle, the kingdom earns {@code economy.renown_per_building}.</li>
 *     <li><b>Workers:</b> while nothing is being built, idle soldiers become woodcutters at the lumber camp
 *     ({@link FellTreesGoal}) and farmers at the field ({@link TendFieldGoal}); they bring what they gather to their
 *     building's chest, which counts as the stockpile (the storehouse until a real one is designed). Every chest of a
 *     finished building is the storehouse: what anyone puts in it goes to the stockpile.</li>
 * </ol>
 * Memory only: supplies, planning problems, cached plots and costs. A building is saved in the kingdom like the keep.
 */
public final class KingdomEconomy {
    public static final ResourceLocation LUMBER_CAMP = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "lumber_camp");
    public static final ResourceLocation WHEAT_FIELD = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "farm_field_wheat");
    public static final ResourceLocation COTTAGE = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "cottage");
    /** The food a building costs (the builders' meals). */
    public static final Item FOOD = Items.WHEAT;

    /** A job a building gives soldiers. */
    public enum Job {
        WOODCUTTER(FellTreesGoal.class, "woodcutter"),
        FARMER(TendFieldGoal.class, "farmer"),
        FISHER(FishGoal.class, "fisher");

        final Class<? extends Goal> goal;
        final String name;

        Job(Class<? extends Goal> goal, String name) {
            this.goal = goal;
            this.name = name;
        }

        int wanted() {
            return switch (this) {
                case WOODCUTTER -> Tuning.ECONOMY_WOODCUTTERS.get();
                case FARMER -> Tuning.ECONOMY_FARMERS.get();
                case FISHER -> Tuning.FISHING_FISHERS.get();
            };
        }
    }

    /**
     * One building of the master plan.
     *
     * @param outer   whether it goes in the outer ring (beyond the claim by up to {@code economy.plot.outer_reach})
     * @param starter whether it is part of the kingdom's start (its materials are given, nothing is paid)
     * @param job     the job it gives, if any
     */
    public record Slot(ResourceLocation structure, int level, boolean outer, boolean starter, @Nullable Job job) {
    }

    /** Master plan v1: after the keep (stage 4), in this order. */
    public static final List<Slot> PLAN = List.of(
            new Slot(LUMBER_CAMP, 1, true, true, Job.WOODCUTTER),
            new Slot(WHEAT_FIELD, 1, true, true, Job.FARMER),
            new Slot(COTTAGE, 1, false, false, null),
            // The fishing dock (the owner's ask, 2026-10-17): only where there is a real lake (KingdomDock).
            new Slot(KingdomDock.FISHING_DOCK, 1, true, false, Job.FISHER));
    @Nullable
    private static List<Slot> planForTests;

    /** The master plan kingdoms follow: {@link #PLAN}, or small stand-ins a GameTest set. */
    public static List<Slot> plan() {
        return planForTests != null ? planForTests : PLAN;
    }

    /** GameTests only: kingdoms follow {@code plan} after their keep ({@code null}: {@link #PLAN} again). */
    public static void usePlanForTests(@Nullable List<Slot> plan) {
        planForTests = plan == null ? null : List.copyOf(plan);
    }

    private static final Map<UUID, MaterialSupply> SUPPLIES = new HashMap<>();
    private static final Map<UUID, Long> NEXT_PLAN = new HashMap<>();
    private static final Map<UUID, String> PROBLEMS = new HashMap<>();
    private static final Map<UUID, Map<Job, Integer>> WORKERS = new HashMap<>();
    private static final Map<KingdomBuilding, Optional<BoundingBox>> BOUNDS = new HashMap<>();
    /** Logs for every wooden block of a slot's building. */
    private static final Map<Slot, Optional<Integer>> LOGS = new HashMap<>();
    @Nullable
    private static MinecraftServer cachedFor;

    private KingdomEconomy() {
    }

    /**
     * One look after the kingdom's economy (called with the keep's, every {@code kingdom.keep.check_ticks}): nothing until
     * the keep is built; then builds the master plan's buildings one at a time and keeps its workers busy.
     */
    public static void update(ServerLevel level, Kingdom kingdom) {
        if (cachedFor != level.getServer()) {
            BOUNDS.clear();
            LOGS.clear();
            cachedFor = level.getServer();
        }
        Optional<KingdomBuilding> keep = KingdomKeep.keep(kingdom);
        if (keep.isEmpty() || keep.get().state() != KingdomBuilding.State.BUILT || kingdom.status() != KingdomStatus.ACTIVE) {
            return;
        }
        releaseCrews(level, kingdom);
        sweepChests(level, kingdom);
        KingdomLayout.update(level, kingdom);
        Optional<KingdomBuilding> running = kingdom.buildings().stream().skip(1)
                .filter(building -> building.state() != KingdomBuilding.State.BUILT).findFirst();
        if (running.isPresent()) {
            build(level, kingdom, running.get());
            return;
        }
        assignWorkers(level, kingdom);
        Optional<Slot> next = next(kingdom, level.getGameTime());
        if (next.isEmpty()) {
            PROBLEMS.remove(kingdom.id());
            return;
        }
        Long when = NEXT_PLAN.get(kingdom.id());
        if (when != null && level.getGameTime() < when) {
            return;
        }
        if (!next.get().starter()) {
            Optional<String> waiting = waitingFor(level, kingdom, next.get());
            if (waiting.isPresent()) {
                problem(kingdom, waiting.get());
                return;
            }
        }
        NEXT_PLAN.put(kingdom.id(), level.getGameTime() + Tuning.KINGDOM_KEEP_PLAN_RETRY_TICKS.get());
        plan(level, kingdom, next.get());
    }

    /** The master plan's next building the kingdom hasn't started, if any. */
    public static Optional<Slot> next(Kingdom kingdom) {
        return next(kingdom, Long.MIN_VALUE);
    }

    /**
     * The master plan's next building the kingdom hasn't started, if any, leaving out a dock that found no lake lately
     * ({@link KingdomDock#resting}): the kingdom builds the rest meanwhile.
     */
    public static Optional<Slot> next(Kingdom kingdom, long gameTime) {
        for (Slot slot : plan()) {
            if (kingdom.buildings().stream().noneMatch(building -> building.structure().equals(slot.structure()))
                    && !(KingdomDock.isDock(slot.structure()) && KingdomDock.resting(kingdom, gameTime))) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    // What a paid building costs

    /**
     * What a building costs the kingdom: the logs for its wooden blocks and the builders' food.
     *
     * @param logs logs for every wooden block (D98)
     * @param food wheat (the builders' meals)
     */
    public record Need(int logs, int food) {
    }

    /** What {@code slot}'s building costs, or empty when its structure isn't loaded. */
    public static Optional<Need> need(ServerLevel level, Slot slot) {
        return LOGS.computeIfAbsent(slot, key -> {
            Optional<ServerBlueprintPlan> plan = blueprint(level, key).flatMap(BlueprintData::createServerPlan);
            if (plan.isEmpty()) {
                return Optional.empty();
            }
            double planks = 0;
            for (ItemStack stack : plan.get().getNeededItems()) {
                OptionalDouble each = WoodCost.planks(level.getServer(), stack.getItem());
                if (each.isPresent()) {
                    planks += each.getAsDouble() * stack.getCount();
                }
            }
            return Optional.of(WoodCost.logsFor(planks));
        }).map(logs -> new Need(logs, Tuning.ECONOMY_FOOD_PER_BUILDING.get()));
    }

    /** Why a paid building can't start yet (not enough logs or food), or empty when the kingdom can pay for it. */
    public static Optional<String> waitingFor(ServerLevel level, Kingdom kingdom, Slot slot) {
        Optional<Need> need = need(level, slot);
        if (need.isEmpty()) {
            return Optional.of("the structure " + slot.structure() + " isn't loaded");
        }
        int logs = WoodCost.logsWorth(kingdom);
        int food = food(kingdom);
        List<String> missing = new ArrayList<>();
        if (logs < need.get().logs()) {
            missing.add((need.get().logs() - logs) + " more logs (needs " + need.get().logs() + ", has " + logs + ")");
        }
        if (food < need.get().food()) {
            missing.add((need.get().food() - food) + " more " + foodName() + " for the builders' meals (needs " + need.get().food() + ", has " + food + ")");
        }
        return missing.isEmpty() ? Optional.empty() : Optional.of("the " + name(level.getServer(), slot.structure()).toLowerCase()
                + " waits for " + String.join(" and ", missing));
    }

    // Planning

    private static Optional<BlueprintData> blueprint(ServerLevel level, Slot slot) {
        Optional<StructureMeta> meta = StructureRegistry.get(level.getServer()).byId(slot.structure()).filter(StructureMeta::isReady);
        if (meta.isEmpty() || !ClaySoldiersCommon.COMMON_HOOKS.isBlueprintEnabled(level.enabledFeatures())) {
            return Optional.empty();
        }
        return Optional.of(StructureBlueprints.blueprint(level, meta.get(), Math.min(slot.level(), meta.get().levelCount())));
    }

    /** Plans {@code slot}'s building: picks and levels a plot, places the easel and chest, pays for it. */
    public static boolean plan(ServerLevel level, Kingdom kingdom, Slot slot) {
        MinecraftServer server = level.getServer();
        Optional<BlueprintData> blueprint = blueprint(level, slot);
        Optional<ServerBlueprintPlan> plan = blueprint.flatMap(BlueprintData::createServerPlan);
        if (plan.isEmpty()) {
            return problem(kingdom, "the structure " + slot.structure() + " isn't loaded or blueprints are off; trying again later");
        }
        List<KeepSite.Area> areas = new ArrayList<>();
        for (Direction facing : new Direction[]{Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST}) {
            areas.add(KingdomKeep.area(plan.get(), facing));
        }
        String what = name(server, slot.structure());
        boolean dock = KingdomDock.isDock(slot.structure());
        Optional<MasterPlan.Plot> planned = dock ? Optional.empty() : KingdomLayout.plotFor(kingdom, slot.structure());
        Optional<KeepSite.Choice> site = dock ? KingdomDock.site(level, kingdom, slot.structure(), plan.get(), areas)
                : planned.flatMap(plot -> KingdomLayout.site(level, kingdom, plot, areas));
        if (site.isEmpty()) {
            if (dock) {
                return problem(kingdom, java.util.Objects.requireNonNullElse(KingdomDock.problemFor(kingdom), "no lake for the fishing dock"));
            }
            site = pick(level, kingdom, slot, areas, what);
            if (site.isEmpty()) {
                return false;
            }
        }
        KeepSite.Choice choice = site.get();
        boolean onPlan = planned.isPresent() && planned.get().easelX() == choice.easelX() && planned.get().easelZ() == choice.easelZ();
        BlockPos easel = new BlockPos(choice.easelX(), choice.groundY() + 1, choice.easelZ());
        int height = plan.get().getSize().getY();
        Rect plotRect = new Rect(choice.minX(KingdomKeep.MARGIN), choice.maxX(KingdomKeep.MARGIN), choice.minZ(KingdomKeep.MARGIN), choice.maxZ(KingdomKeep.MARGIN));
        if (dock) {
            KingdomDock.prepare(level, kingdom, slot.structure(), choice, plan.get());
        } else {
            KingdomKeep.Levelled levelled = KingdomKeep.levelGround(level, choice.minX(KingdomKeep.MARGIN), choice.maxX(KingdomKeep.MARGIN),
                    choice.minZ(KingdomKeep.MARGIN), choice.maxZ(KingdomKeep.MARGIN), choice.groundY(), choice.groundY() + height + KingdomKeep.CLEAR_ABOVE);
            levelled.logs().forEach((item, count) -> Kingdoms.addResource(server, kingdom, KingdomStore.id(item), count));
            int sealed = KingdomLayout.sealHollows(level, plotRect, choice.groundY(), Tuning.TERRAIN_CAVE_PROBE_DEPTH.get());
            DebugLog.event(DebugCategory.TERRAIN, null, () -> "levelled the %s plot of %s (%s): %s; filled %d, cleared %d, sealed %d cave blocks, logs to the stockpile %s"
                    .formatted(what, kingdom.team(), onPlan ? "the master plan's plot" : "picked the stage 5 way", choice.describe(),
                            levelled.filled(), levelled.cleared(), sealed, levelled.logs()));
        }
        SiteCheck check = BuildSitePlanner.check(level, blueprint.get(), slot.structure(), easel, choice.area().facing(), dock);
        if (!check.isValid()) {
            String why = check.message();
            DebugLog.event(DebugCategory.ECONOMY, null, () -> "%s of %s not started at %s: %s".formatted(what, kingdom.team(), easel.toShortString(), why));
            return problem(kingdom, "the " + what.toLowerCase() + " plot at " + easel.toShortString() + " is refused: " + why + "; trying again later");
        }
        if (!slot.starter() && !payFood(server, kingdom)) {
            return problem(kingdom, "not enough wheat for the builders' meals");
        }
        BuildSitePlanner.placeEasel(level, easel, choice.area().facing().getOpposite(), blueprint.get());
        if (!dock) {
            kingdom.plan().ifPresent(masterPlan -> KingdomLayout.showNearby(level, kingdom, masterPlan, plotRect, easel));
        }
        BlockPos chest = KingdomKeep.chestPos(easel, choice.area().facing());
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        int detail = Math.min(slot.level(), StructureRegistry.get(server).byId(slot.structure()).map(StructureMeta::levelCount).orElse(1));
        kingdom.addBuilding(new KingdomBuilding(slot.structure(), easel.immutable(), choice.area().facing(), KingdomBuilding.State.BUILDING, detail));
        KingdomSavedData.get(server).setDirty();
        PROBLEMS.remove(kingdom.id());
        grantMaterials(server, kingdom, slot, plan.get());
        Kingdoms.chronicle(server, kingdom, "The kingdom of " + Kingdoms.teamName(server, kingdom.team()) + " began a "
                + what.toLowerCase() + " at " + easel.getX() + ", " + easel.getZ() + ".");
        DebugLog.event(DebugCategory.ECONOMY, null, () -> "%s of %s started: easel at %s going %s, chest at %s"
                .formatted(what, kingdom.team(), easel.toShortString(), choice.area().facing().getName(), chest.toShortString()));
        return true;
    }

    /** Picks a plot the stage 5 way (no master plan, or its plot can't be used): free ground around the keep. */
    private static Optional<KeepSite.Choice> pick(ServerLevel level, Kingdom kingdom, Slot slot, List<KeepSite.Area> areas, String what) {
        MinecraftServer server = level.getServer();
        int maxRadius = kingdom.claimRadius() + (slot.outer() ? Tuning.ECONOMY_PLOT_OUTER_REACH.get() : 0);
        PlotPicker.Rules rules = new PlotPicker.Rules(0, maxRadius, Tuning.TERRAIN_SEARCH_STEP.get(), Tuning.ECONOMY_PLOT_MAX_HEIGHT_RANGE.get(),
                KingdomKeep.MARGIN, Tuning.ECONOMY_PLOT_GAP.get(), slot.job() == Job.WOODCUTTER);
        List<PlotPicker.Taken> taken = new ArrayList<>();
        for (KingdomBuilding building : kingdom.buildings()) {
            taken.add(taken(level, building));
        }
        taken.addAll(KingdomLayout.reserved(kingdom));
        List<Kingdom> others = Kingdoms.all(server).stream().filter(other -> !other.id().equals(kingdom.id())).toList();
        PlotPicker.Forbidden forbidden = (x, z) -> others.stream().anyMatch(other -> other.claims(level.dimension(), new BlockPos(x, 0, z)));
        BlockPos center = kingdom.center();
        TerrainScan scan = TerrainScanner.scan(level, center, PlotPicker.scanRadius(areas, rules));
        PlotPicker.Result result = PlotPicker.search(scan, center.getX(), center.getZ(), areas, taken, forbidden, rules);
        DebugLog.event(DebugCategory.ECONOMY, null, () -> "%s of %s: %s".formatted(what, kingdom.team(), result.summary()));
        if (result.best().isEmpty()) {
            problem(kingdom, "no plot for the " + what.toLowerCase() + " within " + maxRadius + " blocks of the center ("
                    + result.summary().substring("no plot: ".length()) + "); trying again later");
            return Optional.empty();
        }
        return Optional.of(result.best().get().site());
    }

    private static boolean payFood(MinecraftServer server, Kingdom kingdom) {
        int food = Tuning.ECONOMY_FOOD_PER_BUILDING.get();
        if (food <= 0) {
            return true;
        }
        if (food(kingdom) < food) {
            return false;
        }
        // Wheat first, then fish.
        for (Item item : foods()) {
            int take = Math.min(food, kingdom.resource(KingdomStore.id(item)));
            if (take > 0 && Kingdoms.removeResource(server, kingdom, KingdomStore.id(item), take).done()) {
                food -= take;
            }
        }
        return food <= 0;
    }

    /** What counts as food in the stockpile: wheat, and cod and salmon from the fishing dock ({@code fishing.fish_is_food}). */
    public static List<Item> foods() {
        return Tuning.FISHING_FISH_IS_FOOD.get() == 1 ? List.of(FOOD, Items.COD, Items.SALMON) : List.of(FOOD);
    }

    /** The kingdom's food in its stockpile ({@link #foods()}). */
    public static int food(Kingdom kingdom) {
        return foods().stream().mapToInt(item -> kingdom.resource(KingdomStore.id(item))).sum();
    }

    private static String foodName() {
        return Tuning.FISHING_FISH_IS_FOOD.get() == 1 ? "food (wheat or fish)" : "wheat";
    }

    /**
     * A starter building gets all its materials ({@code economy.free_starter_buildings}); a paid one gets everything
     * that isn't wooden ({@code economy.free_other_materials}): no job makes stone, bricks or glass yet.
     */
    private static void grantMaterials(MinecraftServer server, Kingdom kingdom, Slot slot, ServerBlueprintPlan plan) {
        boolean all = slot.starter() && Tuning.ECONOMY_FREE_STARTER_BUILDINGS.get() == 1;
        boolean other = !slot.starter() && Tuning.ECONOMY_FREE_OTHER_MATERIALS.get() == 1;
        if (!all && !other) {
            return;
        }
        int total = 0;
        for (ItemStack stack : plan.getNeededItems()) {
            if (all || !WoodCost.isWooden(server, stack.getItem())) {
                Kingdoms.addResource(server, kingdom, KingdomStore.id(stack.getItem()), stack.getCount());
                total += stack.getCount();
            }
        }
        int count = total;
        DebugLog.event(DebugCategory.ECONOMY, null, () -> "granted %s %d items for its %s (%s)".formatted(kingdom.team(), count, slot.structure(),
                all ? "economy.free_starter_buildings" : "economy.free_other_materials, everything not wooden"));
    }

    // Building

    private static void build(ServerLevel level, Kingdom kingdom, KingdomBuilding building) {
        if (!level.isLoaded(building.pos())) {
            return;
        }
        MinecraftServer server = level.getServer();
        String what = name(server, building.structure());
        if (!(level.getBlockEntity(building.pos()) instanceof EaselBlockEntity easel) || !(easel.getPlan() instanceof ServerBlueprintPlan plan)) {
            problem(kingdom, "the " + what.toLowerCase() + "'s easel at " + building.pos().toShortString() + " is gone or has no blueprint");
            return;
        }
        if (easel.isFinished()) {
            kingdom.replaceBuilding(building, building.withState(KingdomBuilding.State.BUILT));
            KingdomSavedData.get(server).setDirty();
            SUPPLIES.remove(kingdom.id());
            int renown = Tuning.ECONOMY_RENOWN_PER_BUILDING.get();
            Kingdoms.addRenown(server, kingdom, renown);
            Kingdoms.chronicle(server, kingdom, "The " + what.toLowerCase() + " of " + Kingdoms.teamName(server, kingdom.team())
                    + " is finished." + (renown > 0 ? " The kingdom's renown grows by " + renown + "." : ""));
            DebugLog.event(DebugCategory.ECONOMY, null, () -> "%s of %s finished at %s".formatted(what, kingdom.team(), building.pos().toShortString()));
            return;
        }
        PROBLEMS.remove(kingdom.id());
        BlockPos chest = KingdomKeep.chestPos(building.pos(), building.facing());
        MaterialSupply supply = SUPPLIES.get(kingdom.id());
        if (supply == null || supply.isFinished() || !supply.chestPos().equals(chest)) {
            Item scaffold = BuiltInRegistries.BLOCK.get(Tuning.SCAFFOLD_MATERIAL.get()).asItem();
            supply = new MaterialSupply(level, chest, KingdomStore.of(level, kingdom, scaffold == Items.AIR ? null : scaffold, plan::remainingItems, chest),
                    plan::remainingItems, () -> easel.isRemoved() || easel.isFinished());
            SUPPLIES.put(kingdom.id(), supply);
        }
        supply.restock();
        assign(level, kingdom, chest, Tuning.KINGDOM_KEEP_BUILDERS.get(), BuildBlueprintGoal.class, "builder of the " + what.toLowerCase());
        boolean paid = plan().stream().anyMatch(slot -> slot.structure().equals(building.structure()) && !slot.starter());
        if (paid) {
            // The wheat it paid for is the builders' meals.
            soldiers(level, kingdom).stream()
                    .filter(soldier -> chest.equals(soldier.getPoiPos()) && soldier.getWorkSelector().isDoing(BuildBlueprintGoal.class))
                    .forEach(soldier -> Meals.feed(soldier, kingdom));
        }
    }

    // Soldiers

    /** The kingdom's soldiers (not the king) in and around its claim. */
    private static List<AbstractClaySoldierEntity> soldiers(ServerLevel level, Kingdom kingdom) {
        int radius = kingdom.claimRadius() + Tuning.ECONOMY_PLOT_OUTER_REACH.get() + 16;
        BlockPos center = kingdom.center();
        AABB area = new AABB(center.getX() - radius, level.getMinBuildHeight(), center.getZ() - radius,
                center.getX() + radius + 1, level.getMaxBuildHeight(), center.getZ() + radius + 1);
        return level.getEntitiesOfClass(AbstractClaySoldierEntity.class, area,
                soldier -> soldier.isAlive() && kingdom.team().equals(soldier.getClayTeamType()) && !soldier.getUUID().equals(kingdom.king().id()));
    }

    /**
     * Builders of a finished building (keep included) go back to being idle once its scaffolding is down, so the next
     * building or a job can have them.
     */
    private static void releaseCrews(ServerLevel level, Kingdom kingdom) {
        List<BlockPos> done = new ArrayList<>();
        for (KingdomBuilding building : kingdom.buildings()) {
            if (building.state() == KingdomBuilding.State.BUILT && !Scaffolds.hasJob(level, building.pos())) {
                done.add(KingdomKeep.chestPos(building.pos(), building.facing()));
            }
        }
        if (done.isEmpty()) {
            return;
        }
        for (AbstractClaySoldierEntity soldier : soldiers(level, kingdom)) {
            if (soldier.getPoiPos() != null && done.contains(soldier.getPoiPos()) && soldier.getWorkSelector().isDoing(BuildBlueprintGoal.class)) {
                soldier.getWorkSelector().stopWork();
                soldier.setPoiPos(null);
                DebugLog.event(DebugCategory.ECONOMY, soldier, "%s: builder released, its building is finished", kingdom.team());
            }
        }
    }

    /** While nothing is being built, idle soldiers become woodcutters and farmers at the finished buildings with jobs. */
    private static void assignWorkers(ServerLevel level, Kingdom kingdom) {
        Map<Job, Integer> counts = new LinkedHashMap<>();
        for (Slot slot : plan()) {
            if (slot.job() == null) {
                continue;
            }
            for (KingdomBuilding building : kingdom.buildings()) {
                if (building.structure().equals(slot.structure()) && building.state() == KingdomBuilding.State.BUILT
                        && !Scaffolds.hasJob(level, building.pos())) {
                    BlockPos chest = KingdomKeep.chestPos(building.pos(), building.facing());
                    counts.merge(slot.job(), assign(level, kingdom, chest, slot.job().wanted(), slot.job().goal, slot.job().name), Integer::sum);
                }
            }
        }
        WORKERS.put(kingdom.id(), counts);
    }

    /**
     * Gives soldiers the job {@code goal} at the building whose chest is {@code chest} until it has {@code wanted}:
     * soldiers with no job and no POI that aren't the king, the ones that can already work (holding wheat) first, then the
     * nearest; the others are handed wheat (D93). Returns how many have the job there.
     */
    static int assign(ServerLevel level, Kingdom kingdom, BlockPos chest, int wanted, Class<? extends Goal> goal, String what) {
        List<AbstractClaySoldierEntity> soldiers = soldiers(level, kingdom);
        int crew = (int) soldiers.stream().filter(soldier -> chest.equals(soldier.getPoiPos()) && soldier.getWorkSelector().isDoing(goal)).count();
        if (crew >= wanted) {
            return crew;
        }
        List<AbstractClaySoldierEntity> idle = new ArrayList<>(soldiers.stream()
                .filter(soldier -> !soldier.getWorkSelector().isWorking() && soldier.getPoiPos() == null && !soldier.isPassenger()
                        && !Kingdoms.isKing(soldier) && kingdom.claims(level.dimension(), soldier.blockPosition()))
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
                    continue;
                }
            }
            if (soldier.getWorkSelector().selectWork(goal)) {
                soldier.setPoiPos(chest);
                crew++;
                int count = crew;
                DebugLog.event(DebugCategory.ECONOMY, soldier, () -> "%s made a %s (%d of %d)".formatted(kingdom.team(), what, count, wanted));
            }
        }
        return crew;
    }

    /**
     * The chests of finished buildings are the kingdom's storehouse: everything in them (without components) goes to the
     * stockpile.
     */
    private static void sweepChests(ServerLevel level, Kingdom kingdom) {
        MinecraftServer server = level.getServer();
        for (KingdomBuilding building : kingdom.buildings()) {
            if (building.state() != KingdomBuilding.State.BUILT) {
                continue;
            }
            BlockPos pos = KingdomKeep.chestPos(building.pos(), building.facing());
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
                continue;
            }
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                ItemStack stack = chest.getItem(slot);
                if (!stack.isEmpty() && stack.getComponentsPatch().isEmpty()) {
                    Kingdoms.addResource(server, kingdom, KingdomStore.id(stack.getItem()), stack.getCount());
                    chest.setItem(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    /** Puts what a worker brings into the kingdom's stockpile (it reached its building's chest). */
    public static void deliver(AbstractClaySoldierEntity soldier, Kingdom kingdom, ItemStack stack) {
        MinecraftServer server = soldier.getServer();
        if (server == null || stack.isEmpty()) {
            return;
        }
        Kingdoms.addResource(server, kingdom, KingdomStore.id(stack.getItem()), stack.getCount());
        DebugLog.event(DebugCategory.ECONOMY, soldier, "%s: delivered %s", kingdom.team(), stack);
    }

    // Workplaces

    /**
     * Where a worker works: the finished building of its kingdom whose chest is the soldier's POI.
     *
     * @param chest  the building's chest (deliveries)
     * @param bounds the building's structure, in the world
     */
    public record Workplace(Kingdom kingdom, KingdomBuilding building, BlockPos chest, BoundingBox bounds) {
    }

    /** The soldier's workplace, if its POI is the chest of a finished building of its team's active kingdom. */
    public static Optional<Workplace> workplace(AbstractClaySoldierEntity soldier) {
        MinecraftServer server = soldier.getServer();
        BlockPos poi = soldier.getPoiPos();
        if (server == null || poi == null || !(soldier.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        Optional<Kingdom> kingdom = Kingdoms.byTeam(server, soldier.getClayTeamType())
                .filter(found -> found.status() == KingdomStatus.ACTIVE && found.dimension().equals(level.dimension()));
        if (kingdom.isEmpty()) {
            return Optional.empty();
        }
        for (KingdomBuilding building : kingdom.get().buildings()) {
            if (building.state() == KingdomBuilding.State.BUILT && poi.equals(KingdomKeep.chestPos(building.pos(), building.facing()))) {
                Optional<BoundingBox> bounds = bounds(level, building);
                if (bounds.isPresent()) {
                    return Optional.of(new Workplace(kingdom.get(), building, poi, bounds.get()));
                }
            }
        }
        return Optional.empty();
    }

    /** Where a building's structure stands in the world (from its template's size), or empty when it isn't loaded. */
    public static Optional<BoundingBox> bounds(ServerLevel level, KingdomBuilding building) {
        return BOUNDS.computeIfAbsent(building, key -> {
            Optional<StructureMeta> meta = StructureRegistry.get(level.getServer()).byId(key.structure());
            if (meta.isEmpty()) {
                return Optional.empty();
            }
            Vec3i size;
            try {
                size = StructurePaster.template(level, meta.get(), key.level()).getSize();
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            Direction easelFacing = key.facing().getOpposite();
            BlueprintTemplateSettings settings = new BlueprintTemplateSettings(size, Mirror.NONE, EaselBlockEntity.rotationFor(easelFacing));
            BlockPos base = EaselBlockEntity.templateBase(key.pos(), easelFacing, settings);
            BlockPos a = base.offset(StructureTemplate.calculateRelativePosition(settings.convertTo(), BlockPos.ZERO));
            BlockPos b = base.offset(StructureTemplate.calculateRelativePosition(settings.convertTo(),
                    new BlockPos(size.getX() - 1, size.getY() - 1, size.getZ() - 1)));
            return Optional.of(BoundingBox.fromCorners(a, b));
        });
    }

    /** Whether {@code pos} is part of any of the kingdom's buildings (with a margin of {@code margin}). */
    public static boolean inBuilding(ServerLevel level, Kingdom kingdom, BlockPos pos, int margin) {
        for (KingdomBuilding building : kingdom.buildings()) {
            Optional<BoundingBox> bounds = bounds(level, building);
            if (bounds.isPresent() && bounds.get().inflatedBy(margin).isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    /** The ground a building takes: its structure plus its easel and chest. */
    static PlotPicker.Taken taken(ServerLevel level, KingdomBuilding building) {
        BlockPos easel = building.pos();
        int minX = easel.getX() - 3;
        int maxX = easel.getX() + 3;
        int minZ = easel.getZ() - 3;
        int maxZ = easel.getZ() + 3;
        Optional<BoundingBox> bounds = bounds(level, building);
        if (bounds.isPresent()) {
            minX = Math.min(minX, bounds.get().minX());
            maxX = Math.max(maxX, bounds.get().maxX());
            minZ = Math.min(minZ, bounds.get().minZ());
            maxZ = Math.max(maxZ, bounds.get().maxZ());
        }
        return new PlotPicker.Taken(minX, maxX, minZ, maxZ);
    }

    // Dev shortcuts (the Economy page of the dev book)

    /**
     * Finishes the building the kingdom is building at once (dev): its easel is taken away (and its scaffolding with it),
     * the structure is pasted where the builders would have built it, and it counts as built: a keep crowns the king.
     *
     * @return what was finished, or a reason why nothing was
     */
    public static String finishNow(ServerLevel level, Kingdom kingdom) {
        MinecraftServer server = level.getServer();
        Optional<KingdomBuilding> building = kingdom.buildings().stream().filter(b -> b.state() != KingdomBuilding.State.BUILT).findFirst();
        if (building.isEmpty()) {
            return "Nothing is being built.";
        }
        KingdomBuilding b = building.get();
        Optional<StructureMeta> meta = StructureRegistry.get(server).byId(b.structure());
        if (meta.isEmpty()) {
            return "The structure " + b.structure() + " isn't loaded.";
        }
        level.removeBlock(b.pos(), false);
        try {
            StructurePaster.paste(level, meta.get(), b.level(), b.pos(), b.facing());
        } catch (IllegalArgumentException e) {
            return "Couldn't paste " + b.structure() + ": " + e.getMessage();
        }
        kingdom.replaceBuilding(b, b.withState(KingdomBuilding.State.BUILT));
        KingdomSavedData.get(server).setDirty();
        SUPPLIES.remove(kingdom.id());
        KingdomKeep.forget(kingdom);
        String what = name(server, b.structure());
        boolean keep = KingdomKeep.keep(kingdom).map(first -> first.pos().equals(b.pos())).orElse(false);
        if (keep) {
            Kingdoms.chronicle(server, kingdom, "The keep of " + Kingdoms.teamName(server, kingdom.team()) + " is finished. "
                    + Kingdoms.kingTitle(server, kingdom, true) + " takes his throne.");
            KingdomKeep.placeThrone(level, kingdom, b.withState(KingdomBuilding.State.BUILT));
            KingdomKeep.crown(level, kingdom);
        } else {
            Kingdoms.addRenown(server, kingdom, Tuning.ECONOMY_RENOWN_PER_BUILDING.get());
            Kingdoms.chronicle(server, kingdom, "The " + what.toLowerCase() + " of " + Kingdoms.teamName(server, kingdom.team()) + " is finished.");
        }
        DebugLog.event(DebugCategory.ECONOMY, null, () -> "%s of %s finished at once (dev)".formatted(what, kingdom.team()));
        return "Finished the " + what.toLowerCase() + " at " + b.pos().toShortString() + ".";
    }

    /** Adds the logs and wheat the next paid building still waits for (dev); returns what was added. */
    public static String supplyNext(ServerLevel level, Kingdom kingdom) {
        Optional<Slot> next = plan().stream().filter(slot -> !slot.starter()
                && kingdom.buildings().stream().noneMatch(building -> building.structure().equals(slot.structure()))).findFirst();
        Optional<Need> need = next.flatMap(slot -> need(level, slot));
        if (need.isEmpty()) {
            return "No building the kingdom pays for is waiting.";
        }
        MinecraftServer server = level.getServer();
        int logs = Math.max(0, need.get().logs() - WoodCost.logsWorth(kingdom));
        int food = Math.max(0, need.get().food() - kingdom.resource(KingdomStore.id(FOOD)));
        if (logs > 0) {
            Kingdoms.addResource(server, kingdom, KingdomStore.id(Items.OAK_LOG), logs);
        }
        if (food > 0) {
            Kingdoms.addResource(server, kingdom, KingdomStore.id(FOOD), food);
        }
        return "Added " + logs + " oak logs and " + food + " wheat for the " + name(server, next.get().structure()).toLowerCase() + ".";
    }

    /** Makes every crop in the kingdom's finished fields ripe (dev); returns how many. */
    public static int ripen(ServerLevel level, Kingdom kingdom) {
        int count = 0;
        for (KingdomBuilding building : kingdom.buildings()) {
            Optional<BoundingBox> bounds = bounds(level, building);
            if (building.state() != KingdomBuilding.State.BUILT || bounds.isEmpty()
                    || plan().stream().noneMatch(slot -> slot.job() == Job.FARMER && slot.structure().equals(building.structure()))) {
                continue;
            }
            BoundingBox box = bounds.get();
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY() + 1, box.maxZ())) {
                net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock crop && !crop.isMaxAge(state)) {
                    level.setBlockAndUpdate(pos, crop.getStateForAge(crop.getMaxAge()));
                    count++;
                }
            }
        }
        return count;
    }

    // Status

    private static String name(MinecraftServer server, ResourceLocation structure) {
        return StructureRegistry.get(server).byId(structure).map(StructureMeta::name).orElse(structure.getPath());
    }

    /** Lines about the economy for the book and {@code /csr kingdom status}. */
    public static List<String> describe(MinecraftServer server, Kingdom kingdom) {
        List<String> lines = new ArrayList<>();
        Optional<KingdomBuilding> keep = KingdomKeep.keep(kingdom);
        if (keep.isEmpty() || keep.get().state() != KingdomBuilding.State.BUILT) {
            lines.add("Starts when the keep is finished.");
            return lines;
        }
        ServerLevel level = server.getLevel(kingdom.dimension());
        for (Slot slot : plan()) {
            Optional<KingdomBuilding> building = kingdom.buildings().stream().filter(b -> b.structure().equals(slot.structure())).findFirst();
            String state;
            if (building.isEmpty()) {
                state = "not started";
            } else if (building.get().state() == KingdomBuilding.State.BUILT) {
                state = "finished";
            } else {
                state = "being built";
                BlockPos pos = building.get().pos();
                if (level != null && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof EaselBlockEntity easel
                        && easel.getPlan() instanceof ServerBlueprintPlan plan) {
                    state += ", " + plan.remainingItems().size() + " blocks left";
                }
            }
            lines.add(name(server, slot.structure()) + ": " + state + ".");
        }
        Map<Job, Integer> workers = WORKERS.getOrDefault(kingdom.id(), Map.of());
        lines.add("Woodcutters: " + workers.getOrDefault(Job.WOODCUTTER, 0) + ", farmers: " + workers.getOrDefault(Job.FARMER, 0)
                + ", fishers: " + workers.getOrDefault(Job.FISHER, 0) + ".");
        String dockProblem = KingdomDock.problemFor(kingdom);
        if (dockProblem != null) {
            lines.add("Fishing dock: " + dockProblem + ".");
        }
        lines.addAll(KingdomLayout.describe(kingdom));
        lines.add("Logs: " + WoodCost.logs(kingdom) + ", spare planks: " + WoodCost.sparePlanks(kingdom) + ", wheat: " + kingdom.resource(KingdomStore.id(FOOD))
                + ", fish: " + (kingdom.resource(KingdomStore.id(Items.COD)) + kingdom.resource(KingdomStore.id(Items.SALMON))) + ".");
        Optional<Slot> next = next(kingdom);
        String problem = PROBLEMS.get(kingdom.id());
        if (problem != null) {
            lines.add("Waiting: " + problem + ".");
        } else if (next.isPresent() && level != null && !next.get().starter()) {
            need(level, next.get()).ifPresent(need -> lines.add("Next: the " + name(server, next.get().structure()).toLowerCase() + ", for "
                    + need.logs() + " logs and " + need.food() + " " + foodName() + "."));
        }
        return lines;
    }

    /** Records why the economy can't go on, for the book; returns false. */
    private static boolean problem(Kingdom kingdom, String why) {
        if (!why.equals(PROBLEMS.put(kingdom.id(), why))) {
            DebugLog.event(DebugCategory.ECONOMY, null, () -> "economy of %s waiting: %s".formatted(kingdom.team(), why));
        }
        return false;
    }

    /** Forgets everything kept in memory about the kingdom (disbanding, GameTests). */
    public static void forget(Kingdom kingdom) {
        SUPPLIES.remove(kingdom.id());
        NEXT_PLAN.remove(kingdom.id());
        PROBLEMS.remove(kingdom.id());
        WORKERS.remove(kingdom.id());
        KingdomLayout.forget(kingdom);
        KingdomDock.forget(kingdom);
    }
}
