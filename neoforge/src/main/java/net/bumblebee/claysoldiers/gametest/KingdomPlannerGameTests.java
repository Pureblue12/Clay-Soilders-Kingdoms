package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomLayout;
import net.bumblebee.claysoldiers.kingdom.KingdomSavedData;
import net.bumblebee.claysoldiers.kingdom.layout.Cell;
import net.bumblebee.claysoldiers.kingdom.layout.KingdomPlanner;
import net.bumblebee.claysoldiers.kingdom.layout.MasterPlan;
import net.bumblebee.claysoldiers.kingdom.layout.Rect;
import net.bumblebee.claysoldiers.kingdom.layout.Side;
import net.bumblebee.claysoldiers.terrain.TerrainScan;
import net.bumblebee.claysoldiers.terrain.TerrainScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * The kingdom planner (stage 6): its cases on made-up land ({@link KingdomPlannerCases}: flat land and every front, a
 * cave under the best spot, a river, rough land, land not loaded, cramped land), the plan surviving a save, the land scan
 * finding a cave and the kingdom filling it, roads laid as dirt paths, and the roster of buildings still to come. Each
 * asserts the CORRECT behavior. The whole story in a world is {@link KingdomTownGameTests}.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class KingdomPlannerGameTests {
    @GameTest(template = PLATFORM)
    public static void flatPlanIsSound(GameTestHelper helper) {
        check(helper, KingdomPlannerCases.flatPlanIsSound());
    }

    @GameTest(template = PLATFORM)
    public static void caveIsAvoided(GameTestHelper helper) {
        check(helper, KingdomPlannerCases.caveIsAvoided());
    }

    @GameTest(template = PLATFORM)
    public static void riverGetsAFishingPlot(GameTestHelper helper) {
        check(helper, KingdomPlannerCases.riverGetsAFishingPlot());
    }

    @GameTest(template = PLATFORM)
    public static void noWaterNoFishingPlot(GameTestHelper helper) {
        check(helper, KingdomPlannerCases.noWaterNoFishingPlot());
    }

    @GameTest(template = PLATFORM)
    public static void roughLandIsRespected(GameTestHelper helper) {
        check(helper, KingdomPlannerCases.roughLandIsRespected());
    }

    @GameTest(template = PLATFORM)
    public static void unknownLandIsLeftAlone(GameTestHelper helper) {
        check(helper, KingdomPlannerCases.unknownLandIsLeftAlone());
    }

    @GameTest(template = PLATFORM)
    public static void crampedLandLeavesOutWhatDoesntFit(GameTestHelper helper) {
        check(helper, KingdomPlannerCases.crampedLandLeavesOutWhatDoesntFit());
    }

    @GameTest(template = PLATFORM)
    public static void fullTownFitsEveryBuilding(GameTestHelper helper) {
        check(helper, KingdomPlannerCases.fullTownFitsEveryBuilding());
    }

    private static void check(GameTestHelper helper, @Nullable String problem) {
        helper.assertTrue(problem == null, String.valueOf(problem));
        helper.succeed();
    }

    /**
     * The buildings the plan keeps plots for (D111): the registry's inner and outer buildings, not the kingdom's own list,
     * not street decoration or test copies; outer ones marked outer, every one at its top level's size.
     */
    @GameTest(template = PLATFORM)
    public static void rosterHoldsTheBuildingsStillToCome(GameTestHelper helper) {
        ResourceLocation keep = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "keep");
        Map<String, KingdomPlanner.Item> roster = new LinkedHashMap<>();
        KingdomLayout.roster(helper.getLevel(), keep).forEach(item -> roster.put(item.key(), item));
        for (String id : List.of("csr:church", "csr:town_hall", "csr:market", "csr:inn")) {
            helper.assertTrue(roster.containsKey(id) && !roster.get(id).outer(), id + " should be kept a plot inside the wall: " + roster.keySet());
        }
        for (String id : List.of("csr:quarry", "csr:mine", "csr:farm_field_carrots")) {
            helper.assertTrue(roster.containsKey(id) && roster.get(id).outer(), id + " should be kept a plot outside the wall: " + roster.keySet());
        }
        for (String id : List.of("csr:keep", "csr:cottage", "csr:lumber_camp", "csr:farm_field_wheat", "csr:fishing_dock", "csr:bench",
                "csr:lamp_post", "csr:road", "csr:stone_wall", "csr:batch1_cottage", "csr:test_hut")) {
            helper.assertFalse(roster.containsKey(id), id + " shouldn't be in the roster: " + roster.keySet());
        }
        // The church at its top level: 17 wide and 24 deep (its easel rows add 4 more along its depth).
        KingdomPlanner.Shape church = roster.get("csr:church").shapes().get(0);
        int across = Math.min(church.maxX() - church.minX(), church.maxZ() - church.minZ()) + 1;
        helper.assertTrue(across >= 17, "The church's plot is sized from a smaller level: " + church);
        helper.succeed();
    }

    /** A kingdom's plan is saved with it (kingdoms data version 3) and reads back the same. */
    @GameTest(template = PLATFORM)
    public static void planSurvivesSaving(GameTestHelper helper) {
        KingdomPlanner.Result result = KingdomPlannerCases.plan(new KingdomPlannerCases.Land(), Side.EAST);
        helper.assertTrue(result.plan().isPresent(), "No plan: " + result.notes());
        MasterPlan plan = result.plan().get();
        Kingdom base = KingdomDataGameTests.fullKingdom();
        Kingdom kingdom = new Kingdom(base.id(), base.team(), base.dimension(), base.center(), base.claimRadius(), base.king(), base.status(),
                base.tier(), base.stockpile(), base.buildings(), base.census(), base.renown(), base.foundedTick(), base.chronicle(), Optional.of(plan));
        Tag encoded = KingdomSavedData.encode(kingdom);
        Kingdom decoded = KingdomSavedData.decode(encoded);
        helper.assertTrue(decoded.plan().equals(Optional.of(plan)), "The plan changed when saved: " + decoded.plan().map(MasterPlan::summary));
        helper.assertTrue(decoded.equals(kingdom), "The kingdom changed when saved");
        // A kingdom saved before stage 6 has no plan.
        helper.assertTrue(KingdomSavedData.decode(KingdomSavedData.encode(base)).plan().isEmpty(), "A kingdom without a plan read back with one");
        helper.succeed();
    }

    /**
     * A stone pad 4 high with a 3 by 3 pocket of air 2 under its top: the land scan marks the pocket's columns hollow (2
     * down) and the solid ones not; filling the plot over it ({@link KingdomLayout#sealHollows}) leaves no hollow.
     */
    @GameTest(template = PLATFORM, skyAccess = true)
    public static void scanFindsACaveAndLevellingFillsIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 4; x <= 10; x++) {
            for (int z = 4; z <= 10; z++) {
                for (int y = 2; y <= 5; y++) {
                    boolean pocket = y == 3 && x >= 6 && x <= 8 && z >= 6 && z <= 8;
                    level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)), pocket ? Blocks.AIR.defaultBlockState() : Blocks.STONE.defaultBlockState());
                }
            }
        }
        BlockPos center = helper.absolutePos(new BlockPos(7, 5, 7));
        BlockPos corner = helper.absolutePos(new BlockPos(4, 5, 4));
        TerrainScan scan = TerrainScanner.scan(level, center, 4);
        helper.assertTrue(scan.ground(center.getX(), center.getZ()) == center.getY(), "Ground over the pocket: " + scan.ground(center.getX(), center.getZ()));
        helper.assertTrue(scan.hollow(center.getX(), center.getZ()) == 2, "Over the pocket the scan should see a hollow 2 down, saw " + scan.hollow(center.getX(), center.getZ()));
        helper.assertTrue(scan.hollow(corner.getX(), corner.getZ()) == 0, "Solid stone seen as hollow: " + scan.hollow(corner.getX(), corner.getZ()));

        BlockPos a = helper.absolutePos(new BlockPos(5, 5, 5));
        BlockPos b = helper.absolutePos(new BlockPos(9, 5, 9));
        Rect plot = new Rect(Math.min(a.getX(), b.getX()), Math.max(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getZ(), b.getZ()));
        int filled = KingdomLayout.sealHollows(level, plot, center.getY(), 4);
        helper.assertTrue(filled == 9, "Filled " + filled + " blocks, the pocket has 9");
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(7, 3, 7))).is(Blocks.DIRT), "The pocket wasn't filled with dirt");
        TerrainScan again = TerrainScanner.scan(level, center, 4);
        helper.assertTrue(again.hollow(center.getX(), center.getZ()) == 0, "Still hollow after filling: " + again.hollow(center.getX(), center.getZ()));
        helper.succeed();
    }

    /**
     * A road over grass becomes dirt path: a flower on it is cleared, a tree trunk on it is cut (its logs go to the
     * stockpile), and stone isn't earth, so it stays.
     */
    @GameTest(template = PLATFORM, skyAccess = true)
    public static void roadsAreLaidAsDirtPaths(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 3; x <= 10; x++) {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 1, 8)), Blocks.GRASS_BLOCK.defaultBlockState());
        }
        level.setBlockAndUpdate(helper.absolutePos(new BlockPos(5, 2, 8)), Blocks.POPPY.defaultBlockState());
        level.setBlockAndUpdate(helper.absolutePos(new BlockPos(7, 2, 8)), Blocks.OAK_LOG.defaultBlockState());
        level.setBlockAndUpdate(helper.absolutePos(new BlockPos(7, 3, 8)), Blocks.OAK_LOG.defaultBlockState());
        BlockPos from = helper.absolutePos(new BlockPos(3, 1, 8));
        BlockPos to = helper.absolutePos(new BlockPos(12, 1, 8));
        Cell start = new Cell(from.getX(), from.getZ());
        Cell end = new Cell(to.getX(), to.getZ());
        Rect nowhere = new Rect(start.x() - 40, start.x() - 30, start.z() - 40, start.z() - 30);
        MasterPlan plan = new MasterPlan(start, nowhere, Side.SOUTH, List.of(), List.of(new MasterPlan.Road(start, end, 1)),
                List.of(new Cell(nowhere.minX(), nowhere.minZ()), new Cell(nowhere.maxX(), nowhere.minZ()), new Cell(nowhere.maxX(), nowhere.maxZ()),
                        new Cell(nowhere.minX(), nowhere.maxZ())), List.of(), 20);
        KingdomLayout.Roads roads = KingdomLayout.layRoads(level, plan);
        helper.assertTrue(roads.laid() == 8 && roads.cleared() == 3 && roads.skipped() == 2,
                "Expected 8 laid, 3 cleared (a poppy and 2 logs), 2 skipped (stone): " + roads);
        helper.assertTrue(roads.logs().equals(Map.of(Items.OAK_LOG, 2)), "Logs: " + roads.logs());
        for (int x = 3; x <= 10; x++) {
            BlockPos pos = helper.absolutePos(new BlockPos(x, 1, 8));
            helper.assertTrue(level.getBlockState(pos).is(Blocks.DIRT_PATH), "No dirt path at " + x + ": " + level.getBlockState(pos));
        }
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(11, 1, 8))).is(Blocks.SMOOTH_STONE), "Stone was changed");
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(7, 2, 8))).isAir() && level.getBlockState(helper.absolutePos(new BlockPos(5, 2, 8))).isAir(),
                "The road wasn't cleared");
        helper.succeed();
    }

    /**
     * A road over a stream gets a plank bridge when the stream is short enough, with land at both ends; a road running
     * out into open water gets none (D114).
     */
    @GameTest(template = PLATFORM, skyAccess = true)
    public static void roadsBridgeAStreamButNotTheSea(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // A stream (x 6..8) across a road along z 8, and water from x 6 to the end of a road along z 12.
        for (int x = 2; x <= 14; x++) {
            for (int z : new int[]{7, 9, 11, 13}) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 2, z)), Blocks.STONE.defaultBlockState());
            }
            boolean stream = x >= 6 && x <= 8;
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 2, 8)), x == 14 ? Blocks.STONE.defaultBlockState()
                    : stream ? Blocks.WATER.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, 2, 12)), x == 14 ? Blocks.STONE.defaultBlockState()
                    : x >= 6 ? Blocks.WATER.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState());
        }
        Cell[] ends = new Cell[4];
        int[][] at = {{2, 8}, {13, 8}, {2, 12}, {13, 12}};
        for (int i = 0; i < 4; i++) {
            BlockPos pos = helper.absolutePos(new BlockPos(at[i][0], 2, at[i][1]));
            ends[i] = new Cell(pos.getX(), pos.getZ());
        }
        Rect nowhere = new Rect(ends[0].x() - 40, ends[0].x() - 30, ends[0].z() - 40, ends[0].z() - 30);
        MasterPlan plan = new MasterPlan(ends[0], nowhere, Side.SOUTH, List.of(),
                List.of(new MasterPlan.Road(ends[0], ends[1], 1), new MasterPlan.Road(ends[2], ends[3], 1)),
                List.of(new Cell(nowhere.minX(), nowhere.minZ()), new Cell(nowhere.maxX(), nowhere.minZ()), new Cell(nowhere.maxX(), nowhere.maxZ()),
                        new Cell(nowhere.minX(), nowhere.maxZ())), List.of(), 20);

        List<KingdomLayout.Bridge> tooShort = KingdomLayout.bridgeRoads(level, plan, 2);
        helper.assertTrue(tooShort.isEmpty(), "A 3-long stream was bridged with a longest bridge of 2: " + tooShort);
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(7, 2, 8))).is(Blocks.WATER), "The stream was changed");

        List<KingdomLayout.Bridge> bridges = KingdomLayout.bridgeRoads(level, plan, 8);
        helper.assertTrue(bridges.size() == 1 && bridges.get(0).length() == 3 && bridges.get(0).planks() == 3,
                "Expected one bridge 3 long with 3 planks (none toward the open water): " + bridges);
        for (int x = 6; x <= 8; x++) {
            BlockPos pos = helper.absolutePos(new BlockPos(x, 2, 8));
            helper.assertTrue(level.getBlockState(pos).is(Blocks.SPRUCE_PLANKS), "No plank at " + x + ": " + level.getBlockState(pos));
        }
        for (int x = 6; x <= 13; x++) {
            helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(x, 2, 12))).is(Blocks.WATER), "The open water got a bridge at " + x);
        }
        helper.succeed();
    }
}
