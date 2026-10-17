package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.BlueprintTemplateSettings;
import net.bumblebee.claysoldiers.blueprint.site.BuildSitePlanner;
import net.bumblebee.claysoldiers.blueprint.site.SiteCheck;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.block.blueprint.EaselBlockEntity;
import net.bumblebee.claysoldiers.entity.goal.workgoal.FishGoal;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KeepSite;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomBuilding;
import net.bumblebee.claysoldiers.kingdom.KingdomDock;
import net.bumblebee.claysoldiers.kingdom.KingdomEconomy;
import net.bumblebee.claysoldiers.kingdom.KingdomKeep;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.bumblebee.claysoldiers.kingdom.layout.Cell;
import net.bumblebee.claysoldiers.kingdom.layout.DockSite;
import net.bumblebee.claysoldiers.kingdom.layout.Lake;
import net.bumblebee.claysoldiers.structure.StructureBlueprints;
import net.bumblebee.claysoldiers.structure.StructureMeta;
import net.bumblebee.claysoldiers.structure.StructureRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;
import static net.bumblebee.claysoldiers.gametest.KingdomGameTests.RED;
import static net.bumblebee.claysoldiers.gametest.TerrainScanGameTests.CENTER;
import static net.bumblebee.claysoldiers.gametest.TerrainScanGameTests.RADIUS;
import static net.bumblebee.claysoldiers.gametest.TerrainScanGameTests.TERRAIN;

/**
 * The fishing dock ({@link KingdomDock}, the owner's ask 2026-10-17): the lake check tells a lake from a puddle, the dock
 * goes with its house on the bank and its jetty over the lake, its pilings reach the lake bed, the site check accepts
 * water under the jetty, and a fisher brings fish to the stockpile, where they count as food. The stand-in dock
 * {@code csr:test_dock} is a 5 by 4 hut (rows 0..3) and a 3-wide jetty (rows 4..11, logs at rows 6, 9 and 11).
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class FishingDockGameTests {
    private static final ResourceLocation DOCK = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "test_dock");
    private static final int DOCK_LAND_ROWS = 4;
    private static final List<Direction> DIRECTIONS = List.of(Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.EAST);

    @AfterBatch(batch = "dock_fisher")
    public static void restoreTuning(ServerLevel level) {
        GameTestTuning.restore(level.getServer());
    }

    /** The lake check: a 3 by 3 pond is a puddle, a 12 by 12 lake is a lake, a stream of flowing water counts nothing. */
    @GameTest(template = PLATFORM)
    public static void lakeCheckTellsALakeFromAPuddle(GameTestHelper helper) {
        helper.assertTrue(!Lake.measure(water(0, 2, 0, 2, true), 1, 1, 4000).isLake(120), "A 3 x 3 pond counted as a lake");
        helper.assertTrue(Lake.measure(water(0, 2, 0, 2, true), 1, 1, 4000).size() == 9, "A 3 x 3 pond has 9 still water columns");
        helper.assertTrue(Lake.measure(water(0, 11, 0, 11, true), 3, 3, 4000).isLake(120), "A 12 x 12 lake wasn't a lake");
        helper.assertTrue(Lake.measure(water(0, 0, 0, 200, false), 0, 5, 4000).size() == 0, "Flowing water counted toward a lake");
        helper.assertTrue(Lake.measure(water(0, 11, 0, 11, true), 20, 20, 4000).size() == 0, "Dry land measured as water");
        helper.succeed();
    }

    /**
     * On a bank by a lake (east of the center, 3 deep) the dock goes east: its house on the bank, its jetty over the lake;
     * getting the site ready drives a piling under each of the jetty's 6 logs down to the bed, and the site check accepts
     * the water under the jetty only for a dock.
     */
    @GameTest(template = TERRAIN, batch = "dock_lake", timeoutTicks = 200)
    public static void dockGoesOutOverTheLake(GameTestHelper helper) {
        BlockPos center = land(helper);
        int surface = center.getY();
        water(helper, center, 1, RADIUS - 1, -RADIUS + 1, RADIUS - 1, 3);
        ServerBlueprintPlan plan = plan(helper);
        DockSite.Rules rules = rules();
        DockSite.Result result = DockSite.search(KingdomDock.land(helper.getLevel()), center.getX(), center.getZ(),
                KingdomDock.footprints(DOCK, plan, DIRECTIONS), cell -> false, rules);
        helper.assertTrue(result.best().isPresent(), "No dock site by the lake: " + result.summary(rules));
        DockSite.Choice choice = result.best().get();
        helper.assertTrue(DIRECTIONS.get(choice.index()) == Direction.EAST, "The dock should go east, out over the lake: " + result.summary(rules));
        helper.assertTrue(choice.surface() == surface, "Water surface " + choice.surface() + ", expected " + surface);
        DockSite.Footprint footprint = KingdomDock.footprints(DOCK, plan, List.of(Direction.EAST)).getFirst();
        for (Cell rel : footprint.water()) {
            helper.assertTrue(choice.easelX() + rel.x() - center.getX() >= 1, "A jetty column is on the bank: " + rel);
        }
        for (Cell rel : footprint.land()) {
            helper.assertTrue(choice.easelX() + rel.x() - center.getX() <= 0, "A house column is in the lake: " + rel);
        }

        Kingdom kingdom = found(helper, center.offset(-6, 1, 6));
        ServerLevel level = helper.getLevel();
        KeepSite.Choice site = choice(choice, plan);
        KingdomDock.Prepared prepared = KingdomDock.prepare(level, kingdom, DOCK, site, plan);
        helper.assertTrue(prepared.pilings() == 18, "Piling blocks: " + prepared.pilings() + ", expected 6 logs x 3 deep");
        int logsAtDeck = 0;
        for (Map.Entry<BlockPos, BlockState> target : targets(plan, site).entrySet()) {
            if (target.getKey().getY() == surface + 1 && target.getValue().is(Blocks.OAK_LOG)) {
                logsAtDeck++;
                for (int y = surface; y > surface - 3; y--) {
                    BlockPos below = new BlockPos(target.getKey().getX(), y, target.getKey().getZ());
                    helper.assertTrue(level.getBlockState(below).is(Blocks.OAK_LOG), "No piling at " + below.toShortString() + ": " + level.getBlockState(below));
                }
            }
        }
        helper.assertTrue(logsAtDeck == 6, "Logs at the deck: " + logsAtDeck);

        BlueprintData blueprint = blueprint(helper);
        BlockPos easel = new BlockPos(site.easelX(), surface + 1, site.easelZ());
        SiteCheck overWater = BuildSitePlanner.check(level, blueprint, DOCK, easel, Direction.EAST, true);
        helper.assertTrue(overWater.isValid(), "The dock's site is refused: " + overWater.message());
        SiteCheck onGround = BuildSitePlanner.check(level, blueprint, DOCK, easel, Direction.EAST, false);
        helper.assertTrue(!onGround.isValid() && onGround.reason() == SiteCheck.Reason.NO_GROUND,
                "Without the dock rule the water under the jetty should be no ground: " + onGround.message());
        KingdomGameTests.clear(helper);
        helper.succeed();
    }

    /** A long narrow pond (3 x 13, 39 still water columns) fits the jetty but isn't a lake: no dock, and it says why. */
    @GameTest(template = TERRAIN, batch = "dock_puddle", timeoutTicks = 200)
    public static void noDockOnAPond(GameTestHelper helper) {
        BlockPos center = land(helper);
        water(helper, center, 1, RADIUS - 1, -1, 1, 3);
        ServerBlueprintPlan plan = plan(helper);
        DockSite.Rules rules = rules();
        DockSite.Result result = DockSite.search(KingdomDock.land(helper.getLevel()), center.getX(), center.getZ(),
                KingdomDock.footprints(DOCK, plan, DIRECTIONS), cell -> false, rules);
        helper.assertTrue(result.best().isEmpty(), "A dock on a pond: " + result.summary(rules));
        helper.assertTrue(result.puddles() > 0 && result.summary(rules).contains("puddle"), "The pond wasn't judged a puddle: " + result.summary(rules));
        helper.succeed();
    }

    /**
     * A fisher on a finished dock walks out onto the jetty, catches fish and brings them to the dock's chest: they reach
     * the stockpile and count as food.
     */
    @GameTest(template = TERRAIN, batch = "dock_fisher", timeoutTicks = 2400)
    public static void fisherBringsFishToTheStockpile(GameTestHelper helper) {
        GameTestTuning.load("{\"fishing.wait_min_ticks\": 20, \"fishing.wait_max_ticks\": 40, \"fishing.carry\": 2}");
        BlockPos center = land(helper);
        int surface = center.getY();
        water(helper, center, 1, RADIUS - 1, -RADIUS + 1, RADIUS - 1, 3);
        ServerBlueprintPlan plan = plan(helper);
        DockSite.Rules rules = rules();
        DockSite.Result result = DockSite.search(KingdomDock.land(helper.getLevel()), center.getX(), center.getZ(),
                KingdomDock.footprints(DOCK, plan, DIRECTIONS), cell -> false, rules);
        helper.assertTrue(result.best().isPresent(), "Setup: no dock site: " + result.summary(rules));
        ServerLevel level = helper.getLevel();
        Kingdom kingdom = found(helper, center.offset(-6, 1, 6));
        KeepSite.Choice site = choice(result.best().get(), plan);
        KingdomDock.prepare(level, kingdom, DOCK, site, plan);
        targets(plan, site).forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
        Direction facing = site.area().facing();
        BlockPos easel = new BlockPos(site.easelX(), surface + 1, site.easelZ());
        BlockPos chest = KingdomKeep.chestPos(easel, facing);
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        kingdom.addBuilding(new KingdomBuilding(DOCK, easel, facing, KingdomBuilding.State.BUILT));

        BlockPos start = relative(helper, easel.relative(facing.getOpposite(), 2));
        ClaySoldierEntity fisher = KingdomGameTests.DevSoldier.spawnPlain(helper, start, RED);
        fisher.equipItemIfPossible(new ItemStack(Items.WHEAT));
        helper.assertTrue(fisher.getWorkSelector().selectWork(FishGoal.class), "Setup: no fisher job");
        fisher.setPoiPos(chest);
        ResourceLocation cod = BuiltInRegistries.ITEM.getKey(Items.COD);
        ResourceLocation salmon = BuiltInRegistries.ITEM.getKey(Items.SALMON);
        helper.succeedWhen(() -> {
            int fish = kingdom.resource(cod) + kingdom.resource(salmon);
            helper.assertTrue(fish >= 2, "Fish in the stockpile: " + fish + " of 2");
            helper.assertTrue(KingdomEconomy.food(kingdom) >= fish, "Fish don't count as food: " + KingdomEconomy.food(kingdom));
            KingdomGameTests.clear(helper);
        });
    }

    // Helpers

    /** Water map for the pure check: water with surface 0 in the rectangle, still or flowing; dry land elsewhere. */
    private static Lake.Water water(int minX, int maxX, int minZ, int maxZ, boolean still) {
        return new Lake.Water() {
            private boolean in(int x, int z) {
                return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
            }

            @Override
            public boolean known(int x, int z) {
                return Math.abs(x) < 500 && Math.abs(z) < 500;
            }

            @Override
            public int surface(int x, int z) {
                return in(x, z) ? 0 : Lake.NONE;
            }

            @Override
            public int depth(int x, int z) {
                return in(x, z) ? 3 : 0;
            }

            @Override
            public boolean still(int x, int z) {
                return in(x, z) && still;
            }
        };
    }

    /** Flat land around the test area's center: dirt, grass on top at the center's height, air above. */
    private static BlockPos land(GameTestHelper helper) {
        KingdomDock.useDockForTests(DOCK, DOCK_LAND_ROWS);
        BlockPos center = helper.absolutePos(CENTER);
        ServerLevel level = helper.getLevel();
        int base = center.getY();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int y = base - 6; y <= base + 6; y++) {
                    BlockState state = y < base ? Blocks.DIRT.defaultBlockState() : y == base ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    level.setBlock(new BlockPos(center.getX() + dx, y, center.getZ() + dz), state, Block.UPDATE_CLIENTS);
                }
            }
        }
        return center;
    }

    /** Still water {@code depth} deep (its top level with the land) over the columns dx, dz from the center. */
    private static void water(GameTestHelper helper, BlockPos center, int minDx, int maxDx, int minDz, int maxDz, int depth) {
        ServerLevel level = helper.getLevel();
        for (int dx = minDx; dx <= maxDx; dx++) {
            for (int dz = minDz; dz <= maxDz; dz++) {
                for (int y = center.getY() - depth + 1; y <= center.getY(); y++) {
                    level.setBlock(new BlockPos(center.getX() + dx, y, center.getZ() + dz), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static BlueprintData blueprint(GameTestHelper helper) {
        StructureMeta meta = StructureRegistry.get(helper.getLevel().getServer()).byId(DOCK).orElseThrow();
        return StructureBlueprints.blueprint(helper.getLevel(), meta, 1);
    }

    private static ServerBlueprintPlan plan(GameTestHelper helper) {
        Optional<ServerBlueprintPlan> plan = blueprint(helper).createServerPlan();
        helper.assertTrue(plan.isPresent(), "Setup: the test dock isn't loaded");
        return plan.get();
    }

    private static KeepSite.Choice choice(DockSite.Choice choice, ServerBlueprintPlan plan) {
        return new KeepSite.Choice(KingdomKeep.areaForTests(plan, DIRECTIONS.get(choice.index())), choice.easelX(), choice.easelZ(),
                choice.surface(), choice.earth(), choice.earth(), 0, choice.distance(), 0);
    }

    /** The dock's blocks in the world for a site. */
    private static Map<BlockPos, BlockState> targets(ServerBlueprintPlan plan, KeepSite.Choice site) {
        Direction easelFacing = site.area().facing().getOpposite();
        BlueprintTemplateSettings settings = new BlueprintTemplateSettings(plan.getSize(), Mirror.NONE, EaselBlockEntity.rotationFor(easelFacing));
        BlockPos easel = new BlockPos(site.easelX(), site.groundY() + 1, site.easelZ());
        return plan.targetStates(EaselBlockEntity.templateBase(easel, easelFacing, settings), settings);
    }

    private static Kingdom found(GameTestHelper helper, BlockPos kingAt) {
        KingdomGameTests.clear(helper);
        ServerLevel level = helper.getLevel();
        FakePlayer player = KingdomGameTests.player(helper);
        ClaySoldierEntity king = KingdomGameTests.spawnKing(helper, relative(helper, kingAt), RED, player);
        helper.assertTrue(Kingdoms.found(level, king, player, true).ok(), "Setup: founding failed");
        Kingdom kingdom = Kingdoms.byTeam(level.getServer(), RED).orElseThrow();
        KingdomKeep.forget(kingdom);
        KingdomEconomy.forget(kingdom);
        return kingdom;
    }

    /** A position relative to the test area (not {@code helper.relativePos}: wrong rotation in 1.21.1). */
    private static BlockPos relative(GameTestHelper helper, BlockPos absolute) {
        return absolute.subtract(helper.absolutePos(BlockPos.ZERO));
    }

    /** The search's rules for the test area: every column a candidate, within the land built. */
    private static DockSite.Rules rules() {
        DockSite.Rules rules = KingdomDock.rules(RADIUS - 1);
        return new DockSite.Rules(rules.minLake(), rules.lakeLimit(), rules.minDepth(), rules.maxDepth(), rules.maxRange(), rules.lavaReach(),
                rules.radius(), 1);
    }
}
