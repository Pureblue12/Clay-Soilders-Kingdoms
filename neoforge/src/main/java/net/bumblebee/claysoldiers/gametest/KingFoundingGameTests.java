package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.blueprint.BlueprintData;
import net.bumblebee.claysoldiers.blueprint.templates.ServerBlueprintPlan;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugEvent;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.KingFounding;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomBuilding;
import net.bumblebee.claysoldiers.kingdom.KingdomKeep;
import net.bumblebee.claysoldiers.kingdom.KingdomStatus;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.bumblebee.claysoldiers.structure.StructureBlueprints;
import net.bumblebee.claysoldiers.structure.StructureMeta;
import net.bumblebee.claysoldiers.structure.StructureRegistry;
import net.bumblebee.claysoldiers.team.TeamLoyaltyManger;
import net.bumblebee.claysoldiers.terrain.TestTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.GROUND_Y;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;
import static net.bumblebee.claysoldiers.gametest.TerrainScanGameTests.TERRAIN;

/**
 * Stage 4, founding with nothing but a gold ingot (D93): a soldier that picks up a gold ingot is a king and founds his
 * kingdom by himself, with no player loyal to his team; a king whose team has a kingdom doesn't; the kingdom's soldiers
 * build without a loyal player, and one without wheat is handed some. Uses teams no other test uses (Yellow, Green):
 * team loyalty is saved with the world, and these teams must have no loyal player.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class KingFoundingGameTests {
    static final ResourceLocation YELLOW = ResourceLocation.fromNamespaceAndPath("csr", "yellow");
    static final ResourceLocation GREEN = ResourceLocation.fromNamespaceAndPath("csr", "green");
    private static final String FOUND_BATCH = "king_founding";

    /** On the plateau: a gold ingot dropped by a soldier makes him king, and he founds the kingdom by himself. */
    @GameTest(template = TERRAIN, batch = FOUND_BATCH, timeoutTicks = 2400)
    public static void soldierWhoPicksUpAGoldIngotFoundsAKingdom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(helper, YELLOW);
        TerrainScanGameTests.build(helper, TestTerrain.Shape.PLATEAU);
        KingFounding.enableInTestsForTests(true);
        ClaySoldierEntity soldier = KingdomGameTests.DevSoldier.spawnPlain(helper, new BlockPos(16, 13, 16), YELLOW);
        helper.assertTrue(TeamLoyaltyManger.getTeamPlayerData(level).getPlayerForTeam(YELLOW) == null, "Setup: Yellow must have no loyal player");
        helper.assertFalse(Kingdoms.isKing(soldier), "Setup: a plain soldier isn't a king");
        level.addFreshEntity(new ItemEntity(level, soldier.getX(), soldier.getY() + 0.2, soldier.getZ(), new ItemStack(Items.GOLD_INGOT)));
        helper.succeedWhen(() -> {
            helper.assertTrue(Kingdoms.isKing(soldier), "The soldier didn't pick up the gold ingot (he isn't a king)");
            Optional<Kingdom> kingdom = Kingdoms.byTeam(server(helper), YELLOW);
            helper.assertTrue(kingdom.isPresent(), "No kingdom yet; the king is at " + soldier.blockPosition().toShortString());
            helper.assertTrue(kingdom.get().king().id().equals(soldier.getUUID()), "The kingdom's king isn't the soldier with the ingot");
            helper.assertFalse(KingFounding.founding(soldier), "A king with a kingdom must not go founding again");
            KingFounding.enableInTestsForTests(false);
            clear(helper, YELLOW);
        });
    }

    @AfterBatch(batch = FOUND_BATCH)
    public static void stopFounding(ServerLevel level) {
        KingFounding.enableInTestsForTests(false);
        Kingdoms.byTeam(level.getServer(), YELLOW).ifPresent(kingdom -> Kingdoms.disband(level.getServer(), YELLOW, null));
    }

    private static final String RUINS_BATCH = "king_founding_ruins";

    /**
     * A team whose king died (kingdom RUINED) gets a new kingdom from its next king, found by himself; the ruins don't
     * keep him standing idle (D95).
     */
    @GameTest(template = TERRAIN, batch = RUINS_BATCH, timeoutTicks = 2400)
    public static void newKingFoundsInPlaceOfARuinedKingdom(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        clear(helper, YELLOW);
        TerrainScanGameTests.build(helper, TestTerrain.Shape.PLATEAU);
        ClaySoldierEntity oldKing = KingdomGameTests.DevSoldier.spawnPlain(helper, new BlockPos(16, 13, 16), YELLOW);
        oldKing.equipItemIfPossible(new ItemStack(Items.GOLD_INGOT));
        helper.assertTrue(Kingdoms.found(level, oldKing, null, true).ok(), "Setup: founding failed");
        oldKing.kill();
        Kingdom ruins = Kingdoms.byTeam(server(helper), YELLOW).orElseThrow();
        helper.assertTrue(ruins.status() == KingdomStatus.RUINED, "Setup: the kingdom isn't ruined but " + ruins.status());
        DebugLog.reset();
        DebugLog.setEnabled(DebugCategory.byId("KINGDOM").orElseThrow(), true, server(helper));
        KingFounding.enableInTestsForTests(true);
        ClaySoldierEntity newKing = KingdomGameTests.DevSoldier.spawnPlain(helper, new BlockPos(16, 13, 16), YELLOW);
        newKing.equipItemIfPossible(new ItemStack(Items.GOLD_INGOT));
        helper.assertTrue(KingFounding.founding(newKing), "A new king of a ruined kingdom doesn't go founding");
        helper.succeedWhen(() -> {
            Kingdom kingdom = Kingdoms.byTeam(server(helper), YELLOW).orElseThrow();
            helper.assertTrue(kingdom.status() == KingdomStatus.ACTIVE && kingdom.king().id().equals(newKing.getUUID()),
                    "No new kingdom around the new king yet; he is at " + newKing.blockPosition().toShortString()
                            + ". Recorded: " + DebugLog.lastEvents(12).stream().map(DebugEvent::toLine).toList());
            KingFounding.enableInTestsForTests(false);
            DebugLog.reset();
            clear(helper, YELLOW);
        });
    }

    @AfterBatch(batch = RUINS_BATCH)
    public static void stopFoundingAfterRuins(ServerLevel level) {
        KingFounding.enableInTestsForTests(false);
        DebugLog.reset();
        // A failed run must not leave its claim (radius 32) in the way of later tests' foundings.
        Kingdoms.byTeam(level.getServer(), YELLOW).ifPresent(kingdom -> Kingdoms.disband(level.getServer(), YELLOW, null));
    }

    /**
     * A kingdom whose team no player is loyal to builds its keep (the test hut): the work rule lets a kingdom's soldiers
     * work (D93), and the kingdom hands a soldier without wheat some so he can build.
     */
    @GameTest(template = PLATFORM, batch = "king_founding_build", timeoutTicks = 9000)
    public static void kingdomWithoutALoyalPlayerBuildsAndHandsOutWheat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        clear(helper, GREEN);
        helper.assertTrue(TeamLoyaltyManger.getTeamPlayerData(level).getPlayerForTeam(GREEN) == null, "Setup: Green must have no loyal player");
        ClaySoldierEntity king = KingdomGameTests.DevSoldier.spawnPlain(helper, new BlockPos(13, GROUND_Y, 13), GREEN);
        king.equipItemIfPossible(new ItemStack(Items.GOLD_INGOT));
        helper.assertTrue(Kingdoms.found(level, king, null, true).ok(), "Setup: founding failed");
        Kingdom kingdom = Kingdoms.byTeam(server, GREEN).orElseThrow();
        KingdomKeep.forget(kingdom);
        StructureMeta meta = StructureRegistry.get(server).byId(KingdomKeepGameTests.HUT).orElseThrow();
        BlueprintData blueprint = StructureBlueprints.blueprint(level, meta, 1);
        ServerBlueprintPlan plan = blueprint.createServerPlan().orElseThrow();
        for (ItemStack stack : plan.getNeededItems()) {
            Kingdoms.addResource(server, kingdom, BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount());
        }
        ClaySoldierEntity worker = KingdomGameTests.DevSoldier.spawnPlain(helper, new BlockPos(3, GROUND_Y, 1), GREEN);
        helper.assertFalse(worker.getAttackType().canWork(), "Setup: a plain soldier can't work yet");
        BlockPos easel = helper.absolutePos(new BlockPos(8, GROUND_Y, 4));
        helper.assertTrue(KingdomKeep.start(level, kingdom, KingdomKeepGameTests.HUT, 1, blueprint, easel, Direction.SOUTH),
                "The hut wasn't started: " + KingdomKeep.describe(server, kingdom));
        BlockPos chest = KingdomKeep.chestPos(easel, Direction.SOUTH);
        helper.onEachTick(() -> KingdomKeep.update(level, kingdom));
        helper.succeedWhen(() -> {
            helper.assertTrue(worker.getAttackType().canWork(), "The soldier wasn't handed wheat");
            helper.assertTrue(chest.equals(worker.getPoiPos()), "The soldier wasn't made a builder of the hut");
            KingdomBuilding keep = KingdomKeep.keep(kingdom).orElseThrow();
            helper.assertTrue(keep.state() == KingdomBuilding.State.BUILT, "Not built yet: " + KingdomKeep.describe(server, kingdom));
            clear(helper, GREEN);
        });
    }

    private static void clear(GameTestHelper helper, ResourceLocation team) {
        Kingdoms.byTeam(server(helper), team).ifPresent(kingdom -> Kingdoms.disband(server(helper), team, null));
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }
}
