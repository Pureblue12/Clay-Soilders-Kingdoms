package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.entity.goal.workgoal.CraftGoal;
import net.bumblebee.claysoldiers.entity.goal.workgoal.QuarryGoal;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomBuilding;
import net.bumblebee.claysoldiers.kingdom.KingdomEconomy;
import net.bumblebee.claysoldiers.kingdom.KingdomKeep;
import net.bumblebee.claysoldiers.kingdom.KingdomWorkshop;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.bumblebee.claysoldiers.kingdom.QuarryPit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.GROUND_Y;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;
import static net.bumblebee.claysoldiers.gametest.KingdomGameTests.RED;

/**
 * Stage 6b: the workshop and the quarry ({@link KingdomWorkshop}, {@link QuarryPit}, {@link QuarryGoal}, {@link CraftGoal}).
 * The buildings are records on the kingdom, like the stage 5 tests: what the workshop can make at each level and from
 * what, its order board, a crafter making an order from the stockpile's cobblestone, the quarry's stepped pit, a quarryman
 * digging it, and which raw materials a paid building waits for.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class WorkshopGameTests {
    private static final ResourceLocation HUT = KingdomKeepGameTests.HUT;
    /** The hut goes south of the easel: x 6..10, z 6..10. */
    private static final BlockPos EASEL = new BlockPos(8, GROUND_Y, 4);

    /** Level 1 makes stone blocks from cobblestone, level 2 adds glass and bricks; wooden blocks are the carpenter's. */
    @GameTest(template = PLATFORM, batch = "workshop")
    public static void workshopMakesStoneBlocksFromCobblestone(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        for (Item stone : List.of(Items.STONE, Items.STONE_BRICKS, Items.STONE_BRICK_STAIRS, Items.COBBLESTONE_WALL, Items.SMOOTH_STONE)) {
            Optional<Map<Item, Double>> cost = KingdomWorkshop.rawCost(server, stone, 1);
            helper.assertTrue(cost.isPresent() && cost.get().keySet().equals(java.util.Set.of(Items.COBBLESTONE)),
                    stone + " should be made from cobblestone at level 1, got " + cost);
            helper.assertTrue(Math.abs(cost.get().get(Items.COBBLESTONE) - 1.0) < 1e-6, stone + " should cost 1 cobblestone, got " + cost);
        }
        for (Item later : List.of(Items.GLASS_PANE, Items.BRICKS, Items.LANTERN, Items.MOSSY_COBBLESTONE)) {
            helper.assertTrue(KingdomWorkshop.rawCost(server, later, 1).isEmpty(), later + " can't be made at level 1, got "
                    + KingdomWorkshop.rawCost(server, later, 1));
        }
        helper.assertTrue(KingdomWorkshop.rawCost(server, Items.GLASS_PANE, 2).isPresent(), "Glass panes are made at level 2");
        helper.assertTrue(KingdomWorkshop.rawCost(server, Items.BRICKS, 2).isPresent(), "Bricks are made at level 2");
        helper.assertTrue(KingdomWorkshop.rawCost(server, Items.OAK_STAIRS, 3).isEmpty(), "Wooden blocks are the carpenter's");

        Kingdom kingdom = found(helper, new BlockPos(13, GROUND_Y, 13));
        helper.assertFalse(KingdomWorkshop.canMake(server, kingdom, Items.STONE_BRICKS), "No workshop yet, nothing is made");
        kingdom.addBuilding(new KingdomBuilding(KingdomWorkshop.WORKSHOP, helper.absolutePos(EASEL), Direction.SOUTH, KingdomBuilding.State.BUILT, 1));
        helper.assertTrue(KingdomWorkshop.level(kingdom) == 1, "Workshop level: " + KingdomWorkshop.level(kingdom));
        helper.assertTrue(KingdomWorkshop.canMake(server, kingdom, Items.STONE_BRICKS), "A level 1 workshop makes stone bricks");
        helper.assertFalse(KingdomWorkshop.canMake(server, kingdom, Items.COBBLESTONE), "Cobblestone is raw, not made");
        helper.assertFalse(KingdomWorkshop.canMake(server, kingdom, Items.GLASS_PANE), "A level 1 workshop makes no glass");
        KingdomGameTests.clear(helper);
        helper.succeed();
    }

    /**
     * The order board holds what the building still needs that the workshop makes, less what the stockpile and the chest
     * hold; a paid building waits for the cobblestone it needs once the quarry is finished.
     */
    @GameTest(template = PLATFORM, batch = "workshop")
    public static void ordersAndRawNeedsFollowTheBuildings(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = found(helper, new BlockPos(13, GROUND_Y, 13));
        List<ItemStack> needed = List.of(new ItemStack(Items.COBBLESTONE, 4), new ItemStack(Items.STONE_BRICKS, 5),
                new ItemStack(Items.OAK_PLANKS, 3), new ItemStack(Items.GLASS_PANE, 2));
        helper.assertTrue(KingdomWorkshop.rawNeeds(server, kingdom, needed).isEmpty(), "No quarry, no workshop: nothing to wait for, got "
                + KingdomWorkshop.rawNeeds(server, kingdom, needed));

        kingdom.addBuilding(new KingdomBuilding(KingdomWorkshop.QUARRY, helper.absolutePos(new BlockPos(3, GROUND_Y, 12)), Direction.SOUTH, KingdomBuilding.State.BUILT, 1));
        helper.assertTrue(KingdomWorkshop.gathered(kingdom, Items.COBBLESTONE), "With a quarry, cobblestone is gathered");
        helper.assertTrue(KingdomWorkshop.rawNeeds(server, kingdom, needed).equals(Map.of(Items.COBBLESTONE, 4)),
                "With a quarry: 4 cobblestone, got " + KingdomWorkshop.rawNeeds(server, kingdom, needed));
        KingdomWorkshop.setDry(kingdom, true);
        helper.assertFalse(KingdomWorkshop.gathered(kingdom, Items.COBBLESTONE), "A quarry with no stone gathers none");
        KingdomWorkshop.setDry(kingdom, false);

        kingdom.addBuilding(new KingdomBuilding(KingdomWorkshop.WORKSHOP, helper.absolutePos(EASEL), Direction.SOUTH, KingdomBuilding.State.BUILT, 1));
        helper.assertTrue(KingdomWorkshop.rawNeeds(server, kingdom, needed).equals(Map.of(Items.COBBLESTONE, 9)),
                "With a workshop too: 4 + 5 cobblestone, got " + KingdomWorkshop.rawNeeds(server, kingdom, needed));

        Kingdoms.addResource(server, kingdom, BuiltInRegistries.ITEM.getKey(Items.STONE_BRICKS), 2);
        List<Item> remaining = List.of(Items.STONE_BRICKS, Items.OAK_PLANKS, Items.STONE_BRICKS, Items.GLASS_PANE, Items.STONE_BRICKS,
                Items.COBBLESTONE, Items.STONE_BRICKS, Items.STONE_BRICKS);
        KingdomWorkshop.updateOrders(server, kingdom, remaining, item -> item == Items.STONE_BRICKS ? 1 : 0);
        helper.assertTrue(KingdomWorkshop.orders(kingdom).equals(List.of(new KingdomWorkshop.Order(Items.STONE_BRICKS, 2))),
                "Orders: 5 stone bricks less 2 in the stockpile and 1 in the chest, got " + KingdomWorkshop.orders(kingdom));
        KingdomGameTests.clear(helper);
        helper.succeed();
    }

    /** A crafter at the workshop turns the stockpile's cobblestone into the stone bricks on the order board. */
    @GameTest(template = PLATFORM, batch = "workshop_crafter", timeoutTicks = 1600)
    public static void crafterMakesItsOrdersFromTheStockpile(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        Kingdom kingdom = found(helper, new BlockPos(13, GROUND_Y, 13));
        BlockPos easel = helper.absolutePos(EASEL);
        BlockPos chest = KingdomKeep.chestPos(easel, Direction.SOUTH);
        kingdom.addBuilding(new KingdomBuilding(KingdomWorkshop.WORKSHOP, easel, Direction.SOUTH, KingdomBuilding.State.BUILT, 1));
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        ResourceLocation cobblestone = BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE);
        ResourceLocation bricks = BuiltInRegistries.ITEM.getKey(Items.STONE_BRICKS);
        Kingdoms.addResource(server, kingdom, cobblestone, 12);
        KingdomWorkshop.setOrdersForTests(kingdom, List.of(new KingdomWorkshop.Order(Items.STONE_BRICKS, 10)));

        ClaySoldierEntity crafter = KingdomGameTests.DevSoldier.spawnPlain(helper, new BlockPos(12, GROUND_Y, 2), RED);
        crafter.equipItemIfPossible(new ItemStack(Items.WHEAT));
        helper.assertTrue(crafter.getWorkSelector().selectWork(CraftGoal.class), "Setup: no crafter job");
        crafter.setPoiPos(chest);
        helper.assertTrue(KingdomEconomy.workplace(crafter).isPresent(), "Setup: the crafter has no workplace");

        helper.succeedWhen(() -> {
            helper.assertTrue(kingdom.resource(bricks) == 10, "Stone bricks made: " + kingdom.resource(bricks) + " of 10");
            helper.assertTrue(kingdom.resource(cobblestone) == 2, "Cobblestone left: " + kingdom.resource(cobblestone) + ", expected 2");
            helper.assertTrue(KingdomWorkshop.orders(kingdom).isEmpty(), "Orders left: " + KingdomWorkshop.orders(kingdom));
            KingdomGameTests.clear(helper);
        });
    }

    /** The pit goes behind the building and every deeper layer is one smaller on every side: steps of one block. */
    @GameTest(template = PLATFORM, batch = "workshop")
    public static void quarryPitIsSteppedBehindTheQuarry(GameTestHelper helper) {
        BoundingBox building = new BoundingBox(0, 64, 0, 9, 70, 9);
        QuarryPit south = QuarryPit.behind(building, Direction.SOUTH, 63, 6, 2, 8);
        helper.assertTrue(south.minZ() == 12 && south.maxZ() == 17 && south.minX() == 1 && south.maxX() == 6 && south.depth() == 3,
                "South pit: " + south);
        QuarryPit north = QuarryPit.behind(building, Direction.NORTH, 63, 6, 2, 8);
        helper.assertTrue(north.maxZ() == -3 && north.minZ() == -8, "North pit: " + north);
        QuarryPit east = QuarryPit.behind(building, Direction.EAST, 63, 6, 2, 8);
        helper.assertTrue(east.minX() == 12 && east.minZ() == 1, "East pit: " + east);
        QuarryPit west = QuarryPit.behind(building, Direction.WEST, 63, 6, 2, 8);
        helper.assertTrue(west.maxX() == -3, "West pit: " + west);
        helper.assertTrue(QuarryPit.behind(building, Direction.SOUTH, 63, 16, 2, 5).depth() == 5, "max_depth caps the pit");

        for (int layer = 2; layer <= south.depth(); layer++) {
            for (int x = south.minX(); x <= south.maxX(); x++) {
                for (int z = south.minZ(); z <= south.maxZ(); z++) {
                    if (!south.inLayer(x, z, layer)) {
                        continue;
                    }
                    for (Direction side : Direction.Plane.HORIZONTAL) {
                        int nx = x + side.getStepX();
                        int nz = z + side.getStepZ();
                        helper.assertTrue(south.inLayer(nx, nz, layer - 1), "A step of more than one block beside " + x + ", " + z + " in layer " + layer);
                    }
                }
            }
        }
        helper.assertTrue(south.y(1) == 63 && south.y(3) == 61 && south.layerAt(62) == 2 && south.layerAt(64) == 0, "Layer heights");
        helper.succeed();
    }

    /**
     * A quarryman digs the pit behind its quarry layer by layer and brings the cobblestone to the quarry's chest. The test
     * stands on a stone plateau 3 high so the pit has stone under it (a pit of 3: a 3 by 3 layer, then its middle block).
     */
    @GameTest(template = PLATFORM, batch = "workshop_quarry", timeoutTicks = 3000)
    public static void quarrymanDigsTheSteppedPit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        KingdomGameTests.clear(helper);
        int top = GROUND_Y + 2;
        for (int x = 0; x <= 15; x++) {
            for (int z = 0; z <= 15; z++) {
                for (int y = GROUND_Y; y <= top; y++) {
                    level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState());
                }
            }
        }
        Kingdom kingdom = found(helper, new BlockPos(13, top + 1, 13));
        BlockPos easel = helper.absolutePos(new BlockPos(8, top + 1, 4));
        BlockPos chest = KingdomKeep.chestPos(easel, Direction.SOUTH);
        kingdom.addBuilding(new KingdomBuilding(HUT, easel, Direction.SOUTH, KingdomBuilding.State.BUILT));
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        QuarryGoal.setPitSizeForTests(3);

        ClaySoldierEntity worker = KingdomGameTests.DevSoldier.spawnPlain(helper, new BlockPos(12, top + 1, 10), RED);
        worker.equipItemIfPossible(new ItemStack(Items.WHEAT));
        helper.assertTrue(worker.getWorkSelector().selectWork(QuarryGoal.class), "Setup: no quarryman job");
        worker.setPoiPos(chest);
        Optional<KingdomEconomy.Workplace> place = KingdomEconomy.workplace(worker);
        helper.assertTrue(place.isPresent(), "Setup: the quarryman has no workplace");
        QuarryPit pit = QuarryGoal.pit(place.get());
        helper.assertTrue(pit.depth() == 2 && pit.y(1) == helper.absolutePos(new BlockPos(0, top, 13)).getY()
                        && pit.minZ() == helper.absolutePos(new BlockPos(0, top, 13)).getZ(),
                "Setup: the pit is " + pit);

        ResourceLocation cobblestone = BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE);
        BlockPos middle = new BlockPos(pit.minX() + 1, pit.y(2), pit.minZ() + 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(kingdom.resource(cobblestone) >= 10, "Cobblestone in the stockpile: " + kingdom.resource(cobblestone) + " of 10");
            for (int x = pit.minX(); x <= pit.maxX(); x++) {
                for (int z = pit.minZ(); z <= pit.maxZ(); z++) {
                    helper.assertTrue(level.getBlockState(new BlockPos(x, pit.y(1), z)).isAir(), "Layer 1 isn't dug at " + x + ", " + z);
                    boolean inner = x == middle.getX() && z == middle.getZ();
                    helper.assertTrue(level.getBlockState(new BlockPos(x, pit.y(2), z)).isAir() == inner,
                            (inner ? "The middle of layer 2 isn't dug" : "Dug outside layer 2") + " at " + x + ", " + z);
                }
            }
            helper.assertTrue(KingdomWorkshop.isDry(kingdom), "A dug-out pit has no stone left");
            QuarryGoal.setPitSizeForTests(null);
            KingdomGameTests.clear(helper);
        });
    }

    private static Kingdom found(GameTestHelper helper, BlockPos kingAt) {
        KingdomGameTests.clear(helper);
        ServerLevel level = helper.getLevel();
        FakePlayer player = KingdomGameTests.player(helper);
        ClaySoldierEntity king = KingdomGameTests.spawnKing(helper, kingAt, RED, player);
        helper.assertTrue(Kingdoms.found(level, king, player, true).ok(), "Setup: founding failed");
        Kingdom kingdom = Kingdoms.byTeam(level.getServer(), RED).orElseThrow();
        KingdomKeep.forget(kingdom);
        KingdomEconomy.forget(kingdom);
        return kingdom;
    }
}
