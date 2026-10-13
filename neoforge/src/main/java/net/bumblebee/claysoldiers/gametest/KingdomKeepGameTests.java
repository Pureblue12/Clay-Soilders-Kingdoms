package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KeepSite;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomBuilding;
import net.bumblebee.claysoldiers.kingdom.KingdomKeep;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.bumblebee.claysoldiers.structure.StructureBlueprints;
import net.bumblebee.claysoldiers.structure.StructureMeta;
import net.bumblebee.claysoldiers.structure.StructureRegistry;
import net.bumblebee.claysoldiers.terrain.TerrainScan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.GROUND_Y;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;
import static net.bumblebee.claysoldiers.gametest.KingdomGameTests.RED;

/**
 * Stage 4, the kingdom builds its own keep ({@link KingdomKeep}). The whole loop runs with the test hut (5 x 3 x 5,
 * its throne in the middle) instead of the 31 x 20 x 33 keep, which builds for over 100,000 ticks: the kingdom makes three
 * of its wheat-holding soldiers builders (not the king, not a soldier without wheat), stocks their chest from the
 * stockpile, notices the hut is finished, and its king gets his extra health and stands on the gold block throne. Also: items put in the
 * chest that the build doesn't need go to the stockpile; the site search; levelling; the saved detail level.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class KingdomKeepGameTests {
    static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath(ClaySoldiersCommon.MOD_ID, "test_hut");
    private static final BlockPos KING_AT = new BlockPos(13, GROUND_Y, 13);
    /** The hut goes south of the easel: x 6..10, z 6..10; the chest is at (6, 2, 3). */
    private static final BlockPos EASEL = new BlockPos(8, GROUND_Y, 4);

    @GameTest(template = PLATFORM, batch = "kingdom_keep_build", timeoutTicks = 9000)
    public static void kingdomBuildsItsKeepAndTheKingTakesHisThrone(GameTestHelper helper) {
        KingdomGameTests.clear(helper);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        FakePlayer player = KingdomGameTests.player(helper);
        ClaySoldierEntity king = KingdomGameTests.spawnKing(helper, KING_AT, RED, player);
        helper.assertTrue(Kingdoms.found(level, king, player, true).ok(), "Setup: founding failed");
        Kingdom kingdom = Kingdoms.byTeam(server, RED).orElseThrow();
        KingdomKeep.forget(kingdom);

        BlueprintData blueprint = hut(helper);
        ServerBlueprintPlan plan = blueprint.createServerPlan().orElseThrow();
        Map<Item, Integer> supplied = new java.util.LinkedHashMap<>();
        for (ItemStack stack : plan.getNeededItems()) {
            Kingdoms.addResource(server, kingdom, BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount());
            supplied.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        List<ClaySoldierEntity> workers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ClaySoldierEntity worker = KingdomGameTests.DevSoldier.spawnPlain(helper, new BlockPos(2 + i, GROUND_Y, 1), RED);
            worker.equipItemIfPossible(new ItemStack(Items.WHEAT));
            workers.add(worker);
        }
        ClaySoldierEntity fighter = KingdomGameTests.DevSoldier.spawnPlain(helper, new BlockPos(13, GROUND_Y, 1), RED);

        BlockPos easel = helper.absolutePos(EASEL);
        helper.assertTrue(KingdomKeep.start(level, kingdom, HUT, 1, blueprint, easel, Direction.SOUTH),
                "The hut wasn't started: " + KingdomKeep.describe(server, kingdom));
        BlockPos chest = KingdomKeep.chestPos(easel, Direction.SOUTH);
        helper.assertTrue(level.getBlockEntity(chest) instanceof ChestBlockEntity, "No chest at " + chest.toShortString());
        BlockPos throne = KingdomKeep.throne(level, KingdomKeep.keep(kingdom).orElseThrow()).orElseThrow();
        helper.assertTrue(throne.equals(helper.absolutePos(new BlockPos(8, GROUND_Y, 8))),
                "The hut's throne should be the middle of its floor, " + helper.absolutePos(new BlockPos(8, GROUND_Y, 8)).toShortString() + ", got " + throne.toShortString());

        helper.onEachTick(() -> KingdomKeep.update(level, kingdom));
        helper.succeedWhen(() -> {
            long crew = workers.stream().filter(worker -> chest.equals(worker.getPoiPos())).count();
            helper.assertTrue(crew == 3, "Expected 3 of the 4 wheat soldiers as builders, got " + crew);
            helper.assertTrue(fighter.getPoiPos() == null && !fighter.getWorkSelector().isWorking(), "A soldier without wheat was made a builder");
            helper.assertTrue(king.getPoiPos() == null && !king.getWorkSelector().isWorking(), "The king was made a builder");
            KingdomBuilding keep = KingdomKeep.keep(kingdom).orElseThrow();
            helper.assertTrue(keep.state() == KingdomBuilding.State.BUILT, "Not built yet: " + KingdomKeep.describe(server, kingdom));
            helper.assertTrue(king.getAttribute(Attributes.MAX_HEALTH).getValue() > 20, "The king has no extra health: " + king.getMaxHealth());
            helper.assertTrue(level.getBlockState(throne).is(Blocks.GOLD_BLOCK), "No gold block throne at " + throne.toShortString()
                    + ": " + level.getBlockState(throne));
            double dx = king.getX() - (throne.getX() + 0.5);
            double dz = king.getZ() - (throne.getZ() + 0.5);
            helper.assertTrue(Math.abs(dx) < 0.5 && Math.abs(dz) < 0.5 && Math.abs(king.getY() - (throne.getY() + 1)) < 0.5,
                    "The king isn't standing on his throne at " + throne.toShortString() + ": at " + king.position());
            Item cobblestone = Items.COBBLESTONE;
            int left = kingdom.resource(BuiltInRegistries.ITEM.getKey(cobblestone));
            helper.assertTrue(left < supplied.getOrDefault(cobblestone, 0), "The stockpile's cobblestone wasn't used: " + left);
            helper.assertTrue(kingdom.chronicle().stream().anyMatch(entry -> entry.text().contains("is finished")), "No chronicle line for the finished keep");

            Kingdoms.disband(server, RED, null);
            helper.assertTrue(king.getMaxHealth() <= 20, "Disbanding should take the king's extra health back: " + king.getMaxHealth());
        });
    }

    /**
     * A player supplies the keep by putting items in its chest: what the build needs stays there, anything else goes to
     * the stockpile; the book lists what is still missing.
     */
    @GameTest(template = PLATFORM, batch = "kingdom_keep_chest")
    public static void keepChestSendsOtherItemsToTheStockpile(GameTestHelper helper) {
        KingdomGameTests.clear(helper);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        FakePlayer player = KingdomGameTests.player(helper);
        ClaySoldierEntity king = KingdomGameTests.spawnKing(helper, KING_AT, RED, player);
        helper.assertTrue(Kingdoms.found(level, king, player, true).ok(), "Setup: founding failed");
        Kingdom kingdom = Kingdoms.byTeam(server, RED).orElseThrow();
        KingdomKeep.forget(kingdom);
        BlueprintData blueprint = hut(helper);
        int cobblestone = (int) blueprint.createServerPlan().orElseThrow().remainingItems().stream().filter(item -> item == Items.COBBLESTONE).count();
        BlockPos easel = helper.absolutePos(EASEL);
        helper.assertTrue(KingdomKeep.start(level, kingdom, HUT, 1, blueprint, easel, Direction.SOUTH), "The hut wasn't started");
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(KingdomKeep.chestPos(easel, Direction.SOUTH));
        chest.setItem(0, new ItemStack(Items.DIRT, 5));
        chest.setItem(1, new ItemStack(Items.COBBLESTONE, 3));

        KingdomKeep.update(level, kingdom);

        helper.assertTrue(chest.countItem(Items.DIRT) == 0 && kingdom.resource(BuiltInRegistries.ITEM.getKey(Items.DIRT)) == 5,
                "Dirt (not needed) should have gone to the stockpile: chest " + chest.countItem(Items.DIRT) + ", stockpile " + kingdom.stockpile());
        helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 3, "Cobblestone (needed) should stay in the chest: " + chest.countItem(Items.COBBLESTONE));
        Map<Item, Integer> missing = KingdomKeep.missing(level, kingdom);
        helper.assertTrue(missing.getOrDefault(Items.COBBLESTONE, 0) == cobblestone - 3, "Missing cobblestone should be " + (cobblestone - 3) + ": " + missing);
        String book = String.join("\n", KingdomKeep.describe(server, kingdom));
        helper.assertTrue(book.contains("Being built") && book.contains("still missing") && book.contains("Cobblestone"), "The keep's status: " + book);
        KingdomGameTests.clear(helper);
        helper.succeed();
    }

    /**
     * The site search picks the nearest flat ground: where the kingdom was founded when that is flat; elsewhere when a
     * pillar stands there; nothing when everything is under water.
     */
    @GameTest(template = PLATFORM)
    public static void keepSiteIsTheNearestFlatGround(GameTestHelper helper) {
        KeepSite.Area area = new KeepSite.Area(Direction.SOUTH, -3, 3, -4, 6, 0, 4);
        KeepSite.Rules rules = new KeepSite.Rules(12, 4, 3, 1);
        TerrainScan flat = TerrainScan.flat(0, 0, 40, 64);
        Optional<KeepSite.Choice> here = KeepSite.search(flat, 0, 0, List.of(area), rules).best();
        helper.assertTrue(here.isPresent() && here.get().distance() == 0 && here.get().easelX() == 0 && here.get().easelZ() == -4
                && here.get().groundY() == 64 && here.get().earth() == 0, "On flat ground the keep goes where the kingdom was founded: " + here);

        TerrainScan pillar = TerrainScan.flat(0, 0, 40, 64);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                pillar.setGround(x, z, 72);
            }
        }
        Optional<KeepSite.Choice> moved = KeepSite.search(pillar, 0, 0, List.of(area), rules).best();
        helper.assertTrue(moved.isPresent() && moved.get().distance() > 0 && moved.get().range() <= 3,
                "A pillar where the kingdom was founded should move the keep to flat ground: " + moved);
        KeepSite.Choice choice = moved.get();
        helper.assertTrue(choice.minX(1) > 1 || choice.maxX(1) < -1 || choice.minZ(1) > 1 || choice.maxZ(1) < -1,
                "The chosen site still covers the pillar: " + choice.describe());

        TerrainScan lake = TerrainScan.flat(0, 0, 40, 64);
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                lake.setWater(x, z, 2);
            }
        }
        KeepSite.Result wet = KeepSite.search(lake, 0, 0, List.of(area), rules);
        helper.assertTrue(wet.best().isEmpty() && wet.wet() > 0 && wet.summary().contains("water"), "No site under water: " + wet.summary());
        helper.succeed();
    }

    /**
     * Levelling cuts what stands above the ground (earth, a log, which goes to the stockpile's count) and fills holes.
     */
    @GameTest(template = PLATFORM, batch = "kingdom_keep_level")
    public static void levellingCutsAndFills(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(5, GROUND_Y, 5), Blocks.DIRT);
        helper.setBlock(new BlockPos(5, GROUND_Y + 1, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(6, GROUND_Y, 6), Blocks.OAK_LOG);
        helper.setBlock(new BlockPos(7, GROUND_Y - 1, 7), Blocks.AIR);
        BlockPos min = helper.absolutePos(new BlockPos(4, GROUND_Y - 1, 4));
        BlockPos max = helper.absolutePos(new BlockPos(8, GROUND_Y + 4, 8));
        KingdomKeep.Levelled levelled = KingdomKeep.levelGround(level, Math.min(min.getX(), max.getX()), Math.max(min.getX(), max.getX()),
                Math.min(min.getZ(), max.getZ()), Math.max(min.getZ(), max.getZ()), min.getY(), max.getY());
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, GROUND_Y, 5));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, GROUND_Y + 1, 5));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(6, GROUND_Y, 6));
        helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(7, GROUND_Y - 1, 7));
        helper.assertTrue(levelled.logs().getOrDefault(Items.OAK_LOG, 0) == 1, "The cut log should be counted: " + levelled.logs());
        helper.assertTrue(levelled.filled() >= 1 && levelled.cleared() >= 3, "Filled " + levelled.filled() + ", cleared " + levelled.cleared());
        helper.succeed();
    }

    /**
     * A building's detail level is saved; a building saved before it existed (data version 1) is level 1.
     */
    @GameTest(template = PLATFORM)
    public static void buildingLevelIsSaved(GameTestHelper helper) {
        KingdomBuilding building = new KingdomBuilding(HUT, new BlockPos(1, 64, 2), Direction.EAST, KingdomBuilding.State.BUILDING, 3);
        Tag tag = KingdomBuilding.CODEC.encodeStart(NbtOps.INSTANCE, building).getOrThrow();
        helper.assertTrue(KingdomBuilding.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().equals(building), "The level isn't saved: " + tag);
        CompoundTag old = ((CompoundTag) tag).copy();
        old.remove("level");
        KingdomBuilding read = KingdomBuilding.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
        helper.assertTrue(read.level() == 1 && read.state() == KingdomBuilding.State.BUILDING, "A building without a level should read as level 1: " + read);
        helper.succeed();
    }

    private static BlueprintData hut(GameTestHelper helper) {
        StructureMeta meta = StructureRegistry.get(helper.getLevel().getServer()).byId(HUT).orElseThrow();
        return StructureBlueprints.blueprint(helper.getLevel(), meta, 1);
    }
}
