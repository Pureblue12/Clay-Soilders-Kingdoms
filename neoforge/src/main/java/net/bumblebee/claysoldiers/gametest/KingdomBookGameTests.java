package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.dev.BookFormat;
import net.bumblebee.claysoldiers.dev.DevTool;
import net.bumblebee.claysoldiers.dev.DevToolRegistry;
import net.bumblebee.claysoldiers.entity.soldier.ClaySoldierEntity;
import net.bumblebee.claysoldiers.kingdom.ChronicleEntry;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomBook;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.GROUND_Y;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;
import static net.bumblebee.claysoldiers.gametest.KingdomGameTests.RED;

/**
 * Tests for the observer: the kingdom book, the /csr kingdom commands, and their dev book buttons. Each asserts the
 * CORRECT behavior.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class KingdomBookGameTests {

    /**
     * The book has a status page (team, status, tier, population, renown, king with a "Go to the king" button, claim,
     * founding time), the stockpile, the buildings, and every chronicle line in order with its game time.
     */
    @GameTest(template = PLATFORM)
    public static void bookShowsStatusStockpileBuildingsAndChronicle(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = KingdomDataGameTests.fullKingdom();
        List<Component> pages = KingdomBook.pages(server, kingdom);
        helper.assertTrue(pages.size() >= 4, "Expected status, stockpile, buildings and chronicle pages, got " + pages.size());

        String status = pages.getFirst().getString();
        for (String expected : List.of("Kingdom of Blue", "Ruined", "Tier 3", "Population: 7", "Renown: 42", "King Arthur",
                "103 65 -198", "100 64 -200", "radius 40", "Founded Day 1")) {
            helper.assertTrue(status.contains(expected), "The status page doesn't show '" + expected + "': " + status);
        }
        helper.assertTrue(BookFormat.clickValues(pages.getFirst(), false).contains("/csr kingdom goto_king csr:blue"),
                "No 'Go to the king' button running /csr kingdom goto_king csr:blue: " + BookFormat.clickValues(pages.getFirst(), false));

        String all = pages.stream().map(Component::getString).collect(Collectors.joining("\n"));
        for (String expected : List.of("Stockpile", "12 Oak Log", "30 Wheat", "Buildings", "csr:keep", "built", "csr:cottage", "planned", "Chronicle")) {
            helper.assertTrue(all.contains(expected), "The book doesn't show '" + expected + "'");
        }
        int founded = all.indexOf("The kingdom of Blue was founded.");
        int died = all.indexOf("King Arthur has died.");
        helper.assertTrue(founded >= 0 && died > founded, "Chronicle lines missing or out of order");
        helper.assertTrue(all.contains(ChronicleEntry.formatTime(1200)) && all.contains(ChronicleEntry.formatTime(5000)),
                "Chronicle lines need their game time (" + ChronicleEntry.formatTime(1200) + ")");
        helper.succeed();
    }

    /**
     * Even a huge kingdom (1000 long chronicle lines, 300 kinds of resources, 100 buildings) fits a written book: at most
     * 100 pages, every page within the size limit, a short title; the newest chronicle lines are kept.
     */
    @GameTest(template = PLATFORM)
    public static void bookStaysWithinLimits(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Kingdom kingdom = Kingdom.found(RED, Level.OVERWORLD, BlockPos.ZERO, 32, UUID.randomUUID(), "Bartholomew the Exceedingly Long-Named", 0);
        for (int i = 0; i < 300; i++) {
            kingdom.addResource(ResourceLocation.fromNamespaceAndPath("somemod", "resource_with_a_long_name_" + i), i + 1);
        }
        for (int i = 0; i < 100; i++) {
            kingdom.addBuilding(new net.bumblebee.claysoldiers.kingdom.KingdomBuilding(ResourceLocation.fromNamespaceAndPath("csr", "cottage"),
                    new BlockPos(i, 64, -i), net.minecraft.core.Direction.NORTH, net.bumblebee.claysoldiers.kingdom.KingdomBuilding.State.BUILT));
        }
        for (int i = 0; i < 1000; i++) {
            kingdom.addChronicle(new ChronicleEntry(i * 100L, "Line " + i + ": something rather long happened in the kingdom, worth writing down."), 1000);
        }
        List<Component> pages = KingdomBook.pages(server, kingdom);
        helper.assertTrue(!pages.isEmpty() && pages.size() <= BookFormat.MAX_PAGES, "Pages: " + pages.size());
        for (int i = 0; i < pages.size(); i++) {
            int length = BookFormat.serializedLength(pages.get(i), server.registryAccess());
            helper.assertTrue(length <= BookFormat.MAX_PAGE_JSON_LENGTH, "Page " + (i + 1) + " is too long: " + length);
        }
        helper.assertTrue(KingdomBook.title(server, kingdom).length() <= BookFormat.MAX_TITLE_LENGTH, "Title too long");
        String all = pages.stream().map(Component::getString).collect(Collectors.joining("\n"));
        helper.assertTrue(all.contains("Line 999:"), "The newest chronicle line must be in the book");
        ItemStack book = KingdomBook.create(server, kingdom);
        helper.assertTrue(book.is(Items.WRITTEN_BOOK) && BookFormat.getPages(book).size() == pages.size(), "create() must return the written book");
        helper.succeed();
    }

    /**
     * The commands work for a normal player (permission 0): found the nearest loyal king's kingdom, get the book,
     * disband it.
     */
    @GameTest(template = PLATFORM, batch = "kingdom_commands")
    public static void kingdomCommandsFoundBookAndDisband(GameTestHelper helper) {
        KingdomGameTests.clear(helper);
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer player = KingdomGameTests.player(helper);
        BlockPos at = helper.absolutePos(new BlockPos(8, GROUND_Y, 8));
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.getInventory().clearContent();
        ClaySoldierEntity king = KingdomGameTests.spawnKing(helper, new BlockPos(9, GROUND_Y, 9), RED, player);
        try {
            run(server, player, "csr kingdom found");
            helper.assertTrue(Kingdoms.byTeam(server, RED).map(kingdom -> kingdom.king().id().equals(king.getUUID())).orElse(false),
                    "/csr kingdom found did not found Red's kingdom");
            run(server, player, "csr kingdom book");
            boolean hasBook = false;
            for (ItemStack stack : player.getInventory().items) {
                hasBook |= stack.is(Items.WRITTEN_BOOK) && !BookFormat.getPages(stack).isEmpty()
                        && BookFormat.getPages(stack).getFirst().getString().contains("Kingdom of Red");
            }
            helper.assertTrue(hasBook, "/csr kingdom book did not give the kingdom book");
            run(server, player, "csr kingdom disband");
            helper.assertTrue(Kingdoms.byTeam(server, RED).isEmpty(), "/csr kingdom disband did not disband Red's kingdom");
        } finally {
            KingdomGameTests.clear(helper);
        }
        helper.succeed();
    }

    /**
     * "Go to the king" teleports, so it needs permission level 2 (operators); the other kingdom commands don't.
     */
    @GameTest(template = PLATFORM)
    public static void gotoKingNeedsPermissionTwo(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var normal = server.createCommandSourceStack().withPermission(0);
        var operator = server.createCommandSourceStack().withPermission(2);
        helper.assertTrue(!complete(dispatcher, normal, "csr kingdom goto_king csr:red"), "A normal player could use goto_king");
        helper.assertTrue(complete(dispatcher, operator, "csr kingdom goto_king csr:red"), "An operator can't use goto_king");
        for (String command : List.of("csr kingdom found", "csr kingdom disband", "csr kingdom book", "csr kingdom status", "csr kingdom list")) {
            helper.assertTrue(complete(dispatcher, normal, command), "A normal player can't use /" + command);
        }
        helper.succeed();
    }

    /**
     * The dev book's Kingdom category has buttons for founding, disbanding, the book and status (links to the real
     * commands) and the dev-only stockpile tools.
     */
    @GameTest(template = PLATFORM)
    public static void devBookHasKingdomButtons(GameTestHelper helper) {
        Set<String> commands = DevToolRegistry.defaults().tools().stream().map(DevTool::command).collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        for (String command : List.of("/csr kingdom found", "/csr kingdom disband", "/csr kingdom book", "/csr kingdom status",
                "/csr dev kingdom stock_add", "/csr dev kingdom stock_remove", "/csr dev kingdom stock_show")) {
            if (!commands.contains(command)) {
                missing.add(command);
            }
        }
        helper.assertTrue(missing.isEmpty(), "No dev book entry for " + missing);
        helper.succeed();
    }

    private static void run(MinecraftServer server, FakePlayer player, String command) {
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(0), command);
    }

    private static boolean complete(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher,
                                    net.minecraft.commands.CommandSourceStack source, String command) {
        var parse = dispatcher.parse(command, source);
        return parse.getExceptions().isEmpty() && !parse.getReader().canRead() && parse.getContext().getCommand() != null;
    }
}
