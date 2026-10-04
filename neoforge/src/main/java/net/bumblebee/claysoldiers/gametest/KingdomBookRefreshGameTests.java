package net.bumblebee.claysoldiers.gametest;

import net.bumblebee.claysoldiers.ClaySoldiersCommon;
import net.bumblebee.claysoldiers.dev.BookFormat;
import net.bumblebee.claysoldiers.kingdom.Kingdom;
import net.bumblebee.claysoldiers.kingdom.KingdomBook;
import net.bumblebee.claysoldiers.kingdom.Kingdoms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.stream.Collectors;

import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.GROUND_Y;
import static net.bumblebee.claysoldiers.gametest.ClaySoldierGameTests.PLATFORM;
import static net.bumblebee.claysoldiers.gametest.KingdomGameTests.RED;

/**
 * The kingdom book shows the kingdom as it is when the book is opened (in the hand or on a lectern), not as it was when
 * the book was made. Each asserts the CORRECT behavior. Each test founds a kingdom, so each has its own batch and
 * cleans up.
 */
@GameTestHolder(ClaySoldiersCommon.MOD_ID)
@PrefixGameTestTemplate(false)
public class KingdomBookRefreshGameTests {
    private static final ResourceLocation OAK_LOG = ResourceLocation.withDefaultNamespace("oak_log");

    /**
     * Opening a kingdom book from the hand (right-click) shows stockpile and chronicle changes made after the book was
     * made.
     */
    @GameTest(template = PLATFORM, batch = "kingdom_book_refresh_hand")
    public static void heldBookRefreshesWhenOpened(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer player = setUp(helper);
        try {
            Kingdom kingdom = found(helper, player);
            ItemStack book = KingdomBook.create(server, kingdom);
            player.setItemInHand(InteractionHand.MAIN_HAND, book);
            Kingdoms.addResource(server, kingdom, OAK_LOG, 16);
            Kingdoms.chronicle(server, kingdom, "A traveller passed through.");

            player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);

            String text = text(player.getMainHandItem());
            helper.assertTrue(text.contains("16 Oak Log"), "The opened book doesn't show the new stockpile: " + text);
            helper.assertTrue(text.contains("A traveller passed through."), "The opened book doesn't show the new chronicle line");
        } finally {
            KingdomGameTests.clear(helper);
        }
        helper.succeed();
    }

    /**
     * Opening a kingdom book that lies on a lectern shows changes made after it was put there.
     */
    @GameTest(template = PLATFORM, batch = "kingdom_book_refresh_lectern")
    public static void lecternBookRefreshesWhenOpened(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer player = setUp(helper);
        try {
            Kingdom kingdom = found(helper, player);
            BlockPos lectern = helper.absolutePos(new BlockPos(4, GROUND_Y, 4));
            helper.getLevel().setBlockAndUpdate(lectern, Blocks.LECTERN.defaultBlockState());
            helper.assertTrue(LecternBlock.tryPlaceBook(player, helper.getLevel(), lectern, helper.getLevel().getBlockState(lectern),
                    KingdomBook.create(server, kingdom)), "Setup: the book could not be put on the lectern");
            Kingdoms.addResource(server, kingdom, OAK_LOG, 16);

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(lectern), Direction.UP, lectern, false));

            ItemStack onLectern = ((LecternBlockEntity) helper.getLevel().getBlockEntity(lectern)).getBook();
            String text = text(onLectern);
            helper.assertTrue(text.contains("16 Oak Log"), "The lectern's book doesn't show the new stockpile: " + text);
        } finally {
            KingdomGameTests.clear(helper);
        }
        helper.succeed();
    }

    /**
     * After the kingdom is disbanded, opening its book says so on the first page and keeps the other pages as they
     * were (the last record of it). Founding the team's kingdom again doesn't take over the old book, and opening it
     * again doesn't add a second notice.
     */
    @GameTest(template = PLATFORM, batch = "kingdom_book_refresh_gone")
    public static void bookOfDisbandedKingdomSaysSo(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer player = setUp(helper);
        try {
            Kingdom kingdom = found(helper, player);
            Kingdoms.addResource(server, kingdom, OAK_LOG, 16);
            ItemStack book = KingdomBook.create(server, kingdom);
            int pagesBefore = BookFormat.getPages(book).size();
            player.setItemInHand(InteractionHand.MAIN_HAND, book);
            helper.assertTrue(Kingdoms.disband(server, RED, player).ok(), "Setup: disbanding failed");
            helper.assertTrue(found(helper, player) != null, "Setup: founding again failed");

            for (int i = 0; i < 2; i++) {
                player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
            }

            List<Component> pages = BookFormat.getPages(player.getMainHandItem());
            String first = pages.getFirst().getString();
            helper.assertTrue(first.contains("no longer exists"), "The first page should say the kingdom no longer exists: " + first);
            helper.assertTrue(pages.size() == pagesBefore, "The book should keep its pages (" + pagesBefore + "), has " + pages.size());
            helper.assertTrue(text(player.getMainHandItem()).contains("16 Oak Log"), "The old stockpile page should be kept");
            long notices = pages.stream().filter(page -> page.getString().contains("no longer exists")).count();
            helper.assertTrue(notices == 1, "Expected one notice, found " + notices);
        } finally {
            KingdomGameTests.clear(helper);
        }
        helper.succeed();
    }

    /**
     * Other written books are not changed by opening them.
     */
    @GameTest(template = PLATFORM)
    public static void otherWrittenBooksAreNotChanged(GameTestHelper helper) {
        FakePlayer player = KingdomGameTests.player(helper);
        ItemStack book = BookFormat.createBook("Diary", "Someone", List.of(Component.literal("Dear diary")));
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        player.gameMode.useItem(player, helper.getLevel(), book, InteractionHand.MAIN_HAND);
        helper.assertTrue(text(player.getMainHandItem()).equals("Dear diary"), "A normal book was changed: " + text(player.getMainHandItem()));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.succeed();
    }

    private static FakePlayer setUp(GameTestHelper helper) {
        KingdomGameTests.clear(helper);
        FakePlayer player = KingdomGameTests.player(helper);
        BlockPos at = helper.absolutePos(new BlockPos(8, GROUND_Y, 8));
        player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        player.getInventory().clearContent();
        return player;
    }

    private static Kingdom found(GameTestHelper helper, FakePlayer player) {
        var king = KingdomGameTests.spawnKing(helper, new BlockPos(9, GROUND_Y, 9), RED, player);
        Kingdoms.Result result = Kingdoms.found(helper.getLevel(), king, player);
        helper.assertTrue(result.ok(), "Setup: founding failed: " + result.refusal());
        return result.kingdom();
    }

    private static String text(ItemStack book) {
        return book.is(Items.WRITTEN_BOOK) ? BookFormat.getPages(book).stream().map(Component::getString).collect(Collectors.joining("\n")) : "";
    }
}
