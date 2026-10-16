package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.entity.goal.workgoal.BuildBlueprintGoal;
import net.bumblebee.claysoldiers.entity.goal.workgoal.FellTreesGoal;
import net.bumblebee.claysoldiers.entity.goal.workgoal.TendFieldGoal;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KeepSite;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomBuilding;
import net.bumblebee.claysoldiers.kingdom.KingdomEconomy;
import net.bumblebee.claysoldiers.kingdom.KingdomKeep;
import net.bumblebee.claysoldiers.kingdom.Meals;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.bumblebee.claysoldiers.kingdom.PlotPicker;
import net.bumblebee.claysoldiers.kingdom.WoodCost;
import net.bumblebee.claysoldiers.terrain.TerrainScan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.OptionalDouble;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.GROUND_Y;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;
import static net.bumblebee.claysoldiers.gametest.KingdomGameTests.RED;

/**
 * Stage 5, the first economy ({@link KingdomEconomy}). The buildings are records on the kingdom (the test hut stands in
 * for the lumber camp and the field: a worker's area is its building's structure, its chest the one beside the easel),
 * so the tests run on the 16 by 16 platform: the carpenter's costs (wood made from logs of any kind), the plot picker,
 * a woodcutter felling and replanting a tree, a farmer tilling, sowing, harvesting and replanting, builders let go when
 * their building is finished and made workers, the cottage waiting for wood and food, and renown for a finished building.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class KingdomEconomyGameTests {
    private static final ResourceLocation HUT = KingdomKeepGameTests.HUT;
    private static final BlockPos KING_AT = new BlockPos(13, GROUND_Y, 13);
    /** The hut goes south of the easel: x 6..10, z 6..10; the chest is at (6, 2, 3). */
    private static final BlockPos EASEL = new BlockPos(8, GROUND_Y, 4);

    /** Wooden blocks cost what vanilla's recipes say, in planks; anything not made of wood alone has no cost. */
    @GameTest(template = PLATFORM, batch = "economy_wood")
    public static void woodenBlocksAreMadeFromLogsOfAnyKind(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        assertCost(helper, server, Items.OAK_LOG, 4);
        assertCost(helper, server, Items.SPRUCE_PLANKS, 1);
        assertCost(helper, server, Items.OAK_STAIRS, 1.5);
        assertCost(helper, server, Items.CHEST, 8);
        assertCost(helper, server, Items.CRAFTING_TABLE, 4);
        for (Item notWood : List.of(Items.COBBLESTONE, Items.LANTERN, Items.GLASS_PANE, Items.RED_BED)) {
            helper.assertTrue(WoodCost.planks(server, notWood).isEmpty(), notWood + " isn't made of wood alone, got " + WoodCost.planks(server, notWood));
        }

        Kingdom kingdom = found(helper);
        Kingdoms.addResource(server, kingdom, BuiltInRegistries.ITEM.getKey(Items.BIRCH_LOG), 3);
        int made = WoodCost.make(server, kingdom, Items.SPRUCE_STAIRS, 64);
        helper.assertTrue(made == 8, "3 birch logs (12 planks) should make 8 spruce stairs, made " + made);
        helper.assertTrue(WoodCost.logs(kingdom) == 0, "All 3 logs should be used, left " + WoodCost.logs(kingdom));
        helper.assertTrue(WoodCost.make(server, kingdom, Items.SPRUCE_STAIRS, 64) == 0, "Nothing should be made without logs");
        KingdomGameTests.clear(helper);
        helper.succeed();
    }

    /**
     * Spare planks are wood too (found by the whole-story test): the carpenter cut 8 logs into 32 planks for a hut's floor,
     * the builders brought back 7, and the hut's door then waited forever for logs the kingdom no longer had.
     */
    @GameTest(template = PLATFORM, batch = "economy_spare_planks")
    public static void sparePlanksAreUsedBeforeLogs(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = found(helper);
        Kingdoms.addResource(server, kingdom, BuiltInRegistries.ITEM.getKey(Items.OAK_PLANKS), 7);
        int doors = WoodCost.make(server, kingdom, Items.SPRUCE_DOOR, 1);
        helper.assertTrue(doors == 1, "7 spare oak planks should make a spruce door, made " + doors);
        helper.assertTrue(WoodCost.sparePlanks(kingdom) == 5, "The door costs 2 planks, 5 should be left, left " + WoodCost.sparePlanks(kingdom));
        Kingdoms.addResource(server, kingdom, BuiltInRegistries.ITEM.getKey(Items.OAK_LOG), 1);
        int stairs = WoodCost.make(server, kingdom, Items.OAK_STAIRS, 64);
        helper.assertTrue(stairs == 6, "5 spare planks and a log (9 planks) should make 6 stairs, made " + stairs);
        helper.assertTrue(WoodCost.wood(kingdom) == 0, "All the wood should be used, left " + WoodCost.wood(kingdom) + " planks' worth");
        KingdomGameTests.clear(helper);
        helper.succeed();
    }

    /** A meal makes a builder faster and tougher for a while (the owner's call 2026-10-16); it doesn't eat again while well fed. */
    @GameTest(template = PLATFORM, batch = "economy_meals")
    public static void mealMakesABuilderFasterAndTougher(GameTestHelper helper) {
        Kingdom kingdom = found(helper);
        ClaySoldierEntity builder = KingdomGameTests.DevSoldier.spawnPlain(helper, new BlockPos(4, GROUND_Y, 4), RED);
        double speed = builder.getAttributeValue(Attributes.MOVEMENT_SPEED);
        float health = builder.getMaxHealth();
        helper.assertTrue(Meals.feed(builder, kingdom), "A hungry builder should eat");
        helper.assertTrue(builder.getAttributeValue(Attributes.MOVEMENT_SPEED) > speed, "A meal should make the builder faster");
        helper.assertTrue(builder.getMaxHealth() > health && builder.getHealth() == builder.getMaxHealth(),
                "A meal should give more health, got " + builder.getHealth() + " of " + builder.getMaxHealth() + " (was " + health + ")");
        helper.assertFalse(Meals.feed(builder, kingdom), "A well fed builder shouldn't eat again");
        KingdomGameTests.clear(helper);
        helper.succeed();
    }

    private static void assertCost(GameTestHelper helper, MinecraftServer server, Item item, double expected) {
        OptionalDouble cost = WoodCost.planks(server, item);
        helper.assertTrue(cost.isPresent() && Math.abs(cost.getAsDouble() - expected) < 1e-6, item + " should cost " + expected + " planks, got " + cost);
    }

    /**
     * The plot picker never overlaps a building (with the gap), prefers a plot facing the keep, and finds none when
     * everything is taken.
     */
    @GameTest(template = PLATFORM)
    public static void plotPickerKeepsClearOfBuildingsAndFacesTheKeep(GameTestHelper helper) {
        TerrainScan scan = TerrainScan.flat(0, 0, 80, 64);
        // A 9 by 9 house going south of its easel, with its front row.
        List<KeepSite.Area> areas = List.of(new KeepSite.Area(Direction.SOUTH, -4, 4, -3, 10, 0, 6));
        PlotPicker.Taken keep = new PlotPicker.Taken(-17, 17, -17, 17);
        PlotPicker.Rules rules = new PlotPicker.Rules(0, 48, 4, 3, 1, 3, false);
        PlotPicker.Result result = PlotPicker.search(scan, 0, 0, areas, List.of(keep), (x, z) -> false, rules);
        helper.assertTrue(result.best().isPresent(), "No plot: " + result.summary());
        PlotPicker.Plot plot = result.best().get();
        KeepSite.Choice site = plot.site();
        boolean overlaps = site.minX(1) <= keep.maxX() + 3 && site.maxX(1) >= keep.minX() - 3
                && site.minZ(1) <= keep.maxZ() + 3 && site.maxZ(1) >= keep.minZ() - 3;
        helper.assertFalse(overlaps, "The plot overlaps the keep: " + plot.describe());
        helper.assertTrue(plot.facesCenter() && site.easelZ() > 0, "The house should stand south of the keep, facing it: " + plot.describe());

        PlotPicker.Result none = PlotPicker.search(scan, 0, 0, areas, List.of(new PlotPicker.Taken(-200, 200, -200, 200)), (x, z) -> false, rules);
        helper.assertTrue(none.best().isEmpty(), "Everything is taken, yet: " + none.summary());
        helper.succeed();
    }

    /**
     * A woodcutter walks to the tree near its camp, chops it log by log, plants a sapling where the trunk stood and brings
     * the logs to the camp's chest (into the stockpile).
     */
    @GameTest(template = PLATFORM, batch = "economy_woodcutter", timeoutTicks = 2400)
    public static void woodcutterFellsATreeAndReplantsIt(GameTestHelper helper) {
        Kingdom kingdom = found(helper);
        ServerLevel level = helper.getLevel();
        BlockPos easel = helper.absolutePos(EASEL);
        BlockPos chest = KingdomKeep.chestPos(easel, Direction.SOUTH);
        kingdom.addBuilding(new KingdomBuilding(HUT, easel, Direction.SOUTH, KingdomBuilding.State.BUILT));
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());

        BlockPos base = helper.absolutePos(new BlockPos(2, GROUND_Y, 13));
        level.setBlockAndUpdate(base.below(), Blocks.DIRT.defaultBlockState());
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, 1);
        for (int dy = 2; dy <= 3; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlockAndUpdate(base.offset(dx, dy, dz), leaves);
                }
            }
        }
        level.setBlockAndUpdate(base.above(4), leaves);
        for (int dy = 0; dy < 4; dy++) {
            level.setBlockAndUpdate(base.above(dy), Blocks.OAK_LOG.defaultBlockState());
        }

        ClaySoldierEntity worker = KingdomGameTests.DevSoldier.spawnPlain(helper, new BlockPos(12, GROUND_Y, 2), RED);
        worker.equipItemIfPossible(new ItemStack(Items.WHEAT));
        helper.assertTrue(worker.getWorkSelector().selectWork(FellTreesGoal.class), "Setup: no woodcutter job");
        worker.setPoiPos(chest);
        helper.assertTrue(KingdomEconomy.workplace(worker).isPresent(), "Setup: the woodcutter has no workplace");

        ResourceLocation oak = BuiltInRegistries.ITEM.getKey(Items.OAK_LOG);
        helper.succeedWhen(() -> {
            helper.assertTrue(kingdom.resource(oak) >= 4, "Logs in the stockpile: " + kingdom.resource(oak) + " of 4");
            helper.assertTrue(level.getBlockState(base).is(Blocks.OAK_SAPLING), "No oak sapling where the trunk stood: " + level.getBlockState(base));
            for (int dy = 1; dy < 4; dy++) {
                helper.assertFalse(level.getBlockState(base.above(dy)).is(Blocks.OAK_LOG), "A log is left at " + base.above(dy).toShortString());
            }
            KingdomGameTests.clear(helper);
        });
    }

    /**
     * A farmer tills the bare dirt of its field and sows wheat; ripe wheat is harvested, replanted, and the harvest goes
     * to the field's chest (the stockpile).
     */
    @GameTest(template = PLATFORM, batch = "economy_farmer", timeoutTicks = 2400)
    public static void farmerTillsSowsHarvestsAndReplants(GameTestHelper helper) {
        Kingdom kingdom = found(helper);
        ServerLevel level = helper.getLevel();
        BlockPos easel = helper.absolutePos(EASEL);
        BlockPos chest = KingdomKeep.chestPos(easel, Direction.SOUTH);
        kingdom.addBuilding(new KingdomBuilding(HUT, easel, Direction.SOUTH, KingdomBuilding.State.BUILT));
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        List<BlockPos> cells = List.of(helper.absolutePos(new BlockPos(7, GROUND_Y, 7)), helper.absolutePos(new BlockPos(8, GROUND_Y, 7)),
                helper.absolutePos(new BlockPos(9, GROUND_Y, 7)));
        cells.forEach(cell -> level.setBlockAndUpdate(cell, Blocks.DIRT.defaultBlockState()));

        ClaySoldierEntity farmer = KingdomGameTests.DevSoldier.spawnPlain(helper, new BlockPos(12, GROUND_Y, 2), RED);
        farmer.equipItemIfPossible(new ItemStack(Items.WHEAT));
        helper.assertTrue(farmer.getWorkSelector().selectWork(TendFieldGoal.class), "Setup: no farmer job");
        farmer.setPoiPos(chest);

        boolean[] ripened = {false};
        helper.onEachTick(() -> {
            if (!ripened[0] && cells.stream().allMatch(cell -> level.getBlockState(cell).is(Blocks.FARMLAND)
                    && level.getBlockState(cell.above()).getBlock() instanceof CropBlock)) {
                ripened[0] = true;
                for (BlockPos cell : cells) {
                    level.setBlockAndUpdate(cell.above(), ((CropBlock) Blocks.WHEAT).getStateForAge(7));
                }
            }
        });
        ResourceLocation wheat = BuiltInRegistries.ITEM.getKey(Items.WHEAT);
        helper.succeedWhen(() -> {
            helper.assertTrue(ripened[0], "The farmer hasn't tilled and sown all 3 cells yet");
            helper.assertTrue(kingdom.resource(wheat) >= 3, "Wheat in the stockpile: " + kingdom.resource(wheat) + " of 3");
            for (BlockPos cell : cells) {
                helper.assertTrue(level.getBlockState(cell).is(Blocks.FARMLAND) && level.getBlockState(cell.above()).is(Blocks.WHEAT),
                        "Not replanted at " + cell.toShortString() + ": " + level.getBlockState(cell.above()));
            }
            KingdomGameTests.clear(helper);
        });
    }

    /**
     * Once a building is finished its builders are let go, and an idle soldier becomes a woodcutter at the lumber camp;
     * what lies in a finished building's chest goes to the stockpile.
     */
    @GameTest(template = PLATFORM, batch = "economy_crews")
    public static void buildersAreLetGoAndBecomeWorkers(GameTestHelper helper) {
        Kingdom kingdom = found(helper);
        ServerLevel level = helper.getLevel();
        BlockPos keepEasel = helper.absolutePos(EASEL);
        BlockPos keepChest = KingdomKeep.chestPos(keepEasel, Direction.SOUTH);
        BlockPos campEasel = helper.absolutePos(new BlockPos(3, GROUND_Y, 12));
        BlockPos campChest = KingdomKeep.chestPos(campEasel, Direction.SOUTH);
        kingdom.addBuilding(new KingdomBuilding(HUT, keepEasel, Direction.SOUTH, KingdomBuilding.State.BUILT));
        kingdom.addBuilding(new KingdomBuilding(KingdomEconomy.LUMBER_CAMP, campEasel, Direction.SOUTH, KingdomBuilding.State.BUILT));
        kingdom.addBuilding(new KingdomBuilding(KingdomEconomy.WHEAT_FIELD, helper.absolutePos(new BlockPos(13, GROUND_Y, 3)), Direction.SOUTH, KingdomBuilding.State.BUILT));
        kingdom.addBuilding(new KingdomBuilding(KingdomEconomy.COTTAGE, helper.absolutePos(new BlockPos(13, GROUND_Y, 8)), Direction.SOUTH, KingdomBuilding.State.BUILT));
        level.setBlockAndUpdate(keepChest, Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity) level.getBlockEntity(keepChest)).setItem(0, new ItemStack(Items.DIRT, 5));

        ClaySoldierEntity builder = KingdomGameTests.DevSoldier.spawnPlain(helper, new BlockPos(8, GROUND_Y, 2), RED);
        builder.equipItemIfPossible(new ItemStack(Items.WHEAT));
        helper.assertTrue(builder.getWorkSelector().selectWork(BuildBlueprintGoal.class), "Setup: no builder job");
        builder.setPoiPos(keepChest);

        KingdomEconomy.update(level, kingdom);

        helper.assertFalse(builder.getWorkSelector().isDoing(BuildBlueprintGoal.class), "The keep is finished, yet the soldier is still a builder");
        helper.assertTrue(builder.getWorkSelector().isDoing(FellTreesGoal.class) && campChest.equals(builder.getPoiPos()),
                "The idle soldier should be a woodcutter at the camp (POI " + campChest.toShortString() + "), got POI " + builder.getPoiPos()
                        + " and job " + builder.getWorkSelector());
        helper.assertTrue(kingdom.resource(BuiltInRegistries.ITEM.getKey(Items.DIRT)) == 5, "The keep chest's dirt should be in the stockpile: " + kingdom.stockpile());
        KingdomGameTests.clear(helper);
        helper.succeed();
    }

    /** The cottage waits until the stockpile has the logs for its wooden blocks and the builders' food. */
    @GameTest(template = PLATFORM, batch = "economy_cottage")
    public static void cottageWaitsForWoodAndFood(GameTestHelper helper) {
        Kingdom kingdom = found(helper);
        ServerLevel level = helper.getLevel();
        kingdom.addBuilding(new KingdomBuilding(HUT, helper.absolutePos(EASEL), Direction.SOUTH, KingdomBuilding.State.BUILT));
        kingdom.addBuilding(new KingdomBuilding(KingdomEconomy.LUMBER_CAMP, helper.absolutePos(new BlockPos(3, GROUND_Y, 12)), Direction.SOUTH, KingdomBuilding.State.BUILT));
        kingdom.addBuilding(new KingdomBuilding(KingdomEconomy.WHEAT_FIELD, helper.absolutePos(new BlockPos(13, GROUND_Y, 3)), Direction.SOUTH, KingdomBuilding.State.BUILT));

        KingdomEconomy.Slot next = KingdomEconomy.next(kingdom).orElseThrow();
        helper.assertTrue(next.structure().equals(KingdomEconomy.COTTAGE), "The next building should be the cottage, got " + next.structure());
        KingdomEconomy.Need need = KingdomEconomy.need(level, next).orElseThrow();
        helper.assertTrue(need.logs() > 20, "The cottage is mostly wood: expected over 20 logs, got " + need.logs());
        String waiting = KingdomEconomy.waitingFor(level, kingdom, next).orElse("");
        helper.assertTrue(waiting.contains("logs") && waiting.contains("wheat"), "It should wait for logs and wheat: " + waiting);

        String supplied = KingdomEconomy.supplyNext(level, kingdom);
        helper.assertTrue(KingdomEconomy.waitingFor(level, kingdom, next).isEmpty(), "After '" + supplied + "' it still waits: "
                + KingdomEconomy.waitingFor(level, kingdom, next));
        KingdomGameTests.clear(helper);
        helper.succeed();
    }

    /** A building finished after the keep earns the kingdom renown. */
    @GameTest(template = PLATFORM, batch = "economy_renown")
    public static void finishedBuildingEarnsRenown(GameTestHelper helper) {
        Kingdom kingdom = found(helper);
        ServerLevel level = helper.getLevel();
        kingdom.addBuilding(new KingdomBuilding(HUT, helper.absolutePos(new BlockPos(3, GROUND_Y, 12)), Direction.SOUTH, KingdomBuilding.State.BUILT));
        kingdom.addBuilding(new KingdomBuilding(HUT, helper.absolutePos(EASEL), Direction.SOUTH, KingdomBuilding.State.BUILDING));
        int before = kingdom.renown();

        String result = KingdomEconomy.finishNow(level, kingdom);

        helper.assertTrue(kingdom.buildings().get(1).state() == KingdomBuilding.State.BUILT, "Not built after '" + result + "'");
        helper.assertTrue(kingdom.renown() > before, "No renown for the finished building: " + kingdom.renown());
        helper.assertTrue(kingdom.chronicle().stream().anyMatch(entry -> entry.text().contains("is finished")), "No chronicle line");
        KingdomGameTests.clear(helper);
        helper.succeed();
    }

    private static Kingdom found(GameTestHelper helper) {
        KingdomGameTests.clear(helper);
        ServerLevel level = helper.getLevel();
        FakePlayer player = KingdomGameTests.player(helper);
        ClaySoldierEntity king = KingdomGameTests.spawnKing(helper, KING_AT, RED, player);
        helper.assertTrue(Kingdoms.found(level, king, player, true).ok(), "Setup: founding failed");
        Kingdom kingdom = Kingdoms.byTeam(level.getServer(), RED).orElseThrow();
        KingdomKeep.forget(kingdom);
        KingdomEconomy.forget(kingdom);
        return kingdom;
    }
}
