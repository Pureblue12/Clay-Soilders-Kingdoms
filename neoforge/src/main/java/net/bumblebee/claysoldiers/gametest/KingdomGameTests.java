package net.bumblebee.claysoldiers.gametest;

import com.mojang.authlib.GameProfile;
import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.debug.DebugCategory;
import net.bumblebee.claysoldiers.debug.DebugLog;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.init.ModEntityTypes;
import net.bumblebee.claysoldiers.kingdom.ChronicleEntry;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomStatus;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.bumblebee.claysoldiers.team.TeamLoyaltyManger;
import net.bumblebee.claysoldiers.tuning.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.GROUND_Y;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;

/**
 * Tests for kingdoms: founding (and its refusals), disbanding, the king's death, the stockpile, the chronicle cap, the
 * claim radius from the tuning file, two independent kingdoms, and the KINGDOM recorder category. Each asserts the
 * CORRECT behavior. Kingdom data is world-wide, so every test that founds one has its own batch and cleans up.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class KingdomGameTests {
    static final ResourceLocation RED = ResourceLocation.fromNamespaceAndPath("csr", "red");
    static final ResourceLocation BLUE = ResourceLocation.fromNamespaceAndPath("csr", "blue");
    private static final BlockPos KING_AT = new BlockPos(8, GROUND_Y, 8);

    @GameTest(template = PLATFORM, batch = "kingdom_found")
    public static void foundingCreatesKingdom(GameTestHelper helper) {
        clear(helper);
        FakePlayer player = player(helper);
        ClaySoldierEntity king = spawnKing(helper, KING_AT, RED, player);
        Kingdoms.Result result = Kingdoms.found(helper.getLevel(), king, player);
        helper.assertTrue(result.ok(), "Founding was refused: " + result.refusal());
        Kingdom kingdom = Kingdoms.byTeam(server(helper), RED).orElseThrow();
        helper.assertTrue(kingdom.center().equals(king.blockPosition()), "Center should be the king's position, got " + kingdom.center());
        helper.assertTrue(kingdom.claimRadius() == Tuning.KINGDOM_CLAIM_RADIUS.get(), "Claim radius should come from the tuning file");
        helper.assertTrue(kingdom.king().id().equals(king.getUUID()), "The king is not recorded");
        helper.assertTrue(kingdom.status() == KingdomStatus.ACTIVE && kingdom.tier() == 1, "A new kingdom is active, tier 1");
        helper.assertTrue(kingdom.dimension().equals(Level.OVERWORLD), "Dimension");
        helper.assertTrue(kingdom.chronicle().size() == 1 && kingdom.chronicle().getFirst().text().contains("The kingdom of Red was founded"),
                "The chronicle should say the kingdom was founded: " + kingdom.chronicle());
        clear(helper);
        helper.succeed();
    }

    @GameTest(template = PLATFORM, batch = "kingdom_no_king")
    public static void foundRefusedWithoutKing(GameTestHelper helper) {
        clear(helper);
        FakePlayer player = player(helper);
        DevSoldier.spawnPlain(helper, KING_AT, RED); // a soldier, but no king
        TeamLoyaltyManger.setTeamPlayer(helper.getLevel(), RED, player);
        Kingdoms.Result result = Kingdoms.foundNear(player);
        helper.assertTrue(!result.ok() && result.refusal().toLowerCase().contains("no king"), "Expected a 'no king' refusal, got " + result);
        helper.assertTrue(Kingdoms.byTeam(server(helper), RED).isEmpty(), "A kingdom was founded without a king");
        helper.succeed();
    }

    @GameTest(template = PLATFORM, batch = "kingdom_twice")
    public static void foundRefusedWhenTeamHasKingdom(GameTestHelper helper) {
        clear(helper);
        FakePlayer player = player(helper);
        helper.assertTrue(Kingdoms.found(helper.getLevel(), spawnKing(helper, KING_AT, RED, player), player).ok(), "Setup: first founding failed");
        Kingdoms.Result second = Kingdoms.found(helper.getLevel(), spawnKing(helper, new BlockPos(3, GROUND_Y, 3), RED, player), player);
        helper.assertTrue(!second.ok() && second.refusal().contains("already has a kingdom"), "Expected 'already has a kingdom', got " + second);
        clear(helper);
        helper.succeed();
    }

    @GameTest(template = PLATFORM, batch = "kingdom_overlap")
    public static void foundRefusedOnOverlap(GameTestHelper helper) {
        clear(helper);
        FakePlayer player = player(helper);
        helper.assertTrue(Kingdoms.found(helper.getLevel(), spawnKing(helper, KING_AT, RED, player), player).ok(), "Setup: Red's founding failed");
        Kingdoms.Result blue = Kingdoms.found(helper.getLevel(), spawnKing(helper, new BlockPos(2, GROUND_Y, 2), BLUE, player), player);
        helper.assertTrue(!blue.ok() && blue.refusal().contains("overlaps") && blue.refusal().contains("Red"), "Expected an overlap refusal naming Red, got " + blue);
        helper.assertTrue(Kingdoms.byTeam(server(helper), BLUE).isEmpty(), "Blue was founded on Red's land");
        clear(helper);
        helper.succeed();
    }

    @GameTest(template = PLATFORM, batch = "kingdom_dimension")
    public static void foundRefusedInUnsupportedDimension(GameTestHelper helper) {
        clear(helper);
        FakePlayer player = player(helper);
        ServerLevel nether = server(helper).getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "Setup: no nether");
        ClaySoldierEntity netherKing = ModEntityTypes.CLAY_SOLDIER_ENTITY.get().create(nether);
        netherKing.moveTo(0.5, 64, 0.5);
        netherKing.setClayTeamType(RED);
        netherKing.equipItemIfPossible(new ItemStack(Items.GOLD_INGOT));
        TeamLoyaltyManger.setTeamPlayer(helper.getLevel(), RED, player);
        Kingdoms.Result result = Kingdoms.found(nether, netherKing, player);
        helper.assertTrue(!result.ok() && result.refusal().contains("the_nether"), "Expected a refusal naming the dimension, got " + result);
        helper.succeed();
    }

    /**
     * Disbanding removes the kingdom and nothing else: the blocks around and the king stay as they were.
     */
    @GameTest(template = PLATFORM, batch = "kingdom_disband")
    public static void disbandRemovesKingdomAndLeavesWorld(GameTestHelper helper) {
        clear(helper);
        FakePlayer player = player(helper);
        ClaySoldierEntity king = spawnKing(helper, KING_AT, RED, player);
        helper.assertTrue(Kingdoms.found(helper.getLevel(), king, player).ok(), "Setup: founding failed");
        Map<BlockPos, BlockState> before = blocks(helper);
        int entities = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area(helper)).size();

        Kingdoms.Result disbanded = Kingdoms.disband(server(helper), RED, player);
        helper.assertTrue(disbanded.ok(), "Disband was refused: " + disbanded.refusal());
        helper.assertTrue(Kingdoms.byTeam(server(helper), RED).isEmpty(), "The kingdom still exists");
        helper.assertTrue(blocks(helper).equals(before), "Disbanding changed blocks");
        helper.assertTrue(king.isAlive() && helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, area(helper)).size() == entities,
                "Disbanding changed entities");
        helper.assertTrue(!Kingdoms.disband(server(helper), RED, player).ok(), "Disbanding a kingdom that doesn't exist should be refused");
        helper.succeed();
    }

    @GameTest(template = PLATFORM, batch = "kingdom_king_death")
    public static void kingDeathRuinsKingdom(GameTestHelper helper) {
        clear(helper);
        FakePlayer player = player(helper);
        ClaySoldierEntity king = spawnKing(helper, KING_AT, RED, player);
        ClaySoldierEntity other = DevSoldier.spawnPlain(helper, new BlockPos(4, GROUND_Y, 4), RED);
        helper.assertTrue(Kingdoms.found(helper.getLevel(), king, player).ok(), "Setup: founding failed");
        other.kill();
        helper.assertTrue(Kingdoms.byTeam(server(helper), RED).orElseThrow().status() == KingdomStatus.ACTIVE, "A soldier's death ruined the kingdom");
        king.kill();
        Kingdom kingdom = Kingdoms.byTeam(server(helper), RED).orElseThrow();
        helper.assertTrue(kingdom.status() == KingdomStatus.RUINED, "The king died but the kingdom is " + kingdom.status());
        helper.assertTrue(kingdom.chronicle().getLast().text().contains("has died"), "No chronicle line about the king's death: " + kingdom.chronicle());
        clear(helper);
        helper.succeed();
    }

    /**
     * Stockpile: add, remove, never negative, shortfall reported (all or nothing).
     */
    @GameTest(template = PLATFORM)
    public static void stockpileAddRemoveAndShortfall(GameTestHelper helper) {
        Kingdom kingdom = KingdomDataGameTests.fullKingdom();
        ResourceLocation stone = ResourceLocation.withDefaultNamespace("stone");
        kingdom.addResource(stone, 10);
        helper.assertTrue(kingdom.resource(stone) == 10, "add");
        helper.assertTrue(kingdom.removeResource(stone, 4).done() && kingdom.resource(stone) == 6, "remove 4 of 10");
        Kingdom.Withdrawal tooMuch = kingdom.removeResource(stone, 10);
        helper.assertTrue(!tooMuch.done() && tooMuch.shortfall() == 4 && kingdom.resource(stone) == 6,
                "Removing 10 of 6 must take nothing and report a shortfall of 4, got " + tooMuch + ", left " + kingdom.resource(stone));
        helper.assertTrue(kingdom.removeResource(stone, 6).done() && kingdom.resource(stone) == 0 && !kingdom.stockpile().containsKey(stone), "remove all");
        try {
            kingdom.addResource(stone, -1);
            helper.fail("Adding a negative amount must be refused");
        } catch (IllegalArgumentException expected) {
            // fine
        }
        helper.assertTrue(kingdom.resource(stone) == 0, "never negative");
        helper.succeed();
    }

    /**
     * The chronicle keeps at most the cap: the oldest lines are dropped, the order is kept.
     */
    @GameTest(template = PLATFORM)
    public static void chronicleCapDropsOldest(GameTestHelper helper) {
        Kingdom kingdom = Kingdom.found(RED, Level.OVERWORLD, BlockPos.ZERO, 10, UUID.randomUUID(), null, 0);
        for (int i = 1; i <= 5; i++) {
            kingdom.addChronicle(new ChronicleEntry(i, "line " + i), 3);
        }
        helper.assertTrue(kingdom.chronicle().stream().map(ChronicleEntry::text).toList().equals(List.of("line 3", "line 4", "line 5")),
                "Expected lines 3, 4, 5 in order, got " + kingdom.chronicle());
        helper.succeed();
    }

    /**
     * The chronicle cap comes from the tuning file (kingdom.chronicle_max).
     */
    @GameTest(template = PLATFORM, batch = "kingdom_chronicle_tuning")
    public static void chronicleMaxComesFromTuning(GameTestHelper helper) {
        clear(helper);
        FakePlayer player = player(helper);
        try {
            GameTestTuning.load("{\"kingdom.chronicle_max\": 5}");
            helper.assertTrue(Kingdoms.found(helper.getLevel(), spawnKing(helper, KING_AT, RED, player), player).ok(), "Setup: founding failed");
            Kingdom kingdom = Kingdoms.byTeam(server(helper), RED).orElseThrow();
            for (int i = 1; i <= 8; i++) {
                Kingdoms.chronicle(server(helper), kingdom, "event " + i);
            }
            helper.assertTrue(kingdom.chronicle().size() == 5 && kingdom.chronicle().getLast().text().equals("event 8")
                            && kingdom.chronicle().getFirst().text().equals("event 4"),
                    "Expected the newest 5 lines (event 4 to 8), got " + kingdom.chronicle());
        } finally {
            GameTestTuning.restore(server(helper));
            clear(helper);
        }
        helper.succeed();
    }

    /**
     * A tuning change to kingdom.claim_radius affects the next founding; existing kingdoms keep their radius.
     */
    @GameTest(template = PLATFORM, batch = "kingdom_claim_tuning")
    public static void claimRadiusTuningAffectsNewFounding(GameTestHelper helper) {
        clear(helper);
        FakePlayer player = player(helper);
        try {
            GameTestTuning.load("{\"kingdom.claim_radius\": 5}");
            helper.assertTrue(Kingdoms.found(helper.getLevel(), spawnKing(helper, new BlockPos(3, GROUND_Y, 3), RED, player), player).ok(), "Setup: Red failed");
            GameTestTuning.load("{\"kingdom.claim_radius\": 6}");
            helper.assertTrue(Kingdoms.found(helper.getLevel(), spawnKing(helper, new BlockPos(14, GROUND_Y, 14), BLUE, player), player).ok(), "Setup: Blue failed");
            int red = Kingdoms.byTeam(server(helper), RED).orElseThrow().claimRadius();
            int blue = Kingdoms.byTeam(server(helper), BLUE).orElseThrow().claimRadius();
            helper.assertTrue(red == 5 && blue == 6, "Expected Red 5 (founded before the change) and Blue 6, got " + red + " and " + blue);
        } finally {
            GameTestTuning.restore(server(helper));
            clear(helper);
        }
        helper.succeed();
    }

    /**
     * Two kingdoms of two teams don't affect each other: stockpiles, disbanding and a king's death stay separate.
     */
    @GameTest(template = PLATFORM, batch = "kingdom_two")
    public static void twoKingdomsStayIndependent(GameTestHelper helper) {
        clear(helper);
        FakePlayer player = player(helper);
        try {
            GameTestTuning.load("{\"kingdom.claim_radius\": 5}");
            // A player is loyal-owner of one team at a time, so found Red before making Blue loyal.
            ClaySoldierEntity redKing = spawnKing(helper, new BlockPos(3, GROUND_Y, 3), RED, player);
            helper.assertTrue(Kingdoms.found(helper.getLevel(), redKing, player).ok(), "Setup: founding Red failed");
            ClaySoldierEntity blueKing = spawnKing(helper, new BlockPos(14, GROUND_Y, 14), BLUE, player);
            helper.assertTrue(Kingdoms.found(helper.getLevel(), blueKing, player).ok(), "Setup: founding Blue failed");
            Kingdom red = Kingdoms.byTeam(server(helper), RED).orElseThrow();
            Kingdom blue = Kingdoms.byTeam(server(helper), BLUE).orElseThrow();
            ResourceLocation wheat = ResourceLocation.withDefaultNamespace("wheat");
            Kingdoms.addResource(server(helper), red, wheat, 20);
            helper.assertTrue(red.resource(wheat) == 20 && blue.resource(wheat) == 0, "Stockpiles are shared");
            blueKing.kill();
            helper.assertTrue(blue.status() == KingdomStatus.RUINED && red.status() == KingdomStatus.ACTIVE, "A king's death affected the other kingdom");
            Kingdoms.disband(server(helper), BLUE, player);
            helper.assertTrue(Kingdoms.byTeam(server(helper), RED).isPresent() && Kingdoms.byTeam(server(helper), BLUE).isEmpty(),
                    "Disbanding Blue affected Red");
        } finally {
            GameTestTuning.restore(server(helper));
            clear(helper);
        }
        helper.succeed();
    }

    /**
     * Recorder: founding, a refusal, a stockpile change, the king's death and disbanding are KINGDOM events.
     */
    @GameTest(template = PLATFORM, batch = "kingdom_log")
    public static void kingdomEventsAreLogged(GameTestHelper helper) {
        clear(helper);
        DebugLog.reset();
        DebugCategory kingdomCategory = DebugCategory.byId("KINGDOM").orElseThrow();
        DebugLog.setEnabled(kingdomCategory, true, server(helper));
        FakePlayer player = player(helper);
        ClaySoldierEntity king = spawnKing(helper, KING_AT, RED, player);
        Kingdoms.found(helper.getLevel(), king, player);
        Kingdoms.found(helper.getLevel(), king, player); // refused: already has one
        Kingdom kingdom = Kingdoms.byTeam(server(helper), RED).orElseThrow();
        Kingdoms.addResource(server(helper), kingdom, ResourceLocation.withDefaultNamespace("wheat"), 3);
        Kingdoms.removeResource(server(helper), kingdom, ResourceLocation.withDefaultNamespace("wheat"), 9);
        king.kill();
        Kingdoms.disband(server(helper), RED, player);
        Map<String, Boolean> seen = new HashMap<>();
        for (String text : List.of("founded", "refused", "stockpile", "shortfall", "king", "disbanded")) {
            seen.put(text, DebugLog.contains(kingdomCategory, text));
        }
        DebugLog.reset();
        helper.assertTrue(!seen.containsValue(false), "Missing KINGDOM events: " + seen);
        helper.succeed();
    }

    /**
     * The in-game trigger: a player sneaking and right-clicking their own king with a gold block founds the kingdom,
     * and the gold block is not used up. Without sneaking nothing is founded.
     */
    @GameTest(template = PLATFORM, batch = "kingdom_gold_block")
    public static void goldBlockOnKingFoundsKingdom(GameTestHelper helper) {
        clear(helper);
        FakePlayer player = player(helper);
        ClaySoldierEntity king = spawnKing(helper, KING_AT, RED, player);
        try {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_BLOCK, 2));
            player.setShiftKeyDown(false);
            player.interactOn(king, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(Kingdoms.byTeam(server(helper), RED).isEmpty(), "Founded without sneaking");
            player.setShiftKeyDown(true);
            player.interactOn(king, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(Kingdoms.byTeam(server(helper), RED).isPresent(), "Sneak + right-click with a gold block did not found the kingdom");
            helper.assertTrue(player.getMainHandItem().is(Items.GOLD_BLOCK) && player.getMainHandItem().getCount() == 2, "The gold block was used up");
        } finally {
            player.setShiftKeyDown(false);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            clear(helper);
        }
        helper.succeed();
    }

    /**
     * The census counts the team's living soldiers inside the claim (not other teams, not outside), records when, and
     * updates the king's last position.
     */
    @GameTest(template = PLATFORM, batch = "kingdom_census")
    public static void censusCountsSoldiersInClaim(GameTestHelper helper) {
        clear(helper);
        FakePlayer player = player(helper);
        try {
            GameTestTuning.load("{\"kingdom.claim_radius\": 5}");
            ClaySoldierEntity king = spawnKing(helper, KING_AT, RED, player);
            helper.assertTrue(Kingdoms.found(helper.getLevel(), king, player).ok(), "Setup: founding failed");
            DevSoldier.spawnPlain(helper, new BlockPos(6, GROUND_Y, 8), RED);
            DevSoldier.spawnPlain(helper, new BlockPos(10, GROUND_Y, 9), RED);
            DevSoldier.spawnPlain(helper, new BlockPos(7, GROUND_Y, 7), BLUE);   // other team
            DevSoldier.spawnPlain(helper, new BlockPos(1, GROUND_Y, 1), RED);    // outside the claim (about 10 away)
            king.teleportTo(king.getX() + 1, king.getY(), king.getZ());
            Kingdom kingdom = Kingdoms.byTeam(server(helper), RED).orElseThrow();
            Kingdoms.census(server(helper), kingdom);
            helper.assertTrue(kingdom.census().population() == 3 && kingdom.census().tick() >= 0,
                    "Expected 3 (king + 2 inside), got " + kingdom.census());
            helper.assertTrue(kingdom.king().lastPos().equals(king.blockPosition()), "The king's last position was not updated");
        } finally {
            GameTestTuning.restore(server(helper));
            clear(helper);
        }
        helper.succeed();
    }

    @AfterBatch(batch = "kingdom_log")
    public static void resetLog(ServerLevel level) {
        DebugLog.reset();
    }

    // Helpers

    static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    /** Removes the test teams' kingdoms (kingdom data is world-wide and survives between test runs). */
    static void clear(GameTestHelper helper) {
        for (ResourceLocation team : List.of(RED, BLUE)) {
            Kingdoms.byTeam(server(helper), team).ifPresent(kingdom -> Kingdoms.disband(server(helper), team, null));
        }
    }

    static FakePlayer player(GameTestHelper helper) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "csr-kingdom-tester"));
    }

    /**
     * A king (a clay soldier wearing a gold ingot) of {@code team}, with the team loyal to {@code owner}.
     */
    static ClaySoldierEntity spawnKing(GameTestHelper helper, BlockPos pos, ResourceLocation team, Player owner) {
        ClaySoldierEntity king = DevSoldier.spawnPlain(helper, pos, team);
        king.equipItemIfPossible(new ItemStack(Items.GOLD_INGOT));
        TeamLoyaltyManger.setTeamPlayer(helper.getLevel(), team, owner);
        helper.assertTrue(Kingdoms.isKing(king), "Setup: a soldier with a gold ingot should be a king");
        return king;
    }

    private static net.minecraft.world.phys.AABB area(GameTestHelper helper) {
        return new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16);
    }

    private static Map<BlockPos, BlockState> blocks(GameTestHelper helper) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        BlockPos.betweenClosedStream(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(15, 7, 15)))
                .forEach(pos -> blocks.put(pos.immutable(), helper.getLevel().getBlockState(pos)));
        return blocks;
    }

    /**
     * Plain clay soldiers for tests.
     */
    static final class DevSoldier {
        private DevSoldier() {
        }

        static ClaySoldierEntity spawnPlain(GameTestHelper helper, BlockPos pos, ResourceLocation team) {
            return DoorGameTests.spawnSoldier(helper, pos, team);
        }
    }
}
