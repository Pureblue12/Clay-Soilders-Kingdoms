package net.bumblebee.claysoldiers.kingdom;

import net.bumblebee.claysoldiers.dev.BookFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The observer's book for one kingdom, made on demand ({@code /csr kingdom book}): a status page (tier, status,
 * population, renown, king and where it is with a "Go to the king" button, claim), the stockpile, the buildings and
 * the chronicle. The pages are rewritten whenever the book is opened ({@link #refresh}, from the hand or a lectern), so
 * it always shows the kingdom as it is. A book whose kingdom was disbanded keeps its last pages, with a notice first.
 */
public final class KingdomBook {
    /** Marks kingdom books (custom data), with the team id as value. */
    public static final String MARKER = "csr_kingdom_book";
    /** The kingdom's id: a book follows its own kingdom, not a later kingdom of the same team. */
    static final String ID_TAG = "csr_kingdom_id";
    /** Game time the pages were last written. */
    static final String UPDATED_TAG = "csr_kingdom_book_updated";
    /** Set once the kingdom no longer exists and the notice page was written. */
    static final String GONE_TAG = "csr_kingdom_book_gone";

    private static final String AUTHOR = "Clay Soldiers";

    private KingdomBook() {
    }

    /** Most pages for the stockpile and for the buildings each; the rest of the book is for the chronicle. */
    private static final int MAX_LIST_PAGES = 10;
    /** Chronicle lines longer than this are cut (a page has room for about 250 characters). */
    private static final int MAX_LINE_CHARS = 180;

    public static ItemStack create(MinecraftServer server, Kingdom kingdom) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        BookFormat.setPages(book, title(server, kingdom), AUTHOR, pages(server, kingdom));
        markUpdated(server, book, kingdom);
        return book;
    }

    /**
     * Whether the stack is a kingdom book.
     */
    public static boolean isKingdomBook(ItemStack stack) {
        return stack.is(Items.WRITTEN_BOOK) && tag(stack).contains(MARKER, Tag.TAG_STRING);
    }

    /**
     * Rewrites a kingdom book's pages to show its kingdom as it is now. If the kingdom no longer exists (disbanded,
     * or the team has a different kingdom now), the first page is replaced once by a notice and the other pages are
     * kept as the last record of it. Books made before the kingdom id was stored follow their team's kingdom.
     *
     * @return whether the pages changed (false for other items)
     */
    public static boolean refresh(MinecraftServer server, ItemStack book) {
        if (!isKingdomBook(book)) {
            return false;
        }
        CompoundTag tag = tag(book);
        ResourceLocation team = ResourceLocation.tryParse(tag.getString(MARKER));
        Optional<Kingdom> kingdom = team == null ? Optional.empty() : Kingdoms.byTeam(server, team)
                .filter(found -> !tag.hasUUID(ID_TAG) || tag.getUUID(ID_TAG).equals(found.id()));
        List<Component> old = BookFormat.getPages(book);
        if (kingdom.isPresent()) {
            List<Component> pages = pages(server, kingdom.get());
            boolean changed = !pages.equals(old);
            if (changed) {
                BookFormat.setPages(book, title(server, kingdom.get()), AUTHOR, pages);
            }
            markUpdated(server, book, kingdom.get());
            return changed;
        }
        if (tag.getBoolean(GONE_TAG) || old.isEmpty()) {
            return false;
        }
        List<Component> pages = new ArrayList<>(old);
        pages.set(0, gonePage(book, tag.contains(UPDATED_TAG) ? tag.getLong(UPDATED_TAG) : -1));
        WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        BookFormat.setPages(book, content == null ? "Kingdom" : content.title().raw(), AUTHOR, pages);
        CustomData.update(DataComponents.CUSTOM_DATA, book, data -> data.putBoolean(GONE_TAG, true));
        return true;
    }

    private static Component gonePage(ItemStack book, long updated) {
        WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(content == null ? "Kingdom" : content.title().raw()).withStyle(ChatFormatting.BOLD));
        lines.add(Component.literal("This kingdom no longer exists: it was disbanded.").withStyle(ChatFormatting.DARK_RED));
        lines.add(Component.empty());
        lines.add(Component.literal(updated < 0 ? "The other pages show it as it was when this book last saw it."
                : "The other pages show it as it was on " + ChronicleEntry.formatTime(updated) + ".").withStyle(ChatFormatting.GRAY));
        return BookFormat.joinLines(lines);
    }

    private static void markUpdated(MinecraftServer server, ItemStack book, Kingdom kingdom) {
        long now = server.overworld().getGameTime();
        CustomData.update(DataComponents.CUSTOM_DATA, book, tag -> {
            tag.putString(MARKER, kingdom.team().toString());
            tag.putUUID(ID_TAG, kingdom.id());
            tag.putLong(UPDATED_TAG, now);
        });
    }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static String title(MinecraftServer server, Kingdom kingdom) {
        String title = "Kingdom of " + Kingdoms.teamName(server, kingdom.team());
        return title.length() <= BookFormat.MAX_TITLE_LENGTH ? title : title.substring(0, BookFormat.MAX_TITLE_LENGTH);
    }

    public static List<Component> pages(MinecraftServer server, Kingdom kingdom) {
        List<Component> pages = new ArrayList<>();
        pages.add(statusPage(server, kingdom));

        List<Component> stock = new ArrayList<>();
        kingdom.stockpile().forEach((resource, count) -> stock.add(Component.literal(count + " " + resourceName(resource))));
        if (stock.isEmpty()) {
            stock.add(Component.literal("(empty)").withStyle(ChatFormatting.GRAY));
        }
        pages.addAll(listPages("Stockpile", stock, MAX_LIST_PAGES));

        List<Component> buildings = new ArrayList<>();
        for (KingdomBuilding building : kingdom.buildings()) {
            buildings.add(Component.literal(building.structure() + ": " + building.state().getSerializedName()));
            buildings.add(Component.literal("  at " + format(building.pos()) + " " + building.facing().getName()).withStyle(ChatFormatting.GRAY));
        }
        if (buildings.isEmpty()) {
            buildings.add(Component.literal("(none yet)").withStyle(ChatFormatting.GRAY));
        }
        pages.addAll(listPages("Buildings", buildings, MAX_LIST_PAGES));

        List<Component> keep = KingdomKeep.describe(server, kingdom).stream().map(line -> (Component) Component.literal(line)).toList();
        pages.addAll(listPages("Keep", keep, MAX_LIST_PAGES));

        pages.addAll(chroniclePages(kingdom, BookFormat.MAX_PAGES - pages.size()));
        return pages;
    }

    private static Component statusPage(MinecraftServer server, Kingdom kingdom) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(title(server, kingdom)).withStyle(ChatFormatting.BOLD));
        String status = kingdom.status() == KingdomStatus.ACTIVE ? "Active" : "Ruined";
        lines.add(Component.literal(status + " · Tier " + kingdom.tier()).withStyle(kingdom.status() == KingdomStatus.ACTIVE ? ChatFormatting.DARK_GREEN : ChatFormatting.DARK_RED));
        Kingdom.Census census = kingdom.census();
        lines.add(Component.literal("Population: " + (census.tick() < 0 ? "?" : census.population())));
        lines.add(Component.literal(census.tick() < 0 ? " not counted yet" : " " + ChronicleEntry.formatTime(census.tick())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Renown: " + kingdom.renown()));
        lines.add(Component.literal(Kingdoms.kingTitle(server, kingdom, true)));
        lines.add(Component.literal(" at " + format(kingdom.king().lastPos())).withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal(" seen " + ChronicleEntry.formatTime(kingdom.king().seenTick())).withStyle(ChatFormatting.GRAY));
        String command = "/csr kingdom goto_king " + kingdom.team();
        lines.add(BookFormat.runCommand(Component.literal("[Go to the king]").withStyle(ChatFormatting.BLUE, ChatFormatting.UNDERLINE),
                command, Component.literal("Teleports you to the king (operators only): " + command)));
        lines.add(Component.literal("Claim: " + format(kingdom.center())));
        lines.add(Component.literal(" radius " + kingdom.claimRadius()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Founded " + ChronicleEntry.formatTime(kingdom.foundedTick())));
        return BookFormat.joinLines(lines);
    }

    /**
     * Lines on pages under a heading ("Stockpile", "Stockpile (2)", ...), at most {@code maxPages}; lines that don't
     * fit are summarized.
     */
    private static List<Component> listPages(String heading, List<Component> lines, int maxPages) {
        List<Component> pages = new ArrayList<>();
        List<Component> page = new ArrayList<>();
        int used = 0;
        for (int i = 0; i < lines.size(); i++) {
            int height = BookFormat.estimateLines(lines.get(i).getString(), false);
            if (page.isEmpty()) {
                page.add(Component.literal(pages.isEmpty() ? heading : heading + " (" + (pages.size() + 1) + ")").withStyle(ChatFormatting.BOLD));
                used = 1;
            }
            if (used + height > BookFormat.LINES_PER_PAGE && page.size() > 1) {
                if (pages.size() + 1 == maxPages) {
                    page.add(Component.literal("... and " + (lines.size() - i) + " more").withStyle(ChatFormatting.GRAY));
                    break;
                }
                pages.add(BookFormat.joinLines(page));
                page = new ArrayList<>();
                i--;
                continue;
            }
            page.add(lines.get(i));
            used += height;
        }
        if (!page.isEmpty()) {
            pages.add(BookFormat.joinLines(page));
        }
        return pages;
    }

    /**
     * The chronicle, oldest first, on at most {@code maxPages} pages; when it doesn't fit, the newest lines are kept.
     */
    private static List<Component> chroniclePages(Kingdom kingdom, int maxPages) {
        if (maxPages <= 0) {
            return List.of();
        }
        List<List<Component>> pagesNewestFirst = new ArrayList<>();
        List<Component> page = new ArrayList<>();
        int used = 1; // the heading
        List<ChronicleEntry> entries = kingdom.chronicle();
        int shown = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            ChronicleEntry entry = entries.get(i);
            String text = entry.text().length() <= MAX_LINE_CHARS ? entry.text() : entry.text().substring(0, MAX_LINE_CHARS) + "...";
            String time = ChronicleEntry.formatTime(entry.gameTime());
            int height = 1 + BookFormat.estimateLines(text, false);
            if (used + height > BookFormat.LINES_PER_PAGE && !page.isEmpty()) {
                if (pagesNewestFirst.size() + 1 == maxPages) {
                    break;
                }
                pagesNewestFirst.add(page);
                page = new ArrayList<>();
                used = 1;
            }
            page.addFirst(Component.literal(text));
            page.addFirst(Component.literal(time).withStyle(ChatFormatting.GRAY));
            used += height;
            shown++;
        }
        pagesNewestFirst.add(page);
        List<Component> pages = new ArrayList<>();
        for (int i = pagesNewestFirst.size() - 1; i >= 0; i--) {
            List<Component> lines = new ArrayList<>(pagesNewestFirst.get(i));
            boolean first = pages.isEmpty();
            String heading = first ? (shown < entries.size() ? "Chronicle (newest " + shown + ")" : "Chronicle") : "Chronicle (" + (pages.size() + 1) + ")";
            lines.addFirst(Component.literal(heading).withStyle(ChatFormatting.BOLD));
            if (first && entries.isEmpty()) {
                lines.add(Component.literal("(nothing yet)").withStyle(ChatFormatting.GRAY));
            }
            pages.add(BookFormat.joinLines(lines));
        }
        return pages;
    }

    /** An item's name ("Oak Log") for item ids, otherwise the id. */
    static String resourceName(ResourceLocation resource) {
        return BuiltInRegistries.ITEM.getOptional(resource).filter(item -> item != Items.AIR)
                .map(item -> item.getDescription().getString()).orElse(resource.toString());
    }

    static String format(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
